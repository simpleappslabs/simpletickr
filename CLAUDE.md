# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

**simpletickr** is a simple portfolio tracker (ETFs, crypto, stocks, etc.).
GitHub: https://github.com/simpleappslabs/simpletickr

## Commands

```bash
# Local dev
task up                  # start PostgreSQL via Docker Compose
task backend:run         # start backend on localhost:8080
task frontend:dev        # start frontend dev server on localhost:5173

# Tests
task backend:test        # run all backend tests (Testcontainers, no running DB needed)
task frontend:test:e2e   # run Playwright E2E tests (requires backend on localhost:8080)
task ci                  # api:lint + backend:build + frontend pipeline in parallel (no E2E)

# Run a single backend test class:
cd backend && ./gradlew test --tests "com.simpletickr.transaction.TransactionServiceTest"

# Type-check frontend:
cd frontend && npm run check

# Regenerate after openapi.yaml changes:
task backend:build       # recompiles and regenerates backend sources
task frontend:codegen    # regenerates typed API client
```

## Architecture

### Domain model: rich, not anemic

Code is grouped by domain (`portfolio`, `asset`, `transaction`), not by technical role. Domain classes own business rules and invariants — they carry behavior, not just data. Services coordinate data access and persistence; they do not own business logic.

Key constraints that shape every decision:
- **No ORM** — repositories are plain functions over `JdbcTemplate`, not abstracted behind interfaces
- **Schema-first API** — controllers implement generated interfaces; they are nearly mechanical
- **Framework-free domain logic** — domain classes are pure Kotlin; no Spring annotations, no JDBC inside them

**Portfolio is the aggregate root.** Transactions are child entities within the Portfolio aggregate. Business rules that span transactions (e.g. "can't sell more than you hold") are enforced by `Portfolio`, not by a service. The service fetches the data the domain method needs, passes it in, then persists the result.

`Asset` is a separate aggregate — reference data that exists independently of any portfolio. `Holding` is a pure read model (no DB table, computed from transactions). It has no lifecycle and is never persisted.

### Layer responsibilities

| Layer | Responsibility |
|---|---|
| Domain class | Business rules and invariants — pure Kotlin, unit-testable with no framework |
| Repository | SQL and row-mapping only; returns domain objects |
| Service | Fetches data → calls domain method → persists result; does not own rules |
| Controller | HTTP translation only: deserialize → call service → serialize |

If a business rule lives in a service, it belongs in the domain class instead. If a method can't be unit-tested without Spring, it's in the wrong place.

### Backend package structure

```
backend/src/main/kotlin/com/simpletickr/
├── portfolio/
│   ├── Portfolio.kt               # aggregate root — enforces cross-transaction invariants
│   ├── Holding.kt                 # computed read-model (no DB table)
│   ├── PortfolioController.kt     # implements generated OpenAPI interface
│   ├── PortfolioRepository.kt     # raw SQL via JdbcTemplate
│   └── HoldingRepository.kt      # SQL aggregation for the holdings read-model
├── asset/
│   ├── Asset.kt
│   ├── AssetController.kt
│   └── AssetRepository.kt
├── transaction/
│   ├── Transaction.kt
│   ├── TransactionService.kt      # coordinates repositories; delegates rules to Portfolio
│   ├── TransactionController.kt   # implements generated OpenAPI interface
│   └── TransactionRepository.kt  # raw SQL; also owns net-quantity queries
├── health/
│   └── HealthController.kt
└── shared/
    └── WebConfig.kt               # CORS — allows localhost:5173 and localhost:4173
```

### Frontend route structure

```
frontend/src/routes/
├── +layout.svelte              # navbar, global CSS import
├── +page.svelte                # / — portfolio list
├── assets/
│   └── +page.svelte            # /assets — asset browser
└── portfolios/
    └── [id]/
        ├── +page.svelte        # portfolio detail — holdings, transactions, charts
        └── TransactionForm.svelte
```

Shared modal components live in `frontend/src/lib/`.
API client is initialized in `src/lib/client.ts` (reads `PUBLIC_API_BASE_URL` from env).
Generated SDK is imported from `$lib/api/sdk.gen`.

## Testing

### Backend
- **Service tests** — plain unit tests with Mockito mocks; no Spring context needed
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

## Backlog

Tracked as GitHub Issues on `simpleappslabs/simpletickr`.
Use `gh issue list` to see the current backlog.
Labels used: `feature`, `bug`, `chore`, `infra`, `frontend`, `backend`, `api`

GitHub Project board: https://github.com/orgs/simpleappslabs/projects/2 (project #2, owner `simpleappslabs`)
- Project ID: `PVT_kwDOCTgvzs4BVhCw`
- Status field ID: `PVTSSF_lADOCTgvzs4BVhCwzhQ8U5U`
- Status option IDs: Backlog `57f4641b` · Todo `1b978f2c` · In Progress `3a2722f3` · Done `acffd51b`

When logging a new issue, always add it to the project board in the **Backlog** status (`57f4641b`) immediately after creation:
```bash
ITEM_ID=$(gh project item-add 2 --owner simpleappslabs --url <issue-url> --format json | jq -r '.id')
gh project item-edit --project-id PVT_kwDOCTgvzs4BVhCw --id "$ITEM_ID" \
  --field-id PVTSSF_lADOCTgvzs4BVhCwzhQ8U5U --single-select-option-id 57f4641b
```

## Git

- Do **not** append `Co-Authored-By: Claude` trailers to commit messages
- Do **not** commit automatically — always ask before committing

## Code style

- Kotlin: idiomatic, no nullable abuse, prefer data classes and sealed classes
- SQL: explicit column names, no `SELECT *`
- Svelte: Svelte 5 runes (`$state`, `$derived`), component-per-feature, no inline `<style>` blocks — use Tailwind/DaisyUI classes
- No comments that restate what the code does — only explain non-obvious intent
