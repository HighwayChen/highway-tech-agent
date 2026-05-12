package com.highway.agent.research.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.config.DeepResearchConfig;
import com.highway.agent.model.SearchResult;
import com.highway.agent.research.model.ExtractedContent;
import com.highway.agent.research.model.ResearchPlan;
import com.highway.agent.research.model.ResearchStateKeys;
import com.highway.agent.research.model.SubQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorNode implements NodeAction {

    private final SearchNode searchNode;
    private final ExtractNode extractNode;
    private final DeepSearchNode deepSearchNode;
    private final DeepResearchConfig config;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        Object planObj = state.value(ResearchStateKeys.RESEARCH_PLAN).orElse(null);
        if (planObj == null) {
            log.warn("No research plan found in state");
            return Map.of();
        }

        ResearchPlan plan = convertToType(planObj, ResearchPlan.class);
        if (plan == null || plan.getSubQuestions() == null || plan.getSubQuestions().isEmpty()) {
            log.warn("Empty research plan");
            return Map.of();
        }

        List<SearchResult> allSearchResults = Collections.synchronizedList(new ArrayList<>());
        List<ExtractedContent> allExtractedContents = Collections.synchronizedList(new ArrayList<>());

        int concurrency = Math.max(1, config.getExecutorConcurrency());
        List<SubQuestion> subQuestions = plan.getSubQuestions();

        if (concurrency == 1 || subQuestions.size() == 1) {
            // 顺序执行
            executeSequentially(subQuestions, allSearchResults, allExtractedContents);
        } else {
            // 并行执行
            executeInParallel(subQuestions, allSearchResults, allExtractedContents, concurrency);
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put(ResearchStateKeys.SEARCH_RESULTS, allSearchResults);
        updates.put(ResearchStateKeys.EXTRACTED_CONTENTS, allExtractedContents);

        return updates;
    }

    private void executeSequentially(List<SubQuestion> subQuestions,
                                      List<SearchResult> allSearchResults,
                                      List<ExtractedContent> allExtractedContents) {
        for (SubQuestion subQuestion : subQuestions) {
            executeSubQuestion(subQuestion, allSearchResults, allExtractedContents);
        }
    }

    private void executeInParallel(List<SubQuestion> subQuestions,
                                    List<SearchResult> allSearchResults,
                                    List<ExtractedContent> allExtractedContents,
                                    int concurrency) {
        ExecutorService executor = new ThreadPoolExecutor(
                concurrency, concurrency, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ResearchThreadFactory());

        try {
            List<CompletableFuture<Void>> futures = subQuestions.stream()
                    .map(sq -> CompletableFuture.runAsync(
                            () -> executeSubQuestion(sq, allSearchResults, allExtractedContents),
                            executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdownNow();
        }
    }

    private void executeSubQuestion(SubQuestion subQuestion,
                                     List<SearchResult> allSearchResults,
                                     List<ExtractedContent> allExtractedContents) {
        try {
            log.info("Executing sub-question {}: {}", subQuestion.getIndex(), subQuestion.getQuestion());

            // 1. 搜索
            List<SearchResult> searchResults = searchNode.search(subQuestion.getSearchQueries());
            allSearchResults.addAll(searchResults);

            // 2. 提取内容
            List<ExtractedContent> extractedContents = extractNode.extract(
                    searchResults, subQuestion.getQuestion(),
                    subQuestion.getIndex(), config.getMaxExtractUrls());
            allExtractedContents.addAll(extractedContents);

            // 3. 判断是否需要深度搜索
            if (shouldDeepSearch(searchResults, extractedContents)) {
                String gapDescription = buildGapDescription(subQuestion, searchResults, extractedContents);
                List<SearchResult> deepResults = deepSearchNode.deepSearch(
                        subQuestion.getSearchQueries().get(0), gapDescription, config.getMaxDeepSearchRounds());
                allSearchResults.addAll(deepResults);

                // 深搜结果也需要提取
                List<ExtractedContent> deepExtracted = extractNode.extract(
                        deepResults, subQuestion.getQuestion(),
                        subQuestion.getIndex(), config.getMaxExtractUrls());
                allExtractedContents.addAll(deepExtracted);
            }
        } catch (Exception e) {
            log.error("Sub-question {} execution failed: {}", subQuestion.getIndex(), subQuestion.getQuestion(), e);
        }
    }

    private boolean shouldDeepSearch(List<SearchResult> results, List<ExtractedContent> contents) {
        if (results.isEmpty()) {
            return true;
        }
        long emptyContents = contents.stream()
                .filter(c -> c.getContent() == null || c.getContent().isBlank())
                .count();
        return !contents.isEmpty() && emptyContents > contents.size() / 2;
    }

    private String buildGapDescription(SubQuestion subQuestion, List<SearchResult> results,
                                        List<ExtractedContent> contents) {
        StringBuilder sb = new StringBuilder();
        sb.append("子问题：").append(subQuestion.getQuestion()).append("\n");
        sb.append("当前搜索结果数量：").append(results.size()).append("\n");
        sb.append("有效提取内容数量：").append(contents.stream()
                .filter(c -> c.getContent() != null && !c.getContent().isBlank())
                .count()).append("\n");
        sb.append("需要更详细和具体的信息");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> T convertToType(Object obj, Class<T> type) {
        if (type.isInstance(obj)) {
            return type.cast(obj);
        }
        try {
            return objectMapper.convertValue(obj, type);
        } catch (Exception e) {
            log.warn("Failed to convert state value to {}", type.getSimpleName(), e);
            return null;
        }
    }

    private static class ResearchThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "research-executor-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
