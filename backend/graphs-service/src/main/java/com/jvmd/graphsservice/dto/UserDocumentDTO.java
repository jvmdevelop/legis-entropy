package com.jvmd.graphsservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDocumentDTO {
    private String id;
    private String userId;
    private String fileName;
    private String mimeType;
    private String fileUrl;
    private String summary;
    private LocalDateTime createdAt;
}
