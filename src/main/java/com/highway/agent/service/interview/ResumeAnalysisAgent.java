package com.highway.agent.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.model.interview.agent.ResumeAnalysisResult;
import com.highway.agent.prompt.InterviewAgentPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class ResumeAnalysisAgent {

    private static final Set<String> SENIORITY_LEVELS = Set.of("JUNIOR", "MID", "SENIOR", "EXPERT");

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public ResumeAnalysisResult analyze(String resumeText) {
        BeanOutputConverter<ResumeAnalysisResult> converter = new BeanOutputConverter<>(ResumeAnalysisResult.class);
        String userPrompt = InterviewAgentPrompt.RESUME_ANALYSIS_USER
                .replace("{resumeText}", resumeText)
                .replace("{format}", converter.getFormat());
        String response = chatClient.prompt()
                .system(InterviewAgentPrompt.RESUME_ANALYSIS_SYSTEM)
                .user(userPrompt)
                .call()
                .content();
        ResumeAnalysisResult result = parse(response);
        validate(result);
        return result;
    }

    private ResumeAnalysisResult parse(String response) {
        try {
            return objectMapper.readValue(InterviewJsonParser.extractJson(response), ResumeAnalysisResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("简历分析结果解析失败", e);
        }
    }

    private void validate(ResumeAnalysisResult result) {
        if (result.targetPosition() == null || result.targetPosition().isBlank()) {
            throw new IllegalStateException("简历分析结果缺少目标岗位");
        }
        if (result.seniorityLevel() == null || result.seniorityLevel().isBlank()) {
            throw new IllegalStateException("简历分析结果缺少候选人级别");
        }
        if (!SENIORITY_LEVELS.contains(result.seniorityLevel())) {
            throw new IllegalStateException("候选人级别不合法: " + result.seniorityLevel());
        }
        if (result.difficultyStrategy() == null || result.difficultyStrategy().isBlank()) {
            throw new IllegalStateException("简历分析结果缺少难度策略");
        }
        if (result.techTags() == null || result.techTags().isEmpty()) {
            throw new IllegalStateException("简历分析结果缺少技术标签");
        }
        if (result.summary() == null || result.summary().isBlank()) {
            throw new IllegalStateException("简历分析结果缺少摘要");
        }
    }
}
