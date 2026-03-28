package kz.legis.entropy.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import kz.legis.entropy.domain.GraphNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class MlClient {

    private final WebClient client;

    public MlClient(@Qualifier("mlWebClient") WebClient client) {
        this.client = client;
    }

    public Mono<SearchResponse> search(
        String query,
        List<GraphNode> nodes,
        int topK
    ) {
        var docs = nodes
            .stream()
            .map(n -> new MlDocument(n.id(), n.title(), "", n.status()))
            .toList();
        return client
            .post()
            .uri("/search")
            .bodyValue(new SearchRequest(query, docs, topK))
            .retrieve()
            .bodyToMono(SearchResponse.class)
            .onErrorReturn(new SearchResponse(List.of()));
    }

    public Mono<CompareResponse> compare(GraphNode a, GraphNode b) {
        return client
            .post()
            .uri("/compare")
            .bodyValue(
                new CompareRequest(
                    new MlDocument(a.id(), a.title(), "", a.status()),
                    new MlDocument(b.id(), b.title(), "", b.status())
                )
            )
            .retrieve()
            .bodyToMono(CompareResponse.class)
            .onErrorReturn(new CompareResponse(0f, "unknown"));
    }

    public Mono<ReviewResponse> review(
        GraphNode a,
        GraphNode b,
        float similarity,
        String assessment,
        List<String> sharedIssueExplanations
    ) {
        var docA = new DocMeta(
            a.title(),
            a.status(),
            a.refCount(),
            a.articleCount(),
            a.isAmendment()
        );
        var docB = new DocMeta(
            b.title(),
            b.status(),
            b.refCount(),
            b.articleCount(),
            b.isAmendment()
        );
        return client
            .post()
            .uri("/review")
            .bodyValue(
                new ReviewRequest(
                    docA,
                    docB,
                    similarity,
                    assessment,
                    sharedIssueExplanations
                )
            )
            .retrieve()
            .bodyToMono(ReviewResponse.class)
            .onErrorReturn(
                new ReviewResponse("Сервис анализа недоступен.", false)
            );
    }

    public Mono<CorpusReviewResponse> corpusReview(
        int total,
        int active,
        int outdated,
        int withIssues,
        java.util.Map<String, Integer> issueTypes,
        List<String> mostProblematic
    ) {
        return client
            .post()
            .uri("/corpus-review")
            .bodyValue(
                new CorpusReviewRequest(
                    total,
                    active,
                    outdated,
                    withIssues,
                    issueTypes,
                    mostProblematic
                )
            )
            .retrieve()
            .bodyToMono(CorpusReviewResponse.class)
            .onErrorReturn(
                new CorpusReviewResponse("Сервис анализа недоступен.", false)
            );
    }

    record MlDocument(String id, String title, String text, String status) {}

    record DocMeta(
        String title,
        String status,
        @JsonProperty("ref_count") int refCount,
        @JsonProperty("article_count") int articleCount,
        @JsonProperty("is_amendment") boolean isAmendment
    ) {}

    record SearchRequest(
        String query,
        List<MlDocument> documents,
        @JsonProperty("top_k") int topK
    ) {}

    public record SearchResult(String id, String title, float score) {}

    public record SearchResponse(List<SearchResult> results) {}

    record CompareRequest(
        @JsonProperty("doc_a") MlDocument docA,
        @JsonProperty("doc_b") MlDocument docB
    ) {}

    public record CompareResponse(float similarity, String assessment) {}

    record ReviewRequest(
        @JsonProperty("doc_a") DocMeta docA,
        @JsonProperty("doc_b") DocMeta docB,
        float similarity,
        String assessment,
        @JsonProperty("shared_issues") List<String> sharedIssues
    ) {}

    public record ReviewResponse(
        String review,
        @JsonProperty("model_ready") boolean modelReady
    ) {}

    record CorpusReviewRequest(
        @JsonProperty("total_docs") int totalDocs,
        @JsonProperty("active_count") int activeCount,
        @JsonProperty("outdated_count") int outdatedCount,
        @JsonProperty("with_issues") int withIssues,
        @JsonProperty("issue_types") java.util.Map<String, Integer> issueTypes,
        @JsonProperty("most_problematic") List<String> mostProblematic
    ) {}

    public record CorpusReviewResponse(
        String review,
        @JsonProperty("model_ready") boolean modelReady
    ) {}
}
