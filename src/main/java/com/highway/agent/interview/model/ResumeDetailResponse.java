package com.highway.agent.interview.model;

import java.time.LocalDateTime;
import java.util.List;

public record ResumeDetailResponse(
        Long id,
        String fileName,
        String status,
        String techTags,
        String targetPosition,
        String analysisSummary,
        String interviewPlanSummary,
        String failureReason,
        TaskSummaryResponse latestTask,
        List<SessionSummary> recentSessions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record SessionSummary(
            Long id,
            String status,
            String overallGrade,
            Integer answeredCount,
            Integer totalCount,
            String failureReason,
            LocalDateTime createdAt
    ) {
    }
}
