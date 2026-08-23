package com.taskqueue.dto;

import com.taskqueue.model.DeadLetterJob;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Safe DTO for DeadLetterJob — never serializes raw Hibernate proxies.
 * Extracts only what the frontend needs from the entity graph.
 */
@Data
@Builder
public class DeadLetterJobResponse {

    private String        id;
    private String        jobId;
    private String        jobType;
    private String        projectId;
    private String        projectName;
    private String        companyId;
    private String        companyName;
    private String        failureReason;
    private Integer       retryCount;
    private LocalDateTime failedAt;
    private LocalDateTime replayedAt;
    private String        replayedJobId;
    private boolean       replayed;

    // Payload snapshot at time of failure
    private Map<String, Object> originalPayload;

    public static DeadLetterJobResponse from(DeadLetterJob dlq) {
        // Safely access nested relations — job must be loaded via JOIN FETCH
        var job     = dlq.getJob();
        var project = job  != null ? job.getProject()        : null;
        var company = project != null ? project.getCompany() : null;

        return DeadLetterJobResponse.builder()
                .id(dlq.getId())
                .jobId(job != null ? job.getId() : null)
                .jobType(job != null ? job.getType() : null)
                .projectId(project != null ? project.getId() : null)
                .projectName(project != null ? project.getName() : null)
                .companyId(company != null ? company.getId() : null)
                .companyName(company != null ? company.getName() : null)
                .failureReason(dlq.getFailureReason())
                .retryCount(dlq.getRetryCount())
                .failedAt(dlq.getFailedAt())
                .replayedAt(dlq.getReplayedAt())
                .replayedJobId(dlq.getReplayedJobId())
                .replayed(dlq.isReplayed())
                .originalPayload(dlq.getOriginalPayload())
                .build();
    }
}