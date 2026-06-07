package com.jvmd.chatservice.service;

import com.jvmd.chatservice.dto.ConversationRequest;
import com.jvmd.chatservice.dto.ConversationResponse;
import com.jvmd.chatservice.exception.ResourceNotFoundException;
import com.jvmd.chatservice.mapper.ChatMapper;
import com.jvmd.chatservice.model.Conversation;
import com.jvmd.chatservice.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMapper chatMapper;

    public ConversationResponse createConversation(ConversationRequest request, Long userId) {
        Conversation conversation = new Conversation();
        conversation.setTitle(request.getTitle());
        conversation.setUserId(userId);
        conversation.setActive(true);
        return chatMapper.toConversationResponse(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversationById(Long id, Long userId) {
        return chatMapper.toConversationResponse(requireOwned(id, userId));
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getConversations(Long userId, Pageable pageable) {
        return conversationRepository.findByUserId(userId, pageable).map(chatMapper::toConversationResponse);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getActiveConversations(Long userId, Pageable pageable) {
        return conversationRepository.findByUserIdAndActiveTrue(userId, pageable).map(chatMapper::toConversationResponse);
    }

    public ConversationResponse updateConversation(Long id, ConversationRequest request, Long userId) {
        Conversation conversation = requireOwned(id, userId);
        conversation.setTitle(request.getTitle());
        return chatMapper.toConversationResponse(conversationRepository.save(conversation));
    }

    public void deleteConversation(Long id, Long userId) {
        conversationRepository.delete(requireOwned(id, userId));
    }

    public ConversationResponse toggleConversationActiveStatus(Long id, Long userId) {
        Conversation conversation = requireOwned(id, userId);
        conversation.setActive(!Boolean.TRUE.equals(conversation.getActive()));
        return chatMapper.toConversationResponse(conversationRepository.save(conversation));
    }

    private Conversation requireOwned(Long id, Long userId) {
        return conversationRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + id));
    }
}
