package com.jvmd.dms.repository;

import com.jvmd.dms.entity.UserDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDocumentRepository extends JpaRepository<UserDocument, String> {

    Optional<UserDocument> findByIdAndUserId(String id, String userId);

    Page<UserDocument> findByUserIdAndStatusNot(String userId, com.jvmd.dms.service.DocumentProcessingStatus status, Pageable pageable);
}
