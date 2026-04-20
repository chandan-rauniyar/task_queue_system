package com.taskqueue.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricsResponse {

    // Job counts by status
    private long totalJobs;
    private long queuedJobs;
    private long runningJobs;
    private long successJobs;
    private long failedJobs;
    private long deadJobs;

    // DLQ
    private long pendingDlq;      // dead jobs not yet replayed

    // Entity totals
    private long totalCompanies;
    private long totalProjects;
    private long totalApiKeys;
}