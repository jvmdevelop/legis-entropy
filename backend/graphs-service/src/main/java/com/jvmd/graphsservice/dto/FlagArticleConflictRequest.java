package com.jvmd.graphsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlagArticleConflictRequest {
    private String graphId;
    private String country;
    private String codeA;
    private String numberA;
    private String codeB;
    private String numberB;
    private String reason;
    private Double confidence;
    private String extractedBy;
}
