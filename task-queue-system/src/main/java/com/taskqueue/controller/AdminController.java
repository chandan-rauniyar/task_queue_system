package com.taskqueue.controller;

import com.taskqueue.config.AppProperties;
import com.taskqueue.dto.*;
import com.taskqueue.exception.TaskQueueException;
import com.taskqueue.model.*;
import com.taskqueue.repository.*;
import com.taskqueue.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints.
 * Accessible ONLY from localhost — AdminBypassFilter enforces this.
 * No API key required. Used by the React admin panel on localhost:3000.
 *
 * Base path: /api/v1/admin
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin panel endpoints — localhost only")
public class AdminController {

    private final ApiKeyService        apiKeyService;
    private final EncryptionService    encryptionService;
    private final DlqService           dlqService;
    private final SmtpService          smtpService;
    private final CompanyRepository    companyRepository;
    private final ProjectRepository    projectRepository;
    private final JobRepository        jobRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final SmtpConfigRepository smtpConfigRepository;
    private final UserRepository       userRepository;
    private final ApiKeyRepository     apiKeyRepository;
    private final AppProperties        appProperties;
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;

    // ════════════════════════════════════════════════════════
    // DASHBOARD
    // ════════════════════════════════════════════════════════

    @GetMapping("/metrics")
    @Operation(summary = "Dashboard metrics — job counts and totals")
    public ResponseEntity<ApiResponse<MetricsResponse>> getMetrics() {
        MetricsResponse metrics = MetricsResponse.builder()
                .totalJobs(jobRepository.count())
                .queuedJobs(jobRepository.countByStatus(Job.Status.QUEUED))
                .runningJobs(jobRepository.countByStatus(Job.Status.RUNNING))
                .successJobs(jobRepository.countByStatus(Job.Status.SUCCESS))
                .failedJobs(jobRepository.countByStatus(Job.Status.FAILED))
                .deadJobs(jobRepository.countByStatus(Job.Status.DEAD))
                .pendingDlq(dlqService.countPending())
                .totalCompanies(companyRepository.count())
                .totalProjects(projectRepository.count())
                .totalApiKeys(apiKeyRepository.count())
                .build();

        return ResponseEntity.ok(ApiResponse.ok(metrics));
    }

    // ════════════════════════════════════════════════════════
    // COMPANIES
    // ════════════════════════════════════════════════════════

    @GetMapping("/companies")
    @Operation(summary = "List all companies")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies() {
        List<CompanyResponse> companies = companyRepository.findAll()
                .stream()
                .map(CompanyResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(companies));
    }

    @PostMapping("/companies")
    @Operation(summary = "Create a new company")
    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
            @Valid @RequestBody CreateCompanyRequest req
    ) {
        if (companyRepository.existsBySlug(req.getSlug())) {
            throw TaskQueueException.conflict("Slug '" + req.getSlug() + "' is already taken");
        }

        User admin = userRepository.findByRole(User.Role.ADMIN)
                .orElseThrow(() -> TaskQueueException.notFound("Admin user", "ADMIN"));

        Company company = Company.builder()
                .owner(admin)
                .name(req.getName())
                .slug(req.getSlug())
                .build();

        company = companyRepository.save(company);
        log.info("Company created: id={} name={}", company.getId(), company.getName());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(CompanyResponse.from(company)));
    }

    @PatchMapping("/companies/{id}/toggle")
    @Operation(summary = "Toggle company active/inactive")
    public ResponseEntity<ApiResponse<CompanyResponse>> toggleCompany(
            @PathVariable String id
    ) {
        Company company = companyRepository.findById(id)
                .orElseThrow(() -> TaskQueueException.notFound("Company", id));

        company.setIsActive(!company.getIsActive());
        company = companyRepository.save(company);
        return ResponseEntity.ok(ApiResponse.ok(CompanyResponse.from(company)));
    }

    // ════════════════════════════════════════════════════════
    // PROJECTS
    // ════════════════════════════════════════════════════════

    @GetMapping("/companies/{companyId}/projects")
    @Operation(summary = "List projects for a company")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listProjects(
            @PathVariable String companyId
    ) {
        List<ProjectResponse> projects = projectRepository
                .findByCompanyId(companyId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(projects));
    }

    @PostMapping("/projects")
    @Operation(summary = "Create a project under a company")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest req
    ) {
        Company company = companyRepository.findById(req.getCompanyId())
                .orElseThrow(() -> TaskQueueException.notFound("Company", req.getCompanyId()));

        Project project = Project.builder()
                .company(company)
                .name(req.getName())
                .description(req.getDescription())
                .environment(req.getEnvironment() != null
                        ? req.getEnvironment()
                        : Project.Environment.PRODUCTION)
                .build();

        project = projectRepository.save(project);
        log.info("Project created: id={} name={} company={}",
                project.getId(), project.getName(), company.getName());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ProjectResponse.from(project)));
    }

    @PatchMapping("/projects/{id}/toggle")
    @Operation(summary = "Toggle project active/inactive")
    public ResponseEntity<ApiResponse<ProjectResponse>> toggleProject(
            @PathVariable String id
    ) {
        Project project = projectRepository.findById(id)
                .orElseThrow(() -> TaskQueueException.notFound("Project", id));

        project.setIsActive(!project.getIsActive());
        project = projectRepository.save(project);
        return ResponseEntity.ok(ApiResponse.ok(ProjectResponse.from(project)));
    }

    // ════════════════════════════════════════════════════════
    // API KEYS
    // ════════════════════════════════════════════════════════

    @PostMapping("/keys")
    @Operation(
            summary = "Create an API key for a project",
            description = "Raw key returned ONCE only — copy it immediately."
    )
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> createApiKey(
            @Valid @RequestBody CreateApiKeyRequest req
    ) {
        CreateApiKeyResponse response = apiKeyService.createKey(req);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    @GetMapping("/projects/{projectId}/keys")
    @Operation(summary = "List all API keys for a project")
    public ResponseEntity<ApiResponse<List<ApiKeySummaryResponse>>> listKeys(
            @PathVariable String projectId
    ) {
        List<ApiKeySummaryResponse> keys = apiKeyService
                .listKeysForProject(projectId)
                .stream()
                .map(ApiKeySummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(keys));
    }

    @DeleteMapping("/keys/{keyId}")
    @Operation(summary = "Revoke an API key immediately")
    public ResponseEntity<ApiResponse<Map<String, String>>> revokeKey(
            @PathVariable String keyId
    ) {
        apiKeyService.revokeKey(keyId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Key revoked successfully")));
    }

    // ════════════════════════════════════════════════════════
    // JOB BROWSER
    // ════════════════════════════════════════════════════════

    @GetMapping("/jobs")
    @Operation(summary = "Browse all jobs — filter by project or status")
    public ResponseEntity<ApiResponse<Page<JobDetailResponse>>> browseJobs(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Job.Status status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Job> jobs;
        if (projectId != null && status != null) {
            jobs = jobRepository.findByProjectIdAndStatus(projectId, status, pageable);
        } else if (projectId != null) {
            jobs = jobRepository.findByProjectId(projectId, pageable);
        } else if (status != null) {
            jobs = jobRepository.findByStatus(status, pageable);
        } else {
            jobs = jobRepository.findAll(pageable);
        }

        return ResponseEntity.ok(ApiResponse.ok(jobs.map(JobDetailResponse::from)));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get full detail of a single job — admin view, no API key needed")
    public ResponseEntity<ApiResponse<JobDetailResponse>> getJobById(
            @PathVariable String jobId
    ) {
        Job job = jobRepository.findByIdWithRelations(jobId)
                .orElseThrow(() -> TaskQueueException.notFound("Job", jobId));
        return ResponseEntity.ok(ApiResponse.ok(JobDetailResponse.from(job)));
    }

    @PostMapping("/jobs/{jobId}/retry")
    @Operation(summary = "Manually retry a failed job — admin view, no API key needed")
    public ResponseEntity<ApiResponse<Map<String, String>>> retryJobAdmin(
            @PathVariable String jobId
    ) {
        Job job = jobRepository.findByIdWithRelations(jobId)
                .orElseThrow(() -> TaskQueueException.notFound("Job", jobId));

        if (!job.canRetry()) {
            throw TaskQueueException.badRequest(
                    "Job cannot be retried. Status=" + job.getStatus()
                            + ", attempts=" + job.getRetryCount() + "/" + job.getMaxRetries()
            );
        }

        job.setStatus(Job.Status.QUEUED);
        job.setErrorMessage(null);
        jobRepository.save(job);

        // Re-publish to Kafka
        // Pull companyId and projectId from the loaded relations
        String projectId   = job.getProject().getId();
        String companyId   = job.getProject().getCompany().getId();
        String apiKeyId    = job.getApiKey().getId();
        String jobType     = job.getType();
        String callbackUrl = job.getCallbackUrl();
        Integer retryCount = job.getRetryCount();
        Integer maxRetries = job.getMaxRetries();
        var    priority    = job.getPriority();
        var    payload     = job.getPayload();

        com.taskqueue.model.JobEvent event = com.taskqueue.model.JobEvent.builder()
                .jobId(jobId)
                .projectId(projectId)
                .companyId(companyId)
                .apiKeyId(apiKeyId)
                .type(jobType)
                .payload(payload)
                .priority(priority)
                .retryCount(retryCount)
                .maxRetries(maxRetries)
                .callbackUrl(callbackUrl)
                .build();

        com.taskqueue.config.AppProperties.Kafka.Topics topics =
                appProperties.getKafka().getTopics();
        String topic = switch (priority) {
            case HIGH  -> topics.getHighPriority();
            case LOW   -> topics.getLowPriority();
            default    -> topics.getNormalPriority();
        };

        kafkaTemplate.send(topic, jobId, event);
        log.info("Job manually re-queued by admin: jobId={}", jobId);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "jobId",   jobId,
                "status",  "QUEUED",
                "message", "Job re-queued for processing"
        )));
    }

    // ════════════════════════════════════════════════════════
    // DEAD LETTER QUEUE
    // ════════════════════════════════════════════════════════

    @GetMapping("/dlq")
    @Operation(summary = "List unreplayed dead letter jobs")
    public ResponseEntity<ApiResponse<Page<DeadLetterJob>>> listDlq(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.ok(dlqService.listPending(page, size)));
    }

    @PostMapping("/dlq/{dlqId}/replay")
    @Operation(summary = "Replay a single dead letter job")
    public ResponseEntity<ApiResponse<Map<String, String>>> replaySingle(
            @PathVariable String dlqId
    ) {
        String jobId = dlqService.replaySingle(dlqId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "message", "Job re-queued successfully",
                "jobId",   jobId
        )));
    }

    @PostMapping("/dlq/replay-all")
    @Operation(summary = "Replay ALL pending dead letter jobs")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> replayAll() {
        int count = dlqService.replayAll();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("replayedCount", count)));
    }

    // ════════════════════════════════════════════════════════
    // SMTP CONFIGS
    // ════════════════════════════════════════════════════════

    @GetMapping("/companies/{companyId}/smtp")
    @Operation(summary = "List SMTP configs for a company")
    public ResponseEntity<ApiResponse<List<SmtpConfigResponse>>> listSmtp(
            @PathVariable String companyId
    ) {
        List<SmtpConfigResponse> configs = smtpConfigRepository
                .findByCompanyId(companyId)
                .stream()
                .map(SmtpConfigResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(configs));
    }

    @PostMapping("/smtp")
    @Operation(summary = "Add an SMTP config for a company")
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> createSmtp(
            @Valid @RequestBody CreateSmtpRequest req
    ) {
        Company company = companyRepository.findById(req.getCompanyId())
                .orElseThrow(() -> TaskQueueException.notFound("Company", req.getCompanyId()));

        SmtpConfig config = SmtpConfig.builder()
                .company(company)
                .purpose(req.getPurpose())
                .label(req.getLabel())
                .fromEmail(req.getFromEmail())
                .fromName(req.getFromName())
                .host(req.getHost())
                .port(req.getPort())
                .username(req.getUsername())
                .passwordEnc(encryptionService.encrypt(req.getPassword()))
                .useTls(req.getUseTls())
                .build();

        config = smtpConfigRepository.save(config);
        log.info("SMTP config created: company={} purpose={}", company.getName(), req.getPurpose());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(SmtpConfigResponse.from(config)));
    }

    @PostMapping("/smtp/{smtpId}/test")
    @Operation(summary = "Test SMTP connection — sets isVerified on success")
    public ResponseEntity<ApiResponse<Map<String, String>>> testSmtp(
            @PathVariable String smtpId
    ) {
        SmtpConfig config = smtpConfigRepository.findById(smtpId)
                .orElseThrow(() -> TaskQueueException.notFound("SmtpConfig", smtpId));

        smtpService.testConnection(config);   // throws if fails

        config.setIsVerified(true);
        smtpConfigRepository.save(config);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "message", "SMTP connection verified",
                "email",   config.getFromEmail()
        )));
    }

    @DeleteMapping("/smtp/{smtpId}")
    @Operation(summary = "Delete an SMTP config")
    public ResponseEntity<ApiResponse<Map<String, String>>> deleteSmtp(
            @PathVariable String smtpId
    ) {
        if (!smtpConfigRepository.existsById(smtpId)) {
            throw TaskQueueException.notFound("SmtpConfig", smtpId);
        }
        smtpConfigRepository.deleteById(smtpId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "SMTP config deleted")));
    }

    @PatchMapping("/smtp/{smtpId}/toggle")
    @Operation(summary = "Enable or disable an SMTP config")
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> toggleSmtp(
            @PathVariable String smtpId
    ) {
        SmtpConfig config = smtpConfigRepository.findById(smtpId)
                .orElseThrow(() -> TaskQueueException.notFound("SmtpConfig", smtpId));

        config.setIsActive(!config.getIsActive());
        // Evict sender cache so next email picks up the change
        smtpService.evictCache(smtpId);
        config = smtpConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.ok(SmtpConfigResponse.from(config)));
    }
}


//package com.taskqueue.controller;
//
//import com.taskqueue.dto.*;
//import com.taskqueue.exception.TaskQueueException;
//import com.taskqueue.model.*;
//import com.taskqueue.repository.*;
//import com.taskqueue.service.*;
//import io.swagger.v3.oas.annotations.Operation;
//import io.swagger.v3.oas.annotations.tags.Tag;
//import jakarta.validation.Valid;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.*;
//import org.springframework.http.*;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map;
//
///**
// * Admin-only endpoints.
// * Accessible ONLY from localhost — AdminBypassFilter enforces this.
// * No API key required. Used by the React admin panel on localhost:3000.
// *
// * Base path: /api/v1/admin
// */
//@Slf4j
//@RestController
//@RequestMapping("/admin")
//@RequiredArgsConstructor
//@Tag(name = "Admin", description = "Admin panel endpoints — localhost only")
//public class AdminController {
//
//    private final ApiKeyService        apiKeyService;
//    private final EncryptionService    encryptionService;
//    private final DlqService           dlqService;
//    private final SmtpService          smtpService;
//    private final CompanyRepository    companyRepository;
//    private final ProjectRepository    projectRepository;
//    private final JobRepository        jobRepository;
//    private final DeadLetterRepository deadLetterRepository;
//    private final SmtpConfigRepository smtpConfigRepository;
//    private final UserRepository       userRepository;
//    private final ApiKeyRepository     apiKeyRepository;
//
//    // DASHBOARD
//
//    @GetMapping("/metrics")
//    @Operation(summary = "Dashboard metrics — job counts and totals")
//    public ResponseEntity<ApiResponse<MetricsResponse>> getMetrics() {
//        MetricsResponse metrics = MetricsResponse.builder()
//                .totalJobs(jobRepository.count())
//                .queuedJobs(jobRepository.countByStatus(Job.Status.QUEUED))
//                .runningJobs(jobRepository.countByStatus(Job.Status.RUNNING))
//                .successJobs(jobRepository.countByStatus(Job.Status.SUCCESS))
//                .failedJobs(jobRepository.countByStatus(Job.Status.FAILED))
//                .deadJobs(jobRepository.countByStatus(Job.Status.DEAD))
//                .pendingDlq(dlqService.countPending())
//                .totalCompanies(companyRepository.count())
//                .totalProjects(projectRepository.count())
//                .totalApiKeys(apiKeyRepository.count())
//                .build();
//
//        return ResponseEntity.ok(ApiResponse.ok(metrics));
//    }
//
//    // COMPANIES
//
//    @GetMapping("/companies")
//    @Operation(summary = "List all companies")
//    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies() {
//        List<CompanyResponse> companies = companyRepository.findAll()
//                .stream()
//                .map(CompanyResponse::from)
//                .toList();
//        return ResponseEntity.ok(ApiResponse.ok(companies));
//    }
//
//    @PostMapping("/companies")
//    @Operation(summary = "Create a new company")
//    public ResponseEntity<ApiResponse<CompanyResponse>> createCompany(
//            @Valid @RequestBody CreateCompanyRequest req
//    ) {
//        if (companyRepository.existsBySlug(req.getSlug())) {
//            throw TaskQueueException.conflict("Slug '" + req.getSlug() + "' is already taken");
//        }
//
//        User admin = userRepository.findByRole(User.Role.ADMIN)
//                .orElseThrow(() -> TaskQueueException.notFound("Admin user", "ADMIN"));
//
//        Company company = Company.builder()
//                .owner(admin)
//                .name(req.getName())
//                .slug(req.getSlug())
//                .build();
//
//        company = companyRepository.save(company);
//        log.info("Company created: id={} name={}", company.getId(), company.getName());
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(ApiResponse.ok(CompanyResponse.from(company)));
//    }
//
//    @PatchMapping("/companies/{id}/toggle")
//    @Operation(summary = "Toggle company active/inactive")
//    public ResponseEntity<ApiResponse<CompanyResponse>> toggleCompany(
//            @PathVariable String id
//    ) {
//        Company company = companyRepository.findById(id)
//                .orElseThrow(() -> TaskQueueException.notFound("Company", id));
//
//        company.setIsActive(!company.getIsActive());
//        company = companyRepository.save(company);
//        return ResponseEntity.ok(ApiResponse.ok(CompanyResponse.from(company)));
//    }
//
//    // ════════════════════════════════════════════════════════
//    // PROJECTS
//    // ════════════════════════════════════════════════════════
//
//    @GetMapping("/companies/{companyId}/projects")
//    @Operation(summary = "List projects for a company")
//    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listProjects(
//            @PathVariable String companyId
//    ) {
//        List<ProjectResponse> projects = projectRepository
//                .findByCompanyId(companyId)
//                .stream()
//                .map(ProjectResponse::from)
//                .toList();
//        return ResponseEntity.ok(ApiResponse.ok(projects));
//    }
//
//    @PostMapping("/projects")
//    @Operation(summary = "Create a project under a company")
//    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
//            @Valid @RequestBody CreateProjectRequest req
//    ) {
//        Company company = companyRepository.findById(req.getCompanyId())
//                .orElseThrow(() -> TaskQueueException.notFound("Company", req.getCompanyId()));
//
//        Project project = Project.builder()
//                .company(company)
//                .name(req.getName())
//                .description(req.getDescription())
//                .environment(req.getEnvironment() != null
//                        ? req.getEnvironment()
//                        : Project.Environment.PRODUCTION)
//                .build();
//
//        project = projectRepository.save(project);
//        log.info("Project created: id={} name={} company={}",
//                project.getId(), project.getName(), company.getName());
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(ApiResponse.ok(ProjectResponse.from(project)));
//    }
//
//    @PatchMapping("/projects/{id}/toggle")
//    @Operation(summary = "Toggle project active/inactive")
//    public ResponseEntity<ApiResponse<ProjectResponse>> toggleProject(
//            @PathVariable String id
//    ) {
//        Project project = projectRepository.findById(id)
//                .orElseThrow(() -> TaskQueueException.notFound("Project", id));
//
//        project.setIsActive(!project.getIsActive());
//        project = projectRepository.save(project);
//        return ResponseEntity.ok(ApiResponse.ok(ProjectResponse.from(project)));
//    }
//
//    // ════════════════════════════════════════════════════════
//    // API KEYS
//    // ════════════════════════════════════════════════════════
//
//    @PostMapping("/keys")
//    @Operation(
//            summary = "Create an API key for a project",
//            description = "Raw key returned ONCE only — copy it immediately."
//    )
//    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> createApiKey(
//            @Valid @RequestBody CreateApiKeyRequest req
//    ) {
//        CreateApiKeyResponse response = apiKeyService.createKey(req);
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(ApiResponse.ok(response));
//    }
//
//    @GetMapping("/projects/{projectId}/keys")
//    @Operation(summary = "List all API keys for a project")
//    public ResponseEntity<ApiResponse<List<ApiKeySummaryResponse>>> listKeys(
//            @PathVariable String projectId
//    ) {
//        List<ApiKeySummaryResponse> keys = apiKeyService
//                .listKeysForProject(projectId)
//                .stream()
//                .map(ApiKeySummaryResponse::from)
//                .toList();
//        return ResponseEntity.ok(ApiResponse.ok(keys));
//    }
//
//    @DeleteMapping("/keys/{keyId}")
//    @Operation(summary = "Revoke an API key immediately")
//    public ResponseEntity<ApiResponse<Map<String, String>>> revokeKey(
//            @PathVariable String keyId
//    ) {
//        apiKeyService.revokeKey(keyId);
//        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Key revoked successfully")));
//    }
//
//    // ════════════════════════════════════════════════════════
//    // JOB BROWSER
//    // ════════════════════════════════════════════════════════
//
//    @GetMapping("/jobs")
//    @Operation(summary = "Browse all jobs — filter by project or status")
//    public ResponseEntity<ApiResponse<Page<JobDetailResponse>>> browseJobs(
//            @RequestParam(required = false) String projectId,
//            @RequestParam(required = false) Job.Status status,
//            @RequestParam(defaultValue = "0")  int page,
//            @RequestParam(defaultValue = "20") int size
//    ) {
//        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
//
//        Page<Job> jobs;
//        if (projectId != null && status != null) {
//            jobs = jobRepository.findByProjectIdAndStatus(projectId, status, pageable);
//        } else if (projectId != null) {
//            jobs = jobRepository.findByProjectId(projectId, pageable);
//        } else if (status != null) {
//            jobs = jobRepository.findByStatus(status, pageable);
//        } else {
//            jobs = jobRepository.findAll(pageable);
//        }
//
//        return ResponseEntity.ok(ApiResponse.ok(jobs.map(JobDetailResponse::from)));
//    }
//
//    // ════════════════════════════════════════════════════════
//    // DEAD LETTER QUEUE
//    // ════════════════════════════════════════════════════════
//
//    @GetMapping("/dlq")
//    @Operation(summary = "List unreplayed dead letter jobs")
//    public ResponseEntity<ApiResponse<Page<DeadLetterJob>>> listDlq(
//            @RequestParam(defaultValue = "0")  int page,
//            @RequestParam(defaultValue = "20") int size
//    ) {
//        return ResponseEntity.ok(ApiResponse.ok(dlqService.listPending(page, size)));
//    }
//
//    @PostMapping("/dlq/{dlqId}/replay")
//    @Operation(summary = "Replay a single dead letter job")
//    public ResponseEntity<ApiResponse<Map<String, String>>> replaySingle(
//            @PathVariable String dlqId
//    ) {
//        String jobId = dlqService.replaySingle(dlqId);
//        return ResponseEntity.ok(ApiResponse.ok(Map.of(
//                "message", "Job re-queued successfully",
//                "jobId",   jobId
//        )));
//    }
//
//    @PostMapping("/dlq/replay-all")
//    @Operation(summary = "Replay ALL pending dead letter jobs")
//    public ResponseEntity<ApiResponse<Map<String, Integer>>> replayAll() {
//        int count = dlqService.replayAll();
//        return ResponseEntity.ok(ApiResponse.ok(Map.of("replayedCount", count)));
//    }
//
//    // ════════════════════════════════════════════════════════
//    // SMTP CONFIGS
//    // ════════════════════════════════════════════════════════
//
//    @GetMapping("/companies/{companyId}/smtp")
//    @Operation(summary = "List SMTP configs for a company")
//    public ResponseEntity<ApiResponse<List<SmtpConfigResponse>>> listSmtp(
//            @PathVariable String companyId
//    ) {
//        List<SmtpConfigResponse> configs = smtpConfigRepository
//                .findByCompanyId(companyId)
//                .stream()
//                .map(SmtpConfigResponse::from)
//                .toList();
//        return ResponseEntity.ok(ApiResponse.ok(configs));
//    }
//
//    @PostMapping("/smtp")
//    @Operation(summary = "Add an SMTP config for a company")
//    public ResponseEntity<ApiResponse<SmtpConfigResponse>> createSmtp(
//            @Valid @RequestBody CreateSmtpRequest req
//    ) {
//        Company company = companyRepository.findById(req.getCompanyId())
//                .orElseThrow(() -> TaskQueueException.notFound("Company", req.getCompanyId()));
//
//        SmtpConfig config = SmtpConfig.builder()
//                .company(company)
//                .purpose(req.getPurpose())
//                .label(req.getLabel())
//                .fromEmail(req.getFromEmail())
//                .fromName(req.getFromName())
//                .host(req.getHost())
//                .port(req.getPort())
//                .username(req.getUsername())
//                .passwordEnc(encryptionService.encrypt(req.getPassword()))
//                .useTls(req.getUseTls())
//                .build();
//
//        config = smtpConfigRepository.save(config);
//        log.info("SMTP config created: company={} purpose={}", company.getName(), req.getPurpose());
//        return ResponseEntity
//                .status(HttpStatus.CREATED)
//                .body(ApiResponse.ok(SmtpConfigResponse.from(config)));
//    }
//
//    @PostMapping("/smtp/{smtpId}/test")
//    @Operation(summary = "Test SMTP connection — sets isVerified on success")
//    public ResponseEntity<ApiResponse<Map<String, String>>> testSmtp(
//            @PathVariable String smtpId
//    ) {
//        SmtpConfig config = smtpConfigRepository.findById(smtpId)
//                .orElseThrow(() -> TaskQueueException.notFound("SmtpConfig", smtpId));
//
//        smtpService.testConnection(config);   // throws if fails
//
//        config.setIsVerified(true);
//        smtpConfigRepository.save(config);
//
//        return ResponseEntity.ok(ApiResponse.ok(Map.of(
//                "message", "SMTP connection verified",
//                "email",   config.getFromEmail()
//        )));
//    }
//
//    @DeleteMapping("/smtp/{smtpId}")
//    @Operation(summary = "Delete an SMTP config")
//    public ResponseEntity<ApiResponse<Map<String, String>>> deleteSmtp(
//            @PathVariable String smtpId
//    ) {
//        if (!smtpConfigRepository.existsById(smtpId)) {
//            throw TaskQueueException.notFound("SmtpConfig", smtpId);
//        }
//        smtpConfigRepository.deleteById(smtpId);
//        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "SMTP config deleted")));
//    }
//
//    @PatchMapping("/smtp/{smtpId}/toggle")
//    @Operation(summary = "Enable or disable an SMTP config")
//    public ResponseEntity<ApiResponse<SmtpConfigResponse>> toggleSmtp(
//            @PathVariable String smtpId
//    ) {
//        SmtpConfig config = smtpConfigRepository.findById(smtpId)
//                .orElseThrow(() -> TaskQueueException.notFound("SmtpConfig", smtpId));
//
//        config.setIsActive(!config.getIsActive());
//        // Evict sender cache so next email picks up the change
//        smtpService.evictCache(smtpId);
//        config = smtpConfigRepository.save(config);
//        return ResponseEntity.ok(ApiResponse.ok(SmtpConfigResponse.from(config)));
//    }
//}