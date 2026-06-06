package com.jvmd.llmbrainservice.dto;

public record CommentArticleRef(
        String lawCode,
        String number,
        String country,
        String reason
) {}