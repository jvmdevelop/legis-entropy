package com.jvmd.documentservice.evidence;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_documents")
@Data
@NoArgsConstructor
public class GeneratedDocument {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "template_code", length = 64)
    private String templateCode;

    @Column(name = "graph_id", length = 64)
    private String graphId;

    @Column(name = "situation_id", length = 64)
    private String situationId;

    @Column(name = "title")
    private String title;

    @Column(name = "body_markdown", columnDefinition = "TEXT")
    private String bodyMarkdown;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
