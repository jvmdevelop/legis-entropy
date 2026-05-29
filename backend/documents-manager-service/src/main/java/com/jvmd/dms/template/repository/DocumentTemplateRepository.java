package com.jvmd.dms.template.repository;

import com.jvmd.dms.template.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, String> {
    Optional<DocumentTemplate> findByCode(String code);
    List<DocumentTemplate> findByKind(String kind);

    @org.springframework.data.jpa.repository.Query("""
            SELECT t FROM DocumentTemplate t
            WHERE t.systemTemplate = true OR t.userId = :userId
            ORDER BY t.systemTemplate ASC, t.updatedAt DESC
            """)
    List<DocumentTemplate> findVisibleToUser(@org.springframework.data.repository.query.Param("userId") String userId);

    List<DocumentTemplate> findByUserIdOrderByUpdatedAtDesc(String userId);
}
