package kz.legis.entropy.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CompareResult(
        @JsonProperty("doc_a") GraphNode docA,
        @JsonProperty("doc_b") GraphNode docB,
        float similarity,
        String assessment,
        String explanation,
        @JsonProperty("shared_issues") long sharedIssues,
        @JsonProperty("llm_review") String llmReview,
        @JsonProperty("llm_ready") boolean llmReady
) {
    public static String assessmentFromScore(float score) {
        if (score >= 0.93f) return "duplicate";
        if (score >= 0.82f) return "highly_related";
        if (score >= 0.55f) return "related";
        return "independent";
    }
}
