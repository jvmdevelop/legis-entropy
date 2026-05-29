package com.jvmd.graphsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmendmentEdgeDTO {
    private LocalDate amendmentDate;
    private LocalDate effectiveFrom;
    private String anchor;
    private String contextSnippet;
    private String byLawCode;
    private String byLawTitle;
    private String byLawSourceUri;
    private String byInternalNumber;
}
