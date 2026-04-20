package com.taskqueue.service;

import com.taskqueue.model.SmtpConfig;
import com.taskqueue.repository.SmtpConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Dynamically builds a JavaMailSender for each company's SMTP config.
 *
 * Why dynamic? Because each company has their own SMTP credentials
 * stored in the smtp_configs table. We can't use Spring's single
 * auto-configured mail sender — we need one per company per purpose.
 *
 * Cache: we cache built senders in memory so we don't rebuild
 * on every email. Cache is invalidated when config is updated.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmtpService {

    private final SmtpConfigRepository smtpConfigRepository;
    private final EncryptionService    encryptionService;

    // Cache: key = smtpConfigId, value = built JavaMailSender
    private final Map<String, JavaMailSender> senderCache = new ConcurrentHashMap<>();

    /**
     * Send an email using the company's SMTP config for the given purpose.
     *
     * @param companyId   which company's SMTP to use
     * @param purpose     SUPPORT / BILLING / NOREPLY / ALERT / CUSTOM
     * @param toEmail     recipient address
     * @param subject     email subject line
     * @param htmlBody    email body (HTML allowed)
     */
    public void send(
            String companyId,
            SmtpConfig.Purpose purpose,
            String toEmail,
            String subject,
            String htmlBody
    ) {
        // 1. Find the SMTP config for this company + purpose
        SmtpConfig config = smtpConfigRepository
                .findByCompanyIdAndPurposeAndIsActiveTrue(companyId, purpose)
                .orElseThrow(() -> new RuntimeException(
                        "No active SMTP config found for company=" + companyId
                                + " purpose=" + purpose
                                + ". Go to admin panel → SMTP Settings to add one."
                ));

        // 2. Get or build the mail sender
        JavaMailSender sender = getOrBuildSender(config);

        // 3. Build and send the email
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(config.getFromEmail(), config.getFromName());
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = HTML

            sender.send(message);
            log.info("Email sent: to={} subject={} via={} company={}",
                    toEmail, subject, config.getFromEmail(), companyId);

        } catch (Exception e) {
            log.error("Email send failed: to={} error={}", toEmail, e.getMessage());
            throw new RuntimeException("Email send failed: " + e.getMessage(), e);
        }
    }

    /**
     * Test an SMTP connection without sending a real email.
     * Called by admin panel "Test Connection" button.
     * Returns true if connection succeeds, throws if it fails.
     */
    public boolean testConnection(SmtpConfig config) {
        try {
            JavaMailSenderImpl sender = buildSender(config);
            sender.testConnection();
            log.info("SMTP test successful: host={} port={}", config.getHost(), config.getPort());
            return true;
        } catch (Exception e) {
            log.warn("SMTP test failed: host={} error={}", config.getHost(), e.getMessage());
            throw new RuntimeException("SMTP connection failed: " + e.getMessage());
        }
    }

    /**
     * Evict a company's cached sender — call this after admin updates SMTP config.
     */
    public void evictCache(String smtpConfigId) {
        senderCache.remove(smtpConfigId);
        log.info("SMTP sender cache evicted for configId={}", smtpConfigId);
    }

    // ── Private helpers ───────────────────────────────────────

    private JavaMailSender getOrBuildSender(SmtpConfig config) {
        return senderCache.computeIfAbsent(config.getId(), id -> buildSender(config));
    }

    private JavaMailSenderImpl buildSender(SmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();

        sender.setHost(config.getHost());
        sender.setPort(config.getPort());
        sender.setUsername(config.getUsername());

        // Decrypt the stored password at runtime
        String rawPassword = encryptionService.decrypt(config.getPasswordEnc());
        sender.setPassword(rawPassword);

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.timeout", "10000");   // 10s connection timeout

        if (config.getUseTls()) {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        } else {
            props.put("mail.smtp.ssl.enable", "true");
        }

        return sender;
    }
}