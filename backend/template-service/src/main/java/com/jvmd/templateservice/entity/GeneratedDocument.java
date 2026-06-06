package com.jvmd.templateservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "generated_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedDocument {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "template_id", length = 64)
    private String templateId;

    @Column(name = "template_code", length = 64)
    private String templateCode;

    @Column(name = "graph_id", length = 64)
    private String graphId;

    @Column(name = "situation_id", length = 64)
    private String situationId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "body_markdown", nullable = false, columnDefinition = "TEXT")
    private String bodyMarkdown;

    @Column(name = "object_name", length = 1024)
    private String objectName;

    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
