package com.jvmd.graphsservice.repository;

import com.jvmd.graphsservice.model.Comment;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CommentRepository extends Neo4jRepository<Comment, String> {

    @Query("""
            MATCH (g:UserGraph {id: $graphId})
            WITH g
            MERGE (c:Comment {id: $id})
              ON CREATE SET c.createdAt = $now,
                            c.graphId = $graphId,
                            c.userId = $userId
              SET c.title = coalesce($title, c.title),
                  c.body  = coalesce($body, c.body),
                  c.preview = coalesce($preview, c.preview),
                  c.kind = coalesce($kind, c.kind),
                  c.subjectKind = coalesce($subjectKind, c.subjectKind),
                  c.subjectId = coalesce($subjectId, c.subjectId)
            MERGE (g)-[:CONTAINS_COMMENT]->(c)
            RETURN c
            """)
    Optional<Comment> upsertInGraph(@Param("id") String id,
                                    @Param("graphId") String graphId,
                                    @Param("userId") String userId,
                                    @Param("title") String title,
                                    @Param("body") String body,
                                    @Param("preview") String preview,
                                    @Param("kind") String kind,
                                    @Param("subjectKind") String subjectKind,
                                    @Param("subjectId") String subjectId,
                                    @Param("now") LocalDateTime now);

    @Query("""
            MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_COMMENT]->(c:Comment)
            RETURN c
            ORDER BY c.createdAt DESC
            """)
    List<Comment> findByGraphId(@Param("graphId") String graphId);

    @Query("""
            MATCH (c:Comment {id: $commentId})
            MATCH (s) WHERE s.id = $subjectId AND $subjectKind IN labels(s)
            MERGE (c)-[r:ANALYZES]->(s)
              ON CREATE SET r.linkedAt = localdatetime()
            """)
    void linkSubject(@Param("commentId") String commentId,
                     @Param("subjectKind") String subjectKind,
                     @Param("subjectId") String subjectId);

    @Query("""
            MATCH (c:Comment {id: $commentId})
            MATCH (l:Law {code: $lawCode, country: $country})
            WITH c, l LIMIT 1
            MERGE (c)-[r:REFERENCES_LAW]->(l)
              ON CREATE SET r.linkedAt = localdatetime()
            """)
    void linkLaw(@Param("commentId") String commentId,
                 @Param("lawCode") String lawCode,
                 @Param("country") String country);

    @Query("""
            MATCH (c:Comment {id: $commentId})
            MATCH (a:Article {lawCode: $lawCode, country: $country, number: $number})
            MERGE (c)-[r:REFERENCES_ARTICLE]->(a)
              ON CREATE SET r.linkedAt = localdatetime(),
                            r.reason = $reason
            """)
    void linkArticle(@Param("commentId") String commentId,
                     @Param("lawCode") String lawCode,
                     @Param("country") String country,
                     @Param("number") String number,
                     @Param("reason") String reason);

    @Query("MATCH (c:Comment {id: $id}) DETACH DELETE c")
    void deleteByIdCascade(@Param("id") String id);
}
