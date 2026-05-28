package com.highway.agent.interview.model;

public record ResumeUploadResponse(
        Long id,
        String fileName,
        String status
) {
}
