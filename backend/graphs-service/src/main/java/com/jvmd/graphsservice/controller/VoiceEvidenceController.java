package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.dto.UpsertVoiceEvidenceRequest;
import com.jvmd.graphsservice.dto.VoiceEvidenceDTO;
import com.jvmd.graphsservice.service.VoiceEvidenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-graphs/{graphId}/voice-evidence")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class VoiceEvidenceController {

    private final VoiceEvidenceService voiceEvidenceService;

    @PostMapping("/{voiceId}")
    public ResponseEntity<VoiceEvidenceDTO> upsert(
            @PathVariable String graphId,
            @PathVariable String voiceId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody UpsertVoiceEvidenceRequest req) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.ok(voiceEvidenceService.upsert(graphId, uid, voiceId, req));
    }

    @GetMapping
    public List<VoiceEvidenceDTO> list(@PathVariable String graphId) {
        return voiceEvidenceService.listByGraph(graphId);
    }

    @PostMapping("/{voiceId}/articles/{lawCode}/{number}")
    public ResponseEntity<Void> linkArticle(
            @PathVariable String graphId,
            @PathVariable String voiceId,
            @PathVariable String lawCode,
            @PathVariable String number,
            @RequestParam(defaultValue = "RK") String country,
            @RequestParam(required = false) String reason) {
        voiceEvidenceService.linkToArticle(voiceId, lawCode, country, number, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{voiceId}/situations/{situationId}")
    public ResponseEntity<Void> linkSituation(
            @PathVariable String graphId,
            @PathVariable String voiceId,
            @PathVariable String situationId) {
        voiceEvidenceService.linkToSituation(voiceId, situationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{voiceId}/laws/{lawCode}")
    public ResponseEntity<Void> linkLaw(
            @PathVariable String graphId,
            @PathVariable String voiceId,
            @PathVariable String lawCode,
            @RequestParam(defaultValue = "RK") String country,
            @RequestParam(required = false) String reason) {
        voiceEvidenceService.linkLaw(voiceId, lawCode, country, reason);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{voiceId}/laws/{lawCode}")
    public ResponseEntity<Void> unlinkLaw(
            @PathVariable String graphId,
            @PathVariable String voiceId,
            @PathVariable String lawCode,
            @RequestParam(defaultValue = "RK") String country) {
        voiceEvidenceService.unlinkLaw(voiceId, lawCode, country);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{voiceId}")
    public ResponseEntity<Void> delete(@PathVariable String graphId, @PathVariable String voiceId) {
        voiceEvidenceService.delete(voiceId);
        return ResponseEntity.noContent().build();
    }
}
