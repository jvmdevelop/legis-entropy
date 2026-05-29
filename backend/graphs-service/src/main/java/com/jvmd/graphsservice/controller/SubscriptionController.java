package com.jvmd.graphsservice.controller;

import com.jvmd.graphsservice.dto.ChangeEventDTO;
import com.jvmd.graphsservice.dto.CreateSubscriptionRequest;
import com.jvmd.graphsservice.dto.SubscriptionDTO;
import com.jvmd.graphsservice.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph/subscriptions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<SubscriptionDTO> create(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody CreateSubscriptionRequest req) {
        String uid = userId != null ? userId : "anonymous";
        if (req == null || req.getLawCode() == null || req.getArticleNumber() == null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(subscriptionService.create(uid, req));
    }

    @GetMapping
    public ResponseEntity<List<SubscriptionDTO>> list(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.ok(subscriptionService.list(uid));
    }

    @DeleteMapping("/{subscriptionId}")
    public ResponseEntity<Void> delete(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @PathVariable String subscriptionId) {
        String uid = userId != null ? userId : "anonymous";
        subscriptionService.delete(uid, subscriptionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/events")
    public ResponseEntity<List<ChangeEventDTO>> events(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.ok(subscriptionService.recentEvents(uid));
    }

    @GetMapping("/events/unread-count")
    public ResponseEntity<Map<String, Integer>> unreadCount(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "anonymous";
        return ResponseEntity.ok(Map.of("count", subscriptionService.unreadCount(uid)));
    }

    @PostMapping("/events/acknowledge")
    public ResponseEntity<Void> acknowledgeAll(
            @RequestHeader(value = "X-User-Id", required = false) String userId) {
        String uid = userId != null ? userId : "anonymous";
        subscriptionService.acknowledgeAll(uid);
        return ResponseEntity.noContent().build();
    }
}
