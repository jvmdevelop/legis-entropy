package com.jvmd.graphsservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jvmd.graphsservice.dto.GraphContentResponse.GraphEdgeDTO;
import com.jvmd.graphsservice.dto.GraphContentResponse.GraphNodeDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GraphConsistencyTest {

    private static GraphNodeDTO node(String id, String kind) {
        return GraphNodeDTO.builder().id(id).kind(kind).label(id).build();
    }

    private static GraphEdgeDTO edge(String s, String t, String type) {
        return GraphEdgeDTO.builder().source(s).target(t).relationType(type).build();
    }

    @Test
    void findsArticlesReferencedOnlyByEdges() {
        List<GraphNodeDTO> nodes = List.of(node("doc-1", "DOCUMENT"), node("ТК РК:36", "ARTICLE"));
        List<GraphEdgeDTO> edges = List.of(
            edge("doc-1", "ТК РК:36", "CITES"),
            edge("doc-1", "ТК РК:87", "CONFLICTS_WITH"),
            edge("doc-1", "ГК РК:172", "CONFLICTS_WITH"),
            edge("doc-1", "ТК РК:87", "CONFLICTS_WITH"),
            edge("doc-1", "unknown-node", "RELATES_TO"));
        List<Map<String, Object>> keys = GraphConsistency.missingArticleKeys(nodes, edges);
        assertEquals(List.of("ТК РК:87", "ГК РК:172"), keys.stream().map(k -> k.get("id")).toList());
        assertEquals("ТК РК", keys.get(0).get("lawCode"));
        assertEquals("87", keys.get(0).get("number"));
    }

    @Test
    void completedGraphHasNoDanglingEdgesOrDuplicates() {
        List<GraphNodeDTO> nodes = new ArrayList<>(GraphConsistency.dedupe(List.of(
            node("doc-1", "DOCUMENT"), node("doc-1", "DOCUMENT"), node("ТК РК", "LAW"))));
        nodes.add(GraphConsistency.articleNode("ТК РК:87", "ТК РК", "87", "Виды отпусков", null));
        List<GraphEdgeDTO> edges = List.of(
            edge("doc-1", "ТК РК:87", "CONFLICTS_WITH"),
            edge("doc-1", "ТК РК", "RELATES_TO"),
            edge("doc-1", "gone", "RELATES_TO"));
        assertEquals(3, nodes.size());
        assertEquals("Ст. 87 — Виды отпусков", nodes.get(2).getLabel());
        assertEquals(2, GraphConsistency.withoutDangling(nodes, edges).size());
    }

    @Test
    void editorialNotesAreNotTitles() {
        assertEquals("Ст. 87", GraphConsistency.articleNode("ТК РК:87", "ТК РК", "87",
            "с изменениями, внесенными законами РК от 07", null).getLabel());
        assertEquals("Ст. 172 — Определение срока", GraphConsistency.articleNode("ГК РК:172", "ГК РК", "172",
            "Определение срока", null).getLabel());
    }
}
