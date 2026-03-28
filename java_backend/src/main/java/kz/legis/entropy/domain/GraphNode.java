package kz.legis.entropy.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GraphNode(
        String id,
        String title,
        String url,
        String status,
        @JsonProperty("ref_count") int refCount,
        @JsonProperty("issue_count") int issueCount,
        @JsonProperty("article_count") int articleCount,
        @JsonProperty("is_amendment") boolean isAmendment
) {}
