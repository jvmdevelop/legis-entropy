package com.jvmd.graphsservice.dto;

import lombok.Data;

@Data
public class ContractScanRequest {
    private String text;
    private String country;
}
