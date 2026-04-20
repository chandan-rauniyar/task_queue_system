package com.taskqueue.service;

import com.taskqueue.config.AppProperties;
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

    private final DeadLetterRepository deadLetterRepository;
    private final JobRepository        jobRepository;
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;
    private final AppProperties        appProperties;

    @Transactional(readOnly = true)
    public Page<DeadLetterJob> listPending(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("failedAt").descending());
        return deadLetterRepository.findByReplayedAtIsNull(pageable);
    }

    @Transactional
    public String replaySingle(String dlqId) {
        DeadLetterJob dlqJob = deadLetterRepository.findById(dlqId)
                .orElseThrow(() -> TaskQueueException.notFound("DLQ entry", dlqId));

        if (dlqJob.isReplayed()) {
            throw TaskQueueException.badRequest(
                    "Already replayed on " + dlqJob.getReplayedAt());
        }

        // Load job WITH relations — so getProject() and getApiKey() work
        Job job = jobRepository.findByIdWithRelations(dlqJob.getJob().getId())
                .orElseThrow(() -> TaskQueueException.notFound("Job", dlqJob.getJob().getId()));

        // Reset job state
        job.setStatus(Job.Status.QUEUED);
        job.setRetryCount(0);
        job.setErrorMessage(null);
        job.setStartedAt(null);
        job.setCompletedAt(null);
        jobRepository.save(job);

        // Mark DLQ entry as replayed
        dlqJob.setReplayedAt(LocalDateTime.now());
        dlqJob.setReplayedJobId(job.getId());
        deadLetterRepository.save(dlqJob);

        // Extract values before republish
        republishToKafka(job);

        log.info("DLQ replayed: dlqId={} jobId={}", dlqId, job.getId());
        return job.getId();
    }

    @Transactional
    public int replayAll() {
        List<DeadLetterJob> pending = deadLetterRepository
                .findByReplayedAtIsNull(PageRequest.of(0, 1000))
                .getContent();

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

    /**
     * Republish job to Kafka.
     * CRITICAL: extract ALL values from job before using them in builders.
     * Called inside @Transactional so session is open — safe to access relations.
     */
    private void republishToKafka(Job job) {
        // Extract while session is open and @Transactional is active
        String jobId      = job.getId();
        String projectId  = job.getProject().getId();
        String companyId  = job.getProject().getCompany().getId();
        String apiKeyId   = job.getApiKey().getId();
        String jobType    = job.getType();
        String callbackUrl = job.getCallbackUrl();
        Job.Priority priority = job.getPriority();
        Map<String, Object> payload = job.getPayload();
        LocalDateTime createdAt = job.getCreatedAt();

        JobEvent event = JobEvent.builder()
                .jobId(jobId)
                .projectId(projectId)
                .companyId(companyId)
                .apiKeyId(apiKeyId)
                .type(jobType)
                .payload(payload)
                .priority(priority)
                .retryCount(0)
                .maxRetries(job.getMaxRetries())
                .callbackUrl(callbackUrl)
                .createdAt(createdAt)
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