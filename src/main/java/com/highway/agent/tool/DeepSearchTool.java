package com.highway.agent.tool;

import com.highway.agent.model.SearchResult;
import com.highway.agent.research.prompt.DeepResearchPrompt;
import com.highway.agent.research.util.LlmStreamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSearchTool {

    private final TavilySearchTool tavilySearchTool;
    private final ChatClient chatClient;
    private final DeepResearchPrompt promptTemplate;

    public List<SearchResult> deepSearch(String originalQuery, String gapDescription, int maxRounds) {
        List<SearchResult> allResults = new ArrayList<>();
        String currentQuery = originalQuery;

        for (int round = 0; round < maxRounds; round++) {
            String reformulated = reformulateQuery(currentQuery, gapDescription);
            log.info("Deep search round {}: query='{}'", round + 1, reformulated);

            List<SearchResult> results = tavilySearchTool.searchReactive(reformulated).block();
            if (results != null) {
                allResults.addAll(results);
            }

            if (results == null || results.isEmpty()) {
                break;
            }
            currentQuery = reformulated;
        }
        return allResults;
    }

    private String reformulateQuery(String originalQuery, String gapDescription) {
        try {
            String response = LlmStreamingHelper.streamCall(chatClient,
                    promptTemplate.reformulateQueryPrompt(originalQuery, gapDescription));

            if (response != null && !response.isBlank()) {
                String[] lines = response.split("\n");
                return lines[0].trim();
            }
        } catch (Exception e) {
            log.warn("Query reformulation failed, using original query", e);
        }
        return originalQuery;
    }
}
