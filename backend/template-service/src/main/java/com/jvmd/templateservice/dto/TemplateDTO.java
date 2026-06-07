package com.jvmd.templateservice.dto;

import com.jvmd.templateservice.model.DocumentTemplate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateDTO {
    private String id;
    private String code;
    private String title;
    private String description;
    private String kind;
    private String body;
    private String placeholdersJson;
    private String slotsJson;
    private String suggestedLawCodes;
    private boolean systemTemplate;
    private String userId;
    private String sourceFormat;
    private String aiSummary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static TemplateDTO from(DocumentTemplate t) {
        if (t == null) return null;
        return TemplateDTO.builder()
                .id(t.getId()).code(t.getCode()).title(t.getTitle())
                .description(t.getDescription()).kind(t.getKind()).body(t.getBody())
                .placeholdersJson(t.getPlaceholdersJson()).slotsJson(t.getSlotsJson())
                .suggestedLawCodes(t.getSuggestedLawCodes()).systemTemplate(t.isSystemTemplate())
                .userId(t.getUserId()).sourceFormat(t.getSourceFormat())
                .aiSummary(t.getAiSummary()).createdAt(t.getCreatedAt()).updatedAt(t.getUpdatedAt())
                .build();
    }
}
