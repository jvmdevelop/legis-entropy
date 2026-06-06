package com.jvmd.documentservice.service;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentStorageService {
    String store(String documentId, String userId, MultipartFile file);
    StoredObject load(String objectName, String fileName, String contentType);
    void delete(String objectName);
}
