package com.highway.agent.model.interview.agent;

import java.util.List;

public record ResumeAnalysisResult(
        String targetPosition,
        Integer workYears,
        String salaryExpectation,
        String targetCity,
        String seniorityLevel,
        String difficultyStrategy,
        List<String> techTags,
        String summary,
        List<String> strengths,
        List<String> risks,
        List<String> projectHighlights
) {
}
