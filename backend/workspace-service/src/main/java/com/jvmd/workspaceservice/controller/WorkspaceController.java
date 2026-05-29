package com.jvmd.workspaceservice.controller;

import com.jvmd.workspaceservice.model.Workspace;
import com.jvmd.workspaceservice.service.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    private static UUID toUserUuid(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            return UUID.nameUUIDFromBytes(("user-" + userId).getBytes(StandardCharsets.UTF_8));
        }
    }

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> request
    ) {
        log.info("Creating workspace: {} for user: {}", request.get("name"), userId);

        Workspace workspace = workspaceService.createWorkspace(
                toUserUuid(userId),
                request.get("name"),
                request.get("description")
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(workspace);
    }

    @GetMapping
    public ResponseEntity<Page<Workspace>> getUserWorkspaces(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching workspaces for user: {}", userId);

        Pageable pageable = PageRequest.of(page, size);
        Page<Workspace> workspaces = workspaceService.getUserWorkspaces(toUserUuid(userId), pageable);

        return ResponseEntity.ok(workspaces);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Workspace> getWorkspace(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId
    ) {
        log.info("Fetching workspace: {}", id);

        Workspace workspace = workspaceService.getWorkspaceById(id);

        if (!workspace.getUserId().equals(toUserUuid(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(workspace);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Workspace> updateWorkspace(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId,
            @RequestBody Map<String, String> request
    ) {
        log.info("Updating workspace: {}", id);

        Workspace workspace = workspaceService.getWorkspaceById(id);

        if (!workspace.getUserId().equals(toUserUuid(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Workspace updated = workspaceService.updateWorkspace(
                id,
                request.get("name"),
                request.get("description")
        );

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkspace(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId
    ) {
        log.info("Deleting workspace: {}", id);

        Workspace workspace = workspaceService.getWorkspaceById(id);

        if (!workspace.getUserId().equals(toUserUuid(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        workspaceService.deleteWorkspace(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<Map<String, Object>> getWorkspaceStats(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String userId
    ) {
        Workspace workspace = workspaceService.getWorkspaceById(id);

        if (!workspace.getUserId().equals(toUserUuid(userId))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(Map.of(
                "id", workspace.getId(),
                "name", workspace.getName(),
                "documentCount", workspace.getDocuments().size(),
                "lawReferencesCount", workspace.getLawReferences().size(),
                "createdAt", workspace.getCreatedAt(),
                "updatedAt", workspace.getUpdatedAt()
        ));
    }
}
