package com.jvmd.workspaceservice.service;

import java.time.LocalDateTime;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class DocumentCollaborationService {

    private final Map<String, DocumentShare> documentShares = new HashMap<>();
    private final Map<String, DocumentComment> documentComments =
        new HashMap<>();
    private final Map<String, DocumentVersion> documentVersions =
        new HashMap<>();
    private final Map<String, DocumentAssignment> documentAssignments =
        new HashMap<>();

    public DocumentShare shareDocument(
        String documentId,
        String ownerId,
        String recipientId,
        SharePermission permission
    ) {
        log.info(
            "Sharing document {} with user {} (permission: {})",
            documentId,
            recipientId,
            permission
        );

        String shareId = UUID.randomUUID().toString();
        DocumentShare share = DocumentShare.builder()
            .id(shareId)
            .documentId(documentId)
            .ownerId(ownerId)
            .recipientId(recipientId)
            .permission(permission)
            .sharedAt(LocalDateTime.now())
            .build();

        documentShares.put(shareId, share);
        return share;
    }

    public List<DocumentShare> getDocumentShares(String documentId) {
        return documentShares
            .values()
            .stream()
            .filter(s -> s.getDocumentId().equals(documentId))
            .toList();
    }

    public void revokeShare(String shareId) {
        log.info("Revoking share: {}", shareId);
        documentShares.remove(shareId);
    }

    public DocumentComment addComment(
        String documentId,
        String userId,
        String content,
        int lineNumber
    ) {
        log.info(
            "Adding comment to document {} by user {}",
            documentId,
            userId
        );

        String commentId = UUID.randomUUID().toString();
        DocumentComment comment = DocumentComment.builder()
            .id(commentId)
            .documentId(documentId)
            .userId(userId)
            .content(content)
            .lineNumber(lineNumber)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .resolved(false)
            .build();

        documentComments.put(commentId, comment);
        return comment;
    }

    public List<DocumentComment> getDocumentComments(String documentId) {
        return documentComments
            .values()
            .stream()
            .filter(c -> c.getDocumentId().equals(documentId))
            .toList();
    }

    public CommentReply replyToComment(
        String commentId,
        String userId,
        String content
    ) {
        DocumentComment comment = documentComments.get(commentId);
        if (comment == null) {
            throw new RuntimeException("Comment not found");
        }

        String replyId = UUID.randomUUID().toString();
        CommentReply reply = CommentReply.builder()
            .id(replyId)
            .commentId(commentId)
            .userId(userId)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();

        comment.addReply(reply);
        comment.setUpdatedAt(LocalDateTime.now());
        return reply;
    }

    public void resolveComment(String commentId) {
        DocumentComment comment = documentComments.get(commentId);
        if (comment != null) {
            comment.setResolved(true);
            comment.setUpdatedAt(LocalDateTime.now());
        }
    }

    public DocumentVersion recordVersion(
        String documentId,
        String userId,
        String content,
        String changeDescription
    ) {
        log.info("Recording new version for document {}", documentId);

        String versionId = UUID.randomUUID().toString();
        int versionNumber =
            documentVersions
                .values()
                .stream()
                .filter(v -> v.getDocumentId().equals(documentId))
                .mapToInt(DocumentVersion::getVersionNumber)
                .max()
                .orElse(0) + 1;

        DocumentVersion version = DocumentVersion.builder()
            .id(versionId)
            .documentId(documentId)
            .versionNumber(versionNumber)
            .userId(userId)
            .content(content)
            .changeDescription(changeDescription)
            .createdAt(LocalDateTime.now())
            .build();

        documentVersions.put(versionId, version);
        return version;
    }

    public List<DocumentVersion> getDocumentVersionHistory(String documentId) {
        return documentVersions
            .values()
            .stream()
            .filter(v -> v.getDocumentId().equals(documentId))
            .sorted(
                Comparator.comparingInt(
                    DocumentVersion::getVersionNumber
                ).reversed()
            )
            .toList();
    }

    public DocumentAssignment assignDocument(
        String documentId,
        String assigneeId,
        String assignerId,
        String notes
    ) {
        log.info("Assigning document {} to user {}", documentId, assigneeId);

        String assignmentId = UUID.randomUUID().toString();
        DocumentAssignment assignment = DocumentAssignment.builder()
            .id(assignmentId)
            .documentId(documentId)
            .assigneeId(assigneeId)
            .assignerId(assignerId)
            .notes(notes)
            .status("ASSIGNED")
            .createdAt(LocalDateTime.now())
            .dueDate(LocalDateTime.now().plusDays(7))
            .build();

        documentAssignments.put(assignmentId, assignment);
        return assignment;
    }

    public List<DocumentAssignment> getDocumentAssignments(String documentId) {
        return documentAssignments
            .values()
            .stream()
            .filter(a -> a.getDocumentId().equals(documentId))
            .toList();
    }

    public void updateAssignmentStatus(String assignmentId, String status) {
        DocumentAssignment assignment = documentAssignments.get(assignmentId);
        if (assignment != null) {
            assignment.setStatus(status);
            assignment.setUpdatedAt(LocalDateTime.now());
        }
    }

    public enum SharePermission {
        VIEW,
        COMMENT,
        EDIT,
        MANAGE,
    }

    public static class DocumentShare {

        private String id;
        private String documentId;
        private String ownerId;
        private String recipientId;
        private SharePermission permission;
        private LocalDateTime sharedAt;

        public DocumentShare() {}

        public DocumentShare(Builder builder) {
            this.id = builder.id;
            this.documentId = builder.documentId;
            this.ownerId = builder.ownerId;
            this.recipientId = builder.recipientId;
            this.permission = builder.permission;
            this.sharedAt = builder.sharedAt;
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getId() {
            return id;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getOwnerId() {
            return ownerId;
        }

        public String getRecipientId() {
            return recipientId;
        }

        public SharePermission getPermission() {
            return permission;
        }

        public LocalDateTime getSharedAt() {
            return sharedAt;
        }

        public static class Builder {

            private String id;
            private String documentId;
            private String ownerId;
            private String recipientId;
            private SharePermission permission;
            private LocalDateTime sharedAt;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder documentId(String documentId) {
                this.documentId = documentId;
                return this;
            }

            public Builder ownerId(String ownerId) {
                this.ownerId = ownerId;
                return this;
            }

            public Builder recipientId(String recipientId) {
                this.recipientId = recipientId;
                return this;
            }

            public Builder permission(SharePermission permission) {
                this.permission = permission;
                return this;
            }

            public Builder sharedAt(LocalDateTime sharedAt) {
                this.sharedAt = sharedAt;
                return this;
            }

            public DocumentShare build() {
                return new DocumentShare(this);
            }
        }
    }

    public static class DocumentComment {

        private String id;
        private String documentId;
        private String userId;
        private String content;
        private int lineNumber;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private boolean resolved;
        private List<CommentReply> replies = new ArrayList<>();

        public void addReply(CommentReply reply) {
            replies.add(reply);
        }

        public static Builder builder() {
            return new Builder();
        }

        public String getId() {
            return id;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getUserId() {
            return userId;
        }

        public String getContent() {
            return content;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public boolean isResolved() {
            return resolved;
        }

        public void setResolved(boolean resolved) {
            this.resolved = resolved;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public List<CommentReply> getReplies() {
            return replies;
        }

        public static class Builder {

            private String id;
            private String documentId;
            private String userId;
            private String content;
            private int lineNumber;
            private LocalDateTime createdAt;
            private LocalDateTime updatedAt;
            private boolean resolved;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder documentId(String documentId) {
                this.documentId = documentId;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public Builder lineNumber(int lineNumber) {
                this.lineNumber = lineNumber;
                return this;
            }

            public Builder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public Builder updatedAt(LocalDateTime updatedAt) {
                this.updatedAt = updatedAt;
                return this;
            }

            public Builder resolved(boolean resolved) {
                this.resolved = resolved;
                return this;
            }

            public DocumentComment build() {
                DocumentComment comment = new DocumentComment();
                comment.id = this.id;
                comment.documentId = this.documentId;
                comment.userId = this.userId;
                comment.content = this.content;
                comment.lineNumber = this.lineNumber;
                comment.createdAt = this.createdAt;
                comment.updatedAt = this.updatedAt;
                comment.resolved = this.resolved;
                return comment;
            }
        }
    }

    public static class CommentReply {

        private String id;
        private String commentId;
        private String userId;
        private String content;
        private LocalDateTime createdAt;

        public static Builder builder() {
            return new Builder();
        }

        public String getId() {
            return id;
        }

        public String getCommentId() {
            return commentId;
        }

        public String getUserId() {
            return userId;
        }

        public String getContent() {
            return content;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public static class Builder {

            private String id;
            private String commentId;
            private String userId;
            private String content;
            private LocalDateTime createdAt;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder commentId(String commentId) {
                this.commentId = commentId;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public Builder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public CommentReply build() {
                CommentReply reply = new CommentReply();
                reply.id = this.id;
                reply.commentId = this.commentId;
                reply.userId = this.userId;
                reply.content = this.content;
                reply.createdAt = this.createdAt;
                return reply;
            }
        }
    }

    public static class DocumentVersion {

        private String id;
        private String documentId;
        private int versionNumber;
        private String userId;
        private String content;
        private String changeDescription;
        private LocalDateTime createdAt;

        public static Builder builder() {
            return new Builder();
        }

        public String getId() {
            return id;
        }

        public String getDocumentId() {
            return documentId;
        }

        public int getVersionNumber() {
            return versionNumber;
        }

        public String getUserId() {
            return userId;
        }

        public String getContent() {
            return content;
        }

        public String getChangeDescription() {
            return changeDescription;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public static class Builder {

            private String id;
            private String documentId;
            private int versionNumber;
            private String userId;
            private String content;
            private String changeDescription;
            private LocalDateTime createdAt;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder documentId(String documentId) {
                this.documentId = documentId;
                return this;
            }

            public Builder versionNumber(int versionNumber) {
                this.versionNumber = versionNumber;
                return this;
            }

            public Builder userId(String userId) {
                this.userId = userId;
                return this;
            }

            public Builder content(String content) {
                this.content = content;
                return this;
            }

            public Builder changeDescription(String changeDescription) {
                this.changeDescription = changeDescription;
                return this;
            }

            public Builder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public DocumentVersion build() {
                DocumentVersion version = new DocumentVersion();
                version.id = this.id;
                version.documentId = this.documentId;
                version.versionNumber = this.versionNumber;
                version.userId = this.userId;
                version.content = this.content;
                version.changeDescription = this.changeDescription;
                version.createdAt = this.createdAt;
                return version;
            }
        }
    }

    public static class DocumentAssignment {

        private String id;
        private String documentId;
        private String assigneeId;
        private String assignerId;
        private String notes;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime dueDate;

        public static Builder builder() {
            return new Builder();
        }

        public String getId() {
            return id;
        }

        public String getDocumentId() {
            return documentId;
        }

        public String getAssigneeId() {
            return assigneeId;
        }

        public String getAssignerId() {
            return assignerId;
        }

        public String getNotes() {
            return notes;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }

        public LocalDateTime getDueDate() {
            return dueDate;
        }

        public static class Builder {

            private String id;
            private String documentId;
            private String assigneeId;
            private String assignerId;
            private String notes;
            private String status;
            private LocalDateTime createdAt;
            private LocalDateTime updatedAt;
            private LocalDateTime dueDate;

            public Builder id(String id) {
                this.id = id;
                return this;
            }

            public Builder documentId(String documentId) {
                this.documentId = documentId;
                return this;
            }

            public Builder assigneeId(String assigneeId) {
                this.assigneeId = assigneeId;
                return this;
            }

            public Builder assignerId(String assignerId) {
                this.assignerId = assignerId;
                return this;
            }

            public Builder notes(String notes) {
                this.notes = notes;
                return this;
            }

            public Builder status(String status) {
                this.status = status;
                return this;
            }

            public Builder createdAt(LocalDateTime createdAt) {
                this.createdAt = createdAt;
                return this;
            }

            public Builder updatedAt(LocalDateTime updatedAt) {
                this.updatedAt = updatedAt;
                return this;
            }

            public Builder dueDate(LocalDateTime dueDate) {
                this.dueDate = dueDate;
                return this;
            }

            public DocumentAssignment build() {
                DocumentAssignment assignment = new DocumentAssignment();
                assignment.id = this.id;
                assignment.documentId = this.documentId;
                assignment.assigneeId = this.assigneeId;
                assignment.assignerId = this.assignerId;
                assignment.notes = this.notes;
                assignment.status = this.status;
                assignment.createdAt = this.createdAt;
                assignment.updatedAt = this.updatedAt;
                assignment.dueDate = this.dueDate;
                return assignment;
            }
        }
    }
}
