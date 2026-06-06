package com.jvmd.templateservice.repository;

import com.jvmd.templateservice.entity.DocumentTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTemplateRepository extends JpaRepository<DocumentTemplate, String> {
    Optional<DocumentTemplate> findByCode(String code);
    List<DocumentTemplate> findByKind(String kind);

    @Query("""
            SELECT t FROM DocumentTemplate t
            WHERE t.systemTemplate = true OR t.userId = :userId
            ORDER BY t.systemTemplate ASC, t.updatedAt DESC
            """)
    List<DocumentTemplate> findVisibleToUser(@Param("userId") String userId);

    List<DocumentTemplate> findByUserIdOrderByUpdatedAtDesc(String userId);
}
