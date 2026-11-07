package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.client.LawClient;
import com.jvmd.llmbrainservice.dto.LawInfo;
import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.context.DocumentContextFormatter;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LawRetrievalStrategy implements RetrievalStrategy {

    private static final String DEFAULT_COUNTRY = "RK";
    private static final int MAX_GRAPH_HOPS = 2;
    private static final int PER_QUESTION_HITS = 3;
    private static final int MAX_MERGED_HITS = 20;

    private final LawClient lawClient;
    private final GraphServiceClient graphServiceClient;
    private final DocumentContextFormatter formatter;
    private final LegalQueryRewriter queryRewriter;

    @Override
    public RetrievalMode mode() {
        return RetrievalMode.LAW;
    }

    @Override
    public DocumentContext retrieve(BrainRequest request, ContextPlan plan) {
        try {
            List<String> queries = queryRewriter.rewriteAll(request.message());
            List<RetrievalChunkResponse> chunks = queries.size() == 1
                ? search(queries.get(0))
                : searchEach(queries);

            DocumentContext primary = formatter.format(chunks);

            String graphContext = buildGraphContext(chunks, DEFAULT_COUNTRY);

            if (graphContext.isBlank()) {
                return primary;
            }

            String combined =
                primary.text() +
                "\n\n--- Смежные нормы (граф) ---\n" +
                graphContext;
            return DocumentContext.of(combined, primary.citations());
        } catch (RuntimeException ex) {
            log.warn("Could not load law context: {}", ex.getMessage());
            return DocumentContext.unavailable(
                "Не удалось загрузить контекст нормативно-правовых актов."
            );
        }
    }

    private List<RetrievalChunkResponse> search(String query) {
        List<RetrievalChunkResponse> chunks = lawClient.searchLawsByCountry(
            DEFAULT_COUNTRY,
            query
        );
        return chunks.isEmpty() ? lawClient.searchLaws(query) : chunks;
    }

    /** One search per sub-question, top hits of each interleaved so every question gets context. */
    private List<RetrievalChunkResponse> searchEach(List<String> queries) {
        List<CompletableFuture<List<RetrievalChunkResponse>>> futures;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            futures = queries.stream()
                .map(q -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return search(q);
                    } catch (RuntimeException e) {
                        log.debug("Sub-question search failed for '{}': {}", q, e.getMessage());
                        return List.<RetrievalChunkResponse>of();
                    }
                }, executor))
                .toList();
        }
        List<List<RetrievalChunkResponse>> perQuery = futures.stream()
            .map(CompletableFuture::join)
            .toList();

        Map<String, RetrievalChunkResponse> merged = new LinkedHashMap<>();
        for (int rank = 0; rank < PER_QUESTION_HITS; rank++) {
            for (List<RetrievalChunkResponse> hits : perQuery) {
                if (rank >= hits.size() || merged.size() >= MAX_MERGED_HITS) continue;
                RetrievalChunkResponse chunk = hits.get(rank);
                merged.putIfAbsent(chunkKey(chunk), chunk);
            }
        }
        log.info("Multi-question law retrieval: {} queries -> {} chunks", queries.size(), merged.size());
        return new ArrayList<>(merged.values());
    }

    private static String chunkKey(RetrievalChunkResponse chunk) {
        if (chunk.documentId() != null && chunk.chunkIndex() != null) {
            return chunk.documentId() + "#" + chunk.chunkIndex();
        }
        return String.valueOf(chunk.text() == null ? 0 : chunk.text().hashCode());
    }

    private String buildGraphContext(
        List<RetrievalChunkResponse> chunks,
        String country
    ) {
        Set<String> primaryCodes = extractLawCodes(chunks);
        if (primaryCodes.isEmpty()) return "";

        Set<String> seen = new LinkedHashSet<>(primaryCodes);
        StringBuilder sb = new StringBuilder();
        int hops = 0;

        for (String code : primaryCodes) {
            if (hops >= MAX_GRAPH_HOPS) break;
            try {
                List<LawInfo> related =
                    graphServiceClient.findRelatedLaws(code, country);
                for (LawInfo law : related) {
                    if (
                        law.code() == null || seen.contains(law.code())
                    ) continue;
                    seen.add(law.code());
                    sb.append("• ").append(law.code());
                    if (law.title() != null) sb.append(" — ").append(
                        law.title()
                    );
                    sb.append("\n");
                }
                hops++;
            } catch (Exception e) {
                log.debug("Graph hop failed for {}: {}", code, e.getMessage());
            }
        }

        return sb.toString();
    }

    private Set<String> extractLawCodes(List<RetrievalChunkResponse> chunks) {
        Set<String> codes = new LinkedHashSet<>();
        for (RetrievalChunkResponse chunk : chunks) {
            if (chunk.metadata() == null) continue;
            Object code = chunk.metadata().get("lawCode");
            if (code != null && !code.toString().isBlank()) {
                codes.add(code.toString());
            }
        }
        return codes;
    }
}
