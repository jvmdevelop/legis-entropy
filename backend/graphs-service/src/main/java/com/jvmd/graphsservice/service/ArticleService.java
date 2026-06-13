package com.jvmd.graphsservice.service;

import com.jvmd.graphsservice.client.DmsArticleClient;
import com.jvmd.graphsservice.dto.AmendmentEdgeDTO;
import com.jvmd.graphsservice.dto.ArticleDTO;
import com.jvmd.graphsservice.dto.ArticleHistoryDTO;
import com.jvmd.graphsservice.dto.CreateArticleRequest;
import com.jvmd.graphsservice.dto.LinkDocumentToArticleRequest;
import com.jvmd.graphsservice.model.Article;
import com.jvmd.graphsservice.model.Provenance;
import com.jvmd.graphsservice.model.ProvenanceSource;
import com.jvmd.graphsservice.repository.ArticleRepository;
import com.jvmd.graphsservice.repository.LawRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final LawRepository lawRepository;
    private final com.jvmd.graphsservice.parser.ArticleExtractor articleExtractor;
    private final Neo4jClient neo4jClient;

    @Autowired(required = false)
    private DmsArticleClient dmsArticleClient;

    @Cacheable(value = "articlesByLaw", key = "#country + ':' + #lawCode")
    public List<ArticleDTO> listByLaw(String lawCode, String country) {
        return articleRepository
            .findAllByLawCode(lawCode, country)
            .stream()
            .map(ArticleDTO::from)
            .toList();
    }

    @Cacheable(value = "article", key = "#country + ':' + #lawCode + ':' + #number")
    public Optional<ArticleDTO> get(
        String lawCode,
        String country,
        String number
    ) {
        return articleRepository
            .findByLawCodeAndNumber(lawCode, country, number)
            .map(ArticleDTO::from);
    }

    @Cacheable(value = "articleSearch", key = "#country + ':' + #query")
    public List<ArticleDTO> search(String query, String country) {
        return articleRepository
            .search(query, country)
            .stream()
            .map(ArticleDTO::from)
            .toList();
    }

    public Optional<ArticleHistoryDTO> history(
        String code,
        String country,
        String number
    ) {
        var articleOpt = articleRepository.findByLawCodeAndNumber(
            code,
            country,
            number
        );
        if (articleOpt.isEmpty()) return Optional.empty();
        ArticleDTO dto = ArticleDTO.from(articleOpt.get());

        String cypher = """
            MATCH (a:Article {lawCode: $code, country: $country, number: $number})-[r:AMENDED_BY]->(byLaw:Law)
            RETURN r.amendmentDate AS amendmentDate,
                   r.effectiveFrom AS effectiveFrom,
                   r.anchor AS anchor,
                   r.contextSnippet AS contextSnippet,
                   byLaw.code AS byLawCode,
                   byLaw.title AS byLawTitle,
                   byLaw.sourceUri AS byLawSourceUri,
                   byLaw.internalNumber AS byInternalNumber
            """;

        List<AmendmentEdgeDTO> amendments = new ArrayList<>(
            neo4jClient
                .query(cypher)
                .bindAll(
                    Map.of("code", code, "country", country, "number", number)
                )
                .fetchAs(AmendmentEdgeDTO.class)
                .mappedBy((typeSystem, record) ->
                    AmendmentEdgeDTO.builder()
                        .amendmentDate(
                            record.get("amendmentDate").isNull()
                                ? null
                                : record.get("amendmentDate").asLocalDate()
                        )
                        .effectiveFrom(
                            record.get("effectiveFrom").isNull()
                                ? null
                                : record.get("effectiveFrom").asLocalDate()
                        )
                        .anchor(
                            record.get("anchor").isNull()
                                ? null
                                : record.get("anchor").asString()
                        )
                        .contextSnippet(
                            record.get("contextSnippet").isNull()
                                ? null
                                : record.get("contextSnippet").asString()
                        )
                        .byLawCode(
                            record.get("byLawCode").isNull()
                                ? null
                                : record.get("byLawCode").asString()
                        )
                        .byLawTitle(
                            record.get("byLawTitle").isNull()
                                ? null
                                : record.get("byLawTitle").asString()
                        )
                        .byLawSourceUri(
                            record.get("byLawSourceUri").isNull()
                                ? null
                                : record.get("byLawSourceUri").asString()
                        )
                        .byInternalNumber(
                            record.get("byInternalNumber").isNull()
                                ? null
                                : record.get("byInternalNumber").asString()
                        )
                        .build()
                )
                .all()
        );

        amendments.sort(
            Comparator.comparing(
                (AmendmentEdgeDTO a) -> bestDate(a),
                Comparator.nullsLast(Comparator.reverseOrder())
            )
        );

        return Optional.of(
            ArticleHistoryDTO.builder()
                .article(dto)
                .amendments(amendments)
                .lastAmendmentDate(dto.getLastAmendmentDate())
                .build()
        );
    }

    private static LocalDate bestDate(AmendmentEdgeDTO a) {
        return a.getAmendmentDate() != null
            ? a.getAmendmentDate()
            : a.getEffectiveFrom();
    }

    @CacheEvict(value = {"articlesByLaw", "article", "articleSearch"}, allEntries = true)
    public ArticleDTO create(CreateArticleRequest req) {
        ProvenanceSource src =
            req.getSource() != null
                ? req.getSource()
                : ProvenanceSource.UNKNOWN;
        articleRepository.upsertArticle(
            req.getLawCode(),
            req.getCountry(),
            req.getNumber(),
            req.getTitle(),
            req.getBody(),
            src.name(),
            req.getExtractedBy(),
            req.getConfidence(),
            req.getSourceUri()
        );
        log.info(
            "Upserted article {} of {} ({})",
            req.getNumber(),
            req.getLawCode(),
            req.getCountry()
        );
        return get(
            req.getLawCode(),
            req.getCountry(),
            req.getNumber()
        ).orElseGet(() ->
            ArticleDTO.builder()
                .lawCode(req.getLawCode())
                .country(req.getCountry())
                .number(req.getNumber())
                .title(req.getTitle())
                .body(req.getBody())
                .source(src)
                .extractedBy(req.getExtractedBy())
                .extractedAt(LocalDateTime.now())
                .confidence(req.getConfidence())
                .sourceUri(req.getSourceUri())
                .build()
        );
    }

    public int backfillForLaw(String code, String country) {
        var lawOpt = lawRepository.findByCodeAndCountry(code, country);
        if (lawOpt.isEmpty()) {
            log.warn("Backfill: law {} ({}) not found", code, country);
            return 0;
        }
        var law = lawOpt.get();
        if (law.getSummary() == null || law.getSummary().isBlank()) {
            log.info("Backfill: law {} has no body, nothing to extract", code);
            return 0;
        }
        var articles = articleExtractor.extract(
            code,
            country,
            law.getSummary(),
            law.getSourceUri()
        );
        return persistAll(articles);
    }

    public int backfillAll(String country) {
        List<com.jvmd.graphsservice.model.Law> laws =
            new java.util.ArrayList<>();
        if (country != null) {
            laws.addAll(lawRepository.findByCountry(country));
        } else {
            lawRepository.findAll().forEach(laws::add);
        }
        int total = 0;
        for (var law : laws) {
            if (law.getCode() == null || law.getSummary() == null) continue;
            var arts = articleExtractor.extract(
                law.getCode(),
                law.getCountry(),
                law.getSummary(),
                law.getSourceUri()
            );
            total += persistAll(arts);
        }
        log.info(
            "Backfill complete: {} articles persisted across {} laws (country={})",
            total,
            laws.size(),
            country == null ? "ALL" : country
        );
        return total;
    }

    @CacheEvict(value = {"articlesByLaw", "article", "articleSearch"}, allEntries = true)
    public int persistEnriched(List<Article> articles) {
        if (articles == null || articles.isEmpty()) return 0;
        int saved = 0;
        for (Article a : articles) {
            try {
                articleRepository.upsertArticleEnriched(
                    a.getLawCode(),
                    a.getCountry(),
                    a.getNumber(),
                    a.getTitle(),
                    a.getBody(),
                    a.getAnchor(),
                    a.getPartTitle(),
                    a.getSectionNumber(),
                    a.getSectionTitle(),
                    a.getChapterNumber(),
                    a.getChapterTitle(),
                    a.getLastAmendmentDate(),
                    a.getSource() != null
                        ? a.getSource().name()
                        : ProvenanceSource.UNKNOWN.name(),
                    a.getExtractedBy(),
                    a.getConfidence(),
                    a.getSourceUri()
                );
                saved++;
            } catch (Exception e) {
                log.warn(
                    "Failed to upsert enriched article {} of {}: {}",
                    a.getNumber(),
                    a.getLawCode(),
                    e.getMessage()
                );
            }
        }
        log.info(
            "Persisted {}/{} enriched articles for law {}",
            saved,
            articles.size(),
            articles.get(0).getLawCode()
        );

        if (dmsArticleClient != null && saved > 0) {
            List<DmsArticleClient.ArticleIndexRequest> batch = articles
                .stream()
                .filter(a -> a.getBody() != null && !a.getBody().isBlank())
                .map(a ->
                    new DmsArticleClient.ArticleIndexRequest(
                        a.getLawCode(),
                        a.getCountry(),
                        a.getNumber(),
                        a.getTitle(),
                        a.getBody()
                    )
                )
                .toList();
            dmsArticleClient.indexBatch(batch);
        }

        return saved;
    }

    public int persistAmendments(
        String lawCode,
        String country,
        List<
            com.jvmd.graphsservice.parser.LawStructure.AmendmentEdge
        > amendments
    ) {
        if (amendments == null || amendments.isEmpty()) return 0;
        int saved = 0;
        for (var am : amendments) {
            if (am.byDocId() == null) continue;
            String byUri = "https://adilet.zan.kz/rus/docs/" + am.byDocId();
            try {
                articleRepository.upsertAmendedBy(
                    lawCode,
                    country,
                    am.articleNumber(),
                    byUri,
                    am.byInternalNumber(),
                    am.amendmentDate(),
                    am.effectiveFrom(),
                    am.anchor(),
                    am.contextSnippet()
                );
                saved++;
            } catch (Exception e) {
                log.warn(
                    "Failed to persist amendment for {} ст. {} by {}: {}",
                    lawCode,
                    am.articleNumber(),
                    am.byInternalNumber(),
                    e.getMessage()
                );
            }
        }
        log.info(
            "Persisted {}/{} amendment edges for law {}",
            saved,
            amendments.size(),
            lawCode
        );
        return saved;
    }

    public int persistCrossRefs(
        String fromLawCode,
        String country,
        List<com.jvmd.graphsservice.parser.LawStructure.CrossRefEdge> refs
    ) {
        if (refs == null || refs.isEmpty()) return 0;
        int saved = 0;
        for (var ref : refs) {
            if (ref.toDocId() == null) continue;
            String toUri = "https://adilet.zan.kz/rus/docs/" + ref.toDocId();
            try {
                articleRepository.upsertCrossRefLaw(
                    fromLawCode,
                    country,
                    ref.fromArticleNumber(),
                    toUri,
                    ref.toAnchor(),
                    ref.contextSnippet()
                );
                saved++;
            } catch (Exception e) {
                log.warn(
                    "Failed to persist cross-ref for {} ст. {} → {}: {}",
                    fromLawCode,
                    ref.fromArticleNumber(),
                    ref.toDocId(),
                    e.getMessage()
                );
            }
        }
        log.info(
            "Persisted {}/{} cross-references for law {}",
            saved,
            refs.size(),
            fromLawCode
        );
        return saved;
    }

    @CacheEvict(value = {"articlesByLaw", "article", "articleSearch"}, allEntries = true)
    public int persistAll(List<Article> articles) {
        if (articles == null || articles.isEmpty()) return 0;
        int saved = 0;
        for (Article a : articles) {
            try {
                articleRepository.upsertArticle(
                    a.getLawCode(),
                    a.getCountry(),
                    a.getNumber(),
                    a.getTitle(),
                    a.getBody(),
                    a.getSource() != null
                        ? a.getSource().name()
                        : ProvenanceSource.UNKNOWN.name(),
                    a.getExtractedBy(),
                    a.getConfidence(),
                    a.getSourceUri()
                );
                saved++;
            } catch (Exception e) {
                log.warn(
                    "Failed to upsert article {} of {}: {}",
                    a.getNumber(),
                    a.getLawCode(),
                    e.getMessage()
                );
            }
        }
        log.info(
            "Persisted {}/{} articles for law {}",
            saved,
            articles.size(),
            articles.get(0).getLawCode()
        );
        return saved;
    }

    public Optional<ArticleDTO> linkDocumentClause(
        LinkDocumentToArticleRequest req
    ) {
        var linked = articleRepository.linkDocumentClauseToArticle(
            req.getGraphId(),
            req.getDocumentId(),
            req.getLawCode(),
            req.getCountry(),
            req.getArticleNumber(),
            req.getClauseRef(),
            req.getDocumentSnippet(),
            req.getArticleSnippet(),
            ProvenanceSource.LLM_INFERENCE.name(),
            req.getExtractedBy(),
            req.getConfidence()
        );
        if (linked.isEmpty()) return Optional.empty();
        return get(req.getLawCode(), req.getCountry(), req.getArticleNumber());
    }
}
