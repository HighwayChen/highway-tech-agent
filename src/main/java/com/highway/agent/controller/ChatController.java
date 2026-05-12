package com.highway.agent.controller;

import com.highway.agent.memory.ChatMessageMapper;
import com.highway.agent.model.ChatMessage;
import com.highway.agent.model.ChatRequest;
import com.highway.agent.model.ChatResponse;
import com.highway.agent.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageMapper chatMessageMapper;
    private final ChatClient chatClient;

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @RequestParam(required = false) String conversationId,
            @RequestParam String message) {
        return chatService.chatStream(conversationId, message);
    }

    @PostMapping("/chat/stop")
    public Map<String, Object> stopChat(@RequestParam String conversationId) {
        boolean stopped = chatService.stopStream(conversationId);
        return Map.of("success", stopped);
    }

    @PostMapping("/chat/sync")
    public ChatResponse chatSync(@RequestBody ChatRequest request) {
        return chatService.chatSync(request.getConversationId(), request.getMessage());
    }

    /**
     * 获取对话列表（含摘要）
     */
    @GetMapping("/conversations")
    public List<Map<String, String>> listConversations() {
        List<String> ids = chatMessageMapper.selectConversationIds();
        return ids.stream().map(id -> {
            List<ChatMessage> messages = chatMessageMapper.selectLastN(id, 1);
            String summary = "";
            if (!messages.isEmpty()) {
                String content = messages.get(0).getContent();
                summary = content.length() > 30 ? content.substring(0, 30) + "..." : content;
            }
            return Map.of("id", id, "summary", summary);
        }).collect(Collectors.toList());
    }

    /**
     * 获取对话历史消息
     */
    @GetMapping("/conversations/{conversationId}/messages")
    public List<ChatMessage> getMessages(@PathVariable String conversationId) {
        return chatMessageMapper.selectLastN(conversationId, 100);
    }

    @DeleteMapping("/conversations/{conversationId}")
    public void deleteConversation(@PathVariable String conversationId) {
        chatMessageMapper.deleteByConversationId(conversationId);
    }

    /**
     * 诊断接口：验证 ChatClient 是否能正常调用
     */
    @GetMapping("/diagnose")
    public Mono<Map<String, Object>> diagnose() {
        return Mono.fromCallable(() -> {
                    try {
                        String result = chatClient.prompt().user("说一个字：好").call().content();
                        Map<String, Object> map = new HashMap<>();
                        map.put("success", true);
                        map.put("response", result != null ? result : "null");
                        return map;
                    } catch (Exception e) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("success", false);
                        map.put("error", e.getMessage());
                        map.put("type", e.getClass().getSimpleName());
                        return map;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
