# 智能面试 Agent 工作流模块化实施计划

> **给执行型 Agent 的要求：** 必须按模块逐项执行本计划。每个模块完成后先对照“验收标准”检查，再进入下一个模块。不要一次性把所有 Agent 和 Graph 混在一起实现。

**目标：** 在已有后端基础模块之上，补齐真实 AI 工作流：简历分析、面试计划生成、题目规划、四轮并行出题、题目合并校验、答案评估和报告生成，并替换当前占位逻辑。

**架构：** Agent 负责 AI 能力，Prompt 负责模板，Graph/Workflow 负责编排，Service 负责业务状态流转和持久化。阶段一是 `ResumeAnalysisAgent -> InterviewPlanningAgent` 串行执行；阶段二是 `QuestionPlanningAgent -> 4 个 RoundQuestionNode 并行 -> QuestionMergeNode`；阶段三是 `EvaluationAgent` 生成结构化评价和 Markdown/HTML 报告。

**技术栈：** Java 21、Spring Boot 3.5.7、Spring AI 1.1.2、Spring AI Alibaba 1.1.2.2、Spring AI Alibaba Graph Core、Spring AI ChatClient、BeanOutputConverter、Jackson、MinIO、MyBatis-Plus。

---

## 模块拆分总览

1. **模块一：Agent 数据模型与结构化输出 DTO**
   - 交付：简历分析、面试计划、题目规划、题目生成、评估结果的结构化 DTO。
   - 验收：DTO 字段能完整覆盖设计文档中的 Agent 输出。

2. **模块二：Prompt 模板模块**
   - 交付：5 个 Agent 的 System/User Prompt 模板。
   - 验收：Prompt 不硬编码业务状态流转；变量通过模板注入。

3. **模块三：简历分析与面试计划 Agent**
   - 交付：`ResumeAnalysisAgent`、`InterviewPlanningAgent`。
   - 验收：输入解析文本后能返回结构化分析和固定 4 轮计划。

4. **模块四：题目规划与题目生成 Agent**
   - 交付：`QuestionPlanningAgent`、`QuestionGenerationAgent`。
   - 验收：题目规划不落库；四轮题目生成能产出每轮 2 题。

5. **模块五：题目生成 Graph 编排**
   - 交付：Question planning node、四轮并行 generation node、merge node。
   - 验收：成功时返回 8 道题；任一轮失败则整个生成任务失败。

6. **模块六：评估 Agent 与报告生成**
   - 交付：`EvaluationAgent`、Markdown 报告生成、HTML 报告生成。
   - 验收：评估结果包含总体等级、总体反馈、改进建议和 8 道题逐题反馈。

7. **模块七：替换简历分析占位逻辑**
   - 交付：`InterviewResumeService.analyze` 接入真实 Agent。
   - 验收：简历状态和任务状态按真实分析结果流转。

8. **模块八：替换题目生成占位逻辑**
   - 交付：`InterviewSessionService.start/retryGenerate` 接入 Graph 出题。
   - 验收：数据库保存真实 8 道题和评分要点。

9. **模块九：替换提交评估占位逻辑**
   - 交付：`InterviewSessionService.submit/retryEvaluate` 接入真实评估。
   - 验收：提交后保存真实评分、逐题反馈和报告文件路径。

10. **模块十：Agent 工作流整体联调**
    - 交付：端到端 AI 主流程验收。
    - 验收：上传简历 → 分析 → 创建 Session → 生成题目 → 提交答案 → 生成报告全链路跑通。

---

## 模块一：Agent 数据模型与结构化输出 DTO

### 目标

先定义所有 Agent 的输入输出模型，保证后续 Prompt、Agent、Graph 和 Service 都使用同一套结构。

### 文件范围

- 新建：`src/main/java/com/highway/agent/model/interview/agent/ResumeAnalysisResult.java`
- 新建：`src/main/java/com/highway/agent/model/interview/agent/InterviewPlanResult.java`
- 新建：`src/main/java/com/highway/agent/model/interview/agent/QuestionPlanningResult.java`
- 新建：`src/main/java/com/highway/agent/model/interview/agent/GeneratedQuestionResult.java`
- 新建：`src/main/java/com/highway/agent/model/interview/agent/EvaluationResult.java`

### 实施步骤

- [ ] **步骤 1：创建 ResumeAnalysisResult**

字段：

```java
public record ResumeAnalysisResult(
        String targetPosition,
        Integer workYears,
        String salaryExpectation,
        String targetCity,
        String seniorityLevel,
        String difficultyStrategy,
        List<String> techTags,
        String summary,
        List<String> strengths,
        List<String> risks,
        List<String> projectHighlights
) {
}
```

`seniorityLevel` 只能是 `JUNIOR/MID/SENIOR/EXPERT`，`difficultyStrategy` 用于说明后续题目如何根据年限、期望薪资和岗位级别递进难度。

- [ ] **步骤 2：创建 InterviewPlanResult**

固定 4 轮计划：

```java
public record InterviewPlanResult(
        String summary,
        List<RoundPlan> rounds
) {
    public record RoundPlan(
            Integer roundNumber,
            String roundName,
            String difficulty,
            String difficultyReason,
            String focus,
            List<String> questionDirections
    ) {
    }
}
```

验收要求：`rounds.size() == 4`，轮次分别对应：

1. 语言基础
2. 主技术栈与框架能力
3. 简历项目深挖
4. 工程素养与问题排查

- [ ] **步骤 3：创建 QuestionPlanningResult**

题目规划只在内存中传递，不入库不存 MinIO：

```java
public record QuestionPlanningResult(List<Item> items) {
    public record Item(
            Integer roundNumber,
            Integer questionNumber,
            String focus,
            List<String> avoidOverlapWith
    ) {
    }
}
```

验收要求：`items.size() == 8`。

- [ ] **步骤 4：创建 GeneratedQuestionResult**

```java
public record GeneratedQuestionResult(
        Integer roundNumber,
        String roundName,
        String difficulty,
        Integer questionNumber,
        String content,
        List<String> scoringPoints
) {
}
```

验收要求：不包含完整标准答案，只包含评分要点。

- [ ] **步骤 5：创建 EvaluationResult**

```java
public record EvaluationResult(
        String overallGrade,
        String overallFeedback,
        List<String> improvementSuggestions,
        List<QuestionFeedback> questionFeedbacks,
        String markdownReport,
        String htmlReport
) {
    public record QuestionFeedback(
            Long questionId,
            String feedback
    ) {
    }
}
```

验收要求：`overallGrade` 只能是 `EXCELLENT/GOOD/PASS/FAIL`，`questionFeedbacks.size() == 8`。

### 验收标准

- [ ] 所有 DTO 都放在 `model/interview/agent` 包。
- [ ] DTO 使用 `record`。
- [ ] 题目模型不包含标准答案。
- [ ] 简历分析模型包含工作年限、期望薪资、目标城市、候选人级别和难度策略。
- [ ] 面试计划每轮包含 difficultyReason，说明难度如何匹配候选人画像。
- [ ] 评估模型包含逐题反馈，但不包含逐题等级。
- [ ] `./mvnw -q -DskipTests compile` 或 `mvn -q -DskipTests compile` 通过。

---

## 模块二：Prompt 模板模块

### 目标

把 5 个 Agent 的提示词集中放在 prompt 包，避免业务 Service 中硬编码 Prompt。

### 文件范围

- 新建：`src/main/java/com/highway/agent/prompt/InterviewAgentPrompt.java`

### 实施步骤

- [ ] **步骤 1：创建 Prompt 常量类**

类名：`InterviewAgentPrompt`。

包含 5 组 Prompt：

```java
public static final String RESUME_ANALYSIS_SYSTEM = "...";
public static final String RESUME_ANALYSIS_USER = "...";
public static final String INTERVIEW_PLANNING_SYSTEM = "...";
public static final String INTERVIEW_PLANNING_USER = "...";
public static final String QUESTION_PLANNING_SYSTEM = "...";
public static final String QUESTION_PLANNING_USER = "...";
public static final String QUESTION_GENERATION_SYSTEM = "...";
public static final String QUESTION_GENERATION_USER = "...";
public static final String EVALUATION_SYSTEM = "...";
public static final String EVALUATION_USER = "...";
```

- [ ] **步骤 2：简历分析 Prompt 要求**

Prompt 必须要求输出：

- 目标岗位
- 技术标签
- 简历摘要
- 优势
- 风险点
- 项目亮点

- [ ] **步骤 3：面试计划 Prompt 要求**

Prompt 必须固定输出 4 轮：

1. 语言基础
2. 主技术栈与框架能力
3. 简历项目深挖
4. 工程素养与问题排查

每轮包含难度、关注点和出题方向。

- [ ] **步骤 4：题目规划 Prompt 要求**

Prompt 必须输出 8 个题目规划项，并要求避免重复。

- [ ] **步骤 5：题目生成 Prompt 要求**

Prompt 必须要求：

- 每次只生成一个轮次的 2 道题。
- 题目贴合简历和该轮关注点。
- 输出评分要点。
- 不输出完整标准答案。

- [ ] **步骤 6：评估 Prompt 要求**

Prompt 必须要求：

- 总体等级：`EXCELLENT/GOOD/PASS/FAIL`。
- 总体反馈。
- 改进建议。
- 8 道题逐题反馈。
- Markdown 报告。
- HTML 报告。

### 验收标准

- [ ] Prompt 与逻辑分离，不写在 Service 中。
- [ ] Prompt 中的变量使用 `{resumeText}`、`{analysisJson}`、`{planJson}`、`{questionsJson}`、`{answersJson}` 等模板变量。
- [ ] Prompt 明确要求结构化 JSON 输出。
- [ ] Prompt 明确禁止生成标准答案。
- [ ] `./mvnw -q -DskipTests compile` 或 `mvn -q -DskipTests compile` 通过。

---

## 模块三：简历分析与面试计划 Agent

### 目标

实现阶段一串行工作流：先分析简历，再根据分析结果生成面试计划。

### 文件范围

- 新建：`src/main/java/com/highway/agent/service/interview/ResumeAnalysisAgent.java`
- 新建：`src/main/java/com/highway/agent/service/interview/InterviewPlanningAgent.java`
- 新建：`src/test/java/com/highway/agent/service/interview/ResumeAnalysisAgentTest.java`
- 新建：`src/test/java/com/highway/agent/service/interview/InterviewPlanningAgentTest.java`

### 实施步骤

- [ ] **步骤 1：实现 ResumeAnalysisAgent**

依赖：

```java
private final ChatClient chatClient;
private final ObjectMapper objectMapper;
```

核心方法：

```java
public ResumeAnalysisResult analyze(String resumeText)
```

行为：

1. 使用 `BeanOutputConverter<ResumeAnalysisResult>` 生成 format 提示。
2. 使用 `PromptTemplate` 注入 `resumeText` 和 `format`。
3. 调用 `chatClient.prompt().system(...).user(...).call().content()`。
4. 用 converter 解析结构化结果。
5. 校验技术标签、摘要等必要字段不为空。

- [ ] **步骤 2：实现 InterviewPlanningAgent**

核心方法：

```java
public InterviewPlanResult plan(ResumeAnalysisResult analysisResult, String resumeText)
```

行为：

1. 将 `analysisResult` 序列化为 JSON。
2. 调用大模型生成固定 4 轮计划。
3. 校验 `rounds.size() == 4`。
4. 校验轮次编号为 1 到 4。

- [ ] **步骤 3：编写 Agent 单元测试**

测试重点：

- 使用 mock `ChatClient` 或封装一个可替换的 LLM 调用接口。
- 给定结构化 JSON 字符串，能解析为 DTO。
- 非法 JSON 或缺失字段时抛出异常。
- 面试计划不是 4 轮时抛出异常。

### 验收标准

- [ ] `ResumeAnalysisAgent.analyze` 返回结构化 `ResumeAnalysisResult`。
- [ ] `InterviewPlanningAgent.plan` 返回 4 轮 `InterviewPlanResult`。
- [ ] Agent 不操作数据库。
- [ ] Agent 不写 MinIO。
- [ ] Agent 只负责模型调用和结果校验。
- [ ] 对结构化输出解析失败有明确异常。
- [ ] 对 4 轮计划数量不符有明确异常。

---

## 模块四：题目规划与题目生成 Agent

### 目标

实现阶段二的 AI 能力：先规划 8 个题目焦点，再按轮次生成真实题目。

### 文件范围

- 新建：`src/main/java/com/highway/agent/service/interview/QuestionPlanningAgent.java`
- 新建：`src/main/java/com/highway/agent/service/interview/QuestionGenerationAgent.java`
- 新建：`src/test/java/com/highway/agent/service/interview/QuestionPlanningAgentTest.java`
- 新建：`src/test/java/com/highway/agent/service/interview/QuestionGenerationAgentTest.java`

### 实施步骤

- [ ] **步骤 1：实现 QuestionPlanningAgent**

核心方法：

```java
public QuestionPlanningResult planQuestions(ResumeAnalysisResult analysisResult, InterviewPlanResult interviewPlanResult)
```

行为：

1. 输入简历分析和面试计划。
2. 生成 8 个题目焦点。
3. 校验每轮 2 个题目焦点。
4. 不落库，不存 MinIO。

- [ ] **步骤 2：实现 QuestionGenerationAgent**

核心方法：

```java
public List<GeneratedQuestionResult> generateRoundQuestions(
        ResumeAnalysisResult analysisResult,
        InterviewPlanResult.RoundPlan roundPlan,
        List<QuestionPlanningResult.Item> planningItems
)
```

行为：

1. 每次只处理一个轮次。
2. 输出该轮 2 道题。
3. 校验题目编号、轮次编号、评分要点。
4. 不输出标准答案。

- [ ] **步骤 3：编写单元测试**

测试覆盖：

- 题目规划必须正好 8 个 item。
- 每轮必须正好 2 个 item。
- 生成题目必须正好 2 道。
- 题目必须有 `content` 和 `scoringPoints`。
- scoringPoints 不能为空。

### 验收标准

- [ ] 题目规划结果不写数据库、不写 MinIO。
- [ ] 题目生成结果包含轮次、难度、题干和评分要点。
- [ ] 每轮只生成 2 道题。
- [ ] 题目不包含完整标准答案。
- [ ] 结构化输出异常会导致该 Agent 调用失败。

---

## 模块五：题目生成 Graph 编排

### 目标

用 Spring AI Alibaba Graph/Workflow 编排题目生成：题目规划后，四个轮次并行生成，再合并校验为 8 道题。

### 文件范围

- 新建：`src/main/java/com/highway/agent/interview/graph/InterviewQuestionGraph.java`
- 新建：`src/main/java/com/highway/agent/interview/graph/InterviewQuestionState.java`
- 新建：`src/main/java/com/highway/agent/interview/node/QuestionPlanningNode.java`
- 新建：`src/main/java/com/highway/agent/interview/node/RoundQuestionNode.java`
- 新建：`src/main/java/com/highway/agent/interview/node/QuestionMergeNode.java`
- 新建：`src/test/java/com/highway/agent/interview/graph/InterviewQuestionGraphTest.java`

### 实施步骤

- [ ] **步骤 1：定义 Graph State**

State 至少包含：

```java
resumeId
sessionId
resumeAnalysisResult
interviewPlanResult
questionPlanningResult
round1Questions
round2Questions
round3Questions
round4Questions
mergedQuestions
failureReason
```

- [ ] **步骤 2：实现 QuestionPlanningNode**

调用 `QuestionPlanningAgent`，把结果写入 state。

- [ ] **步骤 3：实现 RoundQuestionNode**

一个节点类复用 4 次，通过 `roundNumber` 参数区分轮次。

行为：

1. 从 state 中读取对应 RoundPlan。
2. 从 questionPlanningResult 中筛选该轮 2 个规划项。
3. 调用 `QuestionGenerationAgent`。
4. 写回对应 round questions。

- [ ] **步骤 4：实现 QuestionMergeNode**

行为：

1. 合并四轮结果。
2. 按 `roundNumber/questionNumber` 排序。
3. 校验总数为 8。
4. 校验每轮 2 题。
5. 校验没有重复的 `roundNumber + questionNumber`。

- [ ] **步骤 5：实现 InterviewQuestionGraph**

目标结构：

```text
START
  -> QuestionPlanningNode
  -> Round1QuestionNode / Round2QuestionNode / Round3QuestionNode / Round4QuestionNode 并行
  -> QuestionMergeNode
  -> END
```

实际类名和 API 以项目已有 `DeepResearchGraph` 中的 Spring AI Alibaba Graph 使用方式为准。

### 验收标准

- [ ] Graph 入口输入包含 resume/session 上下文和阶段一结果。
- [ ] 四轮题目生成节点逻辑复用同一个 `RoundQuestionNode` 类。
- [ ] 四轮生成可以并行执行。
- [ ] 任一轮异常时 Graph 整体失败。
- [ ] Merge 后必须正好 8 道题。
- [ ] Merge 后题目按轮次和题号稳定排序。

---

## 模块六：评估 Agent 与报告生成

### 目标

实现提交后的真实评估逻辑，生成总体评价、逐题反馈和 Markdown/HTML 报告。

### 文件范围

- 新建：`src/main/java/com/highway/agent/service/interview/EvaluationAgent.java`
- 新建：`src/main/java/com/highway/agent/service/interview/InterviewReportRenderer.java`
- 新建：`src/test/java/com/highway/agent/service/interview/EvaluationAgentTest.java`
- 新建：`src/test/java/com/highway/agent/service/interview/InterviewReportRendererTest.java`

### 实施步骤

- [ ] **步骤 1：实现 EvaluationAgent**

核心方法：

```java
public EvaluationResult evaluate(ResumeAnalysisResult analysisResult, InterviewPlanResult planResult, List<InterviewQuestion> questions)
```

行为：

1. 输入简历分析、面试计划、题目和用户答案。
2. 调用大模型输出结构化评价。
3. 校验总体等级合法。
4. 校验逐题反馈覆盖所有 8 道题。

- [ ] **步骤 2：实现报告渲染器**

如果大模型输出中没有稳定 HTML，使用后端渲染器从 `EvaluationResult` 生成 HTML。

方法：

```java
public String renderMarkdown(EvaluationResult result, List<InterviewQuestion> questions)
public String renderHtml(EvaluationResult result, List<InterviewQuestion> questions)
```

HTML 需要做基本转义，防止用户答案或模型内容直接造成 XSS。

- [ ] **步骤 3：编写测试**

测试覆盖：

- 等级只允许 `EXCELLENT/GOOD/PASS/FAIL`。
- 逐题反馈必须覆盖 8 道题。
- Markdown 报告包含总体等级和改进建议。
- HTML 报告包含转义后的内容。

### 验收标准

- [ ] EvaluationAgent 返回结构化 EvaluationResult。
- [ ] 总体等级合法。
- [ ] 每道题都有 feedback。
- [ ] 不产生逐题等级。
- [ ] Markdown 和 HTML 报告都能生成。
- [ ] HTML 报告对用户答案和模型文本做转义。

---

## 模块七：替换简历分析占位逻辑

### 目标

把 `InterviewResumeService.analyze` 中的占位分析替换成真实的 `ResumeAnalysisAgent + InterviewPlanningAgent` 串行调用。

### 文件范围

- 修改：`src/main/java/com/highway/agent/service/InterviewResumeService.java`

### 实施步骤

- [ ] **步骤 1：注入 Agent**

新增依赖：

```java
private final ResumeAnalysisAgent resumeAnalysisAgent;
private final InterviewPlanningAgent interviewPlanningAgent;
private final ObjectMapper objectMapper;
```

- [ ] **步骤 2：读取解析文本**

行为：

1. 如果 `contentTextPath` 为空，先解析原文件并保存文本路径。
2. 从 MinIO 读取解析文本。
3. 使用 `ResumeParsingService.truncateForAgent` 截断到 50,000 字符。

- [ ] **步骤 3：调用真实 Agent**

顺序：

```text
ResumeAnalysisAgent.analyze(resumeText)
InterviewPlanningAgent.plan(analysisResult, resumeText)
```

- [ ] **步骤 4：持久化结果**

保存：

- `targetPosition`
- `techTags`
- `analysisSummary`
- `interviewPlanSummary`
- `interviewPlanPath`
- `reportMdPath`
- `reportHtmlPath`

`InterviewPlanResult` 保存到 MinIO：

```text
interview/resume/{resumeId}/interview-plan.json
```

### 验收标准

- [ ] 成功时状态为 `ANALYZED`。
- [ ] 失败时状态为 `FAILED`。
- [ ] 任务状态同步成功或失败。
- [ ] 简历分析结果不再使用占位字符串。
- [ ] 面试计划 JSON 保存到 MinIO。
- [ ] 重复分析优先复用 `contentTextPath`。

---

## 模块八：替换题目生成占位逻辑

### 目标

把 `InterviewSessionService.start` 和 `retryGenerate` 中的占位出题替换为真实 Graph 出题。

### 文件范围

- 修改：`src/main/java/com/highway/agent/service/InterviewSessionService.java`
- 可能修改：`src/main/java/com/highway/agent/interview/graph/InterviewQuestionGraph.java`

### 实施步骤

- [ ] **步骤 1：注入 Graph**

新增依赖：

```java
private final InterviewQuestionGraph interviewQuestionGraph;
private final ObjectMapper objectMapper;
```

- [ ] **步骤 2：读取阶段一结果**

行为：

1. 从简历记录读取 `contentTextPath`、`interviewPlanPath`。
2. 从 MinIO 读取面试计划 JSON。
3. 从简历记录字段或 MinIO 读取简历分析结果。

如果发现现有表字段不足以恢复完整 `ResumeAnalysisResult`，应在本模块补充保存 `analysis_result_path` 的 schema 变更，不能把大 JSON 直接塞进多个零散字段。

- [ ] **步骤 3：调用 Graph 生成题目**

流程：

```text
QuestionPlanningAgent -> 4 RoundQuestionNode 并行 -> QuestionMergeNode
```

- [ ] **步骤 4：事务保存题目**

行为：

1. 删除当前 Session 旧题目。
2. 插入 8 道真实题。
3. 保存 `scoringPoints`，不保存标准答案。
4. Session 状态改为 `READY`。
5. 任务状态改为 `SUCCESS`。

失败时：

1. Session 状态改为 `GENERATE_FAILED`。
2. 写入 failureReason。
3. 任务状态改为 `FAILED`。

### 验收标准

- [ ] `start` 不再调用占位题生成方法。
- [ ] 成功时数据库有 8 道真实题。
- [ ] 任一轮生成失败时 Session 为 `GENERATE_FAILED`。
- [ ] 重试生成会替换旧题目。
- [ ] 题目保存事务保证不会出现部分轮次成功、部分轮次失败的脏数据。

---

## 模块九：替换提交评估占位逻辑

### 目标

把 `InterviewSessionService.submit` 和 `retryEvaluate` 中的占位评分替换为真实 EvaluationAgent。

### 文件范围

- 修改：`src/main/java/com/highway/agent/service/InterviewSessionService.java`

### 实施步骤

- [ ] **步骤 1：注入 EvaluationAgent 和报告渲染器**

新增依赖：

```java
private final EvaluationAgent evaluationAgent;
private final InterviewReportRenderer reportRenderer;
```

- [ ] **步骤 2：提交时读取上下文**

读取：

- 简历分析结果。
- 面试计划。
- 当前 Session 的 8 道题。
- 用户答案。

- [ ] **步骤 3：调用真实评估**

```java
EvaluationResult result = evaluationAgent.evaluate(analysisResult, planResult, questions);
```

- [ ] **步骤 4：持久化评估结果**

行为：

1. 更新 Session：`overallGrade`、`overallFeedback`、`improvementSuggestions`。
2. 更新每道题的 `feedback`。
3. 生成 Markdown 和 HTML 报告。
4. 保存报告到 MinIO。
5. Session 状态改为 `COMPLETED`。
6. 任务状态改为 `SUCCESS`。

失败时：

1. Session 状态改为 `EVALUATE_FAILED`。
2. 写入 failureReason。
3. 任务状态改为 `FAILED`。

### 验收标准

- [ ] `submit` 不再写占位评分。
- [ ] EvaluationAgent 覆盖全部 8 道题反馈。
- [ ] Session 保存真实总体等级和反馈。
- [ ] Question 保存真实逐题 feedback。
- [ ] Markdown 和 HTML 报告保存到 MinIO。
- [ ] 失败时 Session 为 `EVALUATE_FAILED`。
- [ ] `retryEvaluate` 能重新执行评估。

---

## 模块十：Agent 工作流整体联调

### 目标

验证真实 AI 工作流替换占位逻辑后，智能面试主流程可以端到端跑通。

### 实施步骤

- [ ] **步骤 1：运行单元测试**

```bash
mvn test
```

或如果项目添加了 Maven Wrapper：

```bash
./mvnw test
```

- [ ] **步骤 2：运行打包检查**

```bash
mvn -DskipTests package
```

- [ ] **步骤 3：人工验证主流程**

1. 上传真实或测试简历。
2. 调用简历分析。
3. 确认简历状态为 `ANALYZED`。
4. 确认 `interview-plan.json` 已保存到 MinIO。
5. 创建 Session。
6. 确认生成 4 轮 × 2 题。
7. 保存答案草稿。
8. 提交面试。
9. 确认 Session 为 `COMPLETED`。
10. 查看报告。

### 验收标准

- [ ] 全部测试通过。
- [ ] 应用可打包。
- [ ] 主流程端到端可跑通。
- [ ] 题目不是占位题。
- [ ] 评估不是占位评价。
- [ ] 报告不是占位报告。
- [ ] 失败时状态进入对应失败状态，并写入 task failureReason。

---

## 关键实现注意事项

- Agent 和 Node 不混用概念：Agent 负责 AI 能力，Node 负责编排步骤。
- 题目规划结果只在内存中传递，不落库、不存 MinIO。
- 题目保存时只保存评分要点，不保存标准答案。
- 真实报告文件仍保存到 MinIO，数据库只保存路径。
- 大段简历文本不写数据库。
- HTML 报告必须做转义。
- Graph API 以项目现有 `DeepResearchGraph` 的用法为准，避免凭空引入不兼容写法。

---

计划已保存到 `docs/superpowers/plans/2026-05-24-interview-agent-workflow-plan.md`。

后续执行时，我会从模块一开始：先补 Agent DTO，再做 Prompt，然后逐步实现 Agent 和 Graph。
