package com.jvmd.llmbrainservice.service.pipeline.handler;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.draft.DocumentDraftService;
import com.jvmd.llmbrainservice.service.pipeline.BrainTaskType;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentDraftHandler implements BrainTaskHandler {

    private final DocumentDraftService documentDraftService;

    @Override
    public BrainTaskType taskType() {
        return BrainTaskType.DOCUMENT_DRAFT;
    }

    @Override
    public BrainResponse handle(BrainRequest request, ContextPlan plan) {
        log.info("Pipeline shortcut: DOCUMENT_DRAFT for user={} graphId={}", request.userId(), request.graphId());
        return documentDraftService.draft(request);
    }

    @Override
    public SseEmitter handleStream(BrainRequest request, ContextPlan plan) {
        log.info("Pipeline shortcut: DOCUMENT_DRAFT stream for user={} graphId={}", request.userId(), request.graphId());
        return documentDraftService.streamDraft(request);
    }
}
