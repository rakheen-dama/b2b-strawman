# Multi-tenancy

## What this concern covers

Schema-per-tenant isolation — the load-bearing assumption under every backend service. Each tenant gets a dedicated `tenant_<12-hex>` Postgres schema; isolation is enforced exclusively at the connection level via `search_path`. There are no shared tenant tables and no `tenant_id` columns on domain entities (ADR-064). At the runtime layer, a Java 25 `ScopedValue` carrier (`RequestScopes.TENANT_ID`) propagates the active tenant through the call stack; Hibernate hooks read that carrier on every connection checkout and switch the session's schema accordingly. Servlet filters bind the carrier on inbound HTTP; the `TenantScopedRunner` re-binds it for `@Scheduled` jobs. The provisioning pipeline materialises the schema (Flyway DDL) and reconciles vertical-profile content (packs) idempotently. This page synthesises *how the mechanism is used by every other module*; the module-internal detail lives in [`30-modules/tenancy-provisioning.md`](../30-modules/tenancy-provisioning.md).

## The end-to-end flow

The path of a tenant context, from JWT to query:

1. **HTTP request lands** — staff requests arrive via the gateway BFF (`/api/**`, `TokenRelay=` filter attaches the OAuth2 access token as `Authorization: Bearer <jwt>`); portal requests bypass the gateway and hit the backend directly. Mock-auth (E2E stack on port 3001) produces a Spring-shaped JWT through the same filter chain. The gateway is intentionally *transparent* on tenancy — no tenant header is forwarded; the backend resolves tenancy itself `→ gateway/src/main/resources/application.yml:43`.
2. **JWT validated by Spring Security** — `BearerTokenAuthenticationFilter` runs first; produces a `JwtAuthenticationToken`. The Clerk/Keycloak nested `o.id` claim survives into the request `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java:140`.
3. **`TenantFilter` binds the carrier** — extracts `o.id` via `JwtUtils.extractOrgId(jwt)`, looks the schema up through `OrgSchemaMappingRepository`, and binds `RequestScopes.TENANT_ID` + `RequestScopes.ORG_ID` for the duration of the request. Direct header-driven tenant resolution is intentionally absent — there is no "trust the client" path `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java:50`.
4. **Downstream filters bind dependent carriers** — `MemberFilter` reads `TENANT_ID` (indirectly, via Hibernate which now routes to the tenant schema) to JIT-sync the `Member` row, then binds `MEMBER_ID` + `ORG_ROLE` + `CAPABILITIES`. Order is load-bearing — see [`auth-and-rbac.md`](./auth-and-rbac.md).
5. **Hibernate reads the carrier on connection checkout** — when a service issues a query, Hibernate calls `TenantIdentifierResolver.resolveCurrentTenantIdentifier()` which returns `RequestScopes.TENANT_ID.get()` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantIdentifierResolver.java:7`. It then asks `SchemaMultiTenantConnectionProvider.getConnection(tenantId)`, which executes `SET search_path TO <schema>` on the JDBC connection before handing it to the session `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java:106`.
6. **JPA queries auto-route** — every `Repository` call, every `EntityManager` query, every native SQL statement now sees the tenant schema first on the search path; cross-schema entities (Organization, OrgSchemaMapping, Subscription, AccessRequest) resolve via the `public.<table>` qualifier on their `@Table` annotations, which still works because `public` is on the search path after the tenant schema.
7. **Connection released, schema reset** — on release, the provider runs `SET search_path TO public` so a pooled connection cannot leak a stale tenant scope to the next checkout `→ SchemaMultiTenantConnectionProvider.java:112`.
8. **Scheduled jobs re-bind per tenant** — `@Scheduled` methods do not run inside an HTTP filter chain; instead they call `tenantScopedRunner.forEachTenant((tenantId, orgId) -> ...)`, which iterates `OrgSchemaMappingRepository.findAll()` and re-binds `RequestScopes.TENANT_ID` + `RequestScopes.ORG_ID` for each tenant via `RequestScopes.runForTenant(...)`. Per-tenant exceptions are isolated so one bad tenant does not abort the run `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java:37`.

## Components and their roles

| Component | Path | Role |
|---|---|---|
| `RequestScopes` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/RequestScopes.java:23` | Holds nine `ScopedValue<String>` carriers: `TENANT_ID, MEMBER_ID, ORG_ID, ORG_ROLE, CUSTOMER_ID, PORTAL_CONTACT_ID, AUTOMATION_EXECUTION_ID, CAPABILITIES, GROUPS`. Sanctioned binding helpers `runForTenant(...)` `→ RequestScopes.java:147` and `runForMember(...)` `→ RequestScopes.java:200` are the only sanctioned entry points; raw `ScopedValue.where(...)` outside this class is discouraged in `backend/CLAUDE.md`. |
| `TenantFilter` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantFilter.java:25` | Servlet filter — extracts `o.id` from the validated JWT, resolves it through `OrgSchemaMappingRepository`, binds `TENANT_ID` + `ORG_ID`. Runs after `BearerTokenAuthenticationFilter`, before `MemberFilter`. |
| `SchemaMultiTenantConnectionProvider` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/SchemaMultiTenantConnectionProvider.java:13` | Hibernate `MultiTenantConnectionProvider<String>` hook. Runs `SET search_path TO <schema>` on connection checkout (line 106) and resets to `public` on release (line 112). |
| `TenantIdentifierResolver` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantIdentifierResolver.java:7` | Hibernate `CurrentTenantIdentifierResolver<String>` hook. Single-line implementation that returns `RequestScopes.TENANT_ID.get()`. |
| `HibernateMultiTenancyConfig` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/HibernateMultiTenancyConfig.java:14` | Wires both hooks via `MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER` / `MULTI_TENANT_IDENTIFIER_RESOLVER`. Note: `hibernate.multiTenancy` is **not** set — Hibernate 7 auto-detects from the registered provider (Spring Boot 4 nuance, called out in `backend/CLAUDE.md` anti-patterns). |
| `OrgSchemaMapping` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/OrgSchemaMapping.java:14` | Public-schema entity: `externalOrgId → schemaName`. Read on every request by `TenantFilter`; iterated by `TenantScopedRunner`. |
| `TenantScopedRunner` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantScopedRunner.java:37` | `forEachTenant(BiConsumer<tenantId, orgId>)` — single iteration primitive used by every `@Scheduled` job. Per-iteration exception isolation so one bad tenant does not abort the run. |
| `TenantTransactionHelper` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantTransactionHelper.java` | Programmatic `TransactionTemplate` access for the multi-step provisioning pipeline that needs to write to both `public` and a new tenant schema in different transactions. |
| `TenantProvisioningService` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java:140` | Idempotent pipeline — Flyway tenant migrations (line 209), set vertical profile (line 172), install document-template + automation-template packs (lines 177, 182), reconcile profile (line 189). `@Retryable`. |
| `TenantMigrationRunner` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantMigrationRunner.java:16` | `ApplicationRunner` — re-runs Flyway against every existing tenant schema at backend startup so post-provisioning DDL still lands. |
| `SchemaNameGenerator` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/SchemaNameGenerator.java:6` | Deterministic 12-hex hash of `externalOrgId` — same input always yields the same schema name. The basis of provisioning's retry-safety. |
| `TenantLoggingFilter` | `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantLoggingFilter.java:17` | Last filter in the chain — sets MDC fields (`tenantId, userId, memberId, requestId`) and clears them on response (load-bearing for virtual threads + servlet pool reuse). |

## Provisioning idempotency

Every step of the provisioning pipeline is idempotent so retries and partial-failure restarts are safe (ADR-T007):

- **Schema name is deterministic.** `SchemaNameGenerator` hashes the `externalOrgId` to a 12-hex suffix `→ provisioning/SchemaNameGenerator.java:6`; the same input always produces the same `tenant_<hash>`, so re-running provisioning never creates a stranded second schema.
- **Flyway migrations use `IF NOT EXISTS` / `ON CONFLICT DO NOTHING`.** Both the public-schema migrations (`backend/src/main/resources/db/migration/global/`, run once at startup) and per-tenant migrations (`backend/src/main/resources/db/migration/tenant/`, run at provisioning) are written so re-running them against an already-migrated schema is a no-op.
- **`OrgSchemaMapping` insert is upsert-shaped.** A second provision attempt for the same `externalOrgId` finds the row and proceeds; it does not duplicate.
- **Pack installs are idempotent at the SPI contract.** `PackInstaller.install(packId, tenantId, memberId)` either no-ops if the `pack_install` row already exists, or merges new content into existing rows — see [`30-modules/tenancy-provisioning.md`](../30-modules/tenancy-provisioning.md) for the SPI surface.
- **Reconciliation-on-boot.** `TenantMigrationRunner` re-runs Flyway against every tenant on startup `→ provisioning/TenantMigrationRunner.java:16`; `PackReconciliationRunner` re-applies pack content `→ provisioning/PackReconciliationRunner.java:40`. Late-arriving DDL and pack updates flow into existing tenants without manual intervention.
- **`@Retryable` on the orchestrator.** `TenantProvisioningService.provisionTenant(...)` is annotated `@Retryable` `→ provisioning/TenantProvisioningService.java:137` precisely because the steps it orchestrates are individually safe to re-run.

## Modules affected

Every module operates inside an active `TENANT_ID` scope. The mechanism is consumed transparently — services read `RequestScopes.TENANT_ID` directly only when they need to log or branch on it; otherwise Hibernate handles it. The exceptions:

- [`30-modules/tenancy-provisioning.md`](../30-modules/tenancy-provisioning.md) — owns the mechanism itself; the module-internal source for everything on this page.
- [`30-modules/identity-access.md`](../30-modules/identity-access.md) — `MemberFilter` runs after `TenantFilter` and depends on the tenant scope being bound before it can JIT-sync the `Member` row.
- [`30-modules/platform-administration.md`](../30-modules/platform-administration.md) — operates on the **public** schema (Organization, AccessRequest, Subscription) rather than within a tenant scope. The platform-admin filter chain explicitly does not require `TENANT_ID`.

Cross-link: [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md) (consumed at provisioning), [`50-flows/pack-install-and-vertical-onboarding.md`](../50-flows/pack-install-and-vertical-onboarding.md) (the end-to-end onboarding sequence).

## Active ADRs

- **ADR-T001** — Schema-per-tenant over row-level isolation. Establishes dedicated-schema as the template default.
- **ADR-T002** — Java 25 `ScopedValue` (JEP 506) over `ThreadLocal`. Required for virtual-thread safety; carriers live in `RequestScopes`.
- **ADR-T007** — Idempotent provisioning pipeline. Every step (Flyway, OrgSchemaMapping insert, pack install, reconciliation) must be safe to retry.
- **ADR-T008** — `TenantScopedRunner` as the canonical iteration API. Replaces 13+ inline `for (mapping : repo.findAll()) { ScopedValue.where(...).run(...) }` blocks.
- **ADR-064** — Dedicated schema for *every* tenant — regardless of plan. Supersedes ADR-011/012/015 which had a tiered shared/dedicated split. Collapses the dual-mode maintenance tax.

## Known fragilities / open questions

- **Profile switch leaves orphaned data.** `VerticalProfileReconciliationSeeder.reconcile(...)` only adds modules to `enabledModules`; it never removes them, and never uninstalls packs from the previous profile. Switching `legal-za → consulting-generic` leaves trust-accounting tables, legal templates, FICA checklist instances installed (module-gated, so unreachable from UI). Profile change is one-way-safe but not reversible-clean. See [`30-modules/vertical-profiles.md`](../30-modules/vertical-profiles.md) and `_discovery/A6-cross-cutting.md` §4.
- **ScopedValue re-bind required for non-blocking work.** Virtual threads inherit `ScopedValue` bindings only at fork time. SSE streams, `CompletableFuture` chains spawned outside the request, and any work submitted to an `Executor` after the request returns must explicitly re-bind via `RequestScopes.runForTenant(...)`. The AI assistant's streaming response loop is the canonical example — see [`30-modules/ai-assistant.md`](../30-modules/ai-assistant.md).
- **No per-schema row count or quota enforcement.** Nothing prevents a single tenant from filling the disk. ADR-064's "every tenant gets a dedicated schema" decision pushed quota concerns one layer down (Postgres-level) and they are currently unaddressed.
- **No tenant deletion path.** `Organization.markFailed()` / `markCompleted()` exist but no service offers tenant *removal*; soft-delete via `ProvisioningStatus` would orphan the `tenant_<hash>` schema; hard-delete needs a Flyway-aware drop sequence that does not exist `(no code anchor — gap)`.
- **`TenantMigrationRunner` boot time scales linearly with tenant count.** Re-runs Flyway against every tenant schema on every backend startup `→ provisioning/TenantMigrationRunner.java:16`. Fine at template-product scale; will need rethinking past ~100 active tenants. No ADR.
- **Cross-tenant queries are impossible by design — a feature, not a bug.** Hibernate's session is bound to one schema for its lifetime; there is no JPA path that joins across two tenant schemas. Cross-tenant analytics (the platform-admin "all-orgs revenue" view) are only possible against public-schema projection tables. This is the right answer for isolation but it does mean any cross-tenant feature must be funded with a projection table — never a "just join" shortcut.
