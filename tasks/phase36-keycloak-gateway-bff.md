# Phase 36 — Keycloak + Spring Cloud Gateway BFF Migration

This phase introduces a **Spring Cloud Gateway BFF** (Backend-for-Frontend) that keeps all OAuth2 tokens server-side, adds **JIT tenant provisioning** for Keycloak orgs, creates **themed login/registration pages** via Keycloakify, and rewires team management through **gateway admin proxy endpoints**. The browser never sees a JWT — only an opaque HttpOnly session cookie. The backend remains a stateless resource server.

Phase 35 (PRs #507-#519) already completed: Keycloak SPI, `JwtClaimExtractor` strategy pattern, `KeycloakAdminService`, `OrgManagementController`, next-auth v5 integration, Keycloak Docker setup (26.1), frontend Keycloak auth pages/components, and E2E Keycloak stack. Phase 36 builds on that foundation.

**Architecture doc**: `architecture/phase36-keycloak-gateway-bff.md`

**ADRs**:
- [ADR-138](../adr/ADR-138-keycloak-jwt-claim-structure.md) — Keycloak JWT Claim Structure (built-in `organization` scope + role mapper)
- [ADR-139](../adr/ADR-139-keycloak-org-role-mapper.md) — Organization Role Mapper Strategy (Script Mapper first, Java SPI fallback)
- [ADR-140](../adr/ADR-140-bff-pattern-token-storage.md) — BFF Pattern and Token Storage (Spring Cloud Gateway + Spring Session JDBC)
- [ADR-141](../adr/ADR-141-gateway-servlet-vs-reactive.md) — Gateway Servlet vs Reactive Stack (WebMVC servlet)
- [ADR-142](../adr/ADR-142-jwt-claim-extractor-strategy.md) — JWT Claim Extractor Strategy (interface + Spring Profile)
- [ADR-143](../adr/ADR-143-tenant-provisioning-strategy.md) — Tenant Provisioning Without Clerk Webhooks (JIT first, Event Listener SPI later)
- [ADR-144](../adr/ADR-144-keycloak-theming-strategy.md) — Keycloak Theming Strategy (Keycloakify for login pages, Freemarker for emails)

**Dependencies on prior phases**: Phase 35 (Keycloak auth provider, SPI, frontend integration), Phase 20 (auth abstraction layer, `NEXT_PUBLIC_AUTH_MODE` dispatch).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 268 | Spring Cloud Gateway BFF — Module Scaffolding & Security | Backend (Gateway) | — | L | 268A, 268B | **Done** |
| 269 | Gateway Session Storage & Route Configuration | Backend (Gateway) | 268 | M | 269A, 269B | **Done** |
| 270 | Gateway BFF Endpoints & Admin Proxy | Backend (Gateway) | 269 | M | 270A, 270B | **Done** |
| 271 | JIT Tenant & Member Provisioning | Backend | — | M | 271A, 271B | **Done** |
| 272 | Keycloak 26.5 Upgrade & Realm Configuration | Infra | — | S | 272A | **Done** |
| 273 | Docker Compose Gateway Integration | Infra | 268, 272 | S | 273A | **Done** |
| 274 | Frontend BFF Auth Provider & API Client | Frontend | 269, 270 | M | 274A, 274B | **Done** |
| 275 | Frontend BFF Middleware & Login/Logout Flows | Frontend | 274 | M | 275A, 275B | **Done** (PR #531) |
| 276 | Frontend Team Management Rewiring | Frontend | 270, 275 | M | 276A | **Done** (PR #533) |
| 277 | Keycloakify Theme Project — Login & Registration | Infra/Frontend | 272 | L | 277A, 277B | **Done** |
| 278 | Keycloak Email Templates & Theme Deployment | Infra | 277 | S | 278A | **Done** (PR #534) |
| 279 | Integration Testing & Verification | Both | 271, 273, 275, 276 | M | 279A, 279B | **Done** |

---

## Dependency Graph

```
INFRASTRUCTURE TRACK (independent)
──────────────────────────────────
[E272A Keycloak 26.1→26.5 upgrade:
 Docker image bump, realm-export
 update, org features, seed script
 adjustments]
        |
        +─────────────────────────────────────────────────────+
        |                                                     |
[E273A Docker Compose:                              [E277A Keycloakify project
 gateway service definition,                         scaffold: React + Tailwind,
 depends_on, network config,                         login page, registration page,
 dev-up.sh --keycloak update]                        password reset page + build]
 (also depends on E268B)                                      |
                                                    [E277B Theme polish: invitation
                                                     acceptance, error pages,
                                                     branding consistency + tests]
                                                              |
                                                    [E278A Email templates:
                                                     invitation, verification,
                                                     password reset Freemarker +
                                                     theme JAR deployment]

GATEWAY TRACK (sequential)
──────────────────────────
[E268A Maven module scaffold:
 pom.xml, Spring Boot 4 + Spring
 Cloud Gateway WebMVC + OAuth2
 Client + Spring Session JDBC deps]
        |
[E268B Security config:
 GatewaySecurityConfig, OAuth2Login,
 CSRF (CookieCsrfTokenRepository),
 logout handler, session management
 + unit tests]
        |
[E269A Spring Session JDBC config:
 datasource, session table auto-init,
 timeout, cleanup + GatewayApplication
 + health check tests]
        |
[E269B Route configuration:
 backend-api route (/api/**),
 backend-internal route (/internal/**),
 TokenRelay + SaveSession filters,
 route predicate tests]
        |
[E270A BffController /bff/me:
 user info endpoint, org claim
 extraction, response DTO +
 integration tests]
        |
[E270B Admin proxy endpoints:
 /bff/admin/invite,
 /bff/admin/members,
 /bff/admin/invitations →
 Keycloak Admin API relay +
 integration tests]

BACKEND TRACK (independent)
───────────────────────────
[E271A JIT tenant provisioning:
 TenantFilter modification,
 check org_schema_mapping,
 call TenantProvisioningService
 synchronously if missing +
 tests]
        |
[E271B JIT member provisioning:
 MemberFilter modification,
 check member by externalUserId,
 call MemberSyncService from JWT
 claims if missing + tests]

FRONTEND TRACK (after gateway)
──────────────────────────────
[E274A BFF auth provider:
 lib/auth/providers/keycloak-bff.ts,
 getAuthContext via /bff/me,
 server.ts dispatch update +
 tests]
        |
[E274B API client BFF mode:
 lib/api.ts session cookie +
 CSRF token handling, API_BASE
 pointing to gateway, conditional
 credentials: "include" + tests]
        |
[E275A Keycloak BFF middleware:
 SESSION cookie check, redirect
 to gateway login, public routes
 + tests]
        |
[E275B Login/logout flows:
 redirect to gateway OAuth2
 endpoints, custom UserMenu
 for BFF mode, sign-out via
 gateway /logout + tests]
        |
[E276A Team management rewiring:
 invite-member-form → gateway
 admin proxy, pending-invitations
 → gateway admin proxy,
 member-list remains /api/members
 + tests]

INTEGRATION TRACK (last)
────────────────────────
[E279A Manual flow verification:
 signup → org → first login →
 JIT provisioning → API access.
 Invite → accept → member sync.
 Session expiry → re-auth.
 Backend tests with gateway profile]
        |
[E279B Clerk regression & reversibility:
 env var switch back to clerk,
 verify Clerk mode untouched,
 verify mock E2E mode untouched,
 document runbook]
```

**Parallel opportunities**:
- E272A, E268A, and E271A can all start in parallel (3 independent starting points).
- E277A is independent of the gateway track — can run in parallel after E272A.
- E271A/B (JIT provisioning) is independent of the gateway track.
- Frontend track (E274-E276) depends on gateway track (E269-E270) being complete.
- E279A/B integration testing requires all tracks to converge.

---

## Implementation Order

### Stage 0: Infrastructure Foundation & Gateway Scaffold (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a (parallel) | 272 | 272A | Keycloak 26.1 -> 26.5 Docker image bump: update `compose/docker-compose.yml` image tag, update realm-export.json for 26.5 org features (org ID in token toggle, native invitation API), adjust `compose/scripts/keycloak-seed.sh` for 26.5 Admin REST API changes. ~4 modified files. Infra only. | **Done** (PR #526) |
| 0b (parallel) | 268 | 268A | Create `gateway/` Maven module: `pom.xml` with Spring Boot 4.0.2 parent, Spring Cloud 2025.1.x BOM, `spring-cloud-starter-gateway-server-webmvc`, `spring-boot-starter-oauth2-client`, `spring-session-jdbc`, PostgreSQL driver. `GatewayApplication.java` main class. ~3 new files. Gateway only. | **Done** (PR #522) |
| 0c (parallel) | 271 | 271A | JIT tenant provisioning in `TenantFilter`: when `org_schema_mapping` lookup returns null for a Keycloak org ID, synchronously call `TenantProvisioningService.provision()` with org details from JWT claims. Add `@Profile("keycloak")` conditional or feature flag. Idempotent (existing provisioning pipeline). ~2 modified files, ~1 test file (~6 tests). Backend only. | **Done** (PR #527) |

### Stage 1: Gateway Security & Session (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 268 | 268B | `GatewaySecurityConfig`: `SecurityFilterChain` with `oauth2Login()` (Keycloak provider), `logout()` with `OidcClientInitiatedLogoutSuccessHandler`, `CookieCsrfTokenRepository.withHttpOnlyFalse()`, `SpaCsrfTokenRequestHandler`, `SessionCreationPolicy.IF_REQUIRED`, permit `/actuator/health` + `/error`. ~3 new files (~6 tests). Gateway only. | **Done** (PR #523) |
| 1b | 269 | 269A | Spring Session JDBC configuration: `application.yml` with `spring.session.store-type=jdbc`, `spring.session.jdbc.initialize-schema=always`, 8h timeout, datasource config, `GatewayApplication` with `@EnableJdbcHttpSession`. Health check test. ~2 modified files (~3 tests). Gateway only. | **Done** (PR #524) |
| 1c (parallel) | 271 | 271B | JIT member provisioning in `MemberFilter`: when member lookup by `externalUserId` returns null, call `MemberSyncService.syncMember()` using JWT claims (`sub`, `email`, `name`, org role from `organization` claim). Add `@Profile("keycloak")` conditional. ~2 modified files, ~1 test file (~5 tests). Backend only. | **Done** (PR #527) |

### Stage 2: Gateway Routes & BFF Endpoints

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 269 | 269B | Route configuration in `application.yml`: `backend-api` route (`/api/**` -> `${BACKEND_URL}`), `backend-internal` route (`/internal/**` -> `${BACKEND_URL}`), `TokenRelay=` + `SaveSession` filters on both routes. CORS config for frontend origin. ~1 modified file (~4 tests). Gateway only. | **Done** (PR #524) |
| 2b | 270 | 270A | `BffController` with `GET /bff/me`: extract user info from `@AuthenticationPrincipal OidcUser`, parse `organization` claim for org ID/slug/role, return JSON response `{authenticated, userId, email, name, picture, orgId, orgSlug, orgRole}`. Handle unauthenticated case. ~2 new files (~6 tests). Gateway only. | **Done** (PR #525) |
| 2c | 273 | 273A | Docker Compose `gateway` service definition: Dockerfile for gateway module, service in `docker-compose.yml` (conditional with `--keycloak` flag), env vars (DB_HOST, KEYCLOAK_ISSUER, KEYCLOAK_CLIENT_SECRET, BACKEND_URL), port 8443, depends_on keycloak + postgres. Update `dev-up.sh --keycloak`. ~3 new/modified files. Infra only. | **Done** (PR #528) |

### Stage 3: Gateway Admin Proxy & Frontend BFF Provider (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 270 | 270B | `AdminProxyController` with endpoints: `POST /bff/admin/invite` (relay to Keycloak Admin API org invitations), `GET /bff/admin/invitations` (list pending invitations), `DELETE /bff/admin/invitations/{id}` (revoke invitation), `GET /bff/admin/members` (list org members), `DELETE /bff/admin/members/{id}` (remove member). Uses `WebClient` with admin credentials from config. ADMIN+ role check. ~3 new files (~8 tests). Gateway only. | **Done** (PR #525) |
| 3b (parallel) | 274 | 274A | BFF auth provider `lib/auth/providers/keycloak-bff.ts`: `getAuthContext()` calls `/bff/me` with forwarded cookies, `getAuthToken()` throws (not available in BFF mode), `getCurrentUserEmail()` via `/bff/me`. Update `lib/auth/server.ts` to add `keycloak` case dispatching to BFF provider. ~3 new/modified files (~5 tests). Frontend only. | **Done** (PR #529) |

### Stage 4: Frontend API Client & Middleware

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 274 | 274B | `lib/api.ts` BFF mode: when `AUTH_MODE === "keycloak"`, skip Bearer token, set `credentials: "include"`, add `X-XSRF-TOKEN` header from cookie for mutations, point `API_BASE` to gateway origin. `getCsrfToken()` utility function. ~2 modified files (~6 tests). Frontend only. | **Done** (PR #529) |
| 4b | 275 | 275A | `createKeycloakMiddleware()` in `lib/auth/middleware.ts`: check `SESSION` cookie presence on protected routes, redirect to `${GATEWAY_URL}/oauth2/authorization/keycloak` if absent, handle `/dashboard` redirect via `/bff/me` call. Update `createAuthMiddleware()` dispatch. ~1 modified file (~5 tests). Frontend only. | **Done** (PR #531) |

### Stage 5: Frontend Login/Logout & Team Rewiring

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 275 | 275B | Login/logout flows: login redirects to `${GATEWAY_URL}/oauth2/authorization/keycloak`, logout redirects to `${GATEWAY_URL}/logout`. Custom `UserMenuBff` component for keycloak mode (avatar + name from `/bff/me`, sign-out link). Update header component conditional rendering. ~4 new/modified files (~4 tests). Frontend only. | **Done** (PR #531) |
| 5b (parallel) | 276 | 276A | Team management rewiring for BFF mode: `invite-member-form.tsx` calls `/bff/admin/invite` (via gateway proxy) instead of Clerk SDK, `pending-invitations.tsx` calls `/bff/admin/invitations`, add actions for revoke/resend. `member-list.tsx` unchanged (already uses `/api/members`). Conditional logic based on `AUTH_MODE`. ~4 modified files (~6 tests). Frontend only. | **Done** (PR #533) |

### Stage 6: Keycloakify Theme (parallel with frontend track)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a (parallel) | 277 | 277A | Keycloakify project scaffold: `compose/keycloak/theme/` directory, `package.json` with keycloakify + React + Tailwind deps, Vite config, login page (username/password + Google button), registration page (name, email, password), password reset page. DocTeams branding: Sora font, slate palette, teal accents, logo. Build to theme JAR. ~12 new files. Frontend/Infra. | **Done** (PR #530) |
| 6b | 277 | 277B | Theme polish: invitation acceptance page, email verification page, error pages (generic, expired link, account disabled). Consistent branding across all pages. Theme build verification. ~6 new/modified files. Frontend/Infra. | **Done** (PR #532) |

### Stage 7: Email Templates & Theme Deployment

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 7a | 278 | 278A | Keycloak email templates (Freemarker): invitation email (org name, inviter name, branded header/footer), password reset email, email verification email. Mount in `compose/keycloak/themes/docteams/email/`. Realm config to use `docteams` theme for login + email. ~6 new files. Infra only. | **Done** (PR #534) |

### Stage 8: Integration Testing

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 8a | 279 | 279A | Manual flow verification scripts + backend integration tests: (1) signup -> org creation -> first login -> JIT provisioning -> API access, (2) invite member -> accept -> JIT member sync, (3) session expiry -> transparent token refresh, (4) CSRF validation for mutations, (5) backend tests with keycloak profile ensuring JwtClaimExtractor works with gateway-relayed JWTs. ~3 new files (~10 tests). Both. | **Done** (PR #535) |
| 8b | 279 | 279B | Clerk regression + reversibility test: switch `AUTH_MODE` back to `clerk`, verify all existing Clerk flows work unchanged. Verify mock E2E mode works unchanged. Document operational runbook (env vars, startup order, troubleshooting). ~2 new files (docs). Both. | **Done** (PR #536) |

### Timeline

```
Stage 0: [272A] // [268A] // [271A]                               (3 parallel tracks)
Stage 1: [268B] → [269A]     // [271B]                            (gateway sequential, JIT parallel)
Stage 2: [269B] → [270A]     // [273A]                            (gateway sequential, compose parallel)
Stage 3: [270B] // [274A]                                         (parallel: admin proxy + FE provider)
Stage 4: [274B] → [275A]                                          (sequential)
Stage 5: [275B] // [276A]                                         (parallel)
Stage 6: [277A] → [277B]                                          (parallel with Stages 3-5)
Stage 7: [278A]                                                    (after 277B)
Stage 8: [279A] → [279B]                                          (after all tracks converge)
```

**Critical path**: 268A -> 268B -> 269A -> 269B -> 270A -> 274A -> 274B -> 275A -> 275B -> 279A -> 279B (11 slices sequential at most).

**Fastest path with parallelism**: 3 parallel starting tracks, theming runs entirely parallel. Estimated: 21 slices total, 11 slices on critical path.

---

## Epic 268: Spring Cloud Gateway BFF — Module Scaffolding & Security

**Goal**: Create the `gateway/` Maven module from scratch with Spring Boot 4.0.2, Spring Cloud Gateway WebMVC (servlet stack), OAuth2 client, and a complete security filter chain with CSRF protection, OAuth2 login, and OIDC logout.

**References**: Architecture doc Sections 3.1, 3.2, 3.3. ADR-140 (BFF pattern), ADR-141 (servlet stack).

**Dependencies**: None — greenfield module.

**Scope**: Backend (Gateway)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **268A** | 268.1--268.4 | Maven module scaffold: `gateway/pom.xml` with Spring Boot 4.0.2 parent, Spring Cloud 2025.1.x BOM, `spring-cloud-starter-gateway-server-webmvc`, `spring-boot-starter-oauth2-client`, `spring-session-jdbc`, PostgreSQL driver, `spring-boot-starter-test`. `GatewayApplication.java` main class. `application.yml` skeleton with server port 8443. ~3 new files. Gateway only. | **Done** (PR #522) |
| **268B** | 268.5--268.11 | `GatewaySecurityConfig`: `SecurityFilterChain` with `oauth2Login()` targeting Keycloak provider, `defaultSuccessUrl("/", true)`, `OidcClientInitiatedLogoutSuccessHandler` for RP-Initiated Logout, `CookieCsrfTokenRepository.withHttpOnlyFalse()` for SPA CSRF, `SpaCsrfTokenRequestHandler` for deferred CSRF token loading, `SessionCreationPolicy.IF_REQUIRED`, public endpoints `/`, `/error`, `/actuator/health`. Unit tests (~6). ~3 new files. Gateway only. | **Done** (PR #523) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 268.1 | Create `gateway/pom.xml` Maven module | 268A | | New file: `gateway/pom.xml`. Parent: `spring-boot-starter-parent:4.0.2`. `<dependencyManagement>` with `spring-cloud-dependencies:2025.1.1` BOM. Dependencies: `spring-cloud-starter-gateway-server-webmvc`, `spring-boot-starter-oauth2-client`, `spring-session-jdbc`, `postgresql` (runtime), `spring-boot-starter-test` (test), `spring-security-test` (test). `<build>` with `spring-boot-maven-plugin`. Pattern: `backend/pom.xml` for structure, adjust dependencies. |
| 268.2 | Create `GatewayApplication.java` | 268A | 268.1 | New file: `gateway/src/main/java/io/b2mash/b2b/gateway/GatewayApplication.java`. Standard `@SpringBootApplication` main class. Package: `io.b2mash.b2b.gateway`. |
| 268.3 | Create `application.yml` skeleton | 268A | | New file: `gateway/src/main/resources/application.yml`. Set `server.port: 8443`, placeholder `spring.security.oauth2.client.registration.keycloak` block (client-id, client-secret from env, scope: openid,profile,email,organization, authorization-grant-type: authorization_code), placeholder `spring.security.oauth2.client.provider.keycloak` block (issuer-uri from env). See architecture doc Section 3.2 for exact YAML. |
| 268.4 | Create Dockerfile for gateway | 268A | 268.2 | New file: `gateway/Dockerfile`. Multi-stage build: Maven build stage + JRE runtime stage using `eclipse-temurin:25-jre`. Pattern: `backend/Dockerfile` (if it exists) or standard Spring Boot Docker pattern. Expose port 8443. Entry point: `org.springframework.boot.loader.launch.JarLauncher`. |
| 268.5 | Create `GatewaySecurityConfig` | 268B | 268A | New file: `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java`. `@Configuration @EnableWebSecurity`. `SecurityFilterChain` bean with: `.authorizeHttpRequests()` permitting `/`, `/error`, `/actuator/health`; `.requestMatchers("/bff/me").authenticated()`; `.requestMatchers("/api/**", "/internal/**").authenticated()`; `.anyRequest().authenticated()`. See architecture doc Section 3.3. |
| 268.6 | Configure OAuth2 login in security chain | 268B | 268.5 | In `GatewaySecurityConfig`. `.oauth2Login(oauth2 -> oauth2.defaultSuccessUrl("/", true))`. This configures the authorization code flow with Keycloak. The `redirect-uri` from `application.yml` handles the callback. |
| 268.7 | Configure OIDC logout handler | 268B | 268.5 | In `GatewaySecurityConfig`. Create `OidcClientInitiatedLogoutSuccessHandler` bean using `ClientRegistrationRepository`. `.logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler()).invalidateHttpSession(true).deleteCookies("SESSION"))`. This ensures Keycloak session is also terminated on logout (RP-Initiated Logout). |
| 268.8 | Configure CSRF for SPA | 268B | 268.5 | In `GatewaySecurityConfig`. `.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()).csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))`. `SpaCsrfTokenRequestHandler` is a custom implementation that defers CSRF token validation and uses the `X-XSRF-TOKEN` header. See Spring Security docs for SPA CSRF pattern. |
| 268.9 | Create `SpaCsrfTokenRequestHandler` | 268B | 268.8 | New file: `gateway/src/main/java/io/b2mash/b2b/gateway/config/SpaCsrfTokenRequestHandler.java`. Implements `CsrfTokenRequestHandler`. Delegates to `XorCsrfTokenRequestAttributeHandler` for deferred token validation. Standard Spring Security SPA pattern. See Spring Security reference docs Section "Single-Page Applications". |
| 268.10 | Configure session management | 268B | 268.5 | In `GatewaySecurityConfig`. `.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))`. Sessions created only on OAuth2 login, not on every request. |
| 268.11 | Write security config unit tests | 268B | 268.5 | New file: `gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java`. Tests (~6): (1) `healthEndpoint_permitAll`; (2) `bffMe_unauthenticated_redirectsToLogin`; (3) `apiEndpoint_unauthenticated_redirectsToLogin`; (4) `csrfCookie_setOnAuthenticated`; (5) `logout_invalidatesSession`; (6) `errorEndpoint_permitAll`. Use `@SpringBootTest` + `MockMvc` + `@WithMockUser` / mock OAuth2 login. |

### Key Files

**Slice 268A — Create:**
- `gateway/pom.xml`
- `gateway/src/main/java/io/b2mash/b2b/gateway/GatewayApplication.java`
- `gateway/src/main/resources/application.yml`
- `gateway/Dockerfile`

**Slice 268B — Create:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java`
- `gateway/src/main/java/io/b2mash/b2b/gateway/config/SpaCsrfTokenRequestHandler.java`
- `gateway/src/test/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfigTest.java`

**Slice 268B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` — existing backend security config pattern
- Architecture doc Section 3.3 — security config specification

### Architecture Decisions

- **Servlet stack (WebMVC)**: Per ADR-141, `spring-cloud-starter-gateway-server-webmvc` chosen over reactive `webflux` for consistency with the existing backend.
- **Separate module**: The gateway is a standalone Spring Boot application, not embedded in the backend. Per ADR-140, this keeps session management and business logic separated.
- **CSRF double-submit cookie**: `CookieCsrfTokenRepository.withHttpOnlyFalse()` sets an `XSRF-TOKEN` cookie readable by JavaScript. The SPA reads it and sends `X-XSRF-TOKEN` header on mutations.

---

## Epic 269: Gateway Session Storage & Route Configuration

**Goal**: Configure Spring Session JDBC for server-side token storage in PostgreSQL and define the gateway routes that proxy `/api/**` and `/internal/**` to the backend with JWT token relay.

**References**: Architecture doc Sections 3.2, 3.4, 3.6. ADR-140 (session storage).

**Dependencies**: Epic 268 (gateway module exists).

**Scope**: Backend (Gateway)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **269A** | 269.1--269.5 | Spring Session JDBC: `spring.session.store-type=jdbc`, `initialize-schema=always`, 8h timeout, datasource config for shared PostgreSQL. `@EnableJdbcHttpSession` annotation (if needed). Health check test verifying session table creation. ~2 modified files (~3 tests). Gateway only. | **Done** (PR #524) |
| **269B** | 269.6--269.10 | Route configuration: `spring.cloud.gateway.server.webmvc.routes` with `backend-api` route (`/api/**`), `backend-internal` route (`/internal/**`), `TokenRelay=` and `SaveSession` filters on both. CORS configuration for frontend origin. ~1 modified file (~4 tests). Gateway only. | **Done** (PR #524) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 269.1 | Configure Spring Session JDBC in `application.yml` | 269A | 268A | Modify: `gateway/src/main/resources/application.yml`. Add `spring.session.store-type: jdbc`, `spring.session.jdbc.initialize-schema: always`, `spring.session.jdbc.table-name: SPRING_SESSION`, `spring.session.timeout: 8h`. See architecture doc Section 3.2. |
| 269.2 | Configure gateway datasource | 269A | | Modify: `gateway/src/main/resources/application.yml`. Add `spring.datasource.url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:app}`, `spring.datasource.username: ${DB_USER:postgres}`, `spring.datasource.password: ${DB_PASSWORD:changeme}`. Gateway shares the same PostgreSQL instance, storing sessions in `public.SPRING_SESSION` tables. |
| 269.3 | Add `@EnableJdbcHttpSession` if needed | 269A | 269.1 | Check if Spring Boot 4 auto-configures JDBC sessions from `spring.session.store-type`. If not, add `@EnableJdbcHttpSession` to `GatewayApplication.java` or create a `SessionConfig.java`. Spring Boot 4 may auto-detect — verify by running. |
| 269.4 | Create `application-test.yml` for gateway tests | 269A | | New file: `gateway/src/test/resources/application-test.yml`. H2 in-memory database for test isolation (or Testcontainers PostgreSQL). Disable Keycloak issuer validation for unit tests. Mock OAuth2 provider config. |
| 269.5 | Write session storage integration tests | 269A | 269.1 | New file or extend `GatewaySecurityConfigTest.java`. Tests (~3): (1) `sessionCreated_onOAuth2Login`; (2) `sessionPersisted_inJdbcStore`; (3) `sessionExpired_afterTimeout`. May need `@SpringBootTest` with test profile. |
| 269.6 | Configure backend-api route | 269B | 269A | Modify: `gateway/src/main/resources/application.yml`. Add under `spring.cloud.gateway.server.webmvc.routes`: `id: backend-api`, `uri: ${BACKEND_URL:http://localhost:8080}`, `predicates: Path=/api/**`, `filters: TokenRelay=, SaveSession`. See architecture doc Section 3.2. |
| 269.7 | Configure backend-internal route | 269B | 269.6 | Same file. Add route: `id: backend-internal`, `uri: ${BACKEND_URL:http://localhost:8080}`, `predicates: Path=/internal/**`, `filters: TokenRelay=, SaveSession`. Internal endpoints also go through gateway in Keycloak mode. |
| 269.8 | Configure CORS for gateway | 269B | | Modify: `gateway/src/main/resources/application.yml` or `GatewaySecurityConfig.java`. Add CORS configuration allowing frontend origin (`http://localhost:3000` for dev, configurable via env). Allow credentials (for session cookie). Allow headers including `X-XSRF-TOKEN`. |
| 269.9 | Write route proxy integration tests | 269B | 269.6 | New file: `gateway/src/test/java/io/b2mash/b2b/gateway/RouteConfigTest.java`. Tests (~4): (1) `apiRoute_proxiesToBackend_withBearerToken`; (2) `internalRoute_proxiesToBackend`; (3) `unknownRoute_returns404`; (4) `corsHeaders_presentOnApiResponse`. Use WireMock to simulate backend. |
| 269.10 | Verify TokenRelay adds Authorization header | 269B | 269.6 | Part of route tests. Verify that the `TokenRelay` filter extracts the access token from the Spring Session and adds `Authorization: Bearer <jwt>` to the proxied request. Use WireMock to capture the outbound request headers. |

### Key Files

**Slice 269A — Modify:**
- `gateway/src/main/resources/application.yml`
- `gateway/src/main/java/io/b2mash/b2b/gateway/GatewayApplication.java` (if `@EnableJdbcHttpSession` needed)

**Slice 269A — Create:**
- `gateway/src/test/resources/application-test.yml`

**Slice 269B — Modify:**
- `gateway/src/main/resources/application.yml`

**Slice 269B — Create:**
- `gateway/src/test/java/io/b2mash/b2b/gateway/RouteConfigTest.java`

**Slice 269B — Read for context:**
- Architecture doc Section 3.2 — route configuration YAML

### Architecture Decisions

- **Shared PostgreSQL**: The gateway stores sessions in the same PostgreSQL instance as the app database (`app`). Sessions go in `public.SPRING_SESSION` / `public.SPRING_SESSION_ATTRIBUTES` tables. No conflict with tenant schemas.
- **TokenRelay filter**: This is the core BFF mechanism. The filter extracts the stored OAuth2 access token from the session and adds it as a `Bearer` header to proxied requests. The backend never knows about sessions.
- **8-hour session timeout**: Matches typical B2B workday. Token refresh is handled transparently by Spring Security's `OAuth2AuthorizedClientManager`.

---

## Epic 270: Gateway BFF Endpoints & Admin Proxy

**Goal**: Create the `/bff/me` user info endpoint and the `/bff/admin/*` proxy endpoints that relay team management operations to the Keycloak Admin REST API.

**References**: Architecture doc Sections 3.4, 6.5. ADR-140.

**Dependencies**: Epic 269 (routes + session configured).

**Scope**: Backend (Gateway)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **270A** | 270.1--270.6 | `BffController` with `GET /bff/me`: extract `@AuthenticationPrincipal OidcUser`, parse `organization` claim, return user info JSON. Handle unauthenticated case (`authenticated: false`). Response DTO as nested record. ~2 new files (~6 tests). Gateway only. | **Done** (PR #525) |
| **270B** | 270.7--270.14 | `AdminProxyController`: `POST /bff/admin/invite` (Keycloak org invitation), `GET /bff/admin/invitations` (list pending), `DELETE /bff/admin/invitations/{id}` (revoke), `GET /bff/admin/members` (list org members from Keycloak), `PATCH /bff/admin/members/{id}/role` (change role). `KeycloakAdminClient` service using `WebClient` with service account credentials. ADMIN+ authorization. ~3 new files (~8 tests). Gateway only. | **Done** (PR #525) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 270.1 | Create `BffController` with `/bff/me` endpoint | 270A | 269B | New file: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`. `@RestController @RequestMapping("/bff")`. `@GetMapping("/me")` accepting `@AuthenticationPrincipal OidcUser`. See architecture doc Section 3.4 for exact implementation. |
| 270.2 | Extract organization claim from OidcUser | 270A | 270.1 | In `BffController`. Parse `user.getClaim("organization")` map — single entry, key is slug, value is `{id, roles}`. Extract `orgId`, `orgSlug`, `orgRole` (first element of roles array). Handle null/empty gracefully. |
| 270.3 | Create BFF response DTO | 270A | | In `BffController` as nested record: `record BffUserInfo(boolean authenticated, String userId, String email, String name, String picture, String orgId, String orgSlug, String orgRole)`. |
| 270.4 | Handle unauthenticated `/bff/me` request | 270A | 270.1 | If `OidcUser` is null (security config permits this path for redirect detection), return `{"authenticated": false}`. Otherwise return full user info. Consider making the endpoint authenticated and letting the security chain handle the 401. |
| 270.5 | Create `BffUserInfoExtractor` utility | 270A | | New file: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffUserInfoExtractor.java`. Static utility to extract org info from `OidcUser` claims. Single-org assumption: get first entry from `organization` map. Centralizes claim parsing for reuse in admin proxy. |
| 270.6 | Write BffController integration tests | 270A | 270.1 | New file: `gateway/src/test/java/io/b2mash/b2b/gateway/controller/BffControllerTest.java`. Tests (~6): (1) `me_authenticated_returnsUserInfo`; (2) `me_authenticated_returnsOrgId`; (3) `me_authenticated_returnsOrgRole`; (4) `me_unauthenticated_returns401orFalse`; (5) `me_noOrgClaim_returnsPartialInfo`; (6) `me_multipleOrgs_usesFirst` (defensive). Use `@WithMockOidcUser` or mock `OidcUser`. |
| 270.7 | Create `KeycloakAdminClient` service | 270B | 269A | New file: `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java`. `@Service`. Uses `WebClient` configured with Keycloak Admin REST API base URL + service account credentials (client_credentials grant). Methods: `inviteMember(orgId, email, role)`, `listInvitations(orgId)`, `revokeInvitation(orgId, invitationId)`, `listOrgMembers(orgId)`, `updateMemberRole(orgId, userId, role)`. Pattern: Phase 35's `KeycloakAdminService` (on feature branch) but adapted for gateway context. |
| 270.8 | Configure Keycloak admin credentials | 270B | 270.7 | Modify: `gateway/src/main/resources/application.yml`. Add `keycloak.admin.url`, `keycloak.admin.realm`, `keycloak.admin.client-id`, `keycloak.admin.client-secret` properties. These are separate from the OAuth2 client registration (different client with admin permissions). |
| 270.9 | Create `AdminProxyController` | 270B | 270.7 | New file: `gateway/src/main/java/io/b2mash/b2b/gateway/controller/AdminProxyController.java`. `@RestController @RequestMapping("/bff/admin")`. All endpoints require ADMIN+ role. Extract current user's org ID from `OidcUser` org claim. |
| 270.10 | Implement `POST /bff/admin/invite` | 270B | 270.9 | In `AdminProxyController`. Request body: `{email, role}`. Calls `keycloakAdminClient.inviteMember(orgId, email, role)`. Returns invitation details or error. Maps Keycloak API errors to meaningful responses. |
| 270.11 | Implement `GET /bff/admin/invitations` | 270B | 270.9 | In `AdminProxyController`. Calls `keycloakAdminClient.listInvitations(orgId)`. Returns list of pending invitations with status, email, created date. |
| 270.12 | Implement invitation management endpoints | 270B | 270.9 | In `AdminProxyController`. `DELETE /bff/admin/invitations/{id}` calls `keycloakAdminClient.revokeInvitation(orgId, id)`. |
| 270.13 | Implement member management endpoints | 270B | 270.9 | In `AdminProxyController`. `GET /bff/admin/members` lists org members from Keycloak. `PATCH /bff/admin/members/{id}/role` changes role. |
| 270.14 | Write AdminProxyController integration tests | 270B | 270.9 | New file: `gateway/src/test/java/io/b2mash/b2b/gateway/controller/AdminProxyControllerTest.java`. Tests (~8): (1) `invite_asAdmin_succeeds`; (2) `invite_asMember_returns403`; (3) `listInvitations_asAdmin_returnsList`; (4) `revokeInvitation_asAdmin_succeeds`; (5) `listMembers_asAdmin_returnsList`; (6) `changeRole_asAdmin_succeeds`; (7) `invite_duplicateEmail_returnsConflict`; (8) `invite_invalidRole_returns400`. Use WireMock for Keycloak Admin API. |

### Key Files

**Slice 270A — Create:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffController.java`
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/BffUserInfoExtractor.java`
- `gateway/src/test/java/io/b2mash/b2b/gateway/controller/BffControllerTest.java`

**Slice 270B — Create:**
- `gateway/src/main/java/io/b2mash/b2b/gateway/service/KeycloakAdminClient.java`
- `gateway/src/main/java/io/b2mash/b2b/gateway/controller/AdminProxyController.java`
- `gateway/src/test/java/io/b2mash/b2b/gateway/controller/AdminProxyControllerTest.java`

**Slice 270B — Modify:**
- `gateway/src/main/resources/application.yml`

**Slice 270B — Read for context:**
- Architecture doc Sections 3.4, 6.5, 2.7 — BFF endpoints and invitation flow

### Architecture Decisions

- **Admin proxy in gateway, not backend**: The gateway is the OAuth2 client and holds the user's session. It can use its own service account credentials to call Keycloak Admin API. The backend never needs Keycloak admin access.
- **Service account for admin operations**: The gateway uses a dedicated `gateway-admin` client (client_credentials grant) with Keycloak Admin API permissions. This is separate from the `gateway-bff` client used for user OAuth2 flows.
- **Org ID from user session**: Admin proxy endpoints extract the org ID from the authenticated user's claims, not from request parameters. This prevents cross-org attacks.

---

## Epic 271: JIT Tenant & Member Provisioning

**Goal**: Modify `TenantFilter` and `MemberFilter` to automatically provision tenant schemas and sync members on first authenticated request when using Keycloak, eliminating the need for webhooks.

**References**: Architecture doc Section 5.2. ADR-143 (JIT provisioning).

**Dependencies**: None — modifies existing backend code independently of gateway track.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **271A** | 271.1--271.6 | JIT tenant provisioning: modify `TenantFilter` to detect unprovisioned org (null schema mapping), call `TenantProvisioningService.provision()` synchronously using JWT claims (`sub`, org slug, org ID from `organization` claim). Only active when `keycloak` profile. Idempotent. ~2 modified files, ~1 test file (~6 tests). Backend only. | **Done** (PR #527) |
| **271B** | 271.7--271.11 | JIT member provisioning: modify `MemberFilter` to detect unknown `externalUserId`, call `MemberSyncService.syncMember()` using JWT claims (`sub`, `email`, `name`, org role from `organization` claim). Only active when `keycloak` profile. ~2 modified files, ~1 test file (~5 tests). Backend only. | **Done** (PR #527) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 271.1 | Add JIT provisioning logic to `TenantFilter` | 271A | | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java`. In the `else` branch where `schema == null` (org not provisioned): if JIT provisioning is enabled (check via injected config property `app.jit-provisioning.enabled` or `@Value`), call `tenantProvisioningService.provision(orgId, orgSlug, orgName)` instead of returning 403. After provisioning, retry `resolveTenant()`. |
| 271.2 | Extract org slug and name from JWT for provisioning | 271A | 271.1 | In `TenantFilter`. Use `JwtClaimExtractor.extractOrgSlug(jwt)` for slug. For org name, use the slug as display name (Keycloak org alias = slug). Pass to provisioning service. |
| 271.3 | Inject `TenantProvisioningService` into `TenantFilter` | 271A | 271.1 | Modify `TenantFilter` constructor to accept optional `TenantProvisioningService`. Use `@Autowired(required = false)` or `Optional<TenantProvisioningService>` to avoid breaking non-keycloak profiles. Pattern: conditional injection. |
| 271.4 | Add JIT provisioning config property | 271A | | Modify: `backend/src/main/resources/application-keycloak.yml` (from Phase 35). Add `app.jit-provisioning.enabled: true`. Default false in other profiles. |
| 271.5 | Handle concurrent first requests for same org | 271A | 271.1 | The existing `TenantProvisioningService` is idempotent (unique constraint on `org_schema_mapping.clerk_org_id`). Concurrent requests will: first one provisions successfully, subsequent ones hit the unique constraint and should catch the exception gracefully, then retry the schema lookup. Add `synchronized` block or rely on DB-level idempotency. |
| 271.6 | Write JIT tenant provisioning tests | 271A | 271.1 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilterJitProvisioningTest.java`. Tests (~6): (1) `firstRequest_unprovisionedOrg_provisionsSchema`; (2) `secondRequest_sameOrg_usesCache`; (3) `jitDisabled_unprovisionedOrg_returns403`; (4) `provisioning_failure_returns500`; (5) `concurrent_firstRequests_idempotent`; (6) `provisionedOrg_noJitCall`. Use `@SpringBootTest` with Testcontainers, mock JWT with Keycloak-format org claim. |
| 271.7 | Add JIT member sync logic to `MemberFilter` | 271B | 271A | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`. After JWT extraction, look up member by `externalUserId` (JWT `sub`). If not found and JIT enabled: extract `email`, `name` from JWT standard claims, `orgRole` from `organization` claim via `JwtClaimExtractor`. Call `MemberSyncService.syncMember()`. Continue with the newly created member. |
| 271.8 | Extract email and name from JWT for member sync | 271B | 271.7 | In `MemberFilter`. Keycloak JWTs include standard `email` and `name` claims. Access via `jwt.getClaimAsString("email")` and `jwt.getClaimAsString("name")`. Fallback: use `sub` as name if `name` claim missing. |
| 271.9 | Inject `MemberSyncService` into `MemberFilter` | 271B | 271.7 | Modify `MemberFilter` constructor to accept optional `MemberSyncService`. Same pattern as TenantFilter: conditional injection. |
| 271.10 | Handle concurrent first requests for same member | 271B | 271.7 | `MemberSyncService.syncMember()` already handles upsert logic (creates or updates). Concurrent requests for the same user should be safe due to DB-level constraints. |
| 271.11 | Write JIT member provisioning tests | 271B | 271.7 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/member/MemberFilterJitSyncTest.java`. Tests (~5): (1) `firstRequest_unknownUser_syncsMember`; (2) `secondRequest_sameUser_usesExisting`; (3) `jitDisabled_unknownUser_returns401or403`; (4) `memberSync_extractsEmailAndName`; (5) `memberSync_extractsOrgRole`. Use `@SpringBootTest` with Testcontainers, mock JWT. |

### Key Files

**Slice 271A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java`
- `backend/src/main/resources/application-keycloak.yml`

**Slice 271A — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilterJitProvisioningTest.java`

**Slice 271A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` — provisioning pipeline
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/ClerkJwtUtils.java` — current JWT extraction (Phase 35 replaced with JwtClaimExtractor)

**Slice 271B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java`

**Slice 271B — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/member/MemberFilterJitSyncTest.java`

**Slice 271B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java` — member sync logic
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberFilter.java` — current member resolution

### Architecture Decisions

- **JIT provisioning is profile-gated**: Only enabled when `app.jit-provisioning.enabled=true` (set in `application-keycloak.yml`). Clerk mode continues to use webhook-driven provisioning unchanged.
- **Synchronous provisioning**: The first request for a new org blocks for 2-5 seconds while schema creation + migrations + seeding runs. Acceptable for B2B (org creation is rare). Subsequent requests are instant.
- **Idempotency**: The existing provisioning pipeline is fully idempotent at every stage. Safe for concurrent first requests.

---

## Epic 272: Keycloak 26.5 Upgrade & Realm Configuration

**Goal**: Upgrade the Keycloak Docker image from 26.1 (Phase 35) to 26.5, update the realm export for 26.5 organization features, and adjust the seed script.

**References**: Architecture doc Sections 2.1-2.7.

**Dependencies**: None — independent infrastructure change.

**Scope**: Infra

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **272A** | 272.1--272.5 | Keycloak 26.5 upgrade: Docker image tag bump in `compose/docker-compose.yml`, realm-export.json updates for 26.5 org features (org ID in token, native invitation REST API), seed script adjustments (`compose/scripts/keycloak-seed.sh`), `gateway-bff` client registration in realm. ~4 modified files. Infra only. | **Done** (PR #526) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 272.1 | Bump Keycloak Docker image to 26.5.0 | 272A | | Modify: `compose/docker-compose.yml`. Change `quay.io/keycloak/keycloak:26.1.0` to `quay.io/keycloak/keycloak:26.5.0`. Verify the image exists at `quay.io/keycloak/keycloak:26.5.0`. If not available, use latest 26.x. |
| 272.2 | Update realm-export.json for 26.5 features | 272A | 272.1 | Modify: `compose/keycloak/realm-export.json` (created by Phase 35). Enable: `organizationsEnabled: true`, `membershipType: "MANAGED"` (single org enforcement). Add `organization` default client scope. Enable organization ID toggle in token mapper. Verify `gateway-bff` client config includes `organization` scope. |
| 272.3 | Add `gateway-bff` client to realm export | 272A | | Modify: `compose/keycloak/realm-export.json`. Add confidential client: `clientId: gateway-bff`, `publicClient: false`, `standardFlowEnabled: true`, `directAccessGrantsEnabled: false`, `secret: ${KEYCLOAK_CLIENT_SECRET}`, `redirectUris: ["http://localhost:8443/login/oauth2/code/keycloak"]`, `webOrigins: ["http://localhost:3000"]`, `defaultClientScopes: ["openid", "profile", "email", "organization"]`. |
| 272.4 | Update seed script for 26.5 API | 272A | 272.1 | Modify: `compose/scripts/keycloak-seed.sh`. Verify Keycloak Admin REST API endpoints haven't changed between 26.1 and 26.5 for organization CRUD, user creation, invitation creation. Update if needed. Test: run seed, verify alice/bob/carol users created with correct org roles. |
| 272.5 | Add custom protocol mapper for org roles | 272A | | Modify: `compose/keycloak/realm-export.json`. Add Script Mapper (if `--features=scripts` enabled) or reference the Phase 35 SPI JAR. Mapper adds `roles` array inside each `organization` entry. See architecture doc Section 2.4 for Script Mapper code. Add `--features=scripts` to Keycloak command if using Script Mapper. |

### Key Files

**Slice 272A — Modify:**
- `compose/docker-compose.yml`
- `compose/keycloak/realm-export.json`
- `compose/scripts/keycloak-seed.sh`

**Slice 272A — Read for context:**
- Architecture doc Sections 2.1-2.7 — Keycloak realm configuration
- `keycloak-spi/` (Phase 35 branch) — existing SPI JAR if using Java mapper instead of Script Mapper

### Architecture Decisions

- **Script Mapper vs Java SPI**: Per ADR-139, start with Script Mapper (`--features=scripts`) for dev simplicity. The Phase 35 Java SPI (`OrgRoleProtocolMapper`) exists as a fallback if Script Mapper doesn't work in 26.5.
- **Single org per user**: Enforced at Keycloak level via organization membership policy. Simplifies JWT claim parsing.

---

## Epic 273: Docker Compose Gateway Integration

**Goal**: Add the Spring Cloud Gateway BFF as a Docker Compose service alongside Keycloak and the backend, update the `dev-up.sh` script for the full Keycloak + Gateway stack.

**References**: Architecture doc Section 9.

**Dependencies**: Epic 268 (gateway Dockerfile), Epic 272 (Keycloak 26.5).

**Scope**: Infra

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **273A** | 273.1--273.5 | Gateway Docker Compose service: service definition with build context, environment variables, port mapping (8443), depends_on (keycloak, postgres). Update `dev-up.sh --keycloak` to include gateway. Health check configuration. ~3 modified files. Infra only. | **Done** (PR #528) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 273.1 | Add `gateway` service to `docker-compose.yml` | 273A | 268A | Modify: `compose/docker-compose.yml`. Add `gateway` service: `build.context: ../gateway`, `build.dockerfile: Dockerfile`, `environment` (DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, KEYCLOAK_ISSUER, KEYCLOAK_CLIENT_SECRET, BACKEND_URL), `ports: "8443:8443"`, `depends_on: keycloak (service_healthy), postgres (service_healthy)`. Conditional with `--keycloak` profile. See architecture doc Section 9.1. |
| 273.2 | Add gateway health check | 273A | 273.1 | In `docker-compose.yml` gateway service: `healthcheck.test: ["CMD", "curl", "-f", "http://localhost:8443/actuator/health"]`, `interval: 10s`, `timeout: 5s`, `retries: 5`. Requires actuator dependency in gateway. |
| 273.3 | Update `dev-up.sh --keycloak` for gateway | 273A | 273.1 | Modify: `compose/scripts/dev-up.sh`. When `--keycloak` flag is present, also start the `gateway` service. Print gateway URL (http://localhost:8443) in startup output. |
| 273.4 | Add `KEYCLOAK_CLIENT_SECRET` to `.env.example` | 273A | | Create or modify: `compose/.env.example`. Add `KEYCLOAK_CLIENT_SECRET=<generated-secret>` placeholder. Document that this must match the `gateway-bff` client secret in Keycloak. |
| 273.5 | Test full stack startup | 273A | 273.3 | Verification task: run `dev-up.sh --keycloak`, verify postgres, keycloak, and gateway all start and become healthy. Verify gateway can reach Keycloak OIDC discovery endpoint. Verify gateway can reach backend. |

### Key Files

**Slice 273A — Modify:**
- `compose/docker-compose.yml`
- `compose/scripts/dev-up.sh`

**Slice 273A — Create:**
- `compose/.env.example` (or modify if exists)

---

## Epic 274: Frontend BFF Auth Provider & API Client

**Goal**: Create the Keycloak BFF auth provider that gets user context from `/bff/me` instead of Clerk/next-auth, and modify the API client to use session cookies + CSRF instead of Bearer tokens.

**References**: Architecture doc Sections 6.1-6.3.

**Dependencies**: Epic 269 (gateway routes), Epic 270 (BFF endpoints).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **274A** | 274.1--274.6 | BFF auth provider: `lib/auth/providers/keycloak-bff.ts` with `getAuthContext()` calling `/bff/me`, `getAuthToken()` throwing (BFF mode has no client-visible tokens), `getCurrentUserEmail()` via `/bff/me`. Update `lib/auth/server.ts` dispatch to add `keycloak` case. ~3 new/modified files (~5 tests). Frontend only. | **Done** (PR #529) |
| **274B** | 274.7--274.12 | API client BFF mode: modify `lib/api.ts` to detect `AUTH_MODE === "keycloak"`, skip Bearer token, set `credentials: "include"` for session cookie, add `X-XSRF-TOKEN` header for mutations. `getCsrfToken()` utility. Point `API_BASE` to gateway. ~2 modified files (~6 tests). Frontend only. | **Done** (PR #529) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 274.1 | Create `lib/auth/providers/keycloak-bff.ts` | 274A | | New file: `frontend/lib/auth/providers/keycloak-bff.ts`. `import "server-only"`. Define `GATEWAY_URL = process.env.GATEWAY_URL \|\| "http://localhost:8443"`. Define `BffUserInfo` interface. See architecture doc Section 6.1 for exact interface shape. |
| 274.2 | Implement `getAuthContext()` in BFF provider | 274A | 274.1 | In `keycloak-bff.ts`. Fetch `${GATEWAY_URL}/bff/me` with `headers: { cookie: getRequestCookies() }` (forward session cookie from incoming Next.js request), `cache: "no-store"`. Parse response. Map to `AuthContext` type. Use `cookies()` from `next/headers` to get the `SESSION` cookie. |
| 274.3 | Implement `getAuthToken()` in BFF provider | 274A | 274.1 | In `keycloak-bff.ts`. Throw `Error("getAuthToken() is not available in BFF mode. API calls should route through the gateway.")`. This function should never be called in keycloak mode — API calls go through the gateway which adds Bearer tokens. |
| 274.4 | Implement remaining provider functions | 274A | 274.1 | In `keycloak-bff.ts`. `getCurrentUserEmail()`: call `/bff/me`, return email. `hasPlan()`: delegate to backend `/api/billing/subscription` (same as other providers). `requireRole()`: check role from `/bff/me` response. |
| 274.5 | Update `lib/auth/server.ts` dispatch | 274A | 274.1 | Modify: `frontend/lib/auth/server.ts`. Import `keycloakBffProvider` from `./providers/keycloak-bff`. Add `if (AUTH_MODE === "keycloak") return keycloakBffProvider.getAuthContext()` (and similar for all exported functions). Pattern: existing mock provider dispatch. |
| 274.6 | Write BFF provider tests | 274A | 274.2 | New file: `frontend/__tests__/auth/keycloak-bff-provider.test.ts`. Tests (~5): (1) `getAuthContext_returnsBffUserInfo`; (2) `getAuthContext_forwardsCookies`; (3) `getAuthToken_throws`; (4) `getCurrentUserEmail_returnsEmail`; (5) `requireRole_checksOrgRole`. Mock `fetch` and `cookies()`. |
| 274.7 | Add CSRF token utility | 274B | | Modify: `frontend/lib/api.ts`. Add `getCsrfToken()` function: parse `XSRF-TOKEN` cookie from `document.cookie`, URL-decode, return. This is a client-side function — add to a client-safe utility or guard with `typeof window !== "undefined"`. See architecture doc Section 3.5. |
| 274.8 | Modify `apiRequest()` for BFF mode | 274B | 274.7 | Modify: `frontend/lib/api.ts`. In the main `apiRequest()` function: if `AUTH_MODE === "keycloak"`, do NOT call `getAuthToken()`, instead set `credentials: "include"` on the fetch. For non-GET methods, add `X-XSRF-TOKEN` header from `getCsrfToken()`. See architecture doc Section 6.2. |
| 274.9 | Update `API_BASE` for BFF mode | 274B | | Modify: `frontend/lib/api.ts`. When `AUTH_MODE === "keycloak"`, `API_BASE` points to gateway origin (`GATEWAY_URL` or `http://localhost:8443`) instead of direct backend (`BACKEND_URL`). Use `process.env.GATEWAY_URL` with fallback. Note: server-side API calls also need to route through gateway in BFF mode. |
| 274.10 | Handle server-side API calls in BFF mode | 274B | 274.9 | In `lib/api.ts`. For server-side calls (in Server Components/actions), the API client needs to forward the `SESSION` cookie to the gateway. Detect server context via `typeof window === "undefined"`. On server side: read `SESSION` cookie from `next/headers` and forward it. Pattern: similar to how `getAuthContext()` forwards cookies. |
| 274.11 | Handle CSRF for server-side mutations | 274B | 274.10 | Server-side mutations (Server Actions) need CSRF handling. Options: (1) server-side calls bypass CSRF (gateway exempts requests with valid session), (2) server-side calls include CSRF token from cookie. Determine approach based on gateway CSRF config. |
| 274.12 | Write API client BFF mode tests | 274B | 274.8 | New file: `frontend/__tests__/api-bff-mode.test.ts`. Tests (~6): (1) `bffMode_noAuthorizationHeader`; (2) `bffMode_credentialsInclude`; (3) `bffMode_csrfTokenOnPost`; (4) `bffMode_csrfTokenOnPut`; (5) `bffMode_noCsrfOnGet`; (6) `bffMode_apiBasePointsToGateway`. Mock `fetch`, set `AUTH_MODE`. |

### Key Files

**Slice 274A — Create:**
- `frontend/lib/auth/providers/keycloak-bff.ts`
- `frontend/__tests__/auth/keycloak-bff-provider.test.ts`

**Slice 274A — Modify:**
- `frontend/lib/auth/server.ts`

**Slice 274A — Read for context:**
- `frontend/lib/auth/providers/clerk.ts` — existing provider pattern
- `frontend/lib/auth/providers/mock/server.ts` — mock provider pattern
- `frontend/lib/auth/types.ts` — AuthContext type

**Slice 274B — Modify:**
- `frontend/lib/api.ts`

**Slice 274B — Create:**
- `frontend/__tests__/api-bff-mode.test.ts`

**Slice 274B — Read for context:**
- Architecture doc Sections 6.1-6.2 — BFF provider and API client specs

### Architecture Decisions

- **BFF provider is separate from Phase 35 keycloak provider**: Phase 35 created `lib/auth/providers/keycloak.ts` using next-auth v5. Phase 36's BFF provider (`keycloak-bff.ts`) replaces it with a simpler `/bff/me`-based approach. The BFF provider does not use next-auth at all — the gateway handles OAuth2.
- **No `getAuthToken()` in BFF mode**: The fundamental BFF principle is that the browser never sees tokens. API calls go through the gateway which adds Bearer tokens transparently.
- **Server-side cookie forwarding**: Next.js Server Components need to forward the `SESSION` cookie when calling the gateway. This is the same pattern used by the mock provider for forwarding the mock auth cookie.

---

## Epic 275: Frontend BFF Middleware & Login/Logout Flows

**Goal**: Create Keycloak BFF middleware for route protection using session cookie detection, and implement login/logout flows that redirect to the gateway's OAuth2 endpoints.

**References**: Architecture doc Sections 6.4, 7.

**Dependencies**: Epic 274 (BFF provider).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **275A** | 275.1--275.5 | `createKeycloakMiddleware()`: check `SESSION` cookie on protected routes, redirect to `${GATEWAY_URL}/oauth2/authorization/keycloak` if absent, handle `/dashboard` redirect via org slug from session. Update `createAuthMiddleware()` dispatch. ~1 modified file (~5 tests). Frontend only. | **Done** (PR #531) |
| **275B** | 275.6--275.10 | Login/logout flows: login button redirects to gateway OAuth2 endpoint, logout redirects to `${GATEWAY_URL}/logout`. Custom `UserMenuBff` component (avatar + name from `/bff/me`, sign-out link). Update header conditional rendering for `AUTH_MODE`. ~4 new/modified files (~4 tests). Frontend only. | **Done** (PR #531) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 275.1 | Create `createKeycloakMiddleware()` | 275A | 274A | Modify: `frontend/lib/auth/middleware.ts`. Add `createKeycloakMiddleware()` function. Check `request.cookies.get("SESSION")`. If absent on protected route, redirect to `${GATEWAY_URL}/oauth2/authorization/keycloak`. Public routes pass through. See architecture doc Section 7. |
| 275.2 | Handle `/dashboard` redirect in Keycloak middleware | 275A | 275.1 | In `createKeycloakMiddleware()`. When path is `/dashboard`: need org slug to redirect to `/org/{slug}/dashboard`. Option 1: call `/bff/me` from middleware to get org slug (adds latency). Option 2: store org slug in a separate cookie on first `/bff/me` call. Option 3: redirect to a known path and let the page component handle it. Choose simplest approach. |
| 275.3 | Update `createAuthMiddleware()` dispatch | 275A | 275.1 | Modify: `frontend/lib/auth/middleware.ts`. Add `if (AUTH_MODE === "keycloak") return createKeycloakMiddleware()` before the Clerk case. |
| 275.4 | Update public routes for Keycloak mode | 275A | 275.1 | In middleware. Keycloak mode doesn't need `/sign-in` or `/sign-up` as public routes (auth pages are hosted by Keycloak). But `/` and `/portal/*` and `/api/webhooks/*` remain public. Adjust route matcher or use separate public route list per mode. |
| 275.5 | Write Keycloak middleware tests | 275A | 275.1 | Extend or create: `frontend/__tests__/auth/keycloak-middleware.test.ts`. Tests (~5): (1) `protectedRoute_noSession_redirectsToLogin`; (2) `protectedRoute_withSession_passesThrough`; (3) `publicRoute_noSession_passesThrough`; (4) `dashboard_withSession_redirectsToOrgDashboard`; (5) `portalRoute_noSession_passesThrough`. Mock `NextRequest`. |
| 275.6 | Create login redirect component/utility | 275B | 275A | In Keycloak mode, the sign-in page should redirect to the gateway OAuth2 authorization endpoint. Create utility: `getKeycloakLoginUrl(returnPath?: string)` returns `${GATEWAY_URL}/oauth2/authorization/keycloak`. The return URL handling depends on gateway config (Spring Security default redirect). |
| 275.7 | Create logout redirect utility | 275B | | Utility: `getKeycloakLogoutUrl()` returns `${GATEWAY_URL}/logout`. The gateway's `OidcClientInitiatedLogoutSuccessHandler` handles Keycloak session termination and redirect. |
| 275.8 | Create `UserMenuBff` component | 275B | 275.6 | New file: `frontend/components/auth/user-menu-bff.tsx`. `"use client"`. Displays user avatar + name (from a `useCurrentUser()` hook or server-passed props). Sign-out button navigates to `getKeycloakLogoutUrl()`. Style to match existing `UserButton` from Clerk. Uses slate palette, Sora font for name. |
| 275.9 | Update header for conditional auth mode | 275B | 275.8 | Modify: header component (e.g., `frontend/app/(app)/org/[slug]/layout.tsx` or sidebar component). When `AUTH_MODE === "keycloak"`, render `<UserMenuBff>` instead of Clerk's `<UserButton>`. When `AUTH_MODE === "clerk"`, render Clerk components as before. |
| 275.10 | Write login/logout flow tests | 275B | 275.8 | New or extend test file. Tests (~4): (1) `loginUrl_pointsToGateway`; (2) `logoutUrl_pointsToGateway`; (3) `userMenuBff_rendersNameAndAvatar`; (4) `userMenuBff_signOutNavigatesToLogout`. |

### Key Files

**Slice 275A — Modify:**
- `frontend/lib/auth/middleware.ts`

**Slice 275A — Create:**
- `frontend/__tests__/auth/keycloak-middleware.test.ts`

**Slice 275B — Create:**
- `frontend/components/auth/user-menu-bff.tsx`

**Slice 275B — Modify:**
- `frontend/app/(app)/org/[slug]/layout.tsx` (or relevant header component)

**Slice 275B — Read for context:**
- `frontend/lib/auth/middleware.ts` — existing mock middleware pattern
- `frontend/components/desktop-sidebar.tsx` — current header/sidebar structure

### Architecture Decisions

- **Keycloak owns auth pages**: In BFF mode, the SPA never renders login/registration forms. All auth UI is hosted by Keycloak (themed via Keycloakify). The SPA only redirects to Keycloak URLs.
- **No `OrganizationSwitcher`**: Single org per user eliminates the need for org switching. The `OrganizationSwitcher` component from Clerk is simply not rendered in keycloak mode.

---

## Epic 276: Frontend Team Management Rewiring

**Goal**: Rewire the team management components (`invite-member-form.tsx`, `pending-invitations.tsx`) to call the gateway's admin proxy endpoints instead of the Clerk SDK when in Keycloak BFF mode.

**References**: Architecture doc Section 6.5.

**Dependencies**: Epic 270 (admin proxy endpoints), Epic 275 (BFF middleware).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **276A** | 276.1--276.7 | Team management rewiring: `invite-member-form.tsx` calls `/bff/admin/invite` (via server action), `pending-invitations.tsx` fetches from `/bff/admin/invitations`, revoke calls `DELETE /bff/admin/invitations/{id}`. `member-list.tsx` unchanged (already uses `/api/members`). Conditional logic per `AUTH_MODE`. ~4 modified files (~6 tests). Frontend only. | **Done** (PR #533) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 276.1 | Create team server actions for BFF mode | 276A | | Modify: `frontend/app/(app)/org/[slug]/team/actions.ts`. Add BFF-mode actions: `inviteMemberBff(email, role)` calls gateway `/bff/admin/invite`, `listInvitationsBff()` calls `/bff/admin/invitations`, `revokeInvitationBff(id)` calls `DELETE /bff/admin/invitations/{id}`. These actions use the BFF API client (session cookie + CSRF). Existing Clerk actions remain for `AUTH_MODE === "clerk"`. |
| 276.2 | Create action dispatch by auth mode | 276A | 276.1 | In `actions.ts`. Export wrapper functions that check `AUTH_MODE` and dispatch to Clerk or BFF actions. E.g., `inviteMember()` calls `inviteMemberClerk()` or `inviteMemberBff()` based on env. |
| 276.3 | Update `invite-member-form.tsx` | 276A | 276.1 | Modify: `frontend/components/team/invite-member-form.tsx`. Replace direct Clerk SDK calls (`organization.inviteMember()`) with the dispatched server action `inviteMember()`. The form UI stays the same — only the submission handler changes. |
| 276.4 | Update `pending-invitations.tsx` | 276A | 276.1 | Modify: `frontend/components/team/pending-invitations.tsx`. Replace Clerk SDK calls (`organization.getInvitations()`) with the dispatched action `listInvitations()`. Replace `invitation.revoke()` with `revokeInvitation(id)`. Map Keycloak invitation response to the existing UI data shape. |
| 276.5 | Verify `member-list.tsx` compatibility | 276A | | Read: `frontend/components/team/member-list.tsx`. Verify it uses `/api/members` backend endpoint (not Clerk SDK). If it uses Clerk's `organization.getMemberships()`, update to use backend endpoint. The backend `/api/members` already works with both Clerk and Keycloak (Phase 35 ensured this). |
| 276.6 | Map Keycloak invitation response to UI types | 276A | 276.1 | In `actions.ts`. Keycloak Admin API returns invitations in a different format than Clerk. Create a mapper: `mapKeycloakInvitation(kcInvite) -> { id, email, role, status, createdAt }` matching the shape expected by `pending-invitations.tsx`. |
| 276.7 | Write team management rewiring tests | 276A | 276.3 | Modify or extend: `frontend/components/team/invite-member-form.test.tsx`. Tests (~6): (1) `bffMode_invite_callsGatewayProxy`; (2) `bffMode_invite_sendsCorrectPayload`; (3) `bffMode_listInvitations_returnsMapped`; (4) `bffMode_revokeInvitation_callsDelete`; (5) `clerkMode_invite_callsClerkSdk`; (6) `authModeDispatch_selectsCorrectAction`. |

### Key Files

**Slice 276A — Modify:**
- `frontend/app/(app)/org/[slug]/team/actions.ts`
- `frontend/components/team/invite-member-form.tsx`
- `frontend/components/team/pending-invitations.tsx`

**Slice 276A — Read for context:**
- `frontend/components/team/member-list.tsx` — verify uses backend API
- `frontend/components/team/team-tabs.tsx` — tab container structure

### Architecture Decisions

- **Dual-mode actions**: Team actions support both Clerk and Keycloak mode via a dispatch layer. This maintains the reversibility goal — switching `AUTH_MODE` back to `clerk` restores full Clerk functionality.
- **Gateway admin proxy vs direct Keycloak calls**: Frontend never calls Keycloak Admin API directly. The gateway's `/bff/admin/*` endpoints handle authentication and authorization with Keycloak using service account credentials.

---

## Epic 277: Keycloakify Theme Project — Login & Registration

**Goal**: Create a Keycloakify-based React theme project that generates a Keycloak theme JAR with DocTeams branding for login, registration, and password reset pages.

**References**: Architecture doc Section 8. ADR-144 (Keycloakify strategy).

**Dependencies**: Epic 272 (Keycloak 26.5).

**Scope**: Infra/Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **277A** | 277.1--277.8 | Keycloakify project scaffold: `compose/keycloak/theme/` directory, `package.json` with keycloakify v11+ dependencies, Vite config, Tailwind CSS setup, login page (username/password + Google social), registration page (name, email, password), password reset page. DocTeams branding (Sora font, slate palette, teal accents, logo). Build script producing theme JAR. ~12 new files. | **Done** (PR #530) |
| **277B** | 277.9--277.12 | Theme polish: invitation acceptance page, email verification page, generic error page (expired link, account disabled), branding consistency review. ~6 new/modified files. | **Done** (PR #532) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 277.1 | Initialize Keycloakify project | 277A | | Run `npx keycloakify init` in `compose/keycloak/theme/` or create manually. `package.json` with keycloakify v11+, React 19, Vite. Add Tailwind CSS 4 dependencies. Pattern: Keycloakify starter template. |
| 277.2 | Configure Tailwind CSS | 277A | 277.1 | Create `compose/keycloak/theme/tailwind.config.ts` (or CSS-first v4 config). Import DocTeams slate OKLCH palette and teal accent colors from `frontend/app/globals.css` (copy relevant CSS custom properties). |
| 277.3 | Add DocTeams fonts | 277A | 277.1 | Configure Sora (display), IBM Plex Sans (body), JetBrains Mono (code) fonts. Either load from Google Fonts or bundle font files. Match `frontend/app/layout.tsx` font configuration. |
| 277.4 | Create login page template | 277A | 277.2 | Create login page: DocTeams logo at top, email/password form with slate-styled inputs, "Sign in with Google" social button (teal accent), "Forgot password?" link, "Don't have an account? Register" link. Match Signal Deck design language. |
| 277.5 | Create registration page template | 277A | 277.2 | Create registration page: DocTeams logo, name/email/password/confirm-password form, "Already have an account? Sign in" link. Minimal fields — Keycloak handles email verification. |
| 277.6 | Create password reset page template | 277A | 277.2 | Create password reset page: DocTeams logo, email input, "Reset Password" button, "Back to sign in" link. |
| 277.7 | Configure build script for theme JAR | 277A | 277.1 | In `package.json`: add `build` script that runs Keycloakify build, producing a JAR in `compose/keycloak/theme/dist/`. The JAR is mounted into Keycloak's `themes/` directory via Docker volume. |
| 277.8 | Test theme locally | 277A | 277.7 | Verification: build theme, mount in Keycloak container, set realm login theme to `docteams`, verify login/registration pages render with DocTeams branding. |
| 277.9 | Create invitation acceptance page | 277B | 277A | Keycloak's invitation flow redirects to a registration/login page with invitation context. Customize the page to show: "You've been invited to join {orgName}", accept/decline buttons, DocTeams branding. |
| 277.10 | Create email verification page | 277B | 277A | Customize the email verification page: "Check your email" message, resend link, DocTeams branding. |
| 277.11 | Create error pages | 277B | 277A | Customize error pages: generic error, expired link, account disabled. DocTeams branding, helpful error messages, "Go back" links. |
| 277.12 | Branding consistency review | 277B | 277.11 | Review all themed pages for consistent: logo placement, color usage (slate + teal), font usage (Sora headings, IBM Plex Sans body), spacing, responsive behavior on mobile. Fix inconsistencies. |

### Key Files

**Slice 277A — Create:**
- `compose/keycloak/theme/package.json`
- `compose/keycloak/theme/vite.config.ts`
- `compose/keycloak/theme/src/login/KcPage.tsx` (or equivalent entry point)
- `compose/keycloak/theme/src/login/pages/Login.tsx`
- `compose/keycloak/theme/src/login/pages/Register.tsx`
- `compose/keycloak/theme/src/login/pages/LoginResetPassword.tsx`
- `compose/keycloak/theme/src/login/pages/shared/Layout.tsx`
- `compose/keycloak/theme/tailwind.config.ts` (or globals.css for v4)

**Slice 277B — Create/Modify:**
- `compose/keycloak/theme/src/login/pages/LoginVerifyEmail.tsx`
- `compose/keycloak/theme/src/login/pages/Error.tsx`
- `compose/keycloak/theme/src/login/pages/Info.tsx` (invitation acceptance)

**Read for context:**
- `frontend/app/globals.css` — DocTeams color tokens
- `frontend/app/layout.tsx` — font configuration
- ADR-144 — theming strategy

### Architecture Decisions

- **Keycloakify over raw Freemarker**: Per ADR-144, using React + Tailwind for login pages keeps the tech stack consistent. Keycloakify compiles to standard Keycloak theme JARs.
- **Font bundling**: For production, bundle fonts in the theme JAR to avoid external network dependencies during login.

---

## Epic 278: Keycloak Email Templates & Theme Deployment

**Goal**: Customize Keycloak's Freemarker email templates for invitation, verification, and password reset emails with DocTeams branding, and configure theme deployment.

**References**: Architecture doc Section 8.2. ADR-144.

**Dependencies**: Epic 277 (theme JAR built).

**Scope**: Infra

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **278A** | 278.1--278.5 | Email templates (Freemarker): invitation email (org name, inviter, branded header/footer), password reset, email verification. Mount in Keycloak themes directory. Realm config for `docteams` email theme. ~6 new files. Infra only. | **Done** (PR #534) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 278.1 | Create invitation email template | 278A | | New file: `compose/keycloak/themes/docteams/email/html/org-invite.ftl` (or equivalent Keycloak email template path). HTML email: DocTeams logo header, "You've been invited to join {orgName} by {inviterName}", accept button (teal background), footer with DocTeams branding. Use Freemarker variables for org name, inviter, link. |
| 278.2 | Create password reset email template | 278A | | New file: `compose/keycloak/themes/docteams/email/html/executeActions.ftl`. Branded password reset email: DocTeams header, "Reset your password" message, reset link button, expiry notice, footer. |
| 278.3 | Create email verification template | 278A | | New file: `compose/keycloak/themes/docteams/email/html/email-verification.ftl`. Branded verification email: DocTeams header, "Verify your email address" message, verification link button, footer. |
| 278.4 | Create shared email styles | 278A | | New file: `compose/keycloak/themes/docteams/email/html/template.ftl` (base template). Shared HTML header/footer, inline CSS matching DocTeams branding (slate colors, Sora/IBM Plex Sans fonts via web fonts, teal accent buttons). All email templates extend this base. |
| 278.5 | Configure realm to use docteams email theme | 278A | | Modify: `compose/keycloak/realm-export.json`. Set `emailTheme: "docteams"`. Also set `loginTheme: "docteams"` (from Epic 277). Mount theme files via Docker volume: `./keycloak/themes/docteams:/opt/keycloak/themes/docteams`. |

### Key Files

**Slice 278A — Create:**
- `compose/keycloak/themes/docteams/email/html/org-invite.ftl`
- `compose/keycloak/themes/docteams/email/html/executeActions.ftl`
- `compose/keycloak/themes/docteams/email/html/email-verification.ftl`
- `compose/keycloak/themes/docteams/email/html/template.ftl`
- `compose/keycloak/themes/docteams/email/theme.properties`

**Slice 278A — Modify:**
- `compose/keycloak/realm-export.json`

---

## Epic 279: Integration Testing & Verification

**Goal**: Verify the complete Keycloak + Gateway + Backend flow end-to-end, verify JIT provisioning works, and confirm that switching back to Clerk mode preserves full functionality.

**References**: Architecture doc Section 10, 11.Phase E.

**Dependencies**: All other epics (convergence point).

**Scope**: Both

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **279A** | 279.1--279.6 | Full flow verification: (1) signup -> org creation -> first login -> JIT provisioning -> API access, (2) invite member -> accept -> JIT member sync, (3) session expiry -> transparent re-auth, (4) CSRF validation for mutations. Backend integration tests with keycloak profile. ~3 new files (~10 tests). Both. | **Done** (PR #535) |
| **279B** | 279.7--279.10 | Clerk regression: switch `AUTH_MODE` to clerk, verify all existing flows. Mock E2E mode verification. Operational runbook documenting env vars, startup order, troubleshooting. ~2 new files (docs/tests). Both. | **Done** (PR #536) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 279.1 | Create gateway integration test infrastructure | 279A | | New file: `gateway/src/test/java/io/b2mash/b2b/gateway/integration/GatewayIntegrationTestBase.java`. Base class with Testcontainers PostgreSQL, WireMock for Keycloak OIDC endpoints (discovery, JWKS, token, userinfo), WireMock for backend. Configures Spring Security test OAuth2. |
| 279.2 | Test: complete login flow via gateway | 279A | 279.1 | Test: simulate OAuth2 authorization code flow. Verify: session created, session cookie set, `/bff/me` returns user info, subsequent `/api/**` requests include Bearer token. |
| 279.3 | Test: JIT tenant provisioning on first request | 279A | 271A | Backend test: send request with JWT containing unknown org ID (keycloak profile active). Verify: org_schema_mapping created, schema created, migrations applied, subsequent requests use cached schema. May need docker-compose or Testcontainers setup. |
| 279.4 | Test: JIT member sync on first request | 279A | 271B | Backend test: send request with JWT containing known org but unknown user ID (keycloak profile). Verify: member record created with correct email, name, role. |
| 279.5 | Test: CSRF protection for mutations | 279A | | Gateway test: send POST/PUT/DELETE without CSRF token, verify 403. Send with valid `X-XSRF-TOKEN`, verify request proxied. |
| 279.6 | Test: session expiry and token refresh | 279A | | Gateway test: simulate expired access token in session. Verify: gateway uses refresh token to obtain new access token transparently (Spring Security's `OAuth2AuthorizedClientManager` handles this). |
| 279.7 | Clerk mode regression test | 279B | | Verification: set `NEXT_PUBLIC_AUTH_MODE=clerk`, `SPRING_PROFILES_ACTIVE=local`. Run existing test suites. Verify: no regressions from JIT provisioning code (guarded by profile), no regressions from middleware changes (guarded by AUTH_MODE), no regressions from API client changes (guarded by AUTH_MODE). |
| 279.8 | Mock E2E mode regression test | 279B | | Verification: set `NEXT_PUBLIC_AUTH_MODE=mock`, `SPRING_PROFILES_ACTIVE=e2e`. Run E2E stack with `e2e-up.sh`. Verify: mock IDP flow works, Playwright fixtures work, smoke tests pass. |
| 279.9 | Create operational runbook | 279B | | New file: `documentation/keycloak-gateway-runbook.md`. Document: (1) env var reference for all three modes, (2) startup order (postgres -> keycloak -> gateway -> backend -> frontend), (3) how to generate Keycloak client secret, (4) how to import realm, (5) troubleshooting common issues (session table missing, CSRF errors, token relay failures). |
| 279.10 | Create architecture diagram update | 279B | | Modify or new: add production topology diagram to architecture doc or README showing the three-mode architecture (Clerk/Keycloak/Mock). |

### Key Files

**Slice 279A — Create:**
- `gateway/src/test/java/io/b2mash/b2b/gateway/integration/GatewayIntegrationTestBase.java`
- `gateway/src/test/java/io/b2mash/b2b/gateway/integration/FullFlowIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/multitenancy/JitProvisioningIntegrationTest.java` (if not covered in 271A)

**Slice 279B — Create:**
- `documentation/keycloak-gateway-runbook.md`

**Slice 279B — Read for context:**
- `compose/scripts/e2e-up.sh` — existing E2E stack
- `architecture/phase36-keycloak-gateway-bff.md` — Section 10 (swappable architecture)

### Architecture Decisions

- **E2E stack unchanged**: The existing E2E stack (mock IDP, mock auth) is not modified. It remains the fastest path for automated testing. Keycloak + Gateway integration testing is manual or uses dedicated gateway integration tests.
- **Three-mode architecture**: Clerk (current default), Keycloak BFF (new), Mock (E2E). All three modes coexist, selected by env vars.

---
