# Highway Tech Agent - 开发规范

## 项目概述

AI 智能面试智能体，基于 Spring AI Alibaba 构建，支持 ReAct 模式对话和深度研究工作流。

## 技术栈

- **Java 21** + **Spring Boot 3.5.7**
- **Spring AI 1.1.2** / **Spring AI Alibaba 1.1.2.2**
- **MyBatis-Plus 3.5.12** + MySQL
- **Spring WebFlux** (SSE 流式输出)
- **MinIO** (对象存储)
- **Lombok** (减少样板代码)

## 代码规范

### 命名约定

- 包名：全小写，按功能分层 `com.highway.agent.{layer}`
- 类名：大驼峰 `PascalCase`，类名体现职责
  - Controller 层：`XxxController`
  - Service 层：`XxxService`，接口+实现分离时为 `XxxServiceImpl`
  - Model 层：`XxxRequest` / `XxxResponse` / `XxxDTO` / `XxxEntity`
  - Mapper 层：`XxxMapper` 继承 `BaseMapper`
  - Tool 类：`XxxTool` 实现 `@Tool` 注解方法
- 方法名：小驼峰 `camelCase`，动词开头
- 常量：全大写下划线 `UPPER_SNAKE_CASE`
- 数据库字段：小写下划线 `snake_case`

### 分层架构

```
controller/   → REST 端点，仅做参数校验和调用 Service，不含业务逻辑
service/      → 业务逻辑，编排 AI 模型和工具调用
model/        → DTO / Entity / Request / Response，不混用
config/       → Spring 配置类，每个配置一个类
tool/         → Spring AI Tool 实现，独立可测试
memory/       → 对话记忆持久化
prompt/       → Prompt 模板，与逻辑分离
research/     → 深度研究相关（graph/node/edge/prompt/model/util）
```

- 严格单向依赖：`controller → service → mapper/tool`，禁止跨层调用
- Service 之间可互相调用，但避免循环依赖
- Controller 不直接操作 Mapper 或 Tool

### Java 编码风格

- 使用 Lombok `@Data` / `@Builder` / `@NoArgsConstructor` / `@AllArgsConstructor` 减少样板
- 优先使用 `record` 定义不可变 DTO（Java 21 特性）
- 集合初始化指定容量：`new ArrayList<>(16)`
- 字符串拼接用 `StringBuilder` 或模板引擎，不用 `+` 拼接循环
- 日志使用 `@Slf4j`，关键操作记录 INFO，异常记录 ERROR
- 异常处理：业务异常抛出自定义异常，全局 `@ControllerAdvice` 统一捕获

### 流式输出规范

- 所有 AI 对话接口使用 SSE（Server-Sent Events）流式返回
- Controller 返回 `Flux<ServerSentEvent<String>>`
- Service 返回 `Flux<String>` 或 `Flux<xxxDTO>`
- 流中数据使用 JSON 格式，包含 `type` 字段区分消息类型：
  - `token` — 流式文本片段
  - `thinking` — 思考过程
  - `action` — 工具调用
  - `observation` — 工具结果
  - `done` — 结束标记

### AI / Prompt 规范

- Prompt 模板放在 `prompt/` 包下，使用 Spring AI 的 `PromptTemplate`
- System Prompt 和 User Prompt 分离
- Prompt 中禁止硬编码业务逻辑，通过变量注入
- Tool 描述要清晰，`@Tool` 注解的 `description` 准确描述功能和参数

### 数据库规范

- 建表语句放在 `schema.sql`，使用 `CREATE TABLE IF NOT EXISTS`
- 表名小写下划线，必须有 `id` 主键、`created_at`、`updated_at` 时间戳
- 高频查询字段建索引，索引名 `idx_字段名`
- 大文本字段使用 `TEXT` / `MEDIUMTEXT`
- Schema 变更通过 `SchemaMigration` 类管理，不手动改库

## 前端规范

- 单文件 `static/index.html`，不拆分
- 界面语言：中文
- 样式使用内联 CSS，不引入外部框架
- JS 使用原生 ES6+，不引入前端框架
- SSE 消息解析与后端 `type` 字段对齐
- 新增 UI 组件遵循现有 CSS 变量和布局模式

## Git 规范

### 分支

- `main` — 主分支，始终保持可部署状态
- 功能开发从 `main` 拉取，完成后合并回 `main`

### 提交信息

格式：`<类型>: <简述>`

类型：
- `feat` — 新功能
- `fix` — 修复 Bug
- `refactor` — 重构
- `docs` — 文档
- `style` — 格式调整
- `test` — 测试
- `chore` — 构建/工具变更

示例：`feat: 添加面试评估节点`

### 提交粒度

- 每次提交是一个完整的逻辑变更
- 不提交半成品或无法编译的代码
- 不提交 `.env`、密钥、IDE 配置

## 安全规范

- API Key、数据库密码等敏感信息放在环境变量或 `application.yml` 中用 `${ENV_VAR}` 引用
- 禁止在代码中硬编码密钥
- 用户输入必须校验，防止 SQL 注入和 XSS
- MyBatis-Plus 参数绑定天然防 SQL 注入，不要拼接 SQL
- SSE 推送内容做 HTML 转义

## 开发流程

1. 明确需求 → 编写实现方案
2. 按分层架构新增类，遵循命名和包结构
3. 编写代码，遵守上述规范
4. 本地启动验证，确保编译通过和基本功能可用
5. 提交代码，遵循 Git 提交规范
