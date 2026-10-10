package com.jvmd.lawservice.controller;

import com.jvmd.lawservice.service.ArticleIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/laws/articles")
@RequiredArgsConstructor
@Slf4j
public class ArticleIndexController {

    private final ArticleIndexService articleIndexService;

    public record ArticleIndexRequest(
            String lawCode,
            String country,
            String articleNumber,
            String articleTitle,
            String body
    ) {}

    @PostMapping("/index")
    public ResponseEntity<Void> indexArticle(@RequestBody ArticleIndexRequest request) {
        if (!isValid(request)) {
            return ResponseEntity.badRequest().build();
        }
        articleIndexService.index(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/index/batch")
    public ResponseEntity<Void> indexBatch(@RequestBody List<ArticleIndexRequest> requests) {
        if (requests == null || requests.size() > 5000
                || requests.stream().anyMatch(req -> req == null || !isValid(req))) {
            return ResponseEntity.badRequest().build();
        }
        articleIndexService.indexBatch(requests);
        log.info("Batch indexed {} articles", requests.size());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{lawCode}")
    public ResponseEntity<Void> deleteByLaw(
            @PathVariable String lawCode,
            @RequestParam(defaultValue = "RK") String country) {
        articleIndexService.deleteByLaw(lawCode, country);
        return ResponseEntity.noContent().build();
    }

    private boolean isValid(ArticleIndexRequest request) {
        return request != null
                && request.lawCode() != null && !request.lawCode().isBlank()
                && request.articleNumber() != null && !request.articleNumber().isBlank()
                && request.body() != null && !request.body().isBlank();
    }
}
