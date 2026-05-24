package com.highway.agent.model.interview;

import java.time.LocalDateTime;
import java.util.List;

public record ResumeListResponse(
        long page,
        long pageSize,
        long total,
        List<Item> items
) {
    public record Item(
            Long id,
            String fileName,
            String status,
            String techTags,
            String targetPosition,
            String analysisSummary,
            String failureReason,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }
}
