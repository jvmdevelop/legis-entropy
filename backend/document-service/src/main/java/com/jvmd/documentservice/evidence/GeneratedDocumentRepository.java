package com.jvmd.documentservice.evidence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, String> {
    List<GeneratedDocument> findByGraphIdOrderByCreatedAtDesc(String graphId);
    List<GeneratedDocument> findBySituationIdOrderByCreatedAtDesc(String situationId);
}
