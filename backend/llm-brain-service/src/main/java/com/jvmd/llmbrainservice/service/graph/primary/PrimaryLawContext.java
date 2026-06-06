package com.jvmd.llmbrainservice.service.graph.primary;

import com.jvmd.llmbrainservice.dto.LawInfo;
import com.jvmd.llmbrainservice.service.graph.link.LinkableSubject;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinker;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record PrimaryLawContext(
        String docText,
        String country,
        SubjectLinker linker,
        LinkableSubject subject,
        String graphId,
        int targetCount,
        Map<String, LawInfo> already
) {
    public PrimaryLawContext withAlready(LinkedHashMap<String, LawInfo> updated) {
        return new PrimaryLawContext(docText, country, linker, subject, graphId, targetCount,
                Collections.unmodifiableMap(updated));
    }
}
