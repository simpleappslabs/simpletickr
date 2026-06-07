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

- **Domain-centric** structure — code grouped by business domain, not technical role; aggregates, entities, and value objects are pure Kotlin
- **Use-case pattern** — one class per write operation; controllers translate HTTP to commands, use cases do the work
- **Selective domain events** — Spring `ApplicationEventPublisher` for side effects that don't affect the immediate response; direct calls everywhere else
- **Framework-free domain logic** — entities and value objects are pure Kotlin, no Spring annotations

## Getting Started

> Prerequisites: JDK 21+, Node.js 20+, Docker

### 1. Start the database

```bash
cp .env.example .env
docker compose up -d
```

### 2. Run the backend

```bash
cd backend
./gradlew bootRun
```

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

## Development Process

This project is built with AI pair programming — see [AI.md](AI.md) for details.

## Development Workflow

1. Update `openapi.yaml` first (API contract drives everything)
2. Regenerate Spring interfaces and SvelteKit client
3. Implement backend logic, write migrations under `backend/src/main/resources/db/migration/`
4. Implement frontend features against the typed client