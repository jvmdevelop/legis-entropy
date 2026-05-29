package com.jvmd.graphsservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Node("ChangeEvent")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeEvent {
    @Id
    private String id;

    private String subscriptionId;
    private String userId;
    private String lawCode;
    private String country;
    private String articleNumber;

    private String oldHash;
    private String newHash;
    private LocalDate oldAmendmentDate;
    private LocalDate newAmendmentDate;
    private LocalDateTime detectedAt;

    private boolean acknowledged;
}
