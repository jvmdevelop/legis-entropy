package com.jvmd.documentservice.repository;

import com.jvmd.documentservice.entity.UserDocument;
import com.jvmd.documentservice.service.DocumentProcessingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserDocumentRepository extends JpaRepository<UserDocument, String> {

    Optional<UserDocument> findByIdAndUserId(String id, String userId);

    Page<UserDocument> findByUserIdAndStatusNot(String userId, DocumentProcessingStatus status, Pageable pageable);
}
