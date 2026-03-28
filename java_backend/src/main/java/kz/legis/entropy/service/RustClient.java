package kz.legis.entropy.service;

import kz.legis.entropy.domain.GraphData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class RustClient {

    private static final Logger log = LoggerFactory.getLogger(RustClient.class);

    private final WebClient client;

    public RustClient(@Qualifier("rustWebClient") WebClient client) {
        this.client = client;
    }

    @Cacheable("graph")
    public Mono<GraphData> getGraph(String seeds, int depth) {
        return client.get()
                .uri(u -> u.path("/api/graph")
                        .queryParam("depth", depth)
                        .queryParamIfPresent("seeds", java.util.Optional.ofNullable(seeds))
                        .build())
                .retrieve()
                .bodyToMono(GraphData.class)
                .doOnError(e -> log.error("Failed to fetch graph from Rust service: {}", e.getMessage()));
    }
}
