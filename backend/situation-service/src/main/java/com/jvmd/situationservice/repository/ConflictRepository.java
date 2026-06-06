package com.jvmd.situationservice.repository;

import com.jvmd.situationservice.model.Situation;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConflictRepository extends Neo4jRepository<Situation, String> {

    @Query("""
        MATCH (a:Article {lawCode: $codeA, country: $country, number: $numberA})
        MATCH (b:Article {lawCode: $codeB, country: $country, number: $numberB})
        MERGE (a)-[r:CONFLICTS_WITH {graphId: $graphId}]->(b)
          ON CREATE SET r.reason = $reason, r.confidence = $confidence,
                        r.source = $source, r.extractedBy = $extractedBy,
                        r.extractedAt = localdatetime()
          ON MATCH SET r.reason = coalesce($reason, r.reason),
                       r.confidence = coalesce($confidence, r.confidence),
                       r.supersededAt = localdatetime()
        RETURN a.lawCode + ':' + a.number AS articleKey
        """)
    Optional<String> flagArticleConflict(
        @Param("graphId") String graphId,
        @Param("country") String country,
        @Param("codeA") String codeA,
        @Param("numberA") String numberA,
        @Param("codeB") String codeB,
        @Param("numberB") String numberB,
        @Param("reason") String reason,
        @Param("confidence") Double confidence,
        @Param("source") String source,
        @Param("extractedBy") String extractedBy
    );

    @Query("""
        MATCH (d:UserDocument {id: $documentId})
        MATCH (a:Article {lawCode: $lawCode, country: $country, number: $articleNumber})
        MERGE (d)-[r:CONFLICTS_WITH {graphId: $graphId}]->(a)
          ON CREATE SET r.clauseRef = $clauseRef, r.reason = $reason,
                        r.confidence = $confidence, r.source = $source,
                        r.extractedBy = $extractedBy, r.extractedAt = localdatetime()
          ON MATCH SET r.reason = coalesce($reason, r.reason),
                       r.clauseRef = coalesce($clauseRef, r.clauseRef),
                       r.confidence = coalesce($confidence, r.confidence),
                       r.supersededAt = localdatetime()
        RETURN a.lawCode + ':' + a.number AS articleKey
        """)
    Optional<String> flagDocumentArticleConflict(
        @Param("graphId") String graphId,
        @Param("documentId") String documentId,
        @Param("country") String country,
        @Param("lawCode") String lawCode,
        @Param("articleNumber") String articleNumber,
        @Param("clauseRef") String clauseRef,
        @Param("reason") String reason,
        @Param("confidence") Double confidence,
        @Param("source") String source,
        @Param("extractedBy") String extractedBy
    );

    @Query("MATCH ()-[r:CONFLICTS_WITH {graphId: $graphId}]->() RETURN count(r)")
    int countByGraph(@Param("graphId") String graphId);
}
