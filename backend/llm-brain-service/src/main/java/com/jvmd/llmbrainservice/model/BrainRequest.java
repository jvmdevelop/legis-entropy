package com.jvmd.llmbrainservice.model;

import java.util.List;

public record BrainRequest(String message,
                           String userId,
                           String documentId,
                           String graphId,
                           String situationId,
                           String voiceId,
                           List<HistoryEntry> history) {

    public BrainRequest(String message, String userId, String documentId, String graphId, List<HistoryEntry> history) {
        this(message, userId, documentId, graphId, null, null, history);
    }

    public String effectiveDocumentId() {
        return hasDocumentId() ? documentId : "не указан";
    }

    public boolean hasDocumentId() {
        return documentId != null && !documentId.isBlank();
    }

    public boolean hasGraphId() {
        return graphId != null && !graphId.isBlank();
    }

    public boolean hasSituationId() {
        return situationId != null && !situationId.isBlank();
    }

    public boolean hasVoiceId() {
        return voiceId != null && !voiceId.isBlank();
    }

    @Override
    public List<HistoryEntry> history() {
        return history != null ? history : List.of();
    }
}
