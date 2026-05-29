package com.jvmd.graphsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GraphContentResponse {
    private UserGraphDTO graph;
    private List<GraphNodeDTO> nodes;
    private List<GraphEdgeDTO> edges;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GraphNodeDTO {
        private String id;
        private String kind;
        private String label;
        private String code;
        private String type;
        private String summary;

        private String severity;

        private String generatedDocId;
        private String generatedDocTitle;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GraphEdgeDTO {
        private String source;
        private String target;
        private String relationType;
        private String kind;
        private String reason;
        private Double confidence;
        private String clauseRef;
    }
}
