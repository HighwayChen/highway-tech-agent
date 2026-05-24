# 智能面试系统前端设计

## 设计目标

在现有 `highway-tech-agent` 项目中新增智能面试前端页面。页面面向 Java 后端求职者练习面试，围绕“上传简历 → 分析简历 → 开始面试 → 作答 → 查看报告”的主流程组织。

前端采用单页 `interview.html`，使用原生 HTML、CSS、JavaScript，不引入前端框架。页面语言为中文，样式遵循现有静态页面的内联 CSS 方式。

## 信息架构

顶层只展示“简历管理”。答题、评估中、报告都不是全局平级页面，而是某个面试会话的详情状态。

层级关系：

```text
简历 Resume
  └── 面试会话 Session
        ├── 题目 Questions
        └── 报告 Report
```

页面用面包屑表达当前位置：

```text
简历管理 / 简历名称 / Session #id / 答题
简历管理 / 简历名称 / Session #id / 评估中
简历管理 / 简历名称 / Session #id / 报告
```

## 数据关系

MVP 数据关系固定为：

- `Resume 1:N Session`
- `Session 1:N Question`
- `Session 1:1 Report`

一次 Session 对应一套题和一次提交结果。MVP 不引入 `Attempt` 概念，不支持同一套题多次提交。如果用户想再次练习，需要从简历卡片创建新的 Session，由系统重新生成题目。

## 页面结构

### 1. 简历管理视图

简历管理是默认入口，包含上传区和简历卡片列表。

每张简历卡片展示：

- 文件名
- 上传时间
- 简历评估状态
- 技术标签或简短摘要
- 简历评估报告入口
- 开始新面试入口
- 最近 3 个面试 Session

简历卡片下的 Session 行展示：

- Session ID
- 创建时间
- 当前状态
- 已答进度或总体等级
- 对应操作按钮

Session 操作由状态决定：

| Session 状态 | 前端展示 | 可用操作 |
| --- | --- | --- |
| `GENERATING` | 题目生成中 | 刷新状态 |
| `READY` | 已生成题目 | 开始答题 |
| `ANSWERING` | 答题中，显示已答进度 | 继续答题 |
| `EVALUATING` | 评估中 | 查看评估中状态 / 刷新 |
| `COMPLETED` | 已完成，显示总体等级 | 查看报告 |
| `FAILED` | 显示失败原因 | 重新生成题目 / 删除会话 |

### 2. 答题视图

答题视图从具体 Session 进入，不作为全局入口。

布局：

- 顶部显示面包屑、Session ID、归属简历、已答进度。
- 左侧为轮次导航，展示每轮名称、难度、已答题数和总题数。
- 右侧为当前轮次题目列表，每题包含题目内容和答案输入框。
- 底部提供“保存草稿”和“提交本次面试”。

交互规则：

- 用户可在轮次之间切换，已输入答案需要保留。
- “保存草稿”持久化当前答案，但不触发评分。
- “提交本次面试”前弹确认框，提示未答题目数量。
- 如果仍有未答题，用户可以返回补充，也可以确认提交。
- 提交成功后 Session 进入 `EVALUATING`，页面切换到评估中视图，答题内容禁止再次编辑。

### 3. 评估中视图

评估中视图用于展示 `EVALUATING` 状态。

内容：

- 当前 Session ID
- 归属简历
- 状态说明
- 加载动画
- 刷新状态按钮
- 返回简历管理入口

前端每 3-5 秒轮询 `GET /api/interview/session/{id}`。

轮询结果处理：

- `COMPLETED`：自动跳转报告视图。
- `FAILED`：显示失败原因和返回入口。
- 其他状态：保持当前页面。

### 4. 报告视图

报告视图从 `COMPLETED` Session 进入。

布局：

- 顶部显示 Session ID、归属简历、完成时间、完整 HTML 报告入口。
- 第一层为总览卡片：总体等级、主要优势、优先提升项。
- 第二层为改进建议列表。
- 第三层按轮次折叠展示评价。
- 轮次详情中展示题目级反馈。

操作：

- 查看完整 HTML 报告。
- 返回简历管理。
- 基于该简历开始新面试。

“基于该简历开始新面试”会创建新的 Session 并重新生成题目，不复用当前 Session 的题目。

## 简历状态交互

| 简历状态 | 前端展示 | 可用操作 |
| --- | --- | --- |
| `UPLOADED` | 已上传 | 分析简历 |
| `ANALYZING` | 分析中 | 刷新状态 |
| `ANALYZED` | 已评估 | 查看简历报告 / 开始新面试 |
| `FAILED` | 显示失败原因 | 重新分析 |

## URL 与状态恢复

`interview.html` 支持通过 URL 参数恢复页面状态：

- 无参数：显示简历管理。
- `resumeId`：定位并高亮对应简历卡片。
- `sessionId`：请求 Session 详情，根据状态进入答题、评估中或报告视图。

状态映射：

| Session 状态 | 恢复视图 |
| --- | --- |
| `READY` | 答题视图 |
| `ANSWERING` | 答题视图 |
| `EVALUATING` | 评估中视图 |
| `COMPLETED` | 报告视图 |
| `GENERATING` | 简历管理中展示生成状态 |
| `FAILED` | 简历管理中展示失败状态 |

## API 使用

前端依赖以下接口：

### 简历

- `POST /api/interview/resume/upload`
- `POST /api/interview/resume/{id}/analyze`
- `GET /api/interview/resume/{id}`
- `GET /api/interview/resume/list`

### 面试会话

- `POST /api/interview/session/start`
- `GET /api/interview/session/{id}`
- `GET /api/interview/session/list?resumeId={resumeId}&limit=3`
- `POST /api/interview/session/{id}/submit`
- `GET /api/interview/session/{id}/report`

### 题目

- `GET /api/interview/session/{id}/questions`

## MVP 边界

包含：

- 单页 `interview.html`
- 简历上传入口
- 简历卡片列表
- 简历状态操作
- 简历下最近 3 个 Session
- Session 状态入口
- 轮次导航答题页
- 保存草稿
- 提交前确认
- 评估中轮询
- 报告总览页
- 完整 HTML 报告入口

不包含：

- 独立面试历史页
- 同一套题多次作答
- `Attempt` 数据模型
- 复杂时间线
- 复杂筛选和分页 UI
- 移动端专项优化

## 原型结论

页面布局逻辑确认采用“简历为根入口，Session 作为简历下的子资源，答题和报告作为 Session 详情状态”的方案。

用户路径：

1. 上传简历。
2. 手动分析简历。
3. 在简历卡片点击“开始新面试”。
4. 系统创建 Session 并生成题目。
5. 用户从 Session 进入答题。
6. 提交后 Session 进入评估中。
7. 完成后从该 Session 查看报告。
8. 再次练习时，从简历卡片创建新 Session。
