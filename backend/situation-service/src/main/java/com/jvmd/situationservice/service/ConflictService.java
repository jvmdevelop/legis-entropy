package com.jvmd.situationservice.service;

import com.jvmd.situationservice.dto.ConflictRowDTO;
import com.jvmd.situationservice.dto.FlagArticleConflictRequest;
import com.jvmd.situationservice.dto.FlagDocumentArticleConflictRequest;
import com.jvmd.situationservice.model.ProvenanceSource;
import com.jvmd.situationservice.repository.ConflictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Value;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConflictService {

    private final ConflictRepository conflictRepository;
    private final Neo4jClient neo4jClient;

    public boolean flagArticleConflict(FlagArticleConflictRequest req) {
        String country = req.getCountry() == null ? "RK" : req.getCountry();
        var result = conflictRepository.flagArticleConflict(
                req.getGraphId(), country,
                req.getCodeA(), req.getNumberA(),
                req.getCodeB(), req.getNumberB(),
                req.getReason(), req.getConfidence(),
                ProvenanceSource.LLM_INFERENCE.name(),
                req.getExtractedBy() == null ? "llm-brain" : req.getExtractedBy()
        );
        boolean ok = result != null && result.isPresent();
        if (ok) log.info("Conflict flagged: {} ст.{} <-> {} ст.{} (graph {})",
                req.getCodeA(), req.getNumberA(), req.getCodeB(), req.getNumberB(), req.getGraphId());
        else log.warn("Conflict flag failed: missing article(s) {}-{}/{}-{}",
                req.getCodeA(), req.getNumberA(), req.getCodeB(), req.getNumberB());
        return ok;
    }

    public boolean flagDocumentArticleConflict(FlagDocumentArticleConflictRequest req) {
        String country = req.getCountry() == null ? "RK" : req.getCountry();
        var result = conflictRepository.flagDocumentArticleConflict(
                req.getGraphId(), req.getDocumentId(), country,
                req.getLawCode(), req.getArticleNumber(),
                req.getClauseRef(), req.getReason(), req.getConfidence(),
                ProvenanceSource.LLM_INFERENCE.name(),
                req.getExtractedBy() == null ? "llm-brain" : req.getExtractedBy()
        );
        boolean ok = result != null && result.isPresent();
        if (ok) log.info("Doc-conflict flagged: doc {} (clause {}) <-> {} ст.{} (graph {})",
                req.getDocumentId(), req.getClauseRef(), req.getLawCode(), req.getArticleNumber(), req.getGraphId());
        else log.warn("Doc-conflict flag failed: doc {} -> {} ст.{}", req.getDocumentId(), req.getLawCode(), req.getArticleNumber());
        return ok;
    }

    public int countByGraph(String graphId) {
        return conflictRepository.countByGraph(graphId);
    }

    public List<ConflictRowDTO> listByGraph(String graphId) {
        String cypher = """
                MATCH (a:Article)-[r:CONFLICTS_WITH {graphId: $graphId}]->(b:Article)
                RETURN 'ARTICLE_ARTICLE' AS kind,
                       a.lawCode AS codeA, a.number AS numberA, a.title AS titleA,
                       b.lawCode AS codeB, b.number AS numberB, b.title AS titleB,
                       null AS documentId, null AS clauseRef,
                       r.reason AS reason, r.confidence AS confidence, r.extractedAt AS extractedAt
                UNION
                MATCH (d:UserDocument)-[r:CONFLICTS_WITH {graphId: $graphId}]->(a:Article)
                RETURN 'DOC_ARTICLE' AS kind,
                       a.lawCode AS codeA, a.number AS numberA, a.title AS titleA,
                       null AS codeB, null AS numberB, null AS titleB,
                       d.id AS documentId, r.clauseRef AS clauseRef,
                       r.reason AS reason, r.confidence AS confidence, r.extractedAt AS extractedAt
                """;

        List<ConflictRowDTO> rows = new ArrayList<>(neo4jClient.query(cypher)
                .bindAll(Map.of("graphId", graphId))
                .fetchAs(ConflictRowDTO.class)
                .mappedBy((typeSystem, record) -> ConflictRowDTO.builder()
                        .kind(str(record, "kind")).codeA(str(record, "codeA"))
                        .numberA(str(record, "numberA")).titleA(str(record, "titleA"))
                        .codeB(str(record, "codeB")).numberB(str(record, "numberB"))
                        .titleB(str(record, "titleB")).documentId(str(record, "documentId"))
                        .clauseRef(str(record, "clauseRef")).reason(str(record, "reason"))
                        .confidence(record.get("confidence").isNull() ? null : record.get("confidence").asDouble())
                        .extractedAt(record.get("extractedAt").isNull() ? null : record.get("extractedAt").asLocalDateTime())
                        .build())
                .all());

        rows.sort(Comparator.comparing(ConflictRowDTO::getExtractedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return rows;
    }

    private static String str(org.neo4j.driver.Record record, String field) {
        Value v = record.get(field);
        return v.isNull() ? null : v.asString();
    }
}
