# 智能面试后端基础模块化实施计划

> **给执行型 Agent 的要求：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按模块逐项执行本计划。每个模块完成后先对照“验收标准”检查，再进入下一个模块。

**目标：** 按功能模块实现智能面试系统后端基础能力：数据库与依赖、简历管理、简历解析与评估占位、面试会话、题目与答案、报告代理、REST API 和整体验证。

**架构：** 采用“模块先闭环、再串联”的方式推进。每个模块都要形成可编译、可测试、可验收的小闭环；Controller 只调用 Service，Service 负责业务状态流转和持久化，Mapper 只负责数据访问。本计划只实现非 AI 后端基础，真实 Agent 工作流、Prompt 和前端页面放到后续计划。

**技术栈：** Java 21、Spring Boot 3.5.7、MyBatis-Plus 3.5.12、MySQL/H2、MinIO、Apache Tika、Lombok、JUnit 5、MockMvc。

---

## 模块拆分总览

1. **模块一：基础依赖与数据库结构**
   - 交付：Tika 依赖、4 张面试表、实体、枚举、Mapper。
   - 验收：项目可编译，schema 可加载，Mapper 可被 Spring 扫描。

2. **模块二：MinIO 文件能力与简历解析**
   - 交付：二进制上传/读取、Tika 解析、解析文本保存到 MinIO。
   - 验收：能上传原始简历，能解析文本，能拒绝空文本/过短文本。

3. **模块三：异步任务记录能力**
   - 交付：任务创建、运行中、成功、失败、查询最新任务、计算重试次数。
   - 验收：任意业务对象都能通过 `biz_type + biz_id + task_type` 查询最新任务。

4. **模块四：简历管理模块**
   - 交付：上传简历、分页列表、详情、触发分析、简历报告代理读取。
   - 验收：简历状态能按 `UPLOADED -> ANALYZING -> ANALYZED/FAILED` 流转。

5. **模块五：面试会话与题目模块**
   - 交付：开始面试、生成 4 轮 × 2 题占位题、Session 列表、详情、题目分组查询。
   - 验收：同一简历可创建多个 Session；Session 与题目关系正确。

6. **模块六：答案草稿、提交与报告模块**
   - 交付：保存草稿、提交面试、占位评分报告、失败重试 API 骨架。
   - 验收：答案能持久化；提交后不可再编辑；报告可通过后端读取。

7. **模块七：REST API 模块**
   - 交付：`/api/interview/**` 全部后端接口。
   - 验收：Controller 契约测试通过，Controller 不直接访问 Mapper/MinIO。

8. **模块八：整体联调验证**
   - 交付：全量测试、打包验证、接口主流程人工验证记录。
   - 验收：完整主流程可跑通：上传简历 → 分析 → 开始面试 → 查看题目 → 保存答案 → 提交 → 查看报告。

---

## 模块一：基础依赖与数据库结构

### 目标

先把数据库结构和 Java 持久化对象建立起来，后续模块都基于这些实体和 Mapper 开发。

### 文件范围

- 修改：`pom.xml`
- 修改：`src/main/resources/schema.sql`
- 新建：`src/main/java/com/highway/agent/model/InterviewEnums.java`
- 新建：`src/main/java/com/highway/agent/model/InterviewResume.java`
- 新建：`src/main/java/com/highway/agent/model/InterviewSession.java`
- 新建：`src/main/java/com/highway/agent/model/InterviewQuestion.java`
- 新建：`src/main/java/com/highway/agent/model/InterviewTask.java`
- 新建：`src/main/java/com/highway/agent/memory/InterviewResumeMapper.java`
- 新建：`src/main/java/com/highway/agent/memory/InterviewSessionMapper.java`
- 新建：`src/main/java/com/highway/agent/memory/InterviewQuestionMapper.java`
- 新建：`src/main/java/com/highway/agent/memory/InterviewTaskMapper.java`

### 实施步骤

- [ ] **步骤 1：增加 Tika 依赖**

在 `pom.xml` 的 `<dependencies>` 中加入：

```xml
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-core</artifactId>
    <version>3.2.1</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parser-microsoft-module</artifactId>
    <version>3.2.1</version>
</dependency>
<dependency>
    <groupId>org.apache.tika</groupId>
    <artifactId>tika-parser-pdf-module</artifactId>
    <version>3.2.1</version>
</dependency>
```

- [ ] **步骤 2：追加数据库表**

在 `src/main/resources/schema.sql` 后追加：

```sql
CREATE TABLE IF NOT EXISTS interview_resume (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    content_text_path VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    tech_tags VARCHAR(512),
    target_position VARCHAR(128),
    analysis_summary TEXT,
    interview_plan_summary TEXT,
    interview_plan_path VARCHAR(512),
    report_md_path VARCHAR(512),
    report_html_path VARCHAR(512),
    failure_reason VARCHAR(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS interview_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resume_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    interview_plan_path VARCHAR(512),
    overall_grade VARCHAR(32),
    overall_feedback TEXT,
    improvement_suggestions TEXT,
    report_md_path VARCHAR(512),
    report_html_path VARCHAR(512),
    failure_reason VARCHAR(1024),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_resume_id (resume_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);

CREATE TABLE IF NOT EXISTS interview_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    round_number INT NOT NULL,
    round_name VARCHAR(128) NOT NULL,
    difficulty VARCHAR(64) NOT NULL,
    question_number INT NOT NULL,
    content TEXT NOT NULL,
    scoring_points TEXT,
    user_answer TEXT,
    feedback TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_round_number (round_number),
    UNIQUE KEY uk_session_question (session_id, round_number, question_number)
);

CREATE TABLE IF NOT EXISTS interview_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    biz_type VARCHAR(32) NOT NULL,
    biz_id BIGINT NOT NULL,
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    failure_reason VARCHAR(1024),
    input_summary TEXT,
    output_summary TEXT,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_biz (biz_type, biz_id),
    INDEX idx_task_type (task_type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
);
```

- [ ] **步骤 3：创建枚举类**

创建 `InterviewEnums.java`：

```java
package com.highway.agent.model;

public final class InterviewEnums {

    private InterviewEnums() {
    }

    public enum ResumeStatus {
        UPLOADED,
        ANALYZING,
        ANALYZED,
        FAILED
    }

    public enum SessionStatus {
        GENERATING,
        READY,
        ANSWERING,
        EVALUATING,
        COMPLETED,
        GENERATE_FAILED,
        EVALUATE_FAILED
    }

    public enum TaskStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED
    }

    public enum TaskType {
        RESUME_ANALYZE,
        QUESTION_GENERATE,
        SESSION_EVALUATE
    }

    public enum BizType {
        RESUME,
        SESSION
    }
}
```

- [ ] **步骤 4：创建 4 个实体类**

实体类都放在 `src/main/java/com/highway/agent/model/`，使用 `@Data`、`@TableName`、`@TableId(type = IdType.AUTO)`。

`InterviewResume` 字段：

```java
private Long id;
private String fileName;
private String filePath;
private String contentTextPath;
private String status;
private String techTags;
private String targetPosition;
private String analysisSummary;
private String interviewPlanSummary;
private String interviewPlanPath;
private String reportMdPath;
private String reportHtmlPath;
private String failureReason;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

`InterviewSession` 字段：

```java
private Long id;
private Long resumeId;
private String status;
private String interviewPlanPath;
private String overallGrade;
private String overallFeedback;
private String improvementSuggestions;
private String reportMdPath;
private String reportHtmlPath;
private String failureReason;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

`InterviewQuestion` 字段：

```java
private Long id;
private Long sessionId;
private Integer roundNumber;
private String roundName;
private String difficulty;
private Integer questionNumber;
private String content;
private String scoringPoints;
private String userAnswer;
private String feedback;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

`InterviewTask` 字段：

```java
private Long id;
private String bizType;
private Long bizId;
private String taskType;
private String status;
private Integer retryCount;
private String failureReason;
private String inputSummary;
private String outputSummary;
private LocalDateTime startedAt;
private LocalDateTime finishedAt;
private LocalDateTime createdAt;
private LocalDateTime updatedAt;
```

- [ ] **步骤 5：创建 4 个 Mapper**

每个 Mapper 放在 `src/main/java/com/highway/agent/memory/`，继承 `BaseMapper<T>`：

```java
@Mapper
public interface InterviewResumeMapper extends BaseMapper<InterviewResume> {
}
```

其余三个分别为：

```java
@Mapper
public interface InterviewSessionMapper extends BaseMapper<InterviewSession> {
}
```

```java
@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
}
```

```java
@Mapper
public interface InterviewTaskMapper extends BaseMapper<InterviewTask> {
}
```

- [ ] **步骤 6：编译验证**

运行：

```bash
./mvnw -q -DskipTests compile
```

预期：编译成功。

### 验收标准

- [ ] `pom.xml` 中存在 Tika 依赖。
- [ ] `schema.sql` 中存在 `interview_resume`、`interview_session`、`interview_question`、`interview_task`。
- [ ] 4 张表都有 `id`、`created_at`、`updated_at`。
- [ ] `interview_question` 有 `session_id + round_number + question_number` 唯一约束。
- [ ] 4 个实体类字段与表字段一一对应。
- [ ] 4 个 Mapper 均使用 `@Mapper` 并继承 `BaseMapper`。
- [ ] `./mvnw -q -DskipTests compile` 通过。

---

## 模块二：MinIO 文件能力与简历解析

### 目标

让系统可以保存原始简历文件，并把 MinIO 中的 PDF/DOC/DOCX 解析成文本，再把解析后的 `.txt` 存回 MinIO。

### 文件范围

- 修改：`src/main/java/com/highway/agent/service/MinioService.java`
- 新建：`src/main/java/com/highway/agent/service/ResumeParsingService.java`
- 新建：`src/test/java/com/highway/agent/service/ResumeParsingServiceTest.java`

### 实施步骤

- [ ] **步骤 1：扩展 MinIOService 二进制能力**

在 `MinioService` 中新增：

```java
public void putObject(String key, InputStream inputStream, long size, String contentType) {
    try {
        minioClient.putObject(
                PutObjectArgs.builder()
                        .bucket(minioConfig.getBucket())
                        .object(key)
                        .stream(inputStream, size, -1)
                        .contentType(contentType)
                        .build()
        );
    } catch (Exception e) {
        throw new RuntimeException("Failed to upload object to MinIO: " + key, e);
    }
}

public byte[] getObjectBytes(String key) {
    try (InputStream inputStream = minioClient.getObject(
            GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(key)
                    .build());
         ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        inputStream.transferTo(outputStream);
        return outputStream.toByteArray();
    } catch (Exception e) {
        throw new RuntimeException("Failed to read object from MinIO: " + key, e);
    }
}
```

- [ ] **步骤 2：编写简历解析测试**

测试覆盖：

- 能从 `MinioService.getObjectBytes()` 读取内容。
- 能调用 `MinioService.putObject(textPath, cleanedText)` 保存解析文本。
- 少于 100 字符的解析文本会抛出 `IllegalArgumentException`。
- `truncateForAgent` 会把输入截断到 50,000 字符。

- [ ] **步骤 3：实现 ResumeParsingService**

核心行为：

```java
private static final int MIN_TEXT_LENGTH = 100;
private static final int AGENT_INPUT_LIMIT = 50_000;
```

方法：

```java
public String parseAndSaveText(Long resumeId, String filePath, String fileName)
public String truncateForAgent(String text)
```

`parseAndSaveText` 行为：

1. 从 MinIO 读取原始文件 bytes。
2. 使用 `new Tika().parseToString(new ByteArrayInputStream(content))` 解析。
3. 使用 `text.replaceAll("\\s+", " ").trim()` 清洗文本。
4. 文本长度小于 100 时抛出 `IllegalArgumentException`。
5. 保存到 `interview/resume/{resumeId}/content.txt`。
6. 返回该 textPath。

- [ ] **步骤 4：运行测试**

运行：

```bash
./mvnw -q -Dtest=ResumeParsingServiceTest test
```

预期：测试通过。

### 验收标准

- [ ] `MinioService` 同时支持字符串对象和二进制对象。
- [ ] 原有 `putObject(String, String)` 和 `getObject(String)` 不被破坏。
- [ ] `ResumeParsingService.parseAndSaveText` 返回 `interview/resume/{id}/content.txt`。
- [ ] 解析文本会被保存到 MinIO，而不是写入数据库字段。
- [ ] 解析文本少于 100 字符时失败。
- [ ] Agent 输入截断逻辑固定为 50,000 字符。
- [ ] `./mvnw -q -Dtest=ResumeParsingServiceTest test` 通过。

---

## 模块三：异步任务记录能力

### 目标

实现通用任务记录，支持简历分析、题目生成、会话评估这三类任务的状态追踪和重试计数。

### 文件范围

- 新建：`src/main/java/com/highway/agent/service/InterviewTaskService.java`

### 实施步骤

- [ ] **步骤 1：实现任务创建**

方法：

```java
public InterviewTask createTask(BizType bizType, Long bizId, TaskType taskType, int retryCount, String inputSummary)
```

行为：创建 `PENDING` 状态任务，写入 `bizType`、`bizId`、`taskType`、`retryCount`、`inputSummary`。

- [ ] **步骤 2：实现任务状态更新**

方法：

```java
public void markRunning(Long taskId)
public void markSuccess(Long taskId, String outputSummary)
public void markFailed(Long taskId, String failureReason)
```

状态行为：

- `markRunning`：状态改为 `RUNNING`，写入 `startedAt`。
- `markSuccess`：状态改为 `SUCCESS`，写入 `outputSummary` 和 `finishedAt`。
- `markFailed`：状态改为 `FAILED`，写入 `failureReason` 和 `finishedAt`。

- [ ] **步骤 3：实现最新任务查询和重试次数计算**

方法：

```java
public TaskSummaryResponse latestTask(BizType bizType, Long bizId, TaskType taskType)
public int nextRetryCount(BizType bizType, Long bizId, TaskType taskType)
```

查询条件：

```java
.eq(InterviewTask::getBizType, bizType.name())
.eq(InterviewTask::getBizId, bizId)
.eq(InterviewTask::getTaskType, taskType.name())
.orderByDesc(InterviewTask::getCreatedAt)
.last("LIMIT 1")
```

### 验收标准

- [ ] 每次异步动作都会创建新的 `interview_task` 记录。
- [ ] retryCount 通过同一 `biz_type + biz_id + task_type` 的历史记录数计算。
- [ ] latestTask 能返回最新一条任务摘要。
- [ ] 任务状态只使用 `PENDING/RUNNING/SUCCESS/FAILED`。
- [ ] `./mvnw -q -DskipTests compile` 通过。

---

## 模块四：简历管理模块

### 目标

实现简历的上传、列表、详情、分析触发和简历评估报告读取。这个模块要先用占位评估内容跑通状态流转，后续 Agent 计划再替换真实分析逻辑。

### 文件范围

- 新建：`src/main/java/com/highway/agent/model/interview/TaskSummaryResponse.java`
- 新建：`src/main/java/com/highway/agent/model/interview/ResumeUploadResponse.java`
- 新建：`src/main/java/com/highway/agent/model/interview/ResumeDetailResponse.java`
- 新建：`src/main/java/com/highway/agent/model/interview/ResumeListResponse.java`
- 新建：`src/main/java/com/highway/agent/model/interview/ReportResponse.java`
- 新建：`src/main/java/com/highway/agent/service/InterviewResumeService.java`

### 实施步骤

- [ ] **步骤 1：创建简历相关 DTO**

DTO 包路径：`src/main/java/com/highway/agent/model/interview/`。

必须包含：

```java
public record ResumeUploadResponse(Long id, String fileName, String status) {}
```

```java
public record ResumeListResponse(long page, long pageSize, long total, List<Item> items) {
    public record Item(
            Long id,
            String fileName,
            String status,
            String techTags,
            String targetPosition,
            String analysisSummary,
            String failureReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
```

```java
public record ResumeDetailResponse(
        Long id,
        String fileName,
        String status,
        String techTags,
        String targetPosition,
        String analysisSummary,
        String interviewPlanSummary,
        String failureReason,
        TaskSummaryResponse latestTask,
        List<SessionSummary> recentSessions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record SessionSummary(
            Long id,
            String status,
            String overallGrade,
            Integer answeredCount,
            Integer totalCount,
            String failureReason,
            LocalDateTime createdAt
    ) {}
}
```

```java
public record ReportResponse(Long id, String type, String content) {}
```

- [ ] **步骤 2：实现上传简历**

`InterviewResumeService.upload(MultipartFile file)` 行为：

1. 校验文件不能为空。
2. 校验文件大小不超过 10MB。
3. 校验文件后缀是 `.pdf`、`.doc`、`.docx` 或测试用 `.txt`。
4. 先插入一条 `UPLOADED` 简历记录。
5. 上传原文件到 `interview/resume/{resumeId}/original/{fileName}`。
6. 更新 `filePath`。
7. 返回 `ResumeUploadResponse`。

- [ ] **步骤 3：实现简历列表和详情**

`list(page, pageSize)`：按 `createdAt` 倒序分页。

`get(id)`：返回：

- 简历基础信息。
- 最新 `RESUME_ANALYZE` 任务。
- 最近 3 个 Session。

- [ ] **步骤 4：实现分析触发占位逻辑**

`analyze(id)` 行为：

1. 简历状态改为 `ANALYZING`。
2. 创建并运行 `RESUME_ANALYZE` 任务。
3. 如果 `contentTextPath` 为空，调用 `ResumeParsingService.parseAndSaveText`。
4. 写入占位字段：`techTags`、`targetPosition`、`analysisSummary`、`interviewPlanSummary`。
5. 生成占位 Markdown 和 HTML 报告到 MinIO。
6. 成功后状态改为 `ANALYZED`，任务改为 `SUCCESS`。
7. 失败后状态改为 `FAILED`，写入 `failureReason`，任务改为 `FAILED`。

- [ ] **步骤 5：实现简历报告读取**

`report(id)`：从 `reportHtmlPath` 读取 HTML 内容，并返回：

```java
new ReportResponse(id, "html", htmlContent)
```

### 验收标准

- [ ] 上传成功后数据库有一条 `UPLOADED` 简历记录。
- [ ] 原始文件保存到 MinIO 路径 `interview/resume/{resumeId}/original/{fileName}`。
- [ ] 分析成功后状态为 `ANALYZED`。
- [ ] 分析失败后状态为 `FAILED`，且有 failureReason。
- [ ] 首次分析会生成并保存 `content_text_path`。
- [ ] 重复分析时如果 `content_text_path` 已存在，不重复解析原文件。
- [ ] 简历详情返回最新分析任务和最近 3 个 Session。
- [ ] 简历报告通过后端返回 HTML 内容，不暴露 MinIO 路径。
- [ ] `./mvnw -q -DskipTests compile` 通过。

---

## 模块五：面试会话与题目模块

### 目标

实现基于已分析简历创建面试 Session，并生成一套固定结构的题目：4 轮，每轮 2 题，共 8 题。真实出题 Agent 后续替换当前占位题生成逻辑。

### 文件范围

- 新建：`src/main/java/com/highway/agent/model/interview/SessionStartRequest.java`
- 新建：`src/main/java/com/highway/agent/model/interview/SessionDetailResponse.java`
- 新建：`src/main/java/com/highway/agent/model/interview/QuestionListResponse.java`
- 新建：`src/main/java/com/highway/agent/service/InterviewSessionService.java`
- 新建：`src/test/java/com/highway/agent/service/InterviewSessionServiceTest.java`

### 实施步骤

- [ ] **步骤 1：创建会话和题目 DTO**

必须包含：

```java
public record SessionStartRequest(Long resumeId) {}
```

```java
public record SessionDetailResponse(
        Long id,
        Long resumeId,
        String resumeFileName,
        String status,
        String overallGrade,
        String overallFeedback,
        String improvementSuggestions,
        String failureReason,
        Integer answeredCount,
        Integer totalCount,
        TaskSummaryResponse latestTask,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
```

```java
public record QuestionListResponse(Long sessionId, List<RoundGroup> rounds) {
    public record RoundGroup(
            Integer roundNumber,
            String roundName,
            String difficulty,
            List<QuestionItem> questions
    ) {}

    public record QuestionItem(
            Long id,
            Integer questionNumber,
            String content,
            String userAnswer,
            String feedback
    ) {}
}
```

- [ ] **步骤 2：编写会话服务测试**

测试覆盖：

- `start` 只能基于 `ANALYZED` 简历创建 Session。
- 创建 Session 后状态最终为 `READY`。
- 成功创建 8 道题。
- 题目按 4 轮分组，每轮 2 题。
- `questionsBySession` 返回轮次分组结构。

- [ ] **步骤 3：实现 start**

`InterviewSessionService.start(Long resumeId)` 行为：

1. 校验简历存在且状态为 `ANALYZED`。
2. 插入 `GENERATING` Session。
3. 创建并运行 `QUESTION_GENERATE` 任务。
4. 删除该 Session 下旧题目。
5. 插入 8 道占位题。
6. Session 状态改为 `READY`。
7. 任务状态改为 `SUCCESS`。
8. 返回 Session 详情。

- [ ] **步骤 4：实现会话详情和列表**

`get(sessionId)` 返回：

- Session 基础状态。
- 归属简历文件名。
- 已答数量。
- 总题数。
- 当前状态对应的最新任务。

`list(resumeId, limit)` 返回该简历最近若干 Session。

- [ ] **步骤 5：实现题目分组查询**

`questionsBySession(sessionId)`：按 `roundNumber` 分组，组内按 `questionNumber` 排序。

### 验收标准

- [ ] 只有 `ANALYZED` 简历能开始面试。
- [ ] 每次开始面试都会创建新的 Session，不复用旧 Session。
- [ ] 新 Session 初始生成流程为 `GENERATING -> READY`。
- [ ] 每个 Session 有且只有 8 道题。
- [ ] 题目结构固定为 4 轮 × 2 题。
- [ ] 题目包含 `roundNumber`、`roundName`、`difficulty`、`questionNumber`、`content`、`scoringPoints`。
- [ ] `questionsBySession` 返回前端可直接渲染的轮次分组结构。
- [ ] `./mvnw -q -Dtest=InterviewSessionServiceTest test` 通过。

---

## 模块六：答案草稿、提交与报告模块

### 目标

实现用户答题过程中的草稿保存、统一提交、占位评分报告生成和失败重试入口。MVP 中一次 Session 只能提交一次，提交后不可再编辑答案。

### 文件范围

- 新建：`src/main/java/com/highway/agent/model/interview/AnswerSaveRequest.java`
- 新建：`src/main/java/com/highway/agent/model/interview/SessionSubmitRequest.java`
- 修改：`src/main/java/com/highway/agent/service/InterviewSessionService.java`
- 修改：`src/test/java/com/highway/agent/service/InterviewSessionServiceTest.java`

### 实施步骤

- [ ] **步骤 1：创建答案 DTO**

```java
public record AnswerSaveRequest(List<AnswerItem> answers) {
    public record AnswerItem(Long questionId, String answer) {}
}
```

```java
public record SessionSubmitRequest(List<AnswerSaveRequest.AnswerItem> answers) {}
```

- [ ] **步骤 2：补充答案保存测试**

测试覆盖：

- 保存答案会更新 `interview_question.user_answer`。
- `READY` 状态保存答案后进入 `ANSWERING`。
- `ANSWERING` 状态继续保存答案时仍保持 `ANSWERING`。
- `EVALUATING` 和 `COMPLETED` 状态保存答案会失败。

- [ ] **步骤 3：实现 saveAnswers**

行为：

1. 查询 Session。
2. 如果状态是 `EVALUATING` 或 `COMPLETED`，拒绝编辑。
3. 遍历请求里的 `questionId + answer`。
4. 只更新属于当前 Session 的题目。
5. 如果原状态是 `READY`，改为 `ANSWERING`。
6. 返回 Session 详情。

- [ ] **步骤 4：补充提交测试**

测试覆盖：

- `READY` 可以直接提交。
- `ANSWERING` 可以提交。
- 提交前会先保存请求中携带的答案。
- 提交后状态最终为 `COMPLETED`。
- 提交后写入 `overallGrade`、`overallFeedback`、`improvementSuggestions`。
- 重复提交 `COMPLETED` Session 不会创建第二份报告。

- [ ] **步骤 5：实现 submit**

当前计划中的占位行为：

1. 如果已经是 `COMPLETED` 或 `EVALUATING`，直接返回当前详情。
2. 先调用 `saveAnswers` 保存请求里的答案。
3. Session 进入 `EVALUATING`。
4. 创建并运行 `SESSION_EVALUATE` 任务。
5. 写入占位总体评分：`PASS`。
6. 写入占位总体反馈和改进建议。
7. 生成占位 Markdown/HTML 报告到 MinIO。
8. Session 状态改为 `COMPLETED`。
9. 任务状态改为 `SUCCESS`。
10. 返回 Session 详情。

- [ ] **步骤 6：实现重试和报告读取**

`retryGenerate(sessionId)`：仅允许 `GENERATE_FAILED` 状态重新生成题目。

`retryEvaluate(sessionId)`：仅允许 `EVALUATE_FAILED` 状态重新评估。

`report(sessionId)`：读取 `reportHtmlPath` 并返回 HTML 内容。

### 验收标准

- [ ] 草稿答案保存到 `interview_question.user_answer`。
- [ ] 用户可在提交前多次保存草稿。
- [ ] 第一次保存草稿会让 Session 从 `READY` 进入 `ANSWERING`。
- [ ] 提交允许未答题存在。
- [ ] 提交后 Session 最终为 `COMPLETED`。
- [ ] 提交后不可继续编辑答案。
- [ ] 一个 Session 只产生一份报告。
- [ ] 报告通过后端代理读取，不暴露 MinIO 路径。
- [ ] `GENERATE_FAILED` 和 `EVALUATE_FAILED` 分别有重试入口。
- [ ] `./mvnw -q -Dtest=InterviewSessionServiceTest test` 通过。

---

## 模块七：REST API 模块

### 目标

把前面模块提供的 Service 能力暴露为前端设计中确认的 `/api/interview/**` 接口。

### 文件范围

- 新建：`src/main/java/com/highway/agent/controller/InterviewController.java`
- 新建：`src/test/java/com/highway/agent/controller/InterviewControllerTest.java`

### 实施步骤

- [ ] **步骤 1：编写 Controller 契约测试**

测试至少覆盖：

- `POST /api/interview/resume/upload`
- `GET /api/interview/resume/list`
- `POST /api/interview/resume/{id}/analyze`
- `POST /api/interview/session/start`
- `GET /api/interview/session/{id}`
- `GET /api/interview/session/{id}/questions`
- `POST /api/interview/session/{id}/answers/save`
- `POST /api/interview/session/{id}/submit`
- `GET /api/interview/session/{id}/report`

- [ ] **步骤 2：实现 InterviewController**

Controller 路径：

```java
@RestController
@RequestMapping("/api/interview")
@RequiredArgsConstructor
public class InterviewController {
    private final InterviewResumeService resumeService;
    private final InterviewSessionService sessionService;
}
```

暴露接口：

```text
POST /api/interview/resume/upload
POST /api/interview/resume/{id}/analyze
GET  /api/interview/resume/{id}
GET  /api/interview/resume/list?page=1&pageSize=10
GET  /api/interview/resume/{id}/report
POST /api/interview/session/start
GET  /api/interview/session/{id}
GET  /api/interview/session/list?resumeId={id}&limit=3
POST /api/interview/session/{id}/answers/save
POST /api/interview/session/{id}/submit
POST /api/interview/session/{id}/retry-generate
POST /api/interview/session/{id}/retry-evaluate
GET  /api/interview/session/{id}/questions
GET  /api/interview/session/{id}/report
```

### 验收标准

- [ ] Controller 只依赖 `InterviewResumeService` 和 `InterviewSessionService`。
- [ ] Controller 不直接访问 Mapper、MinIO、Tika 或 Agent。
- [ ] 接口路径和前端设计文档一致。
- [ ] 上传接口使用 `multipart/form-data`。
- [ ] 其他写接口使用 JSON request body 或 path variable。
- [ ] `./mvnw -q -Dtest=InterviewControllerTest test` 通过。

---

## 模块八：整体联调验证

### 目标

确认所有模块组合后可以跑通智能面试 MVP 的后端主流程。

### 文件范围

- 本计划涉及的所有文件。

### 实施步骤

- [ ] **步骤 1：运行完整测试**

```bash
./mvnw test
```

预期：全部测试通过。

- [ ] **步骤 2：运行打包检查**

```bash
./mvnw -DskipTests package
```

预期：应用可成功打包。

- [ ] **步骤 3：人工验证主流程**

启动应用后按顺序验证：

1. 上传 `.txt` 测试简历到 `POST /api/interview/resume/upload`。
2. 调用 `POST /api/interview/resume/{id}/analyze`。
3. 调用 `GET /api/interview/resume/{id}`，确认状态为 `ANALYZED`。
4. 调用 `POST /api/interview/session/start`。
5. 调用 `GET /api/interview/session/{id}/questions`，确认 4 轮 × 2 题。
6. 调用 `POST /api/interview/session/{id}/answers/save` 保存部分答案。
7. 调用 `POST /api/interview/session/{id}/submit` 提交。
8. 调用 `GET /api/interview/session/{id}/report`，确认能返回 HTML 内容。

- [ ] **步骤 4：检查 Git 状态**

```bash
git status --short
```

预期：只存在本计划相关修改。

### 验收标准

- [ ] `./mvnw test` 通过。
- [ ] `./mvnw -DskipTests package` 通过。
- [ ] 主流程从上传到报告可跑通。
- [ ] 数据库中能看到 Resume、Session、Question、Task 记录。
- [ ] MinIO 中能看到原始简历、解析文本、占位报告文件。
- [ ] 没有把大段解析文本写入数据库。
- [ ] 没有实现超出本计划范围的真实 Agent 工作流和前端页面。

---

## 后续计划拆分

本计划完成后，再按以下顺序继续：

1. **Agent 工作流计划**
   - ResumeAnalysisAgent
   - InterviewPlanningAgent
   - QuestionPlanningAgent
   - QuestionGenerationAgent
   - EvaluationAgent
   - Spring AI Alibaba Graph 编排

2. **前端页面计划**
   - `static/interview.html`
   - 简历管理视图
   - 答题视图
   - 评估中视图
   - 报告视图
   - URL 状态恢复

3. **真实端到端验收计划**
   - 上传真实 PDF/DOCX
   - 使用真实大模型生成题目
   - 提交答案并生成真实评估报告

---

## 自检

**规格覆盖：** 本计划覆盖后端基础闭环，包括数据库、简历文件、解析文本路径、异步任务、简历 API、Session API、题目 API、答案草稿、提交和报告代理。真实 AI 工作流和前端页面已明确拆到后续计划。

**模块边界：** 每个模块都有独立目标、文件范围、实施步骤和验收标准。后续写代码时应严格按模块推进，不跨模块提前实现。

**验收方式：** 每个模块至少有编译、测试或接口行为验收；最终模块有完整主流程验收。

---

计划已保存到 `docs/superpowers/plans/2026-05-23-interview-backend-foundation-plan.md`。

后续我会按这个模块化计划写代码：每完成一个模块，就运行该模块验收，再继续下一个模块。
