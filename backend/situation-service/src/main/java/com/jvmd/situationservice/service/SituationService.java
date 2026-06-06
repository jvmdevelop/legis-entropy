package com.jvmd.situationservice.service;

import com.jvmd.situationservice.dto.CreateSituationRequest;
import com.jvmd.situationservice.dto.SituationDTO;
import com.jvmd.situationservice.dto.UpdateSituationRequest;
import com.jvmd.situationservice.model.Situation;
import com.jvmd.situationservice.repository.SituationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SituationService {

    private final SituationRepository situationRepository;

    public SituationDTO create(String graphId, String userId, CreateSituationRequest req) {
        String id = UUID.randomUUID().toString();
        Situation saved = situationRepository.createInGraph(
                id, graphId, userId, req.getTitle(), req.getBody(), req.getPlainText(), LocalDateTime.now()
        ).orElseThrow(() -> new IllegalStateException("Graph not found: " + graphId));
        log.info("Created situation {} in graph {}", saved.getId(), graphId);
        return SituationDTO.from(saved);
    }

    public List<SituationDTO> listByGraph(String graphId) {
        return situationRepository.findByGraphId(graphId).stream().map(SituationDTO::from).toList();
    }

    public Optional<SituationDTO> get(String id) {
        return situationRepository.findById(id).map(SituationDTO::from);
    }

    public Optional<SituationDTO> update(String id, UpdateSituationRequest req) {
        return situationRepository.updateById(id, req.getTitle(), req.getBody(), req.getPlainText(), LocalDateTime.now())
                .map(SituationDTO::from);
    }

    public void delete(String id) {
        situationRepository.deleteByIdCascade(id);
        log.info("Deleted situation {}", id);
    }

    public Optional<SituationDTO> updateGeneratedDoc(String situationId, String docId, String docTitle) {
        return situationRepository.updateGeneratedDoc(situationId, docId, docTitle, LocalDateTime.now())
                .map(SituationDTO::from);
    }

    public void linkDocument(String situationId, String documentId) {
        situationRepository.linkDocument(situationId, documentId);
    }

    public void unlinkDocument(String situationId, String documentId) {
        situationRepository.unlinkDocument(situationId, documentId);
    }

    public void linkArticle(String situationId, String lawCode, String country, String number) {
        situationRepository.linkArticle(situationId, lawCode, country, number);
    }

    public void unlinkArticle(String situationId, String lawCode, String country, String number) {
        situationRepository.unlinkArticle(situationId, lawCode, country, number);
    }

    public void linkLaw(String situationId, String lawCode, String country, String kind) {
        situationRepository.linkLaw(situationId, lawCode, country, kind);
    }

    public void unlinkLaw(String situationId, String lawCode, String country) {
        situationRepository.unlinkLaw(situationId, lawCode, country);
    }
}
