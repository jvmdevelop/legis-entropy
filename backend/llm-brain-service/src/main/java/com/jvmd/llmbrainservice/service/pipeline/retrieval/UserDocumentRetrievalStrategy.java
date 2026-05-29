package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.context.DocumentContextFormatter;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserDocumentRetrievalStrategy implements RetrievalStrategy {

    private static final int FULL_TEXT_CHAR_LIMIT = 60_000;

    private final DmsClient dmsClient;
    private final DocumentContextFormatter formatter;

    @Override
    public RetrievalMode mode() {
        return RetrievalMode.USER_DOCUMENT;
    }

    @Override
    public DocumentContext retrieve(BrainRequest request, ContextPlan plan) {
        if (!request.hasDocumentId()) {
            return DocumentContext.unavailable("Документ не прикреплен.");
        }

        String fullText = fetchFullText(request);
        if (
            fullText != null &&
            !fullText.isBlank() &&
            fullText.length() <= FULL_TEXT_CHAR_LIMIT
        ) {
            return DocumentContext.of(fullText);
        }

        try {
            List<RetrievalChunkResponse> chunks = dmsClient.searchUserDocuments(
                request.message(),
                request.userId(),
                request.documentId()
            );
            return formatter.format(chunks);
        } catch (RuntimeException ex) {
            log.warn(
                "Could not load document context for user {} document {}: {}",
                request.userId(),
                request.documentId(),
                ex.getMessage()
            );
            return DocumentContext.unavailable(
                "Не удалось загрузить контекст документа из сервиса документов."
            );
        }
    }

    private String fetchFullText(BrainRequest request) {
        try {
            Map<String, String> response = dmsClient.getDocumentText(
                request.documentId(),
                request.userId()
            );
            return response == null ? null : response.get("text");
        } catch (RuntimeException ex) {
            log.warn(
                "Could not load full text for user {} document {}: {}",
                request.userId(),
                request.documentId(),
                ex.getMessage()
            );
            return null;
        }
    }
}
