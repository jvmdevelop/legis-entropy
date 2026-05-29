package com.jvmd.graphsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContractScanResponse {
    private List<CitationCheckDTO> citations;
    private int totalCitations;
    private int totalFound;
    private int totalMissing;
    private int totalUnknownCode;
}
