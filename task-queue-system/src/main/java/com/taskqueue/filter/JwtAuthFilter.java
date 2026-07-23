package com.taskqueue.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.service.JwtService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Order 1 — runs after AdminBypassFilter, before ApiKeyFilter.
 *
 * Handles /client/** endpoints — requires a valid JWT Bearer token.
 * Extracts companyId from JWT and stores in ClientContext so every
 * service can filter data to just that company.
 *
 * CLIENT users can only see their own company's data.
 * ADMIN users can call /client/** too (they see everything).
 */
@Slf4j
@Component
@Order(1)  // runs before ApiKeyFilter (Order 2)
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService   jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest  request,
            HttpServletResponse response,
            FilterChain         chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only intercept /client/** paths
        if (!path.contains("/client/")) {
            chain.doFilter(request, response);
            return;
        }

        // Require Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, 401, "Authentication required. Please log in.");
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtService.isValid(token)) {
            sendError(response, 401, "Session expired. Please log in again.");
            return;
        }

        // Extract claims from JWT
        String role      = jwtService.getRole(token);
        String userId    = jwtService.getUserId(token);
        String email     = jwtService.getEmail(token);
        String companyId = jwtService.getCompanyId(token);

        // Build ClientContext
        ClientContext.ClientInfo info = new ClientContext.ClientInfo();
        info.setAdminRequest("ADMIN".equals(role));
        info.setCompanyId(companyId != null && !companyId.isBlank() ? companyId : null);
        info.setProjectName("JWT");
        info.setCompanyName(email);
        ClientContext.set(info);

        log.debug("JWT auth: role={} email={} companyId={}", role, email, companyId);

        try {
            chain.doFilter(request, response);
        } finally {
            ClientContext.clear();
        }
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success",   false,
                "error",     message,
                "timestamp", LocalDateTime.now().toString()
        )));
    }
}