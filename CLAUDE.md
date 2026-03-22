# CLAUDE.md

Multi-tenant B2B SaaS starter (DocTeams) with schema-per-tenant isolation.
Monorepo: `frontend/`, `backend/`, `compose/`, `infra/`.

## General Rules
When asked to create documents (architecture docs, implementation plans, TASKS.md, etc.), ONLY create the documents. Do NOT implement any code unless explicitly asked to do so.
When implementing epics, check TASKS.md and recent PRs to understand which parts have already been completed by other agents. Only implement the scope described — do not duplicate work from other epics/branches.

## Service-Specific Guides

- `frontend/CLAUDE.md` — Next.js 16, Keycloak, Shadcn conventions
- `backend/CLAUDE.md` — Spring Boot 4, Hibernate 7, multitenancy

Always read the relevant subdirectory CLAUDE.md before making changes.

## Tech Stack

| Layer | Technology |
|-------|------------|
| Frontend | Next.js 16 (App Router), React 19, TypeScript 5, Tailwind CSS v4, Shadcn UI |
| Auth | Keycloak (orgs, RBAC) with mock provider for E2E |
| Backend | Spring Boot 4.0.2, Java 25, Maven |
| Database | PostgreSQL 16 with schema-per-tenant multitenancy (Hibernate + Flyway) |
| File Storage | AWS S3 (LocalStack locally) |

## Local Dev Quick Start

```bash
# Start infrastructure (Postgres, LocalStack, Mailpit)
bash compose/scripts/dev-up.sh

# Frontend (from frontend/)
pnpm install && pnpm dev      # Port 3000

# Backend (from backend/)
./mvnw spring-boot:run        # Port 8080
./mvnw spring-boot:test-run   # Alternative: uses Testcontainers, no Docker Compose needed

# Stop infrastructure
bash compose/scripts/dev-down.sh          # Preserve data
bash compose/scripts/dev-down.sh --clean  # Wipe volumes

# Rebuild a specific service
bash compose/scripts/dev-rebuild.sh backend
```

## Git Worktrees 
When working in a git worktree, ALWAYS verify the correct working directory before writing any files. 
The worktree path is NOT inside the main repo directory. Before writing the first file, run `pwd` and confirm you 
are in the worktree directory, not the main repo. Never write files to the main repo when a worktree is active.

## Agent UI Navigation

### Keycloak Dev Stack (Primary)

Use the full Keycloak dev stack for Playwright E2E tests and agent UI navigation.

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Gateway | http://localhost:8443 |
| Backend | http://localhost:8080 |
| Keycloak | http://localhost:8180 (admin/admin) |
| Mailpit UI | http://localhost:8025 |

**Start/stop the stack:**
```bash
bash compose/scripts/dev-e2e-up.sh           # Start all services + bootstrap (3-5 min first time)
bash compose/scripts/dev-e2e-up.sh --clean   # Wipe volumes first, then start
bash compose/scripts/dev-e2e-down.sh         # Tear down + wipe data
```

**To authenticate in Playwright:**
1. Navigate to `http://localhost:8443/oauth2/authorization/keycloak`
2. Keycloak login page loads — fill email and password
3. Redirected through gateway back to `http://localhost:3000` — proceed with navigation

**Platform admin:** `padmin@docteams.local` / `password`

**Tailing logs:**
```bash
docker compose -f compose/docker-compose.yml logs -f backend   # Backend logs
docker compose -f compose/docker-compose.yml logs -f frontend  # Frontend logs
docker compose -f compose/docker-compose.yml logs -f keycloak  # Keycloak logs
```

**Database access:**
```bash
docker exec -it b2b-postgres psql -U postgres -d docteams
```

### Mock-Auth Stack (Deprecated Fallback)

The mock-auth stack is retained as a lightweight fallback for fast smoke tests without Keycloak. Do NOT target new test development at this stack.

| Service | URL |
|---------|-----|
| Frontend (mock auth) | http://localhost:3001 |
| Backend (e2e profile) | http://localhost:8081 |
| Mock IDP | http://localhost:8090 |
| Postgres | localhost:5433 (user: postgres, pass: changeme, db: app) |

**Start/stop the stack:**
```bash
bash compose/scripts/e2e-up.sh           # Build + start (takes ~3-5 min first time)
bash compose/scripts/e2e-down.sh         # Tear down + wipe data
bash compose/scripts/e2e-reseed.sh       # Reset data without rebuild
bash compose/scripts/e2e-rebuild.sh backend frontend  # Rebuild specific services
```

**To authenticate in Playwright:**
1. Navigate to http://localhost:3001/mock-login
2. Click "Sign In" (defaults to Alice, owner role)
3. Redirected to dashboard — proceed with navigation

**Available users:** Alice (owner), Bob (admin), Carol (member)
**Org slug:** e2e-test-org

**Working in a worktree:** Agents can use `isolation: "worktree"` on the Task tool.
The E2E stack builds from relative paths, so it picks up the worktree's source code automatically.

## Reference Docs

- `architecture/ARCHITECTURE.md` — Technical architecture, ADRs, sequence diagrams
- `TASKS.md` — Epic breakdown and implementation status
- `requirements/multi-tenant-saas-starter-spec.md` — Functional requirements and API specs
- `requirements/` — Phase requirement prompt files (claude-code-prompt-*.md)
- `architecture/` — Phase architecture docs (phase*-*.md)
