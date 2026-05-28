package com.highway.agent.interview.service.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.highway.agent.interview.model.InterviewQuestion;
import com.highway.agent.interview.model.agent.EvaluationResult;
import com.highway.agent.interview.model.agent.InterviewPlanResult;
import com.highway.agent.interview.model.agent.ResumeAnalysisResult;
import com.highway.agent.interview.prompt.InterviewAgentPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EvaluationAgent {

    private static final Set<String> GRADES = Set.of("EXCELLENT", "GOOD", "PASS", "FAIL");

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public EvaluationResult evaluate(ResumeAnalysisResult analysisResult, InterviewPlanResult planResult, List<InterviewQuestion> questions) {
        BeanOutputConverter<EvaluationResult> converter = new BeanOutputConverter<>(EvaluationResult.class);
        String userPrompt = InterviewAgentPrompt.EVALUATION_USER
                .replace("{analysisJson}", toJson(analysisResult))
                .replace("{planJson}", toJson(planResult))
                .replace("{questionsJson}", toJson(questions))
                .replace("{format}", converter.getFormat());
        String response = chatClient.prompt()
                .system(InterviewAgentPrompt.EVALUATION_SYSTEM)
                .user(userPrompt)
                .call()
                .content();
        EvaluationResult result = parse(response);
        validate(result, questions);
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("面试评估输入序列化失败", e);
        }
    }

    private EvaluationResult parse(String response) {
        try {
            return objectMapper.readValue(InterviewJsonParser.extractJson(response), EvaluationResult.class);
        } catch (Exception e) {
            throw new IllegalStateException("面试评估结果解析失败", e);
        }
    }

    private void validate(EvaluationResult result, List<InterviewQuestion> questions) {
        if (result.overallGrade() == null || !GRADES.contains(result.overallGrade())) {
            throw new IllegalStateException("面试评估总体等级不合法: " + result.overallGrade());
        }
        if (result.overallFeedback() == null || result.overallFeedback().isBlank()) {
            throw new IllegalStateException("面试评估缺少总体反馈");
        }
        if (result.improvementSuggestions() == null || result.improvementSuggestions().isEmpty()) {
            throw new IllegalStateException("面试评估缺少改进建议");
        }
        if (result.questionFeedbacks() == null || result.questionFeedbacks().size() != questions.size()) {
            throw new IllegalStateException("面试评估必须覆盖全部题目反馈");
        }
        Set<Long> questionIds = questions.stream().map(InterviewQuestion::getId).collect(Collectors.toSet());
        for (EvaluationResult.QuestionFeedback feedback : result.questionFeedbacks()) {
            if (feedback.questionId() == null || !questionIds.contains(feedback.questionId())) {
                throw new IllegalStateException("面试评估题目反馈 ID 不合法: " + feedback.questionId());
            }
            if (feedback.feedback() == null || feedback.feedback().isBlank()) {
                throw new IllegalStateException("面试评估题目反馈不能为空: " + feedback.questionId());
            }
        }
        if (result.markdownReport() == null || result.markdownReport().isBlank()) {
            throw new IllegalStateException("面试评估缺少 Markdown 报告");
        }
        if (result.htmlReport() == null || result.htmlReport().isBlank()) {
            throw new IllegalStateException("面试评估缺少 HTML 报告");
        }
    }
}
