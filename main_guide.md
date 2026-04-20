# Task Queue System — Master Guide

> One file. Everything you need to install, run, test, manage, and extend this project.
> Backend: Spring Boot 3.2 · PostgreSQL · Redis · Kafka · Java 17
> Frontend: React 18 (admin panel — localhost only)

---

## Table of Contents

1. [What this project does](#1-what-this-project-does)
2. [Install required tools](#2-install-required-tools)
3. [Project folder structure](#3-project-folder-structure)
4. [First-time setup](#4-first-time-setup)
5. [Daily workflow](#5-daily-workflow)
6. [Mandatory config changes](#6-mandatory-config-changes)
7. [Run the project](#7-run-the-project)
8. [Verify everything is working](#8-verify-everything-is-working)
9. [Test with Postman](#9-test-with-postman)
10. [All endpoints reference](#10-all-endpoints-reference)
11. [How the system works](#11-how-the-system-works)
12. [Database tables](#12-database-tables)
13. [Adding a new job type](#13-adding-a-new-job-type)
14. [Managing the system day to day](#14-managing-the-system)
15. [Troubleshooting](#15-troubleshooting)
16. [What to build next](#16-what-to-build-next)

---

## 1. What this project does

BillStack Task Queue is a distributed background job processor — like Celery (Python) or Bull (Node.js) but in Java/Spring Boot.

External apps (Swiggy, BillStack, any SaaS) call your REST API to submit background jobs. Your system queues them in Kafka, workers process them asynchronously, and the result is POSTed back to the caller's webhook URL.

**Real-world flow:**
```
Swiggy places order
  → calls POST /jobs with { type: "SEND_EMAIL", payload: {...} }
  → your system returns jobId in under 10ms
  → Kafka worker picks it up in background
  → sends the email using Swiggy's own SMTP config
  → POSTs result to Swiggy's callbackUrl
```

**What is built:**
- REST API for job submission with API key auth and rate limiting
- 3-priority Kafka queue HIGH / NORMAL / LOW
- Automatic retry with exponential backoff 30s then 60s then 120s
- Dead Letter Queue for permanently failed jobs
- Per-company SMTP configuration (each client uses their own email server)
- Admin panel backend (create companies, projects, API keys, manage DLQ)
- Webhook callbacks on job completion

---

## 2. Install required tools

Install all of these before doing anything else.

**Java 17 JDK**
```
Download: https://adoptium.net
Choose:   Temurin 17 LTS
Verify:   java -version
Expected: openjdk version 17.x.x
```

**Maven 3.9+**
```
Download: https://maven.apache.org/download.cgi
Mac:      brew install maven
Verify:   mvn -version
Expected: Apache Maven 3.x.x
```

**Docker Desktop**
```
Download: https://www.docker.com/products/docker-desktop
Install and open it — wait for Docker Desktop is running in taskbar
Verify:   docker -version
Expected: Docker version 24.x.x
```

**IntelliJ IDEA**
```
Download: https://www.jetbrains.com/idea/download
Community edition is free and works perfectly
```

**Postman**
```
Download: https://www.postman.com/downloads
Used to test all API endpoints
```

**Node.js 18+**
```
Download: https://nodejs.org choose LTS
Verify:   node -version
Expected: v18.x.x or v20.x.x
Needed for: React admin panel
```

---

## 3. Project folder structure

```
task-queue-system/
│
├── docker-compose.yml              All infrastructure
├── Makefile                        Shortcut commands
├── MASTER_GUIDE.md                 This file
├── FILE_STRUCTURE.md               Every Java file with its purpose
├── ENDPOINTS.md                    All REST endpoints with examples
│
├── backend/                        Spring Boot application
│   ├── pom.xml                     Maven dependencies
│   ├── Dockerfile                  Container build
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml     All configuration
│       │   └── db/migration/       Flyway SQL auto-run on startup
│       │       ├── V1__create_users_companies.sql
│       │       ├── V2__create_projects_api_keys.sql
│       │       ├── V3__create_smtp_configs.sql
│       │       └── V4__create_jobs.sql
│       └── java/com/taskqueue/
│           ├── TaskQueueApplication.java
│           ├── config/             5 files
│           ├── filter/             4 files
│           ├── model/              8 files
│           ├── repository/         7 files
│           ├── dto/                13 files
│           ├── exception/          2 files
│           ├── service/            7 files
│           ├── worker/             5 files
│           └── controller/         2 files
│
└── admin-panel/                    React admin UI localhost only
    ├── package.json
    └── src/
        ├── api.js
        ├── App.jsx
        └── pages/                  8 pages to build
```

**55 Java files total across 9 packages. All complete.**

---

## 4. First-time setup

Do this once when you first get the project.

**Step 1 — Open Docker Desktop**
Wait until it shows Docker Desktop is running in system tray. Do not skip this.

**Step 2 — Start all infrastructure**
Open terminal in project root (where docker-compose.yml is):
```bash
docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander
```
First run downloads images and takes 3-5 minutes. After that it takes 10 seconds.

**Step 3 — Verify all 6 services are up**
```bash
docker-compose ps
```
All must show Up:
```
tq_postgres       Up    0.0.0.0:5432->5432/tcp
tq_redis          Up    0.0.0.0:6379->6379/tcp
tq_zookeeper      Up    2181/tcp
tq_kafka          Up    0.0.0.0:9092->9092/tcp
tq_kafka_ui       Up    0.0.0.0:8090->8080/tcp
tq_redis_ui       Up    0.0.0.0:8091->8081/tcp
```

Individual verification:
```bash
docker exec tq_postgres pg_isready -U tquser -d taskqueue_db
# Expected: accepting connections

docker exec tq_redis redis-cli ping
# Expected: PONG
```

**Step 4 — Open project in IntelliJ**
```
File → Open → select the backend/ folder
IntelliJ detects Maven → click Load Maven Project
Wait for dependency download (3-5 min first time)
When it says Sync finished you are ready
```

**Step 5 — Make mandatory config changes**
See Section 6 below.

**Step 6 — Run the application**
```
Open TaskQueueApplication.java
Click the green play button
```

**Step 7 — Confirm correct startup**
Watch for these lines in console:
```
Flyway: Successfully applied 4 migrations
Creating topic: jobs.high-priority
Creating topic: jobs.normal-priority
Creating topic: jobs.low-priority
Creating topic: jobs.dead-letter
Started TaskQueueApplication in X.XXX seconds
```

If you see all of these the entire backend is working.

**Step 8 — Verify in browser**
```
Swagger UI:    http://localhost:8080/api/v1/swagger-ui.html
Health:        http://localhost:8080/api/v1/actuator/health
Kafka browser: http://localhost:8090
Redis browser: http://localhost:8091
```

---

## 5. Daily workflow

**Starting your work session:**
```bash
# Open Docker Desktop first

# Start infrastructure
docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander

# Wait 15 seconds for Kafka

# Run Spring Boot from IntelliJ green play button
# OR from terminal:
cd backend && mvn spring-boot:run

# If you need admin panel:
cd admin-panel && npm start
```

**Ending your work session:**
```bash
# Stop Spring Boot: Ctrl+C or stop button in IntelliJ

# Stop Docker services (data preserved)
docker-compose stop

# Or remove containers but keep volumes
docker-compose down
```

**Wipe everything and start fresh:**
```bash
docker-compose down -v
# Deletes all database data. Run docker-compose up -d again after.
```

---

## 6. Mandatory config changes

Open `backend/src/main/resources/application.yml`

**Encryption key — REQUIRED. App crashes without this.**
```yaml
app:
  encryption:
    key: "YourExactly32CharKeyGoesHere123"
```
Must be exactly 32 characters. Count them. Write it down — if you change it all stored SMTP passwords become unreadable.

**Gmail SMTP — needed for EmailWorker to send real emails:**
```yaml
spring:
  mail:
    host: smtp.gmail.com
    port: 587
    username: your-actual-gmail@gmail.com
    password: abcd efgh ijkl mnop
```
How to get Gmail App Password: Gmail > Google Account > Security > 2-Step Verification > App Passwords > Generate.

**Everything else is already correct for local Docker setup.**
PostgreSQL on localhost:5432, Redis on localhost:6379, Kafka on localhost:9092 — all match docker-compose.yml.

---

## 7. Run the project

**Option A — IntelliJ (recommended during development)**
```
Open TaskQueueApplication.java
Click green play button
Stop: red stop button
Restart: restart button
```

**Option B — Terminal**
```bash
cd backend
mvn spring-boot:run
# Stop with Ctrl+C
```

**Option C — Build jar and run**
```bash
cd backend
mvn clean package -DskipTests
java -jar target/task-queue-system-1.0.0.jar
```

**Default port is 8080.**
To change: `server.port: 8081` in application.yml.

---

## 8. Verify everything is working

**Health check:**
```
GET http://localhost:8080/api/v1/actuator/health

Expected:
{
  "status": "UP",
  "components": {
    "db":    { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

**Check database tables:**
```bash
docker exec -it tq_postgres psql -U tquser -d taskqueue_db -c "\dt"
```
Expected 7 tables: api_keys, companies, dead_letter_jobs, jobs, projects, smtp_configs, users

**Check seed admin user:**
```bash
docker exec -it tq_postgres psql -U tquser -d taskqueue_db \
  -c "SELECT email, role FROM users;"
```
Expected: admin@taskqueue.local with role ADMIN

**Check Kafka topics:**
Open http://localhost:8090 > Topics tab
Should show: jobs.high-priority, jobs.normal-priority, jobs.low-priority, jobs.dead-letter

---

## 9. Test with Postman

Follow this exact sequence. Each step depends on the previous one.

**Test 1 — Create a company**
```
POST http://localhost:8080/api/v1/admin/companies
Content-Type: application/json

{
  "name": "Swiggy",
  "slug": "swiggy"
}

Expected: 201 Created
Action:   copy the id from response
```

**Test 2 — Create a project**
```
POST http://localhost:8080/api/v1/admin/projects
Content-Type: application/json

{
  "companyId": "paste-company-id",
  "name": "Order Service",
  "environment": "DEV"
}

Expected: 201 Created
Action:   copy the id from response
```

**Test 3 — Create an API key**
```
POST http://localhost:8080/api/v1/admin/keys
Content-Type: application/json

{
  "projectId": "paste-project-id",
  "label": "Dev Key",
  "rateLimitPerMin": 100,
  "environment": "dev"
}

Expected: 201 Created
Action:   copy rawKey immediately — SHOWN ONLY ONCE
Example:  tq_dev_k9x2mA8bQpR3nJ7vXcY1s2p
```

**Test 4 — Enqueue your first job**
```
POST http://localhost:8080/api/v1/jobs
Content-Type: application/json
X-API-Key: tq_dev_k9x2mA8bQpR3nJ7vXcY1s2p

{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "test@example.com",
    "subject": "Hello from Task Queue",
    "body": "<h1>It works!</h1>"
  },
  "priority": "HIGH"
}

Expected: 202 Accepted with jobId
Action:   copy the jobId
```

**Test 5 — Check job status**
```
GET http://localhost:8080/api/v1/jobs/paste-jobId
X-API-Key: tq_dev_k9x2mA8bQpR3nJ7vXcY1s2p

Expected: 200 OK with status QUEUED or RUNNING or SUCCESS
```

**Test 6 — See job in Kafka**
```
Open http://localhost:8090
Click Topics > jobs.high-priority > Messages tab
See your job event as JSON
```

**Test 7 — Test rate limiting**
```
Send POST /jobs 101 times quickly
101st request returns 429:
{
  "success": false,
  "error": "Rate limit exceeded. Max 100 requests/minute.",
  "retryAfter": 60
}
```

**Test 8 — Test invalid API key**
```
POST http://localhost:8080/api/v1/jobs
X-API-Key: tq_dev_this-is-wrong

Expected: 401 Unauthorized
```

**Test 9 — Dashboard metrics**
```
GET http://localhost:8080/api/v1/admin/metrics

Expected: 200 OK with job counts and entity totals
```

**Test 10 — Add SMTP config**
```
POST http://localhost:8080/api/v1/admin/smtp
Content-Type: application/json

{
  "companyId": "paste-company-id",
  "purpose": "NOREPLY",
  "label": "No Reply Email",
  "fromEmail": "noreply@example.com",
  "fromName": "Task Queue",
  "host": "smtp.gmail.com",
  "port": 587,
  "username": "your-gmail@gmail.com",
  "password": "your-app-password",
  "useTls": true
}

Expected: 201 Created — password never returned in response
```

---

## 10. All endpoints reference

Base URL: `http://localhost:8080/api/v1`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | /jobs | API Key | Enqueue a new job |
| GET | /jobs/{id} | API Key | Get job status |
| GET | /jobs | API Key | List your jobs paginated |
| POST | /jobs/{id}/retry | API Key | Retry a failed job |
| GET | /admin/metrics | Localhost | Dashboard counts |
| GET | /admin/companies | Localhost | List all companies |
| POST | /admin/companies | Localhost | Create company |
| PATCH | /admin/companies/{id}/toggle | Localhost | Enable or disable |
| GET | /admin/companies/{id}/projects | Localhost | List projects |
| POST | /admin/projects | Localhost | Create project |
| PATCH | /admin/projects/{id}/toggle | Localhost | Enable or disable |
| POST | /admin/keys | Localhost | Create API key |
| GET | /admin/projects/{id}/keys | Localhost | List API keys |
| DELETE | /admin/keys/{id} | Localhost | Revoke key |
| GET | /admin/jobs | Localhost | Browse all jobs |
| GET | /admin/dlq | Localhost | List dead jobs |
| POST | /admin/dlq/{id}/replay | Localhost | Replay one dead job |
| POST | /admin/dlq/replay-all | Localhost | Replay all dead jobs |
| GET | /admin/companies/{id}/smtp | Localhost | List SMTP configs |
| POST | /admin/smtp | Localhost | Add SMTP config |
| POST | /admin/smtp/{id}/test | Localhost | Test SMTP connection |
| PATCH | /admin/smtp/{id}/toggle | Localhost | Enable or disable |
| DELETE | /admin/smtp/{id} | Localhost | Delete SMTP config |
| GET | /actuator/health | Public | Health check |
| GET | /swagger-ui.html | Public | Interactive API docs |

---

## 11. How the system works

**Request flow:**
```
POST /jobs arrives with X-API-Key header
  ↓
AdminBypassFilter (Order 0)
  Is this /admin/**? No → pass through

ApiKeyFilter (Order 1)
  Extract X-API-Key header
  Check Redis cache → cache miss → check PostgreSQL
  Invalid or inactive → 401 Unauthorized
  Valid → attach ClientContext to thread (projectId, companyId, rateLimit)

RateLimitFilter (Order 2)
  Redis key: rate:{apiKeyId}:{currentMinute}
  Increment counter atomically
  Over limit → 429 Too Many Requests

JobController → JobService
  Idempotency check → reject if same key used before
  Save Job to PostgreSQL with status QUEUED
  Build JobEvent and publish to Kafka topic by priority
  Return 202 with jobId immediately

Worker picks up from Kafka
  WorkerDispatcher routes by job.type
  BaseWorker marks RUNNING in DB
  EmailWorker / PdfWorker / GenericWorker executes
  Success → mark SUCCESS, fire webhook to callbackUrl
  Failure → RetryService handles

If retries exhausted
  Job marked DEAD
  DeadLetterJob created
  Failure webhook fired
```

**Retry schedule:**
```
Attempt 1 fails → wait 30s  → retry
Attempt 2 fails → wait 60s  → retry
Attempt 3 fails → wait 120s → retry
Attempt 4 fails → DEAD → DLQ
```

**Security:**
```
API Key    → SHA-256 hashed in DB, raw key shown once never stored
SMTP pass  → AES-256 encrypted in DB, decrypted at runtime
Admin      → IP whitelist 127.0.0.1 only enforced by AdminBypassFilter
Rate limit → Redis sliding window per API key per minute
```

---

## 12. Database tables

**users** — id, email, password_hash, full_name, role, is_active
Default admin seeded by Flyway: admin@taskqueue.local / role=ADMIN

**companies** — id, owner_id, name, slug unique, is_active
One user owns many companies.

**projects** — id, company_id, name, environment PRODUCTION/STAGING/DEV, is_active
One company has many projects.

**api_keys** — id, project_id, key_prefix, key_hash SHA-256, key_hint last 4 chars, label, rate_limit_per_min, is_active, expires_at
One project has many keys.

**smtp_configs** — id, company_id, purpose unique per company, from_email, from_name, host, port, username, password_enc AES-256, use_tls, is_verified
Purpose values: SUPPORT, BILLING, NOREPLY, ALERT, CUSTOM

**jobs** — id, project_id, api_key_id, type, payload jsonb, status, priority, retry_count, max_retries, callback_url, idempotency_key, started_at, completed_at, error_message
Status: QUEUED, RUNNING, SUCCESS, FAILED, DEAD
Priority: HIGH, NORMAL, LOW

**dead_letter_jobs** — id, job_id, original_payload, failure_reason, retry_count, failed_at, replayed_at, replayed_job_id

**Connect to database directly:**
```bash
docker exec -it tq_postgres psql -U tquser -d taskqueue_db

# Useful queries:
\dt                                          list all tables
SELECT id, type, status FROM jobs LIMIT 10;
SELECT * FROM dead_letter_jobs WHERE replayed_at IS NULL;
SELECT * FROM api_keys WHERE is_active = true;
\q                                           exit
```

---

## 13. Adding a new job type

Example: add SEND_SMS job type.

**Step 1 — Create SmsWorker.java in worker/ package:**
```java
@Slf4j
@Component
public class SmsWorker extends BaseWorker {

    public SmsWorker(JobRepository jobRepository,
                     RetryService retryService,
                     WebhookService webhookService) {
        super(jobRepository, retryService, webhookService);
    }

    @Override
    protected void process(JobEvent event) throws Exception {
        String phone   = event.getPayload().get("phone").toString();
        String message = event.getPayload().get("message").toString();
        log.info("Sending SMS to {}", phone);
        // Call your SMS provider here (Twilio, AWS SNS, etc.)
    }
}
```

**Step 2 — Add to WorkerDispatcher.java:**
```java
// Add field:
private final SmsWorker smsWorker;

// Add case in dispatch():
case "SEND_SMS" -> smsWorker.execute(event);
```

**Step 3 — Test it:**
```json
POST /jobs
X-API-Key: your-key

{
  "type": "SEND_SMS",
  "payload": {
    "phone": "+919876543210",
    "message": "Your order is confirmed!"
  },
  "priority": "HIGH"
}
```

---

## 14. Managing the system

**View live jobs:**
```
GET /admin/jobs?status=RUNNING
OR in database: SELECT id, type, status FROM jobs WHERE status='RUNNING';
```

**Replay a dead job:**
```
POST /admin/dlq/{dlqId}/replay
OR replay all: POST /admin/dlq/replay-all
```

**Revoke a compromised API key:**
```
DELETE /admin/keys/{keyId}
Effect: Redis cache evicted immediately — key stops working within milliseconds
```

**Create a replacement API key:**
```
POST /admin/keys
Body: { "projectId": "...", "label": "Replacement Key", "rateLimitPerMin": 100 }
Copy rawKey immediately — never shown again
```

**View Kafka queue depth:**
```
Open http://localhost:8090 → Topics → jobs.high-priority → Messages tab
```

**View Redis cache:**
```
Open http://localhost:8091
Keys starting with apikey: = cached API key lookups (5 min TTL)
Keys starting with rate:   = rate limit counters per minute
```

**Force reset stuck running jobs:**
```sql
UPDATE jobs
SET status = 'FAILED', error_message = 'Manually reset'
WHERE status = 'RUNNING'
  AND started_at < NOW() - INTERVAL '30 minutes';
```

---

## 15. Troubleshooting

**Connection refused localhost:5432**
```bash
docker-compose up -d postgres
docker exec tq_postgres pg_isready -U tquser -d taskqueue_db
```

**Connection refused localhost:6379**
```bash
docker-compose up -d redis
docker exec tq_redis redis-cli ping
```

**Connection refused localhost:9092**
```bash
docker-compose up -d zookeeper kafka
# Wait 30 seconds for Kafka to fully initialize
```

**Encryption key must be exactly 32 characters**
```yaml
# Count: YourExactly32CharKeyGoesHere123
#        ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ = 32
app:
  encryption:
    key: "YourExactly32CharKeyGoesHere123"
```

**Port 8080 already in use**
```yaml
server:
  port: 8081
```

**Flyway migration error on startup**
```bash
docker exec -it tq_postgres psql -U tquser -d taskqueue_db \
  -c "DELETE FROM flyway_schema_history WHERE success = false;"
# Then restart the app
```

**Job stuck in QUEUED — worker not processing**
```bash
# Check Kafka consumer group:
# Open http://localhost:8090 → Consumer Groups → task-queue-workers
# If lag is growing, the consumer is not connected

# Check app logs for errors containing "Worker" or "KafkaListener"
```

**Email not sending**
```
1. Test the SMTP config: POST /admin/smtp/{id}/test
2. Check is_verified is true in the response
3. Verify Gmail App Password (not regular password)
4. Verify Gmail has 2-Step Verification enabled
5. Check app logs for "Email send failed"
```

**401 Unauthorized on every request**
```
1. Check X-API-Key header is present and correct
2. Verify key is active: GET /admin/projects/{id}/keys
3. Check is_active = true in response
4. Check expires_at is null or in the future
5. Create a fresh key and try again
```

**429 Too Many Requests unexpectedly**
```
1. Check rate limit: GET /admin/projects/{id}/keys → rateLimitPerMin
2. View counter: http://localhost:8091 → rate:{keyId}:{currentMinute}
3. Wait 60 seconds for window to reset
4. Create new key with higher rateLimitPerMin
```

**Wipe everything:**
```bash
docker-compose down -v
docker-compose up -d postgres redis zookeeper kafka kafka-ui redis-commander
# App recreates all tables from scratch on next startup
```

---

## 16. What to build next

**Phase 4 — React Admin Panel** (next)

8 pages inside `admin-panel/src/pages/`:

| Page | Purpose |
|------|---------|
| Dashboard.jsx | Live metrics cards, job status counts |
| Companies.jsx | List and create companies |
| Projects.jsx | List and create projects per company |
| ApiKeys.jsx | Create key show raw once, list with hint, revoke |
| Jobs.jsx | Paginated job browser, filter by status or project |
| JobDetail.jsx | Full detail, payload viewer, retry button |
| DeadLetterQueue.jsx | Dead jobs, replay single or all, failure reason |
| SmtpSettings.jsx | Add remove SMTP configs, test connection button |

All pages call `http://localhost:8080/api/v1/admin/**` — no auth header needed because AdminBypassFilter allows all localhost requests.

**Phase 5 — Client Job Tracker** (future)

Add ClientController.java for `GET /client/jobs` and build a separate client-ui/ where clients enter their API key to see their own job history.

**Phase 6 — Further features** (future)

Scheduled jobs — the scheduledAt field already exists in the Job entity, just needs a @Scheduled trigger.
Developer and Viewer roles — add a user_company_roles table via Flyway V5 migration.
Job batching — POST /jobs/batch accepting an array of JobRequest objects.
Email templates — save HTML templates in DB and reference by name in payload.

---

## Quick reference

**All URLs:**
```
API base:     http://localhost:8080/api/v1
Swagger:      http://localhost:8080/api/v1/swagger-ui.html
Health:       http://localhost:8080/api/v1/actuator/health
Admin panel:  http://localhost:3000
Kafka UI:     http://localhost:8090
Redis UI:     http://localhost:8091
PostgreSQL:   localhost:5432  user=tquser  pass=tqpass123
```

**Make commands:**
```
make dev          start all docker services and show URLs
make infra-up     start postgres redis kafka and UIs
make infra-down   stop all docker services
make infra-clean  wipe all data and remove containers
make run          mvn spring-boot:run
make build        mvn clean package
make test         mvn test
make admin        npm start for admin panel
make logs s=kafka tail service logs
```

**Docker commands:**
```
docker-compose ps                    check all service status
docker-compose up -d postgres        start one service
docker-compose stop                  stop all keep data
docker-compose down                  remove containers keep volumes
docker-compose down -v               wipe everything including data
docker-compose logs -f kafka         tail kafka logs
docker exec -it tq_postgres psql ... connect to PostgreSQL
docker exec tq_redis redis-cli ping  test Redis
```