package com.jvmd.llmbrainservice.controller;

import com.jvmd.llmbrainservice.model.BrainRequest;
import com.jvmd.llmbrainservice.model.BrainResponse;
import com.jvmd.llmbrainservice.model.CompareRequest;
import com.jvmd.llmbrainservice.service.BrainService;
import com.jvmd.llmbrainservice.service.CompareService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/brain")
@RequiredArgsConstructor
public class BrainController {

    private final BrainService brainService;
    private final CompareService compareService;

    @PostMapping("/chat")
    public ResponseEntity<BrainResponse> chat(@RequestBody BrainRequest request) {
        return ResponseEntity.ok(brainService.chat(request));
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody BrainRequest request) {
        return brainService.streamChat(request);
    }

    @PostMapping("/compare")
    public ResponseEntity<BrainResponse> compare(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody CompareRequest request) {
        String resolvedUser = request.userId() != null && !request.userId().isBlank() ? request.userId() : userId;
        CompareRequest withUser = new CompareRequest(
                request.leftKind(), request.leftId(), request.leftLabel(),
                request.rightKind(), request.rightId(), request.rightLabel(),
                resolvedUser
        );
        return ResponseEntity.ok(compareService.compare(withUser));
    }
}
