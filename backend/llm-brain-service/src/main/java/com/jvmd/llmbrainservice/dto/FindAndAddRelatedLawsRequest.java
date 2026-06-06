package com.jvmd.llmbrainservice.dto;

public record FindAndAddRelatedLawsRequest(
        String graphId,
        String query,
        String country,
        Integer limit
    ) {}

    