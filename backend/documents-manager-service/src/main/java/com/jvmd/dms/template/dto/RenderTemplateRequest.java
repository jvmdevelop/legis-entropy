package com.jvmd.dms.template.dto;

import lombok.Data;

import java.util.Map;

@Data
public class RenderTemplateRequest {

    private String title;
    private String graphId;
    private String situationId;

    private Map<String, String> variables;
}
