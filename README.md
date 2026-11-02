# FlowRelay
### Distributed Job Processing & Reliable Webhook Delivery

**Java 21 · Spring Boot · PostgreSQL · React · Docker · Testcontainers · Prometheus**

FlowRelay is a distributed background-job processing platform designed around durable persistence, concurrency control, reliable retries, and operational visibility.

The system exposes REST APIs for submitting and managing background tasks, uses PostgreSQL row-level locking to coordinate concurrent workers, and provides a React-based operations dashboard.

## System Architecture

```text
                     REST API Clients
                            |
                     Spring Boot API
                            |
                      PostgreSQL
                     Jobs & Events
                            |
                  Worker Lease Engine
                    SKIP LOCKED
                            |
                    Job Execution
                            |
                  +---------+---------+
                  |                   |
             Job Handlers       HTTPS Webhooks
                  |                   |
             Job Results         HTTP Responses
                  |                   |
                  +---------+---------+
                            |
                  Completion / Retry
                     / Dead Letter
                            |
                    React Dashboard
```

## Core Capabilities

- **Distributed job processing:** durable job submission, scheduling, priorities and concurrent worker execution.
- **Database-level concurrency:** PostgreSQL `FOR UPDATE SKIP LOCKED` prevents workers from simultaneously claiming the same eligible job.
- **Failure recovery:** retry scheduling, dead-letter handling and expired lease recovery.
- **Webhook delivery:** HTTPS POST delivery through a Java HTTP client, with destination restrictions, timeouts and HTTP failure handling.
- **Operational dashboard:** React and TypeScript interface for inspecting queue health, jobs and execution history.
- **Observability:** Prometheus metrics, Grafana configuration and structured logging.
- **Automated validation:** JUnit and Testcontainers tests covering leasing, worker processing and database workflows.

## My Engineering Contributions

This version extends the QueueForge open-source foundation through additional development and integration work.

**Webhook Delivery Integration**
- Developed `WebhookDeliveryService` for executing outbound HTTPS webhook requests.
- Integrated webhook delivery into the background job handler pipeline.
- Added HTTP response validation, request timeouts and restricted destination handling.

**Integration Test Improvements**
- Stabilized concurrent job-leasing tests and worker-processing test scenarios.
- Improved test isolation with PostgreSQL Testcontainers.
- Added database connection safety checks before test-data cleanup.
- Addressed job scheduling timing issues affecting integration-test reliability.

**Local Development Infrastructure**
- Updated Docker Compose database port configuration to avoid local PostgreSQL conflicts.
- Verified the complete Gradle unit and integration-test suite.

## Tech Stack

| Category | Technologies |
|---|---|
| Programming | Java 21, TypeScript |
| Backend | Spring Boot, REST APIs, Gradle |
| Database | PostgreSQL 16, JDBC, Flyway |
| Distributed processing | Worker leasing, SQL transactions, row locks |
| Frontend | React, Tailwind CSS |
| Testing | JUnit 5, AssertJ, Testcontainers |
| DevOps | Docker, GitHub Actions |
| Monitoring | Prometheus, Grafana |

## Local Setup

**Requirements:** Java 21, Docker Desktop, Node.js and npm.

Start the database:

```bash
docker compose up -d postgres
```

Start the backend:

```bash
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/queueforge ./gradlew bootRun
```

Start the frontend:

```bash
cd dashboard
npm ci
npm run dev
```

**Development URLs**

- Dashboard: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Swagger: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

## Testing

Run unit tests:

```bash
./gradlew test
```

Run PostgreSQL integration tests:

```bash
./gradlew integrationTest
```

Run the complete suite:

```bash
./gradlew clean test integrationTest --no-daemon --rerun-tasks
```

**Latest local validation:** The complete Gradle run succeeded, including 35 integration tests.

Dedicated webhook HTTP tests, broader destination-security hardening and performance benchmarks remain future work.

## Engineering Roadmap

- Automated webhook delivery tests and failure classification
- Persistent webhook attempt history
- Improved idempotency for external side effects
- Distributed worker load testing
- Reproducible performance benchmarks
- Public deployment with operational monitoring

## Project Foundation & License

FlowRelay extends the [QueueForge](https://github.com/Thaelith/QueueForge) project by Thaelith, distributed under the MIT License. The existing job queue, dashboard and core system architecture form the foundation; the additions developed for FlowRelay are documented above.

See [LICENSE](LICENSE) for the applicable license and copyright notice.
