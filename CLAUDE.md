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
└── openapi.yaml      # API contract — source of truth
```

## Tech stack

### Backend (`backend/`)
- Kotlin, Spring Boot 3.5
- Spring Web (REST controllers)
- Spring JDBC (raw SQL — no ORM, no JPA)
- Flyway (migrations in `backend/src/main/resources/db/migration/`)
- SpringDoc OpenAPI
- PostgreSQL

### Frontend (`frontend/`)
- SvelteKit + Svelte 5 (runes: `$state`, `$derived`, `$props`)
- TypeScript
- Tailwind CSS v4 + DaisyUI v5 (custom theme in `src/app.css`)
- Hey API (typed client generated from `openapi.yaml`, output to `src/lib/api/` — gitignored)
- Chart.js
- Playwright (E2E tests in `tests/*.e2e.ts`)

### API Contract
- `openapi.yaml` is schema-first and the single source of truth
- Backend: generates Spring controller interfaces via OpenAPI Generator
- Frontend: generates typed client via Hey API (`npm run codegen`)

## Architecture

### Style: domain-centric layered, not hexagonal

Code is grouped by domain (e.g. `portfolio`, `asset`, `transaction`), not by technical role. Within each domain there are layers, but they stay thin and explicit — no ports/adapters machinery.

Key constraints that shape every decision:
- **No ORM** — repositories are plain functions over `JdbcTemplate`, not abstracted behind interfaces
- **Schema-first API** — controllers implement generated interfaces; they are nearly mechanical
- **Framework-free domain logic** — entities and value objects are pure Kotlin; no Spring annotations, no JDBC inside them

### Backend package structure

```
backend/src/main/kotlin/com/simpletickr/
├── portfolio/
│   ├── Portfolio.kt               # domain model
│   ├── Holding.kt                 # computed read-model (no DB table)
│   ├── PortfolioController.kt     # implements generated OpenAPI interface
│   ├── PortfolioRepository.kt     # raw SQL via JdbcTemplate
│   └── HoldingRepository.kt      # SQL aggregation for holdings
├── asset/
│   ├── Asset.kt
│   ├── AssetController.kt
│   └── AssetRepository.kt
├── transaction/
│   ├── Transaction.kt
│   ├── TransactionController.kt
│   └── TransactionRepository.kt
├── health/
│   └── HealthController.kt
└── shared/
    └── WebConfig.kt               # CORS — allows localhost:5173 and localhost:4173
```

### Where business logic lives

- **Domain class** — all business rules; unit-testable with no Spring context
- **Repository** — SQL and row-mapping only; returns domain objects, not raw maps or DTOs
- **Controller** — HTTP translation only: deserialize input → call repository → serialize output

If a method can't be unit-tested without Spring, it's in the wrong place.

### Frontend route structure

```
frontend/src/routes/
├── +layout.svelte        # navbar, global CSS import
├── +page.svelte          # / — portfolio list + create form
└── assets/
    └── +page.svelte      # /assets — asset browser + add form
```

API client is initialized in `src/lib/client.ts` (reads `PUBLIC_API_BASE_URL` from env).
Generated SDK is imported from `$lib/api/sdk.gen`.

## Testing

### Backend
- **Repository tests** — `@JdbcTest` + Testcontainers (`@ServiceConnection`) — one per repository
- **Controller tests** — `@WebMvcTest` + `@MockitoBean` — no DB, tests HTTP layer only

### Frontend
- **E2E tests** — Playwright, `tests/*.e2e.ts`, run against preview server + real backend
- Run locally: `task frontend:test:e2e` (requires backend on `localhost:8080`)
- Run in CI: `e2e` job (after `backend` and `frontend` jobs pass)

## CI

Three jobs in `.github/workflows/ci.yml`:
- `backend` — `./gradlew build` (compile + all tests)
- `frontend` — `npm run codegen && npm run build`
- `e2e` — needs both above; spins up PostgreSQL, boots backend, runs Playwright

## Workflow

1. Define or update the API contract in `openapi.yaml`
2. Regenerate backend interfaces (`./gradlew build`) and frontend client (`npm run codegen`)
3. Implement backend: domain model → repository → controller
4. Write Flyway migrations for any schema changes
5. Implement frontend against the generated typed client
6. Add/update E2E tests for new flows

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
- Do **not** commit automatically — always ask before committing

## Code style

- Kotlin: idiomatic, no nullable abuse, prefer data classes and sealed classes
- SQL: explicit column names, no `SELECT *`
- Svelte: Svelte 5 runes (`$state`, `$derived`), component-per-feature, no inline `<style>` blocks — use Tailwind/DaisyUI classes
- No comments that restate what the code does — only explain non-obvious intent
