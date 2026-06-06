package com.jvmd.llmbrainservice.service.pipeline.retrieval;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.client.GraphServiceClient;
import com.jvmd.llmbrainservice.dto.LawInfo;
import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import com.jvmd.llmbrainservice.service.context.DocumentContext;
import com.jvmd.llmbrainservice.service.context.DocumentContextFormatter;
import com.jvmd.llmbrainservice.service.pipeline.ContextPlan;
import com.jvmd.llmbrainservice.service.pipeline.RetrievalMode;
import java.util.ArrayList;
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

    private final DmsClient dmsClient;
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
            String optimizedQuery = queryRewriter.rewrite(request.message());

            List<RetrievalChunkResponse> chunks = dmsClient.searchLawsByCountry(
                DEFAULT_COUNTRY,
                optimizedQuery
            );

            if (chunks.isEmpty()) {
                chunks = dmsClient.searchLaws(optimizedQuery);
            }

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
