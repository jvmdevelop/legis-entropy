package com.jvmd.userservice.controller;

import com.jvmd.userservice.model.User;
import com.jvmd.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public record CreateUserRequest(
            String username, String email, String password,
            String firstName, String lastName,
            String role, String planType) {}

    public record SafeUser(Long id, String username, String email,
                           String firstName, String lastName,
                           String role, String planType,
                           Boolean active, String createdAt) {
        static SafeUser from(User u) {
            return new SafeUser(u.getId(), u.getUsername(), u.getEmail(),
                    u.getFirstName(), u.getLastName(), u.getRole(), u.getPlanType(),
                    u.getActive(), u.getCreatedAt() != null ? u.getCreatedAt().toString() : null);
        }
    }

    private void requireAdmin(String role) {
        if (!"ROLE_ADMIN".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    @GetMapping("/users")
    public PagedModel<SafeUser> getAllUsers(
            Pageable pageable,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        Page<SafeUser> users = userService.getAll(pageable).map(SafeUser::from);
        return new PagedModel<>(users);
    }

    @GetMapping("/users/{id}")
    public SafeUser getUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        return SafeUser.from(userService.getOne(id));
    }

    @PostMapping("/users")
    public ResponseEntity<SafeUser> createUser(
            @RequestBody CreateUserRequest req,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        if (req.username() == null || req.password() == null || req.email() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username, email, password are required");
        }
        String newRole = req.role() != null && req.role().equals("ROLE_ADMIN") ? "ROLE_ADMIN" : "ROLE_USER";
        String newPlan = req.planType() != null &&
                (req.planType().equals("BASIC") || req.planType().equals("PRO")) ? req.planType() : "FREE";

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setPassword(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName() != null ? req.firstName() : "");
        user.setLastName(req.lastName() != null ? req.lastName() : "");
        user.setRole(newRole);
        user.setPlanType(newPlan);
        user.setActive(true);

        User saved = userService.create(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(SafeUser.from(saved));
    }

    @PatchMapping("/users/{id}/toggle-active")
    public User toggleActive(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        User user = userService.getOne(id);
        user.setActive(!Boolean.TRUE.equals(user.getActive()));
        return userService.save(user);
    }

    @PatchMapping("/users/{id}/role")
    public User updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        String newRole = body.get("role");
        if (newRole == null || (!newRole.equals("ROLE_USER") && !newRole.equals("ROLE_ADMIN"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role value");
        }
        User user = userService.getOne(id);
        user.setRole(newRole);
        return userService.save(user);
    }

    @PatchMapping("/users/{id}/plan")
    public User updatePlan(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        String newPlan = body.get("planType");
        if (newPlan == null || (!newPlan.equals("FREE") && !newPlan.equals("BASIC") && !newPlan.equals("PRO"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid planType value. Use: FREE, BASIC, PRO");
        }
        User user = userService.getOne(id);
        user.setPlanType(newPlan);
        return userService.save(user);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        long total = userService.countAll();
        long active = userService.countActive();
        long admins = userService.countByRole("ROLE_ADMIN");
        return Map.of(
                "totalUsers", total,
                "activeUsers", active,
                "adminUsers", admins
        );
    }
}
