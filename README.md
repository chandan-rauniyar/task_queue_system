# Task Queue System — Docker Deployment

A containerized Task Queue System that provides a complete background job processing platform using:

- React frontend
- Spring Boot backend
- PostgreSQL
- Redis
- Apache Kafka
- Zookeeper
- PgAdmin
- Kafka UI
- Redis Commander
- Docker

The Docker branch is designed so that users **do not need to build the application source code**.
The required Docker images are already available on Docker Hub.

---

## Table of Contents

- [Architecture](#architecture)
- [Docker Images](#docker-images)
- [Requirements](#requirements)
- [Quick Start](#quick-start)
- [Application URLs](#application-urls)
- [Default Credentials](#default-credentials)
- [Check Containers](#check-containers)
- [View Logs](#view-logs)
- [Stop Application](#stop-application)
- [Restart Application](#restart-application)
- [Update Application](#update-application)
- [Remove Everything](#remove-everything)
- [Running Backend Independently](#running-backend-independently)
- [Backend Environment Variables](#backend-environment-variables)
- [Running Backend With Docker Compose Services](#running-backend-with-docker-compose-services)
- [Running Backend With Local Services](#running-backend-with-local-services)
- [Running Backend With Your Own PostgreSQL](#running-backend-with-your-own-postgresql)
- [Running Backend With Your Own Redis](#running-backend-with-your-own-redis)
- [Running Backend With Your Own Kafka](#running-backend-with-your-own-kafka)
- [Running Complete Backend With External Services](#running-complete-backend-with-external-services)
- [Running Frontend Independently](#running-frontend-independently)
- [Frontend With Local Backend](#frontend-with-local-backend)
- [Frontend With Backend in Docker](#frontend-with-backend-in-docker)
- [Frontend With Backend on Another Machine](#frontend-with-backend-on-another-machine)
- [Frontend With Cloud Backend](#frontend-with-cloud-backend)
- [Running Backend and Frontend Manually](#running-backend-and-frontend-manually)
- [Environment Variables](#environment-variables)
- [Ports](#ports)
- [Changing Ports](#changing-ports)
- [Docker Network](#docker-network)
- [Frontend Backend Connection](#frontend-backend-connection)
- [Data Persistence](#data-persistence)
- [Troubleshooting](#troubleshooting)
- [Useful Docker Commands](#useful-docker-commands)
- [Clean Docker Installation](#clean-docker-installation)
- [Health Checks](#health-checks)
- [API](#api)
- [Security Notes](#security-notes)
- [Production Deployment](#production-deployment)
- [Recommended Usage](#recommended-usage)
- [Project Structure](#project-structure)
- [Quick Reference](#quick-reference)
- [Summary](#summary)

---

## Architecture

When using Docker Compose, the complete system looks like this:

```text
                         ┌──────────────────────┐
                         │       Browser        │
                         │   localhost:3000     │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │      Frontend        │
                         │   React + Nginx      │
                         │      Port 3000       │
                         └──────────┬───────────┘
                                    │
                                    │ /api/v1/*
                                    ▼
                         ┌──────────────────────┐
                         │       Backend        │
                         │    Spring Boot       │
                         │      Port 8080       │
                         └──────┬─────┬─────┬───┘
                                │     │     │
                    ┌───────────┘     │     └────────────┐
                    ▼                 ▼                  ▼
             ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
             │ PostgreSQL  │   │    Redis    │   │    Kafka    │
             │    :5432    │   │    :6379    │   │   :29092    │
             └─────────────┘   └─────────────┘   └──────┬──────┘
                                                        │
                                                        ▼
                                                 ┌─────────────┐
                                                 │  Zookeeper  │
                                                 │    :2181    │
                                                 └─────────────┘
```

Additional management tools:

```text
PgAdmin       → PostgreSQL
Kafka UI      → Kafka
Redis Commander → Redis
```

---

## Docker Images

The application images are available from Docker Hub. You do not need to build the backend or frontend — Docker Compose automatically downloads them when required.

**Backend:**
```text
chandangupta5/taskqueue-backend:1.1
```

**Frontend:**
```text
chandangupta5/taskqueue-frontend:1.0
```

---

## Requirements

You only need:

- Docker
- Docker Compose (already included in Docker Desktop)

Check versions:

```bash
docker --version
docker compose version
```

Example output:

```text
Docker version 28.x.x
Docker Compose version v2.x.x
```

---

## Quick Start

This is the recommended way to run the complete application.

1. Clone or download this repository.
2. Run:

```bash
docker compose up -d
```

That's it. Docker Compose automatically starts:

```text
PostgreSQL
PgAdmin
Redis
Zookeeper
Kafka
Kafka UI
Redis Commander
Backend
Frontend
```

> The first startup can take some time because Kafka and Zookeeper need to initialize.

---

## Application URLs

Once the containers are running:

| Service         | URL |
| --------------- | --- |
| Frontend        | http://localhost:3000 |
| Backend         | http://localhost:8080 |
| API             | http://localhost:8080/api/v1 |
| Swagger UI      | http://localhost:8080/swagger-ui.html |
| OpenAPI JSON    | http://localhost:8080/api/v1/api-docs |
| PgAdmin         | http://localhost:5050 |
| Kafka UI        | http://localhost:8090 |
| Redis Commander | http://localhost:8091 |

Main application: **http://localhost:3000**

---
## Default Credentials for Admin Login

```text
email: admin@taskqueue.local
Password: admin123
```

## Default Credentials


**PostgreSQL**
```text
Database: taskqueue_db
Username: tquser
Password: tqpass123
```

**PgAdmin**
```text
Email: admin@gmail.com
Password: admin123
```

> ⚠️ Change default passwords before using the system in a production environment.

---

## Check Containers

```bash
docker ps
```

You should see containers similar to:

```text
tq_postgres
tq_pgadmin
tq_redis
tq_zookeeper
tq_kafka
tq_kafka_ui
tq_redis_ui
tq_backend
tq_frontend
```

To see all containers, including stopped ones:

```bash
docker ps -a
```

---

## View Logs

| Service    | Command |
| ---------- | ------- |
| Backend    | `docker logs tq_backend` (add `-f` to follow) |
| Frontend   | `docker logs tq_frontend` |
| PostgreSQL | `docker logs tq_postgres` |
| Redis      | `docker logs tq_redis` |
| Kafka      | `docker logs tq_kafka` |
| Zookeeper  | `docker logs tq_zookeeper` |

---

## Stop Application

```bash
docker compose down
```

This removes the containers but keeps persistent Docker volumes — your PostgreSQL, Redis, Kafka, and Zookeeper data remain intact.

---

## Restart Application

```bash
docker compose restart
```

Or:

```bash
docker compose down
docker compose up -d
```

---

## Update Application

Pull the latest images and restart:

```bash
docker compose pull && docker compose up -d
```

---

## Remove Everything

Stop and remove containers (keeps data):

```bash
docker compose down
```

Remove containers **and** all application data:

```bash
docker compose down -v
```

> ⚠️ **Warning:** `docker compose down -v` deletes Docker volumes. This means PostgreSQL, Redis, Kafka, and Zookeeper stored data will be permanently deleted. Use only for a completely fresh installation.

---

## Running Backend Independently

The backend Docker image can be used without Docker Compose — useful when you already have PostgreSQL, Redis, and Kafka running elsewhere. Configuration is supplied through environment variables.

```bash
docker run --rm --name tq_backend \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<DB_HOST>:<DB_PORT>/<DATABASE_NAME> \
  -e DB_USERNAME=<DB_USERNAME> \
  -e DB_PASSWORD=<DB_PASSWORD> \
  -e REDIS_HOST=<REDIS_HOST> \
  -e REDIS_PORT=<REDIS_PORT> \
  -e KAFKA_BOOTSTRAP_SERVERS=<KAFKA_HOST>:<KAFKA_PORT> \
  chandangupta5/taskqueue-backend:1.1
```

Replace all `<PLACEHOLDER>` values with your actual configuration.

### Backend Environment Variables

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
KAFKA_BOOTSTRAP_SERVERS
```

The same Docker image works across different environments (local, Docker, production) without rebuilding — only the environment variables change:

```text
             Same Docker Image
                    │
     ┌──────────────┼──────────────┐
     ▼               ▼              ▼
  Local            Docker        Production
  PostgreSQL       PostgreSQL    Cloud DB
  Redis             Redis        Cloud Redis
  Kafka             Kafka        Cloud Kafka
```

### Running Backend With Docker Compose Services

If PostgreSQL, Redis, and Kafka are running in Docker on the same network (`tq_network`):

```bash
docker run --rm --name tq_backend \
  --network tq_network \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://postgres:5432/taskqueue_db \
  -e DB_USERNAME=tquser \
  -e DB_PASSWORD=tqpass123 \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  chandangupta5/taskqueue-backend:1.1
```

`postgres`, `redis`, and `kafka` are Docker service/container names — Docker DNS resolves them automatically.

### Running Backend With Local Services

If PostgreSQL, Redis, and Kafka run directly on your computer (not in Docker), use `host.docker.internal` so the container can reach the host:

```bash
docker run --rm --name tq_backend \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5433/taskqueue_db \
  -e DB_USERNAME=tquser \
  -e DB_PASSWORD=tqpass123 \
  -e REDIS_HOST=host.docker.internal \
  -e REDIS_PORT=6379 \
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  chandangupta5/taskqueue-backend:1.1
```

### Running Backend With Your Own PostgreSQL

Example: PostgreSQL at `192.168.1.100:5432`, database `my_database`, user `my_user`.

```bash
docker run --rm --name tq_backend \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://192.168.1.100:5432/my_database \
  -e DB_USERNAME=my_user \
  -e DB_PASSWORD=my_password \
  -e REDIS_HOST=<REDIS_HOST> \
  -e REDIS_PORT=6379 \
  -e KAFKA_BOOTSTRAP_SERVERS=<KAFKA_HOST>:9092 \
  chandangupta5/taskqueue-backend:1.1
```

> The PostgreSQL server must allow connections from the Docker host.

### Running Backend With Your Own Redis

Example: Redis at `192.168.1.101:6379`.

```bash
docker run --rm --name tq_backend \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://192.168.1.100:5432/taskqueue_db \
  -e DB_USERNAME=tquser \
  -e DB_PASSWORD=tqpass123 \
  -e REDIS_HOST=192.168.1.101 \
  -e REDIS_PORT=6379 \
  -e KAFKA_BOOTSTRAP_SERVERS=192.168.1.102:9092 \
  chandangupta5/taskqueue-backend:1.1
```

### Running Backend With Your Own Kafka

Example: Kafka at `192.168.1.102:9092`.

```bash
docker run --rm --name tq_backend \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://192.168.1.100:5432/taskqueue_db \
  -e DB_USERNAME=tquser \
  -e DB_PASSWORD=tqpass123 \
  -e REDIS_HOST=192.168.1.101 \
  -e REDIS_PORT=6379 \
  -e KAFKA_BOOTSTRAP_SERVERS=192.168.1.102:9092 \
  chandangupta5/taskqueue-backend:1.1
```

### Running Complete Backend With External Services

Combining all three external services (PostgreSQL, Redis, Kafka) in one command:

```bash
docker run --rm --name tq_backend \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://192.168.1.100:5432/taskqueue_db \
  -e DB_USERNAME=tquser \
  -e DB_PASSWORD=tqpass123 \
  -e REDIS_HOST=192.168.1.101 \
  -e REDIS_PORT=6379 \
  -e KAFKA_BOOTSTRAP_SERVERS=192.168.1.102:9092 \
  chandangupta5/taskqueue-backend:1.1
```

---

## Running Frontend Independently

The frontend also runs independently and needs to know where the backend is located via the `BACKEND_URL` variable.

```bash
docker run --rm --name tq_frontend \
  -p 3000:80 \
  -e BACKEND_URL=http://<BACKEND_HOST>:8080 \
  chandangupta5/taskqueue-frontend:1.2
```

### Frontend With Local Backend

If the backend runs directly on your computer (`http://localhost:8080`), the container must use `host.docker.internal` instead:

```bash
docker run --rm --name tq_frontend \
  -p 3000:80 \
  -e BACKEND_URL=http://host.docker.internal:8080 \
  chandangupta5/taskqueue-frontend:1.2
```

Then open http://localhost:3000.

### Frontend With Backend in Docker

If both containers are on the same Docker network:

```bash
docker run --rm --name tq_frontend \
  --network tq_network \
  -p 3000:80 \
  -e BACKEND_URL=http://tq_backend:8080 \
  chandangupta5/taskqueue-frontend:1.2
```

### Frontend With Backend on Another Machine

Example: backend running on `192.168.1.100`.

```bash
docker run --rm --name tq_frontend \
  -p 3000:80 \
  -e BACKEND_URL=http://192.168.1.100:8080 \
  chandangupta5/taskqueue-frontend:1.2
```

### Frontend With Cloud Backend

Example: backend deployed at `https://api.example.com`.

```bash
docker run --rm --name tq_frontend \
  -p 3000:80 \
  -e BACKEND_URL=https://api.example.com \
  chandangupta5/taskqueue-frontend:1.2
```

No frontend image rebuild is required — only the `BACKEND_URL` variable changes.

---

## Running Backend and Frontend Manually

Run both without Docker Compose.

1. Create a network:

```bash
docker network create tq_network
```

2. Run the backend:

```bash
docker run --rm --name tq_backend \
  --network tq_network \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://postgres:5432/taskqueue_db \
  -e DB_USERNAME=tquser \
  -e DB_PASSWORD=tqpass123 \
  -e REDIS_HOST=redis \
  -e REDIS_PORT=6379 \
  -e KAFKA_BOOTSTRAP_SERVERS=kafka:29092 \
  chandangupta5/taskqueue-backend:1.1
```

3. Run the frontend:

```bash
docker run --rm --name tq_frontend \
  --network tq_network \
  -p 3000:80 \
  -e BACKEND_URL=http://tq_backend:8080 \
  chandangupta5/taskqueue-frontend:1.2
```

> Important: PostgreSQL, Redis, and Kafka containers must also be connected to `tq_network`.

---

## Environment Variables

### Backend

| Variable | Description | Example |
| --- | --- | --- |
| `DB_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://postgres:5432/taskqueue_db` |
| `DB_USERNAME` | PostgreSQL username | `tquser` |
| `DB_PASSWORD` | PostgreSQL password | `tqpass123` |
| `REDIS_HOST` | Redis hostname | `redis` |
| `REDIS_PORT` | Redis port | `6379` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker | `kafka:29092` |

### Frontend

| Variable | Description | Example |
| --- | --- | --- |
| `BACKEND_URL` | Backend base URL | `http://backend:8080` |

### Why Environment Variables Are Used

Docker images shouldn't contain environment-specific infrastructure addresses (e.g., `localhost:5432` or `192.168.1.100`). Instead, values like `DB_URL` are supplied when the container starts, so the same image can move through:

```text
Local Development → Docker → Testing → AWS → Production
```

without rebuilding.

---

## Ports

| Port | Service |
| ---: | --- |
| `3000` | Frontend |
| `8080` | Backend |
| `5433` | PostgreSQL |
| `6379` | Redis |
| `9092` | Kafka |
| `2181` | Zookeeper |
| `5050` | PgAdmin |
| `8090` | Kafka UI |
| `8091` | Redis Commander |

### Changing Ports

If a port (e.g., `3000`) is already in use, change the host-side port in `docker-compose.yml`:

```yaml
frontend:
  ports:
    - "3001:80"
```

Then access http://localhost:3001. The container-side port (`80`) stays the same — only the host port changes.

---

## Docker Network

Docker Compose creates a network called `tq_network`. All application containers communicate using Docker service names, e.g.:

```text
backend → postgres:5432
backend → redis:6379
backend → kafka:29092
frontend → backend:8080
```

**Do not use `localhost` for container-to-container communication.** For example, this is wrong inside the backend container:

```text
jdbc:postgresql://localhost:5432/taskqueue_db
```

because `localhost` refers to the backend container itself. Use the service name instead:

```text
jdbc:postgresql://postgres:5432/taskqueue_db
```

### Frontend Backend Connection

| Scenario | `BACKEND_URL` |
| --- | --- |
| Docker Compose | `http://backend:8080` |
| Manually run containers | `http://tq_backend:8080` |
| Host backend | `http://host.docker.internal:8080` |
| Remote backend | `http://192.168.1.100:8080` |
| HTTPS production | `https://api.example.com` |

---

## Data Persistence

Docker Compose uses named volumes:

```text
postgres_data
redis_data
kafka_data
zookeeper_data
zookeeper_logs
```

These volumes preserve data when containers are recreated.

- `docker compose down` — does **not** delete data.
- `docker compose down -v` — **does** delete the volumes.

---

## Troubleshooting

**1. Frontend shows an error**
```bash
docker logs tq_frontend
docker logs tq_backend
```

**2. Backend cannot connect to Kafka**
```bash
docker logs tq_kafka
docker ps
docker restart tq_backend
```
Kafka may just need more time to start.

**3. Backend cannot connect to PostgreSQL**
```bash
docker logs tq_postgres
docker exec -it tq_postgres pg_isready -U tquser -d taskqueue_db
```
Expected: `accepting connections`

**4. Redis connection error**
```bash
docker logs tq_redis
docker exec -it tq_redis redis-cli ping
```
Expected: `PONG`

**5. Backend container keeps restarting**
```bash
docker logs tq_backend
```
Look for configuration errors involving PostgreSQL, Redis, Kafka, Flyway, or environment variables.

**6. Port already in use**

Example error: `Bind for 0.0.0.0:3000 failed`. Stop the conflicting application or change the port in `docker-compose.yml`.

**7. Frontend cannot reach backend**

Check that `BACKEND_URL` matches your setup (see [Frontend Backend Connection](#frontend-backend-connection)) and that both containers share the same network:

```bash
docker network inspect tq_network
```

---

## Useful Docker Commands

```bash
# Start
docker compose up -d

# Stop
docker compose down

# Restart
docker compose restart

# Pull latest images
docker compose pull

# Pull and restart
docker compose pull && docker compose up -d

# Show containers
docker ps
docker ps -a

# Show images
docker images

# Backend logs
docker logs tq_backend
docker logs -f tq_backend

# Frontend logs
docker logs tq_frontend

# Enter backend container
docker exec -it tq_backend sh

# Enter PostgreSQL container
docker exec -it tq_postgres sh

# Test Redis
docker exec -it tq_redis redis-cli ping
```

---

## Clean Docker Installation

To completely reset the application:

```bash
docker compose down -v
docker compose pull
docker compose up -d
```

This creates a completely fresh environment.

---

## Health Checks

| Check | URL |
| --- | --- |
| Backend health | http://localhost:8080/actuator/health |
| Swagger | http://localhost:8080/swagger-ui.html |
| Frontend | http://localhost:3000 |

---

## API

The backend uses `/api/v1` as its context path, e.g.:

```text
POST /api/v1/auth/login
```

Full API documentation: http://localhost:8080/swagger-ui.html

---

## Security Notes

The default Docker Compose configuration ships with **development credentials only**:

```text
PostgreSQL password: tqpass123
PgAdmin password:    admin123
```

Before production deployment, change these and any application secrets, such as:

```text
JWT secret
Encryption key
SMTP credentials
Database password
```

**Never commit real production secrets to GitHub.**

---

## Production Deployment

For production, consider:

- HTTPS
- Reverse proxy
- Strong database passwords
- Secret management
- Restricted CORS origins
- Firewall rules
- Private databases
- Kafka authentication
- Redis authentication
- Proper JWT secrets
- Monitoring
- Centralized logging
- Resource limits
- Database backups

The Docker images can stay the same while environment-specific configuration is supplied at runtime.

---

## Recommended Usage

**For normal users** — use Docker Compose:

```bash
docker compose up -d
```

Then open http://localhost:3000. This is the easiest and recommended option.

**For backend developers** — run the backend independently and provide:

```text
DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT, KAFKA_BOOTSTRAP_SERVERS
```

**For frontend developers** — run the frontend independently and provide:

```text
BACKEND_URL
```

---

## Project Structure

This Docker distribution repository intentionally contains only:

```text
task-queue-system-docker/
│
├── docker-compose.yml
└── README.md
```

The application source code remains in the main project repository. This repository is only responsible for distributing and running the Dockerized application.

---

## Quick Reference

**Complete application**
```bash
docker compose up -d
```
Open http://localhost:3000

**Stop**
```bash
docker compose down
```

**Update**
```bash
docker compose pull && docker compose up -d
```

**Backend image:** `chandangupta5/taskqueue-backend:1.1`
**Frontend image:** `chandangupta5/taskqueue-frontend:1.0`

---

## Summary

There are two ways to use this project:

### Option 1 — Complete Application

```text
docker-compose.yml → docker compose up -d → All services start → http://localhost:3000
```

### Option 2 — Independent Docker Images

```text
Backend Image  → provide DB_URL, DB_USERNAME, DB_PASSWORD, REDIS_HOST, REDIS_PORT, KAFKA_BOOTSTRAP_SERVERS → Backend starts
Frontend Image → provide BACKEND_URL → Frontend starts
```

The same Docker images can therefore be used with local infrastructure, Docker infrastructure, another machine, or cloud infrastructure — without rebuilding the images.
