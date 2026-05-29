package com.jvmd.dms.dto;

import com.jvmd.dms.service.DocumentProcessingStatus;

import java.time.LocalDateTime;

public record DocumentStatusResponse(
        String documentId,
        String userId,
        String fileName,
        DocumentProcessingStatus status,
        Integer chunkCount,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
