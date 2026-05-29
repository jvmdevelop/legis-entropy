package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.dto.ArticleDTO;
import com.jvmd.graphsservice.dto.ArticleHistoryDTO;
import com.jvmd.graphsservice.dto.CreateArticleRequest;
import com.jvmd.graphsservice.dto.LinkDocumentToArticleRequest;
import com.jvmd.graphsservice.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/laws/{code}/articles")
    public ResponseEntity<List<ArticleDTO>> listByLaw(
            @PathVariable String code,
            @RequestParam(defaultValue = "RK") String country) {
        return ResponseEntity.ok(articleService.listByLaw(code, country));
    }

    @GetMapping("/laws/{code}/articles/{number}")
    public ResponseEntity<ArticleDTO> get(
            @PathVariable String code,
            @PathVariable String number,
            @RequestParam(defaultValue = "RK") String country) {
        return articleService.get(code, country, number)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/articles/search")
    public ResponseEntity<List<ArticleDTO>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "RK") String country) {
        return ResponseEntity.ok(articleService.search(query, country));
    }

    @GetMapping("/laws/{code}/articles/{number}/history")
    public ResponseEntity<ArticleHistoryDTO> history(
            @PathVariable String code,
            @PathVariable String number,
            @RequestParam(defaultValue = "RK") String country) {
        return articleService.history(code, country, number)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/articles")
    public ResponseEntity<ArticleDTO> create(@RequestBody CreateArticleRequest req) {
        try {
            ArticleDTO created = articleService.create(req);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/articles/link-document")
    public ResponseEntity<ArticleDTO> linkDocument(@RequestBody LinkDocumentToArticleRequest req) {
        return articleService.linkDocumentClause(req)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }
}
