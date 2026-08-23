# Task Queue System — User Guide

## 🚀 Getting Started

The **Task Queue System** allows your application to send background jobs such as email notifications through an API. Jobs are placed into a queue and processed asynchronously by the system.

### 🌐 Open the Application

Visit:

**http://15.207.121.43:3000/login**

> **Note:** The application is currently hosted on an AWS EC2 instance.

---

## 1. Create Your Account

On the login page, click **Create an Account**.

Enter:

* **Name**
* **Email**
* **Password**
* **Company Name**

Then click **Create Account**.

After successful registration, you will automatically be redirected to the **Dashboard**.

### 😄 A Note About the Default Admin

You may notice references to credentials such as:

```text
admin@taskqueue.local
admin123
```

Don't be that person. 😄

Those are default/development credentials and should **not** be used for normal testing. Create your own account instead.

---

# 2. Create a Project

From the Dashboard:

1. Open the **Projects** section.
2. Click **Create Project**.
3. Enter the required project information.
4. Create the project.

Once the project is created, you can configure:

* SMTP configurations
* API keys
* Job processing
* Job monitoring

---

# 3. Configure SMTP

Before sending email jobs, configure an SMTP server for your project.

Go to:

**Project → SMTP Configuration → Add SMTP Config**

You will see the following configuration:

```text
Add SMTP Config

Purpose
  NOREPLY
  SUPPORT
  BILLING
  ALERT
  CUSTOM

Label
  e.g. Gmail No-Reply

From Email
  noreply@company.com

From Name
  My Company

SMTP Host
  smtp.gmail.com

Port
  587

Username
Password

Use TLS
```

## ⚠️ Important: SMTP Purpose

Choose the correct **Purpose** for your SMTP configuration.

Available purposes:

* `NOREPLY`
* `SUPPORT`
* `BILLING`
* `ALERT`
* `CUSTOM`

The purpose is important because the API job can specify which type of email it wants to send.

For example:

```json
{
  "smtpPurpose": "NOREPLY"
}
```

The Task Queue System will then use the SMTP configuration associated with `NOREPLY`.

### Example

Suppose your project has:

```text
NOREPLY  → noreply@company.com
SUPPORT  → support@company.com
BILLING  → billing@company.com
ALERT    → alerts@company.com
```

Then an API request containing:

```json
"smtpPurpose": "BILLING"
```

will use the configured **BILLING** SMTP configuration.

---

# 4. Create an API Key

After configuring SMTP, create an API key.

Go to:

**Project → API Keys → Create API Key**

You will see something similar to:

```text
Create API Key

Key prefix
  tq_live_ (auto from project environment)

Label
  e.g. Production Key Jan 2025

Rate limit (req/min)
  100
```

### API Key Settings

#### Key Prefix

The system automatically generates the prefix based on the project environment.

For example:

```text
tq_live_
```

#### Label

Give your API key a meaningful name.

Example:

```text
Production Backend
```

or:

```text
Production Key Jan 2025
```

#### Rate Limit

Specify how many requests the API key can make per minute.

Example:

```text
100 requests/minute
```

Click **Create Key**.

## ⚠️ IMPORTANT — Save Your API Key

**Save the API key immediately after creating it.**

The raw API key is only displayed when it is created. You may not be able to view the complete key again later.

Example:

```text
tq_live_xxxxxxxxxxxxxxxxxxxxxxxxx
```

Treat this key like a password.

---

# 5. Send a Job to the Task Queue

Once you have:

* Created an account
* Created a project
* Configured SMTP
* Created an API key

your backend application can send jobs to the Task Queue System.

The API endpoint is:

```text
POST http://15.207.121.43:8080/api/v1/jobs
```


---

# 6. Enqueue a SEND_EMAIL Job

Here is a basic example of sending an email job.

### HTTP Request

```http
POST /api/v1/jobs
Content-Type: application/json
X-API-Key: YOUR_RAW_API_KEY
```

### URL

```text
http://15.207.121.43:8080/api/v1/jobs
```

### Request Body

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

### Important

Replace:

```text
YOUR_RAW_API_KEY
```

with the API key generated from your project.

For example:

```http
X-API-Key: tq_live_xxxxxxxxxxxxxxxxx
```



---

# 7. Example Using cURL

You can test the API directly from your terminal.

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

# 8. Expected Response

If the job is accepted successfully, the API returns:

```http
202 Accepted
```

Example response:

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

The important value is:

```text
jobId
```

For example:

```text
f6a7b8c9-d0e1-2345-fabc-456789012345
```

Save this `jobId` if you want to track the job.

---

# 9. Monitor Your Job

After sending the request, open the **Jobs** section in your project dashboard.

You can monitor information such as:

* Job ID
* Job type
* Priority
* Status
* Creation time
* Processing status
* Failed jobs
* Completed jobs

A newly submitted job will initially appear as:

```text
QUEUED
```

Depending on the worker and queue processing, it can then move through its processing lifecycle.

For example:

```text
QUEUED → PROCESSING → COMPLETED
```

If processing fails, the system can retry the job according to the configured retry mechanism.

---

# 10. Verify the Email

After the job is successfully processed, check the inbox configured for testing.

For example, if you are using **Mailtrap**, open your Mailtrap inbox and verify that the email was received.

You should see something similar to:

```text
Subject:
Your order is confirmed!

From:
noreply@company.com

To:
customer@example.com
```

The exact delivery time depends on the queue and worker processing, but during normal operation it should be processed shortly after the request is accepted.

---

# 11. Complete Flow

The complete setup looks like this:

```text
                 ┌───────────────────┐
                 │   Your Backend    │
                 └─────────┬─────────┘
                           │
                           │ POST /api/v1/jobs
                           │ X-API-Key
                           ▼
                 ┌───────────────────┐
                 │  Task Queue API   │
                 └─────────┬─────────┘
                           │
                           │ Queue Job
                           ▼
                 ┌───────────────────┐
                 │   Message Queue   │
                 └─────────┬─────────┘
                           │
                           ▼
                 ┌───────────────────┐
                 │   Worker Service  │
                 └─────────┬─────────┘
                           │
                           │ smtpPurpose
                           ▼
                 ┌───────────────────┐
                 │  SMTP Configuration│
                 │     NOREPLY        │
                 └─────────┬─────────┘
                           │
                           ▼
                 ┌───────────────────┐
                 │   Email Service   │
                 └─────────┬─────────┘
                           │
                           ▼
                 ┌───────────────────┐
                 │      Inbox        │
                 │    Mailtrap/etc.  │
                 └───────────────────┘
```

---
 

# 14. Example Backend Integration

A backend application can make the request like this:

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

This keeps the API key on your backend instead of exposing it to the user.

---

# 🎯 Summary

The Task Queue System provides a simple flow for sending background jobs:

```text
Create Account
      ↓
Create Project
      ↓
Configure SMTP
      ↓
Create API Key
      ↓
Integrate API
      ↓
Send Job
      ↓
Job Queued
      ↓
Worker Processes Job
      ↓
Email Sent
      ↓
Monitor Result
```

Once the project, SMTP configuration, and API key are configured, your application only needs to send a job request to the Task Queue API. The system handles queueing and background processing while your application can continue its normal execution.
