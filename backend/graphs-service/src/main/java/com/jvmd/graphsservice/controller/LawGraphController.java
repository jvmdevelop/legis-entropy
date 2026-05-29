package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.dto.LawDTO;
import com.jvmd.graphsservice.dto.LawGraphResponse;
import com.jvmd.graphsservice.model.Law;
import com.jvmd.graphsservice.model.RelationType;
import com.jvmd.graphsservice.service.LawGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/graph/laws")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LawGraphController {

    private final LawGraphService lawGraphService;

    @GetMapping("/{code}")
    public ResponseEntity<LawGraphResponse> getLawWithRelationships(
            @PathVariable String code,
            @RequestParam(defaultValue = "RK") String country) {
        return lawGraphService.getLawWithRelationships(code, country)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{code}/related")
    public ResponseEntity<List<LawDTO>> getRelatedLaws(
            @PathVariable String code,
            @RequestParam(defaultValue = "RK") String country) {
        List<LawDTO> relatedLaws = lawGraphService.findRelatedLaws(code, country);
        return ResponseEntity.ok(relatedLaws);
    }

    @GetMapping("/search")
    public ResponseEntity<List<LawDTO>> searchLaws(
            @RequestParam String query,
            @RequestParam(defaultValue = "RK") String country) {
        List<LawDTO> results = lawGraphService.searchLaws(query, country);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{code}/info")
    public ResponseEntity<LawDTO> getLaw(
            @PathVariable String code,
            @RequestParam(defaultValue = "RK") String country) {
        return lawGraphService.findLaw(code, country)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<LawDTO> createLaw(@RequestBody Law law) {
        try {
            LawDTO created = lawGraphService.createLaw(law);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/{fromCode}/relationships/{toCode}")
    public ResponseEntity<Void> addRelationship(
            @PathVariable String fromCode,
            @PathVariable String toCode,
            @RequestParam(defaultValue = "RK") String country,
            @RequestParam RelationType type) {
        try {
            lawGraphService.addRelationship(fromCode, country, toCode, country, type);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
