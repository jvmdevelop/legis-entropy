package com.jvmd.documentservice.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DocumentChunkingService {

    public List<Document> splitAndEnrich(List<Document> documents, DocumentIngestionRequest request) {
        List<Document> chunks = new ArrayList<>();
        TokenTextSplitter splitter = new TokenTextSplitter();

        for (Document document : documents) {
            List<Document> splitDocuments = splitter.apply(List.of(normalize(document)));
            chunks.addAll(splitDocuments);
        }

        Instant createdAt = Instant.now();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("userId", request.userId());
            chunk.getMetadata().put("documentId", request.documentId());
            chunk.getMetadata().put("fileName", request.fileName());
            putIfPresent(chunk, "page", page(chunk.getMetadata()));
            chunk.getMetadata().put("chunkIndex", i);
            chunk.getMetadata().put("sourceType", "USER_DOCUMENT");
            chunk.getMetadata().put("type", "USER_DOCUMENT");
            chunk.getMetadata().put("sectionTitle", sectionTitle(chunk.getText()));
            chunk.getMetadata().put("createdAt", createdAt.toString());
        }

        return chunks;
    }

    private void putIfPresent(Document document, String key, Object value) {
        if (value != null) {
            document.getMetadata().put(key, value);
        }
    }

    private Document normalize(Document document) {
        String text = document.getText() == null ? "" : document.getText()
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return new Document(document.getId(), text, document.getMetadata());
    }

    private Integer page(Map<String, Object> metadata) {
        for (String key : List.of("page", "page_number", "pageNumber")) {
            Object value = metadata.get(key);
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                try {
                    return Integer.valueOf(text);
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private String sectionTitle(String text) {
        if (text == null || text.isBlank()) return "";
        String[] lines = text.split("\\R");
        for (String line : lines) {
            String candidate = line.strip();
            if (candidate.length() >= 3 && candidate.length() <= 140 && looksLikeTitle(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private boolean looksLikeTitle(String line) {
        return line.matches("(?iu)^(раздел|глава|статья|пункт|§|\\d+[.)]).*")
                || line.equals(line.toUpperCase());
    }
}
