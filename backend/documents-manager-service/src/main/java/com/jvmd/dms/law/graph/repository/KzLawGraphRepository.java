package com.jvmd.dms.law.graph.repository;

import com.jvmd.dms.law.graph.KzArticleGraphNode;
import com.jvmd.dms.law.graph.KzLawGraphNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface KzLawGraphRepository extends Neo4jRepository<KzLawGraphNode, Long> {

    Optional<KzLawGraphNode> findByUuid(UUID uuid);

    Optional<KzLawGraphNode> findByCode(String code);

    List<KzLawGraphNode> findByDocumentType(String documentType);

    @Query("MATCH (n:KzLaw) WHERE n.isActive = true AND n.hierarchyLevel = :level RETURN n")
    List<KzLawGraphNode> findByHierarchyLevel(@Param("level") String level);

    @Query("MATCH (n:KzLaw) WHERE toLower(n.title) CONTAINS toLower(:title) RETURN n LIMIT 50")
    List<KzLawGraphNode> searchByTitle(@Param("title") String title);

    @Query("MATCH (n:KzLaw)-[:CONTAINS]->(a:KzArticle) WHERE n.uuid = :lawUuid RETURN a")
    List<KzArticleGraphNode> findArticlesByLaw(@Param("lawUuid") UUID lawUuid);

    @Query("MATCH (source:KzLaw)-[rel]->(target:KzLaw) WHERE source.uuid = :uuid RETURN rel, target")
    List<Object> findRelatedLaws(@Param("uuid") UUID uuid);

    @Query("MATCH (n:KzLaw) WHERE n.isActive = true RETURN COUNT(n)")
    Long countActiveLaws();

    @Query("MATCH (n:KzLaw) RETURN n LIMIT :limit")
    List<KzLawGraphNode> findAllWithLimit(@Param("limit") Integer limit);
}
