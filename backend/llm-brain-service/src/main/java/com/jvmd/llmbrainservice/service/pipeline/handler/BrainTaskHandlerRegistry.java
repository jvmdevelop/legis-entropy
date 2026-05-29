package com.jvmd.llmbrainservice.service.pipeline.handler;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BrainTaskHandlerRegistry {

    private final List<BrainTaskHandler> handlers;
    private final DefaultBrainHandler defaultHandler;

    public BrainResponse handle(BrainRequest request, ContextPlan plan) {
        return handlers.stream()
                .filter(h -> !h.isDefaultHandler() && h.taskType() == plan.taskType())
                .findFirst()
                .orElse(defaultHandler)
                .handle(request, plan);
    }

    public SseEmitter handleStream(BrainRequest request, ContextPlan plan) {
        return handlers.stream()
                .filter(h -> !h.isDefaultHandler() && h.taskType() == plan.taskType())
                .findFirst()
                .orElse(defaultHandler)
                .handleStream(request, plan);
    }
}
