package com.jvmd.graphsservice.dto;

import com.jvmd.graphsservice.model.ProvenanceSource;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateArticleRequest {
    private String lawCode;
    private String country;
    private String number;
    private String title;
    private String body;
    private LocalDate validFrom;
    private LocalDate validUntil;

    private ProvenanceSource source;
    private String extractedBy;
    private Double confidence;
    private String sourceUri;
}
