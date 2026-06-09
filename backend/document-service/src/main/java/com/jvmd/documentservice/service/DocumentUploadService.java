package com.jvmd.documentservice.service;

import com.jvmd.documentservice.parser.KzLawNerParser;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentUploadService {

    private final KzLawNerParser nerParser;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    public DocumentUploadResult processDocument(MultipartFile file, UUID workspaceId, UUID userId) throws IOException {
        log.info("Processing document upload: {} (workspace: {})", file.getOriginalFilename(), workspaceId);

        String documentId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        long fileSize = file.getSize();

        String plainText = extractText(file);
        KzLawNerParser.NerExtractionResult nerResult = nerParser.extractNamedEntities(plainText);

        Map<String, Object> analysisPayload = new HashMap<>();
        analysisPayload.put("documentId", documentId);
        analysisPayload.put("workspaceId", workspaceId);
        analysisPayload.put("userId", userId);
        analysisPayload.put("fileName", fileName);
        analysisPayload.put("contentType", contentType);
        analysisPayload.put("fileSize", fileSize);
        analysisPayload.put("plainText", plainText);
        analysisPayload.put("nerResult", nerResult.toMap());

        return DocumentUploadResult.builder()
                .documentId(documentId)
                .fileName(fileName)
                .fileSize(fileSize)
                .contentType(contentType)
                .plainTextLength(plainText.length())
                .extractedEntities(nerResult.getTotalEntities())
                .articleReferencesFound(nerResult.getArticleReferences().size())
                .lawNamesFound(nerResult.getLawNames().size())
                .status("UPLOADED_AND_ANALYZED")
                .message("Document processed successfully. Found " + nerResult.getTotalEntities() + " legal entities.")
                .build();
    }

    private String extractText(MultipartFile file) throws IOException {
        if (file.getSize() > MAX_FILE_SIZE) throw new IOException("File exceeds maximum size of 10MB");
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        if (contentType != null && contentType.contains("application/pdf")) return extractPdfText(file);
        if (contentType != null && (contentType.contains("text") || contentType.contains("plain"))) return new String(file.getBytes());
        if (fileName != null && (fileName.endsWith(".txt") || fileName.endsWith(".md"))) return new String(file.getBytes());
        log.warn("Unsupported file format: {}. Attempting raw text extraction.", contentType);
        return new String(file.getBytes());
    }

    private String extractPdfText(MultipartFile file) throws IOException {
        File tempFile = Files.createTempFile("upload_", ".pdf").toFile();
        try {
            file.transferTo(tempFile);
            PagePdfDocumentReader reader = new PagePdfDocumentReader(tempFile.getAbsolutePath());
            List<Document> documents = reader.get();
            StringBuilder text = new StringBuilder();
            for (Document doc : documents) text.append(doc.getText()).append("\n");
            return text.toString();
        } finally {
            tempFile.delete();
        }
    }

    @Getter
    @Builder
    @AllArgsConstructor
    public static class DocumentUploadResult {
        private String documentId;
        private String fileName;
        private long fileSize;
        private String contentType;
        private int plainTextLength;
        private int extractedEntities;
        private int articleReferencesFound;
        private int lawNamesFound;
        private String status;
        private String message;
    }
}
