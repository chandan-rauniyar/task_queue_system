package com.taskqueue.worker;

import com.taskqueue.model.JobEvent;
import com.taskqueue.repository.JobRepository;
import com.taskqueue.service.RetryService;
import com.taskqueue.service.WebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Processes GENERATE_PDF jobs.
 *
 * Expected payload:
 * {
 *   "templateName": "invoice",         required
 *   "outputFileName": "invoice_123",   required
 *   "data": { ...template variables }  required
 * }
 *
 * Phase 3 stub — logs the request and marks done.
 * To make this real: add iText or Apache PDFBox dependency
 * and replace the TODO block with actual PDF generation.
 */
@Slf4j
@Component
public class PdfWorker extends BaseWorker {

    public PdfWorker(
            JobRepository  jobRepository,
            RetryService   retryService,
            WebhookService webhookService
    ) {
        super(jobRepository, retryService, webhookService);
    }

    @Override
    protected void process(JobEvent event) throws Exception {
        Map<String, Object> payload = event.getPayload();
        String templateName  = String.valueOf(payload.getOrDefault("templateName",  "default"));
        String outputFileName = String.valueOf(payload.getOrDefault("outputFileName", event.getJobId()));

        log.info("PDF generation: template={} output={} jobId={}",
                templateName, outputFileName, event.getJobId());

        // TODO: Add iText/PDFBox dependency and generate real PDF here
        // Example with iText:
        //   Document doc = new Document();
        //   PdfWriter.getInstance(doc, new FileOutputStream(outputPath));
        //   doc.open();
        //   doc.add(new Paragraph("Invoice #" + payload.get("invoiceId")));
        //   doc.close();

        Thread.sleep(200); // simulate PDF generation time

        log.info("PDF generated: output={}.pdf jobId={}", outputFileName, event.getJobId());
    }
}