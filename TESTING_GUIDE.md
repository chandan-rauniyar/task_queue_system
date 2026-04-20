# Task Queue System — Manual Testing Guide

> Follow this file top to bottom in Postman.
> Every test depends on the previous one — do NOT skip steps.
> Base URL: http://localhost:8080/api/v1
> Admin endpoints need `Authorization: Bearer <token>` header.
> Job endpoints need `X-API-Key` header.

---

## Before you start

Make sure all of these are running:
```
docker-compose ps   → all 6 services show "Up"
Spring Boot         → console shows "Started TaskQueueApplication"
Swagger UI          → http://localhost:8080/api/v1/swagger-ui.html opens
Health check        → http://localhost:8080/api/v1/actuator/health shows "UP"
```

Save these values as you go — you need them for later tests:
```
ADMIN_TOKEN  = (copy from Test 1b response)
COMPANY_ID   = (copy from Test 2 response)
PROJECT_ID   = (copy from Test 3 response)
RAW_API_KEY  = (copy from Test 9 response — shown ONCE only)
JOB_ID       = (copy from Test 16 response)
DLQ_ID       = (copy from Test response)
SMTP_ID      = (copy from Test 12 response)
KEY_ID       = (copy from Test 9 response)
```

---

## PHASE 1 — Verify the system is alive

---

### Test 1 — Health check
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/actuator/health
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "status": "UP",
  "components": {
    "db":        { "status": "UP" },
    "redis":     { "status": "UP" },
    "mail":      { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping":      { "status": "UP" }
  }
}
```

If status is DOWN for db → PostgreSQL not running, run: `docker-compose up -d postgres`
If status is DOWN for redis → Redis not running, run: `docker-compose up -d redis`
If status is DOWN for mail → Check Mailtrap credentials in application.yml

---

### Test 1b — Login as Admin
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/auth/login
HEADERS: Content-Type: application/json
BODY:
```
```json
{
  "email": "admin@taskqueue.local",
  "password": "admin123"
}
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "email": "admin@taskqueue.local",
    "role": "ADMIN"
  }
}
```

Action: **Copy the `token` value → save as ADMIN_TOKEN**
> From now on, attach `Authorization: Bearer ADMIN_TOKEN` to all `/admin/` requests!

---

## PHASE 2 — Create company and project (admin endpoints — no API key needed)

---

### Test 2 — Create a company
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/companies
HEADERS: Content-Type: application/json
BODY:
```
```json
{
  "name": "Swiggy",
  "slug": "swiggy"
}
```

Expected response `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
    "name": "Swiggy",
    "slug": "swiggy",
    "isActive": true,
    "ownerEmail": "admin@taskqueue.local",
    "createdAt": "2025-01-15T10:00:00"
  },
  "timestamp": "2025-01-15T10:00:00"
}
```

Action: **Copy the `id` value → save as COMPANY_ID**

---

### Test 3 — Create a second company (to test multi-tenant)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/companies
HEADERS: Content-Type: application/json
BODY:
```
```json
{
  "name": "Zomato",
  "slug": "zomato"
}
```

Expected response `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "name": "Zomato",
    "slug": "zomato",
    "isActive": true,
    "ownerEmail": "admin@taskqueue.local",
    "createdAt": "2025-01-15T10:01:00"
  }
}
```

---

### Test 4 — Try duplicate slug (should fail)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/companies
HEADERS: Content-Type: application/json
BODY:
```
```json
{
  "name": "Swiggy Again",
  "slug": "swiggy"
}
```

Expected response `409 Conflict`:
```json
{
  "success": false,
  "error": "Slug 'swiggy' is already taken",
  "timestamp": "2025-01-15T10:02:00"
}
```

---

### Test 5 — List all companies
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/companies
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "a1b2c3d4-...",
      "name": "Swiggy",
      "slug": "swiggy",
      "isActive": true,
      "ownerEmail": "admin@taskqueue.local"
    },
    {
      "id": "b2c3d4e5-...",
      "name": "Zomato",
      "slug": "zomato",
      "isActive": true,
      "ownerEmail": "admin@taskqueue.local"
    }
  ]
}
```

---

### Test 6 — Create a project under Swiggy
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/projects
HEADERS: Content-Type: application/json
BODY:    (replace COMPANY_ID with your saved value)
```
```json
{
  "companyId": "COMPANY_ID",
  "name": "Order Service",
  "description": "Handles post-order background jobs",
  "environment": "DEV"
}
```

Expected response `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "name": "Order Service",
    "description": "Handles post-order background jobs",
    "environment": "DEV",
    "isActive": true,
    "companyId": "a1b2c3d4-...",
    "companyName": "Swiggy",
    "createdAt": "2025-01-15T10:03:00"
  }
}
```

Action: **Copy the `id` value → save as PROJECT_ID**

---

### Test 7 — Create second project under same company
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/projects
HEADERS: Content-Type: application/json
BODY:
```
```json
{
  "companyId": "COMPANY_ID",
  "name": "Notification Service",
  "description": "Sends emails and SMS notifications",
  "environment": "PRODUCTION"
}
```

Expected response `201 Created` with new project object.

---

### Test 8 — List projects for Swiggy
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/companies/COMPANY_ID/projects
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK` — array of 2 projects under Swiggy.

---

## PHASE 3 — Create API keys

---

### Test 9 — Create a DEV API key
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/keys
HEADERS: Content-Type: application/json
BODY:    (replace PROJECT_ID with your saved value)
```
```json
{
  "projectId": "PROJECT_ID",
  "label": "Dev Key January 2025",
  "rateLimitPerMin": 100,
  "environment": "dev"
}
```

Expected response `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "d4e5f6a7-b8c9-0123-defa-234567890123",
    "label": "Dev Key January 2025",
    "projectId": "c3d4e5f6-...",
    "projectName": "Order Service",
    "rawKey": "tq_dev_k9x2mA8bQpR3nJ7vXcY1s2p",
    "keyHint": "...s2p",
    "keyPrefix": "tq_dev_",
    "rateLimitPerMin": 100,
    "expiresAt": null,
    "createdAt": "2025-01-15T10:05:00",
    "warning": "Save this key now. It will NEVER be shown again."
  }
}
```

Action: **Copy the `rawKey` value → save as RAW_API_KEY**
Action: **Copy the `id` value → save as KEY_ID**
WARNING: rawKey is shown exactly once. If you lose it, revoke and create a new one.

---

### Test 10 — Create a PRODUCTION API key with expiry
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/keys
HEADERS: Content-Type: application/json
BODY:
```
```json
{
  "projectId": "PROJECT_ID",
  "label": "Production Key",
  "rateLimitPerMin": 500,
  "environment": "live",
  "expiresAt": "2025-12-31T23:59:59"
}
```

Expected response `201 Created` with `rawKey` starting `tq_live_...`

---

### Test 11 — List API keys for a project (rawKey never shown here)
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/projects/PROJECT_ID/keys
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": [
    {
      "id": "d4e5f6a7-...",
      "label": "Dev Key January 2025",
      "keyHint": "...s2p",
      "keyPrefix": "tq_dev_",
      "isActive": true,
      "rateLimitPerMin": 100,
      "lastUsedAt": null,
      "expiresAt": null,
      "createdAt": "2025-01-15T10:05:00",
      "projectName": "Order Service"
    }
  ]
}
```

Note: rawKey is NOT in this response — only hint shown.

---

## PHASE 4 — SMTP configuration

---

### Test 12 — Add Mailtrap SMTP config for Swiggy
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/smtp
HEADERS: Content-Type: application/json
BODY:    (replace COMPANY_ID and use your Mailtrap credentials)
```
```json
{
  "companyId": "COMPANY_ID",
  "purpose": "NOREPLY",
  "label": "Swiggy No-Reply Email",
  "fromEmail": "noreply@swiggy.com",
  "fromName": "Swiggy",
  "host": "sandbox.smtp.mailtrap.io",
  "port": 2525,
  "username": "your-mailtrap-username",
  "password": "your-mailtrap-password",
  "useTls": true
}
```

Expected response `201 Created`:
```json
{
  "success": true,
  "data": {
    "id": "e5f6a7b8-c9d0-1234-efab-345678901234",
    "companyId": "a1b2c3d4-...",
    "companyName": "Swiggy",
    "purpose": "NOREPLY",
    "label": "Swiggy No-Reply Email",
    "fromEmail": "noreply@swiggy.com",
    "fromName": "Swiggy",
    "host": "sandbox.smtp.mailtrap.io",
    "port": 2525,
    "username": "your-mailtrap-username",
    "useTls": true,
    "isActive": true,
    "isVerified": false,
    "createdAt": "2025-01-15T10:08:00"
  }
}
```

Note: password is NOT in the response — stored encrypted, never returned.
Action: **Copy the `id` value → save as SMTP_ID**

---

### Test 13 — Add SUPPORT SMTP config
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/smtp
HEADERS: Content-Type: application/json
BODY:
```
```json
{
  "companyId": "COMPANY_ID",
  "purpose": "SUPPORT",
  "label": "Swiggy Support Email",
  "fromEmail": "support@swiggy.com",
  "fromName": "Swiggy Support",
  "host": "sandbox.smtp.mailtrap.io",
  "port": 2525,
  "username": "your-mailtrap-username",
  "password": "your-mailtrap-password",
  "useTls": true
}
```

Expected response `201 Created` — same shape as Test 12.

---

### Test 14 — Test SMTP connection (sets isVerified = true)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/smtp/SMTP_ID/test
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "message": "SMTP connection verified",
    "email": "noreply@swiggy.com"
  }
}
```

If this fails → check your Mailtrap username and password in Test 12 body.

---

### Test 15 — List SMTP configs for a company
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/companies/COMPANY_ID/smtp
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK` — array of 2 SMTP configs, isVerified: true for the one you tested.

---

## PHASE 5 — Enqueue jobs (requires X-API-Key header)

---

### Test 16 — Enqueue a SEND_EMAIL job (basic)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: tq_dev_k9x2mA8bQpR3nJ7vXcY1s2p    ← your RAW_API_KEY
BODY:
```
```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "customer@example.com",
    "subject": "Your order is confirmed!",
    "body": "<h1>Thank you for your order!</h1><p>Your food is being prepared.</p>"
  },
  "priority": "HIGH",
  "smtpPurpose": "NOREPLY"
}
```

Expected response `202 Accepted`:
```json
{
  "success": true,
  "data": {
    "jobId": "f6a7b8c9-d0e1-2345-fabc-456789012345",
    "status": "QUEUED",
    "priority": "HIGH",
    "type": "SEND_EMAIL",
    "createdAt": "2025-01-15T10:15:00",
    "statusUrl": "/api/v1/jobs/f6a7b8c9-d0e1-2345-fabc-456789012345"
  }
}
```

Action: **Copy the `jobId` → save as JOB_ID**
Action: **Open Mailtrap inbox → you should see the email arrive within 5 seconds**

---

### Test 17 — Enqueue a SEND_EMAIL with callback URL
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: RAW_API_KEY
BODY:
```
```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "rahul@example.com",
    "subject": "Order Invoice Ready",
    "body": "<p>Your invoice #INV-001 is attached.</p>"
  },
  "priority": "NORMAL",
  "smtpPurpose": "BILLING",
  "callbackUrl": "https://webhook.site/your-unique-url",
  "idempotencyKey": "order-7821-invoice-email"
}
```

Expected response `202 Accepted` — same shape as Test 16.

Note: Go to https://webhook.site to get a free URL that shows incoming webhooks.
When the job finishes, your system will POST the result to that URL.

---

### Test 18 — Enqueue a GENERATE_PDF job
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: RAW_API_KEY
BODY:
```
```json
{
  "type": "GENERATE_PDF",
  "payload": {
    "templateName": "invoice",
    "outputFileName": "invoice_7821",
    "data": {
      "customerName": "Rahul Sharma",
      "orderId": "ORD-7821",
      "amount": 480,
      "items": ["Butter Chicken", "Naan x2"]
    }
  },
  "priority": "NORMAL"
}
```

Expected response `202 Accepted`.

---

### Test 19 — Enqueue a custom job type (generic worker handles it)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: RAW_API_KEY
BODY:
```
```json
{
  "type": "UPDATE_LOYALTY_POINTS",
  "payload": {
    "userId": "user_123",
    "points": 50,
    "reason": "order_completed",
    "orderId": "ORD-7821"
  },
  "priority": "LOW"
}
```

Expected response `202 Accepted` — GenericWorker will process this.

---

### Test 20 — Enqueue with idempotency key
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: RAW_API_KEY
BODY:
```
```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "test@example.com",
    "subject": "Idempotency test",
    "body": "<p>This should only be sent once.</p>"
  },
  "priority": "HIGH",
  "smtpPurpose": "NOREPLY",
  "idempotencyKey": "unique-email-key-abc123"
}
```

Expected response `202 Accepted` — job queued.

Now send the EXACT same request again (same idempotencyKey):

Expected response `409 Conflict`:
```json
{
  "success": false,
  "error": "Job with idempotency key 'unique-email-key-abc123' already exists. JobId: f6a7b8c9-..."
}
```

This proves duplicate jobs are prevented.

---

## PHASE 6 — Check job status

---

### Test 21 — Get job status
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/jobs/JOB_ID
HEADERS:
  X-API-Key: RAW_API_KEY
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "jobId": "f6a7b8c9-...",
    "type": "SEND_EMAIL",
    "status": "SUCCESS",
    "priority": "HIGH",
    "retryCount": 0,
    "maxRetries": 3,
    "canRetry": false,
    "projectId": "c3d4e5f6-...",
    "projectName": "Order Service",
    "companyName": "Swiggy",
    "createdAt": "2025-01-15T10:15:00",
    "startedAt": "2025-01-15T10:15:01",
    "completedAt": "2025-01-15T10:15:02",
    "errorMessage": null
  }
}
```

Status will be QUEUED right after submitting, then RUNNING, then SUCCESS or FAILED.

---

### Test 22 — List all jobs for your project
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  X-API-Key: RAW_API_KEY
BODY:    (none)
```

Expected response `200 OK` — paginated list of all jobs for your project.

---

### Test 23 — Filter jobs by status
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/jobs?status=SUCCESS&page=0&size=10
HEADERS:
  X-API-Key: RAW_API_KEY
BODY:    (none)
```

Other status values you can filter by: QUEUED, RUNNING, SUCCESS, FAILED, DEAD

---

## PHASE 7 — Security tests

---

### Test 24 — Missing API key (should fail)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS: Content-Type: application/json
BODY:
```
```json
{
  "type": "SEND_EMAIL",
  "payload": { "to": "test@example.com", "subject": "test", "body": "test" }
}
```

Expected response `401 Unauthorized`:
```json
{
  "success": false,
  "error": "Missing X-API-Key header",
  "timestamp": "2025-01-15T10:20:00"
}
```

---

### Test 25 — Wrong API key (should fail)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: tq_dev_this-is-completely-wrong-key
BODY:
```
```json
{
  "type": "SEND_EMAIL",
  "payload": { "to": "test@example.com", "subject": "test", "body": "test" }
}
```

Expected response `401 Unauthorized`:
```json
{
  "success": false,
  "error": "Invalid API key",
  "timestamp": "2025-01-15T10:21:00"
}
```

---

### Test 26 — Validation error — missing required field
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: RAW_API_KEY
BODY:
```
```json
{
  "payload": { "to": "test@example.com" }
}
```

Expected response `400 Bad Request`:
```json
{
  "success": false,
  "error": "Validation failed: type is required",
  "timestamp": "2025-01-15T10:22:00"
}
```

---

### Test 27 — Rate limit test
Send the same request more than 100 times within 1 minute.
On request 101 you should get `429 Too Many Requests`:
```json
{
  "success": false,
  "error": "Rate limit exceeded. Max 100 requests/minute.",
  "retryAfter": 60
}
```

Response headers on all requests:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 45
```

---

## PHASE 8 — Admin job browser

---

### Test 28 — Browse all jobs (admin view — no API key)
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/jobs
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK` — all jobs from all projects.

---

### Test 29 — Filter jobs by project
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/jobs?projectId=PROJECT_ID&page=0&size=20
HEADERS: (none)
BODY:    (none)
```

---

### Test 30 — Filter jobs by status
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/jobs?status=FAILED
HEADERS: (none)
BODY:    (none)
```

---

### Test 31 — Dashboard metrics
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/metrics
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "totalJobs": 6,
    "queuedJobs": 0,
    "runningJobs": 0,
    "successJobs": 5,
    "failedJobs": 1,
    "deadJobs": 0,
    "pendingDlq": 0,
    "totalCompanies": 2,
    "totalProjects": 3,
    "totalApiKeys": 2
  }
}
```

---

## PHASE 9 — Dead Letter Queue

---

### Test 32 — Trigger a job failure (send to bad email)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: RAW_API_KEY
BODY:
```
```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "not-an-email",
    "subject": "This will fail",
    "body": "Bad payload"
  },
  "priority": "HIGH",
  "smtpPurpose": "NOREPLY",
  "maxRetries": 1
}
```

Wait 60 seconds (retry waits 30s, then fails again → DLQ).

---

### Test 33 — View Dead Letter Queue
```
METHOD:  GET
URL:     http://localhost:8080/api/v1/admin/dlq
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": "g7h8i9j0-...",
        "job": {
          "id": "f6a7b8c9-...",
          "type": "SEND_EMAIL",
          "status": "DEAD"
        },
        "failureReason": "Invalid email address",
        "retryCount": 1,
        "failedAt": "2025-01-15T10:35:00",
        "replayedAt": null
      }
    ],
    "totalElements": 1
  }
}
```

Action: **Copy the `id` (DLQ entry id, not job id) → save as DLQ_ID**

---

### Test 34 — Replay a dead letter job
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/dlq/DLQ_ID/replay
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "message": "Job re-queued successfully",
    "jobId": "f6a7b8c9-..."
  }
}
```

---

### Test 35 — Replay ALL pending DLQ jobs at once
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/dlq/replay-all
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "replayedCount": 1
  }
}
```

---

### Test 36 — Try to replay an already-replayed job (should fail)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/admin/dlq/DLQ_ID/replay
HEADERS: (none)
BODY:    (none)
```

Expected response `400 Bad Request`:
```json
{
  "success": false,
  "error": "This job has already been replayed on 2025-01-15T10:36:00"
}
```

---

## PHASE 10 — Retry a failed job manually

---

### Test 37 — Retry a failed job via client API
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs/JOB_ID/retry
HEADERS:
  X-API-Key: RAW_API_KEY
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "jobId": "f6a7b8c9-...",
    "status": "QUEUED",
    "message": "Job re-queued for processing"
  }
}
```

Expected response if job cannot be retried (status=SUCCESS):
```json
{
  "success": false,
  "error": "Job cannot be retried. Status=SUCCESS, attempts=0/3"
}
```

---

## PHASE 11 — Key management

---

### Test 38 — Revoke an API key
```
METHOD:  DELETE
URL:     http://localhost:8080/api/v1/admin/keys/KEY_ID
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "message": "Key revoked successfully"
  }
}
```

---

### Test 39 — Try using revoked key (should fail immediately)
```
METHOD:  POST
URL:     http://localhost:8080/api/v1/jobs
HEADERS:
  Content-Type: application/json
  X-API-Key: tq_dev_k9x2mA8bQpR3nJ7vXcY1s2p   ← the revoked key
BODY:
```
```json
{
  "type": "SEND_EMAIL",
  "payload": { "to": "test@example.com", "subject": "test", "body": "test" }
}
```

Expected response `401 Unauthorized`:
```json
{
  "success": false,
  "error": "API key is inactive or expired"
}
```

Note: Revocation takes effect immediately because the Redis cache is evicted on delete.

---

## PHASE 12 — Company and project management

---

### Test 40 — Disable a company
```
METHOD:  PATCH
URL:     http://localhost:8080/api/v1/admin/companies/COMPANY_ID/toggle
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "id": "a1b2c3d4-...",
    "name": "Swiggy",
    "isActive": false
  }
}
```

---

### Test 41 — Re-enable the company
```
METHOD:  PATCH
URL:     http://localhost:8080/api/v1/admin/companies/COMPANY_ID/toggle
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK` — `isActive` back to `true`.

---

### Test 42 — Disable an SMTP config
```
METHOD:  PATCH
URL:     http://localhost:8080/api/v1/admin/smtp/SMTP_ID/toggle
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK` — `isActive: false`.

---

### Test 43 — Delete an SMTP config
```
METHOD:  DELETE
URL:     http://localhost:8080/api/v1/admin/smtp/SMTP_ID
HEADERS: (none)
BODY:    (none)
```

Expected response `200 OK`:
```json
{
  "success": true,
  "data": {
    "message": "SMTP config deleted"
  }
}
```

---

## PHASE 13 — Kafka and Redis verification

---

### Test 44 — Verify Kafka received the job
```
Open browser → http://localhost:8090
Click: Topics
Click: jobs.high-priority
Click: Messages tab

You should see JSON messages like:
{
  "jobId": "f6a7b8c9-...",
  "type": "SEND_EMAIL",
  "priority": "HIGH",
  "payload": { "to": "customer@example.com", ... },
  "companyId": "a1b2c3d4-...",
  "retryCount": 0
}
```

---

### Test 45 — Verify Redis is caching API keys
```
Open browser → http://localhost:8091
Look for keys starting with: apikey:
You should see entries like: apikey:a3f7b9c2d4...  (SHA-256 hash)

Each key has a 300 second (5 min) TTL.
After 5 min of no requests it auto-expires and is re-fetched from DB on next request.
```

---

### Test 46 — Verify rate limit counter in Redis
```
Open browser → http://localhost:8091
Look for keys starting with: rate:
You should see entries like: rate:d4e5f6a7-...:202501151015  (keyId + minute)

The value is the request count for that minute.
Expires automatically after 60 seconds.
```

---

## PHASE 14 — Check in pgAdmin

---

### Test 47 — View jobs in pgAdmin
```
In pgAdmin:
  Servers → Task Queue DB → Databases → taskqueue_db
  → Schemas → public → Tables → jobs
  → Right click → View/Edit Data → All Rows

You should see all your test jobs with:
  - status: SUCCESS (most), DEAD (failed ones)
  - started_at and completed_at timestamps
  - retry_count incremented on retried jobs
```

---

### Test 48 — Run a custom SQL query
```
In pgAdmin → Tools → Query Tool → paste and run:

SELECT
  j.id,
  j.type,
  j.status,
  j.priority,
  j.retry_count,
  j.created_at,
  j.completed_at,
  p.name AS project_name,
  c.name AS company_name
FROM jobs j
JOIN projects p ON j.project_id = p.id
JOIN companies c ON p.company_id = c.id
ORDER BY j.created_at DESC;
```

---

## Quick reference — all test numbers by phase

| Phase | Tests | What |
|-------|-------|------|
| 1 | 1 | Health check |
| 2 | 2-8 | Create companies and projects |
| 3 | 9-11 | Create and list API keys |
| 4 | 12-15 | SMTP configuration and testing |
| 5 | 16-20 | Enqueue different job types |
| 6 | 21-23 | Check job status and list |
| 7 | 24-27 | Security — auth and rate limit |
| 8 | 28-31 | Admin job browser and metrics |
| 9 | 32-36 | Dead letter queue |
| 10 | 37 | Manual retry |
| 11 | 38-39 | Key revocation |
| 12 | 40-43 | Company and SMTP management |
| 13 | 44-46 | Kafka and Redis verification |
| 14 | 47-48 | pgAdmin database inspection |

**Total: 48 tests covering every feature of the system.**