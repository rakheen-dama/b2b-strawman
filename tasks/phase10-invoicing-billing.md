# Phase 10 — Invoicing & Billing from Time

Phase 10 bridges the gap between tracked billable work and revenue collection. It introduces two new entities (`Invoice`, `InvoiceLine`), extends `TimeEntry` with an invoice reference for double-billing prevention, adds a complete invoice lifecycle (Draft -> Approved -> Sent -> Paid -> Void), implements a mocked payment service provider (PSP) adapter as a clean integration seam for future Stripe/payment integration, and provides a self-contained HTML invoice preview rendered via Thymeleaf.

**Architecture doc**: `architecture/phase10-invoicing-billing.md` (Section 11 of ARCHITECTURE.md)

**ADRs**: [ADR-048](../adr/ADR-048-invoice-numbering-strategy.md) (invoice numbering), [ADR-049](../adr/ADR-049-line-item-granularity.md) (line item granularity), [ADR-050](../adr/ADR-050-double-billing-prevention.md) (double-billing prevention), [ADR-051](../adr/ADR-051-psp-adapter-design.md) (PSP adapter design)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 81 | Invoice Entity Foundation & Migration | Backend | -- | M | 81A, 81B | |
| 82 | Invoice CRUD & Unbilled Time API | Backend | 81 | M | 82A, 82B | |
| 83 | Invoice Lifecycle Transitions & Notifications | Backend | 82 | M | 83A, 83B | |
| 84 | PSP Adapter & HTML Invoice Preview | Backend | 83 | S | 84A | |
| 85 | Invoice Frontend — List, Detail & Generation Flow | Frontend | 82, 83, 84 | L | 85A, 85B, 85C | |
| 86 | Time Entry Billing Status Frontend | Frontend | 82 | S | 86A | |

## Dependency Graph

```
[E81 Invoice Entity Foundation] ──► [E82 Invoice CRUD & Unbilled Time]
          (Backend)                           (Backend)
                                                │
                                                ├──► [E83 Lifecycle & Notifications]
                                                │              (Backend)
                                                │                │
                                                │                └──► [E84 PSP & HTML Preview]
                                                │                            (Backend)
                                                │                              │
                                                ├──────────────────────────────┤
                                                │                              │
                                   E82 + E83 + E84 ──────────► [E85 Invoice Frontend]
                                                │                     (Frontend)
                                                │
                                                └──► [E86 Time Entry Billing Status Frontend]
                                                              (Frontend)
```

**Parallel tracks**:
- Epic 81 (entity foundation) has no external dependencies and is the sole starting point.
- After Epic 82 completes: Epic 86 (time entry billing frontend) can begin in parallel with Epic 83 (lifecycle).
- After Epic 83 completes: Epic 84 (PSP + preview) can begin.
- Epic 85 (invoice frontend) is the final convergence point — it depends on Epics 82, 83, and 84 (all backend APIs).
- Epic 86 can run fully in parallel with Epics 83, 84, and 85.

## Implementation Order

### Stage 1: Backend Entity Foundation

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1 | Epic 81 | 81A | V22 migration (invoices, invoice_lines, invoice_number_seq tables; customer address column; time_entries invoice_id FK). Foundation for everything. |
| 2 | Epic 81 | 81B | Invoice + InvoiceLine entities, InvoiceStatus enum, repositories, InvoiceNumberService. Depends on V22 from 81A. |

### Stage 2: Invoice CRUD + Unbilled Time

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3 | Epic 82 | 82A | InvoiceService (createDraft, updateDraft, deleteDraft, getInvoice, listInvoices), InvoiceController CRUD endpoints, request/response DTOs. Depends on 81B. |
| 4 | Epic 82 | 82B | UnbilledTimeService (native SQL query), unbilled time endpoint on CustomerController, time entry edit/delete protection (409 on billed entries). Depends on 82A. |

### Stage 3: Lifecycle + PSP + Time Entry Frontend (Parallel tracks)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5a | Epic 83 | 83A | InvoiceLifecycleService (approve, markSent, voidInvoice), lifecycle endpoints, invoice number assignment, time entry billed/unbilled marking. Depends on 82A. |
| 5b | Epic 86 | 86A | Time entry billing status badges, filter dropdown, lock indicator. Depends on 82B (billing status filter API). Can run in parallel with 83A. |
| 6 | Epic 83 | 83B | InvoiceEvent sealed interface, InvoiceNotificationHandler, audit events for all lifecycle transitions. Depends on 83A. |
| 7 | Epic 84 | 84A | PaymentProvider interface, MockPaymentProvider, payment recording integration, Thymeleaf invoice preview template. Depends on 83A (lifecycle service). |

### Stage 4: Invoice Frontend

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 8 | Epic 85 | 85A | Invoice list page (route, summary cards, table, filters, sidebar nav). Depends on 82A + 83A (list and lifecycle APIs). |
| 9 | Epic 85 | 85B | Invoice detail page (draft edit + read-only modes, lifecycle action buttons). Depends on 85A + 83A + 84A. |
| 10 | Epic 85 | 85C | Invoice generation flow (customer unbilled time review, entry selection, draft creation), customer detail invoices tab. Depends on 82B + 85A. |

### Timeline

```
Stage 1:  [81A] → [81B]                              ← entity foundation
Stage 2:  [82A] → [82B]                              ← CRUD + unbilled time
Stage 3:  [83A // 86A] → [83B] → [84A]               ← lifecycle + PSP (parallel with time entry FE)
Stage 4:  [85A] → [85B // 85C]                        ← invoice frontend
```

---

## Epic 81: Invoice Entity Foundation & Migration

**Goal**: Create the V22 tenant migration with all new tables (invoices, invoice_lines, invoice_number_seq), alter existing tables (customers.address, time_entries.invoice_id), implement Invoice + InvoiceLine entities with TenantAware pattern, InvoiceStatus enum with state machine validation, repositories, InvoiceNumberService for sequential per-tenant numbering, and add TimeEntry/Customer entity modifications.

**References**: Architecture doc Sections 11.2.1-11.2.5, 11.3.3, 11.9. [ADR-048](../adr/ADR-048-invoice-numbering-strategy.md), [ADR-050](../adr/ADR-050-double-billing-prevention.md).

**Dependencies**: None (builds on existing multi-tenant infrastructure)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **81A** | 81.1-81.4 | V22 migration (all tables + alterations), shared-schema RLS, Customer address field, TimeEntry invoice_id + domain methods | |
| **81B** | 81.5-81.11 | Invoice entity, InvoiceStatus enum, InvoiceLine entity, repositories, InvoiceNumberService, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 81.1 | Create V22 tenant migration | 81A | | `db/migration/tenant/V22__add_invoices.sql`. Three new tables (`invoices`, `invoice_lines`, `invoice_number_seq`) with all columns, constraints, and indexes per Section 11.9. ALTER TABLE `customers` ADD COLUMN `address TEXT`. ALTER TABLE `time_entries` ADD COLUMN `invoice_id UUID REFERENCES invoices(id)`. Include partial unique index `idx_invoice_lines_time_entry_unique` on `invoice_lines(time_entry_id) WHERE time_entry_id IS NOT NULL`. Include unique index `idx_invoices_tenant_number` on `invoices(tenant_id, invoice_number) WHERE invoice_number IS NOT NULL`. Pattern: follow `V19__create_rate_budget_tables.sql` for table structure, `V14__create_audit_events.sql` for RLS policies. |
| 81.2 | Create V22 shared-schema migration with RLS | 81A | | `db/migration/global/V22__add_invoices_shared.sql`. Same DDL as tenant migration, plus RLS policies on `invoices`, `invoice_lines`, `invoice_number_seq` using `USING/WITH CHECK (tenant_id = current_setting('app.current_tenant'))`. Pattern: follow existing RLS policies on `project_budgets` in the Phase 8 shared-schema migration. |
| 81.3 | Add `address` field to Customer entity | 81A | | Modify `customer/Customer.java` — add `String address` field (nullable, `@Column(columnDefinition = "TEXT")`). Update constructor or add setter. Update `update()` domain method to accept `address` parameter. Update `CustomerService.updateCustomer()` and `CustomerController` request DTO to include `address`. Pattern: follow existing nullable field additions in Customer entity. ~4 files modified. |
| 81.4 | Add `invoiceId` field and billing methods to TimeEntry entity | 81A | | Modify `timeentry/TimeEntry.java` — add `UUID invoiceId` field (nullable, `@Column(name = "invoice_id")`). Add methods: `markBilled(UUID invoiceId)` — validates `billable == true && this.invoiceId == null`, sets `invoiceId`. `markUnbilled()` — clears `invoiceId`. `isBilled()` — returns `invoiceId != null`. `isLocked()` — alias for `isBilled()`. Pattern: follow existing domain methods in `TimeEntry.java`. |
| 81.5 | Create InvoiceStatus enum | 81B | | `invoice/InvoiceStatus.java` — enum values: `DRAFT`, `APPROVED`, `SENT`, `PAID`, `VOID`. Method: `canTransitionTo(InvoiceStatus target)` using `switch` expression. Valid transitions: DRAFT->APPROVED, APPROVED->SENT, APPROVED->VOID, SENT->PAID, SENT->VOID. PAID and VOID are terminal. Pattern: similar to existing status enums in project/task entities but with explicit transition validation. |
| 81.6 | Create Invoice entity | 81B | | `invoice/Invoice.java` — JPA entity mapped to `invoices`. All fields per Section 11.2.1 (21 columns). `@FilterDef`/`@Filter` for `tenantFilter`, `@EntityListeners(TenantAwareEntityListener.class)`, implements `TenantAware`. Domain methods: `approve(UUID approvedBy)`, `markSent()`, `recordPayment(String paymentReference)`, `voidInvoice()`, `recalculateTotals(List<InvoiceLine> lines)`, `canEdit()`. Each transition method validates current status via `InvoiceStatus.canTransitionTo()`, throws `IllegalStateException` on invalid. Constructor with required fields: `customerId, currency, customerName, orgName, createdBy`. Pattern: follow `budget/ProjectBudget.java` for entity structure, `timeentry/TimeEntry.java` for TenantAware. |
| 81.7 | Create InvoiceLine entity | 81B | | `invoice/InvoiceLine.java` — JPA entity mapped to `invoice_lines`. All fields per Section 11.2.2. `@FilterDef`/`@Filter`/`TenantAware` pattern. Constructor: `InvoiceLine(UUID invoiceId, UUID projectId, UUID timeEntryId, String description, BigDecimal quantity, BigDecimal unitPrice, BigDecimal amount, int sortOrder)`. Pattern: follow `Invoice.java` entity from 81.6. |
| 81.8 | Create InvoiceRepository | 81B | | `invoice/InvoiceRepository.java` — extends `JpaRepository<Invoice, UUID>`. Methods: `findOneById(UUID id)` (JPQL, bypasses findById EntityManager.find issue with @Filter), `Page<Invoice> findByFilters(UUID customerId, String status, LocalDate from, LocalDate to, Pageable pageable)` (JPQL with optional parameters using `CAST` pattern for nullable dates). Pattern: follow `billingrate/BillingRateRepository.java` for JPQL `findOneById`, `timeentry/TimeEntryRepository.java` for paginated queries. |
| 81.9 | Create InvoiceLineRepository | 81B | | `invoice/InvoiceLineRepository.java` — extends `JpaRepository<InvoiceLine, UUID>`. Methods: `List<InvoiceLine> findByInvoiceIdOrderBySortOrder(UUID invoiceId)`, `findOneById(UUID id)` (JPQL), `void deleteByInvoiceId(UUID invoiceId)`, `Optional<InvoiceLine> findByTimeEntryId(UUID timeEntryId)`. Pattern: follow `InvoiceRepository` from 81.8. |
| 81.10 | Create InvoiceNumberService | 81B | | `invoice/InvoiceNumberService.java` — `@Service`. Method: `String assignInvoiceNumber()` — uses native SQL with `SELECT ... FOR UPDATE` on `invoice_number_seq`. If no row for tenant: INSERT with `next_value = 1`, return `"INV-0001"`. Else: UPDATE `next_value = next_value + 1`, return formatted `"INV-" + leftPad(value, 4, '0')`. Uses `JdbcTemplate` or `EntityManager.createNativeQuery()`. Tenant ID from `RequestScopes.TENANT_ID`. Pattern: follow native SQL query patterns in `report/ReportService.java`. See ADR-048. |
| 81.11 | Add entity foundation integration tests | 81B | | `invoice/InvoiceEntityIntegrationTest.java` (~10 tests): Invoice persist and retrieve, InvoiceLine persist and retrieve, InvoiceStatus transitions (DRAFT->APPROVED valid, DRAFT->SENT invalid, PAID terminal, VOID terminal), Invoice `recalculateTotals()`, `canEdit()` returns true only for DRAFT, InvoiceNumberService assigns sequential numbers (INV-0001, INV-0002, INV-0003), number uniqueness across concurrent calls, TimeEntry `markBilled()`/`markUnbilled()` methods, Customer address field persistence. Seed: provision tenant, sync 1 member, create project, create customer, link customer-project, create task, create time entries. Pattern: follow `billingrate/BillingRateResolutionTest.java` for test setup. |

### Key Files

**Slice 81A — Create:**
- `backend/src/main/resources/db/migration/tenant/V22__add_invoices.sql`
- `backend/src/main/resources/db/migration/global/V22__add_invoices_shared.sql`

**Slice 81A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` — add `address` field
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerService.java` — accept `address` in update
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — add `address` to DTOs
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` — add `invoiceId` + billing methods

**Slice 81A — Read for context:**
- `backend/src/main/resources/db/migration/tenant/V19__create_rate_budget_tables.sql` — Migration table structure pattern
- `backend/src/main/resources/db/migration/tenant/V14__create_audit_events.sql` — RLS policy pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAware.java` — Interface
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/multitenancy/TenantAwareEntityListener.java` — Listener

**Slice 81B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLineRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNumberService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceEntityIntegrationTest.java`

**Slice 81B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/ProjectBudget.java` — Entity pattern with BigDecimal fields
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateRepository.java` — JPQL `findOneById` pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java` — Native SQL query pattern
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateResolutionTest.java` — Integration test setup

### Architecture Decisions

- **`invoice/` package**: Single new feature package under `io.b2mash.b2b.b2bstrawman.invoice` containing Invoice, InvoiceLine, InvoiceStatus, repositories, and services. All invoice-related code in one place.
- **Two-slice decomposition**: 81A is pure data infrastructure (migration + entity modifications to Customer/TimeEntry). 81B is the new domain model (entities + repos + numbering service + tests). Splitting migration from entity code keeps each slice under 10 files.
- **InvoiceNumberService uses native SQL**: The `invoice_number_seq` table is NOT a JPA entity — direct JDBC/native SQL with `SELECT FOR UPDATE` is the correct approach for a counter table. See ADR-048.
- **Partial unique index for double-billing**: The `idx_invoice_lines_time_entry_unique` index is defense-in-depth. Application-layer validation in `InvoiceService.createDraft()` is the primary guard. See ADR-050.
- **Customer address as TEXT**: No structured address fields (street, city, etc.) — a single TEXT field is sufficient for invoice display. This matches the architecture doc's intent for a non-breaking addition.

---

## Epic 82: Invoice CRUD & Unbilled Time API

**Goal**: Implement the InvoiceService with full CRUD operations (create draft from time entries, update draft, delete draft, get invoice, list invoices), the InvoiceController with REST endpoints, the UnbilledTimeService for querying customer unbilled time grouped by project, and time entry edit/delete protection for billed entries.

**References**: Architecture doc Sections 11.3.1-11.3.2, 11.4.1, 11.4.3-11.4.4, 11.4.6. [ADR-049](../adr/ADR-049-line-item-granularity.md).

**Dependencies**: Epic 81 (Invoice/InvoiceLine entities, repositories, migration)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **82A** | 82.1-82.6 | InvoiceService CRUD, InvoiceController, request/response DTOs, line item endpoints, audit events, integration tests | |
| **82B** | 82.7-82.11 | UnbilledTimeService, unbilled time endpoint, time entry edit/delete protection, billing status filter, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 82.1 | Create invoice request/response DTOs | 82A | | Inner DTOs in `InvoiceController.java`: `CreateInvoiceRequest(UUID customerId, String currency, List<UUID> timeEntryIds, LocalDate dueDate, String notes, String paymentTerms)`, `UpdateInvoiceRequest(LocalDate dueDate, String notes, String paymentTerms, BigDecimal taxAmount)`, `InvoiceResponse(all Invoice fields + List<InvoiceLineResponse> lines)`, `InvoiceLineResponse(id, projectId, projectName, timeEntryId, description, quantity, unitPrice, amount, sortOrder)`, `AddLineRequest(UUID projectId, String description, BigDecimal quantity, BigDecimal unitPrice, int sortOrder)`, `UpdateLineRequest(String description, BigDecimal quantity, BigDecimal unitPrice, int sortOrder)`. Add `@NotNull`/`@NotBlank`/`@Positive` validation. Pattern: follow `task/TaskController.java` inner DTO pattern. |
| 82.2 | Create InvoiceService with CRUD operations | 82A | | `invoice/InvoiceService.java` — `@Service`, `@Transactional`. Key methods: (1) `createDraft(CreateInvoiceRequest, UUID createdBy)` — validates customer exists + ACTIVE, validates time entry IDs (unbilled, billable, belong to customer's projects via CustomerProjectRepository, match currency), snapshots customer (name, email, address) + org name (via OrgSettings), creates Invoice + InvoiceLines (one per time entry: qty=durationMinutes/60.0, unitPrice=billingRateSnapshot, desc="{taskTitle} - {memberName} - {date}"), computes totals, publishes `invoice.created` audit event. (2) `updateDraft(UUID id, UpdateInvoiceRequest)` — validates DRAFT status, updates fields, recomputes totals if taxAmount changed, audit event. (3) `deleteDraft(UUID id)` — validates DRAFT, deletes lines then invoice (no cascade), audit event. (4) `getInvoice(UUID id)` — returns Invoice + lines, permission check. (5) `listInvoices(filters, Pageable)` — paginated, filtered. Inject: `InvoiceRepository`, `InvoiceLineRepository`, `CustomerRepository`, `CustomerProjectRepository`, `TimeEntryRepository`, `TaskRepository`, `MemberRepository`, `ProjectRepository`, `OrgSettingsService`, `AuditService`, `ProjectAccessService`. Pattern: follow `budget/BudgetService.java` for CRUD + audit. |
| 82.3 | Create InvoiceController CRUD endpoints | 82A | | `invoice/InvoiceController.java` — `@RestController`, `@RequestMapping("/api/invoices")`. Endpoints: `POST /` (201), `PUT /{id}` (200), `DELETE /{id}` (204), `GET /{id}` (200), `GET /` (200, paginated). Permission: admin/owner full access, project leads limited scope (createDraft from own projects only, edit/delete only if creator). Extract `memberId`/`orgRole` from `RequestScopes`. Pattern: follow `budget/BudgetController.java`. |
| 82.4 | Create invoice line item endpoints | 82A | | Add to `InvoiceController.java`: `POST /{id}/lines` (add manual line to draft, 201), `PUT /{id}/lines/{lineId}` (update line on draft, 200), `DELETE /{id}/lines/{lineId}` (remove line from draft, 204). Service methods in `InvoiceService`: `addLine()`, `updateLine()`, `removeLine()` — all validate invoice is DRAFT, recompute subtotal/total after mutation. Permission: admin/owner full, lead if creator. Pattern: nested resource endpoints similar to project member endpoints. |
| 82.5 | Add SecurityConfig updates for invoice endpoints | 82A | | Modify `security/SecurityConfig.java` — verify `/api/invoices/**` is covered by authenticated endpoint patterns. Likely already covered by existing `/api/**` pattern. If not, add explicitly. |
| 82.6 | Add InvoiceService CRUD integration tests | 82A | | `invoice/InvoiceCrudIntegrationTest.java` (~14 tests): create draft from time entries (happy path), create draft validates customer is ACTIVE, create draft rejects already-billed time entries, create draft rejects non-billable entries, create draft rejects entries not belonging to customer's projects, create draft rejects mismatched currency, update draft (dueDate, notes, taxAmount), update non-draft returns 409, delete draft, delete non-draft returns 409, get invoice with lines, list invoices with pagination, list invoices filtered by status/customer, add manual line item recomputes totals. Seed: provision tenant, sync 2 members (admin + lead), create 2 projects, create customer, link customer to projects, create tasks, create billable time entries with rate snapshots. Pattern: follow `billingrate/BillingRateIntegrationTest.java`. |
| 82.7 | Create UnbilledTimeService | 82B | | `invoice/UnbilledTimeService.java` — `@Service`. Method: `getUnbilledTime(UUID customerId, LocalDate from, LocalDate to)` — native SQL query joining `time_entries`, `tasks`, `projects`, `customer_projects`, `members` (query per Section 11.3.2). Returns `List<UnbilledTimeSummary>` grouped by project. Records: `UnbilledTimeSummary(UUID projectId, String projectName, List<UnbilledTimeEntry> entries, Map<String, BigDecimal> totalsByCurrency)`, `UnbilledTimeEntry(UUID id, LocalDate date, int durationMinutes, BigDecimal durationHours, BigDecimal billingRateSnapshot, String billingRateCurrency, BigDecimal amount, String description, String taskTitle, String memberName)`. Uses `EntityManager.createNativeQuery()` or `JdbcTemplate`. RLS handles tenant isolation for native queries. Pattern: follow `report/ReportService.java` for native SQL queries. |
| 82.8 | Create unbilled time endpoint | 82B | | Add endpoint to `customer/CustomerController.java`: `GET /api/customers/{customerId}/unbilled-time` with query params `from`, `to` (both LocalDate, optional). Response: `{ customerId, customerName, projects: [...], grandTotalsByCurrency: {...} }`. Permission: admin/owner full, lead for own projects only. Pattern: follow existing customer endpoints. |
| 82.9 | Add time entry edit/delete protection | 82B | | Modify `timeentry/TimeEntryService.java` — in `updateTimeEntry()` and `deleteTimeEntry()` methods, add check: if `timeEntry.isBilled()` (invoiceId != null), throw `ErrorResponseException(409)` with ProblemDetail containing invoice number. Requires a lookup to `InvoiceRepository.findOneById(timeEntry.getInvoiceId())` to get the invoice number for the error message. Pattern: follow existing 409 Conflict handling in lifecycle services. |
| 82.10 | Add billing status filter to time entry list | 82B | | Modify `timeentry/TimeEntryRepository.java` — add `billingStatus` filter parameter to existing list query. Values: `UNBILLED` (billable=true AND invoice_id IS NULL), `BILLED` (invoice_id IS NOT NULL), `NON_BILLABLE` (billable=false), `ALL` (no filter). Modify `TimeEntryController` to accept `billingStatus` query param (default ALL). Add `invoiceId` and `invoiceNumber` to time entry response DTO. The `invoiceNumber` requires a join or separate lookup to `invoices` table. Pattern: follow existing filter patterns in `TimeEntryRepository`. |
| 82.11 | Add unbilled time and protection integration tests | 82B | | `invoice/UnbilledTimeIntegrationTest.java` (~8 tests): unbilled time returns entries grouped by project, unbilled time excludes billed entries, unbilled time excludes non-billable entries, unbilled time filtered by date range, unbilled time empty when all billed, time entry PUT returns 409 when billed, time entry DELETE returns 409 when billed, billing status filter returns correct subsets. Seed: reuse test setup from 82.6, add approved invoice to mark some entries as billed. Pattern: follow `invoice/InvoiceCrudIntegrationTest.java` from 82.6. |

### Key Files

**Slice 82A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCrudIntegrationTest.java`

**Slice 82A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/security/SecurityConfig.java` — Verify endpoint patterns

**Slice 82A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetService.java` — CRUD + audit pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetController.java` — Controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` — Customer lookup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectRepository.java` — Customer-project linking
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — Time entry queries
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectAccessService.java` — Permission checks

**Slice 82B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeIntegrationTest.java`

**Slice 82B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — Add unbilled time endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` — Add billed entry protection
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryRepository.java` — Add billing status filter
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java` — Add billingStatus param + response fields

**Slice 82B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/report/ReportService.java` — Native SQL query pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — From 82A (needed for billed entry lookup)

### Architecture Decisions

- **Two-slice decomposition**: 82A is CRUD (the core service + controller + DTOs). 82B is adjacent capabilities that extend existing endpoints (unbilled time, edit protection, billing status filter). Splitting keeps each slice focused and under 10 files.
- **Unbilled time as separate service**: `UnbilledTimeService` is its own class because the native SQL query is complex (5-way join) and conceptually distinct from invoice CRUD.
- **Unbilled time endpoint on CustomerController**: Follows REST convention — unbilled time is a sub-resource of Customer. The endpoint lives on `CustomerController`, not `InvoiceController`.
- **Time entry protection in TimeEntryService**: The 409 check belongs in the existing service, not in a separate guard. This ensures all code paths (including future ones) that modify time entries respect the lock.
- **Billing status filter as query param**: Extends the existing time entry list endpoint rather than creating a new one. The `billingStatus` param defaults to `ALL` for backward compatibility.

---

## Epic 83: Invoice Lifecycle Transitions & Notifications

**Goal**: Implement the invoice lifecycle state machine (DRAFT -> APPROVED -> SENT -> PAID, with VOID from APPROVED/SENT), including invoice number assignment at approval, time entry billed/unbilled status management, domain events for notifications, and audit events for all transitions.

**References**: Architecture doc Sections 11.3.3-11.3.4, 11.4.2, 11.5.1-11.5.3, 11.7.1-11.7.2. [ADR-048](../adr/ADR-048-invoice-numbering-strategy.md).

**Dependencies**: Epic 82 (InvoiceService, InvoiceController)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **83A** | 83.1-83.5 | InvoiceLifecycleService (approve, send, void), lifecycle controller endpoints, invoice number assignment, time entry marking, integration tests | |
| **83B** | 83.6-83.10 | InvoiceEvent sealed interface, InvoiceNotificationHandler, notification type constants, audit events for all transitions, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 83.1 | Create InvoiceLifecycleService (approve, markSent, voidInvoice) | 83A | | `invoice/InvoiceLifecycleService.java` — `@Service`, `@Transactional`. Methods: (1) `approve(UUID invoiceId, UUID approvedBy)` — validates status==DRAFT, validates at least one line item exists, calls `InvoiceNumberService.assignInvoiceNumber()`, calls `invoice.approve(approvedBy)`, marks all referenced time entries as billed (bulk update: `UPDATE time_entries SET invoice_id = :invoiceId WHERE id IN (SELECT time_entry_id FROM invoice_lines WHERE invoice_id = :invoiceId AND time_entry_id IS NOT NULL)`), publishes audit event. (2) `markSent(UUID invoiceId)` — validates APPROVED, calls `invoice.markSent()`, audit. (3) `voidInvoice(UUID invoiceId)` — validates APPROVED or SENT, calls `invoice.voidInvoice()`, reverts time entries (`UPDATE time_entries SET invoice_id = NULL WHERE invoice_id = :invoiceId`), clears `time_entry_id` on voided invoice's lines, audit. Permission: admin/owner only for all operations. Inject: `InvoiceRepository`, `InvoiceLineRepository`, `TimeEntryRepository`, `InvoiceNumberService`, `AuditService`. Pattern: follow `budget/BudgetCheckService.java` for event-driven service pattern. |
| 83.2 | Add lifecycle endpoints to InvoiceController | 83A | | Add to `invoice/InvoiceController.java`: `POST /{id}/approve` (200), `POST /{id}/send` (200), `POST /{id}/void` (200). All return updated `InvoiceResponse`. Invalid transitions return 409 Conflict with ProblemDetail body. Permission: admin/owner only (check orgRole from RequestScopes). Pattern: follow existing lifecycle endpoints in the codebase. |
| 83.3 | Handle time entry bulk marking in approve/void | 83A | | Within `InvoiceLifecycleService`: On approve — iterate line items with non-null `timeEntryId`, load each TimeEntry, call `markBilled(invoiceId)`. On void — load all time entries with `invoice_id = invoiceId`, call `markUnbilled()` on each. Also clear `timeEntryId` on voided invoice's lines (to release partial unique index). Consider using a JPQL bulk update for efficiency: `@Modifying @Query("UPDATE TimeEntry t SET t.invoiceId = :invoiceId WHERE t.id IN :ids")`. Pattern: follow existing bulk update patterns in the codebase. |
| 83.4 | Add lifecycle integration tests | 83A | | `invoice/InvoiceLifecycleIntegrationTest.java` (~12 tests): approve draft (happy path, number assigned INV-0001), approve sets issue date, approve marks time entries as billed, approve with no lines returns 400/409, approve non-draft returns 409, markSent from APPROVED, markSent from non-APPROVED returns 409, voidInvoice from APPROVED, voidInvoice from SENT, void reverts time entries to unbilled, void clears time_entry_id on lines (enables re-invoicing), PAID is terminal (409 on further transitions), sequential numbering across multiple approvals (INV-0001, INV-0002, INV-0003). Seed: provision tenant, create full invoice pipeline (customer -> project -> task -> time entries -> draft invoice). Pattern: follow `invoice/InvoiceCrudIntegrationTest.java` from 82.6. |
| 83.5 | Add re-invoicing after void test | 83A | | Within `InvoiceLifecycleIntegrationTest.java` — additional test (~2 tests): void an approved invoice, verify time entries are unbilled, create a new draft from the same time entries (succeeds), approve new invoice (new number INV-0002). This validates the full void -> re-invoice cycle end-to-end. |
| 83.6 | Create InvoiceEvent sealed interface and records | 83B | | `invoice/InvoiceEvent.java` — sealed interface with records: `Approved(UUID invoiceId, String invoiceNumber, UUID createdBy, UUID approvedBy)`, `Sent(UUID invoiceId, String invoiceNumber)`, `Paid(UUID invoiceId, String invoiceNumber, UUID createdBy, String paymentReference)`, `Voided(UUID invoiceId, String invoiceNumber, UUID createdBy, UUID approvedBy)`. Each record implements `InvoiceEvent`. Pattern: follow `budget/BudgetThresholdEvent.java` record pattern. |
| 83.7 | Create InvoiceNotificationHandler | 83B | | `invoice/InvoiceNotificationHandler.java` — `@Component`, `@EventListener` methods for each `InvoiceEvent` variant. Notification targets: `Approved` -> creator (if != approver). `Sent` -> org admins/owners. `Paid` -> creator + admins/owners. `Voided` -> creator + approver + admins/owners. Uses existing `NotificationService.createNotification()`. Query admins/owners via `MemberRepository`. Pattern: follow `notification/NotificationEventHandler.java` for event listener structure. |
| 83.8 | Add notification type constants and preferences | 83B | | Modify `notification/NotificationService.java` — add constants: `INVOICE_APPROVED`, `INVOICE_SENT`, `INVOICE_PAID`, `INVOICE_VOIDED`. Register in `NotificationPreference` defaults (all enabled by default). Pattern: follow existing notification type constant additions from Phase 6.5 / Phase 8. |
| 83.9 | Publish events from InvoiceLifecycleService | 83B | | Modify `invoice/InvoiceLifecycleService.java` — inject `ApplicationEventPublisher`. After each transition, publish the corresponding `InvoiceEvent` record: `publisher.publishEvent(new InvoiceEvent.Approved(...))`, etc. Also inject and publish audit events: `invoice.approved`, `invoice.sent`, `invoice.voided`. Note: `invoice.paid` audit event will be added in 84A when payment recording is implemented. Pattern: follow `budget/BudgetCheckService.java` for `ApplicationEventPublisher` usage. |
| 83.10 | Add notification and audit integration tests | 83B | | `invoice/InvoiceNotificationIntegrationTest.java` (~6 tests): approve creates notification for creator (if creator != approver), send creates notifications for admins/owners, void creates notifications for creator + approver + admins/owners, notification type constants registered, notification preferences include invoice types, audit events recorded for approve/send/void. Seed: provision tenant, sync 3 members (admin, owner, lead), create and approve invoice. Pattern: follow existing notification integration test patterns from Phase 6.5. |

### Key Files

**Slice 83A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLifecycleService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLifecycleIntegrationTest.java`

**Slice 83A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` — Add lifecycle endpoints

**Slice 83A — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — From 82A
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNumberService.java` — From 81B
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/budget/BudgetService.java` — Event-driven pattern

**Slice 83B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNotificationHandler.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNotificationIntegrationTest.java`

**Slice 83B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLifecycleService.java` — Add event publishing
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` — Add type constants

**Slice 83B — Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java` — Event listener pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/` — Domain event patterns from Phase 6.5
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberRepository.java` — Query admins/owners

### Architecture Decisions

- **Two-slice decomposition**: 83A is the pure lifecycle state machine (transitions + time entry marking). 83B is the cross-cutting concerns (events, notifications, audit). This separation means 83A can be tested independently of the notification pipeline.
- **Approve + markSent + void in one service, payment deferred**: Payment recording depends on the PSP adapter (Epic 84), so the `recordPayment()` transition is implemented there, not here. The lifecycle service handles the three transitions that don't need external integration.
- **Bulk time entry marking**: For small invoices (<50 entries), iterating and calling domain methods is fine. For larger invoices, a JPQL bulk update is more efficient. The builder should assess which approach fits. Either way, the test validates the outcome.
- **Notification handler as separate class**: `InvoiceNotificationHandler` is a separate `@Component`, not merged into `InvoiceLifecycleService`. This keeps lifecycle logic decoupled from notification routing and is consistent with the Phase 6.5 pattern.

---

## Epic 84: PSP Adapter & HTML Invoice Preview

**Goal**: Implement the PaymentProvider strategy interface with a MockPaymentProvider, integrate payment recording into the invoice lifecycle, and create the self-contained HTML invoice preview using Thymeleaf.

**References**: Architecture doc Sections 11.6.1-11.6.3, 11.4.2 (payment endpoint), 11.4.5 (preview endpoint). [ADR-051](../adr/ADR-051-psp-adapter-design.md).

**Dependencies**: Epic 83 (InvoiceLifecycleService)

**Scope**: Backend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **84A** | 84.1-84.8 | PaymentProvider interface, MockPaymentProvider, payment recording, invoice preview template, preview controller, integration tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 84.1 | Create PaymentProvider interface and records | 84A | | `invoice/payment/PaymentProvider.java` — interface with `PaymentResult recordPayment(PaymentRequest request)`. `invoice/payment/PaymentRequest.java` — record `(UUID invoiceId, BigDecimal amount, String currency, String description)`. `invoice/payment/PaymentResult.java` — record with `boolean success`, `String paymentReference`, `String errorMessage`, static factory methods `success(String ref)`, `failure(String error)`. Pattern: standard strategy interface. See ADR-051. |
| 84.2 | Create MockPaymentProvider | 84A | | `invoice/payment/MockPaymentProvider.java` — `@Component`, `@ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true)`. Always returns `PaymentResult.success("MOCK-PAY-" + UUID.randomUUID().toString().substring(0, 8))`. Logs payment details at INFO level. Pattern: follow `@ConditionalOnProperty` patterns in Spring Boot configuration. |
| 84.3 | Add payment.provider config to application.yml | 84A | | Modify `backend/src/main/resources/application.yml` — add `payment.provider: mock` under root config. This is the default; future Stripe integration would set `payment.provider: stripe`. |
| 84.4 | Add recordPayment to InvoiceLifecycleService | 84A | | Modify `invoice/InvoiceLifecycleService.java` — inject `PaymentProvider`. Add `recordPayment(UUID invoiceId, String paymentReference)` method: validates status==SENT, calls `paymentProvider.recordPayment(new PaymentRequest(invoiceId, invoice.getTotal(), invoice.getCurrency(), "Invoice " + invoice.getInvoiceNumber()))`, if success: calls `invoice.recordPayment(result.paymentReference)`, publishes `InvoiceEvent.Paid(...)`, audit event `invoice.paid`. If failure: throw appropriate exception with PSP error. |
| 84.5 | Add payment endpoint to InvoiceController | 84A | | Add to `invoice/InvoiceController.java`: `POST /{id}/payment` with request body `RecordPaymentRequest(String paymentReference)` (optional reference). Returns updated `InvoiceResponse`. Permission: admin/owner only. Pattern: follow existing lifecycle endpoints from 83.2. |
| 84.6 | Create Thymeleaf invoice preview template | 84A | | `backend/src/main/resources/templates/invoice-preview.html` — self-contained HTML with all CSS inline. Sections: Header (org name, invoice number, issue/due dates, status badge), Bill To (customer name, email, address), Line Items table grouped by project (columns: Description, Qty, Rate, Amount) with per-project subtotals, Other Items section (manual lines without project), Totals section (Subtotal, Tax, Total), Footer (payment terms, notes). `@media print` rules to hide status badge, ensure clean page breaks. Monochrome-friendly. No external fonts or `<link>` tags. Table layout with `border-collapse: collapse`. Pattern: follow existing Thymeleaf templates in `backend/src/main/resources/templates/portal/` from Phase 7. |
| 84.7 | Create InvoicePreviewController | 84A | | `invoice/InvoicePreviewController.java` — `@Controller` (not `@RestController`). Endpoint: `GET /api/invoices/{id}/preview` — loads invoice + lines, groups lines by project, populates Thymeleaf model attributes, returns template name `"invoice-preview"`. Response content type: `text/html`. Permission: admin/owner full access, lead if contains their project lines. Uses Spring MVC model and `ModelAndView`. Pattern: follow Thymeleaf controller patterns from Phase 7 dev harness. |
| 84.8 | Add PSP and preview integration tests | 84A | | `invoice/InvoicePaymentIntegrationTest.java` (~6 tests): record payment on SENT invoice (happy path, status becomes PAID, paidAt set, paymentReference from mock PSP), record payment on non-SENT returns 409, record payment creates audit event, PAID is terminal (further transitions return 409), invoice preview returns HTML content type, preview contains invoice number/customer name/line items in response body. Also 1 unit test for MockPaymentProvider: always returns success with MOCK-PAY prefix. Pattern: follow `invoice/InvoiceLifecycleIntegrationTest.java` from 83.4. For HTML assertions, use `mockMvc.perform(get(...)).andExpect(content().contentType("text/html"))`. |

### Key Files

**Slice 84A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/payment/PaymentProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/payment/PaymentRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/payment/PaymentResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/payment/MockPaymentProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoicePreviewController.java`
- `backend/src/main/resources/templates/invoice-preview.html`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoicePaymentIntegrationTest.java`

**Slice 84A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLifecycleService.java` — Add payment recording
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` — Add payment endpoint
- `backend/src/main/resources/application.yml` — Add `payment.provider: mock`

**Slice 84A — Read for context:**
- `backend/src/main/resources/templates/portal/` — Existing Thymeleaf templates for pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/dev/` — Phase 7 dev harness Thymeleaf controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLifecycleService.java` — From 83A

### Architecture Decisions

- **Single slice**: PSP adapter + preview is a small epic (8 tasks, ~10 files). Splitting further would create artificially small slices with tight coupling between them.
- **`payment/` sub-package**: The `PaymentProvider`, `PaymentRequest`, `PaymentResult`, and `MockPaymentProvider` live in `invoice/payment/` to keep the payment abstraction cleanly separated from invoice domain logic.
- **`@Controller` for preview, not `@RestController`**: The preview endpoint returns rendered HTML via Thymeleaf, not JSON. Using `@Controller` with Spring MVC model attributes is the standard Thymeleaf approach.
- **Mock PSP with `matchIfMissing = true`**: The mock is the default. No configuration needed for development or testing. Future Stripe implementation would set `payment.provider: stripe` and the mock would not be loaded.
- **Payment reference passed through**: The user can provide an optional `paymentReference` in the request body (e.g., a wire transfer reference). If not provided, the mock PSP generates one. Real PSP integrations would return their own reference.

---

## Epic 85: Invoice Frontend — List, Detail & Generation Flow

**Goal**: Build the complete invoice frontend: invoice list page with summary cards and filters, invoice detail page (draft editing + read-only views for all states), invoice generation flow from customer unbilled time, customer detail invoices tab, and sidebar navigation.

**References**: Architecture doc Sections 11.8.1-11.8.4.

**Dependencies**: Epics 82, 83, 84 (all backend invoice APIs)

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **85A** | 85.1-85.7 | Invoice list page, route, sidebar nav, summary cards, status badge, data table, filters, server actions | |
| **85B** | 85.8-85.14 | Invoice detail page, draft edit form, read-only views, lifecycle action buttons, line item table, preview integration | |
| **85C** | 85.15-85.20 | Invoice generation flow (unbilled time review, entry selection, draft creation), customer detail invoices tab | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 85.1 | Add "Invoices" sidebar nav item | 85A | | Modify `lib/nav-items.ts` — add "Invoices" nav item with `Receipt` icon from lucide-react, path `/org/[slug]/invoices`. Position after "Profitability" in the sidebar. Pattern: follow existing nav item structure for Profitability. |
| 85.2 | Create invoice server actions (list) | 85A | | `app/(app)/org/[slug]/invoices/actions.ts` — `"use server"` actions: `getInvoices(filters)` calls `GET /api/invoices`, `getInvoiceSummary()` calls `GET /api/invoices` with status filters to compute outstanding/overdue/paid-this-month totals. Uses `apiClient` from `lib/api.ts` with Bearer JWT. Pattern: follow `app/(app)/org/[slug]/projects/actions.ts`. |
| 85.3 | Create InvoiceStatusBadge component | 85A | | `components/invoices/InvoiceStatusBadge.tsx` — renders Badge with variant mapping: `draft` -> neutral, `approved` -> indigo, `sent` -> olive, `paid` -> success, `void` -> destructive. Uses Badge component from `components/ui/badge`. Pattern: follow `components/billing/PlanBadge.tsx` for status-to-variant mapping. |
| 85.4 | Create InvoiceSummaryCards component | 85A | | `components/invoices/InvoiceSummaryCards.tsx` — three summary cards: Outstanding (total of APPROVED + SENT), Overdue (APPROVED + SENT past due date), Paid This Month. Each card shows currency-formatted total. Uses Card component. Pattern: follow `components/dashboard/` summary card patterns. |
| 85.5 | Create invoice list data table | 85A | | `components/invoices/InvoiceTable.tsx` — columns: Invoice # (or "Draft" for drafts), Customer, Status (InvoiceStatusBadge), Issue Date, Due Date, Total, Currency. Row click navigates to `/invoices/{id}`. Pagination. Pattern: follow existing DataTable patterns in projects list page. |
| 85.6 | Create invoice list page | 85A | | `app/(app)/org/[slug]/invoices/page.tsx` — RSC page. Fetches invoice list + summary data. Renders: InvoiceSummaryCards, filter bar (status multi-select dropdown, customer dropdown, date range), InvoiceTable, "New Invoice" button (links to customer selection or dedicated generation page). Pattern: follow `app/(app)/org/[slug]/projects/page.tsx` for list page structure. |
| 85.7 | Add invoice list page tests | 85A | | `__tests__/invoices/invoice-list.test.tsx` (~5 tests): renders invoice table with data, displays summary cards with correct totals, status badges render correct variants, empty state when no invoices, filter controls present. Pattern: follow existing frontend test patterns in `__tests__/`. Use `afterEach(() => cleanup())` for Radix cleanup. |
| 85.8 | Create invoice detail server actions | 85B | | `app/(app)/org/[slug]/invoices/[id]/actions.ts` — `"use server"` actions: `getInvoice(id)`, `updateInvoice(id, data)`, `deleteInvoice(id)`, `approveInvoice(id)`, `sendInvoice(id)`, `recordPayment(id, paymentReference)`, `voidInvoice(id)`, `addInvoiceLine(id, data)`, `updateInvoiceLine(id, lineId, data)`, `removeInvoiceLine(id, lineId)`. Each calls the corresponding API endpoint with Bearer JWT. Pattern: follow `app/(app)/org/[slug]/projects/[id]/actions.ts`. |
| 85.9 | Create InvoiceLineTable component | 85B | | `components/invoices/InvoiceLineTable.tsx` — `"use client"`. Table of line items grouped by project. Columns: Description, Qty, Rate, Amount. Per-project subtotals. For draft mode: edit/delete icons per row, "Add Manual Item" button at bottom. For read-only mode: no action buttons. Props: `lines`, `editable`, `onAddLine`, `onEditLine`, `onRemoveLine`. Pattern: follow existing editable table patterns. |
| 85.10 | Create InvoiceForm component (draft editing) | 85B | | `components/invoices/InvoiceForm.tsx` — `"use client"`. Editable fields: Due Date (date picker), Payment Terms (text input), Notes (textarea), Tax Amount (currency input). Running totals (Subtotal, Tax, Total) recomputed when tax changes. Save button calls `updateInvoice` action. Uses controlled form state. Pattern: follow existing form components in `components/projects/`. |
| 85.11 | Create InvoiceActions component (lifecycle buttons) | 85B | | `components/invoices/InvoiceActions.tsx` — `"use client"`. Renders action buttons based on current status and user role: Draft -> "Preview" + "Approve" (admin/owner) + "Delete" (admin/owner or creator). Approved -> "Preview" + "Mark as Sent" + "Void" (both admin/owner only). Sent -> "Preview" + "Record Payment" + "Void" (admin/owner). Paid -> "Preview" only. Void -> "Preview" only. Approve/void/payment open confirmation dialogs (AlertDialog). "Record Payment" dialog has optional payment reference field. Pattern: follow `components/projects/` action button patterns, `components/ui/alert-dialog` for confirmations. |
| 85.12 | Create invoice detail page | 85B | | `app/(app)/org/[slug]/invoices/[id]/page.tsx` — RSC page. Fetches invoice by ID. Renders: header (invoice number or "Draft", status badge, dates), InvoiceForm (if draft) or read-only display, InvoiceLineTable, InvoiceActions, payment details (if PAID: reference + date). Void state shows "VOID" indicator. Pattern: follow `app/(app)/org/[slug]/projects/[id]/page.tsx` for detail page structure. |
| 85.13 | Add preview button integration | 85B | | In `InvoiceActions.tsx` — "Preview" button opens `GET /api/invoices/{id}/preview` in a new browser tab (`window.open(previewUrl, '_blank')`). The preview URL must include the auth token or use a server-side proxy. Option A: Server action fetches HTML and returns it (complex). Option B: New Next.js API route `/api/invoice-preview/[id]` that proxies the backend request with auth token. Pattern: follow API proxy patterns in `lib/api.ts`. |
| 85.14 | Add invoice detail page tests | 85B | | `__tests__/invoices/invoice-detail.test.tsx` (~6 tests): renders draft in edit mode, renders approved in read-only mode, renders paid with payment details, renders void with void indicator, action buttons match status + role, line item table displays correctly. Pattern: follow existing detail page test patterns. Use `afterEach(() => cleanup())`. |
| 85.15 | Create UnbilledTimeReview component | 85C | | `components/invoices/UnbilledTimeReview.tsx` — `"use client"`. Multi-step invoice generation flow: Step 1: Date range picker + currency dropdown (default from org settings). Step 2: Fetches unbilled time via `getUnbilledTime(customerId, from, to)` action. Displays entries grouped by project with checkboxes. Entries with non-matching currency are grayed out. Running total at bottom. Step 3: "Create Draft" button calls `createInvoice` action with selected entry IDs. Pattern: follow existing multi-step dialog/form patterns. |
| 85.16 | Create invoice generation server actions | 85C | | Add to `app/(app)/org/[slug]/invoices/actions.ts` — `getUnbilledTime(customerId, from?, to?)` calls `GET /api/customers/{customerId}/unbilled-time`. `createInvoice(data)` calls `POST /api/invoices`. Pattern: follow existing server action patterns. |
| 85.17 | Add "Invoices" tab to customer detail page | 85C | | Modify `app/(app)/org/[slug]/customers/[id]/page.tsx` — add "Invoices" tab alongside existing tabs. Tab content: filtered invoice list (same InvoiceTable component, filtered by customerId) + "New Invoice" button that opens the generation flow. Pattern: follow existing tab patterns in customer detail page (e.g., projects tab, documents tab). |
| 85.18 | Create CustomerInvoiceTab component | 85C | | `components/invoices/CustomerInvoiceTab.tsx` — displays invoices for a specific customer with "New Invoice" button. Uses InvoiceTable with customerId filter. "New Invoice" button opens the UnbilledTimeReview flow for this customer. Pattern: follow existing tab components in `components/customers/`. |
| 85.19 | Wire invoice generation flow from customer detail | 85C | | In `CustomerInvoiceTab.tsx` — "New Invoice" button triggers `UnbilledTimeReview` (either as a Dialog or inline). On successful draft creation, redirects to `/invoices/{newInvoiceId}` detail page. Pattern: follow existing dialog-to-redirect patterns in the codebase. |
| 85.20 | Add invoice generation flow tests | 85C | | `__tests__/invoices/invoice-generation.test.tsx` (~5 tests): renders unbilled time grouped by project, currency filter grays out mismatched entries, running total updates on selection, create draft calls correct action with selected IDs, customer invoices tab renders invoice list. Pattern: follow existing frontend test patterns. |

### Key Files

**Slice 85A — Create:**
- `frontend/app/(app)/org/[slug]/invoices/page.tsx`
- `frontend/app/(app)/org/[slug]/invoices/actions.ts`
- `frontend/components/invoices/InvoiceStatusBadge.tsx`
- `frontend/components/invoices/InvoiceSummaryCards.tsx`
- `frontend/components/invoices/InvoiceTable.tsx`
- `frontend/__tests__/invoices/invoice-list.test.tsx`

**Slice 85A — Modify:**
- `frontend/lib/nav-items.ts` — Add "Invoices" nav item

**Slice 85A — Read for context:**
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — List page pattern
- `frontend/components/billing/PlanBadge.tsx` — Badge variant pattern
- `frontend/components/dashboard/` — Summary card patterns
- `frontend/lib/api.ts` — API client for server actions

**Slice 85B — Create:**
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/invoices/[id]/actions.ts`
- `frontend/components/invoices/InvoiceLineTable.tsx`
- `frontend/components/invoices/InvoiceForm.tsx`
- `frontend/components/invoices/InvoiceActions.tsx`
- `frontend/__tests__/invoices/invoice-detail.test.tsx`

**Slice 85B — Read for context:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Detail page pattern
- `frontend/app/(app)/org/[slug]/projects/[id]/actions.ts` — Server action pattern
- `frontend/components/ui/alert-dialog.tsx` — Confirmation dialog pattern

**Slice 85C — Create:**
- `frontend/components/invoices/UnbilledTimeReview.tsx`
- `frontend/components/invoices/CustomerInvoiceTab.tsx`
- `frontend/__tests__/invoices/invoice-generation.test.tsx`

**Slice 85C — Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Add "Invoices" tab
- `frontend/app/(app)/org/[slug]/invoices/actions.ts` — Add unbilled time + create actions

**Slice 85C — Read for context:**
- `frontend/components/customers/` — Existing customer detail tab patterns
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` — Tab structure to extend

### Architecture Decisions

- **Three-slice decomposition**: 85A is the list page (entry point, independent of detail). 85B is the detail page (edit + read-only, depends on list route existing). 85C is the generation flow (depends on list page for navigation). Each slice touches 6-8 files.
- **Sidebar nav in first slice**: Adding the nav item in 85A means the route is navigable as soon as the first slice lands, even if detail/generation aren't ready yet.
- **Preview via new tab**: Opening the backend HTML preview in a new tab is the simplest approach. A Next.js API route proxy handles auth token forwarding. No iframe needed.
- **Customer invoices tab in 85C**: The customer detail modification is grouped with the generation flow (both are customer-scoped) rather than with the list page (which is org-scoped). This keeps 85A self-contained.
- **UnbilledTimeReview as "use client"**: The multi-step flow with checkboxes, running totals, and conditional rendering requires client-side state. This is a natural `"use client"` component.

---

## Epic 86: Time Entry Billing Status Frontend

**Goal**: Add billing status indicators to the existing time entry list UI: status badges ("Billed"/"Unbilled"), invoice number links, billing status filter dropdown, and lock indicators on billed entries that prevent editing/deletion.

**References**: Architecture doc Section 11.8.5.

**Dependencies**: Epic 82 (billing status filter API, time entry response field extensions)

**Scope**: Frontend

**Estimated Effort**: S

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **86A** | 86.1-86.6 | Billing status badge, invoice link, filter dropdown, lock indicator, 409 error handling, tests | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 86.1 | Create BillingStatusBadge component | 86A | | `components/invoices/BillingStatusBadge.tsx` — small inline badge for time entry rows. Variants: "Billed" (with invoice number as link to `/invoices/{invoiceId}`), "Unbilled" (for billable entries without invoice), no badge for non-billable entries. Props: `billable: boolean`, `invoiceId?: string`, `invoiceNumber?: string`. Uses Badge component with appropriate variants. Pattern: follow `components/invoices/InvoiceStatusBadge.tsx` from 85A. |
| 86.2 | Add billing status filter to time entry list | 86A | | Modify time entry list component(s) in `components/tasks/` or `components/` — add a dropdown/select filter: All / Unbilled / Billed / Non-billable. Filter value passed to the server action which forwards as `billingStatus` query parameter to the backend. Pattern: follow existing filter dropdowns in time entry or project list components. |
| 86.3 | Add lock indicator on billed entries | 86A | | Modify time entry list row component — if `isLocked` (or `invoiceId` is present): show lock icon (Lock from lucide-react), disable edit/delete action buttons, tooltip "This entry is billed on invoice {invoiceNumber}". Pattern: follow existing conditional action button rendering in time entry components. |
| 86.4 | Handle 409 Conflict for billed entry edits | 86A | | Modify time entry edit/delete action handlers — if server returns 409, show toast notification with message "Cannot modify: this entry is billed on invoice {invoiceNumber}". Do not show the edit dialog for locked entries (prevent at UI level), but handle 409 as a fallback for race conditions. Pattern: follow existing error handling patterns in server actions. |
| 86.5 | Update time entry response types | 86A | | Modify time entry TypeScript types (in actions or lib files) — add `invoiceId?: string`, `invoiceNumber?: string`, `isLocked?: boolean` fields to the time entry response interface. Ensure backward compatibility (all new fields optional). Pattern: follow existing TypeScript interface extension patterns. |
| 86.6 | Add billing status frontend tests | 86A | | `__tests__/invoices/time-entry-billing.test.tsx` (~6 tests): BillingStatusBadge renders "Billed" with invoice link, renders "Unbilled" for unbilled billable entries, renders nothing for non-billable, lock icon shown on billed entries, edit/delete buttons disabled for locked entries, filter dropdown renders all options. Pattern: follow existing time entry test patterns. Use `afterEach(() => cleanup())`. |

### Key Files

**Slice 86A — Create:**
- `frontend/components/invoices/BillingStatusBadge.tsx`
- `frontend/__tests__/invoices/time-entry-billing.test.tsx`

**Slice 86A — Modify:**
- `frontend/components/tasks/` — Time entry list component (add badge, lock icon, filter)
- `frontend/app/(app)/org/[slug]/projects/[id]/actions.ts` — Add `billingStatus` parameter to time entry queries
- Time entry TypeScript types — Add invoice-related fields

**Slice 86A — Read for context:**
- `frontend/components/tasks/` — Existing time entry list components
- `frontend/components/invoices/InvoiceStatusBadge.tsx` — Badge pattern from 85A (or create independently with same pattern)
- `frontend/components/ui/badge.tsx` — Badge variants

### Architecture Decisions

- **Single slice**: This epic is small and cohesive (6 tasks, ~6-7 files modified/created). Splitting would create unnecessary overhead.
- **BillingStatusBadge in invoices/ directory**: Even though it's used on time entry rows, it's an invoice-related concept. Keeping it in `components/invoices/` maintains domain grouping.
- **Parallel with Epic 85**: This epic depends only on Epic 82 (the backend billing status API), not on Epic 85 (the invoice frontend). It can run in parallel with Epics 83, 84, and 85.
- **Defensive 409 handling**: The UI prevents edits on locked entries, but the 409 handler is a safety net for race conditions (entry gets billed between page load and edit attempt). This is consistent with the architecture doc's guidance.
