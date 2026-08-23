# Task Queue System — API Guide

The **Task Queue System** provides a simple REST API that allows your backend application to submit background jobs and monitor their execution.

The most common use case is sending notifications such as emails without making your main application wait for the task to complete.

---

## 🌐 Base URL

For the currently deployed instance:

```text
http://15.207.121.43:8080/api/v1
```

 

---

# 🔑 Authentication

Job APIs use an **API Key** for authentication.

Add the API key to every job request using:

```http
X-API-Key: YOUR_RAW_API_KEY
```

Example:

```http
X-API-Key: tq_live_xxxxxxxxxxxxxxxxx
```

> **Security:** Keep your API key on your backend/server. Never expose it in frontend JavaScript or commit it to GitHub.

API keys are created from:

```text
Dashboard
 → Project
 → API Keys
 → Create API Key
```

The raw API key is displayed only once when it is created.

---

# 📌 API Overview

| Method | Endpoint              | Purpose                     |
| ------ | --------------------- | --------------------------- |
| `POST` | `/jobs`               | Submit a new background job |
| `GET`  | `/jobs/{jobId}`       | Check a job's status        |
| `GET`  | `/jobs`               | List jobs for your project  |
| `POST` | `/jobs/{jobId}/retry` | Retry a failed job          |

These are the main endpoints your application needs.

---

# 1. Enqueue a Job

Submit a background job to the Task Queue System.

### Request

```http
POST /api/v1/jobs
```

### Headers

```http
Content-Type: application/json
X-API-Key: YOUR_RAW_API_KEY
```

### Example — Send Email

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

### Response

The API returns `202 Accepted` when the job has been successfully queued.

```json
{
  "success": true,
  "data": {
    "jobId": "f6a7b8c9-d0e1-2345-fabc-456789012345",
    "status": "QUEUED",
    "priority": "HIGH",
    "type": "SEND_EMAIL",
    "createdAt": "2026-08-23T10:15:00",
    "statusUrl": "/api/v1/jobs/f6a7b8c9-d0e1-2345-fabc-456789012345"
  }
}
```

Save the returned:

```text
jobId
```

You can use it later to check the job status.

---

# 2. Example Using cURL

```bash
curl -X POST "http://15.207.121.43:8080/api/v1/jobs" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_RAW_API_KEY" \
  -d '{
    "type": "SEND_EMAIL",
    "payload": {
      "to": "customer@example.com",
      "subject": "Your order is confirmed!",
      "body": "<h1>Thank you for your order!</h1><p>Your food is being prepared.</p>"
    },
    "priority": "HIGH",
    "smtpPurpose": "NOREPLY"
  }'
```

---

# 3. Example Using JavaScript

Your API key should come from an environment variable.

```javascript
const response = await fetch(
  "http://15.207.121.43:8080/api/v1/jobs",
  {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-API-Key": process.env.TASK_QUEUE_API_KEY
    },
    body: JSON.stringify({
      type: "SEND_EMAIL",
      payload: {
        to: "customer@example.com",
        subject: "Your order is confirmed!",
        body: "<h1>Thank you for your order!</h1>"
      },
      priority: "HIGH",
      smtpPurpose: "NOREPLY"
    })
  }
);

const data = await response.json();

console.log(data);
```

---

# 4. Check Job Status

After submitting a job, use the `jobId` returned by the enqueue API.

### Request

```http
GET /api/v1/jobs/{jobId}
```

### Example

```http
GET /api/v1/jobs/f6a7b8c9-d0e1-2345-fabc-456789012345
X-API-Key: YOUR_RAW_API_KEY
```

### Response

```json
{
  "success": true,
  "data": {
    "jobId": "f6a7b8c9-d0e1-2345-fabc-456789012345",
    "type": "SEND_EMAIL",
    "status": "SUCCESS",
    "priority": "HIGH",
    "retryCount": 0,
    "maxRetries": 3,
    "canRetry": false,
    "projectId": "c3d4e5f6-a7b8-9012-cdef-123456789012",
    "projectName": "Order Service",
    "companyName": "Swiggy",
    "createdAt": "2026-08-23T10:15:00",
    "startedAt": "2026-08-23T10:15:01",
    "completedAt": "2026-08-23T10:15:02",
    "errorMessage": null
  }
}
```

### Job Lifecycle

A job normally moves through:

```text
QUEUED
   ↓
RUNNING
   ↓
SUCCESS
```

If processing fails:

```text
QUEUED
   ↓
RUNNING
   ↓
FAILED
   ↓
RETRY
   ↓
RUNNING
```

After the maximum number of retries:

```text
FAILED
   ↓
DEAD
```

---

# 5. List Jobs

Retrieve jobs belonging to your project.

### Request

```http
GET /api/v1/jobs
```

### Headers

```http
X-API-Key: YOUR_RAW_API_KEY
```

### Example

```bash
curl "http://15.207.121.43:8080/api/v1/jobs?page=0&size=10" \
  -H "X-API-Key: YOUR_RAW_API_KEY"
```

The response contains a paginated list of jobs.

---

# 6. Filter Jobs by Status

You can filter jobs by their current status.

```http
GET /api/v1/jobs?status=SUCCESS&page=0&size=10
```

Available statuses include:

```text
QUEUED
RUNNING
SUCCESS
FAILED
DEAD
```

Example:

```bash
curl "http://15.207.121.43:8080/api/v1/jobs?status=FAILED&page=0&size=10" \
  -H "X-API-Key: YOUR_RAW_API_KEY"
```

---

# 7. Retry a Failed Job

A failed job can be manually queued again if it is eligible for retry.

### Request

```http
POST /api/v1/jobs/{jobId}/retry
```

### Headers

```http
X-API-Key: YOUR_RAW_API_KEY
```

### Example

```bash
curl -X POST \
  "http://15.207.121.43:8080/api/v1/jobs/JOB_ID/retry" \
  -H "X-API-Key: YOUR_RAW_API_KEY"
```

### Response

```json
{
  "success": true,
  "data": {
    "jobId": "f6a7b8c9-d0e1-2345-fabc-456789012345",
    "status": "QUEUED",
    "message": "Job re-queued for processing"
  }
}
```

A job that has already completed successfully cannot be retried.

---

# 8. Supported Job Types

The system supports different types of background jobs.

## SEND_EMAIL

Used for sending emails through the SMTP configuration associated with your project.

```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "customer@example.com",
    "subject": "Welcome!",
    "body": "<h1>Welcome to our service!</h1>"
  },
  "priority": "NORMAL",
  "smtpPurpose": "NOREPLY"
}
```
 
The payload can contain application-specific data required by your worker.

---

# 9. Job Priority

Jobs can be assigned a priority:

```text
HIGH
NORMAL
LOW
```

Example:

```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "customer@example.com",
    "subject": "Important notification",
    "body": "<p>This is an important message.</p>"
  },
  "priority": "HIGH",
  "smtpPurpose": "ALERT"
}
```

Use `HIGH` for time-sensitive jobs and `LOW` for tasks that can wait.

---

# 10. SMTP Purpose

For email jobs, `smtpPurpose` determines which SMTP configuration should be used.

Supported purposes:

```text
NOREPLY
SUPPORT
BILLING
ALERT
CUSTOM
```

Example:

```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "customer@example.com",
    "subject": "Invoice",
    "body": "<p>Your invoice is ready.</p>"
  },
  "priority": "NORMAL",
  "smtpPurpose": "BILLING"
}
```

The system selects the SMTP configuration configured for the `BILLING` purpose.

---

# 11. Idempotency

Use an `idempotencyKey` when the same request must not create duplicate jobs.

Example:

```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "customer@example.com",
    "subject": "Payment received",
    "body": "<p>Your payment was successfully received.</p>"
  },
  "priority": "HIGH",
  "smtpPurpose": "NOREPLY",
  "idempotencyKey": "payment-7821-email"
}
```

If the same `idempotencyKey` is submitted again, the system prevents the duplicate job.

Example response:

```http
409 Conflict
```

```json
{
  "success": false,
  "error": "Job with idempotency key 'payment-7821-email' already exists."
}
```

This is useful for preventing duplicate notifications when your application retries an HTTP request.

---

# 12. Callback URL

You can optionally provide a callback URL that the Task Queue System can notify after job processing.

Example:

```json
{
  "type": "SEND_EMAIL",
  "payload": {
    "to": "customer@example.com",
    "subject": "Order completed",
    "body": "<p>Your order is complete.</p>"
  },
  "priority": "NORMAL",
  "smtpPurpose": "NOREPLY",
  "callbackUrl": "https://your-server.com/webhooks/task-queue"
}
```

This allows your application to receive job completion information without repeatedly polling the job status endpoint.

---

# 13. Error Responses

## Missing API Key

```http
401 Unauthorized
```

```json
{
  "success": false,
  "error": "Missing X-API-Key header"
}
```

---

## Invalid API Key

```http
401 Unauthorized
```

```json
{
  "success": false,
  "error": "Invalid API key"
}
```

---

## Expired or Revoked API Key

```http
401 Unauthorized
```

```json
{
  "success": false,
  "error": "API key is inactive or expired"
}
```

---

## Validation Error

```http
400 Bad Request
```

Example:

```json
{
  "success": false,
  "error": "Validation failed: type is required"
}
```

---

## Rate Limit Exceeded

```http
429 Too Many Requests
```

Example:

```json
{
  "success": false,
  "error": "Rate limit exceeded. Max 100 requests/minute.",
  "retryAfter": 60
}
```

---

# 14. Rate Limiting

Every API key can have its own request limit.

Example:

```text
Rate limit: 100 requests/minute
```

The API provides rate-limit information through response headers:

```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 45
```

If the limit is exceeded, the API returns:

```http
429 Too Many Requests
```

---

# 🔄 Typical Integration Flow

A normal application integration looks like this:

```text
Your Application
       │
       │ POST /jobs
       │ X-API-Key
       ▼
┌─────────────────────┐
│   Task Queue API    │
└──────────┬──────────┘
           │
           │ 202 Accepted
           ▼
      Job ID returned
           │
           ▼
┌─────────────────────┐
│ Background Worker   │
└──────────┬──────────┘
           │
           ▼
        SUCCESS
           │
           ▼
      Email / Task
      Completed
```

Your application does **not** need to wait for the background operation to finish.

---

# 🧪 Quick API Test

Once you have created a project, configured SMTP, and generated an API key, the simplest test is:

```bash
curl -X POST "http://15.207.121.43:8080/api/v1/jobs" \
  -H "Content-Type: application/json" \
  -H "X-API-Key: YOUR_RAW_API_KEY" \
  -d '{
    "type": "SEND_EMAIL",
    "payload": {
      "to": "customer@example.com",
      "subject": "Task Queue Test",
      "body": "<h1>Hello!</h1><p>This email was sent through the Task Queue System.</p>"
    },
    "priority": "HIGH",
    "smtpPurpose": "NOREPLY"
  }'
```

If successful, you should receive:

```text
HTTP 202 Accepted
```

with a `jobId`.

Then check:

```bash
curl \
  "http://15.207.121.43:8080/api/v1/jobs/JOB_ID" \
  -H "X-API-Key: YOUR_RAW_API_KEY"
```

---

# 📚 Related Documentation

For complete testing of all system features, including:

* Admin APIs
* Company management
* Project management
* API key management
* SMTP management
* Security testing
* Rate limiting
* Dead Letter Queue
* Kafka verification
* Redis verification
* PostgreSQL verification
* pgAdmin queries

see the **Manual Testing Guide**.

The Manual Testing Guide contains the complete **48-test Postman workflow**.
