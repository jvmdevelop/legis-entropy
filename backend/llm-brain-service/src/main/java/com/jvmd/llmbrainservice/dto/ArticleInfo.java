package com.jvmd.llmbrainservice.dto;

public record ArticleInfo(
        Long id,
        String lawCode,
        String country,
        String number,
        String title,
        String body,
        String source,
        String extractedBy,
        Double confidence,
        String verifiedBy,
        String sourceUri
) {}