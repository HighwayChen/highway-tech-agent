package com.highway.agent.chat.service;

import com.highway.agent.common.service.SuggestionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.chat.memory.MysqlChatMemory;
import com.highway.agent.chat.model.ChatResponse;
import com.highway.agent.common.model.SearchResult;
import com.highway.agent.common.model.SseEvent;
import com.highway.agent.common.tool.TavilySearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final TavilySearchTool tavilySearchTool;
    private final MysqlChatMemory chatMemory;
    private final SuggestionService suggestionService;
    private final ObjectMapper objectMapper;

    @Value("${agent.suggestion.count:3}")
    private int suggestionCount;

    private final ConcurrentHashMap<String, Sinks.Empty<Void>> activeStreams = new ConcurrentHashMap<>();

    public boolean stopStream(String conversationId) {
        Sinks.Empty<Void> stopSignal = activeStreams.remove(conversationId);
        if (stopSignal != null) {
            stopSignal.tryEmitEmpty();
            log.info("Stream stopped for conversation: {}", conversationId);
            return true;
        }
        return false;
    }

    public Flux<ServerSentEvent<String>> chatStream(String conversationId, String userMessage, boolean skipSaveUser) {
        String convId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;

        if (skipSaveUser) {
            chatMemory.deleteLastAssistant(convId);
        }

        StringBuilder answerBuilder = new StringBuilder();

        Sinks.Empty<Void> stopSignal = Sinks.empty();
        activeStreams.put(convId, stopSignal);

        // 构建流式请求：正常对话走 Advisor 自动存记忆，重新生成不走 Advisor 手动管理
        Flux<String> textFlux;
        if (skipSaveUser) {
            List<Message> history = chatMemory.get(convId);
            textFlux = chatClient.prompt()
                    .system(sp -> {})
                    .messages(history)
                    .user(userMessage)
                    .stream()
                    .content()
                    .subscribeOn(Schedulers.boundedElastic());
        } else {
            textFlux = chatClient.prompt()
                    .user(userMessage)
                    .advisors(a -> a.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, convId))
                    .stream()
                    .content()
                    .subscribeOn(Schedulers.boundedElastic());
        }

        // SSE token 流
        Flux<ServerSentEvent<String>> answerFlux = textFlux
                .takeUntilOther(stopSignal.asMono())
                .map(text -> {
                    answerBuilder.append(text);
                    return ServerSentEvent.<String>builder().event("token").data(text).build();
                })
                .retryWhen(reactor.util.retry.Retry.backoff(1, java.time.Duration.ofSeconds(1))
                        .doBeforeRetry(signal -> {
                            log.warn("Stream failed, retrying once. convId={}, attempt={}", convId, signal.totalRetries(), signal.failure());
                            answerBuilder.setLength(0);
                        }));

        // 收尾事件流
        Flux<ServerSentEvent<String>> postFlux = Flux.defer(() -> {
            String finalAnswer = answerBuilder.toString();

            // 重新生成路径：手动存 assistant message
            if (skipSaveUser && !finalAnswer.isBlank()) {
                chatMemory.add(convId, new AssistantMessage(finalAnswer));
            }

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

        return Flux.concat(answerFlux, postFlux)
                .doFinally(signal -> activeStreams.remove(convId))
                .onErrorResume(e -> {
                    log.error("Stream error after retry", e);
                    return Flux.just(toErrorSse("处理时出现错误，请重试。"), toSse(SseEvent.done()));
                });
    }

    public ChatResponse chatSync(String conversationId, String userMessage) {
        String convId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;

        String finalAnswer = chatClient.prompt()
                .user(userMessage)
                .advisors(a -> a.param(org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID, convId))
                .call()
                .content();

        return ChatResponse.builder()
                .answer(finalAnswer)
                .references(buildReferences(tavilySearchTool.drainSearchResults()))
                .suggestedQuestions(suggestionService.generateSuggestions(userMessage, finalAnswer, suggestionCount))
                .build();
    }

    private ServerSentEvent<String> toErrorSse(String text) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data("{\"message\":\"" + text.replace("\"", "\\\"") + "\"}")
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
