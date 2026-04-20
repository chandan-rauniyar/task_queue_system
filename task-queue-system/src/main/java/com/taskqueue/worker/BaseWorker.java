package com.taskqueue.worker;

import com.taskqueue.model.*;
import com.taskqueue.repository.JobRepository;
import com.taskqueue.service.RetryService;
import com.taskqueue.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Abstract base for all workers.
 * Uses findByIdWithRelations so project/company/apiKey are all loaded.
 *
 * Lifecycle:
 *   load job (JOIN FETCH) → mark RUNNING → process() → mark SUCCESS/FAILED
 */
@Slf4j
@RequiredArgsConstructor
public abstract class BaseWorker {

    protected final JobRepository  jobRepository;
    protected final RetryService   retryService;
    protected final WebhookService webhookService;

    @Transactional
    public void execute(JobEvent event) {
        String jobId = event.getJobId();
        log.info("Worker starting: jobId={} type={} attempt={}/{}",
                jobId, event.getType(),
                event.getRetryCount() + 1, event.getMaxRetries());

        // JOIN FETCH — loads Project, Company, ApiKey eagerly
        Job job = jobRepository.findByIdWithRelations(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        // Mark RUNNING
        job.setStatus(Job.Status.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        try {
            process(event);

            // Mark SUCCESS
            job.setStatus(Job.Status.SUCCESS);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            log.info("Job SUCCESS: jobId={} type={}", jobId, event.getType());

            // Fire webhook — extract callbackUrl while session is still open
            String callbackUrl = job.getCallbackUrl();
            if (callbackUrl != null) {
                webhookService.fireAsync(callbackUrl, jobId, Job.Status.SUCCESS, null);
            }

        } catch (Exception e) {
            log.error("Job FAILED: jobId={} error={}", jobId, e.getMessage());
            // RetryService loads the job fresh with JOIN FETCH internally
            retryService.handleFailure(jobId, e.getMessage());
        }
    }

    protected abstract void process(JobEvent event) throws Exception;
}