package com.jvmd.graphsservice.repository;

import com.jvmd.graphsservice.model.UserGraph;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserGraphRepository extends Neo4jRepository<UserGraph, String> {

    List<UserGraph> findByWorkspaceIdOrderByCreatedAtDesc(String workspaceId);

    List<UserGraph> findByUserIdOrderByCreatedAtDesc(String userId);

    @Query("MATCH (g:UserGraph {id: $graphId}) OPTIONAL MATCH (g)-[:CONTAINS_LAW]->(l:Law) RETURN count(l)")
    int countLaws(@Param("graphId") String graphId);

    @Query("MATCH (g:UserGraph {id: $graphId}) OPTIONAL MATCH (g)-[:CONTAINS_DOCUMENT]->(d:UserDocument) RETURN count(d)")
    int countDocuments(@Param("graphId") String graphId);

    @Query("MATCH (g:UserGraph {id: $graphId}) OPTIONAL MATCH (g)-[:CONTAINS_SITUATION]->(s:Situation) RETURN count(s)")
    int countSituations(@Param("graphId") String graphId);

    @Query("""
            MATCH (g:UserGraph {id: $graphId})
            WITH g
            MATCH (l:Law {code: $code, country: $country})
            WITH g, l LIMIT 1
            MERGE (g)-[:CONTAINS_LAW]->(l)
            RETURN l LIMIT 1
            """)
    Optional<com.jvmd.graphsservice.model.Law> attachLaw(@Param("graphId") String graphId,
                                                         @Param("code") String code,
                                                         @Param("country") String country);

    @Query("MATCH (g:UserGraph {id: $graphId})-[r:CONTAINS_LAW]->(l:Law {code: $code, country: $country}) DELETE r")
    void detachLaw(@Param("graphId") String graphId,
                   @Param("code") String code,
                   @Param("country") String country);

    @Query("MATCH (g:UserGraph {id: $graphId})-[r:CONTAINS_DOCUMENT]->(d:UserDocument {id: $documentId}) DELETE r")
    void detachDocument(@Param("graphId") String graphId, @Param("documentId") String documentId);

    @Query("MATCH (g:UserGraph {id: $graphId}) DETACH DELETE g")
    void deleteByIdCascade(@Param("graphId") String graphId);

}
