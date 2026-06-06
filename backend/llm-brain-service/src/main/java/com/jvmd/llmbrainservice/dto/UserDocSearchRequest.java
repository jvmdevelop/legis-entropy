package com.jvmd.llmbrainservice.dto;

public record UserDocSearchRequest(
        String query,
        String userId,
        String documentId
    ) {}

    