package com.taskqueue.model;

import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * This is the Kafka message payload.
 * NOT a database entity — only lives inside Kafka messages.
 *
 * Producer (JobService) → serializes this to JSON → Kafka topic
 * Consumer (WorkerDispatcher) → deserializes from JSON → processes
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobEvent {

    private String jobId;
    private String projectId;
    private String companyId;   // workers need this to look up correct SMTP config
    private String apiKeyId;

    private String type;        // SEND_EMAIL, GENERATE_PDF, etc.
    private Map<String, Object> payload;

    private Job.Priority priority;
    private Integer retryCount;
    private Integer maxRetries;
    private String callbackUrl;

    // For SEND_EMAIL jobs — which SMTP config to use
    // e.g. "SUPPORT" → SELECT * FROM smtp_configs WHERE company_id=? AND purpose='SUPPORT'
    private String smtpPurpose;

    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt;
}