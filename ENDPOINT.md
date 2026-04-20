# Task Queue System — All REST Endpoints

> Base URL: `http://localhost:8080/api/v1`
> Swagger UI: `http://localhost:8080/api/v1/swagger-ui.html`
>
> Auth rules:
> - `/jobs/**`  → requires `X-API-Key` header
> - `/admin/**` → localhost only, no API key needed
> - `/actuator/**` → public, no auth

---

## Jobs API
> Used by external apps (Swiggy, BillStack, etc.) with an API key

---

### POST /jobs
**Enqueue a new background job**

Request header:
```
X-API-Key: tq_live_abc123...
Content-Type: application/json
```

Request body:
```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "rahul@gmail.com",
    "subject": "Order Confirmed",
    "orderId": "ord_7821"
  },
  "callbackUrl": "https://swiggy.com/callbacks/jobs",
  "priority": "HIGH",
  "idempotencyKey": "order-7821-confirm-email",
  "maxRetries": 3,
  "smtpPurpose": "NOREPLY",
  "scheduledAt": null
}
```

Response `202 Accepted`:
```json
{
  "success": true,
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "QUEUED",
    "priority": "HIGH",
    "type": "SEND_EMAIL",
    "createdAt": "2025-01-15T14:30:00",
    "statusUrl": "/api/v1/jobs/550e8400-e29b-41d4-a716-446655440000"
  },
  "timestamp": "2025-01-15T14:30:00"
}
```

Error `409 Conflict` (duplicate idempotency key):
```json
{
  "success": false,
  "error": "Job with idempotency key 'order-7821-confirm-email' already exists for this project"
}
```

Error `429 Too Many Requests`:
```json
{
  "success": false,
  "error": "Rate limit exceeded. Max 100 requests/minute.",
  "retryAfter": 60
}
```

Priority values: `HIGH` | `NORMAL` | `LOW`
smtpPurpose values: `SUPPORT` | `BILLING` | `NOREPLY` | `ALERT` | `CUSTOM`

---

### GET /jobs/{jobId}
**Get status of a specific job**

Request header: `X-API-Key: tq_live_abc123...`

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "jobId": "550e8400-e29b-41d4-a716-446655440000",
    "type": "SEND_EMAIL",
    "status": "SUCCESS",
    "priority": "HIGH",
    "retryCount": 0,
    "maxRetries": 3,
    "canRetry": false,
    "projectName": "Order Service",
    "companyName": "Swiggy",
    "createdAt": "2025-01-15T14:30:00",
    "startedAt": "2025-01-15T14:30:01",
    "completedAt": "2025-01-15T14:30:02",
    "errorMessage": null
  }
}
```

Status values: `QUEUED` | `RUNNING` | `SUCCESS` | `FAILED` | `DEAD`

---

### GET /jobs
**List all jobs for the authenticated project**

Request header: `X-API-Key: tq_live_abc123...`

Query params:
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| status | enum | null | Filter by status |
| page | int | 0 | Page number |
| size | int | 20 | Page size (max 100) |

Example: `GET /jobs?status=FAILED&page=0&size=10`

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "content": [ { ...job objects... } ],
    "totalElements": 42,
    "totalPages": 5,
    "page": 0,
    "size": 10
  }
}
```

---

### POST /jobs/{jobId}/retry
**Manually retry a failed job**

Request header: `X-API-Key: tq_live_abc123...`

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "jobId": "550e8400-...",
    "status": "QUEUED",
    "message": "Job re-queued for processing"
  }
}
```

Error `400 Bad Request` (job can't be retried):
```json
{
  "success": false,
  "error": "Job cannot be retried. Status=SUCCESS, retries=0/3"
}
```

---

## Admin API
> Localhost only — no API key needed
> All paths: `/admin/**`

---

### GET /admin/metrics
**Dashboard overview — job counts and entity totals**

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "totalJobs": 1423,
    "queuedJobs": 12,
    "runningJobs": 3,
    "successJobs": 1380,
    "failedJobs": 20,
    "deadJobs": 8,
    "pendingDlq": 8,
    "totalCompanies": 4,
    "totalProjects": 11
  }
}
```

---

## Companies

### GET /admin/companies
**List all companies**

Response `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Swiggy",
      "slug": "swiggy",
      "isActive": true,
      "createdAt": "2025-01-01T00:00:00"
    }
  ]
}
```

---

### POST /admin/companies
**Create a company**

Request body:
```json
{
  "name": "Swiggy",
  "slug": "swiggy"
}
```

Slug rules: lowercase letters, numbers, hyphens only. Must be unique.

Response `201 Created`:
```json
{
  "success": true,
  "data": { "id": "uuid", "name": "Swiggy", "slug": "swiggy", ... }
}
```

Error `409 Conflict`:
```json
{ "success": false, "error": "Slug 'swiggy' already taken" }
```

---

### PATCH /admin/companies/{id}/toggle
**Activate or deactivate a company**

Response `200 OK`:
```json
{ "success": true, "data": "Company deactivated" }
```

---

## Projects

### GET /admin/companies/{companyId}/projects
**List all projects for a company**

Response `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "uuid",
      "name": "Order Service",
      "environment": "PRODUCTION",
      "isActive": true
    }
  ]
}
```

---

### POST /admin/projects
**Create a project under a company**

Request body:
```json
{
  "companyId": "company-uuid",
  "name": "Order Service",
  "description": "Handles post-order async tasks",
  "environment": "PRODUCTION"
}
```

Environment values: `PRODUCTION` | `STAGING` | `DEV`

Response `201 Created`: full project object

---

## API Keys

### POST /admin/keys
**Create an API key for a project**

Request body:
```json
{
  "projectId": "project-uuid",
  "label": "Production Key Jan 2025",
  "rateLimitPerMin": 100,
  "expiresAt": null,
  "environment": "live"
}
```

environment values: `live` → prefix `tq_live_` | `dev` → prefix `tq_dev_`

Response `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "key-uuid",
    "rawKey": "tq_live_k9x2mA8bQpR3nJ7vXcY1s2p",
    "keyHint": "...1s2p",
    "label": "Production Key Jan 2025",
    "rateLimitPerMin": 100,
    "projectId": "project-uuid",
    "createdAt": "2025-01-15T14:00:00",
    "warning": "Save this key now. It will NEVER be shown again."
  }
}
```

> rawKey is shown EXACTLY ONCE. Copy it immediately.

---

### GET /admin/projects/{projectId}/keys
**List all API keys for a project**

Response `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "key-uuid",
      "label": "Production Key Jan 2025",
      "keyHint": "...1s2p",
      "keyPrefix": "tq_live_",
      "isActive": true,
      "rateLimitPerMin": 100,
      "lastUsedAt": "2025-01-15T14:30:00",
      "expiresAt": null,
      "createdAt": "2025-01-01T00:00:00"
    }
  ]
}
```

Note: `rawKey` is NEVER returned here — only the hint.

---

### DELETE /admin/keys/{keyId}
**Revoke an API key**

Revocation is immediate — Redis cache is evicted instantly.

Response `200 OK`:
```json
{ "success": true, "data": "Key revoked successfully" }
```

---

## Jobs Browser (Admin)

### GET /admin/jobs
**Browse all jobs across all companies**

Query params:
| Param | Type | Default | Description |
|-------|------|---------|-------------|
| projectId | string | null | Filter by project |
| status | enum | null | Filter by status |
| page | int | 0 | Page number |
| size | int | 20 | Page size |

Example: `GET /admin/jobs?status=DEAD&page=0&size=20`

Response `200 OK`: paginated list of full job objects

---

## Dead Letter Queue

### GET /admin/dlq
**List all dead (permanently failed) jobs not yet replayed**

Query params: `page`, `size`

Response `200 OK`:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "dlq-uuid",
        "job": { "id": "job-uuid", "type": "SEND_EMAIL", ... },
        "failureReason": "javax.mail.AuthenticationFailedException: 535-5.7.8 ...",
        "retryCount": 3,
        "failedAt": "2025-01-15T12:00:00",
        "replayedAt": null
      }
    ],
    "totalElements": 8
  }
}
```

---

### POST /admin/dlq/{dlqId}/replay
**Replay a dead job — re-enqueue it from scratch**

Response `200 OK`:
```json
{
  "success": true,
  "data": "Job re-queued for processing. JobId: 550e8400-..."
}
```

Error `400 Bad Request` (already replayed):
```json
{ "success": false, "error": "This job has already been replayed" }
```

---

## SMTP Configs

### GET /admin/companies/{companyId}/smtp
**List all SMTP configs for a company**

Response `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "smtp-uuid",
      "companyId": "company-uuid",
      "purpose": "SUPPORT",
      "label": "Customer Support Email",
      "fromEmail": "support@swiggy.com",
      "fromName": "Swiggy Support",
      "host": "smtp.gmail.com",
      "port": 587,
      "username": "support@swiggy.com",
      "useTls": true,
      "isActive": true,
      "isVerified": true,
      "createdAt": "2025-01-01T00:00:00"
    }
  ]
}
```

Note: `password` is NEVER returned in responses.

---

### POST /admin/smtp
**Add an SMTP config for a company**

Request body:
```json
{
  "companyId": "company-uuid",
  "purpose": "SUPPORT",
  "label": "Customer Support Email",
  "fromEmail": "support@swiggy.com",
  "fromName": "Swiggy Support",
  "host": "smtp.gmail.com",
  "port": 587,
  "username": "support@swiggy.com",
  "password": "gmail-app-password-here",
  "useTls": true
}
```

Purpose values: `SUPPORT` | `BILLING` | `NOREPLY` | `ALERT` | `CUSTOM`

Response `201 Created`: SmtpConfig object (without password)

---

### POST /admin/smtp/{smtpId}/test
**Test an SMTP connection**

Performs a live SMTP handshake. Sets `isVerified = true` on success.

Response `200 OK`:
```json
{ "success": true, "data": "SMTP connection verified for: support@swiggy.com" }
```

---

### DELETE /admin/smtp/{smtpId}
**Delete an SMTP config**

Response `200 OK`:
```json
{ "success": true, "data": "SMTP config deleted" }
```

---

## System / Health

### GET /actuator/health
**Service health check**

Response `200 OK`:
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "redis": { "status": "UP" },
    "kafka": { "status": "UP" }
  }
}
```

---

### GET /swagger-ui.html
**Interactive API documentation**

Full Swagger UI — test all endpoints directly in browser.

---

## Error Response Format

All errors follow this shape:
```json
{
  "success": false,
  "error": "Human readable message",
  "timestamp": "2025-01-15T14:30:00"
}
```

## HTTP Status Code Reference

| Code | When |
|------|------|
| 200 OK | Successful GET, PATCH, DELETE, POST (non-creation) |
| 201 Created | Successful resource creation |
| 202 Accepted | Job enqueued (async — not yet processed) |
| 400 Bad Request | Validation failed, bad input, invalid operation |
| 401 Unauthorized | Missing or invalid API key |
| 403 Forbidden | Admin endpoint accessed from non-localhost |
| 404 Not Found | Resource doesn't exist |
| 409 Conflict | Duplicate slug, duplicate idempotency key |
| 429 Too Many Requests | Rate limit exceeded |
| 500 Internal Server Error | Unexpected error |

---

## Endpoints Summary Table

| Method | Path | Auth | Phase | Description |
|--------|------|------|-------|-------------|
| POST | /jobs | API Key | ✅ | Enqueue a job |
| GET | /jobs/{id} | API Key | ✅ | Get job status |
| GET | /jobs | API Key | ✅ | List project's jobs |
| POST | /jobs/{id}/retry | API Key | ✅ | Retry failed job |
| GET | /admin/metrics | Localhost | ✅ | Dashboard counts |
| GET | /admin/companies | Localhost | ✅ | List companies |
| POST | /admin/companies | Localhost | ✅ | Create company |
| PATCH | /admin/companies/{id}/toggle | Localhost | ✅ | Toggle active |
| GET | /admin/companies/{id}/projects | Localhost | ✅ | List projects |
| POST | /admin/projects | Localhost | ✅ | Create project |
| POST | /admin/keys | Localhost | ✅ | Create API key |
| GET | /admin/projects/{id}/keys | Localhost | ✅ | List API keys |
| DELETE | /admin/keys/{id} | Localhost | ✅ | Revoke API key |
| GET | /admin/jobs | Localhost | ✅ | Browse all jobs |
| GET | /admin/dlq | Localhost | ✅ | List DLQ jobs |
| POST | /admin/dlq/{id}/replay | Localhost | ✅ | Replay DLQ job |
| GET | /admin/companies/{id}/smtp | Localhost | ✅ | List SMTP configs |
| POST | /admin/smtp | Localhost | ✅ | Add SMTP config |
| POST | /admin/smtp/{id}/test | Localhost | ✅ | Test SMTP |
| DELETE | /admin/smtp/{id} | Localhost | ✅ | Delete SMTP |
| GET | /actuator/health | Public | ✅ | Health check |
| GET | /swagger-ui.html | Public | ✅ | API docs UI |
| GET | /admin/metrics/detailed | Localhost | 🔜 | Kafka lag + Redis stats |
| GET | /client/jobs | API Key | 🔮 | Client job tracker |