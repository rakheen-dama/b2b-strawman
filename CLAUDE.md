# CLAUDE.md

Multi-tenant B2B SaaS starter (Kazi product, b2mash company) with schema-per-tenant isolation.
Monorepo (pnpm workspace, one root lockfile): `frontend/`, `portal/`, `docs/`, `packages/` (`@b2mash/ui`, `@b2mash/shared`), `backend/`, `compose/`, `infra/`.

## Quality Gates — NON-NEGOTIABLE

These rules apply to **every** PR landing on `main`. They override anything in skill files, agent prompts, or status reports that conflicts. Loopholes are not allowed.

### 1. Build & test bar (mandatory, observed not inferred)

Before any PR can be merged to `main`:

- **Backend changes** → `./mvnw verify` runs **clean** (full suite, not `-Dtest='*Foo*'`). Targeted tests are for inner-loop iteration only. The merge bar is a clean full verify.
- **Frontend changes** → `pnpm lint && pnpm build && pnpm test` all green. Same for portal.
- **Both** → both, run sequentially.
- **Pre-existing baseline failures are not free passes.** If a test was failing before this PR, the PR description must explain the triage decision (own it, ignore with reason, or fix). Do not paper over.

### 2. Review bar

- Every agent-authored PR gets a **review pass** (CodeRabbit if available, otherwise a review subagent or human eyeball) before merge. The review must independently re-verify scenario amendments and substantive claims, not rubber-stamp.
- Reviewer must read the **diff** and the **changed files** in context, not just the PR description.

### 3. PASS means observed

- "PASS" means the agent **ran** the verification end-to-end (browser interaction → backend log → Mailpit / DB / file artefact). Inferring PASS from "code looks right" or "unit test green" is forbidden.
- "PASS-with-note: long-running, deferred" is **not PASS**. Mark it `DEFERRED` and finish later. Don't claim a status you didn't earn.
- "MERGED-AWAITING-VERIFY" is the correct status for code-merged-but-behaviour-unverified. Use it, don't conflate with VERIFIED.

### 4. Reproduce-before-fix

Agents must reproduce a bug locally before writing the fix. Diagnostic-by-spec ("the spec says it's at file:line, fix it") is forbidden — bugs have rendered wrong from the wrong subtree more than once. If you can't reproduce, the spec is wrong; report up, don't fix-and-pray.

### 5. Test scoping

- Targeted tests must include the failing test's package AND any package that imports the changed class. Don't `-Dtest='*Foo*'` and assume coverage.
- A fix that changes production behaviour MUST run the full backend `./mvnw verify` before claiming green. The OBS-2102 → OBS-2108 cascade happened because the dev only ran tests in the same package; the broken test was elsewhere.
- Frontend: full vitest, not narrowed by file path.

### 6. Scenario amendments require explicit authorization

- Editing `qa/testplan/demos/*.md` to make a scenario "match the product" is a **product decision**, not an agent decision. It must be flagged, justified, and authorized — not auto-applied to dispose of a bug.
- Re-classifying a "bug" as "scenario mismatch" requires evidence (file:line that proves intentional design, not just "the product does it differently").
- WONT_FIX is reserved for: explicit mandate exemptions (KYC, Payments), feature gaps that are documented as such, or genuine duplicate findings. Not for "agent doesn't want to investigate."

### 7. Scope discipline

- One fix per PR. If scope expands during a fix ("while I was here, this related bug…"), **stop and re-spec**. Don't bundle.
- Exception: same-bug-class clusters (e.g. 3 dialogs with identical defect) may ship in one PR if explicitly authorized.
- Architectural decisions (which fix option, what test pattern, where to put the new component) are orchestrator/user calls, not agent calls.

### 8. Status reports are drafts, not truth

- Cycle reports are agent narratives. The orchestrator/user must **independently re-run** any green-checkmark before trusting it.
- "ALL_GREEN" / "ALL_DAYS_COMPLETE" claims must be evidence-backed (paths to logs, screenshots, Mailpit message IDs). Without evidence, the marker doesn't apply.
- Don't trust the status doc. Trust `./mvnw verify`, the browser, the database, the email server.

### 9. Pride and honesty

- Agents must report failure honestly. "Stream timed out, here's what got done" is correct. "PASS-with-note: regression failures are pre-existing and unrelated" without proof is dishonest.
- Don't look for loopholes. Don't narrate around the rules. If a rule blocks you, raise it; don't bypass.
- Quality is king. This is not a race. A slow correct fix beats five fast broken ones.

### 10. The merge-gate hook is enforcement, not advice

`.claude/hooks/pre-pr-merge-gate.sh` blocks `gh pr merge` against `main` unless the gate is satisfied. Do not bypass with `--admin`, `--no-verify`, or by editing the hook out. If the hook is wrong, fix the hook upstream and explain why.

---

## General Rules

- When asked to create documents (architecture docs, implementation plans, TASKS.md, etc.), ONLY create the documents. Do NOT implement any code unless explicitly asked to do so.
- When implementing epics, check TASKS.md and recent PRs to understand which parts have already been completed by other agents. Only implement the scope described — do not duplicate work from other epics/branches.
- Read the relevant subdirectory CLAUDE.md (`backend/CLAUDE.md`, `frontend/CLAUDE.md`) before changing anything in that area.

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
# Start infrastructure (Postgres, LocalStack, Mailpit, Keycloak)
bash compose/scripts/dev-up.sh

# Frontend (from frontend/)
pnpm install && pnpm dev      # Port 3000

# Backend (from backend/)
./mvnw spring-boot:run        # Port 8080
./mvnw spring-boot:test-run   # Alternative: uses embedded Postgres, no Docker needed

# Stop infrastructure
bash compose/scripts/dev-down.sh          # Preserve data
bash compose/scripts/dev-down.sh --clean  # Wipe volumes

# Rebuild a specific service
bash compose/scripts/dev-rebuild.sh backend
```

## Agent Service Management (Keycloak Mode)

Agents use `compose/scripts/svc.sh` to start/stop/restart services in Keycloak mode.
Services run in the background with PID tracking, health-check waits, and logs in `.svc/logs/`.

```bash
bash compose/scripts/svc.sh start all              # Start backend, gateway, frontend, portal
bash compose/scripts/svc.sh restart backend         # Restart just backend (after Java changes)
bash compose/scripts/svc.sh stop frontend portal    # Stop specific services
bash compose/scripts/svc.sh status                  # Health check all services
bash compose/scripts/svc.sh logs backend            # Last 50 lines of backend log
```

| Service | Port | Health Check | Start Command (managed by svc.sh) |
|---------|------|-------------|-----------------------------------|
| Backend | 8080 | /actuator/health | `SPRING_PROFILES_ACTIVE=local,keycloak ./mvnw spring-boot:run` |
| Gateway | 8443 | /actuator/health | `./mvnw spring-boot:run` |
| Frontend | 3000 | / | `NEXT_PUBLIC_AUTH_MODE=keycloak pnpm dev` |
| Portal | 3002 | / | `pnpm dev` |

**When to restart**: Backend/Gateway need restart after Java source changes (no hot-reload).
Frontend/Portal use HMR — TypeScript changes are picked up automatically.

## Git Worktrees 
When working in a git worktree, ALWAYS verify the correct working directory before writing any files. 
The worktree path is NOT inside the main repo directory. Before writing the first file, run `pwd` and confirm you 
are in the worktree directory, not the main repo. Never write files to the main repo when a worktree is active.

## Agent UI Navigation (Mock Auth)

Production uses Keycloak — agents cannot authenticate on port 3000.
Use the E2E mock-auth stack on port 3001 instead.

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
