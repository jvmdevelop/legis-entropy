package com.jvmd.graphsservice.dto;

import lombok.Data;

@Data
public class CreateSubscriptionRequest {
    private String lawCode;
    private String country;
    private String articleNumber;
}
