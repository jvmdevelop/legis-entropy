package com.jvmd.llmbrainservice.client;

import com.jvmd.llmbrainservice.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(
        name = "graph-service",
        url = "${graph.service.url:http://localhost:8086}"
)
public interface GraphServiceClient {

    @GetMapping("/api/graph/laws/{code}")
    LawGraphResponse getLawWithRelationships(
            @PathVariable("code") String code,
            @RequestParam("country") String country
    );

    @GetMapping("/api/graph/laws/{code}/related")
    List<LawInfo> findRelatedLaws(
            @PathVariable("code") String code,
            @RequestParam("country") String country
    );

    @GetMapping("/api/graph/laws/search")
    List<LawInfo> searchLaws(
            @RequestParam("query") String query,
            @RequestParam("country") String country
    );

    @GetMapping("/api/graph/laws/{code}/articles")
    List<ArticleInfo> listArticlesByLaw(
            @PathVariable("code") String code,
            @RequestParam("country") String country
    );

    @GetMapping("/api/graph/laws/{code}/articles/{number}")
    ArticleInfo getArticle(
            @PathVariable("code") String code,
            @PathVariable("number") String number,
            @RequestParam("country") String country
    );

    @GetMapping("/api/graph/articles/search")
    List<ArticleInfo> searchArticles(
            @RequestParam("query") String query,
            @RequestParam("country") String country
    );

    @PostMapping(value = "/api/graph/articles/link-document", consumes = MediaType.APPLICATION_JSON_VALUE)
    void linkDocumentClauseToArticle(@RequestBody LinkClauseRequest request);


    @PostMapping(value = "/api/graph/conflicts/article-article", consumes = MediaType.APPLICATION_JSON_VALUE)
    void flagArticleConflict(@RequestBody FlagArticleConflictRequest request);

    @PostMapping(value = "/api/graph/conflicts/document-article", consumes = MediaType.APPLICATION_JSON_VALUE)
    void flagDocumentArticleConflict(@RequestBody FlagDocumentArticleConflictRequest request);


    @PostMapping("/api/v1/user-graphs/{graphId}/laws/{code}")
    void addLawToUserGraph(
            @PathVariable("graphId") String graphId,
            @PathVariable("code") String code,
            @RequestParam("country") String country
    );

    @PostMapping("/api/v1/user-graphs/{graphId}/documents/{documentId}/links/{lawCode}")
    void linkDocumentToLaw(
            @PathVariable("graphId") String graphId,
            @PathVariable("documentId") String documentId,
            @PathVariable("lawCode") String lawCode,
            @RequestParam("country") String country,
            @RequestParam("kind") String kind
    );

    @PostMapping("/api/v1/user-graphs/{graphId}/situations/{situationId}/laws/{lawCode}")
    void linkSituationToLaw(
            @PathVariable("graphId") String graphId,
            @PathVariable("situationId") String situationId,
            @PathVariable("lawCode") String lawCode,
            @RequestParam("country") String country,
            @RequestParam("kind") String kind
    );

    @PostMapping("/api/v1/user-graphs/{graphId}/voice-evidence/{voiceId}/laws/{lawCode}")
    void linkVoiceEvidenceToLaw(
            @PathVariable("graphId") String graphId,
            @PathVariable("voiceId") String voiceId,
            @PathVariable("lawCode") String lawCode,
            @RequestParam("country") String country,
            @RequestParam("reason") String reason
    );

    @PostMapping(value = "/api/v1/user-graphs/{graphId}/situations", consumes = MediaType.APPLICATION_JSON_VALUE)
    IdResponse createSituation(
            @PathVariable("graphId") String graphId,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody CreateSituationRequest request
    );

    @PostMapping(value = "/api/v1/user-graphs/{graphId}/situations/{situationId}/generated-doc", consumes = MediaType.APPLICATION_JSON_VALUE)
    void updateSituationGeneratedDoc(
            @PathVariable("graphId") String graphId,
            @PathVariable("situationId") String situationId,
            @RequestBody UpdateGeneratedDocRequest request
    );

    @GetMapping("/api/v1/user-graphs/{graphId}/situations/{situationId}")
    Map<String, Object> getSituation(
            @PathVariable("graphId") String graphId,
            @PathVariable("situationId") String situationId
    );

    @PostMapping(value = "/api/v1/user-graphs/{graphId}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    IdResponse createComment(
            @PathVariable("graphId") String graphId,
            @RequestBody CreateCommentRequest request
    );
}