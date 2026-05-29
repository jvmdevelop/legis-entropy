package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;
import org.springframework.stereotype.Component;

@Component
public class NoRetrievalStrategy implements RetrievalStrategy {

    @Override
    public RetrievalMode mode() {
        return RetrievalMode.NONE;
    }

    @Override
    public DocumentContext retrieve(BrainRequest request, ContextPlan plan) {
        return DocumentContext.unavailable("Retrieval-контекст не использовался для этого запроса.");
    }
}
