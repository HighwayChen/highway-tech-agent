package com.highway.agent.model.interview.agent;

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
