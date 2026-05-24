package com.highway.agent.model.interview.agent;

import java.util.List;

public record GeneratedQuestionResult(
        Integer roundNumber,
        String roundName,
        String difficulty,
        Integer questionNumber,
        String content,
        List<String> scoringPoints
) {
}
