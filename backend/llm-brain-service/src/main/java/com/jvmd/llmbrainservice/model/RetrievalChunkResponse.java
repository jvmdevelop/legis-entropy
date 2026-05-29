package com.jvmd.llmbrainservice.model;

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
}
