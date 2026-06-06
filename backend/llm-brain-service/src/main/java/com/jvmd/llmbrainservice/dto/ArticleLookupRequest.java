package com.jvmd.llmbrainservice.dto;

public record ArticleLookupRequest(
        String code,
        String country,
        String number
    ) {}

    