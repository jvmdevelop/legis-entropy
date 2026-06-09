package com.jvmd.documentservice.evidence;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "voice_messages")
@Data
@NoArgsConstructor
public class VoiceMessage {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "graph_id", length = 64)
    private String graphId;

    @Column(name = "situation_id", length = 64)
    private String situationId;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "content_type", length = 128)
    private String contentType;

    @Column(name = "object_name", length = 1024)
    private String objectName;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "language", length = 16)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32)
    private VoiceMessageStatus status = VoiceMessageStatus.UPLOADED;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "transcript_json", columnDefinition = "TEXT")
    private String transcriptJson;

    @Column(name = "analysis_json", columnDefinition = "TEXT")
    private String analysisJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
