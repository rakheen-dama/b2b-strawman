# Phase 79 — Session Lifecycle & Auth Experience Hardening

> **Status: READY for `/architecture`.** Scope agreed in `/ideate` (2026-06-21). This is a foundation-quality, fork-friendly phase: every vertical fork inherits graceful session expiry, branded auth surfaces, in-app password management, and a consistently-branded Keycloak theme. Root-cause map verified against source 2026-06-21 (see System Context).
>
> **Naming note:** the Phase 78 doc referred to a future "Phase 79 (Phase C) — skill pack". That skill pack was instead built in the external `../claude-for-legal-sa` fork repo (PRs #1–#7, 2026-06-20) and is **not** a numbered phase in this repo. This Phase 79 is a distinct, in-repo platform-hardening phase and does not touch the skill pack.

## System Context

Kazi is a multi-tenant B2B practice-management platform (Next.js 16 frontend + portal, Spring Boot 4 backend, Keycloak OIDC) with schema-per-tenant isolation. Authentication uses a **BFF (backend-for-frontend) pattern**: the Spring Cloud Gateway holds the real OAuth2 tokens **server-side** (Spring Session — JDBC in dev, Redis in production), and the browser carries only an opaque `SESSION` cookie. Token refresh is delegated to Spring Security's `OAuth2AuthorizedClientManager` (automatic, transparent). The frontend reads identity via `GET /bff/me` and proxies data calls through the gateway's `TokenRelay` filter to `/api/**`.

This is a sound architecture, but it has **unfinished edges** that surface as real user-facing defects today. Phase 79 closes those edges. It is **pure platform hardening** — no new domain entities, no business features. It touches: Keycloak realm config, the gateway logout/session config, the frontend auth client + middleware, the frontend user menu, a small set of new branded frontend routes, and the existing Keycloak (Keycloakify) theme.

### Verified current state (file:line evidence, 2026-06-21)

1. **Token/session refresh** — Opaque `SESSION` cookie (HttpOnly, Secure, SameSite=Lax); OAuth tokens stored server-side in `SPRING_SESSION_ATTRIBUTES` (JDBC dev) / Redis (prod). Refresh is automatic via `OAuth2AuthorizedClientManager`. Frontend identity probe: `frontend/lib/auth/providers/keycloak-bff.ts:29-59` (`GET /bff/me`). Token relay: `gateway/.../GatewaySecurityConfig.java:52` (`TokenRelay=` on `/api/**`). Spring session timeout: `gateway/src/main/resources/application.yml:26` = **8h**.

2. **Lifetime mismatch (root cause of the bug)** — The gateway Spring session is **8h**, but Keycloak token/SSO lifetimes are **never explicitly configured** in `compose/keycloak/realm-export.json` (no `accessTokenLifespan`, `refreshTokenLifespan`, `ssoSessionIdleTimeout`, `ssoSessionMaxLifespan` fields → Keycloak server defaults ~5m access / ~30m idle apply). After long inactivity Keycloak has killed the SSO session while the gateway still believes the session is live → the silent refresh fails.

3. **Expired-session handling is incomplete** — The only graceful handler is for **server-action** 401s: `frontend/lib/api/client.ts:164-166` catches `status === 401` and calls `redirect("/sign-in")`; middleware then bounces to `${GATEWAY_URL}/oauth2/authorization/keycloak` (`frontend/lib/auth/middleware.ts:62-71`). There is **no global 401 boundary** — client-component fetches, polling, SSE, and the `/bff/me` probe itself do not all funnel through that handler, so **raw errors leak to the UI** ("clicking anything leads to errors"). A partial stale-session guard exists (GAP-L-22, `KC_LAST_LOGIN_SUB` cookie cross-check in `middleware.ts:74-94`) but only covers user-mismatch, not generic expiry.

4. **Logout / whitelabel leak** — Logout POSTs to `${GATEWAY_URL}/logout` with CSRF (`frontend/components/auth/user-menu-bff.tsx:69-97`), handled by `OidcClientInitiatedLogoutSuccessHandler` (`GatewaySecurityConfig.java:105-110`). `post.logout.redirect.uris` (`compose/keycloak/realm-export.json:63`) dumps the user at the frontend/gateway root with **no branded "you've been signed out" page**. The same unstyled landing happens when an expired session bounces a user out.

5. **Change password — does not exist** — No in-app link to the Keycloak account console, no self-service form, in frontend or portal. Portal profile (`portal/app/(authenticated)/profile/page.tsx`) is read-only. Users currently must use Keycloak's forgot-password email flow.

6. **Keycloak theme — exists but stale-branded** — Custom Keycloakify (React/TSX) theme at `compose/keycloak/theme/`, named **`docteams`** (the dead brand — product is **Kazi**), wired via `realm-export.json:5-6` (`loginTheme`/`emailTheme`). Login + email pages are themed; **error / info / post-logout pages are not consistently branded**, which is where the "whitelabel" leaks through. Email templates: `compose/keycloak/theme/themes/docteams/email/html/`.

### Founder decisions that constrain this phase (2026-06-21 ideation)

- **Expiry UX = silent graceful re-login.** When a session expires, catch every expired-session path, clear local state, and redirect to a **branded** re-login carrying a **return-to** deep-link so the user resumes where they were. **No** idle-warning countdown modal, **no** keep-alive auto-refresh in v1 (both explicitly out of scope).
- **Change password = deep-link to Keycloak.** Add an in-app "Account & Security" entry that deep-links to the (themed) Keycloak account console / fires the `UPDATE_PASSWORD` action. **No** native in-app password form in v1.
- **Theme = visible-brand rebrand + full page coverage.** Audit and fix user-facing "DocTeams" brand copy/logo/colours in the Keycloak theme pages and email templates so they render **Kazi**, and ensure error / info / post-logout pages are themed so no auth surface leaks whitelabel. **Do NOT rename the Keycloak realm id or the theme directory identifier** — both are `docteams` but are *internal* (the realm id is baked into the OIDC issuer URI `realms/docteams` in every token's `iss` claim and every redirect URI across gateway/backend/frontend/mock-idp/compose; renaming is a ~30-file, token-invalidating migration with zero user-visible benefit). Rebrand = what the user sees, not internal identifiers. (Confirmed 2026-06-21: `realm-export.json:2-3` `id`/`realm` = `docteams`; `application.yml:40` `issuer-uri ...realms/docteams`.)
- **Session lifetimes (locked).** Access token **5m**, SSO **idle 30m**, SSO **max 10h**. Gateway Spring session aligned to SSO max (10h, not 8h) so the two cannot drift. Revisitable via ADR.
- **Scope = auth/session UX only.** No app-wide dark-mode / design-token audit; no MFA / passkeys (flag as future).

## Objective

Make the auth surface **trustworthy and branded end-to-end**: (1) sessions expire predictably and recover gracefully — never a raw error on click; (2) logout and expiry land on branded pages, killing the whitelabel leaks; (3) users can change their own password from inside the app; (4) the Keycloak theme is rebranded Kazi and covers every page (login, email, error, info, logout). No new domain features, no new tables.

## Constraints & Assumptions

- **No new domain entities or tables.** This is config + frontend + theme work. Changes land in: `compose/keycloak/realm-export.json`, the Keycloak bootstrap script (`compose/keycloak/*bootstrap*.sh`), `gateway` security/session config, `frontend/lib/auth/*`, `frontend/lib/api/client.ts`, `frontend/components/auth/*`, new branded routes under `frontend/app/`, and `compose/keycloak/theme/`. Portal gets the same change-password entry point where it has a user menu.
- **Do not fork the auth model.** Everything routes through the existing BFF + Keycloak OIDC + `TenantFilter`/`MemberFilter` pipeline. No client-side token handling, no parallel session store.
- **Lifetimes are config, not code.** Set Keycloak lifetimes in the realm export (and confirm they survive bootstrap/import). Align the gateway Spring session timeout. Both must be environment-overridable (dev vs prod) — do not hardcode where an env var is the existing pattern.
- **Expired-session handling must be comprehensive, not per-call.** The fix is a single funnel every fetch path goes through, plus the middleware path, plus the `/bff/me` probe — verified against the actual set of fetch entry points, not assumed.
- **Return-to must round-trip safely.** The post-login deep-link must be validated (same-origin / allowlisted path) to avoid open-redirect; never reflect an arbitrary external URL.
- **Branded pages are first-party routes.** Prefer frontend routes (`/signed-out`, `/sign-in?reason=expired`) for post-logout/expiry landings (the redirect already targets the frontend); use Keycloak theme pages only for failures that occur *inside* Keycloak (error.ftl / info.ftl).
- **No realm or theme-directory rename.** The rebrand is **visible-brand only** (rendered copy/logo/palette in theme pages + email templates). Leave `realm-export.json` `id`/`realm`, the `realms/docteams` issuer URI, and the theme directory/`loginTheme`/`emailTheme` identifier values as-is. This keeps the change contained to theme assets + page coverage and avoids touching OAuth config, token issuance, redirect URIs, or e2e selectors.
- **Test strategy.** (a) Backend/gateway: an integration or config test asserting the realm lifetimes import as set and the gateway session timeout matches. (b) Frontend: tests for the expired-session funnel (mock a 401 from each entry-point class → assert branded redirect with return-to) and the return-to allowlist (reject external URLs). (c) E2E (Playwright, mock-auth + Keycloak dev stack): logout lands on `/signed-out` branded; an expired session lands on `/sign-in?reason=expired` branded; the "Account & Security" link resolves to the Keycloak account console. (d) Manual QA: real Keycloak dev stack, induce a true idle expiry and confirm graceful re-login with resume.
- **"PASS means observed."** Per project quality gates — every claim verified browser → gateway log → Keycloak, not inferred from code.

---

## Section 1 — Keycloak Session & Token Lifetimes (root-cause fix)

### 1.1 Explicit realm lifetimes
- Set in `compose/keycloak/realm-export.json` (and confirm the bootstrap/import path preserves them):
  - `accessTokenLifespan` = **300s (5m)**
  - `ssoSessionIdleTimeout` = **1800s (30m)**
  - `ssoSessionMaxLifespan` = **36000s (10h)**
  - `refreshTokenMaxReuse` / offline-session settings: confirm `offline_access` behaviour (`realm-export.json:35`) is consistent with the chosen idle/max — document the decision; do not leave a refresh path that silently outlives the SSO idle window.
- Values must be environment-overridable for prod hardening (a firm may want a shorter idle). Use the existing realm-config / env override pattern; do not hardcode if an override mechanism exists.

### 1.2 Gateway session alignment
- Align the gateway Spring session timeout (`gateway/src/main/resources/application.yml:26`, currently `8h`) with the Keycloak SSO max (**10h**) so the BFF session cannot outlive (or undershoot) the IdP session in a way that produces the stale-session failure.
- Confirm the production session store (Redis, `application-production.yml:2-10`) honours the same timeout.
- ADR: document why aligned-to-SSO-max (vs idle) is the correct anchor, and the dev/prod override story.

---

## Section 2 — Graceful Expired-Session Handling (the bug)

### 2.1 Single expiry funnel
- Establish **one** place every authenticated fetch result flows through that detects an expired/invalid session (401 from `/api/**`, refresh-failure signal, and a failed/empty `/bff/me`), clears any client-held auth/UI state, and triggers the branded re-login redirect.
- Enumerate and cover **every** entry-point class against source — at minimum: the server-action path (`frontend/lib/api/client.ts`), client-component fetches, any polling/interval refetch, SSE/streaming endpoints if present, and the middleware path (`frontend/lib/auth/middleware.ts`). The existing `client.ts:164-166` 401→`/sign-in` is the seed; extend coverage to the gaps, do not leave per-call ad-hoc handling.
- The `/bff/me` probe failing must itself produce the graceful path, not a thrown error (today it can surface raw — `keycloak-bff.ts:29-59`).

### 2.2 Return-to deep-link
- On bounce-out, capture the current path and carry it through the Keycloak round-trip so post-login lands the user back where they were.
- **Validate** the return-to: same-origin / allowlisted internal path only; reject/strip external or malformed targets (open-redirect guard). Default to the dashboard when absent or rejected.

### 2.3 Expiry landing UX
- An expired bounce lands on the branded re-login with a clear, non-alarming reason banner (e.g. `/sign-in?reason=expired` → "Your session expired for security. Sign in to continue."). Distinguish *expired* from *signed-out* (Section 3) from *first-time* sign-in — copy and CTA differ.
- **`/sign-in` does not currently exist as a route** (confirmed 2026-06-21) — it is a dangling redirect target referenced in 3 places (`client.ts`, middleware) and whitelisted in middleware, so an expired 401 today redirects to `/sign-in` → **404**. This is itself part of "clicking leads to errors." This phase MUST create the branded `/sign-in` route (which initiates the Keycloak login redirect, honouring the `reason` banner + return-to), not merely wire redirects to it.

---

## Section 3 — Branded Logout & Post-Logout Landing

### 3.1 Branded signed-out page
- Add a first-party `/signed-out` route (frontend; mirror in portal where it has a session) — a branded confirmation ("You've been signed out") with a "Sign in again" CTA. No app chrome / no authed data.
- Update `post.logout.redirect.uris` (`compose/keycloak/realm-export.json:63`) and the gateway logout success handler so logout terminates at `/signed-out`, across all configured origins (localhost dev + `app-dev.heykazi.com` + prod). Keep the redirect URIs allowlisted in Keycloak.

### 3.2 Logout completeness
- Confirm logout clears the `SESSION` cookie AND terminates the Keycloak SSO session (`OidcClientInitiatedLogoutSuccessHandler` already initiates RP-logout — verify end-to-end the IdP session is gone, not just the local cookie). Note the existing behaviour leaves the `SPRING_SESSION` row until GC — confirm that is acceptable or trigger cleanup.

### 3.3 Optional security hardening (low-effort, in-scope if cheap)
- `BffController.java:78` logs full OIDC claims (PII) on every `/bff/me` call. Since `/bff/me` fires on every page load, this is a recurring PII-in-logs leak (POPIA-relevant). Reduce to non-PII fields (subject id + org only) or drop to debug level. Treat as an optional, low-risk hardening within this phase, not a blocking deliverable.

---

## Section 4 — Change Password (deep-link)

### 4.1 In-app entry point
- Add an **"Account & Security"** item to the user menu (`frontend/components/auth/user-menu-bff.tsx`; mirror in portal's user menu if present) that initiates Keycloak's **`UPDATE_PASSWORD`** required action via the authorization endpoint (`.../protocol/openid-connect/auth?...&kc_action=UPDATE_PASSWORD`), then returns the user cleanly to the app.
- **Why kc_action, not the account console (decided at /architecture, ADR-311):** the Keycloak **account console** runs `accountThemeImplementation: none` (`compose/keycloak/theme/vite.config.ts:12`) → it renders **stock/unbranded**, which would reintroduce the exact whitelabel leak this phase exists to kill. The **login theme** (the Keycloakify JAR, already Kazi-branded) DOES include the `login-update-password` page, and `kc_action=UPDATE_PASSWORD` renders under the login theme — branded, no account-theme build required. Confirm/brand `LoginUpdatePassword.tsx`.
- The flow rides the existing gateway OAuth client (no parallel auth). Exact wiring — gateway adds `kc_action` to its authorization request vs a direct authorization-endpoint link — is a `/breakdown` implementation detail.
- The account-console deep-link remains a documented fallback only; if ever used, verify the route fragment against the running Keycloak (requirements drafted `signingin`, no dash). Do NOT build an account theme (out of scope; `accountThemeImplementation` stays `none`).

### 4.2 Scope guard
- v1 is deep-link only. **No** native in-app password form, **no** password-strength UI in-app (Keycloak owns policy). Forgot-password (email) flow is unchanged and already themed.

---

## Section 5 — Keycloak Theme Visible-Brand Rebrand & Full Page Coverage

> **Scope guard (decided 2026-06-21):** rebrand = **what the user sees**. Do NOT rename the Keycloak realm id (`docteams`, load-bearing in the OIDC issuer URI + redirect URIs across ~30 files / 4 services) or the theme directory identifier. Those are internal and invisible to users.

### 5.1 Visible-brand audit
- Sweep the Keycloakify theme pages (`compose/keycloak/theme/src/login/pages/*.tsx`) and email templates (`compose/keycloak/themes/docteams/email/html/*.ftl`) for user-facing "DocTeams"/"DocTeams" brand copy, logos, titles, and palette, and replace with **Kazi** branding (slate + teal, matching the app design system). The *rendered output* must read Kazi; the directory/identifier named `docteams` may remain.
- Leave `realm-export.json:5-6` `loginTheme`/`emailTheme` = `docteams` (identifier unchanged); only the assets the theme serves change.

### 5.2 Full page coverage (kill whitelabel)
- Ensure the theme covers and brands **every** Keycloak page a user can hit: login, username/password split, reset-password, register (if enabled), verify-email, **error.ftl**, **info.ftl**, and the logout/SSO-logout pages. The error/info pages are the current whitelabel leak — bring them under the Kazi theme (slate + teal, matching the app design system).
- Email templates (verification, org-invite, executeActions, template) re-confirmed branded Kazi.

### 5.3 Consistency check
- The themed Keycloak surfaces, the new frontend `/signed-out` and expiry landings, and the app shell must read as one product (logo, palette, type). No surface should drop to stock Keycloak or an unstyled page.

---

## Out of Scope

- **Idle-warning countdown modal** ("you'll be logged out in 2 min") — future enhancement.
- **Keep-alive / auto-refresh while tab active** — future; v1 honours the security timeout.
- **Native in-app change-password form** — deep-link to Keycloak only in v1.
- **MFA / passkeys / TOTP enrolment UI** — flag as future; Keycloak can enforce, but no in-app UX this phase.
- **App-wide dark-mode / design-token audit** — this phase brands the *auth* surfaces only.
- **Any new domain entity, table, or business feature.**
- **Self-serve signup / registration changes** — registration stays as currently configured (admin-gated provisioning per product strategy).
- **Portal customer auth rework** — portal gets the change-password entry + signed-out page only if it has an authenticated user menu; no magic-link flow changes.

---

## ADR Topics to Address

- **ADR-307**: Session lifetime policy — chosen values (5m access / 30m idle / 10h max), why aligned-to-SSO-max for the gateway session, dev vs prod override mechanism, and the `offline_access` interaction.
- **ADR-308**: Expired-session handling architecture — the single-funnel approach across server actions, client fetches, polling/SSE, `/bff/me`, and middleware; how it composes with the existing `client.ts` 401 path and the GAP-L-22 `KC_LAST_LOGIN_SUB` guard.
- **ADR-309**: Return-to deep-link safety — capture/round-trip mechanism and the open-redirect allowlist guard.
- **ADR-310**: Branded landing strategy — first-party frontend routes (`/signed-out`, expiry banner) vs Keycloak theme pages; which failure class lands where.
- **ADR-311**: Change-password approach — account-console deep-link vs `UPDATE_PASSWORD` required-action initiation; realm/URL resolution and return-to-app behaviour.
- **ADR-312**: Visible-brand rebrand scope — why the rebrand is render-only (theme assets + email copy + page coverage) and why the realm id / theme-directory identifier (`docteams`) is deliberately NOT renamed (load-bearing in the OIDC issuer URI; renaming invalidates tokens across 4 services for zero user benefit).

---

## Style & Boundaries

- Follow all conventions in `frontend/CLAUDE.md` (Next.js 16, Keycloak, Shadcn) and `backend/CLAUDE.md` (gateway/security config).
- Reuse existing patterns: the BFF session model, `TenantFilter`/`MemberFilter`, the existing logout CSRF flow, the Keycloakify theme toolchain, the Integrations/settings UI conventions for any new menu entries.
- **No new domain tables or entities.** Changes are config (Keycloak realm + gateway), frontend auth/UX, new branded routes, and theme assets.
- Honour the project quality gates: backend changes → `./mvnw verify` clean; frontend/portal → `pnpm lint && pnpm build && pnpm test` green + prettier `format:check`; "PASS means observed" (browser → gateway log → Keycloak), reproduce-before-fix for the stale-session bug.
- Theme/branding must be verified rendered in a real browser against the Keycloak dev stack, not inferred from the source.
- Frontend design questions (the `/signed-out` and expiry landing pages) should go through the Shadcn / Next.js conventions, matching the existing app shell.

---

## Next step

`/architecture requirements/claude-code-prompt-phase79.md` — generates the architecture section + ADRs (307–312). Then `/breakdown 79` for epics/slices. The stale-session bug (Section 2) must be **reproduced** on the Keycloak dev stack before its fix, per the reproduce-before-fix gate.
