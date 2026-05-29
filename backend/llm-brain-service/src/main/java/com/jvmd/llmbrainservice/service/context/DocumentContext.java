package com.jvmd.llmbrainservice.service.context;

import com.jvmd.llmbrainservice.service.citation.Citation;

import java.util.List;

public record DocumentContext(String text, List<Citation> citations, boolean unavailable, String unavailableReason) {

    public static DocumentContext of(String text) {
        return new DocumentContext(text, List.of(), false, null);
    }

    public static DocumentContext of(String text, List<Citation> citations) {
        return new DocumentContext(text, List.copyOf(citations), false, null);
    }

    public static DocumentContext unavailable(String reason) {
        return new DocumentContext(reason, List.of(), true, reason);
    }

    public boolean hasCitations() {
        return citations != null && !citations.isEmpty();
    }
}
