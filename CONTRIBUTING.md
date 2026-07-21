# Contributing

## Table of Contents

<!-- TOC -->
* [Contributing](#contributing)
  * [Table of Contents](#table-of-contents)
  * [Repository layout](#repository-layout)
  * [Tech stack](#tech-stack)
    * [Backend](#backend)
    * [Frontend](#frontend)
    * [API contract](#api-contract)
  * [Architecture](#architecture)
  * [Prerequisites](#prerequisites)
  * [Local setup](#local-setup)
    * [1. Environment variables](#1-environment-variables)
    * [2. Start the database](#2-start-the-database)
    * [3. Run the backend](#3-run-the-backend)
    * [4. Run the frontend](#4-run-the-frontend)
  * [Running tests](#running-tests)
    * [Backend](#backend-1)
    * [E2E (Playwright)](#e2e-playwright)
    * [All tests + checks](#all-tests--checks)
  * [Code generation](#code-generation)
  * [Workflow](#workflow)
  * [Branching & CI/CD](#branching--cicd)
  * [Available tasks](#available-tasks)
<!-- TOC -->

## Repository layout

```
simpletickr/
├── backend/          # Spring Boot application
├── frontend/         # SvelteKit application
└── openapi.yaml      # API contract (source of truth)
```

## Tech stack

### Backend
- **Kotlin / Spring Boot 3.5** — REST API
- **Spring JDBC** — raw SQL, no ORM
- **Flyway** — schema migrations
- **SpringDoc OpenAPI** — serves Swagger UI at `/swagger-ui.html`
- **PostgreSQL**

### Frontend
- **SvelteKit + Svelte 5** — runes (`$state`, `$derived`, `$props`)
- **Tailwind CSS v4 + DaisyUI v5**
- **Hey API** — typed client generated from `openapi.yaml`
- **Chart.js**
- **Playwright** — E2E tests

### API contract
`openapi.yaml` is schema-first and the single source of truth. The backend generates Spring controller interfaces from it; the frontend generates a typed API client.

## Architecture

Code is grouped by domain (`portfolio`, `asset`, `transaction`), not by technical role. Within each domain there are three thin layers:

- **Domain class** — pure Kotlin, no Spring annotations; all business rules live here
- **Repository** — raw SQL via `JdbcTemplate`; returns domain objects
- **Controller** — HTTP translation only; implements the generated OpenAPI interface

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

The app is available at `http://localhost:5173`.

## Running tests

### Backend

```bash
task backend:test
```

Backend tests use [Testcontainers](https://testcontainers.com) to spin up a real PostgreSQL instance — no env vars or running database required. Docker must be available.

> **Note — Docker API version**: docker-java (used by Testcontainers) defaults to Docker API version 1.32, which Docker Engine 29+ no longer supports (minimum is 1.40). The `api.version=1.44` system property is set in `build.gradle.kts` to work around this. No manual setup is needed.

### E2E (Playwright)

```bash
task frontend:test:e2e
```

Requires the backend to be running on `localhost:8080` (steps 2–3 above).

### All tests + checks

```bash
task ci
```

Runs `api:lint`, `backend:build` (compile + tests), and the frontend pipeline (codegen → type-check → build) in parallel. Does not run E2E tests.

## Code generation

Spring controller interfaces are generated from `openapi.yaml` via the OpenAPI Generator Gradle plugin. Generated sources land in `build/generated` and are not committed.

After cloning, run a build once so the generated sources exist and the IDE can resolve imports:

```bash
task backend:build
```

Re-run this whenever `openapi.yaml` changes:

```bash
cd backend && ./gradlew openApiGenerate
```

The frontend client is generated with:

```bash
task frontend:codegen
```

IntelliJ will pick up the generated source root automatically after a Gradle sync.

## Workflow

1. Define or update `openapi.yaml` — the API contract drives everything
2. Regenerate: `task backend:build` and `task frontend:codegen`
3. Implement: domain class → repository → controller
4. Add Flyway migrations for schema changes (`backend/src/main/resources/db/migration/`)
5. Write tests

## Branching & CI/CD

This project aims to follow [minimumcd.org](https://minimumcd.org)'s minimum viable practices for trunk-based development and continuous integration. Honest status as of this writing:

**Trunk-based development — followed.** All work integrates directly to `main`. When a branch is used, it's short-lived: branched from `main`, merged back, then deleted. There are no long-lived feature branches.

**Continuous integration — mostly followed, one known gap.**
- CI (`.github/workflows/ci.yml`) runs on every push and PR: backend build + tests, frontend build, Helm lint, and E2E tests against a real Postgres instance.
- **Gap:** `main` has no branch protection configured, so a red pipeline doesn't actually block anything — "test before merge" and "stop the line on red" are conventions we're relying on, not enforced. Enabling required status checks on `main` would close this gap.
- Integration is close to daily but not strictly enforced;

**Continuous delivery — intentionally not "continuous."** simpletickr doesn't own a production environment to deploy to; it ships as versioned Docker images and a Helm chart for self-hosters to deploy themselves. Given that, full CD (pipeline auto-deploying to prod) doesn't apply. Instead:
- `.github/workflows/release.yml` is manually triggered (`workflow_dispatch`) to bump a version, build immutable version-tagged Docker images and a Helm chart, and cut a GitHub release.
- Released artifacts are immutable — no changes after build.
- Application config ships with the Helm chart (`values.yaml`), not baked into the image.
- The release workflow does not currently check that the tagged commit's CI run passed before publishing — worth tightening if this becomes a multi-contributor project.

## Available tasks

Run `task --list` to see all available tasks.
)