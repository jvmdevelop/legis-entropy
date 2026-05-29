package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.dto.ContractScanRequest;
import com.jvmd.graphsservice.dto.ContractScanResponse;
import com.jvmd.graphsservice.service.ContractScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/graph/contracts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ContractScanController {

    private final ContractScanService contractScanService;

    @PostMapping("/scan")
    public ResponseEntity<ContractScanResponse> scan(@RequestBody ContractScanRequest req) {
        if (req == null || req.getText() == null || req.getText().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(contractScanService.scan(req.getText(), req.getCountry()));
    }
}
