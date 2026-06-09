package com.jvmd.graphsservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@Slf4j
public class DmsArticleClient {

    public record ArticleIndexRequest(
            String lawCode,
            String country,
            String articleNumber,
            String articleTitle,
            String body
    ) {}

    private final RestClient restClient;

    public DmsArticleClient(
            @Value("${dms.base-url:http://law-service:8087}") String dmsBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(dmsBaseUrl)
                .build();
    }

    public void indexArticle(String lawCode, String country, String number,
                              String title, String body) {
        if (body == null || body.isBlank()) return;
        try {
            ArticleIndexRequest req = new ArticleIndexRequest(lawCode, country, number, title, body);
            restClient.post()
                    .uri("/api/laws/articles/index")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(req)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("DMS article indexed: {} ст.{}", lawCode, number);
        } catch (Exception e) {
            log.warn("Failed to index article {} ст.{} in DMS: {}", lawCode, number, e.getMessage());
        }
    }

    public void indexBatch(List<ArticleIndexRequest> requests) {
        if (requests == null || requests.isEmpty()) return;
        try {
            restClient.post()
                    .uri("/api/laws/articles/index/batch")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requests)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("DMS batch indexed {} articles", requests.size());
        } catch (Exception e) {
            log.warn("Failed to batch-index {} articles in DMS: {}", requests.size(), e.getMessage());
        }
    }
}
