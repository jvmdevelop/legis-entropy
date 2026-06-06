package com.jvmd.situationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConflictRowDTO {
    private String kind;
    private String codeA;
    private String numberA;
    private String titleA;
    private String codeB;
    private String numberB;
    private String titleB;
    private String documentId;
    private String clauseRef;
    private String reason;
    private Double confidence;
    private LocalDateTime extractedAt;
}
