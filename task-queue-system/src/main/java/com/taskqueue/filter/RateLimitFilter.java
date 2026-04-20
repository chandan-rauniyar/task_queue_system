package com.taskqueue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.config.AppProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Runs AFTER ApiKeyFilter (Order 2).
 * By this point, ClientContext is already set.
 *
 * Strategy: Redis sliding counter per client per minute.
 *
 * Redis key pattern: "rate:{apiKeyId}:{minute-bucket}"
 * e.g. "rate:key-abc-123:2024011514"  (yyyyMMddHHmm)
 *
 * On each request:
 *  INCR the counter → if > limit → return 429
 *  Set TTL to 60s so key auto-expires
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        // Skip rate limiting for admin requests and public paths
        ClientContext.ClientInfo client = ClientContext.get();

        if (client == null || client.isAdminRequest()) {
            chain.doFilter(request, response);
            return;
        }

        String apiKeyId = client.getApiKeyId();
        int limit = client.getRateLimitPerMin();

        // Bucket key = apiKeyId + current minute
        String minute = java.time.format.DateTimeFormatter
                .ofPattern("yyyyMMddHHmm")
                .format(LocalDateTime.now());
        String redisKey = "rate:" + apiKeyId + ":" + minute;

        try {
            // Atomically increment and get new value
            Long count = redisTemplate.opsForValue().increment(redisKey);

            // Set TTL on first request of this minute
            if (count != null && count == 1) {
                redisTemplate.expire(redisKey, Duration.ofSeconds(
                        appProperties.getRedis().getRateLimitWindow()
                ));
            }

            if (count != null && count > limit) {
                log.warn("Rate limit exceeded: apiKey={} count={} limit={}", apiKeyId, count, limit);
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                response.setHeader("X-RateLimit-Remaining", "0");
                response.setContentType("application/json");
                response.getWriter().write(
                        objectMapper.writeValueAsString(Map.of(
                                "success", false,
                                "error", "Rate limit exceeded. Max " + limit + " requests/minute.",
                                "retryAfter", 60
                        ))
                );
                return;
            }

            // Add rate limit headers to successful responses too
            if (count != null) {
                response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));
            }

        } catch (Exception e) {
            // Redis failure is non-fatal — allow the request through
            log.error("Rate limit check failed (Redis error), allowing request: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}