

package com.jvmd.llmbrainservice.dto;

public record FlagArticleConflictRequest(
        String graphId,
        String country,
        String codeA,
        String numberA,
        String codeB,
        String numberB,
        String reason,
        Double confidence
    ) {}

    