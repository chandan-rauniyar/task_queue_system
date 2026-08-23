package com.taskqueue.service;

import com.taskqueue.config.AppProperties;
import com.taskqueue.dto.DeadLetterJobResponse;
import com.taskqueue.exception.TaskQueueException;
import com.taskqueue.model.*;
import com.taskqueue.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DlqService {

    private final DeadLetterRepository            deadLetterRepository;
    private final JobRepository                   jobRepository;
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;
    private final AppProperties                   appProperties;

    /**
     * Returns DLQ entries as safe DTOs — no raw Hibernate proxies.
     * Uses findAllPendingWithRelations() JOIN FETCH to load everything in one query.
     */
    @Transactional(readOnly = true)
    public Page<DeadLetterJobResponse> listPending(int page, int size) {
        // JOIN FETCH loads all relations — safe to call .from() outside session
        List<DeadLetterJob> all = deadLetterRepository.findAllPendingWithRelations();

        // Manual pagination since JOIN FETCH + Page has N+1 issues
        int total = all.size();
        int start = Math.min(page * size, total);
        int end   = Math.min(start + size, total);
        List<DeadLetterJobResponse> pageContent = all.subList(start, end)
                .stream()
                .map(DeadLetterJobResponse::from)
                .toList();

        return new PageImpl<>(pageContent, PageRequest.of(page, size, Sort.by("failedAt").descending()), total);
    }

    @Transactional
    public String replaySingle(String dlqId) {
        // Use JOIN FETCH version so relations are loaded
        DeadLetterJob dlqJob = deadLetterRepository.findByIdWithRelations(dlqId)
                .orElseThrow(() -> TaskQueueException.notFound("DLQ entry", dlqId));

        if (dlqJob.isReplayed()) {
            throw TaskQueueException.badRequest("Already replayed on " + dlqJob.getReplayedAt());
        }

        // Load job WITH all relations for republish
        Job job = jobRepository.findByIdWithRelations(dlqJob.getJob().getId())
                .orElseThrow(() -> TaskQueueException.notFound("Job", dlqJob.getJob().getId()));

        // Reset job state
        job.setStatus(Job.Status.QUEUED);
        job.setRetryCount(0);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        jobRepository.save(job);

        // Mark DLQ replayed
        dlqJob.setReplayedAt(LocalDateTime.now());
        dlqJob.setReplayedJobId(job.getId());
        deadLetterRepository.save(dlqJob);

        republishToKafka(job);

        log.info("DLQ replayed: dlqId={} jobId={}", dlqId, job.getId());
        return job.getId();
    }

    @Transactional
    public int replayAll() {
        List<DeadLetterJob> pending = deadLetterRepository.findAllPendingWithRelations();
        int count = 0;
        for (DeadLetterJob dlq : pending) {
            try {
                replaySingle(dlq.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to replay dlqId={}: {}", dlq.getId(), e.getMessage());
            }
        }
        log.info("Bulk replay done: {} jobs re-queued", count);
        return count;
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return deadLetterRepository.countByReplayedAtIsNull();
    }

    private void republishToKafka(Job job) {
        // Extract all values while @Transactional session is open
        String jobId       = job.getId();
        String projectId   = job.getProject().getId();
        String companyId   = job.getProject().getCompany().getId();
        String apiKeyId    = job.getApiKey().getId();
        String jobType     = job.getType();
        String callbackUrl = job.getCallbackUrl();
        Job.Priority priority   = job.getPriority();
        Map<String, Object> payload = job.getPayload();
        LocalDateTime createdAt     = job.getCreatedAt();

        JobEvent event = JobEvent.builder()
                .jobId(jobId).projectId(projectId).companyId(companyId)
                .apiKeyId(apiKeyId).type(jobType).payload(payload)
                .priority(priority).retryCount(0).maxRetries(job.getMaxRetries())
                .callbackUrl(callbackUrl).createdAt(createdAt)
                .build();

        String topic = switch (priority) {
            case HIGH  -> appProperties.getKafka().getTopics().getHighPriority();
            case LOW   -> appProperties.getKafka().getTopics().getLowPriority();
            default    -> appProperties.getKafka().getTopics().getNormalPriority();
        };

        kafkaTemplate.send(topic, jobId, event);
        log.info("Job republished: jobId={} topic={}", jobId, topic);
    }
}