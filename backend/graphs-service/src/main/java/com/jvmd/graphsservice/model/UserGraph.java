package com.jvmd.graphsservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("UserGraph")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGraph {
    @Id
    private String id;

    private String workspaceId;
    private String userId;
    private String name;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
