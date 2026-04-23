# CLAUDE.md — simpletickr

Project context for Claude Code sessions.

## What this project is

**simpletickr** is a simple portfolio tracker (ETFs, crypto, stocks, etc.).
GitHub: https://github.com/simpleappslabs/simpletickr

## Monorepo structure

```
simpletickr/
├── backend/          # Kotlin / Spring Boot
├── frontend/         # SvelteKit
├── openapi.yaml      # API contract — source of truth
└── .tasks/           # Task files / backlog
```

## Tech stack

### Backend (`backend/`)
- Kotlin, Spring Boot
- Spring Web (REST controllers)
- Spring JDBC (raw SQL — no ORM, no JPA)
- Flyway (migrations in `backend/src/main/resources/db/migration/`)
- SpringDoc OpenAPI
- PostgreSQL

### Frontend (`frontend/`)
- SvelteKit
- Hey API (typed client generated from `openapi.yaml`)
- Chart.js

### API Contract
- `openapi.yaml` is schema-first and the single source of truth
- Backend: generates Spring controller interfaces via OpenAPI Generator
- Frontend: generates typed client via Hey API

## Architecture principles

- **Domain-first structure** — code is organized by domain, not by layer
- **Light DDD** — use entities, value objects, and aggregates where it adds clarity
- **Hexagonal instincts** — domain logic must not depend on Spring, JDBC, or any framework
- **No ORM** — use Spring JDBC with raw SQL; keep queries explicit and readable
- **Schema-first API** — never drift from `openapi.yaml`; update the contract first, then implement

## Workflow

1. Define or update the API contract in `openapi.yaml`
2. Regenerate backend interfaces and frontend client
3. Implement backend: domain logic first, then controller, then repository
4. Write Flyway migrations for any schema changes
5. Implement frontend against the generated typed client

## Backlog

Tracked as GitHub Issues on `simpleappslabs/simpletickr`.
Use `gh issue list` to see the current backlog.
Labels used: `feature`, `bug`, `chore`, `infra`, `frontend`, `backend`, `api`

## Code style

- Kotlin: idiomatic, no nullable abuse, prefer data classes and sealed classes
- SQL: explicit column names, no `SELECT *`
- Svelte: component-per-feature, typed stores
- No comments that restate what the code does — only explain non-obvious intent