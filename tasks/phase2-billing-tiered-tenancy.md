# Phase 2 — Billing & Tiered Tenancy

Phase 2 introduces a tiered tenancy model powered by Clerk Billing. Organizations on the free **Starter** plan share a single `tenant_shared` schema with row-level isolation. Organizations on the paid **Pro** plan retain the existing schema-per-tenant model. See architecture/ARCHITECTURE.md §9 for the full design and [ADR-010](adr/ADR-010-billing-integration.md)–[ADR-016](adr/ADR-016-tier-upgrade-migration.md) for decision records.

---

### Epic 23: Tier Data Model & Plan Sync

**Goal**: Extend the `Organization` entity with tier awareness, add supporting value types, and wire Clerk Billing subscription webhooks to propagate plan state to the backend. This is the foundation all other Phase 2 epics build on.

**References**: [ADR-010](adr/ADR-010-billing-integration.md), [ADR-013](adr/ADR-013-plan-state-propagation.md), architecture/ARCHITECTURE.md §9.1, §9.3, §9.4

**Dependencies**: None (builds on existing Phase 1 infrastructure)

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: M

#### Slices

| Slice | Tasks     | Summary                                                                                                                         | Status |
|-------|-----------|---------------------------------------------------------------------------------------------------------------------------------|--------|
| **23A** | 23.1–23.5 | Backend data model: Tier enum, Organization changes, V4 global migration, supporting types, internal endpoint, cache eviction   | Done (PR #44) |
| **23B** | 23.6–23.7 | Plan sync pipeline: webhook handlers, tests                                                                                     | Done (PR #45) |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 23.1 | Create tier-related value types | 23A   | Done | `provisioning/Tier.java` — enum (`STARTER`, `PRO`). `multitenancy/TenantInfo.java` — record (`String schemaName`, `Tier tier`); replaces plain `String` in TenantFilter cache. `provisioning/PlanLimits.java` — utility class with tier limit constants (`STARTER_MAX_MEMBERS = 2`, `PRO_MAX_MEMBERS = 10`). `exception/PlanLimitExceededException.java` — extends `ResponseStatusException` with HTTP 403, includes upgrade prompt message in body. Per ADR-014 §Enforcement. |
| 23.2 | Add tier + planSlug to Organization entity and V4 global migration | 23A   | Done | `Organization.java`: add `tier` (`Tier` enum, NOT NULL DEFAULT STARTER, `@Enumerated(EnumType.STRING)`) and `planSlug` (`String`, nullable). Create `db/migration/global/V4__add_org_tier.sql`: `ALTER TABLE organizations ADD COLUMN tier VARCHAR(20) NOT NULL DEFAULT 'STARTER'; ALTER TABLE organizations ADD COLUMN plan_slug VARCHAR(100);`. Per architecture/ARCHITECTURE.md §9.3 global schema additions. |
| 23.3 | Add ORG_ID ScopedValue to RequestScopes | 23A   | Done | Add `public static final ScopedValue<String> ORG_ID = ScopedValue.newInstance();` to `multitenancy/RequestScopes.java`. Carries the Clerk org ID for row-level filtering in the shared schema. Consumed by SharedTenantFilterAspect (Epic 24), TenantAwareEntityListener (Epic 24), and SchemaMultiTenantConnectionProvider (Epic 24). Per ADR-012 and architecture/ARCHITECTURE.md §9.5.3. |
| 23.4 | Create PlanSyncController | 23A   | Done | `provisioning/PlanSyncController.java` — `POST /internal/orgs/plan-sync` with `PlanSyncRequest(String clerkOrgId, String planSlug)`. Looks up `Organization` by `clerkOrgId`. Derives tier from planSlug (`"pro"` → `PRO`, anything else → `STARTER`). Updates `tier` and `planSlug`, persists. Evicts TenantFilter cache (task 23.5). Returns 200 OK. If org not found, 404. Secured by `ApiKeyAuthFilter` (existing). Per architecture/ARCHITECTURE.md §9.4 flow diagram. |
| 23.5 | Add TenantFilter cache eviction support | 23A   | Done | Add `evictSchema(String clerkOrgId)` method to `TenantFilter`. Removes the cached entry for the given org so the next request fetches fresh `TenantInfo` from the database. Called by `PlanSyncController` after updating tier. Also called by `TenantUpgradeService` (Epic 27) after schema mapping changes. Per ADR-016 §Cache Invalidation. |
| 23.6 | Add subscription webhook handlers in frontend | 23B   | Done | In `lib/webhook-handlers.ts`: add handlers for `subscription.created` and `subscription.updated` events. Extract `org_id` and plan slug from the Clerk subscription event payload. Call `POST /internal/orgs/plan-sync` via `internalApiClient` with `{clerkOrgId, planSlug}`. Follow existing event handler patterns (fire-and-forget error handling, 200 OK). Add event types to `routeWebhookEvent()` dispatch. Per architecture/ARCHITECTURE.md §9.4 and §9.8.1. |
| 23.7 | Add plan sync tests | 23B   | Done | **Backend**: `PlanSyncIntegrationTest.java` — plan update persists tier + planSlug, unknown org returns 404, cache eviction occurs on update, API key required (401 without key). **Frontend**: Update `webhook-handlers.test.ts` — `subscription.created` routes to plan-sync handler, `subscription.updated` routes to plan-sync handler, payload extraction verified. |

#### Key Files

**Create:**
- `backend/src/main/java/.../provisioning/Tier.java`
- `backend/src/main/java/.../multitenancy/TenantInfo.java`
- `backend/src/main/java/.../provisioning/PlanLimits.java`
- `backend/src/main/java/.../exception/PlanLimitExceededException.java`
- `backend/src/main/java/.../provisioning/PlanSyncController.java`
- `backend/src/main/resources/db/migration/global/V4__add_org_tier.sql`
- `backend/src/test/java/.../provisioning/PlanSyncIntegrationTest.java`

**Modify:**
- `backend/src/main/java/.../provisioning/Organization.java` — Add `tier` + `planSlug` fields
- `backend/src/main/java/.../multitenancy/RequestScopes.java` — Add `ORG_ID`
- `backend/src/main/java/.../multitenancy/TenantFilter.java` — Add `evictSchema()` method
- `frontend/lib/webhook-handlers.ts` — Add subscription event handlers
- `frontend/__tests__/webhook-handlers.test.ts` — Add subscription event tests

---

### Epic 24: Shared Schema & Row-Level Isolation

**Goal**: Implement the shared-schema tenant model for Starter-tier orgs with dual-layer isolation: Hibernate `@Filter` for application-level row filtering and Postgres RLS as defense-in-depth. Update the filter chain and provisioning flow for tier-aware resolution.

**References**: [ADR-011](adr/ADR-011-tiered-tenancy.md), [ADR-012](adr/ADR-012-row-level-isolation.md), [ADR-015](adr/ADR-015-provisioning-per-tier.md), architecture/ARCHITECTURE.md §9.2, §9.3, §9.5.1, §9.5.3

**Dependencies**: Epic 23

**Scope**: Backend

**Estimated Effort**: L

#### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **24A** | 24.1–24.4 | Database migrations, entity annotations, entity listener, `tenant_shared` bootstrap | Done |
| **24B** | 24.5–24.8 | Filter chain: TenantFilterTransactionManager, connection provider, TenantFilter tier resolution, provisioning simplification | Done |
| **24C** | 24.9–24.11 | Integration tests: Starter row isolation, mixed Starter+Pro coexistence, existing test compatibility | Done |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 24.1 | Create V7 tenant migration | 24A | Done | `db/migration/tenant/V7__add_tenant_id_for_shared.sql`: Add nullable `tenant_id VARCHAR(255)` column to `projects`, `documents`, `members`, `project_members` (using `IF NOT EXISTS`). Create indexes (`idx_projects_tenant_id`, `idx_documents_tenant_id`, `idx_members_tenant_id`, `idx_project_members_tenant_id`). Enable RLS on all four tables. Create RLS policies using `current_setting('app.current_tenant', true)` with `OR tenant_id IS NULL` guard (allows Pro schemas where `tenant_id` is NULL). Per architecture/ARCHITECTURE.md §9.3 SQL listing. |
| 24.2 | Add tenantId + @FilterDef/@Filter to all tenant entities | 24A | Done | Annotate `Project`, `Document`, `Member`, `ProjectMember` with `@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))` and `@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")`. Add `@Column(name = "tenant_id") private String tenantId` field to each. Register `TenantAwareEntityListener` via `@EntityListeners`. Per ADR-012 §Hibernate Layer and architecture/ARCHITECTURE.md §9.8.2 entity annotation pattern. |
| 24.3 | Create TenantAwareEntityListener | 24A | Done | `multitenancy/TenantAwareEntityListener.java` — JPA `@EntityListener` with `@PrePersist` callback. If `RequestScopes.TENANT_ID.isBound()` and value equals `"tenant_shared"`, sets `entity.tenantId = RequestScopes.ORG_ID.get()`. No-op for dedicated schemas (tenantId stays null). Requires entities to have a `setTenantId()` method or use a shared interface. Per architecture/ARCHITECTURE.md §9.8.2. |
| 24.4 | Bootstrap `tenant_shared` in TenantMigrationRunner | 24A | Done | Enhance `TenantMigrationRunner`: before iterating per-tenant schemas, (1) `CREATE SCHEMA IF NOT EXISTS tenant_shared`, (2) Run Flyway V1–V7 against `tenant_shared`. Subsequent startups are no-ops (Flyway tracks applied versions). Add to `@Bean(initMethod)` or `ApplicationRunner` ordering so `tenant_shared` exists before any request is served. Per ADR-015 §Shared Schema Bootstrap and architecture/ARCHITECTURE.md §9.8.3. |
| 24.5 | Create TenantFilterTransactionManager | 24B | Done | Custom `JpaTransactionManager` subclass (not AOP — AOP approach failed due to Session mismatch). Overrides `doBegin()` to enable Hibernate `@Filter("tenantFilter")` on the same Session that executes queries. Uses `TransactionSynchronizationManager.getResource()` → `EntityManager.unwrap(Session.class)` to avoid field shadowing with parent's `sessionFactory`. Registered as `@Bean @Primary` in `HibernateMultiTenancyConfig`. |
| 24.6 | Update SchemaMultiTenantConnectionProvider | 24B | Done | Uses parameterized `SELECT set_config('app.current_tenant', ?, false)` for RLS defense-in-depth (not `SET` which doesn't support placeholders). Accepts `tenant_shared` in `sanitizeSchema()`. Connection leak protection via try-catch in `getConnection()`. |
| 24.7 | Update TenantFilter for tier-aware resolution | 24B | Done | Cache type changed from `String` to `TenantInfo(schemaName, tier)`. Added `findTenantInfoByClerkOrgId` JPQL query (JOIN org_schema_mapping + organizations). Binds both `TENANT_ID` and `ORG_ID` ScopedValues. Fixed Caffeine NPE with `getIfPresent()` + manual `put()`. V5 global migration drops UNIQUE on `schema_name`. |
| 24.8 | Simplify TenantProvisioningService for Starter-first flow | 24B | Done | Starter: maps to `tenant_shared` (no schema creation/migration). Pro: retains dedicated schema flow. V8 tenant migration widens members unique constraint to `UNIQUE(clerk_user_id, tenant_id)`. MemberSyncService binds `ORG_ID` alongside `TENANT_ID`. |
| 24.9 | Write StarterTenantIntegrationTest | 24C | Done | 7 tests: CRUD isolation for two Starter orgs in `tenant_shared`, document isolation, `tenant_id` verification. Also fixed `findById()` bypassing Hibernate `@Filter` — added JPQL `findOneById()` to ProjectRepository/DocumentRepository. Fixed ProjectAccessService admin/owner access check for shared schema. |
| 24.10 | Write MixedTenantIntegrationTest | 24C | Done | 5 tests: cross-tier isolation (Starter vs Pro), Pro entities have null `tenantId`, Starter entities have org ID as `tenantId`. |
| 24.11 | Update existing integration tests | 24C | Done | All 183 existing tests pass unchanged. Unit test mocks updated for `findOneById()`. 195 total tests, 0 failures. |

#### Architecture Decisions

- **Dual-layer isolation**: Hibernate `@Filter` is the primary isolation mechanism (application-controlled, testable, predictable). Postgres RLS is defense-in-depth — catches native SQL queries, direct DB access, and any @Filter activation failures. Neither layer alone is sufficient; together they eliminate single points of failure. Per ADR-012.
- **`tenant_id` nullable on all schemas**: V7 migration adds the column to ALL tenant schemas (shared + dedicated). In dedicated schemas it stays NULL and is never read. This allows the same entity classes to work in both tiers without conditional annotations. Small storage overhead per row is acceptable. Per ADR-011.
- **AOP for filter activation**: SharedTenantFilterAspect uses AOP rather than explicit `session.enableFilter()` calls in every repository method. This keeps the isolation concern centralized and out of business logic. Per ADR-012.
- **Three-slice decomposition**: 24A is purely additive (migrations + annotations, no behavioral change to existing code). 24B modifies runtime behavior (filter chain, provisioning). 24C validates everything. Each slice is independently deployable.

#### Key Files

**Create:**
- `backend/src/main/resources/db/migration/tenant/V7__add_tenant_id_for_shared.sql`
- `backend/src/main/java/.../multitenancy/TenantAwareEntityListener.java`
- `backend/src/main/java/.../multitenancy/SharedTenantFilterAspect.java`
- `backend/src/test/java/.../multitenancy/StarterTenantIntegrationTest.java`
- `backend/src/test/java/.../multitenancy/MixedTenantIntegrationTest.java`

**Modify:**
- `backend/src/main/java/.../project/Project.java` — Add `tenantId` + filter annotations + entity listener
- `backend/src/main/java/.../document/Document.java` — Add `tenantId` + filter annotations + entity listener
- `backend/src/main/java/.../member/Member.java` — Add `tenantId` + filter annotations + entity listener
- `backend/src/main/java/.../member/ProjectMember.java` — Add `tenantId` + filter annotations + entity listener
- `backend/src/main/java/.../multitenancy/SchemaMultiTenantConnectionProvider.java` — `app.current_tenant` for shared schema
- `backend/src/main/java/.../multitenancy/TenantFilter.java` — `TenantInfo` cache, tier resolution, `ORG_ID` binding
- `backend/src/main/java/.../provisioning/TenantProvisioningService.java` — Simplify for Starter-first
- `backend/src/main/java/.../provisioning/TenantMigrationRunner.java` — Bootstrap `tenant_shared`

---

### Epic 25: Plan Enforcement

**Goal**: Enforce member limits per tier at all three layers: Clerk Dashboard configuration, backend service validation, and frontend UX gating. This prevents organizations from exceeding their plan's member allocation.

**References**: [ADR-014](adr/ADR-014-plan-enforcement.md), architecture/ARCHITECTURE.md §9.7

**Dependencies**: Epic 24

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: S

#### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **25A** | 25.1, 25.2, 25.4 | Backend enforcement: MemberSyncService limit check, PlanEnforcementIntegrationTest, Clerk setup docs | Done |
| **25B** | 25.3 | Frontend enforcement: gate invite form behind member limit, UpgradePrompt | Done |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 25.1 | Add member count validation to MemberSyncService | 25A | Done | Added `PlanLimits.maxMembers(Tier)` switch method. Injected `OrganizationRepository` into `MemberSyncService`. Added `enforceMemberLimit()` — looks up org tier, counts members via `JpaRepository.count()` (Hibernate `@Filter` scopes to tenant), throws `PlanLimitExceededException` if at limit. Only enforced on creation, not updates. Fixed `GlobalExceptionHandler` to extend `ResponseEntityExceptionHandler` for proper ProblemDetail JSON rendering. Updated 6 existing integration tests to upgrade test orgs to PRO tier via `planSyncService.syncPlan()`. |
| 25.2 | Configure Clerk Dashboard member limits | 25A | Done | Created `frontend/docs/clerk-billing-setup.md` documenting Clerk Dashboard configuration: Starter limit 2, Pro limit 10, feature keys `max_members_2`/`max_members_10`/`dedicated_schema`. |
| 25.3 | Gate invite form behind member limit on team page | 25B | Done | In `components/team/invite-member-form.tsx`: check `organization.membersCount + pendingInvitationsCount` against `maxAllowedMemberships` from Clerk. When at limit: replace invite form with inline upgrade prompt (Sparkles icon + link to billing page). Uses Clerk `OrganizationResource` properties directly — no hardcoded constants. 7 tests added. PR #54. |
| 25.4 | Write PlanEnforcementTest | 25A | Done | `PlanEnforcementIntegrationTest.java` with 6 ordered tests: Starter 2 members succeed, 3rd rejected (403), update at limit succeeds, error includes upgradeUrl, Pro 10 members succeed, 11th rejected. All 201 backend tests pass. |

#### Key Files

**Create:**
- `frontend/docs/clerk-billing-setup.md` ✓
- `backend/src/test/java/.../member/PlanEnforcementIntegrationTest.java` ✓

**Modify:**
- `backend/src/main/java/.../member/MemberSyncService.java` — Added member count check before creation ✓
- `backend/src/main/java/.../provisioning/PlanLimits.java` — Added `maxMembers(Tier)` method ✓
- `backend/src/main/java/.../exception/GlobalExceptionHandler.java` — Extended `ResponseEntityExceptionHandler` ✓
- `frontend/components/team/invite-member-form.tsx` — Member limit UX gating (25B)

---

### Epic 26: Billing UI & Feature Gating

**Goal**: Add a billing/subscription management page using Clerk's `<PricingTable>` component and implement plan-aware feature gating across the frontend using `has()` and `<Protect>`.

**References**: [ADR-010](adr/ADR-010-billing-integration.md), architecture/ARCHITECTURE.md §9.8.1

**Dependencies**: Epic 23

**Scope**: Frontend

**Estimated Effort**: M

#### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **26A** | 26.1–26.4 | Billing page, settings link, PlanBadge component, UpgradePrompt component | Done (PR #47) |
| **26B** | 26.5–26.7 | Feature gating on existing pages, plan badge in layout, tests | Done (PR #49) |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 26.1 | Create billing settings page | 26A | Done | `app/(app)/org/[slug]/settings/billing/page.tsx` — Server component rendering `<PricingTable for="organization" />` from Clerk's billing SDK (currently under `@clerk/nextjs/experimental`). Shows current plan and allows subscription management via Clerk's hosted checkout. Per architecture/ARCHITECTURE.md §9.8.1 file listing. |
| 26.2 | Add billing link to settings page and navigation | 26A | Done | Update `app/(app)/org/[slug]/settings/page.tsx` to include a card/link navigating to the billing sub-page. Add "Billing" entry to `lib/nav-items.ts` (as sub-item under Settings or separate nav entry, consistent with existing nav structure). |
| 26.3 | Create PlanBadge component | 26A | Done | `components/billing/plan-badge.tsx` — Reusable Shadcn Badge showing "Starter" (default/gray variant) or "Pro" (blue/accent variant). Server component usage: `const { has } = await auth(); const isPro = has({ plan: 'pro' });`. Client component usage: `useAuth()` hook. Per architecture/ARCHITECTURE.md §9.8.1. |
| 26.4 | Create UpgradePrompt component | 26A | Done | `components/billing/upgrade-prompt.tsx` — Reusable CTA card with plan benefits summary and link to billing page (`/org/{slug}/settings/billing`). Accepts `slug` prop for link construction. Used as `fallback` prop in `<Protect>` wrappers. Per architecture/ARCHITECTURE.md §9.8.1. |
| 26.5 | Add plan badge to org layout header | 26B | Done | Update `app/(app)/org/[slug]/layout.tsx` to render `<PlanBadge />` next to the `<OrganizationSwitcher />` in the header bar. Per architecture/ARCHITECTURE.md §9.8.1. |
| 26.6 | Add plan-aware feature gating to existing pages | 26B | Done | Wrap Pro-only features (if any exist in current pages) with `<Protect plan="pro" fallback={<UpgradePrompt />}>`. In server components, use `const isPro = has({ plan: 'pro' })` for conditional rendering. Initially this may apply to: advanced settings, or serve as reference examples for future Pro features. Per architecture/ARCHITECTURE.md §9.8.1 code examples. |
| 26.7 | Add frontend tests | 26B | Done | Test `PlanBadge` renders correct tier label. Test `UpgradePrompt` renders with correct billing link. Test billing page mounts `PricingTable`. Test `<Protect>` gate shows `UpgradePrompt` fallback for Starter orgs. |

#### Key Files

**Create:**
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx`
- `frontend/components/billing/plan-badge.tsx`
- `frontend/components/billing/upgrade-prompt.tsx`
- `frontend/__tests__/components/billing/` — Test files

**Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — Add billing link
- `frontend/app/(app)/org/[slug]/layout.tsx` — Add plan badge in header
- `frontend/lib/nav-items.ts` — Add billing nav entry

---

### Epic 27: Tier Upgrade — Starter to Pro

**Goal**: Implement the synchronous data migration that promotes a Starter organization from the shared `tenant_shared` schema to a dedicated `tenant_<hash>` schema when they subscribe to the Pro plan.

**References**: [ADR-016](adr/ADR-016-tier-upgrade-migration.md), architecture/ARCHITECTURE.md §9.5.2, §9.9

**Dependencies**: Epic 23, Epic 24

**Scope**: Backend

**Estimated Effort**: M

#### Tasks

| ID | Task | Status | Notes |
|----|------|--------|-------|
| 27.1 | Create TenantUpgradeService — schema creation | Done | `provisioning/TenantUpgradeService.java` — Orchestrates the Starter → Pro upgrade. Phase 1: (1) Set `Organization.provisioningStatus = IN_PROGRESS`. (2) Generate `tenant_<hash>` via `SchemaNameGenerator`. (3) `CREATE SCHEMA IF NOT EXISTS tenant_<hash>`. (4) Run Flyway V1–V7 against new schema (V7 adds nullable `tenant_id` columns for Hibernate consistency). Uses `migrationDataSource` (direct connection) for DDL. Per ADR-016 steps 1–4. |
| 27.2 | Implement data copy from shared to dedicated schema | Done | Within `TenantUpgradeService`: Use `JdbcTemplate` with raw SQL `INSERT INTO tenant_<hash>.{table} SELECT {cols minus tenant_id} FROM tenant_shared.{table} WHERE tenant_id = ?` for all 4 tables. Copy order respects FK constraints: `members` → `projects` → `documents` + `project_members`. Excludes `tenant_id` from INSERT (defaults to NULL in dedicated schemas). Per ADR-016 step 5. |
| 27.3 | Implement atomic cutover | Done | Within `TenantUpgradeService`, in a single `TransactionTemplate` (`PROPAGATION_REQUIRES_NEW`) block: (1) `UPDATE org_schema_mapping SET schema_name = 'tenant_<hash>' WHERE clerk_org_id = ?`. (2) `DELETE FROM tenant_shared.project_members WHERE tenant_id = ?`. (3) `DELETE FROM tenant_shared.documents WHERE tenant_id = ?`. (4) `DELETE FROM tenant_shared.projects WHERE tenant_id = ?`. (5) `DELETE FROM tenant_shared.members WHERE tenant_id = ?`. Delete order respects FK constraints (reverse of copy). Per ADR-016 step 6. |
| 27.4 | Wire upgrade into PlanSyncController | Done | Modify `PlanSyncController`: after updating `Organization.tier`, if previous tier was `STARTER` and new tier is `PRO`, call `TenantUpgradeService.upgrade(clerkOrgId)`. After upgrade completes, call `TenantFilter.evictSchema(clerkOrgId)` (task 23.5) and set `Organization.provisioningStatus = COMPLETED`. Per architecture/ARCHITECTURE.md §9.5.2 sequence diagram. |
| 27.5 | Write TierUpgradeIntegrationTest | Done | Provision a Starter org with test data: 2 members, several projects, documents, project_members. Trigger upgrade to Pro. Verify: (1) New `tenant_<hash>` schema exists with all migrated data. (2) `org_schema_mapping` points to `tenant_<hash>`. (3) All shared schema rows for this org are deleted. (4) `Organization.tier = PRO, provisioningStatus = COMPLETED`. (5) Subsequent requests resolve to the dedicated schema. Per architecture/ARCHITECTURE.md §9.8.4. |
| 27.6 | Verify rollback and idempotency | Done | Test that a failed upgrade (e.g., simulate Flyway failure mid-migration) leaves the org on Starter with data intact in `tenant_shared`. Verify `provisioningStatus` reflects the failure state. Verify an idempotent retry succeeds (schema `IF NOT EXISTS`, Flyway `baselineOnMigrate`). Per ADR-016 §Rollback Scenarios. |

#### Architecture Decisions

- **Synchronous migration**: Starter orgs are bounded (2 members, limited data). Migration completes in < 1s via direct SQL `INSERT INTO ... SELECT`. No background job infrastructure needed. Revisit for async if data volumes grow beyond 5s threshold. Per ADR-016.
- **Raw SQL over ORM for data copy**: `JdbcTemplate` avoids ORM overhead, Hibernate filter interference, and entity lifecycle callbacks during the bulk copy. Direct SQL is also more explicit about exactly which columns are copied.
- **Atomic cutover in single transaction**: The mapping update + shared data deletion must be atomic. If either fails, the transaction rolls back and the org remains on Starter with data intact. No partial state possible.
- **Brief data-less window**: Between cutover (mapping update + delete) and cache eviction, cached requests may resolve to `tenant_shared` but find no rows — returning empty results rather than leaking data. Acceptable for Starter traffic volumes. Per ADR-016 §Availability.

#### Key Files

**Create:**
- `backend/src/main/java/.../provisioning/TenantUpgradeService.java`
- `backend/src/test/java/.../provisioning/TierUpgradeIntegrationTest.java`

**Modify:**
- `backend/src/main/java/.../provisioning/PlanSyncController.java` — Wire upgrade trigger for STARTER → PRO transitions

---

---

## Change Request — Self-Managed Subscriptions

Stripe is not available for South African entities. Clerk Billing hard-depends on Stripe, making it non-viable for production. This change request removes all Clerk Billing integration and replaces it with a self-hosted `subscriptions` table. The plan/tier/tenancy model (Starter shared-schema, Pro schema-per-tenant, member limits, upgrade pipeline) is **unchanged**. PSP choice is deferred — the system operates with admin-managed plans until a payment provider is selected.

**Analysis**: See [`tasks/billing-psp-analysis.md`](tasks/billing-psp-analysis.md) for the full dependency audit, design rationale, and future PSP integration surface.

---

### Epic 28: Self-Hosted Subscriptions & Clerk Billing Removal

**Goal**: Own subscription state in our database via a `subscriptions` table. Expose a billing API for the frontend and an admin API for plan management. Remove all Clerk Billing integration (PricingTable, subscription webhooks, maxAllowedMemberships). No PSP dependency — plans are managed via internal API.

**Dependencies**: Epic 23 (tier data model), Epic 25 (plan enforcement)

**Scope**: Both (Backend + Frontend)

**Estimated Effort**: M

#### Slices

| Slice | Tasks | Summary | Deps | Status |
|-------|-------|---------|------|--------|
| **28A** | 28.1–28.7 | Backend: subscriptions table, entity, service, billing + admin endpoints, provisioning hook, integration tests | E23, E25 | Done (PR #56) |
| **28B** | 28.8–28.10 | Frontend removal: strip Clerk Billing webhook handlers, types, and docs | None (pure deletion) | Done (PR #55) |
| **28C** | 28.11–28.14 | Frontend replacement: custom billing page, invite form limit source change, new API types, tests | 28A | Done (PR #57) |

**Parallelism**: 28A and 28B can be developed and merged in parallel (separate PRs). 28C ships after 28A merges since it consumes the new `GET /api/billing/subscription` endpoint.

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 28.1 | Create V5 global migration for subscriptions table | 28A | Done | `db/migration/global/V5__add_subscriptions.sql` — `subscriptions` table with columns: `id` (UUID PK), `organization_id` (FK → organizations, UNIQUE), `plan_slug` (VARCHAR NOT NULL), `status` (VARCHAR NOT NULL DEFAULT 'ACTIVE'), `current_period_start` (TIMESTAMPTZ), `current_period_end` (TIMESTAMPTZ), `cancelled_at` (TIMESTAMPTZ), `created_at`, `updated_at`. Seed existing organizations with a STARTER subscription: `INSERT INTO subscriptions (organization_id, plan_slug, status) SELECT id, COALESCE(plan_slug, 'starter'), 'ACTIVE' FROM organizations ON CONFLICT DO NOTHING`. Status values: `ACTIVE`, `CANCELLED` only — more statuses added when a PSP defines what they mean. No `psp_*` columns — added when a PSP is chosen. |
| 28.2 | Create Subscription entity and SubscriptionRepository | 28A | Done | `billing/Subscription.java` — JPA entity mapped to `subscriptions` table in `public` schema. Fields: `id`, `organizationId` (UUID), `planSlug` (String), `status` (enum: ACTIVE, CANCELLED), `currentPeriodStart`, `currentPeriodEnd`, `cancelledAt`, `createdAt`, `updatedAt`. `billing/SubscriptionRepository.java` — `findByOrganizationId(UUID)` returning `Optional<Subscription>`. |
| 28.3 | Create SubscriptionService | 28A | Done | `billing/SubscriptionService.java` — (1) `createStarterSubscription(UUID organizationId)` — creates an ACTIVE subscription with `plan_slug = "starter"`. Called during provisioning. (2) `changePlan(String clerkOrgId, String planSlug)` — updates subscription row, then delegates to existing `PlanSyncService.syncPlan()` for tier resolution + upgrade trigger. (3) `getSubscription(String clerkOrgId)` — returns subscription + computed limits (via `PlanLimits.maxMembers()`) + current member count for the billing API response. |
| 28.4 | Create BillingController — public billing endpoint | 28A | Done | `billing/BillingController.java` — `GET /api/billing/subscription` (JWT-authenticated). Resolves org from JWT `o.id` claim, returns `BillingResponse` record: `{ planSlug, tier, status, limits: { maxMembers, currentMembers } }`. Uses `SubscriptionService.getSubscription()`. If no subscription found, returns a synthetic STARTER response (defensive). |
| 28.5 | Create AdminBillingController — internal plan management | 28A | Done | `billing/AdminBillingController.java` — `POST /internal/billing/set-plan` (API key-authenticated). Request body: `SetPlanRequest(String clerkOrgId, String planSlug)`. Updates subscription via `SubscriptionService.changePlan()` which calls `PlanSyncService.syncPlan()` + `TenantUpgradeService.upgrade()` if needed. Returns 200 OK. This is the admin entry point for plan changes — replaces the Clerk subscription webhook path. |
| 28.6 | Hook subscription creation into TenantProvisioningService | 28A | Done | Modify `TenantProvisioningService.provision()` — after creating the Organization and schema mapping, call `SubscriptionService.createStarterSubscription(org.getId())`. Every new org gets a subscription row from day one. |
| 28.7 | Write SubscriptionIntegrationTest | 28A | Done | `billing/SubscriptionIntegrationTest.java` — (1) Subscription created on org provisioning (verify row exists with STARTER). (2) `GET /api/billing/subscription` returns correct plan + limits for Starter org. (3) `POST /internal/billing/set-plan` with "pro" updates subscription + org tier. (4) `GET /api/billing/subscription` reflects PRO after plan change. (5) Admin set-plan triggers Starter→Pro upgrade (verify schema migration). (6) Set-plan for unknown org returns 404. (7) API key required for internal endpoint (401 without). |
| 28.8 | Remove Clerk subscription webhook handlers | 28B | Done | Modify `lib/webhook-handlers.ts` — remove `SubscriptionEventData` interface, `syncPlan()` function, `handleSubscriptionCreated()`, `handleSubscriptionUpdated()`, and the `subscription.created`/`subscription.updated` cases from `routeWebhookEvent()`. All other Clerk webhook handlers (org CRUD, membership CRUD) are unrelated to billing and stay unchanged. |
| 28.9 | Remove PlanSyncRequest from internal-api.ts | 28B | Done | Check if `PlanSyncRequest` type in `lib/internal-api.ts` is used by anything other than the removed subscription handlers. If not, remove it. No new types added here — those belong in 28C. |
| 28.10 | Delete clerk-billing-setup.md | 28B | Done | Delete `frontend/docs/clerk-billing-setup.md` — Clerk Dashboard billing configuration is no longer relevant. Member limits are now enforced entirely by our backend. |
| 28.11 | Replace billing page with custom plan display | 28C | Done | Replace `app/(app)/org/[slug]/settings/billing/page.tsx` — remove `<PricingTable>` import and rendering. New server component: fetch `GET /api/billing/subscription` via `apiClient`, display current plan name (with existing `PlanBadge` component), member usage (e.g., "1 of 2 members"), and plan features summary. Show a "Contact us to upgrade" CTA for Starter orgs (placeholder until PSP is selected). Pro orgs see "You're on the Pro plan" confirmation. Reuse existing `UpgradePrompt` component where appropriate. |
| 28.12 | Replace maxAllowedMemberships in InviteMemberForm | 28C | Done | Modify `components/team/invite-member-form.tsx` — remove dependency on `organization.maxAllowedMemberships` (populated by Clerk Billing). Instead, accept `maxMembers` and `currentMembers` as props from the team page server component. The team page fetches limits from `GET /api/billing/subscription` and passes them down. The limit check logic (`currentMembers + pendingInvitations >= maxMembers`) stays the same, just sourced from our API instead of Clerk. |
| 28.13 | Add BillingResponse and SetPlanRequest types to internal-api.ts | 28C | Done | Add `BillingResponse` and `SetPlanRequest` TypeScript types to `lib/internal-api.ts` for the new backend endpoints. `BillingResponse`: `{ planSlug: string, tier: string, status: string, limits: { maxMembers: number, currentMembers: number } }`. `SetPlanRequest`: `{ clerkOrgId: string, planSlug: string }`. |
| 28.14 | Write frontend tests | 28C | Done | (1) Billing page renders plan name and member usage from API response. (2) Billing page shows upgrade CTA for Starter orgs. (3) Billing page shows Pro confirmation for Pro orgs. (4) `InviteMemberForm` hides form when `currentMembers >= maxMembers` (props-based). (5) `InviteMemberForm` shows form when under limit. (6) Webhook handler no longer dispatches `subscription.created`/`subscription.updated` events (regression guard from 28B). |

#### Architecture Decisions

- **No premature PSP abstraction**: No `BillingProvider` interface, no checkout URLs, no webhook parsers. When a PSP is chosen, the actual API shape will inform the integration design. The future PSP surface is ~100 lines: one webhook endpoint, one checkout redirect, one migration adding `psp_*` columns to `subscriptions`.
- **Subscriptions table is minimal**: Only `plan_slug`, `status`, and period timestamps. No PSP-specific columns. More fields added when they have concrete meaning from a chosen PSP.
- **Admin API replaces webhook-driven plan sync**: Instead of `Clerk Billing → webhook → plan sync`, plans are set via `POST /internal/billing/set-plan`. This works for manual/sales-led B2B billing and becomes the internal target for any future PSP webhook handler.
- **Existing PlanSyncService untouched**: `SubscriptionService.changePlan()` delegates to `PlanSyncService.syncPlan()` which handles tier derivation, org update, cache eviction, and upgrade triggering. No changes to the existing pipeline.
- **Frontend member limits from backend**: Replacing `maxAllowedMemberships` (Clerk Billing property) with our `GET /api/billing/subscription` response makes the frontend fully independent of any billing provider.
- **Three-slice decomposition by dependency profile**: 28A (backend foundation) and 28B (frontend removal) are independent — parallel PRs. 28C (frontend replacement) depends on 28A's API. This maximizes throughput while keeping each PR small and reviewable.

#### Key Files

**Slice 28A — Create:**
- `backend/src/main/resources/db/migration/global/V5__add_subscriptions.sql`
- `backend/src/main/java/.../billing/Subscription.java`
- `backend/src/main/java/.../billing/SubscriptionRepository.java`
- `backend/src/main/java/.../billing/SubscriptionService.java`
- `backend/src/main/java/.../billing/BillingController.java`
- `backend/src/main/java/.../billing/AdminBillingController.java`
- `backend/src/test/java/.../billing/SubscriptionIntegrationTest.java`

**Slice 28A — Modify:**
- `backend/src/main/java/.../provisioning/TenantProvisioningService.java` — Hook subscription creation

**Slice 28B — Modify:**
- `frontend/lib/webhook-handlers.ts` — Remove subscription event handlers
- `frontend/lib/internal-api.ts` — Remove PlanSyncRequest type

**Slice 28B — Delete:**
- `frontend/docs/clerk-billing-setup.md`

**Slice 28C — Modify:**
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` — Replace PricingTable with custom display
- `frontend/components/team/invite-member-form.tsx` — Props-based limits instead of Clerk property
- `frontend/lib/internal-api.ts` — Add BillingResponse + SetPlanRequest types
- `frontend/app/(app)/org/[slug]/team/page.tsx` — Fetch and pass billing limits to InviteMemberForm

---

### Epic 29: Self-Service Plan Upgrade (Simulated)

**Goal**: Allow org admins to upgrade from Starter to Pro directly from the billing page. Since no PSP is integrated yet, the upgrade executes immediately (simulated checkout). When a PSP is added later, the upgrade endpoint becomes the redirect-to-checkout entry point — the frontend flow stays the same.

**Dependencies**: Epic 28 (subscriptions table, billing API)

**Scope**: Both (Backend thin, Frontend heavy)

**Estimated Effort**: S

#### Slices

| Slice | Tasks | Summary | Deps | Status |
|-------|-------|---------|------|--------|
| **29A** | 29.1–29.2 | Backend: public upgrade endpoint on BillingController + integration tests | E28 | Done (PR #58) |
| **29B** | 29.3–29.7 | Frontend: upgrade dialog, server action, pricing comparison, billing page wiring, tests | 29A | Done (PR #59) |

#### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 29.1 | Add `POST /api/billing/upgrade` to BillingController | 29A | Done | Add to existing `billing/BillingController.java` — no new file. `@PostMapping("/upgrade")` with `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")`. Request body: `UpgradeRequest(String planSlug)`. Resolves org via `RequestScopes.requireOrgId()`. Calls `subscriptionService.changePlan(clerkOrgId, planSlug)`. If `result.upgradeNeeded()`, calls `tenantUpgradeService.upgrade(clerkOrgId)`. Returns updated `BillingResponse` so the frontend can immediately render the new state. Guard: if org is already on the requested tier, return 200 with current state (idempotent, no error). This is intentionally a direct upgrade with no payment gate — when a PSP is added, this endpoint will instead create a checkout session and return a redirect URL. |
| 29.2 | Write upgrade integration tests | 29A | Done | Add to existing `SubscriptionIntegrationTest.java` or new `BillingUpgradeIntegrationTest.java`. Tests: (1) Starter admin calls upgrade with "pro" → 200, tier is PRO, schema migrated. (2) Starter member calls upgrade → 403 Forbidden. (3) Pro admin calls upgrade with "pro" → 200 idempotent, no error. (4) Upgrade returns updated BillingResponse with correct limits (maxMembers=10). (5) Unauthenticated request → 401. |
| 29.3 | Create UpgradeConfirmDialog component | 29B | Done | `components/billing/upgrade-confirm-dialog.tsx` — Client component. AlertDialog (Shadcn) with: title "Upgrade to Pro", description listing key Pro benefits (dedicated infrastructure, 10 members, priority support), confirm button "Upgrade Now", cancel button. Accepts `onConfirm` callback (async) and `slug` prop for revalidation. Shows loading state on confirm button while upgrade is in progress. On success: close dialog and trigger page revalidation via `router.refresh()`. On error: show inline error message in dialog. |
| 29.4 | Create upgrade server action | 29B | Done | `app/(app)/org/[slug]/settings/billing/actions.ts` — Server action `upgradeToPro()`. Calls `api.post<BillingResponse>("/api/billing/upgrade", { planSlug: "pro" })`. Revalidates `/org/[slug]/settings/billing` path. Returns `{ success: true, billing: BillingResponse }` or `{ success: false, error: string }`. Follows existing server action patterns (see `team/actions.ts`). |
| 29.5 | Add pricing comparison to billing page | 29B | Done | Add a feature comparison section to the billing page (below the current plan card, above the upgrade CTA). Simple two-column layout: Starter vs Pro. Features to compare: member limit (2 vs 10), infrastructure (shared vs dedicated), data isolation (row-level vs schema-level). Use a clean table or side-by-side cards. Only shown for Starter orgs (Pro orgs already know what they have). |
| 29.6 | Wire upgrade button and dialog into billing page | 29B | Done | Replace the "Contact us" mailto CTA in the billing page's upgrade card with the `UpgradeConfirmDialog`. The billing page is a server component, so: extract the upgrade card into a client component (`UpgradeCard`) that wraps the dialog trigger button + `UpgradeConfirmDialog`. Pass `slug` as prop. The upgrade button text: "Upgrade to Pro". After successful upgrade, the page revalidates and shows the Pro state (plan badge changes, upgrade card disappears, limits update). |
| 29.7 | Write frontend tests | 29B | Done | (1) `UpgradeConfirmDialog` renders with Pro benefits description. (2) Confirm button calls `onConfirm` callback. (3) Dialog shows loading state during upgrade. (4) Billing page shows upgrade button for Starter orgs. (5) Billing page hides upgrade section for Pro orgs. (6) Server action calls correct API endpoint with "pro" planSlug. |

#### Architecture Decisions

- **Public endpoint, not internal**: The upgrade is user-initiated, so it goes through JWT auth + `@PreAuthorize` role check via `POST /api/billing/upgrade`. This is more secure than having a server action call the internal API with the API key — Spring Security handles authorization properly.
- **Endpoint returns BillingResponse**: After upgrade completes, the endpoint returns the new billing state. This avoids a separate fetch and lets the frontend optimistically update the UI.
- **Idempotent upgrade**: Calling upgrade when already on Pro returns 200 with current state, not an error. This handles double-clicks, retries, and race conditions gracefully.
- **Future PSP integration point**: When a PSP is chosen, `POST /api/billing/upgrade` changes from direct-upgrade to create-checkout-session + return redirect URL. The frontend flow changes minimally: instead of showing a confirmation dialog, it redirects to the PSP checkout page. The confirmation dialog becomes the PSP's checkout UI.
- **Server action wraps API call**: The upgrade action is a Next.js server action (not a direct client-side fetch) so it can attach the JWT, revalidate server-cached data, and handle errors consistently with other actions in the app.

#### Key Files

**Slice 29A — Modify:**
- `backend/src/main/java/.../billing/BillingController.java` — Add `POST /upgrade` endpoint

**Slice 29A — Create or Modify:**
- `backend/src/test/java/.../billing/SubscriptionIntegrationTest.java` or new `BillingUpgradeIntegrationTest.java` — Upgrade tests

**Slice 29B — Create:**
- `frontend/components/billing/upgrade-confirm-dialog.tsx`
- `frontend/app/(app)/org/[slug]/settings/billing/actions.ts`

**Slice 29B — Modify:**
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` — Pricing comparison + wire upgrade dialog
- `frontend/lib/internal-api.ts` — Add `UpgradeRequest` type

---

### Phase 2 Implementation Order

#### Stage 1: Billing Foundation

| Order | Epic | Rationale |
|-------|------|-----------|
| 1 | Epic 23: Tier Data Model & Plan Sync | Foundation — all Phase 2 epics depend on the tier model and plan sync pipeline. |

#### Stage 2: Infrastructure (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 2a | Epic 24: Shared Schema & Row-Level Isolation | Core infrastructure — must complete before enforcement and upgrade. Highest technical risk (dual-layer isolation). |
| 2b | Epic 26: Billing UI & Feature Gating | Independent of shared schema — only needs plan state from Epic 23. Can develop in parallel with Epic 24. |

**Rationale**: Epic 24 (shared schema + RLS) and Epic 26 (billing UI) are independent of each other. Developing them in parallel maximizes throughput. Epic 24 carries the highest technical risk in Phase 2 (Hibernate @Filter + AOP + Postgres RLS), so starting it early maximizes time for course correction.

#### Stage 3: Enforcement & Upgrade (Parallel Tracks)

| Order | Epic | Rationale |
|-------|------|-----------|
| 3a | Epic 25: Plan Enforcement | Depends on shared schema (member count per-tenant). Validates limits work correctly. |
| 3b | Epic 27: Tier Upgrade | Depends on shared schema + plan sync. Completes the Starter → Pro lifecycle. |

**Rationale**: Both epics depend on Epic 24 and can be developed in parallel. Epic 25 is a small epic (S effort) that validates the constraint model. Epic 27 is the capstone — after it lands, the full billing lifecycle is operational.

#### Phase 2 Summary Timeline

```
Stage 1:  [E23]
Stage 2:  [E24] [E26]           <- parallel
Stage 3:  [E25] [E27]           <- parallel (after E24)
```
