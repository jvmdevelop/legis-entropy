package com.jvmd.documentservice.dto;

import com.jvmd.documentservice.service.DocumentProcessingStatus;

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
