package com.jvmd.chatservice.service;

import com.jvmd.chatservice.client.LlmBrainClient;
import com.jvmd.chatservice.dto.BrainRequestDto;
import com.jvmd.chatservice.dto.BrainResponseDto;
import com.jvmd.chatservice.dto.ChatRequest;
import com.jvmd.chatservice.dto.ChatResponse;
import com.jvmd.chatservice.dto.HistoryEntryDto;
import com.jvmd.chatservice.dto.MessageRequest;
import com.jvmd.chatservice.dto.MessageResponse;
import com.jvmd.chatservice.exception.ResourceNotFoundException;
import com.jvmd.chatservice.mapper.ChatMapper;
import com.jvmd.chatservice.model.Conversation;
import com.jvmd.chatservice.model.Message;
import com.jvmd.chatservice.model.MessageRole;
import com.jvmd.chatservice.repository.ConversationRepository;
import com.jvmd.chatservice.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    @Value("${brain.stream.url:http://llm-brain-service/api/brain/chat/stream}")
    private String brainStreamUrl;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final LlmBrainClient llmBrainClient;
    private final SseStreamingService sseStreamingService;
    private final ChatMapper chatMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatResponse chat(ChatRequest request, Long userId) {
        Conversation conversation = requireOwnedConversation(request.getConversationId(), userId);
        List<HistoryEntryDto> history = loadHistory(conversation.getId());

        Message userMessage = saveMessage(conversation, MessageRole.USER, request.getContent(), null);

        BrainResponseDto brainResponse = llmBrainClient.chat(buildBrainRequest(request, conversation, history));
        Integer tokens = brainResponse.tokens() > 0 ? brainResponse.tokens() : null;
        Message assistantMessage = saveMessage(conversation, MessageRole.ASSISTANT, brainResponse.content(), tokens);

        return new ChatResponse(chatMapper.toMessageResponse(userMessage), chatMapper.toMessageResponse(assistantMessage));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter chatStream(ChatRequest request, Long userId) {
        Conversation conversation = requireOwnedConversation(request.getConversationId(), userId);
        List<HistoryEntryDto> history = loadHistory(conversation.getId());

        saveMessage(conversation, MessageRole.USER, request.getContent(), null);

        return sseStreamingService.stream(brainStreamUrl, buildBrainRequest(request, conversation, history),
            conversation.getId(),
            content -> saveMessage(conversation, MessageRole.ASSISTANT, content, null));
    }

    public MessageResponse createMessage(MessageRequest request) {
        Conversation conversation = conversationRepository.findById(request.getConversationId())
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + request.getConversationId()));
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(request.getRole());
        message.setContent(request.getContent());
        message.setTokens(request.getTokens());
        return chatMapper.toMessageResponse(messageRepository.save(message));
    }

    @Transactional(readOnly = true)
    public MessageResponse getMessageById(Long id) {
        return chatMapper.toMessageResponse(findMessageById(id));
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessagesByConversationId(Long conversationId, Long userId, Pageable pageable) {
        requireOwnedConversation(conversationId, userId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, pageable)
            .map(chatMapper::toMessageResponse);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getAllMessages(Long userId, Pageable pageable) {
        return messageRepository.findByConversation_UserId(userId, pageable).map(chatMapper::toMessageResponse);
    }

    public MessageResponse updateMessage(Long id, MessageRequest request) {
        Message message = findMessageById(id);
        Conversation conversation = conversationRepository.findById(request.getConversationId())
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + request.getConversationId()));
        message.setConversation(conversation);
        message.setRole(request.getRole());
        message.setContent(request.getContent());
        message.setTokens(request.getTokens());
        return chatMapper.toMessageResponse(messageRepository.save(message));
    }

    public void deleteMessage(Long id) {
        messageRepository.delete(findMessageById(id));
    }

    private Message findMessageById(Long id) {
        return messageRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Message not found with id: " + id));
    }

    private Message saveMessage(Conversation conversation, MessageRole role, String content, Integer tokens) {
        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        message.setTokens(tokens);
        return messageRepository.save(message);
    }

    private Conversation requireOwnedConversation(Long conversationId, Long userId) {
        return conversationRepository.findByIdAndUserId(conversationId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Conversation not found with id: " + conversationId));
    }

    private List<HistoryEntryDto> loadHistory(Long conversationId) {
        List<Message> recent = messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
        Collections.reverse(recent);
        return recent.stream()
            .map(m -> new HistoryEntryDto(m.getRole().name(), m.getContent()))
            .toList();
    }

    private BrainRequestDto buildBrainRequest(ChatRequest request, Conversation conversation, List<HistoryEntryDto> history) {
        return new BrainRequestDto(
            request.getContent(),
            conversation.getUserId().toString(),
            request.getDocumentId(),
            request.getGraphId(),
            request.getSituationId(),
            history
        );
    }
}
