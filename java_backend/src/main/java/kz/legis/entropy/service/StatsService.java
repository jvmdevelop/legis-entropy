package kz.legis.entropy.service;

import kz.legis.entropy.domain.CorpusStats;
import kz.legis.entropy.domain.GraphData;
import kz.legis.entropy.domain.GraphNode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class StatsService {

    public CorpusStats compute(GraphData graph) {
        var nodes = graph.nodes();
        var issues = graph.issues();

        long active   = nodes.stream().filter(n -> "active".equals(n.status())).count();
        long outdated = nodes.stream().filter(n -> "outdated".equals(n.status())).count();
        long unknown  = nodes.stream().filter(n -> "unknown".equals(n.status())).count();
        long withIssues = nodes.stream().filter(n -> n.issueCount() > 0).count();

        double avgRef = nodes.stream()
                .mapToInt(GraphNode::refCount)
                .average()
                .orElse(0);

        Map<String, Long> issueBreakdown = issues.stream()
                .collect(Collectors.groupingBy(i -> i.kind(), Collectors.counting()));

        Map<String, Long> severityBreakdown = issues.stream()
                .collect(Collectors.groupingBy(i -> i.severity(), Collectors.counting()));

        var topReferenced = nodes.stream()
                .sorted(Comparator.comparingInt(GraphNode::refCount).reversed())
                .limit(5)
                .toList();

        var mostProblematic = nodes.stream()
                .filter(n -> n.issueCount() > 0)
                .sorted(Comparator.comparingInt(GraphNode::issueCount).reversed())
                .limit(5)
                .toList();

        return new CorpusStats(
                nodes.size(), active, outdated, unknown, withIssues,
                graph.links().size(), avgRef,
                issueBreakdown, severityBreakdown,
                topReferenced, mostProblematic
        );
    }
}
