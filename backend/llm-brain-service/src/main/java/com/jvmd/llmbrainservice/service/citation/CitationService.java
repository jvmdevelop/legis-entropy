package com.jvmd.llmbrainservice.service.citation;

import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import org.springframework.stereotype.Service;

@Service
public class CitationService {

    public Citation citationFor(int index, RetrievalChunkResponse chunk) {
        return new Citation(
            citationId(index, chunk),
            chunk.documentId(),
            chunk.fileName(),
            chunk.page(),
            chunk.chunkIndex(),
            chunk.score(),
            extractIntMetadata(chunk, "articleNumber"),
            extractStringMetadata(chunk, "lawCode"),
            extractStringMetadata(chunk, "lawDocumentNumber")
        );
    }

    public String formatContextBlock(
        Citation citation,
        RetrievalChunkResponse chunk,
        int maxFragmentLength
    ) {
        StringBuilder context = new StringBuilder();

        if (citation.articleNumber() != null && citation.articleNumber() > 0) {
            context.append("=== [Статья ").append(citation.articleNumber());
            if (citation.lawCode() != null && !citation.lawCode().isBlank()) {
                context.append(" | ").append(citation.lawCode());
            }
            if (
                citation.lawDocumentNumber() != null &&
                !citation.lawDocumentNumber().isBlank()
            ) {
                context
                    .append(" (")
                    .append(citation.lawDocumentNumber())
                    .append(")");
            }
            context.append("] ===\n\n");
        }

        context.append("Фрагмент [").append(citation.id()).append("]\n");
        appendIfPresent(context, "documentId", citation.documentId());
        appendIfPresent(context, "fileName", citation.fileName());
        appendIfPresent(context, "page", citation.page());
        appendIfPresent(context, "chunkIndex", citation.chunkIndex());
        appendIfPresent(context, "score", citation.score());
        appendIfPresent(
            context,
            "sourceType",
            metadataValue(chunk, "sourceType")
        );
        appendIfPresent(
            context,
            "sectionTitle",
            metadataValue(chunk, "sectionTitle")
        );
        String text = chunk.text();
        context
            .append("text:\n")
            .append(text, 0, Math.min(text.length(), maxFragmentLength))
            .append("\n\n");
        return context.toString();
    }

    private String citationId(int index, RetrievalChunkResponse chunk) {
        if (chunk.documentId() != null && chunk.chunkIndex() != null) {
            return "doc-" + chunk.documentId() + "-chunk-" + chunk.chunkIndex();
        }
        if (chunk.chunkIndex() != null) {
            return "chunk-" + chunk.chunkIndex();
        }
        return "chunk-" + (index + 1);
    }

    private Object metadataValue(RetrievalChunkResponse chunk, String key) {
        return chunk.metadata() == null ? null : chunk.metadata().get(key);
    }

    private void appendIfPresent(
        StringBuilder context,
        String label,
        Object value
    ) {
        if (value != null) {
            context.append(label).append(": ").append(value).append("\n");
        }
    }

    private Integer extractIntMetadata(
        RetrievalChunkResponse chunk,
        String key
    ) {
        Object value = metadataValue(chunk, key);
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String extractStringMetadata(
        RetrievalChunkResponse chunk,
        String key
    ) {
        Object value = metadataValue(chunk, key);
        return value == null ? null : value.toString();
    }
}
