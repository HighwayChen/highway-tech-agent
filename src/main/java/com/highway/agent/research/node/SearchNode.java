package com.highway.agent.research.node;

import com.highway.agent.model.SearchResult;
import com.highway.agent.tool.TavilySearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchNode {

    private final TavilySearchTool tavilySearchTool;

    public List<SearchResult> search(List<String> queries) {
        List<SearchResult> allResults = new ArrayList<>();
        for (String query : queries) {
            log.info("Searching: {}", query);
            List<SearchResult> results = tavilySearchTool.searchReactive(query).block();
            if (results != null) {
                allResults.addAll(results);
            }
        }
        return allResults;
    }
}
