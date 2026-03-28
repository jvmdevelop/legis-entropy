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
) {}
