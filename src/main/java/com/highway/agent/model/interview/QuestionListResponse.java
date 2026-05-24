package com.highway.agent.model.interview;

import java.util.List;

public record QuestionListResponse(Long sessionId, List<RoundGroup> rounds) {
    public record RoundGroup(
            Integer roundNumber,
            String roundName,
            String difficulty,
            List<QuestionItem> questions
    ) {
    }

    public record QuestionItem(
            Long id,
            Integer questionNumber,
            String content,
            String userAnswer,
            String feedback
    ) {
    }
}
