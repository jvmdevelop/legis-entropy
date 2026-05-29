package com.jvmd.dms.controller;

import com.jvmd.dms.dto.DocumentStatusResponse;
import com.jvmd.dms.dto.RetrievalChunkResponse;
import com.jvmd.dms.service.UserDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user-documents")
@RequiredArgsConstructor
@Slf4j
public class UserDocumentController {

    private final UserDocumentService userDocumentService;

    @GetMapping
    public ResponseEntity<Page<DocumentStatusResponse>> list(
            @RequestHeader("X-User-Id") String userId,
            Pageable pageable) {
        return ResponseEntity.ok(userDocumentService.listUserDocuments(userId, pageable));
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader("X-User-Id") String userId) {
        try {
            String documentId = userDocumentService.uploadUserDocument(file, userId);
            return ResponseEntity.ok(Map.of("documentId", documentId));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid document upload: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error uploading document: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Could not upload document."));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<List<RetrievalChunkResponse>> search(
            @RequestParam String query,
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(required = false) String documentId) {
        try {
            List<RetrievalChunkResponse> results = userDocumentService.searchInUserDocuments(query, userId, documentId)
                    .stream()
                    .map(RetrievalChunkResponse::from)
                    .toList();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error searching user documents: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{documentId}/status")
    public ResponseEntity<DocumentStatusResponse> status(
            @PathVariable String documentId,
            @RequestHeader("X-User-Id") String userId) {
        try {
            return ResponseEntity.ok(userDocumentService.getStatus(documentId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{documentId}/text")
    public ResponseEntity<Map<String, String>> text(
            @PathVariable String documentId,
            @RequestHeader("X-User-Id") String userId) {
        try {
            String plainText = userDocumentService.getPlainText(documentId, userId);
            return ResponseEntity.ok(Map.of("text", plainText == null ? "" : plainText));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(
            @PathVariable String documentId,
            @RequestHeader("X-User-Id") String userId) {
        try {
            userDocumentService.deleteDocument(documentId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{documentId}/reindex")
    public ResponseEntity<DocumentStatusResponse> reindex(
            @PathVariable String documentId,
            @RequestHeader("X-User-Id") String userId) {
        try {
            userDocumentService.reindexDocument(documentId, userId);
            return ResponseEntity.ok(userDocumentService.getStatus(documentId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
