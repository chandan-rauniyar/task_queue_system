package com.taskqueue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.config.AppProperties;
import com.taskqueue.model.ApiKey;
import com.taskqueue.repository.ApiKeyRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Validates X-API-Key on every request except /admin/** and public paths.
 *
 * Fix for LazyInitializationException:
 * We use findByKeyHashWithProjectAndCompany() which does JOIN FETCH
 * so Project and Company are loaded in one query while session is open.
 * We then immediately read all needed values (projectId, companyId etc.)
 * into the ClientContext before the session closes.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER  = "X-API-Key";
    private static final String REDIS_KEY_PREFIX = "apikey:";

    private final ApiKeyRepository              apiKeyRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties                 appProperties;
    private final ObjectMapper                  objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Skip auth for admin, swagger, actuator
        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        String rawKey = request.getHeader(API_KEY_HEADER);

        if (rawKey == null || rawKey.isBlank()) {
            sendError(response, 401, "Missing X-API-Key header");
            return;
        }

        String keyHash = sha256(rawKey);

        // ── Load API key ──────────────────────────────────────
        // We do NOT use Redis cache for the full object anymore
        // because deserializing lazy proxies from Redis causes the
        // same LazyInitializationException.
        // Instead: always load from DB with JOIN FETCH (fast enough,
        // Postgres handles it well, and we cache just the validation flag).

        // Check Redis for a simple "is this key valid?" boolean
        Boolean cachedValid = getCachedValidity(keyHash);
        if (Boolean.FALSE.equals(cachedValid)) {
            sendError(response, 401, "Invalid or inactive API key");
            return;
        }

        // Load from DB with JOIN FETCH — gets ApiKey + Project + Company in 1 query
        Optional<ApiKey> found = apiKeyRepository
                .findByKeyHashWithProjectAndCompany(keyHash);

        if (found.isEmpty()) {
            log.warn("Invalid API key attempt — hash not found");
            cacheValidity(keyHash, false);
            sendError(response, 401, "Invalid API key");
            return;
        }

        ApiKey apiKey = found.get();

        if (!apiKey.isValid()) {
            cacheValidity(keyHash, false);
            sendError(response, 401, "API key is inactive or expired");
            return;
        }

        // ── Read all values NOW while session is open ─────────
        // This is the critical fix — we extract all strings from the
        // entity graph before the Hibernate session closes.
        // Never pass the entity itself to ClientContext.
        String apiKeyId    = apiKey.getId();
        String projectId   = apiKey.getProject().getId();       // safe — JOIN FETCH loaded this
        String projectName = apiKey.getProject().getName();     // safe
        String companyId   = apiKey.getProject().getCompany().getId();   // safe — JOIN FETCH
        String companyName = apiKey.getProject().getCompany().getName(); // safe
        int    rateLimit   = apiKey.getRateLimitPerMin();

        // Cache valid flag for 5 minutes
        cacheValidity(keyHash, true);

        // ── Set ClientContext ─────────────────────────────────
        ClientContext.ClientInfo info = new ClientContext.ClientInfo();
        info.setApiKeyId(apiKeyId);
        info.setProjectId(projectId);
        info.setProjectName(projectName);
        info.setCompanyId(companyId);
        info.setCompanyName(companyName);
        info.setRateLimitPerMin(rateLimit);
        info.setAdminRequest(false);
        ClientContext.set(info);

        // Update last used timestamp in background (fire and forget)
        final String keyId = apiKeyId;
        Thread.ofVirtual().start(() ->
                apiKeyRepository.updateLastUsedAt(keyId, LocalDateTime.now())
        );

        try {
            chain.doFilter(request, response);
        } finally {
            ClientContext.clear();
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private boolean isPublicPath(String path) {
        return path.contains("/admin/")
                || path.contains("/auth/")
                || path.contains("/actuator")
                || path.contains("/swagger-ui")
                || path.contains("/api-docs")
                || path.contains("/v3/api-docs");
    }

    private Boolean getCachedValidity(String keyHash) {
        try {
            Object val = redisTemplate.opsForValue()
                    .get(REDIS_KEY_PREFIX + keyHash);
            if (val instanceof Boolean) return (Boolean) val;
            if ("true".equals(val))    return true;
            if ("false".equals(val))   return false;
        } catch (Exception e) {
            log.warn("Redis read failed: {}", e.getMessage());
        }
        return null; // null = not cached, must hit DB
    }

    private void cacheValidity(String keyHash, boolean valid) {
        try {
            long ttl = valid
                    ? appProperties.getRedis().getApiKeyCacheTtl()  // 300s for valid keys
                    : 60L;                                           // 60s for invalid keys
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + keyHash,
                    valid,
                    Duration.ofSeconds(ttl)
            );
        } catch (Exception e) {
            log.warn("Redis write failed: {}", e.getMessage());
        }
    }

    private void sendError(HttpServletResponse response,
                           int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of(
                        "success",   false,
                        "error",     message,
                        "timestamp", LocalDateTime.now().toString()
                ))
        );
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }
}