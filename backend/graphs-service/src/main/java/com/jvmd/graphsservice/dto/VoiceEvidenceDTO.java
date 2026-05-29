package com.jvmd.graphsservice.dto;

import com.jvmd.graphsservice.model.VoiceEvidence;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceEvidenceDTO {
    private String id;
    private String graphId;
    private String userId;
    private String label;
    private String classification;
    private String severity;
    private String summary;
    private String speakers;
    private LocalDateTime createdAt;

    public static VoiceEvidenceDTO from(VoiceEvidence v) {
        if (v == null) return null;
        return VoiceEvidenceDTO.builder()
                .id(v.getId())
                .graphId(v.getGraphId())
                .userId(v.getUserId())
                .label(v.getLabel())
                .classification(v.getClassification())
                .severity(v.getSeverity())
                .summary(v.getSummary())
                .speakers(v.getSpeakers())
                .createdAt(v.getCreatedAt())
                .build();
    }
}
