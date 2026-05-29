package com.jvmd.dms.template.controller;

import com.jvmd.dms.template.dto.GeneratedDocumentDTO;
import com.jvmd.dms.template.dto.RenderTemplateRequest;
import com.jvmd.dms.template.dto.TemplateDTO;
import com.jvmd.dms.template.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TemplateController {

    private final TemplateService templateService;

    @GetMapping
    public List<TemplateDTO> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false, defaultValue = "true") boolean visibleOnly) {
        if (kind != null) return templateService.listByKind(kind);
        if (visibleOnly && userId != null && !userId.isBlank()) {
            return templateService.listVisibleToUser(userId);
        }
        return templateService.listAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TemplateDTO> get(@PathVariable String id) {
        return templateService.findById(id).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/by-code/{code}")
    public ResponseEntity<TemplateDTO> getByCode(@PathVariable String code) {
        return templateService.findByCode(code).map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<TemplateDTO> upsert(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody TemplateDTO dto) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.status(HttpStatus.CREATED).body(templateService.upsert(dto, uid));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TemplateDTO> upload(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String kind) {
        String uid = userId != null ? userId : "anonymous";
        TemplateDTO dto = templateService.uploadFromFile(file, uid, title, kind);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String id) {
        boolean removed = templateService.deleteIfOwned(id, userId);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/render")
    public ResponseEntity<GeneratedDocumentDTO> render(
            @PathVariable String id,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody RenderTemplateRequest req) {
        String uid = userId != null ? userId : "anonymous";
        return templateService.render(id, uid, req)
                .map(d -> ResponseEntity.status(HttpStatus.CREATED).body(d))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/by-code/{code}/render")
    public ResponseEntity<GeneratedDocumentDTO> renderByCode(
            @PathVariable String code,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody RenderTemplateRequest req) {
        String uid = userId != null ? userId : "anonymous";
        return templateService.renderByCode(code, uid, req)
                .map(d -> ResponseEntity.status(HttpStatus.CREATED).body(d))
                .orElse(ResponseEntity.notFound().build());
    }
}
