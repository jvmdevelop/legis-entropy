package com.jvmd.graphsservice.dto;

import com.jvmd.graphsservice.model.Comment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentDTO {
    private String id;
    private String graphId;
    private String userId;
    private String title;
    private String body;
    private String preview;
    private String kind;
    private String subjectKind;
    private String subjectId;
    private LocalDateTime createdAt;

    public static CommentDTO from(Comment c) {
        if (c == null) return null;
        return CommentDTO.builder()
                .id(c.getId())
                .graphId(c.getGraphId())
                .userId(c.getUserId())
                .title(c.getTitle())
                .body(c.getBody())
                .preview(c.getPreview())
                .kind(c.getKind())
                .subjectKind(c.getSubjectKind())
                .subjectId(c.getSubjectId())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
