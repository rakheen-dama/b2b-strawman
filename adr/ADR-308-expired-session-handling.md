# ADR-308: Expired-Session Handling Architecture — The Single Funnel

**Status**: Accepted

**Context**:

Even with lifetimes aligned ([ADR-307](ADR-307-session-lifetime-policy.md)), an authenticated user *will* eventually hit an expired session (30m idle or 10h max). Today only **one** path handles this gracefully: server-action 401s in `frontend/lib/api/client.ts:164-166` (`handleApiError` → `redirect("/sign-in")`), with the gateway redirect-to-Keycloak also mapped to `ApiError(401)` at `client.ts:96-99`. **Every other fetch entry-point leaks raw errors**: client-component fetches (`user-menu-bff.tsx:21-47` silent-fails to a fallback), polling/interval refetches, and the `/bff/me` server probe itself (`keycloak-bff.ts:29-46` throws on non-ok). The middleware (`middleware.ts:59-101`) only handles *no SESSION cookie* and the GAP-L-22 *user-mismatch* case — not generic expiry. The result is "clicking anything leads to errors," compounded because the `/sign-in` redirect target is a dangling 404 (fixed in [ADR-310](ADR-310-branded-auth-landing-strategy.md)).

This ADR decides the architecture for detecting and handling expiry consistently, and how it composes with the existing 401 path and the GAP-L-22 `KC_LAST_LOGIN_SUB` guard.

**Options Considered**:

1. **A single shared expiry funnel used by every entry-point (CHOSEN)** — One detector module (`lib/auth/expiry.ts`) exposes `isSessionExpired(res)` plus a server-side redirect (`redirectToReLogin`) and a client-side hard-navigation (`clientRedirectToReLogin`). *Every* fetch entry-point — server-side `apiRequest`, the `/bff/me` probe, middleware, client-component fetches, polling — routes its auth-failure through it. The funnel clears client-held auth/UI state and redirects to the branded `/sign-in?reason=expired&returnTo=…`. The existing `client.ts` 401 path is rewired to call the funnel (instead of a bare `redirect("/sign-in")`); GAP-L-22 stays as the orthogonal user-mismatch branch in middleware.
   - Pros: One detection rule, one redirect shape, one place to audit — no per-call drift; covers the enumerated entry-points by construction; composes cleanly with GAP-L-22 (which only fires when `KC_LAST_LOGIN_SUB` is present and the subject differs, a disjoint trigger); easy to unit-test per entry-point against one contract.
   - Cons: Touches several files to thread the funnel through; must enumerate entry-points against source (server context vs client context need two redirect mechanisms — `next/navigation` redirect vs `window.location`).

2. **Per-call handling (extend the status quo)** — Add ad-hoc 401 handling at each call site as needed.
   - Pros: Incremental; touches only the sites currently observed to leak.
   - Cons: This *is* the current architecture and it is exactly why coverage is incomplete; new fetch sites will keep leaking; no single source of truth; impossible to assert "every entry-point is covered." Directly contradicts the requirement that handling be comprehensive, not per-call.

3. **A global React error boundary / Next.js `error.tsx`** — Catch thrown auth errors at a top-level boundary and redirect from there.
   - Pros: One catch site; idiomatic React.
   - Cons: Only catches errors that *render-throw* in the React tree — it misses server-action redirects, the middleware path (runs before React), polling that swallows its own errors, and `fetch` results that are handled-but-wrong rather than thrown; an error boundary cannot run middleware-stage redirects; conflates auth-expiry with all other render errors. Incomplete by nature for a cross-cutting fetch concern.

**Decision**: Option 1 — a single shared expiry funnel that every fetch entry-point, the middleware, and the `/bff/me` probe route through, redirecting to the branded `/sign-in?reason=expired` with a validated return-to.

**Rationale**:

1. **Comprehensiveness is the requirement, and only a single funnel delivers it.** The defect is *incomplete coverage*; the fix must be a place every path provably goes through, enumerated against source (`client.ts`, `keycloak-bff.ts`, `middleware.ts`, client components, polling) — not more per-call patches.
2. **Server vs client need two mechanisms but one contract.** Server context (server actions, `/bff/me` probe) redirects via `next/navigation`; client context (client components, polling) hard-navigates via `window.location`. The funnel exposes both behind one detector so the *decision* ("is this expired? where do we send them?") lives in one module.
3. **It composes with, not replaces, the existing guards.** The `client.ts` 401 path becomes a caller of the funnel; GAP-L-22 (`KC_LAST_LOGIN_SUB` user-mismatch, `middleware.ts:74-143`) stays a separate branch — its trigger (cookie present + subject mismatch) is disjoint from generic expiry (no/stale session), so they never fight. The funnel's redirect target reuses the same `/sign-in` route GAP-L-22 and login flows use.
4. **The `/bff/me` probe must not be the leak.** Making the probe's failure go through the funnel (rather than throwing raw, `keycloak-bff.ts:42`) closes the most-hit path, since `/bff/me` fires on essentially every page load.
5. **Testability.** Each entry-point class gets a unit test that mocks a 401 and asserts the branded redirect with return-to — coverage is then an assertion, not a hope.

**Consequences**:
- Positive: Raw auth errors no longer leak to the UI; one detector to audit and extend; new fetch sites adopt the funnel by convention.
- Positive: Clean composition with GAP-L-22 and the aligned lifetimes — the now-predictable idle expiry ([ADR-307](ADR-307-session-lifetime-policy.md)) lands on a branded page.
- Negative: Several frontend files change to thread the funnel; the enumeration of entry-points must be kept honest (a new SSE/streaming path, none today, must adopt it).
- Negative: Two redirect mechanisms (server redirect vs client navigation) to maintain and test.
- Neutral: The funnel depends on the real `/sign-in` route existing ([ADR-310](ADR-310-branded-auth-landing-strategy.md)) and a safe return-to ([ADR-309](ADR-309-return-to-redirect-safety.md)).
- Related: [ADR-307](ADR-307-session-lifetime-policy.md) (makes expiry predictable), [ADR-309](ADR-309-return-to-redirect-safety.md) (return-to safety), [ADR-310](ADR-310-branded-auth-landing-strategy.md) (the `/sign-in` landing), GAP-L-22 (`GatewaySecurityConfig.java:116-152`, the orthogonal user-mismatch guard).
