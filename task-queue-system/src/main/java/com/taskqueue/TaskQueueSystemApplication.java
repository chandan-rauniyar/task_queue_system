package com.taskqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableRetry // enables @Retryable on WebhookService
@EnableAsync // enables @Async for non-blocking webhook calls
@EnableScheduling // enables @Scheduled for future scheduled jobs feature
@EnableConfigurationProperties
public class TaskQueueSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskQueueSystemApplication.class, args);
	}

}
