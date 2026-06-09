package com.jvmd.documentservice.evidence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoiceMessageRepository extends JpaRepository<VoiceMessage, String> {
    List<VoiceMessage> findByGraphIdOrderByCreatedAtDesc(String graphId);
    List<VoiceMessage> findBySituationIdOrderByCreatedAtDesc(String situationId);
}
