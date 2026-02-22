# Agent E2E Navigation — Status & Remaining Work

> Tracks the effort to make the mock-auth E2E stack available to agents using Playwright MCP.

## Goal

Enable Claude Code agents to navigate the DocTeams UI via Playwright MCP without hitting Clerk's CAPTCHA. Uses the Phase 20 E2E stack (mock IDP, mock login page, Docker Compose) on alternate ports.

## What Was Done

### Scripts Created

| File | Purpose |
|------|---------|
| `compose/scripts/start-mock-dev.sh` | Builds & starts the full E2E Docker stack from current source, waits for health checks, prints connection summary |
| `compose/scripts/stop-mock-dev.sh` | Tears down stack + wipes volumes (ephemeral) |
| `compose/scripts/reseed-mock-dev.sh` | Re-runs the seed container without rebuilding images |
| `compose/scripts/check-playwright-port.sh` | PreToolUse hook — blocks agent navigation to port 3000, suggests port 3001 |

### Configuration Changes

| File | Change |
|------|--------|
| `CLAUDE.md` | Added "Agent UI Navigation (Mock Auth)" section with full instructions |
| `.claude/settings.json` | Registered `PreToolUse` hook on `mcp__playwright__browser_navigate` |

### Bug Fixes Applied

| File | Fix |
|------|-----|
| `backend/Dockerfile` | Changed from `extract --layers` + `JarLauncher` entrypoint to `extract` + `java -jar` — Spring Boot 4 layered extraction format changed |
| `backend/src/main/resources/application-e2e.yml` | Added `integration.encryption-key` — new feature (Phase 21/22) added after E2E profile was created |
| `compose/mock-idp/src/index.ts` | Added CORS headers (`Access-Control-Allow-Origin: *`) — browser fetch from `localhost:3001` to `localhost:8090` was blocked |
| `compose/docker-compose.e2e.yml` | Fixed frontend health check: `localhost` → `127.0.0.1` — Alpine container doesn't resolve `localhost` |
| `frontend/app/(app)/create-org/page.tsx` | Added mock mode guard — `<CreateOrganization>` from Clerk crashes SSG when `ClerkProvider` is absent |

## What Works

- `bash compose/scripts/start-mock-dev.sh` builds and starts all 6 containers
- Mock login page renders at `http://localhost:3001/mock-login`
- User picker shows Alice (owner), Bob (admin), Carol (member)
- Clicking "Sign In" fetches JWT from mock IDP, sets `mock-auth-token` cookie, redirects to `/org/e2e-test-org/dashboard`
- Backend starts successfully with `e2e` profile, connects to isolated Postgres on port 5433
- Seed script provisions org, syncs 3 members, creates customer (stops at lifecycle transition)
- PreToolUse hook blocks agent navigation to port 3000 with helpful message
- All health checks pass: backend (8081), frontend (3001), mock IDP (8090)

## What Does NOT Work — Remaining Issues

### 1. BLOCKING: Clerk middleware throws in mock mode (production build)

**Symptom**: After successful mock login + redirect, all app pages (`/dashboard`, `/projects`, etc.) crash with:
```
Error: Clerk: auth() was called but Clerk can't detect usage of clerkMiddleware()
```

**Root Cause**: The Next.js production standalone build (used in Docker) still has Clerk's `auth()` function being invoked somewhere in the middleware/server-component chain. In dev mode (`pnpm dev`), the middleware dispatch in `proxy.ts` → `lib/auth/middleware.ts` correctly routes to mock middleware. But in the production build, the Clerk SDK's internal `auth()` check fires before the mock provider can intercept.

**Investigation Starting Points**:
- `frontend/proxy.ts` — The Next.js middleware entry point. Uses `createAuthMiddleware()` from `lib/auth/middleware.ts`
- `frontend/lib/auth/middleware.ts` — Dispatches between `clerkMiddleware()` and mock middleware based on `AUTH_MODE`
- `frontend/lib/auth/server.ts` — The `getAuthContext()` dispatch hub. Uses `AUTH_MODE` to choose provider
- `frontend/lib/auth/providers/clerk.ts` — Clerk implementation calls `auth()` from `@clerk/nextjs/server`

**Likely Fix**: The mock middleware in `lib/auth/middleware.ts` needs to fully suppress Clerk. Currently it may not be intercepting all routes, or the Clerk SDK's internal auth detection runs regardless of middleware. Options:
1. Ensure `clerkMiddleware()` is never imported/registered when `AUTH_MODE=mock` (tree-shaking at build time)
2. The mock middleware must set whatever internal state Clerk's `auth()` checks for (unlikely — Clerk internals)
3. Verify that `getAuthContext()` in `lib/auth/server.ts` is the ONLY path calling Clerk's `auth()` — search for any remaining direct `@clerk/nextjs/server` imports in app pages

**Test**: After fixing, run `bash compose/scripts/start-mock-dev.sh` and navigate to `http://localhost:3001/org/e2e-test-org/projects` — should render the projects page.

### 2. Seed: Customer lifecycle transition fails

**Symptom**: `compose/seed/seed.sh` step 6 fails:
```
[FAIL] Transition to ACTIVE FAILED (HTTP 400)
```

**Root Cause**: Transitioning ONBOARDING → ACTIVE requires completing all checklist items (`CustomerLifecycleGuard`). In a fresh tenant, the pack seeder creates checklist templates during provisioning, so there ARE checklist items to complete.

**Fix Options**:
1. Add a step to `seed.sh` that calls `GET /api/customers/{id}/checklists` and then `POST /api/customers/{id}/checklists/{itemId}/complete` for each item
2. Or skip the ACTIVE transition in seed — the customer in ONBOARDING state is sufficient for basic navigation testing

### 3. Frontend health check timing

**Symptom**: Occasionally the frontend health check times out during initial startup even though the server is running. This causes the seed container to not start.

**Root Cause**: The `start_period: 30s` in `docker-compose.e2e.yml` may not be enough on slower machines. The frontend takes ~171ms to start but Docker's health check interval adds latency.

**Fix**: Increase `start_period` to `60s` for the frontend service in `docker-compose.e2e.yml`.

## Port Mapping Reference

| Service | Container | Host Port | Internal Port |
|---------|-----------|-----------|---------------|
| Frontend (mock auth) | e2e-frontend | 3001 | 3000 |
| Backend (e2e profile) | e2e-backend | 8081 | 8080 |
| Mock IDP | e2e-mock-idp | 8090 | 8090 |
| Postgres | e2e-postgres | 5433 | 5432 |
| LocalStack | e2e-localstack | 4567 | 4566 |

## Architecture References

- `architecture/phase20-e2e-auth-abstraction.md` — Full E2E auth abstraction design
- `tasks/phase20-e2e-auth-abstraction.md` — Phase 20 epic/task breakdown (all marked Done)
- `adr/ADR-085-auth-provider-abstraction.md` — Auth provider abstraction decision
- `adr/ADR-086-mock-idp-strategy.md` — Mock IDP strategy
- `adr/ADR-087-e2e-seed-strategy.md` — Seed data strategy
