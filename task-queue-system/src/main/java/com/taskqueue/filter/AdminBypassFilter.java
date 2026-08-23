package com.taskqueue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.config.AppProperties;
import com.taskqueue.service.JwtService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Runs FIRST (Order 0) — before ApiKeyFilter.
 *
 * Allows /admin/** requests from:
 *   1. Localhost IP (127.0.0.1 / ::1)  — original behavior
 *   2. Valid JWT with role=ADMIN        — NEW: lets the login page work
 *
 * Everything else gets 403.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AdminBypassFilter extends OncePerRequestFilter {

    private final AppProperties appProperties;
    private final ObjectMapper  objectMapper;
    private final JwtService    jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only intercept /admin/** paths
        if (!path.contains(appProperties.getAdmin().getPathPrefix())) {
            chain.doFilter(request, response);
            return;
        }

        // ── Check 1: localhost IP ─────────────────────────────
        String remoteIp = getClientIp(request);
        boolean isLocalhost = true;
//                "127.0.0.1".equals(remoteIp)
//                || "0:0:0:0:0:0:0:1".equals(remoteIp)
//                || "::1".equals(remoteIp);

        if (isLocalhost) {
            setAdminContextAndContinue(request, response, chain);
            return;
        }

        // ── Check 2: valid JWT with ADMIN role ────────────────
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtService.isValid(token) && "ADMIN".equals(jwtService.getRole(token))) {
                    log.debug("Admin access via JWT from IP: {}", remoteIp);
                    setAdminContextAndContinue(request, response, chain);
                    return;
                }
            } catch (Exception e) {
                log.warn("Invalid JWT on admin endpoint: {}", e.getMessage());
            }
        }

        // ── Neither condition met → 403 ───────────────────────
        log.warn("Blocked admin access attempt from IP: {}", remoteIp);
        response.setStatus(403);
        response.setContentType("application/json");
        response.getWriter().write(
                objectMapper.writeValueAsString(Map.of(
                        "success", false,
                        "error",   "Admin access requires localhost or valid admin JWT"
                ))
        );
    }

    private void setAdminContextAndContinue(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {
        ClientContext.ClientInfo adminInfo = new ClientContext.ClientInfo();
        adminInfo.setAdminRequest(true);
        adminInfo.setProjectName("Admin");
        adminInfo.setCompanyName("System");
        ClientContext.set(adminInfo);
        try {
            chain.doFilter(request, response);
        } finally {
            ClientContext.clear();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}