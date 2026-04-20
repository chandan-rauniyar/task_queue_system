package com.taskqueue.worker;

import com.taskqueue.model.JobEvent;
import com.taskqueue.repository.JobRepository;
import com.taskqueue.service.RetryService;
import com.taskqueue.service.WebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback worker for any job type not handled by a specific worker.
 *
 * If you submit a job with type "CUSTOM_TASK" and there's no
 * CustomTaskWorker, this GenericWorker handles it — logs the
 * payload and marks it SUCCESS.
 *
 * Use this as the starting point when you want to add a new
 * job type but haven't built the real worker yet.
 */
@Slf4j
@Component
public class GenericWorker extends BaseWorker {

    public GenericWorker(
            JobRepository  jobRepository,
            RetryService   retryService,
            WebhookService webhookService
    ) {
        super(jobRepository, retryService, webhookService);
    }

    @Override
    protected void process(JobEvent event) throws Exception {
        log.info("GenericWorker processing: type={} jobId={} payload={}",
                event.getType(), event.getJobId(), event.getPayload());

        // Simulate work — replace this with real logic as needed
        Thread.sleep(100);

        log.info("GenericWorker completed: type={} jobId={}", event.getType(), event.getJobId());
    }
}