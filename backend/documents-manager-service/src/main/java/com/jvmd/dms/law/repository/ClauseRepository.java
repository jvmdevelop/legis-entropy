package com.jvmd.dms.law.repository;

import com.jvmd.dms.law.model.kz.Clause;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClauseRepository extends JpaRepository<Clause, UUID> {

    List<Clause> findByArticleIdOrderByClauseNumber(UUID articleId);

    @Query("SELECT c FROM Clause c WHERE c.article.id = :articleId AND LOWER(c.text) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Clause> findByArticleAndKeyword(@Param("articleId") UUID articleId, @Param("keyword") String keyword);

    @Query("SELECT COUNT(c) FROM Clause c WHERE c.article.id = :articleId")
    long countByArticle(@Param("articleId") UUID articleId);
}
