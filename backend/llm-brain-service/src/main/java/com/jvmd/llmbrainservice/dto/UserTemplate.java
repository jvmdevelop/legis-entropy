package com.jvmd.llmbrainservice.dto;


public record UserTemplate(String code, String title, String description, String kind, String body,
                           String suggestedLawCodes, String sourceFormat, boolean systemTemplate) {
}
