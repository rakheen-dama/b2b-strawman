# Phase 51 — Accounting Practice Management Essentials

Phase 51 adds three operational capabilities that close the gap between "configured for accounting" and "works like an accounting practice management tool." The accounting-za vertical profile (Phase 47/49) already has excellent seed data, but the firm still manages regulatory deadlines in spreadsheets, manually kicks off engagement letters after scheduled project creation, and hand-configures rate cards for every new org. This phase delivers: (1) a regulatory deadline calendar with filing status tracking, (2) post-schedule automation for engagement kickoff, and (3) profile-based onboarding seeding for rate cards and schedule templates.

The next Flyway tenant migration is **V81** (V80 is taken by `V80__add_compliance_template_enums.sql`). The next epic number starts at **381**. ADRs 197--199 are already accepted.

**Architecture doc**: `architecture/phase51-accounting-practice-essentials.md`

**ADRs**:
- [ADR-197](adr/ADR-197-calculated-vs-stored-deadlines.md) -- Calculated vs. stored deadlines (deadlines computed on-the-fly from FYE + rules; only filing status is persisted)
- [ADR-198](adr/ADR-198-post-create-action-execution.md) -- Post-create action execution model (synchronous within schedule transaction, best-effort with try-catch per action)
- [ADR-199](adr/ADR-199-filing-status-lazy-creation.md) -- Filing status lazy creation (no pre-population; records created only when user marks as filed/not_applicable)

**Dependencies on prior phases**:
- Phase 8: `BillingRate` entity, `BillingRateRepository`, `BillingRateService` -- rate pack seeder creates org-level billing rates
- Phase 11/23: `FieldDefinition`, `FieldValue`, custom fields on Customer (especially `financial_year_end`, `vat_number`, `cipc_registration_number`) -- deadline calculation reads these
- Phase 12/31: `GeneratedDocumentService.generateForProject()`, `DocumentTemplate` -- post-create action calls this for engagement letter generation
- Phase 13: Schema-per-tenant isolation -- all new entities are plain `@Entity` with no multitenancy boilerplate
- Phase 16: `RecurringSchedule`, `RecurringScheduleExecutor`, `RecurringScheduleService.executeSingleSchedule()` -- extended with post-create actions
- Phase 37/48: `AutomationRule`, `FieldDateScannerJob` -- not extended directly, but the deadline calendar complements the notification automation
- Phase 49: `VerticalModuleGuard`, `VerticalModuleRegistry`, `VerticalProfileService` -- module registration and guard integration
- Phase 50: `JurisdictionDefaults` -- pattern reference for `DeadlineTypeRegistry`

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 381 | Foundation: V81 Migration, DeadlineTypeRegistry, FilingStatus Entity, Module Registration | Backend | -- | M | 381A, 381B | **Done** (PRs #790, #791) |
| 382 | Deadline Calculation Service + Controller | Backend | 381 | M | 382A, 382B | **Done** (PRs #792, #793) |
| 383 | Post-Schedule Actions (Engagement Kickoff) | Backend | 381 (migration only) | M | 383A, 383B | **Done** (PRs #794, #795) |
| 384 | Profile Pack Seeders (Rate + Schedule) | Backend | 381 (migration only) | M | 384A, 384B | **Done** (PRs #796, #797) |
| 385 | Frontend: Deadline Calendar Page | Frontend | 382 | L | 385A, 385B | |
| 386 | Frontend: Schedule Actions UI + Seeding Feedback + Dashboard Widget | Frontend | 383, 384, 385 | M | 386A, 386B | |

---

## Dependency Graph

```
BACKEND FOUNDATION (sequential within epic)
──────────────────────────────────────────────────────────────────

[E381A V81 migration (filing_statuses table,
 recurring_schedules column, org_settings columns),
 DeadlineTypeRegistry static utility,
 + unit tests]
        |
[E381B FilingStatus entity + repository
 + FilingStatusService (upsert, batch, list)
 + VerticalModuleRegistry extension
 + OrgSettings pack tracking fields
 + integration tests]
        |
        +──────────────────────+──────────────────────+
        |                      |                      |
DEADLINE CALCULATION     POST-SCHEDULE          PACK SEEDERS
+ API                    ACTIONS                (sequential)
(sequential)             (sequential)
────────────────         ────────────           ─────────────
        |                      |                      |
[E382A Deadline          [E383A Extend          [E384A RatePackSeeder
 CalculationService       RecurringSchedule       + RatePackDefinition
 (calculateDeadlines,     entity + extend         + SchedulePackSeeder
 calculateForCustomer,    executeSingleSchedule   + SchedulePackDefinition
 calculateSummary)        with postCreateActions   + JSON resources
 + integration tests]     + error handling         + OrgSettings methods
        |                 + integration tests]      + integration tests]
[E382B Deadline                |                      |
 Controller (5 endpoints)  [E383B Extend DTO      [E384B Provisioning
 + module guard             records + schedule       + profile switch
 integration                controller to expose     integration
 + integration tests]       postCreateActions        + integration tests]
        |                  + integration tests]
        |                      |                      |
        +──────────────────────+──────────────────────+
                               |
FRONTEND (requires backend epics 381-384)
──────────────────────────────────────────────────────────────────
                               |
        +──────────────────────+────────────────+
        |                                       |
[E385A Deadlines page                    [E386A PostCreateActions
 (month/list/summary views),              Section component,
 DeadlineFilters, DeadlineListView,       schedule create/edit
 DeadlineCalendarView, actions.ts,        dialog extension,
 nav-items, types, Zod schemas,           seeding feedback toast,
 + frontend tests]                        + frontend tests]
        |                                       |
[E385B FilingStatusDialog,               [E386B Dashboard
 BatchFilingActions,                      DeadlineWidget
 DeadlineSummaryCards,                    (module-gated),
 + frontend tests]                        + frontend tests]
```

**Parallel opportunities**:
- After E381B: E382 (deadline calculation), E383 (post-schedule actions), and E384 (pack seeders) can all run in parallel. They share only the V81 migration from E381A.
- E382A and E382B are sequential (controller depends on calculation service).
- E383A and E383B are sequential (DTO changes depend on entity extension).
- E384A and E384B are sequential (provisioning integration depends on seeders existing).
- Within the frontend: E385A and E386A can run in parallel once their respective backend dependencies are met. E385B depends on E385A. E386B depends on E385A (deadline types for the widget) and E386A.

---

## Implementation Order

### Stage 0: Backend Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 381 | 381A | V81 migration (`filing_statuses` table, `recurring_schedules.post_create_actions` column, `org_settings.rate_pack_status` + `schedule_pack_status` columns), `DeadlineTypeRegistry` static utility with 8 ZA deadline types and calculation rules, unit tests (~8). Backend only. | **Done** (PR #790) |
| 0b | 381 | 381B | `FilingStatus` entity + `FilingStatusRepository` (with custom batch lookup query) + `FilingStatusService` (upsert, batch upsert, list) + register `regulatory_deadlines` in `VerticalModuleRegistry` + add `ratePackStatus`/`schedulePackStatus` fields to `OrgSettings` entity + integration tests (~6). Backend only. | **Done** (PR #791) |

### Stage 1: Backend Domain Services (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a (parallel) | 382 | 382A | `DeadlineCalculationService` with `calculateDeadlines()`, `calculateDeadlinesForCustomer()`, `calculateSummary()` — loads customers with FYE, applies calculation/applicability rules, overlays filing status, cross-references projects. Integration tests (~7). Backend only. | **Done** (PR #792) |
| 1b (parallel) | 383 | 383A | Extend `RecurringSchedule` entity with `postCreateActions` JSONB field, extend `RecurringScheduleService.executeSingleSchedule()` with `executePostCreateActions()` and `notifyPostCreateFailure()` private methods, inject `GeneratedDocumentService` + `InformationRequestService`. Integration tests (~5). Backend only. | **Done** (PR #794) |
| 1c (parallel) | 384 | 384A | `RatePackSeeder` + `RatePackDefinition`, `SchedulePackSeeder` + `SchedulePackDefinition`, `rate-packs/accounting-za.json` and `schedule-packs/accounting-za.json` resources, `OrgSettings` methods (`recordRatePackApplication`, `isRatePackApplied`, `recordSchedulePackApplication`, `isSchedulePackApplied`). Integration tests (~5). Backend only. | **Done** (PR #796) |

### Stage 2: Backend Controllers + Integration (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 382 | 382B | `DeadlineController` with 5 endpoints (GET deadlines, GET summary, GET customer deadlines, PUT filing-status, GET filing-statuses), `VerticalModuleGuard.requireModule("regulatory_deadlines")` integration, authorization checks. Integration tests (~6). Backend only. | **Done** (PR #793) |
| 2b (parallel) | 383 | 383B | Extend `CreateScheduleRequest`, `UpdateScheduleRequest`, `ScheduleResponse` DTOs with `postCreateActions` field, verify `RecurringScheduleController` passes the field through. Integration tests (~4). Backend only. | **Done** (PR #795) |
| 2c (parallel) | 384 | 384B | Integrate `RatePackSeeder` + `SchedulePackSeeder` into `TenantProvisioningService` and `PackReconciliationRunner`, integrate with `VerticalProfileService.switchProfile()`. Integration tests (~4). Backend only. | **Done** (PR #797) |

### Stage 3: Frontend — Deadline Calendar (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 385 | 385A | Deadlines page (`app/(app)/org/[slug]/deadlines/page.tsx`), server actions (`actions.ts`), `DeadlineListView`, `DeadlineCalendarView`, `DeadlineFilters`, TypeScript types, Zod schemas, nav item in `lib/nav-items.ts` with `requiredModule: "regulatory_deadlines"`. Frontend tests (~5). Frontend only. | **Done** (PR #798) |
| 3b | 385 | 385B | `FilingStatusDialog`, `BatchFilingActions`, `DeadlineSummaryCards` components. Frontend tests (~4). Frontend only. | |

### Stage 4: Frontend — Schedule Actions + Dashboard (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 386 | 386A | `PostCreateActionsSection` component (toggles + template selector dropdowns + due days input), integrate into schedule create/edit dialogs, seeding feedback toast after profile switch. Frontend tests (~4). Frontend only. | |
| 4b (parallel) | 386 | 386B | `DeadlineWidget` dashboard component (compact "Upcoming deadlines this month"), module-gated rendering on dashboard page. Frontend tests (~3). Frontend only. | |

---

## Epic 381: Foundation — V81 Migration, DeadlineTypeRegistry, FilingStatus, Module Registration

**Goal**: Lay the database and infrastructure foundation for all Phase 51 features. Create the V81 migration with all DDL, build the `DeadlineTypeRegistry` static utility with ZA deadline type definitions, create the `FilingStatus` entity and service, register the `regulatory_deadlines` module, and add pack tracking fields to `OrgSettings`.

**References**: Architecture doc Sections 2.1, 2.3, 6; ADR-197 (calculated deadlines), ADR-199 (lazy filing status).

**Dependencies**: None (first epic).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **381A** | 381.1--381.6 | V81 migration (`filing_statuses` table with unique constraint + indexes, `recurring_schedules.post_create_actions` JSONB column, `org_settings.rate_pack_status` + `schedule_pack_status` JSONB columns), `DeadlineTypeRegistry` static utility (8 ZA deadline types with calculation rules and applicability predicates), `DeadlineTypeRegistryTest` unit tests (~8). Backend only. | **Done** (PR #790) |
| **381B** | 381.7--381.14 | `FilingStatus` entity + `FilingStatusRepository` (custom batch lookup query) + `FilingStatusService` (upsert, batch upsert, list) + register `regulatory_deadlines` module in `VerticalModuleRegistry` + add `ratePackStatus`/`schedulePackStatus` JSONB fields to `OrgSettings` entity with `recordRatePackApplication()`/`recordSchedulePackApplication()`/`isRatePackApplied()`/`isSchedulePackApplied()` methods + `FilingStatusServiceTest` integration tests (~6). Backend only. | **Done** (PR #791) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 381.1 | Create V81 tenant migration | 381A | -- | New file: `backend/src/main/resources/db/migration/tenant/V81__regulatory_deadlines.sql`. Three DDL groups: (1) `CREATE TABLE IF NOT EXISTS filing_statuses` with PK, customer_id FK, deadline_type_slug, period_key, status, filed_at, filed_by, notes, linked_project_id, created_at, updated_at, UNIQUE constraint `uq_filing_status_customer_deadline_period`, CHECK constraint `chk_filing_status_status` limiting status to `filed`/`not_applicable`, plus 3 indexes; (2) `ALTER TABLE recurring_schedules ADD COLUMN IF NOT EXISTS post_create_actions JSONB`; (3) `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS rate_pack_status JSONB, ADD COLUMN IF NOT EXISTS schedule_pack_status JSONB`. Must be idempotent. Pattern: `V76__data_protection_foundation.sql`. |
| 381.2 | Create `DeadlineTypeRegistry` static utility class | 381A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineTypeRegistry.java`. Static utility, no Spring bean. Contains `DeadlineType` record with fields: `slug`, `name`, `jurisdiction`, `category`, `calculationRule` (`BiFunction<LocalDate, String, LocalDate>`), `applicabilityRule` (`Predicate<Map<String, Object>>`). Static `List<DeadlineType> getDeadlineTypes(String jurisdiction)`, `Optional<DeadlineType> getDeadlineType(String slug)`, `List<String> getCategories(String jurisdiction)`. Register all 8 ZA deadline types from architecture doc Section 2.3. Pattern: `datarequest/JurisdictionDefaults.java`. |
| 381.3 | Implement `sars_provisional_1` and `sars_provisional_2` calculation rules | 381A | 381.2 | Within `DeadlineTypeRegistry`. Provisional 1: last day of 6th month after FYE month. Provisional 2: last day of FYE month. Applicability: all companies (always true). Use `YearMonth` and `LocalDate` arithmetic. |
| 381.4 | Implement `sars_provisional_3`, `sars_annual_return`, `afs_submission` calculation rules | 381A | 381.2 | Within `DeadlineTypeRegistry`. Provisional 3 (voluntary): last day of 7th month after FYE. Annual return: FYE + 12 months. AFS: FYE + 6 months. All applicable to all companies. |
| 381.5 | Implement `sars_vat_return`, `cipc_annual_return`, `sars_paye_monthly` calculation rules | 381A | 381.2 | Within `DeadlineTypeRegistry`. VAT: bi-monthly, 25th of following month -- applicability checks `customFields.containsKey("vat_number")` and non-empty value. CIPC: anniversary of registration date -- applicability checks `cipc_registration_number` presence. PAYE: 7th of following month -- always applicable (firm-level). |
| 381.6 | Write unit tests for `DeadlineTypeRegistry` | 381A | 381.2--381.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineTypeRegistryTest.java`. 8 tests: (1) `getDeadlineTypes("ZA")` returns 8 types, (2) `sars_provisional_1` calculates correct date from known FYE (e.g., FYE=2026-02-28 -> due 2026-08-31), (3) `sars_annual_return` calculates FYE+12 months, (4) VAT applicability returns false when `vat_number` is null/empty, (5) VAT applicability returns true when `vat_number` is present, (6) CIPC applicability checks `cipc_registration_number`, (7) `getDeadlineTypes("US")` returns empty list (unknown jurisdiction), (8) `getDeadlineType("sars_provisional_1")` returns correct type. Pure unit tests, no Spring context. |
| 381.7 | Create `FilingStatus` entity | 381B | 381.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/FilingStatus.java`. Plain `@Entity` with `@Table(name = "filing_statuses")`. Fields: `id` (UUID, `@GeneratedValue`), `customerId` (UUID, NOT NULL), `deadlineTypeSlug` (String, 50, NOT NULL), `periodKey` (String, 20, NOT NULL), `status` (String, 20, NOT NULL), `filedAt` (Instant, nullable), `filedBy` (UUID, nullable), `notes` (String/TEXT, nullable), `linkedProjectId` (UUID, nullable), `createdAt`/`updatedAt` (Instant, managed). No `@Filter`, no `tenant_id` (Phase 13 schema isolation). Pattern: `automation/FieldDateNotificationLog.java`. |
| 381.8 | Create `FilingStatusRepository` | 381B | 381.7 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/FilingStatusRepository.java`. Extends `JpaRepository<FilingStatus, UUID>`. Custom queries: (1) `findByCustomerIdAndDeadlineTypeSlugAndPeriodKey(UUID, String, String)` returning `Optional<FilingStatus>` -- for upsert lookup; (2) `@Query` method for batch lookup: `findByCustomerIdInAndDeadlineTypeSlugInAndPeriodKeyIn(Collection<UUID>, Collection<String>, Collection<String>)` returning `List<FilingStatus>`; (3) `findByCustomerIdAndStatusOptionalAndSlugOptional(UUID, String, String)` -- list with optional filters. Pattern: `FieldDateNotificationLogRepository` or `AuditEventRepository`. |
| 381.9 | Create `FilingStatusService` | 381B | 381.7, 381.8 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/FilingStatusService.java`. Records: `CreateFilingStatusRequest`, `BatchUpdateRequest`, `FilingStatusResponse`. Methods: `upsert(CreateFilingStatusRequest, UUID memberId)` -- queries for existing by unique key, creates or updates; `batchUpsert(BatchUpdateRequest, UUID memberId)` -- loops calling upsert for each item; `list(UUID customerId, String deadlineTypeSlug, String status)` -- all params nullable for optional filtering. Audit event on each upsert: `filing_status.updated`. Pattern: `FilingStatusService` design in architecture doc Section 3.2. |
| 381.10 | Register `regulatory_deadlines` module in `VerticalModuleRegistry` | 381B | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java`. Add module: `id = "regulatory_deadlines"`, `name = "Regulatory Deadlines"`, `description = "Firm-wide calendar of regulatory filing deadlines with status tracking"`, `defaultEnabledFor = ["accounting-za"]`, `navItems = [{ path: "/deadlines", label: "Deadlines", zone: "clients" }]`, `status = "active"`. Pattern: existing module entries in same file (e.g., `trust_accounting`, `court_calendar`). |
| 381.11 | Add `ratePackStatus` and `schedulePackStatus` to `OrgSettings` | 381B | 381.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "rate_pack_status", columnDefinition = "jsonb") private Map<String, Object> ratePackStatus;` and same for `schedulePackStatus`. Add methods: `recordRatePackApplication(String packId, int version)`, `isRatePackApplied(String packId, int version)`, `recordSchedulePackApplication(String packId, int version)`, `isSchedulePackApplied(String packId, int version)`. Pattern: existing `fieldPackStatus` or `compliancePackStatus` fields and methods in same file. |
| 381.12 | Write integration tests for `FilingStatusService` | 381B | 381.9 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/deadline/FilingStatusServiceTest.java`. 6 tests: (1) upsert creates new filing status record with status "filed", (2) upsert updates existing record (same customer/slug/period), (3) batch upsert creates multiple records, (4) list returns all filing statuses for a customer, (5) list filters by status, (6) upsert creates audit event `filing_status.updated`. Use `@SpringBootTest` + `TestcontainersConfiguration`. Create test customer via `TestCustomerFactory.createActiveCustomer()`. |
| 381.13 | Write integration test for module registration | 381B | 381.10 | Extend `FilingStatusServiceTest.java` or add to existing verticals test file. 1 test: `VerticalModuleRegistry.getModule("regulatory_deadlines")` returns a module with `status = "active"` and `defaultEnabledFor` includes `"accounting-za"`. |
| 381.14 | Write integration test for OrgSettings pack tracking | 381B | 381.11 | Extend `FilingStatusServiceTest.java` or existing `OrgSettingsServiceTest`. 1 test: `OrgSettings.recordRatePackApplication("rate-pack-accounting-za", 1)` marks the pack as applied; `isRatePackApplied()` returns true; calling `recordRatePackApplication()` again is idempotent. |

### Key Files

**Create:** `V81__regulatory_deadlines.sql`, `DeadlineTypeRegistry.java`, `DeadlineTypeRegistryTest.java`, `FilingStatus.java`, `FilingStatusRepository.java`, `FilingStatusService.java`, `FilingStatusServiceTest.java`

**Modify:** `VerticalModuleRegistry.java` (+1 module), `OrgSettings.java` (+2 JSONB fields, +4 methods)

### Architecture Decisions

- **Single V81 migration for all DDL**: All Phase 51 DDL changes (`filing_statuses` table, `recurring_schedules.post_create_actions` column, `org_settings.rate_pack_status`/`schedule_pack_status` columns) go into one migration. This avoids ordering issues between epics.
- **`DeadlineTypeRegistry` is a static utility, not a Spring bean**: Deadline types are regulatory constants defined in code, not tenant-configurable data. Same pattern as `JurisdictionDefaults`. Adding new jurisdictions means adding a new static block.
- **`FilingStatus` CHECK constraint limits to "filed" and "not_applicable"**: No "pending" or "overdue" status values in the database. These are computed by `DeadlineCalculationService` at query time (ADR-199).
- **V81, not V80**: V80 is already taken by `V80__add_compliance_template_enums.sql` from Phase 50.

---

## Epic 382: Deadline Calculation Service + Controller

**Goal**: Build the core `DeadlineCalculationService` that computes regulatory deadlines on-the-fly from customer FYE values and deadline type rules, then expose via a `DeadlineController` with module guard integration. This epic delivers the full backend API for the deadline calendar.

**References**: Architecture doc Sections 3.1, 4.1, 4.3, 5.1, 5.3, 8.1, 8.3. ADR-197 (calculated deadlines).

**Dependencies**: Epic 381 (`FilingStatus` entity, `DeadlineTypeRegistry`, `VerticalModuleRegistry` extension).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **382A** | 382.1--382.6 | `DeadlineCalculationService` with `calculateDeadlines(from, to, filters)`, `calculateDeadlinesForCustomer(customerId, from, to)`, `calculateSummary(from, to, filters)`. Loads active customers with FYE, applies calculation/applicability rules, batch-loads filing statuses for overlay, cross-references linked projects. Records: `CalculatedDeadline`, `DeadlineFilters`, `DeadlineSummary`. Integration tests (~7). Backend only. | **Done** (PR #792) |
| **382B** | 382.7--382.13 | `DeadlineController` with 5 endpoints: GET `/api/deadlines`, GET `/api/deadlines/summary`, GET `/api/customers/{id}/deadlines`, PUT `/api/deadlines/filing-status`, GET `/api/filing-statuses`. Module guard via `VerticalModuleGuard.requireModule("regulatory_deadlines")`. Authorization: MEMBER+ for reads, ADMIN+ for filing status writes. Integration tests (~6). Backend only. | **Done** (PR #793) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 382.1 | Create `DeadlineCalculationService` with inner records | 382A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineCalculationService.java`. `@Service` bean. Inner records: `CalculatedDeadline(UUID customerId, String customerName, String deadlineTypeSlug, String deadlineTypeName, String category, LocalDate dueDate, String status, UUID linkedProjectId, UUID filingStatusId)`, `DeadlineFilters(String category, String status, UUID customerId)`, `DeadlineSummary(String month, String category, int total, int filed, int pending, int overdue)`. Inject: `CustomerRepository`, `FilingStatusRepository`, `ProjectRepository`. |
| 382.2 | Implement `calculateDeadlines()` method | 382A | 382.1 | Within `DeadlineCalculationService`. Steps: (1) Load ACTIVE customers with non-null `financial_year_end` custom field. If `filters.customerId` is set, load only that customer. (2) For each customer, for each applicable deadline type from `DeadlineTypeRegistry.getDeadlineTypes("ZA")`, compute due dates within `[from, to]` range. (3) Handle period generation -- for annual deadlines, iterate years that overlap with date range; for monthly deadlines (VAT, PAYE), iterate months. Pattern: architecture doc Section 3.1 algorithm. |
| 382.3 | Implement filing status overlay in `calculateDeadlines()` | 382A | 382.2 | Within `DeadlineCalculationService`. After computing all calculated deadlines: (1) Collect all unique (customerId, deadlineTypeSlug, periodKey) tuples. (2) Batch-load `FilingStatus` records via `FilingStatusRepository`. (3) Build a HashMap keyed by `customerId + slug + periodKey` for O(1) lookup. (4) For each calculated deadline: if matching filing status exists with "filed" -> set status "filed"; if "not_applicable" -> set status "not_applicable"; if no record AND dueDate < today -> set status "overdue"; else "pending". (5) Apply `filters.status` if provided. |
| 382.4 | Implement project cross-referencing in `calculateDeadlines()` | 382A | 382.3 | Within `DeadlineCalculationService`. For each calculated deadline, check for an existing project with matching `customerId` and custom field values (`engagement_type` matching the deadline category, `tax_year` matching the period key). Use `ProjectRepository` with a custom query or in-memory filtering of the customer's projects. Set `linkedProjectId` if found. This is best-effort -- if no matching project exists, `linkedProjectId` is null. |
| 382.5 | Implement `calculateDeadlinesForCustomer()` and `calculateSummary()` | 382A | 382.3 | Within `DeadlineCalculationService`. `calculateDeadlinesForCustomer()`: delegates to `calculateDeadlines()` with `DeadlineFilters(null, null, customerId)`. `calculateSummary()`: calls `calculateDeadlines()`, then groups by `YearMonth.from(dueDate)` and `category`, computing totals/filed/pending/overdue counts per group. Returns `List<DeadlineSummary>`. |
| 382.6 | Write integration tests for `DeadlineCalculationService` | 382A | 382.2--382.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineCalculationServiceTest.java`. 7 tests: (1) calculates provisional tax dates correctly from FYE 2025-02-28, (2) VAT deadlines only generated for customer with `vat_number` custom field, (3) CIPC deadlines only for customer with `cipc_registration_number`, (4) filing status overlay: "filed" status correctly shown, (5) overdue status computed for past-due deadlines with no filing status, (6) date range filtering excludes out-of-range deadlines, (7) PROSPECT customers excluded from calculation. Use `TestCustomerFactory` with custom fields set via `setCustomFields(Map.of("financial_year_end", "2025-02-28"))`. |
| 382.7 | Create `DeadlineController` with GET endpoints | 382B | 382.1--382.5 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineController.java`. Pure delegation controller. Endpoints: `GET /api/deadlines` (required params: `from`, `to`; optional: `category`, `status`, `customerId`), `GET /api/deadlines/summary` (same params), `GET /api/customers/{id}/deadlines` (required params: `from`, `to`). Each is a one-liner delegating to `DeadlineCalculationService`. Pattern: `CalendarController.java` for date-range query endpoints. |
| 382.8 | Add filing status write endpoint to `DeadlineController` | 382B | 382.7 | Within `DeadlineController`. Endpoint: `PUT /api/deadlines/filing-status` -- accepts `BatchUpdateRequest` body, delegates to `FilingStatusService.batchUpsert()`, returns list of `FilingStatusResponse`. Also: `GET /api/filing-statuses` with optional query params `customerId`, `deadlineTypeSlug`, `status` -- delegates to `FilingStatusService.list()`. |
| 382.9 | Add module guard integration | 382B | 382.7, 382.8 | Within `DeadlineController`. Add `VerticalModuleGuard.requireModule("regulatory_deadlines")` call at the start of each endpoint method, or use a `@PreAuthorize` / AOP-style guard. The guard checks `OrgSettings.enabledModules` for the current tenant. If the module is not enabled, return 403. Pattern: existing module guard usage in `verticals/legal/` controllers. |
| 382.10 | Add authorization annotations | 382B | 382.7, 382.8 | Within `DeadlineController`. GET endpoints: MEMBER+ (no `@RequiresCapability` needed -- default authenticated). PUT filing-status: `@RequiresCapability(Capability.MANAGE_COMPLIANCE)` or ADMIN+ role check. The `MANAGE_COMPLIANCE` capability was added in Phase 50 (V77 migration). Pattern: `@RequiresCapability` usage in `DataExportController.java`. |
| 382.11 | Write integration tests for `DeadlineController` GET endpoints | 382B | 382.7, 382.9 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/deadline/DeadlineControllerTest.java`. 3 tests: (1) GET `/api/deadlines?from=2026-01-01&to=2026-12-31` returns calculated deadlines for customers with FYE, (2) GET `/api/deadlines/summary` returns aggregated counts, (3) GET `/api/customers/{id}/deadlines` returns deadlines for specific customer. Use MockMvc with JWT mocks. |
| 382.12 | Write integration tests for filing status and authorization | 382B | 382.8, 382.10 | Extend `DeadlineControllerTest.java`. 3 tests: (4) PUT `/api/deadlines/filing-status` by ADMIN updates filing status successfully, (5) PUT by MEMBER returns 403, (6) GET `/api/deadlines` returns 403 when `regulatory_deadlines` module is disabled for the tenant. |
| 382.13 | Write integration test for missing required params | 382B | 382.11 | Extend `DeadlineControllerTest.java`. 1 additional test: (7) GET `/api/deadlines` without `from`/`to` returns 400 Bad Request. |

### Key Files

**Create:** `DeadlineCalculationService.java`, `DeadlineCalculationServiceTest.java`, `DeadlineController.java`, `DeadlineControllerTest.java`

**Modify:** None (all new files in the `deadline/` package; `FilingStatusService` and `DeadlineTypeRegistry` from Epic 381 are consumed as dependencies).

### Architecture Decisions

- **Calculated, not stored (ADR-197)**: Deadlines are computed on-the-fly. No `RegulatoryDeadline` entity. The computation is bounded: for 200 clients x 8 types, ~1,600 date arithmetic operations -- sub-millisecond in memory.
- **Filing status overlay via HashMap**: Batch-load all relevant `FilingStatus` records, then overlay via O(1) lookup per deadline. This avoids N+1 queries.
- **Module guard at controller level**: The `VerticalModuleGuard` check runs before service calls. If the module is disabled, all deadline endpoints return 403 immediately.

---

## Epic 383: Post-Schedule Actions (Engagement Kickoff)

**Goal**: Extend the `RecurringSchedule` entity with a `postCreateActions` JSONB field and modify `RecurringScheduleService.executeSingleSchedule()` to execute configured post-create actions (document generation and/or information request) after project creation. Expose the new field in schedule DTOs and controller.

**References**: Architecture doc Sections 2.2, 3.3, 4.2, 5.2. ADR-198 (synchronous execution model).

**Dependencies**: Epic 381 (V81 migration adds the `post_create_actions` column).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **383A** | 383.1--383.7 | Extend `RecurringSchedule` entity with `postCreateActions` JSONB field + getter/setter. Extend `RecurringScheduleService.executeSingleSchedule()` with `executePostCreateActions()` and `notifyPostCreateFailure()` private methods. Inject `GeneratedDocumentService` and `InformationRequestService`. Integration tests (~5). Backend only. | **Done** (PR #794) |
| **383B** | 383.8--383.12 | Extend `CreateScheduleRequest`, `UpdateScheduleRequest`, `ScheduleResponse` DTOs with `postCreateActions` field. Verify controller passes field through on create/update/get. Integration tests (~4). Backend only. | **Done** (PR #795) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 383.1 | Add `postCreateActions` JSONB field to `RecurringSchedule` entity | 383A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringSchedule.java`. Add: `@JdbcTypeCode(SqlTypes.JSON) @Column(name = "post_create_actions", columnDefinition = "jsonb") private Map<String, Object> postCreateActions;` with getter/setter. Pattern: existing JSONB fields on `Customer.customFields` or `OrgSettings.enabledModules`. |
| 383.2 | Inject `GeneratedDocumentService` and `InformationRequestService` into `RecurringScheduleService` | 383A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleService.java`. Add constructor parameters for both services. Also inject `NotificationService` and `AuditService` if not already injected (needed for failure notifications and audit events). |
| 383.3 | Implement `executePostCreateActions()` private method | 383A | 383.1, 383.2 | Modify: `RecurringScheduleService.java`. New private method per architecture doc Section 3.3. Two try-catch blocks: (1) If `actions.containsKey("generateDocument")`, extract `templateSlug` and call `generatedDocumentService.generateForProject(project.getId(), templateSlug, schedule.getCreatedBy())`. (2) If `actions.containsKey("sendInfoRequest")`, extract `requestTemplateSlug` and `dueDays`, call `informationRequestService.createFromTemplateSlug(slug, customerId, projectId, dueDays)`. |
| 383.4 | Implement `notifyPostCreateFailure()` private method | 383A | 383.2 | Modify: `RecurringScheduleService.java`. New private method per architecture doc Section 3.3. On failure: (1) Send notification to `schedule.getCreatedBy()` with type `POST_CREATE_ACTION_FAILED`, (2) Create `post_create_action.failed` audit event with details `{ project_id, action_type, error }`. Both wrapped in their own try-catch (failure notification must not throw). |
| 383.5 | Call `executePostCreateActions()` in `executeSingleSchedule()` | 383A | 383.3, 383.4 | Modify: `RecurringScheduleService.java`. Insert call between existing step 8 (auto-completion) and step 9 (audit log): `if (schedule.getPostCreateActions() != null && !schedule.getPostCreateActions().isEmpty()) { executePostCreateActions(schedule, project, customer); }`. |
| 383.6 | Write integration tests for post-create action execution | 383A | 383.5 | Extend or create: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleServiceTest.java`. 3 tests: (1) Schedule with `generateDocument` action creates a generated document after project creation, (2) Schedule with `sendInfoRequest` action creates an information request, (3) Schedule with no `postCreateActions` (null) executes existing behavior unchanged (regression). |
| 383.7 | Write integration tests for failure handling | 383A | 383.5 | Extend `RecurringScheduleServiceTest.java`. 2 tests: (4) Document generation failure does not prevent project creation (project still exists), (5) Document generation failure sends notification to creator with `POST_CREATE_ACTION_FAILED` type. |
| 383.8 | Extend `CreateScheduleRequest` with `postCreateActions` | 383B | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/dto/CreateScheduleRequest.java`. Add `Map<String, Object> postCreateActions` field (nullable). Pattern: existing nullable fields in same record. |
| 383.9 | Extend `UpdateScheduleRequest` with `postCreateActions` | 383B | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/dto/UpdateScheduleRequest.java`. Add `Map<String, Object> postCreateActions` field (nullable). |
| 383.10 | Extend `ScheduleResponse` with `postCreateActions` | 383B | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/dto/ScheduleResponse.java`. Add `Map<String, Object> postCreateActions` field. Ensure the service-to-response mapping populates this from `schedule.getPostCreateActions()`. |
| 383.11 | Verify controller passes `postCreateActions` through | 383B | 383.8, 383.9, 383.10 | Review `RecurringScheduleController.java`. The existing create/update endpoint should pass the DTO to service, which sets the field on the entity. If the service mapping does not already handle `postCreateActions`, modify `RecurringScheduleService.createSchedule()` and `updateSchedule()` to set `schedule.setPostCreateActions(request.postCreateActions())`. |
| 383.12 | Write integration tests for schedule DTO extension | 383B | 383.8--383.11 | Extend: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleControllerTest.java`. 4 tests: (1) POST `/api/schedules` with `postCreateActions` body saves the JSONB correctly, (2) GET `/api/schedules/{id}` returns `postCreateActions` in response, (3) PUT `/api/schedules/{id}` updates `postCreateActions`, (4) POST without `postCreateActions` results in null (backward compatibility). |

### Key Files

**Create:** None (all modifications to existing files).

**Modify:** `RecurringSchedule.java` (+1 JSONB field), `RecurringScheduleService.java` (+2 private methods, +2 injected dependencies, +1 call in `executeSingleSchedule`), `CreateScheduleRequest.java`, `UpdateScheduleRequest.java`, `ScheduleResponse.java` (all +1 field), `RecurringScheduleServiceTest.java` (+5 tests), `RecurringScheduleControllerTest.java` (+4 tests)

### Architecture Decisions

- **Synchronous execution (ADR-198)**: Post-create actions run within the same `REQUIRES_NEW` transaction as project creation. The 1-3 second overhead per schedule is acceptable because the executor runs at 02:00 UTC with no user waiting.
- **Best-effort with per-action try-catch**: Each action fails independently. A failed document generation does not prevent the info request from sending. The project is always created regardless of action outcomes.
- **JSONB over related entity**: `postCreateActions` is stored as JSONB rather than a related table. The structure is simple (2 optional keys, each with 2-3 sub-fields) and doesn't need querying or indexing.

---

## Epic 384: Profile Pack Seeders (Rate + Schedule)

**Goal**: Build two new pack seeders that extend `AbstractPackSeeder` -- `RatePackSeeder` for org-level billing rates and `SchedulePackSeeder` for recurring schedules in disabled state. Create the JSON pack resources for the `accounting-za` profile. Integrate both seeders into the provisioning and profile switch flows.

**References**: Architecture doc Sections 3.4.1, 3.4.2, 3.4.3.

**Dependencies**: Epic 381 (V81 migration adds `org_settings.rate_pack_status` and `schedule_pack_status` columns).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **384A** | 384.1--384.8 | `RatePackSeeder` extending `AbstractPackSeeder<RatePackDefinition>` + `RatePackDefinition` record + `SchedulePackSeeder` extending `AbstractPackSeeder<SchedulePackDefinition>` + `SchedulePackDefinition` record + `rate-packs/accounting-za.json` and `schedule-packs/accounting-za.json` classpath resources + `OrgSettings` pack tracking delegation. Integration tests (~5). Backend only. | **Done** (PR #796) |
| **384B** | 384.9--384.13 | Integrate `RatePackSeeder` and `SchedulePackSeeder` into `TenantProvisioningService` and `PackReconciliationRunner`. Wire seeding feedback (return value or notification with seed counts). Integration tests (~4). Backend only. | **Done** (PR #797) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 384.1 | Create `RatePackDefinition` record | 384A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/RatePackDefinition.java`. Record: `RatePackDefinition(String packId, String verticalProfile, int version, List<RateEntry> rates)`. Inner record: `RateEntry(String description, double hourlyRate, String currency)`. Pattern: `FieldPackDefinition` or `CompliancePackDefinition` records. |
| 384.2 | Create `rate-packs/accounting-za.json` resource | 384A | 384.1 | New file: `backend/src/main/resources/rate-packs/accounting-za.json`. Contents per architecture doc Section 3.4.1: `packId = "rate-pack-accounting-za"`, `verticalProfile = "accounting-za"`, `version = 1`, 4 rate entries (Partner 2500 ZAR, Manager 1800 ZAR, Senior Accountant 1200 ZAR, Clerk/Trainee 650 ZAR). Pattern: `field-packs/accounting-za-customer.json`. |
| 384.3 | Create `RatePackSeeder` | 384A | 384.1, 384.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/RatePackSeeder.java`. Extends `AbstractPackSeeder<RatePackDefinition>`. Inject `BillingRateRepository`, `OrgSettingsService`. Override `getResourcePattern()` to return `"classpath:rate-packs/*.json"`, `getDefinitionType()` to return `RatePackDefinition.class`. Implement `applyPack(RatePackDefinition)`: for each rate entry, create an org-level `BillingRate` with description, hourlyRate, currency. Check `orgSettings.isRatePackApplied(packId, version)` before applying. After apply, call `orgSettings.recordRatePackApplication(packId, version)`. Pattern: `FieldPackSeeder.java`. |
| 384.4 | Create `SchedulePackDefinition` record | 384A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/SchedulePackDefinition.java`. Record: `SchedulePackDefinition(String packId, String verticalProfile, int version, List<ScheduleEntry> schedules)`. Inner record: `ScheduleEntry(String name, String projectTemplateName, String recurrence, String description, Map<String, Object> postCreateActions)`. |
| 384.5 | Create `schedule-packs/accounting-za.json` resource | 384A | 384.4 | New file: `backend/src/main/resources/schedule-packs/accounting-za.json`. Contents per architecture doc Section 3.4.2: `packId = "schedule-pack-accounting-za"`, `verticalProfile = "accounting-za"`, `version = 1`, 2 schedule entries (Annual Tax Return with postCreateActions, Monthly Bookkeeping without). Pattern: `compliance-packs/accounting-za.json`. |
| 384.6 | Create `SchedulePackSeeder` | 384A | 384.4, 384.5 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/SchedulePackSeeder.java`. Extends `AbstractPackSeeder<SchedulePackDefinition>`. Inject `RecurringScheduleRepository`, `ProjectTemplateRepository`, `OrgSettingsService`. For each schedule entry: look up project template by name (if not found, log warning and skip), create `RecurringSchedule` with status `PAUSED`, `customerId = null`, resolved template ID, configured `postCreateActions`. Idempotency via `orgSettings.isSchedulePackApplied()`. Pattern: `RatePackSeeder`. |
| 384.7 | Write integration tests for `RatePackSeeder` | 384A | 384.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/seeder/RatePackSeederTest.java`. 3 tests: (1) Seeds 4 org-level billing rates from `accounting-za` pack, (2) Idempotent -- second run does not duplicate rates, (3) Non-matching vertical profile (e.g., tenant with `legal-za` profile) skips the pack. Use `@SpringBootTest` + `TestcontainersConfiguration`. |
| 384.8 | Write integration tests for `SchedulePackSeeder` | 384A | 384.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/seeder/SchedulePackSeederTest.java`. 2 tests: (1) Seeds 2 recurring schedules in PAUSED state, (2) Missing project template logs warning and skips that schedule entry (no exception). |
| 384.9 | Integrate seeders into `TenantProvisioningService` | 384B | 384.3, 384.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java`. Add `RatePackSeeder` and `SchedulePackSeeder` as constructor parameters. Add calls `ratePackSeeder.seedPacksForTenant(schemaName, clerkOrgId)` and `schedulePackSeeder.seedPacksForTenant(schemaName, clerkOrgId)` after existing seeder calls (line ~120, after `automationTemplateSeeder`). |
| 384.10 | Integrate seeders into `PackReconciliationRunner` | 384B | 384.3, 384.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/PackReconciliationRunner.java`. Add `RatePackSeeder` and `SchedulePackSeeder` as constructor parameters. Add calls after existing seeders (line ~83). |
| 384.11 | Integrate seeders into profile switch flow | 384B | 384.3, 384.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileService.java` (or wherever `switchProfile()` calls pack seeders). Add `ratePackSeeder.seedPacksForTenant()` and `schedulePackSeeder.seedPacksForTenant()` to the seeder chain. If `switchProfile()` does not directly call seeders, find the correct trigger point via `PackReconciliationRunner` or a dedicated profile switch handler. |
| 384.12 | Write integration tests for provisioning integration | 384B | 384.9 | Extend existing `TenantProvisioningServiceTest.java` or create new test. 2 tests: (1) Provisioning a new tenant with `accounting-za` profile seeds 4 billing rates, (2) Provisioning with non-accounting profile does not seed rates. |
| 384.13 | Write integration test for idempotent reconciliation | 384B | 384.10 | Extend existing test or add to `RatePackSeederTest`. 1 test: Running `PackReconciliationRunner.reconcileAllTenants()` twice does not duplicate seeded data (rates and schedules remain at original count). |

### Key Files

**Create:** `RatePackDefinition.java`, `RatePackSeeder.java`, `RatePackSeederTest.java`, `SchedulePackDefinition.java`, `SchedulePackSeeder.java`, `SchedulePackSeederTest.java`, `rate-packs/accounting-za.json`, `schedule-packs/accounting-za.json`

**Modify:** `TenantProvisioningService.java` (+2 seeders), `PackReconciliationRunner.java` (+2 seeders), `VerticalProfileService.java` (+2 seeder calls)

### Architecture Decisions

- **Schedules seeded in PAUSED state**: Seeded schedules are intentionally disabled. The tenant must review, assign customers, and activate. This prevents auto-creating projects for customers the firm hasn't configured yet.
- **`customerId = null` on seeded schedules**: Seeded schedules are templates, not customer-specific. The tenant assigns customers when activating. This avoids assumptions about which customers need which schedules.
- **Pack tracking via OrgSettings JSONB**: Same pattern as existing `fieldPackStatus` -- the JSONB column stores `{ "packId": { "version": 1, "appliedAt": "..." } }`. Idempotency checked before applying.

---

## Epic 385: Frontend -- Deadline Calendar Page

**Goal**: Build the Deadlines page with month, list, and summary views, filing status dialog, batch filing actions, and filters. Add the "Deadlines" sidebar nav item gated behind the `regulatory_deadlines` module.

**References**: Architecture doc Sections 1.5, 7.2. Frontend CLAUDE.md for Shadcn/Tailwind/RSC conventions.

**Dependencies**: Epic 382 (all deadline backend endpoints).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **385A** | 385.1--385.8 | Deadlines page at `app/(app)/org/[slug]/deadlines/page.tsx`, server actions (`actions.ts`), `DeadlineListView` table component with sort/filter, `DeadlineCalendarView` month grid with deadline count badges, `DeadlineFilters` component (category, status, customer), TypeScript types (`lib/types.ts` extension), Zod schemas (`lib/schemas/deadline.ts`), nav item in `lib/nav-items.ts` with `requiredModule: "regulatory_deadlines"`. Frontend tests (~5). Frontend only. | **Done** (PR #798) |
| **385B** | 385.9--385.14 | `FilingStatusDialog` component (mark as filed with date, notes, reference fields), `BatchFilingActions` component (multi-select checkbox + batch "Mark as Filed"/"Mark N/A" buttons), `DeadlineSummaryCards` component (category cards with filed/pending/overdue counts), view mode toggle (month/list/summary tabs). Frontend tests (~4). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 385.1 | Add TypeScript types for deadlines | 385A | -- | Modify: `frontend/lib/types.ts`. Add: `CalculatedDeadline` (customerId, customerName, deadlineTypeSlug, deadlineTypeName, category, dueDate, status, linkedProjectId, filingStatusId), `DeadlineSummary` (month, category, total, filed, pending, overdue), `FilingStatusRequest` (customerId, deadlineTypeSlug, periodKey, status, notes, linkedProjectId), `DeadlineFiltersType` (category, status, customerId, from, to). |
| 385.2 | Create Zod schemas for filing status | 385A | 385.1 | New file: `frontend/lib/schemas/deadline.ts`. Schemas: `filingStatusSchema` (customerId UUID, deadlineTypeSlug string, periodKey string, status enum ["filed", "not_applicable"], notes optional string, linkedProjectId optional UUID). Export `FilingStatusFormData`. Pattern: `lib/schemas/customer.ts`. |
| 385.3 | Create server actions for deadline endpoints | 385A | 385.1 | New file: `frontend/app/(app)/org/[slug]/deadlines/actions.ts`. Server actions: `fetchDeadlines(from, to, filters)` -> GET `/api/deadlines`, `fetchDeadlineSummary(from, to, filters)` -> GET `/api/deadlines/summary`, `fetchCustomerDeadlines(customerId, from, to)` -> GET `/api/customers/{id}/deadlines`, `updateFilingStatus(items)` -> PUT `/api/deadlines/filing-status`. All use `fetchWithAuth` from `lib/api.ts`. Pattern: existing `actions.ts` files in `projects/` or `customers/`. |
| 385.4 | Create `DeadlineFilters` component | 385A | 385.1 | New file: `frontend/components/deadlines/DeadlineFilters.tsx`. Client component (`"use client"`). Category dropdown (tax, corporate, vat, payroll), status dropdown (pending, filed, overdue, not_applicable), customer selector (optional). Month/year navigation (prev/next buttons). Emits `onFilterChange` callback with updated `DeadlineFiltersType`. Use Shadcn `Select` and `Button` components. |
| 385.5 | Create `DeadlineListView` component | 385A | 385.1 | New file: `frontend/components/deadlines/DeadlineListView.tsx`. Client component. Table with columns: Client (link to customer), Deadline Type, Due Date, Status (badge: green=filed, red=overdue, amber=pending, gray=n/a), Linked Engagement (link to project or "—"), Actions (checkbox for batch selection). Sortable by due date, client name, status. Use Shadcn `Table`, `Badge`, `Checkbox`. Pattern: existing table components in `components/customers/`. |
| 385.6 | Create `DeadlineCalendarView` component | 385A | 385.1 | New file: `frontend/components/deadlines/DeadlineCalendarView.tsx`. Client component. Month grid showing days with deadline count badges. Each day cell shows colored count indicators (green = all filed, red = overdue count, amber = pending count). Click a day to filter the list view to that day. Use CSS grid for calendar layout. Pattern: existing calendar components in `calendar/` page. |
| 385.7 | Create Deadlines page | 385A | 385.3--385.6 | New file: `frontend/app/(app)/org/[slug]/deadlines/page.tsx`. Server component. Fetch initial deadlines for current month via `fetchDeadlines()`. Render `DeadlineFilters`, then conditional rendering of `DeadlineListView` or `DeadlineCalendarView` based on active view mode (passed as URL search param or client state). Module-gated: if `regulatory_deadlines` module is not enabled, redirect to dashboard or show "not available" message. Add "Deadlines" nav item to `lib/nav-items.ts` in the "clients" zone with `requiredModule: "regulatory_deadlines"`. |
| 385.8 | Write frontend tests for page and views | 385A | 385.5, 385.6, 385.7 | New file: `frontend/__tests__/deadlines/deadlines-page.test.tsx`. 5 tests: (1) Deadlines page renders with mock data, (2) `DeadlineListView` renders table rows with correct status badges, (3) `DeadlineCalendarView` renders month grid with day count badges, (4) `DeadlineFilters` category dropdown filters the list, (5) Nav item appears when module is enabled, hidden when disabled. Use Vitest + Testing Library. Mock server actions. Pattern: existing test files in `__tests__/`. |
| 385.9 | Create `FilingStatusDialog` component | 385B | 385.1, 385.2 | New file: `frontend/components/deadlines/FilingStatusDialog.tsx`. Client component. Dialog with: filing date picker (defaults to today), notes textarea, reference number input, linked project selector (optional, filtered by customer). Submit calls `updateFilingStatus()` server action. Uses Zod schema validation via react-hook-form. Shadcn `Dialog`, `Form`, `Input`, `Textarea`, `DatePicker`. Pattern: existing dialog components with Zod forms. |
| 385.10 | Create `BatchFilingActions` component | 385B | 385.9 | New file: `frontend/components/deadlines/BatchFilingActions.tsx`. Client component. Sticky action bar that appears when 1+ deadlines are selected via checkboxes in `DeadlineListView`. Shows: count of selected items, "Mark as Filed" button, "Mark as N/A" button, "Clear Selection" button. "Mark as Filed" opens `FilingStatusDialog` pre-filled with the selected items. "Mark as N/A" calls `updateFilingStatus()` directly with status "not_applicable". Pattern: batch action patterns in existing list pages. |
| 385.11 | Create `DeadlineSummaryCards` component | 385B | 385.1 | New file: `frontend/components/deadlines/DeadlineSummaryCards.tsx`. Client component. Cards showing category breakdowns for the current period: "Tax: 12 total (8 filed, 2 pending, 2 overdue)", "Corporate: 5 total (...)", etc. Uses data from `fetchDeadlineSummary()`. Shadcn `Card` with colored indicators. Pattern: dashboard widget card patterns. |
| 385.12 | Add view mode toggle to Deadlines page | 385B | 385.7, 385.11 | Modify: `frontend/app/(app)/org/[slug]/deadlines/page.tsx` or a client wrapper. Add tab bar with 3 modes: "Month" (calendar grid), "List" (table), "Summary" (cards). Use Shadcn `Tabs` component. Default to "List" view. Summary view renders `DeadlineSummaryCards`. Pattern: existing tab patterns on project detail page. |
| 385.13 | Write frontend tests for filing dialog and batch actions | 385B | 385.9, 385.10, 385.11 | New file: `frontend/__tests__/deadlines/filing-status.test.tsx`. 4 tests: (1) `FilingStatusDialog` renders with date picker, notes, reference fields, (2) Submit calls `updateFilingStatus` with correct payload, (3) `BatchFilingActions` shows selected count and action buttons, (4) `DeadlineSummaryCards` renders category cards with correct counts. Use Vitest + Testing Library. Mock server actions. Add `afterEach(() => cleanup())` for Dialog leak prevention. |
| 385.14 | Add `requiredModule` to nav item | 385B | 385.7 | Modify: `frontend/lib/nav-items.ts`. Add entry: `{ path: "/deadlines", label: "Deadlines", icon: "CalendarClock", zone: "clients", requiredModule: "regulatory_deadlines" }`. Verify that the sidebar rendering logic already checks `requiredModule` against the tenant's enabled modules. If not, add the check in the sidebar component. |

### Key Files

**Create:** `deadlines/page.tsx`, `deadlines/actions.ts`, `DeadlineListView.tsx`, `DeadlineCalendarView.tsx`, `DeadlineFilters.tsx`, `FilingStatusDialog.tsx`, `BatchFilingActions.tsx`, `DeadlineSummaryCards.tsx`, `lib/schemas/deadline.ts`, `deadlines-page.test.tsx`, `filing-status.test.tsx`

**Modify:** `lib/types.ts` (+4 types), `lib/nav-items.ts` (+1 nav item)

### Architecture Decisions

- **Server Component page with client sub-components**: The page itself is a server component that fetches initial data. View switching, filtering, and interaction are handled by `"use client"` sub-components that use SWR for re-fetching.
- **Module gate in nav-items**: The nav item uses `requiredModule: "regulatory_deadlines"` -- the sidebar rendering logic hides the item if the module is not in the tenant's enabled modules list. The page itself also checks (redirect to dashboard if module disabled).
- **Calendar view is a simplified month grid**: Not a full calendar component. Each day cell shows a count badge. Clicking a day filters the list. This is simpler than the Phase 30 calendar which renders individual tasks.

---

## Epic 386: Frontend -- Schedule Actions UI + Seeding Feedback + Dashboard Widget

**Goal**: Build the `PostCreateActionsSection` component for the schedule create/edit dialogs, add seeding feedback after profile switch, and create the `DeadlineWidget` for the company dashboard.

**References**: Architecture doc Sections 2.3, 3.3, 7.2.

**Dependencies**: Epic 383 (schedule backend with `postCreateActions`), Epic 384 (seeding backend), Epic 385 (deadline types for widget).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **386A** | 386.1--386.5 | `PostCreateActionsSection` component (toggle + document template selector + info request template selector + due days input), integrate into schedule create/edit dialogs, seeding feedback toast/banner after profile switch showing "Seeded: 4 rate card tiers, 2 schedule templates (inactive)". Frontend tests (~4). Frontend only. | |
| **386B** | 386.6--386.9 | `DeadlineWidget` dashboard component showing "Upcoming deadlines this month" as a compact card. Module-gated -- hidden when `regulatory_deadlines` is not enabled. Integrate into dashboard page. Frontend tests (~3). Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 386.1 | Create `PostCreateActionsSection` component | 386A | -- | New file: `frontend/components/schedules/PostCreateActionsSection.tsx`. Client component. Section header: "After Creation (Optional)". Two toggle rows: (1) "Generate document" toggle + `DocumentTemplate` selector dropdown (filtered by entity type if available) + auto-send checkbox (disabled for v1 with tooltip "Coming soon"). (2) "Send information request" toggle + `RequestTemplate` selector dropdown + "Due in N days" number input (default 14). Helper text below: "These actions run automatically each time this schedule creates an engagement." Emits `onChange(postCreateActions)` callback. Use Shadcn `Switch`, `Select`, `Input`. |
| 386.2 | Integrate `PostCreateActionsSection` into schedule create dialog | 386A | 386.1 | Modify: `frontend/app/(app)/org/[slug]/schedules/` (create dialog component). Add `PostCreateActionsSection` below existing form fields. Pass `postCreateActions` value from the form state. On submit, include `postCreateActions` in the request body. If both toggles are off, send `null` for `postCreateActions`. |
| 386.3 | Integrate `PostCreateActionsSection` into schedule edit dialog | 386A | 386.1 | Modify: schedule edit dialog component. Pre-populate `PostCreateActionsSection` with existing `postCreateActions` from the schedule response. Allow editing and clearing. |
| 386.4 | Add seeding feedback toast after profile switch | 386A | -- | Modify: `frontend/app/(app)/org/[slug]/settings/` (profile switch page/dialog). After a successful profile switch API call, if the response includes seeding summary data (e.g., `{ rateCardsTiersSeeded: 4, scheduleTemplatesSeeded: 2 }`), show a toast: "Accounting profile applied. Seeded: 4 rate card tiers, 2 schedule templates (inactive). Review in Settings > Rates and Settings > Recurring Schedules." Use Shadcn `toast`. If the backend does not return seeding counts, show a generic success message with links to Settings pages. |
| 386.5 | Write frontend tests for schedule actions and seeding | 386A | 386.1--386.4 | New file: `frontend/__tests__/schedules/post-create-actions.test.tsx`. 4 tests: (1) `PostCreateActionsSection` renders with both toggles off by default, (2) Toggling "Generate document" shows template selector dropdown, (3) Toggling "Send information request" shows template selector and due days input, (4) Both toggles off emits null for `postCreateActions`. Use Vitest + Testing Library. Add `afterEach(() => cleanup())`. |
| 386.6 | Create `DeadlineWidget` component | 386B | -- | New file: `frontend/components/dashboard/DeadlineWidget.tsx`. Client component. Compact card showing: "Upcoming Deadlines" header, count of deadlines this month (e.g., "12 deadlines — 8 filed, 2 pending, 2 overdue"), colored indicators matching the status palette (green/amber/red), "View All" link to `/deadlines` page. Uses SWR to fetch `fetchDeadlineSummary()` for current month. Pattern: existing dashboard widget components in `components/dashboard/`. |
| 386.7 | Integrate `DeadlineWidget` into dashboard page | 386B | 386.6 | Modify: `frontend/app/(app)/org/[slug]/dashboard/page.tsx`. Add `DeadlineWidget` to the dashboard grid. Module-gated: only render if `regulatory_deadlines` module is in the tenant's enabled modules. Check via the org settings data already loaded by the dashboard. |
| 386.8 | Write frontend tests for `DeadlineWidget` | 386B | 386.6 | New file: `frontend/__tests__/dashboard/deadline-widget.test.tsx`. 3 tests: (1) `DeadlineWidget` renders with summary data (total, filed, pending, overdue counts), (2) "View All" link points to `/deadlines`, (3) Widget is not rendered when `regulatory_deadlines` module is disabled. Use Vitest + Testing Library. |
| 386.9 | Add module-gated conditional rendering helper | 386B | 386.7 | If not already existing: create or extend a utility function `isModuleEnabled(orgSettings, moduleId)` that checks the `enabledModules` JSONB/array in org settings. Used by both the dashboard widget and the nav items. If this utility already exists (from Phase 49 module guard work), reuse it. |

### Key Files

**Create:** `PostCreateActionsSection.tsx`, `DeadlineWidget.tsx`, `post-create-actions.test.tsx`, `deadline-widget.test.tsx`

**Modify:** Schedule create/edit dialog components (+PostCreateActionsSection integration), `dashboard/page.tsx` (+DeadlineWidget), profile switch settings component (+seeding feedback toast)

### Architecture Decisions

- **Post-create actions UI uses toggles, not a complex config panel**: Two switches with conditional sub-fields. Simple enough for a section within the existing schedule dialog. No separate page or wizard.
- **Dashboard widget uses SWR polling**: The widget fetches summary data via SWR with a long refresh interval (5 minutes). The deadline data changes infrequently (only when filing status is updated or time passes), so aggressive polling is unnecessary.
- **Module-gated rendering**: The widget checks `isModuleEnabled()` client-side. If the module is not enabled, the widget simply does not render -- no error, no placeholder, just absent from the grid.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase51-accounting-practice-essentials.md` - Full architecture specification: domain model, flows, API surface, migration DDL, sequence diagrams
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringScheduleService.java` - Core file to extend with post-create action execution (Epic 383)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/seeder/AbstractPackSeeder.java` - Base class for RatePackSeeder and SchedulePackSeeder (Epic 384)
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/provisioning/TenantProvisioningService.java` - Must be extended to call new seeders during provisioning
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/datarequest/JurisdictionDefaults.java` - Pattern reference for DeadlineTypeRegistry static utility class