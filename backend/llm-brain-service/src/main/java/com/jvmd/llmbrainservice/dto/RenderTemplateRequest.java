package com.jvmd.llmbrainservice.dto;

import java.util.Map;

public record RenderTemplateRequest(
        String title,
        Map<String, Object> variables,
        String graphId,
        String situationId
) {
    public RenderTemplateRequest(String title, String graphId, String situationId) {
        this(title, Map.of(), graphId, situationId);
    }
}