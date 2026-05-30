package com.highway.agent.chat.config;

import com.highway.agent.chat.memory.MysqlChatMemoryRepository;
import com.highway.agent.chat.prompt.PromptTemplate;
import com.highway.agent.common.tool.TavilySearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class ChatConfig {

    @Bean
    @Primary
    public ChatClient chatClient(ChatModel chatModel,
                                  PromptTemplate promptTemplate,
                                  TavilySearchTool tavilySearchTool,
                                 ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultSystem(promptTemplate.getChatSystemPrompt())
                .defaultToolCallbacks(tavilySearchTool.asToolCallback())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ChatClient plainChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public ChatMemory chatMemory(MysqlChatMemoryRepository repository,
                                  @Value("${agent.chat.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(maxMessages)
                .build();
    }
}
