package com.jvmd.situationservice.repository;

import com.jvmd.situationservice.model.Situation;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SituationRepository extends Neo4jRepository<Situation, String> {

    @Query("""
            MATCH (g:UserGraph {id: $graphId})
            WITH g
            CREATE (s:Situation {id: $id, graphId: $graphId, userId: $userId,
                                 title: $title, body: $body, plainText: $plainText,
                                 createdAt: $now, updatedAt: $now})
            MERGE (g)-[:CONTAINS_SITUATION]->(s)
            RETURN s
            """)
    Optional<Situation> createInGraph(@Param("id") String id,
                                      @Param("graphId") String graphId,
                                      @Param("userId") String userId,
                                      @Param("title") String title,
                                      @Param("body") String body,
                                      @Param("plainText") String plainText,
                                      @Param("now") LocalDateTime now);

    @Query("""
            MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_SITUATION]->(s:Situation)
            RETURN s ORDER BY s.updatedAt DESC
            """)
    List<Situation> findByGraphId(@Param("graphId") String graphId);

    @Query("""
            MATCH (s:Situation {id: $id})
            SET s.title = coalesce($title, s.title),
                s.body = coalesce($body, s.body),
                s.plainText = coalesce($plainText, s.plainText),
                s.updatedAt = $now
            RETURN s
            """)
    Optional<Situation> updateById(@Param("id") String id,
                                   @Param("title") String title,
                                   @Param("body") String body,
                                   @Param("plainText") String plainText,
                                   @Param("now") LocalDateTime now);

    @Query("""
            MATCH (s:Situation {id: $id})
            SET s.generatedDocId = $docId, s.generatedDocTitle = $docTitle, s.updatedAt = $now
            RETURN s
            """)
    Optional<Situation> updateGeneratedDoc(@Param("id") String id,
                                           @Param("docId") String docId,
                                           @Param("docTitle") String docTitle,
                                           @Param("now") LocalDateTime now);

    @Query("MATCH (s:Situation {id: $id}) DETACH DELETE s")
    void deleteByIdCascade(@Param("id") String id);

    @Query("""
            MATCH (s:Situation {id: $situationId})
            MATCH (d:UserDocument {id: $documentId})
            MERGE (s)-[r:INVOLVES_DOCUMENT]->(d)
              ON CREATE SET r.linkedAt = localdatetime()
            """)
    void linkDocument(@Param("situationId") String situationId, @Param("documentId") String documentId);

    @Query("""
            MATCH (s:Situation {id: $situationId})-[r:INVOLVES_DOCUMENT]->(d:UserDocument {id: $documentId})
            DELETE r
            """)
    void unlinkDocument(@Param("situationId") String situationId, @Param("documentId") String documentId);

    @Query("""
            MATCH (s:Situation {id: $situationId})
            MATCH (a:Article {lawCode: $lawCode, country: $country, number: $number})
            MERGE (s)-[r:INVOLVES_ARTICLE]->(a)
              ON CREATE SET r.linkedAt = localdatetime()
            """)
    void linkArticle(@Param("situationId") String situationId,
                     @Param("lawCode") String lawCode,
                     @Param("country") String country,
                     @Param("number") String number);

    @Query("""
            MATCH (s:Situation {id: $situationId})-[r:INVOLVES_ARTICLE]->(a:Article {lawCode: $lawCode, country: $country, number: $number})
            DELETE r
            """)
    void unlinkArticle(@Param("situationId") String situationId,
                       @Param("lawCode") String lawCode,
                       @Param("country") String country,
                       @Param("number") String number);

    @Query("""
            MATCH (s:Situation {id: $situationId})
            MATCH (l:Law {code: $lawCode, country: $country})
            WITH s, l LIMIT 1
            MERGE (s)-[r:INVOLVES_LAW]->(l)
              ON CREATE SET r.linkedAt = localdatetime(), r.kind = coalesce($kind, 'SEMANTIC')
            """)
    void linkLaw(@Param("situationId") String situationId,
                 @Param("lawCode") String lawCode,
                 @Param("country") String country,
                 @Param("kind") String kind);

    @Query("""
            MATCH (s:Situation {id: $situationId})-[r:INVOLVES_LAW]->(l:Law {code: $lawCode, country: $country})
            DELETE r
            """)
    void unlinkLaw(@Param("situationId") String situationId,
                   @Param("lawCode") String lawCode,
                   @Param("country") String country);
}
