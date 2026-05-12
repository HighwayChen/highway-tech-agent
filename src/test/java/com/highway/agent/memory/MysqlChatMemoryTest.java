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
    void saveAll_shouldPersistMessages() {
        when(mapper.insert(any(ChatMessage.class))).thenReturn(1);

        chatMemory.saveAll("conv-1", List.of(
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
    void findByConversationId_shouldReturnMessages() {
        ChatMessage msg1 = new ChatMessage();
        msg1.setConversationId("conv-1");
        msg1.setRole("user");
        msg1.setContent("hello");

        ChatMessage msg2 = new ChatMessage();
        msg2.setConversationId("conv-1");
        msg2.setRole("assistant");
        msg2.setContent("hi");

        when(mapper.selectLastN("conv-1", 100)).thenReturn(List.of(msg1, msg2));

        var messages = chatMemory.findByConversationId("conv-1");

        assertEquals(2, messages.size());
        assertEquals("hello", messages.get(0).getText());
        assertEquals("hi", messages.get(1).getText());
    }

    @Test
    void deleteByConversationId_shouldDelete() {
        when(mapper.deleteByConversationId("conv-1")).thenReturn(2);

        chatMemory.deleteByConversationId("conv-1");

        verify(mapper).deleteByConversationId("conv-1");
    }

    @Test
    void findConversationIds_shouldReturnIds() {
        when(mapper.selectConversationIds()).thenReturn(List.of("conv-1", "conv-2"));

        var ids = chatMemory.findConversationIds();

        assertEquals(2, ids.size());
        assertEquals("conv-1", ids.get(0));
    }
}
