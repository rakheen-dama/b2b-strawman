# Phase 20 — Auth Abstraction & E2E Testing Infrastructure

Phase 20 introduces an **auth provider abstraction layer** that decouples the frontend from Clerk's SDK, replacing 44+ direct `@clerk/nextjs/server` imports with a thin platform-owned `lib/auth/` module. A `MockAuthProvider` implementation enables a fully headless E2E testing stack: a custom Node.js mock IDP issues signed JWTs that the unmodified Spring Boot backend accepts, a boot-seed container provisions a tenant via existing `/internal/*` endpoints, and a Docker Compose E2E stack brings up the entire application — Postgres, LocalStack, mock IDP, backend, frontend, and seed — as a single command. Playwright tests and agents can then authenticate as any seeded user via `loginAs(page, 'alice')` without Clerk's CAPTCHA or external service dependencies.

**Architecture doc**: `architecture/phase20-e2e-auth-abstraction.md`

**ADRs**:
- [ADR-085](../adr/ADR-085-auth-provider-abstraction.md) — Auth Provider Abstraction (thin interface over Clerk, build-time provider selection)
- [ADR-086](../adr/ADR-086-mock-idp-strategy.md) — Mock IDP Strategy (custom Node.js container over WireMock/Keycloak)
- [ADR-087](../adr/ADR-087-e2e-seed-strategy.md) — E2E Seed Data Strategy (boot-seed via REST endpoints over SQL dump)

**Dependencies on prior phases**: None. This phase touches only the auth boundary and test infrastructure. All domain entities, services, and API routes are unaffected.

**No database migrations**: This phase introduces no new tenant schema migrations. The next migration will be V36.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 138 | Auth Abstraction Layer — Interface + Clerk Provider | Frontend | — | M | 138A, 138B | **Done** (PRs #292, #293) |
| 139 | 44-File Mechanical Refactor | Frontend | 138 | M | 139A, 139B | **Done** (PRs #294, #295) |
| 140 | Mock IDP Container + Backend E2E Profile | Infra | — | S | 140A | **Done** (PR #296) |
| 141 | Frontend Mock Provider — Server + Middleware | Frontend | 138 | M | 141A, 141B | |
| 142 | Frontend Mock Provider — Client Components | Frontend | 138 | S | 142A | |
| 143 | Docker Compose E2E Stack + Boot-Seed Container | Infra | 140, 141, 142 | M | 143A | |
| 144 | Playwright Fixtures + Smoke Tests | Frontend/E2E | 143 | S | 144A | |

---

## Dependency Graph

```
[E138A lib/auth/types.ts + lib/auth/server.ts interface functions]
       |
[E138B lib/auth/providers/clerk.ts + lib/auth/index.ts + layout.tsx update]
       |
   +---+-------------------+--------------------+
   |                       |                    |
   v                       v                    v
[E139A                 [E141A               [E142A
 Refactor 25 files      lib/auth/            MockAuthProvider
 (actions.ts files)     providers/mock/      client context +
 import + call-site     server.ts +          useAuthUser() +
 substitution]          mock middleware]     useOrgMembers() +
   |                       |                 MockUserButton +
[E139B                 [E141B               MockOrgSwitcher]
 Refactor remaining     MockLoginPage            |
 files: pages,          (app)/(mock-auth)/       |
 lib/api.ts,            sign-in replacement]     |
 layout providers]          |                    |
                            +--------------------+
                                     |
                              [E140A (independent)
                               compose/mock-idp/ Node.js
                               JWKS + /token + /userinfo
                               backend application-e2e.yml]
                                     |
                               [E143A
                                docker-compose.e2e.yml +
                                compose/seed/seed.sh +
                                wait-for-backend.sh +
                                integration smoke test]
                                     |
                               [E144A
                                e2e/fixtures/auth.ts +
                                e2e/playwright.config.ts +
                                3 smoke tests (login, RBAC, navigation)]
```

**Parallel opportunities**:
- Epics 138, 140 are fully independent — they can run in parallel.
- Epics 139, 141, 142 all depend on Epic 138 completing first, but 139 and 141/142 can run in parallel with each other after 138.
- Epic 143 depends on 140, 141, and 142 all completing.
- Epic 144 depends solely on 143.

---

## Implementation Order

### Stage 1: Foundation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 138 | 138A | `lib/auth/types.ts` + `lib/auth/server.ts` interface (function signatures, no implementation). Foundation types that all subsequent slices import. | **Done** (PR #292) |
| 1b | Epic 138 | 138B | `lib/auth/providers/clerk.ts` (wraps existing Clerk calls), `lib/auth/index.ts` (re-exports), `lib/auth/client/auth-provider.tsx` stub. Validates that Clerk production path is unbroken before touching the 44 files. | **Done** (PR #293) |

### Stage 2: Parallel Tracks (After 138)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a (parallel) | Epic 139 | 139A | First half of mechanical refactor — all `actions.ts` files (25 of 44). Import swap + function rename only. No logic changes. | **Done** (PR #294) |
| 2b (parallel) | Epic 140 | 140A | Mock IDP Node.js container + `application-e2e.yml`. Independent of frontend abstraction work. Can complete while 139/141/142 are in progress. | **Done** (PR #296) |
| 2c (parallel) | Epic 141 | 141A | `lib/auth/providers/mock/server.ts` — reads JWT from cookie, exposes `getAuthContext()` / `getAuthToken()` for mock mode. Mock middleware (`lib/auth/providers/mock/middleware.ts`). | **Done** (PR #297) |

### Stage 3: Complete Parallel Tracks

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a (parallel) | Epic 139 | 139B | Second half of mechanical refactor — remaining pages, `lib/api.ts`, `app/layout.tsx`. Completes the 44-file migration. `pnpm build` + `pnpm test` must pass. | **Done** (PR #295) |
| 3b (parallel) | Epic 141 | 141B | `MockLoginPage` at `app/(mock-auth)/sign-in/page.tsx` — user picker dropdown, calls mock IDP `/token`, sets `mock-auth-token` cookie, redirects to dashboard. |
| 3c (parallel) | Epic 142 | 142A | `MockAuthProvider` React context, `useAuthUser()`, `useOrgMembers()`, `MockUserButton`, `MockOrgSwitcher`. Client-side mock UI components. |

### Stage 4: Integration

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4 | Epic 143 | 143A | `docker-compose.e2e.yml` + `compose/seed/seed.sh` + `compose/seed/wait-for-backend.sh`. Depends on 140A (mock IDP image), 141B (mock login page), 142A (mock client). Brings up the full stack and validates it end-to-end. |

### Stage 5: Playwright

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5 | Epic 144 | 144A | `e2e/fixtures/auth.ts` + `e2e/playwright.config.ts` + 3 smoke tests. The E2E stack (143A) must be running and healthy. |

### Timeline

```
Stage 1:  [138A] --> [138B]
Stage 2:  [139A] // [140A] // [141A]            (parallel after 138B)
Stage 3:  [139B] // [141B] // [142A]            (parallel, each depends only on own epic's prior slice)
Stage 4:  [143A]                                (after 140A, 141B, 142A all complete)
Stage 5:  [144A]                                (after 143A)
```

**Critical path**: 138A → 138B → 141A → 141B → 143A → 144A

---

## Epic 138: Auth Abstraction Layer — Interface + Clerk Provider

**Goal**: Create the `lib/auth/` module with platform-owned types, the server-side function interface (`getAuthContext`, `getAuthToken`, `getCurrentUserEmail`, `requireRole`), the Clerk provider implementation (wrapping existing `@clerk/nextjs/server` calls), a client-side `AuthProvider` component stub, and re-export barrel files. After this epic, the abstraction exists but no files have been migrated yet — the next epic (139) performs the mechanical refactor.

**References**: Architecture doc Sections 2.1 (Server-Side Auth Interface), 2.2 (Client-Side Auth Context). [ADR-085](../adr/ADR-085-auth-provider-abstraction.md).

**Dependencies**: None (foundation epic).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **138A** | 138.1–138.4 | `lib/auth/types.ts` (3 platform-owned types), `lib/auth/server.ts` (4 exported async functions with provider dispatch, exports `AUTH_MODE` constant). No Clerk dependency yet. ~2 files created. | **Done** (PR #292) |
| **138B** | 138.5–138.9 | `lib/auth/providers/clerk.ts` (Clerk implementation of all 4 server functions), `lib/auth/providers/mock/server.ts` stub (throws "not yet implemented"), `lib/auth/index.ts` (barrel re-export), `lib/auth/client/auth-provider.tsx` (conditional ClerkProvider wrapper), unit tests (~4 tests). ~5 files created, 1 file modified. | **Done** (PR #293) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 138.1 | Create `lib/auth/types.ts` | 138A | | `frontend/lib/auth/types.ts`. Define three exported TypeScript interfaces: `AuthContext` (`orgId: string`, `orgSlug: string`, `orgRole: string`, `userId: string`), `AuthUser` (`firstName: string \| null`, `lastName: string \| null`, `email: string`, `imageUrl: string \| null`), `OrgMemberInfo` (`id: string`, `role: string`, `email: string`, `name: string`). No Clerk imports. These are platform-owned types — no external SDK leakage. Exact field names per architecture doc Section 2.1 Types table. |
| 138.2 | Create `lib/auth/server.ts` — dispatch shell | 138A | 138.1 | `frontend/lib/auth/server.ts`. Add `"use server"` directive at top (this file is server-only). Export `const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE \|\| 'clerk'` as a named export. Export 4 async function stubs: `getAuthContext(): Promise<AuthContext>`, `getAuthToken(): Promise<string>`, `getCurrentUserEmail(): Promise<string \| null>`, `requireRole(role: 'admin' \| 'owner' \| 'any'): Promise<void>`. Each function body: `if (AUTH_MODE === 'mock') return mockProvider.getAuthContext()` else `return clerkProvider.getAuthContext()`. Import `* as clerkProvider from './providers/clerk'` and `* as mockProvider from './providers/mock/server'`. Architecture doc Section 2.1 Server functions table. |
| 138.3 | Create directory scaffolding | 138A | | Create empty placeholder files to establish directory structure: `frontend/lib/auth/providers/.gitkeep`, `frontend/lib/auth/client/.gitkeep`. These will be replaced in 138B. Builder note: Next.js requires these directories to exist for imports to resolve — create them as empty index files (`export {}`) rather than .gitkeep so TypeScript is happy. |
| 138.4 | Write `lib/auth` unit tests — types | 138A | 138.1 | `frontend/__tests__/lib/auth/types.test.ts`. Tests: `AuthContext` interface has all 4 required fields; `AuthUser` allows `null` firstName/lastName; `OrgMemberInfo` has id, role, email, name. ~3 type-level tests using TypeScript `satisfies` operator to assert shape compliance at compile time. These are compile-time assertions, not runtime assertions. Use `const x: AuthContext = {...} satisfies AuthContext` pattern. |
| 138.5 | Create `lib/auth/providers/clerk.ts` | 138B | 138A | `frontend/lib/auth/providers/clerk.ts`. Add `import { auth, currentUser } from "@clerk/nextjs/server"` at top. Implement all 4 functions: `getAuthContext()` — calls `auth()`, destructures `{ orgId, orgSlug, orgRole, userId }`, returns as `AuthContext` (throw error if orgId null). `getAuthToken()` — calls `const { getToken } = await auth(); return await getToken() ?? ''`. `getCurrentUserEmail()` — calls `currentUser()`, returns `user?.primaryEmailAddress?.emailAddress ?? null`. `requireRole(role)` — calls `auth()`, checks `orgRole`, throws if insufficient. This file consolidates all Clerk SDK usage that currently exists across 44 files into a single location. Pattern: see current usage in `frontend/app/(app)/org/[slug]/customers/actions.ts`. |
| 138.6 | Create `lib/auth/providers/mock/server.ts` stub | 138B | 138A | `frontend/lib/auth/providers/mock/server.ts`. Stub implementation — all functions throw `Error('Mock auth provider not yet implemented — implement in Epic 141')`. This stub exists so `lib/auth/server.ts` compiles. Epic 141 replaces the stub with the real mock implementation. Pattern: keep the same function signatures as `providers/clerk.ts`. |
| 138.7 | Create `lib/auth/index.ts` barrel | 138B | 138.5 | `frontend/lib/auth/index.ts`. Re-exports: `export { getAuthContext, getAuthToken, getCurrentUserEmail, requireRole } from './server'`. `export type { AuthContext, AuthUser, OrgMemberInfo } from './types'`. This is the single import target for all 44 refactored files — they will use `import { getAuthContext } from "@/lib/auth"`. |
| 138.8 | Create `lib/auth/client/auth-provider.tsx` | 138B | 138A | `frontend/lib/auth/client/auth-provider.tsx`. Add `"use client"`. Reads `AUTH_MODE` from `process.env.NEXT_PUBLIC_AUTH_MODE`. If `clerk` (default): renders `<ClerkProvider appearance={{ cssLayerName: "clerk" }} {...props}>{children}</ClerkProvider>`. If `mock`: renders `<MockAuthContextProvider>{children}</MockAuthContextProvider>` (import from `./mock-context` — created in Epic 142). For now (138B), the mock branch can render a placeholder `<div>{children}</div>` with a TODO comment — Epic 142 replaces it. Pattern: current `<ClerkProvider>` in `frontend/app/layout.tsx`. |
| 138.9 | Write Clerk provider unit tests | 138B | 138.5, 138.7 | `frontend/__tests__/lib/auth/clerk-provider.test.ts`. Mock `@clerk/nextjs/server` (mock `auth` to return `{ orgId: 'org_test', orgSlug: 'test-org', orgRole: 'owner', userId: 'user_1', getToken: async () => 'tok_123' }`, mock `currentUser` to return `{ primaryEmailAddress: { emailAddress: 'alice@test.com' } }`). Tests: `getAuthContext()` returns correct `AuthContext` shape; `getAuthToken()` returns the token string; `getCurrentUserEmail()` returns the email string; `requireRole('owner')` passes when role matches; `requireRole('admin')` throws when role is 'member'. ~5 unit tests using vitest `vi.mock`. |

### Key Files

**Slice 138A — Create:**
- `frontend/lib/auth/types.ts` — Platform-owned auth type definitions
- `frontend/lib/auth/server.ts` — Dispatch shell with provider selection
- `frontend/__tests__/lib/auth/types.test.ts` — Compile-time type shape assertions

**Slice 138B — Create:**
- `frontend/lib/auth/providers/clerk.ts` — Clerk SDK wrapper (all 4 server functions)
- `frontend/lib/auth/providers/mock/server.ts` — Stub (throws; replaced in Epic 141)
- `frontend/lib/auth/index.ts` — Barrel re-export
- `frontend/lib/auth/client/auth-provider.tsx` — Conditional ClerkProvider wrapper
- `frontend/__tests__/lib/auth/clerk-provider.test.ts` — Unit tests for Clerk provider

**Slice 138B — Modify:**
- `frontend/app/layout.tsx` — Replace `<ClerkProvider>` with `<AuthProvider>` from `@/lib/auth/client/auth-provider`

**Read for context:**
- `frontend/proxy.ts` — Current `clerkMiddleware()` usage
- `frontend/app/layout.tsx` — Current `<ClerkProvider>` wrapping
- `frontend/lib/api.ts` — Current `auth().getToken()` usage
- `frontend/app/(app)/org/[slug]/customers/actions.ts` — Representative of the 44 files using `auth()`
- Architecture doc Section 2.1

### Architecture Decisions

- **`lib/auth/server.ts` as the dispatch hub**: All 44 files will import from `@/lib/auth` (the barrel). Provider dispatch is in `server.ts`. The Clerk provider file never changes unless Clerk's SDK changes. The mock provider file is replaced in Epic 141.
- **`AUTH_MODE` as build-time constant**: `NEXT_PUBLIC_AUTH_MODE` is inlined by Next.js at build time. In production builds with `AUTH_MODE=clerk`, the mock branch is dead code that tree-shakers remove. No mock code ships to production.
- **Stub mock provider in 138B**: The stub exists so Epic 138 can compile and produce a passing build without waiting for Epic 141.
- **`auth-provider.tsx` as a wrapper, not a replacement**: The `ClerkProvider` configuration (particularly `cssLayerName: "clerk"` required for Tailwind v4 compatibility) is preserved inside the Clerk branch.

---

## Epic 139: 44-File Mechanical Refactor

**Goal**: Replace all `import { auth } from "@clerk/nextjs/server"` and `import { currentUser } from "@clerk/nextjs/server"` calls across 44 frontend files with the platform-owned equivalents from `@/lib/auth`. Each file change is a 2-line mechanical substitution (import path + function call). After this epic, no server-side file imports directly from `@clerk/nextjs` — all auth calls route through the abstraction layer. `pnpm build` and all 220+ frontend tests must pass.

**References**: Architecture doc Section 7 (Migration Path, 44-File Refactor). Before/After diff in Section 7.1.

**Dependencies**: Epic 138 (abstraction layer must exist before import targets can be changed).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **139A** | 139.1–139.5 | Refactor first 25 files: all `actions.ts` server actions in `app/(app)/org/[slug]/**`. Each file: replace import + replace `auth()` destructure + replace any `currentUser()` call. Run `pnpm build` at end to verify no TypeScript errors. ~25 files modified. | **Done** (PR #294) |
| **139B** | 139.6–139.10 | Refactor remaining files: all `page.tsx` server components that call `auth()`, `lib/api.ts` (replace `getToken()`), any remaining files. Run full `pnpm build` + `pnpm test` — all tests must pass. Add `server-only` import to `lib/auth/server.ts`. ~22 files modified. | **Done** (PR #295) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 139.1 | Audit current `@clerk/nextjs/server` import sites | 139A | 138 complete | Run: `grep -r "from \"@clerk/nextjs/server\"" frontend/app --include="*.ts" --include="*.tsx" -l` to get the exact list. Categorize: (a) files using only `auth()` for destructure, (b) files using `auth().getToken()`, (c) files using `currentUser()`, (d) files using `clerkClient()` (NOT refactored per architecture doc Section 7.3). |
| 139.2 | Refactor `actions.ts` files — customers domain | 139A | 139.1 | Modify all `actions.ts` files under `frontend/app/(app)/org/[slug]/customers/**`. For each file: remove `import { auth } from "@clerk/nextjs/server"`, add `import { getAuthContext } from "@/lib/auth"`. Replace `const { orgId, orgRole, userId } = await auth()` with `const { orgId, orgRole, userId } = await getAuthContext()`. Expected count: ~8 files. Pattern: architecture doc Section 7.1 Before/After diff. |
| 139.3 | Refactor `actions.ts` files — projects, tasks, documents domains | 139A | 139.1 | Modify all `actions.ts` files under `frontend/app/(app)/org/[slug]/projects/**`, `tasks/**`, `documents/**`. Same import swap + destructure rename pattern. Expected count: ~10 files. |
| 139.4 | Refactor `actions.ts` files — remaining domains | 139A | 139.1 | Modify all remaining `actions.ts` files: `settings/**`, `retainers/**`, `invoices/**`, `schedules/**`, `notifications/**`, `my-work/**`, `reports/**`. Same pattern. Expected count: ~7 files. |
| 139.5 | Build verification after 139A | 139A | 139.2–139.4 | Run `pnpm build` (or `pnpm tsc --noEmit`). Fix any type errors. Common issues: (a) a file that also used `auth().has()` — add `AUTH_MODE` guard per architecture doc Section 2.1 Note on `has()`. (b) `currentUser()` returns `User \| null` from Clerk but `getCurrentUserEmail()` returns `string \| null`. |
| 139.6 | Refactor `page.tsx` server components — all domains | 139B | 138 complete | Modify all `page.tsx` files that import `auth()` from Clerk. Same import swap + destructure rename. Key files: `customers/page.tsx`, `projects/page.tsx`, `settings/page.tsx`, `dashboard/page.tsx`, `my-work/page.tsx`, etc. Expected count: ~15 files. |
| 139.7 | Refactor `lib/api.ts` | 139B | 138.7 | Modify `frontend/lib/api.ts`. Remove `import { auth } from "@clerk/nextjs/server"`. Add `import { getAuthToken } from "@/lib/auth"`. Replace `const { getToken } = await auth(); const token = await getToken()` with `const token = await getAuthToken()`. |
| 139.8 | Refactor `proxy.ts` middleware | 139B | 138.8 | Modify `frontend/proxy.ts`. Introduce `createAuthMiddleware()` function (defined in `lib/auth/middleware.ts`). Replace `export default clerkMiddleware(...)` with `export default createAuthMiddleware()`. For now, `createAuthMiddleware` returns the existing `clerkMiddleware()` unconditionally — Epic 141 adds the mock branch. |
| 139.9 | Add `server-only` guard to `lib/auth/server.ts` | 139B | 139.7 | Add `import "server-only"` as the first line of `frontend/lib/auth/server.ts`. Ensures client components can't accidentally import server auth functions. Pattern per frontend CLAUDE.md: "the `server-only` package must be imported in any lib file that accesses server-only env vars". |
| 139.10 | Full build + test verification | 139B | 139.6–139.9 | Run `pnpm build` + `pnpm test`. Confirm: 0 TypeScript errors, 0 test failures, `grep -r "from \"@clerk/nextjs/server\"" frontend/app` returns only the 3 exempt files per architecture doc Section 7.3. Add inline comment to exempt files: `// CLERK-SPECIFIC: clerkClient() — not abstracted per ADR-085`. |

### Key Files

**Slice 139A — Modify (25 files):**
- All `actions.ts` files under `frontend/app/(app)/org/[slug]/customers/**`
- All `actions.ts` files under `frontend/app/(app)/org/[slug]/projects/**`
- All `actions.ts` files under `frontend/app/(app)/org/[slug]/settings/**`
- All `actions.ts` files under remaining domains

**Slice 139B — Modify (~22 files):**
- All `page.tsx` files that currently call `auth()`
- `frontend/lib/api.ts` — `getToken()` → `getAuthToken()`
- `frontend/proxy.ts` — introduce `createAuthMiddleware()` delegation
- `frontend/lib/auth/server.ts` — add `server-only` import

**Read for context:**
- `frontend/app/(app)/org/[slug]/customers/actions.ts` — representative before-state
- `frontend/lib/api.ts` — current `auth().getToken()` pattern
- `frontend/proxy.ts` — current `clerkMiddleware()` usage
- Architecture doc Sections 7.1 and 7.3

### Architecture Decisions

- **Mechanical split into 139A/139B**: The 44-file refactor is split to keep each slice under the 12 file limit for builder context. 139A focuses on `actions.ts` files. 139B handles `page.tsx` RSC files and shared library files.
- **3 exempt files retain direct Clerk imports**: Per architecture doc Section 7.3: webhook handler, `webhook-handlers.ts`, `team/actions.ts`. All three get `AUTH_MODE` guards and inline comments.
- **`proxy.ts` gets a thin delegation shell**: Epic 139B introduces the delegation to `createAuthMiddleware()` — but the function itself is a no-op pass-through until Epic 141 implements it.

---

## Epic 140: Mock IDP Container + Backend E2E Profile

**Goal**: Build the `compose/mock-idp/` Node.js service (JWKS endpoint, `/token`, `/userinfo`), write its `Dockerfile`, and add `backend/src/main/resources/application-e2e.yml` that points the Spring Security JWT validator at the mock IDP's JWKS endpoint. No backend Java code changes. After this epic, the backend can validate mock JWTs when started with `--spring.profiles.active=e2e`.

**References**: Architecture doc Sections 3 (Mock IDP Container), 4 (Backend Configuration). [ADR-086](../adr/ADR-086-mock-idp-strategy.md).

**Dependencies**: None (fully independent of frontend abstraction work).

**Scope**: Infrastructure (Node.js + YAML)

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **140A** | 140.1–140.9 | `compose/mock-idp/` directory with `Dockerfile`, `package.json`, `tsconfig.json`, `src/index.ts` (Express server with JWKS + `/token` + `/userinfo`), `src/keys.ts` (RSA key generation), `src/users.ts` (seed user definitions). `backend/src/main/resources/application-e2e.yml`. ~8 files created. | **Done** (PR #296) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 140.1 | Create `compose/mock-idp/package.json` | 140A | | Dependencies: `express` (^4.18), `jsonwebtoken` (^9.0). DevDependencies: `typescript` (^5), `@types/express`, `@types/jsonwebtoken`. Scripts: `build: "tsc"`, `start: "node dist/index.js"`. Minimal dependency footprint — test-only service. |
| 140.2 | Create `compose/mock-idp/tsconfig.json` | 140A | 140.1 | Standard Node.js TypeScript config: `target: "ES2020"`, `module: "commonjs"`, `outDir: "dist"`, `strict: true`. |
| 140.3 | Create `compose/mock-idp/src/keys.ts` | 140A | 140.2 | Uses Node.js `crypto.generateKeyPairSync('rsa', { modulusLength: 2048 })`. Exports: `privateKey: KeyObject`, `publicKeyJwk: JsonWebKey`. Adds `kid: "mock-key-1"`, `use: "sig"`, `alg: "RS256"` to the JWK. Key pair generated once at module load. Architecture doc Section 3.2. |
| 140.4 | Create `compose/mock-idp/src/users.ts` | 140A | | 3 seed users per architecture doc Section 3.3: alice (owner), bob (admin), carol (member). Exports: `USERS: Record<string, SeedUser>`. Also reads `MOCK_USERS` env var (JSON array) for additional users. |
| 140.5 | Create `compose/mock-idp/src/index.ts` — JWKS endpoint | 140A | 140.3, 140.4 | Express app. `GET /.well-known/jwks.json`: returns `{ keys: [{ ...publicKeyJwk }] }`. Port from `PORT` env var (default `8090`). |
| 140.6 | Implement `POST /token` endpoint | 140A | 140.5 | Request body: `{ userId, orgId, orgSlug, orgRole }`. Build JWT in Clerk v2 format: `{ sub: userId, iss: "http://mock-idp:8090", aud: "docteams-e2e", o: { id: orgId, rol: orgRole, slg: orgSlug } }`. Sign with `jsonwebtoken.sign(payload, privateKey, { algorithm: 'RS256', keyid: 'mock-key-1' })`. Return `{ access_token, token_type: 'Bearer', expires_in: 86400 }`. Critical: `o.rol` must use short role names (`owner`, `admin`, `member`) — what `ClerkJwtAuthenticationConverter` reads. |
| 140.7 | Implement `GET /userinfo/:userId` endpoint | 140A | 140.4, 140.5 | Looks up `userId` in `USERS` map. Returns `{ id, firstName, lastName, email, imageUrl }` or 404. |
| 140.8 | Create `compose/mock-idp/Dockerfile` | 140A | 140.5 | Multi-stage: `FROM node:22-alpine AS builder` — `npm ci`, `npm run build`. `FROM node:22-alpine` — copy `dist/` and `node_modules/`. `EXPOSE 8090`. `CMD ["node", "dist/index.js"]`. |
| 140.9 | Create `backend/src/main/resources/application-e2e.yml` | 140A | | Per architecture doc Section 4.1: `spring.security.oauth2.resourceserver.jwt.jwk-set-uri: ${MOCK_IDP_JWKS_URI:http://mock-idp:8090/.well-known/jwks.json}`. No `issuer-uri`. Also: `internal.api.key: ${INTERNAL_API_KEY:e2e-test-key}`. No other properties needed. |

### Key Files

**Slice 140A — Create:**
- `compose/mock-idp/package.json`
- `compose/mock-idp/tsconfig.json`
- `compose/mock-idp/src/keys.ts` — RSA key pair management
- `compose/mock-idp/src/users.ts` — Seed user definitions
- `compose/mock-idp/src/index.ts` — Express server (JWKS + /token + /userinfo)
- `compose/mock-idp/Dockerfile`
- `backend/src/main/resources/application-e2e.yml`

**Read for context:**
- `compose/docker-compose.yml` — Existing service definitions
- `backend/src/main/java/.../security/ClerkJwtAuthenticationConverter.java` — Confirm `o.rol` claim reading
- Architecture doc Section 3.2 — JWT claim structure

### Architecture Decisions

- **RSA key pair generated at process start, not build time**: No secrets baked into Docker image. Every container restart generates a new key pair.
- **No database in mock IDP**: Stateless. User profiles hardcoded. Token issuance is a pure function.
- **Issuer validation disabled in e2e profile**: Only `jwk-set-uri` is set. Spring Security validates JWT signature but skips `iss` claim check.

---

## Epic 141: Frontend Mock Provider — Server + Middleware

**Goal**: Replace the stub `lib/auth/providers/mock/server.ts` with a real implementation that reads the `mock-auth-token` cookie and extracts auth context from its JWT claims. Implement the mock middleware and the `MockLoginPage`. After this epic, the full server-side auth path works in mock mode.

**References**: Architecture doc Sections 2.1, 2.3, 6.1. [ADR-085](../adr/ADR-085-auth-provider-abstraction.md).

**Dependencies**: Epic 138 (abstraction types and dispatch hub), Epic 140 (mock IDP must be defined).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **141A** | 141.1–141.5 | Real `lib/auth/providers/mock/server.ts` (reads `mock-auth-token` cookie, decodes JWT), `lib/auth/middleware.ts` (`createAuthMiddleware()`), mock middleware implementation. ~3 files created, 1 modified. | **Done** (PR #297) |
| **141B** | 141.6–141.10 | `app/(mock-auth)/sign-in/page.tsx` — `MockLoginPage` with user picker, client form action, cookie setting. Update sign-in page redirect. Unit tests (~5 tests). ~4 files created, 1 modified. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 141.1 | Implement `lib/auth/providers/mock/server.ts` — cookie reader | 141A | 138A complete | Replace stub. Import `cookies` from `next/headers`. `getAuthContext()`: reads `mock-auth-token` cookie, decodes JWT payload via `JSON.parse(atob(token.split('.')[1]))`. Extracts `sub` (userId), `o.id` (orgId), `o.slg` (orgSlug), `o.rol` (orgRole). Returns `AuthContext`. |
| 141.2 | Implement `getAuthToken()` in mock server provider | 141A | 141.1 | Returns raw JWT string from `mock-auth-token` cookie. Backend validates against mock IDP JWKS. |
| 141.3 | Implement `getCurrentUserEmail()` in mock server provider | 141A | 141.1 | Reads `userId` from JWT, fetches `${MOCK_IDP_URL}/userinfo/${userId}`, returns `data.email`. Env var `MOCK_IDP_URL` defaults to `http://mock-idp:8090`. |
| 141.4 | Create `lib/auth/middleware.ts` with mock middleware | 141A | 138A, 141.1 | Exports `createAuthMiddleware()`. `AUTH_MODE=clerk`: returns existing `clerkMiddleware()` with full config from `proxy.ts`. `AUTH_MODE=mock`: returns `NextMiddleware` that checks `mock-auth-token` cookie on protected routes, redirects to `/mock-login` if absent. Public routes skip check. |
| 141.5 | Update `proxy.ts` to use `createAuthMiddleware()` | 141A | 141.4 | Replace inline `clerkMiddleware()` with import from `@/lib/auth/middleware`. Move `organizationSyncOptions` and `isPublicRoute` into `lib/auth/middleware.ts`. `proxy.ts` becomes a 3-line file. |
| 141.6 | Create `app/(mock-auth)/` route group and layout | 141B | 141.4 | `frontend/app/(mock-auth)/layout.tsx`. Minimal layout without `<ClerkProvider>` or `<MockAuthProvider>`. Simple centered layout with DocTeams wordmark. |
| 141.7 | Create `MockLoginPage` — server component | 141B | 141.6 | `frontend/app/(mock-auth)/sign-in/page.tsx`. Renders heading "DocTeams — E2E Test Login" and `<MockLoginForm>` client component with seed user list and `mockIdpUrl` prop. |
| 141.8 | Create `MockLoginForm` client component | 141B | 141.7 | `frontend/components/auth/mock-login-form.tsx`. `"use client"`. `<select>` dropdown of users, "Sign In" button. On submit: `POST ${mockIdpUrl}/token`, store `access_token` as `mock-auth-token` cookie, `router.push('/org/${orgSlug}/dashboard')`. |
| 141.9 | Update sign-in page to conditionally render | 141B | 141.7 | Modify `frontend/app/(auth)/sign-in/[[...sign-in]]/page.tsx`. If `AUTH_MODE === 'mock'`: `redirect('/mock-login')`. If `clerk`: render `<SignIn />`. |
| 141.10 | Write unit tests for mock middleware | 141B | 141.4, 141.5 | `frontend/__tests__/lib/auth/mock-middleware.test.ts`. ~5 tests: no cookie → redirect; valid cookie → pass; public route → no cookie needed; `AUTH_MODE=clerk` → Clerk middleware. |

### Key Files

**Slice 141A — Create:**
- `frontend/lib/auth/providers/mock/server.ts` — Real mock server implementation
- `frontend/lib/auth/middleware.ts` — `createAuthMiddleware()`

**Slice 141A — Modify:**
- `frontend/proxy.ts` — Replace inline middleware with delegation

**Slice 141B — Create:**
- `frontend/app/(mock-auth)/layout.tsx` — Mock login layout
- `frontend/app/(mock-auth)/sign-in/page.tsx` — `MockLoginPage`
- `frontend/components/auth/mock-login-form.tsx` — Client form with user picker
- `frontend/__tests__/lib/auth/mock-middleware.test.ts` — Tests

**Slice 141B — Modify:**
- `frontend/app/(auth)/sign-in/[[...sign-in]]/page.tsx` — Add `AUTH_MODE` guard

### Architecture Decisions

- **JWT payload decoded without verification in server components**: The middleware validates the cookie's presence. Server-side claim extraction is a plain base64 decode — acceptable for test environments.
- **`(mock-auth)` route group**: Separate layout without app shell to avoid auth provider wrapper. Public route exempt from cookie check to prevent redirect loops.
- **`MockLoginForm` hardcodes the default org**: `orgId: 'org_e2e_test'`, `orgSlug: 'e2e-test-org'` matching boot-seed values.

---

## Epic 142: Frontend Mock Provider — Client Components

**Goal**: Implement the `MockAuthContextProvider` React context and client-side hooks/components: `useAuthUser()`, `useOrgMembers()`, `useSignOut()`, `MockUserButton`, `MockOrgSwitcher`. Update `auth-provider.tsx` to render the real `MockAuthContextProvider`.

**References**: Architecture doc Section 2.2 (Client-Side Auth Context).

**Dependencies**: Epic 138 (types, `AuthProvider` wrapper stub).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **142A** | 142.1–142.8 | `lib/auth/client/mock-context.tsx`, `lib/auth/client/hooks.ts`, `MockUserButton`, `MockOrgSwitcher`, update `auth-provider.tsx`, unit tests (~5 tests). ~5 files created, 1 modified. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 142.1 | Create `lib/auth/client/mock-context.tsx` — context and provider | 142A | 138A | React `createContext`. `MockAuthContextProvider`: reads `mock-auth-token` cookie via `document.cookie`, decodes JWT payload. On mount: fetch `${NEXT_PUBLIC_MOCK_IDP_URL}/userinfo/${userId}` for display name. Provides `{ authUser, isLoaded, orgSlug }`. |
| 142.2 | Implement `useAuthUser()` hook | 142A | 142.1 | In `lib/auth/client/hooks.ts`. Returns `{ user: AuthUser \| null, isLoaded: boolean }` from `MockAuthContext`. Replaces `useUser()` from `@clerk/nextjs`. |
| 142.3 | Implement `useOrgMembers()` hook | 142A | 142.1 | Calls `GET /api/members` with JWT token from cookie. Returns `{ members: OrgMemberInfo[], isLoaded: boolean }`. Replaces `useOrganization().memberships`. |
| 142.4 | Implement `useSignOut()` hook | 142A | 142.1 | Clears `mock-auth-token` cookie, calls `router.push('/mock-login')`. Replaces `useClerk().signOut()`. |
| 142.5 | Create `MockUserButton` component | 142A | 142.2, 142.4 | `frontend/components/auth/mock-user-button.tsx`. Avatar (initials), display name, "Sign Out" button. Uses Shadcn `DropdownMenu`. |
| 142.6 | Create `MockOrgSwitcher` component | 142A | 142.2 | `frontend/components/auth/mock-org-switcher.tsx`. Static org name text with "E2E" badge. No switching — single-org E2E stack. |
| 142.7 | Update `lib/auth/client/auth-provider.tsx` — wire mock context | 142A | 142.1 | Replace `<div>{children}</div>` placeholder with `<MockAuthContextProvider>{children}</MockAuthContextProvider>`. |
| 142.8 | Write unit tests for mock client hooks | 142A | 142.2–142.4 | `frontend/__tests__/lib/auth/mock-client-hooks.test.tsx`. Mock `document.cookie` and `fetch`. ~5 tests. Add `afterEach(() => cleanup())` per Radix UI leak note. |

### Key Files

**Slice 142A — Create:**
- `frontend/lib/auth/client/mock-context.tsx` — React context + provider
- `frontend/lib/auth/client/hooks.ts` — `useAuthUser()`, `useOrgMembers()`, `useSignOut()`
- `frontend/components/auth/mock-user-button.tsx` — Mock `<UserButton>` replacement
- `frontend/components/auth/mock-org-switcher.tsx` — Mock `<OrganizationSwitcher>` replacement
- `frontend/__tests__/lib/auth/mock-client-hooks.test.tsx` — Unit tests

**Slice 142A — Modify:**
- `frontend/lib/auth/client/auth-provider.tsx` — Replace placeholder with real mock context

### Architecture Decisions

- **`useOrgMembers()` calls the backend API**: Same endpoint as production (`GET /api/members`). Eliminates direct Clerk dependency from team management UI.
- **`MockOrgSwitcher` is static**: E2E stack has one org. No switching needed.
- **Client-side cookie read**: `document.cookie` decoded for display state only. Security enforced server-side.

---

## Epic 143: Docker Compose E2E Stack + Boot-Seed Container

**Goal**: Create `compose/docker-compose.e2e.yml` with all 6 services, the `compose/seed/seed.sh` boot-seed script, and `compose/seed/wait-for-backend.sh`. After this epic, `docker compose -f compose/docker-compose.e2e.yml up` brings up a fully seeded application at `http://localhost:3000`.

**References**: Architecture doc Sections 5, 5.2. [ADR-087](../adr/ADR-087-e2e-seed-strategy.md).

**Dependencies**: Epic 140A (mock IDP image), Epic 141B (mock login page), Epic 142A (mock client context).

**Scope**: Infrastructure

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **143A** | 143.1–143.8 | `compose/docker-compose.e2e.yml` (6 services), `compose/seed/Dockerfile`, `compose/seed/seed.sh`, `compose/seed/wait-for-backend.sh`. ~4 files created. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 143.1 | Create `docker-compose.e2e.yml` — infrastructure services | 143A | 140A | `postgres:16-alpine` + `localstack/localstack:3` + `mock-idp` (build from `./mock-idp`). All with health checks. Shared `e2e-network`. |
| 143.2 | Add `backend` service | 143A | 143.1 | Build from `../backend`. `SPRING_PROFILES_ACTIVE: e2e`. `MOCK_IDP_JWKS_URI: http://mock-idp:8090/.well-known/jwks.json`. Healthcheck via actuator. `depends_on` postgres, localstack, mock-idp. |
| 143.3 | Add `frontend` service | 143A | 143.1 | Build from `../frontend` with `args: { NEXT_PUBLIC_AUTH_MODE: mock, NEXT_PUBLIC_MOCK_IDP_URL: http://localhost:8090 }` (build-time args). `depends_on` backend. |
| 143.4 | Create `compose/seed/wait-for-backend.sh` | 143A | | Polls `GET http://backend:8080/actuator/health` every 2s. 120s timeout. |
| 143.5 | Create `compose/seed/seed.sh` — org provisioning | 143A | 143.4 | Step 1: `POST /internal/orgs/provision`. Step 2: `POST /internal/plans/sync`. Each with HTTP status check. |
| 143.6 | Add member sync to `seed.sh` | 143A | 143.5 | Step 3: sync alice (owner), bob (admin), carol (member) via `POST /internal/members/sync`. |
| 143.7 | Add sample data to `seed.sh` | 143A | 143.6 | Get alice's JWT from mock IDP. Create customer + project via API. Note: customer must be transitioned to ACTIVE per CustomerLifecycleGuard. |
| 143.8 | Create `compose/seed/Dockerfile` and add seed service | 143A | 143.4–143.7 | `FROM alpine:3.19` + `curl` + `jq`. `restart: "no"`. `depends_on` backend (healthy) + frontend (started) + mock-idp (healthy). |

### Key Files

**Slice 143A — Create:**
- `compose/docker-compose.e2e.yml` — Full 6-service E2E stack
- `compose/seed/Dockerfile` — Alpine + curl + jq
- `compose/seed/seed.sh` — Boot-seed script
- `compose/seed/wait-for-backend.sh` — Health check poller

**Read for context:**
- `compose/docker-compose.yml` — Existing compose conventions
- Backend integration test provisioning patterns for exact request shapes

### Architecture Decisions

- **`NEXT_PUBLIC_AUTH_MODE=mock` as Docker build arg**: Next.js bakes `NEXT_PUBLIC_*` at compile time. Must be a build arg, not runtime env.
- **`restart: "no"` for seed container**: One-shot provisioner. Endpoints are idempotent.
- **CustomerLifecycleGuard in seed.sh**: Customer must transition through onboarding to ACTIVE before creating a project.

---

## Epic 144: Playwright Fixtures + Smoke Tests

**Goal**: Set up `e2e/` with Playwright config, `loginAs()` fixture, and 3 smoke tests. After this epic, `npx playwright test` runs against the E2E stack.

**References**: Architecture doc Sections 6, 6.1, 6.2.

**Dependencies**: Epic 143A (E2E stack must be up and seeded).

**Scope**: Frontend / E2E

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **144A** | 144.1–144.7 | `e2e/playwright.config.ts`, `e2e/fixtures/auth.ts`, 3 smoke tests, `package.json` update, `e2e/README.md`. ~6 files created, 1 modified. |  |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 144.1 | Add Playwright to `frontend/package.json` | 144A | 143A | `@playwright/test: ^1.45` to devDeps. Script: `test:e2e: playwright test`. |
| 144.2 | Create `e2e/playwright.config.ts` | 144A | 144.1 | `baseURL: http://localhost:3000`. Single `chromium` project. `globalTimeout: 60_000`. |
| 144.3 | Create `e2e/fixtures/auth.ts` | 144A | | Copy from architecture doc Section 6.1. `loginAs(page, user)` fetches token from mock IDP, sets `mock-auth-token` cookie. |
| 144.4 | Write smoke test 1 — mock login flow | 144A | 144.2, 144.3 | Navigate to `/mock-login`, select Alice, click "Sign In". Assert URL contains `/dashboard`. |
| 144.5 | Write smoke test 2 — owner CRUD | 144A | 144.3 | `loginAs(page, 'alice')`. Navigate to projects. Create project. Assert it appears. |
| 144.6 | Write smoke test 3 — member RBAC | 144A | 144.3 | `loginAs(page, 'carol')`. Navigate to settings. Assert forbidden or redirect. |
| 144.7 | Create `e2e/README.md` | 144A | all | How to start E2E stack, install Playwright browsers, run tests, stop stack. |

### Key Files

**Slice 144A — Create:**
- `frontend/e2e/playwright.config.ts`
- `frontend/e2e/fixtures/auth.ts`
- `frontend/e2e/tests/smoke.spec.ts` — 3 smoke tests
- `frontend/e2e/README.md`

**Slice 144A — Modify:**
- `frontend/package.json` — Add Playwright dev dependency

### Architecture Decisions

- **`loginAs()` uses `addCookies()`, not navigation**: Faster than UI-based login. Middleware validates cookie on next request.
- **3 smoke tests only**: Validates infrastructure works. Feature-specific E2E tests belong in future phases.
- **Tests run against live stack**: True E2E — no Playwright mocks. Requires E2E stack running.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| 44-file refactor introduces TypeScript errors | Low | Medium | `pnpm tsc --noEmit` after each batch; full build check at end |
| Mock JWT claim format diverges from Clerk v2 | Low | High | Mock IDP uses identical `o.id`/`o.rol`/`o.slg`; backend unchanged; smoke tests catch immediately |
| CustomerLifecycleGuard blocks seed — customer stuck in PROSPECT | Medium | Medium | seed.sh calls lifecycle transition endpoint explicitly |
| Docker build arg for `NEXT_PUBLIC_AUTH_MODE` ignored at runtime | Medium | Medium | Documented; verify by inspecting bundled JS for literal `"mock"` |
| Playwright tests run against wrong port / E2E stack not started | Low | Low | README has explicit startup sequence; `globalTimeout: 60_000` |
| `proxy.ts` rename confusion with `middleware.ts` | Low | Low | `createAuthMiddleware()` lives in `lib/auth/middleware.ts`, imported into `proxy.ts` — the Next.js middleware file remains `proxy.ts` |
