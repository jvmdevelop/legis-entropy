package com.jvmd.dms.law.controller;

import com.jvmd.dms.law.service.ArticleIndexService;
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
        if (request.body() == null || request.body().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        articleIndexService.index(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/index/batch")
    public ResponseEntity<Void> indexBatch(@RequestBody List<ArticleIndexRequest> requests) {
        for (ArticleIndexRequest req : requests) {
            if (req.body() != null && !req.body().isBlank()) {
                articleIndexService.index(req);
            }
        }
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
}
