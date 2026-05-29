package com.jvmd.llmbrainservice.dto;

public record RiskScanRequest(String text, String country) {
    public RiskScanRequest {
        if (country == null || country.isBlank()) country = "RK";
    }
}
