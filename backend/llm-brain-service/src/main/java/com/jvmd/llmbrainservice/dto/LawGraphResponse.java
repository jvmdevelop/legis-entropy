package com.jvmd.llmbrainservice.dto;

public record LawGraphResponse(
        LawInfo law,
        Object relationships
) {
    public String formatForLLM() {
        StringBuilder sb = new StringBuilder();
        sb.append("**").append(law.code()).append(": ").append(law.title()).append("**\n\n");
        if (law.summary() != null && !law.summary().isEmpty()) {
            sb.append("Описание: ").append(law.summary()).append("\n\n");
        }
        sb.append("Статус: ").append(law.status()).append("\n");
        sb.append("Тип: ").append(law.type()).append("\n");
        if (law.adoptionDate() != null) {
            sb.append("Принят: ").append(law.adoptionDate()).append("\n");
        }
        if (law.effectiveDate() != null) {
            sb.append("В силе: ").append(law.effectiveDate()).append("\n");
        }
        if (relationships != null) {
            sb.append("\n**Связи с другими нормами:**\n");
            sb.append(relationships.toString());
        }
        return sb.toString();
    }
}