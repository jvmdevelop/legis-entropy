package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.dto.GraphContentResponse.GraphEdgeDTO;
import com.jvmd.graphsservice.dto.GraphContentResponse.GraphNodeDTO;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GraphConsistency {

    private static final Pattern ARTICLE_ID = Pattern.compile("^(.+?):(\\d+(?:[-.]\\d+)*)$");
    private static final Pattern EDITORIAL_NOTE = Pattern.compile(
        "(?iu)^\\s*(с\\s+изменени|в\\s+редакции|исключен|утратил|см\\.)");

    private GraphConsistency() {}

    static List<GraphNodeDTO> dedupe(List<GraphNodeDTO> nodes) {
        Map<String, GraphNodeDTO> byId = new LinkedHashMap<>();
        for (GraphNodeDTO n : nodes) {
            if (n != null && n.getId() != null) byId.putIfAbsent(n.getId(), n);
        }
        return new ArrayList<>(byId.values());
    }

    static List<Map<String, Object>> missingArticleKeys(List<GraphNodeDTO> nodes, List<GraphEdgeDTO> edges) {
        Set<String> ids = ids(nodes);
        Set<String> missing = new LinkedHashSet<>();
        for (GraphEdgeDTO e : edges) {
            for (String id : new String[] {e.getSource(), e.getTarget()}) {
                if (id != null && !ids.contains(id) && ARTICLE_ID.matcher(id).matches()) missing.add(id);
            }
        }
        List<Map<String, Object>> keys = new ArrayList<>();
        for (String id : missing) {
            Matcher m = ARTICLE_ID.matcher(id);
            if (m.matches()) {
                Map<String, Object> k = new HashMap<>();
                k.put("id", id);
                k.put("lawCode", m.group(1));
                k.put("number", m.group(2));
                keys.add(k);
            }
        }
        return keys;
    }

    static GraphNodeDTO articleNode(String id, String lawCode, String number, String title, String body) {
        return GraphNodeDTO.builder()
            .id(id)
            .kind("ARTICLE")
            .label("Ст. " + number + (isTitle(title) ? " — " + title : ""))
            .code(lawCode)
            .type("ARTICLE")
            .summary(body)
            .build();
    }

    static boolean isTitle(String title) {
        return title != null && !title.isBlank() && !EDITORIAL_NOTE.matcher(title).find();
    }

    static List<GraphEdgeDTO> withoutDangling(List<GraphNodeDTO> nodes, List<GraphEdgeDTO> edges) {
        Set<String> ids = ids(nodes);
        List<GraphEdgeDTO> kept = new ArrayList<>();
        for (GraphEdgeDTO e : edges) {
            if (e.getSource() != null && e.getTarget() != null
                    && ids.contains(e.getSource()) && ids.contains(e.getTarget())) {
                kept.add(e);
            }
        }
        return kept;
    }

    private static Set<String> ids(List<GraphNodeDTO> nodes) {
        Set<String> ids = new LinkedHashSet<>();
        for (GraphNodeDTO n : nodes) ids.add(n.getId());
        return ids;
    }
}
