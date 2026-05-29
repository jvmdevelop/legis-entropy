package com.jvmd.workspaceservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "workspace_law_references",
    indexes = {
        @Index(name = "idx_law_ref_workspace_id", columnList = "workspace_id"),
        @Index(name = "idx_law_ref_law_id", columnList = "kz_law_id"),
    }
)
public class WorkspaceLawReference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false)
    private UUID kzLawId;

    @Column(nullable = false)
    private String lawCode;

    @Column(nullable = false)
    private String lawTitle;

    @Column(nullable = false)
    private String referenceType;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(
        name = "created_at",
        nullable = false,
        columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
    )
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(
        name = "updated_at",
        columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
    )
    private LocalDateTime updatedAt = LocalDateTime.now();
}
