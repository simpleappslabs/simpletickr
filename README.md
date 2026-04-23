# simpletickr

A simple portfolio tracker for ETFs, crypto, and other assets.

## Stack

### Backend
- **Kotlin / Spring Boot** — REST API
- **Spring JDBC** — raw SQL, no ORM
- **Flyway** — schema migrations
- **SpringDoc OpenAPI** — generates `openapi.yaml`
- **PostgreSQL**

### Frontend
- **SvelteKit**
- **Hey API** — typed client generated from `openapi.yaml`
- **Chart.js** — graphs

### API Contract
- `openapi.yaml` is the source of truth (schema-first)
- Generates Spring controller interfaces (backend)
- Generates typed API client (frontend)

## Repository Layout

```
simpletickr/
├── backend/          # Spring Boot application
├── frontend/         # SvelteKit application
├── openapi.yaml      # API contract (source of truth)
└── .tasks/           # Task tracking
```

## Architecture

- **Domain-first** monorepo structure
- **Light DDD** — entities, value objects, aggregates
- **Hexagonal instincts** — domain logic stays clean and framework-agnostic

## Getting Started

> Prerequisites: JDK 21+, Node.js 20+, PostgreSQL, Docker (optional)

```bash
# Backend
cd backend
./gradlew bootRun

# Frontend
cd frontend
npm install
npm run dev
```

## Development Workflow

1. Update `openapi.yaml` first (API contract drives everything)
2. Regenerate Spring interfaces and SvelteKit client
3. Implement backend logic, write migrations under `backend/src/main/resources/db/migration/`
4. Implement frontend features against the typed client