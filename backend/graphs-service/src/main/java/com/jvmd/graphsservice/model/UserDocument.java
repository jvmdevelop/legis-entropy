package com.jvmd.graphsservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

import java.time.LocalDateTime;

@Node("UserDocument")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {
    @Id
    private String id;

    private String userId;
    private String fileName;
    private String mimeType;
    private String fileUrl;
    private String summary;
    private LocalDateTime createdAt;
}
