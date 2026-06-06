package com.jvmd.situationservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("Situation")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Situation {

    @Id
    private String id;

    private String graphId;
    private String userId;
    private String title;
    private String body;
    private String plainText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String generatedDocId;
    private String generatedDocTitle;
}
