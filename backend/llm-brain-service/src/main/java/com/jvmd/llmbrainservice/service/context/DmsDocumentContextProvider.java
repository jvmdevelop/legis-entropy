package com.jvmd.llmbrainservice.service.context;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.model.BrainRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DmsDocumentContextProvider implements DocumentContextProvider {

    private final DmsClient dmsClient;
    private final DocumentContextFormatter formatter;

    @Override
    public DocumentContext load(BrainRequest request) {
        if (!request.hasDocumentId()) {
            return DocumentContext.unavailable("Документ не прикреплен.");
        }

        try {
            List<RetrievalChunkResponse> documents = dmsClient.searchUserDocuments(
                    request.message(),
                    request.userId(),
                    request.documentId()
            );
            return formatter.format(documents);
        } catch (RuntimeException ex) {
            log.warn(
                    "Could not load document context for user {} document {}: {}",
                    request.userId(),
                    request.documentId(),
                    ex.getMessage()
            );
            return DocumentContext.unavailable("Не удалось загрузить контекст документа из сервиса документов.");
        }
    }
}
