package com.taskqueue.dto;

import com.taskqueue.model.ApiKey;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiKeySummaryResponse {

    private String id;
    private String label;
    private String keyHint;     // "...x2m9" — shown in UI, safe to display
    private String keyPrefix;   // "tq_live_"
    private Boolean isActive;
    private Integer rateLimitPerMin;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private String projectId;
    private String projectName;

    public static ApiKeySummaryResponse from(ApiKey key) {
        return ApiKeySummaryResponse.builder()
                .id(key.getId())
                .label(key.getLabel())
                .keyHint(key.getKeyHint())
                .keyPrefix(key.getKeyPrefix())
                .isActive(key.getIsActive())
                .rateLimitPerMin(key.getRateLimitPerMin())
                .lastUsedAt(key.getLastUsedAt())
                .expiresAt(key.getExpiresAt())
                .createdAt(key.getCreatedAt())
                .projectId(key.getProject().getId())
                .projectName(key.getProject().getName())
                .build();
    }
}