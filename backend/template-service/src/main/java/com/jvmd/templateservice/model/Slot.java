package com.jvmd.templateservice.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Slot {

    private String id;
    private String label;
    private String context;
    private String type;
    private Placement placement;
    private String aiHint;
    private List<String> examples;
    private boolean required;
    private boolean multiline;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Placement {
        private String preceding;
        private String following;
    }

    public enum SemanticType {
        TEXT, NAME, DATE, ADDRESS, PHONE, AMOUNT, EMAIL, MULTILINE, ENUM, ARTICLE_REF;

        public static SemanticType ofValue(String value) {
            if (value == null) return TEXT;
            try {
                return SemanticType.valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return TEXT;
            }
        }
    }
}
