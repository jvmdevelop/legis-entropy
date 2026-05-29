package com.jvmd.dms.dto;

import org.springframework.ai.document.Document;

import java.util.Map;

public record RetrievalChunkResponse(
        String text,
        String documentId,
        String fileName,
        Integer page,
        Integer chunkIndex,
        Double score,
        Map<String, Object> metadata
) {

    public static RetrievalChunkResponse from(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        return new RetrievalChunkResponse(
                document.getText(),
                stringValue(metadata.get("documentId")),
                stringValue(metadata.get("fileName")),
                intValue(metadata.get("page")),
                intValue(metadata.get("chunkIndex")),
                document.getScore(),
                metadata
        );
    }

    private static String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer intValue(Object value) {
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
        return null;
    }
}
