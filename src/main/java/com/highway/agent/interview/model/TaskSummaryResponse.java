package com.highway.agent.interview.model;

import java.time.LocalDateTime;

public record TaskSummaryResponse(
        Long id,
        String taskType,
        String status,
        Integer retryCount,
        String failureReason,
        String inputSummary,
        String outputSummary,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createdAt
) {
}
