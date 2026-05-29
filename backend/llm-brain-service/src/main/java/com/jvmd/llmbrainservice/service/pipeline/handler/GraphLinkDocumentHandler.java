package com.jvmd.llmbrainservice.service.pipeline.handler;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.graph.GraphActionService;
import com.jvmd.llmbrainservice.service.pipeline.BrainTaskType;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class GraphLinkDocumentHandler implements BrainTaskHandler {

    private final GraphActionService graphActionService;

    @Override
    public BrainTaskType taskType() {
        return BrainTaskType.GRAPH_LINK_DOCUMENT;
    }

    @Override
    public BrainResponse handle(BrainRequest request, ContextPlan plan) {
        log.info("Pipeline shortcut: GRAPH_LINK_SUBJECT for graph={} (doc={}, sit={}, voice={})",
                request.graphId(), request.documentId(),
                request.situationId(), request.voiceId());
        var result = graphActionService.linkRequestSubjectToLaws(request, 5, "RK");
        return new BrainResponse(result.report(), 0);
    }

    @Override
    public SseEmitter handleStream(BrainRequest request, ContextPlan plan) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        BrainResponse response = handle(request, plan);
        try {
            emitter.send(SseEmitter.event().data(response.content()));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
