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
public class CitationCheckDTO {

    private String raw;

    private String number;

    private String inferredCode;

    private String contextSnippet;

    private boolean foundInGraph;

    private String articleTitle;

    private LocalDate lastAmendmentDate;

    private String anchor;

    private String sourceUri;

    private String status;
}
