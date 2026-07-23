package com.taskqueue.service;

import com.taskqueue.config.AppProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Handles JWT token generation and validation.
 *
 * Token payload contains:
 *   sub       → userId
 *   email     → user email
 *   role      → ADMIN or CLIENT
 *   companyId → the company this client owns (null for ADMIN)
 *   name      → user full name
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;

    /**
     * Generate a JWT token for a logged-in user.
     * Token is valid for app.jwt.expiry-hours (default 24h).
     */
    public String generateToken(String userId, String email,
                                String role, String name,
                                String companyId) {
        long expiryMs = appProperties.getJwt().getExpiryHours() * 3_600_000L;

        return Jwts.builder()
                .subject(userId)
                .claims(Map.of(
                        "email",     email,
                        "role",      role,
                        "name",      name,
                        "companyId", companyId != null ? companyId : ""
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(getKey())
                .compact();
    }

    /**
     * Validate a token and return its claims.
     * Throws JwtException if invalid or expired.
     */
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Returns true if token is valid and not expired. */
    public boolean isValid(String token) {
        try {
            validateToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String getRole(String token) {
        return validateToken(token).get("role", String.class);
    }

    public String getUserId(String token) {
        return validateToken(token).getSubject();
    }

    public String getEmail(String token) {
        return validateToken(token).get("email", String.class);
    }

    public String getCompanyId(String token) {
        return validateToken(token).get("companyId", String.class);
    }

    // ── Private ───────────────────────────────────────────────

    private SecretKey getKey() {
        String secret = appProperties.getJwt().getSecret();
        // Pad to 32 bytes if shorter — prevents weak key errors
        while (secret.length() < 32) secret = secret + "0";
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}