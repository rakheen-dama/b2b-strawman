# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Multi-tenant B2B SaaS starter with **schema-per-tenant isolation**. The reference implementation is **DocTeams** — a team-based document hub where organizations manage projects and upload documents. Monorepo with `frontend/`, `backend/`, `compose/`, and `infra/` directories.

**Current status**: Early scaffolding (Epic 1 complete). Most architecture is defined in docs but not yet implemented.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Next.js 16 (App Router), React 19, TypeScript 5, Tailwind CSS v4, Shadcn UI (new-york style) |
| Auth | Clerk (orgs, invitations, RBAC, webhooks) |
| Backend | Spring Boot 4.0.2, Java 25, Maven |
| ORM | Hibernate with schema-per-tenant multitenancy |
| Database | PostgreSQL 16 (Neon in prod, Docker locally) |
| Migrations | Flyway (global + per-tenant) |
| File Storage | AWS S3 (LocalStack locally) |
| IaC | Terraform |

## Build & Dev Commands

### Frontend (`frontend/`)
```bash
npm install              # Install dependencies
npm run dev              # Dev server on port 3000
npm run build            # Production build
npm run lint             # ESLint (flat config: eslint.config.mjs)
```

### Backend (`backend/`)
```bash
./mvnw spring-boot:run   # Dev server on port 8080
./mvnw clean package     # Build JAR
./mvnw test              # Run tests
```

### Local Services (`compose/`)
```bash
docker compose up -d     # Start Postgres (5432) + LocalStack (4566)
docker compose down      # Stop services
docker compose logs -f   # View logs
```

Compose `.env` uses: `POSTGRES_USER=postgres`, `POSTGRES_PASSWORD=changeme`, `POSTGRES_DB=app`. LocalStack auto-creates S3 bucket via `data/s3/init-s3.sh`.

## Architecture

### Multitenancy Model
- Single Postgres database, one schema per tenant (`tenant_<12-char-hex>`)
- `public` schema holds global tables: `organizations`, `org_schema_mapping`, `processed_webhooks`
- Tenant schemas hold: `projects`, `documents`
- Tenant resolved from JWT `organization_id` claim, mapped to schema via `org_schema_mapping`
- Hibernate sets `search_path` per connection checkout

### Authentication Flow
Clerk handles all auth. Next.js receives webhooks, verifies signatures via `@clerk/nextjs/webhooks`, then forwards provisioning requests to Spring Boot's `/internal/orgs/provision` (API-key secured).

JWT claims used: `sub` (user ID), `org_id`, `org_role` (`org:owner`, `org:admin`, `org:member`).

Role mapping: `org:owner` -> `ROLE_ORG_OWNER`, `org:admin` -> `ROLE_ORG_ADMIN`, `org:member` -> `ROLE_ORG_MEMBER`.

### Internal API Security
`/internal/*` endpoints secured by VPC isolation + `X-API-KEY` header. Never exposed through public ALB.

### Database Connections
Dual data sources: HikariCP (pooled, for app traffic via Neon PgBouncer) and direct connection (for Flyway DDL). `search_path` works in transaction-mode pooling because Hibernate wraps operations in transactions.

### File Upload/Download
Presigned S3 URLs. S3 key structure: `org/{orgId}/project/{projectId}/{documentId}`. Browser uploads directly to S3.

### Webhook Handling
Idempotent upserts with `svix-id` deduplication. `organization.created` triggers tenant provisioning. Membership/invitation events are no-op stubs for MVP.

## Frontend Conventions

- Path alias: `@/*` maps to project root
- Shadcn UI: new-york style, RSC-enabled, lucide icons, neutral base color
- Route groups: `(auth)/` for sign-in/up, `(marketing)/` for public pages, `(app)/` for authenticated routes
- Org-scoped routes under `app/(app)/org/[slug]/`
- API client in `lib/api.ts` attaches Bearer JWT to backend requests
- Clerk middleware handles route protection and org sync via `organizationSyncOptions`

## Backend Conventions

- Base package: `io.b2mash.b2b.b2bstrawman`
- Organized by feature: `config/`, `multitenancy/`, `security/`, `provisioning/`, `project/`, `document/`, `s3/`
- Flyway migrations: `db/migration/global/` (public schema), `db/migration/tenant/` (per-tenant schema)
- Spring profiles: local, dev, prod
- Tests use Testcontainers with PostgreSQL, Spring REST Docs

## Key Design Decisions

Refer to `ARCHITECTURE.md` for full ADRs. Key decisions:
- **ADR-001**: Hybrid webhook handler (Next.js verifies, Spring Boot provisions)
- **ADR-002**: VPC + API key for internal API security
- **ADR-003**: Org-based URL paths (`/org/[slug]/...`)
- **ADR-005**: Schema names `tenant_<12-hex-chars>` from deterministic hash of Clerk org ID
- **ADR-006**: Dual DB connections (pooled for app, direct for Flyway)
- **ADR-007**: Status-tracked provisioning with Resilience4j retry (maxAttempts=3, exponential backoff)

## Reference Documentation

- `ARCHITECTURE.md` — Full technical architecture with ADRs, component design, sequence diagrams
- `TASKS.md` — Epic breakdown with task dependencies and implementation order
- `multi-tenant-saas-starter-spec.md` — Functional requirements and API specs