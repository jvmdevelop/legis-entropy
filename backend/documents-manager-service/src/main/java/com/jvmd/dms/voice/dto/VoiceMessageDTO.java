package com.jvmd.dms.voice.dto;

import com.jvmd.dms.voice.entity.VoiceMessage;
import com.jvmd.dms.voice.entity.VoiceMessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceMessageDTO {
    private String id;
    private String userId;
    private String graphId;
    private String situationId;
    private String conversationId;
    private String fileName;
    private String contentType;
    private Integer durationMs;
    private String language;
    private VoiceMessageStatus status;
    private String transcript;
    private String transcriptJson;
    private String analysisJson;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static VoiceMessageDTO from(VoiceMessage v) {
        if (v == null) return null;
        return VoiceMessageDTO.builder()
                .id(v.getId())
                .userId(v.getUserId())
                .graphId(v.getGraphId())
                .situationId(v.getSituationId())
                .conversationId(v.getConversationId())
                .fileName(v.getFileName())
                .contentType(v.getContentType())
                .durationMs(v.getDurationMs())
                .language(v.getLanguage())
                .status(v.getStatus())
                .transcript(v.getTranscript())
                .transcriptJson(v.getTranscriptJson())
                .analysisJson(v.getAnalysisJson())
                .errorMessage(v.getErrorMessage())
                .createdAt(v.getCreatedAt())
                .updatedAt(v.getUpdatedAt())
                .build();
    }
}
