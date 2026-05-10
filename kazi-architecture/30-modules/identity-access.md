# Identity & Access

**Bounded context:** see [`10-bounded-contexts.md` § identity-access](../10-bounded-contexts.md).

## Purpose

Validates the staff JWT (Keycloak production / mock IDP for E2E), JIT-syncs the `Member` row on first request, resolves the actor's `OrgRole` and `Capability` set from tenant-schema tables, and enforces capability-based RBAC on every controller method via `@RequiresCapability`. Owns invitation issuance and acceptance for new members. Portal magic-link auth is **not** in this context — it is owned by `customer-portal` and runs under a separate filter chain (`SecurityConfig.java:79`).

## Entities owned

- `Member` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java:22` — tenant-scoped staff identity; columns include `clerkUserId` (now holds the Keycloak `sub` despite the legacy field name — see Open Questions), `email`, `name`, `orgRole` FK.
- `ProjectMember` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/ProjectMember.java:14` — per-project membership with `projectRole`, used by `projects` for access checks. Co-owned conceptually with `projects` but the table lives in this package.
- `OrgRole` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRole.java:23` — tenant-scoped role aggregate with `@ElementCollection` of capabilities on the `org_role_capabilities` join table. System roles (`owner`, `admin`, `member`) are bootstrapped at provisioning; tenants may create custom roles.
- `PendingInvitation` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/PendingInvitation.java:22` — durable invitation record (`email`, `status`, `expiresAt`, `acceptedAt`, FK→`OrgRole`, FK→`Member invitedBy`). DB-side replacement for the retired Keycloak attribute approach (ADR-179).
- `Capability` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java:7` — 19-value enum (not a JPA entity but the canonical authority vocabulary; line 43 declares the `OWNER_ONLY` set that `admin` does **not** inherit).

## REST surface

- `OrgMemberController` `→ /api/members` (3 endpoints) — `GET /`, `GET /{id}/capabilities`, `PUT /{id}/role` `→ A1-backend-map.md:403`.
- `OrgRoleController` `→ /api/org-roles` (5 endpoints) — full CRUD `→ A1-backend-map.md:404`.
- `MeController` (capabilities self-read) — `GET /api/me/capabilities` `→ A2-frontend-map.md:280` (consumed by `CapabilityProvider`).
- `MemberSyncController` (internal) `→ /internal/members/*` `→ A1-backend-map.md:382` — used by the gateway / provisioning flow to sync member metadata under `X-API-KEY` (ApiKeyAuthFilter chain, never JWT).
- Invitation endpoints under `/api/members` / `/api/org-roles` cluster (issue / accept / revoke). Anchor via the `invitation/` package `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/invitation/PendingInvitation.java:22`.

## Frontend pages / components

- `frontend/app/(app)/org/[slug]/team/page.tsx` — team list / invite / role-assign UI (consumes `/api/members` + `/api/org-roles`). Anchor via the team route `→ A2-frontend-map.md:90`.
- `frontend/app/(app)/org/[slug]/settings/roles/page.tsx` — custom-role editor.
- `frontend/lib/auth/server.ts:19` — `getAuthContext()` server-side identity resolver; the auth-mode switch (`keycloak` | `mock`) lives here `→ A2-frontend-map.md:373`.
- `frontend/lib/auth/providers/keycloak-bff.ts:35` — Keycloak BFF provider; calls `${GATEWAY_URL}/bff/me` forwarding the `SESSION` cookie `→ A2-frontend-map.md:374`.
- `frontend/lib/auth/providers/mock/server.ts` — mock provider for E2E (reads `mock-auth-token` cookie) `→ A2-frontend-map.md:375`.
- `frontend/lib/auth/client/auth-provider.tsx` — `useAuth()` for client components `→ A2-frontend-map.md:376`.
- `frontend/lib/capabilities.tsx:94` — `CapabilityProvider`, seeded from `GET /api/me/capabilities` in the org layout `→ A6-cross-cutting.md` §2 (line 121).
- `frontend/lib/capabilities.tsx:115` — `hasCapability` short-circuits true for `owner`/`admin` (mirrors backend OWNER_ONLY logic).
- `frontend/lib/capabilities.tsx:141` — `<RequiresCapability cap="...">` UI wrapper.
- `frontend/lib/nav-items.ts` — declares `requiredCapability` per nav entry; sidebar hides items the user lacks `→ A6-cross-cutting.md` §2 (line 126).

## Domain events

- **Emitted:** `MemberAddedToProjectEvent` (from `ProjectMemberService`) `→ A1-backend-map.md:459` — consumed by `notifications/NotificationService` for in-app + email notification `→ A1-backend-map.md:475`.
- **Consumed:** none. (No `@EventListener` lives in this package — capability resolution is pull, not push.)
- Audit emission (not a domain event): every role/member mutation calls `auditService.log(...)` directly in-transaction `→ A6-cross-cutting.md` §3.

## Cross-cutting touchpoints

### Filter chain (load-bearing order)

The staff/internal chain is `@Order(2)` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:105`. Filter wiring (`SecurityConfig.java:140-145`):

1. `apiKeyAuthFilter` — before `BearerTokenAuthenticationFilter`; gates `/internal/*` via `X-API-KEY` `→ backend/.../security/ApiKeyAuthFilter.java`.
2. `BearerTokenAuthenticationFilter` (Spring default) — JWT validation, hands off to `ClerkJwtAuthenticationConverter` `→ backend/.../security/ClerkJwtAuthenticationConverter.java`.
3. `tenantFilter` — after Bearer; binds `RequestScopes.TENANT_ID` / `ORG_ID` from JWT `o.id` `→ backend/.../multitenancy/TenantFilter.java:64`.
4. `memberFilter` — after Tenant; binds `MEMBER_ID` / `ORG_ROLE` / `CAPABILITIES`, JIT-creates the `Member` row on first request `→ backend/.../member/MemberFilter.java`.
5. `subscriptionGuardFilter` — after Member; billing tier enforcement (depends on `ORG_ROLE` for owner-bypass).
6. `platformAdminFilter` — after Subscription; binds `GROUPS` from the JWT `groups` claim `→ backend/.../security/PlatformAdminFilter.java`.
7. `tenantLoggingFilter` — last; MDC enrichment.

Order is non-negotiable. `MemberFilter` must run after `TenantFilter` because the member lookup is tenant-scoped via Hibernate's `search_path`. `SubscriptionGuardFilter` must run after `MemberFilter` because owner-bypass needs `ORG_ROLE` `→ A6-cross-cutting.md` §2 (line 105).

### Capability authorisation (not Spring authorities)

The `JwtAuthenticationToken` carries identity but **no** Spring authorities for org roles. Authorisation happens via `@RequiresCapability` annotations on controller methods plus a custom authorisation manager:

- `@RequiresCapability` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/RequiresCapability.java:18`.
- `Capability` enum (19 values) `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java:7`. OWNER_ONLY set at line 43.
- `CapabilityAuthorizationManager` reads the annotation off the method, returns `AuthorizationDecision(RequestScopes.hasCapability(...))` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityAuthorizationManager.java:37`.

The Spring Security `JwtAuthenticationToken` is identity-only; capability resolution happens entirely from DB-resolved `OrgRole` (ADR-178). This is the canonical product-layer authorisation seam (ADR-T003).

### Three coexisting auth modes

- **Keycloak (production)** — gateway BFF (`GatewaySecurityConfig.java:49`), OAuth2 login, `SESSION` cookie, `TokenRelay=` filter on the proxied request. ADR-T004 / ADR-140.
- **Mock IDP (E2E)** — `frontend/lib/auth/providers/mock/server.ts` reads a JWT from `mock-auth-token` cookie; the backend filter chain treats both modes identically since both produce a `JwtAuthenticationToken`. ADR-086.
- **Portal magic-link (separate)** — pointer only. Owned by `customer-portal`; runs under `SecurityConfig.java:79` (`@Order(1)`, `securityMatcher("/portal/**")`); see `30-modules/customer-portal.md`.

The auth-provider abstraction (ADR-085) is the seam that makes Keycloak/mock symmetric: both providers implement the same `AuthProvider` contract behind `getAuthContext()` `→ frontend/lib/auth/server.ts:19`.

### JIT member seeding

`MemberFilter` looks up the `Member` row by `(clerkUserId, current schema)` and creates it on first JWT use, copying `email`/`name` from claims and assigning the default `member` role. This means a freshly-onboarded user gets a tenant-schema row on their first authenticated call rather than at provisioning time `→ A6-cross-cutting.md` §2 (line 100).

## Vertical specifics

None. The `Capability` enum is identical across all four vertical profiles (`legal-za`, `accounting-za`, `consulting-za`, `consulting-generic`). Vertical-specific capabilities like `MANAGE_TRUST` / `APPROVE_TRUST_PAYMENT` / `VIEW_TRUST` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java:7` exist in the universal enum but are only granted by the legal-vertical seeded roles. Module-gating (`trust_accounting`) sits one layer higher, in `vertical-profiles` — see `30-modules/vertical-profiles.md`.

## Active ADRs

- **ADR-T002** (scopedvalues-over-threadlocal) — `RequestScopes` carriers used by filters `→ A4-adr-triage.md:290`.
- **ADR-T003** (product-layer-roles-over-keycloak-org-roles) — capabilities live in DB, not JWT claims `→ A4-adr-triage.md:291`.
- **ADR-T004** (gateway-bff-over-direct-api-access) — gateway is the OAuth2 client; backend is a resource server `→ A4-adr-triage.md:292`.
- **ADR-085** (auth-provider-abstraction) — Keycloak / mock symmetry on the frontend `→ A4-adr-triage.md:94`.
- **ADR-086** (mock-idp-strategy) — E2E mock IDP at port 8090 `→ A4-adr-triage.md:95`.
- **ADR-138** (keycloak-jwt-claim-structure) — JWT claim shape `→ A4-adr-triage.md:147`.
- **ADR-140** (bff-pattern-token-storage) — session-side OAuth2 token, never browser-side `→ A4-adr-triage.md:150`.
- **ADR-161** (application-level-capability-authorization) — capability gate is application code, not Spring authorities `→ A4-adr-triage.md:170`.
- **ADR-178** (db-authoritative-role-resolution) — DB is the source of truth; JWT role claims are advisory `→ A4-adr-triage.md:187`.
- **ADR-179** (pending-invitation-role-assignment) — `PendingInvitation` table replaces Keycloak invitation attributes `→ A4-adr-triage.md:188`.
- **ADR-180** (gateway-authorization-removal) — gateway only authenticates; backend authorises `→ A4-adr-triage.md:189`.
- **ADR-076** (separate-portal-app) — listed for the portal-vs-staff split context, even though the portal-side detail lives in `customer-portal.md` `→ A4-adr-triage.md:85`.

## Key flows

No dedicated flow doc exists for staff login (the magic-link portal flow at `50-flows/portal-magic-link-to-task-completion.md` covers a different surface). Inline mini-flow:

1. Browser hits frontend route on port 3000. `frontend/lib/auth/middleware.ts` runs; if no `SESSION` cookie, redirects to gateway `/oauth2/authorization/keycloak`.
2. Gateway runs OAuth2 dance against Keycloak; on success `oauth2LoginSuccessHandler` `→ gateway/.../GatewaySecurityConfig.java:112` writes `KC_LAST_LOGIN_SUB` (120s) + `SESSION` cookie and redirects to `${FRONTEND_URL}/dashboard`.
3. Each subsequent API call from the frontend goes through the gateway: `TokenRelay=` filter pulls the OAuth2 access token off the server-side session and sets `Authorization: Bearer <jwt>` on the proxied request `→ gateway/src/main/resources/application.yml:43`.
4. Backend filter chain (above) runs in order. `BearerTokenAuthenticationFilter` validates the JWT against Keycloak's JWKS; `TenantFilter` binds `TENANT_ID`; `MemberFilter` resolves (or JIT-creates) the `Member`, hydrates `CAPABILITIES` from the joined `OrgRole`.
5. Spring MVC dispatches to the controller method. If the method carries `@RequiresCapability(Capability.X)`, `CapabilityAuthorizationManager` checks `RequestScopes.hasCapability(X)` and 403s if absent. Otherwise execution proceeds.
6. Controller serves the response; `tenantLoggingFilter` clears MDC.

The mock-auth path (E2E, port 3001/8081) replaces step 1–3 with a `mock-auth-token` cookie containing a self-signed JWT; the backend treats it identically.

## Open questions / known fragility

- **`Member.clerkUserId` field name lies.** The column was named in the Clerk era; today it stores the Keycloak `sub` `→ backend/.../member/Member.java:29`. The glossary divergence is acknowledged here so renames don't drift the field.
- **Implicit ADR supersession.** ADR-139 (Keycloak org-role-mapper) and ADR-156 (Keycloak invitation-role-gap-mitigation) are functionally retired by ADR-178 / ADR-179 / ADR-180 but are still marked `Proposed` in their bodies. A4 §triage flags them as Stale `→ A4-adr-triage.md:148, :165, :588`. They should carry an explicit `Superseded by ADR-178/179/180` marker on a follow-up ADR-index pass.
- **`ProjectMember` ownership ambiguity.** The entity lives in `member/` `→ backend/.../member/ProjectMember.java:14` but is consumed primarily by `projects/` for access checks. Listed as owned here per the package layout; cross-link from `30-modules/projects.md`.
- **No `@RequiresCapability` static analysis.** Forgetting the annotation on a new controller method silently downgrades to "any authenticated tenant member can call this." There is no compile-time or test-time check enforcing presence; mitigated by code review and the `OrgRole`-level inability to escalate beyond the seeded capability set.
