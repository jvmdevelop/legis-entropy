package com.jvmd.documentservice.model;

import com.jvmd.documentservice.service.DocumentProcessingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDocument {

    @Id
    @Column(name = "id", nullable = false)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "object_name", nullable = false)
    private String objectName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DocumentProcessingStatus status = DocumentProcessingStatus.UPLOADED;

    @Column(name = "chunk_count")
    private Integer chunkCount = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "plain_text", columnDefinition = "TEXT")
    private String plainText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public void markIndexing() {
        this.status = DocumentProcessingStatus.INDEXING;
        this.errorMessage = null;
    }

    public void markReady(int chunkCount) {
        this.status = DocumentProcessingStatus.READY;
        this.chunkCount = chunkCount;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = DocumentProcessingStatus.ERROR;
        this.errorMessage = errorMessage;
    }

    public void markDeleted() {
        this.status = DocumentProcessingStatus.DELETED;
        this.errorMessage = null;
    }
}
