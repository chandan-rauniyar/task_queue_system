# Task Queue System - Quickstart Guide

Welcome to the Task Queue System! This guide is explicitly designed for developers who have just cloned or downloaded this repository and want to get both the Backend and Frontend running on their local machines as quickly as possible.

## 🛠️ Prerequisites

Before you begin, ensure you have the following installed on your machine:

1. **Java 17** (Verify with `java -version`)
2. **Maven** (Verify with `mvn -version`)
3. **Docker & Docker Compose** (Must be running on your system)
4. **Node.js (v18+) & NPM** (Verify with `node -v` and `npm -v`)

---

## 🚀 Step 1: Clone the Repository

If you haven't already:
```bash
git clone https://github.com/chandan-rauniyar/task_queue_system.git
cd task_queue_system
```

---

## 🐳 Step 2: Start Infrastructure (Docker)

This project relies on PostgreSQL, Redis, Kafka, and ZooKeeper. These are fully containerized.

1. Open a terminal at the very root of the cloned repository (where `docker-compose.yml` is).
2. Start all services in the background:
   ```bash
   docker-compose up -d
   ```
3. Wait about 15-30 seconds for the databases and brokers to fully initialize.
4. Verify everything is running:
   ```bash
   docker-compose ps
   ```
   *(You should see `Up (healthy)` next to `tq_postgres`, `tq_redis`, `tq_kafka`, and `tq_zookeeper`)*

---

## ☕ Step 3: Start the Backend (Spring Boot)

The core engine of the system is a Spring Boot application that manages API Keys, scheduling, and Kafka messaging.

### Option A: Using the Terminal (Recommended)
1. Navigate into the backend folder:
   ```bash
   cd task-queue-system
   ```
2. Run the application using Maven:
   ```bash
   mvn spring-boot:run
   ```

### Option B: Using IntelliJ IDEA
1. Open IntelliJ → `File` → `Open` → Select the `task-queue-system/` subdirectory.
2. Allow IntelliJ to load the Maven dependencies.
3. Locate `TaskQueueApplication.java` and click the green Play ▶️ button.

**Important:** On the very first launch, Flyway will dynamically build all tables in the PostgreSQL database. **You will find the swagger API Docs at: http://localhost:8080/api/v1/swagger-ui.html**.

---

## ⚛️ Step 4: Start the Frontend (React + Vite)

The Admin UI dashboard manages projects, clients, and metrics.

1. Open a **new, separate terminal** window.
2. Navigate into the frontend UI folder from the project root:
   ```bash
   cd FRONTENDUI
   ```
3. Install the JavaScript package dependencies:
   ```bash
   npm install
   ```
4. Start the frontend development server:
   ```bash
   npm run dev
   ```

*(The frontend will usually spin up at **http://localhost:3000** or **http://localhost:5173** and is hardcoded to securely proxy `/api/v1` traffic locally to the backend container).*

---

## 🔑 Step 5: Log In

Open your browser and navigate to the frontend URL running from Step 4.

The database comes fully customized with a secure, pre-hashed System Admin account. Log into the dashboard with:

- **Email:** `admin@taskqueue.local`
- **Password:** `admin123`

---

## 🛑 Shutting Down

When you are done developing, safely shut down both the Spring Boot and Vite servers with `Ctrl + C` in their respective terminals. 

To shut down the background Docker containers and free up memory:
```bash
docker-compose down
```
*(Data will persist inside Docker volumes until manually cleared)*

🎉 **You're all set! Check `Setup_guide.md` if you require detailed technical debugging on individual infrastructure components.**
