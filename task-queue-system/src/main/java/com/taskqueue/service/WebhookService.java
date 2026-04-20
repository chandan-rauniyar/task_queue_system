package com.taskqueue.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskqueue.config.AppProperties;
import com.taskqueue.model.Job;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Fires HTTP POST callbacks to the client's callbackUrl
 * after a job finishes (success or failure).
 *
 * This is how Swiggy's server knows their job is done —
 * they registered a callbackUrl when submitting the job,
 * and we POST the result to that URL.
 *
 * Has its own 3-attempt retry with 5s backoff.
 * Runs async so it never blocks the worker thread.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RestTemplate    restTemplate;
    private final ObjectMapper    objectMapper;
    private final AppProperties   appProperties;

    /**
     * Fire webhook asynchronously — does not block the calling thread.
     * Worker calls this after job completes, then moves on immediately.
     */
    @Async
    public void fireAsync(
            String callbackUrl,
            String jobId,
            Job.Status status,
            String errorMessage
    ) {
        try {
            fire(callbackUrl, jobId, status, errorMessage);
        } catch (Exception e) {
            // Async — can't propagate, just log
            log.error("Webhook delivery permanently failed: jobId={} url={} error={}",
                    jobId, callbackUrl, e.getMessage());
        }
    }

    /**
     * Actual HTTP POST with retry.
     * @Retryable retries up to 3 times with 5s delay on any exception.
     */
    @Retryable(
            retryFor  = Exception.class,
            maxAttempts = 3,
            backoff   = @Backoff(delay = 5000)   // 5 second wait between retries
    )
    public void fire(
            String callbackUrl,
            String jobId,
            Job.Status status,
            String errorMessage
    ) {
        log.info("Firing webhook: jobId={} status={} url={}", jobId, status, callbackUrl);

        // Build the payload we POST to the client
        Map<String, Object> body = Map.of(
                "jobId",        jobId,
                "status",       status.name(),
                "errorMessage", errorMessage != null ? errorMessage : "",
                "firedAt",      LocalDateTime.now().toString()
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                callbackUrl, request, String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Webhook delivered: jobId={} status={} responseCode={}",
                    jobId, status, response.getStatusCode());
        } else {
            // Non-2xx triggers retry via @Retryable
            throw new RuntimeException(
                    "Webhook returned non-2xx: " + response.getStatusCode()
            );
        }
    }
}