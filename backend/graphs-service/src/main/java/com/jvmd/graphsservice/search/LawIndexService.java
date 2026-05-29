package com.jvmd.graphsservice.search;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.jvmd.graphsservice.dto.LawDTO;
import com.jvmd.graphsservice.model.Law;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LawIndexService {

    private final LawSearchRepository searchRepository;
    private final ElasticsearchOperations esOps;

    public void index(Law law) {
        if (law == null || law.getCode() == null) return;
        LawDocument doc = toDocument(law);
        searchRepository.save(doc);
        log.debug(
            "ES indexed law: {} (rank={})",
            doc.getId(),
            doc.getHierarchyRank()
        );
    }

    public void delete(String code, String country) {
        searchRepository.deleteById(docId(code, country));
    }

    public List<LawDTO> search(String query, String country) {
        if (query == null || query.isBlank()) return List.of();

        var esQuery = NativeQuery.builder()
            .withQuery(q ->
                q.bool(b ->
                    b
                        .filter(f ->
                            f.term(t -> t.field("country").value(country))
                        )
                        .should(s ->
                            s.multiMatch(mm ->
                                mm
                                    .query(query)
                                    .fields(
                                        "citationCode^6",
                                        "code^4",
                                        "title^2",
                                        "titleKk^2",
                                        "subjectArea^1.5",
                                        "summary^1",
                                        "summaryKk^1"
                                    )
                                    .type(TextQueryType.BestFields)
                                    .fuzziness("AUTO")
                            )
                        )
                        .minimumShouldMatch("1")
                )
            )
            .withMaxResults(10)
            .build();

        SearchHits<LawDocument> hits = esOps.search(esQuery, LawDocument.class);

        List<LawDTO> results = hits
            .stream()
            .map(h -> toDTO(h.getContent()))
            .toList();

        log.debug(
            "ES search '{}' in {} → {} hits",
            query,
            country,
            results.size()
        );
        return results;
    }

    private static LawDocument toDocument(Law law) {
        boolean isKazakh = "kaz".equalsIgnoreCase(law.getLanguage());
        return LawDocument.builder()
            .id(docId(law.getCode(), law.getCountry()))
            .citationCode(coalesce(law.getCitationCode(), law.getCode()))
            .code(law.getCode())
            .country(coalesce(law.getCountry(), "RK"))
            .status(law.getStatus())
            .type(law.getType())
            .legalForce(law.getLegalForce())
            .title(isKazakh ? null : law.getTitle())
            .titleKk(isKazakh ? law.getTitle() : null)
            .summary(isKazakh ? null : law.getSummary())
            .summaryKk(isKazakh ? law.getSummary() : null)
            .subjectArea(law.getSubjectArea())
            .hierarchyRank(resolveRank(law.getLegalForce()))
            .sourceUri(law.getSourceUri())
            .build();
    }

    private static LawDTO toDTO(LawDocument doc) {
        return LawDTO.builder()
            .code(doc.getCode())
            .title(doc.getTitle())
            .country(doc.getCountry())
            .status(doc.getStatus())
            .type(doc.getType())
            .summary(doc.getSummary())
            .sourceUri(doc.getSourceUri())
            .build();
    }

    static int resolveRank(String legalForce) {
        if (legalForce == null) return 10;
        String f = legalForce.toLowerCase(Locale.ROOT);
        if (f.contains("конституци") && f.contains("закон")) return 2;
        if (f.contains("конституци")) return 1;
        if (f.contains("кодекс")) return 3;
        if (f.contains("закон")) return 4;
        if (f.contains("указ президент")) return 5;
        return 10;
    }

    public static boolean isIndexable(Law law) {
        if (law == null) return false;
        String force = law.getLegalForce();
        if (force != null) {
            String f = force.toLowerCase(Locale.ROOT);
            if (f.startsWith("постановлен")) return false;
            if (f.startsWith("приказ")) return false;
            if (f.startsWith("распоряжен")) return false;
            if (f.startsWith("инструкци")) return false;
            if (f.startsWith("правил")) return false;
        }
        String code = law.getCode();
        if (code != null) {
            String c = code.toLowerCase(Locale.ROOT);
            if (c.startsWith("постановление")) return false;
            if (c.startsWith("приказ")) return false;
            if (c.startsWith("распоряжение")) return false;
        }
        String type = law.getType();
        if (type != null) {
            String t = type.toLowerCase(Locale.ROOT);
            if (t.startsWith("постановлен")) return false;
            if (t.startsWith("приказ")) return false;
        }
        return true;
    }

    private static String docId(String code, String country) {
        return (
            (code == null ? "unknown" : code) +
            "_" +
            (country == null ? "RK" : country)
        );
    }

    private static String coalesce(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }
}
