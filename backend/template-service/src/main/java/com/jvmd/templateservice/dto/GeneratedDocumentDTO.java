package com.jvmd.templateservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jvmd.templateservice.model.GeneratedDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeneratedDocumentDTO {
    private String id;
    private String userId;
    private String templateId;
    private String templateCode;
    private String graphId;
    private String situationId;
    private String title;
    private String bodyMarkdown;
    private String objectName;
    private String variablesJson;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    public static GeneratedDocumentDTO from(GeneratedDocument d) {
        if (d == null) return null;
        return GeneratedDocumentDTO.builder()
                .id(d.getId()).userId(d.getUserId()).templateId(d.getTemplateId())
                .templateCode(d.getTemplateCode()).graphId(d.getGraphId())
                .situationId(d.getSituationId()).title(d.getTitle())
                .bodyMarkdown(d.getBodyMarkdown()).objectName(d.getObjectName())
                .variablesJson(d.getVariablesJson()).createdAt(d.getCreatedAt()).updatedAt(d.getUpdatedAt())
                .build();
    }
}
