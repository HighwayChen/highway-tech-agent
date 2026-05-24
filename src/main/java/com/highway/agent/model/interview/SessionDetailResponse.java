package com.highway.agent.model.interview;

import java.time.LocalDateTime;

public record SessionDetailResponse(
        Long id,
        Long resumeId,
        String resumeFileName,
        String status,
        String overallGrade,
        String overallFeedback,
        String improvementSuggestions,
        String failureReason,
        Integer answeredCount,
        Integer totalCount,
        TaskSummaryResponse latestTask,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
