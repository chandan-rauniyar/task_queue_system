package com.taskqueue.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Reads all "app.*" properties from application.yml into typed fields.
 * Inject this anywhere with: @Autowired App Properties appProperties;
 */
@Data
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private Encryption encryption = new Encryption();
    private Kafka kafka = new Kafka();
    private Redis redis = new Redis();
    private Retry retry = new Retry();
    private Admin admin = new Admin();
    private Webhook webhook = new Webhook();

    @Data
    public static class Encryption {
        private String key;  // must be exactly 32 chars for AES-256
    }

    @Data
    public static class Kafka {
        private Topics topics = new Topics();

        @Data
        public static class Topics {
            private String highPriority;
            private String normalPriority;
            private String lowPriority;
            private String deadLetter;
        }
    }

    @Data
    public static class Redis {
        private long apiKeyCacheTtl;       // seconds
        private long rateLimitWindow;      // seconds
    }

    @Data
    public static class Retry {
        private int maxAttempts;
        private int initialDelaySeconds;
        private int multiplier;
    }

    @Data
    public static class Admin {
        private String allowedIp;
        private String pathPrefix;
    }

    @Data
    public static class Webhook {
        private int timeoutSeconds;
        private int maxRetries;
    }
}