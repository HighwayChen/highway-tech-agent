package com.highway.agent.common.config;

import com.highway.agent.chat.memory.MysqlChatMemory;
import com.highway.agent.common.service.PromptTemplate;
import com.highway.agent.common.tool.TavilySearchTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel, PromptTemplate promptTemplate,
                                 MysqlChatMemory chatMemory, TavilySearchTool tavilySearchTool) {
        var searchTool = FunctionToolCallback.builder("tavily_search",
                        (java.util.function.Function<TavilySearchTool.SearchRequest, String>) tavilySearchTool::search)
                .description("搜索互联网获取最新信息，返回搜索结果")
                .inputType(TavilySearchTool.SearchRequest.class)
                .build();

        return ChatClient.builder(chatModel)
                .defaultSystem(promptTemplate.getReActSystemPrompt())
                .defaultToolCallbacks(searchTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public ChatClient plainChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
