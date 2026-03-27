package kz.legis.entropy.handler;

import kz.legis.entropy.service.RustClient;
import kz.legis.entropy.service.StatsService;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
public class GraphHandler {

    private final RustClient rustClient;
    private final StatsService statsService;

    public GraphHandler(RustClient rustClient, StatsService statsService) {
        this.rustClient = rustClient;
        this.statsService = statsService;
    }

    public Mono<ServerResponse> getGraph(ServerRequest req) {
        String seeds = req.queryParam("seeds").orElse(null);
        int depth    = req.queryParam("depth").map(Integer::parseInt).orElse(1);

        return rustClient.getGraph(seeds, depth)
                .flatMap(graph -> ServerResponse.ok().bodyValue(graph));
    }

    public Mono<ServerResponse> getStats(ServerRequest req) {
        String seeds = req.queryParam("seeds").orElse(null);
        int depth    = req.queryParam("depth").map(Integer::parseInt).orElse(1);

        return rustClient.getGraph(seeds, depth)
                .map(statsService::compute)
                .flatMap(stats -> ServerResponse.ok().bodyValue(stats));
    }
}
