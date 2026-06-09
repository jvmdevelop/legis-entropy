package com.jvmd.userservice.controller;

import com.jvmd.userservice.model.User;
import com.jvmd.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public PagedModel<User> getAll(Pageable pageable) {
        Page<User> users = userService.getAll(pageable);
        return new PagedModel<>(users);
    }

    @GetMapping("/me")
    public ResponseEntity<User> getMe(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return userService.findById(userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exists-by-username")
    public boolean existsByUsername(@RequestParam String username) {
        return userService.existsByUsername(username);
    }

    @GetMapping("/exists-by-email")
    public boolean existsByEmail(@RequestParam String email) {
        return userService.existsByEmail(email);
    }

    @GetMapping("/find/id/{id}")
    public ResponseEntity<User> findById(@PathVariable Long id) {
        return userService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/find/username/{username}")
    public ResponseEntity<User> findByUsername(@PathVariable String username) {
        return userService.findByUsername(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public User getOne(@PathVariable Long id) {
        return userService.getOne(id);
    }

    @GetMapping("/by-ids")
    public List<User> getMany(@RequestParam List<Long> ids) {
        return userService.getMany(ids);
    }

    @PostMapping
    public User create(@RequestBody User user) {
        return userService.create(user);
    }

    @PatchMapping("/{id}")
    public User patch(
            @PathVariable Long id,
            @RequestBody JsonNode patchNode,
            @RequestHeader(value = "X-User-Id", required = false) Long callerId) throws IOException {
        requireSelfOrInternal(id, callerId);
        return userService.patch(id, patchNode);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long callerId) {
        requireSelfOrInternal(id, callerId);
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping
    public List<Long> patchMany(
            @RequestParam List<Long> ids,
            @RequestBody JsonNode patchNode,
            @RequestHeader(value = "X-User-Id", required = false) Long callerId) throws IOException {
        requireInternal(callerId);
        return userService.patchMany(ids, patchNode);
    }

    @DeleteMapping
    public void deleteMany(
            @RequestParam List<Long> ids,
            @RequestHeader(value = "X-User-Id", required = false) Long callerId) {
        requireInternal(callerId);
        userService.deleteMany(ids);
    }

    private void requireSelfOrInternal(Long targetId, Long callerId) {
        if (callerId != null && !callerId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }

    private void requireInternal(Long callerId) {
        if (callerId != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}
