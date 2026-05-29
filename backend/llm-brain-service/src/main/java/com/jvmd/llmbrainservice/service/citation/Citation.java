package com.jvmd.llmbrainservice.service.citation;

public record Citation(
        String id,
        String documentId,
        String fileName,
        Integer page,
        Integer chunkIndex,
        Double score,
        Integer articleNumber,
        String lawCode,
        String lawDocumentNumber
) {
}
