package com.jvmd.llmbrainservice.service;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.pipeline.BrainPipeline;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
public class BrainService {

    private final BrainPipeline brainPipeline;

    public BrainResponse chat(BrainRequest request) {
        return brainPipeline.answer(request);
    }

    public SseEmitter streamChat(BrainRequest request) {
        return brainPipeline.streamAnswer(request);
    }
}
