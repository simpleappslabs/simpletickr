# Contributing

## Prerequisites

- [sdkman](https://sdkman.io) — manages the Java version (see `.sdkmanrc`)
- Docker (for the local database and tests)
- Node.js 20+
- [Task](https://taskfile.dev) (`brew install go-task` or equivalent)

The project ships a `.sdkmanrc` file that pins the Java version. With `sdkman_auto_env=true` in `~/.sdkman/etc/config`, the correct JDK is selected automatically when you `cd` into the repo. To enable it once:

```bash
sdk env install   # download the pinned JDK if not already present
```

## Local setup

### 1. Environment variables

Copy the example env file and fill in the values:

```bash
cp .env.example .env
```

The backend reads `DB_NAME`, `DB_USER`, and `DB_PASSWORD` at startup.

### 2. Start the database

```bash
task up
```

### 3. Run the backend

```bash
task backend:run
```

The API is available at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`

### 4. Run the frontend

```bash
cd frontend && npm install && npm run dev
```

## Running tests

```bash
task backend:test
```

Backend tests use [Testcontainers](https://testcontainers.com) to spin up a real PostgreSQL instance — no env vars or running database required. Docker must be available.

> **Note — Docker API version**: docker-java (used by Testcontainers) defaults to Docker API version 1.32, which Docker Engine 29+ no longer supports (minimum is 1.40). The `api.version=1.44` system property is set in `build.gradle.kts` to work around this. No manual setup is needed; it is already wired into the Gradle test task.

## Workflow

1. Define or update `openapi.yaml` — this is the API contract and source of truth
2. Regenerate backend interfaces and frontend client
3. Implement: domain logic → controller → repository
4. Add Flyway migrations for any schema changes under `backend/src/main/resources/db/migration/`
5. Write tests

## Available tasks

Run `task --list` to see all available tasks.