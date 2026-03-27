package kz.legis.entropy.domain;

import java.util.List;

public record GraphData(
        List<GraphNode> nodes,
        List<GraphLink> links,
        List<Issue> issues
) {
    public record GraphLink(String source, String target) {}
}
