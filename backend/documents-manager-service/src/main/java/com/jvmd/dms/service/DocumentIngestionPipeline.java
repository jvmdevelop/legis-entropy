package com.jvmd.dms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionPipeline {

    private final DocumentReaderService documentReaderService;
    private final DocumentChunkingService documentChunkingService;
    private final VectorStore vectorStore;

    public int ingest(MultipartFile file, DocumentIngestionRequest request) {
        validate(file, request);
        List<Document> documents = documentReaderService.read(file);
        List<Document> chunks = documentChunkingService.splitAndEnrich(documents, request);
        vectorStore.add(chunks);
        log.info("Saved {} chunks for document {} (user: {})", chunks.size(), request.documentId(), request.userId());
        return chunks.size();
    }

    private void validate(MultipartFile file, DocumentIngestionRequest request) {
        if (request.userId() == null || request.userId().isBlank()) {
            throw new IllegalArgumentException("userId is required.");
        }
        if (request.documentId() == null || request.documentId().isBlank()) {
            throw new IllegalArgumentException("documentId is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty.");
        }
    }
}
