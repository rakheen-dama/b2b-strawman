# A3 — Portal + Gateway Structural Map

**Kazi Customer Portal (`portal/`) and Spring Cloud Gateway BFF (`gateway/`)**
Produced: 2026-05-10

---

## Portal App

### 1. Top-level structure

```
portal/
├── app/                    Next.js App Router root
│   ├── layout.tsx          Root HTML shell; loads Sora/Plex/JetBrains fonts
│   ├── globals.css         Tailwind v4 tokens; light/dark theme; Concrete Studio palette
│   ├── (authenticated)/    Route group — all pages requiring a valid portal JWT
│   ├── auth/exchange/      Magic-link token exchange page (no auth required)
│   ├── login/              Email + orgId form; requests magic link
│   └── accept/[token]/     Token-gated document acceptance flow (no auth required)
├── components/             Shared UI components (portal-sidebar, portal-topbar, etc.)
├── hooks/                  React hooks: use-auth, use-portal-context, use-branding
├── lib/                    Auth store, API client, types, terminology, format, utils
│   └── api/                Module-specific typed API clients (trust, deadlines, retainer, acceptance)
└── public/                 Static assets
```

### 2. Route map

```
/                               → middleware redirects to /home
/login                          Login form — collects email + orgId, calls POST /portal/auth/request-link
/auth/exchange                  Consumes ?token=&orgId=; calls POST /portal/auth/exchange; stores JWT in localStorage
/accept/[token]                 Document acceptance page — token-gated, no session required; GET + POST /api/portal/acceptance/:token

(authenticated)/ — all under AuthenticatedLayout (JWT guard + PortalContextProvider)
  /home                         Dashboard — cards for: info requests, pending acceptances, deadlines, recent invoices, trust movements
  /projects                     Project list with All/Active/Past filter; GET /portal/projects
  /projects/[id]                Project detail — name, status, tasks, documents, comments, summary; 5× parallel GET
  /invoices                     Invoice list; GET /portal/invoices
  /invoices/[id]                Invoice detail — line items, tax breakdown, PDF download, Pay Now button
  /proposals                    Proposal list (actionable vs. past); GET /portal/api/proposals
  /proposals/[id]               Proposal detail — fee, Tiptap HTML content, Accept/Decline actions
  /requests                     Information-request list; GET /portal/requests (module: information_requests)
  /requests/[id]                Request detail — item cards with file upload + submit flow
  /acceptance                   NOT a standalone page — acceptances surfaced via PendingAcceptancesList on /projects
  /deadlines                    Deadline list with status/type filters; GET /portal/deadlines?from=&to= (module: deadlines; profiles: accounting-za, legal-za)
  /trust                        Trust matter selector — auto-redirects if exactly 1 matter; GET /portal/trust/summary (module: trust_accounting; profiles: legal-za)
  /trust/[matterId]             Trust matter detail — balance card, transaction list, statement documents
  /retainer                     Retainer index — hour-bank cards; GET /portal/retainers (module: retainer_agreements; profiles: legal-za, consulting-za)
  /retainer/[id]                Retainer detail — HourBankCard + ConsumptionList
  /activity                     Activity feed — mine/firm tabs; GET /portal/activity?tab=MINE|FIRM
  /profile                      Contact profile card; GET /portal/me
```

**Total pages enumerated: 18 distinct route handlers** (root redirect + 2 public auth routes + 1 public acceptance route + 14 authenticated pages).

### 3. Auth flow

The portal uses **magic-link** authentication with JWT storage in `localStorage` (not cookies). The flow has four steps:

**Step 1 — Request link**
`/login/page.tsx:109` calls `publicFetch("/portal/auth/request-link", { method: "POST", body: { email, orgId } })` via `lib/api-client.ts:publicFetch`.

**Step 2 — Email delivery**
Backend generates a token and emails the link. In dev mode, the response body includes `magicLink` (displayed in the UI at `/login/page.tsx:165`).

**Step 3 — Token exchange**
User clicks the link; browser lands on `/auth/exchange?token=X&orgId=Y`. `auth/exchange/page.tsx:41` calls `publicFetch("/portal/auth/exchange", { method: "POST", body: { token, orgId } })` → `lib/api-client.ts:publicFetch`. On success the backend returns `{ token, email, customerId, customerName }`.

**Step 4 — Session storage**
`auth/exchange/page.tsx:63` calls `storeAuth(data.token, { id, name, email, orgId })` → `lib/auth.ts:storeAuth:52` writes `portal_jwt` + `portal_customer` + `portal_last_org_id` to `localStorage`. Expiry is checked via `jose.decodeJwt` on every `getAuth()` call (`lib/auth.ts:76`).

**Subsequent requests**
`lib/api-client.ts:portalFetch:16` reads the JWT from `localStorage` via `getJwt()` and injects `Authorization: Bearer <jwt>` on every call to `${NEXT_PUBLIC_PORTAL_API_URL}${path}`. On HTTP 401 the client calls `clearAuth()` and hard-navigates to `/login` (`api-client.ts:30–34`).

**Auth guard**
`app/(authenticated)/layout.tsx:75–91` uses `useSyncExternalStore` to watch the auth store. On mount, if `!isAuthenticated`, it redirects to `/login?redirectTo=<path>&orgId=<lastOrg>`. The `portal_last_org_id` key is NOT cleared on logout so deep-link returns after expiry still resolve the tenant (`lib/auth.ts:58`).

### 4. Backend interaction

**Base URL:** `NEXT_PUBLIC_PORTAL_API_URL` (default `http://localhost:8080`) — this points **directly to the main Spring Boot backend**, not to the gateway. The portal does not pass through the gateway.

**Public endpoints (no JWT):**

| Path | Method | Source |
|------|--------|--------|
| `/portal/branding?orgId=` | GET | `login/page.tsx:64` |
| `/portal/auth/request-link` | POST | `login/page.tsx:109` |
| `/portal/auth/exchange` | POST | `auth/exchange/page.tsx:41` |
| `/api/portal/acceptance/:token` | GET | `lib/api/acceptance.ts:11` |
| `/api/portal/acceptance/:token/accept` | POST | `lib/api/acceptance.ts:24` |
| `/api/portal/acceptance/:token/pdf` | GET (iframe src) | `lib/api/acceptance.ts:44` |

**Authenticated endpoints (Bearer JWT):**

| Path | Method | Page/component |
|------|--------|----------------|
| `/portal/session/context` | GET | `hooks/use-portal-context.ts:49` |
| `/portal/me` | GET | `(authenticated)/profile/page.tsx:67` |
| `/portal/projects` | GET | `projects/page.tsx:56` |
| `/portal/projects/:id` | GET | `projects/[id]/page.tsx:70` |
| `/portal/projects/:id/tasks` | GET | `projects/[id]/page.tsx:71` |
| `/portal/projects/:id/documents` | GET | `projects/[id]/page.tsx:72` |
| `/portal/projects/:id/comments` | GET | `projects/[id]/page.tsx:73` |
| `/portal/projects/:id/summary` | GET | `projects/[id]/page.tsx:74` |
| `/portal/projects/:id/comments` | POST | `components/comment-section` |
| `/portal/invoices` | GET | `invoices/page.tsx:33` |
| `/portal/invoices/:id` | GET | `invoices/[id]/page.tsx:43` |
| `/portal/invoices/:id/download` | GET | `invoices/[id]/page.tsx:61` |
| `/portal/invoices/:id/payment-status` | GET | `lib/api-client.ts:87` |
| `/portal/api/proposals` | GET | `proposals/page.tsx:32` |
| `/portal/api/proposals/:id` | GET | `proposals/[id]/page.tsx:68` |
| `/portal/api/proposals/:id/accept` | POST | `proposals/[id]/page.tsx:89` |
| `/portal/api/proposals/:id/decline` | POST | `proposals/[id]/page.tsx:110` |
| `/portal/requests` | GET | `requests/page.tsx:29` |
| `/portal/requests/:id` | GET | `requests/[id]/page.tsx:63` |
| `/portal/requests/:id/items/:itemId/upload` | POST | `requests/[id]/page.tsx:138` |
| `/portal/requests/:id/items/:itemId/submit` | POST | `requests/[id]/page.tsx:153` |
| `/portal/acceptance-requests/pending` | GET | `components/pending-acceptances-list.tsx:38` |
| `/portal/deadlines` | GET | `lib/api/deadlines.ts:76` |
| `/portal/deadlines/:sourceEntity/:id` | GET | `lib/api/deadlines.ts:88` |
| `/portal/trust/summary` | GET | `lib/api/trust.ts:68` |
| `/portal/trust/movements?limit=1` | GET | `home/page.tsx:247` |
| `/portal/trust/matters/:matterId/transactions` | GET | `lib/api/trust.ts:84` |
| `/portal/trust/matters/:matterId/statement-documents` | GET | `lib/api/trust.ts:97` |
| `/portal/retainers` | GET | `lib/api/retainer.ts` |
| `/portal/retainers/:id/consumption` | GET | `components/retainer/consumption-list` |
| `/portal/activity?tab=` | GET | `activity/page.tsx:48` |

**Read-model schema:** The portal routes (`/portal/*`) in the backend are served by a separate read-model layer. The `PortalSessionContext` response (`GET /portal/session/context`) returns `tenantProfile`, `enabledModules`, `terminologyKey`, `brandColor`, `orgName`, `logoUrl` — confirming the backend maintains a portal-specific projection, consistent with ADR-031/ADR-078. The acceptance endpoints use a different path prefix (`/api/portal/acceptance/*`) suggesting they may be served by a different controller group or are pre-auth accessible.

### 5. Functional surface

The portal gives an end-customer access to:

1. **Dashboard (home)** — summary widgets for pending items across all enabled modules.
2. **Matters/Projects** — list and detail view; tasks, documents, comments, billable hours.
3. **Invoices** — list, detail with line items and tax breakdown, PDF download, Pay Now (Stripe/external payment link).
4. **Proposals** — list and detail; accept or decline with optional reason.
5. **Information Requests** — list and detail; file upload + submit per item.
6. **Document Acceptance** — token-gated page (no login required) for reviewing a PDF and signing with typed name.
7. **Deadlines** — list with status/type filters and a slide-over detail panel (legal-za/accounting-za only).
8. **Trust Ledger** — per-matter balance, paginated transaction list, statement document downloads (legal-za + trust_accounting module).
9. **Retainer** — hour-bank balance card with consumption breakdown (legal-za/consulting-za + retainer_agreements module).
10. **Activity feed** — "mine" vs. "firm" tabs showing action timeline.
11. **Profile** — read-only contact info (name, email, role, customer).

### 6. Vertical-aware UI

**Yes — fully implemented via `TerminologyProvider`.** Three mechanisms:

**Terminology map** (`lib/terminology-map.ts:1`): Three profiles — `consulting-za`, `accounting-za`, `legal-za` — each with a `Record<string, string>` that overrides generic English terms. Examples: `Invoice → Fee Note`, `Project → Matter`, `Expense → Disbursement` (legal-za).

**Provider chain** (`app/(authenticated)/layout.tsx:35–38`): `verticalProfile` is read from `useProfile()` (which reads `PortalSessionContext.tenantProfile` fetched on mount). Threaded into `TerminologyProvider`. Pages call `useTerminology().t("invoices")` to get the locale-appropriate term.

**Nav item gating** (`lib/nav-items.ts:32–99`): `PORTAL_NAV_ITEMS` entries declare `profiles?: string[]` and `modules?: string[]`. `filterNavItems()` hides inapplicable nav items before render. Trust, Retainer, and Deadlines are profile-gated. Information Requests and Document Acceptance are module-gated.

**Module gating at page level**: Pages like `trust/page.tsx:36–39` and `deadlines/page.tsx:65–69` check `ctx.enabledModules` and `router.replace("/home")` if the module is off. The backend also returns 404 (source of truth).

---

## Gateway App

### 7. Top-level structure

The gateway package root is `io.b2mash.b2b.gateway`. Only two sub-packages contain source files:

```
io.b2mash.b2b.gateway
├── GatewayApplication.java          Spring Boot entry point (@SpringBootApplication)
└── config/
    ├── GatewaySecurityConfig.java    Spring Security + OAuth2 login + logout + CSRF + CORS
    └── SpaCsrfTokenRequestHandler.java  BREACH-mitigating CSRF token handler for SPA
```

No other packages were found: no `filter/`, `web/`, `bff/`, `controller/` sub-packages exist yet (confirmed by exhaustive probing). The gateway is currently minimal.

### 8. Routing config

Routing is **entirely YAML-driven via Spring Cloud Gateway Server WebMVC**. There is a single declared route:

```yaml
# gateway/src/main/resources/application.yml:43–54
spring.cloud.gateway.server.webmvc.routes:
  - id: backend-api
    uri: ${BACKEND_URL:http://localhost:8080}
    predicates:
      - Path=/api/**
    filters:
      - TokenRelay=
      - DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST
```

All `GET/POST/PUT/DELETE/PATCH` requests to `/api/**` are proxied to the backend with the OAuth2 access token injected via `TokenRelay=`. No custom Java `RouteLocator` beans exist; no programmatic route definition was found.

**Additional in-code route matchers** (from `GatewaySecurityConfig.java:49–59`):

| Path pattern | Disposition |
|---|---|
| `/`, `/error`, `/actuator/health` | `permitAll` |
| `/bff/me`, `/bff/csrf` | `permitAll` (BFF endpoints — no controller yet, 404) |
| `/api/access-requests`, `/api/access-requests/verify` | `permitAll` (unauthenticated access request flow) |
| `/internal/**` | `denyAll` — blocked for all users including authenticated ones |
| `/api/**` (all other) | `authenticated` |
| `anyRequest` | `authenticated` (triggers OAuth2 login redirect) |

### 9. Auth termination

**Authentication verification:** Spring Cloud Gateway uses `spring-boot-starter-oauth2-client` with Keycloak as the OIDC provider (`application.yml:29–40`). The gateway holds the OAuth2 session server-side; session state is persisted in either PostgreSQL (via `spring-session-jdbc`) or Redis (via `spring-session-data-redis`), with a default timeout of 8 hours.

**Login flow:**
1. Browser hits a protected path → `GatewaySecurityConfig:64` redirects to Keycloak.
2. Keycloak authenticates and redirects back to `{baseUrl}/login/oauth2/code/keycloak`.
3. On success, `oauth2LoginSuccessHandler` (`GatewaySecurityConfig:112–148`) sets a short-lived `HttpOnly` cookie `KC_LAST_LOGIN_SUB` (120s TTL) containing the OIDC `sub` claim, then redirects to `${FRONTEND_URL}/dashboard`.

**Token relay:** The `TokenRelay=` gateway filter (`application.yml:52`) extracts the OAuth2 access token from the authenticated session and injects it as the `Authorization: Bearer <token>` header on all requests proxied to the backend. The backend receives the JWT directly and performs its own verification.

**Tenant context determination:** No tenant-resolution filter or header injection was found in the gateway code. The JWT itself (issued by Keycloak with org-scoped claims) is what the backend uses to derive the tenant. The gateway does not resolve `X-Org-Id` or similar headers — it is transparent on that dimension.

**Session cookie:** Browser ↔ gateway session uses an `HttpOnly; SameSite=Lax` cookie named `SESSION` (`application.yml:4–9`). The Next.js frontend communicates with the gateway over the same origin so the cookie is sent automatically.

### 10. Cross-cutting middleware

**CSRF:** `CookieCsrfTokenRepository.withHttpOnlyFalse()` sets an `XSRF-TOKEN` cookie readable by JS; SPAs send it back as `X-XSRF-TOKEN`. `SpaCsrfTokenRequestHandler` applies XOR BREACH protection for header-submitted tokens. CSRF is disabled for `/bff/**` and `/api/**` (`GatewaySecurityConfig:77`) since those paths are server-to-server or session-cookie-plus-CORS protected.

**CORS:** `corsConfigurationSource()` (`GatewaySecurityConfig:91–103`) restricts `allowedOrigins` to `${gateway.frontend-url}` only. Credentials allowed; methods `GET POST PUT DELETE PATCH OPTIONS`; exposed headers include `X-XSRF-TOKEN`.

**Session management:** `SessionCreationPolicy.IF_REQUIRED` with `sessionFixation().changeSessionId()`. Session ID is rotated on authentication to prevent fixation.

**Logout:** OIDC-initiated logout via `OidcClientInitiatedLogoutSuccessHandler` → redirects to Keycloak's `end_session_endpoint` with `post_logout_redirect_uri` = frontend URL. Session invalidated; `SESSION` cookie deleted.

**Filters absent:** No custom `WebFilter`, `GatewayFilter`, rate-limiting filter, or webhook-ingress filter was found. The gateway is intentionally thin. ADR-096/ADR-097 (rate limiting, webhooks) are not yet implemented at the gateway layer.

**Access requests (unauthenticated):** `/api/access-requests` and `/api/access-requests/verify` are `permitAll` — these are proxied to the backend without authentication. This enables a pre-auth flow (e.g. tenant access request) before Keycloak login.

---

## Joint observations

### 11. Portal ↔ gateway relationship

**The portal does not use the gateway.** The portal (`portal/`, port 3002) calls the Spring Boot backend directly via `NEXT_PUBLIC_PORTAL_API_URL` (default `http://localhost:8080`), bypassing the gateway entirely. Confirmed at `lib/api-client.ts:4`:

```ts
const BASE_URL = process.env.NEXT_PUBLIC_PORTAL_API_URL ?? "http://localhost:8080";
```

The gateway (port 8443) serves only the main **staff frontend** (`frontend/`, port 3000). The two apps have completely separate authentication stacks:

| Dimension | Gateway + Staff Frontend | Portal |
|---|---|---|
| Auth mechanism | Keycloak OIDC + OAuth2 session cookie | Custom magic-link + JWT in localStorage |
| Session storage | PostgreSQL / Redis (server-side) | localStorage (client-side) |
| Token type | OAuth2 access token (relay to backend) | Custom portal JWT (short-lived) |
| Entry point | `/login/oauth2/code/keycloak` | `/portal/auth/request-link` + `/portal/auth/exchange` |
| Backend path | `/api/**` (proxied by gateway) | `/portal/**` and `/api/portal/**` (direct) |

The trust boundary is clear: portal traffic never transits the gateway; it hits the backend's portal read-model endpoints directly with a portal-scoped JWT that the backend validates independently.

### 12. Trust-accounting hard guards

**Backend-side, not gateway-side.** The gateway itself has no trust-accounting guard. The guard is described in ADR-276 and implemented in the main backend as `TrustBoundaryGuard` (Phase 71), which blocks pushing trust-related invoices to Xero.

On the portal side:
- The `trust` nav item requires both `profiles: ["legal-za"]` AND `modules: ["trust_accounting"]` (`lib/nav-items.ts:43–48`).
- Pages `/trust` and `/trust/[matterId]` perform a client-side module gate and redirect to `/home` if `trust_accounting` is absent (`trust/page.tsx:36–39`).
- The backend is declared the source of truth — endpoints return 404 when the module is disabled, regardless of client-side gating.
- Trust statement downloads are further guarded: `isSafeDownloadUrl()` validates that the `downloadUrl` is `https:` only before rendering the link (`trust/[matterId]/page.tsx:23–29`).
- No gateway-layer enforcement exists for trust data; enforcement is entirely at the backend and portal client level.

---

## Essential files

**Portal:**
- `portal/middleware.ts` — root redirect; auth gating note
- `portal/lib/auth.ts` — JWT localStorage store; expiry logic
- `portal/lib/api-client.ts` — portalFetch/publicFetch; bearer injection; 401 handling
- `portal/app/login/page.tsx` — magic-link request form
- `portal/app/auth/exchange/page.tsx` — token exchange; storeAuth call
- `portal/app/(authenticated)/layout.tsx` — auth guard; PortalContextProvider; TerminologyProvider
- `portal/hooks/use-portal-context.ts` — session context fetch; module/profile hooks
- `portal/lib/nav-items.ts` — navigation gating by profile/module
- `portal/lib/terminology-map.ts` — vertical terminology overrides
- `portal/lib/api/trust.ts` — trust API client
- `portal/lib/api/acceptance.ts` — public acceptance API
- `portal/app/accept/[token]/acceptance-page.tsx` — document acceptance flow

**Gateway:**
- `gateway/src/main/resources/application.yml` — single route declaration; session config; Keycloak provider
- `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java` — security filter chain; CORS; CSRF; auth success handler
- `gateway/src/main/java/io/b2mash/b2b/gateway/config/SpaCsrfTokenRequestHandler.java` — BREACH-mitigating CSRF handler
- `gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java` — authoritative test of all security rules

---

**Summary:** The portal (`portal/`, port 3002) is a standalone Next.js 16 app with 18 route handlers across 3 route groups. Authentication is magic-link: the user submits email+orgId, receives a one-time token by email, and the `/auth/exchange` page exchanges it for a custom portal JWT stored in `localStorage`. Subsequent requests inject `Authorization: Bearer <jwt>` directly to the Spring Boot backend (`localhost:8080`) — the portal bypasses the gateway entirely. The gateway (`gateway/`, port 8443) is intentionally thin: one YAML-declared route (`/api/**` → backend via `TokenRelay=`) and a `GatewaySecurityConfig` that handles Keycloak OIDC login, session-cookie auth, CORS, and CSRF. No custom Java filters exist yet. Trust-accounting guards live in the backend, not the gateway. Surprising finding: the portal and gateway have completely separate trust boundaries and auth stacks with no shared code or traffic path.
