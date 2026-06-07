package com.jvmd.dms.service;

import com.jvmd.dms.dto.DocumentStatusResponse;
import com.jvmd.dms.model.UserDocument;
import com.jvmd.dms.repository.UserDocumentRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDocumentService {

    private final UserDocumentRepository userDocumentRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentIngestionJob ingestionJob;
    private final VectorStore vectorStore;
    private final DocumentReaderService documentReader;

    public String uploadUserDocument(MultipartFile file, String userId) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("userId is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }

        String documentId = UUID.randomUUID().toString();
        log.info(
            "Uploading document for user {}: {}",
            userId,
            file.getOriginalFilename()
        );
        String objectName = documentStorageService.store(
            documentId,
            userId,
            file
        );

        String plainText = extractPlainText(file);

        UserDocument document = new UserDocument();
        document.setId(documentId);
        document.setUserId(userId);
        document.setFileName(file.getOriginalFilename());
        document.setContentType(file.getContentType());
        document.setObjectName(objectName);
        document.setPlainText(plainText);
        document.setStatus(DocumentProcessingStatus.READY);
        document.setChunkCount(0);
        userDocumentRepository.save(document);
        ingestionJob.indexAsync(documentId);
        return documentId;
    }

    public String getPlainText(String documentId, String userId) {
        return requireDocument(documentId, userId).getPlainText();
    }

    private String extractPlainText(MultipartFile file) {
        try {
            return documentReader
                .read(file)
                .stream()
                .map(org.springframework.ai.document.Document::getText)
                .filter(t -> t != null && !t.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.warn(
                "Could not extract plain text from {}: {}",
                file.getOriginalFilename(),
                e.getMessage()
            );
            return "";
        }
    }

    public DocumentStatusResponse getStatus(String documentId, String userId) {
        return toStatus(requireDocument(documentId, userId));
    }

    public void deleteDocument(String documentId, String userId) {
        UserDocument document = requireDocument(documentId, userId);
        vectorStore.delete(
            filterExpression(document.getUserId(), document.getId())
        );
        documentStorageService.delete(document.getObjectName());
        document.markDeleted();
        userDocumentRepository.save(document);
    }

    public Page<DocumentStatusResponse> listUserDocuments(
        String userId,
        Pageable pageable
    ) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("userId is required.");
        }
        return userDocumentRepository
            .findByUserIdAndStatusNot(
                userId,
                DocumentProcessingStatus.DELETED,
                pageable
            )
            .map(this::toStatus);
    }

    public void reindexDocument(String documentId, String userId) {
        UserDocument document = requireDocument(documentId, userId);
        if (document.getStatus() == DocumentProcessingStatus.INDEXING) {
            throw new IllegalStateException(
                "Document is already being indexed."
            );
        }
        vectorStore.delete(
            filterExpression(document.getUserId(), document.getId())
        );
        document.markIndexing();
        userDocumentRepository.save(document);
        ingestionJob.indexAsync(document.getId());
    }

    public List<Document> searchInUserDocuments(
        String query,
        String userId,
        String documentId
    ) {
        if (!hasText(query)) {
            throw new IllegalArgumentException("query is required.");
        }
        if (!hasText(userId)) {
            throw new IllegalArgumentException("userId is required.");
        }

        boolean documentFilterEnabled = hasText(documentId);

        SearchRequest searchRequest = SearchRequest.builder()
            .query(query)
            .topK(documentFilterEnabled ? 20 : 12)
            .similarityThreshold(documentFilterEnabled ? 0.0 : 0.7)
            .filterExpression(
                documentFilterEnabled
                    ? filterExpression(userId, documentId)
                    : userFilterExpression(userId)
            )
            .build();

        return vectorStore
            .similaritySearch(searchRequest)
            .stream()
            .map(document -> addKeywordScore(document, query))
            .sorted(Comparator.comparingDouble(this::combinedScore).reversed())
            .limit(documentFilterEnabled ? 12 : 5)
            .toList();
    }

    private UserDocument requireDocument(String documentId, String userId) {
        if (!hasText(documentId)) {
            throw new IllegalArgumentException("documentId is required.");
        }
        if (!hasText(userId)) {
            throw new IllegalArgumentException("userId is required.");
        }
        return userDocumentRepository
            .findByIdAndUserId(documentId, userId)
            .orElseThrow(() ->
                new IllegalArgumentException("Document not found.")
            );
    }

    private DocumentStatusResponse toStatus(UserDocument document) {
        return new DocumentStatusResponse(
            document.getId(),
            document.getUserId(),
            document.getFileName(),
            document.getStatus(),
            document.getChunkCount(),
            document.getErrorMessage(),
            document.getCreatedAt(),
            document.getUpdatedAt()
        );
    }

    private Document addKeywordScore(Document document, String query) {
        double keywordScore = keywordScore(document.getText(), query);
        document.getMetadata().put("keywordScore", keywordScore);
        return document;
    }

    private double combinedScore(Document document) {
        double vectorScore =
            document.getScore() == null ? 0.0 : document.getScore();
        Object keywordScore = document.getMetadata().get("keywordScore");
        double keyword = keywordScore instanceof Number number
            ? number.doubleValue()
            : 0.0;
        return vectorScore + keyword;
    }

    private double keywordScore(String text, String query) {
        if (!hasText(text) || !hasText(query)) {
            return 0.0;
        }
        String normalizedText = text.toLowerCase(Locale.ROOT);
        double score = 0.0;
        for (String term : query.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (term.length() >= 3 && normalizedText.contains(term)) {
                score += 0.05;
            }
        }
        return score;
    }

    private String userFilterExpression(String userId) {
        return "userId == '" + escapeFilterValue(userId) + "'";
    }

    private String filterExpression(String userId, String documentId) {
        return (
            userFilterExpression(userId) +
            " && documentId == '" +
            escapeFilterValue(documentId) +
            "'"
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }
}
