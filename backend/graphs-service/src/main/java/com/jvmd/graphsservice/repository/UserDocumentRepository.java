package com.jvmd.graphsservice.repository;

import com.jvmd.graphsservice.model.UserDocument;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserDocumentRepository extends Neo4jRepository<UserDocument, String> {

    @Query("""
            MERGE (d:UserDocument {id: $documentId})
              ON CREATE SET d.userId = $userId, d.fileName = $fileName, d.mimeType = $mimeType,
                            d.fileUrl = $fileUrl, d.summary = $summary, d.createdAt = localdatetime()
              ON MATCH  SET d.fileName = $fileName, d.mimeType = $mimeType,
                            d.fileUrl = $fileUrl, d.summary = $summary
            WITH d MATCH (g:UserGraph {id: $graphId})
            MERGE (g)-[:CONTAINS_DOCUMENT]->(d)
            RETURN d
            """)
    UserDocument attachDocumentToGraph(@Param("graphId") String graphId,
                                       @Param("documentId") String documentId,
                                       @Param("userId") String userId,
                                       @Param("fileName") String fileName,
                                       @Param("mimeType") String mimeType,
                                       @Param("fileUrl") String fileUrl,
                                       @Param("summary") String summary);

    @Query("""
            MATCH (d:UserDocument {id: $documentId})
            WITH d
            MATCH (l:Law {code: $lawCode, country: $country})
            WITH d, l LIMIT 1
            MERGE (d)-[r:RELATES_TO {graphId: $graphId, kind: $kind}]->(l)
              ON CREATE SET r.source = $source,
                            r.extractedBy = $extractedBy,
                            r.extractedAt = localdatetime(),
                            r.confidence = $confidence
              ON MATCH SET r.confidence = coalesce($confidence, r.confidence),
                           r.extractedBy = coalesce($extractedBy, r.extractedBy)
            RETURN d LIMIT 1
            """)
    UserDocument linkToLawWithProvenance(@Param("graphId") String graphId,
                                         @Param("documentId") String documentId,
                                         @Param("lawCode") String lawCode,
                                         @Param("country") String country,
                                         @Param("kind") String kind,
                                         @Param("source") String source,
                                         @Param("extractedBy") String extractedBy,
                                         @Param("confidence") Double confidence);

    default UserDocument linkToLaw(String graphId, String documentId, String lawCode,
                                   String country, String kind) {
        return linkToLawWithProvenance(graphId, documentId, lawCode, country, kind,
                "UNKNOWN", null, null);
    }
}
