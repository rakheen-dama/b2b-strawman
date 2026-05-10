# `gateway/` repo

Per-repo entry point — thin Spring Cloud Gateway BFF.

## 1. Role

Thin BFF in front of the staff backend: terminates Keycloak OIDC login, holds the OAuth2 session in a server-side `SESSION` cookie, and proxies `/api/**` to the backend with `TokenRelay=` injecting the access token. Single YAML route, no custom Java `GatewayFilter`/`RouteLocator` beans yet — intentionally minimal.

→ `gateway/src/main/resources/application.yml:46`
→ `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java`
→ ADR-T004 (gateway-bff-over-direct-api-access)

## 2. Tech stack

- Spring Boot 4.0.2, Java 25, Maven (`./mvnw`)
- Spring Cloud Gateway Server WebMVC (`spring-cloud-starter-gateway-server-webmvc`, BOM `2025.1.1`)
- Spring Security OAuth2 client (`spring-boot-starter-oauth2-client`)
- Spring Session — both backends on the classpath: `spring-session-jdbc` (Postgres, default) and `spring-session-data-redis` (alternative); chosen by `spring.session.store-type` plus classpath presence
- Spring Boot Actuator (health only), validation, virtual threads enabled
- PostgreSQL JDBC driver (runtime)
- Test: spring-security-test, spring-boot-webmvc-test, H2 (Spring Session JDBC), WireMock 3.13 (Keycloak Admin API)

→ `gateway/pom.xml:29`

## 3. Discovery report

- `_discovery/A3-portal-gateway-map.md` — gateway section (§§7–10, 12)

## 4. Top-level directory map

| Path | Contents |
|---|---|
| `src/main/java/io/b2mash/b2b/gateway/GatewayApplication.java` | `@SpringBootApplication` entry point — 12 lines, no customisation |
| `src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java` | `SecurityFilterChain`, OAuth2 login + OIDC logout, CSRF (`CookieCsrfTokenRepository.withHttpOnlyFalse()`), CORS, session-fixation rotation, `KC_LAST_LOGIN_SUB` cookie emission for the Next.js middleware (GAP-L-22 signal) |
| `src/main/java/io/b2mash/b2b/gateway/config/SpaCsrfTokenRequestHandler.java` | BREACH-mitigating CSRF token handler for SPA — XOR-encoded header tokens |
| `src/main/java/io/b2mash/b2b/gateway/controller/BffController.java` | Small BFF helper endpoints (`/bff/me`, `/bff/csrf`) — A3 noted these as 404 stubs but the controller exists; treat as scaffolding |
| `src/main/resources/application.yml` | Single route + Keycloak provider + session cookie config + datasource for JDBC session |
| `src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java` | Authoritative test of all security rules |
| `src/test/.../{RouteConfigTest, SessionStorageTest, SessionTestSupport, integration/, controller/}` | Route wiring, JDBC vs Redis session backend selection, BFF controller tests |

→ `gateway/src/main/java/io/b2mash/b2b/gateway/`

## 5. Build & run

```bash
# from gateway/
./mvnw verify                # full test suite — gate before merge
./mvnw spring-boot:run       # port 8443
```

Agent service management (Keycloak mode):

```bash
bash compose/scripts/svc.sh restart gateway
bash compose/scripts/svc.sh logs gateway
```

→ `compose/scripts/svc.sh`

## 6. Test stack

- `GatewaySecurityConfigTest` — authoritative coverage of every `requestMatchers(...)` rule, CSRF ignore patterns, CORS config, OIDC logout handler, `KC_LAST_LOGIN_SUB` cookie emission. Treat this as the spec for security behaviour; changes to `GatewaySecurityConfig.java` must update it.
- `RouteConfigTest` — verifies the YAML-declared `backend-api` route is registered with `Path=/api/**` predicate and `TokenRelay=` filter.
- `SessionStorageTest` + `SessionTestSupport` — exercise both JDBC (H2) and Redis session backends; the dual-backend support is real, not aspirational.
- `GatewayApplicationTest` — context-loads smoke test.
- `integration/` — WireMock-backed end-to-end flows against a stubbed Keycloak.

→ `gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java`

## 7. Deployment unit

- Single Docker image. Runs on **port 8443** in Keycloak mode (`bash compose/scripts/svc.sh start all`).
- **NOT used in mock-auth mode.** The E2E stack (`compose/docker-compose.e2e.yml`, port 3001 frontend / 8081 backend) bypasses the gateway entirely — the staff frontend talks to the backend directly with a mock IDP. See `agent-e2e-stack.md`.
- Session store: Postgres `SPRING_SESSION` table (auto-initialised in dev via `spring.session.jdbc.initialize-schema=always`; production should use Flyway — TODO in `application.yml:23`).

→ `compose/scripts/svc.sh`
→ `gateway/src/main/resources/application.yml:20`

## 8. Routing surface

**Single declared route** in `application.yml`:

| Id | Predicate | URI | Filters |
|---|---|---|---|
| `backend-api` | `Path=/api/**` | `${BACKEND_URL:http://localhost:8080}` | `TokenRelay=`, `DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST` |

→ `gateway/src/main/resources/application.yml:46`

**Security path matchers** (in `GatewaySecurityConfig.securityFilterChain`):

| Pattern | Disposition |
|---|---|
| `/`, `/error`, `/actuator/health`, `/bff/me`, `/bff/csrf` | `permitAll` |
| `/api/access-requests`, `/api/access-requests/verify` | `permitAll` (pre-auth tenant access-request flow) |
| `/internal/**` | `denyAll` (blocked even for authenticated users) |
| `/api/**` (everything else) | `authenticated` — returns `401` (no login redirect) per `HttpStatusEntryPoint` |
| `anyRequest` | `authenticated` — triggers OAuth2 login redirect to Keycloak |

CSRF: `CookieCsrfTokenRepository.withHttpOnlyFalse()` (XSRF-TOKEN cookie readable by JS), ignored for `/bff/**` and `/api/**` (server-to-server from Next.js, protected by SESSION + SameSite=Lax + CORS).

CORS: `allowedOrigins = ${gateway.frontend-url}` only; credentials allowed; methods `GET POST PUT DELETE PATCH OPTIONS`.

→ `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:46`

## 9. Most-edited / hottest areas

The gateway is intentionally thin and rarely changes.

- **Phase 36 — Keycloak + Gateway BFF** is the foundational phase that established the current shape (one YAML route + `GatewaySecurityConfig`).
- **Phase 54** added the e2e test suite (security config tests, route config test, session storage tests).
- The `KC_LAST_LOGIN_SUB` cookie + `oauth2LoginSuccessHandler` were added during the GAP-L-22 (stale SESSION handoff) verify cycle on 2026-04-25 — passive signal only; decision logic lives in the Next.js middleware.
- No custom `GatewayFilter`, rate-limiter, or webhook-ingress filter has been added since. ADR-096 / ADR-097 (rate limiting, webhooks) are **not** implemented at the gateway layer.

→ `architecture/phase36-keycloak-gateway-bff.md`
→ `project_verify_cycle_2026-04-25_landed.md` (user memory)

## 10. Profile / environment quirks

- `BACKEND_URL` (default `http://localhost:8080`) — proxy target for the `backend-api` route.
- `FRONTEND_URL` → `gateway.frontend-url` (default `http://localhost:3000`) — single CORS allowed origin and post-logout redirect target.
- `KEYCLOAK_ISSUER` (default `http://localhost:8180/realms/docteams`), `KEYCLOAK_CLIENT_ID` (default `gateway-bff`), `KEYCLOAK_CLIENT_SECRET`.
- `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` — JDBC session store. Note: on the dev machine Postgres is `b2mash.local:5432`, not `localhost` (see user memory "Environment").
- Session backend selection: `spring.session.store-type=jdbc` is the default; switching to Redis requires both the redis starter on the classpath (already there) and overriding `store-type` plus pointing `spring.data.redis.*` at a Redis instance.
- `SESSION` cookie attributes: `HttpOnly=true`, `Secure=false` (local dev — must be `true` in production over HTTPS), `SameSite=Lax`, name `SESSION`. Configured in `application.yml:3`.
- Virtual threads enabled (`spring.threads.virtual.enabled=true`).
- `management.endpoints.web.exposure.include=health` only — no metrics, info, or env exposed.

→ `gateway/src/main/resources/application.yml:1`

## 11. Architectural notes

- **Zero product awareness.** The gateway has no concept of vertical, tenant, capability, customer, or any domain entity. It only knows OAuth2, sessions, and `/api/**` → backend. All tenant resolution happens in the backend from the Keycloak JWT — the gateway does not inject `X-Org-Id` or any tenant header. This is the "transparently a token-relay layer, which is also a quiet superpower" property called out in A6 (`_discovery/A6-cross-cutting-bones.md`).
- **The portal does NOT use the gateway.** End-customer traffic (`portal/`, port 3002) calls the backend directly on `localhost:8080` with a magic-link JWT in `localStorage`. Two trust boundaries, two auth stacks — see A3 §11 and `00-overview.md`. Confirmed at `portal/lib/api-client.ts:4`.
- **Trust-accounting hard guards live in the backend, not here.** `TrustBoundaryGuard` is a backend Phase-71 component (ADR-276); the gateway is unaware of trust data. See `30-modules/accounting-integration.md` once filled.
- **Active ADRs:**
  - ADR-T004 — gateway-bff-over-direct-api-access (foundational decision: BFF pattern, not API-key direct access).
- **Known fragility:**
  - `spring.session.jdbc.initialize-schema=always` is a dev shortcut; the comment at `application.yml:23` flags the need for a Flyway migration in production.
  - The `controller/BffController.java` exists but A3 reported `/bff/me` and `/bff/csrf` as 404s — verify what it actually serves before relying on it.
  - `Secure=false` on the SESSION cookie must be flipped for production HTTPS — there is no profile override yet.

→ `_discovery/A6-cross-cutting-bones.md`
→ `_discovery/A3-portal-gateway-map.md` §11
→ `adr/ADR-T004-gateway-bff-over-direct-api-access.md`
