# ADR-309: Return-To Redirect Safety — Open-Redirect Guard

**Status**: Accepted

**Context**:

When the expiry funnel ([ADR-308](ADR-308-expired-session-handling.md)) bounces a user out, the requirement is to capture where they were and land them back there after re-login, so the experience is "sign in and resume," not "sign in and start over at the dashboard." This means carrying a `returnTo` value through the Keycloak round-trip (`/sign-in?returnTo=…` → `/oauth2/authorization/keycloak` → back). A return-to that is reflected into a redirect without validation is a textbook **open-redirect** vulnerability: an attacker crafts `…/sign-in?returnTo=https://evil.example/phish`, the user authenticates against the *real* Kazi/Keycloak, and is then redirected to the attacker's page — a credible phishing/token-relay vector. This is heightened in a multi-tenant SaaS where the post-auth context is trusted.

This ADR decides how return-to is captured and, critically, how it is validated before any redirect.

**Options Considered**:

1. **Capture path-only + allowlist/same-origin validation (CHOSEN)** — Capture `pathname + search` only (never an absolute URL). A single `safeReturnTo(raw)` chokepoint enforces: starts with exactly one `/` (reject `//host` and `/\host`), contains no scheme (`http:`/`https:`/`javascript:`/`data:`), is a same-origin path, and matches a small allowlist of app path prefixes (`/dashboard`, `/org/`, `/platform-admin`, `/create-org`). Anything failing → default `/dashboard`. Every redirect that consumes return-to passes through this one function.
   - Pros: Cannot emit an external URL by construction; one auditable chokepoint; no server-side state or crypto to manage; trivial to unit-test (reject `http://evil`, `//evil`, `/\evil`, `javascript:…`; accept `/dashboard`, `/org/x/...`); allowlist matches the actual authenticated route surface.
   - Cons: Allowlist must be kept current as route prefixes evolve (a missed prefix degrades to `/dashboard` — fails safe, not open); slightly stricter than pure same-origin (intentional defence-in-depth).

2. **Signed/encrypted return-to token** — Sign or encrypt the return-to server-side (e.g. an HMAC param) so only Kazi-issued values are honoured.
   - Pros: Tamper-evident; could carry richer state.
   - Cons: Requires a signing secret + verification path on a flow that doesn't otherwise need server state; adds key-management and rotation concerns; does **not** by itself prevent an open-redirect (a signed *external* URL is still external unless you *also* validate the value) — so you end up needing the allowlist anyway; over-engineered for "remember a path."

3. **No return-to — always land on `/dashboard`** — Drop the deep-link; every re-login goes to the dashboard.
   - Pros: Zero open-redirect surface; simplest possible.
   - Cons: Defeats the stated UX goal ("resume where you were"); for a deep workflow (a half-edited matter) this is a real productivity hit; throws away cheap value to avoid a risk that option 1 already neutralises.

**Decision**: Option 1 — capture path-only and validate every return-to through a single `safeReturnTo` allowlist/same-origin guard, defaulting to `/dashboard` on any failure.

**Rationale**:

1. **Path-only + allowlist makes external redirects structurally impossible.** Because the value is required to be a same-origin path matching a known prefix, there is no input that yields an off-site redirect — the guard fails safe to `/dashboard`. This is the minimal mechanism that fully closes the open-redirect, satisfying the requirement to never reflect an arbitrary external URL.
2. **One chokepoint is auditable; scattered checks are not.** A single `safeReturnTo` used by `/sign-in`, the middleware capture, and any client funnel redirect means the security property is reviewed in one place and unit-tested once.
3. **No crypto for a path.** Signing (option 2) adds secret management without removing the need to validate the decoded value, so it is strictly more complexity for no additional safety here. The BFF model already keeps secrets server-side; we don't add another.
4. **The UX value is real and cheap to keep safely.** In a professional-services tool the resume-where-you-were behaviour matters; option 1 preserves it without the risk, so dropping it (option 3) is an unnecessary regression.
5. **Fails safe, multi-tenant-aware.** An unrecognised prefix degrades to `/dashboard` rather than erroring or redirecting blindly — appropriate for a multi-tenant app where the org context resolves server-side via `/bff/me` after landing.

**Consequences**:
- Positive: Open-redirect is eliminated at one auditable chokepoint; resume-where-you-were UX retained.
- Positive: No signing keys or server state added to the auth flow.
- Negative: The allowlist of path prefixes must be maintained as routes evolve; a forgotten prefix silently degrades (fails safe, but a UX papercut).
- Neutral: Return-to is path-only by design — it cannot carry cross-origin or rich state (acceptable; none needed).
- Related: [ADR-308](ADR-308-expired-session-handling.md) (the funnel that captures return-to), [ADR-310](ADR-310-branded-auth-landing-strategy.md) (`/sign-in` consumes the validated return-to), [ADR-307](ADR-307-session-lifetime-policy.md) (why re-logins happen at all).
