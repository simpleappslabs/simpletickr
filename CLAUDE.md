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

## Architecture

### Style: domain-centric layered, not hexagonal

Code is grouped by domain (e.g. `portfolio`, `ticker`, `position`), not by technical role (e.g. `controllers/`, `repositories/`). Within each domain there are layers, but they stay thin and explicit — no ports/adapters machinery.

Key constraints that shape every decision:
- **No ORM** — repositories are plain functions over `JdbcTemplate`, not abstracted behind interfaces
- **Schema-first API** — controllers implement generated interfaces; they are nearly mechanical
- **Framework-free domain logic** — entities and value objects are pure Kotlin; no Spring annotations, no JDBC inside them

### Backend package structure

```
backend/src/main/kotlin/com/simpletickr/
├── portfolio/
│   ├── Portfolio.kt                      # aggregate root, value objects
│   ├── PortfolioController.kt            # implements generated OpenAPI interface
│   ├── PortfolioRepository.kt            # raw SQL via JdbcTemplate
│   ├── CreatePortfolioUseCase.kt         # one class per write operation
│   ├── RecordTransactionUseCase.kt
│   └── TransactionRecorded.kt            # domain event (only where side effects exist)
├── position/
│   ├── Position.kt
│   ├── PositionController.kt
│   └── PositionRepository.kt
├── ticker/
│   ├── Ticker.kt
│   ├── TickerController.kt
│   ├── TickerRepository.kt
│   ├── UpdatePriceUseCase.kt
│   ├── PriceUpdated.kt                   # domain event
│   └── PriceProvider.kt                 # interface only if a test stub is actually needed
└── shared/
    └── Money.kt                          # value objects shared across domains
```

### Where business logic lives

- **Domain class** (`Portfolio.kt`, `Position.kt`, …) — all business rules; unit-testable with no Spring context
- **Use case** — one class per operation; takes a command (plain data class), fetches → applies domain logic → persists → returns; no business rules here
- **Repository** — SQL and row-mapping only; returns domain objects, not raw maps or DTOs
- **Controller** — HTTP translation only: deserialize input → build command → call use case → serialize output

If a method can't be unit-tested without Spring, it's in the wrong place.

### Domain events

Events are used selectively — only when a use case produces a side effect that is independent of the response. Use cases publish via Spring's `ApplicationEventPublisher`; handlers are separate `@EventListener` classes.

- **Use an event** when the side effect doesn't affect what the caller gets back (e.g. recalculating holdings after a transaction, updating portfolio value after a price change)
- **Use a direct call** when the caller needs the result immediately (e.g. creating a portfolio and returning it in the 201 response)

Everything does not go through the bus — only flows with real decoupling value.

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

GitHub Project board: https://github.com/orgs/simpleappslabs/projects/2 (project #2, owner `simpleappslabs`)
- Project ID: `PVT_kwDOCTgvzs4BVhCw`
- Status field ID: `PVTSSF_lADOCTgvzs4BVhCwzhQ8U5U`
- Status option IDs: Backlog `57f4641b` · Todo `1b978f2c` · In Progress `3a2722f3` · Done `acffd51b`

To move an issue on the board, add it first then edit its status:
```bash
gh project item-add 2 --owner simpleappslabs --url <issue-url>
gh project item-edit --project-id PVT_kwDOCTgvzs4BVhCw --id <item-id> \
  --field-id PVTSSF_lADOCTgvzs4BVhCwzhQ8U5U --single-select-option-id <option-id>
```

## Git

- Do **not** append `Co-Authored-By: Claude` trailers to commit messages

## Code style

- Kotlin: idiomatic, no nullable abuse, prefer data classes and sealed classes
- SQL: explicit column names, no `SELECT *`
- Svelte: component-per-feature, typed stores
- No comments that restate what the code does — only explain non-obvious intent