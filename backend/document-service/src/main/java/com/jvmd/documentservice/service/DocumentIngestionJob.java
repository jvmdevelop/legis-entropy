package com.jvmd.documentservice.service;

import com.jvmd.documentservice.model.UserDocument;
import com.jvmd.documentservice.repository.UserDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionJob {

    private final UserDocumentRepository userDocumentRepository;
    private final DocumentStorageService documentStorageService;
    private final DocumentIngestionPipeline ingestionPipeline;

    @Async("documentIngestionExecutor")
    public void indexAsync(String documentId) {
        index(documentId);
    }

    public void index(String documentId) {
        markIndexing(documentId);
        UserDocument document = requireDocument(documentId);
        try {
            StoredObject object = documentStorageService.load(
                    document.getObjectName(), document.getFileName(), document.getContentType());
            int chunkCount = ingestionPipeline.ingest(
                    new StoredMultipartFile(object),
                    new DocumentIngestionRequest(document.getId(), document.getUserId(), document.getFileName())
            );
            markReady(documentId, chunkCount);
        } catch (RuntimeException ex) {
            log.warn("Document ingestion failed for {}: {}", documentId, ex.getMessage());
            markFailed(documentId, ex.getMessage());
        }
    }

    public void markIndexing(String documentId) {
        UserDocument document = requireDocument(documentId);
        document.markIndexing();
        userDocumentRepository.save(document);
    }

    public void markReady(String documentId, int chunkCount) {
        UserDocument document = requireDocument(documentId);
        document.markReady(chunkCount);
        userDocumentRepository.save(document);
    }

    public void markFailed(String documentId, String errorMessage) {
        UserDocument document = requireDocument(documentId);
        document.markFailed(errorMessage);
        userDocumentRepository.save(document);
    }

    private UserDocument requireDocument(String documentId) {
        return userDocumentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found."));
    }
}
