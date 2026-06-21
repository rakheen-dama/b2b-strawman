# ADR-310: Branded Auth Landing Strategy — First-Party Routes vs Keycloak Theme Pages

**Status**: Accepted

**Context**:

Several auth events currently dump the user on an unstyled or stock-Keycloak page — the "whitelabel leak." Three classes of landing exist: (1) **expiry/sign-in bounce** — the funnel ([ADR-308](ADR-308-expired-session-handling.md)) redirects to `/sign-in`, which **does not exist as a route** (`client.ts:79,166`, `mock-login/page.tsx:9`, whitelisted at `middleware.ts:9`) → a 404; (2) **post-logout** — the gateway redirects to frontend root `/` (`GatewaySecurityConfig.java:105-110`), an unstyled landing; (3) **failures inside Keycloak** — error/info/SSO-logout pages rendered by Keycloak itself (`compose/keycloak/theme/src/login/pages/{Error,Info}.tsx`), currently stock/whitelabel.

The question: where does each failure class land — a first-party Next.js route, or a Keycloak theme page? The redirect targets for (1) and (2) already point at the frontend, while (3) physically renders inside Keycloak.

**Options Considered**:

1. **Hybrid — first-party routes for app-side landings, Keycloak theme pages for in-Keycloak failures (CHOSEN)** — Create first-party branded routes `/sign-in` (expiry/first-time) and `/signed-out` (post-logout) under `frontend/app/(auth)/`, since those redirects already target the frontend. Brand the Keycloak theme pages (`Error.tsx`, `Info.tsx`, logout/SSO-logout) for failures that occur *inside* Keycloak where the frontend has no control. Each failure class lands on the surface that physically renders it.
   - Pros: Each page lives where it is actually served — no contrived redirect to force an in-KC error onto a frontend route; first-party routes get the full app design system (Shadcn, slate+teal) and React interactivity (reason banner, return-to handling); KC theme pages cover the genuinely KC-rendered cases; matches the existing redirect topology (gateway already returns the browser to the frontend post-logout).
   - Cons: Two branding toolchains to keep visually consistent (Next.js/Tailwind vs Keycloakify/TSX) — requires a shared palette/logo discipline (the §11.4.6 consistency check).

2. **Everything via Keycloak theme pages** — Route all landings (including post-expiry and post-logout) to Keycloak-rendered theme pages.
   - Pros: One branding toolchain (Keycloakify); all auth surfaces in one place.
   - Cons: The expiry/sign-in and post-logout redirects target the *frontend*, so forcing them into Keycloak means adding contrived KC pages and redirects; loses the app design system and React interactivity (the reason banner, return-to wiring, "Sign in again" CTA are natural in Next.js, awkward in FTL/Keycloakify); the user sees a context switch to the IdP for what is an app-side event.

3. **Everything via first-party frontend routes** — Try to render even Keycloak's own error/info pages as frontend routes.
   - Pros: One design system; maximal control.
   - Cons: Impossible for failures that occur *inside* Keycloak before any redirect to the frontend (e.g. an OIDC error on the KC error page, an SSO-logout confirmation) — Keycloak renders those itself; you cannot intercept them as Next.js routes. Leaves the actual whitelabel leak (error/info) unfixed.

**Decision**: Option 1 — a hybrid: first-party branded `/sign-in` and `/signed-out` routes for app-side landings, and branded Keycloak theme pages (`Error.tsx`, `Info.tsx`, logout) for in-Keycloak failures.

**Rationale**:

1. **Land each failure where it is actually rendered.** The expiry and post-logout redirects already return the browser to the frontend, so a first-party route is the natural, control-rich home; KC error/info pages render inside Keycloak and cannot be intercepted, so they must be themed in Keycloakify. Option 1 is the only split that matches reality.
2. **First-party routes unlock the required UX.** The reason banner (`?reason=expired` vs first-time), the validated return-to ([ADR-309](ADR-309-return-to-redirect-safety.md)), and the "Sign in again" CTA are first-class in a Next.js server component with the app's Shadcn design system — they would be clumsy as FTL/Keycloakify pages.
3. **It fixes the dangling `/sign-in` 404 directly.** Creating the real `/sign-in` route is itself part of "clicking leads to errors"; this option mandates it.
4. **The only residual cost is consistency, which is a discipline not a blocker.** Two toolchains render auth surfaces; a shared palette (slate+teal), logo, and type, plus the §11.4.6 consistency check and browser verification, keep them reading as one product. [ADR-312](ADR-312-visible-brand-rebrand-scope.md) governs the KC-side brand.
5. **Aligns with the redirect topology already in place.** Post-logout the gateway returns to the frontend (`GatewaySecurityConfig.java:108`); pointing it at `/signed-out` (a frontend route) is a one-line change, and `post.logout.redirect.uris` already wildcards `/*` (`realm-export.json:63`).

**Return-to round-trip mechanism.** The gateway success handler uses `alwaysUseDefaultTargetUrl=true` (`GatewaySecurityConfig.java:113`), so it always lands on `/dashboard` and ignores Spring's SavedRequest — `returnTo` cannot survive the login redirect server-side. The chosen approach (no gateway change) is for the `/sign-in` route to persist the validated `returnTo` in the browser (`sessionStorage`) before initiating the Keycloak login, then read it back and navigate after the gateway lands on `/dashboard`, clearing it. Removing `alwaysUseDefaultTargetUrl` or threading `returnTo` through KC `state` is rejected for now because it touches the gateway and all login flows. The value is allowlist-validated ([ADR-309](ADR-309-return-to-redirect-safety.md)) before it is stored and before navigation.

**Consequences**:
- Positive: Every auth landing is branded; the `/sign-in` 404 is gone; app-side pages get full design-system + interactivity; in-KC failures are covered where they live.
- Positive: Minimal redirect rewiring — frontend targets were already frontend.
- Negative: Two branding toolchains require ongoing visual-consistency discipline (browser-verified, not inferred).
- Neutral: `/signed-out` works under the existing `post.logout.redirect.uris` wildcard — no Keycloak allowlist change, but it must be verified across dev/`app-dev`/prod origins.
- Related: [ADR-308](ADR-308-expired-session-handling.md) (the funnel that redirects to `/sign-in`), [ADR-309](ADR-309-return-to-redirect-safety.md) (return-to consumed by `/sign-in`), [ADR-312](ADR-312-visible-brand-rebrand-scope.md) (the Keycloak-side visible-brand rebrand), [ADR-311](ADR-311-change-password-approach.md) (change-password via `kc_action=UPDATE_PASSWORD`, rendered by the branded login theme).
