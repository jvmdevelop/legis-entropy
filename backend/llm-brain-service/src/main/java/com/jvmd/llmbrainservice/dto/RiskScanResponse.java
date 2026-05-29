package com.jvmd.llmbrainservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScanResponse {
    private String contractType;
    private List<ClauseRiskResult> clauses;
    private List<MissingClauseResult> missingClauses;
    private int totalViolations;
    private int totalWarnings;
    private int totalCompliant;
}
