package com.jvmd.voiceservice.repository;

import com.jvmd.voiceservice.model.VoiceMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoiceMessageRepository extends JpaRepository<VoiceMessage, String> {
    List<VoiceMessage> findByUserIdOrderByCreatedAtDesc(String userId);
    List<VoiceMessage> findByGraphIdOrderByCreatedAtDesc(String graphId);
    List<VoiceMessage> findBySituationIdOrderByCreatedAtDesc(String situationId);
}
