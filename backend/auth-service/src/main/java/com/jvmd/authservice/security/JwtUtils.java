package com.jvmd.authservice.security;

import com.jvmd.authservice.model.UserDetailsImpl;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtUtils {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private int jwtExpirationMs;

    @Value("${jwt.refresh.expiration}")
    private int jwtRefreshExpirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateJwtToken(Authentication authentication) {
        return generateJwtToken((UserDetailsImpl) authentication.getPrincipal());
    }

    public String generateJwtToken(UserDetailsImpl userPrincipal) {
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("userId", userPrincipal.getId())
                .claim("email", userPrincipal.getEmail())
                .claim("firstName", userPrincipal.getFirstName())
                .claim("lastName", userPrincipal.getLastName())
                .claim("active", userPrincipal.isEnabled())
                .claim("role", userPrincipal.getRole())
                .claim("planType", userPrincipal.getPlanType() != null ? userPrincipal.getPlanType() : "FREE")
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public String getUserNameFromJwtToken(String token) {
        return getClaims(token).getSubject();
    }

    public Long getUserIdFromJwtToken(String token) {
        return getClaims(token).get("userId", Long.class);
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateRefreshToken((UserDetailsImpl) authentication.getPrincipal());
    }

    public String generateRefreshToken(UserDetailsImpl userPrincipal) {
        return Jwts.builder()
                .subject(userPrincipal.getUsername())
                .claim("userId", userPrincipal.getId())
                .claim("email", userPrincipal.getEmail())
                .claim("firstName", userPrincipal.getFirstName())
                .claim("lastName", userPrincipal.getLastName())
                .claim("active", userPrincipal.isEnabled())
                .claim("role", userPrincipal.getRole())
                .claim("planType", userPrincipal.getPlanType() != null ? userPrincipal.getPlanType() : "FREE")
                .claim("type", "refresh")
                .issuedAt(new Date())
                .expiration(new Date((new Date()).getTime() + jwtRefreshExpirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean isValidRefreshToken(String token) {
        try {
            return "refresh".equals(getClaims(token).get("type", String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public UserDetailsImpl getUserDetailsFromJwtToken(String token) {
        var claims = getClaims(token);
        String plan = claims.get("planType", String.class);
        return new UserDetailsImpl(
                claims.get("userId", Long.class),
                claims.getSubject(),
                claims.get("email", String.class),
                claims.get("firstName", String.class),
                claims.get("lastName", String.class),
                "",
                Boolean.TRUE.equals(claims.get("active", Boolean.class)),
                claims.get("role", String.class),
                plan != null ? plan : "FREE"
        );
    }

    private io.jsonwebtoken.Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (SecurityException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }
}
