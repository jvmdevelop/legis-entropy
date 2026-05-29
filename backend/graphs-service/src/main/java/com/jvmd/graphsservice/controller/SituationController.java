package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.dto.CreateSituationRequest;
import com.jvmd.graphsservice.dto.SituationDTO;
import com.jvmd.graphsservice.dto.UpdateSituationRequest;
import com.jvmd.graphsservice.service.SituationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-graphs/{graphId}/situations")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SituationController {

    private final SituationService situationService;

    @PostMapping
    public ResponseEntity<SituationDTO> create(
            @PathVariable String graphId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody CreateSituationRequest req) {
        String uid = userId != null ? userId : "anonymous";
        SituationDTO dto = situationService.create(graphId, uid, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public ResponseEntity<List<SituationDTO>> list(@PathVariable String graphId) {
        return ResponseEntity.ok(situationService.listByGraph(graphId));
    }

    @GetMapping("/{situationId}")
    public ResponseEntity<SituationDTO> get(@PathVariable String graphId, @PathVariable String situationId) {
        return situationService.get(situationId)
                .filter(s -> graphId.equals(s.getGraphId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{situationId}")
    public ResponseEntity<SituationDTO> update(
            @PathVariable String graphId,
            @PathVariable String situationId,
            @RequestBody UpdateSituationRequest req) {
        return situationService.update(situationId, req)
                .filter(s -> graphId.equals(s.getGraphId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{situationId}")
    public ResponseEntity<Void> delete(@PathVariable String graphId, @PathVariable String situationId) {
        situationService.delete(situationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{situationId}/generated-doc")
    public ResponseEntity<SituationDTO> updateGeneratedDoc(
            @PathVariable String graphId,
            @PathVariable String situationId,
            @RequestBody java.util.Map<String, String> body) {
        String docId = body.get("docId");
        String docTitle = body.getOrDefault("docTitle", "");
        return situationService.updateGeneratedDoc(situationId, docId, docTitle)
                .filter(s -> graphId.equals(s.getGraphId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{situationId}/documents/{documentId}")
    public ResponseEntity<Void> linkDocument(
            @PathVariable String graphId,
            @PathVariable String situationId,
            @PathVariable String documentId) {
        situationService.linkDocument(situationId, documentId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{situationId}/documents/{documentId}")
    public ResponseEntity<Void> unlinkDocument(
            @PathVariable String graphId,
            @PathVariable String situationId,
            @PathVariable String documentId) {
        situationService.unlinkDocument(situationId, documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{situationId}/articles/{lawCode}/{number}")
    public ResponseEntity<Void> linkArticle(
            @PathVariable String graphId,
            @PathVariable String situationId,
            @PathVariable String lawCode,
            @PathVariable String number,
            @RequestParam(defaultValue = "RK") String country) {
        situationService.linkArticle(situationId, lawCode, country, number);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{situationId}/articles/{lawCode}/{number}")
    public ResponseEntity<Void> unlinkArticle(
            @PathVariable String graphId,
            @PathVariable String situationId,
            @PathVariable String lawCode,
            @PathVariable String number,
            @RequestParam(defaultValue = "RK") String country) {
        situationService.unlinkArticle(situationId, lawCode, country, number);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{situationId}/laws/{lawCode}")
    public ResponseEntity<Void> linkLaw(
            @PathVariable String graphId,
            @PathVariable String situationId,
            @PathVariable String lawCode,
            @RequestParam(defaultValue = "RK") String country,
            @RequestParam(defaultValue = "SEMANTIC") String kind) {
        situationService.linkLaw(situationId, lawCode, country, kind);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{situationId}/laws/{lawCode}")
    public ResponseEntity<Void> unlinkLaw(
            @PathVariable String graphId,
            @PathVariable String situationId,
            @PathVariable String lawCode,
            @RequestParam(defaultValue = "RK") String country) {
        situationService.unlinkLaw(situationId, lawCode, country);
        return ResponseEntity.noContent().build();
    }
}
