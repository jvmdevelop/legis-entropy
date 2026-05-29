package com.jvmd.llmbrainservice.service.graph.link;

public record LinkableSubject(SubjectKind kind, String id, String userId, String label) {

    public LinkableSubject {
        if (kind == null) throw new IllegalArgumentException("kind is required");
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
    }

    public static LinkableSubject document(String id, String userId) {
        return new LinkableSubject(SubjectKind.DOCUMENT, id, userId, null);
    }

    public static LinkableSubject situation(String id, String userId, String label) {
        return new LinkableSubject(SubjectKind.SITUATION, id, userId, label);
    }

    public static LinkableSubject voice(String id, String userId, String label) {
        return new LinkableSubject(SubjectKind.VOICE_EVIDENCE, id, userId, label);
    }
}
