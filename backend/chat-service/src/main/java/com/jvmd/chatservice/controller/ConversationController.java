package com.jvmd.chatservice.controller;

import com.jvmd.chatservice.dto.ConversationRequest;
import com.jvmd.chatservice.dto.ConversationResponse;
import com.jvmd.chatservice.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @Valid @RequestBody ConversationRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return new ResponseEntity<>(conversationService.createConversation(request, userId), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<Page<ConversationResponse>> getConversations(
            @RequestHeader("X-User-Id") Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(conversationService.getConversations(userId, pageable));
    }

    @GetMapping("/active")
    public ResponseEntity<Page<ConversationResponse>> getActiveConversations(
            @RequestHeader("X-User-Id") Long userId,
            Pageable pageable) {
        return ResponseEntity.ok(conversationService.getActiveConversations(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversationById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(conversationService.getConversationById(id, userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ConversationResponse> updateConversation(
            @PathVariable Long id,
            @Valid @RequestBody ConversationRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(conversationService.updateConversation(id, request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        conversationService.deleteConversation(id, userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<ConversationResponse> toggleConversationActiveStatus(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(conversationService.toggleConversationActiveStatus(id, userId));
    }
}
