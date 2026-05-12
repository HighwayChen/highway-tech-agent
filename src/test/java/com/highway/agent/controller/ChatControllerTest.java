package com.highway.agent.controller;

import com.highway.agent.memory.ChatMessageMapper;
import com.highway.agent.model.ChatRequest;
import com.highway.agent.model.ChatResponse;
import com.highway.agent.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ChatService chatService;

    @MockitoBean
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

    @Test
    void deleteConversation_shouldReturnOk() {
        when(chatMessageMapper.deleteByConversationId("conv-1")).thenReturn(1);

        webTestClient.delete()
                .uri("/api/conversations/conv-1")
                .exchange()
                .expectStatus().isOk();
    }
}
