package com.jvmd.graphsservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("Comment")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Comment {
    @Id
    private String id;

    private String graphId;
    private String userId;
    private String title;

    private String body;

    private String preview;

    private String kind;

    private String subjectKind;

    private String subjectId;
    private LocalDateTime createdAt;
}
