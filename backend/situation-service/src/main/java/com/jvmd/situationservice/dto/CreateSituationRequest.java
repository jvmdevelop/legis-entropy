package com.jvmd.situationservice.dto;

import lombok.Data;

@Data
public class CreateSituationRequest {
    private String title;
    private String body;
    private String plainText;
}
