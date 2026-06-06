package com.jvmd.lawservice.service;

import com.jvmd.lawservice.model.kz.*;
import com.jvmd.lawservice.parser.KzLawParser;
import com.jvmd.lawservice.parser.KzLawNerParser;
import com.jvmd.lawservice.repository.ArticleRepository;
import com.jvmd.lawservice.repository.KzLegislativeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KzLawService {

    private final KzLegislativeDocumentRepository lawRepository;
    private final ArticleRepository articleRepository;
    private final KzLawParser kzLawParser;
    private final KzLawNerParser nerParser;

    @Transactional
    public KzLegislativeDocument importLegislativeDocument(
            String title,
            String documentNumber,
            String code,
            String fullText,
            LocalDate datePublished,
            String issuedBy
    ) {
        log.info("Importing legislative document: {} ({})", title, documentNumber);

        KzLegislativeDocument document = kzLawParser.detectDocumentType(title, fullText);
        document.setTitle(title);
        document.setCode(code);
        document.setDocumentNumber(documentNumber);
        document.setFullText(fullText);
        document.setDatePublished(datePublished);
        document.setIssuedBy(issuedBy);
        document.setIsActive(true);
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());

        kzLawParser.parseArticlesAndClauses(document, fullText);

        KzLegislativeDocument saved = lawRepository.save(document);
        log.info("Document imported successfully with {} articles", document.getArticles().size());

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<KzLegislativeDocument> findById(UUID id) {
        return lawRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<KzLegislativeDocument> findByCode(String code) {
        return lawRepository.findByCode(code);
    }

    @Transactional(readOnly = true)
    public Optional<KzLegislativeDocument> findByDocumentNumber(String documentNumber) {
        return lawRepository.findByDocumentNumber(documentNumber);
    }

    @Transactional(readOnly = true)
    public List<KzLegislativeDocument> findByType(KzLegislativeDocumentType type) {
        return lawRepository.findByDocumentTypeAndIsActiveTrue(type);
    }

    @Transactional(readOnly = true)
    public Page<KzLegislativeDocument> searchByTitle(String title, Pageable pageable) {
        return lawRepository.searchByTitle(title, pageable);
    }

    @Transactional(readOnly = true)
    public List<Article> getArticles(UUID legislativeDocumentId) {
        return articleRepository.findByLegislativeDocumentIdAndIsActiveTrue(legislativeDocumentId);
    }

    @Transactional(readOnly = true)
    public Optional<Article> getArticle(UUID legislativeDocumentId, Integer articleNumber) {
        return articleRepository.findByLegislativeDocumentIdAndArticleNumber(legislativeDocumentId, articleNumber);
    }

    @Transactional(readOnly = true)
    public Page<Article> searchArticles(String keyword, Pageable pageable) {
        return articleRepository.searchByKeyword(keyword, pageable);
    }

    @Transactional(readOnly = true)
    public List<KzLegislativeDocument> getAllActiveLaws() {
        return lawRepository.findAllActiveLaws();
    }

    @Transactional(readOnly = true)
    public List<KzLegislativeDocument> getAllActiveCodes() {
        return lawRepository.findAllActiveCodes();
    }

    @Transactional(readOnly = true)
    public long countArticles(UUID legislativeDocumentId) {
        return articleRepository.countActiveArticlesByLaw(legislativeDocumentId);
    }

    @Transactional
    public void markAsExpired(UUID documentId, LocalDate expirationDate) {
        lawRepository.findById(documentId).ifPresent(doc -> {
            doc.setDateExpired(expirationDate);
            doc.setIsActive(false);
            doc.setUpdatedAt(LocalDateTime.now());
            lawRepository.save(doc);
            log.info("Document {} marked as expired", documentId);
        });
    }

    @Transactional
    public KzLegislativeDocument createAmendment(UUID parentDocumentId, String title, String content) {
        Optional<KzLegislativeDocument> parent = lawRepository.findById(parentDocumentId);

        if (parent.isEmpty()) {
            throw new IllegalArgumentException("Parent document not found: " + parentDocumentId);
        }

        KzLegislativeDocument amendment = new SimpleLaw();
        amendment.setTitle(title);
        amendment.setFullText(content);
        amendment.setDocumentType(KzLegislativeDocumentType.AMENDMENT);
        amendment.setParentDocument(parent.get());
        amendment.setIsActive(true);
        amendment.setVersion(parent.get().getVersion() + 1);
        amendment.setCreatedAt(LocalDateTime.now());

        kzLawParser.parseArticlesAndClauses(amendment, content);
        KzLegislativeDocument saved = lawRepository.save(amendment);

        parent.get().addAmendment(saved);
        parent.get().setUpdatedAt(LocalDateTime.now());
        lawRepository.save(parent.get());

        return saved;
    }

    public KzLawNerParser.NerExtractionResult analyzeDocumentForReferences(String documentText) {
        return nerParser.extractNamedEntities(documentText);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDocumentHierarchy(UUID documentId) {
        return lawRepository.findById(documentId)
                .map(doc -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", doc.getId());
                    result.put("title", doc.getTitle());
                    result.put("type", doc.getHierarchyLevel());
                    result.put("code", doc.getCode() != null ? doc.getCode() : "");
                    result.put("articleCount", countArticles(documentId));
                    result.put("datePublished", doc.getDatePublished());
                    result.put("version", doc.getVersion());
                    result.put("isActive", doc.getIsActive());
                    return result;
                })
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
    }
}
