# Task Queue — Admin Panel Frontend Guide

> React 18 + Vite + Tailwind CSS + TanStack Query + Axios
> Dark/Light theme toggle · Sidebar + Top navbar · JWT login
> Talks to: http://localhost:8080/api/v1

---

## Table of Contents

1. [Tech stack and why](#1-tech-stack-and-why)
2. [Backend changes needed first](#2-backend-changes-needed-first)
3. [Project setup](#3-project-setup)
4. [Folder structure](#4-folder-structure)
5. [Color scheme and design system](#5-color-scheme-and-design-system)
6. [All pages — what each does](#6-all-pages)
7. [Component breakdown](#7-component-breakdown)
8. [API layer — all calls](#8-api-layer)
9. [JWT auth flow](#9-jwt-auth-flow)
10. [How to build page by page](#10-build-order)

---

## 1. Tech stack and why

| Tool | Version | Why chosen |
|------|---------|-----------|
| React | 18 | Component model, hooks, industry standard |
| Vite | 5 | Starts in <1s, instant hot reload — much faster than CRA |
| Tailwind CSS | 3 | Utility classes, dark mode built in, no separate CSS files |
| TanStack Query | 5 | Handles loading/error/cache for every API call automatically |
| Axios | 1.6 | Interceptors attach JWT header to every request in one place |
| React Router | 6 | Client-side routing between pages |
| Recharts | 2 | Lightweight charts for dashboard — works perfectly with Tailwind |
| React Hot Toast | 2 | Clean toast notifications for success/error messages |
| Lucide React | latest | Clean icon set, tree-shakeable |
| date-fns | 3 | Format timestamps cleanly |

**Do NOT use:** Create React App (dead/slow), Material UI (too opinionated), Redux (overkill — React Query handles server state).

---

## 2. Backend changes needed first

Before building any frontend, add these two things to Spring Boot:

### 2a — Login endpoint

The admin panel needs to authenticate. Add `POST /auth/login` to your backend.

Create `AuthController.java`:
```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> login(
        @RequestBody LoginRequest req
    ) {
        // 1. Find user by email
        // 2. BCrypt.matches(req.password, user.passwordHash)
        // 3. If valid → generate JWT → return it
        // 4. If invalid → return 401
    }
}
```

Create `JwtService.java`:
```java
@Service
public class JwtService {
    // generateToken(userId, role) → returns JWT string
    // validateToken(token) → returns claims or throws
}
```

Add to `pom.xml`:
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

Add to `application.yml`:
```yaml
app:
  jwt:
    secret: "YourJwtSecretKeyMustBe32CharsLong!!"  # exactly 32+ chars
    expiry-hours: 24
```

Login request body:
```json
{ "email": "admin@taskqueue.local", "password": "admin123" }
```

Login response:
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

### 2b — Update AdminBypassFilter to also accept JWT

Currently admin endpoints only allow localhost IP.
Add a second check: if request has valid JWT with role=ADMIN, also allow it.
This lets the React app (on localhost:3000) authenticate properly.

Update `AdminBypassFilter.java`:
```java
// Check 1: localhost IP (existing)
// Check 2: valid JWT with ADMIN role (new)
String authHeader = request.getHeader("Authorization");
if (authHeader != null && authHeader.startsWith("Bearer ")) {
    String token = authHeader.substring(7);
    if (jwtService.isValid(token) && jwtService.getRole(token).equals("ADMIN")) {
        setAdminContext(request);
        chain.doFilter(request, response);
        return;
    }
}
```

---

## 3. Project setup

```bash
# Create Vite + React project
npm create vite@latest admin-panel -- --template react
cd admin-panel

# Install all dependencies
npm install

# Tailwind CSS
npm install -D tailwindcss postcss autoprefixer
npx tailwindcss init -p

# Core packages
npm install axios
npm install @tanstack/react-query
npm install react-router-dom
npm install recharts
npm install react-hot-toast
npm install lucide-react
npm install date-fns
npm install clsx

# Run dev server
npm run dev
# Opens at http://localhost:5173
```

### tailwind.config.js — paste this exactly:
```js
/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  darkMode: "class",   // class-based dark mode — we toggle "dark" on <html>
  theme: {
    extend: {
      colors: {
        primary: {
          50:  "#EEEDFE",
          100: "#CECBF6",
          400: "#7F77DD",
          500: "#534AB7",
          600: "#3C3489",
          900: "#26215C",
        },
        teal: {
          50:  "#E1F5EE",
          400: "#1D9E75",
          600: "#0F6E56",
        }
      }
    }
  },
  plugins: [],
}
```

### src/index.css — paste this:
```css
@tailwind base;
@tailwind components;
@tailwind utilities;

/* Smooth transitions for dark/light switch */
* { transition: background-color 0.2s, border-color 0.2s; }
```

### vite.config.js — add proxy to avoid CORS:
```js
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      }
    }
  }
})
```

---

## 4. Folder structure

```
admin-panel/
├── index.html
├── vite.config.js
├── tailwind.config.js
├── package.json
│
└── src/
    ├── main.jsx                    App entry point
    ├── App.jsx                     Router + QueryClient + ThemeProvider
    ├── index.css                   Tailwind imports
    │
    ├── context/
    │   ├── AuthContext.jsx         JWT token, user info, login/logout functions
    │   └── ThemeContext.jsx        Dark/light toggle, persisted in localStorage
    │
    ├── hooks/
    │   ├── useAuth.js              useContext(AuthContext) shortcut
    │   └── useTheme.js             useContext(ThemeContext) shortcut
    │
    ├── api/
    │   ├── axios.js                Axios instance + JWT interceptor + error handling
    │   ├── auth.js                 login()
    │   ├── companies.js            getCompanies(), createCompany(), toggleCompany()
    │   ├── projects.js             getProjects(), createProject(), toggleProject()
    │   ├── apiKeys.js              getApiKeys(), createApiKey(), revokeApiKey()
    │   ├── jobs.js                 getJobs(), getJob(), retryJob()
    │   ├── dlq.js                  getDlq(), replaySingle(), replayAll()
    │   ├── smtp.js                 getSmtp(), createSmtp(), testSmtp(), deleteSmtp()
    │   └── metrics.js              getMetrics()
    │
    ├── components/
    │   ├── layout/
    │   │   ├── AppLayout.jsx       Sidebar + TopBar wrapper — wraps all protected pages
    │   │   ├── Sidebar.jsx         Left navigation menu with icons
    │   │   └── TopBar.jsx          Top bar — page title, theme toggle, user info
    │   │
    │   ├── ui/
    │   │   ├── Button.jsx          Primary/secondary/danger variants
    │   │   ├── Card.jsx            White/dark card container
    │   │   ├── Badge.jsx           Status badges — QUEUED/RUNNING/SUCCESS/FAILED/DEAD
    │   │   ├── Table.jsx           Reusable data table with pagination
    │   │   ├── Modal.jsx           Reusable confirm/form modal
    │   │   ├── Input.jsx           Styled form input
    │   │   ├── Select.jsx          Styled dropdown
    │   │   ├── Spinner.jsx         Loading spinner
    │   │   ├── EmptyState.jsx      "No data" placeholder with icon
    │   │   └── PageHeader.jsx      Page title + action button row
    │   │
    │   └── dashboard/
    │       ├── MetricCard.jsx      Number + label + trend card
    │       └── JobsChart.jsx       Recharts bar chart — jobs by status
    │
    └── pages/
        ├── Login.jsx               Email + password form → JWT stored in localStorage
        ├── Dashboard.jsx           Metrics overview + charts + recent jobs
        ├── Companies.jsx           List + create companies
        ├── Projects.jsx            List + create projects per company
        ├── ApiKeys.jsx             Create key (show raw once), list, revoke
        ├── Jobs.jsx                Browse all jobs with filters
        ├── JobDetail.jsx           Full job detail — payload, timeline, retry button
        ├── DeadLetterQueue.jsx     Dead jobs — replay single or all
        └── SmtpSettings.jsx        SMTP configs per company — add, test, delete
```

---

## 5. Color scheme and design system

### Primary colors
```
Purple (brand):   #534AB7  used for buttons, active nav, badges
Teal (success):   #1D9E75  used for success states, verified badges
Amber (warning):  #BA7517  used for FAILED status, warnings
Red (danger):     #E24B4A  used for DEAD status, destructive actions
Gray (neutral):   used for text, borders, backgrounds
```

### Dark mode values
```
Background:       #0F0F0F  page background
Surface:          #1A1A1A  cards, sidebar
Border:           #2A2A2A  card borders
Text primary:     #F0F0F0
Text secondary:   #9CA3AF
```

### Light mode values
```
Background:       #F8F9FA  page background
Surface:          #FFFFFF  cards
Border:           #E5E7EB
Text primary:     #111827
Text secondary:   #6B7280
```

### Status badge colors
```
QUEUED:   blue bg   — #DBEAFE text #1E40AF
RUNNING:  amber bg  — #FEF3C7 text #92400E
SUCCESS:  green bg  — #D1FAE5 text #065F46
FAILED:   red bg    — #FEE2E2 text #991B1B
DEAD:     gray bg   — #F3F4F6 text #374151
```

---

## 6. All pages

### Login.jsx
```
What it shows:
  - Centered card with logo/title at top
  - Email input field
  - Password input field
  - "Sign in" button
  - Error message if credentials wrong

What it does:
  - POST /auth/login
  - On success: saves token to localStorage, saves user to AuthContext, redirects to /dashboard
  - On fail: shows red error message under form

No layout wrapper — full page, centered, no sidebar
```

### Dashboard.jsx
```
What it shows:
  - Row of 6 metric cards: Total Jobs, Queued, Running, Success, Failed, Dead
  - Bar chart: jobs by status (Recharts)
  - DLQ badge: "X pending dead jobs" with link to DLQ page
  - Recent jobs table: last 10 jobs across all companies
  - Auto-refreshes every 10 seconds

API calls:
  GET /admin/metrics
  GET /admin/jobs?page=0&size=10
```

### Companies.jsx
```
What it shows:
  - Page header with "New Company" button
  - Table: name, slug, owner, active status, created date, actions
  - Actions per row: toggle active/inactive
  - Modal: create new company (name + slug fields)
  - Clicking a company row navigates to its projects

API calls:
  GET /admin/companies
  POST /admin/companies
  PATCH /admin/companies/{id}/toggle
```

### Projects.jsx
```
What it shows:
  - Company selector dropdown at top (or comes from URL param)
  - Table: name, environment badge, active status, created date
  - Actions: toggle active/inactive
  - Modal: create project (name, description, environment dropdown)
  - Clicking a project row navigates to its API keys

API calls:
  GET /admin/companies (for dropdown)
  GET /admin/companies/{id}/projects
  POST /admin/projects
  PATCH /admin/projects/{id}/toggle
```

### ApiKeys.jsx
```
What it shows:
  - Project selector at top
  - Table: label, key hint (...x2m9), prefix, rate limit, last used, expiry, status
  - "Create Key" button → modal with projectId, label, rateLimit, environment fields
  - After creation: full screen modal showing rawKey with copy button and warning banner
    "SAVE THIS NOW — shown once only"
  - Revoke button per row with confirm dialog

API calls:
  GET /admin/projects/{id}/keys
  POST /admin/keys
  DELETE /admin/keys/{id}
```

### Jobs.jsx
```
What it shows:
  - Filter bar: company dropdown, project dropdown, status dropdown
  - Table: type, status badge, priority, company/project, created, completed
  - Clicking a row navigates to JobDetail
  - Pagination at bottom

API calls:
  GET /admin/jobs?projectId=x&status=x&page=x&size=20
```

### JobDetail.jsx
```
What it shows:
  - Back button
  - Job info card: id, type, status badge, priority, project/company
  - Timestamps timeline: created → started → completed
  - Payload viewer: formatted JSON in a code block
  - Error message if failed
  - Retry button (visible only if status=FAILED and canRetry=true)
  - Retry count: "2 of 3 attempts used"

API calls:
  GET /admin/jobs/{id} (admin view — no API key needed)
  POST /jobs/{id}/retry (needs API key — explain this in UI)
```

### DeadLetterQueue.jsx
```
What it shows:
  - "X pending dead jobs" count badge at top
  - "Replay All" button at top right (with confirm dialog)
  - Table: job type, company/project, failure reason, failed date, replayed status
  - Per row: "Replay" button — disabled if already replayed
  - Replayed rows show replayed date in green

API calls:
  GET /admin/dlq
  POST /admin/dlq/{id}/replay
  POST /admin/dlq/replay-all
```

### SmtpSettings.jsx
```
What it shows:
  - Company selector at top
  - Cards grid: one card per SMTP config showing purpose, from email, host, verified badge
  - "Add SMTP Config" button → form modal (all fields)
  - Per card: Test Connection button, Enable/Disable toggle, Delete button
  - Test Connection shows green check or red error inline

API calls:
  GET /admin/companies/{id}/smtp
  POST /admin/smtp
  POST /admin/smtp/{id}/test
  PATCH /admin/smtp/{id}/toggle
  DELETE /admin/smtp/{id}
```

---

## 7. Component breakdown

### AppLayout.jsx
```
Structure:
  <div class="flex h-screen">
    <Sidebar />                      left, fixed, w-64
    <div class="flex-1 flex flex-col">
      <TopBar />                     top, fixed, h-16
      <main class="flex-1 overflow-auto p-6 mt-16">
        <Outlet />                   page renders here
      </main>
    </div>
  </div>
```

### Sidebar.jsx
```
Shows:
  - Logo/app name at top
  - Nav links with icons:
      Dashboard      (LayoutDashboard icon)
      Companies      (Building2 icon)
      Projects       (FolderOpen icon)
      API Keys       (Key icon)
      Jobs           (Briefcase icon)
      Dead Letter Q  (AlertTriangle icon) + red badge if pending count > 0
      SMTP Settings  (Mail icon)
  - Bottom: logged-in user email + logout button

Active link: purple background, white text
Inactive link: gray text, purple on hover

Collapsible on mobile (hamburger from TopBar toggles it)
```

### TopBar.jsx
```
Shows:
  - Hamburger menu (mobile only)
  - Current page title (from route)
  - Theme toggle button (sun/moon icon)
  - User avatar + name
  - Logout button

Theme toggle:
  Clicking adds/removes "dark" class on document.documentElement
  Persists choice in localStorage key "theme"
```

### Badge.jsx
```jsx
// Usage: <Badge status="SUCCESS" />
// Usage: <Badge status="QUEUED" />

const colors = {
  QUEUED:  "bg-blue-100 text-blue-800 dark:bg-blue-900 dark:text-blue-200",
  RUNNING: "bg-amber-100 text-amber-800 dark:bg-amber-900 dark:text-amber-200",
  SUCCESS: "bg-green-100 text-green-800 dark:bg-green-900 dark:text-green-200",
  FAILED:  "bg-red-100 text-red-800 dark:bg-red-900 dark:text-red-200",
  DEAD:    "bg-gray-100 text-gray-800 dark:bg-gray-700 dark:text-gray-200",
}
```

### MetricCard.jsx
```
Props: title, value, icon, color, trend (optional)

Shows:
  - Icon on left in colored circle
  - Large number in center
  - Title below number
  - Optional up/down trend percentage

Example: "1,423 Total Jobs"  "12 Queued"  "8 Dead"
```

---

## 8. API layer

### src/api/axios.js
```js
import axios from 'axios'

const api = axios.create({
  baseURL: '/api/v1',       // proxied to localhost:8080 via vite.config.js
  timeout: 15000,
})

// Request interceptor — attach JWT to every request automatically
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response interceptor — handle 401 globally
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('admin_token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default api
```

### src/api/auth.js
```js
import api from './axios'

export const login = async (email, password) => {
  const res = await api.post('/auth/login', { email, password })
  return res.data.data   // { token, email, role }
}
```

### src/api/companies.js
```js
import api from './axios'

export const getCompanies  = () => api.get('/admin/companies').then(r => r.data.data)
export const createCompany = (data) => api.post('/admin/companies', data).then(r => r.data.data)
export const toggleCompany = (id) => api.patch(`/admin/companies/${id}/toggle`).then(r => r.data.data)
```

### src/api/jobs.js
```js
import api from './axios'

export const getJobs = (params) =>
  api.get('/admin/jobs', { params }).then(r => r.data.data)

export const getJob = (id) =>
  api.get(`/admin/jobs/${id}`).then(r => r.data.data)
```

### src/api/metrics.js
```js
import api from './axios'

export const getMetrics = () => api.get('/admin/metrics').then(r => r.data.data)
```

**Same pattern for all other API files.**

---

## 9. JWT auth flow

```
1. User opens http://localhost:5173
2. App checks localStorage for "admin_token"
3. If no token → redirect to /login
4. Login page → POST /auth/login with email + password
5. Backend returns JWT token (24hr expiry)
6. Frontend:
     localStorage.setItem('admin_token', token)
     AuthContext.setUser({ email, role })
     navigate('/dashboard')
7. Every API request — axios interceptor adds:
     Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
8. AdminBypassFilter checks:
     a. Is request from localhost? → allow
     b. Does Bearer token have ADMIN role? → allow
     c. Neither → 401
9. On 401 response — axios interceptor:
     localStorage.removeItem('admin_token')
     redirect to /login
10. Logout button:
     localStorage.removeItem('admin_token')
     AuthContext.setUser(null)
     navigate('/login')
```

### Protected route wrapper
```jsx
// src/components/ProtectedRoute.jsx
export default function ProtectedRoute({ children }) {
  const token = localStorage.getItem('admin_token')
  if (!token) return <Navigate to="/login" replace />
  return children
}

// Usage in App.jsx
<Route path="/" element={
  <ProtectedRoute>
    <AppLayout />
  </ProtectedRoute>
}>
  <Route index element={<Navigate to="/dashboard" />} />
  <Route path="dashboard" element={<Dashboard />} />
  <Route path="companies" element={<Companies />} />
  ...
</Route>
<Route path="/login" element={<Login />} />
```

---

## 10. Build order

Build in this exact order — each step depends on the previous:

```
Step 1 — Backend (do first)
  Add POST /auth/login endpoint
  Add JwtService
  Update AdminBypassFilter to accept JWT

Step 2 — Project setup
  npm create vite + install all packages
  Configure tailwind.config.js
  Configure vite.config.js proxy

Step 3 — Foundation
  src/context/ThemeContext.jsx     (dark/light toggle)
  src/context/AuthContext.jsx      (JWT state)
  src/api/axios.js                 (interceptors)
  src/App.jsx                      (router + providers)

Step 4 — UI components (build once, use everywhere)
  Button.jsx
  Card.jsx
  Badge.jsx
  Input.jsx
  Select.jsx
  Modal.jsx
  Spinner.jsx
  Table.jsx

Step 5 — Login page
  src/pages/Login.jsx
  Test: login with admin@taskqueue.local / admin123
  Confirm token appears in localStorage
  Confirm redirect to /dashboard works

Step 6 — Layout
  Sidebar.jsx
  TopBar.jsx
  AppLayout.jsx
  ProtectedRoute.jsx
  Test: navigation between pages works

Step 7 — Dashboard
  src/api/metrics.js
  src/components/dashboard/MetricCard.jsx
  src/components/dashboard/JobsChart.jsx
  src/pages/Dashboard.jsx
  Test: metrics load, chart renders, auto-refresh works

Step 8 — Companies + Projects
  src/api/companies.js
  src/api/projects.js
  src/pages/Companies.jsx
  src/pages/Projects.jsx
  Test: create company, create project, toggle active

Step 9 — API Keys
  src/api/apiKeys.js
  src/pages/ApiKeys.jsx
  Test: create key, see rawKey modal, revoke

Step 10 — Jobs
  src/api/jobs.js
  src/pages/Jobs.jsx
  src/pages/JobDetail.jsx
  Test: filter by status, click row → detail page

Step 11 — DLQ
  src/api/dlq.js
  src/pages/DeadLetterQueue.jsx
  Test: replay single, replay all

Step 12 — SMTP Settings
  src/api/smtp.js
  src/pages/SmtpSettings.jsx
  Test: add config, test connection, delete
```

---

## All endpoints used by frontend

| Page | Method | Endpoint |
|------|--------|---------|
| Login | POST | /auth/login |
| Dashboard | GET | /admin/metrics |
| Dashboard | GET | /admin/jobs?page=0&size=10 |
| Companies | GET | /admin/companies |
| Companies | POST | /admin/companies |
| Companies | PATCH | /admin/companies/{id}/toggle |
| Projects | GET | /admin/companies/{id}/projects |
| Projects | POST | /admin/projects |
| Projects | PATCH | /admin/projects/{id}/toggle |
| API Keys | GET | /admin/projects/{id}/keys |
| API Keys | POST | /admin/keys |
| API Keys | DELETE | /admin/keys/{id} |
| Jobs | GET | /admin/jobs |
| JobDetail | GET | /admin/jobs/{id} |
| DLQ | GET | /admin/dlq |
| DLQ | POST | /admin/dlq/{id}/replay |
| DLQ | POST | /admin/dlq/replay-all |
| SMTP | GET | /admin/companies/{id}/smtp |
| SMTP | POST | /admin/smtp |
| SMTP | POST | /admin/smtp/{id}/test |
| SMTP | PATCH | /admin/smtp/{id}/toggle |
| SMTP | DELETE | /admin/smtp/{id} |