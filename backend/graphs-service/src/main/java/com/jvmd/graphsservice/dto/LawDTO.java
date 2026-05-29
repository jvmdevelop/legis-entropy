package com.jvmd.graphsservice.dto;

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
public class LawDTO {
    private Long id;
    private String title;
    private String code;
    private String country;
    private String type;
    private LocalDate adoptionDate;
    private LocalDate effectiveDate;
    private String status;
    private String summary;

    private ProvenanceSource source;
    private String extractedBy;
    private LocalDateTime extractedAt;
    private Double confidence;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private String sourceUri;
}
