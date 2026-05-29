package com.jvmd.graphsservice.dto;

import com.jvmd.graphsservice.model.RelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LawGraphResponse {
    private LawDTO law;
    private Map<RelationType, List<LawDTO>> relationships;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RelatedLaw {
        private LawDTO law;
        private RelationType relationType;
        private String description;
    }
}
