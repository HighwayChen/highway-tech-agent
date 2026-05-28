package com.highway.agent.interview.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.interview.model.agent.InterviewPlanResult;
import com.highway.agent.interview.model.agent.QuestionPlanningResult;
import com.highway.agent.interview.model.agent.ResumeAnalysisResult;
import com.highway.agent.interview.prompt.InterviewAgentPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionPlanningAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public QuestionPlanningResult planQuestions(ResumeAnalysisResult analysisResult, InterviewPlanResult interviewPlanResult) {
        BeanOutputConverter<QuestionPlanningResult> converter = new BeanOutputConverter<>(QuestionPlanningResult.class);
        String userPrompt = InterviewAgentPrompt.QUESTION_PLANNING_USER
                .replace("{analysisJson}", toJson(analysisResult))
                .replace("{planJson}", toJson(interviewPlanResult))
                .replace("{format}", converter.getFormat());
        String response = chatClient.prompt()
                .system(InterviewAgentPrompt.QUESTION_PLANNING_SYSTEM)
                .user(userPrompt)
                .call()
                .content();
        QuestionPlanningResult result = parse(response);
        validate(result);
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("题目规划输入序列化失败", e);
        }
    }

    private QuestionPlanningResult parse(String response) {
        try {
            return objectMapper.readValue(InterviewJsonParser.extractJson(response), QuestionPlanningResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("题目规划结果解析失败", e);
        }
    }

    private void validate(QuestionPlanningResult result) {
        if (result.items() == null || result.items().size() != 8) {
            throw new IllegalStateException("题目规划必须包含 8 个题目焦点");
        }
        Map<Integer, Long> countByRound = result.items().stream()
                .collect(Collectors.groupingBy(QuestionPlanningResult.Item::roundNumber, Collectors.counting()));
        for (int roundNumber = 1; roundNumber <= 4; roundNumber++) {
            if (countByRound.getOrDefault(roundNumber, 0L) != 2L) {
                throw new IllegalStateException("每轮必须包含 2 个题目焦点: " + roundNumber);
            }
        }
        result.items().forEach(item -> {
            if (item.questionNumber() == null || item.questionNumber() < 1 || item.questionNumber() > 2) {
                throw new IllegalStateException("题目规划题号必须是 1 或 2");
            }
            if (item.focus() == null || item.focus().isBlank()) {
                throw new IllegalStateException("题目规划缺少 focus");
            }
        });
    }
}
