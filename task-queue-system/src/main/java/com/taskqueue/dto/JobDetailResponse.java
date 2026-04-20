package com.taskqueue.dto;

import com.taskqueue.model.Job;
import lombok.*;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDetailResponse {

    private String jobId;
    private String type;
    private Map<String, Object> payload;

    private Job.Status status;
    private Job.Priority priority;

    private Integer retryCount;
    private Integer maxRetries;
    private boolean canRetry;

    private String callbackUrl;
    private String idempotencyKey;

    private String projectId;
    private String projectName;
    private String companyName;

    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    private String errorMessage;

    public static JobDetailResponse from(Job job) {
        return JobDetailResponse.builder()
                .jobId(job.getId())
                .type(job.getType())
                .payload(job.getPayload())
                .status(job.getStatus())
                .priority(job.getPriority())
                .retryCount(job.getRetryCount())
                .maxRetries(job.getMaxRetries())
                .canRetry(job.canRetry())
                .callbackUrl(job.getCallbackUrl())
                .idempotencyKey(job.getIdempotencyKey())
                .projectId(job.getProject().getId())
                .projectName(job.getProject().getName())
                .companyName(job.getProject().getCompany().getName())
                .createdAt(job.getCreatedAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .errorMessage(job.getErrorMessage())
                .build();
    }
}