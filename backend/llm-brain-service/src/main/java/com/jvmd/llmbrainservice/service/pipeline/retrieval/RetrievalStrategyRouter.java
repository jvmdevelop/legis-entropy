package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class RetrievalStrategyRouter {

    private final Map<RetrievalMode, RetrievalStrategy> strategies = new EnumMap<>(RetrievalMode.class);

    public RetrievalStrategyRouter(List<RetrievalStrategy> strategies) {
        for (RetrievalStrategy strategy : strategies) {
            this.strategies.put(strategy.mode(), strategy);
        }
    }

    public DocumentContext retrieve(BrainRequest request, ContextPlan plan) {
        RetrievalStrategy strategy = strategies.get(plan.retrievalMode());
        if (strategy == null) {
            throw new IllegalStateException("No retrieval strategy registered for " + plan.retrievalMode());
        }
        return strategy.retrieve(request, plan);
    }
}
