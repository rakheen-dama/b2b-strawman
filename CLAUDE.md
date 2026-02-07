# CLAUDE.md

Multi-tenant B2B SaaS starter (DocTeams) with schema-per-tenant isolation.
Monorepo: `frontend/`, `backend/`, `compose/`, `infra/`.

## General Rules
When asked to create documents (architecture docs, implementation plans, TASKS.md, etc.), ONLY create the documents. Do NOT implement any code unless explicitly asked to do so.
When implementing epics, check TASKS.md and recent PRs to understand which parts have already been completed by other agents. Only implement the scope described — do not duplicate work from other epics/branches.

## Service-Specific Guides

- `frontend/CLAUDE.md` — Next.js 16, Clerk, Shadcn conventions
- `backend/CLAUDE.md` — Spring Boot 4, Hibernate 7, multitenancy

Always read the relevant subdirectory CLAUDE.md before making changes.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Next.js 16 (App Router), React 19, TypeScript 5, Tailwind CSS v4, Shadcn UI |
| Auth | Clerk (orgs, invitations, RBAC, webhooks) |
| Backend | Spring Boot 4.0.2, Java 25, Maven |
| Database | PostgreSQL 16 with schema-per-tenant multitenancy (Hibernate + Flyway) |
| File Storage | AWS S3 (LocalStack locally) |

## Local Dev Quick Start

```bash
# Services (from compose/)
docker compose up -d          # Postgres (5432) + LocalStack (4566)

# Frontend (from frontend/)
pnpm install && pnpm dev      # Port 3000

# Backend (from backend/)
./mvnw spring-boot:run        # Port 8080
./mvnw spring-boot:test-run   # Alternative: uses Testcontainers, no Docker Compose needed
```

## Reference Docs

- `ARCHITECTURE.md` — Technical architecture, ADRs, sequence diagrams
- `TASKS.md` — Epic breakdown and implementation status
- `multi-tenant-saas-starter-spec.md` — Functional requirements and API specs
