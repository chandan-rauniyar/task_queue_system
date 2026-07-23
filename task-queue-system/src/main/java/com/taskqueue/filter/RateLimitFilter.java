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
 * Rate limiting — only applies to API key requests (X-API-Key header).
 * JWT requests (/client/**) and admin requests are NOT rate limited here.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        ClientContext.ClientInfo client = ClientContext.get();

        // Skip: no context, admin requests, or JWT-based client requests (no apiKeyId)
        if (client == null
                || client.isAdminRequest()
                || client.getApiKeyId() == null
                || client.getApiKeyId().isBlank()
                || client.getRateLimitPerMin() <= 0) {
            chain.doFilter(request, response);
            return;
        }

        String apiKeyId = client.getApiKeyId();
        int    limit    = client.getRateLimitPerMin();

        String minute = java.time.format.DateTimeFormatter
                .ofPattern("yyyyMMddHHmm")
                .format(LocalDateTime.now());
        String redisKey = "rate:" + apiKeyId + ":" + minute;

        try {
            Long count = redisTemplate.opsForValue().increment(redisKey);

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
                response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                        "success",    false,
                        "error",      "Rate limit exceeded. Max " + limit + " requests/minute.",
                        "retryAfter", 60
                )));
                return;
            }

            if (count != null) {
                response.setHeader("X-RateLimit-Limit",     String.valueOf(limit));
                response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - count)));
            }

        } catch (Exception e) {
            log.error("Rate limit Redis error, allowing request: {}", e.getMessage());
        }

        chain.doFilter(request, response);
    }
}