package com.highway.agent.interview.model.agent;

import java.util.List;

public record EvaluationResult(
        String overallGrade,
        String overallFeedback,
        List<String> improvementSuggestions,
        List<QuestionFeedback> questionFeedbacks,
        String markdownReport,
        String htmlReport
) {
    public record QuestionFeedback(
            Long questionId,
            String feedback
    ) {
    }
}
