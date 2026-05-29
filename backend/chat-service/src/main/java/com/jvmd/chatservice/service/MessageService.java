package com.jvmd.chatservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jvmd.chatservice.client.LlmBrainClient;
import com.jvmd.chatservice.dto.BrainRequestDto;
import com.jvmd.chatservice.dto.BrainResponseDto;
import com.jvmd.chatservice.dto.ChatRequest;
import com.jvmd.chatservice.dto.ChatResponse;
import com.jvmd.chatservice.dto.HistoryEntryDto;
import com.jvmd.chatservice.dto.MessageRequest;
import com.jvmd.chatservice.dto.MessageResponse;
import com.jvmd.chatservice.entity.Conversation;
import com.jvmd.chatservice.entity.Message;
import com.jvmd.chatservice.entity.MessageRole;
import com.jvmd.chatservice.exception.ResourceNotFoundException;
import com.jvmd.chatservice.repository.ConversationRepository;
import com.jvmd.chatservice.repository.MessageRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class MessageService {

    @Value("${brain.stream.url:http://llm-brain-service/api/brain/chat/stream}")
    private String brainStreamUrl;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final LlmBrainClient llmBrainClient;
    private final RestTemplate loadBalancedRestTemplate;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatResponse chat(ChatRequest request, Long userId) {
        Conversation conversation = requireOwnedConversation(
            request.getConversationId(),
            userId
        );
        List<HistoryEntryDto> history = loadHistory(conversation.getId());

        Message userMessage = new Message();
        userMessage.setConversation(conversation);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(request.getContent());
        Message savedUserMessage = messageRepository.save(userMessage);

        BrainRequestDto brainRequest = buildBrainRequest(
            request,
            conversation,
            history
        );
        BrainResponseDto brainResponse = llmBrainClient.chat(brainRequest);

        Message assistantMessage = new Message();
        assistantMessage.setConversation(conversation);
        assistantMessage.setRole(MessageRole.ASSISTANT);
        assistantMessage.setContent(brainResponse.content());
        assistantMessage.setTokens(
            brainResponse.tokens() > 0 ? brainResponse.tokens() : null
        );
        Message savedAssistantMessage = messageRepository.save(
            assistantMessage
        );

        return new ChatResponse(
            mapToResponse(savedUserMessage),
            mapToResponse(savedAssistantMessage)
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter chatStream(ChatRequest request, Long userId) {
        Conversation conversation = requireOwnedConversation(
            request.getConversationId(),
            userId
        );
        List<HistoryEntryDto> history = loadHistory(conversation.getId());

        Message userMessage = new Message();
        userMessage.setConversation(conversation);
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(request.getContent());
        messageRepository.save(userMessage);

        BrainRequestDto brainRequest = buildBrainRequest(
            request,
            conversation,
            history
        );
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        CompletableFuture.runAsync(() -> {
            StringBuilder fullContent = new StringBuilder();
            try {
                loadBalancedRestTemplate.execute(
                    brainStreamUrl,
                    HttpMethod.POST,
                    req -> {
                        req.getHeaders().setContentType(
                            MediaType.APPLICATION_JSON
                        );
                        req.getHeaders().setAccept(
                            List.of(MediaType.TEXT_EVENT_STREAM)
                        );
                        objectMapper.writeValue(req.getBody(), brainRequest);
                    },
                    resp -> {
                        try (
                            BufferedReader reader = new BufferedReader(
                                new InputStreamReader(
                                    resp.getBody(),
                                    StandardCharsets.UTF_8
                                )
                            )
                        ) {
                            parseSseStream(reader, emitter, fullContent);
                        } catch (Exception ex) {
                            throw new java.io.IOException(ex);
                        }
                        return null;
                    }
                );
                Message assistantMessage = new Message();
                assistantMessage.setConversation(conversation);
                assistantMessage.setRole(MessageRole.ASSISTANT);
                assistantMessage.setContent(fullContent.toString());
                messageRepository.save(assistantMessage);
                emitter.complete();
            } catch (Exception e) {
                log.error(
                    "Stream error for conversation {}: {}",
                    conversation.getId(),
                    e.getMessage()
                );
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void parseSseStream(
        BufferedReader reader,
        SseEmitter emitter,
        StringBuilder fullContent
    ) throws Exception {
        String line;
        StringBuilder eventBuf = new StringBuilder();
        String currentEventType = "";
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("event:")) {
                currentEventType = line.substring(6).trim();
            } else if (line.startsWith("data:")) {
                if (eventBuf.length() > 0) eventBuf.append('\n');
                eventBuf.append(line.substring(5));
            } else if (line.isEmpty() && eventBuf.length() > 0) {
                currentEventType = flushSseEvent(
                    eventBuf,
                    currentEventType,
                    emitter,
                    fullContent
                );
            }
        }
        if (eventBuf.length() > 0) {
            flushSseEvent(eventBuf, currentEventType, emitter, fullContent);
        }
    }

    private String flushSseEvent(
        StringBuilder eventBuf,
        String eventType,
        SseEmitter emitter,
        StringBuilder fullContent
    ) throws Exception {
        String chunk = eventBuf.toString();
        eventBuf.setLength(0);
        if (!eventType.isEmpty()) {
            emitter.send(SseEmitter.event().name(eventType).data(chunk));
            if ("redaction".equals(eventType)) {
                fullContent.setLength(0);
                fullContent.append(chunk);
            }
        } else {
            fullContent.append(chunk);
            emitter.send(SseEmitter.event().data(chunk));
        }
        return "";
    }

    public MessageResponse createMessage(MessageRequest request) {
        Conversation conversation = conversationRepository
            .findById(request.getConversationId())
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Conversation not found with id: " +
                        request.getConversationId()
                )
            );

        Message message = new Message();
        message.setConversation(conversation);
        message.setRole(request.getRole());
        message.setContent(request.getContent());
        message.setTokens(request.getTokens());

        Message saved = messageRepository.save(message);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public MessageResponse getMessageById(Long id) {
        Message message = messageRepository
            .findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Message not found with id: " + id
                )
            );
        return mapToResponse(message);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessagesByConversationId(
        Long conversationId,
        Long userId,
        Pageable pageable
    ) {
        requireOwnedConversation(conversationId, userId);
        return messageRepository
            .findByConversationIdOrderByCreatedAtAsc(conversationId, pageable)
            .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getAllMessages(
        Long userId,
        Pageable pageable
    ) {
        return messageRepository
            .findByConversation_UserId(userId, pageable)
            .map(this::mapToResponse);
    }

    public MessageResponse updateMessage(Long id, MessageRequest request) {
        Message message = messageRepository
            .findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Message not found with id: " + id
                )
            );

        Conversation conversation = conversationRepository
            .findById(request.getConversationId())
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Conversation not found with id: " +
                        request.getConversationId()
                )
            );

        message.setConversation(conversation);
        message.setRole(request.getRole());
        message.setContent(request.getContent());
        message.setTokens(request.getTokens());

        Message updated = messageRepository.save(message);
        return mapToResponse(updated);
    }

    public void deleteMessage(Long id) {
        Message message = messageRepository
            .findById(id)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Message not found with id: " + id
                )
            );
        messageRepository.delete(message);
    }

    private MessageResponse mapToResponse(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setConversationId(message.getConversation().getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setTokens(message.getTokens());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }

    private Conversation requireOwnedConversation(
        Long conversationId,
        Long userId
    ) {
        return conversationRepository
            .findByIdAndUserId(conversationId, userId)
            .orElseThrow(() ->
                new ResourceNotFoundException(
                    "Conversation not found with id: " + conversationId
                )
            );
    }

    private List<HistoryEntryDto> loadHistory(Long conversationId) {
        List<Message> recent =
            messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(
                conversationId
            );
        Collections.reverse(recent);
        return recent
            .stream()
            .map(m -> new HistoryEntryDto(m.getRole().name(), m.getContent()))
            .toList();
    }

    private BrainRequestDto buildBrainRequest(
        ChatRequest request,
        Conversation conversation,
        List<HistoryEntryDto> history
    ) {
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
