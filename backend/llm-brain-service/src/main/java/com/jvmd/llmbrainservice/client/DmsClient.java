package com.jvmd.llmbrainservice.client;

import com.jvmd.llmbrainservice.model.RetrievalChunkResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "document-service")
public interface DmsClient {

    @GetMapping("/api/user-documents/search")
    List<RetrievalChunkResponse> searchUserDocuments(@RequestParam("query") String query, @RequestHeader("X-User-Id") String userId, @RequestParam(value = "documentId", required = false) String documentId);

    @GetMapping("/api/user-documents/{documentId}/text")
    Map<String, String> getDocumentText(@PathVariable("documentId") String documentId, @RequestHeader("X-User-Id") String userId);
}
