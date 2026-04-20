package com.taskqueue.worker;

import com.taskqueue.model.*;
import com.taskqueue.repository.JobRepository;
import com.taskqueue.service.RetryService;
import com.taskqueue.service.SmtpService;
import com.taskqueue.service.WebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Processes SEND_EMAIL jobs.
 *
 * Expected payload fields:
 * {
 *   "to":       "user@example.com",   required
 *   "subject":  "Your order confirmed", required
 *   "body":     "<h1>Hello!</h1>",     required (HTML supported)
 *   "cc":       "manager@co.com",      optional
 * }
 *
 * The smtpPurpose on the JobEvent tells us which SMTP config to use:
 *   SUPPORT → support@company.com
 *   BILLING → billing@company.com
 *   NOREPLY → noreply@company.com
 */
@Slf4j
@Component
public class EmailWorker extends BaseWorker {

    private final SmtpService smtpService;

    public EmailWorker(
            JobRepository  jobRepository,
            RetryService   retryService,
            WebhookService webhookService,
            SmtpService    smtpService
    ) {
        super(jobRepository, retryService, webhookService);
        this.smtpService = smtpService;
    }

    @Override
    protected void process(JobEvent event) throws Exception {
        Map<String, Object> payload = event.getPayload();

        // Extract required fields from payload
        String to      = getRequired(payload, "to");
        String subject = getRequired(payload, "subject");
        String body    = getRequired(payload, "body");

        // Determine SMTP purpose — default to NOREPLY if not specified
        SmtpConfig.Purpose purpose = SmtpConfig.Purpose.NOREPLY;
        if (event.getSmtpPurpose() != null) {
            try {
                purpose = SmtpConfig.Purpose.valueOf(event.getSmtpPurpose().toUpperCase());
            } catch (IllegalArgumentException e) {
                log.warn("Unknown smtpPurpose '{}' — falling back to NOREPLY", event.getSmtpPurpose());
            }
        }

        log.info("Sending email: to={} subject={} purpose={} company={}",
                to, subject, purpose, event.getCompanyId());

        smtpService.send(event.getCompanyId(), purpose, to, subject, body);
    }

    private String getRequired(Map<String, Object> payload, String key) {
        Object val = payload.get(key);
        if (val == null || val.toString().isBlank()) {
            throw new IllegalArgumentException(
                    "EmailWorker: required payload field missing: '" + key + "'"
            );
        }
        return val.toString();
    }
}