package com.taskqueue.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateApiKeyRequest {

    @NotBlank(message = "projectId is required")
    private String projectId;

    @NotBlank(message = "label is required")
    @Size(max = 100, message = "label must not exceed 100 characters")
    private String label;

    @Min(value = 1, message = "rateLimitPerMin must be at least 1")
    @Max(value = 10000, message = "rateLimitPerMin must not exceed 10000")
    @Builder.Default
    private Integer rateLimitPerMin = 100;

    // NULL = never expires
    private LocalDateTime expiresAt;

    // "live" → prefix "tq_live_"
    // "dev"  → prefix "tq_dev_"
    @Builder.Default
    private String environment = "live";
}