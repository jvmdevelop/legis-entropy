package kz.legis.entropy.handler;

import kz.legis.entropy.domain.AnalysisEvent;
import kz.legis.entropy.domain.CompareRequest;
import kz.legis.entropy.domain.CompareResult;
import kz.legis.entropy.domain.SearchRequest;
import kz.legis.entropy.service.AnalysisStreamService;
import kz.legis.entropy.service.MlClient;
import kz.legis.entropy.service.RustClient;
import kz.legis.entropy.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AnalysisHandler {

    private static final Logger log = LoggerFactory.getLogger(AnalysisHandler.class);

    private final AnalysisStreamService streamService;
    private final RustClient rustClient;
    private final MlClient mlClient;
    private final StatsService statsService;

    public AnalysisHandler(
        AnalysisStreamService streamService,
        RustClient rustClient,
        MlClient mlClient,
        StatsService statsService
    ) {
        this.streamService = streamService;
        this.rustClient = rustClient;
        this.mlClient = mlClient;
        this.statsService = statsService;
    }

    public Mono<ServerResponse> streamAnalysis(ServerRequest req) {
        String seeds = req.queryParam("seeds").orElse(null);
        int depth = req.queryParam("depth").map(Integer::parseInt).orElse(1);

        var events = streamService.stream(seeds, depth);
        return ServerResponse.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(events, new ParameterizedTypeReference<ServerSentEvent<AnalysisEvent>>() {});
    }

    public Mono<ServerResponse> search(ServerRequest req) {
        return req.bodyToMono(SearchRequest.class)
            .flatMap(body ->
                rustClient.getGraph(null, 1)
                    .flatMap(graph -> mlClient.search(body.query(), graph.nodes(), body.topK()))
                    .flatMap(result -> ServerResponse.ok().bodyValue(result))
            );
    }

    public Mono<ServerResponse> compare(ServerRequest req) {
        return req.bodyToMono(CompareRequest.class)
            .flatMap(body ->
                rustClient.getGraph(null, 1)
                    .flatMap(graph -> {
                        var nodeA = graph.nodes().stream()
                            .filter(n -> n.id().equals(body.id1())).findFirst();
                        var nodeB = graph.nodes().stream()
                            .filter(n -> n.id().equals(body.id2())).findFirst();

                        if (nodeA.isEmpty() || nodeB.isEmpty()) {
                            return ServerResponse.badRequest()
                                .bodyValue("One or both document IDs not found in graph");
                        }

                        var a = nodeA.get();
                        var b = nodeB.get();

                        // Shared issue explanations for LLM context
                        var sharedIssueExplanations = graph.issues().stream()
                            .filter(i -> i.documentIds().contains(a.id()) && i.documentIds().contains(b.id()))
                            .map(i -> i.explanation())
                            .limit(3)
                            .collect(Collectors.toList());

                        long sharedIssueCount = sharedIssueExplanations.size();

                        // Run BERT compare then LLM review sequentially
                        return mlClient.compare(a, b)
                            .flatMap(mlResult -> {
                                String assessment = mlResult.assessment();
                                String explanation = buildExplanation(a.title(), b.title(), mlResult.similarity(), sharedIssueCount);

                                return mlClient.review(a, b, mlResult.similarity(), assessment, sharedIssueExplanations)
                                    .map(reviewResult -> new CompareResult(
                                        a, b,
                                        mlResult.similarity(),
                                        assessment,
                                        explanation,
                                        sharedIssueCount,
                                        reviewResult.review(),
                                        reviewResult.modelReady()
                                    ));
                            })
                            .flatMap(result -> ServerResponse.ok().bodyValue(result));
                    })
            );
    }

    public Mono<ServerResponse> corpusReview(ServerRequest req) {
        return rustClient.getGraph(null, 1)
            .flatMap(graph -> {
                var stats = statsService.compute(graph);

                Map<String, Integer> issueTypes = graph.issues().stream()
                    .collect(Collectors.groupingBy(
                        i -> i.kind(),
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)
                    ));

                List<String> problematicTitles = graph.nodes().stream()
                    .filter(n -> n.issueCount() > 0)
                    .sorted((a, b) -> Integer.compare(b.issueCount(), a.issueCount()))
                    .limit(3)
                    .map(n -> n.title())
                    .collect(Collectors.toList());

                return mlClient.corpusReview(
                    graph.nodes().size(),
                    (int) stats.activeCount(),
                    (int) stats.outdatedCount(),
                    (int) stats.withIssues(),
                    issueTypes,
                    problematicTitles
                ).flatMap(result -> ServerResponse.ok().bodyValue(result));
            });
    }

    private String buildExplanation(String titleA, String titleB, float similarity, long shared) {
        int pct = Math.round(similarity * 100);
        String base = "«%s» и «%s» семантически совпадают на %d%%.".formatted(titleA, titleB, pct);
        if (shared > 0) {
            base += " Обнаружено %d общих нормативных проблем.".formatted(shared);
        }
        return base;
    }
}
