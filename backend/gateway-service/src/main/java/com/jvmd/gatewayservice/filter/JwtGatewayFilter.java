package com.jvmd.gatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class JwtGatewayFilter implements Filter {

    public static final String ATTR_USER_ID = "X-User-Id";
    public static final String ATTR_USERNAME = "X-Username";
    public static final String ATTR_USER_ROLE = "X-User-Role";
    public static final String ATTR_PLAN_TYPE = "X-Plan-Type";

    private static final Set<String> PUBLIC_PREFIXES = Set.of(
        "/api/auth/",
        "/actuator/",
        "/monitoring/"
    );

    private final SecretKey jwtSigningKey;

    @Override
    public void doFilter(
        ServletRequest servletRequest,
        ServletResponse servletResponse,
        FilterChain chain
    ) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (isPublic(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String token = extractToken(request);
        if (token == null) {
            sendUnauthorized(
                response,
                "Missing or invalid Authorization header"
            );
            return;
        }

        try {
            Claims claims = Jwts.parser()
                .verifyWith(jwtSigningKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            Long userId = claims.get("userId", Long.class);
            String username = claims.getSubject();
            String userRole = claims.get("role", String.class);
            String planType = claims.get("planType", String.class);

            if (userId != null) request.setAttribute(
                ATTR_USER_ID,
                userId.toString()
            );
            if (username != null) request.setAttribute(ATTR_USERNAME, username);
            if (userRole != null) request.setAttribute(
                ATTR_USER_ROLE,
                userRole
            );
            request.setAttribute(
                ATTR_PLAN_TYPE,
                planType != null ? planType : "FREE"
            );

            chain.doFilter(request, response);
        } catch (JwtException e) {
            sendUnauthorized(response, "Invalid or expired token");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("le_access_token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private boolean isPublic(String path) {
        return PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void sendUnauthorized(HttpServletResponse response, String message)
        throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
