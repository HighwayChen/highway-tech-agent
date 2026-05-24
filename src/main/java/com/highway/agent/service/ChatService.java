package com.highway.agent.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.memory.MysqlChatMemory;
import com.highway.agent.model.ChatResponse;
import com.highway.agent.model.SearchResult;
import com.highway.agent.model.SseEvent;
import com.highway.agent.tool.TavilySearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import reactor.core.publisher.Sinks;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ReactAgent reactAgent;
    private final TavilySearchTool tavilySearchTool;
    private final MysqlChatMemory chatMemoryRepository;
    private final SuggestionService suggestionService;
    private final ObjectMapper objectMapper;

    @Value("${agent.suggestion.count:3}")
    private int suggestionCount;

    /**
     * 跟踪活跃的流式会话，用于支持中断输出
     */
    private final ConcurrentHashMap<String, Sinks.Empty<Void>> activeStreams = new ConcurrentHashMap<>();

    /**
     * 停止指定会话的流式输出
     */
    public boolean stopStream(String conversationId) {
        Sinks.Empty<Void> stopSignal = activeStreams.remove(conversationId);
        if (stopSignal != null) {
            stopSignal.tryEmitEmpty();
            log.info("Stream stopped for conversation: {}", conversationId);
            return true;
        }
        return false;
    }

    /**
     * 流式对话，返回 SSE 事件流
     */
    public Flux<ServerSentEvent<String>> chatStream(String conversationId, String userMessage, boolean skipSaveUser) {
        String convId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;

        if (!skipSaveUser) {
            chatMemoryRepository.saveAll(convId, List.of(new UserMessage(userMessage)));
        }

        // 加载历史对话，拼上当前消息一起传给 Agent
        List<Message> history = chatMemoryRepository.findByConversationId(convId);

        StringBuilder answerBuilder = new StringBuilder();

        // 创建中断信号
        Sinks.Empty<Void> stopSignal = Sinks.empty();
        activeStreams.put(convId, stopSignal);

        // 1) 核心流：直接透传 NodeOutput，前端按 outputType 解析
        Flux<ServerSentEvent<String>> answerFlux;
        try {
            answerFlux = reactAgent.stream(history)
                    .subscribeOn(Schedulers.boundedElastic())
                    .ofType(StreamingOutput.class)
                    .takeUntilOther(stopSignal.asMono())
                    .map(sout -> {
                        // 累积最终回答文本，用于后续 suggestions 和存档
                        if (sout.getOutputType() == OutputType.AGENT_MODEL_STREAMING
                                && sout.message() instanceof AssistantMessage am) {
                            String text = am.getText();
                            if (text != null && !text.isEmpty()) {
                                answerBuilder.append(text);
                            }
                        }
                        return toNodeSse(sout);
                    });
        } catch (GraphRunnerException e) {
            log.error("Failed to start stream", e);
            activeStreams.remove(convId);
            return Flux.just(toErrorSse("处理时出现错误，请重试。"), toSse(SseEvent.done()));
        }

        // 2) 收尾事件流：references → suggestions → conversation → done
        Flux<ServerSentEvent<String>> postFlux = Flux.defer(() -> {
            String finalAnswer = answerBuilder.toString();
            chatMemoryRepository.saveAll(convId, List.of(new AssistantMessage(finalAnswer)));

            List<ServerSentEvent<String>> events = new ArrayList<>();

            List<ChatResponse.Reference> references = buildReferences(tavilySearchTool.drainSearchResults());
            if (!references.isEmpty()) {
                events.add(toSse(SseEvent.references(references)));
            }

            List<String> suggestions = suggestionService.generateSuggestions(userMessage, finalAnswer, suggestionCount);
            if (!suggestions.isEmpty()) {
                events.add(toSse(SseEvent.suggested(suggestions)));
            }

            events.add(ServerSentEvent.<String>builder().event("conversation").data("\"" + convId + "\"").build());
            events.add(toSse(SseEvent.done()));

            return Flux.fromIterable(events);
        });

        // 3) 拼接：answer 流结束后接收尾流，统一兜底错误处理
        return Flux.concat(answerFlux, postFlux)
                .doFinally(signal -> activeStreams.remove(convId))
                .onErrorResume(e -> {
                    log.error("Stream error", e);
                    return Flux.just(toErrorSse("处理时出现错误，请重试。"), toSse(SseEvent.done()));
                });
    }

    /**
     * 同步对话
     */
    public ChatResponse chatSync(String conversationId, String userMessage) {
        String convId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;

        chatMemoryRepository.saveAll(convId, List.of(new UserMessage(userMessage)));

        List<Message> history = chatMemoryRepository.findByConversationId(convId);

        String finalAnswer;
        try {
            finalAnswer = reactAgent.call(history).getText();
        } catch (GraphRunnerException e) {
            log.error("Agent call failed", e);
            throw new RuntimeException("Agent call failed", e);
        }

        chatMemoryRepository.saveAll(convId, List.of(new AssistantMessage(finalAnswer)));

        return ChatResponse.builder()
                .answer(finalAnswer)
                .references(buildReferences(tavilySearchTool.drainSearchResults()))
                .suggestedQuestions(suggestionService.generateSuggestions(userMessage, finalAnswer, suggestionCount))
                .build();
    }

    /**
     * 将 StreamingOutput 转为前端可解析的 SSE 事件
     * 不能直接序列化 StreamingOutput（Message 接口多态，Jackson 会丢失数据），
     * 所以只提取前端需要的字段组成简单 Map
     */
    private ServerSentEvent<String> toNodeSse(StreamingOutput sout) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("outputType", sout.getOutputType().name());

        if (sout.message() instanceof AssistantMessage am) {
            Map<String, Object> msg = new HashMap<>();
            msg.put("text", am.getText());
            msg.put("hasToolCalls", am.hasToolCalls());
            if (am.hasToolCalls()) {
                List<Map<String, String>> toolCalls = new ArrayList<>();
                for (AssistantMessage.ToolCall tc : am.getToolCalls()) {
                    Map<String, String> tcMap = new HashMap<>();
                    tcMap.put("name", tc.name());
                    tcMap.put("arguments", tc.arguments());
                    toolCalls.add(tcMap);
                }
                msg.put("toolCalls", toolCalls);
            }
            payload.put("message", msg);
        } else if (sout.message() instanceof ToolResponseMessage trm) {
            List<String> responses = new ArrayList<>();
            for (ToolResponseMessage.ToolResponse resp : trm.getResponses()) {
                String content = resp.responseData();
                if (content != null && content.length() > 500) {
                    content = content.substring(0, 500) + "...";
                }
                responses.add(content != null ? content : "");
            }
            payload.put("message", Map.of("responses", responses));
        } else {
            // chunk 字段作为兜底
            payload.put("chunk", sout.chunk());
        }

        try {
            return ServerSentEvent.<String>builder()
                    .event("node")
                    .data(objectMapper.writeValueAsString(payload))
                    .build();
        } catch (Exception e) {
            log.warn("Failed to serialize node payload", e);
            return ServerSentEvent.<String>builder()
                    .event("node")
                    .data("{}")
                    .build();
        }
    }

    /**
     * 错误时发送一个简单的 answer 类型 node 事件
     */
    private ServerSentEvent<String> toErrorSse(String text) {
        return ServerSentEvent.<String>builder()
                .event("node")
                .data("{\"outputType\":\"ERROR\",\"message\":\"" + text.replace("\"", "\\\"") + "\"}")
                .build();
    }

    private List<ChatResponse.Reference> buildReferences(List<SearchResult> searchResults) {
        var seen = new HashSet<String>();
        List<ChatResponse.Reference> refs = new ArrayList<>();
        for (SearchResult r : searchResults) {
            if (r.getUrl() != null && !r.getUrl().isBlank() && seen.add(r.getUrl())) {
                refs.add(ChatResponse.Reference.builder()
                        .id(refs.size() + 1)
                        .title(r.getTitle())
                        .url(r.getUrl())
                        .build());
            }
        }
        return refs;
    }

    private ServerSentEvent<String> toSse(SseEvent event) {
        try {
            return ServerSentEvent.<String>builder()
                    .event(event.getType())
                    .data(objectMapper.writeValueAsString(event.getData()))
                    .build();
        } catch (Exception e) {
            return ServerSentEvent.<String>builder()
                    .event(event.getType())
                    .data("{}")
                    .build();
        }
    }
}
