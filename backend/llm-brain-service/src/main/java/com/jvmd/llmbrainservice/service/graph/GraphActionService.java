package com.jvmd.llmbrainservice.service.graph;

import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.service.graph.link.LinkableSubject;
import com.jvmd.llmbrainservice.service.graph.link.SubjectAwareLinkService;
import com.jvmd.llmbrainservice.service.graph.link.SubjectKind;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinker;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinkerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphActionService {

    private final GraphServiceClient graphServiceClient;
    private final SubjectLinkerRegistry linkerRegistry;
    private final SubjectAwareLinkService linkService;

    public record LinkResult(int linked, String report, List<String> lawCodes) {
        static LinkResult from(SubjectAwareLinkService.LinkResult r) {
            return new LinkResult(r.linked(), r.report(), r.lawCodes());
        }
    }

    public record AddResult(boolean added, String code, String title, String report) {}

    public LinkResult linkRequestSubjectToLaws(BrainRequest request, int limit, String country) {
        var maybeLinker = linkerRegistry.selectFor(request);
        if (maybeLinker.isEmpty()) {
            return new LinkResult(0,
                "В запросе не указан ни один объект для связывания (документ / ситуация / голосовое сообщение).",
                List.of());
        }
        SubjectLinker linker = maybeLinker.get();
        return linker.resolve(request)
            .map(subject -> LinkResult.from(linkService.linkSubjectToLaws(linker, subject, request.graphId(), limit, country)))
            .orElseGet(() -> new LinkResult(0,
                "Не удалось извлечь " + linker.displayNameAccusative() + " из запроса.",
                List.of()));
    }

    public LinkResult linkDocumentToLaws(String graphId, String documentId, String userId, int limit, String country) {
        SubjectLinker linker = requireLinker(SubjectKind.DOCUMENT);
        LinkableSubject subject = LinkableSubject.document(documentId, safeUserId(userId));
        return LinkResult.from(linkService.linkSubjectToLaws(linker, subject, graphId, limit, country));
    }

    public LinkResult linkSituationToLaws(String graphId, String situationId, String userId, int limit, String country) {
        SubjectLinker linker = requireLinker(SubjectKind.SITUATION);
        String uid = safeUserId(userId);
        BrainRequest probe = new BrainRequest("", uid, null, graphId, situationId, null, null);
        LinkableSubject subject = linker.resolve(probe).orElse(LinkableSubject.situation(situationId, uid, null));
        return LinkResult.from(linkService.linkSubjectToLaws(linker, subject, graphId, limit, country));
    }

    public LinkResult linkVoiceEvidenceToLaws(String graphId, String voiceId, String userId, int limit, String country) {
        SubjectLinker linker = requireLinker(SubjectKind.VOICE_EVIDENCE);
        String uid = safeUserId(userId);
        BrainRequest probe = new BrainRequest("", uid, null, graphId, null, voiceId, null);
        LinkableSubject subject = linker.resolve(probe).orElse(LinkableSubject.voice(voiceId, uid, null));
        return LinkResult.from(linkService.linkSubjectToLaws(linker, subject, graphId, limit, country));
    }

    public AddResult addLawToGraph(String graphId, String code, String country) {
        String ctry = defaultCountry(country);
        if (tryAddLaw(graphId, code, ctry)) {
            return new AddResult(true, code, null, "Закон " + code + " добавлен в граф.");
        }
        var found = graphServiceClient.searchLaws(code, ctry);
        if (!found.isEmpty()) {
            var first = found.get(0);
            if (tryAddLaw(graphId, first.code(), ctry)) {
                return new AddResult(true, first.code(), first.title(),
                    "Добавлен закон " + first.code() + ": " + first.title());
            }
        }
        return new AddResult(false, code, null, "Не удалось добавить закон '" + code + "' — не найден в базе.");
    }

    public LinkResult findAndAddLaws(String graphId, String query, String country, int limit) {
        String ctry = defaultCountry(country);
        int safeLimit = Math.max(1, Math.min(limit, 10));
        var laws = graphServiceClient.searchLaws(query, ctry);
        if (laws.isEmpty()) {
            return new LinkResult(0, "Ничего не найдено по запросу «" + query + "».", List.of());
        }
        int added = 0;
        List<String> codes = new ArrayList<>();
        StringBuilder report = new StringBuilder("Найдено и добавлено в граф:\n");
        for (var law : laws.stream().limit(safeLimit).toList()) {
            if (tryAddLaw(graphId, law.code(), ctry)) {
                added++;
                codes.add(law.code());
                report.append("- **").append(law.code()).append("**")
                    .append(law.title() == null ? "" : ": " + law.title())
                    .append("\n");
            }
        }
        if (added == 0) {
            return new LinkResult(0, "Найдены законы, но ни один не удалось добавить в граф.", codes);
        }
        return new LinkResult(added, report.toString(), codes);
    }

    private SubjectLinker requireLinker(SubjectKind kind) {
        return linkerRegistry.byKind(kind)
            .orElseThrow(() -> new IllegalStateException(kind + " linker is not registered"));
    }

    private boolean tryAddLaw(String graphId, String code, String country) {
        try {
            graphServiceClient.addLawToUserGraph(graphId, code, country);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String safeUserId(String userId) {
        return userId == null ? "" : userId;
    }

    private static String defaultCountry(String country) {
        return country == null || country.isBlank() ? "RK" : country;
    }
}
