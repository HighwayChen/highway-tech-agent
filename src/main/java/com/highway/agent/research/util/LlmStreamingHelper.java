package com.highway.agent.research.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * 流式调用 LLM 并累积完整响应，替代同步 .call().content()。
 * 流式模式下 token 持续到达，不会触发 Netty ReadTimeoutException。
 * 支持通过 tokenConsumer 实时推送每个 token 到 SSE 流。
 */
@Slf4j
public class LlmStreamingHelper {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    /** 全局 token 消费者（volatile 保证跨线程可见性） */
    private static volatile Consumer<String> tokenConsumer;

    /** 全局报告 token 消费者（volatile 保证跨线程可见性） */
    private static volatile Consumer<String> reportTokenConsumer;

    /** 报告生成前置回调，用于在 reporter 节点开始时通知前端 */
    private static volatile Runnable onGeneratingReport;

    private LlmStreamingHelper() {}

    public static void setTokenConsumer(Consumer<String> consumer) {
        tokenConsumer = consumer;
    }

    public static void clearTokenConsumer() {
        tokenConsumer = null;
    }

    public static void setReportTokenConsumer(Consumer<String> consumer) {
        reportTokenConsumer = consumer;
    }

    public static void clearReportTokenConsumer() {
        reportTokenConsumer = null;
    }

    public static void setOnGeneratingReport(Runnable callback) {
        onGeneratingReport = callback;
    }

    public static void clearOnGeneratingReport() {
        onGeneratingReport = null;
    }

    /** 由 ReporterNode 调用，触发 generating_report 事件通知前端 */
    public static void notifyGeneratingReport() {
        if (onGeneratingReport != null) {
            try { onGeneratingReport.run(); } catch (Exception ignored) {}
        }
    }

    public static String streamCall(ChatClient chatClient, String userPrompt) {
        return streamCall(chatClient, userPrompt, DEFAULT_TIMEOUT);
    }

    /**
     * 流式调用 LLM，累积所有 token 后返回完整文本。
     * 同时通过 tokenConsumer 实时推送每个 token。
     *
     * @return 累积的完整响应文本，超时或异常时返回空字符串
     */
    public static String streamCall(ChatClient chatClient, String userPrompt, Duration timeout) {
        Consumer<String> consumer = tokenConsumer;
        try {
            Flux<String> contentFlux = chatClient.prompt()
                    .user(userPrompt)
                    .stream()
                    .content();

            if (consumer != null) {
                contentFlux = contentFlux.doOnNext(chunk -> {
                    try { consumer.accept(chunk); } catch (Exception ignored) {}
                });
            }

            String result = contentFlux
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block(timeout);

            if (result == null) {
                log.warn("Streaming call returned null for prompt (first 50 chars): {}",
                        userPrompt.substring(0, Math.min(50, userPrompt.length())));
            }
            return result != null ? result : "";
        } catch (Exception e) {
            log.error("Streaming call failed for prompt (first 50 chars): {}",
                    userPrompt.substring(0, Math.min(50, userPrompt.length())), e);
            return "";
        }
    }

    /**
     * 流式调用 LLM 生成报告，每个 token 同时推送到 reportTokenConsumer。
     * 不同于 streamCall，此方法会将 token 推送到专用的报告消费者，
     * 而非推理消费者（reasoning），避免两类内容混淆。
     */
    public static String streamReportCall(ChatClient chatClient, String userPrompt) {
        Consumer<String> reportConsumer = reportTokenConsumer;
        Consumer<String> reasoningConsumer = tokenConsumer;
        try {
            Flux<String> contentFlux = chatClient.prompt()
                    .user(userPrompt)
                    .stream()
                    .content();

            if (reportConsumer != null || reasoningConsumer != null) {
                contentFlux = contentFlux.doOnNext(chunk -> {
                    try { if (reportConsumer != null) reportConsumer.accept(chunk); } catch (Exception ignored) {}
                    try { if (reasoningConsumer != null) reasoningConsumer.accept(chunk); } catch (Exception ignored) {}
                });
            }

            String result = contentFlux
                    .collectList()
                    .map(chunks -> String.join("", chunks))
                    .block(DEFAULT_TIMEOUT);

            if (result == null) {
                log.warn("Report streaming call returned null for prompt (first 50 chars): {}",
                        userPrompt.substring(0, Math.min(50, userPrompt.length())));
            }
            return result != null ? result : "";
        } catch (Exception e) {
            log.error("Report streaming call failed for prompt (first 50 chars): {}",
                    userPrompt.substring(0, Math.min(50, userPrompt.length())), e);
            return "";
        }
    }
}
