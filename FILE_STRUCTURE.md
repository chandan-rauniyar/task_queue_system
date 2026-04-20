# Task Queue System — File Structure (Current State)

> Last updated after Phase 3 completion.
> 55 Java files + 4 SQL migrations + config files = backend complete.
> Status: ✅ Done | 🔜 Next (React admin panel) | 🔮 Future

---

## What you need to do before running

Step 1 — Copy all files into your IntelliJ project matching this exact folder structure.
Step 2 — Run: docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander
Step 3 — Wait 20 seconds. Verify: docker-compose ps (all 6 show "Up")
Step 4 — Update application.yml: change app.encryption.key to any 32-character string
Step 5 — Run TaskQueueApplication.java from IntelliJ
Step 6 — Watch console: Flyway runs 4 migrations, Kafka creates 4 topics, app starts on port 8080
Step 7 — Open Swagger: http://localhost:8080/api/v1/swagger-ui.html

---

## Root

```
task-queue-system/
├── docker-compose.yml      ✅  PostgreSQL + Redis + Kafka + Zookeeper + Kafka UI + Redis UI
├── Makefile                ✅  make dev, make run, make admin, make infra-up
├── README.md               ✅  Prerequisites and quick start
├── SETUP_GUIDE.md          ✅  Step-by-step setup with verification commands
├── FILE_STRUCTURE.md       ✅  This file — full file map + what to do
├── ENDPOINTS.md            ✅  All 22 REST endpoints with request/response examples
├── backend/                ✅  Spring Boot application — COMPLETE
└── admin-panel/            🔜  React admin panel — next phase
```

---

## backend/

```
backend/
├── Dockerfile              ✅  Multi-stage: Maven build → JRE 17 alpine runtime
├── pom.xml                 ✅  All dependencies: Web, JPA, PostgreSQL, Flyway,
│                               Redis, Kafka, Mail, Security, Actuator,
│                               Lombok, Swagger (springdoc), spring-retry, bouncycastle
│
└── src/main/
    │
    ├── resources/
    │   ├── application.yml                         ✅  All config — DB, Kafka, Redis,
    │   │                                               SMTP, encryption key, app props
    │   └── db/migration/
    │       ├── V1__create_users_companies.sql       ✅  users + companies tables
    │       ├── V2__create_projects_api_keys.sql     ✅  projects + api_keys tables
    │       ├── V3__create_smtp_configs.sql          ✅  smtp_configs table
    │       └── V4__create_jobs.sql                  ✅  jobs + dead_letter_jobs tables
    │
    └── java/com/taskqueue/
        │
        ├── TaskQueueApplication.java   ✅  Main class. @EnableRetry @EnableAsync @EnableScheduling
        │
        ├── config/                         Loaded once on startup
        │   ├── AppProperties.java      ✅  Reads all "app.*" from yml into typed Java object
        │   ├── KafkaConfig.java        ✅  4 topics + ContainerFactory with manual ack
        │   ├── RedisConfig.java        ✅  RedisTemplate<String,Object> with JSON serializer
        │   ├── RestTemplateConfig.java ✅  RestTemplate with 10s timeouts for WebhookService
        │   └── SecurityConfig.java     ✅  BCrypt bean + CORS for localhost:3000 + permit all
        │
        ├── filter/                         Runs on EVERY request in this order
        │   ├── AdminBypassFilter.java  ✅  Order 0 — blocks /admin/** from non-localhost IPs
        │   ├── ApiKeyFilter.java       ✅  Order 1 — validates X-API-Key via Redis→PostgreSQL
        │   ├── RateLimitFilter.java    ✅  Order 2 — Redis INCR counter, 429 if over quota
        │   └── ClientContext.java      ✅  ThreadLocal — holds clientId/projectId/companyId
        │                                   per request. Cleared after every request.
        │
        ├── model/                          JPA entities mapped to PostgreSQL tables
        │   ├── User.java               ✅  id, email, password_hash, role(ADMIN/CLIENT/...), is_active
        │   │                               Role enum: ADMIN, CLIENT, DEVELOPER, VIEWER
        │   ├── Company.java            ✅  id, owner_id→users, name, slug(unique), is_active
        │   │                               Relations: →User(owner), →List<Project>, →List<SmtpConfig>
        │   ├── Project.java            ✅  id, company_id→companies, name, environment, is_active
        │   │                               Environment enum: PRODUCTION, STAGING, DEV
        │   │                               Relations: →Company, →List<ApiKey>
        │   ├── ApiKey.java             ✅  id, project_id→projects, key_prefix, key_hash(SHA-256),
        │   │                               key_hint(last 4 chars), label, rate_limit_per_min, is_active
        │   │                               isValid() checks active + not expired
        │   ├── SmtpConfig.java         ✅  id, company_id→companies, purpose, from_email, from_name,
        │   │                               host, port, username, password_enc(AES-256), use_tls
        │   │                               Purpose enum: SUPPORT, BILLING, NOREPLY, ALERT, CUSTOM
        │   ├── Job.java                ✅  id, project_id, api_key_id, type, payload(jsonb),
        │   │                               status, priority, retry_count, max_retries,
        │   │                               callback_url, idempotency_key, scheduled_at,
        │   │                               started_at, completed_at, error_message
        │   │                               Status enum: QUEUED, RUNNING, SUCCESS, FAILED, DEAD
        │   │                               Priority enum: HIGH, NORMAL, LOW
        │   │                               canRetry() → status==FAILED && retryCount < maxRetries
        │   ├── DeadLetterJob.java      ✅  id, job_id→jobs, original_payload(jsonb),
        │   │                               failure_reason, retry_count, failed_at,
        │   │                               replayed_at(null=not replayed), replayed_job_id
        │   └── JobEvent.java           ✅  Kafka message DTO (NOT a DB entity)
        │                                   jobId, projectId, companyId, type, payload,
        │                                   priority, smtpPurpose, callbackUrl, retryCount
        │
        ├── repository/                     JPA interfaces — Spring generates SQL automatically
        │   ├── UserRepository.java     ✅  findByEmail, existsByEmail, findByRole
        │   ├── CompanyRepository.java  ✅  findByOwnerId, findBySlug, existsBySlug
        │   ├── ProjectRepository.java  ✅  findByCompanyId, findByCompanyIdAndIsActiveTrue
        │   ├── ApiKeyRepository.java   ✅  findByKeyHash (hot path — Redis cached),
        │   │                               findByProjectId, updateLastUsedAt
        │   ├── SmtpConfigRepository.java ✅ findByCompanyIdAndPurposeAndIsActiveTrue (worker uses),
        │   │                               findByCompanyId
        │   ├── JobRepository.java      ✅  findByProjectId, findByProjectIdAndStatus,
        │   │                               findByStatus, countByStatus, findRetryableJobs,
        │   │                               findByProjectIdAndIdempotencyKey, countGroupedByStatus
        │   └── DeadLetterRepository.java ✅ findByReplayedAtIsNull, findByJobId,
        │                                   countByReplayedAtIsNull
        │
        ├── dto/                            Public classes only — no package-private
        │   ├── ApiResponse.java        ✅  Universal wrapper: {success, data, error, timestamp}
        │   │                               ApiResponse.ok(data) / ApiResponse.error("msg")
        │   ├── JobRequest.java         ✅  type, payload, callbackUrl, priority, idempotencyKey,
        │   │                               maxRetries, smtpPurpose, scheduledAt
        │   ├── JobResponse.java        ✅  jobId, status, priority, type, createdAt, statusUrl
        │   │                               JobResponse.from(job)
        │   ├── JobDetailResponse.java  ✅  Full job detail + projectName + companyName + canRetry
        │   │                               JobDetailResponse.from(job)
        │   ├── CreateApiKeyRequest.java ✅ projectId, label, rateLimitPerMin, expiresAt, environment
        │   ├── CreateApiKeyResponse.java ✅ id, rawKey(shown ONCE), keyHint, label, warning msg
        │   ├── ApiKeySummaryResponse.java ✅ id, label, keyHint, isActive, lastUsedAt (no rawKey)
        │   │                               ApiKeySummaryResponse.from(apiKey)
        │   ├── CreateCompanyRequest.java ✅ name, slug (validated: lowercase+hyphens only)
        │   ├── CompanyResponse.java    ✅  id, name, slug, isActive, ownerEmail, createdAt
        │   │                               CompanyResponse.from(company)
        │   ├── CreateProjectRequest.java ✅ companyId, name, description, environment
        │   ├── ProjectResponse.java    ✅  id, name, environment, isActive, companyId, companyName
        │   │                               ProjectResponse.from(project)
        │   ├── CreateSmtpRequest.java  ✅  companyId, purpose, label, fromEmail, fromName,
        │   │                               host, port, username, password(raw — encrypted on save)
        │   ├── SmtpConfigResponse.java ✅  All fields EXCEPT password — never returned
        │   │                               SmtpConfigResponse.from(smtpConfig)
        │   └── MetricsResponse.java    ✅  Job counts by status + entity totals
        │
        ├── exception/
        │   ├── TaskQueueException.java ✅  RuntimeException + HttpStatus
        │   │                               .notFound(entity, id) → 404
        │   │                               .badRequest(msg)      → 400
        │   │                               .conflict(msg)        → 409
        │   │                               .forbidden(msg)       → 403
        │   └── GlobalExceptionHandler.java ✅ @ControllerAdvice — all exceptions → clean JSON
        │
        ├── service/
        │   ├── JobService.java         ✅  enqueue(JobRequest) → JobResponse
        │   │                               getJob(jobId), listJobs(status,page,size)
        │   │                               retryJob(jobId) → re-publishes to Kafka
        │   ├── ApiKeyService.java      ✅  createKey(CreateApiKeyRequest) → CreateApiKeyResponse
        │   │                               revokeKey(keyId) → evicts Redis cache immediately
        │   │                               listKeysForProject(projectId)
        │   ├── EncryptionService.java  ✅  AES-256 encrypt(raw) / decrypt(enc) for SMTP passwords
        │   │                               Key from app.encryption.key (must be 32 chars)
        │   ├── RetryService.java       ✅  handleFailure(jobId, error) → retry OR moveToDlq
        │   │                               Backoff: 30s → 60s → 120s (exponential)
        │   │                               moveToDlq() → creates DeadLetterJob + fires webhook
        │   ├── WebhookService.java     ✅  fireAsync() → @Async non-blocking POST to callbackUrl
        │   │                               fire() → @Retryable 3 attempts 5s backoff
        │   ├── DlqService.java         ✅  listPending(page,size) → Page<DeadLetterJob>
        │   │                               replaySingle(dlqId) → resets job + re-publishes Kafka
        │   │                               replayAll() → bulk replay all pending
        │   │                               countPending() → for dashboard badge
        │   └── SmtpService.java        ✅  send(companyId, purpose, to, subject, htmlBody)
        │                                   testConnection(SmtpConfig) → SMTP handshake
        │                                   evictCache(smtpConfigId) → removes cached sender
        │                                   Internal sender cache: Map<configId, JavaMailSender>
        │
        ├── worker/
        │   ├── BaseWorker.java         ✅  Abstract class. execute(JobEvent):
        │   │                               mark RUNNING → call process() → mark SUCCESS/FAILED
        │   │                               On exception → calls RetryService.handleFailure()
        │   ├── WorkerDispatcher.java   ✅  @KafkaListener on all 3 topics (high/normal/low)
        │   │                               Routes by job type: SEND_EMAIL→Email, GENERATE_PDF→Pdf,
        │   │                               anything else→Generic. Manual ack after processing.
        │   ├── EmailWorker.java        ✅  Reads: to, subject, body from payload
        │   │                               Uses SmtpService.send() with company SMTP
        │   │                               Respects smtpPurpose from JobEvent
        │   ├── PdfWorker.java          ✅  Reads: templateName, outputFileName from payload
        │   │                               Stub — add iText/PDFBox for real generation
        │   └── GenericWorker.java      ✅  Fallback for any unrecognised job type
        │                                   Logs payload, sleeps 100ms, marks SUCCESS
        │
        └── controller/
            ├── JobController.java      ✅  POST /jobs → enqueue
            │                               GET  /jobs/{id} → status
            │                               GET  /jobs → list (paginated, filtered)
            │                               POST /jobs/{id}/retry → re-queue
            └── AdminController.java    ✅  All /admin/** endpoints:
                                            GET/POST /admin/companies
                                            PATCH /admin/companies/{id}/toggle
                                            GET/POST /admin/companies/{id}/projects
                                            POST /admin/projects, PATCH /admin/projects/{id}/toggle
                                            POST /admin/keys, GET /admin/projects/{id}/keys
                                            DELETE /admin/keys/{id}
                                            GET /admin/jobs (browse all, filter)
                                            GET /admin/dlq, POST /admin/dlq/{id}/replay
                                            POST /admin/dlq/replay-all
                                            GET/POST /admin/companies/{id}/smtp
                                            POST /admin/smtp/{id}/test
                                            DELETE/PATCH /admin/smtp/{id}
                                            GET /admin/metrics
```

---

## admin-panel/ (next phase)

```
admin-panel/
├── package.json            ✅  React 18, react-router-dom, axios, recharts, react-query
│
└── src/
    ├── index.js            🔜  React root
    ├── App.jsx             🔜  Router — all page routes
    ├── api.js              🔜  Axios instance → http://localhost:8080/api/v1
    │
    ├── pages/
    │   ├── Dashboard.jsx       🔜  Metrics cards + job counts. Polls /admin/metrics every 5s
    │   ├── Companies.jsx       🔜  List + create companies
    │   ├── Projects.jsx        🔜  List + create projects per company
    │   ├── ApiKeys.jsx         🔜  Create key (show raw once), list with hint, revoke
    │   ├── Jobs.jsx            🔜  Browse all jobs, filter by project/status, paginated
    │   ├── JobDetail.jsx       🔜  Full job detail, payload viewer, retry button
    │   ├── DeadLetterQueue.jsx 🔜  DLQ table, replay single/all, failure reason
    │   └── SmtpSettings.jsx    🔜  Add/remove SMTP configs per company, test connection
    │
    └── components/
        ├── Sidebar.jsx         🔜  Navigation
        ├── MetricCard.jsx      🔜  Number + label dashboard card
        ├── JobTable.jsx        🔜  Reusable paginated table
        ├── StatusBadge.jsx     🔜  Coloured pill: QUEUED/RUNNING/SUCCESS/FAILED/DEAD
        └── ConfirmModal.jsx    🔜  Reusable confirm dialog
```

---

## What is fully done

| Layer | Files | Status |
|-------|-------|--------|
| Infrastructure | docker-compose.yml, Makefile | ✅ |
| DB Migrations | V1-V4 SQL | ✅ |
| Config | AppProperties, Kafka, Redis, Security, RestTemplate | ✅ |
| Filters | AdminBypass, ApiKey, RateLimit, ClientContext | ✅ |
| Models | User, Company, Project, ApiKey, SmtpConfig, Job, DeadLetterJob, JobEvent | ✅ |
| Repositories | All 7 | ✅ |
| DTOs | All 13 — all public, all with .from() factory methods | ✅ |
| Exceptions | TaskQueueException, GlobalExceptionHandler | ✅ |
| Services | Job, ApiKey, Encryption, Retry, Webhook, Dlq, Smtp | ✅ |
| Workers | Base, Dispatcher, Email, Pdf, Generic | ✅ |
| Controllers | Job, Admin | ✅ |
| Admin panel | — | 🔜 |

---

## Exact steps to run right now

### 1. Start infrastructure
```bash
docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander
```

### 2. Verify all 6 are running
```bash
docker-compose ps
# All must show "Up"

docker exec tq_postgres pg_isready -U tquser -d taskqueue_db
# Must print: accepting connections

docker exec tq_redis redis-cli ping
# Must print: PONG
```

### 3. Fix application.yml — two things to change
```yaml
app:
  encryption:
    key: "YourExact32CharacterKeyGoesHere!!"  # exactly 32 chars

spring:
  mail:
    username: your-gmail@gmail.com
    password: xxxx-xxxx-xxxx-xxxx  # Gmail App Password
```

### 4. Run from IntelliJ
```
Open: TaskQueueApplication.java
Click: Green ▶ Run button

Watch for these lines in console:
  "Flyway: Successfully applied 4 migrations"
  "Creating topic: jobs.high-priority"
  "Started TaskQueueApplication in X seconds"
```

### 5. Verify it's live
```
Swagger UI:    http://localhost:8080/api/v1/swagger-ui.html
Health check:  http://localhost:8080/api/v1/actuator/health
Kafka UI:      http://localhost:8090
Redis UI:      http://localhost:8091
```

### 6. First Postman test sequence
```
1. POST /admin/companies         → creates "Swiggy", copy id
2. POST /admin/projects          → creates "Order Service", copy id
3. POST /admin/keys              → creates API key, COPY rawKey immediately
4. POST /jobs (X-API-Key: ...)   → enqueue first job, get jobId
5. GET  /jobs/{jobId}            → check status = QUEUED
6. Open Kafka UI → jobs.high-priority topic → see your message
```

---

## What to build next

**Phase 4 — Admin Panel (React)**
8 pages + 5 components + api.js + routing
All talking to localhost:8080 with no auth needed (AdminBypassFilter handles it)

**Phase 5 — Future**
ClientController.java + client-ui/ for clients to view their own jobs via UI
DEVELOPER and VIEWER roles via user_company_roles table
Scheduled job execution (scheduledAt field already in Job entity)