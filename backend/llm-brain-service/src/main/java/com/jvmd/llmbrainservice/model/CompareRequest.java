package com.jvmd.llmbrainservice.model;

public record CompareRequest(
        String leftKind,
        String leftId,
        String leftLabel,
        String rightKind,
        String rightId,
        String rightLabel,
        String userId
) {
    public boolean isLeftLaw() { return "LAW".equalsIgnoreCase(leftKind); }
    public boolean isRightLaw() { return "RIGHT_LAW".equalsIgnoreCase(rightKind) || "LAW".equalsIgnoreCase(rightKind); }
}
