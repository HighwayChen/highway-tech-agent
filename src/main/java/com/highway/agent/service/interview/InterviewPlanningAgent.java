package com.highway.agent.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.model.interview.agent.InterviewPlanResult;
import com.highway.agent.model.interview.agent.ResumeAnalysisResult;
import com.highway.agent.prompt.InterviewAgentPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewPlanningAgent {

    private static final List<String> REQUIRED_ROUND_NAMES = List.of(
            "语言基础",
            "主技术栈与框架能力",
            "简历项目深挖",
            "工程素养与问题排查"
    );

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public InterviewPlanResult plan(ResumeAnalysisResult analysisResult, String resumeText) {
        BeanOutputConverter<InterviewPlanResult> converter = new BeanOutputConverter<>(InterviewPlanResult.class);
        String userPrompt = InterviewAgentPrompt.INTERVIEW_PLANNING_USER
                .replace("{analysisJson}", toJson(analysisResult))
                .replace("{resumeText}", resumeText)
                .replace("{format}", converter.getFormat());
        String response = chatClient.prompt()
                .system(InterviewAgentPrompt.INTERVIEW_PLANNING_SYSTEM)
                .user(userPrompt)
                .call()
                .content();
        InterviewPlanResult result = parse(response);
        validate(result);
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("面试计划输入序列化失败", e);
        }
    }

    private InterviewPlanResult parse(String response) {
        try {
            return objectMapper.readValue(InterviewJsonParser.extractJson(response), InterviewPlanResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("面试计划结果解析失败", e);
        }
    }

    private void validate(InterviewPlanResult result) {
        if (result.summary() == null || result.summary().isBlank()) {
            throw new IllegalStateException("面试计划缺少摘要");
        }
        if (result.rounds() == null || result.rounds().size() != 4) {
            throw new IllegalStateException("面试计划必须包含固定 4 轮");
        }
        for (int i = 0; i < REQUIRED_ROUND_NAMES.size(); i++) {
            InterviewPlanResult.RoundPlan round = result.rounds().get(i);
            int expectedRoundNumber = i + 1;
            if (!Integer.valueOf(expectedRoundNumber).equals(round.roundNumber())) {
                throw new IllegalStateException("面试计划轮次编号不正确: " + round.roundNumber());
            }
            if (!REQUIRED_ROUND_NAMES.get(i).equals(round.roundName())) {
                throw new IllegalStateException("面试计划轮次名称不正确: " + round.roundName());
            }
            if (round.difficulty() == null || round.difficulty().isBlank()) {
                throw new IllegalStateException("面试计划缺少轮次难度: " + expectedRoundNumber);
            }
            if (round.difficultyReason() == null || round.difficultyReason().isBlank()) {
                throw new IllegalStateException("面试计划缺少难度依据: " + expectedRoundNumber);
            }
            if (round.focus() == null || round.focus().isBlank()) {
                throw new IllegalStateException("面试计划缺少轮次关注点: " + expectedRoundNumber);
            }
        }
    }
}
