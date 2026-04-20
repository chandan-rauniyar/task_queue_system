package com.taskqueue.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApiKeyResponse {

    private String id;
    private String label;
    private String projectId;
    private String projectName;

    // Full raw key — returned EXACTLY ONCE, never stored, never returned again
    private String rawKey;

    // Last 4 chars e.g. "...x2m9" — shown in UI for identification
    private String keyHint;

    // e.g. "tq_live_"
    private String keyPrefix;

    private Integer rateLimitPerMin;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    // Always shown so the admin knows to copy it now
    @Builder.Default
    private String warning = "Save this key now. It will NEVER be shown again.";
}