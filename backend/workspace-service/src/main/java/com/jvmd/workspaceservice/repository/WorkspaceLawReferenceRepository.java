package com.jvmd.workspaceservice.repository;

import com.jvmd.workspaceservice.model.WorkspaceLawReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkspaceLawReferenceRepository extends JpaRepository<WorkspaceLawReference, UUID> {

    List<WorkspaceLawReference> findByWorkspaceId(UUID workspaceId);

    List<WorkspaceLawReference> findByWorkspaceIdAndReferenceType(UUID workspaceId, String referenceType);

    @Query("SELECT r FROM WorkspaceLawReference r WHERE r.workspace.id = :workspaceId AND r.kzLawId = :lawId")
    List<WorkspaceLawReference> findByWorkspaceAndLaw(
            @Param("workspaceId") UUID workspaceId,
            @Param("lawId") UUID lawId
    );

    @Query("SELECT COUNT(r) FROM WorkspaceLawReference r WHERE r.workspace.id = :workspaceId")
    long countByWorkspace(@Param("workspaceId") UUID workspaceId);
}
