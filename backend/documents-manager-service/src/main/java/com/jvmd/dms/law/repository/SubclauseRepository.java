package com.jvmd.dms.law.repository;

import com.jvmd.dms.law.model.kz.Subclause;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubclauseRepository extends JpaRepository<Subclause, UUID> {

    List<Subclause> findByParentClauseIdOrderByLabel(UUID parentClauseId);

    @Query("SELECT s FROM Subclause s WHERE s.parentClause.id = :clauseId AND LOWER(s.text) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Subclause> findByClauseAndKeyword(@Param("clauseId") UUID clauseId, @Param("keyword") String keyword);

    @Query("SELECT COUNT(s) FROM Subclause s WHERE s.parentClause.id = :clauseId")
    long countByClause(@Param("clauseId") UUID clauseId);
}
