package com.jvmd.llmbrainservice.model;

import lombok.Data;
import java.util.Map;

@Data
public class DocumentResponse {
    private String text;
    private Map<String, Object> metadata;
}
