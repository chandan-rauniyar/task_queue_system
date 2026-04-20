package com.taskqueue.filter;

import lombok.Data;

/**
 * Holds the authenticated client info for the current HTTP request.
 * Set by ApiKeyFilter, read by controllers and services.
 * Cleared after every request — no memory leaks.
 *
 * Usage in a controller:
 *   String clientId = ClientContext.get().getApiKeyId();
 */
public class ClientContext {

    private static final ThreadLocal<ClientInfo> context = new ThreadLocal<>();

    public static void set(ClientInfo info) {
        context.set(info);
    }

    public static ClientInfo get() {
        return context.get();
    }

    public static void clear() {
        context.remove();
    }

    public static boolean isAdmin() {
        ClientInfo info = context.get();
        return info != null && info.isAdminRequest();
    }

    @Data
    public static class ClientInfo {
        private String apiKeyId;
        private String projectId;
        private String companyId;
        private String projectName;
        private String companyName;
        private int rateLimitPerMin;
        private boolean adminRequest; // true = request came from localhost admin panel
    }
}