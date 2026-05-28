package com.highway.agent.common.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.common.model.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class TavilySearchTool {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String searchDepth;
    private final int maxResults;
    private final String timeRange;

    /** 累积的搜索结果，线程安全 */
    private final List<SearchResult> accumulatedResults = new CopyOnWriteArrayList<>();

    public TavilySearchTool(WebClient tavilyWebClient,
                            ObjectMapper objectMapper,
                            @Value("${tavily.api-key}") String apiKey,
                            @Value("${tavily.search-depth:advanced}") String searchDepth,
                            @Value("${tavily.max-results:5}") int maxResults,
                            @Value("${tavily.time-range:month}") String timeRange) {
        this.webClient = tavilyWebClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.searchDepth = searchDepth;
        this.maxResults = maxResults;
        this.timeRange = timeRange;
    }

    /**
     * Spring AI FunctionCallback 调用入口
     */
    public String search(SearchRequest request) {
        return search(request.query());
    }

    /**
     * 重载：直接传字符串
     */
    public String search(String query) {
        List<SearchResult> results = searchReactive(query).block();
        if (results == null) {
            results = List.of();
        }
        accumulatedResults.addAll(results);
        return formatResults(results);
    }

    /**
     * 工具调用输入参数，record 类型保证生成 object schema
     */
    public record SearchRequest(String query) {}

    public Mono<List<SearchResult>> searchReactive(String query) {
        Map<String, Object> requestBody = new java.util.HashMap<>();
        requestBody.put("query", query);
        requestBody.put("search_depth", searchDepth);
        requestBody.put("max_results", maxResults);
        requestBody.put("include_answer", false);
        if (timeRange != null && !timeRange.isBlank()) {
            requestBody.put("time_range", timeRange);
        }

        return webClient.post()
                .uri("/search")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResults)
                .doOnError(e -> log.error("Tavily search failed for query: {}", query, e))
                .onErrorResume(e -> Mono.just(List.of()));
    }

    /**
     * 获取并清除累积的搜索结果
     */
    public List<SearchResult> drainSearchResults() {
        List<SearchResult> results = new ArrayList<>(accumulatedResults);
        accumulatedResults.clear();
        return Collections.unmodifiableList(results);
    }

    private String formatResults(List<SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append(String.format("[%d] %s\n来源：%s\n摘要：%s\n\n",
                    i + 1, r.getTitle(), r.getUrl(), r.getContent()));
        }
        return sb.toString();
    }

    private List<SearchResult> parseResults(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode resultsNode = root.get("results");
            List<SearchResult> results = new ArrayList<>();

            if (resultsNode != null && resultsNode.isArray()) {
                for (JsonNode node : resultsNode) {
                    SearchResult sr = new SearchResult();
                    sr.setTitle(node.path("title").asText(""));
                    sr.setUrl(node.path("url").asText(""));
                    sr.setContent(node.path("content").asText(""));
                    sr.setScore(node.path("score").asDouble(0));
                    results.add(sr);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Failed to parse Tavily response", e);
            return List.of();
        }
    }
}
