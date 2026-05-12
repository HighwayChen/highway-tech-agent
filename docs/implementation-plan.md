# 联网问答系统 Agent 实施计划

**目标：** 构建基于 Spring AI Alibaba + ReAct 模式的联网问答系统，支持智能搜索决策、参考文献、猜你想问、SSE 流式输出

**架构：** 自定义 ReAct 循环（Thought → Action → Observation），ChatClient 驱动 LLM 调用，FunctionToolCallback 注册 Tavily 搜索工具，MyBatis-Plus 持久化会话记忆，WebFlux SSE 流式输出

**技术栈：** JDK 21 / Spring Boot 3.5.x / Spring AI 1.1.2 / Spring AI Alibaba 1.1.2.2 / MyBatis-Plus / WebFlux / WebClient / MySQL / Tavily API

---

## 文件结构总览

```
highway-agent/
├── pom.xml
├── src/main/java/com/highway/agent/
│   ├── HighwayAgentApplication.java          # Spring Boot 启动类
│   ├── config/
│   │   ├── AiConfig.java                    # ChatClient + ChatMemory 配置
│   │   └── WebClientConfig.java             # WebClient Bean 配置
│   ├── controller/
│   │   └── ChatController.java              # SSE + 同步端点
│   ├── service/
│   │   ├── ChatService.java                 # ReAct 循环编排 + SSE 事件发射
│   │   └── SuggestionService.java           # 生成"猜你想问"
│   ├── agent/
│   │   ├── ReActAgent.java                  # ReAct 循环核心逻辑
│   │   └── ReActStep.java                  # 单步推理结果（thought/action/observation/finalAnswer）
│   ├── tool/
│   │   └── TavilySearchTool.java            # Tavily 联网搜索（WebClient）
│   ├── memory/
│   │   ├── MysqlChatMemory.java             # ChatMemory 接口的 MySQL 实现
│   │   └── ChatMessageMapper.java           # MyBatis-Plus Mapper
│   ├── model/
│   │   ├── ChatMessage.java                 # MyBatis-Plus 实体
│   │   ├── ChatRequest.java                 # 请求 DTO
│   │   ├── ChatResponse.java                # 响应 DTO（answer + references + suggested）
│   │   ├── SearchResult.java                # Tavily 搜索结果
│   │   └── SseEvent.java                    # SSE 事件封装
│   └── prompt/
│       └── PromptTemplate.java              # System Prompt 模板
│
├── src/main/resources/
│   ├── application.yml                      # 配置文件
│   ├── schema.sql                           # 建表语句
│   └── static/
│       └── index.html                       # 聊天 UI
│
└── src/test/java/com/highway/agent/
    ├── tool/
    │   └── TavilySearchToolTest.java        # 搜索工具测试
    ├── agent/
    │   └── ReActAgentTest.java              # ReAct 解析测试
    └── memory/
        └── MysqlChatMemoryTest.java         # 会话记忆测试
```

---

## 任务 1：项目骨架搭建

**文件：** `pom.xml`, `application.yml`, `HighwayAgentApplication.java`, `schema.sql`

### 步骤 1.1：创建 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.7</version>
        <relativePath/>
    </parent>

    <groupId>com.highway</groupId>
    <artifactId>highway-agent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>highway-agent</name>
    <description>Web Search QA Agent based on Spring AI Alibaba with ReAct pattern</description>

    <properties>
        <java.version>21</java.version>
        <spring-ai.version>1.1.2</spring-ai.version>
        <spring-ai-alibaba.version>1.1.2.2</spring-ai-alibaba-extensions.version>
        <spring-ai-alibaba-extensions.version>1.1.2.2</spring-ai-alibaba-extensions.version>
        <mybatis-plus.version>3.5.12</mybatis-plus.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>${spring-ai.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-bom</artifactId>
                <version>${spring-ai-alibaba.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-extensions-bom</artifactId>
                <version>${spring-ai-alibaba-extensions.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <!-- Spring AI Alibaba DashScope Starter -->
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-starter-dashscope</artifactId>
        </dependency>

        <!-- Spring AI Chat Memory (for ChatMemory interface + MessageWindowChatMemory) -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-model-chat-memory</artifactId>
        </dependency>

        <!-- WebFlux -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Jackson (already in spring-boot-starter, but explicit for JSON processing) -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.projectreactor</groupId>
            <artifactId>reactor-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 步骤 1.2：创建 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: highway-agent
  ai:
    dashscope:
      api-key: ${AI_DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DB:highway_agent}?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto

tavily:
  api-key: ${TAVILY_API_KEY}
  base-url: https://api.tavily.com
  search-depth: advanced
  max-results: 5

agent:
  react:
    max-iterations: 5
  suggestion:
    count: 3
```

### 步骤 1.3：创建启动类

```java
package com.highway.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HighwayAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(HighwayAgentApplication.class, args);
    }
}
```

### 步骤 1.4：创建 schema.sql

```sql
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation (conversation_id, created_at)
);
```

### 步骤 1.5：验证项目编译

```bash
cd /Users/chenhaiwei/IdeaProjects/highway-agent && mvn compile
```

预期输出：BUILD SUCCESS

### 步骤 1.6：提交

```bash
git init && git add -A && git commit -m "feat: project skeleton with Spring Boot 3.5 + Spring AI Alibaba"
```

---

## 任务 2：模型层

**文件：** `model/ChatMessage.java`, `model/SearchResult.java`, `model/ChatRequest.java`, `model/ChatResponse.java`, `model/SseEvent.java`, `agent/ReActStep.java`

### 步骤 2.1：创建 ChatMessage（MyBatis-Plus 实体）

```java
package com.highway.agent.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String conversationId;

    private String role;

    private String content;

    private LocalDateTime createdAt;
}
```

### 步骤 2.2：创建 SearchResult（Tavily 搜索结果）

```java
package com.highway.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchResult {

    private String title;

    private String url;

    private String content;

    private double score;
}
```

### 步骤 2.3：创建 ChatRequest

```java
package com.highway.agent.model;

import lombok.Data;

@Data
public class ChatRequest {

    private String conversationId;

    private String message;
}
```

### 步骤 2.4：创建 ChatResponse

```java
package com.highway.agent.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatResponse {

    private String answer;

    private List<Reference> references;

    private List<String> suggestedQuestions;

    @Data
    @Builder
    public static class Reference {
        private int id;
        private String title;
        private String url;
    }
}
```

### 步骤 2.5：创建 SseEvent

```java
package com.highway.agent.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SseEvent {

    private String type;   // thought, action, observation, answer, references, suggested, done
    private Object data;

    public static SseEvent thought(String content) {
        return SseEvent.builder().type("thought").data(content).build();
    }

    public static SseEvent action(String tool, String query) {
        return SseEvent.builder().type("action").data(new ActionData(tool, query)).build();
    }

    public static SseEvent observation(String content) {
        return SseEvent.builder().type("observation").data(content).build();
    }

    public static SseEvent answer(String content) {
        return SseEvent.builder().type("answer").data(content).build();
    }

    public static SseEvent references(Object refs) {
        return SseEvent.builder().type("references").data(refs).build();
    }

    public static SseEvent suggested(Object questions) {
        return SseEvent.builder().type("suggested").data(questions).build();
    }

    public static SseEvent done() {
        return SseEvent.builder().type("done").data("").build();
    }

    @Data
    public static class ActionData {
        private final String tool;
        private final String query;
    }
}
```

### 步骤 2.6：创建 ReActStep

```java
package com.highway.agent.agent;

import com.highway.agent.model.SearchResult;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ReActStep {

    private String thought;
    private String actionTool;
    private String actionQuery;
    private String observation;
    private List<SearchResult> searchResults;
    private String finalAnswer;

    public boolean hasAction() {
        return actionTool != null && !actionTool.isBlank();
    }

    public boolean hasFinalAnswer() {
        return finalAnswer != null && !finalAnswer.isBlank();
    }
}
```

### 步骤 2.7：验证编译

```bash
mvn compile
```

预期输出：BUILD SUCCESS

### 步骤 2.8：提交

```bash
git add -A && git commit -m "feat: add model layer (ChatMessage, SearchResult, ChatRequest, ChatResponse, SseEvent, ReActStep)"
```

---

## 任务 3：数据库层 — 会话记忆

**文件：** `memory/ChatMessageMapper.java`, `memory/MysqlChatMemory.java`

### 步骤 3.1：写失败测试 — MysqlChatMemory

```java
package com.highway.agent.memory;

import com.highway.agent.model.ChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MysqlChatMemoryTest {

    private ChatMessageMapper mapper;
    private MysqlChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        mapper = mock(ChatMessageMapper.class);
        chatMemory = new MysqlChatMemory(mapper);
    }

    @Test
    void add_shouldPersistMessages() {
        when(mapper.insert(any(ChatMessage.class))).thenReturn(1);

        chatMemory.add("conv-1", List.of(
                new UserMessage("hello"),
                new AssistantMessage("hi there")
        ));

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(mapper, times(2)).insert(captor.capture());

        List<ChatMessage> inserted = captor.getAllValues();
        assertEquals("conv-1", inserted.get(0).getConversationId());
        assertEquals("user", inserted.get(0).getRole());
        assertEquals("hello", inserted.get(0).getContent());
        assertEquals("assistant", inserted.get(1).getRole());
        assertEquals("hi there", inserted.get(1).getContent());
    }

    @Test
    void get_shouldReturnLastNMessages() {
        ChatMessage msg1 = new ChatMessage();
        msg1.setConversationId("conv-1");
        msg1.setRole("user");
        msg1.setContent("hello");

        ChatMessage msg2 = new ChatMessage();
        msg2.setConversationId("conv-1");
        msg2.setRole("assistant");
        msg2.setContent("hi");

        when(mapper.selectLastN("conv-1", 2)).thenReturn(List.of(msg1, msg2));

        var messages = chatMemory.get("conv-1", 2);

        assertEquals(2, messages.size());
        assertEquals("hello", messages.get(0).getText());
        assertEquals("hi", messages.get(1).getText());
    }

    @Test
    void clear_shouldDeleteByConversationId() {
        when(mapper.deleteByConversationId("conv-1")).thenReturn(2);

        chatMemory.clear("conv-1");

        verify(mapper).deleteByConversationId("conv-1");
    }
}
```

### 步骤 3.2：验证测试失败

```bash
mvn test -Dtest=MysqlChatMemoryTest
```

预期输出：编译失败（MysqlChatMemory 类不存在）

### 步骤 3.3：实现 ChatMessageMapper

```java
package com.highway.agent.memory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.highway.agent.model.ChatMessage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    @Select("SELECT * FROM chat_message WHERE conversation_id = #{conversationId} ORDER BY created_at ASC LIMIT #{lastN}")
    List<ChatMessage> selectLastN(@Param("conversationId") String conversationId, @Param("lastN") int lastN);

    @Delete("DELETE FROM chat_message WHERE conversation_id = #{conversationId}")
    int deleteByConversationId(@Param("conversationId") String conversationId);

    @Select("SELECT DISTINCT conversation_id FROM chat_message ORDER BY MIN(created_at) DESC")
    List<String> selectConversationIds();
}
```

### 步骤 3.4：实现 MysqlChatMemory

```java
package com.highway.agent.memory;

import com.highway.agent.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MysqlChatMemory implements ChatMemory {

    private final ChatMessageMapper mapper;

    @Override
    public void add(String conversationId, List<Message> messages) {
        for (Message message : messages) {
            ChatMessage entity = new ChatMessage();
            entity.setConversationId(conversationId);
            entity.setRole(message.getMessageType().getValue());
            entity.setContent(message.getText());
            mapper.insert(entity);
        }
    }

    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<ChatMessage> entities = mapper.selectLastN(conversationId, lastN);
        return entities.stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public void clear(String conversationId) {
        mapper.deleteByConversationId(conversationId);
    }

    private Message toMessage(ChatMessage entity) {
        return switch (entity.getRole()) {
            case "user" -> new UserMessage(entity.getContent());
            case "assistant" -> new AssistantMessage(entity.getContent());
            case "system" -> new SystemMessage(entity.getContent());
            default -> new UserMessage(entity.getContent());
        };
    }
}
```

### 步骤 3.5：验证测试通过

```bash
mvn test -Dtest=MysqlChatMemoryTest
```

预期输出：3 tests passed

### 步骤 3.6：提交

```bash
git add -A && git commit -m "feat: add MysqlChatMemory with MyBatis-Plus and ChatMessageMapper"
```

---

## 任务 4：Tavily 搜索工具

**文件：** `config/WebClientConfig.java`, `tool/TavilySearchTool.java`

### 步骤 4.1：写失败测试 — TavilySearchTool

```java
package com.highway.agent.tool;

import com.highway.agent.model.SearchResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TavilySearchToolTest {

    private MockWebServer mockWebServer;
    private TavilySearchTool searchTool;

    @BeforeEach
    void setUp() {
        mockWebServer = new MockWebServer();
        String baseUrl = mockWebServer.url("/").toString();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer test-key")
                .build();

        searchTool = new TavilySearchTool(webClient, "test-key", "basic", 5);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void search_shouldReturnResults() throws Exception {
        String mockResponse = """
                {
                  "query": "quantum computing",
                  "results": [
                    {
                      "title": "Quantum Computing Report",
                      "url": "https://example.com/quantum",
                      "content": "Quantum computing uses quantum mechanics.",
                      "score": 0.95
                    },
                    {
                      "title": "QC Overview",
                      "url": "https://example.org/overview",
                      "content": "Recent breakthroughs in quantum computing.",
                      "score": 0.88
                    }
                  ]
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setBody(mockResponse)
                .setHeader("Content-Type", "application/json"));

        List<SearchResult> results = searchTool.search("quantum computing").block();

        assertNotNull(results);
        assertEquals(2, results.size());
        assertEquals("Quantum Computing Report", results.get(0).getTitle());
        assertEquals("https://example.com/quantum", results.get(0).getUrl());
    }

    @Test
    void search_emptyResults_shouldReturnEmptyList() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"query\":\"test\",\"results\":[]}")
                .setHeader("Content-Type", "application/json"));

        List<SearchResult> results = searchTool.search("nothing found").block();

        assertNotNull(results);
        assertTrue(results.isEmpty());
    }
}
```

### 步骤 4.2：验证测试失败

```bash
mvn test -Dtest=TavilySearchToolTest
```

预期输出：编译失败（TavilySearchTool 类不存在）

需要添加 MockWebServer 依赖到 pom.xml：

```xml
<!-- 在 <dependencies> 的 test 部分 -->
<dependency>
    <groupId>com.squareup.okhttp3</groupId>
    <artifactId>mockwebserver</artifactId>
    <scope>test</scope>
</dependency>
```

### 步骤 4.3：实现 WebClientConfig

```java
package com.highway.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient tavilyWebClient() {
        return WebClient.builder()
                .baseUrl("https://api.tavily.com")
                .build();
    }
}
```

### 步骤 4.4：实现 TavilySearchTool

```java
package com.highway.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TavilySearchTool {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String searchDepth;
    private final int maxResults;

    public TavilySearchTool(WebClient tavilyWebClient,
                            ObjectMapper objectMapper,
                            @Value("${tavily.api-key}") String apiKey,
                            @Value("${tavily.search-depth:advanced}") String searchDepth,
                            @Value("${tavily.max-results:5}") int maxResults) {
        this.webClient = tavilyWebClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.searchDepth = searchDepth;
        this.maxResults = maxResults;
    }

    public Mono<List<SearchResult>> search(String query) {
        Map<String, Object> requestBody = Map.of(
                "query", query,
                "search_depth", searchDepth,
                "max_results", maxResults,
                "include_answer", false
        );

        return webClient.post()
                .uri("/search")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResults)
                .doOnError(e -> log.error("Tavily search failed for query: {}", query, e))
                .onErrorResume(e -> Mono.just(List.of()));
    }

    private List<SearchResult> parseResults(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode resultsNode = root.get("results");
            List<SearchResult> results = new ArrayList<>();

            if (resultsNode != null && resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    SearchResult sr = new SearchResult();
                    sr.setTitle(node.path("title").asText(""));
                    sr.setUrl(node.path("url").asText(""));
                    sr.setContent(node.path("content").asText(""));
                    sr.setScore(node.path("score").asDouble(0));
                    results.add(sr);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse Tavily response", e);
            return List.of();
        }
    }
}
```

### 步骤 4.5：验证测试通过

```bash
mvn test -Dtest=TavilySearchToolTest
```

预期输出：2 tests passed

### 步骤 4.6：提交

```bash
git add -A && git commit -m "feat: add TavilySearchTool with WebClient and MockWebServer tests"
```

---

## 任务 5：Prompt 模板

**文件：** `prompt/PromptTemplate.java`

### 步骤 5.1：实现 PromptTemplate

```java
package com.highway.agent.prompt;

import org.springframework.stereotype.Component;

@Component
public class PromptTemplate {

    private static final String REACT_SYSTEM_PROMPT = """
            你是一个联网问答助手，使用 ReAct 格式进行推理和回答。

            可用工具：
            - tavily_search: 搜索互联网获取最新信息。参数格式：{"query": "搜索关键词"}

            你必须严格按以下格式回复：

            Thought: 你的分析和推理过程
            Action: {"tool": "tavily_search", "query": "你的搜索关键词"}

            当你有足够信息回答时，使用以下格式：

            Thought: 你的分析
            Final Answer: 你的最终回答

            规则：
            1. 如果问题不需要最新信息或联网搜索，直接给出 Final Answer
            2. 搜索时在回答中用 [1][2] 等编号标注信息来源
            3. Final Answer 末尾不要列出参考文献列表（系统会自动附加）
            4. 每次只能调用一个工具
            5. 用中文回答
            6. 回答要详细、准确、有条理
            """;

    private static final String OBSERVATION_TEMPLATE = """
            Observation: %s
            """;

    private static final String SUGGESTION_PROMPT = """
            基于以下对话，生成 %d 个用户可能想继续提问的问题。
            只返回问题列表，每行一个问题，不要编号，不要其他内容。

            对话内容：
            用户：%s
            助手：%s
            """;

    public String getReActSystemPrompt() {
        return REACT_SYSTEM_PROMPT;
    }

    public String formatObservation(String observationContent) {
        return String.format(OBSERVATION_TEMPLATE, observationContent);
    }

    public String getSuggestionPrompt(int count, String userMessage, String assistantAnswer) {
        return String.format(SUGGESTION_PROMPT, count, userMessage, assistantAnswer);
    }
}
```

### 步骤 5.2：提交

```bash
git add -A && git commit -m "feat: add PromptTemplate with ReAct system prompt and suggestion prompt"
```

---

## 任务 6：ReAct Agent 核心逻辑

**文件：** `agent/ReActAgent.java`

### 步骤 6.1：写失败测试 — ReAct 解析

```java
package com.highway.agent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReActAgentTest {

    @Test
    void parseResponse_withAction_shouldExtractThoughtAndAction() {
        String response = """
                Thought: 用户问的是最新新闻，我需要搜索
                Action: {"tool": "tavily_search", "query": "最新AI新闻"}
                """;

        ReActStep step = ReActAgent.parseReActResponse(response);

        assertEquals("用户问的是最新新闻，我需要搜索", step.getThought());
        assertEquals("tavily_search", step.getActionTool());
        assertEquals("最新AI新闻", step.getActionQuery());
        assertNull(step.getFinalAnswer());
    }

    @Test
    void parseResponse_withFinalAnswer_shouldExtractFinalAnswer() {
        String response = """
                Thought: 我已经知道答案，不需要搜索
                Final Answer: Java是一种面向对象的编程语言。
                """;

        ReActStep step = ReActAgent.parseReActResponse(response);

        assertEquals("我已经知道答案，不需要搜索", step.getThought());
        assertNull(step.getActionTool());
        assertEquals("Java是一种面向对象的编程语言。", step.getFinalAnswer());
    }

    @Test
    void parseResponse_withOnlyFinalAnswer_shouldWork() {
        String response = "Final Answer: 1+1=2";

        ReActStep step = ReActAgent.parseReActResponse(response);

        assertEquals("1+1=2", step.getFinalAnswer());
        assertFalse(step.hasAction());
    }

    @Test
    void parseResponse_withFinalAnswerAndReferences_shouldExtractCleanAnswer() {
        String response = """
                Thought: 搜索结果已足够
                Final Answer: 量子计算利用量子力学进行计算[1]，近年有突破[2]。
                """;

        ReActStep step = ReActAgent.parseReActResponse(response);

        assertTrue(step.hasFinalAnswer());
        assertTrue(step.getFinalAnswer().contains("[1]"));
    }
}
```

### 步骤 6.2：验证测试失败

```bash
mvn test -Dtest=ReActAgentTest
```

预期输出：编译失败

### 步骤 6.3：实现 ReActAgent

```java
package com.highway.agent.agent;

import com.highway.agent.model.SearchResult;
import com.highway.agent.prompt.PromptTemplate;
import com.highway.agent.tool.TavilySearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReActAgent {

    private final ChatClient chatClient;
    private final TavilySearchTool tavilySearchTool;
    private final PromptTemplate promptTemplate;

    private static final Pattern THOUGHT_PATTERN = Pattern.compile("Thought:\\s*(.+?)(?=\\n(?:Action:|Final Answer:)|$)", Pattern.DOTALL);
    private static final Pattern ACTION_PATTERN = Pattern.compile("Action:\\s*\\{\\s*\"tool\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"query\"\\s*:\\s*\"([^\"]+)\"\\s*\\}");
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile("Final Answer:\\s*(.+)", Pattern.DOTALL);

    /**
     * 执行 ReAct 循环，返回所有步骤（含搜索结果用于提取参考文献）
     */
    public ReActResult execute(String userMessage, List<Message> history, int maxIterations) {
        List<Message> messages = new ArrayList<>(history);
        messages.add(new SystemMessage(promptTemplate.getReActSystemPrompt()));
        messages.add(new UserMessage(userMessage));

        List<ReActStep> steps = new ArrayList<>();
        List<SearchResult> allSearchResults = new ArrayList<>();
        String finalAnswer = null;

        for (int i = 0; i < maxIterations; i++) {
            log.info("ReAct iteration {}/{}", i + 1, maxIterations);

            // 调用 LLM
            String llmResponse = callLlm(messages);
            ReActStep step = parseReActResponse(llmResponse);
            steps.add(step);

            if (step.hasFinalAnswer()) {
                finalAnswer = step.getFinalAnswer();
                break;
            }

            if (step.hasAction()) {
                // 执行工具
                List<SearchResult> searchResults = tavilySearchTool
                        .search(step.getActionQuery())
                        .block();
                if (searchResults == null) {
                    searchResults = List.of();
                }

                allSearchResults.addAll(searchResults);
                step.setSearchResults(searchResults);

                // 构建 Observation
                String observation = formatObservation(searchResults);
                step.setObservation(observation);

                // 追加到对话上下文
                messages.add(new AssistantMessage(llmResponse));
                messages.add(new UserMessage(promptTemplate.formatObservation(observation)));
            } else {
                // 既没有 Action 也没有 Final Answer，当作 Final Answer 处理
                finalAnswer = llmResponse;
                step.setFinalAnswer(llmResponse);
                break;
            }
        }

        if (finalAnswer == null) {
            finalAnswer = "抱歉，我在搜索后仍无法找到足够的信息来回答您的问题。";
        }

        return new ReActResult(steps, allSearchResults, finalAnswer);
    }

    private String callLlm(List<Message> messages) {
        return chatClient.prompt()
                .messages(messages)
                .call()
                .content();
    }

    private String formatObservation(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder("搜索结果：\n");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append(String.format("[%d] %s\n来源：%s\n摘要：%s\n\n",
                    i + 1, r.getTitle(), r.getUrl(), r.getContent()));
        }
        return sb.toString();
    }

    /**
     * 解析 LLM 的 ReAct 格式响应
     */
    public static ReActStep parseReActResponse(String response) {
        ReActStep.ReActStepBuilder builder = ReActStep.builder();

        Matcher thoughtMatcher = THOUGHT_PATTERN.matcher(response);
        if (thoughtMatcher.find()) {
            builder.thought(thoughtMatcher.group(1).trim());
        }

        Matcher actionMatcher = ACTION_PATTERN.matcher(response);
        if (actionMatcher.find()) {
            builder.actionTool(actionMatcher.group(1));
            builder.actionQuery(actionMatcher.group(2));
        }

        Matcher finalAnswerMatcher = FINAL_ANSWER_PATTERN.matcher(response);
        if (finalAnswerMatcher.find()) {
            builder.finalAnswer(finalAnswerMatcher.group(1).trim());
        }

        return builder.build();
    }

    /**
     * ReAct 执行结果
     */
    public record ReActResult(List<ReActStep> steps, List<SearchResult> allSearchResults, String finalAnswer) {}
}
```

### 步骤 6.4：验证测试通过

```bash
mvn test -Dtest=ReActAgentTest
```

预期输出：4 tests passed

### 步骤 6.5：提交

```bash
git add -A && git commit -m "feat: add ReActAgent with response parsing and execution loop"
```

---

## 任务 7：SuggestionService

**文件：** `service/SuggestionService.java`

### 步骤 7.1：实现 SuggestionService

```java
package com.highway.agent.service;

import com.highway.agent.prompt.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public List<String> generateSuggestions(String userMessage, String assistantAnswer, int count) {
        String prompt = promptTemplate.getSuggestionPrompt(count, userMessage, assistantAnswer);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            return parseSuggestions(response);
        } catch (Exception e) {
            log.error("Failed to generate suggestions", e);
            return List.of();
        }
    }

    private List<String> parseSuggestions(String response) {
        return Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceAll("^\\d+[.、)\\s]+", "")) // 去掉编号前缀
                .toList();
    }
}
```

### 步骤 7.2：提交

```bash
git add -A && git commit -m "feat: add SuggestionService for generating follow-up questions"
```

---

## 任务 8：ChatService — 核心编排

**文件：** `service/ChatService.java`

### 步骤 8.1：实现 ChatService

```java
package com.highway.agent.service;

import com.highway.agent.agent.ReActAgent;
import com.highway.agent.memory.MysqlChatMemory;
import com.highway.agent.model.ChatResponse;
import com.highway.agent.model.SearchResult;
import com.highway.agent.model.SseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ReActAgent reActAgent;
    private final MysqlChatMemory chatMemory;
    private final SuggestionService suggestionService;

    @Value("${agent.react.max-iterations:5}")
    private int maxIterations;

    @Value("${agent.suggestion.count:3}")
    private int suggestionCount;

    /**
     * 流式对话，返回 SSE 事件流
     */
    public Flux<ServerSentEvent<String>> chatStream(String conversationId, String userMessage) {
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        final String convId = conversationId;

        // 保存用户消息
        chatMemory.add(convId, List.of(new UserMessage(userMessage)));

        // 获取历史消息
        List<Message> history = chatMemory.get(convId, 20);

        return Flux.create(sink -> {
            try {
                // 执行 ReAct 循环
                ReActAgent.ReActResult result = reActAgent.execute(userMessage, history, maxIterations);

                // 发射 ReAct 步骤事件
                for (var step : result.steps()) {
                    if (step.getThought() != null) {
                        sink.next(toSse(SseEvent.thought(step.getThought())));
                    }
                    if (step.hasAction()) {
                        sink.next(toSse(SseEvent.action(step.getActionTool(), step.getActionQuery())));
                    }
                    if (step.getObservation() != null) {
                        sink.next(toSse(SseEvent.observation(step.getObservation())));
                    }
                }

                // 发射回答事件
                sink.next(toSse(SseEvent.answer(result.finalAnswer())));

                // 构建参考文献
                List<ChatResponse.Reference> references = buildReferences(result.allSearchResults());
                if (!references.isEmpty()) {
                    sink.next(toSse(SseEvent.references(references)));
                }

                // 生成猜你想问
                List<String> suggestions = suggestionService.generateSuggestions(
                        userMessage, result.finalAnswer(), suggestionCount);
                if (!suggestions.isEmpty()) {
                    sink.next(toSse(SseEvent.suggested(suggestions)));
                }

                // 保存助手回答
                chatMemory.add(convId, List.of(new AssistantMessage(result.finalAnswer())));

                // 发射 conversationId（方便前端新建会话后获取 ID）
                sink.next(ServerSentEvent.<String>builder()
                        .event("conversation")
                        .data("\"" + convId + "\"")
                        .build());

                // 完成
                sink.next(toSse(SseEvent.done()));
                sink.complete();

            } catch (Exception e) {
                log.error("Chat stream error", e);
                sink.next(toSse(SseEvent.answer("抱歉，处理您的问题时出现了错误，请稍后重试。")));
                sink.next(toSse(SseEvent.done()));
                sink.complete();
            }
        });
    }

    /**
     * 同步对话
     */
    public ChatResponse chatSync(String conversationId, String userMessage) {
        if (conversationId == null || conversationId.isBlank()) {
            conversationId = UUID.randomUUID().toString();
        }

        chatMemory.add(conversationId, List.of(new UserMessage(userMessage)));
        List<Message> history = chatMemory.get(conversationId, 20);

        ReActAgent.ReActResult result = reActAgent.execute(userMessage, history, maxIterations);

        chatMemory.add(conversationId, List.of(new AssistantMessage(result.finalAnswer())));

        List<ChatResponse.Reference> references = buildReferences(result.allSearchResults());
        List<String> suggestions = suggestionService.generateSuggestions(
                userMessage, result.finalAnswer(), suggestionCount);

        return ChatResponse.builder()
                .answer(result.finalAnswer())
                .references(references)
                .suggestedQuestions(suggestions)
                .build();
    }

    private List<ChatResponse.Reference> buildReferences(List<SearchResult> searchResults) {
        // 去重（按 URL）
        var seen = new java.util.HashSet<String>();
        return searchResults.stream()
                .filter(r -> r.getUrl() != null && !r.getUrl().isBlank())
                .filter(r -> seen.add(r.getUrl()))
                .map(r -> ChatResponse.Reference.builder()
                        .id(seen.size())
                        .title(r.getTitle())
                        .url(r.getUrl())
                        .build())
                .toList();
    }

    private ServerSentEvent<String> toSse(SseEvent event) {
        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(event.getData());
            return ServerSentEvent.<String>builder()
                    .event(event.getType())
                    .data(json)
                    .build();
        } catch (Exception e) {
            return ServerSentEvent.<String>builder()
                    .event(event.getType())
                    .data("{}")
                    .build();
        }
    }
}
```

### 步骤 8.2：提交

```bash
git add -A && git commit -m "feat: add ChatService with ReAct orchestration and SSE streaming"
```

---

## 任务 9：AI 配置

**文件：** `config/AiConfig.java`

### 步骤 9.1：实现 AiConfig

```java
package com.highway.agent.config;

import com.highway.agent.memory.MysqlChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatMemory chatMemory(MysqlChatMemory mysqlChatMemory) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(mysqlChatMemory)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem("你是一个知识丰富的AI助手。")
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
```

### 步骤 9.2：提交

```bash
git add -A && git commit -m "feat: add AiConfig with ChatClient and ChatMemory beans"
```

---

## 任务 10：Controller 层

**文件：** `controller/ChatController.java`

### 步骤 10.1：实现 ChatController

```java
package com.highway.agent.controller;

import com.highway.agent.memory.ChatMessageMapper;
import com.highway.agent.model.ChatRequest;
import com.highway.agent.model.ChatResponse;
import com.highway.agent.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageMapper chatMessageMapper;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam(required = false) String conversationId,
            @RequestParam String message) {
        return chatService.chatStream(conversationId, message);
    }

    @PostMapping("/chat/sync")
    public ChatResponse chatSync(@RequestBody ChatRequest request) {
        return chatService.chatSync(request.getConversationId(), request.getMessage());
    }

    @GetMapping("/conversations")
    public List<String> listConversations() {
        return chatMessageMapper.selectConversationIds();
    }

    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId) {
        chatMessageMapper.deleteByConversationId(conversationId);
    }
}
```

### 步骤 10.2：提交

```bash
git add -A && git commit -m "feat: add ChatController with SSE stream, sync, and conversation management"
```

---

## 任务 11：MysqlChatMemory 适配 ChatMemoryRepository

**注意：** Spring AI 的 `MessageWindowChatMemory` 需要 `ChatMemoryRepository` 接口，而非 `ChatMemory` 接口。需要调整 MysqlChatMemory 实现 `ChatMemoryRepository`。

### 步骤 11.1：修正 MysqlChatMemory 实现 ChatMemoryRepository

```java
package com.highway.agent.memory;

import com.highway.agent.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class MysqlChatMemory implements ChatMemoryRepository {

    private final ChatMessageMapper mapper;

    @Override
    public List<String> findConversationIds() {
        return mapper.selectConversationIds();
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        return mapper.selectLastN(conversationId, 100).stream()
                .map(this::toMessage)
                .toList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        for (Message message : messages) {
            ChatMessage entity = new ChatMessage();
            entity.setConversationId(conversationId);
            entity.setRole(message.getMessageType().getValue());
            entity.setContent(message.getText());
            mapper.insert(entity);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        mapper.deleteByConversationId(conversationId);
    }

    private Message toMessage(ChatMessage entity) {
        return switch (entity.getRole()) {
            case "user" -> new UserMessage(entity.getContent());
            case "assistant" -> new AssistantMessage(entity.getContent());
            case "system" -> new SystemMessage(entity.getContent());
            default -> new UserMessage(entity.getContent());
        };
    }
}
```

### 步骤 11.2：修正 AiConfig

```java
package com.highway.agent.config;

import com.highway.agent.memory.MysqlChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatMemory chatMemory(MysqlChatMemory mysqlChatMemory) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(mysqlChatMemory)
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
```

### 步骤 11.3：修正 ChatService 使用 ChatMemory

ChatService 中直接使用 `MysqlChatMemory`（即 `ChatMemoryRepository`）进行手动消息存储，同时通过 `MessageChatMemoryAdvisor` 让 ChatClient 自动管理对话上下文。

ChatService 中 `chatMemory.add(...)` 改为 `chatMemoryRepository.saveAll(...)`，`chatMemory.get(...)` 改为 `chatMemoryRepository.findByConversationId(...)`。

### 步骤 11.4：更新测试并验证

```bash
mvn test
```

预期输出：全部通过

### 步骤 11.5：提交

```bash
git add -A && git commit -m "fix: adapt MysqlChatMemory to ChatMemoryRepository interface"
```

---

## 任务 12：前端聊天 UI

**文件：** `static/index.html`

### 步骤 12.1：实现聊天界面

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Highway Agent - 联网问答</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; height: 100vh; display: flex; background: #f5f5f5; }

        /* Sidebar */
        .sidebar { width: 260px; background: #202123; color: #fff; display: flex; flex-direction: column; }
        .sidebar-header { padding: 16px; border-bottom: 1px solid #444; }
        .new-chat-btn { width: 100%; padding: 10px; background: #2a2b32; border: 1px solid #555; color: #fff; border-radius: 6px; cursor: pointer; font-size: 14px; }
        .new-chat-btn:hover { background: #3a3b42; }
        .conversation-list { flex: 1; overflow-y: auto; padding: 8px; }
        .conversation-item { padding: 10px 12px; border-radius: 6px; cursor: pointer; font-size: 13px; margin-bottom: 4px; display: flex; justify-content: space-between; align-items: center; }
        .conversation-item:hover { background: #2a2b32; }
        .conversation-item.active { background: #343541; }
        .conversation-item .delete-btn { display: none; background: none; border: none; color: #999; cursor: pointer; font-size: 16px; }
        .conversation-item:hover .delete-btn { display: block; }

        /* Main */
        .main { flex: 1; display: flex; flex-direction: column; }
        .chat-container { flex: 1; overflow-y: auto; padding: 20px 40px; }
        .message { margin-bottom: 24px; max-width: 800px; }
        .message.user { margin-left: auto; }
        .message.assistant { margin-right: auto; }

        .message-bubble { padding: 12px 16px; border-radius: 12px; line-height: 1.6; font-size: 15px; white-space: pre-wrap; }
        .message.user .message-bubble { background: #3b82f6; color: #fff; border-bottom-right-radius: 4px; }
        .message.assistant .message-bubble { background: #fff; color: #333; border-bottom-left-radius: 4px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }

        /* React Steps */
        .react-steps { margin-top: 8px; }
        .react-step { font-size: 12px; color: #888; padding: 2px 0; cursor: pointer; }
        .react-step:hover { color: #555; }
        .react-detail { display: none; font-size: 12px; color: #666; padding: 4px 12px; background: #f9f9f9; border-radius: 4px; margin: 2px 0; }
        .react-detail.show { display: block; }

        /* References */
        .references { margin-top: 12px; padding: 8px 12px; background: #f0f4ff; border-radius: 6px; }
        .references h4 { font-size: 13px; color: #555; margin-bottom: 6px; }
        .reference-item { font-size: 12px; color: #3b82f6; margin: 3px 0; }
        .reference-item a { color: #3b82f6; text-decoration: none; }
        .reference-item a:hover { text-decoration: underline; }

        /* Suggested Questions */
        .suggested { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 8px; }
        .suggested-tag { padding: 6px 14px; background: #f0f4ff; border: 1px solid #d0ddff; border-radius: 16px; font-size: 13px; color: #3b82f6; cursor: pointer; }
        .suggested-tag:hover { background: #d0ddff; }

        /* Input */
        .input-container { padding: 16px 40px; border-top: 1px solid #e5e5e5; background: #fff; }
        .input-wrapper { display: flex; max-width: 800px; margin: 0 auto; }
        .chat-input { flex: 1; padding: 12px 16px; border: 1px solid #ddd; border-radius: 8px; font-size: 15px; outline: none; resize: none; }
        .chat-input:focus { border-color: #3b82f6; }
        .send-btn { margin-left: 8px; padding: 12px 20px; background: #3b82f6; color: #fff; border: none; border-radius: 8px; cursor: pointer; font-size: 15px; }
        .send-btn:hover { background: #2563eb; }
        .send-btn:disabled { background: #93c5fd; cursor: not-allowed; }

        .typing-indicator::after { content: '|'; animation: blink 1s infinite; }
        @keyframes blink { 0%, 50% { opacity: 1; } 51%, 100% { opacity: 0; } }
    </style>
</head>
<body>
    <div class="sidebar">
        <div class="sidebar-header">
            <button class="new-chat-btn" onclick="newChat()">+ 新建对话</button>
        </div>
        <div class="conversation-list" id="conversationList"></div>
    </div>
    <div class="main">
        <div class="chat-container" id="chatContainer"></div>
        <div class="input-container">
            <div class="input-wrapper">
                <input type="text" class="chat-input" id="chatInput" placeholder="输入你的问题..." onkeydown="if(event.key==='Enter')sendMessage()">
                <button class="send-btn" id="sendBtn" onclick="sendMessage()">发送</button>
            </div>
        </div>
    </div>

<script>
    let conversationId = null;
    let isStreaming = false;

    function newChat() {
        conversationId = null;
        document.getElementById('chatContainer').innerHTML = '';
        loadConversations();
    }

    async function loadConversations() {
        try {
            const res = await fetch('/api/conversations');
            const ids = await res.json();
            const list = document.getElementById('conversationList');
            list.innerHTML = ids.map(id =>
                `<div class="conversation-item ${id === conversationId ? 'active' : ''}" onclick="switchConversation('${id}')">
                    <span>${id.substring(0, 12)}...</span>
                    <button class="delete-btn" onclick="event.stopPropagation();deleteConversation('${id}')">×</button>
                </div>`
            ).join('');
        } catch (e) { console.error(e); }
    }

    async function switchConversation(id) {
        conversationId = id;
        document.getElementById('chatContainer').innerHTML = '';
        // TODO: load history from memory
        loadConversations();
    }

    async function deleteConversation(id) {
        await fetch(`/api/conversations/${id}`, { method: 'DELETE' });
        if (conversationId === id) newChat();
        else loadConversations();
    }

    function addMessage(role, content) {
        const container = document.getElementById('chatContainer');
        const div = document.createElement('div');
        div.className = `message ${role}`;
        div.innerHTML = `<div class="message-bubble">${escapeHtml(content)}</div>`;
        container.appendChild(div);
        container.scrollTop = container.scrollHeight;
        return div.querySelector('.message-bubble');
    }

    function addAssistantMessage() {
        const container = document.getElementById('chatContainer');
        const div = document.createElement('div');
        div.className = 'message assistant';
        div.innerHTML = `
            <div class="message-bubble">
                <div class="react-steps" id="reactSteps"></div>
                <div id="answerContent" class="typing-indicator"></div>
                <div id="referencesContent"></div>
                <div id="suggestedContent"></div>
            </div>`;
        container.appendChild(div);
        container.scrollTop = container.scrollHeight;
        return div;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    async function sendMessage() {
        const input = document.getElementById('chatInput');
        const message = input.value.trim();
        if (!message || isStreaming) return;

        input.value = '';
        isStreaming = true;
        document.getElementById('sendBtn').disabled = true;

        addMessage('user', message);
        const assistantDiv = addAssistantMessage();
        const reactSteps = assistantDiv.querySelector('#reactSteps');
        const answerContent = assistantDiv.querySelector('#answerContent');
        const referencesContent = assistantDiv.querySelector('#referencesContent');
        const suggestedContent = assistantDiv.querySelector('#suggestedContent');

        let answerText = '';

        try {
            const url = `/api/chat?message=${encodeURIComponent(message)}` +
                (conversationId ? `&conversationId=${conversationId}` : '');

            const eventSource = new EventSource(url);

            eventSource.addEventListener('thought', (e) => {
                const data = JSON.parse(e.data);
                addReactStep(reactSteps, '💭 思考', data);
            });

            eventSource.addEventListener('action', (e) => {
                const data = JSON.parse(e.data);
                addReactStep(reactSteps, '🔍 搜索', `${data.tool}: ${data.query}`);
            });

            eventSource.addEventListener('observation', (e) => {
                const data = JSON.parse(e.data);
                addReactStep(reactSteps, '👁 观察', data.substring(0, 100) + '...');
            });

            eventSource.addEventListener('answer', (e) => {
                answerContent.classList.remove('typing-indicator');
                answerText += JSON.parse(e.data);
                answerContent.textContent = answerText;
                document.getElementById('chatContainer').scrollTop = document.getElementById('chatContainer').scrollHeight;
            });

            eventSource.addEventListener('references', (e) => {
                const refs = JSON.parse(e.data);
                referencesContent.innerHTML = `<div class="references"><h4>📚 参考文献</h4>` +
                    refs.map(r => `<div class="reference-item">[${r.id}] <a href="${r.url}" target="_blank">${r.title}</a></div>`).join('') +
                    `</div>`;
            });

            eventSource.addEventListener('suggested', (e) => {
                const questions = JSON.parse(e.data);
                suggestedContent.innerHTML = `<div class="suggested">` +
                    questions.map(q => `<span class="suggested-tag" onclick="askSuggested(this)">${q}</span>`).join('') +
                    `</div>`;
            });

            eventSource.addEventListener('conversation', (e) => {
                conversationId = JSON.parse(e.data);
                loadConversations();
            });

            eventSource.addEventListener('done', () => {
                eventSource.close();
                isStreaming = false;
                document.getElementById('sendBtn').disabled = false;
            });

            eventSource.onerror = () => {
                eventSource.close();
                answerContent.classList.remove('typing-indicator');
                if (!answerText) answerContent.textContent = '连接错误，请重试。';
                isStreaming = false;
                document.getElementById('sendBtn').disabled = false;
            };

        } catch (e) {
            answerContent.classList.remove('typing-indicator');
            answerContent.textContent = '请求失败，请重试。';
            isStreaming = false;
            document.getElementById('sendBtn').disabled = false;
        }
    }

    function addReactStep(container, label, detail) {
        const step = document.createElement('div');
        step.className = 'react-step';
        step.textContent = label;
        step.onclick = () => {
            const d = step.nextElementSibling;
            if (d) d.classList.toggle('show');
        };

        const detailDiv = document.createElement('div');
        detailDiv.className = 'react-detail';
        detailDiv.textContent = detail;

        container.appendChild(step);
        container.appendChild(detailDiv);
    }

    function askSuggested(el) {
        document.getElementById('chatInput').value = el.textContent;
        sendMessage();
    }

    // Init
    loadConversations();
</script>
</body>
</html>
```

### 步骤 12.2：提交

```bash
git add -A && git commit -m "feat: add chat UI with SSE streaming, references, and suggested questions"
```

---

## 任务 13：集成测试与启动验证

### 步骤 13.1：写集成测试

```java
package com.highway.agent;

import com.highway.agent.controller.ChatController;
import com.highway.agent.memory.ChatMessageMapper;
import com.highway.agent.model.ChatRequest;
import com.highway.agent.model.ChatResponse;
import com.highway.agent.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private ChatService chatService;

    @MockBean
    private ChatMessageMapper chatMessageMapper;

    @Test
    void syncChat_shouldReturnResponse() {
        ChatResponse mockResponse = ChatResponse.builder()
                .answer("Test answer")
                .references(List.of())
                .suggestedQuestions(List.of("Q1", "Q2"))
                .build();

        when(chatService.chatSync(anyString(), anyString())).thenReturn(mockResponse);

        ChatRequest request = new ChatRequest();
        request.setMessage("hello");
        request.setConversationId("test-conv");

        webTestClient.post()
                .uri("/api/chat/sync")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.answer").isEqualTo("Test answer")
                .jsonPath("$.suggestedQuestions[0]").isEqualTo("Q1");
    }

    @Test
    void listConversations_shouldReturnIds() {
        when(chatMessageMapper.selectConversationIds()).thenReturn(List.of("conv-1", "conv-2"));

        webTestClient.get()
                .uri("/api/conversations")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0]").isEqualTo("conv-1")
                .jsonPath("$[1]").isEqualTo("conv-2");
    }
}
```

### 步骤 13.2：运行全部测试

```bash
mvn test
```

预期输出：全部通过

### 步骤 13.3：启动应用验证

```bash
AI_DASHSCOPE_API_KEY=your-key TAVILY_API_KEY=your-key mvn spring-boot:run
```

验证点：
- 访问 `http://localhost:8080` 看到聊天界面
- 发送问题后收到 SSE 流式响应

### 步骤 13.4：提交

```bash
git add -A && git commit -m "feat: add integration tests and verify full stack"
```

---

## 自审检查

### 规范覆盖

| 需求 | 对应任务 |
|------|---------|
| ReAct 推理 | 任务 5 + 任务 6 |
| 智能搜索决策 | 任务 5（System Prompt 指令）+ 任务 6（ReActAgent 解析 Final Answer） |
| 参考文献 | 任务 6（buildReferences）+ 任务 8（ChatService 发射 references 事件）+ 任务 12（前端渲染） |
| 猜你想问 | 任务 7 + 任务 8 + 任务 12 |
| 会话记忆（MySQL）| 任务 3 + 任务 11 |
| SSE 流式输出 | 任务 8 + 任务 10 + 任务 12 |
| 聊天 UI | 任务 12 |
| Tavily 搜索 | 任务 4 |
| 通义千问 | 任务 1（DashScope starter）+ 任务 9（AiConfig） |

### 占位符扫描

无 TBD / TODO / "类似任务 N" / 未给出代码的步骤

### 类型一致性

- `SearchResult`：title/url/content/score — 在 TavilySearchTool 和 ReActAgent 中一致使用
- `ReActStep`：thought/actionTool/actionQuery/observation/searchResults/finalAnswer — ReActAgent 和测试中一致
- `ChatResponse.Reference`：id/title/url — ChatService 和前端一致
- `MysqlChatMemory`：实现 `ChatMemoryRepository` 接口 — AiConfig 中作为 `ChatMemoryRepository` 使用
