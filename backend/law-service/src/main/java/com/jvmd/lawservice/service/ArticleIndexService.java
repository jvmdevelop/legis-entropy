package com.jvmd.lawservice.service;

import com.jvmd.lawservice.controller.ArticleIndexController.ArticleIndexRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleIndexService {

    private final VectorStore vectorStore;

    public void index(ArticleIndexRequest req) {
        indexBatch(List.of(req));
    }

    public void indexBatch(List<ArticleIndexRequest> requests) {
        List<ArticleIndexRequest> validRequests = requests.stream()
                .filter(req -> req.body() != null && !req.body().isBlank())
                .toList();
        if (validRequests.isEmpty()) return;

        // Replace existing versions of these articles before writing the new batch.
        new LinkedHashSet<>(validRequests.stream().map(this::articleFilter).toList())
                .forEach(vectorStore::delete);

        List<Document> documents = new ArrayList<>(validRequests.size());
        for (ArticleIndexRequest req : validRequests) {
            Map<String, Object> metadata = new HashMap<>();
            String country = normalizedCountry(req.country());
            metadata.put("lawCode", req.lawCode());
            metadata.put("country", country);
            metadata.put("articleNumber", req.articleNumber());
            metadata.put("articleTitle", req.articleTitle() != null ? req.articleTitle() : "");
            metadata.put("collection", collectionName(country));
            metadata.put("sourceType", "article");
            documents.add(new Document(buildText(req), metadata));
        }
        for (int start = 0; start < documents.size(); start += 100) {
            vectorStore.add(documents.subList(start, Math.min(start + 100, documents.size())));
        }
        log.debug("Indexed {} articles into pgvector", documents.size());
    }

    public void deleteByLaw(String lawCode, String country) {
        vectorStore.delete("lawCode == '" + escape(lawCode) + "' && country == '" + escape(normalizedCountry(country)) + "'");
        log.info("Deleted indexed articles for law {} ({})", lawCode, country);
    }

    private String articleFilter(ArticleIndexRequest req) {
        return "lawCode == '" + escape(req.lawCode()) + "' && country == '" + escape(normalizedCountry(req.country()))
                + "' && articleNumber == '" + escape(req.articleNumber()) + "'";
    }

    private String normalizedCountry(String country) {
        return country == null || country.isBlank() ? "RK" : country.toUpperCase();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String buildText(ArticleIndexRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append(req.lawCode());
        if (req.articleNumber() != null && !req.articleNumber().isBlank()) {
            sb.append(" ст. ").append(req.articleNumber());
        }
        if (req.articleTitle() != null && !req.articleTitle().isBlank()) {
            sb.append(" ").append(req.articleTitle());
        }
        sb.append("\n").append(req.body());
        return sb.toString();
    }

    private String collectionName(String country) {
        return country.toLowerCase() + "_article";
    }
}
