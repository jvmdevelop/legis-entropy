package com.jvmd.graphsservice.dto;

import com.jvmd.graphsservice.model.Situation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SituationDTO {
    private String id;
    private String graphId;
    private String userId;
    private String title;
    private String body;
    private String plainText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SituationDTO from(Situation s) {
        if (s == null) return null;
        return SituationDTO.builder()
                .id(s.getId())
                .graphId(s.getGraphId())
                .userId(s.getUserId())
                .title(s.getTitle())
                .body(s.getBody())
                .plainText(s.getPlainText())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
