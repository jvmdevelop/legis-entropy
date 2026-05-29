package com.jvmd.llmbrainservice.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class GraphServiceClient {

    private final RestTemplate restTemplate;
    private final String graphServiceUrl;

    public GraphServiceClient(RestTemplate restTemplate,
                             @Value("${graph.service.url:http://localhost:8086}") String graphServiceUrl) {
        this.restTemplate = restTemplate;
        this.graphServiceUrl = graphServiceUrl;
    }

    public Optional<LawGraphResponse> getLawWithRelationships(String code, String country) {
        try {
            String url = String.format("%s/api/graph/laws/%s?country=%s", graphServiceUrl, code, country);
            LawGraphResponse response = restTemplate.getForObject(url, LawGraphResponse.class);
            log.info("Successfully retrieved law graph for {} {}", code, country);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            log.warn("Failed to retrieve law graph for {} {}: {}", code, country, e.getMessage());
            return Optional.empty();
        }
    }

    public List<LawInfo> findRelatedLaws(String code, String country) {
        try {
            String url = String.format("%s/api/graph/laws/%s/related?country=%s", graphServiceUrl, code, country);
            LawInfo[] laws = restTemplate.getForObject(url, LawInfo[].class);
            log.info("Found {} related laws for {} {}", laws != null ? laws.length : 0, code, country);
            return laws != null ? List.of(laws) : new ArrayList<>();
        } catch (RestClientException e) {
            log.warn("Failed to find related laws for {} {}: {}", code, country, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<LawInfo> searchLaws(String query, String country) {
        try {
            String url = String.format("%s/api/graph/laws/search?query=%s&country=%s",
                    graphServiceUrl, query, country);
            LawInfo[] laws = restTemplate.getForObject(url, LawInfo[].class);
            log.info("Found {} laws matching '{}' in {}", laws != null ? laws.length : 0, query, country);
            return laws != null ? List.of(laws) : new ArrayList<>();
        } catch (RestClientException e) {
            log.warn("Failed to search laws: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public boolean addLawToUserGraph(String graphId, String code, String country) {
        try {
            String url = String.format("%s/api/v1/user-graphs/%s/laws/%s?country=%s",
                    graphServiceUrl, graphId, code, country);
            restTemplate.postForObject(url, null, Void.class);
            log.info("Added law {} to user graph {}", code, graphId);
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to add law {} to user graph {}: {}", code, graphId, e.getMessage());
            return false;
        }
    }

    public boolean linkDocumentToLaw(String graphId, String documentId, String lawCode, String country, String kind) {
        try {
            String url = String.format("%s/api/v1/user-graphs/%s/documents/%s/links/%s?country=%s&kind=%s",
                    graphServiceUrl, graphId, documentId, lawCode, country, kind);
            restTemplate.postForObject(url, null, Void.class);
            log.info("Linked document {} to law {} in graph {}", documentId, lawCode, graphId);
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to link document {} to law {} in graph {}: {}", documentId, lawCode, graphId, e.getMessage());
            return false;
        }
    }

    public boolean linkSituationToLaw(String graphId, String situationId, String lawCode, String country, String kind) {
        try {
            String url = String.format("%s/api/v1/user-graphs/%s/situations/%s/laws/%s?country=%s&kind=%s",
                    graphServiceUrl, graphId, situationId, lawCode, country, kind);
            restTemplate.postForObject(url, null, Void.class);
            log.info("Linked situation {} to law {} in graph {}", situationId, lawCode, graphId);
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to link situation {} to law {}: {}", situationId, lawCode, e.getMessage());
            return false;
        }
    }

    public boolean linkVoiceEvidenceToLaw(String graphId, String voiceId, String lawCode, String country, String reason) {
        try {
            String safeReason = reason == null ? "" : reason;
            String url = String.format("%s/api/v1/user-graphs/%s/voice-evidence/%s/laws/%s?country=%s&reason=%s",
                    graphServiceUrl, graphId, voiceId, lawCode, country,
                    java.net.URLEncoder.encode(safeReason, java.nio.charset.StandardCharsets.UTF_8));
            restTemplate.postForObject(url, null, Void.class);
            log.info("Linked voice {} to law {} in graph {}", voiceId, lawCode, graphId);
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to link voice {} to law {}: {}", voiceId, lawCode, e.getMessage());
            return false;
        }
    }

    public Optional<String> createSituation(String graphId, String userId, String title, String plainText) {
        try {
            String url = String.format("%s/api/v1/user-graphs/%s/situations", graphServiceUrl, graphId);
            java.util.Map<String, String> body = new java.util.HashMap<>();
            String safeText = plainText != null ? plainText : "";
            body.put("title", title != null ? title : "Ситуация");
            body.put("body", safeText);
            body.put("plainText", safeText);
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            headers.set("X-User-Id", userId != null ? userId : "");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.postForObject(
                    url, new org.springframework.http.HttpEntity<>(body, headers), java.util.Map.class);
            if (response == null) return Optional.empty();
            Object id = response.get("id");
            log.info("Created situation '{}' in graph {} for user {}", title, graphId, userId);
            return id != null ? Optional.of(id.toString()) : Optional.empty();
        } catch (RestClientException e) {
            log.warn("Failed to create situation in graph {}: {}", graphId, e.getMessage());
            return Optional.empty();
        }
    }

    public boolean updateSituationGeneratedDoc(String graphId, String situationId, String docId, String docTitle) {
        try {
            String url = String.format("%s/api/v1/user-graphs/%s/situations/%s/generated-doc",
                    graphServiceUrl, graphId, situationId);
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("docId", docId);
            body.put("docTitle", docTitle != null ? docTitle : "");
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            restTemplate.postForObject(url, new org.springframework.http.HttpEntity<>(body, headers), Void.class);
            log.info("Updated situation {} generatedDocId={} (graph {})", situationId, docId, graphId);
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to update generated-doc on situation {}: {}", situationId, e.getMessage());
            return false;
        }
    }

    public Optional<java.util.Map<String, Object>> getSituation(String graphId, String situationId) {
        try {
            String url = String.format("%s/api/v1/user-graphs/%s/situations/%s",
                    graphServiceUrl, graphId, situationId);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> response = restTemplate.getForObject(url, java.util.Map.class);
            return Optional.ofNullable(response);
        } catch (RestClientException e) {
            log.warn("Could not fetch situation {} in graph {}: {}", situationId, graphId, e.getMessage());
            return Optional.empty();
        }
    }

    public List<ArticleInfo> listArticlesByLaw(String code, String country) {
        try {
            String url = String.format("%s/api/graph/laws/%s/articles?country=%s",
                    graphServiceUrl, code, country);
            ArticleInfo[] articles = restTemplate.getForObject(url, ArticleInfo[].class);
            return articles != null ? List.of(articles) : new ArrayList<>();
        } catch (RestClientException e) {
            log.warn("Failed to list articles for {} {}: {}", code, country, e.getMessage());
            return new ArrayList<>();
        }
    }

    public Optional<ArticleInfo> getArticle(String code, String number, String country) {
        try {
            String url = String.format("%s/api/graph/laws/%s/articles/%s?country=%s",
                    graphServiceUrl, code, number, country);
            return Optional.ofNullable(restTemplate.getForObject(url, ArticleInfo.class));
        } catch (RestClientException e) {
            log.warn("Failed to get article {} of {} {}: {}", number, code, country, e.getMessage());
            return Optional.empty();
        }
    }

    public List<ArticleInfo> searchArticles(String query, String country) {
        try {
            String url = String.format("%s/api/graph/articles/search?query=%s&country=%s",
                    graphServiceUrl, query, country);
            ArticleInfo[] articles = restTemplate.getForObject(url, ArticleInfo[].class);
            return articles != null ? List.of(articles) : new ArrayList<>();
        } catch (RestClientException e) {
            log.warn("Failed to search articles for '{}' in {}: {}", query, country, e.getMessage());
            return new ArrayList<>();
        }
    }

    public boolean linkDocumentClauseToArticle(String graphId, String documentId,
                                               String lawCode, String country, String articleNumber,
                                               String clauseRef, String documentSnippet,
                                               String articleSnippet, String extractedBy, Double confidence) {
        try {
            String url = String.format("%s/api/graph/articles/link-document", graphServiceUrl);
            LinkClauseRequest body = new LinkClauseRequest(graphId, documentId, lawCode, country,
                    articleNumber, clauseRef, documentSnippet, articleSnippet, extractedBy, confidence);
            restTemplate.postForObject(url, body, Void.class);
            log.info("Linked clause {} of doc {} to art. {} of {} (graph {})",
                    clauseRef, documentId, articleNumber, lawCode, graphId);
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to link clause to article: {}", e.getMessage());
            return false;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ArticleInfo {
        private Long id;
        private String lawCode;
        private String country;
        private String number;
        private String title;
        private String body;
        private String source;
        private String extractedBy;
        private Double confidence;
        private String verifiedBy;
        private String sourceUri;
    }

    public boolean flagArticleConflict(String graphId, String country,
                                       String codeA, String numberA,
                                       String codeB, String numberB,
                                       String reason, Double confidence) {
        try {
            String url = String.format("%s/api/graph/conflicts/article-article", graphServiceUrl);
            FlagArticleConflictRequest body = new FlagArticleConflictRequest(
                    graphId, country, codeA, numberA, codeB, numberB, reason, confidence, "llm-brain");
            restTemplate.postForObject(url, body, Object.class);
            log.info("Flagged article-article conflict {} ст.{} <-> {} ст.{} (graph {})",
                    codeA, numberA, codeB, numberB, graphId);
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to flag article conflict: {}", e.getMessage());
            return false;
        }
    }

    public boolean flagDocumentArticleConflict(String graphId, String documentId,
                                               String lawCode, String country,
                                               String articleNumber, String clauseRef,
                                               String reason, Double confidence) {
        try {
            String url = String.format("%s/api/graph/conflicts/document-article", graphServiceUrl);
            FlagDocumentArticleConflictRequest body = new FlagDocumentArticleConflictRequest(
                    graphId, documentId, lawCode, country, articleNumber, clauseRef,
                    reason, confidence, "llm-brain");
            restTemplate.postForObject(url, body, Object.class);
            log.info("Flagged doc-article conflict: doc {} (clause {}) <-> {} ст.{} (graph {})",
                    documentId, clauseRef, lawCode, articleNumber, graphId);
            return true;
        } catch (RestClientException e) {
            log.warn("Failed to flag doc-article conflict: {}", e.getMessage());
            return false;
        }
    }

    public String createComment(String graphId, CreateCommentRequest body) {
        try {
            String url = String.format("%s/api/v1/user-graphs/%s/comments", graphServiceUrl, graphId);
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> resp = restTemplate.postForObject(url, body, java.util.Map.class);
            if (resp == null) return null;
            Object id = resp.get("id");
            log.info("Created comment {} in graph {} (kind={}, laws={}, articles={})",
                    id, graphId, body.getKind(),
                    body.getReferencedLawCodes() == null ? 0 : body.getReferencedLawCodes().size(),
                    body.getReferencedArticles() == null ? 0 : body.getReferencedArticles().size());
            return id == null ? null : id.toString();
        } catch (RestClientException e) {
            log.warn("Failed to create comment in graph {}: {}", graphId, e.getMessage());
            return null;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CommentArticleRef {
        private String lawCode;
        private String number;
        private String country;
        private String reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @lombok.Builder
    public static class CreateCommentRequest {
        private String id;
        private String title;
        private String body;
        private String preview;
        private String kind;
        private String subjectKind;
        private String subjectId;
        private java.util.List<String> referencedLawCodes;
        private java.util.List<CommentArticleRef> referencedArticles;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlagArticleConflictRequest {
        private String graphId;
        private String country;
        private String codeA;
        private String numberA;
        private String codeB;
        private String numberB;
        private String reason;
        private Double confidence;
        private String extractedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlagDocumentArticleConflictRequest {
        private String graphId;
        private String documentId;
        private String lawCode;
        private String country;
        private String articleNumber;
        private String clauseRef;
        private String reason;
        private Double confidence;
        private String extractedBy;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinkClauseRequest {
        private String graphId;
        private String documentId;
        private String lawCode;
        private String country;
        private String articleNumber;
        private String clauseRef;
        private String documentSnippet;
        private String articleSnippet;
        private String extractedBy;
        private Double confidence;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LawInfo {
        private Long id;
        private String title;
        private String code;
        private String country;
        private String type;
        private String status;
        private String summary;

        @JsonProperty("adoptionDate")
        private String adoptionDate;

        @JsonProperty("effectiveDate")
        private String effectiveDate;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LawGraphResponse {
        private LawInfo law;
        private Object relationships;

        public String formatForLLM() {
            StringBuilder sb = new StringBuilder();
            sb.append("**").append(law.code).append(": ").append(law.title).append("**\n\n");
            if (law.summary != null && !law.summary.isEmpty()) {
                sb.append("Описание: ").append(law.summary).append("\n\n");
            }
            sb.append("Статус: ").append(law.status).append("\n");
            sb.append("Тип: ").append(law.type).append("\n");
            if (law.adoptionDate != null) {
                sb.append("Принят: ").append(law.adoptionDate).append("\n");
            }
            if (law.effectiveDate != null) {
                sb.append("В силе: ").append(law.effectiveDate).append("\n");
            }
            if (relationships != null) {
                sb.append("\n**Связи с другими нормами:**\n");
                sb.append(relationships.toString());
            }
            return sb.toString();
        }
    }
}
