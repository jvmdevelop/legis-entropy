package com.jvmd.llmbrainservice.service.context;

import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.citation.Citation;
import com.jvmd.llmbrainservice.service.citation.CitationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DocumentContextFormatter {

    private static final int MAX_FRAGMENT_LENGTH = 2500;
    private final CitationService citationService;

    public DocumentContext format(List<RetrievalChunkResponse> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return DocumentContext.unavailable("Документ ещё индексируется или не найден в индексе пользователя.");
        }

        StringBuilder context = new StringBuilder();
        List<Citation> citations = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalChunkResponse chunk = chunks.get(i);
            if (chunk == null || !hasText(chunk.text())) {
                continue;
            }

            Citation citation = citationService.citationFor(i, chunk);
            citations.add(citation);
            context.append(citationService.formatContextBlock(citation, chunk, MAX_FRAGMENT_LENGTH));
        }

        if (context.isEmpty()) {
            return DocumentContext.unavailable("Документ найден, но его текстовые фрагменты пустые.");
        }

        return DocumentContext.of(context.toString(), citations);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
