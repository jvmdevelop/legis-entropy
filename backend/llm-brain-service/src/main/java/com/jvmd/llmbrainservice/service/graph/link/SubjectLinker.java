package com.jvmd.llmbrainservice.service.graph.link;

import com.jvmd.llmbrainservice.model.BrainRequest;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface SubjectLinker {

    SubjectKind kind();

    default int priority() {
        return 10;
    }

    boolean canHandle(BrainRequest request);

    Optional<LinkableSubject> resolve(BrainRequest request);

    String fetchSearchableText(LinkableSubject subject, String graphId);

    boolean linkToLaw(String graphId, LinkableSubject subject, String lawCode, String country);

    default boolean linkClauseToArticle(String graphId,
                                        LinkableSubject subject,
                                        String lawCode,
                                        String country,
                                        String articleNumber,
                                        String clauseRef,
                                        String subjectSnippet,
                                        String articleSnippet,
                                        double confidence) {
        return false;
    }

    default boolean flagConflictOnArticle(String graphId,
                                          LinkableSubject subject,
                                          String lawCode,
                                          String country,
                                          String articleNumber,
                                          String clauseRef,
                                          String reason,
                                          double confidence) {
        return false;
    }

    default List<String> suggestedLawCodes(LinkableSubject subject, String graphId) {
        return Collections.emptyList();
    }

    String displayName();

    default String displayNameAccusative() {
        return displayName();
    }

    default String displayNameGenitive() {
        return displayName();
    }
}
