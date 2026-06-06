package com.jvmd.llmbrainservice.dto;

public record ProgressEvent(
        String phase,
        int done,
        int total,
        String message
    ) {}