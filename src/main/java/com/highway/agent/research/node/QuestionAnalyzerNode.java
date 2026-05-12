package com.highway.agent.research.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.research.model.ResearchStateKeys;
import com.highway.agent.research.prompt.DeepResearchPrompt;
import com.highway.agent.research.util.LlmStreamingHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionAnalyzerNode implements NodeAction {

    private final ChatClient chatClient;
    private final DeepResearchPrompt promptTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> apply(OverAllState state) throws Exception {
        String question = state.value(ResearchStateKeys.ORIGINAL_QUESTION, String.class).orElse("");
        String userClarification = state.value(ResearchStateKeys.USER_CLARIFICATION, String.class).orElse("");

        log.info("Analyzing question: {}", question);

        String llmResponse = LlmStreamingHelper.streamCall(chatClient,
                promptTemplate.questionAnalyzerPrompt(question, userClarification));

        String clarityLevel = "clear";
        List<String> clarificationQuestions = new ArrayList<>();

        try {
            // 尝试提取 JSON（LLM 可能在 JSON 前后添加 markdown 标记）
            String json = extractJson(llmResponse);
            JsonNode root = objectMapper.readTree(json);
            clarityLevel = root.path("clarity_level").asText("clear");
            JsonNode questionsNode = root.path("clarification_questions");
            if (questionsNode.isArray()) {
                for (JsonNode q : questionsNode) {
                    clarificationQuestions.add(q.asText());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse question analyzer response, defaulting to clear", e);
        }

        log.info("Question analysis result: clarity={}, questions={}", clarityLevel, clarificationQuestions);

        return Map.of(
                ResearchStateKeys.CLARITY_LEVEL, clarityLevel,
                ResearchStateKeys.CLARIFICATION_QUESTIONS, clarificationQuestions
        );
    }

    private String extractJson(String text) {
        // 去除 markdown 代码块标记
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
}
