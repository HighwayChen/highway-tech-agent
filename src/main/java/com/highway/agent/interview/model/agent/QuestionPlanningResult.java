package com.highway.agent.interview.model.agent;

import java.util.List;

public record QuestionPlanningResult(List<Item> items) {
    public record Item(
            Integer roundNumber,
            Integer questionNumber,
            String focus,
            List<String> avoidOverlapWith
    ) {
    }
}
