package com.jvmd.llmbrainservice.dto;

public record FlagDocumentArticleConflictRequest(
        String graphId,
        String documentId,
        String lawCode,
        String country,
        String articleNumber,
        String clauseRef,
        String reason,
        Double confidence
    ) {}