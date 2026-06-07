package com.jvmd.chatservice.repository;

import com.jvmd.chatservice.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Page<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId, Pageable pageable);

    List<Message> findByConversationId(Long conversationId);

    List<Message> findTop20ByConversationIdOrderByCreatedAtDesc(Long conversationId);

    Page<Message> findByConversation_UserId(Long userId, Pageable pageable);
}
