package com.jvmd.llmbrainservice.service.graph.link;

import com.jvmd.llmbrainservice.client.SituationClient;
import com.jvmd.llmbrainservice.model.BrainRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SituationSubjectLinker implements SubjectLinker {

    private final SituationClient situationClient;

    @Override
    public SubjectKind kind() {
        return SubjectKind.SITUATION;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public boolean canHandle(BrainRequest request) {
        return request.hasSituationId() && request.hasGraphId();
    }

    @Override
    public Optional<LinkableSubject> resolve(BrainRequest request) {
        if (!canHandle(request)) return Optional.empty();
        try {
            Map<String, Object> situation = situationClient.getSituation(request.graphId(), request.situationId());
            if (situation == null) return Optional.empty();

            String label = stringFromMap(situation, "title");
            return Optional.of(LinkableSubject.situation(
                    request.situationId(),
                    request.userId() == null ? "" : request.userId(),
                    label));
        } catch (Exception e) {
            log.warn("Failed to resolve situation {} in graph {}: {}", request.situationId(), request.graphId(), e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public String fetchSearchableText(LinkableSubject subject, String graphId) {
        if (graphId == null || graphId.isBlank()) return "";
        try {
            Map<String, Object> map = situationClient.getSituation(graphId, subject.id());
            if (map == null) return "";

            String plain = stringFromMap(map, "plainText");
            if (plain != null && !plain.isBlank()) return plain;

            String body = stringFromMap(map, "body");
            return body == null ? "" : body;
        } catch (Exception e) {
            log.warn("Failed to fetch searchable text for situation {}: {}", subject.id(), e.getMessage());
            return "";
        }
    }

    @Override
    public boolean linkToLaw(String graphId, LinkableSubject subject, String lawCode, String country) {
        try {
            situationClient.linkSituationToLaw(graphId, subject.id(), lawCode, country, "SEMANTIC");
            return true;
        } catch (Exception e) {
            log.warn("Failed to link situation {} to law {}: {}", subject.id(), lawCode, e.getMessage());
            return false;
        }
    }

    @Override
    public String displayName() {
        return "Ситуация";
    }

    @Override
    public String displayNameAccusative() {
        return "ситуация";
    }

    @Override
    public String displayNameGenitive() {
        return "ситуации";
    }

    private static String stringFromMap(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}