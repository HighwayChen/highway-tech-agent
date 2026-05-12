package com.highway.agent.config;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.highway.agent.prompt.PromptTemplate;
import com.highway.agent.research.graph.DeepResearchGraph;
import com.highway.agent.tool.TavilySearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    @Bean
    public com.alibaba.cloud.ai.graph.agent.ReactAgent reactAgent(ChatModel chatModel, PromptTemplate promptTemplate, TavilySearchTool tavilySearchTool) {
        var searchTool = FunctionToolCallback.builder("tavily_search",
                        (java.util.function.Function<TavilySearchTool.SearchRequest, String>) tavilySearchTool::search)
                .description("搜索互联网获取最新信息，返回搜索结果")
                .inputType(TavilySearchTool.SearchRequest.class)
                .build();

        return com.alibaba.cloud.ai.graph.agent.ReactAgent.builder()
                .name("highway-agent")
                .model(chatModel)
                .systemPrompt(promptTemplate.getReActSystemPrompt())
                .tools(searchTool)
                .build();
    }

    @Bean
    public CompiledGraph compiledResearchGraph(DeepResearchGraph deepResearchGraphBuilder) {
        try {
            return deepResearchGraphBuilder.compile();
        } catch (GraphStateException e) {
            log.error("Failed to compile deep research graph", e);
            throw new RuntimeException("Failed to compile deep research graph", e);
        }
    }
}
