package com.jvmd.graphsservice.repository;

import com.jvmd.graphsservice.model.VoiceEvidence;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoiceEvidenceRepository extends Neo4jRepository<VoiceEvidence, String> {

    @Query("""
            MATCH (g:UserGraph {id: $graphId})
            WITH g
            MERGE (v:VoiceEvidence {id: $id})
              ON CREATE SET v.createdAt = $now,
                            v.graphId = $graphId,
                            v.userId = $userId
              SET v.label = coalesce($label, v.label),
                  v.classification = coalesce($classification, v.classification),
                  v.severity = coalesce($severity, v.severity),
                  v.summary = coalesce($summary, v.summary),
                  v.speakers = coalesce($speakers, v.speakers)
            MERGE (g)-[:CONTAINS_VOICE]->(v)
            RETURN v
            """)
    Optional<VoiceEvidence> upsertInGraph(@Param("id") String id,
                                          @Param("graphId") String graphId,
                                          @Param("userId") String userId,
                                          @Param("label") String label,
                                          @Param("classification") String classification,
                                          @Param("severity") String severity,
                                          @Param("summary") String summary,
                                          @Param("speakers") String speakers,
                                          @Param("now") LocalDateTime now);

    @Query("""
            MATCH (g:UserGraph {id: $graphId})-[:CONTAINS_VOICE]->(v:VoiceEvidence)
            RETURN v
            ORDER BY v.createdAt DESC
            """)
    List<VoiceEvidence> findByGraphId(@Param("graphId") String graphId);

    @Query("""
            MATCH (v:VoiceEvidence {id: $voiceId})
            MATCH (s:Situation {id: $situationId})
            MERGE (s)-[r:INVOLVES_VOICE]->(v)
              ON CREATE SET r.linkedAt = localdatetime()
            """)
    void linkToSituation(@Param("voiceId") String voiceId,
                         @Param("situationId") String situationId);

    @Query("""
            MATCH (v:VoiceEvidence {id: $voiceId})
            MATCH (a:Article {lawCode: $lawCode, country: $country, number: $number})
            MERGE (v)-[r:EVIDENCES_VIOLATION]->(a)
              ON CREATE SET r.linkedAt = localdatetime(),
                            r.reason = $reason
            """)
    void linkToArticle(@Param("voiceId") String voiceId,
                       @Param("lawCode") String lawCode,
                       @Param("country") String country,
                       @Param("number") String number,
                       @Param("reason") String reason);

    @Query("""
            MATCH (v:VoiceEvidence {id: $voiceId})
            MATCH (l:Law {code: $lawCode, country: $country})
            WITH v, l LIMIT 1
            MERGE (v)-[r:EVIDENCES_VIOLATION]->(l)
              ON CREATE SET r.linkedAt = localdatetime(),
                            r.reason = $reason
            """)
    void linkLaw(@Param("voiceId") String voiceId,
                 @Param("lawCode") String lawCode,
                 @Param("country") String country,
                 @Param("reason") String reason);

    @Query("""
            MATCH (v:VoiceEvidence {id: $voiceId})-[r:EVIDENCES_VIOLATION]->(l:Law {code: $lawCode, country: $country})
            DELETE r
            """)
    void unlinkLaw(@Param("voiceId") String voiceId,
                   @Param("lawCode") String lawCode,
                   @Param("country") String country);

    @Query("MATCH (v:VoiceEvidence {id: $id}) DETACH DELETE v")
    void deleteByIdCascade(@Param("id") String id);
}
