package kz.legis.entropy.service;

import kz.legis.entropy.domain.AnalysisEvent;
import kz.legis.entropy.domain.GraphData;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AnalysisStreamService {

    private final RustClient rustClient;
    private final StatsService statsService;

    public AnalysisStreamService(
        RustClient rustClient,
        StatsService statsService
    ) {
        this.rustClient = rustClient;
        this.statsService = statsService;
    }

    public Flux<ServerSentEvent<AnalysisEvent>> stream(
        String seeds,
        int depth
    ) {
        Flux<ServerSentEvent<AnalysisEvent>> started = Flux.just(
            sse("status", AnalysisEvent.started())
        );

        Flux<ServerSentEvent<AnalysisEvent>> pipeline = rustClient
            .getGraph(seeds, depth)
            .flatMapMany(graph -> buildPipelineEvents(graph))
            .onErrorResume(e ->
                Flux.just(
                    sse(
                        "error",
                        AnalysisEvent.error("Ошибка: " + e.getMessage())
                    )
                )
            );

        return Flux.concat(started, pipeline);
    }

    private Flux<ServerSentEvent<AnalysisEvent>> buildPipelineEvents(
        GraphData graph
    ) {
        var graphLoadedEvent = sse(
            "status",
            AnalysisEvent.graphLoaded(
                graph.nodes().size(),
                graph.links().size()
            )
        );

        var analyzingEvent = sse(
            "status",
            AnalysisEvent.analyzing(graph.nodes().size())
        );

        Mono<ServerSentEvent<AnalysisEvent>> completeEvent = Mono.fromCallable(
            () -> {
                var stats = statsService.compute(graph);
                return sse("complete", AnalysisEvent.complete(graph, stats));
            }
        );

        return Flux.concat(
            Flux.just(graphLoadedEvent, analyzingEvent),
            completeEvent.flux()
        );
    }

    private static <T> ServerSentEvent<T> sse(String eventType, T data) {
        return ServerSentEvent.<T>builder().event(eventType).data(data).build();
    }
}
