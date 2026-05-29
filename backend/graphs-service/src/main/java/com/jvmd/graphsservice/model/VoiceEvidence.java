package com.jvmd.graphsservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("VoiceEvidence")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoiceEvidence {
    @Id
    private String id;

    private String graphId;
    private String userId;
    private String label;
    private String classification;
    private String severity;
    private String summary;

    private String speakers;
    private LocalDateTime createdAt;
}
