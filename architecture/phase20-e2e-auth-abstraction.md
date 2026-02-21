# Auth Abstraction & E2E Testing Infrastructure

> Standalone architecture document. ADR files go in `adr/`.

---

## Auth Abstraction & E2E Testing Infrastructure

This phase introduces an **auth provider abstraction layer** to decouple the platform from Clerk's SDK, and an **E2E testing stack** (mock IDP, boot-seed, Docker Compose) that enables agents and Playwright tests to drive the full application without Clerk authentication. The auth abstraction also reduces vendor lock-in — switching auth providers (Auth0, Supabase Auth, self-hosted) becomes a single file swap rather than a 50-file migration.

Currently, 44 server-side files import `auth()` from `@clerk/nextjs/server`, 5 client components use Clerk hooks or UI components, and the central API client (`lib/api.ts`) obtains JWTs via Clerk's `getToken()`. The backend validates Clerk JWTs against a JWKS endpoint. All of these are standard, well-isolated integration points — no Clerk-specific logic is deeply embedded in business logic.

**Dependencies on prior phases**: None. This is a cross-cutting infrastructure change that touches the auth boundary only. All existing domain code (projects, customers, invoices, templates, etc.) is unaffected.

### What's New

| Capability | Before | After |
|---|---|---|
| Auth provider coupling | 44+ files import directly from `@clerk/nextjs` | All server files import from `@/lib/auth` — provider is swappable |
| E2E test capability | Blocked by Clerk CAPTCHA and session management | Mock IDP + boot-seed enables headless browser testing |
| Agent UI automation | Impossible — agents cannot authenticate | Agents can step through the full UI via mock auth tokens |
| Playwright integration | Not possible | `loginAs(page, 'admin')` fixture sets mock JWT cookie |
| Auth provider lock-in | Switching Clerk requires touching 50+ files | Switching requires implementing one provider file |
| Backend auth flexibility | Hardcoded Clerk JWKS URI | `e2e` profile points at mock IDP JWKS endpoint |

**Out of scope**: Migrating away from Clerk (Clerk remains the production provider), SAML/OIDC federation, multi-provider support (only one provider active at a time), Clerk testing tokens (Clerk's own testing feature — our approach is provider-agnostic), visual regression testing infrastructure, CI pipeline integration (future phase).

---

### 1. Overview

The design follows a **provider pattern**: a thin interface defines the auth contract, and two implementations exist — `ClerkAuthProvider` (production) and `MockAuthProvider` (E2E/testing). The active provider is selected at build time via `NEXT_PUBLIC_AUTH_MODE` (frontend) and `SPRING_PROFILES_ACTIVE` (backend).

The core abstractions:

1. **AuthProvider Interface** — Defines the server-side auth contract: `getAuthContext()` (returns org/user/role), `getAuthToken()` (returns JWT string), `getCurrentUserEmail()` (returns current user's email). All 44 files that currently call `auth()` will call these functions instead.
2. **ClientAuthProvider** — React context that wraps the app and provides client-side auth state: current user info, org membership data. In production, wraps `ClerkProvider`. In mock mode, reads from seed data / mock IDP.
3. **Mock IDP Container** — A lightweight Node.js service (~200 lines) that serves a JWKS endpoint and issues JWTs on demand. The backend validates these JWTs identically to Clerk JWTs — no backend code changes beyond a YAML property override. See [ADR-085](../adr/ADR-085-auth-provider-abstraction.md).
4. **Boot-Seed Service** — A container that runs after the backend starts, calling `/internal/*` endpoints to provision a tenant, sync members, and create sample data. Mirrors the exact production webhook flow. See [ADR-086](../adr/ADR-086-mock-idp-strategy.md).
5. **Docker Compose E2E Stack** — A self-contained `docker-compose.e2e.yml` that brings up Postgres, LocalStack, mock IDP, backend, frontend, and boot-seed as a single command. See [ADR-087](../adr/ADR-087-e2e-seed-strategy.md).

---

### 2. Auth Provider Abstraction (Frontend)

#### 2.1 Server-Side Auth Interface

The abstraction replaces direct `@clerk/nextjs/server` imports with provider-agnostic functions. The return types are platform-owned interfaces, not Clerk types.

**Types** (`lib/auth/types.ts`):

| Type | Fields | Purpose |
|---|---|---|
| `AuthContext` | `orgId: string`, `orgSlug: string`, `orgRole: string`, `userId: string` | Replaces `auth()` destructure |
| `AuthUser` | `firstName: string \| null`, `lastName: string \| null`, `email: string`, `imageUrl: string \| null` | Replaces `currentUser()` and `useUser()` |
| `OrgMemberInfo` | `id: string`, `role: string`, `email: string`, `name: string` | Replaces `useOrganization().memberships` |

**Server functions** (`lib/auth/server.ts`):

| Function | Signature | Replaces |
|---|---|---|
| `getAuthContext()` | `() => Promise<AuthContext>` | `auth()` destructure (44 files) |
| `getAuthToken()` | `() => Promise<string>` | `auth().getToken()` (lib/api.ts + 1 route handler) |
| `getCurrentUserEmail()` | `() => Promise<string \| null>` | `currentUser().primaryEmailAddress` (2 files) |
| `requireRole(role)` | `(role: 'admin' \| 'owner' \| 'any') => Promise<void>` | Manual `orgRole` checks (convenience) |

**Provider selection** (`lib/auth/server.ts`):

```typescript
const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || 'clerk'

export async function getAuthContext(): Promise<AuthContext> {
  if (AUTH_MODE === 'mock') return mockGetAuthContext()
  return clerkGetAuthContext()
}
```

**Design decisions**:

- **Build-time provider selection**: `NEXT_PUBLIC_AUTH_MODE` is a build-time env var (Next.js inlines `NEXT_PUBLIC_*` at build). This means the mock code is tree-shaken out of production builds entirely. No dead code in production bundles.
- **No runtime provider switching**: Switching providers requires a rebuild. This is intentional — auth is a fundamental infrastructure choice, not a runtime toggle.
- **`has()` permission check excluded from abstraction**: Only 1 file uses `auth().has()` for plan-based checks. This is Clerk-specific functionality (plan metadata stored in Clerk). In mock mode, plan checks fall back to a simple env var or seed data lookup. This is handled in the mock provider, not the interface.
- **`clerkClient()` not abstracted**: The 2 files using `clerkClient()` (invitation sending + webhook user fetch) perform Clerk-specific operations. In mock mode, invitations are not available (members are pre-seeded) and webhook handlers are not called. These files retain their Clerk import with a guard: `if (AUTH_MODE === 'mock') return`.

#### 2.2 Client-Side Auth Context

**Provider component** (`lib/auth/client/auth-provider.tsx`):

Wraps the app in `app/layout.tsx`. In Clerk mode, renders `<ClerkProvider>`. In mock mode, renders a `<MockAuthProvider>` that provides:

| Hook | Returns | Mock Source |
|---|---|---|
| `useAuthUser()` | `AuthUser` | Reads from mock IDP `/userinfo` or hardcoded seed |
| `useOrgMembers()` | `OrgMemberInfo[]` | Calls backend `/api/members` (same as production) |
| `useSignOut()` | `() => void` | Clears mock auth cookie, redirects to mock login |

**Clerk UI component replacements**:

| Clerk Component | Mock Replacement | Scope |
|---|---|---|
| `<SignIn>` | `<MockLoginPage>` — user picker dropdown + "Sign In" button | 1 file |
| `<SignUp>` | Hidden in mock mode (users are pre-seeded) | 1 file |
| `<UserButton>` | `<MockUserButton>` — shows name, avatar, sign-out | 1 file |
| `<OrganizationSwitcher>` | `<MockOrgSwitcher>` — shows current org (single-org in E2E) | 1 file |
| `<CreateOrganization>` | Hidden in mock mode | 1 file |

**Design decisions**:

- **`useOrganization()` replaced with backend API call**: The 3 team management components currently query Clerk directly for member lists. In mock mode (and arguably in production), this data should come from the backend `/api/members` endpoint. This eliminates a direct Clerk dependency from the team management UI. The refactor is optional for phase 1 but recommended.
- **Mock login page**: A simple dropdown of seeded users (e.g., "Alice (Owner)", "Bob (Admin)", "Carol (Member)"). Selecting a user calls the mock IDP's `/token` endpoint, stores the JWT in a cookie, and redirects to `/dashboard`. This is the entry point for Playwright tests and agent automation.

#### 2.3 Middleware

The middleware (`proxy.ts`) currently uses `clerkMiddleware()` for route protection and org sync. In mock mode:

- **Route protection**: Mock middleware reads the auth cookie. If present and valid (JWT signature checked against mock JWKS), the request proceeds. If absent, redirects to `/mock-login`.
- **Org sync**: Mock middleware extracts `orgSlug` from the URL and `orgId` from the JWT claims. No Clerk API call needed.
- **Implementation**: A `createAuthMiddleware()` function returns either `clerkMiddleware()` or `mockMiddleware()` based on `AUTH_MODE`.

---

### 3. Mock IDP Container

A lightweight Node.js service that replaces Clerk's token infrastructure for E2E testing. See [ADR-086](../adr/ADR-086-mock-idp-strategy.md).

#### 3.1 Endpoints

| Method | Path | Purpose | Request | Response |
|---|---|---|---|---|
| GET | `/.well-known/jwks.json` | JWKS endpoint for JWT signature verification | — | `{ keys: [{ kty, n, e, kid, use, alg }] }` |
| POST | `/token` | Issue a signed JWT with configurable claims | `{ userId, orgId, orgSlug, orgRole }` | `{ access_token, token_type, expires_in }` |
| GET | `/userinfo/:userId` | Mock user profile | — | `{ id, firstName, lastName, email, imageUrl }` |

#### 3.2 JWT Structure

The mock IDP issues JWTs in **Clerk v2 format** — identical claim structure to production:

```json
{
  "sub": "user_e2e_alice",
  "iss": "http://mock-idp:8090",
  "aud": "docteams-e2e",
  "iat": 1708000000,
  "exp": 1708086400,
  "o": {
    "id": "org_e2e_test",
    "rol": "owner",
    "slg": "e2e-test-org"
  }
}
```

**Design decisions**:

- **RSA key pair generated at build time**: The Dockerfile generates an RSA-2048 key pair during the build. The public key is served as JWKS. The private key signs tokens. No secrets management needed — these are test-only keys.
- **Clerk v2 claim format preserved**: The mock JWT uses the same nested `"o"` map structure that Clerk uses. This means `ClerkJwtAuthenticationConverter` and `ClerkJwtUtils` in the backend work unmodified. Zero backend code changes.
- **Stateless**: The mock IDP has no database. User profiles are hardcoded from a seed config file baked into the container. Token issuance is a pure function (claims in → signed JWT out).
- **No OIDC discovery**: Only the JWKS endpoint is implemented. Spring Security's `jwk-set-uri` property points directly at it — no `issuer-uri` validation in E2E mode.

#### 3.3 Seed Users

The mock IDP ships with a default user set:

| User ID | Name | Email | Default Role |
|---|---|---|---|
| `user_e2e_alice` | Alice Owner | alice@e2e-test.local | owner |
| `user_e2e_bob` | Bob Admin | bob@e2e-test.local | admin |
| `user_e2e_carol` | Carol Member | carol@e2e-test.local | member |

Additional users can be added via environment variable (`MOCK_USERS` JSON array) for test scenarios requiring more members.

---

### 4. Backend Configuration

#### 4.1 E2E Profile

A new Spring profile (`e2e`) overrides the JWKS endpoint:

**`application-e2e.yml`**:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: ${MOCK_IDP_JWKS_URI:http://mock-idp:8090/.well-known/jwks.json}
          # No issuer-uri — skip issuer validation for mock tokens

internal:
  api:
    key: ${INTERNAL_API_KEY:e2e-test-key}
```

**Design decisions**:

- **No backend code changes**: The `SecurityConfig`, `ClerkJwtAuthenticationConverter`, `TenantFilter`, and `MemberFilter` all work identically. The only change is where the public key comes from. This validates the existing backend design — it's properly decoupled from Clerk's infrastructure.
- **Issuer validation disabled**: In production, `issuer-uri` validates the JWT's `iss` claim against Clerk's issuer URL. In E2E mode, the mock IDP's issuer is `http://mock-idp:8090`, so issuer validation is skipped (only `jwk-set-uri` is set, not `issuer-uri`).

#### 4.2 No Changes Required

These backend components work unchanged with mock JWTs:

| Component | Why It Works |
|---|---|
| `ClerkJwtAuthenticationConverter` | Reads `o.rol` from JWT claims — same structure in mock JWT |
| `ClerkJwtUtils` | Reads `o.id`, `o.rol` from nested map — same structure |
| `TenantFilter` | Extracts `orgId` from JWT, looks up schema — mock org ID is in seed data |
| `MemberFilter` | Resolves member by `sub` claim — mock user IDs match seeded members |
| `ApiKeyAuthFilter` | Uses `X-API-KEY` header — no JWT involved |
| `CustomerAuthFilter` | Uses custom portal JWT — no Clerk involved |

---

### 5. Docker Compose E2E Stack

#### 5.1 Service Topology

```
┌─────────────────────────────────────────────────────────┐
│  docker-compose.e2e.yml                                 │
│                                                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐              │
│  │ postgres │  │localstack│  │ mock-idp │              │
│  │  :5432   │  │  :4566   │  │  :8090   │              │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘              │
│       │              │             │                    │
│  ┌────┴──────────────┴─────────────┴─────┐              │
│  │           backend (:8080)             │              │
│  │  profile: e2e                         │              │
│  │  jwk-set-uri → mock-idp              │              │
│  └────────────────┬──────────────────────┘              │
│                   │                                     │
│  ┌────────────────┴──────────────────────┐              │
│  │          frontend (:3000)             │              │
│  │  AUTH_MODE=mock                       │              │
│  │  MOCK_IDP_URL → mock-idp             │              │
│  └───────────────────────────────────────┘              │
│                                                         │
│  ┌───────────────────────────────────────┐              │
│  │          seed (run-once)              │              │
│  │  provisions tenant + syncs members    │              │
│  │  via /internal/* endpoints            │              │
│  └───────────────────────────────────────┘              │
└─────────────────────────────────────────────────────────┘
```

#### 5.2 Boot-Seed Sequence

The seed container runs after the backend is healthy and executes the production provisioning flow:

```
1. POST /internal/orgs/provision
   → Creates org in public schema
   → Creates tenant_<hash> schema
   → Runs Flyway V1–V35
   → Seeds packs (fields, templates, compliance, reports)

2. POST /internal/plans/sync
   → Upgrades org to PRO (removes member limits)

3. POST /internal/members/sync  (×3, one per seed user)
   → Creates member records in tenant schema
   → alice (owner), bob (admin), carol (member)

4. POST /api/customers  (with alice's JWT from mock IDP)
   → Creates a sample ACTIVE customer

5. POST /api/projects  (with alice's JWT)
   → Creates a sample project linked to customer
```

**Design decisions**:

- **Boot-seed over SQL dump**: The seed container calls the same REST/internal endpoints that production uses. This means seed data is always consistent with the current schema and validation rules. An SQL dump would break every time a migration changes. See [ADR-087](../adr/ADR-087-e2e-seed-strategy.md).
- **Seed container exits after completion**: It's a one-shot container (`restart: "no"`). The data persists in the Postgres volume.
- **Mock IDP provides tokens for seed API calls**: Steps 4-5 require authenticated API calls. The seed container calls `POST /token` on the mock IDP to get a valid JWT for alice, then uses that JWT as `Authorization: Bearer` header.

#### 5.3 File Layout

```
compose/
├── docker-compose.yml          # Existing dev stack (Postgres + LocalStack)
├── docker-compose.e2e.yml      # E2E stack (all services)
├── mock-idp/
│   ├── Dockerfile
│   ├── package.json
│   ├── src/
│   │   ├── index.ts            # Express server (~200 lines)
│   │   ├── keys.ts             # RSA key pair management
│   │   └── users.ts            # Seed user definitions
│   └── tsconfig.json
└── seed/
    ├── Dockerfile
    ├── seed.sh                 # Boot-seed script
    └── wait-for-backend.sh     # Health check poller
```

---

### 6. Playwright Integration

Once the E2E stack is running, Playwright tests authenticate via the mock IDP:

#### 6.1 Auth Fixture

```typescript
// e2e/fixtures/auth.ts
import { Page } from '@playwright/test'

const MOCK_IDP_URL = process.env.MOCK_IDP_URL || 'http://localhost:8090'

type SeedUser = 'alice' | 'bob' | 'carol'

const USERS: Record<SeedUser, { userId: string; orgId: string; orgRole: string; orgSlug: string }> = {
  alice: { userId: 'user_e2e_alice', orgId: 'org_e2e_test', orgRole: 'owner', orgSlug: 'e2e-test-org' },
  bob:   { userId: 'user_e2e_bob',   orgId: 'org_e2e_test', orgRole: 'admin', orgSlug: 'e2e-test-org' },
  carol: { userId: 'user_e2e_carol', orgId: 'org_e2e_test', orgRole: 'member', orgSlug: 'e2e-test-org' },
}

export async function loginAs(page: Page, user: SeedUser): Promise<void> {
  const res = await fetch(`${MOCK_IDP_URL}/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(USERS[user]),
  })
  const { access_token } = await res.json()

  await page.context().addCookies([{
    name: 'mock-auth-token',
    value: access_token,
    domain: 'localhost',
    path: '/',
  }])
}
```

#### 6.2 Test Example

```typescript
import { test, expect } from '@playwright/test'
import { loginAs } from './fixtures/auth'

test('admin can create a project', async ({ page }) => {
  await loginAs(page, 'alice')
  await page.goto('http://localhost:3000/org/e2e-test-org/projects')
  await page.getByRole('button', { name: 'New Project' }).click()
  await page.getByLabel('Project Name').fill('Playwright Test Project')
  await page.getByRole('button', { name: 'Create' }).click()
  await expect(page.getByText('Playwright Test Project')).toBeVisible()
})

test('member cannot access settings', async ({ page }) => {
  await loginAs(page, 'carol')
  await page.goto('http://localhost:3000/org/e2e-test-org/settings')
  await expect(page.getByText('Forbidden')).toBeVisible()
})
```

---

### 7. Migration Path (44-File Refactor)

The refactor from `@clerk/nextjs/server` to `@/lib/auth` is mechanical:

#### 7.1 Before / After

```diff
// Every server action, page, route handler:
- import { auth } from "@clerk/nextjs/server";
+ import { getAuthContext } from "@/lib/auth";

- const { orgRole, userId } = await auth();
+ const { orgRole, userId } = await getAuthContext();
```

```diff
// lib/api.ts:
- import { auth } from "@clerk/nextjs/server";
+ import { getAuthToken } from "@/lib/auth";

- const { getToken } = await auth();
- const token = await getToken();
+ const token = await getAuthToken();
```

```diff
// my-work/page.tsx, projects/[id]/page.tsx:
- import { currentUser } from "@clerk/nextjs/server";
+ import { getCurrentUserEmail } from "@/lib/auth";

- const user = await currentUser();
- const email = user?.primaryEmailAddress?.emailAddress;
+ const email = await getCurrentUserEmail();
```

#### 7.2 Refactor Strategy

1. Create `lib/auth/` with types, server functions, and Clerk provider (no behavior change)
2. Run find-replace across all 44 files (import + function call)
3. Verify: `pnpm build` + `pnpm test` (all 220+ tests must pass)
4. No domain logic changes — purely mechanical

#### 7.3 Files Unchanged

| File | Why |
|---|---|
| `app/api/webhooks/clerk/route.ts` | Webhook handler — Clerk-specific by nature, only runs in production |
| `lib/webhook-handlers.ts` | Uses `clerkClient()` — Clerk-specific, guarded by `AUTH_MODE` check |
| `app/(app)/org/[slug]/team/actions.ts` | Uses `clerkClient()` for invitations — guarded by `AUTH_MODE` check |

---

### 8. Implementation Phases

| Phase | Scope | Depends On | Estimated Effort |
|---|---|---|---|
| **A** | Auth abstraction layer (`lib/auth/`) + Clerk provider implementation. 44-file mechanical refactor. All existing tests must pass. | — | 1 epic (2-3 slices) |
| **B** | Mock IDP container (`compose/mock-idp/`). JWKS endpoint + `/token` + `/userinfo`. Dockerfile. | — | 1 slice |
| **C** | Backend `e2e` profile (`application-e2e.yml`). One YAML file. | — | Trivial (part of slice B) |
| **D** | Frontend mock provider (server + client). `MockLoginPage`, `MockUserButton`, mock middleware. | A | 1 epic (2 slices) |
| **E** | Docker Compose E2E stack + boot-seed container. Integration testing. | B, C, D | 1 slice |
| **F** | Playwright fixture + smoke tests. | E | 1 slice |

Phases A, B, and C are independent and can run in parallel. Phase D depends on A (the abstraction must exist before the mock provider can implement it). Phase E integrates everything. Phase F builds on E.

---

### 9. Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| 44-file refactor introduces bugs | Low | Medium | Mechanical change, full test suite catches regressions |
| Mock JWT claim format diverges from Clerk | Low | High | Mock IDP uses exact Clerk v2 structure; backend code is unchanged |
| Frontend mock provider misses an auth check | Medium | Low | E2E tests will fail visibly; mock login page makes it obvious |
| Docker Compose startup ordering | Medium | Low | Health checks + `depends_on` conditions; seed container retries |
| Clerk SDK upgrade breaks abstraction | Low | Low | Abstraction is thin (5 functions); Clerk changes are isolated to one file |
