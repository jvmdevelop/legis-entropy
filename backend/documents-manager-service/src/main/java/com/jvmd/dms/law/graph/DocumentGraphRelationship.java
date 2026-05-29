package com.jvmd.dms.law.graph;

import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGraphRelationship {

    public enum RelationType {
        CONTAINS("содержит", "CONTAINS"),
        REFERENCES("ссылается на", "REFERENCES"),
        CITES("цитирует", "CITES"),
        CONFLICTS_WITH("противоречит", "CONFLICTS_WITH"),
        COMPLIES_WITH("соответствует", "COMPLIES_WITH"),
        SUPERSEDES("отменяет/изменяет", "SUPERSEDES"),
        SUPERSEDED_BY("отменено", "SUPERSEDED_BY"),
        AMENDS("изменяет", "AMENDS"),
        RELATED_TO("связано с", "RELATED_TO");

        private final String displayName;
        private final String neo4jLabel;

        RelationType(String displayName, String neo4jLabel) {
            this.displayName = displayName;
            this.neo4jLabel = neo4jLabel;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getNeo4jLabel() {
            return neo4jLabel;
        }
    }

    private UUID id;
    private UUID sourceId;
    private UUID targetId;
    private RelationType relationType;
    private Float confidence;
    private String reason;
    private LocalDateTime detectedAt;

    public DocumentGraphRelationship(
        UUID sourceId,
        UUID targetId,
        RelationType relationType,
        Float confidence
    ) {
        this.id = UUID.randomUUID();
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.relationType = relationType;
        this.confidence = confidence;
        this.detectedAt = LocalDateTime.now();
    }
}
