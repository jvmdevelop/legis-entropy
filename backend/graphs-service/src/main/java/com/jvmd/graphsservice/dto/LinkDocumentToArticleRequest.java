package com.jvmd.graphsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LinkDocumentToArticleRequest {
    private String graphId;
    private String documentId;
    private String lawCode;
    private String country;
    private String articleNumber;
    private String clauseRef;
    private String documentSnippet;
    private String articleSnippet;
    private String extractedBy;
    private Double confidence;
}
