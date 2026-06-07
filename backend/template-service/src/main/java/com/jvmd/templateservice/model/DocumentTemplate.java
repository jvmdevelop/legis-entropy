package com.jvmd.templateservice.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTemplate {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "kind", nullable = false, length = 64)
    private String kind;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "placeholders_json", columnDefinition = "TEXT")
    private String placeholdersJson;

    @Column(name = "suggested_law_codes", columnDefinition = "TEXT")
    private String suggestedLawCodes;

    @Column(name = "is_system", nullable = false)
    private boolean systemTemplate;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "source_format", nullable = false, length = 32)
    private String sourceFormat = "MANUAL";

    @Column(name = "source_object_name", length = 1024)
    private String sourceObjectName;

    @Column(name = "slots_json", columnDefinition = "TEXT")
    private String slotsJson;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
