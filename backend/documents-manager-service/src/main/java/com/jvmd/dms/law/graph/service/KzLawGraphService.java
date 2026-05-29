package com.jvmd.dms.law.graph.service;

import com.jvmd.dms.law.graph.DocumentGraphRelationship;
import com.jvmd.dms.law.graph.KzArticleGraphNode;
import com.jvmd.dms.law.graph.KzLawGraphNode;
import com.jvmd.dms.law.graph.repository.KzArticleGraphRepository;
import com.jvmd.dms.law.graph.repository.KzLawGraphRepository;
import com.jvmd.dms.law.model.kz.Article;
import com.jvmd.dms.law.model.kz.KzLegislativeDocument;
import com.jvmd.dms.law.repository.ArticleRepository;
import com.jvmd.dms.law.repository.KzLegislativeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KzLawGraphService {

    private final KzLawGraphRepository lawGraphRepository;
    private final KzArticleGraphRepository articleGraphRepository;
    private final KzLegislativeDocumentRepository lawRepository;
    private final ArticleRepository articleRepository;

    @Transactional
    public void syncLawToGraph(UUID lawId) {
        lawRepository.findById(lawId).ifPresent(law -> {
            KzLawGraphNode graphNode = KzLawGraphNode.builder()
                    .uuid(law.getId())
                    .title(law.getTitle())
                    .code(law.getCode())
                    .documentType(law.getDocumentType().name())
                    .documentNumber(law.getDocumentNumber())
                    .datePublished(law.getDatePublished())
                    .issuedBy(law.getIssuedBy())
                    .isActive(law.getIsActive())
                    .version(law.getVersion())
                    .hierarchyLevel(law.getHierarchyLevel())
                    .articleCount((int) articleRepository.countActiveArticlesByLaw(lawId))
                    .build();

            lawGraphRepository.save(graphNode);
            log.info("Synced law to graph: {}", law.getTitle());

            syncArticlesToGraph(lawId);
        });
    }

    @Transactional
    public void syncArticlesToGraph(UUID lawId) {
        List<Article> articles = articleRepository.findByLegislativeDocumentIdAndIsActiveTrue(lawId);

        lawRepository.findById(lawId).ifPresent(law -> {
            for (Article article : articles) {
                KzArticleGraphNode graphArticle = KzArticleGraphNode.builder()
                        .uuid(article.getId())
                        .articleNumber(article.getArticleNumber())
                        .title(article.getTitle())
                        .lawCode(law.getCode())
                        .lawTitle(law.getTitle())
                        .isActive(article.getIsActive())
                        .version(article.getVersion())
                        .build();

                articleGraphRepository.save(graphArticle);
            }
            log.info("Synced {} articles to graph for law: {}", articles.size(), law.getTitle());
        });
    }

    @Transactional(readOnly = true)
    public Optional<KzLawGraphNode> getLawNode(UUID uuid) {
        return lawGraphRepository.findByUuid(uuid);
    }

    @Transactional(readOnly = true)
    public Optional<KzLawGraphNode> getLawNodeByCode(String code) {
        return lawGraphRepository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public List<KzLawGraphNode> searchLaws(String title) {
        return lawGraphRepository.searchByTitle(title);
    }

    @Transactional(readOnly = true)
    public List<KzLawGraphNode> getLawsByHierarchyLevel(String level) {
        return lawGraphRepository.findByHierarchyLevel(level);
    }

    @Transactional(readOnly = true)
    public List<KzLawGraphNode> getConstituationAndCodes() {
        List<KzLawGraphNode> constitution = lawGraphRepository.findByDocumentType("CONSTITUTION");
        List<KzLawGraphNode> codes = lawGraphRepository.findByDocumentType("CODE");
        constitution.addAll(codes);
        return constitution;
    }

    @Transactional(readOnly = true)
    public Long countActiveLaws() {
        return lawGraphRepository.countActiveLaws();
    }

    @Transactional(readOnly = true)
    public Optional<KzArticleGraphNode> getArticle(UUID uuid) {
        return articleGraphRepository.findByUuid(uuid);
    }

    @Transactional(readOnly = true)
    public Optional<KzArticleGraphNode> getArticleByNumber(String lawCode, Integer articleNumber) {
        return articleGraphRepository.findByLawCodeAndArticleNumber(lawCode, articleNumber);
    }

    @Transactional(readOnly = true)
    public List<KzArticleGraphNode> searchArticles(String keyword) {
        return articleGraphRepository.searchByKeyword(keyword);
    }

    @Transactional(readOnly = true)
    public List<KzArticleGraphNode> getArticlesByLawCode(String lawCode) {
        return articleGraphRepository.findByLawCode(lawCode);
    }

    public void createRelationship(DocumentGraphRelationship relationship) {
        log.info("Creating relationship {} between {} and {}",
                relationship.getRelationType(),
                relationship.getSourceId(),
                relationship.getTargetId());
    }

    @Transactional
    public void fullSync() {
        log.info("Starting full sync of laws to Neo4j graph...");
        List<KzLegislativeDocument> allLaws = lawRepository.findAll();

        for (KzLegislativeDocument law : allLaws) {
            syncLawToGraph(law.getId());
        }

        log.info("Full sync completed. Total laws: {}", allLaws.size());
    }
}
