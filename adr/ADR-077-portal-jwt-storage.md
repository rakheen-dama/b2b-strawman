# ADR-077: Client-Side JWT Storage for Portal

**Status**: Accepted
**Date**: 2026-02-20

**Context**:

The customer portal authenticates via magic link tokens exchanged for short-lived JWTs (1-hour TTL, issued by the Spring Boot backend using HS256). The portal frontend needs to store these JWTs and include them in API requests. The storage mechanism affects security, user experience, and implementation complexity.

The portal is a read-heavy, low-interactivity application. Clients typically visit to check a document, review an invoice, or leave a comment. Sessions are short (minutes, not hours) and infrequent (weekly or monthly). The portal has no destructive operations in v1 -- it is read-only except for comment posting.

**Options Considered**:

1. **localStorage (chosen)** -- Store the portal JWT in `localStorage` under a known key. The frontend reads it on page load and includes it as a `Bearer` token in API requests. Cleared on logout or expiry.
   - Pros: Simple implementation (no backend changes, no cookie configuration); works with any deployment topology (no same-site cookie concerns); JWT is accessible to JavaScript for expiry checking and claims parsing; no BFF layer required; straightforward 401 handling (clear storage, redirect to login).
   - Cons: Vulnerable to XSS -- if an attacker injects JavaScript, they can read the JWT; token persists across browser sessions until explicitly cleared or expired; cannot be scoped to specific paths or origins at the storage level.

2. **HTTP-only cookies via BFF (backend-for-frontend)** -- The backend sets an HTTP-only, Secure, SameSite cookie containing the JWT during the exchange step. The portal frontend never sees the token directly. API requests automatically include the cookie.
   - Pros: JWT is inaccessible to JavaScript (immune to XSS token theft); cookie scoping (SameSite, Secure, Path) provides defense in depth; industry best practice for high-security applications; no client-side token management code.
   - Cons: Requires a BFF layer or backend cookie-setting endpoint (the Spring Boot backend currently returns the JWT in the response body); CORS and cookie configuration complexity (SameSite=Strict may block cross-origin requests if portal and API are on different domains); CSRF protection required (cookies are auto-sent); cannot easily inspect token claims client-side for expiry checks; deployment topology constraints (portal app and API must share a cookie domain or use a BFF proxy).

3. **sessionStorage** -- Store the JWT in `sessionStorage`, which is cleared when the browser tab closes.
   - Pros: Same simplicity as localStorage; automatically cleared on tab close (shorter exposure window); still accessible to JavaScript for claims parsing.
   - Cons: Same XSS vulnerability as localStorage; token lost when user opens a new tab (they must re-authenticate, degrading UX); inconsistent behavior across browsers for "restore session" features; not meaningfully more secure than localStorage given the 1-hour TTL.

**Decision**: Option 1 -- localStorage.

**Rationale**:

The security risk profile of the portal is low. The JWT grants read-only access to a single customer's projects, documents, and invoices. Comment posting is the only write operation, and it is content-limited (2000 characters). An XSS attack that steals the portal JWT gains access to data the client already has access to -- there are no privilege escalation opportunities and no destructive operations.

The 1-hour TTL limits the attack window. Even if a JWT is stolen via XSS, it expires quickly and cannot be refreshed. The attacker gains at most one hour of read access to one customer's data. Compare this to the admin frontend, where Clerk manages authentication with refresh tokens and broader permissions -- the risk profile is fundamentally different.

The BFF approach (Option 2) would require either extending the Spring Boot backend to set cookies (adding CORS and CSRF complexity) or introducing a new proxy layer between the portal and the backend. This infrastructure cost is not justified by the marginal security improvement for a read-only portal with short-lived tokens. If the portal adds destructive operations (payments, approvals) in a future phase, migrating to HTTP-only cookies via a BFF is straightforward -- the auth layer is encapsulated in `lib/auth.ts` and `lib/api-client.ts`.

sessionStorage (Option 3) offers no meaningful security improvement over localStorage given the 1-hour TTL, but degrades UX by losing the session on tab close.

**Consequences**:

- Positive:
  - Simple implementation -- no backend changes, no cookie configuration, no BFF layer
  - JWT accessible for client-side expiry checks and claims parsing (orgId, customerId)
  - Works with any deployment topology (portal and API can be on different domains)
  - Straightforward 401 handling (clear localStorage, redirect to login)

- Negative:
  - XSS can steal the JWT (mitigated by: 1-hour TTL limits exposure, read-only access limits impact, standard XSS prevention via CSP headers and input sanitization)
  - Token persists across browser sessions until expiry (mitigated by 1-hour TTL -- stale tokens are detected and cleared on page load)
  - If the portal adds destructive operations in a future phase, migration to HTTP-only cookies should be prioritized (mitigated by auth layer encapsulation in `lib/auth.ts`)
