package com.jvmd.graphsservice.dto;

import lombok.Data;

@Data
public class UpsertVoiceEvidenceRequest {
    private String label;
    private String classification;
    private String severity;
    private String summary;
    private String speakers;

    private String situationId;
}
