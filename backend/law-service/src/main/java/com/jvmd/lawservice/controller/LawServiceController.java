package com.jvmd.lawservice.controller;

import com.jvmd.lawservice.dto.ArticleResponseDto;
import com.jvmd.lawservice.dto.LawResponseDto;
import com.jvmd.lawservice.service.LawService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/laws")
public class LawServiceController {

    private final LawService lawService;

    @GetMapping("/search")
    public ResponseEntity<Page<LawResponseDto>> search(
            @RequestParam String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<LawResponseDto> results = lawService.searchLaws(query, PageRequest.of(page, size));
        return ResponseEntity.ok(results);
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<LawResponseDto> getByCode(@PathVariable String code) {
        return lawService.getLawByCode(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LawResponseDto> getById(@PathVariable UUID id) {
        return lawService.getLawById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/hierarchy/constitution-and-codes")
    public ResponseEntity<List<LawResponseDto>> getConstitutionAndCodes() {
        List<LawResponseDto> docs = lawService.getConstitutionAndCodes();
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/hierarchy/all-codes")
    public ResponseEntity<List<LawResponseDto>> getAllCodes() {
        List<LawResponseDto> codes = lawService.getAllCodes();
        return ResponseEntity.ok(codes);
    }

    @GetMapping("/hierarchy/all-laws")
    public ResponseEntity<List<LawResponseDto>> getAllLaws() {
        List<LawResponseDto> laws = lawService.getAllLaws();
        return ResponseEntity.ok(laws);
    }

    @GetMapping("/{id}/articles")
    public ResponseEntity<List<ArticleResponseDto>> getArticles(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        List<ArticleResponseDto> articles = lawService.getArticles(id, activeOnly);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/{lawCode}/articles/{articleNumber}")
    public ResponseEntity<ArticleResponseDto> getArticle(
            @PathVariable String lawCode,
            @PathVariable Integer articleNumber
    ) {
        return lawService.getArticle(lawCode, articleNumber)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/search/articles")
    public ResponseEntity<List<ArticleResponseDto>> searchArticles(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<ArticleResponseDto> articles = lawService.searchArticles(keyword, limit);
        return ResponseEntity.ok(articles);
    }

    @GetMapping("/{id}/hierarchy")
    public ResponseEntity<Map<String, Object>> getHierarchy(@PathVariable UUID id) {
        Map<String, Object> hierarchy = lawService.getHierarchyInfo(id);
        return ResponseEntity.ok(hierarchy);
    }

    @GetMapping("/{id}/amendments")
    public ResponseEntity<List<LawResponseDto>> getAmendments(@PathVariable UUID id) {
        List<LawResponseDto> amendments = lawService.getAmendments(id);
        return ResponseEntity.ok(amendments);
    }

    @GetMapping("/stats/total-count")
    public ResponseEntity<Long> getTotalLawsCount() {
        Long count = lawService.getTotalLawsCount();
        return ResponseEntity.ok(count);
    }

    @GetMapping("/stats/by-type")
    public ResponseEntity<Map<String, Long>> getStatsByType() {
        Map<String, Long> stats = lawService.getStatsByType();
        return ResponseEntity.ok(stats);
    }
}
