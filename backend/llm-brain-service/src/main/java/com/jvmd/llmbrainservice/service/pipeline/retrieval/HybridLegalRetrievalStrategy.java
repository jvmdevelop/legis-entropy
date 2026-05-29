package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.service.citation.Citation;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class HybridLegalRetrievalStrategy implements RetrievalStrategy {

    private final UserDocumentRetrievalStrategy userDocumentRetrievalStrategy;
    private final LawRetrievalStrategy lawRetrievalStrategy;

    @Override
    public RetrievalMode mode() {
        return RetrievalMode.HYBRID_LEGAL;
    }

    @Override
    public DocumentContext retrieve(BrainRequest request, ContextPlan plan) {
        DocumentContext documentContext = userDocumentRetrievalStrategy.retrieve(request, plan);
        DocumentContext lawContext = lawRetrievalStrategy.retrieve(request, plan);
        List<Citation> citations = new ArrayList<>();
        citations.addAll(documentContext.citations());
        citations.addAll(lawContext.citations());
        return DocumentContext.of("""
                Контекст документа:
                %s

                Контекст законов:
                %s
                """.formatted(documentContext.text(), lawContext.text()), citations);
    }
}
