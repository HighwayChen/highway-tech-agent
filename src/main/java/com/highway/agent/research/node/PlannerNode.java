package com.highway.agent.research.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.config.DeepResearchConfig;
import com.highway.agent.research.model.CritiqueFeedback;
import com.highway.agent.research.model.ResearchPlan;
import com.highway.agent.research.model.ResearchStateKeys;
import com.highway.agent.research.prompt.DeepResearchPrompt;
import com.highway.agent.research.util.LlmStreamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlannerNode implements NodeAction {

    private final ChatClient chatClient;
    private final DeepResearchPrompt promptTemplate;
    private final ObjectMapper objectMapper;
    private final DeepResearchConfig config;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(ResearchStateKeys.ORIGINAL_QUESTION, String.class).orElse("");

        // 如果有 critique_feedback，生成修订建议
        String critiqueFeedback = "";
        Object feedbackObj = state.value(ResearchStateKeys.CRITIQUE_FEEDBACK).orElse(null);
        if (feedbackObj != null) {
            CritiqueFeedback feedback = convertToType(feedbackObj, CritiqueFeedback.class);
            if (feedback != null) {
                critiqueFeedback = String.format("信息充分度: %.0f%%, 缺口: %s, 建议: %s",
                        feedback.getCompletenessScore() * 100,
                        feedback.getGaps(),
                        feedback.getRevisionSuggestion());
            }
        }

        log.info("Generating research plan for: {}", question);

        String llmResponse = LlmStreamingHelper.streamCall(chatClient,
                promptTemplate.plannerPrompt(question, config.getMaxSubQuestions(), critiqueFeedback));

        ResearchPlan plan = parsePlan(llmResponse);
        if (plan == null) {
            // 解析失败，创建默认计划
            plan = ResearchPlan.builder()
                    .summary("研究: " + question)
                    .estimatedSearchCount(config.getMaxSubQuestions() * 2)
                    .build();
        }

        log.info("Research plan generated: {} sub-questions, estimated {} searches",
                plan.getSubQuestions() != null ? plan.getSubQuestions().size() : 0,
                plan.getEstimatedSearchCount());

        return Map.of(ResearchStateKeys.RESEARCH_PLAN, plan);
    }

    private ResearchPlan parsePlan(String llmResponse) {
        try {
            String json = extractJson(llmResponse);
            return objectMapper.readValue(json, ResearchPlan.class);
        } catch (Exception e) {
            log.warn("Failed to parse research plan", e);
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
