package com.jvmd.graphsservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Provenance {
    private ProvenanceSource source;
    private String extractedBy;
    private LocalDateTime extractedAt;
    private Double confidence;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private String sourceUri;

    public static Provenance llm(String modelId, double confidence) {
        return Provenance.builder()
                .source(ProvenanceSource.LLM_INFERENCE)
                .extractedBy(modelId)
                .confidence(confidence)
                .extractedAt(LocalDateTime.now())
                .build();
    }

    public static Provenance govParser(String sourceUri) {
        return Provenance.builder()
                .source(ProvenanceSource.GOV_PARSER)
                .extractedBy("gov-parser")
                .extractedAt(LocalDateTime.now())
                .sourceUri(sourceUri)
                .confidence(1.0)
                .build();
    }

    public static Provenance manual(String userId) {
        return Provenance.builder()
                .source(ProvenanceSource.USER_MANUAL)
                .extractedBy(userId)
                .extractedAt(LocalDateTime.now())
                .confidence(1.0)
                .build();
    }
}
