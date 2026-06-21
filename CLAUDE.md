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
task frontend:test:e2e   # run Playwright E2E tests (starts isolated test DB automatically; backend must already be running)
task ci                  # api:lint + backend:build + frontend pipeline in parallel (no E2E)

# Run a single backend test class:
cd backend && ./gradlew test --tests "com.simpletickr.transaction.TransactionRepositoryTest"

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
| Use case | **Write side (commands)**: validate → orchestrate → persist. One class per named operation. |
| Service | **Read side (queries)**: compute, assemble, and return projections. No side effects. |
| Scheduled job | Thin `@Component` with a single `@Scheduled` method; delegates immediately to a use case |
| Controller | HTTP translation only: deserialize → call service or use case → serialize |

**Controllers talk only to services and use cases — never directly to repositories.**

Use cases exist even when the logic is simple today, because they provide a single place to add validation, duplicate checks, audit logging, and authorization later.

`FxRateService.lookupOrFetch()` is the one exception: it may write to the DB as a lazy cache-fill, but callers treat it as a query. The write is an implementation detail, not a command.

If a business rule lives in a service or use case, it belongs in the domain class instead. If a method can't be unit-tested without Spring, it's in the wrong place.

### Backend package structure

```
backend/src/main/kotlin/com/simpletickr/
├── portfolio/
│   ├── Portfolio.kt                  # aggregate root — enforces cross-transaction invariants
│   ├── Holding.kt                    # computed read-model (no DB table)
│   ├── HoldingWithValuation.kt       # read-model with current price and FX-normalised value
│   ├── CurrencyTotal.kt              # read-model: total portfolio value per currency
│   ├── PortfolioController.kt        # implements generated OpenAPI interface
│   ├── PortfolioRepository.kt        # raw SQL via JdbcTemplate
│   ├── HoldingRepository.kt          # SQL aggregation for the holdings read-model
│   ├── HoldingService.kt             # fetches holdings with valuation and FX normalisation
│   ├── ValuationService.kt           # looks up latest prices and converts to portfolio currency
│   ├── RealizedGainsCalculator.kt    # pure domain logic for FIFO/AVCO gain calculation
│   ├── RealizedGainsReport.kt
│   ├── RealizedGainEntry.kt
│   └── RealizationMethod.kt          # enum: FIFO | AVCO
├── asset/
│   ├── Asset.kt
│   ├── Listing.kt                    # exchange listing (ticker + exchange) for an asset
│   ├── AssetService.kt               # read: listAssets, getAsset (with price mappings)
│   ├── CreateAssetUseCase.kt         # write: create asset + listings + price mappings (transactional)
│   ├── UpdateAssetUseCase.kt
│   ├── DeleteAssetUseCase.kt
│   ├── CreateListingUseCase.kt
│   ├── UpdateListingUseCase.kt
│   ├── DeleteListingUseCase.kt
│   ├── AssetController.kt
│   ├── AssetRepository.kt
│   └── ListingRepository.kt
├── transaction/
│   ├── Transaction.kt
│   ├── TransactionCommands.kt        # command data classes (RecordCommand, AmendCommand, …)
│   ├── RecordTransactionUseCase.kt   # validates + persists a new transaction
│   ├── AmendTransactionUseCase.kt
│   ├── DeleteTransactionUseCase.kt
│   ├── TransactionController.kt      # implements generated OpenAPI interface
│   └── TransactionRepository.kt      # raw SQL; also owns net-quantity queries
├── price/
│   ├── PricePoint.kt
│   ├── PriceProvider.kt              # interface implemented by Yahoo Finance provider
│   ├── PriceProviderMapping.kt       # maps an asset listing to a provider symbol
│   ├── PriceProviderMappingRepository.kt
│   ├── AssetPriceHistoryRepository.kt
│   ├── PriceQueryService.kt          # read: listMappings, getMapping, getPriceHistory
│   ├── SyncPricesUseCase.kt          # write: fetch + upsert price history for all mappings
│   ├── SetPriceMappingUseCase.kt
│   ├── DeletePriceMappingUseCase.kt
│   ├── PriceSyncJob.kt               # @Scheduled — delegates to SyncPricesUseCase
│   ├── PriceController.kt
│   └── YahooFinancePriceProvider.kt
├── fx/
│   ├── FxRate.kt
│   ├── FxRateSource.kt               # enum: e.g. YAHOO_FINANCE
│   ├── FxRateProvider.kt             # interface for FX rate sources
│   ├── FxRateRepository.kt
│   ├── FxRateService.kt              # read: lookupOrFetch (lazy cache — may write as side effect)
│   ├── SyncFxRatesUseCase.kt         # write: fetch + upsert FX rates for all currency pairs
│   ├── FxRateSyncJob.kt              # @Scheduled — delegates to SyncFxRatesUseCase
│   ├── FxController.kt
│   └── YahooFinanceFxRateProvider.kt
├── settings/
│   ├── UserSettings.kt               # e.g. base currency, price sync window
│   ├── UserSettingsRepository.kt
│   └── SettingsController.kt
├── health/
│   └── HealthController.kt
└── shared/
    ├── CurrencyCode.kt               # value type wrapping ISO 4217 currency codes
    ├── GlobalExceptionHandler.kt
    ├── E2eTestConfig.kt              # bean overrides active only under the e2e Spring profile
    └── WebConfig.kt                  # CORS — allows localhost:5173 and localhost:4173
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
- Frontend components follow the **container/presentational** pattern:
  - *Presentational* components (e.g. `TransactionsTable`, `HoldingsTable`) receive data as props and emit events via callbacks — no API calls, no modal state
  - *Container* components (e.g. `TransactionsSection`) own state, call the API, and render presentational children alongside their modals — exposed to the page via a single `onchange` callback
  - Pages are thin: they fetch initial data, own page-level state (portfolio, holdings), and compose containers
- No comments that restate what the code does — only explain non-obvious intent
