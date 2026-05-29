package com.jvmd.dms.controller;

import com.jvmd.dms.dto.RetrievalChunkResponse;
import com.jvmd.dms.law.model.LawCategory;
import com.jvmd.dms.law.storage.CategorizedLawStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/laws")
@RequiredArgsConstructor
@Slf4j
public class CategorizedLawController {

    private final CategorizedLawStorage categorizedLawStorage;

    @GetMapping("/search")
    public ResponseEntity<List<RetrievalChunkResponse>> searchAll(@RequestParam String query) {
        try {
            List<RetrievalChunkResponse> results = categorizedLawStorage.searchAll(query)
                    .stream()
                    .map(RetrievalChunkResponse::from)
                    .toList();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error in searchAll: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/search/{country}")
    public ResponseEntity<List<RetrievalChunkResponse>> searchByCountry(
            @PathVariable String country,
            @RequestParam String query) {
        try {
            List<RetrievalChunkResponse> results = categorizedLawStorage.searchByCountry(query, country.toUpperCase())
                    .stream()
                    .map(RetrievalChunkResponse::from)
                    .toList();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error in searchByCountry: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/search/category/{category}")
    public ResponseEntity<List<RetrievalChunkResponse>> searchByCategory(
            @PathVariable LawCategory category,
            @RequestParam String query) {
        try {
            List<RetrievalChunkResponse> results = categorizedLawStorage.searchByCategory(query, category)
                    .stream()
                    .map(RetrievalChunkResponse::from)
                    .toList();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error in searchByCategory: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/search/{country}/{category}")
    public ResponseEntity<List<RetrievalChunkResponse>> searchByCountryAndCategory(
            @PathVariable String country,
            @PathVariable LawCategory category,
            @RequestParam String query) {
        try {
            List<RetrievalChunkResponse> results = categorizedLawStorage.search(query, country.toUpperCase(), category)
                    .stream()
                    .map(RetrievalChunkResponse::from)
                    .toList();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error in searchByCountryAndCategory: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/categories")
    public ResponseEntity<LawCategory[]> getCategories() {
        return ResponseEntity.ok(LawCategory.values());
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Integer>> getStatistics() {
        try {
            Map<String, Integer> stats = categorizedLawStorage.getCollectionStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting statistics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/collections")
    public ResponseEntity<List<String>> getCollections() {
        try {
            Map<String, Integer> stats = categorizedLawStorage.getCollectionStatistics();
            List<String> collections = stats.keySet().stream()
                .filter(key -> stats.get(key) > 0)
                .sorted()
                .toList();
            return ResponseEntity.ok(collections);
        } catch (Exception e) {
            log.error("Error getting collections: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
