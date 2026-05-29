package com.jvmd.graphsservice.dto;

import com.jvmd.graphsservice.model.Article;
import com.jvmd.graphsservice.model.ProvenanceSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleDTO {
    private Long id;
    private String lawCode;
    private String country;
    private String number;
    private String title;
    private String body;
    private String anchor;
    private LocalDate lastAmendmentDate;

    private LocalDate validFrom;
    private LocalDate validUntil;

    private ProvenanceSource source;
    private String extractedBy;
    private LocalDateTime extractedAt;
    private Double confidence;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private String sourceUri;

    public static ArticleDTO from(Article a) {
        if (a == null) return null;
        return ArticleDTO.builder()
                .id(a.getId())
                .lawCode(a.getLawCode())
                .country(a.getCountry())
                .number(a.getNumber())
                .title(a.getTitle())
                .body(a.getBody())
                .anchor(a.getAnchor())
                .lastAmendmentDate(a.getLastAmendmentDate())
                .validFrom(a.getValidFrom())
                .validUntil(a.getValidUntil())
                .source(a.getSource())
                .extractedBy(a.getExtractedBy())
                .extractedAt(a.getExtractedAt())
                .confidence(a.getConfidence())
                .verifiedBy(a.getVerifiedBy())
                .verifiedAt(a.getVerifiedAt())
                .sourceUri(a.getSourceUri())
                .build();
    }
}
