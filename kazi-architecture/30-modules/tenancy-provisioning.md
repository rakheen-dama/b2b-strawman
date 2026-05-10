# Tenancy & Provisioning

**Bounded context:** see [`10-bounded-contexts.md` § tenancy-provisioning](../10-bounded-contexts.md).

## Purpose

Schema-per-tenant isolation, JIT tenant provisioning, and the Java 25 ScopedValue carriers used by every request and every background job. This module is the multi-tenancy mechanism — Hibernate hooks (`MultiTenantConnectionProvider`, `CurrentTenantIdentifierResolver`), the entry-binding `TenantFilter`, the iteration primitive `TenantScopedRunner`, and the Flyway-driven provisioning pipeline that materialises a fresh `tenant_<12-hex>` schema with vertical profile + packs wired in. Every other context depends on it (transitively or directly) — the foundation under everything.

## Entities owned

Both shared / public schema. There are no tenant-scoped entities here — by definition this module establishes the tenant boundary, it does not live inside one.

- `Organization` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/Organization.java:16` — public-schema row representing a tenant. Carries `externalOrgId` (Keycloak/Clerk `o.id`), `name`, and a `ProvisioningStatus` state machine (`PENDING → IN_PROGRESS → COMPLETED|FAILED`) `→ provisioning/Organization.java:95`.
- `OrgSchemaMapping` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMapping.java:14` — public-schema lookup row mapping `externalOrgId → schemaName`. Read on every request by `TenantFilter` to resolve the tenant; iterated by `TenantScopedRunner` for cross-tenant jobs.

## REST surface

API-key secured (`X-API-KEY` header on `/internal/*`, validated by `ApiKeyAuthFilter`); never exposed through the gateway BFF. Provisioning is server-to-server only — invoked by the access-request approval pipeline, the platform-admin demo controller, and integration tests.

- `POST /internal/orgs/provision` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/ProvisioningController.java:16` — single endpoint; delegates straight to `TenantProvisioningService.provisionTenant(...)`.

There is also a thin `OrgController` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/OrgController.java:16` at `POST /api/orgs` guarded by `@PreAuthorize("@platformSecurityService.isPlatformAdmin()")`. It exists for the platform-admin "create org" path and wraps `OrgProvisioningService.createOrg(...)` (which itself calls into `TenantProvisioningService`).

## Frontend pages / components

Provisioning is admin-driven; there is no self-serve sign-up. End-user-facing surfaces are minimal:

- `frontend/app/(app)/create-org/page.tsx:8` — Keycloak-mode landing for users without an org. Platform admins are redirected to `/platform-admin/access-requests`; mock-auth users skip straight to the e2e test org. The page does **not** call `/internal/orgs/provision` directly — it surfaces the access-request flow, which the platform admin then approves.
- The actual provision trigger lives server-side in `AccessRequestApprovalService` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/accessrequest/AccessRequestApprovalService.java:108` — calls `tenantProvisioningService.provisionTenant(...)` after Keycloak org creation.
- Settings page at `frontend/app/(app)/org/[slug]/settings/` exposes existing-tenant configuration (vertical profile, branding, modules) but does not create new tenants.

## Domain events

_None._ Provisioning predates the `DomainEvent` bus and remains imperative — the pipeline is a script, not a state machine, and nothing else needs to react. Audit emission happens directly via `auditService.log(...)` calls inside the service (consistent with the rest of the audit pattern — see [`20-cross-cutting/audit-and-compliance.md`](../20-cross-cutting/audit-and-compliance.md)).

## Cross-cutting touchpoints

This module **is** the multi-tenancy mechanism — see the synthesis in [`20-cross-cutting/multitenancy.md`](../20-cross-cutting/multitenancy.md) for the end-to-end view. Anchors below:

- **Entry binding (servlet filter):** `TenantFilter` extracts `o.id` from the validated JWT, looks up the schema via `OrgSchemaMappingRepository`, and binds `RequestScopes.TENANT_ID` + `RequestScopes.ORG_ID` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java:25`. Runs after `BearerTokenAuthenticationFilter`, before `MemberFilter` — order is load-bearing per [`20-cross-cutting/auth-and-rbac.md`](../20-cross-cutting/auth-and-rbac.md).
- **ScopedValue carriers:** `RequestScopes` holds nine ScopedValues (`TENANT_ID, MEMBER_ID, ORG_ID, ORG_ROLE, CUSTOMER_ID, PORTAL_CONTACT_ID, AUTOMATION_EXECUTION_ID, CAPABILITIES, GROUPS`) `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java:23`. The sanctioned binding helpers are `runForTenant(...)` `→ RequestScopes.java:173` and `runForMember(...)` `→ RequestScopes.java:200`; direct `ScopedValue.where(...)` outside this class is discouraged.
- **Hibernate hooks:** `SchemaMultiTenantConnectionProvider` implements `MultiTenantConnectionProvider<String>` and runs `SET search_path TO <schema>` on connection checkout, resetting to `public` on release `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java:13`. `TenantIdentifierResolver` implements `CurrentTenantIdentifierResolver<String>` and reads `RequestScopes.TENANT_ID` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantIdentifierResolver.java:7`. Both are wired in `HibernateMultiTenancyConfig` — `hibernate.multiTenancy` is **not** set; Hibernate 7 auto-detects from the registered provider (per `backend/CLAUDE.md` anti-patterns).
- **Background-job iteration:** `TenantScopedRunner.forEachTenant(BiConsumer<tenantId, orgId>)` is the single primitive used by every `@Scheduled` job — it iterates `OrgSchemaMappingRepository.findAll()`, binds `TENANT_ID + ORG_ID` per iteration via `RequestScopes.runForTenant(...)`, and isolates per-tenant exceptions so one bad tenant doesn't abort the run `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java:37`.
- **Cross-schema provisioning helper:** `TenantTransactionHelper` provides programmatic `TransactionTemplate` access for the multi-step pipeline that needs to write to both `public` and the new tenant schema in different transactions `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantTransactionHelper.java`.
- **Idempotent pipeline:** `TenantProvisioningService.provisionTenant(...)` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:140` runs Flyway tenant migrations (`classpath:db/migration/tenant`, line 209), sets vertical profile (line 172), installs document-template packs (line 177), installs automation-template packs (line 182), and runs the reconciliation seeder (line 189). Annotated `@Retryable` (line 137); each step is idempotent (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`) so retries and partial-failure restarts are safe — see ADR-T007.
- **Late-migration runner:** `TenantMigrationRunner` (`ApplicationRunner`) re-runs Flyway against every existing tenant schema at backend startup, so DDL added after a tenant was provisioned still lands `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantMigrationRunner.java:16`.
- **Pack reconciliation runner:** `PackReconciliationRunner` (also `ApplicationRunner`) re-applies `PackInstaller` content to existing tenants on startup so newly-shipped pack updates flow in `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PackReconciliationRunner.java:40`.
- **Schema naming:** deterministic 12-hex hash of the external org id `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/SchemaNameGenerator.java:6` — same input always yields the same schema name, which is what makes provisioning safe to retry.

## Vertical specifics

The vertical profile is selected at provisioning time. `TenantProvisioningService.provisionTenant(clerkOrgId, orgName, verticalProfile, country)` `→ provisioning/TenantProvisioningService.java:140` is the single entry point that wires profile + packs + reconciler:

1. Resolve currency from `country` (or fall back to profile defaults).
2. `setVerticalProfile(...)` writes `OrgSettings.verticalProfile`, `enabledModules`, and `terminologyNamespace` from the JSON profile registry `→ TenantProvisioningService.java:240`.
3. `installPacksViaPipeline(...)` installs universal `DOCUMENT_TEMPLATE` packs first, then profile-specific ones `→ TenantProvisioningService.java:177`.
4. Same for `AUTOMATION_TEMPLATE` packs (line 182).
5. `verticalProfileReconciliationSeeder.reconcile(schemaName, clerkOrgId)` merges the profile's `enabledModules` into the tenant's settings (idempotent — safe to re-run on profile change) `→ TenantProvisioningService.java:189`.

Cross-link to [`30-modules/vertical-profiles.md`](./vertical-profiles.md) for the profile registry and reconciliation seeder, and to [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md) for the per-vertical pack inventory. Vertical-specific *content* (which packs ship for legal-za, what FICA fields look like, etc.) lives there; this module owns the *mechanism* that consumes it.

## Active ADRs

- **ADR-T001** (schema-per-tenant over row-level isolation) — establishes the dedicated-schema strategy as the template default; superseded by ADR-064 in the Kazi codebase but the foundational reasoning is unchanged.
- **ADR-T002** (ScopedValues over ThreadLocal) — Java 25 `ScopedValue` (JEP 506) replaces `ThreadLocal` for request-scoped context; required for virtual-thread safety.
- **ADR-T007** (idempotent provisioning pipeline) — every step (Flyway migration, Keycloak org creation, `OrgSchemaMapping` insert, pack install, reconciliation) must be safe to retry; partial failures don't poison the row.
- **ADR-T008** (`TenantScopedRunner` canonical API) — single primitive for binding tenant scope outside a request; replaces 13+ inline `for (mapping : repo.findAll()) { ScopedValue.where(...).run(...) }` blocks.
- **ADR-064** (dedicated schema for all tenants) — supersedes the earlier tiered ADR-011/ADR-012/ADR-015. Every tenant — regardless of plan — gets a dedicated `tenant_<hash>` schema. There is no shared schema, no row-level isolation, no `tenant_id` columns. This collapses a compounding maintenance tax that the dual-mode strategy had imposed.

## Key flows

- **Tenant onboarding (provisioning):** see [`50-flows/pack-install-and-vertical-onboarding.md`](../50-flows/pack-install-and-vertical-onboarding.md) for the end-to-end sequence from access-request approval → Keycloak org creation → `provisionTenant(...)` → pack install → reconciliation seeder.
- **Per-request tenant resolution:** JWT → `TenantFilter` → `OrgSchemaMappingRepository.findByExternalOrgId(...)` → `RequestScopes.TENANT_ID` bound → all Hibernate queries route to `tenant_<hash>` via `search_path`. Anchored in [`20-cross-cutting/multitenancy.md`](../20-cross-cutting/multitenancy.md).
- **Background-job iteration:** `@Scheduled` method → `tenantScopedRunner.forEachTenant((tenantId, orgId) -> ...)` → per-tenant `RequestScopes.runForTenant(...)` carrier → exception-isolated execution. Used by `AutomationScheduler`, `DormancyScheduler`, `SubscriptionEnforcementScheduler`, `TimeReminderScheduler`, `AcceptanceExpiryProcessor`, `ProposalExpiryProcessor`, `InformationRequestReminderScheduler`, `FieldDateScannerScheduler`, et al.

## Open questions / known fragility

- **Profile-switch leaves orphaned data.** `VerticalProfileReconciliationSeeder.reconcile(...)` only *adds* modules to `enabledModules` — it never removes them, and it never uninstalls packs from the previous profile. Switching a tenant from `legal-za` back to `consulting-generic` leaves trust-accounting tables, legal document templates, FICA checklist instances, and tariff data installed (but module-gated, so unreachable from UI). Profile change is therefore **one-way-safe but not reversible-clean**. Documented in `_discovery/A6-cross-cutting.md` §4 and on the [`10-bounded-contexts.md`](../10-bounded-contexts.md) §4 multi-vertical seam. No remediation ADR yet — the reverse-reconciliation problem touches `packs` SPI semantics (`uninstall` is defined but rarely safe to call live) and `OrgSettings` audit (you'd want to record what was removed).
- **No tenant-deletion path.** `Organization.markFailed()` / `markCompleted()` exists but no service offers tenant *removal*. Soft-delete via `ProvisioningStatus` would orphan the `tenant_<hash>` schema; hard-delete needs a Flyway-aware drop sequence that doesn't exist. Currently a `(no code anchor — gap)` situation.
- **`TenantMigrationRunner` runs on every backend startup against every tenant.** Boot time scales linearly with tenant count. Fine at template-product scale; will need rethinking past ~100 active tenants. Tracked informally — no ADR.
