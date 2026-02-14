# Phase 10 — Invoicing & Billing from Time

Phase 10 adds the **revenue capture layer** to the DocTeams platform — the bridge between tracked billable work and formal invoices. Billable time becomes invoiceable line items, invoices follow a controlled lifecycle (Draft → Approved → Sent → Paid), and a clean payment service provider (PSP) seam is established for future Stripe integration.

The design introduces two new entities (`Invoice`, `InvoiceLine`), extends `TimeEntry` with an `invoice_id` column for double-billing prevention, and adds a tenant-scoped counter table for gap-free invoice numbering. The invoice preview is rendered server-side as self-contained HTML, explicitly structured for future PDF conversion without template changes.

**Architecture doc**: `architecture/phase10-invoicing-billing.md`

**ADRs**: [ADR-048](../adr/ADR-048-invoice-numbering-strategy.md) (invoice numbering), [ADR-049](../adr/ADR-049-line-item-granularity.md) (line item granularity), [ADR-050](../adr/ADR-050-double-billing-prevention.md) (double-billing prevention), [ADR-051](../adr/ADR-051-psp-adapter-design.md) (PSP adapter design)

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 81 | Invoice Entity Foundation & Migration | Backend | -- | M | 81A, 81B | |
| 82 | Invoice CRUD & Lifecycle Backend | Backend | 81 | L | 82A, 82B | |
| 83 | Unbilled Time & Invoice Generation | Both | 82 | L | 83A, 83B | |
| 84 | Invoice Detail & List Pages | Frontend | 83 | M | 84A | |
| 85 | Audit, Notification & HTML Preview | Both | 82 | L | 85A, 85B, 85C | |
| 86 | Time Entry Billing UX | Both | 81A, 82 | M | 86A, 86B | |

## Dependency Graph

```
[E81A V23 Migration] ──► [E81B InvoiceLine + Numbering] ──► [E82A Invoice CRUD] ──► [E82B Invoice Lifecycle]
     (Backend)                    (Backend)                       (Backend)               (Backend)
                                                                                              │
                                                                              ┌───────────────┼───────────────┐
                                                                              ▼               ▼               ▼
                                                                      [E83A Unbilled     [E85A Audit +    [E86A Time
                                                                       Time BE]           Events]          Billing BE]
                                                                       (Backend)          (Backend)        (Backend)
                                                                          │                  │               │
                                                                          ▼                  ▼               ▼
                                                                      [E83B Invoice     [E85B Preview    [E86B Time
                                                                       Gen FE]          BE]              Billing FE]
                                                                       (Frontend)        (Backend)        (Frontend)
                                                                          │                  │
                                                                          ▼                  ▼
                                                                      [E84A Invoice     [E85C Preview
                                                                       Pages]            FE]
                                                                       (Frontend)        (Frontend)
```

**Parallel tracks**:
- Epic 81A and 81B are sequential (81B depends on 81A migration).
- Epic 82A and 82B are sequential (82B depends on 82A core service).
- After Epic 82B: Epic 83A, 85A, and 86A can all run in parallel (all are backend, independent features).
- Epic 83B (frontend invoice generation) depends on 83A (needs backend APIs).
- Epic 84A (invoice pages) depends on 83B (generation flow needed for context).
- Epic 85B (preview backend) depends on 85A (domain events), and 85C (preview frontend) depends on 85B.
- Epic 86B (time billing frontend) depends on 86A (backend billing status API).

## Implementation Order

### Stage 1: Foundation — Migration & Entity Setup

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 81 | 81A | V23 migration (invoices, invoice_lines, invoice_counters tables, time_entries ALTER, RLS policies, indexes). Invoice entity with TenantAware, @FilterDef/@Filter, InvoiceStatus enum, domain mutation methods, InvoiceRepository with findOneById (JPQL). Integration tests for entity persistence and tenant isolation (~6 tests). Foundation for all invoice work. |
| 1b | Epic 81 | 81B | InvoiceLine entity with TenantAware, @FilterDef/@Filter, InvoiceLineRepository. InvoiceNumberService (counter-based sequential numbering via SELECT ... FOR UPDATE). Integration tests for line items and numbering (~6 tests). Depends on 81A. Completes the entity layer. |

### Stage 2: Invoice Lifecycle Backend

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 82 | 82A | InvoiceService core methods — createDraft, updateDraft, deleteDraft, findById, findAll with filters. InvoiceController CRUD endpoints (GET list, GET detail, POST create, PUT update, DELETE). PaymentProvider interface + MockPaymentProvider. Basic RBAC checks (admin/owner/creator). Integration tests for CRUD operations and creator permissions (~8 tests). Depends on 81B. Establishes the service layer. |
| 2b | Epic 82 | 82B | InvoiceService lifecycle methods — approve (with InvoiceNumberService), send, recordPayment (with PaymentProvider), void (with time entry revert). State machine validation (409 for invalid transitions). Time entry edit/delete locking (409 when invoiceId set). InvoiceController lifecycle endpoints (POST /approve, /send, /payment, /void). Integration tests for all transitions, double-billing prevention, and edit locking (~10 tests). Depends on 82A. Completes the invoice backend. |

### Stage 3: Supporting Backend Features (Parallel)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 83 | 83A | CustomerController unbilled time endpoint (GET /api/customers/{id}/unbilled-time). InvoiceService unbilled time query method (joins tasks, projects, customer_projects, filters billable=true AND invoice_id IS NULL). UnbilledTimeResponse DTO with project grouping and currency totals. Invoice generation logic (POST /api/invoices from time entry IDs). Integration tests for unbilled queries and generation (~8 tests). Depends on 82B. Enables invoice creation. |
| 3b | Epic 85 | 85A | Invoice domain events (InvoiceApprovedEvent, InvoiceSentEvent, InvoicePaidEvent, InvoiceVoidedEvent). Update DomainEvent sealed interface permits clause. NotificationService handler methods (INVOICE_APPROVED, INVOICE_SENT, INVOICE_PAID, INVOICE_VOIDED). NotificationEventHandler registration. AuditService integration (invoice.created, .updated, .approved, .sent, .paid, .voided, .deleted). Integration tests for event publication and notification delivery (~8 tests). Depends on 82B. Independent of 83A and 86A — can run in parallel. |
| 3c | Epic 86 | 86A | TimeEntryService billing status filter (billingStatus query param: UNBILLED, BILLED, NON_BILLABLE). TimeEntryController query enhancement. TimeEntryResponse includes invoiceId and invoiceNumber (resolved via join or separate query). Integration tests for billing status filtering and response enrichment (~6 tests). Depends on 81A (invoice_id column exists) and 82B (lifecycle populates invoice_id). Independent of 83A and 85A — can run in parallel. |

### Stage 4: Frontend Invoice Flows (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 83 | 83B | InvoiceGenerationDialog (3-step: date range + currency → select entries → create draft). CustomerInvoicesTab (new tab on customer detail page). API client functions for unbilled time and invoice creation. Frontend tests for generation dialog flow (~8 tests). Depends on 83A (unbilled time API). |
| 4b | Epic 84 | 84A | Invoice detail page (/invoices/{id}) with draft edit mode and read-only modes. Invoice list page (/invoices) with summary cards, filters, and data table. Sidebar nav "Invoices" item. InvoiceLineTable component (editable for drafts). StatusBadge component. API client functions for CRUD and lifecycle transitions. Frontend tests for detail page modes, line editing, and lifecycle buttons (~10 tests). Depends on 83B (generation flow provides context for detail page). |

### Stage 5: Preview & Final UX (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 5a | Epic 85 | 85B | Thymeleaf invoice preview template (invoice-preview.html). InvoiceController preview endpoint (GET /api/invoices/{id}/preview) with HTML rendering. Thymeleaf dependency in pom.xml. Template includes header, bill-to, line items grouped by project, totals, footer. Print/PDF-ready inline CSS. Integration tests for preview rendering and data binding (~5 tests). Depends on 85A (domain events for approval flow). |
| 5b | Epic 85 | 85C | Invoice detail page preview button (opens preview in new tab). Preview integration in lifecycle flow (available for APPROVED+ statuses). Frontend tests for preview button and new tab behavior (~3 tests). Depends on 85B (preview endpoint must exist). |
| 5c | Epic 86 | 86B | Billing status badges on time entry rows (Billed/Unbilled/Non-billable). BillingStatusFilter dropdown on time entry list. Edit/delete disabled state for billed entries with tooltip. Frontend tests for badge display, filter behavior, and disabled state (~6 tests). Depends on 86A (backend billing status API). Can proceed in parallel with 85B/85C. |

### Timeline

```
Stage 1:  [81A] ──► [81B]                                        ← foundation (sequential)
Stage 2:  [82A] ──► [82B]                                        ← invoice backend (sequential)
Stage 3:  [83A]  //  [85A]  //  [86A]                            ← supporting backend (parallel)
Stage 4:  [83B] ──► [84A]                                        ← frontend flows (sequential)
Stage 5:  [85B] ──► [85C]  //  [86B]                             ← preview + billing UX (parallel)
```

---

## Epic 81: Invoice Entity Foundation & Migration

**Goal**: Establish the invoice data model foundation via V23 migration (invoices, invoice_lines, invoice_counters tables, time_entries ALTER, RLS policies, indexes). Create Invoice and InvoiceLine entities with full tenant isolation, InvoiceNumberService for gap-free sequential numbering, and repositories with JPQL-based findOneById (to respect Hibernate @Filter).

**References**: Architecture doc Sections 11.2 (domain model), 11.10 (migrations), 11.11.3 (entity pattern), 11.11.4 (repository pattern). [ADR-048](../adr/ADR-048-invoice-numbering-strategy.md), [ADR-049](../adr/ADR-049-line-item-granularity.md).

**Dependencies**: None (new tables and entities)

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **81A** | 81.1-81.7 | V23 migration (invoices, invoice_lines, invoice_counters, time_entries ALTER, RLS, indexes), Invoice entity with InvoiceStatus enum, InvoiceRepository with findOneById, integration tests (~6 tests) | **Done** (PR #167) |
| **81B** | 81.8-81.13 | InvoiceLine entity, InvoiceLineRepository, InvoiceNumberService (SELECT ... FOR UPDATE counter), integration tests for line items and numbering (~6 tests) | **Done** (PR #168) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 81.1 | Create V23 migration file | 81A | | `db/migration/tenant/V23__create_invoices.sql`. Create invoices table (22 columns per Section 11.2.1), invoice_lines table (11 columns per Section 11.2.2), invoice_counters table (3 columns per Section 11.2.4), ALTER time_entries ADD COLUMN invoice_id UUID REFERENCES invoices(id). Unique indexes for invoice_number (per-schema for Pro, composite tenant_id+invoice_number for Starter). Partial unique index on invoice_lines(time_entry_id) WHERE time_entry_id IS NOT NULL for double-billing prevention. Indexes per Section 11.10.2. RLS policies per Section 11.10.1. Pattern: follow V19 (rate tables) for multi-table migration with RLS. |
| 81.2 | Create InvoiceStatus enum | 81A | | `invoice/InvoiceStatus.java`. Enum with five values: DRAFT, APPROVED, SENT, PAID, VOID. No additional methods needed (simple enum). Pattern: standard Java enum. |
| 81.3 | Create Invoice entity | 81A | | `invoice/Invoice.java`. @Entity, @Table(name = "invoices"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 22 fields per Section 11.2.1. Protected no-arg constructor. Public constructor taking (customerId, currency, customerName, customerEmail, customerAddress, orgName, createdBy) — sets status=DRAFT, subtotal/taxAmount/total=0, createdAt=now(). Domain methods: updateDraft(dueDate, notes, paymentTerms, taxAmount), recalculateTotals(subtotal), approve(invoiceNumber, approvedBy), markSent(), recordPayment(paymentReference), voidInvoice(). Each method validates current status and throws IllegalStateException for invalid transitions. Pattern: follow Entity pattern in Section 11.11.3 exactly. Use BigDecimal for all monetary fields, LocalDate for dates, Instant for timestamps. |
| 81.4 | Create InvoiceRepository | 81A | | `invoice/InvoiceRepository.java`. JpaRepository<Invoice, UUID>. Add JPQL-based findOneById: `@Query("SELECT i FROM Invoice i WHERE i.id = :id") Optional<Invoice> findOneById(@Param("id") UUID id)`. Add JPQL query: `List<Invoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId)`. Add JPQL query: `List<Invoice> findByStatusOrderByCreatedAtDesc(String status)`. Pattern: follow existing repository conventions. JPQL queries respect Hibernate @Filter. |
| 81.5 | Add Invoice entity integration tests | 81A | | `invoice/InvoiceIntegrationTest.java` (~6 tests): (1) save and retrieve invoice in dedicated schema, (2) save and retrieve in tenant_shared with tenant filter active, (3) findOneById respects @Filter (cross-tenant access returns empty), (4) approve() transition sets invoiceNumber and approvedBy, (5) voidInvoice() from APPROVED sets status=VOID, (6) updateDraft() throws IllegalStateException when status is APPROVED. Seed: provision tenant, create customer, create invoice. Pattern: follow `customer/CustomerIntegrationTest.java` for entity persistence tests with tenant provisioning. |
| 81.6 | Add InvoiceCounter entity (optional) | 81A | | `invoice/InvoiceCounter.java`. @Entity, @Table(name = "invoice_counters"). Fields: id UUID, tenantId String, nextNumber Integer. No TenantAware interface needed (counters are accessed within tenant-bound transactions, RLS handles isolation). Pattern: minimal entity, no domain methods, used only by InvoiceNumberService. Alternative: skip entity and use native SQL directly in InvoiceNumberService. |
| 81.7 | Update TimeEntry entity with invoiceId column | 81A | | Modify `timeentry/TimeEntry.java`. Add field: `@Column(name = "invoice_id") private UUID invoiceId`. Add getter/setter. No other changes (lifecycle logic comes in Epic 82B). Pattern: minimal field addition. Migration already added column in 81.1. |
| 81.8 | Create InvoiceLine entity | 81B | | `invoice/InvoiceLine.java`. @Entity, @Table(name = "invoice_lines"), TenantAware, @FilterDef/@Filter, @EntityListeners(TenantAwareEntityListener.class). 11 fields per Section 11.2.2. Protected no-arg constructor. Public constructor taking (invoiceId, projectId, timeEntryId, description, quantity, unitPrice, sortOrder) — computes amount = quantity * unitPrice, sets createdAt=now(). Domain method: recalculateAmount() updates amount field from quantity * unitPrice. Pattern: follow Invoice entity pattern. Use BigDecimal for quantity (DECIMAL(10,4)), unitPrice (DECIMAL(12,2)), amount (DECIMAL(14,2)). |
| 81.9 | Create InvoiceLineRepository | 81B | | `invoice/InvoiceLineRepository.java`. JpaRepository<InvoiceLine, UUID>. Add JPQL query: `List<InvoiceLine> findByInvoiceIdOrderBySortOrder(UUID invoiceId)`. Add JPQL query: `Optional<InvoiceLine> findByTimeEntryId(UUID timeEntryId)`. Pattern: follow InvoiceRepository pattern. |
| 81.10 | Create InvoiceNumberService | 81B | | `invoice/InvoiceNumberService.java`. @Service. Method: `String assignNumber(String tenantId)`. Implementation per ADR-048: INSERT INTO invoice_counters ON CONFLICT DO UPDATE with SELECT ... FOR UPDATE semantics, format result as "INV-" + String.format("%04d", nextNumber). Pattern: follow MemberSyncService for ScopedValue.where().call() with TransactionTemplate. |
| 81.11 | Add InvoiceNumberService integration tests | 81B | | `invoice/InvoiceNumberServiceTest.java` (~4 tests): (1) first call returns "INV-0001", (2) second call returns "INV-0002", (3) concurrent calls (2 threads) produce distinct numbers, (4) different tenants have independent sequences. Pattern: integration test with CountDownLatch for concurrency. |
| 81.12 | Add InvoiceLine integration tests | 81B | | `invoice/InvoiceLineIntegrationTest.java` (~3 tests): (1) save and retrieve line item with invoiceId FK, (2) findByInvoiceIdOrderBySortOrder returns lines in correct order, (3) double-billing prevention: save line with timeEntryId, attempt to save second line with same timeEntryId throws unique constraint violation. Pattern: follow InvoiceIntegrationTest pattern. |
| 81.13 | Verify V23 migration runs cleanly | 81B | | Run `./mvnw clean test` and verify no migration errors. Check that invoices, invoice_lines, invoice_counters tables are created in both dedicated schemas and tenant_shared. Verify RLS policies are applied. |

### Key Files

**Slice 81A — Create:**
- `backend/src/main/resources/db/migration/tenant/V23__create_invoices.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceRepository.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceIntegrationTest.java`

**Slice 81A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntry.java` — Add invoiceId field

**Slice 81A — Read for context:**
- `architecture/phase10-invoicing-billing.md` Sections 11.2.1, 11.10.1, 11.11.3
- `backend/src/main/resources/db/migration/tenant/V19__create_rate_budget_tables.sql` — Multi-table migration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/Customer.java` — Entity pattern reference

**Slice 81B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLineRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNumberService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceNumberServiceTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLineIntegrationTest.java`

**Slice 81B — Read for context:**
- `architecture/phase10-invoicing-billing.md` Sections 11.2.2, 11.2.4
- `adr/ADR-048-invoice-numbering-strategy.md` — Counter-based numbering design
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/member/MemberSyncService.java` — ScopedValue.where().call() pattern

### Architecture Decisions

- **invoice/ package**: New feature package containing all Phase 10 backend code (entities, repositories, service, controller, DTOs). Follows the feature-per-package convention established in Phase 4+.
- **InvoiceStatus enum**: Simple enum, no complexity needed. Used as string in database (VARCHAR(20)).
- **Invoice domain methods**: State transitions encapsulated in entity methods (approve, markSent, recordPayment, voidInvoice) with IllegalStateException for invalid transitions. Service calls domain methods rather than directly mutating status fields.
- **JPQL findOneById**: Necessary because JpaRepository.findById() bypasses Hibernate @Filter. Critical for shared-schema multitenancy.
- **V23 migration RLS policies**: All new tables get RLS policies following the established pattern from V13+ migrations.

---

## Epic 82: Invoice CRUD & Lifecycle Backend

**Goal**: Build the complete invoice backend service layer and REST API — CRUD operations (create draft, update draft, delete draft, find by ID, list with filters), lifecycle transitions (approve, send, record payment, void), state machine validation, time entry billing lock, PaymentProvider interface with mock implementation, and comprehensive RBAC enforcement.

**References**: Architecture doc Sections 11.3 (flows), 11.4 (API surface), 11.7 (PSP integration), 11.12 (RBAC table). [ADR-050](../adr/ADR-050-double-billing-prevention.md), [ADR-051](../adr/ADR-051-psp-adapter-design.md).

**Dependencies**: Epic 81 (Invoice and InvoiceLine entities, InvoiceNumberService)

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **82A** | 82.1-82.10 | InvoiceService core CRUD methods, InvoiceController CRUD endpoints, PaymentProvider interface + MockPaymentProvider, DTO records, basic RBAC (admin/owner/creator), integration tests for CRUD operations and permissions (~8 tests) | **Done** (PR #169) |
| **82B** | 82.11-82.19 | InvoiceService lifecycle methods (approve, send, recordPayment, void), state machine validation, time entry edit/delete locking, InvoiceController lifecycle endpoints, double-billing prevention checks, integration tests for transitions and locking (~10 tests) | **Done** (PR #170) |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 82.1 | Create invoice DTO records | 82A | | `invoice/dto/InvoiceResponse.java` — record matching Invoice entity fields + List<InvoiceLineResponse> lines. `invoice/dto/InvoiceLineResponse.java` — record matching InvoiceLine fields + String projectName, String taskTitle. `invoice/dto/CreateInvoiceRequest.java` — record with UUID customerId, String currency, List<UUID> timeEntryIds, LocalDate dueDate, String notes, String paymentTerms. `invoice/dto/UpdateInvoiceRequest.java` — record with LocalDate dueDate, String notes, String paymentTerms, BigDecimal taxAmount. Pattern: follow Phase 8 DTOs. Records only, immutable. |
| 82.2 | Create PaymentProvider interface | 82A | | `invoice/PaymentProvider.java`. Interface with method: `PaymentResult recordPayment(PaymentRequest request)`. Pattern: single-method interface per ADR-051. |
| 82.3 | Create payment-related records | 82A | | `invoice/PaymentRequest.java` — record with UUID invoiceId, BigDecimal amount, String currency, String description. `invoice/PaymentResult.java` — record with boolean success, String paymentReference, String errorMessage. Pattern: simple data transfer records. |
| 82.4 | Create MockPaymentProvider | 82A | | `invoice/MockPaymentProvider.java`. @Component, @ConditionalOnProperty(name = "payment.provider", havingValue = "mock", matchIfMissing = true). recordPayment() generates reference `"MOCK-PAY-" + UUID.randomUUID().toString().substring(0, 8)`, logs at INFO level, returns success. Pattern: follow ADR-051. |
| 82.5 | Add payment.provider config property | 82A | | Modify `application.yml`. Add `payment.provider: mock` under root level. Pattern: simple YAML property addition. |
| 82.6 | Create InvoiceService core methods | 82A | | `invoice/InvoiceService.java`. @Service. Inject: InvoiceRepository, InvoiceLineRepository, CustomerRepository, ProjectRepository, TimeEntryRepository. Implement: createDraft(CreateInvoiceRequest, UUID createdBy) — validate customer, time entries, currency; snapshot customer/org details; create Invoice + InvoiceLine per time entry; compute subtotal. updateDraft(UUID invoiceId, UpdateInvoiceRequest) — validate DRAFT status, update fields. deleteDraft(UUID invoiceId) — validate DRAFT, delete (cascade). findById(UUID invoiceId) — enrich with lines and project names. Pattern: follow CustomerService.java for service structure. Use @Transactional. |
| 82.7 | Create InvoiceController CRUD endpoints | 82A | | `invoice/InvoiceController.java`. @RestController, @RequestMapping("/api/invoices"). POST /api/invoices (201 Created), PUT /api/invoices/{id} (200 OK), DELETE /api/invoices/{id} (204 No Content), GET /api/invoices/{id} (200 OK), GET /api/invoices (paginated list with filters: customerId, projectId, status, from, to). Extract tenantId, memberId, orgRole from RequestScopes. Pattern: follow CustomerController.java. |
| 82.8 | Add invoice line item CRUD endpoints | 82A | | Add to InvoiceController: POST /api/invoices/{id}/lines, PUT /api/invoices/{id}/lines/{lineId}, DELETE /api/invoices/{id}/lines/{lineId}. Create AddLineItemRequest and UpdateLineItemRequest DTOs. Validates invoice status=DRAFT. Recomputes subtotal/total after each change. Pattern: nested resource endpoints. |
| 82.9 | Add InvoiceService list methods | 82A | | Add findAll() with filters (customerId, projectId, status, date range). For non-admin/non-owner, filter invoices to those with lines from accessible projects. Pattern: follow paginated list methods in ProjectService. |
| 82.10 | Add InvoiceService CRUD integration tests | 82A | | `invoice/InvoiceServiceIntegrationTest.java` (~8 tests): (1) createDraft with valid time entries, (2) createDraft validates customer exists, (3) createDraft rejects already-billed entries, (4) createDraft rejects currency mismatch, (5) updateDraft updates fields and recomputes totals, (6) updateDraft throws on non-DRAFT, (7) deleteDraft removes invoice and cascades, (8) findById returns enriched line details. Pattern: follow CustomerServiceIntegrationTest.java. |
| 82.11 | Implement approve() in InvoiceService | 82B | | Add method: approve(UUID invoiceId, UUID approvedBy). Validates DRAFT status, has lines, all time entries still unbilled, calls InvoiceNumberService.assignNumber(), sets invoice_id on time entries. @Transactional. Pattern: multi-step transactional operation. See Section 11.3.2. |
| 82.12 | Implement send() in InvoiceService | 82B | | Add method: send(UUID invoiceId). Validates APPROVED status, calls invoice.markSent(). Pattern: simple state transition. |
| 82.13 | Implement recordPayment() in InvoiceService | 82B | | Add method: recordPayment(UUID invoiceId, String paymentReference). Inject PaymentProvider. Validates SENT status, calls PaymentProvider.recordPayment(), sets payment details. Pattern: delegate to provider interface per ADR-051. |
| 82.14 | Implement void() in InvoiceService | 82B | | Add method: voidInvoice(UUID invoiceId). Validates APPROVED or SENT status, sets VOID, clears invoice_id on time entries. @Transactional. Pattern: state transition + related entity update in single transaction. See Section 11.3.3. |
| 82.15 | Add lifecycle transition endpoints | 82B | | Add to InvoiceController: POST /api/invoices/{id}/approve, POST /api/invoices/{id}/send, POST /api/invoices/{id}/payment (body: RecordPaymentRequest), POST /api/invoices/{id}/void. All require admin/owner. Pattern: POST for state transitions. |
| 82.16 | Add time entry edit/delete locking | 82B | | Modify `timeentry/TimeEntryService.java`. In updateTimeEntry() and deleteTimeEntry(), add check: if invoiceId != null, throw IllegalStateException mapped to 409 Conflict with message referencing the invoice number. Pattern: guard clause. |
| 82.17 | Add state machine validation | 82B | | Invoice entity domain methods validate current status and throw IllegalStateException for invalid transitions. GlobalExceptionHandler maps to 409 Conflict. Pattern: domain-driven validation. See Section 11.3.1. |
| 82.18 | Add InvoiceService lifecycle integration tests | 82B | | `invoice/InvoiceLifecycleIntegrationTest.java` (~10 tests): (1) approve assigns number and sets time entry invoice_id, (2) approve fails if no lines, (3) approve fails if not DRAFT, (4) send transitions APPROVED → SENT, (5) send fails if not APPROVED, (6) recordPayment transitions SENT → PAID, (7) recordPayment fails if not SENT, (8) void from APPROVED sets VOID and clears time entry invoice_id, (9) void from SENT works, (10) void fails if PAID. |
| 82.19 | Add double-billing prevention integration test | 82B | | Add to InvoiceLifecycleIntegrationTest: create draft A and draft B with same time entry, approve A (succeeds, marks time entry), attempt to approve B (fails — time entry already billed). See ADR-050. |

### Key Files

**Slice 82A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/InvoiceResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/InvoiceLineResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/CreateInvoiceRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/UpdateInvoiceRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/PaymentProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/PaymentRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/PaymentResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/MockPaymentProvider.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceServiceIntegrationTest.java`

**Slice 82A — Modify:**
- `backend/src/main/resources/application.yml` — Add payment.provider config

**Slice 82B — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/RecordPaymentRequest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLifecycleIntegrationTest.java`

**Slice 82B — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — Add lifecycle methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` — Add lifecycle endpoints
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` — Add edit/delete locking

### Architecture Decisions

- **InvoiceService transaction boundaries**: createDraft, approve, void are @Transactional to ensure atomicity.
- **Double-billing prevention at approval**: Drafts don't mark time entries as billed (invoice_id remains null). Only approval sets invoice_id.
- **PaymentProvider as interface**: Clean seam for future Stripe integration. MockPaymentProvider is the default.
- **State machine validation in entity**: Domain methods validate current status and throw IllegalStateException.
- **Time entry locking**: Edit/delete blocked when invoice_id IS NOT NULL. 409 Conflict with clear error message.

---

## Epic 83: Unbilled Time & Invoice Generation

**Goal**: Build the unbilled time query endpoint (GET /api/customers/{customerId}/unbilled-time) and the invoice generation flow (backend API + frontend 3-step dialog). The backend returns unbilled billable time entries grouped by project with currency totals. The frontend InvoiceGenerationDialog guides users through date range selection, time entry selection, and draft creation.

**References**: Architecture doc Sections 11.3.2 (generation flow), 11.3.5 (unbilled time query), 11.4.4 (API), 11.13.1 (customer invoices tab), 11.13.3 (generation dialog).

**Dependencies**: Epic 82 (InvoiceService and InvoiceController must exist)

**Scope**: Both (Backend 83A, Frontend 83B)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **83A** | 83.1-83.6 | Unbilled time backend endpoint (GET /api/customers/{id}/unbilled-time), UnbilledTimeResponse DTO with project grouping and currency totals, native SQL query with joins, integration tests (~8 tests) | |
| **83B** | 83.7-83.14 | InvoiceGenerationDialog (3-step: date range → select entries → create draft), CustomerInvoicesTab on customer detail page, API client functions, frontend tests (~8 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 83.1 | Create UnbilledTimeResponse DTOs | 83A | | `invoice/dto/UnbilledTimeResponse.java` — record with UUID customerId, String customerName, List<UnbilledProjectGroup> projects, Map<String, CurrencyTotal> grandTotals. `invoice/dto/UnbilledProjectGroup.java` — record with UUID projectId, String projectName, List<UnbilledTimeEntry> entries, Map<String, CurrencyTotal> totals. `invoice/dto/UnbilledTimeEntry.java` — record with UUID id, String taskTitle, String memberName, LocalDate date, int durationMinutes, BigDecimal billingRateSnapshot, String billingRateCurrency, BigDecimal billableValue, String description. `invoice/dto/CurrencyTotal.java` — record with double hours, BigDecimal amount. Pattern: nested DTOs for grouped response. |
| 83.2 | Add unbilled time query to InvoiceService | 83A | | Add getUnbilledTime(UUID customerId, LocalDate from, LocalDate to, UUID memberId, String orgRole). Native SQL query joins time_entries, tasks, projects, customer_projects, members. Filters: billable=true AND invoice_id IS NULL AND optional date range. Post-process: group by projectId, compute per-currency totals and grand totals. For non-admin, filter by project access. Pattern: native SQL + post-processing. See Section 11.3.5 for SQL. |
| 83.3 | Add unbilled time endpoint to CustomerController | 83A | | Modify `customer/CustomerController.java`. Add GET /api/customers/{id}/unbilled-time?from=&to= (both optional). Pattern: nested resource endpoint. |
| 83.4 | Add unbilled time integration tests | 83A | | `invoice/UnbilledTimeIntegrationTest.java` (~5 tests): (1) returns entries grouped by project, (2) excludes entries with invoice_id set, (3) excludes non-billable entries, (4) date range filtering works, (5) project access filtering for non-admin. |
| 83.5 | Enhance createDraft validation in InvoiceService | 83A | | Add validation: all timeEntryIds belong to customer's projects via customer_projects, all billable=true, all invoice_id IS NULL, all billingRateCurrency matches request currency. Pattern: guard clauses. |
| 83.6 | Add invoice generation integration test | 83A | | End-to-end flow test: getUnbilledTime() → createDraft() → verify invoice created → approve → verify time entries marked billed. |
| 83.7 | Create invoice API client functions | 83B | | `frontend/lib/api/invoices.ts`. Functions: fetchUnbilledTime(customerId, from?, to?), createInvoiceDraft(req), fetchInvoice(invoiceId), fetchInvoices(filters). TypeScript interfaces for request/response shapes. Pattern: follow lib/api/customers.ts. |
| 83.8 | Create InvoiceGenerationDialog (step 1) | 83B | | `frontend/components/invoices/invoice-generation-dialog.tsx`. Multi-step dialog using Shadcn Dialog. Props: customerId, customerName, onSuccess. Step 1: DateRangePicker (from, to), currency Select, "Fetch Unbilled Time" button. On click: call fetchUnbilledTime(), advance to step 2. Pattern: dialog with internal step state. See Section 11.13.3. |
| 83.9 | Create InvoiceGenerationDialog (step 2) | 83B | | Step 2: Display entries grouped by project. Checkbox per entry, "Select All" per project. Currency mismatch entries disabled. Running total at bottom. "Create Draft" button calls createInvoiceDraft(). Pattern: controlled checkboxes. |
| 83.10 | Create CustomerInvoicesTab | 83B | | `frontend/components/customers/customer-invoices-tab.tsx`. Fetches invoices for customer. Data table: Invoice Number, Status (badge), Issue Date, Due Date, Total, Currency. "New Invoice" button opens InvoiceGenerationDialog. Click row navigates to /invoices/{id}. Pattern: follow CustomerDocumentsTab. |
| 83.11 | Integrate CustomerInvoicesTab into customer detail | 83B | | Modify customer detail page. Add "Invoices" tab with CustomerInvoicesTab component. Pattern: follow existing tab structure. |
| 83.12 | Add InvoiceGenerationDialog tests | 83B | | `frontend/__tests__/components/invoices/invoice-generation-dialog.test.tsx` (~5 tests): (1) renders step 1, (2) fetch advances to step 2, (3) step 2 displays grouped entries, (4) currency mismatch disabled, (5) create draft calls API. Pattern: vitest + testing-library. |
| 83.13 | Add CustomerInvoicesTab tests | 83B | | `frontend/__tests__/components/customers/customer-invoices-tab.test.tsx` (~3 tests): (1) renders table, (2) "New Invoice" opens dialog, (3) row click navigates. Pattern: component tests. |

### Key Files

**Slice 83A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/UnbilledTimeResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/UnbilledProjectGroup.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/UnbilledTimeEntry.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/CurrencyTotal.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeIntegrationTest.java`

**Slice 83A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` — Add getUnbilledTime(), enhance createDraft validation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerController.java` — Add unbilled time endpoint

**Slice 83B — Create:**
- `frontend/lib/api/invoices.ts`
- `frontend/components/invoices/invoice-generation-dialog.tsx`
- `frontend/components/customers/customer-invoices-tab.tsx`
- `frontend/__tests__/components/invoices/invoice-generation-dialog.test.tsx`
- `frontend/__tests__/components/customers/customer-invoices-tab.test.tsx`

**Slice 83B — Modify:**
- Customer detail page — Add Invoices tab

### Architecture Decisions

- **Unbilled time endpoint under CustomerController**: Logically belongs to customer resource.
- **Native SQL for unbilled time**: Multi-table join, RLS handles tenant isolation.
- **Project grouping in response**: Mirrors UI needs — frontend displays entries grouped by project.
- **3-step dialog flow**: Date range + currency → select entries → create draft.
- **Currency mismatch UX**: Entries with different currency shown but disabled.

---

## Epic 84: Invoice Detail & List Pages

**Goal**: Build the frontend invoice detail page (draft edit mode and read-only modes for APPROVED/SENT/PAID/VOID) and the invoice list page with summary cards, filters, and data table. Add sidebar navigation item for Invoices.

**References**: Architecture doc Sections 11.13.2 (detail page), 11.13.4 (list page).

**Dependencies**: Epic 83B (invoice generation flow provides context for detail page entry)

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **84A** | 84.1-84.10 | Invoice detail page (draft edit + read-only modes), invoice list page with summary cards and filters, InvoiceLineTable, StatusBadge, sidebar nav item, API client enhancements, frontend tests (~10 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 84.1 | Create StatusBadge component | 84A | | `frontend/components/invoices/status-badge.tsx`. Props: status. Colors: DRAFT (gray), APPROVED (blue), SENT (indigo), PAID (green), VOID (red). Pattern: Shadcn Badge with variant. |
| 84.2 | Create InvoiceLineTable component | 84A | | `frontend/components/invoices/invoice-line-table.tsx`. Props: lines, editable, onEdit/onDelete/onAdd. Table: Description, Project, Qty, Rate, Amount. If editable: edit/delete icons + "Add Line" button. Pattern: conditional rendering. |
| 84.3 | Add invoice API client lifecycle functions | 84A | | Modify `frontend/lib/api/invoices.ts`. Add: approveInvoice, sendInvoice, recordPayment, voidInvoice, updateInvoice, deleteInvoice, addLineItem, updateLineItem, deleteLineItem. Pattern: follow existing API client patterns. |
| 84.4 | Create invoice detail page (draft edit mode) | 84A | | `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`. If DRAFT: editable form (Due Date, Notes, Payment Terms, Tax Amount) + InvoiceLineTable editable=true. Actions: "Preview", "Delete Draft", "Approve" (admin/owner only). Running totals. Pattern: conditional rendering based on status. |
| 84.5 | Create invoice detail page (read-only modes) | 84A | | Same page, read-only for APPROVED/SENT/PAID/VOID. Status-specific action buttons per Section 11.13.2. Pattern: role-based conditional rendering. |
| 84.6 | Create invoice list page with summary cards | 84A | | `frontend/app/(app)/org/[slug]/invoices/page.tsx`. Summary cards: Total Outstanding, Total Overdue, Paid This Month. Filter bar: Status, Customer, Date range. Data table. Pattern: dashboard-style page. |
| 84.7 | Create invoice list data table | 84A | | Shadcn Table: Invoice Number, Customer, Status (badge), Issue Date, Due Date, Total, Currency. Rows clickable → /invoices/{id}. Pagination. Pattern: follow project list table. |
| 84.8 | Add "Invoices" sidebar navigation item | 84A | | Modify sidebar nav config. Label "Invoices", icon FileText or Receipt. Position after "Profitability". Pattern: follow existing nav items. |
| 84.9 | Add invoice detail page tests | 84A | | `frontend/__tests__/app/(app)/org/[slug]/invoices/[id]/page.test.tsx` (~5 tests): (1) draft renders editable fields, (2) APPROVED renders lifecycle buttons, (3) PAID renders payment details, (4) non-admin lacks admin buttons, (5) delete confirms and calls API. |
| 84.10 | Add invoice list page tests | 84A | | `frontend/__tests__/app/(app)/org/[slug]/invoices/page.test.tsx` (~5 tests): (1) summary cards, (2) table columns, (3) status filter, (4) row click navigates, (5) pagination. |

### Key Files

**Slice 84A — Create:**
- `frontend/components/invoices/status-badge.tsx`
- `frontend/components/invoices/invoice-line-table.tsx`
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/invoices/page.tsx`
- `frontend/__tests__/app/(app)/org/[slug]/invoices/[id]/page.test.tsx`
- `frontend/__tests__/app/(app)/org/[slug]/invoices/page.test.tsx`

**Slice 84A — Modify:**
- `frontend/lib/api/invoices.ts` — Add lifecycle API functions
- Sidebar nav config — Add Invoices nav item

### Architecture Decisions

- **Single detail page with conditional modes**: Avoids route complexity (no separate /edit route).
- **Summary cards computed client-side**: From fetched invoice list. Dedicated endpoint if performance concern.
- **Role-based button visibility**: Admin/owner see all lifecycle buttons. Leads see limited actions.

---

## Epic 85: Audit, Notification & HTML Preview

**Goal**: Integrate invoice lifecycle events with audit and notification systems. Create domain events, notification handlers, audit logging, and Thymeleaf HTML invoice preview with print-ready CSS.

**References**: Architecture doc Sections 11.6 (HTML preview), 11.8 (notifications), 11.9 (audit).

**Dependencies**: Epic 82 (InvoiceService lifecycle methods must publish events)

**Scope**: Both (Backend 85A and 85B, Frontend 85C)

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **85A** | 85.1-85.7 | Invoice domain events (4 records), DomainEvent sealed interface update, NotificationService handlers, NotificationEventHandler registration, AuditService integration, integration tests (~8 tests) | |
| **85B** | 85.8-85.12 | Thymeleaf invoice preview template, InvoiceController preview endpoint (GET /api/invoices/{id}/preview), Thymeleaf dependency, print/PDF-ready CSS, integration tests (~5 tests) | |
| **85C** | 85.13-85.15 | Invoice detail page preview button, preview integration, frontend tests (~3 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 85.1 | Create invoice domain event records | 85A | | `event/InvoiceApprovedEvent.java`, `event/InvoiceSentEvent.java`, `event/InvoicePaidEvent.java`, `event/InvoiceVoidedEvent.java`. All implement DomainEvent sealed interface. Fields per Section 11.8.2. InvoicePaidEvent adds paymentReference. InvoiceVoidedEvent adds approvedByMemberId. Pattern: follow existing event records (CommentCreatedEvent, etc.). |
| 85.2 | Update DomainEvent sealed interface | 85A | | Modify `event/DomainEvent.java`. Add four new event records to permits clause. |
| 85.3 | Add invoice notification types | 85A | | Modify `notification/NotificationService.java`. Add: "INVOICE_APPROVED", "INVOICE_SENT", "INVOICE_PAID", "INVOICE_VOIDED". |
| 85.4 | Add invoice notification handlers | 85A | | Modify `notification/NotificationService.java`. Add handler methods per Section 11.8.3 recipient rules. INVOICE_APPROVED → creator. INVOICE_SENT → admins/owners. INVOICE_PAID → creator + admins/owners. INVOICE_VOIDED → creator + approver + admins/owners. Respect NotificationPreference opt-out. Exclude actor. |
| 85.5 | Register invoice event handlers | 85A | | Modify `notification/NotificationEventHandler.java`. Add @EventListener methods for four invoice events. |
| 85.6 | Add audit logging to InvoiceService | 85A | | Modify `invoice/InvoiceService.java`. Inject AuditService. Add audit events per Section 11.9.1: invoice.created, .updated, .approved, .sent, .paid, .voided, .deleted. Use AuditEventBuilder. |
| 85.7 | Add notification and audit integration tests | 85A | | `notification/InvoiceNotificationIntegrationTest.java` (~5 tests) + `audit/InvoiceAuditIntegrationTest.java` (~3 tests). Test event publication, notification delivery, preference opt-out, audit event details. |
| 85.8 | Add Thymeleaf dependency to pom.xml | 85B | | Modify `backend/pom.xml`. Add spring-boot-starter-thymeleaf. |
| 85.9 | Create Thymeleaf invoice preview template | 85B | | `backend/src/main/resources/templates/invoice-preview.html`. Self-contained HTML with inline CSS per Section 11.6. Structure: header, bill-to, line items grouped by project, totals, footer. Print/PDF-ready CSS: @media print rules, inline styles, A4-compatible max-width, system font stack. |
| 85.10 | Add preview endpoint to InvoiceController | 85B | | Add GET /api/invoices/{id}/preview. Inject TemplateEngine. Load invoice + lines, group by projectId, resolve project names, render template, return text/html. Pattern: follow Phase 7 Thymeleaf rendering. |
| 85.11 | Add TemplateEngine configuration (if needed) | 85B | | Check auto-configuration. Create ThymeleafConfig if needed. Pattern: standard config. |
| 85.12 | Add invoice preview integration tests | 85B | | `invoice/InvoicePreviewIntegrationTest.java` (~5 tests): (1) returns HTML, (2) includes invoice number and customer name, (3) includes grouped line items, (4) includes totals, (5) respects project access. Pattern: MockMvc with HTML content assertions. |
| 85.13 | Add preview button to invoice detail page | 85C | | Modify invoice detail page. Add "Preview" button (all statuses). On click: window.open preview URL in new tab. Pattern: simple button. |
| 85.14 | Add preview integration in lifecycle flow | 85C | | Ensure preview button visible in all modes (draft, APPROVED, SENT, PAID, VOID). |
| 85.15 | Add preview button frontend tests | 85C | | Add ~3 tests: (1) preview button renders for DRAFT, (2) renders for APPROVED, (3) click opens new tab with correct URL. Mock window.open. |

### Key Files

**Slice 85A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InvoiceApprovedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InvoiceSentEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InvoicePaidEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/InvoiceVoidedEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/notification/InvoiceNotificationIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/audit/InvoiceAuditIntegrationTest.java`

**Slice 85A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/DomainEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationEventHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java`

**Slice 85B — Create:**
- `backend/src/main/resources/templates/invoice-preview.html`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoicePreviewIntegrationTest.java`

**Slice 85B — Modify:**
- `backend/pom.xml`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java`

**Slice 85C — Modify:**
- `frontend/app/(app)/org/[slug]/invoices/[id]/page.tsx`
- `frontend/__tests__/app/(app)/org/[slug]/invoices/[id]/page.test.tsx`

### Architecture Decisions

- **Domain events**: Four sealed interface records following Phase 6.5 pattern.
- **Notification recipients**: Role and involvement-based. All respect opt-out.
- **Audit logging**: All mutations logged with "invoice.*" event types.
- **Thymeleaf template**: Self-contained HTML, inline CSS, print/PDF-ready. No external dependencies.
- **Preview endpoint**: Returns HTML directly. Frontend opens in new tab.

---

## Epic 86: Time Entry Billing UX

**Goal**: Enhance time entry list with billing status indicators. Backend filtering by billing status, response enrichment with invoiceId/invoiceNumber. Frontend badges, filter dropdown, and disabled edit/delete for billed entries.

**References**: Architecture doc Sections 11.2.3 (TimeEntry changes), 11.13.5 (time entry list enhancements).

**Dependencies**: Epic 81A (time_entries.invoice_id column), Epic 82B (invoice lifecycle populates invoice_id)

**Scope**: Both (Backend 86A, Frontend 86B)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **86A** | 86.1-86.4 | TimeEntryService billing status filter, TimeEntryController query enhancement, TimeEntryResponse enrichment (invoiceId, invoiceNumber), integration tests (~6 tests) | |
| **86B** | 86.5-86.10 | BillingStatusBadge, BillingStatusFilter dropdown, edit/delete disabled state for billed entries, frontend tests (~6 tests) | |

### Tasks

| ID | Task | Slice | Status | Notes |
|----|------|-------|--------|-------|
| 86.1 | Add billing status filter to TimeEntryService | 86A | | Modify `timeentry/TimeEntryService.java`. Add BillingStatus enum (UNBILLED, BILLED, NON_BILLABLE) in `timeentry/BillingStatus.java`. Add optional billingStatus parameter to list methods. UNBILLED: billable=true AND invoiceId IS NULL. BILLED: invoiceId IS NOT NULL. NON_BILLABLE: billable=false. |
| 86.2 | Enrich TimeEntryResponse with invoice fields | 86A | | Modify TimeEntryResponse (or DTO). Add invoiceId (UUID) and invoiceNumber (String). Resolve invoiceNumber via InvoiceRepository.findOneById() or LEFT JOIN. |
| 86.3 | Add billing status query param to TimeEntryController | 86A | | Modify `timeentry/TimeEntryController.java`. Add @RequestParam(required = false) billingStatus. |
| 86.4 | Add billing status integration tests | 86A | | `timeentry/TimeEntryBillingIntegrationTest.java` (~6 tests): (1) UNBILLED filter, (2) BILLED filter, (3) NON_BILLABLE filter, (4) response includes invoiceId/invoiceNumber, (5) response has null for unbilled, (6) edit fails with 409 for billed entries. |
| 86.5 | Create BillingStatusBadge component | 86B | | `frontend/components/time-entries/billing-status-badge.tsx`. If invoiceId set: green Badge "Billed" with link to invoice. If billable and no invoiceId: gray "Unbilled". If not billable: no badge. Pattern: Shadcn Badge + Next.js Link. |
| 86.6 | Create BillingStatusFilter component | 86B | | `frontend/components/time-entries/billing-status-filter.tsx`. Dropdown: All, Unbilled, Billed, Non-billable. Pattern: Shadcn Select. |
| 86.7 | Integrate badges and filter into time entry list | 86B | | Modify time entry list component. Add BillingStatusFilter to filter bar. Add BillingStatusBadge to table rows. |
| 86.8 | Add edit/delete disabled state for billed entries | 86B | | Disable edit/delete buttons when invoiceId != null. Tooltip: "Time entry is part of invoice {invoiceNumber}. Void the invoice to unlock." Pattern: Shadcn Tooltip. |
| 86.9 | Add billing status frontend tests | 86B | | `frontend/__tests__/components/time-entries/billing-status-badge.test.tsx` (~3 tests) + `frontend/__tests__/components/time-entries/billing-status-filter.test.tsx` (~2 tests). |
| 86.10 | Add disabled state test | 86B | | Test billed entry has disabled edit/delete with tooltip (~1 test). |

### Key Files

**Slice 86A — Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/BillingStatus.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryBillingIntegrationTest.java`

**Slice 86A — Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java`
- TimeEntryResponse DTO
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryController.java`

**Slice 86B — Create:**
- `frontend/components/time-entries/billing-status-badge.tsx`
- `frontend/components/time-entries/billing-status-filter.tsx`
- `frontend/__tests__/components/time-entries/billing-status-badge.test.tsx`
- `frontend/__tests__/components/time-entries/billing-status-filter.test.tsx`

**Slice 86B — Modify:**
- Time entry list component(s) — Add badge column and filter

### Architecture Decisions

- **BillingStatus enum**: Three values map to three query predicates. Simple and explicit.
- **Response enrichment**: invoiceId + invoiceNumber in response avoids separate API call.
- **Badge with link**: "Billed" links to invoice detail page for quick navigation.
- **Double-layer protection**: Backend 409 + frontend disabled buttons for billed entries.
