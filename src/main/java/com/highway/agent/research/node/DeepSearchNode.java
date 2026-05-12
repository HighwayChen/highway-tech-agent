package com.highway.agent.research.node;

import com.highway.agent.model.SearchResult;
import com.highway.agent.tool.DeepSearchTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSearchNode {

    private final DeepSearchTool deepSearchTool;

    public List<SearchResult> deepSearch(String originalQuery, String gapDescription, int maxRounds) {
        log.info("Deep searching for gaps: {}", gapDescription);
        return deepSearchTool.deepSearch(originalQuery, gapDescription, maxRounds);
    }
}
