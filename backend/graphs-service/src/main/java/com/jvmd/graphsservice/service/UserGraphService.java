package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.dto.AddUserDocumentRequest;
import com.jvmd.graphsservice.dto.CreateUserGraphRequest;
import com.jvmd.graphsservice.dto.GraphContentResponse;
import com.jvmd.graphsservice.dto.UserDocumentDTO;
import com.jvmd.graphsservice.dto.UserGraphDTO;
import com.jvmd.graphsservice.model.Law;
import com.jvmd.graphsservice.model.UserDocument;
import com.jvmd.graphsservice.model.UserGraph;
import com.jvmd.graphsservice.repository.UserDocumentRepository;
import com.jvmd.graphsservice.repository.UserGraphRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserGraphService {

    private final UserGraphRepository userGraphRepository;
    private final UserDocumentRepository userDocumentRepository;
    private final Neo4jClient neo4jClient;

    public UserGraphDTO createGraph(
        String userId,
        CreateUserGraphRequest request
    ) {
        UserGraph graph = new UserGraph();
        graph.setId(UUID.randomUUID().toString());
        graph.setUserId(userId);
        graph.setWorkspaceId(request.getWorkspaceId());
        graph.setName(request.getName());
        graph.setDescription(request.getDescription());
        graph.setCreatedAt(LocalDateTime.now());
        graph.setUpdatedAt(LocalDateTime.now());
        UserGraph saved = userGraphRepository.save(graph);
        log.info(
            "Created user graph: id={} workspace={} user={}",
            saved.getId(),
            saved.getWorkspaceId(),
            userId
        );
        return toDTO(saved);
    }

    public List<UserGraphDTO> listByWorkspace(String workspaceId) {
        return userGraphRepository
            .findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
            .stream()
            .map(this::toDTO)
            .toList();
    }

    public Optional<UserGraphDTO> getGraph(String graphId) {
        return userGraphRepository.findById(graphId).map(this::toDTO);
    }

    public void deleteGraph(String graphId) {
        userGraphRepository.deleteByIdCascade(graphId);
        log.info("Deleted user graph: {}", graphId);
    }

    public Optional<Law> addLaw(String graphId, String code, String country) {
        return userGraphRepository.attachLaw(graphId, code, country);
    }

    public void removeLaw(String graphId, String code, String country) {
        userGraphRepository.detachLaw(graphId, code, country);
    }

    public UserDocumentDTO addDocument(
        String graphId,
        String userId,
        AddUserDocumentRequest request
    ) {
        UserDocument doc = userDocumentRepository.attachDocumentToGraph(
            graphId,
            request.getDocumentId(),
            userId,
            request.getFileName(),
            request.getMimeType(),
            request.getFileUrl(),
            request.getSummary()
        );
        return toDTO(doc);
    }

    public void removeDocument(String graphId, String documentId) {
        userGraphRepository.detachDocument(graphId, documentId);
    }

    public void linkDocumentToLaw(
        String graphId,
        String documentId,
        String lawCode,
        String country,
        String kind
    ) {
        linkDocumentToLaw(
            graphId,
            documentId,
            lawCode,
            country,
            kind,
            null,
            null,
            null
        );
    }

    public void linkDocumentToLaw(
        String graphId,
        String documentId,
        String lawCode,
        String country,
        String kind,
        String source,
        String extractedBy,
        Double confidence
    ) {
        userDocumentRepository.linkToLawWithProvenance(
            graphId,
            documentId,
            lawCode,
            country,
            kind == null ? "SEMANTIC" : kind,
            source == null
                ? com.jvmd.graphsservice.model.ProvenanceSource.LLM_INFERENCE.name()
                : source,
            extractedBy,
            confidence
        );
    }

    public Optional<GraphContentResponse> getGraphContent(String graphId) {
        return userGraphRepository.findById(graphId).map(graph -> {
            List<GraphContentResponse.GraphNodeDTO> nodes = new ArrayList<>();

            Collection<Map<String, Object>> lawRows = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_LAW]->(l:Law)
                    RETURN DISTINCT l.code AS code, l.title AS title, l.type AS type, l.summary AS summary
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : lawRows) {
                String code = str(r, "code");
                nodes.add(
                    GraphContentResponse.GraphNodeDTO.builder()
                        .id(code)
                        .kind("LAW")
                        .label(str(r, "title"))
                        .code(code)
                        .type(str(r, "type"))
                        .summary(str(r, "summary"))
                        .build()
                );
            }

            Collection<Map<String, Object>> docRows = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_DOCUMENT]->(d:UserDocument)
                    RETURN d.id AS id, d.fileName AS fileName, d.mimeType AS mimeType, d.summary AS summary
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : docRows) {
                nodes.add(
                    GraphContentResponse.GraphNodeDTO.builder()
                        .id(str(r, "id"))
                        .kind("DOCUMENT")
                        .label(str(r, "fileName"))
                        .type(str(r, "mimeType"))
                        .summary(str(r, "summary"))
                        .build()
                );
            }

            List<GraphContentResponse.GraphEdgeDTO> edges = new ArrayList<>();

            Collection<Map<String, Object>> lawEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_LAW]->(a:Law),
                          (g)-[:CONTAINS_LAW]->(b:Law),
                          (a)-[r:REFERENCES|AMENDS|DEFINES|RELATED|SUPERSEDES|IMPLEMENTS]->(b)
                    RETURN DISTINCT a.code AS source, b.code AS target, type(r) AS relationType
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : lawEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType(str(r, "relationType"))
                        .kind("LAW_LAW")
                        .build()
                );
            }

            Collection<Map<String, Object>> docEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_DOCUMENT]->(d:UserDocument)
                    MATCH (d)-[r:RELATES_TO {graphId: $graphId}]->(l:Law)
                    RETURN d.id AS source, l.code AS target, r.kind AS kind
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : docEdges) {
                String kind = str(r, "kind");
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("RELATES_TO")
                        .kind(kind == null ? "DOCUMENT_LAW" : kind)
                        .build()
                );
            }

            Collection<Map<String, Object>> articleRows = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_DOCUMENT]->(d:UserDocument)
                    MATCH (d)-[r:CITES {graphId: $graphId}]->(a:Article)
                    RETURN DISTINCT
                        a.lawCode + ':' + a.number AS id,
                        a.number AS number,
                        a.title AS title,
                        a.lawCode AS lawCode,
                        a.body AS body
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : articleRows) {
                nodes.add(
                    GraphContentResponse.GraphNodeDTO.builder()
                        .id(str(r, "id"))
                        .kind("ARTICLE")
                        .label(
                            "Ст. " +
                                str(r, "number") +
                                (str(r, "title") != null
                                    ? " — " + str(r, "title")
                                    : "")
                        )
                        .code(str(r, "lawCode"))
                        .type("ARTICLE")
                        .summary(str(r, "body"))
                        .build()
                );
            }

            Collection<Map<String, Object>> citesEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_DOCUMENT]->(d:UserDocument)
                    MATCH (d)-[r:CITES {graphId: $graphId}]->(a:Article)
                    RETURN d.id AS source,
                           a.lawCode + ':' + a.number AS target,
                           r.clauseRef AS clauseRef,
                           r.confidence AS confidence
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : citesEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("CITES")
                        .kind("DOCUMENT_ARTICLE")
                        .clauseRef(str(r, "clauseRef"))
                        .confidence(dbl(r, "confidence"))
                        .build()
                );
            }

            Collection<Map<String, Object>> conflictArtEdges = neo4jClient
                .query(
                    """
                    MATCH (a:Article)-[r:CONFLICTS_WITH {graphId: $graphId}]->(b:Article)
                    RETURN a.lawCode + ':' + a.number AS source,
                           b.lawCode + ':' + b.number AS target,
                           r.reason AS reason,
                           r.confidence AS confidence
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : conflictArtEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("CONFLICTS_WITH")
                        .kind("ARTICLE_ARTICLE_CONFLICT")
                        .reason(str(r, "reason"))
                        .confidence(dbl(r, "confidence"))
                        .build()
                );
            }

            Collection<Map<String, Object>> conflictDocEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_DOCUMENT]->(d:UserDocument)
                    MATCH (d)-[r:CONFLICTS_WITH {graphId: $graphId}]->(a:Article)
                    RETURN d.id AS source,
                           a.lawCode + ':' + a.number AS target,
                           r.clauseRef AS clauseRef,
                           r.reason AS reason,
                           r.confidence AS confidence
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : conflictDocEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("CONFLICTS_WITH")
                        .kind("DOCUMENT_ARTICLE_CONFLICT")
                        .clauseRef(str(r, "clauseRef"))
                        .reason(str(r, "reason"))
                        .confidence(dbl(r, "confidence"))
                        .build()
                );
            }

            Collection<Map<String, Object>> voiceRows = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_VOICE]->(v:VoiceEvidence)
                    RETURN v.id AS id, v.label AS label, v.classification AS classification,
                           v.severity AS severity, v.summary AS summary
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : voiceRows) {
                nodes.add(
                    GraphContentResponse.GraphNodeDTO.builder()
                        .id(str(r, "id"))
                        .kind("VOICE")
                        .label(str(r, "label"))
                        .type(str(r, "classification"))
                        .summary(str(r, "summary"))
                        .severity(str(r, "severity"))
                        .build()
                );
            }

            Collection<Map<String, Object>> voiceArticleEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_VOICE]->(v:VoiceEvidence)
                    MATCH (v)-[r:EVIDENCES_VIOLATION]->(a:Article)
                    RETURN v.id AS source,
                           a.lawCode + ':' + a.number AS target,
                           r.reason AS reason
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : voiceArticleEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("EVIDENCES_VIOLATION")
                        .kind("VOICE_ARTICLE")
                        .reason(str(r, "reason"))
                        .build()
                );
            }

            Collection<Map<String, Object>> situationRows = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_SITUATION]->(s:Situation)
                    RETURN s.id AS id, s.title AS title, s.plainText AS summary,
                           s.generatedDocId AS generatedDocId, s.generatedDocTitle AS generatedDocTitle
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : situationRows) {
                String genDocId = str(r, "generatedDocId");
                String genDocTitle = str(r, "generatedDocTitle");
                nodes.add(
                    GraphContentResponse.GraphNodeDTO.builder()
                        .id(str(r, "id"))
                        .kind("SITUATION")
                        .label(str(r, "title"))
                        .type("SITUATION")
                        .summary(str(r, "summary"))
                        .generatedDocId(genDocId)
                        .generatedDocTitle(genDocTitle)
                        .build()
                );
                if (genDocId != null && !genDocId.isBlank()) {
                    nodes.add(
                        GraphContentResponse.GraphNodeDTO.builder()
                            .id(genDocId)
                            .kind("DOCUMENT")
                            .label(
                                genDocTitle != null && !genDocTitle.isBlank()
                                    ? genDocTitle
                                    : "Сгенерированный документ"
                            )
                            .type("GENERATED")
                            .generatedDocId(genDocId)
                            .build()
                    );
                    edges.add(
                        GraphContentResponse.GraphEdgeDTO.builder()
                            .source(str(r, "id"))
                            .target(genDocId)
                            .relationType("HAS_GENERATED_DOC")
                            .kind("SITUATION_GENERATED_DOC")
                            .build()
                    );
                }
            }

            Collection<Map<String, Object>> situationDocEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_SITUATION]->(s:Situation)
                    MATCH (s)-[:INVOLVES_DOCUMENT]->(d:UserDocument)
                    RETURN s.id AS source, d.id AS target
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : situationDocEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("INVOLVES_DOCUMENT")
                        .kind("SITUATION_DOCUMENT")
                        .build()
                );
            }

            Collection<Map<String, Object>> situationArtEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_SITUATION]->(s:Situation)
                    MATCH (s)-[:INVOLVES_ARTICLE]->(a:Article)
                    RETURN s.id AS source,
                           a.lawCode + ':' + a.number AS target
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : situationArtEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("INVOLVES_ARTICLE")
                        .kind("SITUATION_ARTICLE")
                        .build()
                );
            }

            Collection<Map<String, Object>> situationLawEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_SITUATION]->(s:Situation)
                    MATCH (s)-[r:INVOLVES_LAW]->(l:Law)
                    WHERE l.code IS NOT NULL
                    RETURN DISTINCT s.id AS source, l.code AS target, head(collect(r.kind)) AS kind
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : situationLawEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("INVOLVES_LAW")
                        .kind("SITUATION_LAW")
                        .build()
                );
            }

            Collection<Map<String, Object>> voiceLawEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_VOICE]->(v:VoiceEvidence)
                    MATCH (v)-[r:EVIDENCES_VIOLATION]->(l:Law)
                    WHERE l.code IS NOT NULL
                    RETURN DISTINCT v.id AS source, l.code AS target, head(collect(r.reason)) AS reason
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : voiceLawEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("EVIDENCES_VIOLATION")
                        .kind("VOICE_LAW")
                        .reason(str(r, "reason"))
                        .build()
                );
            }

            Collection<Map<String, Object>> situationVoiceEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_SITUATION]->(s:Situation)
                    MATCH (s)-[:INVOLVES_VOICE]->(v:VoiceEvidence)
                    RETURN s.id AS source, v.id AS target
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : situationVoiceEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("INVOLVES_VOICE")
                        .kind("SITUATION_VOICE")
                        .build()
                );
            }

            Collection<Map<String, Object>> commentRows = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_COMMENT]->(c:Comment)
                    RETURN c.id AS id,
                           c.title AS title,
                           c.preview AS preview,
                           c.kind AS kind
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : commentRows) {
                String title = str(r, "title");
                nodes.add(
                    GraphContentResponse.GraphNodeDTO.builder()
                        .id(str(r, "id"))
                        .kind("COMMENT")
                        .label(
                            title == null || title.isBlank()
                                ? "Анализ ИИ"
                                : title
                        )
                        .type(str(r, "kind"))
                        .summary(str(r, "preview"))
                        .build()
                );
            }

            Collection<Map<String, Object>> commentArticleEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_COMMENT]->(c:Comment)
                    MATCH (c)-[r:REFERENCES_ARTICLE]->(a:Article)
                    RETURN c.id AS source,
                           a.lawCode + ':' + a.number AS target,
                           r.reason AS reason
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : commentArticleEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("REFERENCES_ARTICLE")
                        .kind("COMMENT_ARTICLE")
                        .reason(str(r, "reason"))
                        .build()
                );
            }

            Collection<Map<String, Object>> commentLawEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_COMMENT]->(c:Comment)
                    MATCH (c)-[r:REFERENCES_LAW]->(l:Law)
                    WHERE l.code IS NOT NULL
                    RETURN DISTINCT c.id AS source, l.code AS target
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : commentLawEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("REFERENCES_LAW")
                        .kind("COMMENT_LAW")
                        .build()
                );
            }

            Collection<Map<String, Object>> commentSubjectEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_COMMENT]->(c:Comment)
                    MATCH (c)-[r:ANALYZES]->(s)
                    WHERE s.id IS NOT NULL
                    RETURN c.id AS source, s.id AS target, head(labels(s)) AS subjectKind
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : commentSubjectEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("ANALYZES")
                        .kind(
                            "COMMENT_" +
                                (str(r, "subjectKind") == null
                                    ? "SUBJECT"
                                    : str(r, "subjectKind").toUpperCase())
                        )
                        .build()
                );
            }

            Collection<Map<String, Object>> allArticleRows = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})
                    OPTIONAL MATCH (g)-[:CONTAINS_DOCUMENT]->(d:UserDocument)-[:CITES {graphId: $graphId}]->(a1:Article)
                    OPTIONAL MATCH (g)-[:CONTAINS_VOICE]->(v:VoiceEvidence)-[:EVIDENCES_VIOLATION]->(a2:Article)
                    OPTIONAL MATCH (g)-[:CONTAINS_SITUATION]->(s:Situation)-[:INVOLVES_ARTICLE]->(a3:Article)
                    OPTIONAL MATCH (g)-[:CONTAINS_COMMENT]->(c:Comment)-[:REFERENCES_ARTICLE]->(a4:Article)
                    WITH collect(DISTINCT a1) + collect(DISTINCT a2) + collect(DISTINCT a3) + collect(DISTINCT a4) AS arts
                    UNWIND arts AS a
                    WITH DISTINCT a WHERE a IS NOT NULL
                    RETURN a.lawCode AS lawCode, a.country AS country, a.number AS number,
                           a.title AS title, a.body AS body
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            java.util.Set<String> articleIds = new java.util.HashSet<>();
            for (var node : nodes) {
                if (
                    "ARTICLE".equals(node.getKind()) && node.getId() != null
                ) articleIds.add(node.getId());
            }
            for (Map<String, Object> r : allArticleRows) {
                String id = str(r, "lawCode") + ":" + str(r, "number");
                if (!articleIds.add(id)) continue;
                String title = str(r, "title");
                nodes.add(
                    GraphContentResponse.GraphNodeDTO.builder()
                        .id(id)
                        .kind("ARTICLE")
                        .label(
                            "Ст. " +
                                str(r, "number") +
                                (title != null && !title.isBlank()
                                    ? " — " + title
                                    : "")
                        )
                        .code(str(r, "lawCode"))
                        .type("ARTICLE")
                        .summary(str(r, "body"))
                        .build()
                );
            }

            Collection<Map<String, Object>> hasArticleEdges = neo4jClient
                .query(
                    """
                    MATCH (g:UserGraph {id: $graphId})
                    OPTIONAL MATCH (g)-[:CONTAINS_DOCUMENT]->(d:UserDocument)-[:CITES {graphId: $graphId}]->(a1:Article)
                    OPTIONAL MATCH (g)-[:CONTAINS_VOICE]->(v:VoiceEvidence)-[:EVIDENCES_VIOLATION]->(a2:Article)
                    OPTIONAL MATCH (g)-[:CONTAINS_SITUATION]->(s:Situation)-[:INVOLVES_ARTICLE]->(a3:Article)
                    OPTIONAL MATCH (g)-[:CONTAINS_COMMENT]->(c:Comment)-[:REFERENCES_ARTICLE]->(a4:Article)
                    WITH collect(DISTINCT a1) + collect(DISTINCT a2) + collect(DISTINCT a3) + collect(DISTINCT a4) AS arts
                    UNWIND arts AS a
                    WITH DISTINCT a WHERE a IS NOT NULL
                    MATCH (l:Law {code: a.lawCode, country: a.country})
                    RETURN DISTINCT a.lawCode + ':' + a.number AS source, l.code AS target
                    """
                )
                .bind(graphId)
                .to("graphId")
                .fetch()
                .all();
            for (Map<String, Object> r : hasArticleEdges) {
                edges.add(
                    GraphContentResponse.GraphEdgeDTO.builder()
                        .source(str(r, "source"))
                        .target(str(r, "target"))
                        .relationType("HAS_ARTICLE")
                        .kind("ARTICLE_LAW")
                        .build()
                );
            }

            return GraphContentResponse.builder()
                .graph(toDTO(graph))
                .nodes(nodes)
                .edges(edges)
                .build();
        });
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : v.toString();
    }

    private static Double dbl(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private UserGraphDTO toDTO(UserGraph g) {
        return UserGraphDTO.builder()
            .id(g.getId())
            .workspaceId(g.getWorkspaceId())
            .userId(g.getUserId())
            .name(g.getName())
            .description(g.getDescription())
            .createdAt(g.getCreatedAt())
            .updatedAt(g.getUpdatedAt())
            .lawCount(userGraphRepository.countLaws(g.getId()))
            .documentCount(userGraphRepository.countDocuments(g.getId()))
            .situationCount(userGraphRepository.countSituations(g.getId()))
            .build();
    }

    private UserDocumentDTO toDTO(UserDocument d) {
        return UserDocumentDTO.builder()
            .id(d.getId())
            .userId(d.getUserId())
            .fileName(d.getFileName())
            .mimeType(d.getMimeType())
            .fileUrl(d.getFileUrl())
            .summary(d.getSummary())
            .createdAt(d.getCreatedAt())
            .build();
    }
}
