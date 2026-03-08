# Phase 40 -- Bulk Billing & Batch Operations

Phase 40 adds a **bulk billing system** to the DocTeams platform -- the ability to generate, review, approve, and send invoices for multiple customers in a single coordinated workflow called a "billing run." The design introduces a `BillingRun` entity as a coordination layer over existing invoice infrastructure, delegating all invoice creation to the existing `InvoiceService.createDraft()`. The phase includes cross-customer unbilled work discovery, entry-level cherry-picking, batch retainer period closing, rate-limited email sending, and a multi-step frontend wizard.

**Architecture doc**: `architecture/phase40-bulk-billing-batch-operations.md`

**Dependencies on prior phases**:
- Phase 10 (Invoicing): `Invoice`, `InvoiceLine`, `InvoiceService.createDraft()`, `InvoiceEmailService`
- Phase 17 (Retainer Billing): `RetainerAgreement`, `RetainerPeriod`, `RetainerPeriodService.closePeriod()`
- Phase 24 (Email Delivery): `EmailNotificationChannel`, SMTP and SendGrid adapters
- Phase 26 (Tax Handling): `TaxRate`, `TaxCalculationService`
- Phase 30 (Expense Billing): `Expense` entity with billable flag and markup
- Phase 6 (Audit): `AuditService`, `AuditEventBuilder`
- Phase 6.5 (Notifications): `ApplicationEvent` publication, `NotificationService`
- Phase 14 (Customer Lifecycle): `CustomerLifecycleGuard`, `PrerequisiteService`

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 303 | BillingRun Entity Foundation & Migration | Backend | -- | M | 303A, 303B | **Done** (PRs #595, #596) |
| 304 | Preview, Customer Discovery & Unbilled Summary | Backend | 303 | M | 304A, 304B | **Done** (PRs #597, #598) |
| 305 | Entry Selection & Cherry-Pick | Backend | 304 | S | 305A | **Done** (PR #599) |
| 306 | Batch Generation & Cancel | Backend | 305 | M | 306A, 306B | **Done** (PRs #600, #601) |
| 307 | Batch Approve, Send & Notifications | Backend | 306 | M | 307A, 307B | **Done** (PRs #602, #603) |
| 308 | Retainer Batch Close | Backend | 306 | S | 308A | **Done** (PR #604) |
| 309 | Billing Run List & Detail Pages (Frontend) | Frontend | 307 | M | 309A, 309B | **Done** (PRs #605, #606) |
| 310 | Billing Run Wizard (Frontend) | Frontend | 309 | L | 310A, 310B, 310C | **Done** (PRs #607, #608, #609) |
| 311 | Billing Settings & Polish (Frontend) | Frontend | 309 | S | 311A | **Done** (PR #610) |

---

## Dependency Graph

```
BACKEND TRACK (sequential core, one parallel branch)
──────────────────────────────────────────────────

[E303A BillingRun entity,
 BillingRunItem entity,
 BillingRunEntrySelection entity,
 enums, V63 migration]
        |
[E303B BillingRunRepository,
 BillingRunController (CRUD),
 BillingRunService (create/cancel
 preview-only), DTO records,
 Invoice + OrgSettings extensions,
 RBAC, audit CREATED event
 + integration tests]
        |
[E304A BillingRunService.loadPreview()
 auto-discovery query,
 prerequisite pre-check,
 preview endpoints,
 item detail endpoints
 + integration tests]
        |
[E304B Cross-customer unbilled
 summary query + endpoint
 on InvoiceController
 + InvoiceService
 + integration tests]
        |
[E305A Entry selection upsert,
 exclude/include customer,
 total recalculation,
 selection endpoints
 + integration tests]
        |
[E306A generate() method,
 entry resolution from
 selections, failure isolation,
 IN_PROGRESS guard,
 billingRunId linking,
 stats computation
 + integration tests]
        |
[E306B cancelRun() extended
 for IN_PROGRESS/COMPLETED,
 void drafts, unbill entries,
 audit GENERATED event
 + integration tests]
        |
        +─────────────────────────────────+
        |                                 |
[E307A batchApprove(),                  [E308A Retainer batch:
 batchSend() with rate                   loadRetainerPreview(),
 limiting, default due                   generateRetainerInvoices(),
 date/terms application,                 retainer endpoints
 progress tracking                       + integration tests]
 + integration tests]                    (PARALLEL with 307)
        |
[E307B Audit events
 (APPROVED, SENT, CANCELLED),
 BillingRunEventListener,
 3 notification types,
 OrgSettings batch billing
 settings update endpoint
 + integration tests]
        |
FRONTEND TRACK (after backend APIs)
────────────────────────────────────

[E309A Billing runs list page,
 API client (lib/api/billing-runs.ts),
 billing-run-status-badge,
 billing-run-item-status-badge,
 "Billing Runs" tab on invoices page
 + tests]
        |
[E309B Billing run detail page,
 summary stat cards,
 items table, action buttons
 (Resume, Cancel, Approve
 Remaining, Send Remaining)
 + tests]
        |
        +─────────────────────────────────+
        |                                 |
[E310A Wizard scaffold +                [E311A Batch billing settings
 Step 1 (configure) +                   section on Settings page
 Step 2 (customer selection)             (async threshold, email rate
 + tests]                                limit, default currency)
        |                                + tests]
[E310B Step 3 (cherry-pick)             (PARALLEL with 310)
 accordion per customer,
 entry-level checkboxes,
 subtotal recalculation
 + tests]
        |
[E310C Step 4 (review drafts)
 + Step 5 (send)
 inline editing, batch
 due date/terms, approve,
 send progress indicator
 + tests]
```

**Parallel opportunities**:
- E303A/B are sequential (entity before service/controller).
- E304A/B are sequential (preview before unbilled summary, since unbilled summary is used by preview).
- E305A depends on E304 (selection requires items from preview).
- E306A/B are sequential (generate before extended cancel).
- E307A/B are sequential (approve/send before notifications/audit).
- E308A (retainer batch) can run in parallel with E307A/B after E306B.
- E309A/B (frontend list/detail) can start after E307B.
- E310A/B/C (wizard) are sequential after E309.
- E311A (settings) can run in parallel with E310 after E309.

---

## Implementation Order

### Stage 0: Entity Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 303 | 303A | `BillingRun`, `BillingRunItem`, `BillingRunEntrySelection` entities, 3 enums (`BillingRunStatus`, `BillingRunItemStatus`, `EntryType`), V63 tenant migration. ~7 new files. Backend only. | **Done** (PR #595) |
| 0b | 303 | 303B | `BillingRunRepository`, `BillingRunItemRepository`, `BillingRunEntrySelectionRepository`, `BillingRunService` (create + cancel-preview), `BillingRunController` (CRUD), DTO records, `Invoice.billingRunId` extension, `OrgSettings` 3-field extension, audit event. ~10 new/modified files (~12 tests). Backend only. | **Done** (PR #596) |

### Stage 1: Preview & Discovery

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 304 | 304A | `BillingRunService.loadPreview()` with auto-discovery, prerequisite pre-check, `BillingRunEntrySelection` creation during preview. Preview + item endpoints. ~3 modified files (~10 tests). Backend only. | **Done** (PR #597) |
| 1b | 304 | 304B | `InvoiceService.getUnbilledSummary()` cross-customer query, `GET /api/invoices/unbilled-summary` endpoint, `CustomerUnbilledSummary` DTO. ~3 modified/new files (~5 tests). Backend only. | **Done** (PR #598) |

### Stage 2: Selection & Cherry-Pick

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a | 305 | 305A | `updateEntrySelection()`, `excludeCustomer()`, `includeCustomer()`, total recalculation, 3 selection endpoints. ~2 modified files (~8 tests). Backend only. | **Done** (PR #599) |

### Stage 3: Generation & Cancel

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a | 306 | 306A | `generate()` with failure isolation, entry resolution, single-active-run guard, `billingRunId` linking, stats computation, generate endpoint. ~3 modified files (~10 tests). Backend only. | **Done** (PR #600) |
| 3b | 306 | 306B | Extended `cancelRun()` for IN_PROGRESS/COMPLETED (void drafts, unbill entries), `BILLING_RUN_GENERATED` audit event. ~3 modified files (~6 tests). Backend only. | **Done** (PR #601) |

### Stage 4: Approve, Send & Retainers (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 307 | 307A | `batchApprove()`, `batchSend()` with rate limiting, default due date/terms, progress tracking, approve + send endpoints, `BatchOperationResult`/`BatchSendRequest` DTOs. ~3 modified files (~8 tests). Backend only. | **Done** (PR #602) |
| 4b (parallel) | 308 | 308A | `loadRetainerPreview()`, `generateRetainerInvoices()`, retainer-preview + retainer-generate endpoints, `RetainerPeriodPreview` DTO. ~3 modified files (~5 tests). Backend only. | **Done** (PR #604) |
| 4c | 307 | 307B | 3 remaining audit events (APPROVED, SENT, CANCELLED), `BillingRunEventListener` with 3 notification types, OrgSettings batch billing settings update method + endpoint inclusion. ~4 new/modified files (~6 tests). Backend only. | **Done** (PR #603) |

### Stage 5: Frontend List & Detail

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a | 309 | 309A | `lib/api/billing-runs.ts` API client, `billing-run-status-badge.tsx`, `billing-run-item-status-badge.tsx`, billing runs list page (`invoices/billing-runs/page.tsx`), "Billing Runs" tab addition to invoices page. ~7 new/modified files (~6 tests). Frontend only. | **Done** (PR #605) |
| 5b | 309 | 309B | Billing run detail page (`invoices/billing-runs/[id]/page.tsx`), summary stat cards, items table with status column, action buttons (Resume, Cancel, Approve Remaining, Send Remaining), server actions. ~5 new files (~5 tests). Frontend only. | **Done** (PR #606) |

### Stage 6: Wizard & Settings (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a (parallel) | 311 | 311A | Batch billing settings section on Settings page (async threshold, email rate limit, default currency), server action for update. ~3 modified/new files (~4 tests). Frontend only. | **Done** (PR #610) |
| 6b | 310 | 310A | Wizard scaffold (`billing-run-wizard.tsx`) with step navigation, Step 1 (`configure-step.tsx`), Step 2 (`customer-selection-step.tsx`), new billing run page route. ~6 new files (~6 tests). Frontend only. | **Done** (PR #607) |
| 6c | 310 | 310B | Step 3 (`cherry-pick-step.tsx`) -- accordion per customer, time entry table, expense table, entry-level checkboxes, subtotal recalculation, server actions for selection updates. ~4 new files (~5 tests). Frontend only. | **Done** (PR #608) |
| 6d | 310 | 310C | Step 4 (`review-drafts-step.tsx`) + Step 5 (`send-step.tsx`) -- draft table, inline editing (due date, payment terms), batch set actions, approve all, send with progress indicator, confirmation dialog, final summary. ~5 new files (~6 tests). Frontend only. | **Done** (PR #609) |

### Timeline

```
Stage 0: [303A] -> [303B]                                              (sequential)
Stage 1: [304A] -> [304B]                                              (sequential)
Stage 2: [305A]                                                        (sequential, after 304B)
Stage 3: [306A] -> [306B]                                              (sequential, after 305A)
Stage 4: [307A] // [308A] -> [307B]                                    (307A + 308A parallel, 307B after 307A)
Stage 5: [309A] -> [309B]                                              (sequential, after 307B)
Stage 6: [310A] -> [310B] -> [310C] // [311A]                         (wizard sequential, settings parallel)
```

**Critical path**: 303A -> 303B -> 304A -> 304B -> 305A -> 306A -> 306B -> 307A -> 307B -> 309A -> 309B -> 310A -> 310B -> 310C (14 slices sequential at most).

**Fastest path with parallelism**: 308A parallel with 307, 311A parallel with 310. Estimated: 16 slices total, 14 on critical path.

---

## Epic 303: BillingRun Entity Foundation & Migration

**Goal**: Create the three core entities (`BillingRun`, `BillingRunItem`, `BillingRunEntrySelection`) with their enums, repositories, basic CRUD service/controller, V63 migration, and the `Invoice`/`OrgSettings` extensions. This is the data model foundation that all other epics depend on.

**References**: Architecture doc Sections 40.2, 40.7, 40.8.

**Dependencies**: None -- greenfield entity in tenant schema.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **303A** | 303.1--303.7 | `BillingRun`, `BillingRunItem`, `BillingRunEntrySelection` entities, `BillingRunStatus`, `BillingRunItemStatus`, `EntryType` enums, V63 tenant migration with 3 new tables + Invoice/OrgSettings column additions. ~7 new files. Backend only. | **Done** (PR #595) |
| **303B** | 303.8--303.17 | `BillingRunRepository`, `BillingRunItemRepository`, `BillingRunEntrySelectionRepository`, `BillingRunService` (create + cancel-preview-only), `BillingRunController` (4 CRUD endpoints), DTO records (`CreateBillingRunRequest`, `BillingRunResponse`), `Invoice.billingRunId` field addition, `OrgSettings` 3-field extension, `BILLING_RUN_CREATED` audit event builder. ~10 new/modified files (~12 tests). Backend only. | **Done** (PR #596) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 303.1 | Create `BillingRunStatus` enum | 303A | | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunStatus.java`. Values: `PREVIEW`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`. Pattern: `backend/.../invoice/InvoiceStatus.java`. |
| 303.2 | Create `BillingRunItemStatus` enum | 303A | | New file: `backend/.../billingrun/BillingRunItemStatus.java`. Values: `PENDING`, `GENERATING`, `GENERATED`, `FAILED`, `EXCLUDED`. Pattern: `backend/.../invoice/InvoiceStatus.java`. |
| 303.3 | Create `EntryType` enum | 303A | | New file: `backend/.../billingrun/EntryType.java`. Values: `TIME_ENTRY`, `EXPENSE`. |
| 303.4 | Create `BillingRun` entity | 303A | 303.1 | New file: `backend/.../billingrun/BillingRun.java`. JPA entity mapped to `billing_runs` table. 15 fields per architecture doc Section 40.2.1. Include `startGeneration()`, `complete()`, `cancel()` lifecycle methods. UUID PK with `GenerationType.UUID`, `@Enumerated(EnumType.STRING)` for status. `@PrePersist`/`@PreUpdate` for timestamps. Pattern: `backend/.../invoice/Invoice.java` for entity conventions. |
| 303.5 | Create `BillingRunItem` entity | 303A | 303.2 | New file: `backend/.../billingrun/BillingRunItem.java`. JPA entity mapped to `billing_run_items` table. 11 fields per Section 40.2.2. FK to `billing_runs` (UUID, not JPA relationship), FK to `customers` (UUID), FK to `invoices` (nullable UUID). Unique constraint annotation: `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"billing_run_id", "customer_id"}))`. Pattern: `backend/.../invoice/InvoiceLine.java`. |
| 303.6 | Create `BillingRunEntrySelection` entity | 303A | 303.3 | New file: `backend/.../billingrun/BillingRunEntrySelection.java`. JPA entity mapped to `billing_run_entry_selections` table. 6 fields per Section 40.2.3. Polymorphic `entryId` (no DB FK). Unique constraint: `@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"billing_run_item_id", "entry_type", "entry_id"}))`. Pattern: `backend/.../invoice/InvoiceLine.java`. |
| 303.7 | Create V63 tenant migration | 303A | | New file: `backend/src/main/resources/db/migration/tenant/V63__create_billing_run_tables.sql`. DDL from architecture doc Section 40.7: 3 CREATE TABLE statements, ALTER TABLE invoices (add `billing_run_id`), ALTER TABLE org_settings (add 3 columns), all indexes. Pattern: `backend/.../db/migration/tenant/V62__create_resource_planning_tables.sql` for version numbering. |
| 303.8 | Create `BillingRunRepository` | 303B | 303A | New file: `backend/.../billingrun/BillingRunRepository.java`. Extends `JpaRepository<BillingRun, UUID>`. Methods: `findByStatusIn(List<BillingRunStatus>, Pageable)`, `existsByStatusIn(List<BillingRunStatus>)`, `findAllByOrderByCreatedAtDesc(Pageable)`. Pattern: `backend/.../invoice/InvoiceRepository.java`. |
| 303.9 | Create `BillingRunItemRepository` | 303B | 303A | New file: `backend/.../billingrun/BillingRunItemRepository.java`. Extends `JpaRepository<BillingRunItem, UUID>`. Methods: `findByBillingRunId(UUID)`, `findByBillingRunIdAndStatus(UUID, BillingRunItemStatus)`, `@Modifying deleteByBillingRunId(UUID)`. Pattern: `backend/.../invoice/InvoiceLineRepository.java`. |
| 303.10 | Create `BillingRunEntrySelectionRepository` | 303B | 303A | New file: `backend/.../billingrun/BillingRunEntrySelectionRepository.java`. Extends `JpaRepository<BillingRunEntrySelection, UUID>`. Methods: `findByBillingRunItemId(UUID)`, `deleteByBillingRunItemId(UUID)`. Add `@Modifying @Query` for `deleteByBillingRunId(UUID)` (native query joining through billing_run_items). |
| 303.11 | Create DTO records | 303B | | New file: `backend/.../billingrun/dto/BillingRunDtos.java`. Records: `CreateBillingRunRequest(String name, LocalDate periodFrom, LocalDate periodTo, String currency, boolean includeExpenses, boolean includeRetainers)`, `BillingRunResponse(UUID id, String name, BillingRunStatus status, LocalDate periodFrom, LocalDate periodTo, String currency, boolean includeExpenses, boolean includeRetainers, Integer totalCustomers, Integer totalInvoices, BigDecimal totalAmount, Integer totalSent, Integer totalFailed, UUID createdBy, Instant createdAt, Instant updatedAt, Instant completedAt)`. Pattern: `backend/.../invoice/dto/` for nested records. |
| 303.12 | Create `BillingRunService` (create + cancel-preview) | 303B | 303.8 | New file: `backend/.../billingrun/BillingRunService.java`. `@Service`. Methods: `createRun(CreateBillingRunRequest, UUID actorMemberId)` -- validates period, creates `BillingRun` in PREVIEW status, logs `BILLING_RUN_CREATED` audit event, returns `BillingRunResponse`. `cancelRun(UUID billingRunId, UUID actorMemberId)` -- PREVIEW-only cancel: deletes items + selections + run. `getRun(UUID)`, `listRuns(Pageable, List<BillingRunStatus>)`. Constructor injection of `BillingRunRepository`, `BillingRunItemRepository`, `BillingRunEntrySelectionRepository`, `AuditService`. Pattern: `backend/.../invoice/InvoiceService.java` for service structure. |
| 303.13 | Create `BillingRunController` (CRUD) | 303B | 303.12 | New file: `backend/.../billingrun/BillingRunController.java`. `@RestController @RequestMapping("/api/billing-runs")`. All methods: `@PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN', 'ROLE_ORG_OWNER')")`. Endpoints: `POST /` (create), `GET /` (list, paginated, optional status filter), `GET /{id}` (get), `DELETE /{id}` (cancel). Pure delegation -- one service call per endpoint. Pattern: follow backend CLAUDE.md controller discipline. |
| 303.14 | Add `billingRunId` field to `Invoice` entity | 303B | 303.7 | Modify: `backend/.../invoice/Invoice.java`. Add `@Column(name = "billing_run_id") private UUID billingRunId;` with getter/setter. Nullable. No JPA relationship (just a UUID field). |
| 303.15 | Add 3 batch billing fields to `OrgSettings` entity | 303B | 303.7 | Modify: `backend/.../settings/OrgSettings.java`. Add `billingBatchAsyncThreshold` (Integer, default 50), `billingEmailRateLimit` (Integer, default 5), `defaultBillingRunCurrency` (String, nullable). Update `OrgSettingsService` to include new fields in the response DTO. Modify: `backend/.../settings/OrgSettingsService.java`. |
| 303.16 | Add `billingRunCreated()` to `AuditEventBuilder` | 303B | | Modify: `backend/.../audit/AuditEventBuilder.java`. Add builder method `billingRunCreated(BillingRun run)` returning an `AuditEvent` with type `BILLING_RUN_CREATED` and JSONB details `{name, periodFrom, periodTo, currency}`. Pattern: existing builder methods in same class. |
| 303.17 | Write integration tests for CRUD | 303B | 303.13 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunControllerTest.java`. Tests (~12): (1) `createRun_validRequest_returns201`; (2) `createRun_missingPeriod_returns400`; (3) `createRun_memberRole_returns403`; (4) `getRun_exists_returns200`; (5) `getRun_notFound_returns404`; (6) `listRuns_empty_returns200`; (7) `listRuns_withStatusFilter_returnsFiltered`; (8) `cancelRun_previewStatus_deletesRun`; (9) `cancelRun_notFound_returns404`; (10) `createRun_setsPreviewStatus`; (11) `createRun_logsAuditEvent`; (12) `cancelRun_memberRole_returns403`. Use `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfiguration.class)`. Pattern: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceControllerTest.java`. |

### Key Files

**Slice 303A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRun.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunItem.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunEntrySelection.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunItemStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/EntryType.java`
- `backend/src/main/resources/db/migration/tenant/V63__create_billing_run_tables.sql`

**Slice 303A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` -- entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceStatus.java` -- enum pattern
- `backend/src/main/resources/db/migration/tenant/V62__create_resource_planning_tables.sql` -- latest migration for version numbering

**Slice 303B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunItemRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunEntrySelectionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/dto/BillingRunDtos.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunControllerTest.java`

**Slice 303B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` -- add `billingRunId` field
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` -- add 3 batch billing fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` -- include new fields in response
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- add `billingRunCreated()`

**Slice 303B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java` -- repository pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- service pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` -- controller pattern (note: may violate thin-controller rule; follow CLAUDE.md discipline instead)

### Architecture Decisions

- **Tenant-scoped entities**: All 3 new entities live in the tenant schema. No `@FilterDef`/`@Filter` needed (dedicated schema handles isolation per Phase 13).
- **UUID FK fields (not JPA relationships)**: `BillingRunItem.billingRunId`, `billingRunItem.customerId`, etc. are stored as raw UUID fields, not `@ManyToOne` JPA relationships. This avoids lazy-loading complexity and keeps entities simple.
- **Single migration file**: V63 contains all 3 tables + 2 ALTER TABLE statements. This ensures atomic deployment -- a partial migration (e.g., tables without the Invoice extension) would leave the schema in an inconsistent state.
- **Dedicated `BillingRun` entity over tag**: See ADR-157. Full lifecycle tracking enables preview persistence, cancel-with-void, and historical reporting.

---

## Epic 304: Preview, Customer Discovery & Unbilled Summary

**Goal**: Implement the preview loading flow that discovers customers with unbilled work, creates `BillingRunItem` records with preview amounts, auto-excludes customers failing prerequisites, and exposes the cross-customer unbilled summary endpoint.

**References**: Architecture doc Sections 40.3.2, 40.3.3, 40.4.

**Dependencies**: Epic 303 (entities, repositories, service shell, controller).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **304A** | 304.1--304.7 | `BillingRunService.loadPreview()` with auto-discovery SQL query, prerequisite pre-check via `PrerequisiteService`, `BillingRunEntrySelection` creation during preview (all entries included by default). Preview + item endpoints on `BillingRunController`. Preview/item response DTOs. ~3 modified files + 1 new test file (~10 tests). Backend only. | **Done** (PR #597) |
| **304B** | 304.8--304.12 | `InvoiceService.getUnbilledSummary()` cross-customer native SQL query, `GET /api/invoices/unbilled-summary` endpoint on `InvoiceController`, `CustomerUnbilledSummary` DTO. ~3 modified/new files (~5 tests). Backend only. | **Done** (PR #598) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 304.1 | Add `BillingRunPreviewResponse` and `BillingRunItemResponse` DTOs | 304A | 303B | Modify: `backend/.../billingrun/dto/BillingRunDtos.java`. Add records: `BillingRunPreviewResponse(UUID billingRunId, int totalCustomers, BigDecimal totalUnbilledAmount, List<BillingRunItemResponse> items)`, `BillingRunItemResponse(UUID id, UUID customerId, String customerName, BillingRunItemStatus status, BigDecimal unbilledTimeAmount, BigDecimal unbilledExpenseAmount, int unbilledTimeCount, int unbilledExpenseCount, BigDecimal totalUnbilledAmount, boolean hasPrerequisiteIssues, String prerequisiteIssueReason, UUID invoiceId, String failureReason)`, `LoadPreviewRequest(List<UUID> customerIds)`. |
| 304.2 | Implement auto-discovery query | 304A | 303.8 | Modify: `backend/.../billingrun/BillingRunService.java`. Add private method `discoverCustomersWithUnbilledWork(LocalDate periodFrom, LocalDate periodTo, String currency)`. Uses `@Query(nativeQuery = true)` on a repository or `EntityManager.createNativeQuery()`. SQL from architecture doc Section 40.3.2. Returns customer IDs + unbilled counts/amounts. Pattern: `backend/.../invoice/InvoiceService.java` `getUnbilledTime()` for native SQL usage. |
| 304.3 | Implement `loadPreview()` method | 304A | 304.2 | Modify: `backend/.../billingrun/BillingRunService.java`. `loadPreview(UUID billingRunId, List<UUID> customerIds)`: (1) validate run is PREVIEW, (2) if customerIds empty, call auto-discovery, (3) for each customer, query unbilled time/expenses, (4) check prerequisites via `PrerequisiteService`, (5) create `BillingRunItem` records (PENDING or EXCLUDED), (6) create `BillingRunEntrySelection` records for all unbilled entries (all `included = true`), (7) update run `totalCustomers`, (8) return `BillingRunPreviewResponse`. Constructor inject `PrerequisiteService`, `CustomerRepository`, `TimeEntryRepository`, `ExpenseRepository`. |
| 304.4 | Create `BillingRunEntrySelection` records during preview | 304A | 304.3 | Part of `loadPreview()`. For each customer's unbilled time entries and expenses discovered, insert a `BillingRunEntrySelection` record with `included = true`. Use batch inserts via `saveAll()`. This ensures deterministic generation per ADR-158. |
| 304.5 | Add preview endpoints to `BillingRunController` | 304A | 304.3 | Modify: `backend/.../billingrun/BillingRunController.java`. Add: `POST /{id}/preview` (body: `LoadPreviewRequest`), `GET /{id}/items` (list items), `GET /{id}/items/{itemId}` (get item detail), `GET /{id}/items/{itemId}/unbilled-time` (time entries for cherry-pick), `GET /{id}/items/{itemId}/unbilled-expenses` (expenses for cherry-pick). All delegate to service. |
| 304.6 | Implement unbilled time/expense detail methods | 304A | 304.3 | Modify: `backend/.../billingrun/BillingRunService.java`. Add `getUnbilledTimeEntries(UUID billingRunItemId)` and `getUnbilledExpenses(UUID billingRunItemId)` -- returns the actual time entry/expense records associated with selection records for the item. Used by cherry-pick UI to display entry details. |
| 304.7 | Write integration tests for preview | 304A | 304.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunPreviewTest.java`. Tests (~10): (1) `loadPreview_autoDiscovery_findsCustomersWithUnbilledWork`; (2) `loadPreview_specificCustomerIds_createsItems`; (3) `loadPreview_prerequisiteFailure_autoExcludes`; (4) `loadPreview_noUnbilledWork_returnsEmpty`; (5) `loadPreview_createsEntrySelectionRecords`; (6) `loadPreview_allSelectionsIncludedByDefault`; (7) `loadPreview_currencyFilter_excludesMismatch`; (8) `getItems_returnsPreviewData`; (9) `getItem_notFound_returns404`; (10) `loadPreview_nonPreviewRun_returns400`. Use `TestCustomerFactory.createActiveCustomer()` for test customers. Setup requires time entries and expenses in the billing period. |

| 304.8 | Add `CustomerUnbilledSummary` DTO | 304B | | Modify: `backend/.../billingrun/dto/BillingRunDtos.java` or new file `backend/.../invoice/dto/CustomerUnbilledSummary.java`. Record: `CustomerUnbilledSummary(UUID customerId, String customerName, String customerEmail, int unbilledTimeEntryCount, BigDecimal unbilledTimeAmount, int unbilledExpenseCount, BigDecimal unbilledExpenseAmount, BigDecimal totalUnbilledAmount, boolean hasPrerequisiteIssues, String prerequisiteIssueReason)`. |
| 304.9 | Implement `getUnbilledSummary()` on `InvoiceService` | 304B | 304.8 | Modify: `backend/.../invoice/InvoiceService.java`. Add `getUnbilledSummary(LocalDate from, LocalDate to, String currency)`. Native SQL query from architecture doc Section 40.3.2. Returns `List<CustomerUnbilledSummary>`. Includes prerequisite check per customer. |
| 304.10 | Add `GET /api/invoices/unbilled-summary` endpoint | 304B | 304.9 | Modify: `backend/.../invoice/InvoiceController.java`. Add `@GetMapping("/unbilled-summary")` with `@RequestParam` for `periodFrom`, `periodTo`, `currency`. `@PreAuthorize("hasAnyAuthority('ROLE_ORG_ADMIN', 'ROLE_ORG_OWNER')")`. Delegates to `invoiceService.getUnbilledSummary()`. |
| 304.11 | Add `findByBillingRunIdAndStatus` to `InvoiceRepository` | 304B | 303.14 | Modify: `backend/.../invoice/InvoiceRepository.java`. Add `List<Invoice> findByBillingRunIdAndStatus(UUID billingRunId, InvoiceStatus status)`. Used by cancel and batch operations. |
| 304.12 | Write integration tests for unbilled summary | 304B | 304.10 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/UnbilledSummaryTest.java`. Tests (~5): (1) `unbilledSummary_returnsCustomersWithUnbilledWork`; (2) `unbilledSummary_excludesBilledEntries`; (3) `unbilledSummary_filtersPerCurrency`; (4) `unbilledSummary_filtersPerPeriod`; (5) `unbilledSummary_memberRole_returns403`. |

### Key Files

**Slice 304A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java` -- add `loadPreview()`, auto-discovery query, unbilled detail methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunController.java` -- add 5 preview/item endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/dto/BillingRunDtos.java` -- add preview/item DTOs

**Slice 304A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunPreviewTest.java`

**Slice 304A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- `getUnbilledTime()` pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/prerequisite/PrerequisiteService.java` -- prerequisite evaluation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/` -- time entry repository for unbilled queries
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/` -- expense repository for unbilled queries

**Slice 304B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- add `getUnbilledSummary()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` -- add unbilled-summary endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java` -- add `findByBillingRunIdAndStatus()`

**Slice 304B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/UnbilledSummaryTest.java`

### Architecture Decisions

- **Explicit entry selection during preview**: Per ADR-158, `BillingRunEntrySelection` records are created for every unbilled entry during `loadPreview()`. This guarantees deterministic generation -- entries logged after preview are not included.
- **Native SQL for cross-customer query**: The auto-discovery query joins customers -> customer_projects -> projects -> tasks -> time_entries and projects -> expenses. This is a complex cross-entity query best expressed in native SQL, consistent with the existing `getUnbilledTime()` pattern.
- **Prerequisite pre-check**: Customers failing prerequisites are auto-excluded during preview (not during generation). This gives the admin visibility into issues before committing to generate.

---

## Epic 305: Entry Selection & Cherry-Pick

**Goal**: Implement the entry-level cherry-pick flow -- toggling individual time entries and expenses for inclusion/exclusion, customer-level exclude/include, and preview total recalculation.

**References**: Architecture doc Sections 40.3.3, 40.4.

**Dependencies**: Epic 304 (preview creates items and entry selection records).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **305A** | 305.1--305.6 | `updateEntrySelection()` upsert logic, `excludeCustomer()`, `includeCustomer()`, `recalculateItemTotals()`, 3 selection endpoints on controller, DTOs (`UpdateEntrySelectionsRequest`, `EntrySelectionDto`). ~2 modified files + 1 new test file (~8 tests). Backend only. | **Done** (PR #599) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 305.1 | Create `UpdateEntrySelectionsRequest` and `EntrySelectionDto` DTOs | 305A | 304A | Modify: `backend/.../billingrun/dto/BillingRunDtos.java`. Add: `UpdateEntrySelectionsRequest(List<EntrySelectionDto> selections)`, `EntrySelectionDto(EntryType entryType, UUID entryId, boolean included)`. |
| 305.2 | Implement `updateEntrySelection()` | 305A | 305.1 | Modify: `backend/.../billingrun/BillingRunService.java`. `updateEntrySelection(UUID billingRunItemId, UpdateEntrySelectionsRequest request)`: (1) validate item exists and parent run is PREVIEW, (2) for each selection DTO, find existing `BillingRunEntrySelection` by (itemId, entryType, entryId), (3) update `included` flag, (4) call `recalculateItemTotals()`. Pattern: see architecture doc Section 40.3.3 pseudocode. |
| 305.3 | Implement `recalculateItemTotals()` | 305A | 305.2 | Modify: `backend/.../billingrun/BillingRunService.java`. Private method. Queries included selections, sums amounts from `TimeEntryRepository` and `ExpenseRepository` by IDs, updates item's unbilled amounts and counts. See architecture doc Section 40.3.3 pseudocode. May need `sumBillableAmountByIds(List<UUID>)` on `TimeEntryRepository`. |
| 305.4 | Implement `excludeCustomer()` and `includeCustomer()` | 305A | | Modify: `backend/.../billingrun/BillingRunService.java`. `excludeCustomer(UUID billingRunItemId)`: validate item + run PREVIEW status, set item status to `EXCLUDED`. `includeCustomer(UUID billingRunItemId)`: validate, set status back to `PENDING`. |
| 305.5 | Add selection endpoints to `BillingRunController` | 305A | 305.2, 305.4 | Modify: `backend/.../billingrun/BillingRunController.java`. Add: `PUT /{id}/items/{itemId}/selections` (body: `UpdateEntrySelectionsRequest`), `PUT /{id}/items/{itemId}/exclude` (no body), `PUT /{id}/items/{itemId}/include` (no body). All return `ResponseEntity.ok()` or item response. |
| 305.6 | Write integration tests for entry selection | 305A | 305.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunEntrySelectionTest.java`. Tests (~8): (1) `updateSelection_excludeEntry_updatesIncludedFlag`; (2) `updateSelection_recalculatesTotals`; (3) `updateSelection_nonPreviewRun_returns400`; (4) `excludeCustomer_setsExcludedStatus`; (5) `includeCustomer_setsPendingStatus`; (6) `excludeCustomer_nonPreviewRun_returns400`; (7) `updateSelection_entryNotFound_returns404`; (8) `updateSelection_multipleEntries_updatesAll`. |

### Key Files

**Slice 305A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java` -- add selection/exclude/include/recalculate methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunController.java` -- add 3 selection endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/dto/BillingRunDtos.java` -- add selection DTOs

**Slice 305A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunEntrySelectionTest.java`

**Slice 305A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/` -- `TimeEntryRepository` for sum query
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/` -- `ExpenseRepository` for sum query

### Architecture Decisions

- **Upsert pattern**: Selection records are created during preview (304A). The cherry-pick step only updates `included` flags, not inserts. This prevents new entries from sneaking in between preview and generation.
- **Recalculation on every change**: Item totals are recalculated immediately after selection changes. This ensures the wizard always shows accurate running totals.

---

## Epic 306: Batch Generation & Cancel

**Goal**: Implement the core `generate()` method with failure isolation, entry resolution from selection records, single-active-run guard, invoice `billingRunId` linking, and extended cancel logic for IN_PROGRESS/COMPLETED runs.

**References**: Architecture doc Sections 40.3.4, 40.3.7, 40.8.

**Dependencies**: Epic 305 (entry selections are the input to generation).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **306A** | 306.1--306.7 | `BillingRunService.generate()` with per-customer `TransactionTemplate`, entry resolution from `BillingRunEntrySelection`, single-active-run guard, `Invoice.billingRunId` linking, summary stats computation, generate endpoint. ~3 modified files + 1 new test file (~10 tests). Backend only. | **Done** (PR #600) |
| **306B** | 306.8--306.12 | Extended `cancelRun()` for IN_PROGRESS/COMPLETED (void DRAFT invoices, unbill time entries/expenses, mark CANCELLED), `BILLING_RUN_GENERATED` audit event builder method. ~3 modified files + 1 new test file (~6 tests). Backend only. | **Done** (PR #601) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 306.1 | Implement entry resolution methods | 306A | 305A | Modify: `backend/.../billingrun/BillingRunService.java`. Add `resolveSelectedTimeEntryIds(BillingRunItem item)` and `resolveSelectedExpenseIds(BillingRunItem item)`: query `BillingRunEntrySelectionRepository.findByBillingRunItemId()`, filter `included = true`, group by `entryType`, return ID lists. |
| 306.2 | Implement `generate()` method | 306A | 306.1 | Modify: `backend/.../billingrun/BillingRunService.java`. Full implementation per architecture doc Section 40.3.4. NOT `@Transactional`. Uses `TransactionTemplate` per customer. Steps: (1) validate PREVIEW status, (2) check no other IN_PROGRESS run, (3) transition to IN_PROGRESS, (4) for each PENDING item: set GENERATING, resolve entries, build `CreateInvoiceRequest`, call `InvoiceService.createDraft()`, set `Invoice.billingRunId`, set item GENERATED/FAILED, (5) compute stats, (6) transition to COMPLETED, (7) publish events. Constructor inject `InvoiceService`, `InvoiceRepository`, `TransactionTemplate`. |
| 306.3 | Implement single-active-run guard | 306A | 306.2 | Part of `generate()`. Check `billingRunRepository.existsByStatusIn(List.of(BillingRunStatus.IN_PROGRESS))`. Throw `ResourceConflictException` if another run is in progress. |
| 306.4 | Add `generate` endpoint to controller | 306A | 306.2 | Modify: `backend/.../billingrun/BillingRunController.java`. Add: `POST /{id}/generate`. Delegates to `billingRunService.generate()`. Returns `BillingRunResponse`. |
| 306.5 | Publish `BillingRunCompletedEvent` and `BillingRunFailuresEvent` | 306A | 306.2 | In `generate()` method. Use `ApplicationEventPublisher.publishEvent()`. Create event records: `BillingRunCompletedEvent(BillingRun run)`, `BillingRunFailuresEvent(BillingRun run, int failureCount)`. New file: `backend/.../billingrun/BillingRunEvents.java` (records for all billing run domain events). |
| 306.6 | Add `BILLING_RUN_GENERATED` audit event | 306A | | Modify: `backend/.../audit/AuditEventBuilder.java`. Add `billingRunGenerated(BillingRun run, int invoiceCount, int failedCount)`. |
| 306.7 | Write integration tests for generate | 306A | 306.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunGenerateTest.java`. Tests (~10): (1) `generate_allPendingItems_createsInvoices`; (2) `generate_linksBillingRunIdToInvoice`; (3) `generate_computesSummaryStats`; (4) `generate_transitionsToCompleted`; (5) `generate_failedCustomer_capturesReason`; (6) `generate_failedCustomer_doesNotAbortBatch`; (7) `generate_excludedItems_skipped`; (8) `generate_nonPreviewRun_returns400`; (9) `generate_anotherInProgress_returns409`; (10) `generate_usesSelectedEntries`. Requires setup: active customers, time entries, expenses, preview loaded, selections optionally cherry-picked. |
| 306.8 | Extend `cancelRun()` for IN_PROGRESS/COMPLETED | 306B | 306A | Modify: `backend/.../billingrun/BillingRunService.java`. Extend `cancelRun()`: if run is IN_PROGRESS or COMPLETED, (1) find all DRAFT invoices via `InvoiceRepository.findByBillingRunIdAndStatus(billingRunId, InvoiceStatus.DRAFT)`, (2) for each: unbill time entries via `TimeEntryRepository.unbillByInvoiceId()`, unbill expenses via `ExpenseRepository.unbillByInvoiceId()`, delete invoice lines via `InvoiceLineRepository.deleteByInvoiceId()`, delete invoice, (3) mark run CANCELLED, (4) log audit event. Per architecture doc Section 40.3.7. |
| 306.9 | Add `unbillByInvoiceId()` to repositories if missing | 306B | | Check: `backend/.../timeentry/TimeEntryRepository.java` and `backend/.../expense/ExpenseRepository.java`. If `unbillByInvoiceId(UUID)` methods exist, skip. Otherwise, add `@Modifying @Query("UPDATE TimeEntry t SET t.invoiceId = NULL WHERE t.invoiceId = :invoiceId")` (and equivalent for Expense). |
| 306.10 | Add `BILLING_RUN_CANCELLED` audit event builder | 306B | | Modify: `backend/.../audit/AuditEventBuilder.java`. Add `billingRunCancelled(BillingRun run, int voidedInvoiceCount)`. |
| 306.11 | Write integration tests for cancel | 306B | 306.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunCancelTest.java`. Tests (~6): (1) `cancelPreview_deletesRunAndItems`; (2) `cancelCompleted_voidsDraftInvoices`; (3) `cancelCompleted_unbillsTimeEntries`; (4) `cancelCompleted_marksRunCancelled`; (5) `cancelCompleted_preservesApprovedInvoices`; (6) `cancel_logsAuditEvent`. |
| 306.12 | Add `deleteByInvoiceId()` to `InvoiceLineRepository` if missing | 306B | | Check: `backend/.../invoice/InvoiceLineRepository.java`. If `deleteByInvoiceId(UUID)` exists, skip. Otherwise add `@Modifying` method. |

### Key Files

**Slice 306A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java` -- add `generate()`, entry resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunController.java` -- add generate endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- add `billingRunGenerated()`

**Slice 306A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunEvents.java` -- domain event records
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunGenerateTest.java`

**Slice 306A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- `createDraft()` method signature and request object
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/` -- `CreateInvoiceRequest` for building the request

**Slice 306B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java` -- extend `cancelRun()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- add `billingRunCancelled()`
- (Possibly) `backend/.../timeentry/TimeEntryRepository.java`, `backend/.../expense/ExpenseRepository.java`, `backend/.../invoice/InvoiceLineRepository.java`

**Slice 306B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunCancelTest.java`

### Architecture Decisions

- **No `@Transactional` on `generate()`**: Per-customer `TransactionTemplate` ensures a failure for one customer does not roll back invoices already generated for others. See architecture doc Section 40.3.4.
- **Cancel bypasses `InvoiceService`**: DRAFT invoices are deleted directly via repositories. `Invoice.voidInvoice()` only works for APPROVED/SENT. See architecture doc Section 40.3.7.
- **Single-active-run guard**: Checked in code (`existsByStatusIn`), not by DB constraint. PREVIEW runs can coexist freely; only IN_PROGRESS blocks.

---

## Epic 307: Batch Approve, Send & Notifications

**Goal**: Implement batch approve, rate-limited batch send, remaining audit events, notification dispatch for billing run lifecycle events, and OrgSettings batch billing settings exposure.

**References**: Architecture doc Sections 40.3.6, 40.6, 40.11.

**Dependencies**: Epic 306 (generation produces invoices that can be approved/sent).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **307A** | 307.1--307.7 | `batchApprove()`, `batchSend()` with token-bucket rate limiting, default due date/terms application, progress tracking via `BillingRun.totalSent`, approve + send endpoints, `BatchOperationResult`/`BatchFailure`/`BatchSendRequest` DTOs. ~3 modified files + 1 new test file (~8 tests). Backend only. | **Done** (PR #602) |
| **307B** | 307.8--307.14 | `BILLING_RUN_APPROVED`, `BILLING_RUN_SENT` audit event builders, `BillingRunEventListener` listening for 3 domain events (completed, sent, failures), 3 notification types via `NotificationService`, OrgSettings batch billing settings update method. ~4 new/modified files + 1 new test file (~6 tests). Backend only. | **Done** (PR #603) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 307.1 | Create `BatchOperationResult`, `BatchFailure`, `BatchSendRequest` DTOs | 307A | | Modify: `backend/.../billingrun/dto/BillingRunDtos.java`. Add records: `BatchOperationResult(int successCount, int failureCount, List<BatchFailure> failures)`, `BatchFailure(UUID invoiceId, String reason)`, `BatchSendRequest(LocalDate defaultDueDate, String defaultPaymentTerms)`. |
| 307.2 | Implement `batchApprove()` | 307A | 306A | Modify: `backend/.../billingrun/BillingRunService.java`. Per architecture doc Section 40.3.6. Load GENERATED items, for each DRAFT invoice call `InvoiceService.approve()`, capture success/failure per invoice, return `BatchOperationResult`. |
| 307.3 | Implement `batchSend()` with rate limiting | 307A | 307.2 | Modify: `backend/.../billingrun/BillingRunService.java`. Per architecture doc Section 40.3.6. Load APPROVED invoices for the run. Apply default due date/terms to invoices missing them. Partition into bursts of `OrgSettings.billingEmailRateLimit`. Process each burst, sleep 1s between bursts. Call `InvoiceService.send()` per invoice. Update `BillingRun.totalSent` after each burst. Return `BatchOperationResult`. Constructor inject `OrgSettingsService`. |
| 307.4 | Implement partition utility method | 307A | | Modify: `backend/.../billingrun/BillingRunService.java`. Private method `partition(List<T> list, int size)` splitting a list into sublists. Simple utility, no external dependency. |
| 307.5 | Add approve + send endpoints to controller | 307A | 307.2, 307.3 | Modify: `backend/.../billingrun/BillingRunController.java`. Add: `POST /{id}/approve` (no body, returns `BatchOperationResult`), `POST /{id}/send` (body: `BatchSendRequest`, returns `BatchOperationResult`). |
| 307.6 | Publish `BillingRunSentEvent` in `batchSend()` | 307A | 307.3 | In `batchSend()` method. Add `BillingRunSentEvent(BillingRun run)` to `BillingRunEvents.java`. Publish after batch send completes. |
| 307.7 | Write integration tests for approve and send | 307A | 307.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunBatchOperationsTest.java`. Tests (~8): (1) `batchApprove_approvesDraftInvoices`; (2) `batchApprove_skipAlreadyApproved`; (3) `batchApprove_capturesFailures`; (4) `batchSend_sendsApprovedInvoices`; (5) `batchSend_appliesDefaultDueDate`; (6) `batchSend_appliesDefaultPaymentTerms`; (7) `batchSend_updatesTotalSent`; (8) `batchSend_capturesSendFailures`. Note: rate limiting behavior is hard to test in integration tests; verify burst partitioning in a unit test if needed. |
| 307.8 | Add `BILLING_RUN_APPROVED` and `BILLING_RUN_SENT` audit event builders | 307B | | Modify: `backend/.../audit/AuditEventBuilder.java`. Add `billingRunApproved(BillingRun run, int approvedCount)` and `billingRunSent(BillingRun run, int sentCount, BigDecimal totalAmount)`. |
| 307.9 | Add audit logging to `batchApprove()` and `batchSend()` | 307B | 307.8 | Modify: `backend/.../billingrun/BillingRunService.java`. Add `auditService.log()` calls at the end of `batchApprove()` and `batchSend()`. |
| 307.10 | Create `BillingRunEventListener` | 307B | 306.5 | New file: `backend/.../billingrun/BillingRunEventListener.java`. `@Component`. Listens for `BillingRunCompletedEvent`, `BillingRunSentEvent`, `BillingRunFailuresEvent`. For each, creates a notification via `NotificationService`. Completed: in-app to run creator ("Billing run completed: X invoices generated"). Sent: in-app ("X invoices sent, totaling Y"). Failures: in-app + email ("Billing run has Z failures"). Pattern: `backend/.../invoice/InvoiceEmailEventListener.java` for event listener pattern. |
| 307.11 | Add notification template constants for billing run | 307B | 307.10 | Determine where notification types are registered. Add 3 new types: `BILLING_RUN_COMPLETED`, `BILLING_RUN_SENT`, `BILLING_RUN_FAILURES`. Add to notification constants/enum. Pattern: existing notification types in `backend/.../notification/`. |
| 307.12 | Add batch billing settings to `OrgSettingsService` response and update | 307B | 303.15 | Modify: `backend/.../settings/OrgSettingsService.java`. Ensure the 3 new fields (`billingBatchAsyncThreshold`, `billingEmailRateLimit`, `defaultBillingRunCurrency`) are included in the settings response DTO and can be updated via the existing settings update method. |
| 307.13 | Add batch billing settings to `OrgSettingsController` | 307B | 307.12 | Modify: `backend/.../settings/OrgSettingsController.java`. If settings update is a single endpoint (PATCH/PUT), just ensure the request DTO includes the new fields. If separate endpoints, add one for batch billing settings. |
| 307.14 | Write integration tests for notifications and audit | 307B | 307.10 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunNotificationTest.java`. Tests (~6): (1) `generate_publishesCompletedEvent`; (2) `generate_withFailures_publishesFailuresEvent`; (3) `batchSend_publishesSentEvent`; (4) `eventListener_createsNotification`; (5) `batchApprove_logsAuditEvent`; (6) `batchSend_logsAuditEvent`. Use `@MockBean ApplicationEventPublisher` or `ApplicationEvents` test utility. |

### Key Files

**Slice 307A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java` -- add `batchApprove()`, `batchSend()`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunController.java` -- add approve + send endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/dto/BillingRunDtos.java` -- add batch operation DTOs
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunEvents.java` -- add `BillingRunSentEvent`

**Slice 307A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunBatchOperationsTest.java`

**Slice 307A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- `approve()`, `send()` method signatures
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` -- settings access pattern

**Slice 307B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunEventListener.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunNotificationTest.java`

**Slice 307B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditEventBuilder.java` -- add 2 builder methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java` -- add audit calls
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsService.java` -- settings response/update
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettingsController.java` -- settings endpoint

**Slice 307B -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/` -- notification service and types
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceEmailEventListener.java` -- event listener pattern

### Architecture Decisions

- **Token-bucket rate limiting**: Per ADR-160. `Thread.sleep(1000)` between bursts of `billingEmailRateLimit` emails. No external queue dependency. Sufficient for batch sizes under 200.
- **Progress tracking via `totalSent`**: `BillingRun.totalSent` is updated after each burst, enabling the frontend to poll for progress during long batch sends.
- **Default due date/terms application**: Applied to invoices missing values before sending. Does not override explicitly set values.

---

## Epic 308: Retainer Batch Close

**Goal**: Implement the opt-in retainer batch close feature -- discovering retainer agreements with periods due for close, generating retainer invoices, and linking them to the billing run.

**References**: Architecture doc Section 40.3.5.

**Dependencies**: Epic 306 (generation infrastructure, `BillingRunItem` creation pattern).

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **308A** | 308.1--308.6 | `loadRetainerPreview()`, `generateRetainerInvoices()`, retainer-preview + retainer-generate endpoints, `RetainerPeriodPreview` DTO. ~3 modified files + 1 new test file (~5 tests). Backend only. | **Done** (PR #604) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 308.1 | Create `RetainerPeriodPreview` DTO | 308A | | Modify: `backend/.../billingrun/dto/BillingRunDtos.java`. Add record: `RetainerPeriodPreview(UUID agreementId, UUID customerId, String customerName, LocalDate periodStart, LocalDate periodEnd, BigDecimal consumedHours, BigDecimal estimatedAmount)`. |
| 308.2 | Add `findActiveWithDuePeriodsInRange()` to `RetainerAgreementRepository` | 308A | | Modify: `backend/.../retainer/RetainerAgreementRepository.java`. Add method to find ACTIVE retainer agreements where the current OPEN period's `endDate` falls within [periodFrom, periodTo]. May need a `@Query` joining `retainer_agreements` and `retainer_periods`. |
| 308.3 | Implement `loadRetainerPreview()` | 308A | 308.2 | Modify: `backend/.../billingrun/BillingRunService.java`. Per architecture doc Section 40.3.5. Queries agreements with due periods, maps to `RetainerPeriodPreview`. Constructor inject `RetainerAgreementRepository`, `RetainerPeriodRepository`. |
| 308.4 | Implement `generateRetainerInvoices()` | 308A | 308.3 | Modify: `backend/.../billingrun/BillingRunService.java`. Per architecture doc Section 40.3.5. For each selected agreement: call `RetainerPeriodService.closePeriod()`, link invoice to billing run, create `BillingRunItem` for tracking. Failure isolation per agreement. Constructor inject `RetainerPeriodService`. |
| 308.5 | Add retainer endpoints to controller | 308A | 308.3, 308.4 | Modify: `backend/.../billingrun/BillingRunController.java`. Add: `GET /{id}/retainer-preview` (returns `List<RetainerPeriodPreview>`), `POST /{id}/retainer-generate` (body: `List<UUID> retainerAgreementIds`, returns `List<BillingRunItemResponse>`). |
| 308.6 | Write integration tests for retainer batch | 308A | 308.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunRetainerTest.java`. Tests (~5): (1) `retainerPreview_findsDueAgreements`; (2) `retainerPreview_excludesNonActive`; (3) `retainerGenerate_closesPeriodsAndCreatesInvoices`; (4) `retainerGenerate_linksToRun`; (5) `retainerGenerate_capturesFailures`. Requires setup: retainer agreements with OPEN periods in the billing period. |

### Key Files

**Slice 308A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunService.java` -- add retainer preview and generate
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunController.java` -- add 2 retainer endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreementRepository.java` -- add query

**Slice 308A -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunRetainerTest.java`

**Slice 308A -- Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodService.java` -- `closePeriod()` signature
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreement.java` -- entity structure
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriod.java` -- period structure

### Architecture Decisions

- **Delegation to `RetainerPeriodService.closePeriod()`**: The billing run does not abbreviate or modify the 12-step retainer close process. Each close produces a standard invoice.
- **Retainer invoices join the batch**: After generation, retainer invoices participate in batch approve/send identically to time-based invoices.

---

## Epic 309: Billing Run List & Detail Pages (Frontend)

**Goal**: Build the frontend pages for listing billing runs and viewing billing run detail with summary statistics, items table, and action buttons. Also create the API client and status badge components shared across billing run UI.

**References**: Architecture doc Section 40.8 (frontend changes).

**Dependencies**: Epic 307 (all backend APIs complete).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **309A** | 309.1--309.7 | `lib/api/billing-runs.ts` API client with all billing run endpoint functions, `billing-run-status-badge.tsx`, `billing-run-item-status-badge.tsx`, billing runs list page, "Billing Runs" tab addition to invoices page. ~7 new/modified files (~6 tests). Frontend only. | **Done** (PR #605) |
| **309B** | 309.8--309.13 | Billing run detail page with summary stat cards, items table (color-coded status), action buttons (Resume, Cancel, Approve Remaining, Send Remaining), server actions for detail page operations. ~5 new files (~5 tests). Frontend only. | **Done** (PR #606) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 309.1 | Create `lib/api/billing-runs.ts` API client | 309A | | New file: `frontend/lib/api/billing-runs.ts`. Functions: `createBillingRun()`, `listBillingRuns()`, `getBillingRun()`, `cancelBillingRun()`, `loadPreview()`, `getItems()`, `getItem()`, `updateSelections()`, `excludeCustomer()`, `includeCustomer()`, `getUnbilledTime()`, `getUnbilledExpenses()`, `generate()`, `batchApprove()`, `batchSend()`, `getRetainerPreview()`, `generateRetainers()`, `getUnbilledSummary()`. Uses `apiClient` from `@/lib/api`. Pattern: `frontend/lib/api.ts` for fetch wrapper pattern. |
| 309.2 | Create TypeScript interfaces for billing run types | 309A | | Add to `lib/api/billing-runs.ts` or a separate types file. Interfaces: `BillingRun`, `BillingRunItem`, `BillingRunStatus`, `BillingRunItemStatus`, `BatchOperationResult`, `BatchFailure`, `CustomerUnbilledSummary`, `CreateBillingRunRequest`, `BatchSendRequest`. |
| 309.3 | Create `billing-run-status-badge.tsx` | 309A | | New file: `frontend/components/billing-runs/billing-run-status-badge.tsx`. Color-coded badge: PREVIEW (slate), IN_PROGRESS (amber), COMPLETED (green), CANCELLED (red). Pattern: `frontend/components/invoices/invoice-status-badge.tsx` (if exists) or similar status badge component. |
| 309.4 | Create `billing-run-item-status-badge.tsx` | 309A | | New file: `frontend/components/billing-runs/billing-run-item-status-badge.tsx`. Color-coded badge: PENDING (slate), GENERATING (amber), GENERATED (green), FAILED (red), EXCLUDED (gray). |
| 309.5 | Create billing runs list page | 309A | 309.1, 309.3 | New file: `frontend/app/(app)/org/[slug]/invoices/billing-runs/page.tsx`. Server component. Fetches `listBillingRuns()`. Shows: active run banner (if PREVIEW/IN_PROGRESS), data table with columns (name, period, created, status badge, customers, invoices, total amount, sent). "New Billing Run" button. Click row navigates to detail. Pattern: `frontend/app/(app)/org/[slug]/invoices/page.tsx` for page/table structure. |
| 309.6 | Add "Billing Runs" tab to invoices page | 309A | 309.5 | Modify: `frontend/app/(app)/org/[slug]/invoices/page.tsx`. Add a tab navigation element linking to `/invoices/billing-runs`. Pattern: existing tab implementations in the app. |
| 309.7 | Write tests for billing run list | 309A | 309.5 | New file: `frontend/__tests__/billing-runs/billing-runs-list.test.tsx`. Tests (~6): (1) status badge renders correct colors; (2) list page renders table with correct columns; (3) "New Billing Run" button present for admin; (4) empty state message; (5) active run banner shows for PREVIEW run; (6) list page renders run history. |
| 309.8 | Create server actions for billing run detail | 309B | 309A | New file: `frontend/app/(app)/org/[slug]/invoices/billing-runs/[id]/actions.ts`. Server actions: `cancelBillingRunAction()`, `batchApproveAction()`, `batchSendAction()`. Each calls the corresponding API client function. Pattern: `frontend/app/(app)/org/[slug]/invoices/[id]/actions.ts`. |
| 309.9 | Create billing run detail page | 309B | 309.8, 309.4 | New file: `frontend/app/(app)/org/[slug]/invoices/billing-runs/[id]/page.tsx`. Server component. Fetches `getBillingRun()` and `getItems()`. Shows: run metadata (name, period, currency, created by, status), 6 summary stat cards (generated, approved, sent, paid, failed, total amount), items data table (customer name, status badge, unbilled amount, invoice number, failure reason). Action buttons depend on status: PREVIEW -> Resume (link to wizard), COMPLETED -> Approve All / Send All / Cancel, IN_PROGRESS -> Cancel. |
| 309.10 | Create summary stat cards component | 309B | | New file: `frontend/components/billing-runs/billing-run-summary-cards.tsx`. Reusable grid of stat cards: generated count, approved count, sent count, failed count, total amount. Uses existing Card component with `font-mono tabular-nums` for numbers. Pattern: `frontend/components/dashboard/` for stat card patterns. |
| 309.11 | Create items data table for detail page | 309B | 309.4 | Part of detail page or extracted component `frontend/components/billing-runs/billing-run-items-table.tsx`. Columns: customer name, status badge, unbilled time amount, unbilled expense amount, invoice number (link to invoice if generated), failure reason (for FAILED items). |
| 309.12 | Create cancel confirmation dialog | 309B | | Part of detail page. `AlertDialog` with confirmation text. On confirm, calls `cancelBillingRunAction()`. Pattern: existing AlertDialog usage in invoice/customer pages. |
| 309.13 | Write tests for billing run detail | 309B | 309.9 | New file: `frontend/__tests__/billing-runs/billing-run-detail.test.tsx`. Tests (~5): (1) detail page renders run metadata; (2) summary cards show correct counts; (3) items table renders customer rows; (4) action buttons conditional on status; (5) failed items show failure reason. |

### Key Files

**Slice 309A -- Create:**
- `frontend/lib/api/billing-runs.ts`
- `frontend/components/billing-runs/billing-run-status-badge.tsx`
- `frontend/components/billing-runs/billing-run-item-status-badge.tsx`
- `frontend/app/(app)/org/[slug]/invoices/billing-runs/page.tsx`
- `frontend/__tests__/billing-runs/billing-runs-list.test.tsx`

**Slice 309A -- Modify:**
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` -- add Billing Runs tab

**Slice 309A -- Read for context:**
- `frontend/lib/api.ts` -- API client pattern
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` -- invoices page layout

**Slice 309B -- Create:**
- `frontend/app/(app)/org/[slug]/invoices/billing-runs/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/invoices/billing-runs/[id]/actions.ts`
- `frontend/components/billing-runs/billing-run-summary-cards.tsx`
- `frontend/components/billing-runs/billing-run-items-table.tsx`
- `frontend/__tests__/billing-runs/billing-run-detail.test.tsx`

**Slice 309B -- Read for context:**
- `frontend/components/dashboard/` -- stat card patterns
- `frontend/app/(app)/org/[slug]/invoices/[id]/` -- invoice detail page for action button patterns

### Architecture Decisions

- **Server Components by default**: Both list and detail pages are server components (no `"use client"`). Data fetched on the server via API client with JWT.
- **API client in single file**: All billing run API functions in one file for cohesion. The wizard, list, and detail pages all import from this file.
- **Tab addition**: "Billing Runs" added as a tab on the existing invoices page, not a separate sidebar entry. Billing runs are an invoicing feature, not a separate domain.

---

## Epic 310: Billing Run Wizard (Frontend)

**Goal**: Build the multi-step billing run wizard with 5 steps: configure, select customers, cherry-pick entries, review drafts, and send. This is the most complex frontend component in Phase 40.

**References**: Architecture doc Section 40.8 (frontend wizard steps).

**Dependencies**: Epic 309 (API client, status badges, base pages).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **310A** | 310.1--310.7 | Wizard scaffold with step navigation (`billing-run-wizard.tsx`), Step 1 configure (`configure-step.tsx`), Step 2 customer selection (`customer-selection-step.tsx`), new billing run page route. ~6 new files (~6 tests). Frontend only. | **Done** (PR #607) |
| **310B** | 310.8--310.12 | Step 3 cherry-pick (`cherry-pick-step.tsx`) -- accordion per customer, time entry table, expense table, per-entry checkboxes, subtotal recalculation, server actions for selection updates. ~4 new files (~5 tests). Frontend only. | **Done** (PR #608) |
| **310C** | 310.13--310.18 | Step 4 review drafts (`review-drafts-step.tsx`) + Step 5 send (`send-step.tsx`) -- draft invoice table, inline editing (due date, payment terms), batch set actions, approve all button, send with progress indicator, confirmation dialog, final summary, done button. ~5 new files (~6 tests). Frontend only. | **Done** (PR #609) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 310.1 | Create wizard scaffold | 310A | 309A | New file: `frontend/components/billing-runs/billing-run-wizard.tsx`. `"use client"` component. Multi-step navigation with step indicator (1-5). State: current step, billing run ID. Steps are linear (can go back, cannot skip ahead). Step labels: Configure, Select Customers, Review & Cherry-Pick, Review Drafts, Send. Pattern: existing multi-step UI patterns in the app (if any), or a simple step counter with conditional rendering. |
| 310.2 | Create new billing run page route | 310A | 310.1 | New file: `frontend/app/(app)/org/[slug]/invoices/billing-runs/new/page.tsx`. Server component that renders `<BillingRunWizard />`. If a billing run ID is in the URL query params (resuming), pass it as a prop. |
| 310.3 | Create Step 1: Configure | 310A | 310.1 | New file: `frontend/components/billing-runs/configure-step.tsx`. `"use client"`. Form fields: name (optional text), billing period (date range picker, default: previous month), currency selector (pre-filled from org settings), include expenses checkbox (default: checked), include retainers checkbox (default: unchecked). "Next" button calls `createBillingRun()` API, stores the returned billing run ID. Pattern: existing form components using Shadcn Input, DatePicker, Checkbox. |
| 310.4 | Create server actions for wizard | 310A | 309.1 | New file: `frontend/app/(app)/org/[slug]/invoices/billing-runs/new/actions.ts`. Server actions: `createBillingRunAction()`, `loadPreviewAction()`, `getUnbilledSummaryAction()`. Delegates to API client functions. |
| 310.5 | Create Step 2: Customer Selection | 310A | 310.4 | New file: `frontend/components/billing-runs/customer-selection-step.tsx`. `"use client"`. Calls `getUnbilledSummary()` to show customers with unbilled work. Data table: checkbox, customer name, unbilled time (hours + amount), unbilled expenses (amount), total, prerequisite status icon (green/amber). Select All/Deselect All toggle. Search/filter by name. Summary bar at bottom (count + total). "Load Preview" button calls `loadPreview()` with selected customer IDs, then advances to Step 3. |
| 310.6 | Create date range picker for billing period | 310A | | May use existing DatePicker or create a range variant. If a date range component does not exist, use two DatePicker fields (from/to). Pattern: `frontend/components/ui/calendar.tsx` and `frontend/components/ui/popover.tsx` for date picker. |
| 310.7 | Write tests for wizard + Steps 1-2 | 310A | 310.5 | New file: `frontend/__tests__/billing-runs/billing-run-wizard.test.tsx`. Tests (~6): (1) wizard renders step indicator; (2) configure step renders form fields; (3) configure step validates required fields; (4) customer selection shows unbilled summary; (5) customer selection checkbox toggles; (6) customer selection summary bar updates. |
| 310.8 | Create Step 3: Cherry-Pick | 310B | 310A | New file: `frontend/components/billing-runs/cherry-pick-step.tsx`. `"use client"`. Accordion list (Shadcn Accordion or Collapsible): one section per customer. Each customer section: customer name + total unbilled amount, time entries table (date, member, task, project, hours, rate, amount, include checkbox), expenses table (date, description, project, amount, include checkbox). Subtotal recalculates as entries are toggled. "Exclude Customer" button per section. Uses `getUnbilledTime()` and `getUnbilledExpenses()` API calls per customer (lazy-loaded on accordion expand). |
| 310.9 | Create server actions for cherry-pick | 310B | 309.1 | New file or modify: `frontend/app/(app)/org/[slug]/invoices/billing-runs/new/actions.ts`. Add: `updateSelectionsAction()`, `excludeCustomerAction()`, `includeCustomerAction()`, `getUnbilledTimeAction()`, `getUnbilledExpensesAction()`. |
| 310.10 | Implement entry toggle with server sync | 310B | 310.8, 310.9 | In `cherry-pick-step.tsx`. When user toggles an entry checkbox, batch the change and call `updateSelectionsAction()` with the updated selection state. Optimistic UI update: toggle checkbox immediately, revert on failure. Debounce API calls to avoid excessive requests when toggling multiple entries quickly. |
| 310.11 | Create retainer section in cherry-pick (if `includeRetainers`) | 310B | 310.8 | Conditional section in `cherry-pick-step.tsx`. If billing run has `includeRetainers = true`, show retainer agreements with due periods. Each agreement: customer name, period, consumed hours, estimated amount, include checkbox. Calls `getRetainerPreview()` API. |
| 310.12 | Write tests for cherry-pick step | 310B | 310.8 | New file: `frontend/__tests__/billing-runs/cherry-pick-step.test.tsx`. Tests (~5): (1) accordion renders customer sections; (2) time entries table renders with checkboxes; (3) entry toggle updates subtotal; (4) exclude customer button works; (5) retainer section shows when enabled. |
| 310.13 | Create Step 4: Review Drafts | 310C | 310A | New file: `frontend/components/billing-runs/review-drafts-step.tsx`. `"use client"`. Calls `generate()` API when entering step (or generate button in cherry-pick step). Shows generated invoices table: customer name, invoice number, line items count, subtotal, tax, total, status (DRAFT/FAILED). Failed items in red with failure reason. Click row opens inline editor in Sheet/Drawer (due date, notes, payment terms). "Batch set due date" action, "Batch set payment terms" action. Summary bar: total drafts, total amount. "Approve All" button calls `batchApprove()`. |
| 310.14 | Create invoice inline editor component | 310C | | New file or part of `review-drafts-step.tsx`. Sheet/Drawer that opens on clicking an invoice row. Fields: due date (date picker), payment terms (dropdown), notes (textarea). Save button calls existing invoice update API. Pattern: existing Sheet usage for detail editing. |
| 310.15 | Create Step 5: Send | 310C | 310.13 | New file: `frontend/components/billing-runs/send-step.tsx`. `"use client"`. Shows approved invoices table: customer name, invoice number, total, email address. Email preview section (shows the template that will be used). "Send All" button with confirmation AlertDialog ("Send X invoices totaling Y?"). During send: progress indicator (polls `getBillingRun()` for `totalSent` updates or uses the send response). Final summary: sent count, total amount, any send failures. "Done" button navigates to billing runs list. |
| 310.16 | Create send progress indicator | 310C | 310.15 | Part of `send-step.tsx`. Progress bar or numeric counter that updates as emails are dispatched. If using polling: poll `getBillingRun()` every 2s during send. If using the synchronous send response: show result after completion. Simpler approach (sync response) preferred for v1. |
| 310.17 | Create batch set actions (due date, payment terms) | 310C | 310.13 | Part of `review-drafts-step.tsx`. Dropdown or popover with date picker for "Set due date for all". Dropdown for "Set payment terms for all". Calls invoice update API for each invoice missing the value. |
| 310.18 | Write tests for Steps 4-5 | 310C | 310.15 | New file: `frontend/__tests__/billing-runs/review-send-steps.test.tsx`. Tests (~6): (1) review step shows generated invoices; (2) failed items shown in red with reason; (3) approve all button calls batchApprove; (4) send step shows approved invoices; (5) send confirmation dialog appears; (6) done button navigates away. |

### Key Files

**Slice 310A -- Create:**
- `frontend/components/billing-runs/billing-run-wizard.tsx`
- `frontend/components/billing-runs/configure-step.tsx`
- `frontend/components/billing-runs/customer-selection-step.tsx`
- `frontend/app/(app)/org/[slug]/invoices/billing-runs/new/page.tsx`
- `frontend/app/(app)/org/[slug]/invoices/billing-runs/new/actions.ts`
- `frontend/__tests__/billing-runs/billing-run-wizard.test.tsx`

**Slice 310A -- Read for context:**
- `frontend/components/ui/` -- Shadcn components (Input, Button, Checkbox, DatePicker)
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` -- invoices page for layout consistency
- `frontend/lib/api/billing-runs.ts` -- API client from 309A

**Slice 310B -- Create:**
- `frontend/components/billing-runs/cherry-pick-step.tsx`
- `frontend/__tests__/billing-runs/cherry-pick-step.test.tsx`

**Slice 310B -- Modify:**
- `frontend/app/(app)/org/[slug]/invoices/billing-runs/new/actions.ts` -- add selection actions

**Slice 310B -- Read for context:**
- `frontend/components/ui/accordion.tsx` or `frontend/components/ui/collapsible.tsx` -- accordion pattern

**Slice 310C -- Create:**
- `frontend/components/billing-runs/review-drafts-step.tsx`
- `frontend/components/billing-runs/send-step.tsx`
- `frontend/__tests__/billing-runs/review-send-steps.test.tsx`

**Slice 310C -- Read for context:**
- `frontend/components/ui/sheet.tsx` -- Sheet for inline editor
- `frontend/components/ui/alert-dialog.tsx` -- confirmation dialog pattern

### Architecture Decisions

- **Wizard state backed by server**: The `BillingRun` entity in PREVIEW status acts as the wizard's backing store. Closing the browser doesn't lose work -- the user can resume by navigating to the billing run detail page.
- **`"use client"` for all wizard components**: Wizard steps use hooks, event handlers, and manage local state (current step, selections). They must be client components.
- **Lazy-load cherry-pick data**: Unbilled time entries and expenses are loaded per customer when the accordion section is expanded, not all at once. This avoids loading thousands of entries for all customers upfront.
- **3 slices for wizard**: The wizard has 5 steps but is split into 3 slices: Steps 1-2 (configuration + selection, simpler forms), Step 3 (cherry-pick, most complex UI with accordion + per-entry checkboxes), Steps 4-5 (review + send, similar table-based UIs).

---

## Epic 311: Billing Settings & Polish (Frontend)

**Goal**: Add batch billing settings section to the Settings page, allowing admins to configure the async threshold, email rate limit, and default billing run currency.

**References**: Architecture doc Sections 40.2.5, 40.8.

**Dependencies**: Epic 309 (base frontend pages, API client).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **311A** | 311.1--311.5 | Batch billing settings section on Settings page (3 fields: async threshold, email rate limit, default currency), server action for update. ~3 modified/new files (~4 tests). Frontend only. | **Done** (PR #610) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 311.1 | Create billing settings component | 311A | | New file: `frontend/components/settings/batch-billing-settings.tsx`. Card with 3 fields: (1) Async threshold (number input, default 50, help text: "Customer count above which batch generation runs asynchronously"), (2) Email rate limit (number input, default 5, help text: "Maximum emails per second during batch send"), (3) Default billing run currency (3-letter input or dropdown, nullable). Save button. Pattern: existing settings sections on the Settings page. |
| 311.2 | Create server action for batch billing settings update | 311A | | New file or modify: `frontend/app/(app)/org/[slug]/settings/actions.ts`. Add `updateBatchBillingSettingsAction()` calling the OrgSettings update API with the 3 batch billing fields. |
| 311.3 | Add billing settings section to Settings page | 311A | 311.1 | Modify: `frontend/app/(app)/org/[slug]/settings/page.tsx`. Import and render `<BatchBillingSettings />` component. Place in the appropriate section (billing/invoicing area). Pass current OrgSettings values as props. |
| 311.4 | Add nav item for billing runs (if needed) | 311A | | Review: `frontend/lib/nav-items.ts`. If "Billing Runs" needs a dedicated sidebar entry (in addition to the tab on invoices), add it. Otherwise skip -- the tab on invoices page may be sufficient. |
| 311.5 | Write tests for billing settings | 311A | 311.1 | New file: `frontend/__tests__/billing-runs/batch-billing-settings.test.tsx`. Tests (~4): (1) renders 3 settings fields; (2) displays current values; (3) validates numeric inputs; (4) save button calls update action. |

### Key Files

**Slice 311A -- Create:**
- `frontend/components/settings/batch-billing-settings.tsx`
- `frontend/__tests__/billing-runs/batch-billing-settings.test.tsx`

**Slice 311A -- Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` -- add billing settings section
- `frontend/app/(app)/org/[slug]/settings/actions.ts` -- add update action

**Slice 311A -- Read for context:**
- `frontend/components/settings/` -- existing settings section patterns
- `frontend/app/(app)/org/[slug]/settings/page.tsx` -- current settings page layout

### Architecture Decisions

- **Settings page integration**: Batch billing settings are added to the existing Settings page (not a separate page). They are an org-level configuration, consistent with other OrgSettings fields.
- **No separate sidebar entry**: Billing runs are accessed via the "Billing Runs" tab on the invoices page. A dedicated sidebar entry would add clutter for a feature used monthly.

---

### Critical Files for Implementation
- `/Users/rakheendama/Projects/2026/b2b-strawman/architecture/phase40-bulk-billing-batch-operations.md` - Full architecture specification with domain model, flows, API surface, migration DDL, and implementation guidance
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` - Core service to delegate to: `createDraft()`, `approve()`, `send()`, `getUnbilledTime()` -- the billing run orchestrates these existing methods
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` - Entity pattern to follow for BillingRun entities, and the entity that gains the `billingRunId` field
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` - Entity gaining 3 batch billing configuration fields
- `/Users/rakheendama/Projects/2026/b2b-strawman/frontend/app/(app)/org/[slug]/invoices/page.tsx` - Existing invoices page where the "Billing Runs" tab will be added