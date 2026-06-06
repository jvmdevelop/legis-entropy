package com.jvmd.templateservice.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RenderTemplateRequest {
    private String title;
    private String graphId;
    private String situationId;
    private Map<String, Object> variables;
}
