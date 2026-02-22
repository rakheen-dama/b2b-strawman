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

## Git Worktrees 
When working in a git worktree, ALWAYS verify the correct working directory before writing any files. 
The worktree path is NOT inside the main repo directory. Before writing the first file, run `pwd` and confirm you 
are in the worktree directory, not the main repo. Never write files to the main repo when a worktree is active.

## Agent UI Navigation (Mock Auth)

Clerk uses CAPTCHA — agents cannot authenticate on port 3000.
Use the E2E mock-auth stack on port 3001 instead.

| Service | URL |
|---------|-----|
| Frontend (mock auth) | http://localhost:3001 |
| Backend (e2e profile) | http://localhost:8081 |
| Mock IDP | http://localhost:8090 |
| Postgres | localhost:5433 (user: postgres, pass: changeme, db: app) |

**Start/stop the stack:**
```bash
bash compose/scripts/start-mock-dev.sh   # Build + start (takes ~3-5 min first time)
bash compose/scripts/stop-mock-dev.sh    # Tear down + wipe data
bash compose/scripts/reseed-mock-dev.sh  # Reset data without rebuild
```

**To authenticate in Playwright:**
1. Navigate to http://localhost:3001/mock-login
2. Click "Sign In" (defaults to Alice, owner role)
3. Redirected to dashboard — proceed with navigation

**Available users:** Alice (owner), Bob (admin), Carol (member)
**Org slug:** e2e-test-org

**Tailing logs:**
```bash
docker compose -f compose/docker-compose.e2e.yml logs -f backend   # Backend logs
docker compose -f compose/docker-compose.e2e.yml logs -f frontend  # Frontend logs
```

**Database access:**
```bash
docker exec -it e2e-postgres psql -U postgres -d app
```

**Working in a worktree:** Agents can use `isolation: "worktree"` on the Task tool.
The E2E stack builds from relative paths, so it picks up the worktree's source code automatically.

## Reference Docs

- `architecture/ARCHITECTURE.md` — Technical architecture, ADRs, sequence diagrams
- `TASKS.md` — Epic breakdown and implementation status
- `requirements/multi-tenant-saas-starter-spec.md` — Functional requirements and API specs
- `requirements/` — Phase requirement prompt files (claude-code-prompt-*.md)
- `architecture/` — Phase architecture docs (phase*-*.md)
