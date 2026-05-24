package com.highway.agent.model.interview.agent;

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
