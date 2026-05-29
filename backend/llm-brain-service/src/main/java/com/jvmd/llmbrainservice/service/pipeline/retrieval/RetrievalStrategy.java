package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;

public interface RetrievalStrategy {

    RetrievalMode mode();

    DocumentContext retrieve(BrainRequest request, ContextPlan plan);
}
