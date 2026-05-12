package com.highway.agent.tool;

import com.highway.agent.research.model.ExtractedContent;
import com.highway.agent.research.prompt.DeepResearchPrompt;
import com.highway.agent.research.util.LlmStreamingHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
public class WebContentExtractorTool {

    private final ChatClient chatClient;
    private final DeepResearchPrompt promptTemplate;
    private final WebClient webClient;

    public WebContentExtractorTool(ChatClient chatClient,
                                   DeepResearchPrompt promptTemplate,
                                   @Qualifier("contentExtractorWebClient") WebClient webClient) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
        this.webClient = webClient;
    }

    public ExtractedContent extract(String url, String focusTopic, int subQuestionIndex) {
        try {
            String html = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .onErrorResume(e -> {
                        log.warn("Failed to fetch URL: {}", url, e);
                        return Mono.just("");
                    })
                    .block();

            if (html == null || html.isBlank()) {
                return ExtractedContent.builder()
                        .url(url)
                        .content("")
                        .subQuestionIndex(subQuestionIndex)
                        .build();
            }

            String text = stripHtml(html);
            if (text.length() > 8000) {
                text = text.substring(0, 8000);
            }

            String extracted = LlmStreamingHelper.streamCall(chatClient,
                    promptTemplate.extractContentPrompt(text, focusTopic));

            return ExtractedContent.builder()
                    .url(url)
                    .content(extracted != null ? extracted : "")
                    .subQuestionIndex(subQuestionIndex)
                    .build();
        } catch (Exception e) {
            log.error("Content extraction failed for URL: {}", url, e);
            return ExtractedContent.builder()
                    .url(url)
                    .content("")
                    .subQuestionIndex(subQuestionIndex)
                    .build();
        }
    }

    private String stripHtml(String html) {
        String text = html.replaceAll("(?s)<script[^>]*>.*?</script>", " ");
        text = text.replaceAll("(?s)<style[^>]*>.*?</style>", " ");
        text = text.replaceAll("<[^>]+>", " ");
        text = text.replaceAll("&nbsp;", " ");
        text = text.replaceAll("&amp;", "&");
        text = text.replaceAll("&lt;", "<");
        text = text.replaceAll("&gt;", ">");
        text = text.replaceAll("&quot;", "\"");
        text = text.replaceAll("\\s+", " ").trim();
        return text;
    }
}
