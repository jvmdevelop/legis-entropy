package kz.legis.entropy.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import kz.legis.entropy.domain.GraphNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class MlClient {

    private final WebClient client;

    public MlClient(@Qualifier("mlWebClient") WebClient client) {
        this.client = client;
    }

    // ── /search ───────────────────────────────────────────────────────────────

    public Mono<SearchResponse> search(String query, List<GraphNode> nodes, int topK) {
        var docs = nodes.stream()
                .map(n -> new MlDocument(n.id(), n.title(), "", n.status()))
                .toList();
        return client.post()
                .uri("/search")
                .bodyValue(new SearchRequest(query, docs, topK))
                .retrieve()
                .bodyToMono(SearchResponse.class)
                .onErrorReturn(new SearchResponse(List.of()));
    }

    // ── /compare ─────────────────────────────────────────────────────────────

    public Mono<CompareResponse> compare(GraphNode a, GraphNode b) {
        return client.post()
                .uri("/compare")
                .bodyValue(new CompareRequest(
                        new MlDocument(a.id(), a.title(), "", a.status()),
                        new MlDocument(b.id(), b.title(), "", b.status())
                ))
                .retrieve()
                .bodyToMono(CompareResponse.class)
                .onErrorReturn(new CompareResponse(0f, "unknown"));
    }

    // ── Wire types ────────────────────────────────────────────────────────────

    record MlDocument(String id, String title, String text, String status) {}

    record SearchRequest(String query, List<MlDocument> documents, @JsonProperty("top_k") int topK) {}

    public record SearchResult(String id, String title, float score) {}

    public record SearchResponse(List<SearchResult> results) {}

    record CompareRequest(@JsonProperty("doc_a") MlDocument docA, @JsonProperty("doc_b") MlDocument docB) {}

    public record CompareResponse(float similarity, String assessment) {}
}
