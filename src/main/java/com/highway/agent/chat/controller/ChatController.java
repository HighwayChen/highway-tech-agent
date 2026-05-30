package com.highway.agent.chat.controller;

import com.highway.agent.chat.memory.ChatMessageMapper;
import com.highway.agent.chat.model.ChatMessage;
import com.highway.agent.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatMemory chatMemory;
    private final ChatClient plainChatClient;

    public ChatController(ChatService chatService,
                          ChatMessageMapper chatMessageMapper,
                          ChatMemory chatMemory,
                          @Qualifier("plainChatClient") ChatClient plainChatClient) {
        this.chatService = chatService;
        this.chatMessageMapper = chatMessageMapper;
        this.chatMemory = chatMemory;
        this.plainChatClient = plainChatClient;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(
            @RequestParam(required = false) String conversationId,
            @RequestParam String message,
            @RequestParam(required = false, defaultValue = "false") boolean skipSaveUser) {
        return chatService.chatStream(conversationId, message, skipSaveUser);
    }

    @PostMapping("/chat/stop")
    public Map<String, Object> stopChat(@RequestParam String conversationId) {
        return Map.of("success", chatService.stopStream(conversationId));
    }

    @GetMapping("/conversations")
    public List<Map<String, String>> listConversations() {
        return chatMessageMapper.selectConversationIds().stream().map(id -> {
            List<ChatMessage> messages = chatMessageMapper.selectLastN(id, 1);
            String summary = messages.isEmpty() ? "" :
                    messages.get(0).getContent().substring(0, Math.min(30, messages.get(0).getContent().length()));
            if (!messages.isEmpty() && messages.get(0).getContent().length() > 30) summary += "...";
            return Map.of("id", id, "summary", summary);
        }).toList();
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessage> getMessages(@PathVariable String conversationId) {
        return chatMessageMapper.selectLastN(conversationId, 100);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId) {
        chatMemory.clear(conversationId);
    }

    @GetMapping("/diagnose")
    public Mono<Map<String, Object>> diagnose() {
        return Mono.fromCallable(() -> {
            try {
                String result = plainChatClient.prompt().user("说一个字：好").call().content();
                return Map.<String, Object>of("success", true, "response", result != null ? result : "null");
            } catch (Exception e) {
                return Map.<String, Object>of("success", false, "error", e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
