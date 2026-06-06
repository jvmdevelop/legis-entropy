package com.jvmd.llmbrainservice.dto;

public record CreateSituationRequest(
        String title,
        String body,
        String plainText
) {
    public CreateSituationRequest(String title, String plainText) {
        this(
            title != null ? title : "Ситуация",
            plainText != null ? plainText : "",
            plainText != null ? plainText : ""
        );
    }
}