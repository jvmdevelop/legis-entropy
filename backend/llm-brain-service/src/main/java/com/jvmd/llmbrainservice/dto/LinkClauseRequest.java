package com.jvmd.llmbrainservice.dto;

public record LinkClauseRequest(
        String graphId,
        String documentId,
        String lawCode,
        String country,
        String articleNumber,
        String clauseRef,
        String documentSnippet,
        String articleSnippet,
        String extractedBy,
        Double confidence
) {}