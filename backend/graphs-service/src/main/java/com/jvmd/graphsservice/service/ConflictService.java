package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.dto.ConflictRowDTO;
import com.jvmd.graphsservice.dto.FlagArticleConflictRequest;
import com.jvmd.graphsservice.dto.FlagDocumentArticleConflictRequest;
import com.jvmd.graphsservice.model.ProvenanceSource;
import com.jvmd.graphsservice.repository.ConflictRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import org.neo4j.driver.Value;

import java.util.ArrayList;
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
        return logFlagResult(result != null && result.isPresent(),
                String.format("Conflict flagged: %s ст.%s <-> %s ст.%s (graph %s, conf %s)",
                        req.getCodeA(), req.getNumberA(), req.getCodeB(), req.getNumberB(),
                        req.getGraphId(), req.getConfidence()),
                String.format("Conflict flag failed: missing article(s) %s-%s/%s-%s",
                        req.getCodeA(), req.getNumberA(), req.getCodeB(), req.getNumberB()));
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
        return logFlagResult(result != null && result.isPresent(),
                String.format("Doc-conflict flagged: doc %s (clause %s) <-> %s ст.%s (graph %s, conf %s)",
                        req.getDocumentId(), req.getClauseRef(),
                        req.getLawCode(), req.getArticleNumber(),
                        req.getGraphId(), req.getConfidence()),
                String.format("Doc-conflict flag failed: doc %s -> %s ст.%s",
                        req.getDocumentId(), req.getLawCode(), req.getArticleNumber()));
    }

    private boolean logFlagResult(boolean ok, String successMsg, String failMsg) {
        if (ok) log.info(successMsg); else log.warn(failMsg);
        return ok;
    }

    private static ConflictRowDTO mapConflictRow(org.neo4j.driver.Record record) {
        return ConflictRowDTO.builder()
                .kind(str(record, "kind"))
                .codeA(str(record, "codeA"))
                .numberA(str(record, "numberA"))
                .titleA(str(record, "titleA"))
                .codeB(str(record, "codeB"))
                .numberB(str(record, "numberB"))
                .titleB(str(record, "titleB"))
                .documentId(str(record, "documentId"))
                .clauseRef(str(record, "clauseRef"))
                .reason(str(record, "reason"))
                .confidence(record.get("confidence").isNull() ? null : record.get("confidence").asDouble())
                .extractedAt(record.get("extractedAt").isNull() ? null : record.get("extractedAt").asLocalDateTime())
                .build();
    }

    private static String str(org.neo4j.driver.Record record, String field) {
        Value v = record.get(field);
        return v.isNull() ? null : v.asString();
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
                .mappedBy((typeSystem, record) -> mapConflictRow(record))
                .all());

        rows.sort(java.util.Comparator
                .comparing(ConflictRowDTO::getExtractedAt,
                           java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return rows;
    }
}
