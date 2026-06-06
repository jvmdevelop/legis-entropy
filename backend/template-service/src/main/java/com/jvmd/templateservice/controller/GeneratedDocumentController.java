package com.jvmd.templateservice.controller;

import com.jvmd.templateservice.dto.GeneratedDocumentDTO;
import com.jvmd.templateservice.service.DocumentExportService;
import com.jvmd.templateservice.service.TemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/generated-documents")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class GeneratedDocumentController {

    private final TemplateService templateService;
    private final DocumentExportService exportService;

    @GetMapping
    public List<GeneratedDocumentDTO> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) String situationId,
            @RequestParam(required = false) String graphId) {
        if (situationId != null) return templateService.listGeneratedBySituation(situationId);
        if (graphId != null) return templateService.listGeneratedByGraph(graphId);
        String uid = userId != null ? userId : "anonymous";
        return templateService.listGeneratedByUser(uid);
    }

    @GetMapping("/{id}")
    public ResponseEntity<GeneratedDocumentDTO> get(@PathVariable String id) {
        return templateService.getGenerated(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/{id}/raw", produces = "text/markdown; charset=UTF-8")
    public ResponseEntity<String> raw(@PathVariable String id) {
        return templateService.getGenerated(id)
                .<ResponseEntity<String>>map(d -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("text/markdown; charset=UTF-8"))
                        .body(d.getBodyMarkdown()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<GeneratedDocumentDTO> updateBody(
            @PathVariable String id, @RequestBody Map<String, String> body) {
        String markdown = body.get("bodyMarkdown");
        if (markdown == null) return ResponseEntity.badRequest().build();
        return templateService.updateBody(id, markdown).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable String id, @RequestParam(defaultValue = "pdf") String format) {
        return templateService.getGenerated(id).map(doc -> {
            try {
                String slug = slugify(doc.getTitle());
                if ("docx".equalsIgnoreCase(format)) {
                    byte[] bytes = exportService.toDocx(doc.getTitle(), doc.getBodyMarkdown());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(slug + ".docx").build().toString())
                            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                            .body(bytes);
                } else {
                    byte[] bytes = exportService.toPdf(doc.getTitle(), doc.getBodyMarkdown());
                    return ResponseEntity.ok()
                            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(slug + ".pdf").build().toString())
                            .contentType(MediaType.APPLICATION_PDF)
                            .body(bytes);
                }
            } catch (Exception e) {
                log.error("Export failed for doc {}: {}", id, e.getMessage(), e);
                return ResponseEntity.internalServerError().<byte[]>build();
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/regenerate")
    public ResponseEntity<GeneratedDocumentDTO> regenerate(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "anonymous";
        return templateService.regenerate(id, uid)
                .map(d -> ResponseEntity.status(HttpStatus.CREATED).body(d))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        templateService.deleteGenerated(id);
        return ResponseEntity.noContent().build();
    }

    private static String slugify(String s) {
        if (s == null) return "document";
        return s.toLowerCase().replaceAll("[^a-zа-я0-9]+", "-").replaceAll("^-|-$", "");
    }
}
