package com.jvmd.dms.service;

import com.jvmd.dms.law.model.kz.Article;
import com.jvmd.dms.law.model.kz.KzLegislativeDocument;
import com.jvmd.dms.law.parser.KzLawNerParser;
import com.jvmd.dms.law.repository.ArticleRepository;
import com.jvmd.dms.law.repository.KzLegislativeDocumentRepository;
import java.util.*;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RelationshipMapperService {

    private final KzLegislativeDocumentRepository lawRepository;
    private final ArticleRepository articleRepository;
    private final KzLawNerParser nerParser;

    public RelationshipMappingResult mapDocumentToLaws(
        String documentText,
        UUID workspaceId
    ) {
        log.info("Mapping document relationships to KZ laws...");

        RelationshipMappingResult result = new RelationshipMappingResult();
        result.workspaceId = workspaceId;
        result.documentId = UUID.randomUUID();

        KzLawNerParser.NerExtractionResult nerResult =
            nerParser.extractNamedEntities(documentText);

        for (Object obj : nerResult.getArticleReferences()) {
            if (obj instanceof Number) {
                Integer articleNum = ((Number) obj).intValue();
                findAndMapArticles(articleNum, result);
            }
        }

        for (Object obj : nerResult.getLawNames()) {
            String name = obj.toString();
            findAndMapLaws(name, result);
        }

        result.totalRelationshipsFound =
            result.articleMappings.size() + result.lawMappings.size();
        result.message = String.format(
            "Found %d law references and %d law mappings",
            result.articleMappings.size(),
            result.lawMappings.size()
        );

        return result;
    }

    private void findAndMapArticles(
        Integer articleNumber,
        RelationshipMappingResult result
    ) {
        Optional<KzLegislativeDocument> constitution = lawRepository.findByCode(
            "КРК"
        );
        if (constitution.isPresent()) {
            KzLegislativeDocument law = constitution.get();
            Optional<Article> article =
                articleRepository.findByLegislativeDocumentIdAndArticleNumber(
                    law.getId(),
                    articleNumber
                );
            if (article.isPresent()) {
                result.articleMappings.add(
                    new ArticleMapping(
                        law.getId(),
                        law.getTitle(),
                        article.get().getId(),
                        articleNumber,
                        article.get().getTitle(),
                        "DIRECT_REFERENCE"
                    )
                );
            }
        }

        for (String code : new String[] { "УК", "ГК", "ПК", "НК", "ТК" }) {
            Optional<KzLegislativeDocument> codeDoc = lawRepository.findByCode(
                code
            );
            if (codeDoc.isPresent()) {
                KzLegislativeDocument codeEntity = codeDoc.get();
                Optional<Article> article =
                    articleRepository.findByLegislativeDocumentIdAndArticleNumber(
                        codeEntity.getId(),
                        articleNumber
                    );
                if (article.isPresent()) {
                    result.articleMappings.add(
                        new ArticleMapping(
                            codeEntity.getId(),
                            codeEntity.getTitle() +
                                " (" +
                                codeEntity.getCode() +
                                ")",
                            article.get().getId(),
                            articleNumber,
                            article.get().getTitle(),
                            "DIRECT_REFERENCE"
                        )
                    );
                }
            }
        }
    }

    private void findAndMapLaws(
        String lawName,
        RelationshipMappingResult result
    ) {
        Page<KzLegislativeDocument> searchResults = lawRepository.searchByTitle(
            lawName,
            PageRequest.of(0, 10)
        );

        for (KzLegislativeDocument law : searchResults) {
            result.lawMappings.add(
                new LawMapping(
                    law.getId(),
                    law.getTitle(),
                    law.getCode(),
                    law.getDocumentType().name(),
                    calculateSimilarity(lawName, law.getTitle()),
                    "TITLE_MATCH"
                )
            );
        }
    }

    private float calculateSimilarity(String s1, String s2) {
        if (s1.equalsIgnoreCase(s2)) return 1.0f;
        if (
            s1.toLowerCase().contains(s2.toLowerCase()) ||
            s2.toLowerCase().contains(s1.toLowerCase())
        ) {
            return 0.9f;
        }
        int distance = levenshteinDistance(s1.toLowerCase(), s2.toLowerCase());
        int maxLen = Math.max(s1.length(), s2.length());
        return 1.0f - ((float) distance / maxLen);
    }

    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] =
                        1 +
                        Math.min(
                            Math.min(dp[i - 1][j], dp[i][j - 1]),
                            dp[i - 1][j - 1]
                        );
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    public static class RelationshipMappingResult {

        public UUID workspaceId;
        public UUID documentId;
        public List<ArticleMapping> articleMappings = new ArrayList<>();
        public List<LawMapping> lawMappings = new ArrayList<>();
        public int totalRelationshipsFound;
        public String message;
    }

    public static class ArticleMapping {

        public UUID lawId;
        public String lawTitle;
        public UUID articleId;
        public Integer articleNumber;
        public String articleTitle;
        public String relationType;

        public ArticleMapping(
            UUID lawId,
            String lawTitle,
            UUID articleId,
            Integer articleNumber,
            String articleTitle,
            String relationType
        ) {
            this.lawId = lawId;
            this.lawTitle = lawTitle;
            this.articleId = articleId;
            this.articleNumber = articleNumber;
            this.articleTitle = articleTitle;
            this.relationType = relationType;
        }
    }

    public static class LawMapping {

        public UUID lawId;
        public String lawTitle;
        public String lawCode;
        public String docType;
        public Float similarity;
        public String mappingType;

        public LawMapping(
            UUID lawId,
            String lawTitle,
            String lawCode,
            String docType,
            Float similarity,
            String mappingType
        ) {
            this.lawId = lawId;
            this.lawTitle = lawTitle;
            this.lawCode = lawCode;
            this.docType = docType;
            this.similarity = similarity;
            this.mappingType = mappingType;
        }
    }
}
