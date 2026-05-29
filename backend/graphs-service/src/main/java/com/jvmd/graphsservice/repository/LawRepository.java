package com.jvmd.graphsservice.repository;

import com.jvmd.graphsservice.model.Law;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LawRepository extends Neo4jRepository<Law, Long> {

    @Query("MATCH (l:Law {code: $code, country: $country}) RETURN l LIMIT 1")
    Optional<Law> findByCodeAndCountry(@Param("code") String code, @Param("country") String country);

    @Query("MATCH (l:Law) RETURN l")
    List<Law> findAllShallow();

    List<Law> findByCountry(String country);

    @Query("""
            MATCH (l:Law {country: $country})
            WHERE l.sourceUri IS NOT NULL AND l.sourceUri <> ''
            RETURN l.sourceUri
            """)
    List<String> findSourceUrisByCountry(@Param("country") String country);

    @Query("MATCH (l:Law {sourceUri: $sourceUri, country: $country}) RETURN l LIMIT 1")
    Optional<Law> findBySourceUriAndCountry(@Param("sourceUri") String sourceUri, @Param("country") String country);

    List<Law> findByType(String type);

    @Query("MATCH (l:Law {code: $code, country: $country}) RETURN l LIMIT 1")
    Optional<Law> findLawByCodeAndCountry(@Param("code") String code, @Param("country") String country);

    @Query("""
            MATCH (l:Law {code: $code, country: $country})
            WITH l LIMIT 1
            OPTIONAL MATCH (l)-[r:REFERENCES]->(ref:Law)
            OPTIONAL MATCH (l)-[a:AMENDS]->(amended:Law)
            OPTIONAL MATCH (l)-[d:DEFINES]->(defined:Law)
            OPTIONAL MATCH (l)-[rel:RELATED]->(related:Law)
            OPTIONAL MATCH (l)-[s:SUPERSEDES]->(superseded:Law)
            OPTIONAL MATCH (l)-[i:IMPLEMENTS]->(impl:Law)
            RETURN l, collect(ref) as references, collect(amended) as amends,
                   collect(defined) as defines, collect(related) as related,
                   collect(superseded) as supersedes, collect(impl) as implements
            """)
    Optional<Law> findLawWithRelationships(@Param("code") String code, @Param("country") String country);

    @Query("""
            MATCH path = (start:Law {code: $startCode, country: $country})-[*1..3]->(end:Law)
            RETURN distinct end
            ORDER BY length(path)
            LIMIT 20
            """)
    List<Law> findRelatedLaws(@Param("startCode") String startCode, @Param("country") String country);

    @Query("""
            MATCH (l:Law)
            WHERE l.country = $country
              AND (toLower(coalesce(l.title, '')) CONTAINS toLower($query)
                OR toLower(coalesce(l.code, '')) CONTAINS toLower($query)
                OR toLower(coalesce(l.citationCode, '')) CONTAINS toLower($query)
                OR toLower(coalesce(l.internalNumber, '')) CONTAINS toLower($query)
                OR toLower(coalesce(l.registryNumber, '')) CONTAINS toLower($query)
                OR toLower(coalesce(l.subjectArea, '')) CONTAINS toLower($query)
                OR toLower(coalesce(l.summary, '')) CONTAINS toLower($query))
            RETURN DISTINCT l
            ORDER BY
              CASE WHEN toLower(coalesce(l.citationCode, '')) CONTAINS toLower($query) THEN 0
                   WHEN toLower(coalesce(l.title, '')) CONTAINS toLower($query) THEN 1
                   ELSE 2 END
            LIMIT 10
            """)
    List<Law> searchLaws(@Param("query") String query, @Param("country") String country);

    @Query("""
            MATCH (l:Law)-[r:REFERENCES|AMENDS|DEFINES|RELATED|SUPERSEDES|IMPLEMENTS]->(related:Law)
            WHERE l.code = $code AND l.country = $country
            RETURN type(r) as type, collect(related) as laws
            """)
    List<Object> findAllRelationshipsByLaw(@Param("code") String code, @Param("country") String country);

    @Query("""
            MATCH (l:Law {country: $country})
            WHERE l.status IN ['EXPIRED', 'INVALID', 'PARSE_ERROR']
            WITH collect(l) AS expiredLaws
            UNWIND expiredLaws AS l
            OPTIONAL MATCH (l)-[:HAS_ARTICLE]->(a:Article)
            WITH expiredLaws, collect(DISTINCT a) AS candidateArticles
            UNWIND candidateArticles AS a
            OPTIONAL MATCH (otherLaw:Law)-[:HAS_ARTICLE]->(a)
            WHERE NOT otherLaw IN expiredLaws
            WITH expiredLaws, a, count(otherLaw) AS keepers
            WITH expiredLaws, collect(CASE WHEN keepers = 0 THEN a END) AS orphanedArticles
            FOREACH (a IN [x IN orphanedArticles WHERE x IS NOT NULL] | DETACH DELETE a)
            WITH expiredLaws
            UNWIND expiredLaws AS l
            DETACH DELETE l
            RETURN count(*) AS removed
            """)
    Long purgeExpiredByCountry(@Param("country") String country);
}
