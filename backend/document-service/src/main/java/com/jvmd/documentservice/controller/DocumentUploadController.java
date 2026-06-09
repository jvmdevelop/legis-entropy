package com.jvmd.documentservice.controller;

import com.jvmd.documentservice.service.DocumentUploadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/documents/upload")
public class DocumentUploadController {

    private final DocumentUploadService documentUploadService;

    @PostMapping("/workspace/{workspaceId}")
    public ResponseEntity<DocumentUploadService.DocumentUploadResult> uploadDocument(
            @PathVariable UUID workspaceId,
            @RequestParam UUID userId,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("Document upload request: {} to workspace {}", file.getOriginalFilename(), workspaceId);
        try {
            return ResponseEntity.ok(documentUploadService.processDocument(file, workspaceId, userId));
        } catch (IOException e) {
            log.error("Error uploading document", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/batch")
    public ResponseEntity<String> uploadBatch(
            @RequestParam UUID workspaceId,
            @RequestParam UUID userId,
            @RequestParam("files") MultipartFile[] files
    ) {
        log.info("Batch upload: {} files to workspace {}", files.length, workspaceId);
        int successful = 0, failed = 0;
        for (MultipartFile file : files) {
            try {
                documentUploadService.processDocument(file, workspaceId, userId);
                successful++;
            } catch (IOException e) {
                log.warn("Failed to process file: {}", file.getOriginalFilename(), e);
                failed++;
            }
        }
        return ResponseEntity.ok(String.format("""
                {
                  "total": %d,
                  "successful": %d,
                  "failed": %d,
                  "message": "Batch upload completed"
                }
                """, files.length, successful, failed));
    }
}
