package com.jvmd.situationservice.controller;

import com.jvmd.situationservice.dto.ConflictRowDTO;
import com.jvmd.situationservice.dto.FlagArticleConflictRequest;
import com.jvmd.situationservice.dto.FlagDocumentArticleConflictRequest;
import com.jvmd.situationservice.service.ConflictService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph/conflicts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConflictController {

    private final ConflictService conflictService;

    @PostMapping("/article-article")
    public ResponseEntity<Map<String, Object>> flagArticle(@RequestBody FlagArticleConflictRequest req) {
        boolean ok = conflictService.flagArticleConflict(req);
        return ResponseEntity.ok(Map.of("flagged", ok));
    }

    @PostMapping("/document-article")
    public ResponseEntity<Map<String, Object>> flagDocument(@RequestBody FlagDocumentArticleConflictRequest req) {
        boolean ok = conflictService.flagDocumentArticleConflict(req);
        return ResponseEntity.ok(Map.of("flagged", ok));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Integer>> count(@RequestParam String graphId) {
        return ResponseEntity.ok(Map.of("count", conflictService.countByGraph(graphId)));
    }

    @GetMapping("/list")
    public ResponseEntity<List<ConflictRowDTO>> list(@RequestParam String graphId) {
        return ResponseEntity.ok(conflictService.listByGraph(graphId));
    }
}
