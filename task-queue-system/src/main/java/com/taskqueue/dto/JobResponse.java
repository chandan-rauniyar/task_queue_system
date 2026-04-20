package com.taskqueue.dto;

import com.taskqueue.model.Job;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobResponse {

    // Client stores this to poll status later
    private String jobId;

    private Job.Status status;
    private Job.Priority priority;
    private String type;
    private LocalDateTime createdAt;

    // Tells client exactly where to poll for status
    private String statusUrl;

    public static JobResponse from(Job job) {
        return JobResponse.builder()
                .jobId(job.getId())
                .status(job.getStatus())
                .priority(job.getPriority())
                .type(job.getType())
                .createdAt(job.getCreatedAt())
                .statusUrl("/api/v1/jobs/" + job.getId())
                .build();
    }
}