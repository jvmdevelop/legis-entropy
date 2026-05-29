package com.jvmd.llmbrainservice.service.pipeline.handler;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.pipeline.BrainTaskType;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface BrainTaskHandler {
    BrainTaskType taskType();
    BrainResponse handle(BrainRequest request, ContextPlan plan);
    SseEmitter handleStream(BrainRequest request, ContextPlan plan);

    default boolean isDefaultHandler() { return false; }
}
