package com.jvmd.chatservice.repository;

import com.jvmd.chatservice.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByUserId(Long userId);

    Page<Conversation> findByUserIdAndActiveTrue(Long userId, Pageable pageable);

    Optional<Conversation> findByIdAndUserId(Long id, Long userId);

    Page<Conversation> findByUserId(Long userId, Pageable pageable);
}
