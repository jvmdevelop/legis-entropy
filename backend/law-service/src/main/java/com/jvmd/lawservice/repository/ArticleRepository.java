package com.jvmd.lawservice.repository;

import com.jvmd.lawservice.model.kz.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {

    @Query("SELECT a FROM Article a WHERE a.legislativeDocument.id = :legislativeDocumentId ORDER BY a.articleNumber")
    List<Article> findByLegislativeDocumentIdOrderByArticleNumber(@Param("legislativeDocumentId") UUID legislativeDocumentId);

    @Query("SELECT a FROM Article a WHERE a.legislativeDocument.id = :legislativeDocumentId AND a.articleNumber = :articleNumber")
    Optional<Article> findByLegislativeDocumentIdAndArticleNumber(@Param("legislativeDocumentId") UUID legislativeDocumentId, @Param("articleNumber") Integer articleNumber);

    @Query("SELECT a FROM Article a WHERE a.legislativeDocument.id = :legislativeDocumentId AND a.isActive = true")
    List<Article> findByLegislativeDocumentIdAndIsActiveTrue(@Param("legislativeDocumentId") UUID legislativeDocumentId);

    @Query("SELECT a FROM Article a WHERE LOWER(a.fullText) LIKE LOWER(CONCAT('%', :keyword, '%')) AND a.isActive = true")
    Page<Article> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COUNT(a) FROM Article a WHERE a.legislativeDocument.id = :lawId AND a.isActive = true")
    long countActiveArticlesByLaw(@Param("lawId") UUID lawId);
}
