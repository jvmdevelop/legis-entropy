package com.jvmd.llmbrainservice.client;

import com.jvmd.llmbrainservice.dto.CreateSituationRequest;
import com.jvmd.llmbrainservice.dto.FlagArticleConflictRequest;
import com.jvmd.llmbrainservice.dto.FlagDocumentArticleConflictRequest;
import com.jvmd.llmbrainservice.dto.IdResponse;
import com.jvmd.llmbrainservice.dto.UpdateGeneratedDocRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@FeignClient(name = "situation-service")
public interface SituationClient {

    @PostMapping(value = "/api/graph/conflicts/article-article", consumes = MediaType.APPLICATION_JSON_VALUE)
    void flagArticleConflict(@RequestBody FlagArticleConflictRequest request);

    @PostMapping(value = "/api/graph/conflicts/document-article", consumes = MediaType.APPLICATION_JSON_VALUE)
    void flagDocumentArticleConflict(@RequestBody FlagDocumentArticleConflictRequest request);

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

    @PostMapping("/api/v1/user-graphs/{graphId}/situations/{situationId}/laws/{lawCode}")
    void linkSituationToLaw(
            @PathVariable("graphId") String graphId,
            @PathVariable("situationId") String situationId,
            @PathVariable("lawCode") String lawCode,
            @RequestParam("country") String country,
            @RequestParam("kind") String kind
    );
}
