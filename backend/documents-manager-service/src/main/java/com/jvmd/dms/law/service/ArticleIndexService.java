package com.jvmd.dms.law.service;

import com.jvmd.dms.law.controller.ArticleIndexController.ArticleIndexRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleIndexService {

    private final VectorStore vectorStore;

    public void index(ArticleIndexRequest req) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("lawCode", req.lawCode());
        metadata.put("country", req.country() != null ? req.country() : "RK");
        metadata.put("articleNumber", req.articleNumber());
        metadata.put(
            "articleTitle",
            req.articleTitle() != null ? req.articleTitle() : ""
        );
        metadata.put("collection", collectionName(req.country()));
        metadata.put("sourceType", "article");

        String text = buildText(req);
        Document doc = new Document(text, metadata);
        vectorStore.add(List.of(doc));

        log.debug(
            "Indexed article {} ст.{} into pgvector",
            req.lawCode(),
            req.articleNumber()
        );
    }

    public void deleteByLaw(String lawCode, String country) {
        log.info(
            "deleteByLaw requested for {} {} — re-index will replace stale chunks",
            lawCode,
            country
        );
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
        return (country != null ? country.toLowerCase() : "rk") + "_article";
    }
}
