package com.highway.agent.research.node;

import com.highway.agent.model.SearchResult;
import com.highway.agent.research.model.ExtractedContent;
import com.highway.agent.tool.WebContentExtractorTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
public class ExtractNode {

    private final WebContentExtractorTool contentExtractorTool;

    public List<ExtractedContent> extract(List<SearchResult> searchResults, String focusTopic,
                                           int subQuestionIndex, int maxExtractUrls) {
        List<SearchResult> topResults = searchResults.stream()
                .filter(r -> r.getUrl() != null && !r.getUrl().isBlank())
                .limit(maxExtractUrls)
                .toList();

        if (topResults.size() <= 1) {
            // 单个 URL 无需并行
            List<ExtractedContent> contents = new ArrayList<>();
            for (SearchResult result : topResults) {
                contents.add(extractOne(result, focusTopic, subQuestionIndex));
            }
            return contents;
        }

        // 多个 URL 并行提取
        ExecutorService executor = new ThreadPoolExecutor(
                topResults.size(), topResults.size(), 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ExtractThreadFactory());

        try {
            List<CompletableFuture<ExtractedContent>> futures = topResults.stream()
                    .map(result -> CompletableFuture.supplyAsync(
                            () -> extractOne(result, focusTopic, subQuestionIndex), executor))
                    .toList();

            List<ExtractedContent> contents = new ArrayList<>();
            for (CompletableFuture<ExtractedContent> f : futures) {
                try {
                    contents.add(f.join());
                } catch (Exception e) {
                    log.warn("URL extraction failed", e);
                }
            }
            return contents;
        } finally {
            executor.shutdownNow();
        }
    }

    private ExtractedContent extractOne(SearchResult result, String focusTopic, int subQuestionIndex) {
        log.info("Extracting content from: {}", result.getUrl());
        ExtractedContent content = contentExtractorTool.extract(
                result.getUrl(), focusTopic, subQuestionIndex);
        content.setTitle(result.getTitle());
        return content;
    }

    private static class ExtractThreadFactory implements ThreadFactory {
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "extract-executor-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }
}
