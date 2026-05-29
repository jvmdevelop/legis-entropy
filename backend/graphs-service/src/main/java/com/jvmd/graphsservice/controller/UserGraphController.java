package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.dto.AddUserDocumentRequest;
import com.jvmd.graphsservice.dto.CreateUserGraphRequest;
import com.jvmd.graphsservice.dto.GraphContentResponse;
import com.jvmd.graphsservice.dto.UserDocumentDTO;
import com.jvmd.graphsservice.dto.UserGraphDTO;
import com.jvmd.graphsservice.service.UserGraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/user-graphs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserGraphController {

    private final UserGraphService userGraphService;

    @PostMapping
    public ResponseEntity<UserGraphDTO> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody CreateUserGraphRequest request) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.status(HttpStatus.CREATED).body(userGraphService.createGraph(uid, request));
    }

    @GetMapping
    public ResponseEntity<List<UserGraphDTO>> listByWorkspace(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam String workspaceId) {
        List<UserGraphDTO> all = userGraphService.listByWorkspace(workspaceId);
        if (userId != null) {
            all = all.stream().filter(g -> userId.equals(g.getUserId())).toList();
        }
        return ResponseEntity.ok(all);
    }

    @GetMapping("/{graphId}")
    public ResponseEntity<UserGraphDTO> get(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String graphId) {
        return userGraphService.getGraph(graphId)
                .filter(g -> userId == null || userId.equals(g.getUserId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{graphId}/content")
    public ResponseEntity<GraphContentResponse> getContent(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String graphId) {
        return userGraphService.getGraphContent(graphId)
                .filter(c -> userId == null || userId.equals(c.getGraph().getUserId()))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{graphId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String graphId) {
        var existing = userGraphService.getGraph(graphId);
        if (existing.isEmpty()) return ResponseEntity.notFound().build();
        if (userId != null && !userId.equals(existing.get().getUserId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userGraphService.deleteGraph(graphId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{graphId}/laws/{code}")
    public ResponseEntity<Void> addLaw(
            @PathVariable String graphId,
            @PathVariable String code,
            @RequestParam(defaultValue = "RK") String country) {
        return userGraphService.addLaw(graphId, code, country)
                .<ResponseEntity<Void>>map(law -> ResponseEntity.ok().build())
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{graphId}/laws/{code}")
    public ResponseEntity<Void> removeLaw(
            @PathVariable String graphId,
            @PathVariable String code,
            @RequestParam(defaultValue = "RK") String country) {
        userGraphService.removeLaw(graphId, code, country);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{graphId}/documents")
    public ResponseEntity<UserDocumentDTO> addDocument(
            @PathVariable String graphId,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody AddUserDocumentRequest request) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.status(HttpStatus.CREATED).body(userGraphService.addDocument(graphId, uid, request));
    }

    @DeleteMapping("/{graphId}/documents/{documentId}")
    public ResponseEntity<Void> removeDocument(
            @PathVariable String graphId,
            @PathVariable String documentId) {
        userGraphService.removeDocument(graphId, documentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{graphId}/documents/{documentId}/links/{lawCode}")
    public ResponseEntity<Void> linkDocumentToLaw(
            @PathVariable String graphId,
            @PathVariable String documentId,
            @PathVariable String lawCode,
            @RequestParam(defaultValue = "RK") String country,
            @RequestParam(defaultValue = "SEMANTIC") String kind) {
        userGraphService.linkDocumentToLaw(graphId, documentId, lawCode, country, kind);
        return ResponseEntity.ok().build();
    }
}
