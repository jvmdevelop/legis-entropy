package com.jvmd.llmbrainservice.controller;

import com.jvmd.llmbrainservice.client.DmsClient;
import com.jvmd.llmbrainservice.service.citation.ReferenceAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/brain/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentAuditController {

    public record AuditResponse(String summary, String markdown, long problems,
                                List<ReferenceAuditService.Finding> findings) {}

    private final DmsClient dmsClient;
    private final ReferenceAuditService referenceAuditService;

    @PostMapping("/{documentId}/audit")
    public ResponseEntity<AuditResponse> audit(@PathVariable String documentId,
                                               @RequestHeader("X-User-Id") String userId) {
        String text;
        try {
            Map<String, String> response = dmsClient.getDocumentText(documentId, userId);
            text = response == null ? null : response.get("text");
        } catch (RuntimeException e) {
            log.warn("Audit: could not load text of document {} for user {}: {}", documentId, userId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
        if (text == null || text.isBlank()) {
            return ResponseEntity.unprocessableEntity().build();
        }
        ReferenceAuditService.Audit audit = referenceAuditService.audit(text);
        return ResponseEntity.ok(new AuditResponse(audit.summary(), audit.markdown(), audit.problems(), audit.findings()));
    }
}
