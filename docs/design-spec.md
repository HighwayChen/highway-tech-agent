# 联网问答系统 Agent — 设计规范

## 概述

基于 Spring AI Alibaba 的通用联网问答系统，采用 ReAct 推理模式（Thought → Action → Observation），用户自由提问后 Agent 自主判断是否需要联网搜索，综合信息后给出带参考文献的回答，并推荐后续问题。

## 核心需求

| 需求 | 说明 |
|------|------|
| 通用联网问答 | 用户自由提问，Agent 搜索互联网获取实时信息后回答 |
| ReAct 推理 | Thought → Action → Observation 循环，支持多步推理和多轮搜索 |
| 智能搜索决策 | Agent 自主判断是否需要搜索，不需要时直接回答 |
| 参考文献 | 回答中标注来源编号[1][2]，末尾列出参考文献列表 |
| 猜你想问 | 回答后推荐 3 个相关问题，点击即发送 |
| 会话级记忆 | 多轮对话支持追问，MySQL 持久化 |
| SSE 流式输出 | 逐步展示回答过程，包括 ReAct 推理步骤 |
| 简单聊天 UI | 静态 HTML 页面，打字机效果，可点击引用和推荐问题 |

## 技术栈

| 组件 | 选型 | 说明 |
|------|------|------|
| JDK | 21 | |
| 框架 | Spring Boot 3.x | |
| AI 框架 | Spring AI Alibaba | 通义千问集成 |
| Spring AI | ChatClient、ChatMemory、Function Calling | |
| 搜索 API | Tavily | 专为 AI Agent 设计的搜索 API |
| ORM | MyBatis-Plus | 会话记忆持久化 |
| 响应式 | Spring WebFlux + Flux | SSE 流式输出 |
| HTTP 客户端 | WebClient | 调用 Tavily API |
| 数据库 | MySQL | 会话记忆存储 |
| LLM | 通义千问 Qwen | |
| 前端 | 静态 HTML + CSS + JS | 轻量聊天界面 |

## 架构设计

```
┌─────────────────────────────────────────────────┐
│              Web Chat UI (静态页面)               │
│   index.html — 聊天界面 + SSE 流式显示           │
└──────────────────┬──────────────────────────────┘
                   │ HTTP / SSE
┌──────────────────▼──────────────────────────────┐
│           Spring Boot Controller (WebFlux)       │
│   /api/chat (SSE)  /api/chat/sync (POST)        │
│   /api/conversations (GET)  /api/conversations/{id} (DELETE) │
└──────────────────┬──────────────────────────────┘
                   │
┌──────────────────▼──────────────────────────────┐
│          ChatService (核心服务层)                 │
│   ┌─────────────────────────────────────────┐   │
│   │  ReAct Agent Loop                       │   │
│   │                                         │   │
│   │  Thought → Action → Observation         │   │
│   │       ↓              ↓                  │   │
│   │  满足条件？→ Final Answer               │   │
│   │  不满足？→ 继续循环                     │   │
│   │                                         │   │
│   │  Tools: TavilySearchTool                │   │
│   └─────────────────────────────────────────┘   │
│   + 会话记忆 (MySQL持久化, MyBatis-Plus)        │
│   + System Prompt (ReAct格式+来源引用指令)      │
│   + SuggestionService (生成猜你想问)            │
└──────┬────────────┬──────────────┬──────────────┘
       │            │              │
┌──────▼──────┐ ┌───▼────┐  ┌─────▼─────┐
│ 通义千问 API │ │Tavily  │  │  MySQL    │
└─────────────┘ └────────┘  └───────────┘
```

## ReAct 执行流程

```
用户提问
  │
  ▼
┌─ ReAct Loop (max 5 轮) ──────────────────┐
│                                          │
│  1. Thought: LLM 分析问题，决定下一步    │
│     例："用户问的是最新新闻，需要搜索"   │
│                                          │
│  2. Action: 执行工具调用                 │
│     例：TavilySearch("最新AI新闻")       │
│                                          │
│  3. Observation: 获取工具返回结果        │
│     例："搜索到3条结果：..."             │
│                                          │
│  4. 判断: 信息是否充分？                 │
│     - 充分 → 生成最终回答（含来源引用）  │
│     - 不充分 → 回到 Step 1 继续推理     │
│     - 不需要搜索 → 直接给出 Final Answer │
│                                          │
└──────────────────────────────────────────┘
  │
  ▼
收集参考文献 → 生成猜你想问 → 组装响应
```

## 回答格式

```json
{
  "answer": "量子计算是利用量子力学原理进行计算的技术[1]，近年来取得了重大突破[2]...",
  "references": [
    { "id": 1, "title": "量子计算年度报告", "url": "https://..." },
    { "id": 2, "title": "量子技术综述", "url": "https://..." }
  ],
  "suggestedQuestions": [
    "量子计算有哪些实际应用？",
    "量子计算和经典计算的区别？",
    "如何学习量子计算？"
  ]
}
```

## SSE 事件流

```
GET /api/chat?conversationId=xxx&message=什么是量子计算？

event: thought
data: {"content": "用户问的是量子计算，需要搜索最新信息"}

event: action
data: {"tool": "tavily_search", "query": "量子计算 最新进展"}

event: observation
data: {"content": "搜索到3条结果：..."}

event: answer
data: {"content": "量子计算是利用量子力学原理..."}

event: answer
data: {"content": "近年来取得了重大突破..."}

event: references
data: [{"id":1,"title":"...","url":"https://..."}]

event: suggested
data: ["量子计算有哪些实际应用？","..."]

event: done
data: {}
```

## API 端点

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/chat` | GET (SSE) | 流式对话，返回 Flux<ServerSentEvent> |
| `/api/chat/sync` | POST | 同步对话，返回完整 JSON |
| `/api/conversations` | GET | 获取会话列表 |
| `/api/conversations/{id}` | DELETE | 删除会话 |

### 请求参数

**GET /api/chat**
- `conversationId` (可选): 会话 ID，为空则新建
- `message` (必填): 用户提问

**POST /api/chat/sync**
```json
{
  "conversationId": "可选，为空新建",
  "message": "用户提问"
}
```

## 项目结构

```
highway-agent/
├── pom.xml
├── src/main/java/com/highway/agent/
│   ├── HighwayAgentApplication.java
│   │
│   ├── config/
│   │   ├── AiConfig.java              # ChatClient、ChatMemory 配置
│   │   ├── TavilyConfig.java          # Tavily API + WebClient 配置
│   │   └── WebFluxConfig.java         # WebFlux 配置
│   │
│   ├── controller/
│   │   └── ChatController.java        # WebFlux 端点，返回 Flux<ServerSentEvent>
│   │
│   ├── service/
│   │   ├── ChatService.java           # 核心服务：ReAct 循环编排
│   │   └── SuggestionService.java     # 生成"猜你想问"
│   │
│   ├── agent/
│   │   ├── ReActAgent.java            # ReAct 循环核心逻辑
│   │   └── ReActContext.java          # ReAct 执行上下文（思考/行动/观察记录）
│   │
│   ├── tool/
│   │   └── TavilySearchTool.java      # 联网搜索工具（WebClient 调用 Tavily）
│   │
│   ├── memory/
│   │   ├── MysqlChatMemory.java       # MySQL 会话记忆（MyBatis-Plus）
│   │   └── ChatMessageMapper.java     # MyBatis-Plus Mapper
│   │
│   ├── model/
│   │   ├── ChatMessage.java           # MyBatis-Plus 实体
│   │   ├── ChatResponse.java          # 统一响应（answer + references + suggested）
│   │   └── SearchResult.java          # 搜索结果封装
│   │
│   └── prompt/
│       └── PromptTemplate.java        # System Prompt 模板管理
│
├── src/main/resources/
│   ├── application.yml
│   ├── schema.sql                     # 建表语句
│   └── static/
│       └── index.html                 # 聊天 UI
│
└── src/test/java/
```

## 数据库表

```sql
CREATE TABLE chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,        -- user/assistant/system/tool
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id, created_at)
);
```

## 前端 UI 设计

- **左侧**：会话列表，可切换/删除会话
- **右侧上方**：消息流，打字机效果显示回答
- **回答下方**：
  - 参考文献卡片（编号 + 标题 + 可点击链接）
  - 猜你想问标签（点击即发送新提问）
- **ReAct 过程**：可折叠显示 thought/action/observation，让用户看到 Agent 思考过程

## System Prompt 核心指令

```
你是一个联网问答助手，使用 ReAct 格式进行推理。

格式规范：
- Thought: 分析用户问题，决定下一步行动
- Action: 选择工具并传入参数（仅支持 tavily_search）
- Observation: 工具返回的结果
- Final Answer: 最终回答

规则：
1. 如果问题不需要最新信息或联网搜索，直接给出 Final Answer
2. 搜索时提取关键信息，在回答中用 [1][2] 标注来源
3. Final Answer 末尾不要列出参考文献（系统会自动附加）
4. 最多进行 5 轮 Thought-Action-Observation 循环
5. 用中文回答
```

## MVP 范围

**包含**：
- ReAct 推理循环（Thought → Action → Observation）
- Tavily 联网搜索
- 智能搜索决策
- 参考文献
- 猜你想问
- MySQL 会话记忆持久化
- SSE 流式输出
- 简单聊天 UI

**不包含（后续迭代）**：
- 多搜索源（Google/Bing 等）
- 搜索缓存
- 用户认证
- 生产级部署配置
- 更多工具（代码执行、数学计算等）
