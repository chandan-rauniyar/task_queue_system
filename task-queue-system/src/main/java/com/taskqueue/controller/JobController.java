package com.taskqueue.controller;

import com.taskqueue.dto.*;
import com.taskqueue.model.Job;
import com.taskqueue.service.JobService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public job API — requires X-API-Key header on every request.
 * Used by external apps (Swiggy, BillStack, etc.)
 *
 * Base path: /api/v1/jobs
 */
@Slf4j
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Enqueue and manage background jobs")
public class JobController {

    private final JobService jobService;

    // ── POST /jobs ────────────────────────────────────────────
    // Enqueue a new job. Returns immediately with jobId.
    // Job is processed asynchronously by workers.

    @PostMapping
    @Operation(
            summary = "Enqueue a new job",
            description = "Submits a background job. Returns jobId immediately — processing is async."
    )
    public ResponseEntity<ApiResponse<JobResponse>> enqueue(
            @Valid @RequestBody JobRequest request
    ) {
        JobResponse response = jobService.enqueue(request);
        return ResponseEntity
                .status(HttpStatus.ACCEPTED)    // 202 — accepted but not yet processed
                .body(ApiResponse.ok(response));
    }

    // ── GET /jobs/{jobId} ─────────────────────────────────────
    // Poll status of a specific job.

    @GetMapping("/{jobId}")
    @Operation(summary = "Get job status and details")
    public ResponseEntity<ApiResponse<JobDetailResponse>> getJob(
            @PathVariable String jobId
    ) {
        Job job = jobService.getJob(jobId);
        return ResponseEntity.ok(ApiResponse.ok(JobDetailResponse.from(job)));
    }

    // ── GET /jobs ─────────────────────────────────────────────
    // List jobs for the authenticated project (from X-API-Key context).

    @GetMapping
    @Operation(
            summary = "List jobs for your project",
            description = "Returns paginated jobs. Filter by status. Only your project's jobs returned."
    )
    public ResponseEntity<ApiResponse<Page<JobDetailResponse>>> listJobs(
            @RequestParam(required = false) Job.Status status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<JobDetailResponse> jobs = jobService
                .listJobs(status, page, size)
                .map(JobDetailResponse::from);
        return ResponseEntity.ok(ApiResponse.ok(jobs));
    }

    // ── POST /jobs/{jobId}/retry ──────────────────────────────
    // Manually retry a failed job.

    @PostMapping("/{jobId}/retry")
    @Operation(summary = "Manually retry a failed job")
    public ResponseEntity<ApiResponse<Map<String, String>>> retryJob(
            @PathVariable String jobId
    ) {
        String result = jobService.retryJob(jobId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "jobId",   jobId,
                "status",  "QUEUED",
                "message", result
        )));
    }
}