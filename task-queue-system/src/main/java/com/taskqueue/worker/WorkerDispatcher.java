package com.taskqueue.worker;

import com.taskqueue.model.JobEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Single entry point for ALL Kafka messages across all 3 topics.
 * Routes each job to the correct worker by job type.
 *
 * Why one listener for all topics?
 * Simpler to manage — one place to add new job types.
 * Each topic still runs with its own thread pool via partitions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerDispatcher {

    private final EmailWorker   emailWorker;
    private final PdfWorker     pdfWorker;
    private final GenericWorker genericWorker;

    @KafkaListener(
            topics = {
                    "#{@appProperties.kafka.topics.highPriority}",
                    "#{@appProperties.kafka.topics.normalPriority}",
                    "#{@appProperties.kafka.topics.lowPriority}"
            },
            groupId = "task-queue-workers",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void dispatch(JobEvent event, Acknowledgment ack) {
        log.info("Received job from Kafka: jobId={} type={} priority={}",
                event.getJobId(), event.getType(), event.getPriority());

        try {
            switch (event.getType().toUpperCase()) {
                case "SEND_EMAIL"    -> emailWorker.execute(event);
                case "GENERATE_PDF"  -> pdfWorker.execute(event);
                default              -> genericWorker.execute(event);
            }
            // Manual ack — only after successful processing
            ack.acknowledge();

        } catch (Exception e) {
            // Log but still ack — RetryService handles retry logic,
            // not Kafka. We don't want Kafka to redeliver endlessly.
            log.error("Worker dispatch error: jobId={} error={}",
                    event.getJobId(), e.getMessage());
            ack.acknowledge();
        }
    }
}