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
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class GraphAddLawsHandler implements BrainTaskHandler {

    private static final Set<String> ADD_LAWS_STOPWORDS = Set.of(
            "добавь", "добавьте", "добавить", "подбери", "подбор",
            "найди", "найти", "помести", "поместить", "положи", "положить",
            "включи", "включить", "собери",
            "в", "на", "с", "и", "по", "про",
            "граф", "графе", "граф.",
            "пожалуйста", "давай", "давайте", "а"
    );

    private final GraphActionService graphActionService;

    @Override
    public BrainTaskType taskType() {
        return BrainTaskType.GRAPH_ADD_LAWS;
    }

    @Override
    public BrainResponse handle(BrainRequest request, ContextPlan plan) {
        String query = extractAddLawsQuery(request.message());
        log.info("Pipeline shortcut: GRAPH_ADD_LAWS for graph={} query='{}'", request.graphId(), query);
        var result = graphActionService.findAndAddLaws(request.graphId(), query, "RK", 5);
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

    private static String extractAddLawsQuery(String message) {
        if (message == null) return "";
        String[] tokens = message.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String t : tokens) {
            String key = t.toLowerCase(Locale.ROOT).replaceAll("[.,!?;]+$", "");
            if (ADD_LAWS_STOPWORDS.contains(key)) continue;
            if (key.isBlank()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(t);
        }
        String result = out.toString().trim();
        return result.isBlank() ? message : result;
    }
}
