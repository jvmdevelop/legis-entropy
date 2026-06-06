package com.jvmd.llmbrainservice.dto;

public record AddLawToGraphRequest(
        String graphId,
        String code,
        String country
    ) {}

    