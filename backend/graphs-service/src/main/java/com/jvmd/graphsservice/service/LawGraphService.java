package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.dto.LawDTO;
import com.jvmd.graphsservice.dto.LawGraphResponse;
import com.jvmd.graphsservice.model.Law;
import com.jvmd.graphsservice.model.RelationType;
import com.jvmd.graphsservice.repository.LawRepository;
import com.jvmd.graphsservice.search.LawIndexService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class LawGraphService {

    private final LawRepository lawRepository;
    private final LawIndexService lawIndexService;

    private static final Map<RelationType, Function<Law, List<Law>>> RELATIONSHIP_GETTERS =
            new LinkedHashMap<>(Map.of(
                    RelationType.REFERENCES,  Law::getReferences,
                    RelationType.AMENDS,      Law::getAmends,
                    RelationType.DEFINES,     Law::getDefines,
                    RelationType.RELATED,     Law::getRelated,
                    RelationType.SUPERSEDES,  Law::getSupersedes,
                    RelationType.IMPLEMENTS,  Law::getImplementation
            ));

    public Optional<LawGraphResponse> getLawWithRelationships(String code, String country) {
        return lawRepository.findLawWithRelationships(code, country)
                .map(law -> {
                    Map<RelationType, List<LawDTO>> relationships = new LinkedHashMap<>();
                    RELATIONSHIP_GETTERS.forEach((type, getter) -> {
                        List<Law> rels = getter.apply(law);
                        if (!rels.isEmpty()) {
                            relationships.put(type, rels.stream().map(this::lawToDTO).toList());
                        }
                    });
                    return LawGraphResponse.builder()
                            .law(lawToDTO(law))
                            .relationships(relationships)
                            .build();
                });
    }

    public List<LawDTO> findRelatedLaws(String code, String country) {
        return lawRepository.findRelatedLaws(code, country)
                .stream()
                .map(this::lawToDTO)
                .toList();
    }

    public List<LawDTO> searchLaws(String query, String country) {
        try {
            List<LawDTO> results = lawIndexService.search(query, country);
            if (!results.isEmpty()) return results;
        } catch (Exception e) {
            log.warn("ES search failed, falling back to Neo4j: {}", e.getMessage());
        }
        return lawRepository.searchLaws(query, country)
                .stream()
                .map(this::lawToDTO)
                .toList();
    }

    public Optional<LawDTO> findLaw(String code, String country) {
        return lawRepository.findByCodeAndCountry(code, country)
                .map(this::lawToDTO);
    }

    public LawDTO createLaw(Law law) {
        if (!LawIndexService.isIndexable(law)) {
            log.debug("Skipping low-force act: {} ({})", law.getCode(), law.getLegalForce());
            return lawToDTO(law);
        }
        Law saved = lawRepository.save(law);
        lawIndexService.index(saved);
        log.info("Created and indexed law: {} {}", saved.getCode(), saved.getTitle());
        return lawToDTO(saved);
    }

    public void addRelationship(String fromCode, String fromCountry,
                               String toCode, String toCountry, RelationType type) {
        lawRepository.findByCodeAndCountry(fromCode, fromCountry).ifPresent(fromLaw ->
            lawRepository.findByCodeAndCountry(toCode, toCountry).ifPresent(toLaw -> {
                addRelationshipToLaw(fromLaw, toLaw, type);
                lawRepository.save(fromLaw);
                log.info("Added {} relationship: {} -> {}", type.getValue(), fromCode, toCode);
            })
        );
    }

    private void addRelationshipToLaw(Law source, Law target, RelationType type) {
        Function<Law, List<Law>> getter = RELATIONSHIP_GETTERS.get(type);
        if (getter != null) {
            List<Law> list = getter.apply(source);
            if (!list.contains(target)) list.add(target);
        }
    }

    private LawDTO lawToDTO(Law law) {
        return LawDTO.builder()
                .id(law.getId())
                .title(law.getTitle())
                .code(law.getCode())
                .country(law.getCountry())
                .type(law.getType())
                .adoptionDate(law.getAdoptionDate())
                .effectiveDate(law.getEffectiveDate())
                .status(law.getStatus())
                .summary(law.getSummary())
                .source(law.getSource())
                .extractedBy(law.getExtractedBy())
                .extractedAt(law.getExtractedAt())
                .confidence(law.getConfidence())
                .verifiedBy(law.getVerifiedBy())
                .verifiedAt(law.getVerifiedAt())
                .sourceUri(law.getSourceUri())
                .build();
    }
}
