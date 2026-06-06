package com.jvmd.llmbrainservice.service.pipeline;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.pipeline.handler.BrainTaskHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Slf4j
public class BrainPipeline {

    private final IntentClassifier intentClassifier;
    private final BrainTaskHandlerRegistry handlerRegistry;

    public BrainResponse answer(BrainRequest request) {
        return handlerRegistry.handle(request, resolvePlan(request));
    }

    public SseEmitter streamAnswer(BrainRequest request) {
        return handlerRegistry.handleStream(request, resolvePlan(request));
    }

    private ContextPlan resolvePlan(BrainRequest request) {
        ContextPlan plan = intentClassifier.classify(request);
        if (plan.taskType().requiresGraphId() && !request.hasGraphId()) {
            log.warn("Task {} requires graphId but none provided - falling back to GENERAL_LEGAL_ADVICE", plan.taskType());
            return new ContextPlan(BrainTaskType.GENERAL_LEGAL_ADVICE, RetrievalMode.NONE);
        }
        return plan;
    }
}
