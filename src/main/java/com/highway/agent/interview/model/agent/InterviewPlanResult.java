package com.highway.agent.interview.model.agent;

import java.util.List;

public record InterviewPlanResult(
        String summary,
        List<RoundPlan> rounds
) {
    public record RoundPlan(
            Integer roundNumber,
            String roundName,
            String difficulty,
            String difficultyReason,
            String focus,
            List<String> questionDirections
    ) {
    }
}
