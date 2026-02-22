# Fix: Clerk Middleware Crash in Mock Auth Production Build

## Context

We're enabling agents to navigate the DocTeams UI via Playwright MCP. A Docker Compose E2E stack runs on alternate ports (frontend:3001, backend:8081, mock-idp:8090) with `NEXT_PUBLIC_AUTH_MODE=mock` baked into the Next.js production build. The mock login page works — agents can authenticate as seeded users (Alice/Bob/Carol) and get a JWT cookie. But after login, every app page crashes.

## The Problem

After mock login redirects to `/org/e2e-test-org/dashboard`, the page throws:

```
Error: Clerk: auth() was called but Clerk can't detect usage of clerkMiddleware()
```

This happens on ALL authenticated pages (`/dashboard`, `/projects`, `/settings`, etc.). The mock login page itself (`/mock-login`) works fine because it's in the `(mock-auth)` route group which has its own layout without the app shell.

## Architecture Background

Phase 20 introduced an auth abstraction layer. Read these files to understand the full design:

- `tasks/agent-e2e-navigation.md` — Status doc with all findings, what works, what doesn't
- `architecture/phase20-e2e-auth-abstraction.md` — Full architecture (Sections 2.1-2.3 for frontend auth, Section 7 for migration)
- `tasks/phase20-e2e-auth-abstraction.md` — Epic breakdown showing all slices as Done (PRs #292-#301)

## Key Files to Investigate

**Middleware chain** (this is where the bug lives):
- `frontend/proxy.ts` — Next.js middleware entry point. Should delegate to `createAuthMiddleware()`
- `frontend/lib/auth/middleware.ts` — `createAuthMiddleware()` dispatches between Clerk and mock middleware based on `AUTH_MODE`

**Auth dispatch**:
- `frontend/lib/auth/server.ts` — `getAuthContext()`, `getAuthToken()` — provider dispatch hub
- `frontend/lib/auth/providers/clerk.ts` — Clerk implementation (calls `auth()` from `@clerk/nextjs/server`)
- `frontend/lib/auth/providers/mock/server.ts` — Mock implementation (reads JWT from cookie)

**App layout** (wraps all authenticated pages):
- `frontend/app/layout.tsx` — Root layout with `<AuthProvider>`
- `frontend/lib/auth/client/auth-provider.tsx` — Renders `ClerkProvider` or `MockAuthContextProvider` based on `AUTH_MODE`

## What's Happening

1. `NEXT_PUBLIC_AUTH_MODE=mock` is set as a Docker build arg — confirmed it's baked into the production build
2. `proxy.ts` calls `createAuthMiddleware()` which should return mock middleware when `AUTH_MODE=mock`
3. Mock middleware checks the `mock-auth-token` cookie and allows the request through
4. But then server components call `getAuthContext()` → dispatches to mock provider (correct)
5. HOWEVER, somewhere Clerk's `auth()` function is ALSO being called — likely from:
   - A leftover direct `@clerk/nextjs/server` import in an app page (the 44-file refactor in Epic 139 should have caught these, but verify)
   - Clerk's `@clerk/nextjs` package auto-detecting middleware and throwing because `clerkMiddleware()` wasn't the one that ran
   - The `<ClerkProvider>` being rendered even in mock mode (check `auth-provider.tsx` conditional)

## Investigation Steps

1. **Search for direct Clerk imports** — `grep -r "from \"@clerk/nextjs" frontend/app/ --include="*.ts" --include="*.tsx"` — anything beyond the 3 exempt files (webhook handler, webhook-handlers.ts, team/actions.ts) is a bug
2. **Read `proxy.ts`** and **`lib/auth/middleware.ts`** — trace the mock middleware path. Does it actually run? Does it properly suppress Clerk?
3. **Read `lib/auth/client/auth-provider.tsx`** — in mock mode, is `ClerkProvider` truly not rendered? If it IS rendered, it could trigger Clerk's auth detection
4. **Check if Clerk SDK auto-registers middleware** — Clerk's `@clerk/nextjs` package may have side effects that detect whether `clerkMiddleware()` ran. In mock mode, it didn't — hence the error
5. **Test locally** — Run `NEXT_PUBLIC_AUTH_MODE=mock pnpm dev --port 3001` from the frontend directory with mock IDP running (`docker compose -f compose/docker-compose.e2e.yml up -d mock-idp`). If dev mode works but Docker doesn't, the issue is specific to the production standalone build.

## Likely Fix Approaches

**A. Complete Clerk suppression in mock mode**: Ensure `clerkMiddleware()` is never imported when `AUTH_MODE=mock`. Dynamic imports or build-time dead code elimination via the `NEXT_PUBLIC_` prefix should handle this.

**B. Residual direct imports**: If any `app/` files still import directly from `@clerk/nextjs/server` (beyond the 3 exempt files), replace them with `@/lib/auth` imports.

**C. ClerkProvider leaking**: If `auth-provider.tsx` renders `<ClerkProvider>` in mock mode due to a bug in the conditional, Clerk's internal auth state machine activates and expects `clerkMiddleware()`.

## Secondary Issues (fix if time permits)

1. **Seed customer lifecycle** — `compose/seed/seed.sh` step 6 fails transitioning ONBOARDING → ACTIVE (needs checklist completion). Add checklist completion calls or skip the transition.

2. **Frontend health check timing** — `docker-compose.e2e.yml` frontend `start_period: 30s` may be too short. Increase to `60s`.

## How to Test

```bash
# Start the E2E stack
bash compose/scripts/start-mock-dev.sh

# Navigate to mock login (should render)
# Then navigate to /org/e2e-test-org/projects (currently crashes — should render after fix)

# Or test with dev server:
docker compose -f compose/docker-compose.e2e.yml up -d mock-idp
NEXT_PUBLIC_AUTH_MODE=mock NEXT_PUBLIC_MOCK_IDP_URL=http://localhost:8090 pnpm --dir frontend dev --port 3001

# Check frontend logs for errors:
docker compose -f compose/docker-compose.e2e.yml logs -f frontend
```

## Success Criteria

- `http://localhost:3001/mock-login` → Sign In → redirected to dashboard → page renders with sidebar, header, content
- Navigate to `/org/e2e-test-org/projects` → projects page renders (may be empty list, that's fine)
- Navigate to `/org/e2e-test-org/customers` → customers page renders (should show "Acme Corp")
- No `Clerk: auth() was called but clerkMiddleware() not detected` errors in frontend logs
