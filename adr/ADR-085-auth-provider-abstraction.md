# ADR-085: Auth Provider Abstraction

**Status**: Proposed

**Context**:

The DocTeams frontend currently imports authentication functions directly from `@clerk/nextjs/server` in 44 server-side files, and uses Clerk React components and hooks in 5 client-side components. The central API client (`lib/api.ts`) obtains JWTs via Clerk's `getToken()` for all backend API calls. This tight coupling creates two problems: (1) Clerk's Cloudflare Turnstile CAPTCHA on sign-in pages prevents headless browser automation, blocking both agent-driven UI testing and Playwright E2E tests; (2) switching to a different auth provider would require modifying 50+ files simultaneously.

The actual API surface used from Clerk is narrow — `auth()` returns `{ orgId, orgSlug, orgRole, userId, getToken }`, and 95% of call sites destructure only `orgRole`. The remaining touchpoints are `currentUser()` (2 files, email lookup only), `clerkClient()` (2 files, invitation sending and webhook user fetch), and client hooks `useUser()` / `useOrganization()` (5 components for display name, avatar, and member lists).

**Options Considered**:

1. **Auth provider abstraction layer (chosen)** — Create a `lib/auth/` module that exports provider-agnostic functions (`getAuthContext()`, `getAuthToken()`, `getCurrentUserEmail()`). Two implementations: `ClerkAuthProvider` (production) and `MockAuthProvider` (E2E). Provider selected at build time via `NEXT_PUBLIC_AUTH_MODE`.
   - Pros: Clean separation of concerns; all 44 files import from a platform-owned module; Clerk upgrade breakage is isolated to one file; mock provider enables E2E testing without touching domain code; reduces lock-in for future provider changes.
   - Cons: 44-file refactor (mechanical but wide); adds an indirection layer; new developers must learn the abstraction.

2. **Module aliasing (mock `@clerk/nextjs` at import level)** — Use Next.js webpack aliases to redirect `@clerk/nextjs/server` to a mock module when `AUTH_MODE=mock`.
   - Pros: Zero changes to existing 44 files — they keep importing `@clerk/nextjs/server`; fastest to implement.
   - Cons: Mocking an external SDK's API surface is fragile — Clerk version upgrades can silently break the mock; the mock must mirror Clerk's exact type signatures; no compile-time safety if Clerk changes its exports; doesn't reduce lock-in (all files still "think" they're using Clerk).

3. **Clerk testing tokens** — Use Clerk's built-in testing mode (`CLERK_TESTING_TOKEN` header) to bypass CAPTCHA in test environments.
   - Pros: No code changes; officially supported by Clerk; works with real Clerk infrastructure.
   - Cons: Still depends on Clerk's external service (network calls, rate limits, potential outages during test runs); doesn't reduce lock-in; requires a Clerk test instance with specific configuration; doesn't help with agent automation (agents need programmatic token issuance, not Clerk's test mode).

4. **Backend-only mock (bypass frontend auth entirely)** — Skip frontend auth and test the backend directly via API calls with mock JWTs.
   - Pros: Simplest approach; no frontend changes; backend mock is trivial (one YAML property).
   - Cons: Doesn't enable UI testing at all — the stated goal; agents still can't step through the web UI; Playwright tests can't run.

**Decision**: Option 1 — auth provider abstraction layer.

**Rationale**:

The primary goal is enabling agents and Playwright tests to drive the full UI, which requires both frontend and backend auth bypass. Option 4 doesn't achieve this. Option 3 depends on an external service during tests, which contradicts the goal of a self-contained E2E stack. Option 2 is tempting for speed but creates a maintenance hazard — Clerk's SDK is actively developed, and any export change silently breaks the mock with no compile-time warning.

Option 1's 44-file refactor is mechanical (import path change + function rename) and the project's test suite (220+ frontend tests) provides confidence. The abstraction is thin (5 functions) and the interface is stable — auth context is fundamentally `{ orgId, orgRole, userId }` regardless of provider. The indirection cost is negligible. The lock-in reduction is a strategic bonus: if the project ever evaluates Auth0, Supabase Auth, or self-hosted auth, the migration is one provider file instead of 50+ files.

**Consequences**:

- Positive:
  - All server-side auth imports route through `@/lib/auth` — single point of provider configuration
  - Clerk SDK changes are isolated to `lib/auth/providers/clerk.ts` — one file
  - Mock provider enables headless browser testing without external dependencies
  - Platform owns its auth types (`AuthContext`, `AuthUser`) — no Clerk type leakage into domain code
  - Future auth provider migration is a single file swap

- Negative:
  - 44-file refactor creates a large diff (though each change is a 2-line modification)
  - Adds one level of indirection for auth calls
  - Two providers must be maintained (though mock provider is ~50 lines)

- Neutral:
  - `clerkClient()` usage (2 files) is not abstracted — these are Clerk-specific operations (invitations, user fetch) that don't apply in mock mode
  - Webhook handler (`app/api/webhooks/clerk/route.ts`) retains direct Clerk import — webhooks are Clerk-specific by nature
  - `AUTH_MODE` is a build-time decision, not runtime — switching requires a rebuild
