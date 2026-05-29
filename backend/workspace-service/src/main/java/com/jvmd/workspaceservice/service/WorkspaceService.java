package com.jvmd.workspaceservice.service;

import com.jvmd.workspaceservice.model.Workspace;
import com.jvmd.workspaceservice.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;

    public Workspace createWorkspace(UUID userId, String name, String description) {
        log.info("Creating workspace: {} for user: {}", name, userId);

        Workspace workspace = Workspace.builder()
                .name(name)
                .description(description)
                .userId(userId)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return workspaceRepository.save(workspace);
    }

    public Workspace getWorkspaceById(UUID id) {
        return workspaceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Workspace not found: " + id));
    }

    public Page<Workspace> getUserWorkspaces(UUID userId, Pageable pageable) {
        log.info("Fetching workspaces for user: {}", userId);
        return workspaceRepository.findByUserIdAndIsActiveTrue(userId, pageable);
    }

    public Workspace updateWorkspace(UUID id, String name, String description) {
        log.info("Updating workspace: {}", id);

        Workspace workspace = getWorkspaceById(id);
        workspace.setName(name);
        workspace.setDescription(description);
        workspace.setUpdatedAt(LocalDateTime.now());

        return workspaceRepository.save(workspace);
    }

    public void deleteWorkspace(UUID id) {
        log.info("Deleting workspace: {}", id);

        Workspace workspace = getWorkspaceById(id);
        workspace.setIsActive(false);
        workspace.setUpdatedAt(LocalDateTime.now());

        workspaceRepository.save(workspace);
    }

    public void permanentlyDeleteWorkspace(UUID id) {
        log.info("Permanently deleting workspace: {}", id);
        workspaceRepository.deleteById(id);
    }

    public long getWorkspaceDocumentCount(UUID workspaceId) {
        Workspace workspace = getWorkspaceById(workspaceId);
        return workspace.getDocuments().size();
    }
}
