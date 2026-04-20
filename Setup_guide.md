# Task Queue System — Complete Setup Guide

Follow every step in order. Do NOT skip ahead.
Estimated time: 30-45 minutes on first setup.

---

## STEP 0 — Install required tools

Install these before anything else. Check versions after each install.

### Java 17
```bash
# Download from: https://adoptium.net
# Choose: Temurin 17 LTS → your OS

java -version
# Expected: openjdk version "17.x.x"

javac -version
# Expected: javac 17.x.x
```

### Maven
```bash
# Download from: https://maven.apache.org/download.cgi
# Extract and add /bin to PATH

mvn -version
# Expected: Apache Maven 3.9.x
```

### Docker Desktop
```bash
# Download from: https://www.docker.com/products/docker-desktop
# Install and OPEN Docker Desktop — it must be running

docker --version
# Expected: Docker version 24.x.x

docker compose version
# Expected: Docker Compose version v2.x.x
```

### Node.js (for admin panel — Phase 4)
```bash
# Download from: https://nodejs.org → LTS

node --version   # v18.x.x or higher
npm --version    # 9.x.x or higher
```

### IntelliJ IDEA
```
Download: https://www.jetbrains.com/idea/download
Community Edition is free and enough.
```

### Postman
```
Download: https://www.postman.com/downloads
Used for testing API endpoints.
```

---

## STEP 1 — Project structure check

Make sure your folder looks like this before starting:

```
task-queue-system/
├── docker-compose.yml          ← infrastructure config
├── Makefile
├── README.md
├── SETUP_GUIDE.md              ← this file
├── ENDPOINTS.md
├── FILE_STRUCTURE.md
├── backend/
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── resources/
│       │   ├── application.yml
│       │   └── db/migration/
│       │       ├── V1__create_users_companies.sql
│       │       ├── V2__create_projects_api_keys.sql
│       │       ├── V3__create_smtp_configs.sql
│       │       └── V4__create_jobs.sql
│       └── java/com/taskqueue/
│           ├── TaskQueueApplication.java
│           ├── config/
│           ├── filter/
│           ├── model/
│           ├── repository/
│           ├── dto/
│           ├── exception/
│           ├── service/
│           ├── worker/         (empty for now — Phase 3)
│           └── controller/
└── admin-panel/
    └── package.json
```

---

## STEP 2 — Update docker-compose.yml for PostgreSQL

Open `docker-compose.yml` and replace the entire content with:

```yaml
version: '3.8'

services:

  postgres:
    image: postgres:16-alpine
    container_name: tq_postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: taskqueue_db
      POSTGRES_USER: tquser
      POSTGRES_PASSWORD: tqpass123
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U tquser -d taskqueue_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7.2-alpine
    container_name: tq_redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    container_name: tq_zookeeper
    restart: unless-stopped
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zookeeper_data:/var/lib/zookeeper/data
      - zookeeper_logs:/var/lib/zookeeper/log
    healthcheck:
      test: ["CMD", "bash", "-c", "echo 'ruok' | nc localhost 2181"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    container_name: tq_kafka
    restart: unless-stopped
    depends_on:
      zookeeper:
        condition: service_healthy
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:29092,PLAINTEXT_HOST://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,PLAINTEXT_HOST:PLAINTEXT
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
      KAFKA_LOG_RETENTION_HOURS: 168
    volumes:
      - kafka_data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]
      interval: 15s
      timeout: 10s
      retries: 5

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    container_name: tq_kafka_ui
    restart: unless-stopped
    depends_on:
      - kafka
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092

  redis-commander:
    image: rediscommander/redis-commander:latest
    container_name: tq_redis_ui
    restart: unless-stopped
    depends_on:
      - redis
    ports:
      - "8091:8081"
    environment:
      REDIS_HOSTS: local:redis:6379

  pgadmin:
    image: dpage/pgadmin4:latest
    container_name: tq_pgadmin
    restart: unless-stopped
    depends_on:
      - postgres
    ports:
      - "5050:80"
    environment:
      PGADMIN_DEFAULT_EMAIL: admin@taskqueue.local
      PGADMIN_DEFAULT_PASSWORD: admin123

volumes:
  postgres_data:
  redis_data:
  kafka_data:
  zookeeper_data:
  zookeeper_logs:

networks:
  default:
    name: tq_network
```

---

## STEP 3 — Update pom.xml for PostgreSQL

In `backend/pom.xml` make two changes:

**Remove** these two dependencies:
```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

**Add** this one:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

---

## STEP 4 — Update application.yml for PostgreSQL

In `backend/src/main/resources/application.yml` update the datasource and JPA sections:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taskqueue_db
    username: tquser
    password: tqpass123
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

Everything else in application.yml stays the same.

---

## STEP 5 — Start all Docker services

Open terminal in the project root (where docker-compose.yml lives):

```bash
# Start all services
docker-compose up -d

# Watch startup logs live (optional — Ctrl+C to stop watching)
docker-compose logs -f
```

Wait 30 seconds, then check status:

```bash
docker-compose ps
```

Expected — every service must show `running` or `Up`:
```
NAME               STATUS
tq_postgres        Up (healthy)
tq_redis           Up (healthy)
tq_zookeeper       Up (healthy)
tq_kafka           Up (healthy)
tq_kafka_ui        Up
tq_redis_ui        Up
tq_pgadmin         Up
```

If any service shows `Exit` — check its logs:
```bash
docker-compose logs postgres     # check postgres error
docker-compose logs kafka        # check kafka error
docker-compose logs redis        # check redis error
```

---

## STEP 6 — Verify each service in browser

### pgAdmin — PostgreSQL visual browser
```
URL:      http://localhost:5050
Email:    admin@taskqueue.local
Password: admin123
```

Connect to the database:
```
1. Left panel → right-click Servers → Register → Server
2. General tab  → Name: TaskQueue Local
3. Connection tab:
     Host:     postgres       (the docker service name, NOT localhost)
     Port:     5432
     Database: taskqueue_db
     Username: tquser
     Password: tqpass123
4. Click Save
```

You should now see:
```
Servers
  └── TaskQueue Local
        └── Databases
              └── taskqueue_db    ← database exists, tables not yet (Flyway runs on app start)
```

### Kafka UI
```
URL: http://localhost:8090
```
You should see the `local` cluster. Topics are empty — created when Spring Boot starts.

### Redis Commander
```
URL: http://localhost:8091
```
You should see the Redis connection with 0 keys.

---

## STEP 7 — Open project in IntelliJ

```
1. Open IntelliJ IDEA
2. File → Open
3. Navigate to: task-queue-system/backend
4. Click OK
5. IntelliJ detects pom.xml → click "Load Maven Project" if prompted
6. Wait for indexing (bottom progress bar) — 2-3 minutes first time
```

Enable Lombok annotation processing:
```
File → Settings (Ctrl+Alt+S)
  → Build, Execution, Deployment
    → Compiler
      → Annotation Processors
        → Enable annotation processing  ✓ (check this box)
Click OK
```

Install Lombok plugin if not already there:
```
File → Settings → Plugins
  → Search "Lombok"
  → Install → Restart IntelliJ
```

---

## STEP 8 — Run the Spring Boot application

### From IntelliJ:
```
1. Navigate to: src/main/java/com/taskqueue/TaskQueueApplication.java
2. Click the green ▶ play button in the left gutter next to the class
   OR right-click the file → Run 'TaskQueueApplication'
```

### From terminal:
```bash
cd backend
mvn spring-boot:run
```

### What you MUST see in the console for success:

```
[Flyway] Successfully applied 4 migrations to schema "public"
  - V1__create_users_companies.sql
  - V2__create_projects_api_keys.sql
  - V3__create_smtp_configs.sql
  - V4__create_jobs.sql

[KafkaAdmin] Created topic 'jobs.high-priority'
[KafkaAdmin] Created topic 'jobs.normal-priority'
[KafkaAdmin] Created topic 'jobs.low-priority'
[KafkaAdmin] Created topic 'jobs.dead-letter'

Started TaskQueueApplication in 8.3 seconds
```

Flyway line is critical — if it says "Successfully applied 4 migrations" all your tables are created.

### What to do if startup fails:

**"Connection refused" to PostgreSQL:**
```bash
# PostgreSQL not ready yet. Check:
docker-compose ps
# If tq_postgres not healthy yet, wait 10s and try again
```

**"Flyway migration checksum mismatch":**
```bash
# You edited a migration file after it already ran.
# Wipe everything and restart fresh:
docker-compose down -v
docker-compose up -d
# Wait 30s then run the app again
```

**"Port 8080 already in use":**
```bash
# Something else is using port 8080. Either:
# Option 1 — kill the other process
# Option 2 — change server.port in application.yml to 8081
```

---

## STEP 9 — Verify app is running

```bash
curl http://localhost:8080/api/v1/actuator/health
```

Expected:
```json
{
  "status": "UP",
  "components": {
    "db":    { "status": "UP" },
    "redis": { "status": "UP" },
    "ping":  { "status": "UP" }
  }
}
```

Open Swagger UI:
```
http://localhost:8080/api/v1/swagger-ui.html
```
You should see Jobs API and Admin API sections with all endpoints listed.

Check Kafka UI now:
```
http://localhost:8090 → Topics
```
You should now see 4 topics created:
- jobs.high-priority
- jobs.normal-priority
- jobs.low-priority
- jobs.dead-letter

Check pgAdmin tables:
```
http://localhost:5050
TaskQueue Local → taskqueue_db → Schemas → public → Tables
```
You should see 6 tables:
- users
- companies
- projects
- api_keys
- smtp_configs
- jobs
- dead_letter_jobs

---

## STEP 10 — Test all endpoints in Postman

Open Postman. Run these requests in ORDER — each one depends on the previous.

---

### Request 1 — Create a company
```
Method:  POST
URL:     http://localhost:8080/api/v1/admin/companies
Headers: Content-Type: application/json
Body (raw JSON):
{
  "name": "Swiggy",
  "slug": "swiggy"
}
```

Expected `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "name": "Swiggy",
    "slug": "swiggy",
    "isActive": true,
    "createdAt": "2025-01-15T10:00:00"
  }
}
```

**Copy the `id` value — you need it for the next request.**

---

### Request 2 — Create a project
```
Method:  POST
URL:     http://localhost:8080/api/v1/admin/projects
Headers: Content-Type: application/json
Body:
{
  "companyId": "PASTE-COMPANY-ID-HERE",
  "name": "Order Service",
  "environment": "DEV"
}
```

Expected `201 Created` — **copy the project `id`**.

---

### Request 3 — Generate an API key
```
Method:  POST
URL:     http://localhost:8080/api/v1/admin/keys
Headers: Content-Type: application/json
Body:
{
  "projectId": "PASTE-PROJECT-ID-HERE",
  "label": "My First Dev Key",
  "rateLimitPerMin": 100,
  "environment": "dev"
}
```

Expected `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "key-uuid",
    "rawKey": "tq_dev_k9x2mA8bQpR3nJ7vXcY1s2p",
    "keyHint": "...1s2p",
    "label": "My First Dev Key",
    "warning": "Save this key now. It will NEVER be shown again."
  }
}
```

**Copy the `rawKey` value immediately — it will NEVER be shown again.**

---

### Request 4 — Enqueue your first job
```
Method:  POST
URL:     http://localhost:8080/api/v1/jobs
Headers:
  Content-Type: application/json
  X-API-Key: PASTE-RAW-KEY-HERE
Body:
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "test@gmail.com",
    "subject": "Hello from Task Queue",
    "body": "My first job!"
  },
  "priority": "HIGH"
}
```

Expected `202 Accepted`:
```json
{
  "success": true,
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "QUEUED",
    "priority": "HIGH",
    "type": "SEND_EMAIL",
    "createdAt": "2025-01-15T10:05:00",
    "statusUrl": "/api/v1/jobs/550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Copy the `jobId`.**

---

### Request 5 — Check job status
```
Method:  GET
URL:     http://localhost:8080/api/v1/jobs/PASTE-JOB-ID-HERE
Headers: X-API-Key: PASTE-RAW-KEY-HERE
```

Expected `200 OK`:
```json
{
  "success": true,
  "data": {
    "jobId": "550e8400-...",
    "type": "SEND_EMAIL",
    "status": "QUEUED",
    "retryCount": 0,
    "projectName": "Order Service",
    "companyName": "Swiggy"
  }
}
```

Status is `QUEUED` — workers not built yet. This is correct.

---

### Request 6 — Test rate limiting
```
Send Request 4 again 5 times quickly with the same key.
After hitting your limit (100/min) you should get:
```

Expected `429 Too Many Requests`:
```json
{
  "success": false,
  "error": "Rate limit exceeded. Max 100 requests/minute.",
  "retryAfter": 60
}
```

---

### Request 7 — Test invalid API key
```
Method:  POST
URL:     http://localhost:8080/api/v1/jobs
Headers:
  Content-Type: application/json
  X-API-Key: tq_dev_thisisafakekey
Body: { "type": "TEST", "payload": {} }
```

Expected `401 Unauthorized`:
```json
{
  "success": false,
  "error": "Invalid API key"
}
```

---

### Request 8 — Get admin metrics
```
Method:  GET
URL:     http://localhost:8080/api/v1/admin/metrics
(No API key needed — admin route, localhost only)
```

Expected `200 OK`:
```json
{
  "success": true,
  "data": {
    "totalJobs": 1,
    "queuedJobs": 1,
    "runningJobs": 0,
    "successJobs": 0,
    "failedJobs": 0,
    "deadJobs": 0,
    "pendingDlq": 0,
    "totalCompanies": 1,
    "totalProjects": 1
  }
}
```

---

## STEP 11 — Verify in Kafka UI and pgAdmin

### In Kafka UI (http://localhost:8090):
```
Topics → jobs.high-priority → Messages
```
You should see your SEND_EMAIL job as a JSON message.

### In Redis Commander (http://localhost:8091):
```
You should see a key like: apikey:abc123def456...
```
That is your cached API key — proves Redis caching is working.

### In pgAdmin (http://localhost:5050):
```
taskqueue_db → Schemas → public → Tables
Right-click jobs → View/Edit Data → All Rows
```
You should see 1 row with:
- type = SEND_EMAIL
- status = QUEUED
- priority = HIGH

---

## All setup checkboxes

Work through these in order. All must be green before coding Phase 3.

```
Infrastructure:
[ ] docker-compose ps shows all 7 services as Up/healthy
[ ] http://localhost:5050  pgAdmin loads
[ ] http://localhost:8090  Kafka UI loads
[ ] http://localhost:8091  Redis Commander loads

Application:
[ ] mvn spring-boot:run starts without errors
[ ] Console shows "Successfully applied 4 migrations"
[ ] Console shows "Created topic jobs.high-priority"
[ ] http://localhost:8080/api/v1/actuator/health returns UP
[ ] http://localhost:8080/api/v1/swagger-ui.html loads

Database:
[ ] pgAdmin shows 7 tables in taskqueue_db
[ ] Kafka UI shows 4 topics

API Tests (Postman):
[ ] POST /admin/companies returns 201
[ ] POST /admin/projects returns 201
[ ] POST /admin/keys returns 201 with rawKey
[ ] POST /jobs returns 202 with jobId
[ ] GET /jobs/{id} returns QUEUED status
[ ] Invalid API key returns 401
[ ] GET /admin/metrics returns counts

Verification:
[ ] Kafka UI shows job message in jobs.high-priority
[ ] Redis Commander shows apikey:... cache entry
[ ] pgAdmin shows job row in jobs table with status=QUEUED
```

All checked → infrastructure is fully working → start Phase 3 (workers).

---

## Quick reference commands

```bash
# Start everything
docker-compose up -d

# Stop everything (data preserved)
docker-compose down

# Wipe everything including DB data (fresh start)
docker-compose down -v

# Check service status
docker-compose ps

# See logs for a service
docker-compose logs -f postgres
docker-compose logs -f kafka
docker-compose logs -f redis

# Restart one service
docker-compose restart postgres

# Run Spring Boot
cd backend && mvn spring-boot:run

# Build jar (skip tests)
cd backend && mvn clean package -DskipTests
```