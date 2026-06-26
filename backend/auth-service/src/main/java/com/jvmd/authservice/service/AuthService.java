package com.jvmd.authservice.service;

import com.jvmd.authservice.client.UserClient;
import com.jvmd.authservice.dto.JwtResponse;
import com.jvmd.authservice.dto.LoginRequest;
import com.jvmd.authservice.dto.RefreshRequest;
import com.jvmd.authservice.dto.RegisterRequest;
import com.jvmd.authservice.model.User;
import com.jvmd.authservice.model.UserDetailsImpl;
import com.jvmd.authservice.security.JwtUtils;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@AllArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserClient userClient;
    private final PasswordEncoder passwordEncoder;

    public JwtResponse authUser(LoginRequest loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);
        String refreshToken = jwtUtils.generateRefreshToken(authentication);

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();

        return new JwtResponse(
                jwt,
                "Bearer",
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getFirstName(),
                userDetails.getLastName(),
                refreshToken,
                userDetails.getRole(),
                userDetails.getPlanType()
        );
    }


    public ResponseEntity<JwtResponse> refreshToken(RefreshRequest request) {
        if (!jwtUtils.isValidRefreshToken(request.getRefreshToken())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserDetailsImpl userDetails = jwtUtils.getUserDetailsFromJwtToken(request.getRefreshToken());
        String newAccessToken = jwtUtils.generateJwtToken(userDetails);
        String newRefreshToken = jwtUtils.generateRefreshToken(userDetails);
        return ResponseEntity.ok(new JwtResponse(
                newAccessToken,
                "Bearer",
                userDetails.getId(),
                userDetails.getUsername(),
                userDetails.getEmail(),
                userDetails.getFirstName(),
                userDetails.getLastName(),
                newRefreshToken,
                userDetails.getRole(),
                userDetails.getPlanType()
        ));
    }

    public ResponseEntity<Map<String, String>> registerUser(RegisterRequest registerRequest) {
        if (userClient.existsByUsername(registerRequest.getUsername())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username is already taken"));
        }

        if (userClient.existsByEmail(registerRequest.getEmail())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email is already in use"));
        }

        User user = new User();
        user.setUsername(registerRequest.getUsername());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setActive(true);

        try {
            userClient.create(user);
        } catch (FeignException.Conflict e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Username or email already exists"));
        }

        return ResponseEntity.ok(Map.of("message", "User registered successfully"));
    }
}
