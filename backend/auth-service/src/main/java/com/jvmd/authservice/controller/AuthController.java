package com.jvmd.authservice.controller;

import com.jvmd.authservice.client.UserClient;
import com.jvmd.authservice.dto.JwtResponse;
import com.jvmd.authservice.dto.LoginRequest;
import com.jvmd.authservice.dto.RefreshRequest;
import com.jvmd.authservice.dto.RegisterRequest;
import com.jvmd.authservice.model.User;
import com.jvmd.authservice.model.UserDetailsImpl;
import com.jvmd.authservice.security.JwtUtils;
import com.jvmd.authservice.service.AuthService;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> authenticateUser(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.authUser(loginRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> registerUser(@Valid @RequestBody RegisterRequest registerRequest) {
        return authService.registerUser(registerRequest);
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refreshToken(@Valid @RequestBody RefreshRequest request) {
        return authService.refreshToken(request);
    }

    @GetMapping("/me")
    public ResponseEntity<JwtResponse> me(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new JwtResponse(
                null,
                "Bearer",
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getFirstName(),
                userDetails.getLastName(),
                null,
                userDetails.getRole(),
                userDetails.getPlanType()
        ));
    }
}
