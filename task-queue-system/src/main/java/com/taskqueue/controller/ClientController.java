package com.taskqueue.controller;

import com.taskqueue.config.AppProperties;
import com.taskqueue.dto.*;
import com.taskqueue.model.JobEvent;
import com.taskqueue.service.JwtService;
import org.springframework.kafka.core.KafkaTemplate;
import com.taskqueue.exception.TaskQueueException;
import com.taskqueue.filter.ClientContext;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CLIENT user endpoints — requires JWT Bearer token.
 * All data is automatically scoped to the user's own company.
 *
 * A CLIENT can:
 *   - See their own company, projects, API keys, jobs, SMTP configs
 *   - Create projects, API keys, SMTP configs under their company
 *   - See and replay their own DLQ entries
 *
 * A CLIENT CANNOT:
 *   - See other companies' data
 *   - Access /admin/** endpoints
 *   - Create or delete companies
 *
 * Base path: /api/v1/client
 */
@Slf4j
@RestController
@RequestMapping("/client")
@RequiredArgsConstructor
@Tag(name = "Client", description = "JWT-protected endpoints for company owners")
public class ClientController {

    private final CompanyRepository    companyRepository;
    private final ProjectRepository    projectRepository;
    private final JobRepository        jobRepository;
    private final ApiKeyRepository     apiKeyRepository;
    private final SmtpConfigRepository smtpConfigRepository;
    private final DeadLetterRepository deadLetterRepository;
    private final ApiKeyService        apiKeyService;
    private final EncryptionService    encryptionService;
    private final SmtpService          smtpService;
    private final DlqService           dlqService;
    private final UserRepository       userRepository;
    private final JwtService           jwtService;
    private final AppProperties        appProperties;
    private final KafkaTemplate<String, JobEvent> kafkaTemplate;

    // ── Helper: get companyId from JWT context ────────────────
    private String requireCompanyId() {
        ClientContext.ClientInfo info = ClientContext.get();

        // ADMIN sees all — no company filter needed
        // But /client endpoints still work for ADMIN (they just see their context)
        if (info == null) {
            throw TaskQueueException.forbidden("Not authenticated");
        }

        // CLIENT must have a companyId in their JWT
        if (!info.isAdminRequest() && (info.getCompanyId() == null || info.getCompanyId().isBlank())) {
            throw TaskQueueException.forbidden(
                    "Your account is not linked to any company. Contact admin."
            );
        }

        return info.getCompanyId();
    }

    // ════════════════════════════════════════════════════════
    // MY COMPANY
    // ════════════════════════════════════════════════════════

    @GetMapping("/my-company")
    @Operation(summary = "Get your own company details")
    public ResponseEntity<ApiResponse<CompanyResponse>> getMyCompany() {
        String companyId = requireCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> TaskQueueException.notFound("Company", companyId));
        return ResponseEntity.ok(ApiResponse.ok(CompanyResponse.from(company)));
    }

    // ════════════════════════════════════════════════════════
    // METRICS — only for this company
    // ════════════════════════════════════════════════════════

    @GetMapping("/metrics")
    @Operation(summary = "Dashboard metrics for your company only")
    public ResponseEntity<ApiResponse<MetricsResponse>> getMyMetrics() {
        String companyId = requireCompanyId();

        // Get all project IDs for this company
        List<String> projectIds = projectRepository
                .findByCompanyId(companyId)
                .stream()
                .map(Project::getId)
                .toList();

        long totalJobs   = 0, queuedJobs = 0, runningJobs = 0,
                successJobs = 0, failedJobs = 0, deadJobs    = 0;

        for (String pid : projectIds) {
            totalJobs   += jobRepository.countByProjectId(pid);
            queuedJobs  += jobRepository.countByProjectIdAndStatus(pid, Job.Status.QUEUED);
            runningJobs += jobRepository.countByProjectIdAndStatus(pid, Job.Status.RUNNING);
            successJobs += jobRepository.countByProjectIdAndStatus(pid, Job.Status.SUCCESS);
            failedJobs  += jobRepository.countByProjectIdAndStatus(pid, Job.Status.FAILED);
            deadJobs    += jobRepository.countByProjectIdAndStatus(pid, Job.Status.DEAD);
        }

        long pendingDlq = deadLetterRepository.countByJobProjectIdInAndReplayedAtIsNull(projectIds);

        MetricsResponse metrics = MetricsResponse.builder()
                .totalJobs(totalJobs)
                .queuedJobs(queuedJobs)
                .runningJobs(runningJobs)
                .successJobs(successJobs)
                .failedJobs(failedJobs)
                .deadJobs(deadJobs)
                .pendingDlq(pendingDlq)
                .totalCompanies(1L)
                .totalProjects((long) projectIds.size())
                .totalApiKeys(apiKeyRepository.countByProjectIdIn(projectIds))
                .build();

        return ResponseEntity.ok(ApiResponse.ok(metrics));
    }

    // ════════════════════════════════════════════════════════
    // PROJECTS
    // ════════════════════════════════════════════════════════

    @GetMapping("/projects")
    @Operation(summary = "List your company's projects")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listProjects() {
        String companyId = requireCompanyId();
        List<ProjectResponse> projects = projectRepository
                .findByCompanyId(companyId)
                .stream()
                .map(ProjectResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(projects));
    }

    @PostMapping("/projects")
    @Operation(summary = "Create a project under your company")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest req
    ) {
        String companyId = requireCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> TaskQueueException.notFound("Company", companyId));

        Project project = Project.builder()
                .company(company)
                .name(req.getName())
                .description(req.getDescription())
                .environment(req.getEnvironment() != null ? req.getEnvironment() : Project.Environment.PRODUCTION)
                .build();

        project = projectRepository.save(project);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ProjectResponse.from(project)));
    }

    // ════════════════════════════════════════════════════════
    // API KEYS
    // ════════════════════════════════════════════════════════

    @GetMapping("/projects/{projectId}/keys")
    @Operation(summary = "List API keys for one of your projects")
    public ResponseEntity<ApiResponse<List<ApiKeySummaryResponse>>> listKeys(
            @PathVariable String projectId
    ) {
        assertProjectBelongsToMe(projectId);
        List<ApiKeySummaryResponse> keys = apiKeyService
                .listKeysForProject(projectId)
                .stream()
                .map(ApiKeySummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(keys));
    }

    @PostMapping("/keys")
    @Operation(summary = "Create an API key for one of your projects")
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> createKey(
            @Valid @RequestBody CreateApiKeyRequest req
    ) {
        assertProjectBelongsToMe(req.getProjectId());
        CreateApiKeyResponse response = apiKeyService.createKey(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @DeleteMapping("/keys/{keyId}")
    @Operation(summary = "Revoke one of your API keys")
    public ResponseEntity<ApiResponse<Map<String, String>>> revokeKey(
            @PathVariable String keyId
    ) {
        // Verify key belongs to this company
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> TaskQueueException.notFound("ApiKey", keyId));
        assertProjectBelongsToMe(key.getProject().getId());
        apiKeyService.revokeKey(keyId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Key revoked")));
    }

    // ════════════════════════════════════════════════════════
    // JOBS
    // ════════════════════════════════════════════════════════

    @GetMapping("/jobs")
    @Operation(summary = "List jobs for your company — filtered and paginated")
    public ResponseEntity<ApiResponse<Page<JobDetailResponse>>> listJobs(
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) Job.Status status,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String companyId = requireCompanyId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Job> jobs;
        if (projectId != null) {
            // Verify project belongs to this company
            assertProjectBelongsToMe(projectId);
            jobs = status != null
                    ? jobRepository.findByProjectIdAndStatus(projectId, status, pageable)
                    : jobRepository.findByProjectId(projectId, pageable);
        } else {
            // All jobs across all company projects
            List<String> projectIds = projectRepository
                    .findByCompanyId(companyId)
                    .stream().map(Project::getId).toList();
            jobs = status != null
                    ? jobRepository.findByProjectIdInAndStatus(projectIds, status, pageable)
                    : jobRepository.findByProjectIdIn(projectIds, pageable);
        }

        return ResponseEntity.ok(ApiResponse.ok(jobs.map(JobDetailResponse::from)));
    }

    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get a single job — must belong to your company")
    public ResponseEntity<ApiResponse<JobDetailResponse>> getJob(
            @PathVariable String jobId
    ) {
        Job job = jobRepository.findByIdWithRelations(jobId)
                .orElseThrow(() -> TaskQueueException.notFound("Job", jobId));
        assertProjectBelongsToMe(job.getProject().getId());
        return ResponseEntity.ok(ApiResponse.ok(JobDetailResponse.from(job)));
    }

    // ════════════════════════════════════════════════════════
    // SMTP
    // ════════════════════════════════════════════════════════

    @GetMapping("/smtp")
    @Operation(summary = "List your company's SMTP configs")
    public ResponseEntity<ApiResponse<List<SmtpConfigResponse>>> listSmtp() {
        String companyId = requireCompanyId();
        List<SmtpConfigResponse> configs = smtpConfigRepository
                .findByCompanyId(companyId)
                .stream()
                .map(SmtpConfigResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(configs));
    }

    @PostMapping("/smtp")
    @Operation(summary = "Add an SMTP config for your company")
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> createSmtp(
            @Valid @RequestBody CreateSmtpRequest req
    ) {
        // Enforce that companyId in request matches JWT companyId
        String myCompanyId = requireCompanyId();
        if (!myCompanyId.equals(req.getCompanyId())) {
            throw TaskQueueException.forbidden("Cannot create SMTP for another company");
        }
        Company company = companyRepository.findById(myCompanyId)
                .orElseThrow(() -> TaskQueueException.notFound("Company", myCompanyId));

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
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(SmtpConfigResponse.from(config)));
    }

    @PostMapping("/smtp/{smtpId}/test")
    @Operation(summary = "Test one of your SMTP configs")
    public ResponseEntity<ApiResponse<Map<String, String>>> testSmtp(@PathVariable String smtpId) {
        SmtpConfig config = smtpConfigRepository.findById(smtpId)
                .orElseThrow(() -> TaskQueueException.notFound("SmtpConfig", smtpId));
        assertCompanyOwnsSmtp(config);
        smtpService.testConnection(config);
        config.setIsVerified(true);
        smtpConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "SMTP verified", "email", config.getFromEmail())));
    }

    // ════════════════════════════════════════════════════════
    // DLQ
    // ════════════════════════════════════════════════════════

    @GetMapping("/dlq")
    @Operation(summary = "List your company's dead letter jobs")
    public ResponseEntity<ApiResponse<Page<DeadLetterJob>>> listDlq(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String companyId = requireCompanyId();
        List<String> projectIds = projectRepository.findByCompanyId(companyId)
                .stream().map(Project::getId).toList();

        Pageable pageable = PageRequest.of(page, size, Sort.by("failedAt").descending());
        Page<DeadLetterJob> dlq = deadLetterRepository
                .findByJobProjectIdInAndReplayedAtIsNull(projectIds, pageable);
        return ResponseEntity.ok(ApiResponse.ok(dlq));
    }

    @PostMapping("/dlq/{dlqId}/replay")
    @Operation(summary = "Replay one of your dead letter jobs")
    public ResponseEntity<ApiResponse<Map<String, String>>> replay(@PathVariable String dlqId) {
        // Verify DLQ entry belongs to this company
        DeadLetterJob dlq = deadLetterRepository.findById(dlqId)
                .orElseThrow(() -> TaskQueueException.notFound("DLQ entry", dlqId));
        assertProjectBelongsToMe(dlq.getJob().getProject().getId());

        String jobId = dlqService.replaySingle(dlqId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Job re-queued", "jobId", jobId)));
    }

    // ════════════════════════════════════════════════════════
    // Security helpers
    // ════════════════════════════════════════════════════════

    /** Throws 403 if the project does not belong to the current user's company */
    private void assertProjectBelongsToMe(String projectId) {
        String myCompanyId = requireCompanyId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> TaskQueueException.notFound("Project", projectId));
        if (!project.getCompany().getId().equals(myCompanyId)) {
            throw TaskQueueException.forbidden("Access denied — this project belongs to another company");
        }
    }

    /** Throws 403 if the SMTP config does not belong to the current user's company */
    private void assertCompanyOwnsSmtp(SmtpConfig config) {
        String myCompanyId = requireCompanyId();
        if (!config.getCompany().getId().equals(myCompanyId)) {
            throw TaskQueueException.forbidden("Access denied — this SMTP config belongs to another company");
        }
    }

    // ════════════════════════════════════════════════════════
    // CREATE COMPANY (first time after register)
    // ════════════════════════════════════════════════════════

    @PostMapping("/create-company")
    @Operation(summary = "Create your company — called once after registering")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createCompany(
            @RequestBody Map<String, String> body,
            @org.springframework.web.bind.annotation.RequestHeader("Authorization") String authHeader
    ) {
        ClientContext.ClientInfo info = ClientContext.get();
        if (info == null) throw TaskQueueException.forbidden("Not authenticated");

        String name = body.get("name");
        if (name == null || name.isBlank()) throw TaskQueueException.badRequest("Company name is required");

        // Find the user from JWT
        String token  = authHeader.substring(7);
        String userId = jwtService.getUserId(token);
        String email  = jwtService.getEmail(token);
        String role   = jwtService.getRole(token);
        String uname  = jwtService.validateToken(token).get("name", String.class);

        // Check user doesn't already have a company
        var existing = companyRepository.findByOwnerId(userId);
        if (!existing.isEmpty()) {
            throw TaskQueueException.conflict("You already have a company: " + existing.get(0).getName());
        }

        com.taskqueue.model.User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskQueueException.notFound("User", userId));

        // Generate unique slug
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
        int i = 2;
        String finalSlug = slug;
        while (companyRepository.existsBySlug(finalSlug)) finalSlug = slug + "-" + i++;

        Company company = Company.builder()
                .owner(user)
                .name(name)
                .slug(finalSlug)
                .isActive(true)
                .build();
        company = companyRepository.save(company);

        // Issue a new JWT with the companyId included
        String newToken = jwtService.generateToken(userId, email, role, uname, company.getId());

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "token",       newToken,
                "companyId",   company.getId(),
                "companyName", company.getName(),
                "slug",        company.getSlug()
        )));
    }
}
// Note: the above class closing brace is replaced by this addition