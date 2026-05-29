package com.jvmd.workspaceservice.repository;

import com.jvmd.workspaceservice.model.WorkspaceDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceDocumentRepository extends JpaRepository<WorkspaceDocument, UUID> {

    List<WorkspaceDocument> findByWorkspaceIdAndIsActiveTrue(UUID workspaceId);

    Optional<WorkspaceDocument> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    @Query("SELECT d FROM WorkspaceDocument d WHERE d.workspace.id = :workspaceId AND LOWER(d.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<WorkspaceDocument> searchByName(@Param("workspaceId") UUID workspaceId, @Param("name") String name);

    @Query("SELECT COUNT(d) FROM WorkspaceDocument d WHERE d.workspace.id = :workspaceId AND d.isActive = true")
    long countByWorkspace(@Param("workspaceId") UUID workspaceId);
}
