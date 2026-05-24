package com.highway.agent.model.interview.agent;

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
