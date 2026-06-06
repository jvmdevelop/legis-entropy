package com.jvmd.situationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlagDocumentArticleConflictRequest {
    private String graphId;
    private String documentId;
    private String lawCode;
    private String country;
    private String articleNumber;
    private String clauseRef;
    private String reason;
    private Double confidence;
    private String extractedBy;
}
