# B2B SaaS Starter (DocTeams)

Multi-tenant B2B SaaS starter with schema-per-tenant isolation. Reference implementation: **DocTeams** — a team-based document hub where organizations manage projects and upload documents.

## Prerequisites

- Node.js 20+ and pnpm (frontend)
- Java 25 (backend)
- Docker & Docker Compose (local services)
- Maven 3.9+ (backend build, or use included `./mvnw` wrapper)

## Quick Start (Native)

Run services natively for development with hot-reload:

1. **Start local services** (Postgres + LocalStack S3)
   ```bash
   cd compose
   cp .env.example .env   # Edit if needed
   docker compose up -d postgres localstack
   ```

2. **Start backend** (port 8080)
   ```bash
   cd backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```

3. **Start frontend** (port 3000)
   ```bash
   cd frontend
   cp .env.local.example .env.local  # Fill in Clerk keys
   pnpm install
   pnpm dev
   ```

## Quick Start (Full Docker Stack)

Run the entire stack in containers — no local Java or Node.js required:

1. **Configure environment**
   ```bash
   cd compose
   cp .env.example .env
   ```
   Edit `.env` and fill in your Clerk keys (`NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY`, `CLERK_SECRET_KEY`).

2. **Build and start all services**
   ```bash
   docker compose up -d --build
   ```
   This starts all 4 services:
   - **Postgres** on port 5432
   - **LocalStack S3** on port 4566
   - **Backend** (Spring Boot) on port 8080
   - **Frontend** (Next.js) on port 3000

3. **Verify services are running**
   ```bash
   docker compose ps
   docker compose logs -f backend   # Watch backend logs
   docker compose logs -f frontend  # Watch frontend logs
   ```

4. **Open the app** at [http://localhost:3000](http://localhost:3000)

5. **Stop all services**
   ```bash
   docker compose down           # Stop containers
   docker compose down -v        # Stop and remove volumes (reset data)
   ```

### Troubleshooting

- **Backend fails to start**: Check Postgres is healthy first (`docker compose ps`). The backend waits for Postgres to be ready but may need longer on first build.
- **Frontend shows auth errors**: Ensure Clerk keys are set in `compose/.env`. `NEXT_PUBLIC_CLERK_PUBLISHABLE_KEY` and `CLERK_SECRET_KEY` are required.
- **Rebuild after code changes**: `docker compose up -d --build` rebuilds images with your latest code.
- **View logs**: `docker compose logs -f <service>` where service is `postgres`, `localstack`, `backend`, or `frontend`.

## Project Structure

```
b2b-strawman/
├── frontend/    # Next.js 16, React 19, TypeScript 5, Tailwind v4, Shadcn UI
├── backend/     # Spring Boot 4.0.2, Java 25, Maven
├── compose/     # Docker Compose (Postgres 16, LocalStack S3)
├── infra/       # Terraform IaC
└── .github/     # CI/CD workflows
```

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Next.js 16 (App Router), React 19, TypeScript 5, Tailwind CSS v4, Shadcn UI |
| Auth | Clerk (orgs, invitations, RBAC, webhooks) |
| Backend | Spring Boot 4.0.2, Java 25, Maven |
| ORM | Hibernate with schema-per-tenant multitenancy |
| Database | PostgreSQL 16 (Neon in prod, Docker locally) |
| Migrations | Flyway (global + per-tenant) |
| File Storage | AWS S3 (LocalStack locally) |
| IaC | Terraform |

## Multitenancy Model

- Single Postgres database, one schema per tenant (`tenant_<12-hex-chars>`)
- Global tables in `public` schema: `organizations`, `org_schema_mapping`, `processed_webhooks`
- Tenant tables in each `tenant_*` schema: `projects`, `documents`
- Tenant resolved from Clerk JWT `org_id` claim via Hibernate connection provider

## Documentation

- [Architecture & ADRs](ARCHITECTURE.md)
- [Task Breakdown](TASKS.md)
- [Frontend Guide](frontend/CLAUDE.md)
- [Backend Guide](backend/CLAUDE.md)

## Environment Variables

See `frontend/.env.local.example` and `backend/src/main/resources/application-local.yml` for required configuration.
