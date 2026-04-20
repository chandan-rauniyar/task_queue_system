package com.taskqueue.dto;

import com.taskqueue.model.Job;
import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobRequest {

    @NotBlank(message = "type is required")
    @Size(max = 100, message = "type must not exceed 100 characters")
    private String type;

    @NotNull(message = "payload is required")
    private Map<String, Object> payload;

    private String callbackUrl;

    @Builder.Default
    private Job.Priority priority = Job.Priority.NORMAL;

    // Client sets this to prevent submitting the same job twice
    private String idempotencyKey;

    @Min(value = 0, message = "maxRetries must be 0 or more")
    @Max(value = 10, message = "maxRetries must not exceed 10")
    @Builder.Default
    private Integer maxRetries = 3;

    // For SEND_EMAIL jobs — picks SMTP config by purpose
    // Values: SUPPORT, BILLING, NOREPLY, ALERT, CUSTOM
    private String smtpPurpose;

    // NULL = run now. Set for scheduled future jobs (Phase 3)
    private LocalDateTime scheduledAt;
}