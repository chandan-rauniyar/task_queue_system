# 🚀 Task Queue System

> A high-performance, distributed background job processing system built with Spring Boot, Kafka, and PostgreSQL, featuring a sleek React + Vite administrative dashboard.

---

## 📖 Overview

The Task Queue System allows applications to reliably trigger background tasks (like sending emails, generating PDFs, generic webhooks, etc.) asynchronously using a robust Kafka message broker architecture, backed by PostgreSQL for state persistence and Redis for extreme-scale caching and API Rate Limiting.

**Main Features:**
- **Admin Dashboard:** A beautiful React 18 frontend to manage projects, companies, API keys, and track DLQ (Dead Letter Queue) metrics.
- **Multi-Tenant:** Safely partition projects and keys across multiple distinct companies.
- **JWT-Secured Admin:** Secure the admin APIs with stateless JWT Authentication.
- **Client API Security:** Project-specific `X-API-Key` ingress filtering cached in Redis.
- **Resilient Retry Mechanics:** Exponential backoff for failed jobs, graduating to a proper DLQ mechanism.

---

## 🛠️ Developer Setup & Cloning

If you have just cloned this repository, please immediately consult the [**Quickstart Clone Guide**](QUICKSTART_CLONE.md).

The Quickstart will walk you step-by-step through:
1. Bootstrapping PostgreSQL, Redis, Kafka, and Zookeeper via Docker.
2. Spinning up the Spring Boot engine.
3. Spinning up the Vite React dashboard.
4. Logging in as the System Admin.

---

## 📂 Documentation Stack

To understand the deeper architecture, consult the following markdown manuals included in the repository:

- 🏗️ **`FILE_STRUCTURE.md`**: Explains every folder, class, and architectural decision.
- 🧪 **`testingGuide.md`**: The exhaustive sequence of how to manually test the APIs directly using Postman.
- 🔌 **`ENDPOINT.md`**: The REST API surface definitions.
- 🐳 **`docker-compose.yml`**: The centralized infrastructure definition list.

---

## 💡 Tech Stack

**Backend:** Java 17, Spring Boot 3, Spring Data JPA, Spring Kafka, Spring Data Redis, Flyway, PostgreSQL, jjwt.
**Frontend:** React 18, Vite, Tailwind CSS, React Router DOM, Axios, Recharts.
**Infrastructure:** Docker, Docker Compose, Confluent Kafka, PostgreSQL, Redis.