# Task Queue System

Distributed background job processor — like Celery but in Java/Spring Boot.

---

## Prerequisites

Install these before anything else:

| Tool | Version | Download |
|------|---------|----------|
| Java JDK | 17+ | https://adoptium.net |
| Maven | 3.9+ | https://maven.apache.org |
| Docker Desktop | Latest | https://www.docker.com/products/docker-desktop |
| Node.js | 18+ | https://nodejs.org |
| IntelliJ IDEA | Any | https://www.jetbrains.com/idea |

---

## First-time setup

### Step 1 — Start infrastructure
```bash
# From project root
docker-compose up -d mysql redis zookeeper kafka kafka-ui redis-commander

# Wait ~20 seconds for everything to start, then verify:
docker-compose ps
# All services should show "Up"
```

### Step 2 — Verify services are running
| Service | URL | Credentials |
|---------|-----|-------------|
| Kafka UI | http://localhost:8090 | none |
| Redis UI | http://localhost:8091 | none |
| MySQL | localhost:3306 | tquser / tqpass123 |

### Step 3 — Configure application.yml
Open `backend/src/main/resources/application.yml` and update:
- `app.encryption.key` → change to any 32-character string
- `spring.mail.*` → your Gmail + app password (for system emails)

### Step 4 — Run Spring Boot
```bash
cd backend
mvn spring-boot:run
```

Flyway runs automatically on startup — creates all tables from `db/migration/`.

### Step 5 — Verify backend
- Swagger UI: http://localhost:8080/api/v1/swagger-ui/index.html
- Health check: http://localhost:8080/api/v1/actuator/health

### Step 6 — Start admin panel
```bash
cd admin-panel
npm install
npm start
# Opens at http://localhost:3000
```

---

## Project structure

```
task-queue-system/
├── backend/                          Spring Boot app
│   ├── src/main/java/com/taskqueue/
│   │   ├── config/                   Kafka, Redis, Security, AppProperties
│   │   ├── filter/                   ApiKeyFilter, RateLimitFilter, AdminBypass
│   │   ├── controller/               JobController, AdminController, MetricsController
│   │   ├── service/                  JobService, ApiKeyService, RetryService, WebhookService
│   │   ├── worker/                   BaseWorker, EmailWorker, GenericWorker, Dispatcher
│   │   ├── model/                    Job, ApiKey, Company, Project, SmtpConfig (JPA entities)
│   │   ├── repository/               JPA repositories
│   │   ├── dto/                      Request/Response DTOs
│   │   └── exception/                GlobalExceptionHandler
│   ├── src/main/resources/
│   │   ├── application.yml           All config
│   │   └── db/migration/             Flyway SQL files (V1, V2, V3, V4...)
│   ├── Dockerfile
│   └── pom.xml
├── admin-panel/                      React admin UI (localhost only)
│   └── src/pages/                    Dashboard, ApiKeys, Jobs, DLQ, Clients
├── docker-compose.yml                MySQL + Redis + Kafka + UI tools
├── Makefile                          Dev shortcuts
└── README.md
```

---

## Quick commands

```bash
make dev            # start all docker services + show URLs
make run            # run Spring Boot
make admin          # run React admin panel
make infra-down     # stop docker services
make logs s=kafka   # tail kafka logs
make test           # run tests
```

---

## URLs when everything is running

| Service | URL |
|---------|-----|
| Spring Boot API | http://localhost:8080/api/v1 |
| Swagger UI | http://localhost:8080/api/v1/swagger-ui/index.html |
| Admin Panel | http://localhost:3000 |
| Kafka UI | http://localhost:8090 |
| Redis UI | http://localhost:8091 |
| Actuator health | http://localhost:8080/api/v1/actuator/health |