package com.jvmd.dms.law.controller;

import com.jvmd.dms.law.model.kz.Article;
import com.jvmd.dms.law.model.kz.KzLegislativeDocument;
import com.jvmd.dms.law.model.kz.KzLegislativeDocumentType;
import com.jvmd.dms.law.parser.KzLawNerParser;
import com.jvmd.dms.law.service.KzLawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/kz-laws")
public class KzLawController {

    private final KzLawService lawService;

    @GetMapping("/search")
    public ResponseEntity<Page<KzLegislativeDocument>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<KzLegislativeDocument> results = lawService.searchByTitle(query, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<KzLegislativeDocument> getByCode(@PathVariable String code) {
        return lawService.findByCode(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<KzLegislativeDocument> getById(@PathVariable UUID id) {
        return lawService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<List<KzLegislativeDocument>> getByType(@PathVariable KzLegislativeDocumentType type) {
        List<KzLegislativeDocument> docs = lawService.findByType(type);
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/{id}/articles")
    public ResponseEntity<List<Article>> getArticles(@PathVariable UUID id) {
        List<Article> articles = lawService.getArticles(id);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/{id}/articles/{articleNumber}")
    public ResponseEntity<Article> getArticle(
            @PathVariable UUID id,
            @PathVariable Integer articleNumber
    ) {
        return lawService.getArticle(id, articleNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/all/codes")
    public ResponseEntity<List<KzLegislativeDocument>> getAllCodes() {
        List<KzLegislativeDocument> codes = lawService.getAllActiveCodes();
        return ResponseEntity.ok(codes);
    }

    @GetMapping("/all/laws")
    public ResponseEntity<List<KzLegislativeDocument>> getAllLaws() {
        List<KzLegislativeDocument> laws = lawService.getAllActiveLaws();
        return ResponseEntity.ok(laws);
    }

    @GetMapping("/{id}/hierarchy")
    public ResponseEntity<Map<String, Object>> getHierarchy(@PathVariable UUID id) {
        Map<String, Object> hierarchy = lawService.getDocumentHierarchy(id);
        return ResponseEntity.ok(hierarchy);
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeDocument(@RequestBody String documentText) {
        KzLawNerParser.NerExtractionResult result = lawService.analyzeDocumentForReferences(documentText);
        return ResponseEntity.ok(result.toMap());
    }

    @PostMapping("/{id}/mark-expired")
    public ResponseEntity<Void> markAsExpired(
            @PathVariable UUID id,
            @RequestParam LocalDate expirationDate
    ) {
        lawService.markAsExpired(id, expirationDate);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{parentId}/amendments")
    public ResponseEntity<KzLegislativeDocument> createAmendment(
            @PathVariable UUID parentId,
            @RequestParam String title,
            @RequestBody String content
    ) {
        KzLegislativeDocument amendment = lawService.createAmendment(parentId, title, content);
        return ResponseEntity.ok(amendment);
    }
}
