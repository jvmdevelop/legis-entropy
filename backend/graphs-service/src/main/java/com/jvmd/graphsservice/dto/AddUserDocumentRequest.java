package com.jvmd.graphsservice.dto;

import lombok.Data;

@Data
public class AddUserDocumentRequest {
    private String documentId;
    private String fileName;
    private String mimeType;
    private String fileUrl;
    private String summary;
}
