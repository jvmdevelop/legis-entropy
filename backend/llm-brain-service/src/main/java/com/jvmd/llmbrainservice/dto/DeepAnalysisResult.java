package com.jvmd.llmbrainservice.dto;

public record DeepAnalysisResult(
        int primaryLaws,
        int secondaryLaws,
        int tertiaryLaws,
        int articlesLinked,
        int conflictsFound,
        String report
    ) {}