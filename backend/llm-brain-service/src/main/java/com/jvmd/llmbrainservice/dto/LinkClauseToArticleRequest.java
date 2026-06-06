package com.jvmd.llmbrainservice.dto;

public record LinkClauseToArticleRequest(
        String graphId,
        String documentId,
        String lawCode,
        String country,
        String articleNumber,
        String clauseRef,
        String documentSnippet,
        String articleSnippet,
        Double confidence
    ) {}

   