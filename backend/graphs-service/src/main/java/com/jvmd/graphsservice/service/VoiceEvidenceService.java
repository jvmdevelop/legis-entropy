package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.dto.UpsertVoiceEvidenceRequest;
import com.jvmd.graphsservice.dto.VoiceEvidenceDTO;
import com.jvmd.graphsservice.model.VoiceEvidence;
import com.jvmd.graphsservice.repository.VoiceEvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceEvidenceService {

    private final VoiceEvidenceRepository repository;

    public VoiceEvidenceDTO upsert(String graphId, String userId, String voiceId, UpsertVoiceEvidenceRequest req) {
        VoiceEvidence saved = repository.upsertInGraph(
                voiceId, graphId, userId,
                req.getLabel(),
                req.getClassification(),
                req.getSeverity(),
                req.getSummary(),
                req.getSpeakers(),
                LocalDateTime.now()
        ).orElseThrow(() -> new IllegalStateException("Graph not found: " + graphId));
        if (req.getSituationId() != null && !req.getSituationId().isBlank()) {
            repository.linkToSituation(voiceId, req.getSituationId());
        }
        log.info("Upserted voice evidence {} into graph {}", voiceId, graphId);
        return VoiceEvidenceDTO.from(saved);
    }

    public List<VoiceEvidenceDTO> listByGraph(String graphId) {
        return repository.findByGraphId(graphId).stream().map(VoiceEvidenceDTO::from).toList();
    }

    public Optional<VoiceEvidenceDTO> get(String id) {
        return repository.findById(id).map(VoiceEvidenceDTO::from);
    }

    public void linkToArticle(String voiceId, String lawCode, String country, String number, String reason) {
        repository.linkToArticle(voiceId, lawCode, country, number, reason);
    }

    public void linkToSituation(String voiceId, String situationId) {
        repository.linkToSituation(voiceId, situationId);
    }

    public void linkLaw(String voiceId, String lawCode, String country, String reason) {
        repository.linkLaw(voiceId, lawCode, country, reason);
    }

    public void unlinkLaw(String voiceId, String lawCode, String country) {
        repository.unlinkLaw(voiceId, lawCode, country);
    }

    public void delete(String id) {
        repository.deleteByIdCascade(id);
    }
}
