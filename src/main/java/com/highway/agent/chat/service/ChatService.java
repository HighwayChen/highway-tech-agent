package com.highway.agent.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.chat.memory.ChatMessageMapper;
import com.highway.agent.chat.model.ChatResponse;
import com.highway.agent.common.model.SearchResult;
import com.highway.agent.common.tool.TavilySearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Value;
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
public class ChatService {

    private static final String SUGGESTION_SEPARATOR = "---suggestions---";

    private final ChatClient chatClient;
    private final TavilySearchTool tavilySearchTool;
    private final ChatMemory chatMemory;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Sinks.Empty<Void>> activeStreams = new ConcurrentHashMap<>();

    public ChatService(ChatClient chatClient,
                       TavilySearchTool tavilySearchTool,
                       ChatMemory chatMemory,
                       ChatMessageMapper chatMessageMapper,
                       ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.tavilySearchTool = tavilySearchTool;
        this.chatMemory = chatMemory;
        this.chatMessageMapper = chatMessageMapper;
        this.objectMapper = objectMapper;
    }

    public boolean stopStream(String conversationId) {
        Sinks.Empty<Void> stopSignal = activeStreams.remove(conversationId);
        if (stopSignal != null) {
            stopSignal.tryEmitEmpty();
            return true;
        }
        return false;
    }

    public Flux<String> chatStream(String conversationId, String userMessage, boolean skipSaveUser) {
        String convId = (conversationId == null || conversationId.isBlank())
                ? UUID.randomUUID().toString() : conversationId;

        if (skipSaveUser) {
            chatMessageMapper.deleteLastAssistant(convId);
        }

        StringBuilder answerBuilder = new StringBuilder();
        Sinks.Empty<Void> stopSignal = Sinks.empty();
        activeStreams.put(convId, stopSignal);

        var requestSpec = chatClient.prompt();

        if (skipSaveUser) {
            requestSpec.messages(chatMemory.get(convId));
        } else {
            requestSpec.user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, convId));
        }

        Flux<String> textFlux = requestSpec.stream().content()
                .subscribeOn(Schedulers.boundedElastic())
                .retryWhen(reactor.util.retry.Retry.backoff(3, java.time.Duration.ofSeconds(1))
                        .maxBackoff(java.time.Duration.ofSeconds(5))
                        .jitter(0.5)
                        .doBeforeRetry(signal -> {
                            log.warn("Stream retry {}/{}, convId={}", signal.totalRetries(), 3, convId, signal.failure());
                            answerBuilder.setLength(0);
                        }));

        return textFlux
                .takeUntilOther(stopSignal.asMono())
                .map(text -> {
                    answerBuilder.append(text);
                    return json("token", "content", text);
                })
                .concatWith(Flux.defer(() -> {
                    String rawAnswer = answerBuilder.toString();
                    ParsedAnswer parsed = parseAnswer(rawAnswer);

                    if (skipSaveUser && !parsed.answer().isBlank()) {
                        chatMemory.add(convId, new AssistantMessage(parsed.answer()));
                    }
                    return postEvents(convId, parsed);
                }))
                .doFinally(s -> activeStreams.remove(convId))
                .onErrorResume(e -> {
                    log.error("Stream error, convId={}", convId, e);
                    return Flux.just(json("error", "message", "处理时出现错误，请重试。"), json("done"));
                });
    }

    private ParsedAnswer parseAnswer(String rawAnswer) {
        int idx = rawAnswer.indexOf(SUGGESTION_SEPARATOR);
        if (idx < 0) {
            return new ParsedAnswer(rawAnswer.trim(), List.of());
        }
        String answer = rawAnswer.substring(0, idx).trim();
        String suggestionPart = rawAnswer.substring(idx + SUGGESTION_SEPARATOR.length()).trim();
        List<String> suggestions = suggestionPart.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.replaceAll("^\\d+[.、)\\s]+", ""))
                .toList();
        return new ParsedAnswer(answer, suggestions);
    }

    private Flux<String> postEvents(String convId, ParsedAnswer parsed) {
        List<String> events = new java.util.ArrayList<>();

        if (!parsed.suggestions().isEmpty()) {
            events.add(json("answer", "content", parsed.answer()));
        }

        List<ChatResponse.Reference> refs = buildReferences(tavilySearchTool.drainSearchResults());
        if (!refs.isEmpty()) {
            events.add(json("references", "data", refs));
        }

        if (!parsed.suggestions().isEmpty()) {
            events.add(json("suggested", "data", parsed.suggestions()));
        }

        events.add(json("conversation", "id", convId));
        events.add(json("done"));
        return Flux.fromIterable(events);
    }

    private String json(String type) {
        return "{\"type\":\"" + type + "\"}";
    }

    private String json(String type, String key, Object value) {
        try {
            return "{\"type\":\"" + type + "\",\"" + key + "\":" + objectMapper.writeValueAsString(value) + "}";
        } catch (Exception e) {
            return "{\"type\":\"" + type + "\"}";
        }
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

    private record ParsedAnswer(String answer, List<String> suggestions) {}
}
