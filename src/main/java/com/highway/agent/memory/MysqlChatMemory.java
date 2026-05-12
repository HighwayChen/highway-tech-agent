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
