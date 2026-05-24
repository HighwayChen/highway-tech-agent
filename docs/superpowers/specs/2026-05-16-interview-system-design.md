# 智能面试系统设计文档

## 项目概述

基于现有 highway-tech-agent 项目，新增 Java 后端技术面试模拟系统。AI 扮演面试官，用户上传简历后自动生成针对性面试题目，用户作答后给出等级评价和改进建议。

## 核心需求

### 目标用户

求职者（练习面试），AI 扮演面试官角色

### 面试领域

Java 后端技术面试

### 面试流程

1. **上传简历**：用户上传 PDF/Word 格式简历
2. **AI 简历评估**：用户手动触发，AI 异步评估简历，生成评估报告
3. **AI 生成题目**：用户点击"开始面试"，AI 根据面试计划生成题目
4. **用户作答**：用户在页面上看到所有题目并逐题作答
5. **统一提交**：所有轮次一次性提交
6. **AI 评分**：逐轮给出等级评价 + 总体评价 + 改进建议

### 题目结构

- 固定 4 个轮次，每轮 2 题，总计 8 题
- 轮次结构：Java 基础与语言机制 → 主技术栈与框架能力 → 简历项目深挖 → 工程素养与问题排查
- AI 针对简历中的项目经验和技术细节出题
- 题目生成时附带评分要点（踩分点），不生成参考答案或完整标准答案

### 评分机制

- 整场面试使用等级评价制：优秀(EXCELLENT) / 良好(GOOD) / 合格(PASS) / 不合格(FAIL)
- 每题给出文字反馈，不做题目级等级落库
- 轮次总结保存在完整 Markdown/HTML 报告中，不做结构化落库
- 总体评价 + 改进建议写入 interview_session 摘要字段

### 关键特性

- 支持同一简历多次面试（重新生成不同题目）
- 面试历史记录持久化，支持查看
- 不使用流式输出，全部同步返回
- 简历评估报告和面试报告生成 Markdown + HTML，存储到 MinIO

## Agent 工作机制

### 5 个 Agent 职责划分

| Agent | 类型 | 输入 | 输出 | 工具 | 输出转换 |
|-------|------|------|------|------|---------|
| ResumeAnalysisAgent | ReAct Agent | 简历文本 | 结构化简历摘要 + 完整简历评估报告 | Tavily 搜索 | BeanOutputConverter |
| InterviewPlanningAgent | 纯 Prompt Agent | 简历文本 + 简历摘要 | 固定 4 轮、每轮 2 题的面试计划 | 无 | BeanOutputConverter |
| QuestionPlanningAgent | 纯 Prompt Agent | 完整面试计划 + 简历评估摘要 + 简历文本 | 4 轮 8 题的题目规划 | 无 | BeanOutputConverter |
| QuestionGenerationAgent | ReAct Agent | 题目规划 + 当前轮次 + 完整面试计划 + 简历评估摘要 + 简历文本 | 当前轮次 2 道题 + 每题评分要点 | Tavily 搜索 | BeanOutputConverter |
| EvaluationAgent | ReAct Agent | 题目 + 回答 + 评分要点 + 简历评估报告 | 总评摘要 + 8 题反馈 + 完整 Markdown 报告 | Tavily 搜索 | BeanOutputConverter |

### 执行流程

```
上传简历（手动触发评估）
    │
    ▼
阶段一（串行，保证面试计划使用简历分析摘要）
┌─────────────────────────────────┐
│   ResumeAnalysisAgent           │
│   输入: 简历文本                 │
│   输出: 简历分析摘要 + 完整报告   │
└─────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────┐
│   InterviewPlanningAgent        │
│   输入: 简历文本 + 简历分析摘要   │
│   输出: 固定 4 轮面试计划         │
└─────────────────────────────────┘
              │
              ▼
        简历评估完成，面试计划已生成

用户点击"开始面试"
              │
              ▼
阶段二（使用 Spring AI Alibaba 并行编排生成题目）
┌─────────────────────────────────┐
│   QuestionPlanningAgent         │
│   输出: 4 轮 8 题的题目规划       │
└─────────────────────────────────┘
              │
              ▼
┌──────────────┬──────────────┬──────────────┬──────────────┐
│ Round1 Node  │ Round2 Node  │ Round3 Node  │ Round4 Node  │
│ 调用          │ 调用          │ 调用          │ 调用          │
│QuestionGenerationAgent 并行生成各轮 2 题 + 评分要点       │
└──────────────┴──────────────┴──────────────┴──────────────┘
              │
              ▼
┌─────────────────────────────────┐
│   QuestionMergeNode             │
│   合并并校验 8 题，不调用 LLM     │
└─────────────────────────────────┘
              │
              ▼
        用户作答（前端）
              │
              ▼
阶段三（评分）
┌─────────────────────────────────┐
│   EvaluationAgent               │
│   输出: 总评摘要 + 8题反馈 +报告  │
└─────────────────────────────────┘
```

### 关键设计决策

- **框架编排**：目标使用 Spring AI Alibaba 内置 Graph/Workflow 能力编排；编码时确认当前版本 API，若不可用再降级为 Java 并发实现
- **阶段一串行**：ResumeAnalysisAgent 先输出简历分析摘要，InterviewPlanningAgent 再基于简历文本 + 摘要生成面试计划，提高计划质量
- **阶段二并行**：QuestionPlanningAgent 先生成题目规划，随后 4 个 Round 节点并行调用 QuestionGenerationAgent 生成各轮题目
- **结构化输出**：统一用 Spring AI BeanOutputConverter 映射为 Java 对象
- **非流式**：所有 AI 调用同步返回
- **答案策略**：生成题目时附带评分要点/踩分点，评估时按要点评分，不生成参考答案或完整标准答案
- **固定题量**：MVP 固定 4 轮、每轮 2 题，共 8 题；不新增 question_type 字段，由 round_number / round_name 表达题目类型
- **题目规划不持久化**：QuestionPlanningAgent 输出只作为工作流中间结果传递给 Round 节点，不落库、不存 MinIO

### Agent 结构化输出

#### ResumeAnalysisAgent

输出分为两层：结构化摘要用于落库和后续 Agent，完整报告用于生成 Markdown + HTML 并存 MinIO。

结构化摘要字段：

```json
{
  "targetPosition": "Java后端开发",
  "techTags": ["Java", "Spring Boot", "MySQL", "Redis"],
  "summary": "候选人具备...",
  "strengths": ["..."],
  "weaknesses": ["..."],
  "projectHighlights": ["..."],
  "verificationPoints": ["..."],
  "suggestions": ["..."]
}
```

完整简历评估报告章节：总体评价、技术标签与岗位匹配、项目经历亮点、技能短板、面试官重点验证点、可能追问方向、简历优化建议。

#### InterviewPlanningAgent

输出固定 4 轮面试计划，每轮 2 题：

1. Java 基础与语言机制
2. 主技术栈与框架能力
3. 简历项目深挖
4. 工程素养与问题排查

`interview_plan_summary` 只保存文本摘要用于前端展示，完整面试计划存 MinIO，题目生成时读取完整面试计划文件。

#### QuestionPlanningAgent

输出 4 轮 8 题的题目规划，只描述每题考察方向和去重边界，不生成完整题干。规划结果只在工作流内存中传递，不落库、不存 MinIO。

```json
{
  "items": [
    {
      "roundNumber": 1,
      "questionNumber": 1,
      "focus": "HashMap 底层结构与扩容机制",
      "avoidOverlapWith": ["并发集合", "Spring Bean 生命周期"]
    }
  ]
}
```

#### QuestionGenerationAgent

输出当前轮次 2 道题目。代码上复用一个基础 Agent，Prompt 按轮次区分：Java 基础与语言机制、主技术栈与框架能力、简历项目深挖、工程素养与问题排查。每题包含轮次、难度、题目序号、题目内容、评分要点，不生成参考答案。

```json
{
  "roundNumber": 1,
  "roundName": "Java基础与语言机制",
  "difficulty": "基础",
  "questionNumber": 1,
  "content": "请说明 HashMap 在 JDK 1.8 中的底层结构。",
  "scoringPoints": [
    "能说明数组+链表+红黑树结构",
    "能说明树化阈值和扩容机制",
    "能说明哈希冲突处理"
  ]
}
```

#### EvaluationAgent

输出结构化评分结果和完整 Markdown 报告内容。结构化结果用于落库，Markdown 报告用于生成 HTML 并存 MinIO。

```json
{
  "overallGrade": "GOOD",
  "overallFeedback": "整体具备 Java 后端岗位基础...",
  "improvementSuggestions": ["补强 JVM...", "加强项目表达..."],
  "questionFeedbacks": [
    { "questionId": 101, "feedback": "回答覆盖了数组和链表，但没有说明红黑树树化条件。" }
  ]
}
```

`questionFeedbacks` 必须覆盖全部 8 道题。未作答题目也要输出反馈，例如“未作答，无法评估该题掌握情况”。

## 数据模型

### interview_resume（简历）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| file_name | VARCHAR(255) | 原始文件名 |
| file_path | VARCHAR(512) | 原始 PDF/Word 文件 MinIO 路径 |
| content_text_path | VARCHAR(512) | 解析后的纯文本 .txt 文件 MinIO 路径 |
| status | VARCHAR(32) | UPLOADED / ANALYZING / ANALYZED / FAILED |
| tech_tags | TEXT | 技术标签 JSON 字符串 |
| target_position | VARCHAR(128) | 目标岗位/方向 |
| analysis_summary | TEXT | 简历评估摘要 |
| interview_plan_summary | TEXT | 面试计划文本摘要 |
| interview_plan_path | VARCHAR(512) | 完整面试计划文件 MinIO 路径 |
| report_md_path | VARCHAR(512) | 评估报告 Markdown 文件 MinIO 路径 |
| report_html_path | VARCHAR(512) | 评估报告 HTML 文件 MinIO 路径 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### interview_session（面试会话）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| resume_id | BIGINT FK | 关联简历(interview_resume.id) |
| status | VARCHAR(32) | GENERATING / READY / ANSWERING / EVALUATING / COMPLETED / GENERATE_FAILED / EVALUATE_FAILED |
| interview_plan_path | VARCHAR(512) | 面试计划文件 MinIO 路径 |
| overall_grade | VARCHAR(16) | 整场面试总等级：EXCELLENT / GOOD / PASS / FAIL |
| overall_feedback | TEXT | 总体评价摘要 |
| improvement_suggestions | TEXT | 改进建议（JSON 或文本） |
| report_md_path | VARCHAR(512) | 面试报告 Markdown 文件 MinIO 路径 |
| report_html_path | VARCHAR(512) | 面试报告 HTML 文件 MinIO 路径 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### interview_question（面试题目）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| session_id | BIGINT FK | 关联会话(interview_session.id) |
| round_number | INT | 轮次序号(1,2,3,4) |
| round_name | VARCHAR(128) | 轮次名称(如"Java基础") |
| difficulty | VARCHAR(32) | 难度等级 |
| question_number | INT | 题目序号 |
| content | TEXT | 题目内容 |
| scoring_points | TEXT | 评分要点(JSON 字符串) |
| user_answer | TEXT | 用户回答/草稿答案 |
| feedback | TEXT | 题目级文字反馈 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### interview_task（异步任务）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT PK | 主键 |
| biz_type | VARCHAR(32) NOT NULL | RESUME / SESSION |
| biz_id | BIGINT NOT NULL | 关联业务对象 ID |
| task_type | VARCHAR(64) NOT NULL | RESUME_ANALYZE / QUESTION_GENERATE / SESSION_EVALUATE |
| status | VARCHAR(32) NOT NULL | PENDING / RUNNING / SUCCESS / FAILED |
| retry_count | INT NOT NULL DEFAULT 0 | 第几次尝试（0=首次，1=第一次重试） |
| failure_reason | TEXT | 失败原因 |
| input_summary | TEXT | 输入摘要 |
| output_summary | TEXT | 输出摘要 |
| started_at | DATETIME | 开始时间 |
| finished_at | DATETIME | 结束时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：`idx_biz (biz_type, biz_id)`、`idx_task_status (task_type, status)`

### 表关系

```
interview_resume 1:N interview_session 1:N interview_question
interview_task 通过 biz_type + biz_id 关联 interview_resume 或 interview_session
```

### 设计说明

- 去掉了 interview_round 表，轮次信息（round_number, round_name, difficulty）冗余到 question 表中，因为轮次固定 3-4 轮，无需独立表
- 原始简历文件存 MinIO，interview_resume.file_path 只保存原始 PDF/Word 路径
- 上传限制：仅允许 PDF / DOC / DOCX，最大 10MB
- 解析后的纯文本不存数据库大字段，保存为 MinIO `.txt` 文件，interview_resume.content_text_path 只保存路径
- 简历解析使用 Apache Tika；上传简历时只保存原始文件并置为 UPLOADED；第一次分析简历时，如果 content_text_path 为空，则解析原文件生成 `.txt` 并写入路径；后续重分析默认复用该 `.txt`
- 解析文本尽量完整保存到 MinIO；Agent 输入时按 MVP 规则取前 50,000 字符，超出时在 Prompt 中说明已截断
- 解析失败规则：Tika 抛异常、清洗后文本为空、或清洗后少于 100 字符，均视为失败，写入 interview_task.failure_reason，并将简历状态置为 FAILED
- 简历评估报告和面试报告的完整内容存为 MinIO 文件（MD + HTML），表里只存文件路径，前端通过后端代理接口获取 HTML 内容，不暴露 MinIO 路径
- 面试计划同理，完整内容存 MinIO 文件，表里只存路径；interview_plan_summary 只保存文本摘要用于前端展示
- 简历表 status：UPLOADED / ANALYZING / ANALYZED / FAILED
- 面试会话 status 细化为：GENERATING / READY / ANSWERING / EVALUATING / COMPLETED / GENERATE_FAILED / EVALUATE_FAILED
- 面试总评摘要冗余保存在 interview_session：overall_grade / overall_feedback / improvement_suggestions，便于列表和报告总览快速展示
- 题目级不保存等级，只保存 feedback 文字反馈；轮次级评价不结构化落库，完整轮次总结由 HTML/Markdown 报告承载
- scoring_points 作为 JSON 字符串保存在 interview_question，不拆分评分点表
- 新增 interview_task 统一管理异步任务（简历分析、题目生成、答案评估），每次重试新增一条 task 记录，保留完整执行历史
- 前端不直接查询任务表，Resume/Session 详情接口聚合返回 `latestTask`

## API 接口

### 简历相关

| 方法 | 路径 | 说明 | 请求 | 响应 |
|------|------|------|------|------|
| POST | `/api/interview/resume/upload` | 上传简历(PDF/Word) | multipart/form-data | `{ id, fileName, status: "UPLOADED" }` |
| POST | `/api/interview/resume/{id}/analyze` | 手动触发AI评估，异步执行 | - | `{ id, status: "ANALYZING", latestTask }` |
| GET | `/api/interview/resume/{id}` | 获取简历信息、评估状态和最新任务 | - | `{ id, fileName, status, hasReport, reportGeneratedAt, latestTask }` |
| GET | `/api/interview/resume/list` | 简历分页列表，不嵌套 Session | `page, pageSize` | `{ records: [{ id, fileName, status, createdAt, latestTask }], total, page, pageSize }` |
| GET | `/api/interview/resume/{id}/report` | 获取简历评估报告 HTML（后端代理 MinIO） | - | HTML报告内容 |

### 面试会话

| 方法 | 路径 | 说明 | 请求 | 响应 |
|------|------|------|------|------|
| POST | `/api/interview/session/start` | 开始面试，创建 Session 并异步生成题目 | `{ resumeId }` | `{ id, resumeId, status: "GENERATING", latestTask }` |
| GET | `/api/interview/session/{id}` | 获取会话详情、状态和最新任务 | - | `{ id, resumeId, status, answeredCount, totalCount, hasReport, reportGeneratedAt, latestTask }` |
| GET | `/api/interview/session/list` | 查询某份简历最近的面试会话，MVP 仅支持 resumeId + limit | `resumeId, limit` | `[{ id, resumeId, status, createdAt, answeredCount, totalCount, overallGrade, latestTask }]` |
| POST | `/api/interview/session/{id}/answers/save` | 批量保存答题草稿，不触发评分 | `{ answers: [{ questionId, answer }] }` | `{ id, status: "ANSWERING", answeredCount, totalCount, updatedAt }` |
| POST | `/api/interview/session/{id}/submit` | 提交本次面试，保存答案并异步评分 | `{ answers: [{ questionId, answer }] }` | `{ id, status: "EVALUATING", latestTask }` |
| POST | `/api/interview/session/{id}/retry-generate` | 在原 Session 上重试题目生成 | - | `{ id, status: "GENERATING", latestTask }` |
| POST | `/api/interview/session/{id}/retry-evaluate` | 在原 Session 上重试答案评估 | - | `{ id, status: "EVALUATING", latestTask }` |
| GET | `/api/interview/session/{id}/report` | 获取面试报告 HTML（后端代理 MinIO） | - | HTML报告内容 |

### 题目相关

| 方法 | 路径 | 说明 | 响应 |
|------|------|------|------|
| GET | `/api/interview/session/{id}/questions` | 获取该会话所有题目和已保存答案，按轮次分组 | `{ rounds: [{ roundNumber, roundName, difficulty, questions: [{ id, content, userAnswer }] }] }` |

### 状态流转

**简历**：UPLOADED → (用户点击分析) → ANALYZING → ANALYZED / FAILED

**面试会话**：

- 题目生成：GENERATING → READY / GENERATE_FAILED
- 答题草稿：READY → (保存草稿) → ANSWERING
- 提交评估：READY / ANSWERING → EVALUATING → COMPLETED / EVALUATE_FAILED
- 生成失败重试：GENERATE_FAILED → GENERATING → READY / GENERATE_FAILED
- 评估失败重试：EVALUATE_FAILED → EVALUATING → COMPLETED / EVALUATE_FAILED

提交接口幂等规则：

- READY / ANSWERING：保存本次答案，状态改为 EVALUATING，创建 SESSION_EVALUATE 任务。
- EVALUATING：不修改答案，不重复触发评分，直接返回当前状态。
- COMPLETED：不修改答案，返回当前状态和报告入口。
- GENERATE_FAILED / EVALUATE_FAILED：不修改答案，返回失败状态，用户需走对应重试接口。

草稿保存规则：

- 前端通过 `POST /api/interview/session/{id}/answers/save` 批量保存答案到 `interview_question.user_answer`。
- 草稿保存不触发评分。
- 后端允许未答题提交，未答题按空答案参与评分。

题目生成与重试规则：

- 阶段二任一 Round 节点失败、题目数量不等于 8、重复校验失败、评分要点缺失，都视为题目生成失败。
- 题目生成失败时不写入部分题目，Session 置为 `GENERATE_FAILED`，`interview_task.failure_reason` 记录原因。
- 用户点击“重新生成题目”时，在原 Session 上整体重跑：QuestionPlanningAgent + 4 个 Round 节点 + QuestionMergeNode。
- 重试生成成功后，在数据库事务中删除该 Session 旧题目并写入新的 8 道题，再将 Session 置为 `READY`。
- 只有 `GENERATE_FAILED` 状态允许调用 `retry-generate`；`READY/ANSWERING/EVALUATING/COMPLETED/EVALUATE_FAILED` 不允许在原 Session 上重新生成题目。
- 用户想换一套题时，需要从简历卡片创建新 Session。

评估失败重试规则：

- `EVALUATE_FAILED` 只允许基于已提交答案重新评估，不允许修改答案。
- `retry-evaluate` 不接收新的答案参数，直接读取 `interview_question.user_answer`。
- 成功后写入题目反馈、总评摘要和报告路径，并将 Session 置为 `COMPLETED`。
- 失败后 Session 仍为 `EVALUATE_FAILED`，并新增一条失败的 `interview_task` 记录。

列表加载规则：

- 简历列表分页查询：`GET /api/interview/resume/list?page=1&pageSize=10`。
- 简历列表响应不嵌套 Session，保持接口轻量。
- 前端渲染每张简历卡片时，单独调用 `GET /api/interview/session/list?resumeId={id}&limit=3` 获取最近 3 个 Session。
- MVP 不提供 Session 历史分页；后续如需“查看更多历史”，再扩展 `session/list` 的 page/pageSize。

## 前端页面（简历管理页已确认，其余待设计）

### 简历管理页 `interview.html`

**页面结构：**

```
┌─────────────────────────────────────────────┐
│  高速公路技术面试                            │
├─────────────────────────────────────────────┤
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │  拖拽上传简历 (PDF/Word)            │    │
│  │  或点击选择文件                      │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  简历列表                                   │
│  ┌─────────────────────────────────────┐    │
│  │ 张三_Java开发.pdf                    │    │
│  │ 状态: 已评估  [查看报告] [开始面试]   │    │
│  ├─────────────────────────────────────┤    │
│  │ 李四_后端开发.docx                   │    │
│  │ 状态: 分析中...                      │    │
│  ├─────────────────────────────────────┤    │
│  │ 王五_Spring开发.pdf                  │    │
│  │ 状态: 已上传  [分析简历]              │    │
│  └─────────────────────────────────────┘    │
│                                             │
└─────────────────────────────────────────────┘
```

**功能点：**
- 上传区域：拖拽或点击上传 PDF/Word
- 简历列表：显示文件名、状态、操作按钮
- 状态标识：已上传(灰) → 分析中(蓝/转圈) → 已评估(绿) → 失败(红)
- 操作：查看报告、开始面试、分析简历、删除
- 单文件 `interview.html`，内联 CSS，原生 JS，不引入框架

### 其他页面（已在 2026-05-23 讨论确认）

详细设计见 `docs/superpowers/specs/2026-05-23-interview-frontend-design.md`。

- 面试答题页：从具体 Session 进入，左侧轮次导航，右侧当前轮次题目与答案框。
- 报告查看页：从 `COMPLETED` Session 进入，总览优先，支持轮次/题目反馈展开和完整 HTML 报告入口。
- 面试历史：不做独立历史页，最近 Session 聚合展示在简历卡片下。

## 待讨论事项

- [x] Prompt 模板设计（4个Agent的职责、结构化输出、固定题量、报告输出）
- [x] 前端其他页面设计（答题页、报告页、面试列表页）
- [x] Spring AI Alibaba 并行编排策略（目标使用框架 Graph/Workflow，阶段二 4 轮并行，编码时确认具体 API）
- [x] PDF/Word 解析方案（Apache Tika、10MB、解析文本 MinIO 缓存、Agent 输入截断）
- [x] 面试会话状态流转的边界条件（异步执行、草稿保存、幂等提交、失败重试、任务表）
- [x] 列表接口分页策略（简历列表分页，Session 最近 3 条按需查询）
