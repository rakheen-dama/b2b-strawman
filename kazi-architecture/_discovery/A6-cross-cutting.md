# A6 — Kazi Cross-Cutting Architectural Concerns

**Generated:** 2026-05-10
**Scope:** All four repos (`backend/`, `frontend/`, `gateway/`, `portal/`).
**Source:** Builds on A1–A3 discovery. Anchored at `→ relative/path:line` against the worktree root `/Users/rakheendama/Projects/2026/b2b-strawman/`.

This document maps the *cross-cutting* concerns that thread through more than one module. Module-internal concerns (invoicing rules, retainer rollover, etc.) are explicitly out of scope. Sections cross-link where the same artefact participates in two concerns (the sealed `DomainEvent` bus is the obvious one — referenced in §3, §4, and §6).

---

## 1. Multi-tenancy (schema-per-tenant)

Schema-per-tenant isolation is the load-bearing assumption of every backend service. Every tenant gets a dedicated `tenant_<12-hex>` schema; the only public-schema entities are `organizations`, `org_schema_mapping`, `subscriptions`, `subscription_payments`, `access_requests`, and the portal read-model projection tables. There are no shared tenant tables and no `tenant_id` columns on any domain entity — isolation is enforced exclusively at the connection level via Postgres `search_path`. ADR-064 ("dedicated-schema-only") locks this in.

### Tenant ID entry point

The tenant ID enters the system through a single backend filter — never through the gateway, never through HTTP headers. The gateway is intentionally transparent on this dimension (A3 §9): it forwards the OAuth2 access token via `TokenRelay=` and the backend resolves tenancy itself.

`TenantFilter` extracts `o.id` from the validated JWT (Clerk v2 nested format or Keycloak format) and resolves it through `OrgSchemaMappingRepository`:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java:50` — `doFilterInternal` extracts `JwtUtils.extractOrgId(jwt)` then binds `RequestScopes.TENANT_ID` and `RequestScopes.ORG_ID`.

### Propagation: ScopedValue holders

Java 25 `ScopedValue` replaces `ThreadLocal` (ADR-T002 referenced in `backend/CLAUDE.md` anti-patterns). The carriers live in one file:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java:23` — `TENANT_ID, MEMBER_ID, ORG_ROLE, ORG_ID, CUSTOMER_ID, PORTAL_CONTACT_ID, AUTOMATION_EXECUTION_ID, CAPABILITIES, GROUPS`.

The `bindTenantScope(...)` helper (`RequestScopes.java:233`) is the only sanctioned way to bind — direct `ScopedValue.where()` outside this class is discouraged in favor of `runForTenant(...)` and `runForMember(...)`. The portal `CustomerAuthFilter` and the staff `MemberFilter` both follow this pattern, building carrier chains.

### Hibernate hooks

Two `@Component`-registered beans wire Hibernate 7 multitenancy. The Spring Boot 4 / Hibernate 7 nuance (called out in `backend/CLAUDE.md`) is that `hibernate.multiTenancy` is *not* set — Hibernate auto-detects from the registered provider:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java:13` — implements `MultiTenantConnectionProvider<String>`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java:106` — `stmt.execute("SET search_path TO " + sanitizeSchema(schema));` runs on connection checkout; line 112 resets to `public` on release.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantIdentifierResolver.java:7` — implements `CurrentTenantIdentifierResolver<String>`, reads `RequestScopes.TENANT_ID`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/HibernateMultiTenancyConfig.java:14` — registers both via `MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER` / `MULTI_TENANT_IDENTIFIER_RESOLVER`.

### Tenant-aware background jobs

`TenantScopedRunner` is the single iteration primitive used by every `@Scheduled` job — there are no per-job tenant loops:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java:37` — `forEachTenant(BiConsumer<String, String>)` iterates `OrgSchemaMappingRepository.findAll()` and binds `TENANT_ID + ORG_ID` for each invocation. See §7 below for the schedulers that use it.

### Provisioning: how a new tenant gets a schema

The provisioning service is a script, not a state machine — it runs Flyway DDL, sets the vertical profile, and pipes through pack installers. Cross-link to §4 below for the pack/profile interplay:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:140` — `provisionTenant(clerkOrgId, orgName, verticalProfile, country)`. Lines 170–189 sequence: resolve currency → set profile → install document-template packs → install automation-template packs → reconcile profile (merge `enabledModules`).

Migrations split: `backend/src/main/resources/db/migration/global/` (public schema, run once at startup) vs `backend/src/main/resources/db/migration/tenant/` (per-tenant, run at provisioning + on startup for late-arriving migrations). The audit-events DDL is in the tenant tree because audit is per-tenant (see §3).

### Frontend tenant context

The frontend does **not** explicitly forward a tenant header. The org slug comes from the URL parameter `[slug]` and is used only for client routing/deep-links. All backend calls are tenant-scoped via the auth token; the gateway/backend derives tenant from the JWT (A2 §5 "Tenant Context Propagation"; A3 §9 "Tenant context determination"). The `OrgProfileProvider` (`frontend/lib/org-profile.tsx:27`) carries `verticalProfile`, `enabledModules`, `terminologyNamespace` — but those are projected from server-side `OrgSettings`, not used to address the tenant.

This is a load-bearing simplification: the gateway is one less place a "trust the client header" bug can be introduced. The cost is that a frontend cannot legitimately speak for two tenants in one session.

---

## 2. Authentication & RBAC

### Auth modes — where the switch lives

Two production stacks coexist:

- **Staff (`frontend/`, port 3000)**: Keycloak OIDC via the gateway BFF. Auth mode flips at runtime via `NEXT_PUBLIC_AUTH_MODE` (`mock` | `keycloak`); the switch lives in `frontend/lib/auth/server.ts` (A2 §5). The backend filter chain treats both modes identically — both produce a Spring Security `JwtAuthenticationToken`.
- **Portal (`portal/`, port 3002)**: A completely separate magic-link + portal-JWT-in-`localStorage` stack. The portal bypasses the gateway and hits the Spring Boot backend directly at `NEXT_PUBLIC_PORTAL_API_URL` (A3 §11).

The most surprising structural fact in A3 holds: **two trust domains, two filter chains, no shared code path.**

### Gateway BFF flow

`→ gateway/src/main/resources/application.yml:43` — single declared route `id: backend-api`, `Path=/api/**`, filters `TokenRelay=` + `DedupeResponseHeader`.
`→ gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:49` — security rules: `/`, `/error`, `/actuator/health`, `/bff/me`, `/bff/csrf`, `/api/access-requests`, `/api/access-requests/verify` are `permitAll`; `/internal/**` is `denyAll`; everything else is `authenticated`.
`→ gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:112` — `oauth2LoginSuccessHandler` writes the short-lived `KC_LAST_LOGIN_SUB` cookie (120s TTL) and redirects to `${FRONTEND_URL}/dashboard`.

The session is persisted server-side (Postgres or Redis via `spring-session-jdbc` / `spring-session-data-redis`); the browser cookie is `SESSION` (HttpOnly, SameSite=Lax). The `TokenRelay=` filter pulls the OAuth2 access token off the session and sets `Authorization: Bearer <jwt>` on the proxied request — the backend then runs its full JWT verification locally via Spring Security OAuth2 Resource Server.

### Portal magic-link flow

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkService.java:130` — issues a `MagicLinkToken` row with hashed token + `expiresAt`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalAuthController.java:20` — `POST /portal/auth/request-link` and `POST /portal/auth/exchange`.
`→ portal/lib/auth.ts:52` — client `storeAuth(token, customer)` writes `portal_jwt`, `portal_customer`, `portal_last_org_id` to `localStorage`.
`→ portal/lib/api-client.ts:16` — `portalFetch` injects `Authorization: Bearer <jwt>` from `localStorage`; on 401, calls `clearAuth()` and hard-navigates to `/login`.

The `portal_last_org_id` key persists across logout so deep-link returns after JWT expiry still resolve the tenant — a small design polish noted in A3 §3.

### Backend filter chain order (the load-bearing detail)

Two security filter chains coexist by `securityMatcher`:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:79` — `@Order(1)`, `securityMatcher("/portal/**")`, adds only `customerAuthFilter` (line 95) before `UsernamePasswordAuthenticationFilter`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:105` — `@Order(2)`, the staff/internal chain.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:140` — chain order:
1. `apiKeyAuthFilter` (before `BearerTokenAuthenticationFilter`) — `/internal/*` only.
2. `BearerTokenAuthenticationFilter` (Spring Security default) — JWT validation via `ClerkJwtAuthenticationConverter`.
3. `tenantFilter` (after Bearer) — binds `TENANT_ID` / `ORG_ID`.
4. `memberFilter` (after Tenant) — binds `MEMBER_ID` / `ORG_ROLE` / `CAPABILITIES`. JIT-syncs the `Member` row if absent.
5. `subscriptionGuardFilter` (after Member) — billing tier enforcement.
6. `platformAdminFilter` (after Subscription) — extracts `groups` claim, binds `GROUPS`.
7. `tenantLoggingFilter` (after PlatformAdmin) — MDC fields.

This order matters: `MemberFilter` must run after `TenantFilter` because it queries the tenant schema for the `Member` row (`MemberFilter.java:54` reads `RequestScopes.TENANT_ID` indirectly via Hibernate). `SubscriptionGuardFilter` must run after Member because it needs the role to allow owner-bypass for grace-period flows.

### Capability-based RBAC

The Spring Security `JwtAuthenticationToken` carries identity but no Spring authorities for org roles — authorization is enforced *inside the filter chain* by `@RequiresCapability` annotations and a custom `AuthorizationManager`:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/RequiresCapability.java:18` — `@interface` declaration.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java:7` — 19-value enum (`FINANCIAL_VISIBILITY`, `INVOICING`, `PROJECT_MANAGEMENT`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT`, `OVERRIDE_MATTER_CLOSURE`, `AI_ASSISTANT_USE`, etc.). Line 43 declares `OWNER_ONLY` — capabilities that admin does not inherit.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/CapabilityAuthorizationManager.java:28` — reads the annotation off the method, returns `AuthorizationDecision(RequestScopes.hasCapability(annotation.value()))`.

`OrgRole` is the aggregate (`backend/.../orgrole/OrgRole.java:23`) — a tenant-scoped entity with `@ElementCollection` of capabilities. System roles (`owner`, `admin`, `member`) are bootstrapped per tenant on provisioning; tenants can also create custom roles (frontend at `/settings/roles`).

### Frontend mirroring

The frontend mirrors the capability model client-side for UX (hide buttons users cannot use), but the backend is always the enforcement point:

`→ frontend/lib/capabilities.tsx:94` — `CapabilityProvider`, seeded from `GET /api/me/capabilities` in the org layout.
`→ frontend/lib/capabilities.tsx:115` — `hasCapability` returns true automatically for `owner`/`admin` (matches owner-bypass in OWNER_ONLY).
`→ frontend/lib/capabilities.tsx:141` — `<RequiresCapability cap="...">` wrapper.
`→ frontend/app/(app)/org/[slug]/layout.tsx:1` — wires `CapabilityProvider` + `OrgProfileProvider` + `TerminologyProvider` (A2 §2 route map).

Nav-item gating in `frontend/lib/nav-items.ts` declares `requiredCapability` per nav entry — sidebar items the user lacks the capability for are hidden client-side, but the route page still calls a backend list endpoint that re-checks.

---

## 3. Audit & compliance

### Audit emission

Every service that mutates state calls `AuditService.log(...)` directly — there is no implicit "audit everything via aspect" mechanism. ADR-067-style explicit emission won out over interceptor-based magic.

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java:16` — interface (`log`, `findEvents`).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/DatabaseAuditService.java:88` — `public void log(AuditEventRecord record)`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java:24` — fluent builder.

### Storage: append-only

Two layers enforce immutability — the JPA `@Immutable` annotation and a Postgres trigger:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEvent.java:27` — `@Immutable` (Hibernate skips dirty-check, never issues UPDATE).
`→ backend/src/main/resources/db/migration/tenant/V12__create_audit_events.sql:32` — `CREATE OR REPLACE FUNCTION prevent_audit_update()` that `RAISE EXCEPTION 'audit_events rows cannot be updated'`. Trigger `audit_events_no_update` registered on line 47.
`→ backend/src/main/resources/db/migration/tenant/V74__prevent_audit_delete.sql:4` — companion `prevent_audit_delete()` trigger.

Belt + braces: even raw SQL outside of Hibernate cannot mutate audit rows.

### Field-level diffs

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditDeltaBuilder.java:24` — utility for computing per-field diffs into the `details` JSONB column. Used heavily by `OrgSettingsService`, `CustomerService`, and any service that does PATCH-style updates.

### Audit UI

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventController.java` — `/api/audit-events`, paginated + filtered (event type, entity type, actor, date range). `AuditEventFilter`, `AuditSeverity`, `EventTypeFacet` and the related projections drive the filter UI in `frontend/app/(app)/org/[slug]/settings/audit-log/page.tsx`.

### DSAR / data-protection

The data-protection package lives under `datarequest/` (not `compliance/dataprotection/` as A1 suggested — the package was relocated):

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/PaiaManualGenerationService.java` — generates PAIA (Promotion of Access to Information Act, ZA jurisdiction) manuals.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationService.java` — anonymization-over-deletion pattern (ADR-062).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataExportService.java` — DSAR export bundling.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/JurisdictionDefaults.java` — per-jurisdiction retention defaults (ZA = PAIA; non-ZA tenants get a different default set).

The `DataSubjectRequest` entity is the durable handle for an in-flight request; status transitions emit audit events through the same path as everything else (§3).

---

## 4. Multi-vertical mechanism (the load-bearing concern)

Kazi's vertical strategy is **fork-the-template, not fork-the-codebase** — every tenant runs the same JAR/binary; verticality is a runtime configuration of three orthogonal knobs: a *vertical profile* (string identifier loaded from a JSON file), a list of *enabled modules* (string slugs), and a *terminology namespace* (also a string). All three live on `OrgSettings`.

### Vertical profile JSON loading

Profiles are classpath-resident JSON files. There are four:

`→ backend/src/main/resources/vertical-profiles/legal-za.json`
`→ backend/src/main/resources/vertical-profiles/accounting-za.json`
`→ backend/src/main/resources/vertical-profiles/consulting-za.json`
`→ backend/src/main/resources/vertical-profiles/consulting-generic.json`

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java:24` — `@Component`, loads `classpath:vertical-profiles/*.json` at startup.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java:62` — constructor reads via `PathMatchingResourcePatternResolver`; line 85 parses `enabledModules` array; line 96 parses `terminologyOverrides` (which becomes `terminologyNamespace`); line 128 stores into an immutable map.

A `ProfileDefinition` record (line 47) carries `profileId, name, description, enabledModules, terminologyNamespace, rateCardDefaults, taxDefaults`. Adding a new vertical means adding one JSON file — no code changes to the registry.

### Module gating — backend side

Two layers gate features. The first is a coarse-grained domain switch (§5 IntegrationGuardService). The second is module-slug gating, enforced inside the relevant service. There is no middleware or annotation; services check the slug imperatively:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleGuard.java:23` — `requireModule(String moduleId)` throws if the module is not in `OrgSettings.enabledModules`. This is the *generic* gate. Most legal-vertical services call it directly.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java:24` — the in-code definition of every module slug, its category, and its description. Line 46 is the `trust_accounting` entry.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountService.java:26` — `private static final String MODULE_ID = "trust_accounting";` then a `verticalModuleGuard.requireModule(MODULE_ID)` at the top of every public method.

This pattern is duplicated across `TrustReconciliationService:43`, `TrustTransactionService:45`, `ClientLedgerService:28`. The duplication is intentional — these services are *each* an entry point that could be reached without going through a shared gate, so each one self-defends. ADR-076 (module identity contract) frames this as fail-closed: if `enabledModules` doesn't contain the slug, every entry point returns `ModuleNotEnabledException` (403).

### Module gating — frontend side

The frontend has *three* gating layers (one more than the backend, because nav and page-level fallback both add UX value):

1. **Nav gates** — `frontend/lib/nav-items.ts:32` declares `requiredModule` per entry. `filterNavItems()` in the sidebar components removes inapplicable entries.
2. **Page-level server gate** — `frontend/lib/api/settings.ts:21` exposes `isModuleEnabledServer(slug)`; pages call this in their RSC body and short-circuit data fetching with a "feature off" placeholder. Used in legal-only pages like `/court-calendar` and `/trust-accounting/*`.
3. **Component gate** — `frontend/components/module-gate.tsx:11` — `<ModuleGate module="court_calendar">` reads from `OrgProfileProvider` and conditionally renders. Used inline in dashboard widgets (e.g. `app/(app)/org/[slug]/dashboard/page.tsx:210` per A2 §7).

The `OrgProfileProvider` holds the projected state:

`→ frontend/lib/org-profile.tsx:27` — props: `verticalProfile, enabledModules, terminologyNamespace`. Built once in `org/[slug]/layout.tsx` from the result of `GET /api/settings`.

### Terminology overrides

Terminology branching has a rare clean separation: backend stores only a *namespace key*; the frontend owns the actual word map.

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java:184` — `private String terminologyNamespace;` (column `terminology_namespace`). Set on profile change via `OrgSettingsService.updateVerticalProfile(...)` (`OrgSettingsService.java:818`).
`→ frontend/lib/terminology-map.ts:1` — `TERMINOLOGY` object with `consulting-za`, `accounting-za`, `legal-za` keys, each a `Record<string, string>`.
`→ frontend/lib/terminology.tsx:24` — `TerminologyProvider` consumes `verticalProfile` and provides `t("invoices") → "Fee Notes"` (legal-za).
`→ portal/lib/terminology-map.ts:1` — duplicated for the portal app (separate JS bundle, must ship its own copy).

The portal and staff frontend share *zero* terminology code, but both keys come from the same backend column. There is no `GET /api/settings/terminology` endpoint that returns the resolved map — the frontend has the map baked in, the backend just tells it which key to use. This is elegant (no backend code change to retitle a UI label) but also fragile (renaming a profile key requires changes in both `frontend/lib/terminology-map.ts` and `portal/lib/terminology-map.ts`).

### Pack system

The pack system is the SPI that lets vertical-specific *content* (document templates, automation rules, field groups, compliance checklists, clause libraries, project templates) ship as installable units rather than baked into seeders:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstaller.java:20` — SPI interface: `type()`, `availablePacks()`, `install(packId, tenantId, memberId)`, `checkUninstallable(...)`, `uninstall(...)`. Idempotent install + all-or-nothing uninstall (line 16).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackCatalogService.java:23` — aggregates all `PackInstaller` Spring beans into a `Map<PackType, PackInstaller>`. Line 36 fails fast at boot if two installers register for the same `PackType`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackInstall.java:17` — durable record of "this tenant has pack X installed" (per-tenant `pack_install` table).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/packs/PackType.java:7` — enum: `FIELD`, `COMPLIANCE`, `DOCUMENT_TEMPLATE`, `AUTOMATION_TEMPLATE`, `CLAUSE`, `CHECKLIST`, etc.

Concrete implementations are scattered (each domain owns its installer): `compliance/CompliancePackSeeder`, `template/TemplatePackInstaller`, `automation/...`, `fielddefinition/...`. The `seeder/` package holds shared abstract seeder helpers.

### Vertical-specific feature gates (worked example: trust accounting)

End-to-end, here is how trust accounting is hidden on a non-legal tenant:

1. **Profile registry** — `legal-za.json` is the only profile whose `enabledModules` array contains `"trust_accounting"`.
2. **Backend service gates** — every trust service self-checks via `verticalModuleGuard.requireModule("trust_accounting")` (`TrustAccountService.java:26`, `TrustTransactionService.java:45`, `TrustReconciliationService.java:43`, `ClientLedgerService.java:28`). 403 on miss.
3. **Backend invoice export guard** — independent of the module gate, the Phase 71 `TrustBoundaryGuard` blocks any *trust-related invoice* from being pushed to Xero (cross-link to §5).
4. **Frontend nav gate** — `frontend/lib/nav-items.ts` declares the trust-accounting nav group with `requiredModule: "trust_accounting"`.
5. **Frontend page server gate** — `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` calls `isModuleEnabledServer` and returns a placeholder if disabled.
6. **Frontend capability gate** — `VIEW_TRUST` / `MANAGE_TRUST` / `APPROVE_TRUST_PAYMENT` capabilities (`Capability.java:20-24`); the page is also wrapped in `<RequiresCapability cap="VIEW_TRUST">`.
7. **Portal nav gate** — `portal/lib/nav-items.ts:43` declares trust nav with both `profiles: ["legal-za"]` AND `modules: ["trust_accounting"]`.
8. **Portal page redirect** — `portal/app/(authenticated)/trust/page.tsx` checks `ctx.enabledModules` and `router.replace("/home")` if the module is off.
9. **Portal data endpoints** — `customerbackend/service/PortalTrustLedgerService.java:31` checks `MODULE_ID = "trust_accounting"` and returns 404 (not 403) if disabled — the module's *existence* is hidden from the portal.

Nine layers. Most are belt-and-braces UX defenses; the load-bearing ones are #2 (backend service gate) and #3 (the export guard). The gateway, notably, has zero awareness of trust-accounting — it transparently proxies whatever the backend chooses to expose (A3 §12).

### Vertical onboarding flow

When `provisionTenant(orgId, name, "legal-za", "ZA")` runs:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:140` — sequence (lines 170–189):
1. Resolve currency from `country` (`resolveCurrency(...)` line 292).
2. `setVerticalProfile(...)` (line 241) — sets `OrgSettings.verticalProfile`, `enabledModules`, `terminologyNamespace`.
3. `installPacksViaPipeline(schemaName, "legal-za", PackType.DOCUMENT_TEMPLATE)` — installs universal packs first (line 308 — `verticalProfile == null` in pack metadata) then profile-specific packs (line 313 — `getPackIdsForProfile("legal-za", DOCUMENT_TEMPLATE)`).
4. Same for `AUTOMATION_TEMPLATE`.
5. `verticalProfileReconciliationSeeder.reconcile(schemaName, orgId)` (line 189) — merges the profile's `enabledModules` into the tenant's settings (idempotent — safe to re-run).

`VerticalProfileReconciliationSeeder` (`verticals/VerticalProfileReconciliationSeeder.java:65`) is also runnable post-hoc — when an existing tenant changes profile, this is the routine that re-merges the enabledModules.

This is the part that makes the system elegant: switching a tenant from `consulting-generic` to `legal-za` after live data exists is a `PATCH /api/settings/vertical-profile` away, and the reconciliation seeder migrates them. It's also the part that's fragile — the reconciler does *not* uninstall packs from the old profile, only adds new ones. Going legal→consulting leaves orphaned legal templates installed.

---

## 5. Integration ports / BYOAK

### The six canonical domains

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationDomain.java:4` — six values: `ACCOUNTING("noop")`, `AI("noop")`, `DOCUMENT_SIGNING("noop")`, `EMAIL("smtp")`, `KYC_VERIFICATION("noop")`, `PAYMENT("noop")`. Each carries a default-adapter slug used when no tenant configuration exists.

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationGuardService.java:25` — `requireEnabled(domain)`. PAYMENT, EMAIL, and KYC_VERIFICATION always return (line 26 — "core infrastructure, not feature-gated"). ACCOUNTING / AI / DOCUMENT_SIGNING are gated by feature flags on `OrgSettings`.

This split is opinionated: payment/email/KYC are considered foundational; if you're a tenant on Kazi, you have all three. Accounting/AI/signing are opt-in. The `IntegrationDisabledException` translates to HTTP 403.

### Per-tenant adapter choice

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/OrgIntegration.java:20` — entity: `domain`, `providerSlug`, `enabled`, `configJson` (jsonb), `keySuffix`. One row per `(tenant, domain)`.

### Encrypted secret storage

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/SecretStore.java` — port interface (`storeSecret`, `getSecret`, `deleteSecret`).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/secret/EncryptedDatabaseSecretStore.java:19` — implementation. Line 23: `ALGORITHM = "AES/GCM/NoPadding"`. Line 38: SecretKeySpec from `app.security.secret-key` config. Persisted in `org_secrets` table per-tenant.

The secret store is database-backed (not Vault, not KMS) — a deliberately small dependency footprint. The encryption key is environment-supplied; rotation is "issue a new env var, run a re-encryption job" (no key-version metadata on rows).

### Adapter resolution

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java:15` — at startup, scans for all `IntegrationAdapter`-annotated Spring beans and builds `Map<IntegrationDomain, Map<String, Object>>` (domain → slug → adapter bean). Line 32 fails fast on duplicate `(domain, slug)`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationRegistry.java:57` — `resolve(domain, portInterface)` reads the current tenant's `OrgIntegration`, looks up the slug, returns the adapter cast to the requested port interface. Falls back to the domain's default slug if no row or no match (lines 80–98).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/integration/IntegrationAdapter.java:18` — annotation: `@IntegrationAdapter(domain=..., slug=...)`. The slug discriminator is what distinguishes `noop` from `xero` for ACCOUNTING.

### Phase 71 Xero adapter

The Xero accounting adapter is the worked example for this whole pattern. It lives under `integration/accounting/` (not yet implemented at the time of A1 — Phase 71 is the next-up phase per the user's project memory). The architecture doc:

`→ architecture/phase71-xero-accounting-integration.md:13` — "All sync orchestration is owned by a dedicated `AccountingSyncService` -- not the Phase 37 rule engine ([ADR-274](../adr/ADR-274-dedicated-accounting-sync-service-not-rule-engine.md))."
`→ architecture/phase71-xero-accounting-integration.md:29` — in-scope: OAuth2 connect/disconnect/refresh, invoice push, customer push, payment pull (polling — ADR-277), sync queue with retry/dead-letter, trust-accounting guard, tax code mapping.
`→ adr/ADR-272-xero-only-accounting-adapter-v1.md` — single accounting adapter is acceptable; YAGNI on the second one.
`→ adr/ADR-275-oauth2-augmentation-org-integration.md` — OAuth2 tokens are stored on `OrgIntegration` (augmenting the entity, not a new sibling table).
`→ adr/ADR-278-idempotent-push-via-external-reference.md` — uses `external_reference` field for idempotent re-push.

### Trust-accounting hard guard (ADR-276)

The integration push pipeline contains a *fail-closed* guard that cannot be bypassed by configuration:

`→ architecture/phase71-xero-accounting-integration.md:296` — "Trust boundary guard -- `TrustBoundaryGuard.evaluate(invoice)` runs. If refused: sync entry created with `state = BLOCKED_TRUST_BOUNDARY`, `last_error_code = TRUST_BOUNDARY`, audit event `integration.xero.push_blocked_trust` emitted."
`→ architecture/phase71-xero-accounting-integration.md:785` — "regulatory safeguard mandated by the Legal Practice Act Section 86 ... deterministic Java code -- no LLM, no AI, no human bypass."
`→ architecture/phase71-xero-accounting-integration.md:793` — three-condition refusal logic: invoice flagged trust (line 793), any line item from a trust account (line 794), customer has active trust balances (line 795). If any DB lookup fails, the guard *refuses* (line 799) — fail-closed.

Cross-link: this is the third defense of trust accounting on top of the module gate (§4) and the capability gate (§2). The guard is invoked **inside the sync service**, not on the public `/api/invoices` controller — i.e. you can still create a trust invoice in Kazi (that's the legal vertical's whole point), you just cannot export it to Xero.

---

## 6. Domain event bus

### `DomainEvent` sealed interface

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java:17` — `public sealed interface DomainEvent permits BudgetThresholdEvent, ...` — line 18 begins a 35-class permit list. Every event is a Java record. Publication uses the standard `ApplicationEventPublisher` (no custom bus, no Kafka, no in-memory queue).

### Universal subscriber: the automation engine

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationEventListener.java:25` — `@Component`. Line 56: `@EventListener public void onDomainEvent(DomainEvent event)` — single method takes any `DomainEvent`, dispatches to the trigger-matching engine. No `@TransactionalEventListener` here — automations need to fire on the in-flight transaction (e.g. for "delay-until-after-task-completes" semantics, the executor itself controls timing).

### Secondary subscribers

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java:50` — listens to ~16 events with `@TransactionalEventListener(phase = AFTER_COMMIT)` semantics. Notifications fan out to in-app + email; if the source transaction rolls back, no notification fires.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalDocumentNotificationHandler.java:114` — `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/notification/PortalEmailNotificationChannel.java:115, :152, :182, :209` — four `@TransactionalEventListener(AFTER_COMMIT)` handlers (acceptance, proposal, document, invoice).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/PortalContactAutoProvisioner.java:42` — `@EventListener` (no transactional phase) — auto-creates a `PortalContact` row when a portal email is mentioned in an outbound flow.

### After-commit pattern (why most listeners use it)

The vast majority of secondary subscribers use `AFTER_COMMIT` because:

1. **Email is irreversible.** If the transaction rolls back after the email is sent, the email already went out. AFTER_COMMIT eliminates that class of bug.
2. **Read-model sync after rollback writes wrong data.** The portal read-model tables (`portal_*` tables in the global schema, populated by listeners) must reflect committed state, not in-flight state.
3. **Audit emission is the one exception.** Audit events are written *in* the same transaction as the change they describe — `AuditService.log(...)` is called directly by services, not via an event listener. The "audit cannot lie about a rolled-back operation" property is achieved by sharing the transaction.

This design is consistent across the codebase. The number of `@EventListener` (no-phase) usages is small (auto-provisioner, automation engine) and each is justified — the automation engine in particular needs in-flight access for delayed-action scheduling.

---

## 7. Observability & operations

This is the **thinnest** cross-cutting concern in the codebase. Most observability is "structured logs to stdout, hope CloudWatch picks them up" — there is no APM integration, no distributed tracing, no per-tenant metrics dashboard.

### Logging

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantLoggingFilter.java:17` — `OncePerRequestFilter`, runs last in the chain.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantLoggingFilter.java:29` — sets MDC fields: `tenantId, userId, memberId, requestId` (UUID per request). Lines 46–49 clear them on response — load-bearing for virtual threads + servlet pool re-use.

`backend/CLAUDE.md` "Observability" section: "Spring Boot structured logging format: ECS (Elastic Common Schema). CloudWatch Logs in production via Fargate `awslogs` driver." — i.e. logs are JSON, but the consumer is unspecified beyond CloudWatch.

### Metrics

Spring Boot Actuator is wired but only `/actuator/health` is broadly exposed:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:113` — `auth.requestMatchers("/actuator/**")` (in the staff chain — accessible).
`→ gateway/src/main/java/io/b2mash/b2b/gateway/config/GatewaySecurityConfig.java:49` — `/actuator/health` is `permitAll`. No other actuator endpoints are exposed through the gateway.

There is no `MeterRegistry` customization, no Prometheus scrape endpoint configured for production, no per-tenant counter. The tier above (the Fargate / Neon dashboards) is presumed.

### Health checks

`/actuator/health` on the backend (port 8080), `/actuator/health` on the gateway (port 8443). Both use Spring Boot Actuator's default health indicators (DataSource, Mail, etc.). Neither has a custom `HealthIndicator` for tenant-aware checks (e.g. "all tenant schemas are reachable") — this is a gap.

### Background job pattern

Schedulers are scattered across packages and follow a uniform pattern: `@Scheduled` + `TenantScopedRunner.forEachTenant(...)`. There is no shared base class — every scheduler reinvents the loop:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java:61` — `@Scheduled(fixedDelay = POLL_INTERVAL_MS)` (delayed-action poll, 15min).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/AutomationScheduler.java:78` — `@Scheduled(fixedDelay = CRON_POLL_INTERVAL_MS)` (cron trigger poll, 60s).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/automation/FieldDateScannerJob.java:60` — `@Scheduled(cron = "0 0 6 * * *")`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/proposal/ProposalExpiryProcessor.java:57` — hourly fixed-rate.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/acceptance/AcceptanceExpiryProcessor.java:27` — hourly fixed-delay.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billing/SubscriptionExpiryJob.java:54` — three cron jobs (3am, 3:05am, 3:10am) for subscription lifecycle.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/compliance/DormancyScheduledJob.java:38` — daily 2am.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkCleanupService.java:34` — hourly.

### Reaper jobs

ADR-271 ("scheduled trigger extension") introduces the pattern of *reaper* jobs that clean up invocation/invocation-related rows that have aged out. Two examples:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/invocation/AiInvocationExpirySweeper.java:64` — `@Scheduled(cron = "0 0 3 * * *")`. Sweeps expired AI invocations.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/assistant/invocation/AiInvocationReaper.java` — companion reaper for invocation result rows.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/portal/MagicLinkCleanupService.java:34` — magic-link tokens past `expiresAt` are deleted hourly.

The reaper pattern is a deliberate ADR-271 decision: *don't* cascade-delete when a parent row goes; let an idempotent scheduled sweep handle it. This makes ad-hoc backfills cheap.

---

## 8. Data protection / retention

### Anonymization-over-deletion (ADR-062)

The system never hard-deletes a customer or project once it has been ACTIVE — instead, it transitions through `OFFBOARDING → OFFBOARDED → ANONYMIZED`. The `ANONYMIZED` state replaces PII fields with placeholders while retaining structural rows for audit linkage:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataAnonymizationService.java` — implements the anonymization sweep.
`→ frontend/lib/types/customer.ts:6` — `LifecycleStatus = "PROSPECT|ONBOARDING|ACTIVE|DORMANT|OFFBOARDING|OFFBOARDED|ANONYMIZED"` (A2 §4).

### Retention clocks

Projects carry an explicit retention clock that starts on closure, not creation:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:97` — `private Instant retentionClockStartedAt;`.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:259` — set on `complete()` (transition to COMPLETED if not already set).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java:303` — set on `close()`. Line 311: "deliberately preserves `retentionClockStartedAt` — the canonical anchor is the earliest …" (ADR-249).

The reopen flow preserves the original clock — re-completing a previously-closed project does not restart retention. This is the "you cannot un-stamp time" rule that compliance leans on.

### DSAR endpoints, PAIA jurisdiction

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/DataRequestController.java` — DSAR submission/status endpoints.
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/PaiaManualGenerationService.java` — PAIA manual generation (ZA jurisdiction).
`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/JurisdictionDefaults.java` — per-jurisdiction retention defaults. ZA is the only fully-implemented jurisdiction; others fall through to a generic default set.

The frontend exposes this at `frontend/app/(app)/org/[slug]/customers/[id]` (data-protection tab) and on `frontend/app/(app)/org/[slug]/settings/compliance/page.tsx`.

### Encrypted secret storage

Cross-link to §5: secrets for integration adapters live in the `org_secrets` per-tenant table, encrypted with AES-GCM via `EncryptedDatabaseSecretStore` (`integration/secret/EncryptedDatabaseSecretStore.java:19`). DSAR exports include the *fact of* a connected integration but not its credentials — the export service explicitly redacts `org_secrets`.

---

**End of A6.** Next-step companion docs: A7 should turn each section into a target-state architecture proposal; A8 should identify the largest gaps (notably observability, §7) and prioritise remediation.
