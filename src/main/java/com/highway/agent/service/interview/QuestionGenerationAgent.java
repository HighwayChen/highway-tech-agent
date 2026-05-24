package com.highway.agent.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.model.interview.agent.GeneratedQuestionListResult;
import com.highway.agent.model.interview.agent.GeneratedQuestionResult;
import com.highway.agent.model.interview.agent.InterviewPlanResult;
import com.highway.agent.model.interview.agent.QuestionPlanningResult;
import com.highway.agent.model.interview.agent.ResumeAnalysisResult;
import com.highway.agent.prompt.InterviewAgentPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuestionGenerationAgent {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public List<GeneratedQuestionResult> generateRoundQuestions(
            ResumeAnalysisResult analysisResult,
            InterviewPlanResult interviewPlanResult,
            InterviewPlanResult.RoundPlan roundPlan,
            List<QuestionPlanningResult.Item> planningItems) {
        BeanOutputConverter<GeneratedQuestionListResult> converter = new BeanOutputConverter<>(GeneratedQuestionListResult.class);
        String userPrompt = InterviewAgentPrompt.QUESTION_GENERATION_USER
                .replace("{analysisJson}", toJson(analysisResult))
                .replace("{planJson}", toJson(interviewPlanResult))
                .replace("{roundPlanJson}", toJson(roundPlan))
                .replace("{planningItemsJson}", toJson(planningItems))
                .replace("{format}", converter.getFormat());
        String response = chatClient.prompt()
                .system(InterviewAgentPrompt.QUESTION_GENERATION_SYSTEM)
                .user(userPrompt)
                .call()
                .content();
        List<GeneratedQuestionResult> result = parse(response);
        validate(roundPlan, result);
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("题目生成输入序列化失败", e);
        }
    }

    private List<GeneratedQuestionResult> parse(String response) {
        try {
            GeneratedQuestionListResult result = objectMapper.readValue(InterviewJsonParser.extractJson(response), GeneratedQuestionListResult.class);
            return result.questions();
        } catch (Exception e) {
            throw new IllegalStateException("题目生成结果解析失败", e);
        }
    }

    private void validate(InterviewPlanResult.RoundPlan roundPlan, List<GeneratedQuestionResult> result) {
        if (result == null || result.size() != 2) {
            throw new IllegalStateException("每轮必须生成 2 道题");
        }
        for (GeneratedQuestionResult question : result) {
            if (!roundPlan.roundNumber().equals(question.roundNumber())) {
                throw new IllegalStateException("生成题目轮次编号不匹配");
            }
            if (!roundPlan.roundName().equals(question.roundName())) {
                throw new IllegalStateException("生成题目轮次名称不匹配");
            }
            if (!roundPlan.difficulty().equals(question.difficulty())) {
                throw new IllegalStateException("生成题目难度不匹配");
            }
            if (question.questionNumber() == null || question.questionNumber() < 1 || question.questionNumber() > 2) {
                throw new IllegalStateException("生成题目题号必须是 1 或 2");
            }
            if (question.content() == null || question.content().isBlank()) {
                throw new IllegalStateException("生成题目缺少题干");
            }
            if (question.scoringPoints() == null || question.scoringPoints().isEmpty()) {
                throw new IllegalStateException("生成题目缺少评分要点");
            }
        }
    }
}
