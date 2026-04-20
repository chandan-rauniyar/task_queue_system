# Task Queue System — Complete Project Guide
# Everything in one place: structure, setup, run, understand

---

## What is this project

A distributed background job processing system — like Celery for Python but built in Java.
External apps submit jobs via REST API. Jobs are queued in Kafka, processed by workers,
and results are delivered via webhooks. Admin panel built in React for full visibility and control.

---

## Tech Stack

### Backend
| Technology       | Version | Purpose |
|-----------------|---------|---------|
| Java            | 17      | Language |
| Spring Boot     | 3.2     | Web framework, dependency injection, auto-config |
| PostgreSQL      | 16      | Primary database — stores all entities |
| Redis           | 7.2     | API key cache + rate limiting counters |
| Apache Kafka    | 3.9     | Message queue — 3 priority topics |
| Flyway          | 10      | Auto database migrations on startup |
| Hibernate / JPA | 6.4     | ORM — maps Java classes to DB tables |
| Lombok          | 1.18    | Removes boilerplate code (@Data, @Builder etc) |
| Maven           | 3.9     | Build tool and dependency manager |

### Frontend
| Technology       | Version | Purpose |
|-----------------|---------|---------|
| React           | 19      | UI framework |
| Vite            | 8       | Build tool — instant hot reload |
| Tailwind CSS    | 3.4     | Utility-first CSS — dark/light mode built in |
| TanStack Query  | 5       | Server state, caching, auto-refetch |
| Axios           | 1.15    | HTTP client with JWT interceptor |
| React Router    | 7       | Client-side routing |
| Recharts        | 3       | Bar charts on dashboard |
| Lucide React    | latest  | Icon set |
| React Hot Toast | 2.6     | Toast notifications |
| date-fns        | 4       | Date formatting |

### Infrastructure (Docker)
| Service         | Port  | Purpose |
|----------------|-------|---------|
| PostgreSQL      | 5432  | Database |
| Redis           | 6379  | Cache |
| Zookeeper       | 2181  | Kafka coordinator |
| Kafka           | 9092  | Message broker |
| Kafka UI        | 8090  | Visual Kafka browser |
| Redis Commander | 8091  | Visual Redis browser |

---

## Complete File Structure

```
task-queue-system/
│
├── docker-compose.yml              Infrastructure — all 6 services
├── Makefile                        Shortcut commands
├── README.md                       Quick start
├── MASTER_GUIDE.md                 Full setup + run + manage guide
├── FILE_STRUCTURE.md               Every Java file with its purpose
├── ENDPOINTS.md                    All 25 REST endpoints with examples
├── SETUP_GUIDE.md                  Step by step first time setup
├── TESTING_GUIDE.md                48 Postman tests across 14 phases
├── GMAIL_TESTING_GUIDE.md          End to end email test with Gmail
├── FRONTEND_GUIDE.md               Frontend architecture and build guide
│
├── backend/                        Spring Boot application
│   ├── pom.xml                     All Maven dependencies
│   ├── Dockerfile                  Container build
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml     All config — DB, Kafka, Redis, SMTP, JWT
│       │   └── db/migration/       Flyway SQL — runs automatically on startup
│       │       ├── V1__create_users_companies.sql
│       │       ├── V2__create_projects_api_keys.sql
│       │       ├── V3__create_smtp_configs.sql
│       │       └── V4__create_jobs.sql
│       │
│       └── java/com/taskqueue/
│           │
│           ├── TaskQueueApplication.java       Main entry point
│           │
│           ├── config/                         Loaded once on startup
│           │   ├── AppProperties.java          Reads app.* from yml into typed object
│           │   ├── KafkaConfig.java            4 topics + ErrorHandlingDeserializer
│           │   ├── RedisConfig.java            RedisTemplate with JSON serializer
│           │   ├── RestTemplateConfig.java     RestTemplate for WebhookService
│           │   └── SecurityConfig.java         BCrypt + CORS for localhost:3000
│           │
│           ├── filter/                         Runs on every request, in this order
│           │   ├── AdminBypassFilter.java      Order 0 — blocks /admin from non-localhost
│           │   ├── ApiKeyFilter.java           Order 1 — validates X-API-Key header
│           │   │                               Uses JOIN FETCH to avoid LazyInit errors
│           │   ├── RateLimitFilter.java        Order 2 — Redis counter per key per minute
│           │   └── ClientContext.java          ThreadLocal — holds projectId/companyId
│           │
│           ├── model/                          JPA entities → PostgreSQL tables
│           │   ├── User.java                   id, email, password_hash, role VARCHAR(20)
│           │   ├── Company.java                id, owner_id, name, slug unique
│           │   ├── Project.java                id, company_id, name, environment VARCHAR(20)
│           │   ├── ApiKey.java                 id, project_id, key_hash SHA-256, key_hint
│           │   ├── SmtpConfig.java             id, company_id, purpose VARCHAR(20), password AES-256
│           │   ├── Job.java                    id, payload JSONB, status VARCHAR(20), priority VARCHAR(10)
│           │   ├── DeadLetterJob.java          id, job_id, failure_reason, replayed_at
│           │   └── JobEvent.java               Kafka DTO — not a DB entity
│           │
│           ├── repository/                     JPA interfaces — Spring generates SQL
│           │   ├── UserRepository.java
│           │   ├── CompanyRepository.java
│           │   ├── ProjectRepository.java
│           │   ├── ApiKeyRepository.java       findByKeyHashWithProjectAndCompany (JOIN FETCH)
│           │   ├── SmtpConfigRepository.java
│           │   ├── JobRepository.java          findByIdWithRelations (JOIN FETCH)
│           │   └── DeadLetterRepository.java
│           │
│           ├── dto/                            Request/Response objects — all public classes
│           │   ├── ApiResponse.java            Universal wrapper {success, data, error}
│           │   ├── JobRequest.java             POST /jobs body
│           │   ├── JobResponse.java            POST /jobs response — jobId + status
│           │   ├── JobDetailResponse.java      GET /jobs/{id} — full detail
│           │   ├── CreateApiKeyRequest.java
│           │   ├── CreateApiKeyResponse.java   includes rawKey shown once
│           │   ├── ApiKeySummaryResponse.java  no rawKey — just hint
│           │   ├── CreateCompanyRequest.java
│           │   ├── CompanyResponse.java
│           │   ├── CreateProjectRequest.java
│           │   ├── ProjectResponse.java
│           │   ├── CreateSmtpRequest.java
│           │   ├── SmtpConfigResponse.java     no password field ever returned
│           │   └── MetricsResponse.java
│           │
│           ├── exception/
│           │   ├── TaskQueueException.java     .notFound() .badRequest() .conflict() .forbidden()
│           │   └── GlobalExceptionHandler.java @ControllerAdvice — all errors → clean JSON
│           │
│           ├── service/
│           │   ├── JobService.java             enqueue, getJob, listJobs, retryJob
│           │   │                               Uses findByIdWithRelations everywhere
│           │   ├── ApiKeyService.java          createKey, revokeKey, listKeysForProject
│           │   ├── EncryptionService.java      AES-256 encrypt/decrypt for SMTP passwords
│           │   ├── RetryService.java           handleFailure → retry OR moveToDlq
│           │   │                               Extracts all values before virtual thread
│           │   ├── WebhookService.java         @Async + @Retryable POST to callbackUrl
│           │   ├── DlqService.java             replaySingle, replayAll, countPending
│           │   └── SmtpService.java            Dynamic JavaMailSender per company SMTP config
│           │                                   In-memory cache Map<configId, JavaMailSender>
│           │
│           ├── worker/
│           │   ├── BaseWorker.java             Abstract — lifecycle: RUNNING → SUCCESS/FAILED
│           │   │                               Uses findByIdWithRelations
│           │   ├── WorkerDispatcher.java       @KafkaListener on all 3 topics
│           │   │                               Routes by job.type to correct worker
│           │   ├── EmailWorker.java            SEND_EMAIL — calls SmtpService
│           │   ├── PdfWorker.java              GENERATE_PDF — stub, add iText for real PDF
│           │   └── GenericWorker.java          Fallback for unknown job types
│           │
│           └── controller/
│               ├── JobController.java          POST/GET /jobs — requires X-API-Key
│               └── AdminController.java        All /admin/** — localhost only
│
│                 ─── STILL NEEDED IN BACKEND ───
│               ├── AuthController.java         POST /auth/login — NOT YET CREATED
│               └── JwtService.java             JWT generate/validate — NOT YET CREATED
│
└── frontend/                       React 19 admin panel
    ├── index.html                  HTML entry point
    ├── vite.config.js              Port 3000, proxy /api → localhost:8080
    ├── tailwind.config.js          Dark mode class, primary purple color scale
    ├── postcss.config.js           Tailwind + autoprefixer
    │
    └── src/
        ├── main.jsx                React root
        ├── App.jsx                 Router + QueryClient + ThemeProvider + AuthProvider
        ├── index.css               Tailwind + custom component classes
        │
        ├── context/
        │   ├── AuthContext.jsx     JWT token + user in localStorage, login/logout
        │   └── ThemeContext.jsx    Dark/light toggle, persists to localStorage
        │
        ├── api/
        │   ├── axios.js            Instance with JWT interceptor + 401 redirect
        │   ├── auth.js             login()
        │   ├── companies.js        getCompanies, createCompany, toggleCompany
        │   ├── projects.js         getProjects, createProject, toggleProject
        │   ├── apiKeys.js          getApiKeys, createApiKey, revokeApiKey
        │   ├── jobs.js             getJobs, getJob, retryJob
        │   ├── dlq.js              getDlq, replaySingle, replayAll
        │   ├── smtp.js             getSmtpConfigs, createSmtp, testSmtp, toggleSmtp, deleteSmtp
        │   └── metrics.js          getMetrics
        │
        ├── components/
        │   ├── ProtectedRoute.jsx  Redirects to /login if no token in localStorage
        │   ├── layout/
        │   │   ├── AppLayout.jsx   Sidebar + TopBar + <Outlet /> wrapper
        │   │   ├── Sidebar.jsx     Nav links, DLQ badge, user info, logout
        │   │   └── TopBar.jsx      Page title, theme toggle button
        │   ├── ui/
        │   │   ├── Badge.jsx       Status/env/purpose/priority colored pills
        │   │   ├── Modal.jsx       ESC to close, backdrop click, 4 sizes
        │   │   └── index.jsx       Spinner, EmptyState, PageHeader
        │   └── dashboard/
        │       └── MetricCard.jsx  Number + icon + label card
        │
        └── pages/
            ├── Login.jsx           Email + password → POST /auth/login → JWT → localStorage
            ├── Dashboard.jsx       Metrics grid, Recharts bar chart, recent jobs, DLQ alert
            ├── Companies.jsx       Table + create modal + toggle active
            ├── Projects.jsx        Grouped by environment (PRODUCTION/STAGING/DEV) + create
            ├── ApiKeys.jsx         Key hint display, env prefix auto-derived, raw key shown once
            ├── Jobs.jsx            Filter by company/project/status, paginated table
            ├── JobDetail.jsx       Full detail, payload JSON, timeline, retry button
            ├── DeadLetterQueue.jsx Pending dead jobs, replay single/all, confirm modal
            └── SmtpSettings.jsx    Card grid per SMTP config, test connection, toggle, delete
```

---

## What is done vs what still needs to be done

### DONE — 100% complete
```
✅ PostgreSQL schema — 7 tables via 4 Flyway migrations
✅ All 7 JPA entities with VARCHAR enums (no custom PG types)
✅ All 7 repositories with JOIN FETCH queries
✅ All 13 DTOs as separate public classes
✅ All 7 services with LazyInit fix (extract before virtual threads)
✅ All 5 workers with BaseWorker lifecycle
✅ JobController — POST/GET /jobs
✅ AdminController — all 23 admin endpoints
✅ ApiKeyFilter — JOIN FETCH fix, Redis validity cache
✅ RetryService — exponential backoff 30s→60s→120s
✅ DlqService — replay single and bulk
✅ KafkaConfig — ErrorHandlingDeserializer (no more deserialization crashes)
✅ Gmail SMTP working via smtp_configs table
✅ Docker infrastructure — 6 services
✅ React frontend — 8 pages, dark/light toggle, JWT auth flow
```

### NOT DONE — Must build before frontend login works
```
❌ AuthController.java  — POST /auth/login endpoint
❌ JwtService.java      — generate and validate JWT tokens
❌ pom.xml              — add jjwt-api, jjwt-impl, jjwt-jackson dependencies
❌ application.yml      — add app.jwt.secret and app.jwt.expiry-hours
❌ AdminBypassFilter    — update to also accept valid JWT Bearer tokens
```

---

## Setup — First time only

### Step 1 — Install required tools

```
Java 17 JDK    → https://adoptium.net  (Temurin 17 LTS)
Maven 3.9+     → https://maven.apache.org  OR  brew install maven
Docker Desktop → https://www.docker.com/products/docker-desktop
Node.js 18+    → https://nodejs.org (LTS version)
IntelliJ IDEA  → https://www.jetbrains.com/idea (Community free)
Postman        → https://www.postman.com/downloads

Verify installs:
  java -version    → openjdk 17
  mvn -version     → Apache Maven 3.x
  docker -version  → Docker 24+
  node -version    → v18+ or v20+
```

### Step 2 — Start infrastructure

```bash
# In project root (where docker-compose.yml is)
docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander

# Wait 20 seconds, then verify all 6 are Up
docker-compose ps

# Test each:
docker exec tq_postgres pg_isready -U tquser -d taskqueue_db
# → "accepting connections"

docker exec tq_redis redis-cli ping
# → PONG
```

### Step 3 — Configure application.yml

Open `backend/src/main/resources/application.yml`

**Two mandatory changes:**

```yaml
# 1. Encryption key — MUST be exactly 32 characters
app:
  encryption:
    key: "YourExactly32CharKeyGoesHere123!"

# 2. Mailtrap SMTP (get from mailtrap.io → SMTP Settings)
spring:
  mail:
    host: sandbox.smtp.mailtrap.io
    port: 2525
    username: your-mailtrap-username
    password: your-mailtrap-password
```

### Step 4 — Run backend in IntelliJ

```
Open: backend/ folder in IntelliJ
Wait: Maven sync completes (first time ~3 min)
Run:  TaskQueueApplication.java → green play button

Watch console for:
  "Successfully applied 4 migrations"  ← DB tables created
  "Creating topic: jobs.high-priority" ← Kafka topics created
  "Started TaskQueueApplication"       ← ready

Verify:
  http://localhost:8080/api/v1/actuator/health → status: UP
  http://localhost:8080/api/v1/swagger-ui.html → Swagger UI
```

### Step 5 — Run frontend

```bash
cd frontend
npm run dev
# Opens at http://localhost:3000

NOTE: Login page will fail until AuthController is built.
To bypass temporarily — see "Testing without login" below.
```

---

## Testing without login (temporary bypass)

While you build AuthController, edit `src/App.jsx` to remove ProtectedRoute:

```jsx
// Change this:
<Route path="/" element={
  <ProtectedRoute>
    <AppLayout />
  </ProtectedRoute>
}>

// To this temporarily:
<Route path="/" element={<AppLayout />}>
```

Now navigate directly to `http://localhost:3000/dashboard` — you can use all pages.
**Remember to put ProtectedRoute back after building the login endpoint.**

---

## Building the missing login endpoint

### Step 1 — Add JWT dependencies to pom.xml

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.3</version>
    <scope>runtime</scope>
</dependency>
```

### Step 2 — Add JWT config to application.yml

```yaml
app:
  jwt:
    secret: "YourJwtSecretKeyMustBe32CharsLong!!"   # exactly 32+ chars
    expiry-hours: 24
```

### Step 3 — Create JwtService.java in service/ package

```java
@Service
public class JwtService {
    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiry-hours}")
    private int expiryHours;

    public String generateToken(String userId, String email, String role) {
        return Jwts.builder()
            .subject(userId)
            .claim("email", email)
            .claim("role", role)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiryHours * 3600_000L))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public boolean isValid(String token) {
        try { validateToken(token); return true; }
        catch (Exception e) { return false; }
    }

    public String getRole(String token) {
        return validateToken(token).get("role", String.class);
    }
}
```

### Step 4 — Create AuthController.java in controller/ package

```java
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository  userRepository;
    private final JwtService      jwtService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(
        @RequestBody Map<String, String> body
    ) {
        String email    = body.get("email");
        String password = body.get("password");

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> TaskQueueException.badRequest("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw TaskQueueException.badRequest("Invalid credentials");
        }

        if (!user.getIsActive()) {
            throw TaskQueueException.forbidden("Account is disabled");
        }

        String token = jwtService.generateToken(
            user.getId(), user.getEmail(), user.getRole().name()
        );

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "token", token,
            "email", user.getEmail(),
            "role",  user.getRole().name()
        )));
    }
}
```

### Step 5 — Add BCrypt bean to SecurityConfig.java

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
}
```

### Step 6 — Update AdminBypassFilter to accept JWT

In `AdminBypassFilter.java`, add this check alongside the IP check:

```java
// Check 1: localhost IP (already exists)
// Check 2: valid JWT with ADMIN role (add this)
String authHeader = request.getHeader("Authorization");
if (authHeader != null && authHeader.startsWith("Bearer ")) {
    String token = authHeader.substring(7);
    try {
        if (jwtService.isValid(token) && "ADMIN".equals(jwtService.getRole(token))) {
            ClientContext.ClientInfo info = new ClientContext.ClientInfo();
            info.setAdminRequest(true);
            ClientContext.set(info);
            chain.doFilter(request, response);
            ClientContext.clear();
            return;
        }
    } catch (Exception ignored) {}
}
```

After these 6 steps, restart Spring Boot and test login:

```
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{ "email": "admin@taskqueue.local", "password": "admin123" }

Expected: { "success": true, "data": { "token": "eyJ...", "email": "...", "role": "ADMIN" } }
```

---

## Daily workflow

### Start work session

```bash
# 1. Open Docker Desktop — wait until running in taskbar

# 2. Start infrastructure
docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander

# 3. Wait 15 seconds for Kafka

# 4. Run Spring Boot from IntelliJ

# 5. Run frontend
cd frontend && npm run dev
```

### Stop work session

```bash
# Stop frontend: Ctrl+C in terminal

# Stop Spring Boot: stop button in IntelliJ

# Stop Docker (keeps data)
docker-compose stop
```

### Wipe everything and start fresh

```bash
docker-compose down -v
docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander
# Spring Boot recreates all tables on next start
```

---

## All URLs when running

```
React admin panel    http://localhost:3000
Spring Boot API      http://localhost:8080/api/v1
Swagger UI           http://localhost:8080/api/v1/swagger-ui.html
Health check         http://localhost:8080/api/v1/actuator/health
Kafka UI             http://localhost:8090
Redis UI             http://localhost:8091
pgAdmin (Docker)     http://localhost:5050  login: admin@taskqueue.local / admin123
PostgreSQL direct    localhost:5432  user: tquser  pass: tqpass123
```

---

## Common errors and fixes

### LazyInitializationException
```
Cause:  Accessing job.getProject() or job.getApiKey() outside a transaction
Fix:    Use jobRepository.findByIdWithRelations() instead of findById()
        Extract all values from entity before Thread.ofVirtual().start()
```

### Kafka deserialization error
```
Cause:  Old messages in Kafka topics have no type headers
Fix:    docker-compose down && docker volume rm *kafka* *zookeeper*
        docker-compose up -d
        Spring Boot recreates topics
```

### SQL enum error: operator does not exist user_role = character varying
```
Cause:  Using PostgreSQL custom ENUM types with Hibernate
Fix:    Use VARCHAR(N) columns in SQL with CHECK constraints
        Use @Enumerated(EnumType.STRING) without columnDefinition in entities
```

### Mail health DOWN
```
Cause:  SMTP port blocked on your network
Fix:    Use Mailtrap (sandbox.smtp.mailtrap.io port 2525)
        Or add: management.health.mail.enabled: false to disable check
```

### 500 on POST /admin/companies
```
Cause:  Enum column type mismatch in PostgreSQL
Fix:    Drop all tables, update SQL to use VARCHAR, restart Spring Boot
```

### Frontend login 404
```
Cause:  AuthController not yet built in backend
Fix:    Build AuthController + JwtService (see Building login endpoint above)
        OR temporarily remove ProtectedRoute in App.jsx for testing
```

---

## Database quick reference

```bash
# Connect to PostgreSQL
docker exec -it tq_postgres psql -U tquser -d taskqueue_db

# Useful queries
\dt                                          list all tables
SELECT id, type, status FROM jobs LIMIT 10;
SELECT * FROM dead_letter_jobs WHERE replayed_at IS NULL;
SELECT * FROM api_keys WHERE is_active = true;
SELECT * FROM smtp_configs;
\q                                           exit
```

### All 7 tables

```
users           id, email, password_hash, role VARCHAR(20), is_active
companies       id, owner_id, name, slug unique, is_active
projects        id, company_id, name, environment VARCHAR(20), is_active
api_keys        id, project_id, key_hash SHA-256, key_hint, label, rate_limit_per_min
smtp_configs    id, company_id, purpose VARCHAR(20), host, port, password_enc AES-256
jobs            id, project_id, api_key_id, type, payload JSONB, status, priority
dead_letter_jobs id, job_id, failure_reason, retry_count, replayed_at
```

---

## Adding a new job type

```java
// 1. Create worker in worker/ package
@Component
public class SmsWorker extends BaseWorker {
    public SmsWorker(JobRepository r, RetryService rs, WebhookService ws) {
        super(r, rs, ws);
    }

    @Override
    protected void process(JobEvent event) throws Exception {
        String phone   = event.getPayload().get("phone").toString();
        String message = event.getPayload().get("message").toString();
        // call your SMS API here
    }
}

// 2. Add to WorkerDispatcher.java
private final SmsWorker smsWorker;  // inject via constructor

case "SEND_SMS" -> smsWorker.execute(event);

// 3. Test
POST /jobs  X-API-Key: your-key
{ "type": "SEND_SMS", "payload": { "phone": "+91...", "message": "Hello" }, "priority": "HIGH" }
```

---

## What to build next (Phase 5)

### Multi-tenant client login
```
1. Create V5__create_user_company_roles.sql migration
   Table: user_company_roles (user_id, company_id, role OWNER/DEVELOPER/VIEWER)

2. Update JWT payload to include companyIds for CLIENT role

3. Update every service method to filter by companyId from JWT
   Admin → no filter (sees all)
   Client → filter by their companyIds

4. Add POST /admin/users endpoint to create client accounts

5. Update frontend routing — CLIENT sees only their company pages
```

### Scheduled jobs
```
scheduledAt column already exists in jobs table
Add @Scheduled Spring task that polls for jobs where:
  scheduledAt IS NOT NULL AND scheduledAt <= NOW() AND status = QUEUED
Publish those to Kafka when their time arrives
```

---

## Project counts

```
Backend Java files:    55
SQL migration files:   4
Frontend React files:  31
Config files:          8
Documentation files:   9

Total files:           107
```