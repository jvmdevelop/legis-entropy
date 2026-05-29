package com.jvmd.llmbrainservice.service.graph.link;

import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.model.BrainRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class SituationSubjectLinker implements SubjectLinker {

    private final GraphServiceClient graphServiceClient;

    @Override
    public SubjectKind kind() { return SubjectKind.SITUATION; }

    @Override
    public int priority() { return 10; }

    @Override
    public boolean canHandle(BrainRequest request) {
        return request.hasSituationId() && request.hasGraphId();
    }

    @Override
    public Optional<LinkableSubject> resolve(BrainRequest request) {
        if (!canHandle(request)) return Optional.empty();
        String label = graphServiceClient.getSituation(request.graphId(), request.situationId())
                .map(map -> stringFromMap(map, "title"))
                .orElse(null);
        return Optional.of(LinkableSubject.situation(
                request.situationId(),
                request.userId() == null ? "" : request.userId(),
                label));
    }

    @Override
    public String fetchSearchableText(LinkableSubject subject, String graphId) {
        if (graphId == null || graphId.isBlank()) return "";
        return graphServiceClient.getSituation(graphId, subject.id())
                .map(map -> {
                    String plain = stringFromMap(map, "plainText");
                    if (plain != null && !plain.isBlank()) return plain;
                    String body = stringFromMap(map, "body");
                    return body == null ? "" : body;
                })
                .orElse("");
    }

    @Override
    public boolean linkToLaw(String graphId, LinkableSubject subject, String lawCode, String country) {
        return graphServiceClient.linkSituationToLaw(graphId, subject.id(), lawCode, country, "SEMANTIC");
    }

    @Override
    public String displayName() { return "Ситуация"; }

    @Override
    public String displayNameAccusative() { return "ситуация"; }

    @Override
    public String displayNameGenitive() { return "ситуации"; }

    private static String stringFromMap(java.util.Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : v.toString();
    }
}
