package com.jvmd.graphsservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("Subscription")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    @Id
    private String id;

    private String userId;
    private String lawCode;
    private String country;
    private String articleNumber;

    private String lastSnapshotHash;
    private LocalDateTime lastCheckedAt;
    private LocalDateTime createdAt;
}
