package com.jvmd.llmbrainservice.controller;

import com.jvmd.llmbrainservice.dto.RiskScanRequest;
import com.jvmd.llmbrainservice.dto.RiskScanResponse;
import com.jvmd.llmbrainservice.service.contract.ContractRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brain/contracts")
@RequiredArgsConstructor
public class ContractRiskController {

    private final ContractRiskService contractRiskService;

    @PostMapping("/risk-scan")
    public ResponseEntity<RiskScanResponse> riskScan(@RequestBody RiskScanRequest req) {
        if (req == null || req.text() == null || req.text().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(contractRiskService.scan(req.text(), req.country()));
    }
}
