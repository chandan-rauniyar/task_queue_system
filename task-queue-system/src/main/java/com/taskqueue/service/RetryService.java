package com.taskqueue.service;

import com.taskqueue.config.AppProperties;
import com.taskqueue.model.*;
import com.taskqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetryService {

    private final JobRepository        jobRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;
    private final AppProperties        appProperties;
    private final WebhookService       webhookService;

    /**
     * Called by a worker after a job fails.
     * Loads the job with JOIN FETCH so all relations are available.
     * Decides whether to retry or send to DLQ.
     */
    @Transactional
    public void handleFailure(String jobId, String errorMessage) {

        // Use JOIN FETCH — loads Project, Company, ApiKey in one query
        Job job = jobRepository.findByIdWithRelations(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        int newRetryCount = job.getRetryCount() + 1;
        job.setRetryCount(newRetryCount);
        job.setErrorMessage(errorMessage);

        if (newRetryCount >= job.getMaxRetries()) {
            moveToDlq(job, errorMessage);
        } else {
            job.setStatus(Job.Status.FAILED);
            jobRepository.save(job);

            long delaySeconds = calculateDelay(newRetryCount);
            log.warn("Job failed — scheduling retry {} of {} in {}s: jobId={}",
                    newRetryCount, job.getMaxRetries(), delaySeconds, jobId);

            scheduleRetry(job, delaySeconds);
        }
    }

    /**
     * Moves a job to Dead Letter Queue after all retries exhausted.
     */
    @Transactional
    public void moveToDlq(Job job, String errorMessage) {
        log.error("Job moving to DLQ: jobId={} retries={}",
                job.getId(), job.getRetryCount());

        job.setStatus(Job.Status.DEAD);
        job.setErrorMessage(errorMessage);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        DeadLetterJob dlqEntry = DeadLetterJob.builder()
                .job(job)
                .originalPayload(job.getPayload())
                .failureReason(errorMessage)
                .retryCount(job.getRetryCount())
                .build();

        deadLetterRepository.save(dlqEntry);
        log.info("DLQ entry created: jobId={}", job.getId());

        // Fire failure webhook — extract values NOW before async
        if (job.getCallbackUrl() != null) {
            String callbackUrl = job.getCallbackUrl();
            String jobId       = job.getId();
            webhookService.fireAsync(callbackUrl, jobId, Job.Status.DEAD, errorMessage);
        }
    }

    /**
     * Re-publishes job to Kafka after delay.
     * CRITICAL: extract ALL values from job BEFORE entering virtual thread.
     * Hibernate session closes after this method returns — lazy proxies
     * cannot be accessed inside the thread.
     */
    private void scheduleRetry(Job job, long delaySeconds) {
        String topic = getTopicForPriority(job.getPriority());

        // ── Extract everything HERE while session is still open ──
        String   jobId       = job.getId();
        String   projectId   = job.getProject().getId();
        String   companyId   = job.getProject().getCompany().getId();
        String   apiKeyId    = job.getApiKey().getId();
        String   jobType     = job.getType();
        String   callbackUrl = job.getCallbackUrl();
        Integer  retryCount  = job.getRetryCount();
        Integer  maxRetries  = job.getMaxRetries();
        Job.Priority priority = job.getPriority();
        Map<String, Object> payload = job.getPayload();
        LocalDateTime createdAt = job.getCreatedAt();
        // ── End extraction ────────────────────────────────────────

        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(delaySeconds * 1000);

                // Build event using only local variables — no entity access
                JobEvent retryEvent = JobEvent.builder()
                        .jobId(jobId)
                        .projectId(projectId)
                        .companyId(companyId)
                        .apiKeyId(apiKeyId)
                        .type(jobType)
                        .payload(payload)
                        .priority(priority)
                        .retryCount(retryCount)
                        .maxRetries(maxRetries)
                        .callbackUrl(callbackUrl)
                        .createdAt(createdAt)
                        .build();

                kafkaTemplate.send(topic, jobId, retryEvent);
                log.info("Retry published: jobId={} attempt={} topic={}",
                        jobId, retryCount, topic);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Retry interrupted: jobId={}", jobId);
            }
        });
    }

    // Exponential backoff: 30s → 60s → 120s
    private long calculateDelay(int attempt) {
        int initial    = appProperties.getRetry().getInitialDelaySeconds();
        int multiplier = appProperties.getRetry().getMultiplier();
        return (long) (initial * Math.pow(multiplier, attempt - 1));
    }

    private String getTopicForPriority(Job.Priority priority) {
        AppProperties.Kafka.Topics t = appProperties.getKafka().getTopics();
        return switch (priority) {
            case HIGH  -> t.getHighPriority();
            case LOW   -> t.getLowPriority();
            default    -> t.getNormalPriority();
        };
    }
}