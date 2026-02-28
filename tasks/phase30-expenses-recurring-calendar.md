# Phase 30 — Expenses, Recurring Tasks & Daily Work Completeness

Phase 30 closes the remaining revenue-capture and daily-work-organisation gaps in the DocTeams platform. Professional services firms bill 15–30% of revenue as disbursements, yet the platform currently tracks only time-based fees. This phase adds four independent feature tracks that have no data dependencies on each other and can be developed in full parallel.

**Architecture doc**: `architecture/phase30-expenses-recurring-calendar.md`

**ADRs**:
- [ADR-114](../adr/ADR-114-expense-billing-status-derivation.md) — Expense Billing Status Derivation (computed, not persisted)
- [ADR-115](../adr/ADR-115-expense-markup-model.md) — Expense Markup Model (org-default + per-expense override)
- [ADR-116](../adr/ADR-116-recurring-task-on-entity.md) — Recurring Task Implementation (fields on Task entity, synchronous auto-creation)
- [ADR-117](../adr/ADR-117-time-reminder-scheduling.md) — Time Reminder Scheduling Strategy (15-minute polling, UTC interpretation)
- [ADR-118](../adr/ADR-118-invoice-line-type-discriminator.md) — Invoice Line Type Discriminator (explicit `line_type` column)

**Migrations**: V50 (single combined tenant migration: expenses table, task recurrence columns, invoice_lines extension, org_settings extension).

**Note on migration numbering**: Phase 29 used V47 (based on its architecture doc). Phase 31 used subsequent V48/V49 migrations. The next available is V50 for Phase 30.

**Dependencies on prior phases**: Phase 4 (Tasks, Projects), Phase 5 (TimeEntry billing patterns), Phase 6 (AuditService), Phase 6.5 (NotificationService, NotificationPreference), Phase 8 (OrgSettings entity, profitability reports), Phase 10 (Invoice, InvoiceLine), Phase 12 (S3/Document infrastructure for receipts), Phase 29 (Project lifecycle guards, task status transitions, project due dates).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 218 | Expense Entity Foundation & Migration | Backend | -- | M | 218A, 218B | **Done** (PRs #447, #448) |
| 219 | Expense Service, Controller & CRUD API | Backend | 218 | L | 219A, 219B | **Done** (PRs #449, #450) |
| 220 | Expense Frontend — Project Expenses Tab | Frontend | 219 | M | 220A, 220B | **Done** (PRs #451, #452) |
| 221 | Expense Billing Integration — InvoiceLine Extension & Invoice Pipeline | Backend | 218 | L | 221A, 221B | **Done** (PRs #453, #454) |
| 222 | Expense Billing Frontend — Unbilled Summary & Invoice Generation | Frontend | 221, 220 | M | 222A | **Done** (PR #455) |
| 223 | Recurring Task Foundation — Migration & Entity | Backend | -- | M | 223A, 223B | |
| 224 | Recurring Task Service & Controller | Backend | 223 | M | 224A, 224B | |
| 225 | Recurring Task Frontend | Frontend | 224 | M | 225A | |
| 226 | Time Reminder Scheduler & OrgSettings | Backend | -- | M | 226A, 226B | |
| 227 | Time Reminder Frontend — Settings & Preferences | Frontend | 226 | S | 227A | |
| 228 | Calendar View — Backend Endpoint | Backend | -- | M | 228A | |
| 229 | Calendar View — Frontend Page | Frontend | 228 | M | 229A, 229B | |

---

## Dependency Graph

```
TRACK 1: EXPENSES
─────────────────
[E218A V50 migration: expenses table
 + OrgSettings markup column]
        |
[E218B Expense entity + category enum
 + ExpenseRepository + entity tests]
        |
        +─────────────────────────────────+
        |                                 |
[E219A ExpenseService: CRUD,     [E221A InvoiceLine extension:
 validation, billing status,      add expense_id + line_type
 write-off, audit events]         to entity + repo + migration
        |                         backfill + InvoiceLineType enum]
[E219B ExpenseController:                 |
 7 endpoints + integration tests] [E221B InvoiceService extension:
        |                          unbilled expenses, generate
        |                          with expenses, approve/void
        |                          + profitability extension
        |                          + integration tests]
        |                                 |
[E220A Expenses tab UI:           [E222A Expense Billing Frontend:
 expense-list.tsx,                 unbilled-summary expenses,
 log-expense-dialog.tsx,           generate-invoice-dialog
 expense-category-badge.tsx,       expense selection + tests]
 project layout tab extension]
        |
[E220B My Expenses + expense
 write-off UX + edit dialog
 + frontend tests]

TRACK 2: RECURRING TASKS
─────────────────────────
[E223A V50 migration: task
 recurrence columns + index]
        |
[E223B Task entity extension:
 recurrenceRule, recurrenceEndDate,
 parentTaskId fields + RecurrenceRule
 value object + unit tests]
        |
[E224A TaskService: completeTask()
 recurrence auto-creation,
 RRULE parsing, next-date calc,
 audit + notification]
        |
[E224B TaskController: extend
 complete response with nextInstance,
 recurrence fields in create/update
 DTOs, recurring filter + tests]
        |
[E225A Recurring Task Frontend:
 recurrence form, badge, detail
 sheet info, completion toast,
 filter + tests]

TRACK 3: TIME REMINDERS
─────────────────────────
[E226A V50 migration: org_settings
 time reminder columns + OrgSettings
 entity/DTO extension + unit tests]
        |
[E226B TimeReminderScheduler:
 @Scheduled job, per-org processing,
 working day check, member time query,
 notification creation + tests]
        |
[E227A Time Reminders Frontend:
 settings section, toggle, working
 days, time picker, min hours,
 notification preference toggle + tests]

TRACK 4: CALENDAR VIEW
──────────────────────
[E228A CalendarService +
 CalendarController: GET /api/calendar,
 aggregation query, access control,
 CalendarItemDto + tests]
        |
[E229A Calendar page:
 month-view, list-view components,
 sidebar nav link, date navigation]
        |
[E229B Calendar overdue section,
 filters (project/type/assignee),
 color coding + frontend tests]
```

**Parallel opportunities**:
- Tracks 1, 2, 3, and 4 are fully independent — all four can start simultaneously.
- Within Track 1: 218A runs first; after 218B, epics 219 and 221 run in parallel; after 219B, 220 starts; after 221B, 222 starts (or in parallel with 220B).
- Within Track 2: 223A → 223B → 224A → 224B → 225A (sequential chain).
- Within Track 3: 226A → 226B → 227A (sequential chain).
- Within Track 4: 228A → 229A → 229B (sequential chain).

---

## Implementation Order

### Stage 0: Database Migration (all tracks in parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a (parallel) | 218 | 218A | V50 tenant migration: CREATE TABLE expenses + indexes + task recurrence columns + invoice_lines ALTER (expense_id, line_type) + org_settings ALTER (time reminder + markup columns). Single combined file. ~1 new migration file. Backend only. | **Done** (PR #447) |
| 0b (parallel) | 223 | 223A | Task recurrence columns are part of V50 (contributed by 218A builder, or appended here if running in parallel). If parallel: 223A creates its own section of V50. See migration strategy note. | |
| 0c (parallel) | 226 | 226A | OrgSettings extension: entity + DTO + migration — can piggyback on V50 from 218A. Extend entity with 4 time reminder fields + 1 markup field. ~2 modified files, ~1 migration section. Backend only. | |

### Stage 1: Entity Layer (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 218 | 218B | Expense entity + ExpenseCategory enum + ExpenseRepository + entity unit tests. ~4 new files. Backend only. | **Done** (PR #448) |
| 1b | 223 | 223B | Task entity extension (3 new fields) + RecurrenceRule value object (RRULE parser, next-date calculator) + unit tests. ~3 new files, ~1 modified file. Backend only. | |

### Stage 2: Service Layer (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 219 | 219A | ExpenseService: full CRUD, validation, billing status computation, write-off/restore, audit events, domain events. ~1 new file, ~1 new event file. Backend only. | **Done** (PR #449) |
| 2b (parallel) | 221 | 221A | InvoiceLine entity extension (expenseId + lineType fields) + InvoiceLineType enum + InvoiceLineRepository update + backfill handling. ~3 modified/new files. Backend only. | **Done** (PR #453) |
| 2c (parallel) | 224 | 224A | TaskService.completeTask() extension: recurrence check, next-date calculation, auto-create new Task in same transaction, audit event, notification. ~1 modified file, ~1 new event file. Backend only. | |
| 2d (parallel) | 226 | 226B | TimeReminderScheduler: @Scheduled component, per-org processing, working day check, member time query, notification creation. ~1 new file. Backend only. | |

### Stage 3: Controllers + Integration Tests (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 219 | 219B | ExpenseController: 7 endpoints + complete integration test suite (~20 tests). ~1 new controller file, ~1 new test file. Backend only. | **Done** (PR #450) |
| 3b (parallel) | 221 | 221B | InvoiceService extension: unbilled summary + expenses, generate with EXPENSE lines, approve stamps expenses, void clears invoiceId on expenses + profitability query extension + tests (~15 tests). ~2 modified files, ~1 new test file. Backend only. | **Done** (PR #454) |
| 3c (parallel) | 224 | 224B | TaskController: extend complete response with nextInstance, recurrence fields in create/update DTOs, ?recurring=true filter + integration tests (~15 tests). ~1 modified file, ~1 new test file. Backend only. | |
| 3d (parallel) | 228 | 228A | CalendarService + CalendarController: GET /api/calendar, UNION ALL query, access control + tests (~5 tests). ~2 new files, ~1 new test file. Backend only. | |

### Stage 4: Frontend (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a (parallel) | 220 | 220A | Project Expenses tab: layout extension, expense-list.tsx, log-expense-dialog.tsx, expense-category-badge.tsx, expense-actions.ts. ~5 new/modified files. Frontend only. | **Done** (PR #451) |
| 4b (parallel) | 225 | 225A | Recurring Task Frontend: recurrence form section in task dialogs, badge in task list, recurrence info in task detail sheet, completion toast + tests. ~4 modified files. Frontend only. | |
| 4c (parallel) | 227 | 227A | Time Reminders Frontend: settings section + notification preference toggle + tests. ~2 modified files. Frontend only. | |
| 4d (parallel) | 229 | 229A | Calendar page shell: month-view component, list-view component, sidebar nav addition, calendar-actions.ts, basic routing. ~4 new files. Frontend only. | |

### Stage 5: Frontend Polish & Integration (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 220 | 220B | My Expenses section on My Work page, write-off UX, edit expense dialog + frontend tests (~10 tests). ~3 modified files. Frontend only. | **Done** (PR #452) |
| 5b (parallel) | 222 | 222A | Expense Billing Frontend: unbilled-summary.tsx expense section, generate-invoice-dialog.tsx expense selection + tests (~7 tests). ~2 modified files. Frontend only. | **Done** (PR #455) |
| 5c (parallel) | 229 | 229B | Calendar overdue section, filters (project/type/assignee), color coding + frontend tests (~7 tests). ~3 modified files. Frontend only. | |

### Timeline

```
Stage 0: [218A] // [223A] // [226A]                              (parallel)
Stage 1: [218B] // [223B]                                         (parallel, after 218A / 223A)
Stage 2: [219A] // [221A] // [224A] // [226B]                    (parallel)
Stage 3: [219B] // [221B] // [224B] // [228A]                    (parallel)
Stage 4: [220A] // [225A] // [227A] // [229A]                    (parallel)
Stage 5: [220B] // [222A] // [229B]                              (parallel)
```

**Critical path (Track 1 — Expense)**: 218A → 218B → 219A → 219B → 220A → 220B (6 slices sequential at most).

**Critical path (Track 2 — Recurring)**: 223A → 223B → 224A → 224B → 225A (5 slices sequential).

**Note on V50 migration**: All schema changes for this phase belong in a single `V50__expenses_recurring_calendar.sql` file (matching the architecture doc). The recommended approach is that the builder for 218A creates the full V50 file with all sections (expenses table, task recurrence ALTER, invoice_lines ALTER, org_settings ALTER). If builders for 223A and 226A run in parallel, they should omit their migration sections from V50 since 218A handles them — the orchestrator must coordinate this.

---

## Epic 218: Expense Entity Foundation & Migration

**Goal**: Create the database schema for the `expenses` table and all Phase 30 schema changes in a single V50 migration, then build the `Expense` entity, `ExpenseCategory` enum, and `ExpenseRepository` as the foundation for the expense track.

**References**: Architecture doc Section 30.7 (Database Migration), Section 30.2.1 (Expense Entity), Section 30.8.1, Section 30.8.3–30.8.4. ADR-114, ADR-115.

**Dependencies**: None — this is the foundation epic for Track 1.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **218A** | 218.1–218.4 | V50 combined tenant migration: (1) CREATE TABLE expenses with all columns, constraints, and 4 indexes; (2) ALTER TABLE tasks ADD 3 recurrence columns + 1 index; (3) ALTER TABLE invoice_lines ADD expense_id + line_type columns + backfill UPDATE statements + CHECK constraint + 1 index; (4) ALTER TABLE org_settings ADD 5 columns (time reminder × 4 + markup). ~1 new migration file. Backend only. | **Done** (PR #447) |
| **218B** | 218.5–218.10 | `Expense` entity (13 columns + 3 computed methods) + `ExpenseCategory` enum (8 values) + `ExpenseRepository` (4 query methods) + entity unit tests covering computed billing status and billable amount calculation. ~4 new files. Backend only. | **Done** (PR #448) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 218.1 | Create V50 combined tenant migration — expenses table | 218A | | New file: `backend/src/main/resources/db/migration/tenant/V50__expenses_recurring_calendar.sql`. Section 1: `CREATE TABLE expenses (id UUID PK DEFAULT gen_random_uuid(), project_id UUID NOT NULL, task_id UUID, member_id UUID NOT NULL, date DATE NOT NULL, description VARCHAR(500) NOT NULL, amount NUMERIC(12,2) NOT NULL, currency VARCHAR(3) NOT NULL, category VARCHAR(30) NOT NULL, receipt_document_id UUID, billable BOOLEAN NOT NULL DEFAULT true, invoice_id UUID, markup_percent NUMERIC(5,2), notes VARCHAR(1000), created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now(), CONSTRAINT expenses_amount_positive CHECK (amount > 0), CONSTRAINT expenses_category_check CHECK (category IN ('FILING_FEE','TRAVEL','COURIER','SOFTWARE','SUBCONTRACTOR','PRINTING','COMMUNICATION','OTHER')), CONSTRAINT expenses_markup_non_negative CHECK (markup_percent IS NULL OR markup_percent >= 0))`. Then 4 indexes: `idx_expenses_project_id`, `idx_expenses_member_id`, `idx_expenses_billable_invoice (partial: WHERE billable = true AND invoice_id IS NULL)`, `idx_expenses_project_date`. |
| 218.2 | V50 migration — task recurrence columns | 218A | | Section 2 (same file): `ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence_rule VARCHAR(100)`. `ALTER TABLE tasks ADD COLUMN IF NOT EXISTS recurrence_end_date DATE`. `ALTER TABLE tasks ADD COLUMN IF NOT EXISTS parent_task_id UUID`. `CREATE INDEX IF NOT EXISTS idx_tasks_parent_task_id ON tasks(parent_task_id) WHERE parent_task_id IS NOT NULL`. |
| 218.3 | V50 migration — invoice_lines extension | 218A | | Section 3 (same file): `ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS expense_id UUID`. `ALTER TABLE invoice_lines ADD COLUMN IF NOT EXISTS line_type VARCHAR(20) NOT NULL DEFAULT 'TIME'`. Backfill: `UPDATE invoice_lines SET line_type = 'RETAINER' WHERE retainer_period_id IS NOT NULL AND line_type = 'TIME'`. `UPDATE invoice_lines SET line_type = 'MANUAL' WHERE time_entry_id IS NULL AND retainer_period_id IS NULL AND line_type = 'TIME'`. `ALTER TABLE invoice_lines ADD CONSTRAINT invoice_lines_line_type_check CHECK (line_type IN ('TIME','RETAINER','EXPENSE','MANUAL'))`. `CREATE INDEX IF NOT EXISTS idx_invoice_lines_expense_id ON invoice_lines(expense_id) WHERE expense_id IS NOT NULL`. See ADR-118. |
| 218.4 | V50 migration — org_settings extension | 218A | | Section 4 (same file): `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_enabled BOOLEAN NOT NULL DEFAULT false`. `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_days VARCHAR(50) DEFAULT 'MON,TUE,WED,THU,FRI'`. `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_time TIME DEFAULT '17:00'`. `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS time_reminder_min_minutes INTEGER DEFAULT 240`. `ALTER TABLE org_settings ADD COLUMN IF NOT EXISTS default_expense_markup_percent NUMERIC(5,2)`. |
| 218.5 | Create `ExpenseCategory` enum | 218B | | New file: `expense/ExpenseCategory.java`. Values: `FILING_FEE`, `TRAVEL`, `COURIER`, `SOFTWARE`, `SUBCONTRACTOR`, `PRINTING`, `COMMUNICATION`, `OTHER`. Include `fromString(String)` static method for validation. Pattern: `task/TaskPriority.java`. |
| 218.6 | Create `Expense` entity | 218B | 218.5 | New file: `expense/Expense.java`. All 13 DB columns as fields with `@Column` annotations. Constructor sets `billable = true`, `createdAt = Instant.now()`, `updatedAt = Instant.now()`. Three computed methods: (1) `getBillingStatus()` — if `invoiceId != null` return "BILLED"; if `!billable` return "NON_BILLABLE"; else "UNBILLED". (2) `getBillableAmount()` — per-expense markup only, zero if non-billable. (3) `computeBillableAmount(BigDecimal orgDefaultMarkupPercent)` — effective markup with org fallback. See entity code pattern in architecture doc Section 30.8.3. Pattern: `timeentry/TimeEntry.java`. No `@ManyToOne`, UUID soft FKs only. |
| 218.7 | Add `update()` method to Expense entity | 218B | 218.6 | In `expense/Expense.java`: add `update(LocalDate date, String description, BigDecimal amount, String currency, String category, UUID taskId, UUID receiptDocumentId, BigDecimal markupPercent, boolean billable, String notes)` method. Validates: expense must not be BILLED (`invoiceId != null` → throw `IllegalStateException`). Sets `updatedAt = Instant.now()`. |
| 218.8 | Add `writeOff()` and `restore()` methods to Expense entity | 218B | 218.6 | In `expense/Expense.java`: `writeOff()` — validates `invoiceId == null` and `billable == true`, sets `billable = false`, `updatedAt`. Throws `IllegalStateException` if BILLED or already NON_BILLABLE. `restore()` — validates `billable == false` and `invoiceId == null`, sets `billable = true`, `updatedAt`. Throws `IllegalStateException` if already billable or BILLED. |
| 218.9 | Create `ExpenseRepository` | 218B | 218.6 | New file: `expense/ExpenseRepository.java`. Extends `JpaRepository<Expense, UUID>`. Methods: (1) `Page<Expense> findByProjectId(UUID projectId, Pageable pageable)`. (2) `List<Expense> findByProjectIdAndBillableTrueAndInvoiceIdIsNull(UUID projectId)` — unbilled expenses for invoice generation. (3) `Page<Expense> findByMemberId(UUID memberId, Pageable pageable)` — "my expenses". (4) `@Query` `findFiltered(UUID projectId, String category, LocalDate from, LocalDate to, UUID memberId, Pageable pageable)` — parameterized JPQL with nullability checks. See repository code pattern in architecture doc Section 30.8.4. |
| 218.10 | Write Expense entity unit tests | 218B | 218.6, 218.7, 218.8 | New file: `expense/ExpenseTest.java`. Tests: (1) `newExpense_defaultsBillableTrue`; (2) `getBillingStatus_whenInvoiceIdSet_returnsBilled`; (3) `getBillingStatus_whenBillableFalse_returnsNonBillable`; (4) `getBillingStatus_whenBillableAndNoInvoice_returnsUnbilled`; (5) `computeBillableAmount_usesPerExpenseMarkup`; (6) `computeBillableAmount_fallsBackToOrgDefault`; (7) `computeBillableAmount_zeroWhenNonBillable`; (8) `writeOff_fromUnbilled_succeeds`; (9) `writeOff_whenBilled_throws`; (10) `restore_fromNonBillable_succeeds`; (11) `restore_whenAlreadyBillable_throws`; (12) `update_whenBilled_throws`. ~12 tests. Pattern: `acceptance/AcceptanceRequestTest.java`. |

### Key Files

**Slice 218A — Create:**
- `backend/src/main/resources/db/migration/tenant/V50__expenses_recurring_calendar.sql`

**Slice 218B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/ExpenseCategory.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/Expense.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/ExpenseRepository.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/expense/ExpenseTest.java`

**Slice 218B — Read for context:**
- `backend/src/main/java/.../timeentry/TimeEntry.java` — billing status pattern + UUID soft FK pattern
- `backend/src/main/java/.../task/TaskPriority.java` — simple enum pattern
- `backend/src/main/java/.../timeentry/TimeEntryRepository.java` — repository query method pattern

### Architecture Decisions

- **Single V50 migration file for all Phase 30 schema changes**: All four tracks (expenses, recurring tasks, invoice_lines, org_settings) contribute to one combined file. This avoids migration numbering conflicts when tracks run in parallel. The 218A builder creates the full file; parallel builders for 223A and 226A skip their migration sections.
- **Expense entity mirrors TimeEntry**: No `@ManyToOne`, no `@Filter`, no `billing_status` column. Computed billing status from `billable + invoiceId` per ADR-114.
- **`computeBillableAmount` takes org default as parameter**: The entity cannot access OrgSettings directly — the caller passes the org default. This keeps the entity pure and testable without Spring context.

---

## Epic 219: Expense Service, Controller & CRUD API

**Goal**: Implement `ExpenseService` with full CRUD, validation, billing status computation, write-off/restore and audit events. Add `ExpenseController` with 7 endpoints and a comprehensive integration test suite.

**References**: Architecture doc Sections 30.3.1, 30.4.1, 30.8.1, 30.9. ADR-114, ADR-115.

**Dependencies**: Epic 218 (entity + repository).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **219A** | 219.1–219.7 | `ExpenseService`: createExpense, getExpense, listExpenses, updateExpense, deleteExpense, writeOffExpense, restoreExpense, getMyExpenses. All operations include project access validation, billing status guard, OrgSettings load for default markup, audit events, domain events. ~1 new service file, ~2 new event files. Backend only. | **Done** (PR #449) |
| **219B** | 219.8–219.15 | `ExpenseController`: 7 endpoints with `@PreAuthorize`, `ExpenseResponse` DTO, `CreateExpenseRequest`/`UpdateExpenseRequest` records, complete integration test suite (~20 tests). ~1 new controller file, ~1 new test file. Backend only. | **Done** (PR #450) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 219.1 | Create `ExpenseCreatedEvent` and `ExpenseDeletedEvent` domain events | 219A | | New files: `event/ExpenseCreatedEvent.java`, `event/ExpenseDeletedEvent.java`. Java records with fields: `UUID expenseId, UUID projectId, UUID memberId, String tenantId, String orgId, Instant occurredAt`. Pattern: `event/TaskCompletedEvent.java`. |
| 219.2 | Implement `ExpenseService.createExpense()` | 219A | 218B | New file: `expense/ExpenseService.java`. Method: `createExpense(UUID projectId, UUID memberId, CreateExpenseRequest request)`. Validates: (1) project exists and status is ACTIVE via `ProjectRepository.findById()` — throw 404 if not found, 409 if COMPLETED/ARCHIVED; (2) if `taskId` provided, verify task belongs to same project via `TaskRepository`; (3) if `receiptDocumentId` provided, verify document exists via `DocumentRepository`; (4) load `OrgSettings` for default currency fallback; (5) validate `amount > 0`, `category` is valid `ExpenseCategory` value. Create `Expense`, save, audit `expense.created`, publish `ExpenseCreatedEvent`. Return saved expense with `OrgSettings.defaultExpenseMarkupPercent` for `computeBillableAmount`. Pattern: `timeentry/TimeEntryService.java`. |
| 219.3 | Implement `ExpenseService.getExpense()` and `listExpenses()` | 219A | 218B | In `expense/ExpenseService.java`. `getExpense(UUID projectId, UUID expenseId, UUID memberId)`: validate project access via `ProjectAccessService.requireViewAccess()`, load expense, verify `expense.projectId == projectId`, load OrgSettings, return. `listExpenses(UUID projectId, String category, LocalDate from, LocalDate to, UUID filterMemberId, Pageable pageable)`: project access check, call `expenseRepository.findFiltered()`, enrich with OrgSettings markup for billableAmount in each response. |
| 219.4 | Implement `ExpenseService.updateExpense()` | 219A | 218B | In `expense/ExpenseService.java`. `updateExpense(UUID projectId, UUID expenseId, UUID memberId, String orgRole, UpdateExpenseRequest request)`. Permission: creator OR ADMIN+. Guard: expense must not be BILLED (`invoiceId != null` → 409). Call `expense.update(...)`. Save. Audit `expense.updated`. Return. |
| 219.5 | Implement `ExpenseService.deleteExpense()` | 219A | 218B | In `expense/ExpenseService.java`. `deleteExpense(UUID projectId, UUID expenseId, UUID memberId, String orgRole)`. Permission: creator OR ADMIN+. Guard: expense billing status must be UNBILLED or NON_BILLABLE (not BILLED). Hard delete via `expenseRepository.deleteById()`. Audit `expense.deleted`. Publish `ExpenseDeletedEvent`. |
| 219.6 | Implement `ExpenseService.writeOffExpense()` and `restoreExpense()` | 219A | 218B | In `expense/ExpenseService.java`. `writeOffExpense(UUID projectId, UUID expenseId, UUID memberId, String orgRole)`: ADMIN+ only (throw `ForbiddenException` otherwise). Call `expense.writeOff()`. Save. Audit `expense.written_off`. `restoreExpense(UUID projectId, UUID expenseId, UUID memberId, String orgRole)`: ADMIN+ only. Call `expense.restore()`. Save. Audit `expense.restored`. |
| 219.7 | Implement `ExpenseService.getMyExpenses()` | 219A | 218B | In `expense/ExpenseService.java`. `getMyExpenses(UUID memberId, Pageable pageable)`. No project scoping — cross-project personal view. Call `expenseRepository.findByMemberId(memberId, pageable)`. Load OrgSettings per unique project (cache in map to avoid N+1 on OrgSettings). Return page of `ExpenseResponse`. |
| 219.8 | Create `ExpenseController` with 5 project-scoped endpoints | 219B | 219A | New file: `expense/ExpenseController.java`. Endpoints: (1) `POST /api/projects/{projectId}/expenses` → 201 Created; (2) `GET /api/projects/{projectId}/expenses` → paged list with `?category&from&to&memberId&page&size&sort`; (3) `GET /api/projects/{projectId}/expenses/{id}` → single expense; (4) `PUT /api/projects/{projectId}/expenses/{id}` → update; (5) `DELETE /api/projects/{projectId}/expenses/{id}` → 204 No Content. All endpoints: `@PreAuthorize("hasAnyRole('ORG_MEMBER','ORG_ADMIN','ORG_OWNER')")` (fine-grained in service). Read `RequestScopes.MEMBER_ID` and `RequestScopes.ORG_ROLE` from ScopedValue. Pattern: `timeentry/TimeEntryController.java`. |
| 219.9 | Add `PATCH /write-off` endpoint and `GET /expenses/mine` | 219B | 219A | In `expense/ExpenseController.java`: `PATCH /api/projects/{projectId}/expenses/{id}/write-off` → 200 with updated expense; `PATCH /api/projects/{projectId}/expenses/{id}/restore` → 200 with updated expense (both ADMIN+ via `@PreAuthorize("hasAnyRole('ORG_ADMIN','ORG_OWNER')")`). Separate: `GET /api/expenses/mine` → paged cross-project personal expense list (`@PreAuthorize("hasAnyRole('ORG_MEMBER','ORG_ADMIN','ORG_OWNER')")`). |
| 219.10 | Create `ExpenseResponse` DTO and request records | 219B | | In `expense/ExpenseController.java` as nested records. `ExpenseResponse`: all entity fields + computed `billingStatus` (String), `billableAmount` (BigDecimal), `memberName` (String, resolved from member name map). `CreateExpenseRequest`: `date`, `description`, `amount`, `currency`, `category`, `taskId` (nullable), `receiptDocumentId` (nullable), `markupPercent` (nullable), `billable` (default true), `notes` (nullable). `UpdateExpenseRequest`: same nullable fields. |
| 219.11 | Write expense integration tests — CRUD happy paths | 219B | 219.8, 219.9 | New file: `expense/ExpenseControllerTest.java`. Tests (1–8): (1) `createExpense_returnsCreated`; (2) `createExpense_invalidCategory_returns400`; (3) `createExpense_negativeAmount_returns400`; (4) `createExpense_onCompletedProject_returns409`; (5) `getExpense_returnsExpenseWithComputedFields`; (6) `listExpenses_filterByCategory`; (7) `listExpenses_filterByBillingStatus_unbilled`; (8) `getMyExpenses_returnsCrossProjectResults`. Pattern: `timeentry/TimeEntryControllerTest.java` (MockMvc + JWT mocking). |
| 219.12 | Write expense integration tests — update and delete | 219B | 219.11 | Continuing `expense/ExpenseControllerTest.java`. Tests (9–14): (9) `updateExpense_asCreator_succeeds`; (10) `updateExpense_asOtherMember_forbidden`; (11) `updateExpense_whenBilled_returns409`; (12) `deleteExpense_whenUnbilled_succeeds`; (13) `deleteExpense_whenBilled_returns409`; (14) `deleteExpense_asNonCreatorNonAdmin_returns403`. |
| 219.13 | Write expense integration tests — write-off and restore | 219B | 219.12 | Continuing `expense/ExpenseControllerTest.java`. Tests (15–20): (15) `writeOffExpense_asAdmin_succeeds`; (16) `writeOffExpense_asMember_returns403`; (17) `writeOffExpense_whenBilled_returns409`; (18) `restoreExpense_fromNonBillable_succeeds`; (19) `restoreExpense_whenAlreadyBillable_returns409`; (20) `billingStatus_derivedCorrectly_acrossStateChanges`. Total: ~20 integration tests. |

### Key Files

**Slice 219A — Create:**
- `backend/src/main/java/.../expense/ExpenseService.java`
- `backend/src/main/java/.../event/ExpenseCreatedEvent.java`
- `backend/src/main/java/.../event/ExpenseDeletedEvent.java`

**Slice 219A — Read for context:**
- `backend/src/main/java/.../timeentry/TimeEntryService.java` — service structure pattern
- `backend/src/main/java/.../project/ProjectAccessService.java` — access control pattern
- `backend/src/main/java/.../settings/OrgSettings.java` — OrgSettings access pattern
- `backend/src/main/java/.../customer/CustomerLifecycleGuard.java` — lifecycle guard pattern

**Slice 219B — Create:**
- `backend/src/main/java/.../expense/ExpenseController.java`
- `backend/src/test/java/.../expense/ExpenseControllerTest.java`

**Slice 219B — Read for context:**
- `backend/src/main/java/.../timeentry/TimeEntryController.java` — controller structure with RequestScopes pattern
- `backend/src/test/java/.../timeentry/TimeEntryControllerTest.java` — test scaffolding pattern

### Architecture Decisions

- **Service validates project is ACTIVE**: Expenses cannot be created on COMPLETED/ARCHIVED projects. Uses direct `ProjectRepository.findById()` + status check rather than introducing a new `ProjectLifecycleGuard` — keeps the implementation simple.
- **Creator-or-ADMIN permission pattern**: Matches `TimeEntryService` update/delete permission logic. The ScopedValue `RequestScopes.ORG_ROLE` is read in the service to determine admin status.
- **OrgSettings loaded per operation**: Each service method that needs `computeBillableAmount` loads OrgSettings once. `getMyExpenses` caches per unique projectId to avoid N+1.
- **Hard delete for expenses**: Consistent with `TimeEntry` deletion. No soft delete needed.

---

## Epic 220: Expense Frontend — Project Expenses Tab

**Goal**: Add a full Expenses management UI to the project detail page, including an expense list with filtering, a log expense dialog, category badge, and a "My Expenses" section on the My Work page.

**References**: Architecture doc Sections 30.8.2, 30.5.1, 30.4.1. ADR-115.

**Dependencies**: Epic 219 (expense backend API must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **220A** | 220.1–220.7 | Project Expenses tab infrastructure: "Expenses" tab added to project detail layout, `expense-actions.ts` server actions, `expense-category-badge.tsx`, `expense-list.tsx` (filterable table), `log-expense-dialog.tsx` (create form with all fields). ~5 new/modified files. Frontend only. | **Done** (PR #451) |
| **220B** | 220.8–220.14 | Edit expense dialog, delete confirmation dialog, write-off action, "My Expenses" section on My Work page, frontend tests (~10 tests). ~3 new/modified files. Frontend only. | **Done** (PR #452) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 220.1 | Create `expense-actions.ts` server actions | 220A | | New file: `app/(app)/org/[slug]/projects/[id]/expenses/expense-actions.ts`. Server actions: `createExpense(projectId, formData)`, `updateExpense(projectId, expenseId, formData)`, `deleteExpense(projectId, expenseId)`, `writeOffExpense(projectId, expenseId)`, `restoreExpense(projectId, expenseId)`, `listExpenses(projectId, filters)`, `getMyExpenses(page)`. Each calls the backend API using `getBackendUrl()` + org auth headers pattern. Pattern: `app/(app)/org/[slug]/projects/[id]/actions.ts`. |
| 220.2 | Add "Expenses" tab to project detail layout | 220A | | Modify `app/(app)/org/[slug]/projects/[id]/layout.tsx`: add "Expenses" tab link to the project detail tabs navigation. Pattern: existing tabs (Tasks, Documents, Time, Financials, Activity). |
| 220.3 | Create `ExpenseCategoryBadge` component | 220A | | New file: `components/expenses/expense-category-badge.tsx`. Props: `category: string`. Color mapping: `FILING_FEE`=blue, `TRAVEL`=orange, `COURIER`=gray, `SOFTWARE`=purple, `SUBCONTRACTOR`=indigo, `PRINTING`=amber, `COMMUNICATION`=teal, `OTHER`=slate. Use Shadcn `Badge` with `cn()` for variant. Pattern: `components/tasks/task-priority-badge.tsx`. |
| 220.4 | Create expenses route `page.tsx` | 220A | 220.2 | New file: `app/(app)/org/[slug]/projects/[id]/expenses/page.tsx`. Server component. Fetches initial expense list via `listExpenses()`. Renders `ExpenseList` with data. Handles empty state (no expenses yet — show icon + "Log your first expense" CTA). |
| 220.5 | Create `ExpenseList` component | 220A | 220.3 | New file: `components/expenses/expense-list.tsx`. Client component. Props: `expenses: ExpenseResponse[], projectId: string, userRole: string, memberId: string`. Renders Shadcn `Table` with columns: Date, Description, Category (badge), Amount, Billable Amount, Billing Status, Member, Actions. Filters bar: category dropdown, billing status (UNBILLED/BILLED/NON_BILLABLE), date range, member picker. Sort: date desc by default. Actions per row: Edit (creator or admin), Delete (creator or admin, UNBILLED only), Write-off (admin only). |
| 220.6 | Create `LogExpenseDialog` component | 220A | 220.3 | New file: `components/expenses/log-expense-dialog.tsx`. Client component. Uses Shadcn `Dialog`, `Form`, `Input`, `Select`, `DatePicker`, `Switch`. Fields: date (required), description (required), amount (required, > 0), currency (default from org settings), category (required, dropdown with `ExpenseCategory` values), task (optional, searchable dropdown from project tasks), markup percent (optional, number input), billable toggle (default on), notes (optional textarea). Submit calls `createExpense()` server action. Show success toast on save. Pattern: `components/time-entries/log-time-dialog.tsx`. |
| 220.7 | Add receipt upload to `LogExpenseDialog` | 220A | 220.6 | Extend `components/expenses/log-expense-dialog.tsx`: add optional "Receipt" file upload section. Reuse existing document upload flow: call `/api/projects/{id}/documents/upload-init`, upload to presigned S3 URL, call `/api/documents/{id}/confirm`, then pass `receiptDocumentId` in the expense create request. Show thumbnail or filename after upload. Pattern: `components/documents/document-upload.tsx`. |
| 220.8 | Create `EditExpenseDialog` component | 220B | 220.6 | New file: `components/expenses/edit-expense-dialog.tsx`. Reuses `LogExpenseDialog` form fields, pre-populated with existing expense values. BILLED expenses: all fields disabled + read-only banner "This expense has been invoiced and cannot be edited." Submit calls `updateExpense()` server action. |
| 220.9 | Add delete confirmation and write-off action | 220B | 220.5 | Modify `components/expenses/expense-list.tsx`: row actions overflow menu. Delete: show confirmation dialog with warning "This expense will be permanently deleted." — calls `deleteExpense()`. Write-off: show confirmation "Mark this expense as non-billable (written off)?" — calls `writeOffExpense()`. Restore: show for NON_BILLABLE expenses — calls `restoreExpense()`. Use `AlertDialog` pattern. |
| 220.10 | Create "My Expenses" section on My Work page | 220B | 220.1 | Modify `app/(app)/org/[slug]/my-work/page.tsx`: add a new "My Expenses" section below time entries or as a new tab. Fetches via `getMyExpenses()` server action. Display as simple table: Date, Project, Description, Category, Amount, Billable Amount, Status. Empty state: "No expenses logged yet." Pattern: existing My Work time entries section. |
| 220.11 | Write frontend tests — expense list rendering | 220B | 220.5 | New file: `components/expenses/__tests__/expense-list.test.tsx`. Tests: (1) `renders_expense_list_with_columns`; (2) `shows_empty_state_when_no_expenses`; (3) `filters_by_category`; (4) `filters_by_billing_status_unbilled`; (5) `shows_write_off_button_for_admin_only`. Pattern: `components/time-entries/__tests__/time-entry-list.test.tsx`. |
| 220.12 | Write frontend tests — log expense dialog | 220B | 220.6 | New file: `components/expenses/__tests__/log-expense-dialog.test.tsx`. Tests: (6) `renders_all_required_fields`; (7) `validates_amount_positive`; (8) `validates_category_required`; (9) `submits_with_valid_data`; (10) `shows_receipt_upload_section`. |

### Key Files

**Slice 220A — Create:**
- `frontend/app/(app)/org/[slug]/projects/[id]/expenses/page.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/expenses/expense-actions.ts`
- `frontend/components/expenses/expense-list.tsx`
- `frontend/components/expenses/log-expense-dialog.tsx`
- `frontend/components/expenses/expense-category-badge.tsx`

**Slice 220A — Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/layout.tsx`

**Slice 220B — Create:**
- `frontend/components/expenses/edit-expense-dialog.tsx`
- `frontend/components/expenses/__tests__/expense-list.test.tsx`
- `frontend/components/expenses/__tests__/log-expense-dialog.test.tsx`

**Slice 220B — Modify:**
- `frontend/app/(app)/org/[slug]/my-work/page.tsx`
- `frontend/components/expenses/expense-list.tsx`

**Slice 220A/B — Read for context:**
- `frontend/components/time-entries/log-time-dialog.tsx` — dialog pattern with form fields
- `frontend/app/(app)/org/[slug]/projects/[id]/layout.tsx` — tab navigation pattern
- `frontend/components/tasks/task-priority-badge.tsx` — badge color mapping pattern

### Architecture Decisions

- **Expenses tab is a new route**: `projects/[id]/expenses/page.tsx` follows the pattern of other project sub-routes (tasks, documents, time). The tab link is added to the shared `[id]/layout.tsx`.
- **Receipt upload reuses existing S3 flow**: No new upload infrastructure. The presigned URL + confirm pattern from documents is reused directly.
- **BILLED expenses are read-only**: The edit dialog renders in a disabled state with a banner. Delete is hidden for BILLED expenses. This enforces the billing immutability rule in the UI.

---

## Epic 221: Expense Billing Integration — InvoiceLine Extension & Invoice Pipeline

**Goal**: Add `expenseId` and `lineType` fields to the `InvoiceLine` entity, create the `InvoiceLineType` enum, extend `InvoiceService` to include expenses in unbilled summaries, invoice generation, approve/void flows, and extend profitability queries with expense data.

**References**: Architecture doc Sections 30.2.3, 30.3.2, 30.4.2, 30.5.2, 30.6. ADR-118.

**Dependencies**: Epic 218 (V50 migration creates the invoice_lines columns — entity extension can start after 218A; full service work needs 218B for Expense entity).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **221A** | 221.1–221.5 | `InvoiceLine` entity extension: add `expenseId` and `lineType` fields + `InvoiceLineType` enum + `InvoiceLineRepository` update + entity-level validation. ~3 new/modified files. Backend only. | **Done** (PR #453) |
| **221B** | 221.6–221.14 | `InvoiceService` extension: unbilled summary with expenses, generate invoice with EXPENSE-type lines, approve stamps expense invoiceIds, void clears expense invoiceIds + profitability query extension with expense cost/revenue + integration tests (~15 tests). ~2 modified files, ~1 new test file. Backend only. | **Done** (PR #454) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 221.1 | Create `InvoiceLineType` enum | 221A | | New file: `invoice/InvoiceLineType.java`. Values: `TIME`, `EXPENSE`, `RETAINER`, `MANUAL`. Simple enum, no transition methods. Pattern: `task/TaskPriority.java`. |
| 221.2 | Extend `InvoiceLine` entity with `expenseId` and `lineType` | 221A | 221.1 | Modify `invoice/InvoiceLine.java`: add `@Column(name = "expense_id") private UUID expenseId` (nullable). Add `@Enumerated(EnumType.STRING) @Column(name = "line_type", nullable = false, length = 20) private InvoiceLineType lineType` with default `InvoiceLineType.TIME`. Add getters/setters. Update all existing constructors: set `this.lineType = InvoiceLineType.TIME` (or `RETAINER`, `MANUAL` based on context). |
| 221.3 | Update all existing InvoiceLine creation sites | 221A | 221.2 | Modify `invoice/InvoiceService.java` (or wherever lines are created): every existing `new InvoiceLine(...)` call must now set `lineType` explicitly. TIME lines: `InvoiceLineType.TIME`. RETAINER lines (if applicable): `InvoiceLineType.RETAINER`. MANUAL lines: `InvoiceLineType.MANUAL`. Prevents lineType from defaulting inconsistently. |
| 221.4 | Update `InvoiceLineRepository` for type-filtered queries | 221A | 221.2 | Modify `invoice/InvoiceLineRepository.java` (or create if using derived queries): add `List<InvoiceLine> findByInvoiceIdAndLineType(UUID invoiceId, InvoiceLineType lineType)` for PDF rendering and type-grouped display. Add `List<InvoiceLine> findByExpenseId(UUID expenseId)` for void un-billing. |
| 221.5 | Write InvoiceLine entity unit tests | 221A | 221.2 | New or modify existing test file for InvoiceLine. Tests: (1) `newTimeLine_hasLineTypeTime`; (2) `newExpenseLine_hasLineTypeExpense`; (3) `lineType_setExplicitly_overridesDefault`. ~3 unit tests. |
| 221.6 | Extend `InvoiceService.getUnbilledSummary()` with expenses | 221B | 221A, 218B | Modify `invoice/InvoiceService.java`. Method `getUnbilledSummary(UUID customerId, ...)`: load unbilled time entries (existing logic), load `OrgSettings` for default markup, query `ExpenseRepository` for all `billable=true AND invoiceId IS NULL` expenses across the customer's projects (native query joining through `project_members` or `customer_projects`). Build `UnbilledExpenseSummary` DTO. Return combined `UnbilledSummaryResponse` with `unbilledExpenses`, `unbilledExpenseTotal`, updated `grandTotal`. |
| 221.7 | Extend invoice generation with EXPENSE-type lines | 221B | 221.6 | Modify `invoice/InvoiceService.java`. Method `generateInvoice(GenerateInvoiceRequest request)` where `request` now includes optional `expenseIds: List<UUID>`. For each selected expense: load expense, compute `billableAmount` using org default markup, create `new InvoiceLine(...)` with `lineType=EXPENSE`, `expenseId=expense.id`, `quantity=1`, `unitPrice=billableAmount`, `amount=billableAmount`, `description=expense.description + " [" + expense.category + "]"`. Add to invoice. |
| 221.8 | Extend invoice approve to stamp expense invoiceIds | 221B | 221.7 | Modify `invoice/InvoiceService.java`. Method `approveInvoice(UUID invoiceId)`: existing logic stamps `invoiceId` on time entries. Extend: find all `InvoiceLine` entries where `lineType=EXPENSE` and `expenseId IS NOT NULL`. For each, load the `Expense` and set `expense.invoiceId = invoiceId`. Save all. This transitions them to BILLED status. |
| 221.9 | Extend invoice void to clear expense invoiceIds | 221B | 221.7 | Modify `invoice/InvoiceService.java`. Method `voidInvoice(UUID invoiceId)`: existing logic clears `invoiceId` on time entries. Extend: find all expense-type lines via `invoiceLineRepository.findByInvoiceIdAndLineType(invoiceId, EXPENSE)`. For each, load `Expense` and set `expense.invoiceId = null`. Save. Reverts to UNBILLED. |
| 221.10 | Extend profitability queries with expense data | 221B | 218B | Modify `report/ProfitabilityReportService.java`. Extend `getProjectProfitability(UUID projectId)`: add `totalExpenseCost = SUM(expenses.amount)`, `totalExpenseRevenue = SUM(billed expenses: amount * (1 + COALESCE(markup_percent, 0) / 100))`. Update margin formula: `margin = (totalRevenue - totalCost) / totalRevenue * 100` where `totalCost = totalTimeCost + totalExpenseCost` and `totalRevenue = totalTimeRevenue + totalExpenseRevenue`. Add expense breakdown fields to profitability response DTO. Use SQL aggregation query from architecture doc Section 30.6. |
| 221.11 | Write unbilled summary extension tests | 221B | 221.6 | New file: `invoice/ExpenseBillingIntegrationTest.java`. Tests (1–6): (1) `unbilledSummary_includesExpenses`; (2) `unbilledSummary_excludesBilledExpenses`; (3) `unbilledSummary_excludesNonBillableExpenses`; (4) `generateInvoice_withExpenses_createsExpenseLines`; (5) `approveInvoice_stampsInvoiceIdOnExpenses`; (6) `voidInvoice_clearsInvoiceIdFromExpenses`. |
| 221.12 | Write expense billing workflow tests | 221B | 221.11 | Continuing `invoice/ExpenseBillingIntegrationTest.java`. Tests (7–15): (7) `generateInvoice_expenseLineType_isExpense`; (8) `generateInvoice_expenseBillableAmount_includesMarkup`; (9) `generateInvoice_mixedTimeAndExpenseLines`; (10) `approveInvoice_expenseBillingStatus_becomesBilled`; (11) `voidInvoice_expenseBillingStatus_revertsToUnbilled`; (12) `profitability_includesExpenseCost`; (13) `profitability_includesExpenseRevenue_forBilledExpenses`; (14) `profitability_absorbs_nonBillableExpenses_asPureCost`; (15) `profitability_markupContributesToMargin`. Total: ~15 integration tests. |

### Key Files

**Slice 221A — Modify:**
- `backend/src/main/java/.../invoice/InvoiceLine.java`
- `backend/src/main/java/.../invoice/InvoiceLineRepository.java` (or InvoiceRepository)

**Slice 221A — Create:**
- `backend/src/main/java/.../invoice/InvoiceLineType.java`

**Slice 221B — Modify:**
- `backend/src/main/java/.../invoice/InvoiceService.java`
- `backend/src/main/java/.../report/ProfitabilityReportService.java`

**Slice 221B — Create:**
- `backend/src/test/java/.../invoice/ExpenseBillingIntegrationTest.java`

**Slice 221A/B — Read for context:**
- `backend/src/main/java/.../invoice/InvoiceLine.java` — existing entity structure
- `backend/src/main/java/.../invoice/InvoiceService.java` — existing approve/void flow
- `backend/src/main/java/.../report/ProfitabilityReportService.java` — existing profitability query structure

### Architecture Decisions

- **`lineType` defaults to TIME for backward compatibility**: All existing invoice lines default to `InvoiceLineType.TIME` via the `DEFAULT 'TIME'` column constraint and the migration backfill. Existing creation code is updated to set `lineType` explicitly to prevent drift.
- **Expense billing status is authoritative via FK**: Invoice void clears `expense.invoiceId`, making the status derivation (`invoiceId != null → BILLED`) automatically correct — no explicit status field to synchronize.
- **Profitability uses COALESCE for org-default markup**: The SQL query for expense revenue uses `COALESCE(markup_percent, 0)` since the org default is not accessible in a pure SQL query against the expenses table. The org default is applied in-memory for response DTOs but the SQL-level profitability aggregation uses the per-expense markup only. This is acceptable for the aggregation; the slight discrepancy from org default is documented.

---

## Epic 222: Expense Billing Frontend — Unbilled Summary & Invoice Generation

**Goal**: Extend the existing invoice generation UI to show and allow selection of unbilled expenses alongside time entries, and update the unbilled summary display.

**References**: Architecture doc Sections 30.4.2, 30.5.2, 30.8.2.

**Dependencies**: Epic 221 (backend billing integration must exist), Epic 220 (expense list patterns to follow).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **222A** | 222.1–222.8 | Extend `unbilled-summary.tsx` with expense section, extend `generate-invoice-dialog.tsx` with expense selection checkboxes, update invoice detail to show expense lines, add expense totals row + frontend tests (~7 tests). ~2 modified files, ~1 test file. Frontend only. | **Done** (PR #455) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 222.1 | Update `getUnbilledSummary` action to include expenses | 222A | 221B | Modify `app/(app)/org/[slug]/invoices/invoice-actions.ts` (or wherever the unbilled summary action lives). Update the fetch call to `GET /api/customers/{id}/unbilled-summary` — the backend now returns `unbilledExpenses` and `unbilledExpenseTotal`. Update TypeScript types: `UnbilledSummaryResponse` gains `unbilledExpenses: UnbilledExpenseDto[]`, `unbilledExpenseTotal`, `grandTotal`. |
| 222.2 | Extend `unbilled-summary.tsx` with expense section | 222A | 222.1 | Modify `components/invoices/unbilled-summary.tsx`: add an "Expenses" section below "Time Entries". Shows `unbilledExpenses` in a sub-table with columns: Date, Project, Description, Category (badge), Amount, Markup %, Billable Amount. Show `unbilledExpenseTotal` subtotal row. Update `grandTotal` display to sum time + expense totals. Pattern: existing time entries section in same component. |
| 222.3 | Extend `generate-invoice-dialog.tsx` with expense selection | 222A | 222.1 | Modify `components/invoices/generate-invoice-dialog.tsx`: add "Include Expenses" section with checkboxes for each unbilled expense (similar to time entry checkboxes). Group by project. Show: date, description, category, billable amount. "Select All Expenses" / "Deselect All" controls. Collect `selectedExpenseIds: string[]` in dialog state. Pass to generate invoice request body. |
| 222.4 | Update invoice generate action to send expenseIds | 222A | 222.3 | Modify invoice generate server action: include `expenseIds: selectedExpenseIds` in `POST /api/invoices/generate` body. |
| 222.5 | Show expense lines in invoice detail view | 222A | | Modify `components/invoices/invoice-line-list.tsx` (or equivalent). Group lines by `lineType`: "Time Entries" section, then "Expenses" section, then "Other" for MANUAL/RETAINER. Show expense category badge on expense lines. Pattern: existing invoice detail line rendering. |
| 222.6 | Write frontend tests — unbilled summary with expenses | 222A | 222.2 | New file: `components/invoices/__tests__/unbilled-summary-expenses.test.tsx`. Tests: (1) `renders_expense_section_when_expenses_present`; (2) `shows_expense_total_separately`; (3) `shows_grand_total_combining_time_and_expenses`; (4) `shows_empty_state_when_no_expenses`. |
| 222.7 | Write frontend tests — invoice generation with expenses | 222A | 222.3 | Continuing test file or new file. Tests: (5) `renders_expense_checkboxes_in_generate_dialog`; (6) `selects_all_expenses_via_select_all`; (7) `passes_selected_expense_ids_in_generate_request`. Total: ~7 frontend tests. |

### Key Files

**Slice 222A — Modify:**
- `frontend/components/invoices/unbilled-summary.tsx`
- `frontend/components/invoices/generate-invoice-dialog.tsx`
- `frontend/app/(app)/org/[slug]/invoices/invoice-actions.ts`

**Slice 222A — Create:**
- `frontend/components/invoices/__tests__/unbilled-summary-expenses.test.tsx`

**Slice 222A — Read for context:**
- `frontend/components/invoices/generate-invoice-dialog.tsx` — existing dialog structure
- `frontend/components/invoices/unbilled-summary.tsx` — existing unbilled time section pattern
- `frontend/components/expenses/expense-category-badge.tsx` — badge component to reuse

### Architecture Decisions

- **Expense selection follows the same pattern as time entry selection**: Checkboxes per expense, "Select All", grouped by project. The same generate dialog is extended rather than creating a separate expense dialog.
- **Invoice detail groups by lineType**: TIME lines appear first, EXPENSE lines in their own section. This makes the invoice summary readable without mixing expense and time line items.

---

## Epic 223: Recurring Task Foundation — Migration & Entity

**Goal**: Add recurrence columns to the `tasks` table (via V50, already contributed by Epic 218), extend the `Task` entity with three new nullable fields, and build the `RecurrenceRule` value object that parses RRULE strings and computes next due dates.

**References**: Architecture doc Sections 30.2.2, 30.3.3, 30.4.3. ADR-116.

**Dependencies**: None independently (V50 migration is created in 218A — if running in parallel, 223A contributes its section or verifies 218A created it).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **223A** | 223.1–223.2 | V50 migration section for task recurrence columns (if not already written by 218A) — 3 ALTER TABLE + 1 index. ~1 migration file (shared with Epic 218 or verified). Backend only. | |
| **223B** | 223.3–223.9 | Task entity extension (3 new nullable fields, getters/setters, update() extension) + `RecurrenceRule` value object (RRULE parser, next-date calculator for DAILY/WEEKLY/MONTHLY/YEARLY) + unit tests for RecurrenceRule. ~3 new/modified files. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 223.1 | Verify/contribute task recurrence section in V50 migration | 223A | | If 218A already created V50 with task recurrence columns — verify the three `ALTER TABLE tasks ADD COLUMN IF NOT EXISTS` statements are present: `recurrence_rule VARCHAR(100)`, `recurrence_end_date DATE`, `parent_task_id UUID`. And `CREATE INDEX IF NOT EXISTS idx_tasks_parent_task_id ON tasks(parent_task_id) WHERE parent_task_id IS NOT NULL`. If 218A did NOT create these (parallel execution), add them to `V50__expenses_recurring_calendar.sql` or create `V50b__task_recurrence.sql` as a fallback. Note: `IF NOT EXISTS` guards prevent conflicts. |
| 223.2 | Verify backward compatibility | 223A | 223.1 | Existing tasks are unaffected: all three columns default to NULL. No data backfill needed. Validated by integration test bootstrap. |
| 223.3 | Extend `Task` entity with recurrence fields | 223B | 223.1 | Modify `task/Task.java`: add `@Column(name = "recurrence_rule", length = 100) private String recurrenceRule`. Add `@Column(name = "recurrence_end_date") private LocalDate recurrenceEndDate`. Add `@Column(name = "parent_task_id") private UUID parentTaskId`. Add getters and setters for all three. Update `update()` method to accept and set `recurrenceRule`, `recurrenceEndDate` (clearing `parentTaskId` is not allowed via update — it is set by the system on auto-creation). Add `isRecurring()` convenience method: `return recurrenceRule != null`. |
| 223.4 | Create `RecurrenceRule` value object | 223B | | New file: `task/RecurrenceRule.java`. Java record or class with fields: `String frequency` (DAILY/WEEKLY/MONTHLY/YEARLY), `int interval` (default 1). Static factory: `RecurrenceRule parse(String rruleString)`. Parses `FREQ=MONTHLY;INTERVAL=1` format. Validates that `frequency` is one of the four supported values. Throws `IllegalArgumentException` for unrecognized formats. |
| 223.5 | Implement `calculateNextDueDate()` in RecurrenceRule | 223B | 223.4 | In `task/RecurrenceRule.java`: method `LocalDate calculateNextDueDate(LocalDate currentDueDate)`. Base date = `currentDueDate` (or `LocalDate.now()` if `currentDueDate == null`). DAILY: `base.plusDays(interval)`. WEEKLY: `base.plusWeeks(interval)` (same day of week, `plusWeeks` handles this). MONTHLY: `base.plusMonths(interval)` (Java's `plusMonths` clamps day-of-month automatically, e.g., Jan 31 + 1 month = Feb 28). YEARLY: `base.plusYears(interval)` (Feb 29 handling: `plusYears` converts to Feb 28 on non-leap years). |
| 223.6 | Add lineage helper to Task entity | 223B | 223.3 | In `task/Task.java`: add method `UUID getRootTaskId()` returning `parentTaskId != null ? parentTaskId : id`. This is used by `TaskService` when creating the next recurring instance: `newTask.parentTaskId = completedTask.getRootTaskId()` — ensuring all instances point to the original root, not the immediately preceding task. |
| 223.7 | Write `RecurrenceRule` unit tests | 223B | 223.4, 223.5 | New file: `task/RecurrenceRuleTest.java`. Tests: (1) `parse_monthly_interval1`; (2) `parse_weekly_interval2`; (3) `parse_daily_noInterval_defaultsTo1`; (4) `parse_yearly_interval1`; (5) `parse_invalidFrequency_throws`; (6) `nextDueDate_daily_addsDays`; (7) `nextDueDate_weekly_addsWeeks`; (8) `nextDueDate_monthly_sameDay`; (9) `nextDueDate_monthly_clampsDayOfMonth_jan31_to_feb28`; (10) `nextDueDate_yearly_handlesFeb29`; (11) `nextDueDate_nullBaseDate_usesToday`; (12) `parse_freqOnly_defaultsIntervalTo1`. ~12 unit tests. |
| 223.8 | Write Task entity recurrence field tests | 223B | 223.3, 223.6 | New or extend existing `task/TaskTest.java`. Tests: (1) `newTask_recurrenceFieldsNullByDefault`; (2) `isRecurring_returnsTrueWhenRecurrenceRuleSet`; (3) `isRecurring_returnsFalseForNullRule`; (4) `getRootTaskId_returnsOwnIdWhenNoParent`; (5) `getRootTaskId_returnsParentIdWhenParentSet`. ~5 tests. |

### Key Files

**Slice 223A — Verify/Modify:**
- `backend/src/main/resources/db/migration/tenant/V50__expenses_recurring_calendar.sql` (verify or append)

**Slice 223B — Modify:**
- `backend/src/main/java/.../task/Task.java`

**Slice 223B — Create:**
- `backend/src/main/java/.../task/RecurrenceRule.java`
- `backend/src/test/java/.../task/RecurrenceRuleTest.java`

**Slice 223B — Read for context:**
- `backend/src/main/java/.../task/Task.java` — existing entity structure
- `backend/src/main/java/.../invoice/InvoiceLineType.java` — simple enum pattern (after 221A)

### Architecture Decisions

- **`RecurrenceRule` is a pure value object**: No Spring annotations, no database persistence. Instantiated by `TaskService` when needed. Easy to unit test without Spring context.
- **Flat lineage via `getRootTaskId()`**: If a task with `parentTaskId = ROOT_ID` completes and spawns a new instance, the new instance also gets `parentTaskId = ROOT_ID` (not its immediate predecessor). This keeps the lineage query trivially simple.
- **`plusMonths()` and `plusYears()` for date arithmetic**: Java's built-in methods handle month-length clamping and leap-year edge cases correctly without custom logic.

---

## Epic 224: Recurring Task Service & Controller

**Goal**: Extend `TaskService.completeTask()` to auto-create the next recurring instance in the same transaction, extend the task DTOs and controller to expose recurrence fields and the `nextInstance` in the complete response.

**References**: Architecture doc Sections 30.3.3, 30.4.3, 30.5.3. ADR-116.

**Dependencies**: Epic 223 (entity + RecurrenceRule value object).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **224A** | 224.1–224.7 | Extend `TaskService.completeTask()`: recurrence check after DONE transition, RRULE parse, next-date calculation, `recurrenceEndDate` check, new Task creation in same transaction, `task.recurrence_created` audit event, assignee notification. ~1 modified service file, ~1 new event file. Backend only. | |
| **224B** | 224.8–224.13 | `TaskController` extensions: complete endpoint returns `CompleteTaskResponse { completedTask, nextInstance }`, create/update request DTOs gain `recurrenceRule`, `recurrenceEndDate` fields, `GET /tasks` gains `?recurring=true` filter, integration tests (~15 tests). ~1 modified controller file, ~1 new test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 224.1 | Create `TaskRecurrenceCreatedEvent` domain event | 224A | | New file: `event/TaskRecurrenceCreatedEvent.java`. Record: `UUID originalTaskId, UUID newTaskId, UUID projectId, UUID assigneeId, String taskTitle, LocalDate newDueDate, String tenantId, String orgId, Instant occurredAt`. Pattern: `event/TaskCompletedEvent.java`. |
| 224.2 | Extend `TaskService.completeTask()` with recurrence auto-creation | 224A | 223B | Modify `task/TaskService.java`. After the standard `task.complete(memberId)` + save + audit, add: `if (task.isRecurring()) { handleRecurrence(task, memberId); }`. The `handleRecurrence` private method: (1) `RecurrenceRule rule = RecurrenceRule.parse(task.getRecurrenceRule())`; (2) `LocalDate nextDue = rule.calculateNextDueDate(task.getDueDate())`; (3) if `task.getRecurrenceEndDate() != null && nextDue.isAfter(task.getRecurrenceEndDate())` → return null (no new instance, recurrence expired); (4) otherwise create new Task — see task 224.3. |
| 224.3 | Implement new Task creation for recurrence | 224A | 224.2 | In `task/TaskService.java` `handleRecurrence()` method: create `new Task(task.getProjectId(), task.getTitle(), task.getDescription(), task.getPriority(), memberId)` (createdBy = completer). Set `newTask.setAssigneeId(task.getAssigneeId())`. Set `newTask.setDueDate(nextDue)`. Set `newTask.setRecurrenceRule(task.getRecurrenceRule())`. Set `newTask.setRecurrenceEndDate(task.getRecurrenceEndDate())`. Set `newTask.setParentTaskId(task.getRootTaskId())`. Save via `taskRepository.save(newTask)`. Audit `task.recurrence_created`. Publish `TaskRecurrenceCreatedEvent`. Return `newTask`. |
| 224.4 | Wire assignee notification for recurrence | 224A | 224.3 | Modify `notification/NotificationEventHandler.java`: add handler for `TaskRecurrenceCreatedEvent`. If `event.assigneeId() != null`: create in-app notification "Your recurring task '{taskTitle}' has a new instance due {newDueDate}." Pattern: existing `TaskAssignedEvent` handler. |
| 224.5 | Extend `completeTask()` return type | 224A | 224.2 | In `task/TaskService.java`: change `completeTask()` return type from `Task` to a new `CompleteTaskResult` record: `record CompleteTaskResult(Task completedTask, Task nextInstance)` where `nextInstance` is null if not recurring or recurrence expired. This is consumed by the controller to build the API response. |
| 224.6 | Handle CANCEL behavior for recurring tasks | 224A | 223B | In `task/TaskService.java` `cancelTask()` method: when a recurring task is cancelled, no new instance is created (by design per ADR-116). Add a comment: `// Cancelling a recurring task stops recurrence. User must reopen and complete to resume.` No additional code needed — the existing cancel logic is correct. |
| 224.7 | Validate recurrenceRule format on task create/update | 224A | 223B | In `task/TaskService.java` `createTask()` and `updateTask()` methods: if `recurrenceRule` is provided in the request, call `RecurrenceRule.parse(recurrenceRule)` — it throws `IllegalArgumentException` for invalid format. Catch and convert to `BadRequestException` or return 400 response. |
| 224.8 | Extend task create/update request DTOs with recurrence fields | 224B | 224A | Modify `task/TaskController.java`: add to `CreateTaskRequest` record: `String recurrenceRule` (nullable), `LocalDate recurrenceEndDate` (nullable). Add same to `UpdateTaskRequest`. Pass through to `TaskService.createTask()` and `updateTask()`. Update service signatures accordingly. |
| 224.9 | Extend `TaskResponse` DTO with recurrence fields | 224B | 223B | Modify `task/TaskController.java`: add to `TaskResponse` record: `String recurrenceRule`, `LocalDate recurrenceEndDate`, `UUID parentTaskId`, `boolean isRecurring`. Map from entity: `isRecurring = task.isRecurring()`. |
| 224.10 | Create `CompleteTaskResponse` DTO and update complete endpoint | 224B | 224.5 | Modify `task/TaskController.java` `completeTask` endpoint: change return type to `CompleteTaskResponse` record with `completedTask: TaskResponse` and `nextInstance: TaskResponse` (nullable). Map from `CompleteTaskResult`. |
| 224.11 | Add `?recurring=true` filter to listTasks | 224B | 223B | Modify `task/TaskController.java` and `task/TaskService.java`: add optional `Boolean recurring` query parameter to `GET /api/projects/{projectId}/tasks`. When `recurring=true`, add `AND t.recurrenceRule IS NOT NULL` filter. Update `TaskRepository.findByProjectIdWithFilters` to include `recurring` parameter. |
| 224.12 | Write recurring task integration tests — auto-creation | 224B | 224.10 | New file: `task/RecurringTaskIntegrationTest.java`. Tests (1–8): (1) `completeRecurringTask_createsNextInstance`; (2) `nextInstance_hasCorrectDueDate_forMonthly`; (3) `nextInstance_hasCorrectDueDate_forWeekly`; (4) `nextInstance_hasParentTaskId_setToRoot`; (5) `nextInstance_inheritsPriority_andAssignee`; (6) `recurrenceExpired_noNextInstance`; (7) `cancelRecurringTask_doesNotCreateNext`; (8) `completeNonRecurringTask_returnsNullNextInstance`. |
| 224.13 | Write recurring task integration tests — CRUD and filters | 224B | 224.12 | Continuing `task/RecurringTaskIntegrationTest.java`. Tests (9–15): (9) `createTask_withRecurrenceRule_succeeds`; (10) `createTask_withInvalidRrule_returns400`; (11) `updateTask_setsRecurrenceRule`; (12) `updateTask_clearsRecurrenceRule`; (13) `listTasks_filterRecurringOnly_returnsOnlyRecurring`; (14) `listTasks_noFilter_includesRecurring`; (15) `completeResponse_includesNextInstanceDetails`. Total: ~15 integration tests. |

### Key Files

**Slice 224A — Modify:**
- `backend/src/main/java/.../task/TaskService.java`
- `backend/src/main/java/.../notification/NotificationEventHandler.java`

**Slice 224A — Create:**
- `backend/src/main/java/.../event/TaskRecurrenceCreatedEvent.java`

**Slice 224B — Modify:**
- `backend/src/main/java/.../task/TaskController.java`
- `backend/src/main/java/.../task/TaskRepository.java`

**Slice 224B — Create:**
- `backend/src/test/java/.../task/RecurringTaskIntegrationTest.java`

**Slice 224B — Read for context:**
- `backend/src/main/java/.../task/TaskService.java` — existing completeTask() structure
- `backend/src/main/java/.../task/TaskController.java` — existing complete endpoint and DTO pattern
- `backend/src/main/java/.../notification/NotificationEventHandler.java` — notification handler pattern

### Architecture Decisions

- **Synchronous auto-creation in same transaction**: No scheduler, no async. The user sees the next instance immediately. If the transaction fails, no orphan task is created. See ADR-116.
- **`CompleteTaskResult` record wraps both tasks**: The service returns a structured result rather than two separate return values. The controller maps this to the API response. This keeps the controller thin.
- **CANCEL stops recurrence silently**: No notification, no error. This is intentional — the user can reopen and complete to resume. Documented with a comment in the code.

---

## Epic 225: Recurring Task Frontend

**Goal**: Add frontend UI for configuring recurrence on tasks, displaying recurring badges in lists, showing recurrence information in task detail sheets, and showing a completion toast with the next instance due date.

**References**: Architecture doc Sections 30.8.2, 30.4.3. ADR-116.

**Dependencies**: Epic 224 (backend API must expose recurrence fields and `nextInstance` in complete response).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **225A** | 225.1–225.8 | Recurrence section in task create/edit dialogs (frequency, interval, end date), recurring badge in task list, recurrence info panel in task detail sheet, completion toast with next instance info, ?recurring=true filter chip, frontend tests (~5 tests). ~4 modified files, ~1 new component file, ~1 test file. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 225.1 | Add recurrence section to task creation dialog | 225A | | Modify `components/tasks/task-form.tsx` (or wherever task create dialog lives). Add collapsible "Recurrence" section at the bottom of the form. Controls: (1) Frequency dropdown: None (no recurrence), Daily, Weekly, Monthly, Yearly — Shadcn `Select`. (2) Interval input: number field showing "Every N [weeks/months/etc]" — hidden when Frequency = None. (3) End date picker: optional, shows "End after" date — Shadcn `DatePicker`. When Frequency = None, `recurrenceRule` is null in request. When set, format as `FREQ=MONTHLY;INTERVAL=N`. Pass `recurrenceRule`, `recurrenceEndDate` in create task server action call. |
| 225.2 | Add recurrence section to task edit dialog | 225A | 225.1 | Modify task edit form (same `task-form.tsx` or `task-detail-sheet.tsx`): pre-populate recurrence fields from existing task data. Parse existing `recurrenceRule` string back into frequency + interval for display. Allow changing or clearing recurrence (set to None removes the rule). |
| 225.3 | Add recurring badge to task list | 225A | | Modify `components/tasks/task-list.tsx` (or task row component): if `task.isRecurring == true`, show a small "↻" icon or "Recurring" badge in the task title area. Use a `Tooltip` showing the recurrence description (e.g., "Repeats monthly"). Pattern: `components/tasks/task-priority-badge.tsx`. |
| 225.4 | Add "Recurring" filter chip to task list | 225A | | Modify task list filters (e.g., `components/tasks/task-filters.tsx`): add a "Recurring" toggle chip. When active, appends `&recurring=true` to the task list API call (or server action URL). Pattern: existing status filter chips. |
| 225.5 | Show recurrence info in task detail sheet | 225A | | Modify `components/tasks/task-detail-sheet.tsx` (or task detail page): add a "Recurrence" info section (shown only if `task.isRecurring`). Display: human-readable description ("Repeats every month"), end date if set, link to parent task if `parentTaskId` is set ("Generated from: [Parent Task Title]"). |
| 225.6 | Show completion toast for recurring tasks | 225A | | Modify the task completion flow handler (wherever `completeTask` action is called and the response is handled). If `response.nextInstance != null`, show a toast notification: "Task completed! Next instance due [nextInstance.dueDate]." If `nextInstance == null` and task was recurring, show: "Task completed. Recurrence has ended." Use `useToast` or Sonner `toast()` pattern. Pattern: other success toast notifications in the app. |
| 225.7 | Update task server actions for recurrence fields | 225A | | Modify `app/(app)/org/[slug]/projects/[id]/tasks/task-actions.ts` (or equivalent): update `createTask` and `updateTask` actions to include `recurrenceRule` and `recurrenceEndDate` in the request body. Update `completeTask` action to return `CompleteTaskResponse` shape with `nextInstance`. Update TypeScript types for `TaskResponse` to include `recurrenceRule`, `recurrenceEndDate`, `parentTaskId`, `isRecurring`. |
| 225.8 | Write frontend recurrence tests | 225A | 225.1, 225.3, 225.6 | New file: `components/tasks/__tests__/task-recurrence.test.tsx`. Tests: (1) `shows_recurring_badge_for_recurring_tasks`; (2) `hides_recurring_badge_for_non_recurring_tasks`; (3) `recurrence_form_renders_frequency_dropdown`; (4) `recurrence_form_hides_interval_when_none_selected`; (5) `completion_toast_shows_next_due_date`. ~5 tests. |

### Key Files

**Slice 225A — Modify:**
- `frontend/components/tasks/task-form.tsx`
- `frontend/components/tasks/task-list.tsx`
- `frontend/components/tasks/task-detail-sheet.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/tasks/task-actions.ts`

**Slice 225A — Create:**
- `frontend/components/tasks/__tests__/task-recurrence.test.tsx`

**Slice 225A — Read for context:**
- `frontend/components/tasks/task-detail-sheet.tsx` — existing detail sheet structure
- `frontend/components/tasks/task-form.tsx` — existing form field patterns
- `frontend/components/tasks/task-priority-badge.tsx` — badge pattern

### Architecture Decisions

- **Human-readable recurrence description in UI**: The frontend derives a human-readable string from the `recurrenceRule` field (e.g., `FREQ=MONTHLY;INTERVAL=1` → "Repeats every month"). This parsing happens client-side to avoid adding a derived field to the API response.
- **Completion toast uses `nextInstance` from response**: The frontend does not recalculate the next due date — it reads it from the `CompleteTaskResponse.nextInstance.dueDate`. This avoids duplication of the RRULE parsing logic.

---

## Epic 226: Time Reminder Scheduler & OrgSettings

**Goal**: Extend `OrgSettings` with four time reminder configuration columns, build the `TimeReminderScheduler` component with `@Scheduled` polling every 15 minutes, implement per-org processing (working day check, member time query, notification creation), and support individual opt-out via `NotificationPreference`.

**References**: Architecture doc Sections 30.2.4, 30.3.4, 30.4.5. ADR-117.

**Dependencies**: None (this is an independent track). Builds on Phase 6.5 notification infrastructure.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **226A** | 226.1–226.5 | V50 migration section for OrgSettings time reminder columns + extend `OrgSettings` entity with 4 new fields + extend `OrgSettingsController` DTO + update the OrgSettings update/create path + unit tests for OrgSettings. ~2 modified files, ~1 migration section. Backend only. | |
| **226B** | 226.6–226.13 | `TimeReminderScheduler` component: `@Scheduled(fixedRate=900000)` method, tenant iteration, per-org processing (working day check, minimum time check, notification creation), `NotificationPreference` opt-out support + integration tests (~8 tests). ~1 new scheduler file, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 226.1 | Verify OrgSettings columns in V50 migration | 226A | | Verify (or contribute) the org_settings ALTER statements in `V50__expenses_recurring_calendar.sql`: `time_reminder_enabled BOOLEAN NOT NULL DEFAULT false`, `time_reminder_days VARCHAR(50) DEFAULT 'MON,TUE,WED,THU,FRI'`, `time_reminder_time TIME DEFAULT '17:00'`, `time_reminder_min_minutes INTEGER DEFAULT 240`, `default_expense_markup_percent NUMERIC(5,2)`. If 218A already wrote these, verify. Otherwise append to V50 or create a sub-file. |
| 226.2 | Extend `OrgSettings` entity with time reminder fields | 226A | 226.1 | Modify `settings/OrgSettings.java`: add `@Column(name = "time_reminder_enabled") private boolean timeReminderEnabled = false`. Add `@Column(name = "time_reminder_days", length = 50) private String timeReminderDays = "MON,TUE,WED,THU,FRI"`. Add `@Column(name = "time_reminder_time") private LocalTime timeReminderTime`. Add `@Column(name = "time_reminder_min_minutes") private int timeReminderMinMinutes = 240`. Add `@Column(name = "default_expense_markup_percent", precision = 5, scale = 2) private BigDecimal defaultExpenseMarkupPercent`. Add getters/setters for all. Add convenience method `getTimeReminderMinHours()` returning `timeReminderMinMinutes / 60.0`. |
| 226.3 | Extend OrgSettings DTO and controller | 226A | 226.2 | Modify `settings/OrgSettingsController.java`: add new fields to `OrgSettingsResponse` record: `boolean timeReminderEnabled`, `String timeReminderDays`, `LocalTime timeReminderTime`, `double timeReminderMinHours` (= `minMinutes/60.0`), `BigDecimal defaultExpenseMarkupPercent`. Add to `UpdateOrgSettingsRequest` record: same fields (all optional/nullable). Map in update logic. Pattern: existing OrgSettings field handling. |
| 226.4 | Add time reminder working day parse helper | 226A | 226.2 | In `settings/OrgSettings.java` (or a utility class): add `Set<DayOfWeek> getWorkingDays()` that parses `timeReminderDays` CSV string ("MON,TUE,WED,THU,FRI") into a `Set<DayOfWeek>` using `DayOfWeek.valueOf()` mapping (MON → MONDAY, TUE → TUESDAY, etc.). Used by the scheduler. |
| 226.5 | Write OrgSettings unit tests for new fields | 226A | 226.4 | Extend existing `settings/OrgSettingsTest.java` (or create). Tests: (1) `defaultTimeReminderEnabled_isFalse`; (2) `getWorkingDays_parsesCorrectly`; (3) `getWorkingDays_emptyString_returnsEmptySet`; (4) `getTimeReminderMinHours_returns4ForDefault240Minutes`. ~4 unit tests. |
| 226.6 | Create `TimeReminderScheduler` component | 226B | 226A | New file: `schedule/TimeReminderScheduler.java`. Annotated `@Component`. Constructor-injected: `OrgSettingsRepository` (or service), `MemberRepository`, `TimeEntryRepository`, `NotificationService`, `NotificationPreferenceRepository`, a list of all tenant schema names (via `TenantProvisioningService` or `OrgSchemaMappingRepository`). Method: `@Scheduled(fixedRate = 900000) public void checkTimeReminders()`. Note: `fixedRate = 15 * 60 * 1000` (15 minutes). Use `@EnableScheduling` in `BackendApplication` or a `@Configuration` class if not already present. |
| 226.7 | Implement tenant iteration in scheduler | 226B | 226.6 | In `schedule/TimeReminderScheduler.java`. `checkTimeReminders()` body: (1) get all active org schemas from the global mapping table — use `OrgSchemaMappingRepository.findAll()` (public schema query, not tenant-scoped). (2) For each schema, determine if it falls in the current 15-minute window: get `OrgSettings.timeReminderTime` and check if current UTC time is within the 15-minute window `[timeReminderTime, timeReminderTime + 15min)`. (3) If yes and `timeReminderEnabled == true`, run `processOrgReminders(schema, orgSettings)`. (4) Use `ScopedValue.where(RequestScopes.TENANT_ID, schema).run(() -> ...)` to switch schema context. Pattern: existing `ProjectScheduleService` or `SchedulerConfig` if present. |
| 226.8 | Implement per-org reminder processing | 226B | 226.7 | In `schedule/TimeReminderScheduler.java` private method `processOrgReminders(String schema, OrgSettings orgSettings)`. Steps: (1) Check if today's `DayOfWeek` is in `orgSettings.getWorkingDays()`. If not, skip. (2) Get today's date as `LocalDate.now()`. (3) Load all active members via `MemberRepository.findAllByOrgId(...)` or equivalent tenant-scoped query. (4) For each member, call `checkMemberTimeLogged(memberId, today, orgSettings)`. |
| 226.9 | Implement per-member time check and notification | 226B | 226.8 | In `schedule/TimeReminderScheduler.java` private method `checkMemberTimeLogged(UUID memberId, LocalDate today, OrgSettings orgSettings)`. Steps: (1) Check opt-out: `NotificationPreferenceRepository.findByMemberIdAndType(memberId, "TIME_REMINDER")` — if found and `enabled == false`, skip. (2) Query total minutes: `timeEntryRepository.sumDurationMinutesByMemberIdAndDate(memberId, today)` (JPQL: `SELECT COALESCE(SUM(te.durationMinutes),0) FROM TimeEntry te WHERE te.memberId=:memberId AND te.date=:date`). (3) If `totalMinutes < orgSettings.getTimeReminderMinMinutes()`: create notification via `notificationService.createInAppNotification(memberId, "TIME_REMINDER", "You have logged " + (totalMinutes/60.0) + " of " + orgSettings.getTimeReminderMinHours() + " hours today. Don't forget to log your time!")`. |
| 226.10 | Add `TIME_REMINDER` to NotificationPreference | 226B | | Verify that the existing `NotificationPreference` infrastructure supports a `TIME_REMINDER` preference type. If `NotificationType` or similar enum is used, add `TIME_REMINDER` value. Update the `NotificationPreference` creation endpoint or the seed/default logic to include `TIME_REMINDER` as an opt-in/opt-out option. Pattern: existing preference types (e.g., `TASK_ASSIGNED`, `INVOICE_SENT`). |
| 226.11 | Add `sumDurationMinutesByMemberIdAndDate` to TimeEntryRepository | 226B | | Modify `timeentry/TimeEntryRepository.java`: add `@Query("SELECT COALESCE(SUM(te.durationMinutes), 0) FROM TimeEntry te WHERE te.memberId = :memberId AND te.date = :date") int sumDurationMinutesByMemberIdAndDate(@Param("memberId") UUID memberId, @Param("date") LocalDate date)`. |
| 226.12 | Write TimeReminderScheduler unit/integration tests | 226B | 226.9 | New file: `schedule/TimeReminderSchedulerTest.java`. Tests: (1) `reminderNotSent_whenOrgDisabled`; (2) `reminderNotSent_whenNotWorkingDay`; (3) `reminderSent_whenMemberBelowThreshold`; (4) `reminderNotSent_whenMemberAboveThreshold`; (5) `reminderNotSent_whenMemberOptedOut`; (6) `reminderNotSent_whenNotInTimeWindow`; (7) `reminderSent_includesHoursLogged_inMessage`; (8) `notificationCreated_withCorrectType`. ~8 integration tests (MockMvc + tenant schema context, or unit tests with mocked dependencies). |

### Key Files

**Slice 226A — Modify:**
- `backend/src/main/java/.../settings/OrgSettings.java`
- `backend/src/main/java/.../settings/OrgSettingsController.java`

**Slice 226B — Create:**
- `backend/src/main/java/.../schedule/TimeReminderScheduler.java`
- `backend/src/test/java/.../schedule/TimeReminderSchedulerTest.java`

**Slice 226B — Modify:**
- `backend/src/main/java/.../timeentry/TimeEntryRepository.java`
- `backend/src/main/java/.../notification/NotificationType.java` (if enum exists)

**Slice 226A/B — Read for context:**
- `backend/src/main/java/.../settings/OrgSettings.java` — existing OrgSettings entity
- `backend/src/main/java/.../notification/NotificationService.java` — notification creation pattern
- `backend/src/main/java/.../notification/NotificationPreferenceRepository.java` — preference lookup pattern

### Architecture Decisions

- **UTC interpretation for v1**: OrgSettings stores `timeReminderTime` as UTC. Orgs set their local time expressed in UTC (e.g., South African firm sets 15:00 UTC for 17:00 SAST). No `orgTimezone` column needed for v1. See ADR-117.
- **15-minute polling**: Job runs every 15 minutes. Acceptable imprecision for a reminder feature. See ADR-117.
- **`ScopedValue` for tenant switching in scheduler**: The scheduler uses the same `ScopedValue.where(RequestScopes.TENANT_ID, schema).run(...)` pattern as the rest of the backend. This ensures schema isolation during processing.
- **`TIME_REMINDER` opt-out via existing NotificationPreference**: Reuses the Phase 6.5 infrastructure. No new tables needed.

---

## Epic 227: Time Reminder Frontend — Settings & Preferences

**Goal**: Add a "Time Tracking" settings section to the OrgSettings page with controls for time reminders, and add a "Time reminders" toggle to the notification preferences page for individual member opt-out.

**References**: Architecture doc Sections 30.4.5, 30.8.2. ADR-117.

**Dependencies**: Epic 226 (backend OrgSettings fields must exist).

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **227A** | 227.1–227.7 | Time Tracking settings section in OrgSettings page (toggle, days checkboxes, time picker, min hours), expense markup default field, notification preferences "Time reminders" toggle + frontend tests (~3 tests). ~2 modified files, ~1 test file. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 227.1 | Add "Time Tracking" section to OrgSettings page | 227A | | Modify `app/(app)/org/[slug]/settings/page.tsx` (or the settings form component). Add a new "Time Tracking" section card. Contains: (1) Enable time reminders toggle (`timeReminderEnabled` boolean, Shadcn `Switch`). (2) Working days: row of day-of-week checkboxes (Mon–Fri pre-checked by default). Translates to `timeReminderDays` CSV. (3) Reminder time: `<input type="time">` or Shadcn time picker. Sets `timeReminderTime`. (4) Minimum hours: number input (0.5 step), shows hours (e.g., 4.0). Converts to minutes for `timeReminderMinMinutes`. Helper text: "Note: Time is interpreted as UTC. For SAST (UTC+2), enter 2 hours earlier than your local time." |
| 227.2 | Add default expense markup field to OrgSettings | 227A | | In the same OrgSettings page (or a "Billing" section): add "Default Expense Markup (%)" number input for `defaultExpenseMarkupPercent`. Shows `null` as empty field = "No markup". Tooltip: "Applied to all expenses unless overridden per-expense. Leave blank for no markup." |
| 227.3 | Update OrgSettings actions for new fields | 227A | | Modify `app/(app)/org/[slug]/settings/settings-actions.ts` (or equivalent): update the `updateOrgSettings` action to include `timeReminderEnabled`, `timeReminderDays`, `timeReminderTime`, `timeReminderMinMinutes`, `defaultExpenseMarkupPercent` in the PUT request body. Update TypeScript types for OrgSettings. |
| 227.4 | Add "Time Reminders" to notification preferences page | 227A | | Modify `app/(app)/org/[slug]/notifications/preferences/page.tsx` (or the notification preferences component): add a "Time Reminders" row with a toggle. When toggled off, creates/updates a `NotificationPreference` record with type `TIME_REMINDER` and `enabled=false`. Pattern: existing notification preference toggles (e.g., task assigned, invoice sent). |
| 227.5 | Update notification preferences actions | 227A | | Modify notification preference server actions: ensure `TIME_REMINDER` is a recognized preference type. Fetches current preference state on page load. Toggle sends PATCH/PUT to backend preference endpoint. |
| 227.6 | Write settings time reminders tests | 227A | 227.1 | New file: `app/(app)/org/[slug]/settings/__tests__/settings-time-reminders.test.tsx`. Tests: (1) `renders_time_reminder_section_with_toggle`; (2) `working_day_checkboxes_default_to_weekdays`; (3) `min_hours_converts_to_minutes_on_save`. ~3 tests. |

### Key Files

**Slice 227A — Modify:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx`
- `frontend/app/(app)/org/[slug]/notifications/preferences/page.tsx`

**Slice 227A — Create:**
- `frontend/app/(app)/org/[slug]/settings/__tests__/settings-time-reminders.test.tsx`

**Slice 227A — Read for context:**
- `frontend/app/(app)/org/[slug]/settings/page.tsx` — existing settings sections pattern
- `frontend/app/(app)/org/[slug]/notifications/preferences/page.tsx` — notification preference toggle pattern

### Architecture Decisions

- **UTC helper text in UI**: Since `timeReminderTime` is UTC, the settings UI shows "Note: Time is interpreted as UTC" with a practical example for South African users. This addresses the main usability concern of the UTC-interpretation decision from ADR-117.
- **Min hours displayed as decimal, stored as minutes**: Frontend shows `4.0` hours, the action converts to `240` minutes for the backend. The rounding is `Math.round(hours * 60)`.

---

## Epic 228: Calendar View — Backend Endpoint

**Goal**: Create the `calendar` package with `CalendarService` and `CalendarController`, implementing the single `GET /api/calendar` endpoint that aggregates task and project due dates across accessible projects using a UNION ALL query.

**References**: Architecture doc Sections 30.3.5, 30.4.4. ADR-117 (access control pattern).

**Dependencies**: None — reads existing data from Phase 29 (project due dates, task due dates). No new migrations needed.

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **228A** | 228.1–228.8 | `CalendarService` + `CalendarController`: `CalendarItemDto` record, UNION ALL aggregation query (tasks + project deadlines), access control (member vs. admin/owner), optional `overdue=true` parameter, `projectId`/`type`/`assigneeId` filters + integration tests (~5 tests). ~2 new files, ~1 test file. Backend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 228.1 | Create `CalendarItemDto` record | 228A | | In `calendar/CalendarController.java` (as nested record) or `calendar/CalendarItemDto.java`. Record fields: `UUID id`, `String name`, `String itemType` ("TASK" or "PROJECT"), `LocalDate dueDate`, `String status`, `String priority` (null for projects), `UUID assigneeId` (null for projects), `UUID projectId`, `String projectName`. |
| 228.2 | Create `CalendarService` with main aggregation query | 228A | | New file: `calendar/CalendarService.java`. Method: `List<CalendarItemDto> getCalendarItems(UUID memberId, String orgRole, LocalDate from, LocalDate to, UUID filterProjectId, String filterType, UUID filterAssigneeId)`. Builds UNION ALL query as native SQL or two separate JPQL queries merged in Java. Tasks query: join `tasks → projects → project_members` (for MEMBER; skip join for ADMIN/OWNER). Filter: `t.due_date BETWEEN :from AND :to AND t.status NOT IN ('CANCELLED','DONE')`. Projects query: join `projects → project_members`. Filter: `p.due_date BETWEEN :from AND :to AND p.status != 'ARCHIVED'`. Merge and sort by `dueDate`. |
| 228.3 | Implement ADMIN/OWNER access control in CalendarService | 228A | 228.2 | In `calendar/CalendarService.java`: if `orgRole` is `ORG_ADMIN` or `ORG_OWNER`, use a simplified query without `project_members` join (sees all projects/tasks). If `ORG_MEMBER`, use the access-controlled query. Reuse `ProjectAccessService` logic if accessible, or inline the role check. Pattern: existing `ProjectService.listProjects()` access control. |
| 228.4 | Implement optional `overdue=true` query | 228A | 228.2 | In `calendar/CalendarService.java`: if `includeOverdue=true`, also query tasks with `due_date < :from AND status IN ('OPEN','IN_PROGRESS')` and projects with `due_date < :from AND status = 'ACTIVE'`. Append to results with `dueDate` as-is (may be in the past). Include `overdueCount` in response (count of items past today's date). |
| 228.5 | Implement optional filters in CalendarService | 228A | 228.2 | In `calendar/CalendarService.java`: if `filterProjectId != null`, add `AND p.id = :projectId` to both sub-queries. If `filterType = "TASK"`, omit projects sub-query. If `filterType = "PROJECT"`, omit tasks sub-query. If `filterAssigneeId != null`, add `AND t.assignee_id = :assigneeId` to tasks sub-query only (projects have no assignee). |
| 228.6 | Create `CalendarController` | 228A | 228.2 | New file: `calendar/CalendarController.java`. Single endpoint: `GET /api/calendar`. `@RequestParam LocalDate from`, `@RequestParam LocalDate to` (both required, validated: `to >= from`, max range 366 days). Optional params: `projectId`, `type` (TASK/PROJECT/null), `assigneeId`, `overdue` (boolean, default false). `@PreAuthorize("hasAnyRole('ORG_MEMBER','ORG_ADMIN','ORG_OWNER')")`. Returns `CalendarResponse` record: `List<CalendarItemDto> items`, `int overdueCount`. |
| 228.7 | Validate date range in CalendarController | 228A | 228.6 | In `calendar/CalendarController.java`: validate `from <= to` and that the range is at most 366 days. If invalid, return 400 with `ProblemDetail`. Also validate `type` param is one of "TASK", "PROJECT", or null — reject unknown values. |
| 228.8 | Write CalendarController integration tests | 228A | 228.6 | New file: `calendar/CalendarControllerTest.java`. Tests: (1) `getCalendar_returnsTasksAndProjects_inDateRange`; (2) `getCalendar_memberSeesOnlyAccessibleProjects`; (3) `getCalendar_adminSeesAllProjects`; (4) `getCalendar_filterByType_task`; (5) `getCalendar_withOverdue_returnsOverdueItems`; (6) `getCalendar_invalidDateRange_returns400`. ~5–6 integration tests. |

### Key Files

**Slice 228A — Create:**
- `backend/src/main/java/.../calendar/CalendarService.java`
- `backend/src/main/java/.../calendar/CalendarController.java`
- `backend/src/test/java/.../calendar/CalendarControllerTest.java`

**Slice 228A — Read for context:**
- `backend/src/main/java/.../project/ProjectService.java` — access control pattern (member vs. admin)
- `backend/src/main/java/.../mywork/MyWorkService.java` — cross-project aggregation query pattern
- `backend/src/main/java/.../task/TaskRepository.java` — task query structure

### Architecture Decisions

- **Two separate queries merged in Java rather than native UNION ALL**: JPQL does not support UNION ALL natively. The service runs two JPQL/native queries and merges the results in Java, sorted by `dueDate`. This avoids native SQL and keeps the code readable. For large date ranges the result set is bounded by the 366-day limit.
- **`overdueCount` in response without full overdue data by default**: When `overdue=false`, `overdueCount` is still returned (a cheap COUNT query). When `overdue=true`, the actual overdue items are included. This lets the frontend show a badge without loading all overdue items.
- **No new migration**: This epic is entirely read-only over existing data. Project `due_date` comes from Phase 29 (Epic 205). Task `due_date` already existed.

---

## Epic 229: Calendar View — Frontend Page

**Goal**: Build the calendar frontend page accessible from the sidebar, with a month grid view and a list view (chronological), overdue section, filters, and color coding for urgency.

**References**: Architecture doc Sections 30.4.4, 30.8.2.

**Dependencies**: Epic 228 (calendar backend endpoint must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **229A** | 229.1–229.6 | Calendar page shell, `calendar-actions.ts`, month-view component (grid with due-date dots/counts), list-view component (chronological grouped by week), sidebar navigation link, view toggle (Month/List). ~4 new files. Frontend only. | |
| **229B** | 229.7–229.13 | Overdue section, filters (project, type, assignee), color coding (overdue=red, this week=amber, future=default), frontend tests (~7 tests). ~3 modified files, ~1 test file. Frontend only. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 229.1 | Create `calendar-actions.ts` server action | 229A | | New file: `app/(app)/org/[slug]/calendar/calendar-actions.ts`. Server action `getCalendarItems(from: string, to: string, filters?: CalendarFilters)`. Calls `GET /api/calendar?from=&to=&projectId=&type=&assigneeId=&overdue=`. Parses response into `CalendarItem[]` TypeScript type matching `CalendarItemDto`. |
| 229.2 | Create calendar `page.tsx` | 229A | 229.1 | New file: `app/(app)/org/[slug]/calendar/page.tsx`. Server component. Determines initial date range (current month: `from = first day of month`, `to = last day of month`). Fetches via `getCalendarItems()`. Renders `CalendarPageClient` with initial data. Month/List view toggle in the page header. |
| 229.3 | Create `CalendarMonthView` component | 229A | | New file: `app/(app)/org/[slug]/calendar/calendar-month-view.tsx`. Client component. Props: `items: CalendarItem[], year: number, month: number`. Renders a 7-column month grid using `date-fns` or native `Date` API for day calculation. Each day cell shows: date number, colored dots for each due item (truncated at 3, with "+N more" count). Clicking a day reveals a popover with item list. Navigation: Previous/Next month buttons (re-fetches data). Pattern: no direct codebase pattern — use a custom grid or a headless calendar utility. |
| 229.4 | Create `CalendarListView` component | 229A | | New file: `app/(app)/org/[slug]/calendar/calendar-list-view.tsx`. Client component. Props: `items: CalendarItem[]`. Renders items sorted by `dueDate`, grouped by week header ("Week of Feb 10 – Feb 16"). Each item row: icon (task/project), name, project name, status badge, priority (for tasks), assignee avatar. Click navigates to the task or project detail page. |
| 229.5 | Add "Calendar" to sidebar navigation | 229A | | Modify `components/desktop-sidebar.tsx` and `components/mobile-sidebar.tsx`: add "Calendar" nav item with a calendar icon (e.g., Lucide `CalendarDays`) between "My Work" and "Notifications" in the sidebar. Pattern: existing sidebar nav items. |
| 229.6 | Implement month navigation with re-fetch | 229A | 229.3 | In `CalendarMonthView`: prev/next month buttons update `year` and `month` state, then call `getCalendarItems(newFrom, newTo)` server action (via `useTransition` or `router.push` with query params). Update the displayed items. |
| 229.7 | Add overdue section | 229B | 229.4 | Modify `app/(app)/org/[slug]/calendar/page.tsx` and `calendar-list-view.tsx`: fetch with `overdue=true`. Show an "Overdue" section at the top of the list view (above current week), with red text/border. Show `overdueCount` badge in the page header. Month view shows overdue items in day cells with red indicators. |
| 229.8 | Add filter bar | 229B | 229.1 | Modify `app/(app)/org/[slug]/calendar/page.tsx` or create `CalendarFilters` component: filter bar with: (1) Project dropdown (load project list, default "All Projects"). (2) Type toggle: All / Tasks / Projects. (3) Assignee dropdown (load team members, default "All Members"). Filters pass query params to `getCalendarItems()` action and re-fetch. |
| 229.9 | Implement color coding for urgency | 229B | 229.3, 229.4 | Helper function `getDueDateColor(dueDate: Date, status: string)`: `dueDate < today` → `text-red-600` + `bg-red-50` (overdue); `dueDate <= today + 7 days` → `text-amber-600` + `bg-amber-50` (due this week); otherwise → default text/bg. Apply to dots in month view and row styles in list view. |
| 229.10 | Write calendar page tests — month view | 229B | 229.3 | New file: `app/(app)/org/[slug]/calendar/__tests__/calendar-page.test.tsx`. Tests: (1) `renders_month_grid_with_correct_day_count`; (2) `shows_task_dot_on_due_date`; (3) `shows_plus_n_more_when_multiple_items_on_same_day`; (4) `navigates_to_next_month_on_button_click`. |
| 229.11 | Write calendar page tests — list view and overdue | 229B | 229.4, 229.7 | Continuing test file. Tests: (5) `renders_items_grouped_by_week`; (6) `shows_overdue_section_when_enabled`; (7) `applies_red_color_to_overdue_items`. Total: ~7 frontend tests. |

### Key Files

**Slice 229A — Create:**
- `frontend/app/(app)/org/[slug]/calendar/page.tsx`
- `frontend/app/(app)/org/[slug]/calendar/calendar-actions.ts`
- `frontend/app/(app)/org/[slug]/calendar/calendar-month-view.tsx`
- `frontend/app/(app)/org/[slug]/calendar/calendar-list-view.tsx`

**Slice 229A — Modify:**
- `frontend/components/desktop-sidebar.tsx`
- `frontend/components/mobile-sidebar.tsx`

**Slice 229B — Modify:**
- `frontend/app/(app)/org/[slug]/calendar/page.tsx`
- `frontend/app/(app)/org/[slug]/calendar/calendar-month-view.tsx`
- `frontend/app/(app)/org/[slug]/calendar/calendar-list-view.tsx`

**Slice 229B — Create:**
- `frontend/app/(app)/org/[slug]/calendar/__tests__/calendar-page.test.tsx`

**Slice 229A/B — Read for context:**
- `frontend/components/desktop-sidebar.tsx` — sidebar nav item pattern
- `frontend/app/(app)/org/[slug]/my-work/page.tsx` — cross-project aggregation page pattern
- `frontend/components/projects/project-list.tsx` — list rendering pattern

### Architecture Decisions

- **Custom month grid (no calendar library)**: The month view is implemented as a CSS Grid (7 columns, 5–6 rows) with due-date dots. This avoids introducing a heavy calendar library dependency for a read-only display. The grid calculation uses `new Date(year, month, 1).getDay()` for the offset.
- **Re-fetch on month navigation**: Month navigation does not use client-side state with all data pre-loaded. Each navigation re-calls `getCalendarItems()` for the new month range. This keeps the data fresh and avoids overfetching.
- **Calendar page is read-only**: No drag-and-drop scheduling. Items are clickable links to their respective task or project detail pages. This is explicitly in scope as a deadline visualization tool, not a scheduling tool.

---

## Summary Statistics

| Epic | Slices | New Backend Files | Modified Backend Files | New Frontend Files | Modified Frontend Files | Tests (BE) | Tests (FE) |
|------|--------|-------------------|------------------------|---------------------|-------------------------|------------|------------|
| 218 | 2 | 4 (migration, entity, enum, repo) | 0 | 0 | 0 | 12 unit | 0 |
| 219 | 2 | 3 (service, controller, 2 events) | 0 | 0 | 0 | ~20 integration | 0 |
| 220 | 2 | 0 | 0 | 5 (page, actions, list, dialog, badge) | 3 (layout, my-work, expense-list) | 0 | ~10 |
| 221 | 2 | 2 (enum, test) | 2 (InvoiceLine, InvoiceService) | 0 | 0 | ~15 integration | 0 |
| 222 | 1 | 0 | 0 | 0 | 3 (unbilled summary, generate dialog, actions) | 0 | ~7 |
| 223 | 2 | 3 (RecurrenceRule, test, verify migration) | 1 (Task entity) | 0 | 0 | ~17 unit | 0 |
| 224 | 2 | 2 (event, test) | 2 (TaskService, TaskController) | 0 | 0 | ~15 integration | 0 |
| 225 | 1 | 0 | 0 | 1 (test) | 4 (task-form, task-list, task-detail-sheet, actions) | 0 | ~5 |
| 226 | 2 | 2 (scheduler, test) | 3 (OrgSettings, OrgSettingsController, TimeEntryRepo) | 0 | 0 | ~12 | 0 |
| 227 | 1 | 0 | 0 | 1 (test) | 2 (settings, notification prefs) | 0 | ~3 |
| 228 | 1 | 3 (service, controller, test) | 0 | 0 | 0 | ~6 integration | 0 |
| 229 | 2 | 0 | 0 | 5 (page, actions, month-view, list-view, test) | 2 (sidebars) | 0 | ~7 |
| **Total** | **20** | **~19** | **~8** | **~12** | **~14** | **~97** | **~32** |

---

### Critical Files for Implementation

- `backend/src/main/resources/db/migration/tenant/V50__expenses_recurring_calendar.sql` - Single combined migration for all Phase 30 schema changes; must be authored by the 218A builder and verified by 223A and 226A builders
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/Expense.java` - Core new entity following TimeEntry billing pattern; all expense billing logic depends on its computed methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` - Most heavily modified existing file; unbilled summary extension, generate-with-expenses, approve/void stamp/clear flows all concentrated here
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/task/TaskService.java` - Critical modification point for recurring task auto-creation; the `completeTask()` extension must be synchronous and transactional
- `frontend/app/(app)/org/[slug]/projects/[id]/layout.tsx` - Pattern file for adding the Expenses tab; all project-detail tab additions follow this layout pattern