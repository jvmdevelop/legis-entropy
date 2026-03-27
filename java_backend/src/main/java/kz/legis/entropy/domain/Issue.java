package kz.legis.entropy.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record Issue(
        String kind,
        String severity,
        @JsonProperty("document_ids") List<String> documentIds,
        String explanation
) {}
