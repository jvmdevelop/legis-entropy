package com.jvmd.graphsservice.repository;

import com.jvmd.graphsservice.model.Article;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArticleRepository extends Neo4jRepository<Article, Long> {

    @Query("""
            MATCH (a:Article {lawCode: $code, country: $country, number: $number})
            RETURN a LIMIT 1
            """)
    Optional<Article> findByLawCodeAndNumber(@Param("code") String code,
                                             @Param("country") String country,
                                             @Param("number") String number);

    @Query("""
            MATCH (a:Article {lawCode: $code, country: $country})
            RETURN DISTINCT a
            ORDER BY a.number
            """)
    List<Article> findAllByLawCode(@Param("code") String code, @Param("country") String country);

    @Query("""
            MATCH (a:Article)
            WHERE a.country = $country
              AND (a.title CONTAINS $query OR a.body CONTAINS $query OR a.number CONTAINS $query)
            RETURN a LIMIT 20
            """)
    List<Article> search(@Param("query") String query, @Param("country") String country);

    @Query("""
            MATCH (l:Law {code: $code, country: $country})
            WITH l
            MATCH (a:Article {lawCode: $code, country: $country, number: $number})
            MERGE (l)-[:HAS_ARTICLE]->(a)
            RETURN a
            """)
    Optional<Article> attachToLaw(@Param("code") String code,
                                  @Param("country") String country,
                                  @Param("number") String number);

    @Query("""
            MERGE (a:Article {lawCode: $lawCode, country: $country, number: $number})
              ON CREATE SET a.title = $title,
                            a.body = $body,
                            a.source = $source,
                            a.extractedBy = $extractedBy,
                            a.extractedAt = localdatetime(),
                            a.confidence = $confidence,
                            a.sourceUri = $sourceUri,
                            a.recordedAt = localdatetime()
              ON MATCH SET a.title = coalesce($title, a.title),
                           a.body = coalesce($body, a.body),
                           a.sourceUri = coalesce($sourceUri, a.sourceUri),
                           a.supersededAt = localdatetime()
            WITH a
            OPTIONAL MATCH (l:Law {code: $lawCode, country: $country})
            WITH a, collect(DISTINCT l) AS laws
            FOREACH (law IN laws | MERGE (law)-[:HAS_ARTICLE]->(a))
            """)
    void upsertArticle(@Param("lawCode") String lawCode,
                       @Param("country") String country,
                       @Param("number") String number,
                       @Param("title") String title,
                       @Param("body") String body,
                       @Param("source") String source,
                       @Param("extractedBy") String extractedBy,
                       @Param("confidence") Double confidence,
                       @Param("sourceUri") String sourceUri);

    @Query("""
            MERGE (a:Article {lawCode: $lawCode, country: $country, number: $number})
              ON CREATE SET a.title = $title,
                            a.body = $body,
                            a.anchor = $anchor,
                            a.partTitle = $partTitle,
                            a.sectionNumber = $sectionNumber,
                            a.sectionTitle = $sectionTitle,
                            a.chapterNumber = $chapterNumber,
                            a.chapterTitle = $chapterTitle,
                            a.lastAmendmentDate = $lastAmendmentDate,
                            a.source = $source,
                            a.extractedBy = $extractedBy,
                            a.extractedAt = localdatetime(),
                            a.confidence = $confidence,
                            a.sourceUri = $sourceUri,
                            a.recordedAt = localdatetime()
              ON MATCH SET a.title = coalesce($title, a.title),
                           a.body = coalesce($body, a.body),
                           a.anchor = coalesce($anchor, a.anchor),
                           a.partTitle = coalesce($partTitle, a.partTitle),
                           a.sectionNumber = coalesce($sectionNumber, a.sectionNumber),
                           a.sectionTitle = coalesce($sectionTitle, a.sectionTitle),
                           a.chapterNumber = coalesce($chapterNumber, a.chapterNumber),
                           a.chapterTitle = coalesce($chapterTitle, a.chapterTitle),
                           a.lastAmendmentDate = coalesce($lastAmendmentDate, a.lastAmendmentDate),
                           a.sourceUri = coalesce($sourceUri, a.sourceUri),
                           a.supersededAt = localdatetime()
            WITH a
            OPTIONAL MATCH (l:Law {code: $lawCode, country: $country})
            WITH a, collect(DISTINCT l) AS laws
            FOREACH (law IN laws | MERGE (law)-[:HAS_ARTICLE]->(a))
            """)
    void upsertArticleEnriched(@Param("lawCode") String lawCode,
                               @Param("country") String country,
                               @Param("number") String number,
                               @Param("title") String title,
                               @Param("body") String body,
                               @Param("anchor") String anchor,
                               @Param("partTitle") String partTitle,
                               @Param("sectionNumber") String sectionNumber,
                               @Param("sectionTitle") String sectionTitle,
                               @Param("chapterNumber") String chapterNumber,
                               @Param("chapterTitle") String chapterTitle,
                               @Param("lastAmendmentDate") java.time.LocalDate lastAmendmentDate,
                               @Param("source") String source,
                               @Param("extractedBy") String extractedBy,
                               @Param("confidence") Double confidence,
                               @Param("sourceUri") String sourceUri);

    @Query("""
            MATCH (a:Article {lawCode: $lawCode, country: $country, number: $articleNumber})
            MERGE (byLaw:Law {sourceUri: $bySourceUri, country: $country})
              ON CREATE SET byLaw.code = coalesce(byLaw.code, $byInternalNumber),
                            byLaw.internalNumber = coalesce(byLaw.internalNumber, $byInternalNumber),
                            byLaw.recordedAt = localdatetime(),
                            byLaw.source = 'GOV_PARSER',
                            byLaw.extractedBy = 'adilet-dom-extractor',
                            byLaw.confidence = 0.85
            MERGE (a)-[r:AMENDED_BY {sourceUri: $bySourceUri}]->(byLaw)
              ON CREATE SET r.amendmentDate = $amendmentDate,
                            r.effectiveFrom = $effectiveFrom,
                            r.anchor = $anchor,
                            r.contextSnippet = $contextSnippet,
                            r.extractedAt = localdatetime()
              ON MATCH SET r.amendmentDate = coalesce($amendmentDate, r.amendmentDate),
                           r.effectiveFrom = coalesce($effectiveFrom, r.effectiveFrom),
                           r.anchor = coalesce($anchor, r.anchor)
            """)
    void upsertAmendedBy(@Param("lawCode") String lawCode,
                         @Param("country") String country,
                         @Param("articleNumber") String articleNumber,
                         @Param("bySourceUri") String bySourceUri,
                         @Param("byInternalNumber") String byInternalNumber,
                         @Param("amendmentDate") java.time.LocalDate amendmentDate,
                         @Param("effectiveFrom") java.time.LocalDate effectiveFrom,
                         @Param("anchor") String anchor,
                         @Param("contextSnippet") String contextSnippet);

    @Query("""
            MATCH (from:Article {lawCode: $fromLawCode, country: $country, number: $fromNumber})
            MERGE (toLaw:Law {sourceUri: $toSourceUri, country: $country})
              ON CREATE SET toLaw.recordedAt = localdatetime(),
                            toLaw.source = 'GOV_PARSER',
                            toLaw.extractedBy = 'adilet-dom-extractor',
                            toLaw.confidence = 0.7
            MERGE (from)-[r:CITES_LAW {anchor: coalesce($toAnchor, '')}]->(toLaw)
              ON CREATE SET r.contextSnippet = $contextSnippet,
                            r.extractedAt = localdatetime()
            """)
    void upsertCrossRefLaw(@Param("fromLawCode") String fromLawCode,
                          @Param("country") String country,
                          @Param("fromNumber") String fromNumber,
                          @Param("toSourceUri") String toSourceUri,
                          @Param("toAnchor") String toAnchor,
                          @Param("contextSnippet") String contextSnippet);

    @Query("""
            MATCH (d:UserDocument {id: $documentId})
            WITH d
            MATCH (a:Article {lawCode: $lawCode, country: $country, number: $number})
            WITH d, a LIMIT 1
            MERGE (d)-[r:CITES {graphId: $graphId}]->(a)
              ON CREATE SET r.clauseRef = $clauseRef,
                            r.documentSnippet = $documentSnippet,
                            r.articleSnippet = $articleSnippet,
                            r.source = $source,
                            r.extractedBy = $extractedBy,
                            r.extractedAt = localdatetime(),
                            r.confidence = $confidence
              ON MATCH SET r.clauseRef = $clauseRef,
                           r.documentSnippet = $documentSnippet,
                           r.articleSnippet = $articleSnippet,
                           r.confidence = $confidence
            RETURN a.lawCode + ':' + a.number AS articleKey
            """)
    Optional<String> linkDocumentClauseToArticle(
            @Param("graphId") String graphId,
            @Param("documentId") String documentId,
            @Param("lawCode") String lawCode,
            @Param("country") String country,
            @Param("number") String number,
            @Param("clauseRef") String clauseRef,
            @Param("documentSnippet") String documentSnippet,
            @Param("articleSnippet") String articleSnippet,
            @Param("source") String source,
            @Param("extractedBy") String extractedBy,
            @Param("confidence") Double confidence);
}
