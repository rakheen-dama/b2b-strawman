# Phase 8 — Rate Cards, Budgets & Profitability

Phase 8 adds the **revenue infrastructure layer** to the DocTeams platform — the bridge between tracked work and financial insight. It introduces billing rate cards with a three-level resolution hierarchy (ADR-039), cost rates for margin calculation (ADR-043), point-in-time rate snapshots on time entries (ADR-040), project budgets with threshold-based alerts (ADR-042), multi-currency support with store-in-original semantics (ADR-041), and query-derived profitability views. All additions build on Phase 5 (TimeEntry), Phase 6 (AuditService), and Phase 6.5 (ApplicationEvent + notification pipeline).

**Architecture doc**: `architecture/phase8-rate-cards-budgets-profitability.md` (Section 11 of ARCHITECTURE.md)

**ADRs**: [ADR-039](../adr/ADR-039-rate-resolution-hierarchy.md) (rate hierarchy), [ADR-040](../adr/ADR-040-point-in-time-rate-snapshotting.md) (snapshotting), [ADR-041](../adr/ADR-041-multi-currency-store-in-original.md) (multi-currency), [ADR-042](../adr/ADR-042-single-budget-per-project.md) (single budget), [ADR-043](../adr/ADR-043-margin-aware-profitability.md) (margin-aware profitability)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 67 | OrgSettings & Rate Entity Foundation | Backend | -- | L | 67A, 67B, 67C, 67D | **Done** (PRs #133–#136) |
| 68 | Rate Management Frontend — Settings, Project & Customer Rates | Frontend | 67 | M | 68A, 68B | **Done** (PRs #138, #139) |
| 69 | TimeEntry Rate Snapshots & Billable Enrichment | Backend | 67 | M | 69A, 69B | **Done** (PRs #137, #140) |
| 70 | TimeEntry Frontend — Billable UX & Rate Preview | Frontend | 69 | S | 70A | **Done** (PR #141) |
| 71 | Project Budgets — Entity, Status & Alerts | Backend | 69 | M | 71A, 71B | |
| 72 | Budget Frontend — Configuration & Status Visualization | Frontend | 71 | S | 72A | |
| 73 | Profitability Backend — Reports & Aggregation Queries | Backend | 69 | M | 73A, 73B | |
| 74 | Profitability & Financials Frontend — Pages & Tabs | Frontend | 73, 72, 68 | L | 74A, 74B, 74C | |

## Dependency Graph

```
[E67 OrgSettings & Rate Foundation] ──┬──► [E68 Rate Mgmt Frontend]
         (Backend)                     │
                                       └──► [E69 TimeEntry Snapshots] ──┬──► [E70 TimeEntry Frontend]
                                                 (Backend)               │
                                                                         ├──► [E71 Budget Backend] ──► [E72 Budget Frontend]
                                                                         │
                                                                         └──► [E73 Profitability Backend]
                                                                                        │
                                  [E68] + [E72] + [E73] ──────────────────────────────► [E74 Profitability Frontend]
```

**Parallel tracks**:
- Epic 67 (backend rate foundation) has no external dependencies and is the sole starting point.
- After Epic 67 completes: Epic 68 (rate frontend) and Epic 69 (time entry snapshots) can run in parallel.
- After Epic 69 completes: Epic 70 (time entry frontend), Epic 71 (budget backend), and Epic 73 (profitability backend) can all run in parallel.
- After Epic 71: Epic 72 (budget frontend) can proceed.
- Epic 74 (profitability frontend) is the final convergence point — it depends on Epics 68, 72, and 73.

## Implementation Order

### Stage 1: Backend Foundation

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1 | Epic 67 | 67A | V19 migration (org_settings, billing_rates, cost_rates, project_budgets tables), OrgSettings entity/repo/service/controller. Foundation for everything. |
| 2a | Epic 67 | 67B | BillingRate entity, repository, resolution logic. Depends on V19 from 67A. |
| 2b | Epic 67 | 67C | CostRate entity, repository, resolution logic. Parallel with 67B. |
| 3 | Epic 67 | 67D | BillingRate controller + CostRate controller + integration tests for both. Depends on 67B + 67C. |

### Stage 2: Rate Frontend + TimeEntry Snapshots (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 68 | 68A | Settings rates page (org-level billing/cost rates, currency selector). Depends on 67D APIs. |
| 4b | Epic 68 | 68B | Project + customer rate overrides tabs. Depends on 67D APIs. |
| 4c | Epic 69 | 69A | V20 migration (rate snapshot columns on time_entries), TimeEntry entity modifications, snapshot-on-create logic. Depends on 67B + 67C. |
| 5 | Epic 69 | 69B | PATCH billable endpoint, billable filter, TimeEntry response changes, re-snapshot admin endpoint, integration tests. Depends on 69A. |

### Stage 3: TimeEntry Frontend + Budget + Profitability (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 6a | Epic 70 | 70A | Billable checkbox in LogTimeDialog, rate preview, billable indicator/filter in list. Depends on 69B APIs. |
| 6b | Epic 71 | 71A | ProjectBudget entity, repository, service, controller, budget status calculation. Depends on 69A (snapshots exist). |
| 6c | Epic 73 | 73A | Project + customer profitability endpoints, ReportService, native SQL queries. Depends on 69A. |
| 7a | Epic 71 | 71B | BudgetCheckService, BudgetThresholdEvent, NotificationEventHandler integration, alert tests. Depends on 71A. |
| 7b | Epic 73 | 73B | Utilization + org profitability endpoints, self-service access control, tests. Depends on 73A. |

### Stage 4: Frontend Convergence

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 8a | Epic 72 | 72A | Budget tab on project detail page. Depends on 71A + 71B APIs. |
| 8b | Epic 74 | 74A | Profitability page (sidebar nav, utilization section, project profitability table). Depends on 73A + 73B. |
| 9a | Epic 74 | 74B | Project financials tab + budget panel integration. Depends on 72A + 73A. |
| 9b | Epic 74 | 74C | Customer financials tab + customer profitability. Depends on 73A + 68B. |

### Timeline

```
Stage 1:  [67A] → [67B // 67C] → [67D]              ← backend rate foundation
Stage 2:  [68A // 68B // 69A] → [69B]                ← rate frontend + snapshot backend (parallel)
Stage 3:  [70A // 71A // 73A] → [71B // 73B]         ← billable UX + budget + profitability (parallel)
Stage 4:  [72A // 74A] → [74B // 74C]                ← frontend convergence
```

---

## Epic 67: OrgSettings & Rate Entity Foundation

**Goal**: Create the V19 tenant migration with all four new tables (org_settings, billing_rates, cost_rates, project_budgets), implement the OrgSettings entity with CRUD API, and implement BillingRate + CostRate entities with their repositories, rate resolution logic, CRUD services, controllers, and integration tests. This epic delivers the complete backend rate infrastructure.

**References**: Architecture doc Sections 11.2.1-11.2.3, 11.3.1, 11.4.1-11.4.3, 11.7.1, 11.8.1. [ADR-039](../adr/ADR-039-rate-resolution-hierarchy.md), [ADR-041](../adr/ADR-041-multi-currency-store-in-original.md).

**Dependencies**: None (builds on existing multi-tenant infrastructure, audit service from Phase 6)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **67A** | 67.1-67.5 | V19 migration (all 4 tables), OrgSettings entity/repo/service/controller, integration tests | |
| **67B** | 67.6-67.10 | BillingRate entity, repository with resolution queries, service with 3-level rate resolution + overlap validation | |
| **67C** | 67.11-67.14 | CostRate entity, repository, service with resolution + overlap validation | |
| **67D** | 67.15-67.21 | BillingRate controller + CostRate controller, all REST endpoints, request/response DTOs, full integration + controller tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 67.1 | Create V19 tenant migration for org_settings, billing_rates, cost_rates, project_budgets tables | 67A | | `db/migration/tenant/V19__create_rate_budget_tables.sql`. Four tables with all constraints, indexes, and RLS policies per Section 11.7.1. Note: V18 already used by Phase 7 portal contacts. Include CHECK constraints: `chk_billing_rate_scope` (mutual exclusivity of project_id/customer_id), `chk_budget_at_least_one`, `chk_budget_currency_required`. Pattern: follow `V14__create_audit_events.sql` for RLS structure. |
| 67.2 | Create OrgSettings entity | 67A | | `settings/OrgSettings.java` — JPA entity mapped to `org_settings`. Fields: UUID id, String defaultCurrency (VARCHAR(3), NOT NULL, default 'USD'), String tenantId (UNIQUE), Instant createdAt, Instant updatedAt. Annotations: `@FilterDef`/`@Filter` for `tenantFilter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Constructor: `OrgSettings(String defaultCurrency)`. Methods: `updateCurrency(String currency)` — sets updatedAt. Pattern: follow `timeentry/TimeEntry.java` entity structure. |
| 67.3 | Create OrgSettingsRepository and OrgSettingsService | 67A | | `settings/OrgSettingsRepository.java` — extends `JpaRepository<OrgSettings, UUID>`. Methods: `Optional<OrgSettings> findOneById(UUID id)` (JPQL), `Optional<OrgSettings> findByTenantId(String tenantId)`. `settings/OrgSettingsService.java` — `getSettings()` returns settings or default (never null/404), `updateSettings(String defaultCurrency, UUID memberId, String orgRole)` — admin/owner only, upsert, audit event `org_settings.updated`. `getDefaultCurrency()` helper for other services. Pattern: follow `task/TaskService.java` for audit integration. |
| 67.4 | Create OrgSettingsController | 67A | | `settings/OrgSettingsController.java` — `@RestController`, `@RequestMapping("/api/settings")`. Inner DTOs: `SettingsResponse(String defaultCurrency)`, `UpdateSettingsRequest(@NotBlank @Size(min=3, max=3) String defaultCurrency)`. `GET /` returns 200 always (defaults if no row). `PUT /` upsert, admin/owner only. Extract memberId/orgRole from `RequestScopes`. Pattern: follow `task/TaskController.java`. |
| 67.5 | Add OrgSettings integration tests | 67A | | `settings/OrgSettingsIntegrationTest.java` (~6 tests): GET returns USD default when no settings exist, PUT creates settings, GET returns updated value, PUT updates existing settings, PUT by non-admin returns 403, validates 3-char currency code. `settings/OrgSettingsControllerTest.java` (~4 MockMvc tests): GET 200 shape, PUT 200 shape, PUT 403 unauthorized, invalid currency 400. Pattern: follow `audit/AuditEventControllerTest.java` for MockMvc, `timeentry/TimeEntryIntegrationTest.java` for service tests. |
| 67.6 | Create BillingRate entity | 67B | | `billingrate/BillingRate.java` — JPA entity mapped to `billing_rates`. Fields per Section 11.2.2: UUID id, UUID memberId, UUID projectId (nullable), UUID customerId (nullable), String currency, BigDecimal hourlyRate (precision=12, scale=2), LocalDate effectiveFrom, LocalDate effectiveTo (nullable), String tenantId, Instant createdAt, Instant updatedAt. Full `@FilterDef`/`@Filter`/`TenantAware` pattern. Constructor with all fields. `update(BigDecimal hourlyRate, String currency, LocalDate effectiveFrom, LocalDate effectiveTo)` method. `getScope()` returns MEMBER_DEFAULT / PROJECT_OVERRIDE / CUSTOMER_OVERRIDE. See Section 11.8.1 entity code. |
| 67.7 | Create BillingRateRepository with resolution queries | 67B | | `billingrate/BillingRateRepository.java` — extends `JpaRepository<BillingRate, UUID>`. Methods: `findOneById(UUID id)` (JPQL), `findProjectOverride(UUID memberId, UUID projectId, LocalDate date)` (List return, ORDER BY effectiveFrom DESC), `findCustomerOverride(UUID memberId, UUID customerId, LocalDate date)`, `findMemberDefault(UUID memberId, LocalDate date)`, `findOverlapping(UUID memberId, UUID projectId, UUID customerId, LocalDate startDate, LocalDate endDate, UUID excludeId)` for overlap validation, list queries with optional filters: `findByFilters(UUID memberId, UUID projectId, UUID customerId)`. See Section 11.8.1 repository code. |
| 67.8 | Create BillingRateService with 3-level rate resolution | 67B | | `billingrate/BillingRateService.java`. `ResolvedRate` record: `(BigDecimal hourlyRate, String currency, String source, UUID billingRateId)`. Key methods: (1) `resolveRate(UUID memberId, UUID projectId, LocalDate date)` — 3-level cascade per ADR-039 (project override → customer override via CustomerProjectRepository → member default). (2) `createRate(...)` — validates no overlap, validates scope (project/customer mutual exclusivity), publishes `billing_rate.created` audit event. (3) `updateRate(UUID id, ...)` — validates overlap excluding self. (4) `deleteRate(UUID id)` — audit event. (5) `listRates(UUID memberId, UUID projectId, UUID customerId, boolean activeOnly)`. Permission enforcement: admin/owner for all scopes, project lead for project overrides only (via `ProjectAccessService`). Inject: `BillingRateRepository`, `CustomerProjectRepository`, `ProjectAccessService`, `AuditService`, `OrgSettingsService`. Pattern: follow `task/TaskService.java` for access control + audit. |
| 67.9 | Create BillingRate resolution integration tests | 67B | | Within `billingrate/BillingRateResolutionTest.java` (~10 tests): resolve project override (highest priority), resolve customer override when no project override, resolve member default as fallback, return empty when no rate configured, effective date range filtering (future rate not resolved), rate resolution with multiple date ranges (picks correct period), customer resolution via CustomerProject join (first linked customer), project override trumps customer override for same member, overlapping date range rejection, re-resolution after date range expiry. Seed: provision tenant, sync 2 members, create project, create customer, link customer to project. Pattern: follow `timeentry/TimeEntryIntegrationTest.java`. |
| 67.10 | Create BillingRate CRUD integration tests | 67B | | Within `billingrate/BillingRateIntegrationTest.java` (~8 tests): create member default rate, create project override, create customer override, reject compound override (project+customer both set), update rate, delete rate, admin creates rate (success), member creates rate (rejected 403). Test overlap validation: create two rates with overlapping dates for same scope (rejected 409). Pattern: follow `timeentry/TimeEntryIntegrationTest.java`. |
| 67.11 | Create CostRate entity | 67C | | `costrate/CostRate.java` — JPA entity mapped to `cost_rates`. Fields per Section 11.2.3: UUID id, UUID memberId, String currency, BigDecimal hourlyCost (precision=12, scale=2), LocalDate effectiveFrom, LocalDate effectiveTo (nullable), String tenantId, Instant createdAt, Instant updatedAt. Full `@FilterDef`/`@Filter`/`TenantAware` pattern. Constructor. `update(BigDecimal hourlyCost, String currency, LocalDate effectiveFrom, LocalDate effectiveTo)` method. Simpler than BillingRate — no project/customer scoping. Pattern: follow BillingRate entity from 67.6. |
| 67.12 | Create CostRateRepository | 67C | | `costrate/CostRateRepository.java` — extends `JpaRepository<CostRate, UUID>`. Methods: `findOneById(UUID id)` (JPQL), `findByMemberIdAndDate(UUID memberId, LocalDate date)` (JPQL, ORDER BY effectiveFrom DESC), `findOverlapping(UUID memberId, LocalDate startDate, LocalDate endDate, UUID excludeId)`, `findByMemberId(UUID memberId)`. Simpler than BillingRateRepository — no scope variants. |
| 67.13 | Create CostRateService with resolution logic | 67C | | `costrate/CostRateService.java`. Key methods: (1) `resolveCostRate(UUID memberId, LocalDate date)` — simple single-level lookup (no hierarchy). (2) `createCostRate(...)` — admin/owner only (check orgRole), validates no overlap, audit event `cost_rate.created`. (3) `updateCostRate(UUID id, ...)` — overlap validation excluding self. (4) `deleteCostRate(UUID id)` — audit event. (5) `listCostRates(UUID memberId)`. Inject: `CostRateRepository`, `AuditService`, `OrgSettingsService`. Pattern: follow BillingRateService from 67.8 (simpler — no project/customer scoping). |
| 67.14 | Create CostRate integration tests | 67C | | `costrate/CostRateIntegrationTest.java` (~8 tests): create cost rate, resolve cost rate by date, resolve returns empty when no rate, effective date range filtering, overlapping date rejection, update cost rate, delete cost rate, non-admin rejected (403). Seed: provision tenant, sync 2 members (admin + regular). Pattern: follow BillingRate tests from 67.9/67.10. |
| 67.15 | Create BillingRateController with DTOs | 67D | | `billingrate/BillingRateController.java` — `@RestController`, `@RequestMapping("/api/billing-rates")`. Inner DTOs: `CreateBillingRateRequest(UUID memberId, UUID projectId, UUID customerId, @NotBlank @Size(min=3,max=3) String currency, @NotNull @Positive BigDecimal hourlyRate, @NotNull LocalDate effectiveFrom, LocalDate effectiveTo)`, `UpdateBillingRateRequest(...)`, `BillingRateResponse(UUID id, UUID memberId, String memberName, UUID projectId, String projectName, UUID customerId, String customerName, String scope, String currency, BigDecimal hourlyRate, LocalDate effectiveFrom, LocalDate effectiveTo, Instant createdAt, Instant updatedAt)`, `ResolvedRateResponse(BigDecimal hourlyRate, String currency, String source, UUID billingRateId)`. 5 endpoints per Section 11.4.2: `GET /` (list with filters), `POST /` (201), `PUT /{id}` (200), `DELETE /{id}` (204), `GET /resolve` (memberId, projectId, date params). Member name enrichment via MemberRepository. Pattern: follow `task/TaskController.java`. |
| 67.16 | Create CostRateController with DTOs | 67D | | `costrate/CostRateController.java` — `@RestController`, `@RequestMapping("/api/cost-rates")`. Inner DTOs: `CreateCostRateRequest(UUID memberId, @NotBlank @Size(min=3,max=3) String currency, @NotNull @Positive BigDecimal hourlyCost, @NotNull LocalDate effectiveFrom, LocalDate effectiveTo)`, `UpdateCostRateRequest(...)`, `CostRateResponse(UUID id, UUID memberId, String memberName, String currency, BigDecimal hourlyCost, LocalDate effectiveFrom, LocalDate effectiveTo, Instant createdAt, Instant updatedAt)`. 4 endpoints per Section 11.4.3: `GET /` (filter by memberId), `POST /` (201), `PUT /{id}` (200), `DELETE /{id}` (204). All admin/owner only. Pattern: follow BillingRateController from 67.15. |
| 67.17 | Add SecurityConfig updates for rate endpoints | 67D | | Modify `security/SecurityConfig.java` — verify `/api/billing-rates/**`, `/api/cost-rates/**`, `/api/settings/**` are covered by authenticated endpoint patterns. Likely already covered by existing `/api/**` pattern. If not, add explicitly. Also verify `/api/admin/**` pattern for future re-snapshot endpoint (69B). |
| 67.18 | Add BillingRateController MockMvc tests | 67D | | `billingrate/BillingRateControllerTest.java` (~8 MockMvc tests): POST creates rate 201, GET lists rates 200, GET with filters, PUT updates 200, DELETE 204, POST with invalid data 400, POST overlap returns 409, GET /resolve returns rate. Test permission: admin JWT creates rate (success), member JWT creates member-default rate (rejected). Pattern: follow `audit/AuditEventControllerTest.java`. |
| 67.19 | Add CostRateController MockMvc tests | 67D | | `costrate/CostRateControllerTest.java` (~5 MockMvc tests): POST creates 201, GET lists 200, PUT updates 200, DELETE 204, member JWT rejected 403. Pattern: follow BillingRateControllerTest from 67.18. |
| 67.20 | Add rate tenant isolation tests | 67D | | ~3 tests within existing test files or separate: billing rate in tenant A invisible in tenant B, cost rate in tenant A invisible in tenant B, shared-schema @Filter isolation verification. Pattern: follow `audit/AuditTenantIsolationTest.java`. |
| 67.21 | Verify OrgSettings integration with rate creation defaults | 67D | | ~2 tests: create billing rate without explicit currency — service falls back to OrgSettings defaultCurrency. Create cost rate after updating org default currency — verify new rate uses updated default. This validates the cross-service integration between OrgSettingsService and rate creation flows. |

### Key Files

**Slice 67A — Create:**
- `backend/src/main/resources/db/migration/tenant/V19__create_rate_budget_tables.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsControllerTest.java`

**Slice 67A — Read for context:**
- `backend/src/main/resources/db/migration/tenant/V14__create_audit_events.sql` — RLS pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` — Entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` — Interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java` — Listener
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` — Audit integration

**Slice 67B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateResolutionTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateIntegrationTest.java`

**Slice 67B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectRepository.java` — For customer resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectAccessService.java` — Permission checks
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` — Access control + audit pattern

**Slice 67C — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/CostRate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/CostRateRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/CostRateService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/costrate/CostRateIntegrationTest.java`

**Slice 67C — Read for context:**
- Same entity/audit patterns as 67B (use BillingRate from 67B as direct reference)

**Slice 67D — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/CostRateController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateControllerTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/costrate/CostRateControllerTest.java`

**Slice 67D — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` — Verify endpoint patterns

**Slice 67D — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskController.java` — Controller pattern (RequestScopes, ResponseEntity)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventControllerTest.java` — MockMvc + JWT mock

### Architecture Decisions

- **`settings/` package**: New feature package for OrgSettings. Distinct from existing `billing/` (which handles subscriptions/plans from Phase 2).
- **`billingrate/` package**: Named `billingrate` (not `rate`) to distinguish from cost rates and avoid ambiguity.
- **`costrate/` package**: Separate package from billing rates because they have different scoping rules (no project/customer overrides).
- **V19 migration creates all 4 tables in one file**: Even though tables are used in different slices, a single migration is simpler than coordinating multiple V19/V20/etc. migrations across parallel slices. The tables have no cross-dependencies — each can be created independently. This also matches the architecture doc's suggested V18 approach (adjusted to V19 since V18 is already taken by Phase 7).
- **Four-slice decomposition**: 67A is the foundation (migration + OrgSettings). 67B and 67C are rate entities+services (can run in parallel). 67D is controllers+tests for both rate types (combined because the controllers follow identical patterns and share test setup).
- **`ResolvedRate` record in billingrate package**: Service-level record, not a separate entity. Contains `hourlyRate`, `currency`, `source` (enum: MEMBER_DEFAULT, CUSTOMER_OVERRIDE, PROJECT_OVERRIDE), `billingRateId`.
- **Overlap validation in service layer**: Not a database constraint. The architecture doc explicitly states this — range overlap checks with nullable columns are complex in SQL. Service-level enforcement produces better error messages.

---

## Epic 68: Rate Management Frontend — Settings, Project & Customer Rates

**Goal**: Build the frontend UI for managing billing rates and cost rates. This includes the org-level "Rates & Currency" settings page (member rate table, currency selector), project-level rate overrides tab, and customer-level rate overrides tab.

**References**: Architecture doc Sections 11.8.2 (frontend changes), 11.4.1-11.4.3 (API surface), 11.9 (permission model)

**Dependencies**: Epic 67 (all backend rate APIs must be available)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **68A** | 68.1-68.7 | Settings "Rates & Currency" page: currency selector, member billing rate table, cost rate management, server actions | |
| **68B** | 68.8-68.13 | Project rates tab (project-level overrides), customer rates tab (customer overrides), rate override dialogs | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 68.1 | Create rates settings page route | 68A | | `frontend/app/(app)/org/[slug]/settings/rates/page.tsx` — Server component. Fetches org settings (GET /api/settings), fetches member list, fetches billing rates (GET /api/billing-rates), fetches cost rates (GET /api/cost-rates). Passes data to client components. Restrict to admin/owner via Clerk role check. Pattern: follow `frontend/app/(app)/org/[slug]/settings/billing/page.tsx`. |
| 68.2 | Create rates server actions | 68A | | `frontend/app/(app)/org/[slug]/settings/rates/actions.ts` — Server actions: `updateDefaultCurrency(formData)` calls PUT /api/settings, `createBillingRate(formData)` calls POST /api/billing-rates, `updateBillingRate(id, formData)` calls PUT /api/billing-rates/{id}, `deleteBillingRate(id)` calls DELETE, `createCostRate(formData)`, `updateCostRate(id, formData)`, `deleteCostRate(id)`. All call `revalidatePath`. Pattern: follow `frontend/app/(app)/org/[slug]/team/actions.ts`. |
| 68.3 | Create CurrencySelector component | 68A | | `frontend/components/rates/currency-selector.tsx` — "use client". Searchable dropdown (Shadcn Command component) with common currencies prioritized: ZAR, USD, GBP, EUR, AUD, CAD. Full ISO 4217 list below. Shows currency code + name. Used in rate dialogs and settings page. Pattern: follow Shadcn Command usage from `add-member-dialog.tsx` (uses Command for member search). |
| 68.4 | Create MemberRatesTable component | 68A | | `frontend/components/rates/member-rates-table.tsx` — "use client". Table of all org members with columns: Name, Default Billing Rate, Currency, Effective From, Cost Rate (admin only), Actions. Each row shows active rate for today (or "Not set"). Click to manage opens AddRateDialog. Rate amounts formatted with `Intl.NumberFormat` and currency code. Pattern: follow `frontend/components/team/` table components. |
| 68.5 | Create AddRateDialog and EditRateDialog components | 68A | | `frontend/components/rates/add-rate-dialog.tsx`, `frontend/components/rates/edit-rate-dialog.tsx` — "use client". Dialog for creating/editing billing rates and cost rates. Fields: member (pre-selected or dropdown), hourly rate (number input), currency (CurrencySelector), effective from (date picker), effective to (optional date picker). Billing rate dialog has scope selector (member default / project / customer) — but on the settings page, only member default scope. Show conflict errors (409). Pattern: follow `frontend/components/tasks/log-time-dialog.tsx` for dialog pattern. |
| 68.6 | Add "Rates & Currency" link to settings page | 68A | | Modify `frontend/app/(app)/org/[slug]/settings/page.tsx` — add a link/card for "Rates & Currency" that navigates to `/settings/rates`. Visible to admin/owner only. Pattern: existing settings page has a "Billing & Plan" link. |
| 68.7 | Add rates settings page tests | 68A | | `frontend/__tests__/rates-settings.test.tsx` (~6 tests): renders currency selector, renders member rates table, currency change triggers action, add rate dialog opens/submits, edit rate dialog opens/submits, delete rate with confirmation. Pattern: follow `frontend/__tests__/` existing test files. |
| 68.8 | Create ProjectRatesTab component | 68B | | `frontend/components/rates/project-rates-tab.tsx` — "use client". Table of project-level billing rate overrides. Columns: Member, Hourly Rate, Currency, Effective From-To, Resolved Rate (shows what would apply without override). "Add Override" button opens AddProjectRateDialog. Fetches rates via GET /api/billing-rates?projectId=X. Also calls GET /api/billing-rates/resolve for each member to show the effective rate without the override (for comparison). Pattern: follow `frontend/components/projects/project-members-panel.tsx` tab pattern. |
| 68.9 | Create AddProjectRateDialog component | 68B | | `frontend/components/rates/add-project-rate-dialog.tsx` — "use client". Select member from project members, enter hourly rate, currency, date range. Scope pre-set to PROJECT_OVERRIDE with projectId. On submit calls server action. Shows existing resolved rate for context ("Current rate: $200/hr from member default"). Pattern: follow AddRateDialog from 68.5 (with projectId scope). |
| 68.10 | Integrate project rates tab into project detail page | 68B | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — add "Rates" tab to project tabs (visible to project leads + admin/owner). Add server action for project rate overrides in `frontend/app/(app)/org/[slug]/projects/[id]/rate-actions.ts`. Fetch project billing rates in the page's server component. Pattern: follow existing tab pattern in project-tabs.tsx. |
| 68.11 | Create CustomerRatesTab component | 68B | | `frontend/components/rates/customer-rates-tab.tsx` — "use client". Table of customer-level billing rate overrides. Same structure as ProjectRatesTab but scoped to customerId. Fetches rates via GET /api/billing-rates?customerId=X. "Add Override" button. Pattern: follow ProjectRatesTab from 68.8. |
| 68.12 | Integrate customer rates tab into customer detail page | 68B | | Modify `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — add "Rates" tab. Add `frontend/app/(app)/org/[slug]/customers/[id]/rate-actions.ts` for customer rate server actions. Visible to admin/owner only. Pattern: follow project rates integration from 68.10. |
| 68.13 | Add project/customer rate UI tests | 68B | | `frontend/__tests__/project-rates.test.tsx` (~4 tests): renders project rate overrides table, add override dialog, shows resolved rate comparison. `frontend/__tests__/customer-rates.test.tsx` (~4 tests): renders customer rates table, add/edit customer override. Pattern: follow existing test files. |

### Key Files

**Slice 68A — Create:**
- `frontend/app/(app)/org/[slug]/settings/rates/page.tsx`
- `frontend/app/(app)/org/[slug]/settings/rates/actions.ts`
- `frontend/components/rates/currency-selector.tsx`
- `frontend/components/rates/member-rates-table.tsx`
- `frontend/components/rates/add-rate-dialog.tsx`
- `frontend/components/rates/edit-rate-dialog.tsx`
- `frontend/__tests__/rates-settings.test.tsx`

**Slice 68A — Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — Add "Rates & Currency" link

**Slice 68A — Read for context:**
- `frontend/app/(app)/org/[slug]/settings/billing/page.tsx` — Settings page pattern
- `frontend/components/tasks/log-time-dialog.tsx` — Dialog form pattern
- `frontend/lib/api.ts` — API client usage
- `frontend/lib/format.ts` — `formatDuration`, `formatDate` utilities

**Slice 68B — Create:**
- `frontend/components/rates/project-rates-tab.tsx`
- `frontend/components/rates/add-project-rate-dialog.tsx`
- `frontend/components/rates/customer-rates-tab.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/rate-actions.ts`
- `frontend/app/(app)/org/[slug]/customers/[id]/rate-actions.ts`
- `frontend/__tests__/project-rates.test.tsx`
- `frontend/__tests__/customer-rates.test.tsx`

**Slice 68B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add Rates tab
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Add Rates tab
- `frontend/components/projects/project-tabs.tsx` — Add "Rates" tab definition

### Architecture Decisions

- **`components/rates/` directory**: New component directory for all rate-related components (billing, cost, project overrides, customer overrides). Shared across settings page, project detail, and customer detail.
- **Two-slice frontend split**: 68A covers the org-level settings page (standalone). 68B covers the integration into existing project/customer detail pages (requires modifying existing pages).
- **CurrencySelector as shared component**: Reused in rate dialogs, budget form, and settings page. Uses Shadcn Command for searchable dropdown.
- **Resolved rate comparison**: The project/customer rate override tables show both the override rate and what the resolved rate would be without the override. This helps users understand the effect of their overrides.

---

## Epic 69: TimeEntry Rate Snapshots & Billable Enrichment

**Goal**: Add rate snapshot columns to the TimeEntry entity (V20 migration), modify TimeEntryService to automatically resolve and snapshot billing/cost rates on create/update, add the PATCH billable toggle endpoint, add the admin re-snapshot endpoint, and update TimeEntry API responses to include rate snapshots and computed values.

**References**: Architecture doc Sections 11.2.5, 11.3.2, 11.4.6, 11.4.7, 11.7.2. [ADR-040](../adr/ADR-040-point-in-time-rate-snapshotting.md).

**Dependencies**: Epic 67 (BillingRateService.resolveRate and CostRateService.resolveCostRate must exist)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **69A** | 69.1-69.5 | V20 migration (rate snapshot columns), TimeEntry entity modifications, snapshot-on-create/update logic in TimeEntryService | |
| **69B** | 69.6-69.10 | PATCH billable endpoint, billable filter on list endpoints, admin re-snapshot endpoint, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 69.1 | Create V20 tenant migration for time_entry rate snapshot columns | 69A | | `db/migration/tenant/V20__add_time_entry_rate_snapshots.sql`. ALTER TABLE time_entries ADD COLUMN: `billing_rate_snapshot DECIMAL(12,2)`, `billing_rate_currency VARCHAR(3)`, `cost_rate_snapshot DECIMAL(12,2)`, `cost_rate_currency VARCHAR(3)`. CHECK constraints: `chk_billing_snapshot_currency` (both null or both non-null), `chk_cost_snapshot_currency` (same). Index: `idx_time_entries_billing_currency ON time_entries (billing_rate_currency) WHERE billing_rate_currency IS NOT NULL`. Note: `rate_cents` NOT removed — deprecated only. See Section 11.7.2. Pattern: follow `V15__create_comments.sql` ALTER TABLE pattern. |
| 69.2 | Modify TimeEntry entity with rate snapshot fields | 69A | | Modify `timeentry/TimeEntry.java`. Add fields: `BigDecimal billingRateSnapshot` (precision=12, scale=2, nullable), `String billingRateCurrency` (VARCHAR(3), nullable), `BigDecimal costRateSnapshot` (same), `String costRateCurrency` (same). Add `@Deprecated` annotation on existing `rateCents` field. Add methods: `snapshotBillingRate(BigDecimal rate, String currency)`, `snapshotCostRate(BigDecimal rate, String currency)`, `setBillable(boolean billable)`. Add computed getters (not stored): `getBillableValue()` and `getCostValue()` — returns `BigDecimal` or null. |
| 69.3 | Modify TimeEntryService to snapshot rates on create | 69A | | Modify `timeentry/TimeEntryService.java`. In `createTimeEntry()`: after task/project validation, call `billingRateService.resolveRate(memberId, projectId, date)` and `costRateService.resolveCostRate(memberId, date)`. If resolved, call `entry.snapshotBillingRate(rate.hourlyRate(), rate.currency())` and same for cost. Inject `BillingRateService` and `CostRateService` as new constructor parameters. |
| 69.4 | Modify TimeEntryService to re-snapshot on date/project change | 69A | | Modify `timeentry/TimeEntryService.java`. In `updateTimeEntry()`: detect if `date` or `taskId` (which implies project change) has changed. If so, re-resolve billing and cost rates for new context and update snapshots. Log rate snapshot changes in audit delta: `{"billing_rate_snapshot": {"from": "150.00", "to": "175.00"}}`. If only duration/description/billable changed, do NOT re-snapshot. |
| 69.5 | Add rate snapshot integration tests | 69A | | `timeentry/TimeEntryRateSnapshotTest.java` (~7 tests): create time entry with configured rate — verify snapshot populated, create entry with no rate configured — verify snapshot null, update entry date to different rate period — verify re-snapshot, update entry description only — verify snapshot unchanged, entry with both billing and cost rates — verify both snapshots, computed billableValue and costValue correct, billable=false entry has null billableValue but non-null costValue. Seed: provision tenant, create billing rate, create cost rate, create project+task. Pattern: follow `timeentry/TimeEntryIntegrationTest.java`. |
| 69.6 | Add PATCH billable toggle endpoint to TimeEntryController | 69B | | Modify `timeentry/TimeEntryController.java`. Add `PATCH /api/projects/{projectId}/time-entries/{id}/billable` endpoint. Request DTO: `ToggleBillableRequest(boolean billable)`. Permission: entry creator, project lead, admin/owner. Updates billable flag, does NOT re-snapshot rates. Returns updated time entry. Add billable field to existing create/update DTOs (default true). |
| 69.7 | Add billable filter to time entry list endpoint | 69B | | Modify `timeentry/TimeEntryController.java` and `TimeEntryRepository.java`. Add optional `billable` query parameter to `GET /api/projects/{projectId}/time-entries`. Repository: add `findByProjectIdAndBillable(UUID projectId, Boolean billable, Pageable pageable)` or modify existing query to accept optional billable filter. |
| 69.8 | Update TimeEntry response DTOs with rate snapshot fields | 69B | | Modify `timeentry/TimeEntryController.java` response DTO. Add fields: `billable`, `billingRateSnapshot`, `billingRateCurrency`, `costRateSnapshot`, `costRateCurrency`, `billableValue` (computed), `costValue` (computed). Deprecate `rateCents` in response (keep for backward compatibility but exclude from new clients). Pattern: computed values are calculated in the mapping from entity to DTO. |
| 69.9 | Create admin re-snapshot endpoint | 69B | | Create `timeentry/AdminTimeEntryController.java` — `@RestController`, `@RequestMapping("/api/admin/time-entries")`. `POST /re-snapshot` endpoint. Request: `ReSnapshotRequest(UUID projectId, UUID memberId, LocalDate fromDate, LocalDate toDate)` — at least one filter required. Service method: query matching time entries, re-resolve rates for each, update snapshots, count processed/updated/skipped. Returns `ReSnapshotResponse(int entriesProcessed, int entriesUpdated, int entriesSkipped)`. Publishes single audit event `time_entry.rate_re_snapshot`. Admin/owner only. See Section 11.4.7. |
| 69.10 | Add billable and re-snapshot integration tests | 69B | | Additional tests in `timeentry/TimeEntryBillableTest.java` (~6 tests): PATCH toggle billable to false, PATCH toggle back to true, PATCH by non-authorized user (rejected), create entry with billable=false (verify no billableValue), list filter by billable=true, re-snapshot endpoint updates historical entries. `timeentry/AdminReSnapshotTest.java` (~4 tests): re-snapshot updates entries that had null snapshots, re-snapshot skips entries with same rate, re-snapshot with project filter, non-admin rejected. Pattern: follow existing test patterns. |

### Key Files

**Slice 69A — Create:**
- `backend/src/main/resources/db/migration/tenant/V20__add_time_entry_rate_snapshots.sql`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRateSnapshotTest.java`

**Slice 69A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` — Add snapshot fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` — Add rate resolution calls

**Slice 69A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java` — resolveRate() method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/costrate/CostRateService.java` — resolveCostRate() method
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` — Delta logging

**Slice 69B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/AdminTimeEntryController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryBillableTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/AdminReSnapshotTest.java`

**Slice 69B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java` — PATCH endpoint, billable filter, response DTOs
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — Billable filter query

### Architecture Decisions

- **V20 migration separate from V19**: The rate snapshot columns are an ALTER TABLE on an existing table (`time_entries`), which is conceptually distinct from the V19 CREATE TABLE operations. Separate migrations are cleaner and allow independent rollback.
- **`rateCents` deprecated, not removed**: Backward compatibility. Annotated `@Deprecated` in entity. Excluded from new DTO responses. Removed in a future migration post-Phase 8.
- **Computed values not stored**: `billableValue` and `costValue` are derived on read (`duration * rate`), not stored as columns. Keeps the schema simpler and avoids data staleness.
- **AdminTimeEntryController separate from TimeEntryController**: The re-snapshot endpoint is an admin-only operational tool, not a standard CRUD operation. Separate controller keeps the standard controller clean.

---

## Epic 70: TimeEntry Frontend — Billable UX & Rate Preview

**Goal**: Update the frontend time entry UI to support the billable flag and rate snapshots. Add a "Billable" checkbox to LogTimeDialog, show resolved rate preview, add billable indicator/filter to TimeEntryList, and show rate snapshot in EditTimeEntryDialog.

**References**: Architecture doc Section 11.8.2 (modified components), Section 11.4.6 (TimeEntry API changes)

**Dependencies**: Epic 69 (time entry API must return billable and snapshot fields)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **70A** | 70.1-70.6 | Billable checkbox in LogTimeDialog, rate preview, billable indicator in list, filter toggle, edit dialog updates, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 70.1 | Update LogTimeDialog with billable checkbox and rate preview | 70A | | Modify `frontend/components/tasks/log-time-dialog.tsx`. Add "Billable" checkbox (default checked). Below duration field, call GET /api/billing-rates/resolve?memberId=X&projectId=Y&date=Z to show resolved rate. Display computed value: "2.5h x R1,800/hr = R4,500 ZAR". Use `Intl.NumberFormat` for currency formatting. Rate preview updates live as user types duration or changes date. If no rate resolved, show "No billing rate configured". Pattern: add to existing dialog form fields. |
| 70.2 | Create rate preview server action | 70A | | `frontend/app/(app)/org/[slug]/projects/[id]/rate-actions.ts` (may merge with existing actions file). Add `resolveRate(memberId, projectId, date)` action that calls GET /api/billing-rates/resolve. Returns `{ hourlyRate, currency, source }` or null. Used by LogTimeDialog for live rate preview. Pattern: follow existing server actions. |
| 70.3 | Update TimeEntryList with billable indicator and filter | 70A | | Modify `frontend/components/tasks/time-entry-list.tsx`. Add billable indicator column: small icon (check for billable, dash for non-billable) or badge. Add filter toggle above the list: "All / Billable / Non-billable" using Shadcn Tabs or button group. Filter state passed to list fetch. Show `billableValue` and rate snapshot inline where available. Pattern: follow existing column structure. |
| 70.4 | Update EditTimeEntryDialog with billable flag | 70A | | Modify `frontend/components/tasks/edit-time-entry-dialog.tsx`. Add "Billable" checkbox (editable). Show rate snapshot as read-only context: "Billing rate: R1,800/hr ZAR (project override)". Show cost rate snapshot (if user has permission). Rate snapshots are not editable — they are set by the system. Pattern: add fields to existing dialog. |
| 70.5 | Add currency formatting utility | 70A | | Add `formatCurrency(amount: number | string, currency: string): string` to `frontend/lib/format.ts`. Uses `Intl.NumberFormat` with the currency code. Always show currency code suffix: "R1,800.00 ZAR", "$500.00 USD". Handles null/undefined gracefully (returns "N/A"). Pattern: extend existing `formatDuration` pattern in format.ts. |
| 70.6 | Add time entry billable UI tests | 70A | | `frontend/__tests__/time-entry-billable.test.tsx` (~6 tests): billable checkbox renders checked by default, unchecking billable hides rate preview, rate preview shows resolved rate, billable indicator in list, filter toggle works, edit dialog shows rate snapshot read-only. Pattern: follow existing `frontend/__tests__/` test files. |

### Key Files

**Slice 70A — Create:**
- `frontend/__tests__/time-entry-billable.test.tsx`

**Slice 70A — Modify:**
- `frontend/components/tasks/log-time-dialog.tsx` — Billable checkbox, rate preview
- `frontend/components/tasks/time-entry-list.tsx` — Billable indicator, filter toggle
- `frontend/components/tasks/edit-time-entry-dialog.tsx` — Billable flag, rate snapshot display
- `frontend/lib/format.ts` — Add `formatCurrency` utility
- `frontend/app/(app)/org/[slug]/projects/[id]/time-entry-actions.ts` — Add rate resolution action (or separate rate-actions.ts)

**Slice 70A — Read for context:**
- `frontend/components/tasks/log-time-dialog.tsx` — Existing dialog structure
- `frontend/components/tasks/time-entry-list.tsx` — Existing list structure
- `frontend/lib/format.ts` — Existing formatting utilities
- `frontend/lib/api.ts` — API client

### Architecture Decisions

- **Single slice**: This epic modifies existing components (not creating new pages), so the surface area is contained. The billable checkbox, rate preview, and list indicator are all interconnected changes to the same component set.
- **Live rate preview**: The LogTimeDialog calls the resolve endpoint on input change (debounced). This provides immediate feedback without waiting for the form to submit. The resolved rate is purely informational — the actual snapshot is set server-side.
- **`formatCurrency` in shared lib**: Not per-component — the same formatting is needed across rates, budgets, and profitability views.

---

## Epic 71: Project Budgets — Entity, Status & Alerts

**Goal**: Implement the ProjectBudget entity with CRUD API, budget status computation from time entry aggregates, and budget threshold alert notifications via the existing ApplicationEvent pipeline.

**References**: Architecture doc Sections 11.2.4, 11.3.3, 11.3.4, 11.4.4. [ADR-042](../adr/ADR-042-single-budget-per-project.md).

**Dependencies**: Epic 69 (time entries must have rate snapshot columns for monetary budget calculation)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **71A** | 71.1-71.6 | ProjectBudget entity, repository, service with status calculation, controller, budget CRUD tests | |
| **71B** | 71.7-71.11 | BudgetCheckService, BudgetThresholdEvent, NotificationEventHandler integration, alert deduplication, alert tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 71.1 | Create ProjectBudget entity | 71A | | `budget/ProjectBudget.java` — JPA entity mapped to `project_budgets`. Fields per Section 11.2.4: UUID id, UUID projectId (NOT NULL, UNIQUE), BigDecimal budgetHours (nullable, precision=10 scale=2), BigDecimal budgetAmount (nullable, precision=14 scale=2), String budgetCurrency (nullable, VARCHAR(3)), int alertThresholdPct (NOT NULL, default 80), boolean thresholdNotified (NOT NULL, default false), String notes (nullable), String tenantId, Instant createdAt, Instant updatedAt. Full `@FilterDef`/`@Filter`/`TenantAware` pattern. Constructor. `updateBudget(...)` method — resets `thresholdNotified` to false when budget values change. `resetThresholdNotified()`. `markThresholdNotified()`. Pattern: follow `timeentry/TimeEntry.java`. |
| 71.2 | Create ProjectBudgetRepository | 71A | | `budget/ProjectBudgetRepository.java` — extends `JpaRepository<ProjectBudget, UUID>`. Methods: `Optional<ProjectBudget> findOneById(UUID id)` (JPQL), `Optional<ProjectBudget> findByProjectId(UUID projectId)` (JPQL). Pattern: follow existing repo patterns. |
| 71.3 | Create BudgetStatus record and computation logic | 71A | | `budget/BudgetStatus.java` — record with: `UUID projectId`, `BigDecimal budgetHours`, `BigDecimal budgetAmount`, `String budgetCurrency`, `int alertThresholdPct`, `String notes`, `BigDecimal hoursConsumed`, `BigDecimal hoursRemaining`, `BigDecimal hoursConsumedPct`, `BigDecimal amountConsumed`, `BigDecimal amountRemaining`, `BigDecimal amountConsumedPct`, `BudgetStatusEnum hoursStatus`, `BudgetStatusEnum amountStatus`, `BudgetStatusEnum overallStatus`. `BudgetStatusEnum` enum: ON_TRACK, AT_RISK, OVER_BUDGET. Computation helper: `statusFromPct(BigDecimal pct, int threshold)` — ON_TRACK if < threshold, AT_RISK if >= threshold and < 100, OVER_BUDGET if >= 100. Overall = worse of the two. |
| 71.4 | Create ProjectBudgetService | 71A | | `budget/ProjectBudgetService.java`. Inject: `ProjectBudgetRepository`, `TimeEntryRepository`, `ProjectAccessService`, `AuditService`. Methods: (1) `getBudgetWithStatus(UUID projectId, UUID memberId, String orgRole)` — loads budget (404 if none), computes hours consumed (native SQL SUM of all time entries for project), computes amount consumed (native SQL SUM of billable entries where billing_rate_currency matches budget_currency), derives status. (2) `getBudgetStatusOnly(UUID projectId, ...)` — lightweight version for dashboards. (3) `upsertBudget(UUID projectId, request, UUID memberId, String orgRole)` — lead/admin/owner via ProjectAccessService, creates or updates, resets thresholdNotified on value change, audit event. (4) `deleteBudget(UUID projectId, UUID memberId, String orgRole)` — audit event. Validation: at least one of budgetHours/budgetAmount non-null. budgetCurrency required if budgetAmount set. Pattern: follow `task/TaskService.java`. |
| 71.5 | Create ProjectBudgetController | 71A | | `budget/ProjectBudgetController.java` — `@RestController`, `@RequestMapping("/api/projects/{projectId}/budget")`. Inner DTOs: `UpsertBudgetRequest(BigDecimal budgetHours, BigDecimal budgetAmount, @Size(min=3,max=3) String budgetCurrency, Integer alertThresholdPct, String notes)`, `BudgetStatusResponse(...)`. Endpoints per Section 11.4.4: `GET /` (200 or 404), `GET /status` (lightweight), `PUT /` (upsert, 200), `DELETE /` (204 or 404). Permission checks via RequestScopes. Pattern: follow `task/TaskController.java`. |
| 71.6 | Add ProjectBudget CRUD + status integration tests | 71A | | `budget/ProjectBudgetIntegrationTest.java` (~10 tests): create budget (hours only), create budget (amount only), create budget (both), get budget with status (hours consumed matches time entries), amount consumed only counts matching currency entries, status ON_TRACK when under threshold, status AT_RISK when over threshold, status OVER_BUDGET at 100%, upsert updates existing budget, delete budget. Seed: provision tenant, create project, create tasks, create time entries with rate snapshots. Pattern: follow `timeentry/TimeEntryIntegrationTest.java`. |
| 71.7 | Create BudgetCheckService | 71B | | `budget/BudgetCheckService.java`. Inject: `ProjectBudgetRepository`, `TimeEntryRepository`, `ApplicationEventPublisher`. Method: `checkAndAlert(UUID projectId, UUID actorMemberId, String actorName, String tenantId, String orgId)`. Logic per Section 11.3.4: load budget, if none return, if thresholdNotified return, compute consumption, if threshold crossed: set thresholdNotified=true, save, publish BudgetThresholdEvent. Called from TimeEntryService after create/update. |
| 71.8 | Create BudgetThresholdEvent | 71B | | `event/BudgetThresholdEvent.java` — record implementing `DomainEvent`. Fields: eventType ("budget.threshold_reached"), entityType ("project_budget"), UUID entityId, UUID projectId, UUID actorMemberId, String actorName, String tenantId, String orgId, Instant occurredAt, Map details (project_name, dimension, consumed_pct). Update `DomainEvent` sealed interface permits list to include BudgetThresholdEvent. |
| 71.9 | Integrate BudgetCheckService into TimeEntryService | 71B | | Modify `timeentry/TimeEntryService.java`. After `createTimeEntry()` persists: call `budgetCheckService.checkAndAlert(projectId, memberId, memberName, tenantId, orgId)`. Same after `updateTimeEntry()` if duration or date changed. Inject BudgetCheckService as new constructor parameter. |
| 71.10 | Add BUDGET_ALERT handling to NotificationEventHandler | 71B | | Modify `notification/NotificationEventHandler.java`. Add `@EventListener` method for `BudgetThresholdEvent`. Recipients: project leads + org admins/owners (query from ProjectMemberRepository + MemberRepository). Check notification preferences for `BUDGET_ALERT` type. Create Notification rows with type `BUDGET_ALERT`, title template per Section 11.3.4. Also add default `NotificationPreference` for `BUDGET_ALERT` type (in-app enabled, email disabled). |
| 71.11 | Add budget alert integration tests | 71B | | `budget/BudgetAlertNotificationTest.java` (~6 tests): time entry crosses threshold — notification created for leads/admins, duplicate alert prevented (thresholdNotified=true), budget update resets thresholdNotified, subsequent time entry after reset triggers new alert, alert not sent when preference disabled, no alert when budget does not exist. Seed: extend ProjectBudgetIntegrationTest setup. Pattern: follow `notification/` test patterns. |

### Key Files

**Slice 71A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudget.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudgetRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudgetService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudgetController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudgetIntegrationTest.java`

**Slice 71A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — For aggregation queries
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectAccessService.java` — Permission checks
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` — Snapshot fields for amount calculation

**Slice 71B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetCheckService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/BudgetThresholdEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/budget/BudgetAlertNotificationTest.java`

**Slice 71B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` — Add BudgetCheckService call
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java` — Add BUDGET_ALERT handler
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java` — Add BudgetThresholdEvent to permits

**Slice 71B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java` — Existing event handling pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java` — Sealed interface pattern

### Architecture Decisions

- **`budget/` package**: New feature package. Contains entity, repo, service, controller, and BudgetCheckService.
- **Two-slice split**: 71A (entity + CRUD + status calculation) and 71B (alert logic + notification integration). The alert logic modifies existing notification infrastructure and is conceptually separate from budget CRUD.
- **Status computed on read**: No stored status column. Consistent with ADR-022 (on-the-fly aggregation). Budget status = derived from time entry SUM queries every time it is read.
- **BudgetCheckService as separate service**: Not inline in ProjectBudgetService. Called from TimeEntryService after mutations. Clean separation of concerns — budget CRUD vs. budget alerting.
- **Native SQL for budget consumption queries**: The SUM/JOIN queries for hours and amount consumed use native SQL for performance, consistent with existing profitability query patterns. Uses `CAST(:param AS DATE)` for nullable parameters per lessons learned.

---

## Epic 72: Budget Frontend — Configuration & Status Visualization

**Goal**: Build the frontend Budget tab on the project detail page with budget configuration form, progress bars with color-coded status indicators, and consumption breakdown.

**References**: Architecture doc Section 11.8.2 (Budget tab description), 11.4.4 (Budget API)

**Dependencies**: Epic 71 (budget backend API must be available)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **72A** | 72.1-72.6 | Budget tab on project detail, configuration form, progress bars, status indicators, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 72.1 | Create BudgetPanel component | 72A | | `frontend/components/budget/budget-panel.tsx` — "use client". Displays budget status: dual progress bars for hours and amount. Color-coded: green bg for ON_TRACK, amber for AT_RISK, red for OVER_BUDGET. Shows consumed/remaining/percentage. Uses `Intl.NumberFormat` with `formatCurrency()` from format.ts for amount display. Handles cases where only hours or only amount budget is set. "No budget set" empty state with "Set Budget" button. Pattern: follow `frontend/components/projects/time-summary-panel.tsx` for panel layout. |
| 72.2 | Create BudgetConfigDialog component | 72A | | `frontend/components/budget/budget-config-dialog.tsx` — "use client". Shadcn Dialog for creating/editing budget. Fields: budget hours (number input, optional), budget amount (number input, optional), budget currency (CurrencySelector from Epic 68, required if amount set), alert threshold slider (50-100%, default 80%), notes (textarea, optional). Validation: at least one of hours/amount must be set. On submit calls server action (PUT /api/projects/{projectId}/budget). Pattern: follow `frontend/components/tasks/log-time-dialog.tsx`. |
| 72.3 | Create DeleteBudgetDialog component | 72A | | `frontend/components/budget/delete-budget-dialog.tsx` — "use client". AlertDialog confirmation for budget deletion. On confirm calls server action (DELETE). Pattern: follow `frontend/components/projects/delete-project-dialog.tsx`. |
| 72.4 | Create budget server actions | 72A | | `frontend/app/(app)/org/[slug]/projects/[id]/budget-actions.ts` — Server actions: `getBudgetStatus(projectId)` calls GET /api/projects/{projectId}/budget, `upsertBudget(projectId, formData)` calls PUT, `deleteBudget(projectId)` calls DELETE. Handle 404 gracefully (no budget set). Pattern: follow `frontend/app/(app)/org/[slug]/projects/[id]/time-entry-actions.ts`. |
| 72.5 | Integrate Budget tab into project detail page | 72A | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — add "Budget" tab to project tabs. Server component fetches budget status (GET /api/projects/{projectId}/budget). Tab content renders BudgetPanel with configure/edit/delete actions. Visible to all project members (view), edit restricted to lead/admin/owner (check role). Modify `frontend/components/projects/project-tabs.tsx` to add "Budget" tab. Pattern: follow existing Time tab integration. |
| 72.6 | Add budget UI tests | 72A | | `frontend/__tests__/budget-panel.test.tsx` (~6 tests): renders progress bars with correct percentages, ON_TRACK green styling, AT_RISK amber styling, OVER_BUDGET red styling, "No budget set" empty state, config dialog opens and submits. Pattern: follow existing test files. |

### Key Files

**Slice 72A — Create:**
- `frontend/components/budget/budget-panel.tsx`
- `frontend/components/budget/budget-config-dialog.tsx`
- `frontend/components/budget/delete-budget-dialog.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/budget-actions.ts`
- `frontend/__tests__/budget-panel.test.tsx`

**Slice 72A — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add Budget tab
- `frontend/components/projects/project-tabs.tsx` — Add "Budget" tab definition

**Slice 72A — Read for context:**
- `frontend/components/projects/time-summary-panel.tsx` — Panel layout pattern
- `frontend/components/tasks/log-time-dialog.tsx` — Dialog form pattern
- `frontend/components/rates/currency-selector.tsx` — Shared CurrencySelector (from Epic 68)
- `frontend/lib/format.ts` — `formatCurrency` (from Epic 70)

### Architecture Decisions

- **`components/budget/` directory**: New component directory for budget-related components.
- **Single slice**: Budget frontend is self-contained. BudgetPanel + BudgetConfigDialog + server actions + tests fits within the 8-12 file limit.
- **CurrencySelector reuse**: The budget currency selector reuses the same CurrencySelector component from Epic 68. This ensures consistent currency selection UX across the app.
- **Progress bar styling**: Uses Tailwind utility classes for color-coding. No new CSS variables or custom animation needed — standard Shadcn Progress component with conditional color classes.

---

## Epic 73: Profitability Backend — Reports & Aggregation Queries

**Goal**: Implement the profitability query endpoints: project profitability, customer profitability, team utilization, and org-wide profitability. All are query-derived (no new entities), using native SQL aggregation queries grouped by currency.

**References**: Architecture doc Sections 11.3.5, 11.4.5, 11.8.1 (report package), 11.8.3 (testing strategy). [ADR-043](../adr/ADR-043-margin-aware-profitability.md), [ADR-041](../adr/ADR-041-multi-currency-store-in-original.md).

**Dependencies**: Epic 69 (time entries must have rate snapshot columns for monetary aggregation)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **73A** | 73.1-73.6 | Project profitability + customer profitability endpoints, ReportService, native SQL queries, projection interfaces | |
| **73B** | 73.7-73.12 | Team utilization + org profitability endpoints, self-service access, remaining tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 73.1 | Create profitability projection interfaces | 73A | | `report/` package. Create Spring Data projection interfaces: `RevenueCurrencyProjection(String getCurrency(), BigDecimal getTotalBillableHours(), BigDecimal getTotalNonBillableHours(), BigDecimal getTotalHours(), BigDecimal getBillableValue())`, `CostCurrencyProjection(String getCurrency(), BigDecimal getCostValue())`. These match native SQL column aliases exactly. Also: `ProjectRevenueSummary` (adds UUID getProjectId(), String getProjectName()), `MemberUtilizationProjection(UUID getMemberId(), String getMemberName(), BigDecimal getTotalHours(), BigDecimal getBillableHours(), BigDecimal getNonBillableHours())`. Pattern: follow `timeentry/ProjectTimeSummaryProjection.java`. |
| 73.2 | Create ReportRepository with native SQL queries | 73A | | `report/ReportRepository.java` — custom repository (not a JpaRepository — no entity to bind). Use `@Repository` with injected `EntityManager` for native queries. Methods: `getProjectRevenue(UUID projectId, LocalDate from, LocalDate to)` — revenue query per Section 11.3.5 grouped by billing_rate_currency. `getProjectCost(UUID projectId, LocalDate from, LocalDate to)` — cost query grouped by cost_rate_currency. `getCustomerRevenue(UUID customerId, LocalDate from, LocalDate to)` — revenue via customer_projects join. `getCustomerCost(UUID customerId, LocalDate from, LocalDate to)`. All use `CAST(:param AS DATE)` for nullable date params. Pattern: follow native SQL patterns from `timeentry/TimeEntryRepository.java`. |
| 73.3 | Create ReportService for project + customer profitability | 73A | | `report/ReportService.java`. Inject: `ReportRepository`, `ProjectAccessService`, `CustomerRepository`. Methods: (1) `getProjectProfitability(UUID projectId, LocalDate from, LocalDate to, UUID memberId, String orgRole)` — project lead/admin/owner check via ProjectAccessService. Executes revenue + cost queries. Merges results by currency. Computes margin only where billing and cost currencies match. Returns `ProjectProfitabilityResponse`. (2) `getCustomerProfitability(UUID customerId, LocalDate from, LocalDate to, UUID memberId, String orgRole)` — admin/owner only. Same dual-query + merge pattern. Returns `CustomerProfitabilityResponse`. |
| 73.4 | Create ReportController with project + customer endpoints | 73A | | `report/ReportController.java` — `@RestController`. Inner DTOs: `CurrencyBreakdown(String currency, BigDecimal totalBillableHours, BigDecimal totalNonBillableHours, BigDecimal totalHours, BigDecimal billableValue, BigDecimal costValue, BigDecimal margin, BigDecimal marginPercent)`, `ProjectProfitabilityResponse(UUID projectId, String projectName, List<CurrencyBreakdown> currencies)`, `CustomerProfitabilityResponse(UUID customerId, String customerName, List<CurrencyBreakdown> currencies)`. Endpoints: `GET /api/projects/{projectId}/profitability` (from, to params), `GET /api/customers/{customerId}/profitability` (from, to params). Margin/marginPercent null when cost is null or currencies mismatch. Pattern: follow `timeentry/ProjectTimeSummaryController.java`. |
| 73.5 | Add project profitability integration tests | 73A | | `report/ProjectProfitabilityTest.java` (~8 tests): project with all billable entries — correct revenue, project with mixed billable/non-billable — only billable in revenue, multi-currency project shows per-currency breakdown, date range filtering, margin calculation (revenue - cost), marginPercent null when no cost rates, empty result for project with no time entries, permission: regular member rejected. Seed: provision tenant, create rates, create entries with varying billable/currency/dates. Pattern: follow `timeentry/TimeEntryIntegrationTest.java`. |
| 73.6 | Add customer profitability integration tests | 73A | | `report/CustomerProfitabilityTest.java` (~6 tests): aggregates across multiple projects for one customer, date range filtering, multi-currency grouping, customer with no projects returns empty, non-admin rejected, margin N/A when no cost configured. Seed: extend from 73.5 setup with customer+project linkage. |
| 73.7 | Add utilization queries to ReportRepository | 73B | | Extend `report/ReportRepository.java`. Methods: `getMemberUtilization(LocalDate from, LocalDate to)` — native SQL per Section 11.3.5 (SUM duration grouped by member, split billable/non-billable), `getMemberUtilization(UUID memberId, LocalDate from, LocalDate to)` — filtered to one member, `getMemberBillableValues(LocalDate from, LocalDate to)` — per-member per-currency billable/cost values for the value breakdown. |
| 73.8 | Add org profitability queries to ReportRepository | 73B | | Extend `report/ReportRepository.java`. Methods: `getOrgProjectRevenue(LocalDate from, LocalDate to, UUID customerId)` — revenue grouped by project + billing_rate_currency, `getOrgProjectCost(LocalDate from, LocalDate to, UUID customerId)` — cost grouped by project + cost_rate_currency. Optional customerId filter via customer_projects join. |
| 73.9 | Add utilization + org profitability to ReportService | 73B | | Extend `report/ReportService.java`. Methods: (1) `getUtilization(LocalDate from, LocalDate to, UUID memberId, UUID requestingMemberId, String orgRole)` — admin/owner sees all, regular member sees self only (`memberId` must match `requestingMemberId` or throw ForbiddenException). Computes utilizationPercent = billableHours / totalHours * 100. (2) `getOrgProfitability(LocalDate from, LocalDate to, UUID customerId, UUID memberId, String orgRole)` — admin/owner only. Dual-query, merge by project+currency, compute margin. Sort by margin DESC. |
| 73.10 | Add utilization + org profitability endpoints to ReportController | 73B | | Extend `report/ReportController.java`. DTOs: `MemberUtilizationRecord(UUID memberId, String memberName, BigDecimal totalHours, BigDecimal billableHours, BigDecimal nonBillableHours, BigDecimal utilizationPercent, List<CurrencyBreakdown> currencies)`, `UtilizationResponse(LocalDate from, LocalDate to, List<MemberUtilizationRecord> members)`, `ProjectProfitabilitySummary(UUID projectId, String projectName, String customerName, String currency, BigDecimal billableHours, BigDecimal billableValue, BigDecimal costValue, BigDecimal margin, BigDecimal marginPercent)`, `OrgProfitabilityResponse(List<ProjectProfitabilitySummary> projects)`. Endpoints: `GET /api/reports/utilization` (from, to required, memberId optional), `GET /api/reports/profitability` (from, to, customerId optional). |
| 73.11 | Add utilization integration tests | 73B | | `report/UtilizationReportTest.java` (~6 tests): team utilization shows all members, utilization percentage calculated correctly, self-service: member queries own utilization only, admin queries all members, date range filtering, member with no entries shows zero. Seed: extend profitability test setup. |
| 73.12 | Add org profitability integration tests | 73B | | `report/OrgProfitabilityTest.java` (~4 tests): shows all projects sorted by margin, customerId filter narrows to linked projects, margin N/A for projects without cost rates, non-admin rejected. |

### Key Files

**Slice 73A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/RevenueCurrencyProjection.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/CostCurrencyProjection.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/report/ProjectProfitabilityTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/report/CustomerProfitabilityTest.java`

**Slice 73A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/ProjectTimeSummaryProjection.java` — Projection interface pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/ProjectTimeSummaryController.java` — Report controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectAccessService.java` — Permission checks

**Slice 73B — Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/report/UtilizationReportTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/report/OrgProfitabilityTest.java`

**Slice 73B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportRepository.java` — Add utilization + org queries
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java` — Add utilization + org methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportController.java` — Add utilization + org endpoints

### Architecture Decisions

- **`report/` package**: New feature package. Contains only service, controller, repository, and projection interfaces — no entities (all data is query-derived).
- **Custom repository, not JpaRepository**: ReportRepository uses `EntityManager` for native SQL queries because these are cross-entity aggregation queries, not single-entity CRUD.
- **Dual-query approach**: Revenue and cost are queried separately and merged in the service layer. This avoids complex SQL that would need to align billing and cost currencies in a single query.
- **`CAST(:param AS DATE)` for nullable params**: Per lessons learned, PostgreSQL cannot infer parameter types in `IS NULL` within native queries.
- **Two-slice split**: 73A (project + customer profitability) and 73B (utilization + org profitability). The utilization endpoint has additional self-service access control logic that warrants its own slice.
- **Margin null when currencies mismatch**: Per ADR-041. The API returns both revenue and cost values with their respective currencies, but margin is null if they differ. Frontend shows "N/A".

---

## Epic 74: Profitability & Financials Frontend — Pages & Tabs

**Goal**: Build the Profitability page (sidebar nav, utilization table, project profitability table, customer profitability table), the Project Financials tab (profitability + budget integration), and the Customer Financials tab (customer lifetime profitability).

**References**: Architecture doc Section 11.8.2 (new pages, new tabs, modified components)

**Dependencies**: Epic 73 (profitability APIs), Epic 72 (budget frontend for integration), Epic 68 (rate management frontend for navigation consistency)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **74A** | 74.1-74.6 | Profitability page: sidebar nav, utilization section, project profitability table with currency grouping | |
| **74B** | 74.7-74.11 | Project financials tab (profitability + budget panel side-by-side), customer profitability section in profitability page | |
| **74C** | 74.12-74.16 | Customer financials tab on customer detail, project list budget status indicator | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 74.1 | Create profitability page route | 74A | | `frontend/app/(app)/org/[slug]/profitability/page.tsx` — Server component. Restricted to admin/owner. Fetches utilization data (GET /api/reports/utilization with default date range: current month) and org profitability (GET /api/reports/profitability). Renders UtilizationTable and ProjectProfitabilityTable. Date range picker at top. Pattern: follow `frontend/app/(app)/org/[slug]/my-work/page.tsx` for report-style page. |
| 74.2 | Create profitability server actions | 74A | | `frontend/app/(app)/org/[slug]/profitability/actions.ts` — Server actions: `getUtilization(from, to)`, `getOrgProfitability(from, to, customerId)`, `getProjectProfitability(projectId, from, to)`, `getCustomerProfitability(customerId, from, to)`. All call corresponding API endpoints via `api.ts`. Pattern: follow existing server action files. |
| 74.3 | Create UtilizationTable component | 74A | | `frontend/components/profitability/utilization-table.tsx` — "use client". Table of team members with columns: Name, Total Hours, Billable Hours, Non-Billable Hours, Utilization %. Utilization shown as progress bar (billable/total). Sortable by utilization %, billable hours, total hours. Per-currency value breakdown expandable per row. Date range picker triggers refetch. Pattern: follow Shadcn DataTable or simple table with sorting. |
| 74.4 | Create ProjectProfitabilityTable component | 74A | | `frontend/components/profitability/project-profitability-table.tsx` — "use client". Table of projects ranked by margin. Columns: Project, Customer, Currency, Billable Hours, Revenue, Cost, Margin, Margin %. Sorted by margin DESC by default. One row per project+currency combination. "N/A" badge for margin when cost data missing. Currency amounts formatted with `formatCurrency()`. Pattern: follow existing table components. |
| 74.5 | Add "Profitability" item to sidebar navigation | 74A | | Modify `frontend/lib/nav-items.ts` — add profitability nav item: `{ label: "Profitability", href: "/profitability", icon: TrendingUp, roles: ["admin", "owner"] }`. Visible to admin/owner only. Pattern: follow existing nav item definitions. Also add "Reports" section header if not present. |
| 74.6 | Add profitability page tests | 74A | | `frontend/__tests__/profitability-page.test.tsx` (~6 tests): renders utilization table, renders project profitability table, date range picker triggers refetch, sortable columns, N/A badge for missing margin, admin-only access. Pattern: follow existing test files. |
| 74.7 | Create ProjectFinancialsTab component | 74B | | `frontend/components/profitability/project-financials-tab.tsx` — "use client". Displays project profitability data inline: per-currency breakdown with billable value, cost, margin, hours breakdown. If budget exists, shows BudgetPanel (from Epic 72) side-by-side or stacked below profitability. Visible to project leads and admins/owners. Pattern: follow `frontend/components/projects/time-summary-panel.tsx` for layout. |
| 74.8 | Integrate Financials tab into project detail page | 74B | | Modify `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — add "Financials" tab. Server component fetches project profitability (GET /api/projects/{projectId}/profitability) and budget status. Tab content renders ProjectFinancialsTab. Permission: lead/admin/owner. Modify `frontend/components/projects/project-tabs.tsx` to add "Financials" tab. Pattern: follow existing tab integrations. |
| 74.9 | Create CustomerProfitabilitySection component | 74B | | `frontend/components/profitability/customer-profitability-section.tsx` — "use client". Section within the profitability page showing customer rankings. Table: Customer Name, Currency, Total Billable Value, Total Cost, Margin, Margin %. Expandable rows showing per-project breakdown. Sortable by lifetime billable value. Pattern: follow ProjectProfitabilityTable from 74.4. |
| 74.10 | Integrate customer section into profitability page | 74B | | Modify `frontend/app/(app)/org/[slug]/profitability/page.tsx` — add CustomerProfitabilitySection below the project profitability table. Uses tabbed or sectioned layout (Utilization / Project Profitability / Customer Profitability). Fetches customer profitability data. |
| 74.11 | Add financials tab tests | 74B | | `frontend/__tests__/project-financials.test.tsx` (~4 tests): renders profitability data, renders budget panel when budget exists, N/A for margin when no cost rates, lead can view but member cannot. Pattern: follow existing test files. |
| 74.12 | Create CustomerFinancialsTab component | 74C | | `frontend/components/profitability/customer-financials-tab.tsx` — "use client". Shows customer lifetime profitability: total billable value, cost, margin across all projects. Per-project breakdown table below. Per-currency grouping. Pattern: follow ProjectFinancialsTab from 74.7 (adapted for customer). |
| 74.13 | Integrate Financials tab into customer detail page | 74C | | Modify `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — add "Financials" tab. Server component fetches customer profitability. Visible to admin/owner only. Add `frontend/app/(app)/org/[slug]/customers/[id]/profitability-actions.ts` for server actions. |
| 74.14 | Add budget status indicator to project list | 74C | | Modify `frontend/app/(app)/org/[slug]/projects/page.tsx` — optionally show budget status indicator on project cards/rows. Small colored dot: green (ON_TRACK), amber (AT_RISK), red (OVER_BUDGET), no dot if no budget. Fetch budget status via lightweight GET /api/projects/{projectId}/budget/status for each project (or batch). Pattern: follow existing project list card styling. |
| 74.15 | Add customer financials tests | 74C | | `frontend/__tests__/customer-financials.test.tsx` (~4 tests): renders customer lifetime values, per-project breakdown, N/A for margin, admin-only access. |
| 74.16 | Add project list budget indicator tests | 74C | | Add tests within existing project list test or `frontend/__tests__/project-budget-indicator.test.tsx` (~2 tests): shows budget indicator colors, no indicator when no budget. |

### Key Files

**Slice 74A — Create:**
- `frontend/app/(app)/org/[slug]/profitability/page.tsx`
- `frontend/app/(app)/org/[slug]/profitability/actions.ts`
- `frontend/components/profitability/utilization-table.tsx`
- `frontend/components/profitability/project-profitability-table.tsx`
- `frontend/__tests__/profitability-page.test.tsx`

**Slice 74A — Modify:**
- `frontend/lib/nav-items.ts` — Add Profitability nav item

**Slice 74A — Read for context:**
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` — Report page pattern
- `frontend/lib/api.ts` — API client
- `frontend/lib/format.ts` — `formatCurrency`, `formatDuration`
- `frontend/components/desktop-sidebar.tsx` — Sidebar structure

**Slice 74B — Create:**
- `frontend/components/profitability/project-financials-tab.tsx`
- `frontend/components/profitability/customer-profitability-section.tsx`
- `frontend/__tests__/project-financials.test.tsx`

**Slice 74B — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Add Financials tab
- `frontend/components/projects/project-tabs.tsx` — Add "Financials" tab definition
- `frontend/app/(app)/org/[slug]/profitability/page.tsx` — Add customer section

**Slice 74C — Create:**
- `frontend/components/profitability/customer-financials-tab.tsx`
- `frontend/app/(app)/org/[slug]/customers/[id]/profitability-actions.ts`
- `frontend/__tests__/customer-financials.test.tsx`
- `frontend/__tests__/project-budget-indicator.test.tsx`

**Slice 74C — Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Add Financials tab
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — Add budget status indicator

### Architecture Decisions

- **`components/profitability/` directory**: New component directory for all profitability-related components. Shared across the profitability page, project financials tab, and customer financials tab.
- **Three-slice frontend split**: 74A (standalone profitability page — new route, new components). 74B (integration into existing project detail + customer section on profitability page). 74C (customer detail integration + project list enhancement). Each slice is self-contained and touches different existing pages.
- **Profitability page as tabbed layout**: Three sections (Utilization / Project Profitability / Customer Profitability) within a single page, using tabs or vertically stacked sections. Admin/owner only.
- **Budget status on project list**: Lightweight indicator (colored dot) fetched from the `/budget/status` endpoint. If the project has no budget, no indicator is shown. This avoids overloading the project list while providing at-a-glance financial health.
- **Date range picker**: Shared component on the profitability page. Defaults to current month. Triggers refetch for all sections simultaneously.

---

## Summary

| Metric | Count |
|--------|-------|
| **Epics** | 8 (67-74) |
| **Slices** | 17 (67A-D, 68A-B, 69A-B, 70A, 71A-B, 72A, 73A-B, 74A-C) |
| **Backend slices** | 10 |
| **Frontend slices** | 7 |
| **New backend packages** | 5 (`settings/`, `billingrate/`, `costrate/`, `budget/`, `report/`) |
| **New frontend component directories** | 3 (`rates/`, `budget/`, `profitability/`) |
| **New migrations** | 2 (V19, V20) |
| **Estimated backend tests** | ~96 |
| **Estimated frontend tests** | ~42 |
| **Estimated total tests** | ~138 |
