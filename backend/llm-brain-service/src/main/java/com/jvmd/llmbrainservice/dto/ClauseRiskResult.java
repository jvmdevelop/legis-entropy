package com.jvmd.llmbrainservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClauseRiskResult {
    private String clauseId;
    private String clauseText;
    private String riskLevel;
    private String lawCitation;
    private String reason;
}
