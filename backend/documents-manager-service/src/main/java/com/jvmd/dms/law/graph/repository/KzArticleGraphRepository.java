package com.jvmd.dms.law.graph.repository;

import com.jvmd.dms.law.graph.KzArticleGraphNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KzArticleGraphRepository extends Neo4jRepository<KzArticleGraphNode, Long> {

    Optional<KzArticleGraphNode> findByUuid(UUID uuid);

    List<KzArticleGraphNode> findByLawCode(String lawCode);

    @Query("MATCH (a:KzArticle) WHERE a.lawCode = :lawCode AND a.articleNumber = :articleNumber RETURN a")
    Optional<KzArticleGraphNode> findByLawCodeAndArticleNumber(
            @Param("lawCode") String lawCode,
            @Param("articleNumber") Integer articleNumber
    );

    @Query("MATCH (a:KzArticle) WHERE toLower(a.title) CONTAINS toLower(:keyword) OR toLower(a.lawTitle) CONTAINS toLower(:keyword) RETURN a LIMIT 100")
    List<KzArticleGraphNode> searchByKeyword(@Param("keyword") String keyword);

    @Query("MATCH (a:KzArticle)-[rel]->(target) WHERE a.uuid = :uuid RETURN rel, target")
    List<Object> findReferencedBy(@Param("uuid") UUID uuid);

    @Query("MATCH (a:KzArticle) WHERE a.lawCode = :lawCode RETURN COUNT(a)")
    Long countByLawCode(@Param("lawCode") String lawCode);
}
