package com.jvmd.workspaceservice.repository;

import com.jvmd.workspaceservice.model.Workspace;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    List<Workspace> findByUserIdAndIsActiveTrue(UUID userId);

    Optional<Workspace> findByIdAndUserId(UUID id, UUID userId);

    Page<Workspace> findByUserIdAndIsActiveTrue(UUID userId, Pageable pageable);

    @Query("SELECT w FROM Workspace w WHERE w.userId = :userId AND LOWER(w.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Workspace> searchByName(@Param("userId") UUID userId, @Param("name") String name);

    @Query("SELECT COUNT(w) FROM Workspace w WHERE w.userId = :userId AND w.isActive = true")
    long countActiveWorkspacesByUser(@Param("userId") UUID userId);
}
