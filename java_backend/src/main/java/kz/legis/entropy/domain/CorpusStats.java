package kz.legis.entropy.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record CorpusStats(
        @JsonProperty("total_documents") int totalDocuments,
        @JsonProperty("active_count") long activeCount,
        @JsonProperty("outdated_count") long outdatedCount,
        @JsonProperty("unknown_count") long unknownCount,
        @JsonProperty("with_issues") long withIssues,
        @JsonProperty("total_links") int totalLinks,
        @JsonProperty("avg_ref_count") double avgRefCount,
        @JsonProperty("issue_breakdown") Map<String, Long> issueBreakdown,
        @JsonProperty("severity_breakdown") Map<String, Long> severityBreakdown,
        @JsonProperty("top_referenced") List<GraphNode> topReferenced,
        @JsonProperty("most_problematic") List<GraphNode> mostProblematic
) {}
