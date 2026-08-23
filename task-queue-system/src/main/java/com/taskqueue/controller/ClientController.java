package com.taskqueue.controller;

import com.taskqueue.config.AppProperties;
import com.taskqueue.dto.*;
import com.taskqueue.dto.DeadLetterJobResponse;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * CLIENT endpoints — JWT Bearer token required.
 * All data scoped to the company in the JWT (active company).
 *
 * Multi-company support:
 *   GET  /client/my-companies          → list all companies owned by this user
 *   POST /client/my-companies          → create a new company
 *   POST /client/switch-company/{id}   → get a new JWT for a different company
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

    // ── Helper ────────────────────────────────────────────────

    private String requireCompanyId() {
        ClientContext.ClientInfo info = ClientContext.get();
        if (info == null) throw TaskQueueException.forbidden("Not authenticated");
        if (!info.isAdminRequest() &&
                (info.getCompanyId() == null || info.getCompanyId().isBlank())) {
            throw TaskQueueException.forbidden("No active company. Use /client/switch-company to select one.");
        }
        return info.getCompanyId();
    }

    private String requireUserId(String authHeader) {
        String token = authHeader.substring(7);
        return jwtService.getUserId(token);
    }

    // ════════════════════════════════════════════════════════
    // MULTI-COMPANY — list, create, switch
    // ════════════════════════════════════════════════════════

    /**
     * List all companies owned by the logged-in user.
     * Returns all companies regardless of which one is "active" in JWT.
     */
    @GetMapping("/my-companies")
    @Operation(summary = "List all your companies")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listMyCompanies(
            @RequestHeader("Authorization") String authHeader
    ) {
        String userId = requireUserId(authHeader);
        List<CompanyResponse> companies = companyRepository
                .findByOwnerId(userId)
                .stream()
                .map(CompanyResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(companies));
    }

    /**
     * Create a new company for this user.
     * Returns a new JWT with the new company as active.
     */
    @PostMapping("/my-companies")
    @Operation(summary = "Create a new company — returns new JWT with this company active")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createMyCompany(
            @RequestBody Map<String, String> body,
            @RequestHeader("Authorization") String authHeader
    ) {
        String name = body.get("name");
        if (name == null || name.isBlank())
            throw TaskQueueException.badRequest("Company name is required");

        String token  = authHeader.substring(7);
        String userId = jwtService.getUserId(token);
        String email  = jwtService.getEmail(token);
        String role   = jwtService.getRole(token);
        String uname  = jwtService.validateToken(token).get("name", String.class);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> TaskQueueException.notFound("User", userId));

        // Generate unique slug
        String slug = toSlug(name);
        slug = uniqueSlug(slug);

        Company company = Company.builder()
                .owner(user)
                .name(name)
                .slug(slug)
                .isActive(true)
                .build();
        company = companyRepository.save(company);
        log.info("New company created by client: name={} user={}", name, email);

        // Issue new JWT with this company as active
        String newToken = jwtService.generateToken(userId, email, role, uname, company.getId());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(Map.of(
                "token",       newToken,
                "companyId",   company.getId(),
                "companyName", company.getName(),
                "slug",        company.getSlug(),
                "message",     "Company created. Use the new token for further requests."
        )));
    }

    /**
     * Switch active company — issues a new JWT with the selected company.
     * User must own the company they are switching to.
     */
    @PostMapping("/switch-company/{companyId}")
    @Operation(summary = "Switch active company — returns new JWT")
    public ResponseEntity<ApiResponse<Map<String, Object>>> switchCompany(
            @PathVariable String companyId,
            @RequestHeader("Authorization") String authHeader
    ) {
        String token  = authHeader.substring(7);
        String userId = jwtService.getUserId(token);
        String email  = jwtService.getEmail(token);
        String role   = jwtService.getRole(token);
        String uname  = jwtService.validateToken(token).get("name", String.class);

        // Verify user owns this company
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> TaskQueueException.notFound("Company", companyId));

        if (!company.getOwner().getId().equals(userId)) {
            throw TaskQueueException.forbidden("You do not own this company");
        }

        // Issue new JWT with switched company
        String newToken = jwtService.generateToken(userId, email, role, uname, companyId);

        log.info("Company switched: user={} company={}", email, company.getName());

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "token",       newToken,
                "companyId",   company.getId(),
                "companyName", company.getName(),
                "message",     "Switched to " + company.getName()
        )));
    }

    // ════════════════════════════════════════════════════════
    // ACTIVE COMPANY
    // ════════════════════════════════════════════════════════

    @GetMapping("/my-company")
    @Operation(summary = "Get your active company")
    public ResponseEntity<ApiResponse<CompanyResponse>> getMyCompany() {
        String companyId = requireCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> TaskQueueException.notFound("Company", companyId));
        return ResponseEntity.ok(ApiResponse.ok(CompanyResponse.from(company)));
    }

    // ════════════════════════════════════════════════════════
    // METRICS
    // ════════════════════════════════════════════════════════

    @GetMapping("/metrics")
    @Operation(summary = "Dashboard metrics for your active company")
    public ResponseEntity<ApiResponse<MetricsResponse>> getMyMetrics() {
        String companyId = requireCompanyId();
        List<String> projectIds = projectRepository.findByCompanyId(companyId)
                .stream().map(Project::getId).toList();

        long total = 0, queued = 0, running = 0, success = 0, failed = 0, dead = 0;
        for (String pid : projectIds) {
            total   += jobRepository.countByProjectId(pid);
            queued  += jobRepository.countByProjectIdAndStatus(pid, Job.Status.QUEUED);
            running += jobRepository.countByProjectIdAndStatus(pid, Job.Status.RUNNING);
            success += jobRepository.countByProjectIdAndStatus(pid, Job.Status.SUCCESS);
            failed  += jobRepository.countByProjectIdAndStatus(pid, Job.Status.FAILED);
            dead    += jobRepository.countByProjectIdAndStatus(pid, Job.Status.DEAD);
        }

        long pendingDlq = projectIds.isEmpty() ? 0L
                : deadLetterRepository.countByJobProjectIdInAndReplayedAtIsNull(projectIds);

        return ResponseEntity.ok(ApiResponse.ok(MetricsResponse.builder()
                .totalJobs(total).queuedJobs(queued).runningJobs(running)
                .successJobs(success).failedJobs(failed).deadJobs(dead)
                .pendingDlq(pendingDlq)
                .totalCompanies(1L)
                .totalProjects((long) projectIds.size())
                .totalApiKeys(projectIds.isEmpty() ? 0L
                        : apiKeyRepository.countByProjectIdIn(projectIds))
                .build()));
    }

    // ════════════════════════════════════════════════════════
    // PROJECTS
    // ════════════════════════════════════════════════════════

    @GetMapping("/projects")
    @Operation(summary = "List projects in your active company")
    public ResponseEntity<ApiResponse<List<ProjectResponse>>> listProjects() {
        String companyId = requireCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(
                projectRepository.findByCompanyId(companyId)
                        .stream().map(ProjectResponse::from).toList()
        ));
    }

    @PostMapping("/projects")
    @Operation(summary = "Create project in your active company")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest req
    ) {
        String companyId = requireCompanyId();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> TaskQueueException.notFound("Company", companyId));

        Project project = Project.builder()
                .company(company).name(req.getName())
                .description(req.getDescription())
                .environment(req.getEnvironment() != null
                        ? req.getEnvironment() : Project.Environment.PRODUCTION)
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ProjectResponse.from(projectRepository.save(project))));
    }

    // ════════════════════════════════════════════════════════
    // API KEYS
    // ════════════════════════════════════════════════════════

    @GetMapping("/projects/{projectId}/keys")
    public ResponseEntity<ApiResponse<List<ApiKeySummaryResponse>>> listKeys(
            @PathVariable String projectId
    ) {
        assertProjectBelongsToMe(projectId);
        return ResponseEntity.ok(ApiResponse.ok(
                apiKeyService.listKeysForProject(projectId)
                        .stream().map(ApiKeySummaryResponse::from).toList()
        ));
    }

    @PostMapping("/keys")
    public ResponseEntity<ApiResponse<CreateApiKeyResponse>> createKey(
            @Valid @RequestBody CreateApiKeyRequest req
    ) {
        assertProjectBelongsToMe(req.getProjectId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(apiKeyService.createKey(req)));
    }

    @DeleteMapping("/keys/{keyId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> revokeKey(
            @PathVariable String keyId
    ) {
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
            assertProjectBelongsToMe(projectId);
            jobs = status != null
                    ? jobRepository.findByProjectIdAndStatus(projectId, status, pageable)
                    : jobRepository.findByProjectId(projectId, pageable);
        } else {
            List<String> pids = projectRepository.findByCompanyId(companyId)
                    .stream().map(Project::getId).toList();
            jobs = status != null
                    ? jobRepository.findByProjectIdInAndStatus(pids, status, pageable)
                    : jobRepository.findByProjectIdIn(pids, pageable);
        }

        return ResponseEntity.ok(ApiResponse.ok(jobs.map(JobDetailResponse::from)));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ApiResponse<JobDetailResponse>> getJob(@PathVariable String jobId) {
        Job job = jobRepository.findByIdWithRelations(jobId)
                .orElseThrow(() -> TaskQueueException.notFound("Job", jobId));
        assertProjectBelongsToMe(job.getProject().getId());
        return ResponseEntity.ok(ApiResponse.ok(JobDetailResponse.from(job)));
    }

    // ════════════════════════════════════════════════════════
    // SMTP
    // ════════════════════════════════════════════════════════

    @GetMapping("/smtp")
    public ResponseEntity<ApiResponse<List<SmtpConfigResponse>>> listSmtp() {
        String companyId = requireCompanyId();
        return ResponseEntity.ok(ApiResponse.ok(
                smtpConfigRepository.findByCompanyId(companyId)
                        .stream().map(SmtpConfigResponse::from).toList()
        ));
    }

    @PostMapping("/smtp")
    public ResponseEntity<ApiResponse<SmtpConfigResponse>> createSmtp(
            @Valid @RequestBody CreateSmtpRequest req
    ) {
        String myCompanyId = requireCompanyId();
        Company company = companyRepository.findById(myCompanyId)
                .orElseThrow(() -> TaskQueueException.notFound("Company", myCompanyId));

        SmtpConfig config = SmtpConfig.builder()
                .company(company).purpose(req.getPurpose()).label(req.getLabel())
                .fromEmail(req.getFromEmail()).fromName(req.getFromName())
                .host(req.getHost()).port(req.getPort()).username(req.getUsername())
                .passwordEnc(encryptionService.encrypt(req.getPassword()))
                .useTls(req.getUseTls()).build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(SmtpConfigResponse.from(smtpConfigRepository.save(config))));
    }

    @PostMapping("/smtp/{smtpId}/test")
    public ResponseEntity<ApiResponse<Map<String, String>>> testSmtp(@PathVariable String smtpId) {
        SmtpConfig config = smtpConfigRepository.findById(smtpId)
                .orElseThrow(() -> TaskQueueException.notFound("SmtpConfig", smtpId));
        assertCompanyOwnsSmtp(config);
        smtpService.testConnection(config);
        config.setIsVerified(true);
        smtpConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "SMTP verified")));
    }

    // ════════════════════════════════════════════════════════
    // DLQ
    // ════════════════════════════════════════════════════════

    @GetMapping("/dlq")
    public ResponseEntity<ApiResponse<Page<DeadLetterJobResponse>>> listDlq(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String companyId = requireCompanyId();
        List<String> pids = projectRepository.findByCompanyId(companyId)
                .stream().map(Project::getId).toList();
        Pageable pageable = PageRequest.of(page, size, Sort.by("failedAt").descending());
        Page<DeadLetterJob> dlqPage = deadLetterRepository
                .findByJobProjectIdInAndReplayedAtIsNull(pids, pageable);
        return ResponseEntity.ok(ApiResponse.ok(dlqPage.map(DeadLetterJobResponse::from)));
    }

    @PostMapping("/dlq/{dlqId}/replay")
    public ResponseEntity<ApiResponse<Map<String, String>>> replay(@PathVariable String dlqId) {
        DeadLetterJob dlq = deadLetterRepository.findById(dlqId)
                .orElseThrow(() -> TaskQueueException.notFound("DLQ", dlqId));
        assertProjectBelongsToMe(dlq.getJob().getProject().getId());
        String jobId = dlqService.replaySingle(dlqId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("jobId", jobId)));
    }

    // ════════════════════════════════════════════════════════
    // Security helpers
    // ════════════════════════════════════════════════════════

    private void assertProjectBelongsToMe(String projectId) {
        String myCompanyId = requireCompanyId();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> TaskQueueException.notFound("Project", projectId));
        if (!project.getCompany().getId().equals(myCompanyId))
            throw TaskQueueException.forbidden("This project belongs to a different company");
    }

    private void assertCompanyOwnsSmtp(SmtpConfig config) {
        String myCompanyId = requireCompanyId();
        if (!config.getCompany().getId().equals(myCompanyId))
            throw TaskQueueException.forbidden("This SMTP config belongs to a different company");
    }

    private String toSlug(String name) {
        return name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String uniqueSlug(String base) {
        if (!companyRepository.existsBySlug(base)) return base;
        int i = 2;
        while (companyRepository.existsBySlug(base + "-" + i)) i++;
        return base + "-" + i;
    }
}