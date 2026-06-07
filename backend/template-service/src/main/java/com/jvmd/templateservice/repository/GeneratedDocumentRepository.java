package com.jvmd.templateservice.repository;

import com.jvmd.templateservice.model.GeneratedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeneratedDocumentRepository extends JpaRepository<GeneratedDocument, String> {
    List<GeneratedDocument> findByUserIdOrderByCreatedAtDesc(String userId);
    List<GeneratedDocument> findBySituationIdOrderByCreatedAtDesc(String situationId);
    List<GeneratedDocument> findByGraphIdOrderByCreatedAtDesc(String graphId);
}
