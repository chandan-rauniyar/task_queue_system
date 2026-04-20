package com.taskqueue.service;

import com.taskqueue.config.AppProperties;
import com.taskqueue.dto.JobRequest;
import com.taskqueue.dto.JobResponse;
import com.taskqueue.exception.TaskQueueException;
import com.taskqueue.filter.ClientContext;
import com.taskqueue.model.*;
import com.taskqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository     jobRepository;
    private final ApiKeyRepository  apiKeyRepository;
    private final ProjectRepository projectRepository;
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;
    private final AppProperties     appProperties;

    // ── Enqueue ───────────────────────────────────────────────

    @Transactional
    public JobResponse enqueue(JobRequest request) {
        ClientContext.ClientInfo client = ClientContext.get();

        Project project = projectRepository.findById(client.getProjectId())
                .orElseThrow(() -> TaskQueueException.notFound("Project", client.getProjectId()));

        ApiKey apiKey = apiKeyRepository.findById(client.getApiKeyId())
                .orElseThrow(() -> TaskQueueException.notFound("ApiKey", client.getApiKeyId()));

        // Idempotency check
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            jobRepository.findByProjectIdAndIdempotencyKey(
                    client.getProjectId(), request.getIdempotencyKey()
            ).ifPresent(existing -> {
                throw TaskQueueException.conflict(
                        "Job with idempotency key '" + request.getIdempotencyKey()
                                + "' already exists. JobId: " + existing.getId()
                );
            });
        }

        Job.Priority priority = request.getPriority() != null
                ? request.getPriority()
                : Job.Priority.NORMAL;

        Job job = Job.builder()
                .project(project)
                .apiKey(apiKey)
                .type(request.getType())
                .payload(request.getPayload())
                .status(Job.Status.QUEUED)
                .priority(priority)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .callbackUrl(request.getCallbackUrl())
                .idempotencyKey(request.getIdempotencyKey())
                .scheduledAt(request.getScheduledAt())
                .build();

        job = jobRepository.save(job);
        log.info("Job saved: id={} type={} priority={} project={}",
                job.getId(), job.getType(), job.getPriority(), project.getName());

        // Build Kafka event — use client context values (already plain strings)
        JobEvent event = JobEvent.builder()
                .jobId(job.getId())
                .projectId(client.getProjectId())
                .companyId(client.getCompanyId())
                .apiKeyId(client.getApiKeyId())
                .type(job.getType())
                .payload(job.getPayload())
                .priority(job.getPriority())
                .retryCount(0)
                .maxRetries(job.getMaxRetries())
                .callbackUrl(job.getCallbackUrl())
                .smtpPurpose(request.getSmtpPurpose())
                .createdAt(job.getCreatedAt())
                .scheduledAt(job.getScheduledAt())
                .build();

        String topic = topicForPriority(job.getPriority());
        kafkaTemplate.send(topic, job.getId(), event);
        log.info("Job published to Kafka: jobId={} topic={}", job.getId(), topic);

        return JobResponse.from(job);
    }

    // ── Get single job ────────────────────────────────────────

    @Transactional(readOnly = true)
    public Job getJob(String jobId) {
        // JOIN FETCH — loads Project + Company + ApiKey in one query
        Job job = jobRepository.findByIdWithRelations(jobId)
                .orElseThrow(() -> TaskQueueException.notFound("Job", jobId));

        // Client can only see their own jobs
        ClientContext.ClientInfo client = ClientContext.get();
        if (!ClientContext.isAdmin()) {
            if (!job.getProject().getId().equals(client.getProjectId())) {
                throw TaskQueueException.forbidden("Access denied to job: " + jobId);
            }
        }

        return job;
    }

    // ── List jobs ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Job> listJobs(Job.Status status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        ClientContext.ClientInfo client = ClientContext.get();

        if (status != null) {
            return jobRepository.findByProjectIdAndStatus(
                    client.getProjectId(), status, pageable);
        }
        return jobRepository.findByProjectId(client.getProjectId(), pageable);
    }

    // ── Retry ─────────────────────────────────────────────────

    @Transactional
    public String retryJob(String jobId) {
        // JOIN FETCH — need project + company + apiKey for Kafka event
        Job job = jobRepository.findByIdWithRelations(jobId)
                .orElseThrow(() -> TaskQueueException.notFound("Job", jobId));

        // Security check
        ClientContext.ClientInfo client = ClientContext.get();
        if (!ClientContext.isAdmin()) {
            if (!job.getProject().getId().equals(client.getProjectId())) {
                throw TaskQueueException.forbidden("Access denied");
            }
        }

        if (!job.canRetry()) {
            throw TaskQueueException.badRequest(
                    "Job cannot be retried. Status=" + job.getStatus()
                            + ", attempts=" + job.getRetryCount() + "/" + job.getMaxRetries());
        }

        job.setStatus(Job.Status.QUEUED);
        job.setErrorMessage(null);
        jobRepository.save(job);

        // Extract values while @Transactional session is open
        String   jobIdStr    = job.getId();
        String   projectId   = job.getProject().getId();
        String   companyId   = job.getProject().getCompany().getId();
        String   apiKeyId    = job.getApiKey().getId();
        String   jobType     = job.getType();
        String   callbackUrl = job.getCallbackUrl();
        Integer  retryCount  = job.getRetryCount();
        Integer  maxRetries  = job.getMaxRetries();
        Job.Priority priority = job.getPriority();
        Map<String, Object> payload = job.getPayload();

        JobEvent event = JobEvent.builder()
                .jobId(jobIdStr)
                .projectId(projectId)
                .companyId(companyId)
                .apiKeyId(apiKeyId)
                .type(jobType)
                .payload(payload)
                .priority(priority)
                .retryCount(retryCount)
                .maxRetries(maxRetries)
                .callbackUrl(callbackUrl)
                .build();

        kafkaTemplate.send(topicForPriority(priority), jobIdStr, event);
        log.info("Job manually re-queued: jobId={}", jobId);
        return "Job re-queued for processing";
    }

    // ── Helper ────────────────────────────────────────────────

    private String topicForPriority(Job.Priority priority) {
        AppProperties.Kafka.Topics t = appProperties.getKafka().getTopics();
        return switch (priority) {
            case HIGH  -> t.getHighPriority();
            case LOW   -> t.getLowPriority();
            default    -> t.getNormalPriority();
        };
    }
}