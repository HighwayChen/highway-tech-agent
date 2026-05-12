package com.highway.agent.research.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.config.DeepResearchConfig;
import com.highway.agent.research.model.CritiqueFeedback;
import com.highway.agent.research.model.ExtractedContent;
import com.highway.agent.research.model.ResearchPlan;
import com.highway.agent.research.model.ResearchStateKeys;
import com.highway.agent.research.prompt.DeepResearchPrompt;
import com.highway.agent.research.util.LlmStreamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class CriticNode implements NodeAction {

    private final ChatClient chatClient;
    private final DeepResearchPrompt promptTemplate;
    private final ObjectMapper objectMapper;
    private final DeepResearchConfig config;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String originalQuestion = state.value(ResearchStateKeys.ORIGINAL_QUESTION, String.class).orElse("");
        Integer iterationCount = state.value(ResearchStateKeys.ITERATION_COUNT, Integer.class).orElse(0);
        iterationCount = iterationCount + 1;

        // 获取研究计划摘要
        String planSummary = "";
        Object planObj = state.value(ResearchStateKeys.RESEARCH_PLAN).orElse(null);
        if (planObj != null) {
            ResearchPlan plan = convertToType(planObj, ResearchPlan.class);
            if (plan != null) {
                planSummary = plan.getSummary();
            }
        }

        // 获取提取内容摘要
        String contentsSummary = buildContentsSummary(state);

        log.info("Critiquing research, iteration {}/{}", iterationCount, config.getMaxIterations());

        String llmResponse = LlmStreamingHelper.streamCall(chatClient,
                promptTemplate.criticPrompt(originalQuestion, planSummary, contentsSummary,
                        iterationCount, config.getMaxIterations()));

        CritiqueFeedback feedback = parseFeedback(llmResponse);
        if (feedback == null) {
            feedback = CritiqueFeedback.builder()
                    .sufficient(iterationCount >= config.getMaxIterations())
                    .completenessScore(0.5)
                    .build();
        }

        log.info("Critique result: sufficient={}, score={}, gaps={}",
                feedback.isSufficient(), feedback.getCompletenessScore(), feedback.getGaps());

        return Map.of(
                ResearchStateKeys.CRITIQUE_FEEDBACK, feedback,
                ResearchStateKeys.IS_SUFFICIENT, feedback.isSufficient(),
                ResearchStateKeys.ITERATION_COUNT, iterationCount
        );
    }

    private String buildContentsSummary(OverAllState state) {
        Object contentsObj = state.value(ResearchStateKeys.EXTRACTED_CONTENTS).orElse(null);
        if (contentsObj == null) {
            return "暂无收集的信息";
        }

        try {
            List<ExtractedContent> contents = objectMapper.convertValue(contentsObj,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ExtractedContent.class));

            StringBuilder sb = new StringBuilder();
            for (ExtractedContent c : contents) {
                if (c.getContent() != null && !c.getContent().isBlank()) {
                    sb.append("【来源: ").append(c.getUrl()).append("】\n");
                    String content = c.getContent();
                    if (content.length() > 500) {
                        content = content.substring(0, 500) + "...";
                    }
                    sb.append(content).append("\n\n");
                }
            }
            return sb.isEmpty() ? "暂无有效信息" : sb.toString();
        } catch (Exception e) {
            return "信息解析失败";
        }
    }

    private CritiqueFeedback parseFeedback(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            return objectMapper.readValue(json, CritiqueFeedback.class);
        } catch (Exception e) {
            log.warn("Failed to parse critique feedback", e);
            return null;
        }
    }

    private String extractJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
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
}
