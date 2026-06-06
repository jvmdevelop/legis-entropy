package com.jvmd.llmbrainservice.dto;

public record LinkDocumentToLawsRequest(
        String graphId,
        String documentId,
        String userId,
        Integer limit
    ) {}

    