package com.jvmd.llmbrainservice.service.pipeline.handler;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.service.graph.DeepAnalysisService;
import com.jvmd.llmbrainservice.service.graph.link.LinkableSubject;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinker;
import com.jvmd.llmbrainservice.service.graph.link.SubjectLinkerRegistry;
import com.jvmd.llmbrainservice.service.pipeline.BrainTaskType;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.IntentClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.jvmd.llmbrainservice.util.JsonEscape;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class GraphDeepAnalysisHandler implements BrainTaskHandler {

    private final DeepAnalysisService deepAnalysisService;
    private final SubjectLinkerRegistry linkerRegistry;
    private final IntentClassifier intentClassifier;

    @Override
    public BrainTaskType taskType() {
        return BrainTaskType.GRAPH_DEEP_ANALYSIS;
    }

    @Override
    public BrainResponse handle(BrainRequest request, ContextPlan plan) {
        ResolvedSubject pair = resolveLinkerAndSubject(request);
        if (pair == null) {
            return new BrainResponse(
                    "Глубокий анализ требует активный объект в графе — выберите документ, ситуацию или ГС "
                            + "и повторите запрос.", 0);
        }
        int depth = intentClassifier.classifyDepth(request.message());
        log.info("Pipeline shortcut: GRAPH_DEEP_ANALYSIS for graph={} kind={} id={} depth={}",
                request.graphId(), pair.linker.kind(), pair.subject.id(), depth);
        var result = deepAnalysisService.run(
                request.graphId(), pair.linker, pair.subject,
                "RK", depth, null);
        return new BrainResponse(result.report(), 0);
    }

    @Override
    public SseEmitter handleStream(BrainRequest request, ContextPlan plan) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        int depth = intentClassifier.classifyDepth(request.message());
        emitThinking(emitter, "{\"step\":\"classify\",\"task\":\"GRAPH_DEEP_ANALYSIS\",\"depth\":" + depth + "}");

        ResolvedSubject resolved = resolveLinkerAndSubject(request);
        if (resolved == null) {
            try {
                emitter.send(SseEmitter.event().data(
                        "Глубокий анализ требует активный объект в графе — выберите документ, ситуацию или ГС "
                                + "и повторите запрос."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        CompletableFuture.runAsync(() -> {
            try {
                var result = deepAnalysisService.run(
                        request.graphId(), resolved.linker, resolved.subject,
                        "RK", depth,
                        ev -> {
                            try {
                                String json = "{\"phase\":\"" + JsonEscape.escape(ev.phase())
                                        + "\",\"done\":" + ev.done()
                                        + ",\"total\":" + ev.total()
                                        + ",\"message\":\"" + JsonEscape.escape(ev.message()) + "\"}";
                                emitter.send(SseEmitter.event().name("deep-analysis-progress").data(json));
                            } catch (IOException io) {
                                log.debug("Deep-analysis progress send failed: {}", io.getMessage());
                            }
                        });
                emitter.send(SseEmitter.event().data(result.report()));
                emitter.complete();
            } catch (Exception e) {
                log.error("Deep analysis failed: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event().name("error")
                            .data("Глубокий анализ прерван: " + e.getMessage()));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private record ResolvedSubject(SubjectLinker linker, LinkableSubject subject) {}

    private ResolvedSubject resolveLinkerAndSubject(BrainRequest request) {
        return linkerRegistry.selectFor(request)
                .flatMap(linker -> linker.resolve(request).map(s -> new ResolvedSubject(linker, s)))
                .orElse(null);
    }

    private void emitThinking(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().name("thinking").data(data));
        } catch (IOException e) {
            log.debug("Could not send thinking event: {}", e.getMessage());
        }
    }

}
