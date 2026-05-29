package com.highway.agent.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.chat.memory.MysqlChatMemory;
import com.highway.agent.chat.model.ChatResponse;
import com.highway.agent.common.model.SearchResult;
import com.highway.agent.common.service.SuggestionService;
import com.highway.agent.common.tool.TavilySearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

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

        // 正常对话走 Advisor 自动存记忆；重新生成手动拼历史、手动存回答
        Flux<String> textFlux = skipSaveUser
                ? chatClient.prompt()
                    .system(sp -> {})
                    .messages(chatMemory.get(convId))
                    .user(userMessage)
                    .stream().content()
                    .subscribeOn(Schedulers.boundedElastic())
                : chatClient.prompt()
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId))
                    .stream().content()
                    .subscribeOn(Schedulers.boundedElastic());

        return textFlux
                .takeUntilOther(stopSignal.asMono())
                .map(text -> {
                    answerBuilder.append(text);
                    return sse("token", text);
                })
                .retryWhen(reactor.util.retry.Retry.backoff(1, java.time.Duration.ofSeconds(1))
                        .doBeforeRetry(signal -> {
                            log.warn("Stream retry, convId={}", convId, signal.failure());
                            answerBuilder.setLength(0);
                        }))
                .concatWith(Flux.defer(() -> {
                    String answer = answerBuilder.toString();
                    if (skipSaveUser && !answer.isBlank()) {
                        chatMemory.add(convId, new AssistantMessage(answer));
                    }
                    return postEvents(convId, userMessage, answer);
                }))
                .doFinally(s -> activeStreams.remove(convId))
                .onErrorResume(e -> {
                    log.error("Stream error, convId={}", convId, e);
                    return Flux.just(sse("error", "{\"message\":\"处理时出现错误，请重试。\"}"), sse("done", "\"\""));
                });
    }

    private Flux<ServerSentEvent<String>> postEvents(String convId, String userMessage, String answer) {
        List<ServerSentEvent<String>> events = new java.util.ArrayList<>();

        List<ChatResponse.Reference> refs = buildReferences(tavilySearchTool.drainSearchResults());
        if (!refs.isEmpty()) {
            events.add(sse("references", toJson(refs)));
        }

        List<String> suggestions = suggestionService.generateSuggestions(userMessage, answer, suggestionCount);
        if (!suggestions.isEmpty()) {
            events.add(sse("suggested", toJson(suggestions)));
        }

        events.add(sse("conversation", "\"" + convId + "\""));
        events.add(sse("done", "\"\""));
        return Flux.fromIterable(events);
    }

    private ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private List<ChatResponse.Reference> buildReferences(List<SearchResult> results) {
        var seen = new HashSet<String>();
        return results.stream()
                .filter(r -> r.getUrl() != null && !r.getUrl().isBlank() && seen.add(r.getUrl()))
                .map(r -> ChatResponse.Reference.builder()
                        .id(seen.size())
                        .title(r.getTitle())
                        .url(r.getUrl())
                        .build())
                .toList();
    }
}
