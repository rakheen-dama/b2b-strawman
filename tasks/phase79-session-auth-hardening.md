# Phase 79 — Session Lifecycle & Auth Experience Hardening

> **Architecture**: [`architecture/phase79-session-auth-hardening.md`](../architecture/phase79-session-auth-hardening.md) (Section 11, §11.1–§11.11)
> **Requirements**: [`requirements/claude-code-prompt-phase79.md`](../requirements/claude-code-prompt-phase79.md)
> **ADRs**: [ADR-307](../adr/ADR-307-session-lifetime-policy.md) (5m access / 30m idle / 10h max; gateway session anchored to SSO max; env override; `offline_access` interaction), [ADR-308](../adr/ADR-308-expired-session-handling.md) (single expiry funnel across all fetch entry-points + middleware + `/bff/me`; composes with `client.ts` 401 path + GAP-L-22), [ADR-309](../adr/ADR-309-return-to-redirect-safety.md) (same-origin / allowlist validation; open-redirect guard), [ADR-310](../adr/ADR-310-branded-auth-landing-strategy.md) (first-party frontend routes vs KC theme pages; which failure lands where), [ADR-311](../adr/ADR-311-change-password-approach.md) (`kc_action=UPDATE_PASSWORD` via the branded login theme, chosen; account-console deep-link rejected), [ADR-312](../adr/ADR-312-visible-brand-rebrand-scope.md) (render-only rebrand; realm id / theme identifier deliberately unchanged)
> **Predecessors**: BFF OIDC pattern (Spring Cloud Gateway OAuth2 client + Spring Session JDBC dev / Redis prod); GAP-L-22 stale-handoff guard (`GatewaySecurityConfig.java:116-152` + `middleware.ts:74-143`); ADR-T005 (portal magic-link auth — why portal is largely out of scope); Keycloakify (React/TSX) theme toolchain at `compose/keycloak/theme/`.
> **Starting epic**: 568. Last completed: 567 (Phase 78).
> **No migrations.** This is a pure platform-hardening phase: NO new domain entities, NO new DB tables, NO Flyway migrations (global or tenant). All change is configuration (Keycloak realm + gateway), frontend auth glue + branded routes, and Keycloak theme assets. There is no "migrations first" ordering.

Phase 79 closes the unfinished edges of the existing BFF OIDC architecture — edges that surface today as real user-facing defects. The root cause is a **lifetime mismatch**: the gateway Spring session is 8h while the Keycloak realm sets *no* explicit token/SSO lifespans (running at server defaults ~5m/~30m/~10h), so after idle Keycloak kills the SSO session while the gateway still believes it is live, the silent refresh fails, and — because expired-session handling is incomplete and `/sign-in` is a dangling 404 redirect target — **raw errors leak to the UI**. Phase 79 makes lifetimes explicit and aligned (568), funnels every expiry path through one branded re-login flow with a safe return-to (569), lands logout on a branded `/signed-out` page (570), adds in-app change-password via Keycloak's `kc_action=UPDATE_PASSWORD` rendered by the branded login theme (571), and rebrands the Keycloak theme **Kazi** with full page coverage (572). Every vertical fork inherits these auth guarantees for free.

---

## Scope-category rule (this phase)

The standard "backend OR frontend, never both" slice rule is extended: a slice belongs to exactly **one** of four scope categories and never mixes them:

| Category | What it covers |
|---|---|
| **keycloak-config** | `compose/keycloak/realm-export.json`, `compose/scripts/keycloak-bootstrap.sh` |
| **gateway** | `gateway/src/main/{resources,java}/...` (Spring session, security, logout, BFF) |
| **frontend** | `frontend/lib/auth/*`, `frontend/lib/api/client.ts`, `frontend/components/auth/*`, `frontend/app/...` routes |
| **theme** | `compose/keycloak/theme/src/login/pages/*`, `compose/keycloak/themes/docteams/email/html/*.ftl`, theme JAR rebuild |

---

## Open Questions (resolve at implementation time)

- **`(auth)` route group does not exist yet.** Current frontend route groups are `(app)`, `(mock-auth)`, plus top-level `accept`, `accept-invite`, `request-access`, `portal`. The arch doc proposes `frontend/app/(auth)/sign-in` and `(auth)/signed-out`. Builder must create the `(auth)` group (with its own minimal `layout.tsx` — no app chrome). Confirm middleware `PUBLIC_ROUTES` (`middleware.ts:7-16`) lists `/sign-in` and `/signed-out` as public (currently `/sign-in` is whitelisted but unrendered; add `/signed-out`).
- **`LoginUpdatePassword.tsx` is NOT a custom override.** Only the Keycloakify default exists (`node_modules/.../login/pages/LoginUpdatePassword.tsx`); there is no `compose/keycloak/theme/src/login/pages/LoginUpdatePassword.tsx`. The page already renders *under* the docteams login theme template (so it is partially branded via the shared `Template.tsx`/CSS). Slice 572 must (a) confirm it renders Kazi-branded and (b) add a custom override only if visible-brand copy needs it. Confirm `KcPage.tsx`/route registration picks up any new override.
- **`offline_access` interaction (ADR-307).** Realm grants `offline_access` as a default role / client scope, but the gateway-bff client scope is `openid,profile,email,organization` (no `offline_access`, `application.yml:31-40`), so `ssoSession*` fully governs the gateway refresh path. Builder must set **no** `offlineSession*` keys (do not create a refresh path outliving SSO idle) and document this in the realm-export comment.
- **Gateway success handler `alwaysUseDefaultTargetUrl=true` (`GatewaySecurityConfig.java:113`).** `returnTo` cannot survive the gateway login redirect (always lands `/dashboard`). Chosen mechanism (no gateway change): `/sign-in` persists validated `returnTo` in `sessionStorage` before initiating KC login; a small client step on `/dashboard` reads + clears it. Do NOT remove `alwaysUseDefaultTargetUrl` (touches all login flows — ADR-309/310 rejected alternative).
- **Email templates live in a separate tree.** Keycloakify pages are under `compose/keycloak/theme/src/login/pages/`, but email templates are under `compose/keycloak/themes/docteams/email/html/*.ftl` (`email-verification.ftl`, `org-invite.ftl`, `executeActions.ftl`, `template.ftl`). Both trees are in the **theme** scope category.
- **Change-password gateway wiring (ADR-311).** Exact mechanism — a gateway endpoint that adds `kc_action=UPDATE_PASSWORD` to its authorization request vs a direct authorization-endpoint link — is decided in 571's gateway slice. Because the frontend never talks to Keycloak directly, the flow must ride the gateway OAuth client. This crosses categories → SPLIT (gateway slice 571A + frontend slice 571B).
- **Theme JAR rebuild is a deliverable.** `compose/keycloak/theme/dist_keycloak/` + `compose/keycloak/providers/keycloak-theme.jar` must be rebuilt/redeployed (`build-keycloak-theme`) for theme changes to take effect; `accountThemeImplementation` stays `none` (`vite.config.ts:12`) — no account-theme build.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 568 | Session/Token Lifetimes + Gateway Session Alignment | keycloak-config + gateway | -- | S | 568A, 568B | **Done** (PR #1482) |
| 569 | Graceful Expiry Funnel + Branded `/sign-in` Route + Return-To | Frontend | 568 | L | 569A, 569B | **Done** (PR #1483) |
| 570 | Branded `/signed-out` Page + Logout Redirect Wiring (+ optional PII-log hardening) | Frontend + gateway | 569 | M | 570A, 570B | **Done** (PR #1484) |
| 571 | Change-Password via `kc_action=UPDATE_PASSWORD` | gateway + Frontend | 572 (for branded page) | M | 571A, 571B | |
| 572 | Keycloak Theme Visible-Brand Rebrand + Page Coverage | theme | -- | M | 572A, 572B | **Done** (PR #1485) |

**Slice count: 9** (architecture §11.10's 6 capability slices re-mapped to enforce the unmixed-category + 6–12 files / ~800 LOC budget). Slice 1 → Epic 568 (split keycloak-config 568A / gateway 568B). Slice 2 → Epic 569 (funnel-lib+route 569A / entry-point wiring 569B, both frontend). Slice 3 + slice 6 → Epic 570 (frontend route 570A / gateway redirect + optional PII log 570B). Slice 4 → Epic 571 (gateway wiring 571A / frontend entry 571B — category split). Slice 5 → Epic 572 (page coverage 572A / visible-brand audit + JAR rebuild 572B).

**Maps to architecture capability slices**: S1 → 568 · S2 → 569 · S3 → 570A + S6 → 570B · S4 → 571 · S5 → 572.

---

## Dependency Graph

```
Existing BFF OIDC architecture (gateway OAuth2 client + Spring Session;
GAP-L-22 stale-handoff guard; Keycloakify docteams theme)
        │
        ├──────────────────────────────────────────────┐
        ▼                                                ▼
┌────────────────────────────────────┐       ┌─────────────────────────────────┐
│ EPIC 568 — LIFETIMES (root cause)   │       │ EPIC 572 — THEME REBRAND         │
│  [568A keycloak-config:             │       │  [572A theme: Error/Info/logout  │
│   realm-export lifetimes +          │       │   page coverage under Kazi theme +│
│   bootstrap env-override PUT]       │       │   LoginUpdatePassword confirm]    │
│  [568B gateway:                     │       │  [572B theme: visible-brand audit │
│   application.yml 8h→10h +          │       │   (login pages + email ftl) +     │
│   config assertion test]           │       │   JAR rebuild/redeploy]           │
│  (INDEPENDENT — can start now)      │       │  (INDEPENDENT — can start now)    │
└───────────────┬─────────────────────┘       └────────────────┬────────────────┘
                │ (expiry now predictable)                      │ (branded update-pw page)
                ▼                                                │
┌────────────────────────────────────┐                         │
│ EPIC 569 — EXPIRY FUNNEL + /sign-in │                         │
│  [569A frontend: expiry.ts +        │                         │
│   return-to.ts + (auth)/sign-in     │                         │
│   route + unit tests]               │                         │
│  [569B frontend: wire client.ts +   │                         │
│   keycloak-bff.ts + middleware.ts + │                         │
│   user-menu client funnel + e2e]    │                         │
└───────────────┬─────────────────────┘                        │
                │ (shared (auth) group + branding)              │
                ▼                                                │
┌────────────────────────────────────┐                         │
│ EPIC 570 — /signed-out + LOGOUT      │                        │
│  [570A frontend: (auth)/signed-out  │                         │
│   route + "Sign in again" CTA]      │                         │
│  [570B gateway: setPostLogout-      │                         │
│   RedirectUri → /signed-out +        │                        │
│   optional /bff/me PII-log + tests] │                         │
└──────────────────────────────────────┘                        │
                                                                 ▼
                                       ┌─────────────────────────────────────────┐
                                       │ EPIC 571 — CHANGE PASSWORD (kc_action)    │
                                       │  [571A gateway: kc_action=UPDATE_PASSWORD │
                                       │   initiation on the OAuth client + tests] │
                                       │  [571B frontend: user-menu "Account &     │
                                       │   Security" entry + e2e]                   │
                                       │  (needs 572 for fully branded page)        │
                                       └───────────────────────────────────────────┘
```

**Parallel opportunities:**
- **Epic 568 (lifetimes) and Epic 572 (theme) are fully independent and can start immediately, in parallel.** 568 is the root-cause fix; 572 is pure theme assets. Neither blocks the other.
- **Epic 569 depends on 568** only so expiry is *predictable to reproduce* (the funnel can be coded against 568's lifetimes; functionally 569 could start in parallel, but 568 is S-effort and should land first to make the reproduce-before-fix gate meaningful).
- **Epic 570 depends on 569** (shares the `(auth)` route group + branding from 569A). 570A (frontend route) and 570B (gateway redirect + PII log) are different categories — they are separate slices that can run in parallel once 569A's `(auth)` group exists.
- **Epic 571 is largely independent** of 569/570; its gateway slice (571A) depends on nothing in the funnel work. It depends on **572** for the *branded* `login-update-password` page (functional flow works without it; visual branding needs 572). 571A (gateway) and 571B (frontend) are a category split.

---

## Implementation Order

### Stage 1 — Root-cause fix + theme (parallel, independent)

| Order | Slice | Category | Summary | Runs in parallel with |
|-------|-------|----------|---------|-----------------------|
| 1a | **568A** ✅ | keycloak-config | `realm-export.json` lifetimes (`accessTokenLifespan:300`, `ssoSessionIdleTimeout:1800`, `ssoSessionMaxLifespan:36000`); env-override `PUT` in `keycloak-bootstrap.sh`; import-assertion. **Done** (PR #1482) | 568B, 572A, 572B |
| 1b | **568B** ✅ | gateway | `application.yml` session `timeout` `8h`→`${GATEWAY_SESSION_TIMEOUT:10h}`; Redis prod parity; config test. **Done** (PR #1482) | 568A, 572A, 572B |
| 1c | **572A** ✅ | theme | Bring `Error.tsx` / `Info.tsx` / logout surface under the Kazi theme; confirm/brand `LoginUpdatePassword`. **Done** (PR #1485) | 568A/B, 572B |
| 1d | **572B** ✅ | theme | Visible-brand audit of all login pages + email `*.ftl`; JAR rebuild + redeploy; browser render verification. **Done** (PR #1485) | 568A/B, 572A |

### Stage 2 — Expiry funnel + branded sign-in (after 568)

| Order | Slice | Category | Summary |
|-------|-------|----------|---------|
| 2a | **569A** ✅ | frontend | New `lib/auth/expiry.ts` (detector + redirect builders) + `lib/auth/return-to.ts` (`safeReturnTo` allowlist) + new `(auth)/sign-in` route (reason banner, sessionStorage return-to); unit tests for funnel + allowlist. **Done** (PR #1483) |
| 2b | **569B** ✅ | frontend | Wire `client.ts` + `keycloak-bff.ts` + `middleware.ts` + `user-menu-bff.tsx` client fetches through the 569A funnel; Playwright expiry e2e. **Done** (PR #1483) |

### Stage 3 — Branded signed-out + logout (after 569)

| Order | Slice | Category | Summary | Runs in parallel with |
|-------|-------|----------|---------|-----------------------|
| 3a | **570A** | frontend | New `(auth)/signed-out` route + "Sign in again" CTA; add `/signed-out` to middleware `PUBLIC_ROUTES`. | 570B |
| 3b | **570B** | gateway | `setPostLogoutRedirectUri(frontendUrl + "/signed-out")`; verify `post.logout.redirect.uris` covers `/signed-out`; optional `/bff/me` PII-log reduction; gateway tests. | 570A |

### Stage 4 — Change password (after 572; category split)

| Order | Slice | Category | Summary |
|-------|-------|----------|---------|
| 4a | **571A** | gateway | Gateway initiation that adds `kc_action=UPDATE_PASSWORD` to the OAuth client's authorization request; return-to-app; gateway test. |
| 4b | **571B** | frontend | "Account & Security" item in `user-menu-bff.tsx` that triggers 571A; Playwright change-password e2e (renders Kazi-branded `login-update-password`, returns to app). |

### Timeline

```
Stage 1:  [568A // 568B]   //   [572A → 572B]          ← root-cause + theme (all independent)
Stage 2:  [569A → 569B]                                ← funnel + /sign-in (after 568)
Stage 3:  [570A // 570B]                               ← signed-out + logout (after 569A)
Stage 4:  [571A → 571B]                                ← change-password (after 572)
```

---

## Epic 568: Session/Token Lifetimes + Gateway Session Alignment

**Goal**: Eliminate the lifetime mismatch at the root. Make Keycloak token/SSO lifetimes explicit and tunable (access 5m, SSO idle 30m, SSO max 10h) and align the gateway Spring session to the SSO max (10h) so the two session anchors can never drift into the stale-session failure. This is the foundational, independent fix — once it lands, idle-driven expiry still happens (at 30m) but is now *handled gracefully* by Epic 569 rather than leaking raw errors.

**References**: Architecture §11.3.1 (realm lifetimes + env-override strategy + offline_access interaction), §11.3.2 (gateway session alignment + lifetime invariant), §11.10 slice 1; [ADR-307]. Requirements §1.1, §1.2.

**Dependencies**: None (root-cause fix; can start immediately).

**Scope**: keycloak-config (568A) + gateway (568B) — split by category.

**Estimated Effort**: S

### Slices

| Slice | Tasks | Files Touched | Summary | Status |
|-------|-------|---------------|---------|--------|
| **568A** | 568A.1–568A.3 | ~3 (1 realm-export modify + 1 bootstrap script modify + 1 test) | Explicit realm lifetimes in `realm-export.json`; env-parameterised realm `PUT` override in `keycloak-bootstrap.sh`; no `offlineSession*` keys; import-assertion test. | **Done** (PR #1482) |
| **568B** | 568B.1–568B.2 | ~3 (1 application.yml modify + verify production.yml + 1 config test) | Gateway session `timeout` 8h→`${GATEWAY_SESSION_TIMEOUT:10h}`; Redis prod parity; resolved-timeout config test. | **Done** (PR #1482) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 568A.1 | Add explicit realm lifetimes | `compose/keycloak/realm-export.json` (modify) | 568A.3 | Existing realm-level keys (`id`/`realm`/`loginTheme`/`emailTheme`, lines 2-6) | Add realm-level `accessTokenLifespan:300`, `ssoSessionIdleTimeout:1800`, `ssoSessionMaxLifespan:36000` alongside `id`/`realm`. Add a JSON comment-style note (or doc the decision in the PR) that **no `offlineSession*` keys are set** — the gateway-bff client scope (`openid,profile,email,organization`, `application.yml:31-40`) has no `offline_access`, so `ssoSession*` fully governs the refresh path (ADR-307). Do NOT rename `id`/`realm` (`docteams` is load-bearing in the issuer URI — ADR-312). |
| 568A.2 | Add env-override realm `PUT` to bootstrap | `compose/scripts/keycloak-bootstrap.sh` (modify) | manual QA (bootstrap run) | Existing post-import realm/component `PUT` pattern already in this script | After realm import, add an env-parameterised realm `PUT` setting `accessTokenLifespan`/`ssoSessionIdleTimeout`/`ssoSessionMaxLifespan` from `KC_ACCESS_TOKEN_LIFESPAN`/`KC_SSO_IDLE_TIMEOUT`/`KC_SSO_MAX_LIFESPAN`, defaulting to the realm-export values (300/1800/36000). This is the prod hardening knob (a firm may shorten idle) without touching committed defaults. |
| 568A.3 | Realm lifetime import-assertion test | new test under the realm/config test location (e.g. `gateway/src/test/.../RealmLifetimeImportTest.java` or a JSON-shape assertion in the existing config test suite) | ~3 assertions: parse `realm-export.json` and assert `accessTokenLifespan==300`, `ssoSessionIdleTimeout==1800`, `ssoSessionMaxLifespan==36000`; assert NO `offlineSessionIdleTimeout`/`offlineSessionMaxLifespan` keys present | `gateway/src/test/.../config/GatewaySecurityConfigTest.java` as the config-assertion test style | A lightweight JSON-parse assertion is sufficient (no live Keycloak needed for the unit gate); the full import is covered by manual QA on the KC dev stack. |
| 568B.1 | Align gateway session timeout to SSO max | `gateway/src/main/resources/application.yml:26` (modify); verify `gateway/src/main/resources/application-production.yml:1-11` (Redis, no `timeout` override → inherits base) | 568B.2 | `application.yml` existing `spring.session` block; existing `${ENV:default}` placeholder convention | Change `timeout: 8h` → `timeout: ${GATEWAY_SESSION_TIMEOUT:10h}`. Confirm production (Redis) does NOT override `timeout` so it inherits 10h (Redis parity automatic). Invariant: `access 5m < idle 30m < max 10h == gateway session 10h`. |
| 568B.2 | Gateway session-timeout config test | `gateway/src/test/java/io/b2mash/b2b/gateway/SessionStorageTest.java` (modify) or new `SessionTimeoutConfigTest.java` | ~2 tests: resolved `spring.session.timeout` == `PT10H` (10h) on the default profile; production profile inherits the same value | existing `SessionStorageTest.java` / `RouteConfigTest.java` config-assertion style | Assert via `@SpringBootTest`/`Environment` binding, not a hardcoded string. |

### Key Files

**Modify (keycloak-config):**
- `compose/keycloak/realm-export.json` — add 3 lifetime keys (realm-level)
- `compose/scripts/keycloak-bootstrap.sh` — env-parameterised realm `PUT` override

**Modify (gateway):**
- `gateway/src/main/resources/application.yml:26` — `timeout: ${GATEWAY_SESSION_TIMEOUT:10h}`
- `gateway/src/main/resources/application-production.yml` — verify no `timeout` override (inherits 10h)

**Create (tests):**
- realm lifetime import-assertion test (568A.3)
- gateway session-timeout config test (568B.2)

**Read for context:**
- `gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java` — config-test pattern
- `gateway/src/test/java/io/b2mash/b2b/gateway/SessionStorageTest.java` — session config test pattern

### Architecture Decisions

- **5m / 30m / 10h, gateway anchored to SSO max** ([ADR-307]) — `max == gateway session` kills the stale-session class where the gateway's belief diverged from KC's lifetimes; idle-driven expiry at 30m is left for the §11.4 funnel to handle gracefully.
- **No `offlineSession*` keys** ([ADR-307]) — the bff client requests no `offline_access`, so setting offline lifetimes would create a refresh path silently outliving SSO idle. Deliberately omitted.
- **Env-overridable, not hardcoded** ([ADR-307]) — realm-export values are committed defaults; `keycloak-bootstrap.sh` `PUT` + `GATEWAY_SESSION_TIMEOUT` give prod a hardening knob without code change.

### Non-scope

- No idle-warning modal, no keep-alive auto-refresh (out of scope this phase).
- No expiry *handling* (lands in 569 — this epic only makes expiry predictable).

---

## Epic 569: Graceful Expiry Funnel + Branded `/sign-in` Route + Return-To

**Goal**: Replace "clicking anything leads to errors" with a single graceful funnel. Build one shared expiry detector + redirect builder used by every authenticated fetch entry-point (server actions, the `/bff/me` probe, middleware, client-component fetches), create the real branded `/sign-in` route (currently a 404 dangling target) with a reason banner and a safe return-to round-trip, and validate return-to through an open-redirect allowlist guard.

**References**: Architecture §11.4.1 (the funnel + entry-point table), §11.4.2 (return-to capture + `safeReturnTo`), §11.4.3 (the `/sign-in` route + sessionStorage return-to mechanism), §11.6 (open-redirect guard), §11.7 (route inventory), §11.10 slice 2; [ADR-308], [ADR-309], [ADR-310]. Requirements §2.1, §2.2, §2.3.

**Dependencies**: Epic 568 (lifetimes make expiry predictable to reproduce per the reproduce-before-fix gate).

**Scope**: Frontend only (both slices).

**Estimated Effort**: L

### Slices

| Slice | Tasks | Files Touched | Summary | Status |
|-------|-------|---------------|---------|--------|
| **569A** | 569A.1–569A.4 | ~6 (2 new lib files + new route `page.tsx` + new `(auth)/layout.tsx` + small return-to client helper + 2 test files) | `lib/auth/expiry.ts` (detector + server/client redirect builders); `lib/auth/return-to.ts` (`captureReturnTo` + `safeReturnTo` allowlist); new `(auth)/sign-in` branded route (reason banner, sessionStorage return-to persist-before-KC + read-on-return); unit tests for funnel + allowlist. | **Done** (PR #1483) |
| **569B** | 569B.1–569B.4 | ~5 (client.ts modify + keycloak-bff.ts modify + middleware.ts modify + user-menu-bff.tsx modify + 1 e2e spec) | Route all entry-points through the 569A funnel; Playwright expiry e2e. | **Done** (PR #1483) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 569A.1 | Create shared expiry funnel module | `frontend/lib/auth/expiry.ts` (new) | 569A.4 | `frontend/lib/api/client.ts:96-99,163-188` (existing 401→redirect seed); `frontend/CLAUDE.md` (Next.js 16 conventions) | Export `isSessionExpired(res)` (true on 401 from `/api/**`, the `redirect:"manual"` 3xx-to-KC already mapped to `ApiError(401)`, and failed/`authenticated:false` `/bff/me`); `redirectToReLogin(returnTo, reason="expired"): never` (server-side typed `redirect()` via `next/navigation`); `clientRedirectToReLogin(returnTo, reason?)` (client hard navigation, clears any cached `/bff/me` identity first). All redirects target the real `/sign-in?reason=expired&returnTo=…`. Composes with — never conflicts with — GAP-L-22 (which only fires when `KC_LAST_LOGIN_SUB` present + subject differs). |
| 569A.2 | Create return-to capture + allowlist guard | `frontend/lib/auth/return-to.ts` (new) | 569A.4 | §11.4.2; existing `middleware.ts` `nextUrl` usage; ADR-309 | `captureReturnTo(req)` → `pathname + search`. `safeReturnTo(raw): string` enforces: starts with single `/` (reject `//`, `/\`), no scheme (`http:`/`https:`/`javascript:`), path-only same-origin, matches allowlist prefixes (`/dashboard`, `/org/`, `/platform-admin`, `/create-org`); else → `/dashboard`. Single chokepoint — never reflect unvalidated input. |
| 569A.3 | Create branded `(auth)/sign-in` route + group layout | `frontend/app/(auth)/sign-in/page.tsx` (new), `frontend/app/(auth)/layout.tsx` (new) | 569A.4 + 569B.4 | App shell branding (slate+teal, Kazi logo); Shadcn card; `frontend/app/(mock-auth)/layout.tsx` as a "no-app-chrome auth group layout" precedent; `frontend/components/auth/keycloak-redirect.tsx` for KC-login initiation | Server component reading `searchParams.{reason,returnTo}`. Branded card; copy keyed off `reason` (`expired` → "Your session expired for security…", none → "Sign in to Kazi"). NOT a credential form. Primary CTA: persist `safeReturnTo(returnTo)` to `sessionStorage` (small client component), THEN navigate to `${GATEWAY_URL}/oauth2/authorization/keycloak`. Add the return-to read+clear step (a tiny client effect that runs on `/dashboard` landing — colocate the helper here or in `lib/auth/return-to.ts`). Public per middleware allowlist. New `(auth)` route group layout has no app chrome/no authed data. |
| 569A.4 | Unit tests — funnel + allowlist | `frontend/lib/auth/__tests__/expiry.test.ts` (new), `frontend/lib/auth/__tests__/return-to.test.ts` (new) | vitest: **funnel (~5)** — `isSessionExpired` true for 401, manual-redirect-3xx-mapped-401, `authenticated:false` `/bff/me`, failed `/bff/me`; false for 200; redirect builders produce `/sign-in?reason=expired&returnTo=…`. **allowlist (~7)** — `safeReturnTo` rejects `http://evil`, `https://evil`, `//evil`, `/\evil`, `javascript:alert(1)`, empty/null → `/dashboard`; accepts `/dashboard`, `/org/x/matters`; non-allowlisted path → `/dashboard`. | `frontend` vitest conventions (`frontend/CLAUDE.md`) | Tests live in this slice. |
| 569B.1 | Route server-action `apiRequest` through funnel | `frontend/lib/api/client.ts:76-80,163-188` (modify) | 569B.4 + reuse 569A.4 | existing `client.ts:164-166` 401→`redirect("/sign-in")` seed | Replace the ad-hoc `redirect("/sign-in")` with `redirectToReLogin(captureReturnTo(...), "expired")` so the dangling 404 target becomes the real route with `reason`+`returnTo`. Cover the `redirect:"manual"` 3xx→`ApiError(401)` path (`client.ts:96-99`). |
| 569B.2 | Route `/bff/me` probe through funnel | `frontend/lib/auth/providers/keycloak-bff.ts:29-46` (modify) | 569B.4 | §11.4.1 probe row | Probe non-ok / `authenticated:false` must call the graceful path (server redirect), NOT throw a raw error (today it throws on non-ok and can surface raw). |
| 569B.3 | Add middleware expired-session branch + user-menu client funnel | `frontend/lib/auth/middleware.ts:59-101` (modify), `frontend/components/auth/user-menu-bff.tsx:21-47` (modify) | 569B.4 | `middleware.ts:74-143` (GAP-L-22 cross-check) | Middleware: add an explicit expired-session branch → `/sign-in?reason=expired&returnTo=<path>` **scoped to the case where the GAP-L-22 `/bff/me` cross-check already fires** (do NOT add a per-request `/bff/me` probe — SESSION-present passes; downstream 401 catches stale sessions). `user-menu-bff.tsx`: on 401/unauth from its client fetch, call `clientRedirectToReLogin`. |
| 569B.4 | Playwright expiry e2e | `frontend/e2e/<auth-expiry>.spec.ts` (new) | ~2 e2e: expired session lands on branded `/sign-in?reason=expired` (reason banner visible); after re-login, return-to resumes the original path | `frontend/e2e/` existing specs + `frontend/e2e/playwright.config.ts`; mock-auth stack for CI, KC dev stack for manual | Mock-auth in CI; the **real idle-expiry reproduction** on the KC dev stack is a manual-QA gate (reproduce-before-fix). |

### Key Files

**Create (frontend):**
- `frontend/lib/auth/expiry.ts`, `frontend/lib/auth/return-to.ts`
- `frontend/app/(auth)/sign-in/page.tsx`, `frontend/app/(auth)/layout.tsx`
- `frontend/lib/auth/__tests__/expiry.test.ts`, `frontend/lib/auth/__tests__/return-to.test.ts`
- `frontend/e2e/<auth-expiry>.spec.ts`

**Modify (frontend):**
- `frontend/lib/api/client.ts:76-80,163-188`
- `frontend/lib/auth/providers/keycloak-bff.ts:29-46`
- `frontend/lib/auth/middleware.ts:59-101` (and confirm `/sign-in` in `PUBLIC_ROUTES` lines 7-16)
- `frontend/components/auth/user-menu-bff.tsx:21-47`

**Read for context:**
- `frontend/components/auth/keycloak-redirect.tsx` — KC-login initiation pattern
- `frontend/app/(mock-auth)/layout.tsx` — no-chrome auth-group layout precedent
- `frontend/CLAUDE.md` — Next.js 16 / vitest conventions

### Architecture Decisions

- **Single funnel, not per-call handling** ([ADR-308]) — one detector + redirect builder used by every entry-point; composes with the existing `client.ts` 401 path and the GAP-L-22 guard (which stays the *user-mismatch* guard).
- **No per-request `/bff/me` probe in middleware** ([ADR-308]) — SESSION-present passes; stale sessions are caught downstream by the next `/api/**` or `/bff/me` 401. The middleware expired branch is scoped to the existing GAP-L-22 cross-check.
- **Return-to via sessionStorage, not gateway change** ([ADR-309], [ADR-310]) — `alwaysUseDefaultTargetUrl=true` makes the gateway always land `/dashboard`; persisting validated return-to client-side avoids touching the gateway/all login flows. `safeReturnTo` is the single open-redirect chokepoint.
- **`/sign-in` is a first-party branded route, not a credential form** ([ADR-310]) — Kazi never collects credentials; the route initiates the KC login redirect and renders the reason banner.

### Non-scope

- No `/signed-out` route or logout wiring (lands in 570).
- No change-password (lands in 571).
- No theme changes (lands in 572).

---

## Epic 570: Branded `/signed-out` Page + Logout Redirect Wiring (+ optional PII-log hardening)

**Goal**: Make logout (and any bounce-out) terminate on a branded first-party `/signed-out` confirmation page instead of an unstyled frontend root, killing the post-logout whitelabel leak. Wire the gateway's post-logout redirect to `/signed-out`, verify Keycloak's `post.logout.redirect.uris` already allows it across all origins, and fold in the optional, non-blocking `/bff/me` PII-in-logs hardening.

**References**: Architecture §11.4.4 (branded `/signed-out` + post-logout wiring), §11.6 (logout completeness + PII-in-logs), §11.7 (route inventory), §11.10 slice 3 + slice 6; [ADR-310]. Requirements §3.1, §3.2, §3.3.

**Dependencies**: Epic 569 (shares the `(auth)` route group + branding established in 569A).

**Scope**: Frontend (570A) + gateway (570B) — split by category.

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary | Status |
|-------|-------|---------------|---------|--------|
| **570A** | 570A.1–570A.2 | ~3 (new route `page.tsx` + middleware modify + 1 test) | New `(auth)/signed-out` branded confirmation page + "Sign in again" CTA; add `/signed-out` to middleware `PUBLIC_ROUTES`. | **Done** (PR #1484) |
| **570B** | 570B.1–570B.3 | ~4 (GatewaySecurityConfig modify + BffController modify + verify realm-export + 1 test) | Gateway `setPostLogoutRedirectUri` → `/signed-out`; verify `post.logout.redirect.uris`; optional `/bff/me` PII-log reduction; gateway tests. | **Done** (PR #1484) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 570A.1 | Create branded `(auth)/signed-out` route | `frontend/app/(auth)/signed-out/page.tsx` (new) | 570A.2 + 570B's e2e | 569A `(auth)/sign-in` branding + `(auth)/layout.tsx` (reuse the group layout — no app chrome/no authed data) | Branded "You've been signed out" confirmation with a "Sign in again" CTA → `/sign-in`. No authed data fetch. |
| 570A.2 | Whitelist `/signed-out` + route render test | `frontend/lib/auth/middleware.ts:7-16` (modify `PUBLIC_ROUTES`); test colocated | vitest/Playwright (~1-2): `/signed-out` renders without auth; "Sign in again" links to `/sign-in` | `middleware.ts` `PUBLIC_ROUTES` array | Add `/signed-out` (and confirm `/sign-in`) to `PUBLIC_ROUTES`. |
| 570B.1 | Point gateway post-logout redirect at `/signed-out` | `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:105-110` (modify) | 570B.3 | existing `OidcClientInitiatedLogoutSuccessHandler.setPostLogoutRedirectUri(frontendUrl)` | Change to `setPostLogoutRedirectUri(frontendUrl + "/signed-out")`. Logout *initiation* (CSRF form POST in `user-menu-bff.tsx:69-97`) is unchanged. Verify logout completeness: `SESSION` cookie deleted (`.deleteCookies("SESSION")` present), KC SSO session terminated (RP end-session present), `SPRING_SESSION` row GC'd by timeout. |
| 570B.2 | Verify KC post-logout allowlist + optional PII-log reduction | `compose/keycloak/realm-export.json:63` (verify only — already wildcards `…/*` for localhost + `app-dev.heykazi.com`); `gateway/.../controller/BffController.java:78` (modify, optional) | 570B.3 | §11.6; existing `BffController` logging | Verify `/signed-out` is an allowed post-logout target across dev + `app-dev` + prod (likely no change needed — confirm). **Optional, non-blocking**: reduce `/bff/me` full-OIDC-claims logging to non-PII (subject id + org slug) or `debug` (POPIA). |
| 570B.3 | Gateway logout + e2e tests | `gateway/src/test/java/io/b2mash/b2b/gateway/config/Oauth2LoginSuccessHandlerTest.java` (modify) or new logout-handler test; `frontend/e2e/<auth-logout>.spec.ts` (new) | gateway (~1-2): post-logout redirect URI resolves to `frontendUrl + "/signed-out"`; (optional) no full-claims line at info. e2e (~1): logout lands on branded `/signed-out` | existing gateway config tests; `frontend/e2e/` patterns | e2e on mock-auth (CI) + KC dev stack (manual). |

### Key Files

**Create (frontend):**
- `frontend/app/(auth)/signed-out/page.tsx`
- `frontend/e2e/<auth-logout>.spec.ts`

**Modify (frontend):**
- `frontend/lib/auth/middleware.ts:7-16` — add `/signed-out` to `PUBLIC_ROUTES`

**Modify (gateway):**
- `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:105-110` — post-logout redirect → `/signed-out`
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java:78` — optional PII-log reduction

**Verify (keycloak-config, no change expected):**
- `compose/keycloak/realm-export.json:63` — `post.logout.redirect.uris` covers `/signed-out`

**Read for context:**
- `frontend/components/auth/user-menu-bff.tsx:69-97` — logout CSRF POST (unchanged)
- `gateway/src/test/java/io/b2mash/b2b/gateway/config/Oauth2LoginSuccessHandlerTest.java` — handler test pattern

### Architecture Decisions

- **Branded landing is a first-party route** ([ADR-310]) — the post-logout redirect already targets the frontend, so `/signed-out` is a frontend route (not a KC theme page); KC theme pages are reserved for failures *inside* Keycloak (error/info — Epic 572).
- **PII-log hardening is non-blocking** ([ADR-308] security notes / §11.6) — folded into the gateway slice as a small task; it never blocks the epic.
- **Logout completeness verified, not changed** (§11.6) — cookie + IdP session + row GC already correct; this epic only redirects the landing.

### Non-scope

- No change to logout *initiation* or the CSRF flow.
- No magic-link/portal logout rework (ADR-T005, out of scope).

---

## Epic 571: Change-Password via `kc_action=UPDATE_PASSWORD`

**Goal**: Let users change their own password from inside the app via an "Account & Security" menu item that initiates Keycloak's `kc_action=UPDATE_PASSWORD` through the gateway OAuth client, rendering Keycloak's `login-update-password` page under the already-Kazi-branded login theme, then returning cleanly to the app. The account console is deliberately NOT used (it renders unbranded — `accountThemeImplementation: none`).

**References**: Architecture §11.4.5 (change-password via `kc_action=UPDATE_PASSWORD`), §11.5 (sequence diagram 3), §11.7 (theme inventory — `LoginUpdatePassword`), §11.10 slice 4; [ADR-311]. Requirements §4.1, §4.2.

**Dependencies**: Epic 572 (the `login-update-password` page must be confirmed/branded under the Kazi login theme for the *fully branded* flow; the functional flow works without it, so 571A/B can be coded in parallel with 572 and verified once 572 lands).

**Scope**: gateway (571A) + frontend (571B) — split by category (the two cross categories, so they must be separate slices).

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary | Status |
|-------|-------|---------------|---------|--------|
| **571A** | 571A.1–571A.2 | ~3 (GatewaySecurityConfig or BffController modify + 1 test + verify yml) | Gateway initiation that adds `kc_action=UPDATE_PASSWORD` to the OAuth client's authorization request; clean return-to-app; gateway test. | |
| **571B** | 571B.1–571B.2 | ~3 (user-menu-bff modify + 1 e2e + verify portal) | "Account & Security" user-menu item triggering 571A; Playwright change-password e2e; portal entry where applicable (likely omitted — magic-link). | |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 571A.1 | Add gateway change-password initiation (`kc_action=UPDATE_PASSWORD`) | `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java` (modify) or `gateway/.../controller/BffController.java` (modify) | 571A.2 | existing OAuth2 authorization-request customisation in `GatewaySecurityConfig.java`; §11.4.5 | Add `kc_action=UPDATE_PASSWORD` to the gateway's authorization request, riding the existing OAuth client (no parallel auth, no direct frontend→KC call). Either an `OAuth2AuthorizationRequestResolver` that appends `kc_action` for a dedicated initiation path, or a `/bff` endpoint that redirects into `/oauth2/authorization/keycloak` with the param. Return cleanly to the app after the action. NO account-console URL helper. |
| 571A.2 | Gateway change-password test | `gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java` (modify) or new test | ~1-2: the change-password initiation includes `kc_action=UPDATE_PASSWORD` in the resolved authorization request | existing gateway config-test pattern | Assert the param is appended; no live KC needed for the unit gate. |
| 571B.1 | Add "Account & Security" user-menu item | `frontend/components/auth/user-menu-bff.tsx:140-158` (modify) | 571B.2 | existing user-menu dropdown items (logout entry at lines 69-97) | Add an "Account & Security" dropdown item that navigates to the 571A gateway initiation. Any client fetch routes through the 569 client funnel. Match the menu's existing item styling. |
| 571B.2 | Playwright change-password e2e + portal check | `frontend/e2e/<auth-change-password>.spec.ts` (new); verify `portal/components/portal-topbar.tsx` (read-only check) | ~1 e2e: "Account & Security" → renders the Kazi-branded `login-update-password` page → set password → returns to app | `frontend/e2e/` patterns; KC dev stack | Portal uses magic-link auth (ADR-T005) — if its model has no password, the change-password entry is **omitted** in portal and noted as out of scope (verify `portal-topbar.tsx`). e2e on KC dev stack (renders the real KC page); branded-page assertion depends on 572. |

### Key Files

**Create (frontend):**
- `frontend/e2e/<auth-change-password>.spec.ts`

**Modify (gateway):**
- `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java` (or `controller/BffController.java`) — `kc_action=UPDATE_PASSWORD` initiation

**Modify (frontend):**
- `frontend/components/auth/user-menu-bff.tsx:140-158` — "Account & Security" item

**Verify (frontend/portal):**
- `portal/components/portal-topbar.tsx` — change-password entry only if magic-link model supports it (likely omit)

**Read for context:**
- `compose/keycloak/theme/vite.config.ts:12` — `accountThemeImplementation: none` (account console intentionally unbranded → not used)
- Keycloakify default `LoginUpdatePassword.tsx` (node_modules) — the page rendered by `kc_action`

### Architecture Decisions

- **`kc_action=UPDATE_PASSWORD` via the login theme, not the account console** ([ADR-311]) — the account console runs `accountThemeImplementation: none` (renders stock/unbranded, reintroducing the exact whitelabel leak this phase kills); the login theme is Kazi-branded and includes `login-update-password`. No account-theme build (out of scope).
- **Rides the existing gateway OAuth client** ([ADR-311]) — the frontend never talks to Keycloak directly; initiation goes through the gateway, no parallel auth.
- **Deep-link only, no native form** (§4.2) — Keycloak owns password policy/strength; v1 has no in-app password form.

### Non-scope

- No native in-app change-password form, no in-app password-strength UI.
- No MFA/passkeys/TOTP enrolment (future).
- No portal magic-link rework.

---

## Epic 572: Keycloak Theme Visible-Brand Rebrand + Page Coverage

**Goal**: Rebrand the Keycloak theme **Kazi** (visible brand only) and bring *every* user-reachable Keycloak page under it — closing the error/info/logout whitelabel leaks and confirming the `login-update-password` page (used by Epic 571) is branded — then rebuild and redeploy the theme JAR and verify every page renders Kazi in a real browser. The realm id, issuer URI, and theme directory identifier (`docteams`) are deliberately NOT renamed (load-bearing across ~30 files / 4 services).

**References**: Architecture §11.4.6 (theme audit + page coverage), §11.7 (theme inventory table), §11.10 slice 5; [ADR-312]. Requirements §5.1, §5.2, §5.3.

**Dependencies**: None functionally (independent — can start immediately, in parallel with 568); coordinate visual tokens (slate+teal, Kazi logo) with the 569/570 frontend `(auth)` pages for one-product consistency.

**Scope**: theme only (both slices).

**Estimated Effort**: M

### Slices

| Slice | Tasks | Files Touched | Summary | Status |
|-------|-------|---------------|---------|--------|
| **572A** | 572A.1–572A.3 | ~5 (Error.tsx + Info.tsx + logout surface + LoginUpdatePassword confirm/override + shared template/CSS) | Bring `Error.tsx`/`Info.tsx`/logout under the Kazi theme; confirm/brand `login-update-password`. | **Done** (PR #1485) |
| **572B** | 572B.1–572B.3 | ~8-10 (login pages audit + email ftl audit + JAR rebuild artifacts + browser verification notes) | Visible-brand audit of all login pages + email templates; JAR rebuild/redeploy; browser render verification of every page. | **Done** (PR #1485) |

### Tasks

| ID | Task | Files Touched | Tests | Pattern Reference | Notes |
|----|------|---------------|-------|-------------------|-------|
| 572A.1 | Bring Error + Info pages under Kazi theme | `compose/keycloak/theme/src/login/pages/Error.tsx` (modify), `compose/keycloak/theme/src/login/pages/Info.tsx` (modify) | 572B.3 (browser verification) | already-themed `Login.tsx`/`LoginPassword.tsx` + the shared `pages/shared/` template/CSS as the branding reference | These are the current whitelabel leaks. Apply Kazi logo/copy/palette (slate+teal) matching the app shell and the new `(auth)` pages. |
| 572A.2 | Brand the logout / SSO-logout surface | logout/SSO-logout page under `compose/keycloak/theme/src/login/pages/` (modify/add override as needed) | 572B.3 | shared template/CSS; §11.7 logout row | Ensure the KC-side logout/SSO-logout surface renders Kazi (distinct from the frontend `/signed-out` route — this covers logout *inside* Keycloak). |
| 572A.3 | Confirm/brand `login-update-password` | `compose/keycloak/theme/src/login/pages/LoginUpdatePassword.tsx` (add override only if needed) | 572B.3 + 571B.2 e2e | Keycloakify default `LoginUpdatePassword.tsx` (renders under the docteams `Template.tsx` already) | The `kc_action=UPDATE_PASSWORD` page (Epic 571) renders here. There is currently NO custom override (only the Keycloakify default, which inherits the themed template/CSS). Confirm it reads Kazi; add a thin override only if copy/branding needs it. |
| 572B.1 | Visible-brand audit of login pages | `compose/keycloak/theme/src/login/pages/*.tsx` (modify), shared template/CSS, theme assets/logo | 572B.3 | existing theme assets; §11.4.6 / §11.7 | Sweep `Login.tsx`, `LoginUsername.tsx`, `LoginPassword.tsx`, `LoginResetPassword.tsx`, `LoginVerifyEmail.tsx`, `Register.tsx` (+ shared) for "DocTeams" copy/logo/title/palette → **Kazi** (slate+teal). Leave the `docteams` directory + `loginTheme`/`emailTheme` identifier unchanged (ADR-312). |
| 572B.2 | Audit email templates + rebuild/redeploy theme JAR | `compose/keycloak/themes/docteams/email/html/{email-verification,org-invite,executeActions,template}.ftl` (modify), `compose/keycloak/theme/dist_keycloak/` + `compose/keycloak/providers/keycloak-theme.jar` (rebuild artifacts) | 572B.3 | existing email ftl; `build-keycloak-theme` toolchain | Re-confirm Kazi branding in the 4 email templates. Rebuild + redeploy the theme JAR (`build-keycloak-theme`); `accountThemeImplementation` stays `none` (no account-theme build). |
| 572B.3 | Browser render verification (every KC page) | verification notes (manual QA; no source-inferred PASS) | manual: browser-verify login, username/password split, reset-password, verify-email, register, **error.ftl**, **info.ftl**, logout/SSO-logout, `login-update-password`, and the 4 email templates render Kazi | §11.8 "Theme render verification"; project "PASS means observed" gate | Verify rendered against the KC dev stack in a real browser — NOT inferred from source. Consistency check: KC surfaces + frontend `(auth)` pages + app shell read as one product. |

### Key Files

**Modify (theme — login pages):**
- `compose/keycloak/theme/src/login/pages/Error.tsx`, `Info.tsx`
- `compose/keycloak/theme/src/login/pages/Login.tsx`, `LoginUsername.tsx`, `LoginPassword.tsx`, `LoginResetPassword.tsx`, `LoginVerifyEmail.tsx`, `Register.tsx`, `shared/` template/CSS
- `compose/keycloak/theme/src/login/pages/LoginUpdatePassword.tsx` (add override only if needed)
- logout/SSO-logout surface override (572A.2)

**Modify (theme — email + build):**
- `compose/keycloak/themes/docteams/email/html/{email-verification,org-invite,executeActions,template}.ftl`
- `compose/keycloak/theme/dist_keycloak/` + `compose/keycloak/providers/keycloak-theme.jar` (rebuild/redeploy)

**Read for context:**
- `compose/keycloak/theme/vite.config.ts:12` — `accountThemeImplementation: none` (do not change)
- `compose/keycloak/realm-export.json:5-6` — `loginTheme`/`emailTheme` = `docteams` (identifier unchanged)

### Architecture Decisions

- **Render-only rebrand; identifiers unchanged** ([ADR-312]) — the realm id (`docteams`) is load-bearing in the OIDC issuer URI `realms/docteams` + redirect URIs across ~30 files / 4 services; renaming invalidates tokens for zero user benefit. Rebrand = what the user sees (copy/logo/palette), not internal identifiers or the theme directory.
- **Full page coverage kills the whitelabel leak** (§5.2) — error/info/logout pages are the current leaks; bringing them under the Kazi theme is the core deliverable.
- **No account-theme build** ([ADR-311], [ADR-312]) — `accountThemeImplementation` stays `none`; the account console is intentionally not themed and not used (Epic 571 uses the login theme instead).
- **PASS means observed** (project gate) — theme branding is verified rendered in a real browser against the KC dev stack, never inferred from source.

### Non-scope

- No realm/theme-directory/identifier rename.
- No account-theme build.
- No app-wide dark-mode / design-token audit (auth surfaces only).

---

## Phase-level Testing Summary

| Test | Scope | Slice |
|---|---|---|
| Realm lifetime import assertion (`300/1800/36000`; no `offlineSession*`) | keycloak-config | 568A.3 |
| Gateway session timeout config test (`== 10h`, prod inherits) | gateway | 568B.2 |
| Expiry-funnel unit tests (per entry-point class → branded redirect) | frontend vitest | 569A.4 |
| Return-to allowlist tests (reject `http://`/`//`/`/\`/`javascript:`; accept `/dashboard`,`/org/...`) | frontend vitest | 569A.4 |
| Playwright e2e — expiry → `/sign-in?reason=expired` + return-to resume | frontend e2e | 569B.4 |
| Playwright e2e — logout → branded `/signed-out` | frontend e2e | 570B.3 |
| Gateway post-logout redirect-URI test (`+ "/signed-out"`) | gateway | 570B.3 |
| Gateway `kc_action=UPDATE_PASSWORD` initiation test | gateway | 571A.2 |
| Playwright e2e — "Account & Security" → Kazi `login-update-password` → returns to app | frontend e2e | 571B.2 |
| Browser render verification — every KC page renders Kazi (login/email/error/info/logout/update-password) | theme manual QA | 572B.3 |
| **Manual KC-dev idle-expiry reproduction** (reproduce-before-fix gate) | manual QA | 569 (pre-fix), 568 (post-fix confirms mismatch gone) |

---

## Slice-sizing sanity check

Every slice touches ≤8-12 files and stays within ~800 LOC; tests live with the code they test; no slice mixes scope categories (568A keycloak-config / 568B gateway / 569A+569B+570A frontend / 570B+571A gateway / 571B frontend / 572A+572B theme). The largest reads are in 569B (4 existing auth files) and 572B (login-page sweep) — both under the 15-file read budget. 9 slices across 5 epics, within the 5-epic / 7-10-slice target.