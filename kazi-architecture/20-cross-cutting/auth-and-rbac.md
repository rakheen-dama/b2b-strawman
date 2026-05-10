# Auth and RBAC

## What this concern covers

Three coexisting authentication stacks, one capability-based authorisation model, and a load-bearing backend filter chain. Specifically:

1. **Staff auth via gateway + Keycloak (production)** — OAuth2 OIDC at the gateway BFF, server-side session, `TokenRelay=` injects the access token onto the proxied request. `→ ../_discovery/A6-cross-cutting.md:68`, `→ ../_discovery/A3-portal-gateway-map.md:208`.
2. **Staff auth via mock IDP (E2E tests)** — same backend filter chain; the frontend's `getAuthContext()` switches provider via `NEXT_PUBLIC_AUTH_MODE`. Both modes produce a Spring Security `JwtAuthenticationToken` so the backend is auth-mode-agnostic. `→ ../_discovery/A6-cross-cutting.md:68`.
3. **Portal magic-link (separate trust domain)** — backend-minted HS256 JWT in `localStorage`, validated by a dedicated filter chain matching `/portal/**`. Bypasses the gateway entirely. `→ ../_discovery/A6-cross-cutting.md:69`, `→ ../_discovery/A3-portal-gateway-map.md:241`.

A fourth authentication shape — **platform admin** — is not a separate stack but a privileged claim *inside* the staff Keycloak token: the `groups` claim is read by `PlatformAdminFilter` and bound onto `RequestScopes.GROUPS`. Platform-admin endpoints (`/api/platform-admin/**`) hit the same staff filter chain; the difference is downstream gating. `→ ../_discovery/A6-cross-cutting.md:102`.

Authorisation is **capability-based, not role-based**. The `JwtAuthenticationToken` carries identity but no Spring authorities for org roles; a custom `CapabilityAuthorizationManager` resolves the actor's capabilities from the tenant schema and a `@RequiresCapability` annotation on each controller method enforces it. `→ ../_discovery/A6-cross-cutting.md:109`. The frontend mirrors the model purely for UX — the backend is always the enforcement point.

## The three auth stacks side-by-side

| Aspect | Staff (Keycloak) | Staff (mock E2E) | Portal | Platform admin |
|---|---|---|---|---|
| Issuer | Keycloak | mock-idp `:8090` | backend itself (`PortalJwtService`) | Keycloak (with `groups` claim) |
| Token type | OAuth2 access token | HS-signed mock JWT | HS256 portal JWT, 1h TTL | OAuth2 access token |
| Storage | Server-side session in gateway (Postgres / Redis) | `mock-auth-token` cookie | `localStorage` key `portal_jwt` | Server-side session in gateway |
| Browser cookie | `SESSION` (HttpOnly, SameSite=Lax) | `mock-auth-token` | _(none — Bearer header)_ | `SESSION` |
| Path prefix | `/api/**` via gateway `:8443` | `/api/**` direct to backend `:8081` | `/portal/**` + `/api/portal/**` direct to backend | `/api/platform-admin/**` via gateway |
| Tenant binding | `TenantFilter` from `o.id` JWT claim | same | `CustomerAuthFilter` from JWT `org_id` | varies — most platform-admin ops run on `public` schema |
| Filter chain | `@Order(2)` staff chain | `@Order(2)` staff chain | `@Order(1)` portal chain (`securityMatcher("/portal/**")`) | `@Order(2)` staff chain + `PlatformAdminFilter` gate |
| Front door | `frontend/lib/auth/providers/keycloak-bff.ts` | `frontend/lib/auth/providers/mock/server.ts` | `portal/lib/api-client.ts` (`portalFetch`) | `frontend/` admin pages |

Anchors: `→ gateway/src/main/resources/application.yml:43`, `→ gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:49`, `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:79`, `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:105`, `→ frontend/lib/auth/server.ts:19`, `→ portal/lib/api-client.ts:16`, `→ portal/lib/auth.ts:52`.

## The filter chain (staff path)

The staff/internal `SecurityFilterChain` is `@Order(2)`, declared at `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:105`. The portal chain at `@Order(1)` matches first against `/portal/**` and runs only `customerAuthFilter`; everything else falls through to staff. Filter order on the staff chain (`SecurityConfig.java:140`):

1. **`apiKeyAuthFilter`** — added before `BearerTokenAuthenticationFilter`. Gates `/internal/**` via `X-API-KEY`; never validates a JWT. Used by the gateway's `MemberSyncController` calls and provisioning. `→ ../30-modules/identity-access.md:50`.
2. **`BearerTokenAuthenticationFilter`** (Spring Security default) — validates the JWT against Keycloak's JWKS (or the mock IDP's signing key) and hands off to `ClerkJwtAuthenticationConverter`. The class name is a Clerk-era artefact; today it adapts both Keycloak and mock claims. `→ ../_discovery/A6-cross-cutting.md:98`.
3. **`tenantFilter`** — extracts `o.id` from the validated JWT, resolves the tenant schema via `OrgSchemaMappingRepository`, and binds `RequestScopes.TENANT_ID` + `RequestScopes.ORG_ID`. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java:50`.
4. **`memberFilter`** — JIT-syncs the `Member` row (creates on first authenticated call), then binds `MEMBER_ID`, `ORG_ROLE`, and `CAPABILITIES` from the joined `OrgRole`. Must run after `TenantFilter` because the lookup uses Hibernate's tenant-scoped `search_path`. `→ ../30-modules/identity-access.md:53`, `→ ../_discovery/A6-cross-cutting.md:100`.
5. **`subscriptionGuardFilter`** — billing-tier enforcement. Must run after `MemberFilter` because owner-bypass during grace-period flows depends on `ORG_ROLE`. `→ ../_discovery/A6-cross-cutting.md:105`.
6. **`platformAdminFilter`** — extracts the `groups` claim and binds `RequestScopes.GROUPS`. Runs after subscription so platform-admin operations can override subscription gating where appropriate. `→ ../_discovery/A6-cross-cutting.md:102`.
7. **`tenantLoggingFilter`** — last; sets MDC fields `tenantId, userId, memberId, requestId` (UUID per request) and clears them on response. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantLoggingFilter.java:17`.

After the filter chain runs, Spring MVC dispatches to the controller. Method-level `@RequiresCapability` is then evaluated by `CapabilityAuthorizationManager` (see next section).

This forms a **dependency-resolution ladder**: each filter's bindings depend on the previous filter's bindings. Reordering is hostile — see _Known fragilities_ below. The ordering invariant is captured in `→ ../30-modules/identity-access.md:58`.

## Capability-based RBAC

### The model

- **`Capability` enum (19 values)** — the canonical authorisation vocabulary: `FINANCIAL_VISIBILITY`, `INVOICING`, `PROJECT_MANAGEMENT`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT`, `OVERRIDE_MATTER_CLOSURE`, `AI_ASSISTANT_USE`, etc. Line 43 declares the `OWNER_ONLY` set — capabilities `admin` does **not** inherit. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java:7`.
- **`OrgRole` aggregate** — a tenant-scoped entity carrying an `@ElementCollection` of capabilities on the `org_role_capabilities` join table. System roles (`owner`, `admin`, `member`) are bootstrapped at provisioning; tenants may create custom roles via `/settings/roles`. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/OrgRole.java:23`.
- **`Member.capabilityOverrides`** — per-member additive/subtractive capability overrides on top of the role's base set, used for surgical exceptions without spawning a new role. (See ADR-163 in the index.) `→ ../90-adr-index.md:63`.

### Enforcement (backend)

`@RequiresCapability(Capability.X)` on a controller method triggers `CapabilityAuthorizationManager.check(...)` which reads the annotation, calls `RequestScopes.hasCapability(annotation.value())`, and returns an `AuthorizationDecision`. The check resolves capabilities from the DB-resolved `OrgRole` plus overrides — **not** from JWT claims. JWT carries identity only. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityAuthorizationManager.java:28`, `→ ../_discovery/A6-cross-cutting.md:109`.

This is the load-bearing decision in ADR-178 (DB-authoritative role resolution): the JWT's role/group claims are advisory at most, and the gateway authorises **nothing** (ADR-180).

### Frontend mirroring

The frontend mirrors the capability model so users do not see buttons they cannot use; the backend remains the only enforcement point. Wiring:

- `frontend/lib/capabilities.tsx:94` — `CapabilityProvider`, seeded from `GET /api/me/capabilities` in the org layout `→ ../30-modules/identity-access.md:21`.
- `frontend/lib/capabilities.tsx:115` — `hasCapability` short-circuits true for `owner`/`admin` to mirror backend OWNER_ONLY semantics.
- `frontend/lib/capabilities.tsx:141` — `<RequiresCapability cap="...">` UI wrapper.
- `frontend/lib/nav-items.ts` — `requiredCapability` per nav entry; sidebar items the user lacks are hidden client-side, and the backend re-checks on the route's data fetch. `→ ../_discovery/A6-cross-cutting.md:126`.

## Portal stack

The portal runs an entirely separate auth path. Trust-boundary separation is the design point: portal traffic never touches the gateway and never enters the staff filter chain.

1. **Magic-link request.** `POST /portal/auth/request-link` with `{ email, orgId }` issues a `MagicLinkToken` row containing a SHA-256 hash of a 32-byte random token (the raw token is emailed and never persisted) plus `expiresAt` (~15 min, single-use). `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkService.java:130`, `→ ../30-modules/customer-portal.md:43`.
2. **Email delivery.** Backend emails the link; in dev mode the response also returns `magicLink` so test scripts can grab it without Mailpit. `→ ../_discovery/A3-portal-gateway-map.md:66`.
3. **Token exchange.** `POST /portal/auth/exchange` with `{ token, orgId }` looks up by hash, validates `usedAt IS NULL AND expiresAt > now()`, marks `usedAt`, and mints a backend-signed HS256 portal JWT carrying `org_id`, `customer_id`, `portal_contact_id`, `email`. 1h TTL per ADR-077. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalAuthController.java:20`, `→ ../30-modules/customer-portal.md:140`.
4. **Storage.** `portal/lib/auth.ts:52` `storeAuth(token, customer)` writes `portal_jwt` + `portal_customer` + `portal_last_org_id` to `localStorage`. The XSS-vs-XSRF tradeoff is documented in ADR-077 — localStorage was chosen because the portal is read-mostly, the 1h TTL caps blast radius, and avoiding cookies sidesteps BFF infrastructure. `→ ../30-modules/customer-portal.md:142`.
5. **Subsequent requests.** `portal/lib/api-client.ts:16` `portalFetch` injects `Authorization: Bearer <jwt>` on every call to `${NEXT_PUBLIC_PORTAL_API_URL}` (default `http://localhost:8080`). On HTTP 401 the client clears storage and hard-navigates to `/login`. `→ ../_discovery/A3-portal-gateway-map.md:75`.
6. **Backend validation.** The `@Order(1)` chain at `SecurityConfig.java:79` runs only `customerAuthFilter`. The filter validates HS256 + claims via `PortalJwtService`, then binds `RequestScopes.CUSTOMER_ID` + `RequestScopes.PORTAL_CONTACT_ID` (in addition to `TENANT_ID`). Downstream services scope queries by these values. `→ ../30-modules/customer-portal.md:148`.

The `portal_last_org_id` key persists across logout so deep-link returns after JWT expiry can still resolve the tenant before login. `→ ../_discovery/A3-portal-gateway-map.md:78`.

## Mock-vs-Keycloak symmetry

Two production frontends, one set of backend filters. The auth-mode switch lives in `frontend/lib/auth/server.ts:19` (`getAuthContext()`) and selects between `keycloak-bff` and `mock` providers based on `NEXT_PUBLIC_AUTH_MODE`. Both providers implement the same `AuthProvider` contract (ADR-085). Anchors:

- `frontend/lib/auth/providers/keycloak-bff.ts:35` — Keycloak BFF provider; calls `${GATEWAY_URL}/bff/me` forwarding the `SESSION` cookie. `→ ../30-modules/identity-access.md:30`.
- `frontend/lib/auth/providers/mock/server.ts` — mock provider; reads the `mock-auth-token` cookie. `→ ../30-modules/identity-access.md:31`.
- Mock IDP runs at `:8090` in the E2E stack (ADR-086). `→ ../_discovery/A6-cross-cutting.md:68`.

The critical property: **both modes produce a Spring Security `JwtAuthenticationToken` with the same shape downstream**. The backend filter chain does not branch on mode; the same `BearerTokenAuthenticationFilter` validates either token, and the same `ClerkJwtAuthenticationConverter` maps claims. This is what makes Playwright E2E coverage tractable — tests run against the real backend filter chain, only the IDP is faked. `→ ../30-modules/identity-access.md:76`.

## Modules affected

Every authenticated module in the system relies on this concern. Direct cross-links:

- **`30-modules/identity-access.md`** — staff auth filter chain, JIT member sync, `Capability` enum, `OrgRole`, `PendingInvitation`. The canonical owner of staff identity.
- **`30-modules/customer-portal.md`** — portal magic-link, `CustomerAuthFilter`, `MagicLinkToken`, portal JWT shape, read-model boundary.
- **`30-modules/platform-administration.md`** — `groups` claim handling, `PlatformAdminFilter`, public-schema admin operations *(filled in subsequent pass)*.
- **`30-modules/tenancy-provisioning.md`** — `OrgSchemaMappingRepository` lookup driven by `TenantFilter`, JIT seeding interplay with provisioning *(filled in subsequent pass)*.

Cross-cutting: every other module page lists this concern under its _Cross-cutting touchpoints_ section.

## Active ADRs

Per `90-adr-index.md` (canonical: ADR-178 + ADR-180 + ADR-T003 + ADR-T004):

- **ADR-T003** — product-layer-roles-over-keycloak-org-roles. Capabilities live in DB, not JWT claims. `→ ../90-adr-index.md:69`.
- **ADR-T004** — gateway-bff-over-direct-api-access. Gateway is the OAuth2 client; backend is a resource server. `→ ../90-adr-index.md:70`.
- **ADR-085** — auth-provider-abstraction. The Keycloak/mock seam on the frontend. `→ ../90-adr-index.md:54`.
- **ADR-086** — mock-idp-strategy. E2E mock IDP at `:8090`. `→ ../90-adr-index.md:55`.
- **ADR-138** — keycloak-jwt-claim-structure. Defines the JWT claim shape both producers must satisfy. `→ ../90-adr-index.md:56`.
- **ADR-140** — bff-pattern-token-storage. OAuth2 token sits in the server-side session, never in the browser. `→ ../90-adr-index.md:57`.
- **ADR-161** — application-level-capability-authorization. The capability gate is application code, not Spring authorities. `→ ../90-adr-index.md:61`.
- **ADR-178** — db-authoritative-role-resolution. DB is the source of truth; JWT role claims are advisory. `→ ../90-adr-index.md:64`.
- **ADR-179** — pending-invitation-role-assignment. `PendingInvitation` table replaces Keycloak-attribute invitations. `→ ../90-adr-index.md:65`.
- **ADR-180** — gateway-authorization-removal. Gateway only authenticates; backend authorises. `→ ../90-adr-index.md:66`.

Portal stack:

- **ADR-T005** — magic-links-over-customer-accounts. Template-level rationale. `→ ../90-adr-index.md:286`.
- **ADR-030** — magic-link-auth-for-customers. DB-backed `MagicLinkToken`. `→ ../90-adr-index.md:272`.
- **ADR-077** — portal-jwt-storage. localStorage, 1h TTL, accepted XSS exposure given read-mostly surface. `→ ../90-adr-index.md:275`.
- **ADR-076** — separate-portal-app. Portal is its own Next.js bundle, not a route group in `frontend/`. `→ ../90-adr-index.md:274`.

## Known fragilities / open questions

1. **`Member.clerkUserId` field-name lies.** The column was named in the Clerk era; today it stores the Keycloak `sub`. The glossary divergence is acknowledged so renames don't drift the field, but the field name remains a code-reading trap. `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/Member.java:28`, `→ ../30-modules/identity-access.md:116`.

2. **Implicit ADR supersession without formal markers.** ADR-139 (Keycloak org-role-mapper) and ADR-156 (Keycloak invitation-role-gap-mitigation) are functionally retired by ADR-178 / ADR-179 / ADR-180 but are still marked `Proposed` in their bodies. `90-adr-index.md` flags them as Stale; a future pass should retro-stamp `Status: Superseded` lines. `→ ../90-adr-index.md:440`, `→ ../90-adr-index.md:441`, `→ ../30-modules/identity-access.md:117`.

3. **Filter ordering is implicit.** The staff chain's order is encoded only in the imperative wiring at `SecurityConfig.java:140`. Refactoring `subscriptionGuardFilter` earlier (e.g., before `MemberFilter`) would break owner-bypass silently — there is no test that asserts the order, and no comment block at the wiring site explaining why each filter sits where it does. The dependency-resolution ladder is documented here and at `→ ../30-modules/identity-access.md:58` but not enforced.

4. **Portal JWT in localStorage — XSS vs cookie tradeoff.** ADR-077's exposure analysis assumes the portal is "read-mostly except comment posting." Today's surface includes comment post + proposal accept/decline + acceptance accept + info-request item upload + info-request submit + payment-link redirect. None catastrophic, but the trajectory is one-way; ADR-077 says migration to HTTP-only cookies should be prioritised "if the portal adds destructive operations in a future phase." That trigger is not formalised. `→ ../30-modules/customer-portal.md:210`.

5. **No `@RequiresCapability` static analysis.** Forgetting the annotation on a new controller method silently downgrades to "any authenticated tenant member can call this." There is no compile-time or test-time check enforcing presence. Mitigated only by code review and the `OrgRole`-level inability to escalate beyond the seeded capability set. `→ ../30-modules/identity-access.md:119`.

6. **Token revocation when a `PortalContact` is offboarded.** Flipping `PortalContact.status` to `SUSPENDED` or `ARCHIVED` does not invalidate live portal JWTs; they remain valid for the remainder of their TTL (up to 1h). No JWT denylist or session-invalidation table exists. `→ ../30-modules/customer-portal.md:212`.
