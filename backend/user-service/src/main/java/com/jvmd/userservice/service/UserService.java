package com.jvmd.userservice.service;

import com.jvmd.userservice.model.User;
import com.jvmd.userservice.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    public Page<User> getAll(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public User getOne(Long id) {
        Optional<User> userOptional = userRepository.findById(id);
        return userOptional.orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id)));
    }

    public List<User> getMany(List<Long> ids) {
        return userRepository.findAllById(ids);
    }

    public User create(User user) {
        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username or email already exists");
        }
    }

    public User patch(Long id, JsonNode patchNode) throws IOException {
        User user = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id)));

        stripImmutableFields(patchNode);
        objectMapper.readerForUpdating(user).readValue(patchNode);

        return userRepository.save(user);
    }

    public List<Long> patchMany(List<Long> ids, JsonNode patchNode) throws IOException {
        Collection<User> users = userRepository.findAllById(ids);

        stripImmutableFields(patchNode);
        for (User user : users) {
            objectMapper.readerForUpdating(user).readValue(patchNode);
        }

        List<User> resultUsers = userRepository.saveAll(users);
        return resultUsers.stream()
                .map(User::getId)
                .toList();
    }

    public User delete(Long id) {
        User user = userRepository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Entity with id `%s` not found".formatted(id)));
        userRepository.delete(user);
        return user;
    }

    private void stripImmutableFields(JsonNode patchNode) {
        if (patchNode instanceof ObjectNode objectNode) {
            objectNode.remove("password");
            objectNode.remove("id");
            objectNode.remove("createdAt");
        }
    }

    public void deleteMany(List<Long> ids) {
        userRepository.deleteAllById(ids);
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public long countAll() {
        return userRepository.count();
    }

    public long countActive() {
        return userRepository.countByActive(true);
    }

    public long countByRole(String role) {
        return userRepository.countByRole(role);
    }
}
