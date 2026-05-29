package com.jvmd.gatewayservice.controller;

import com.jvmd.gatewayservice.filter.JwtGatewayFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthCheckController {

    @GetMapping("/internal/auth/check-admin")
    public ResponseEntity<Void> checkAdmin(HttpServletRequest request) {
        Object role = request.getAttribute(JwtGatewayFilter.ATTR_USER_ROLE);
        if ("ROLE_ADMIN".equals(role)) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.status(403).build();
    }
}
