package com.jvmd.workspaceservice.controller;

import com.jvmd.workspaceservice.service.DocumentCollaborationService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/collaboration")
public class CollaborationController {

    private final DocumentCollaborationService collaborationService;

    @PostMapping("/share")
    public ResponseEntity<Map<String, Object>> shareDocument(
        @RequestBody ShareRequest request,
        @RequestHeader("X-User-Id") String userId
    ) {
        log.info(
            "Sharing document {} with user {}",
            request.getDocumentId(),
            request.getRecipientId()
        );

        try {
            DocumentCollaborationService.DocumentShare share =
                collaborationService.shareDocument(
                    request.getDocumentId(),
                    userId,
                    request.getRecipientId(),
                    DocumentCollaborationService.SharePermission.valueOf(
                        request.getPermission()
                    )
                );

            return ResponseEntity.status(HttpStatus.CREATED).body(
                Map.of(
                    "id",
                    share.getId(),
                    "document_id",
                    share.getDocumentId(),
                    "recipient_id",
                    share.getRecipientId(),
                    "permission",
                    share.getPermission().toString(),
                    "shared_at",
                    share.getSharedAt().toString()
                )
            );
        } catch (Exception e) {
            log.error("Error sharing document", e);
            return ResponseEntity.badRequest().body(
                Map.of("error", "Failed to share document")
            );
        }
    }

    @GetMapping("/shares/{documentId}")
    public ResponseEntity<List<Map<String, Object>>> getDocumentShares(
        @PathVariable String documentId
    ) {
        List<DocumentCollaborationService.DocumentShare> shares =
            collaborationService.getDocumentShares(documentId);

        List<Map<String, Object>> response = shares
            .stream()
            .map(s -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("id", s.getId());
                map.put("recipient_id", s.getRecipientId());
                map.put("permission", s.getPermission().toString());
                map.put("shared_at", s.getSharedAt().toString());
                return map;
            })
            .toList();

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/share/{shareId}")
    public ResponseEntity<Void> revokeShare(@PathVariable String shareId) {
        collaborationService.revokeShare(shareId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/comments")
    public ResponseEntity<Map<String, Object>> addComment(
        @RequestBody CommentRequest request,
        @RequestHeader("X-User-Id") String userId
    ) {
        log.info("Adding comment to document {}", request.getDocumentId());

        DocumentCollaborationService.DocumentComment comment =
            collaborationService.addComment(
                request.getDocumentId(),
                userId,
                request.getContent(),
                request.getLineNumber()
            );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            Map.of(
                "id",
                comment.getId(),
                "document_id",
                comment.getDocumentId(),
                "user_id",
                comment.getUserId(),
                "content",
                comment.getContent(),
                "line_number",
                comment.getLineNumber(),
                "created_at",
                comment.getCreatedAt().toString(),
                "resolved",
                comment.isResolved()
            )
        );
    }

    @GetMapping("/comments/{documentId}")
    public ResponseEntity<List<Map<String, Object>>> getDocumentComments(
        @PathVariable String documentId
    ) {
        List<DocumentCollaborationService.DocumentComment> comments =
            collaborationService.getDocumentComments(documentId);

        List<Map<String, Object>> response = comments
            .stream()
            .map(c -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("id", c.getId());
                map.put("user_id", c.getUserId());
                map.put("content", c.getContent());
                map.put("line_number", c.getLineNumber());
                map.put("created_at", c.getCreatedAt().toString());
                map.put("updated_at", c.getUpdatedAt().toString());
                map.put("resolved", c.isResolved());
                map.put("replies_count", c.getReplies().size());
                return map;
            })
            .toList();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/comments/{commentId}/resolve")
    public ResponseEntity<Void> resolveComment(@PathVariable String commentId) {
        collaborationService.resolveComment(commentId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/versions/{documentId}")
    public ResponseEntity<List<Map<String, Object>>> getVersionHistory(
        @PathVariable String documentId
    ) {
        List<DocumentCollaborationService.DocumentVersion> versions =
            collaborationService.getDocumentVersionHistory(documentId);

        List<Map<String, Object>> response = versions
            .stream()
            .map(v -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("id", v.getId());
                map.put("version_number", v.getVersionNumber());
                map.put("user_id", v.getUserId());
                map.put("change_description", v.getChangeDescription());
                map.put("created_at", v.getCreatedAt().toString());
                return map;
            })
            .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/versions")
    public ResponseEntity<Map<String, Object>> recordVersion(
        @RequestBody VersionRequest request,
        @RequestHeader("X-User-Id") String userId
    ) {
        DocumentCollaborationService.DocumentVersion version =
            collaborationService.recordVersion(
                request.getDocumentId(),
                userId,
                request.getContent(),
                request.getChangeDescription()
            );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            Map.of(
                "id",
                version.getId(),
                "version_number",
                version.getVersionNumber(),
                "user_id",
                version.getUserId(),
                "change_description",
                version.getChangeDescription(),
                "created_at",
                version.getCreatedAt().toString()
            )
        );
    }

    @PostMapping("/assignments")
    public ResponseEntity<Map<String, Object>> assignDocument(
        @RequestBody AssignmentRequest request,
        @RequestHeader("X-User-Id") String userId
    ) {
        DocumentCollaborationService.DocumentAssignment assignment =
            collaborationService.assignDocument(
                request.getDocumentId(),
                request.getAssigneeId(),
                userId,
                request.getNotes()
            );

        return ResponseEntity.status(HttpStatus.CREATED).body(
            Map.of(
                "id",
                assignment.getId(),
                "document_id",
                assignment.getDocumentId(),
                "assignee_id",
                assignment.getAssigneeId(),
                "status",
                assignment.getStatus(),
                "due_date",
                assignment.getDueDate().toString(),
                "created_at",
                assignment.getCreatedAt().toString()
            )
        );
    }

    @GetMapping("/assignments/{documentId}")
    public ResponseEntity<List<Map<String, Object>>> getAssignments(
        @PathVariable String documentId
    ) {
        List<DocumentCollaborationService.DocumentAssignment> assignments =
            collaborationService.getDocumentAssignments(documentId);

        List<Map<String, Object>> response = assignments
            .stream()
            .map(a -> {
                Map<String, Object> map = new java.util.LinkedHashMap<>();
                map.put("id", a.getId());
                map.put("assignee_id", a.getAssigneeId());
                map.put("status", a.getStatus());
                map.put("notes", a.getNotes());
                map.put("due_date", a.getDueDate().toString());
                map.put("created_at", a.getCreatedAt().toString());
                return map;
            })
            .toList();

        return ResponseEntity.ok(response);
    }

    public static class ShareRequest {

        private String documentId;
        private String recipientId;
        private String permission;

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getRecipientId() {
            return recipientId;
        }

        public void setRecipientId(String recipientId) {
            this.recipientId = recipientId;
        }

        public String getPermission() {
            return permission;
        }

        public void setPermission(String permission) {
            this.permission = permission;
        }
    }

    public static class CommentRequest {

        private String documentId;
        private String content;
        private int lineNumber;

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public void setLineNumber(int lineNumber) {
            this.lineNumber = lineNumber;
        }
    }

    public static class VersionRequest {

        private String documentId;
        private String content;
        private String changeDescription;

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getChangeDescription() {
            return changeDescription;
        }

        public void setChangeDescription(String changeDescription) {
            this.changeDescription = changeDescription;
        }
    }

    public static class AssignmentRequest {

        private String documentId;
        private String assigneeId;
        private String notes;

        public String getDocumentId() {
            return documentId;
        }

        public void setDocumentId(String documentId) {
            this.documentId = documentId;
        }

        public String getAssigneeId() {
            return assigneeId;
        }

        public void setAssigneeId(String assigneeId) {
            this.assigneeId = assigneeId;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
