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
 * If the request path starts with /admin/ AND comes from localhost,
 * set ClientContext as an admin request and skip ApiKeyFilter entirely.
 *
 * If someone tries to call /admin/** from a non-localhost IP,
 * return 403 immediately — even if they have a valid API key.
 *
 * This is how your React admin panel (localhost:3000) talks
 * to your backend (localhost:8080) without needing an API key.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AdminBypassFilter extends OncePerRequestFilter {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only intercept /admin/** paths
        if (!path.contains(appProperties.getAdmin().getPathPrefix())) {
            chain.doFilter(request, response);
            return;
        }

        // Check 2: valid JWT with ADMIN role
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtService.isValid(token) && "ADMIN".equals(jwtService.getRole(token))) {
                    ClientContext.ClientInfo info = new ClientContext.ClientInfo();
                    info.setAdminRequest(true);
                    info.setProjectName("Admin");
                    info.setCompanyName("System");
                    ClientContext.set(info);
                    chain.doFilter(request, response);
                    ClientContext.clear();
                    return;
                }
            } catch (Exception ignored) {}
        }

        String remoteIp = getClientIp(request);
        String allowedIp = appProperties.getAdmin().getAllowedIp();

        boolean isLocalhost = "127.0.0.1".equals(remoteIp)
                || "0:0:0:0:0:0:0:1".equals(remoteIp)  // IPv6 localhost
                || "::1".equals(remoteIp);

        if (!isLocalhost) {
            log.warn("Blocked admin access attempt from IP: {}", remoteIp);
            response.setStatus(403);
            response.setContentType("application/json");
            response.getWriter().write(
                    objectMapper.writeValueAsString(Map.of(
                            "success", false,
                            "error", "Admin panel is only accessible from localhost"
                    ))
            );
            return;
        }

        // Localhost request — set admin context, skip ApiKeyFilter
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
        // Check X-Forwarded-For in case of proxy
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}