package com.jvmd.dms.voice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "voice_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoiceMessage {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "graph_id", length = 64)
    private String graphId;

    @Column(name = "situation_id", length = 64)
    private String situationId;

    @Column(name = "conversation_id", length = 64)
    private String conversationId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "object_name", nullable = false, length = 1024)
    private String objectName;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "language", length = 16)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private VoiceMessageStatus status = VoiceMessageStatus.UPLOADED;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "transcript_json", columnDefinition = "TEXT")
    private String transcriptJson;

    @Column(name = "analysis_json", columnDefinition = "TEXT")
    private String analysisJson;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
