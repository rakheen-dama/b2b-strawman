# Phase 17 — Retainer Agreements & Billing

Phase 17 adds a **retainer management layer** to the DocTeams platform -- per-customer retainer agreements with hour bank tracking, period-based billing cycles, rollover policies, and automated draft invoice generation at period close. Retainers are a commercial coordination layer that ties together existing infrastructure: time entries (Phase 5), billing rate hierarchy (Phase 8), and the invoice system (Phase 10). Two new entities (`RetainerAgreement`, `RetainerPeriod`), one column extension on `InvoiceLine`, and frontend pages for retainer management and customer integration.

**Architecture doc**: `architecture/phase17-retainer-agreements-billing.md`

**ADRs**:
- [ADR-072](../adr/ADR-072-admin-triggered-period-close.md) -- Admin-Triggered Period Close
- [ADR-073](../adr/ADR-073-standard-billing-rate-for-overage.md) -- Standard Billing Rate for Overage
- [ADR-074](../adr/ADR-074-query-based-consumption.md) -- Query-Based Consumption
- [ADR-075](../adr/ADR-075-one-active-retainer-per-customer.md) -- One Active Retainer Per Customer

**MIGRATION**: `V31__create_retainer_tables.sql` -- 2 tables + 1 ALTER on `invoice_lines`.

**Dependencies on prior phases**: Phase 4 (Customer, CustomerProject), Phase 5 (TimeEntry), Phase 6 (AuditService, AuditEventBuilder), Phase 6.5 (ApplicationEvent, NotificationService), Phase 8 (BillingRate, BillingRateService, OrgSettings), Phase 10 (Invoice, InvoiceLine, InvoiceService), Phase 13 (dedicated schema -- no tenant_id), Phase 16 (RecurringSchedule -- optional FK).

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 122 | Entity Foundation & Migration | Backend | -- | M | 122A, 122B | **Done** (PRs #247, #248) |
| 123 | Retainer CRUD & Lifecycle | Backend | 122 | M | 123A, 123B | **Done** (PRs #249, #250) |
| 124 | Consumption Tracking & Summary | Backend | 123 | M | 124A, 124B | |
| 125 | Period Close & Invoice Generation | Backend | 124 | L | 125A, 125B | |
| 126 | Retainer Dashboard & Create UI | Frontend | 123, 124, 125 | M | 126A, 126B | |
| 127 | Customer Retainer Tab & Detail Page | Frontend | 126 | M | 127A, 127B | |
| 128 | Time Entry Indicators & Notifications | Both | 124, 127 | M | 128A, 128B | |

---

## Dependency Graph

```
[E122A Migration + Entities + Enums]
            |
[E122B Repositories + Entity Tests]
            |
     +------+------+
     |             |
     v             v
[E123A Agreement  (continues sequentially)
 Service + DTOs]
     |
[E123B Agreement
 Controller + Tests]
     |
[E124A Consumption
 Listener + Event]
     |
[E124B Summary
 Controller + Tests]
     |
[E125A Period Close
 Service + Rollover]
     |
[E125B Period
 Controller + Tests]
     |
     +------+------+---------+
     |             |         |
     v             v         v
[E126A Dashboard  [E127A    [E128A
 Page + List +     Customer  Time Entry
 API Client]       Tab]      Indicator]
     |             |         |
[E126B Create +   [E127B    [E128B
 Status Badges]    Detail +  Notification
                   Close     Wiring]
                   Dialog]
```

**Parallel opportunities**:
- After Epic 125 completes: Epics 126, 127 (partially), and 128 are somewhat independent frontend tracks -- but 127 depends on shared components from 126.
- Epic 128A (frontend indicators) can start after 126A provides the API client.
- Epic 128B (notification wiring, backend) can run in parallel with frontend work.

---

## Implementation Order

### Stage 1: Foundation (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 1a | Epic 122 | 122A | V31 migration, 2 JPA entities, 6 enums, InvoiceLine extension. Foundation for everything else. | **Done** (PR #247) |
| 1b | Epic 122 | 122B | 2 repositories + `RetainerFrequency.calculateNextEnd()` utility + entity-level tests. | **Done** (PR #248) |

### Stage 2: Backend Domain Logic (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 2a | Epic 123 | 123A | `RetainerAgreementService` -- create (with first period), update, lifecycle transitions. DTOs. Audit events. ~12 service tests. | **Done** (PR #249) |
| 2b | Epic 123 | 123B | `RetainerAgreementController` -- 7 REST endpoints, permission checks, SecurityConfig. ~12 controller tests. | **Done** (PR #250) |
| 2c | Epic 124 | 124A | `TimeEntryChangedEvent`, `RetainerConsumptionListener`, consumption query, threshold logic. Modify `TimeEntryService`. ~10 integration tests. |
| 2d | Epic 124 | 124B | `RetainerSummaryController` + `RetainerSummaryResponse`. Customer retainer summary endpoint. ~5 controller tests. |

### Stage 3: Period Close (Sequential)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 3a | Epic 125 | 125A | `RetainerPeriodService.closePeriod()` -- overage calculation, rollover logic (all 3 policies), invoice generation via InvoiceService, rate resolution via BillingRateService, next period creation, auto-termination. ~15 service tests. |
| 3b | Epic 125 | 125B | `RetainerPeriodController` -- period list, current period, close endpoint. Audit events for close. ~8 controller tests. |

### Stage 4: Frontend (Parallel tracks after Stage 3)

| Order | Epic | Slice | Rationale |
|-------|------|-------|-----------|
| 4a | Epic 126 | 126A | Retainer dashboard page, retainer list, API client, sidebar nav. ~8 tests. |
| 4b | Epic 126 | 126B | Create retainer dialog, status badges, progress bar component. ~6 tests. |
| 4c | Epic 127 | 127A | Customer retainer tab, active retainer card, period history table. ~5 tests. Parallel with 126. |
| 4d | Epic 127 | 127B | Retainer detail page, edit dialog, close period dialog, lifecycle action buttons. ~6 tests. |
| 4e | Epic 128 | 128A | Retainer indicator on time entry form, overage warning. ~4 frontend tests. Parallel with 127. |
| 4f | Epic 128 | 128B | Backend notification wiring: RETAINER_PERIOD_READY_TO_CLOSE trigger, ensure all notification types are dispatched. ~5 backend tests. Parallel with frontend slices. |

### Timeline

```
Stage 1:  [122A] --> [122B]
Stage 2:  [123A] --> [123B] --> [124A] --> [124B]
Stage 3:  [125A] --> [125B]
Stage 4:  [126A] --> [126B]  //  [127A] --> [127B]  //  [128A]  //  [128B]
```

**Critical path**: 122A -> 122B -> 123A -> 123B -> 124A -> 124B -> 125A -> 125B -> 126A -> 126B
**Parallel savings**: Stage 4 has up to four parallel tracks.

---

## Epic 122: Entity Foundation & Migration

**Goal**: Create the V31 database migration, both JPA entities (`RetainerAgreement`, `RetainerPeriod`), all 6 enums, the `InvoiceLine` extension (`retainerPeriodId` field), repositories, and the `RetainerFrequency.calculateNextEnd()` utility method. This epic lays the data foundation -- no service logic, no controllers.

**References**: Architecture doc Sections 17.2 (Domain Model), 17.7 (Migration), 17.8.3 (Entity Code Pattern), 17.8.4 (Repository Pattern). [ADR-075](../adr/ADR-075-one-active-retainer-per-customer.md).

**Dependencies**: None (foundation epic).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **122A** | 122.1--122.9 | V31 migration (2 tables + 1 ALTER), `RetainerAgreement` entity, `RetainerPeriod` entity, 6 enums (`RetainerType`, `RetainerStatus`, `RetainerFrequency`, `RolloverPolicy`, `PeriodStatus`), `InvoiceLine` modification (add `retainerPeriodId`). ~9 files created/modified. | **Done** (PR #247) |
| **122B** | 122.10--122.14 | `RetainerAgreementRepository`, `RetainerPeriodRepository`, `RetainerFrequency.calculateNextEnd()` period date utility, entity-level tests (constructor, lifecycle methods, validation), migration verification test. ~10 tests. | **Done** (PR #248) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 122.1 | Create V31 migration | 122A | | `backend/src/main/resources/db/migration/tenant/V31__create_retainer_tables.sql`. Exact SQL from architecture doc Section 17.7. 2 tables: `retainer_agreements`, `retainer_periods`. ALTER `invoice_lines` add `retainer_period_id`. 6 indexes, 4 CHECK constraints, 1 UNIQUE constraint. Copy SQL verbatim from architecture doc. |
| 122.2 | Create `RetainerType` enum | 122A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerType.java`. Values: `HOUR_BANK`, `FIXED_FEE`. Simple enum. |
| 122.3 | Create `RetainerStatus` enum | 122A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerStatus.java`. Values: `ACTIVE`, `PAUSED`, `TERMINATED`. Simple enum. |
| 122.4 | Create `RetainerFrequency` enum | 122A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerFrequency.java`. Values: `WEEKLY`, `FORTNIGHTLY`, `MONTHLY`, `QUARTERLY`, `SEMI_ANNUALLY`, `ANNUALLY`. Include `calculateNextEnd(LocalDate start)` method per architecture doc Section 17.3.1 period date calculation table. |
| 122.5 | Create `RolloverPolicy` enum | 122A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RolloverPolicy.java`. Values: `FORFEIT`, `CARRY_FORWARD`, `CARRY_CAPPED`. Simple enum. |
| 122.6 | Create `PeriodStatus` enum | 122A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/PeriodStatus.java`. Values: `OPEN`, `CLOSED`. Simple enum. |
| 122.7 | Create `RetainerAgreement` entity | 122A | 122.1--122.6 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreement.java`. All fields per architecture doc Section 17.2.1. Include `pause()`, `resume()`, `terminate()`, `updateTerms()` lifecycle methods with state validation. Constructor sets `status = ACTIVE`, `createdAt = Instant.now()`. `@Entity @Table(name = "retainer_agreements")`. Pattern: follow `Invoice.java` for entity with lifecycle status. Exact code in architecture doc Section 17.8.3. |
| 122.8 | Create `RetainerPeriod` entity | 122A | 122.1--122.6 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriod.java`. All fields per architecture doc Section 17.2.2. Include `updateConsumption(BigDecimal consumedHours)` method that recalculates `remainingHours = MAX(0, allocatedHours - consumedHours)`. Include `close(UUID invoiceId, UUID closedBy, BigDecimal overageHours, BigDecimal rolloverHoursOut)` method. Constructor for initial period creation. Pattern: follow `RetainerAgreement` entity structure. |
| 122.9 | Modify `InvoiceLine` entity | 122A | 122.1 | Add `retainerPeriodId` (UUID, nullable) field to `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java`. `@Column(name = "retainer_period_id")`. Add getter. |
| 122.10 | Create `RetainerAgreementRepository` | 122B | 122.7 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreementRepository.java`. `JpaRepository<RetainerAgreement, UUID>`. Methods: `findByStatus()`, `findByCustomerId()`, `findActiveOrPausedByCustomerId()` (JPQL: status IN ACTIVE, PAUSED), `findByCustomerIdAndStatus()`. Exact code in architecture doc Section 17.8.4. |
| 122.11 | Create `RetainerPeriodRepository` | 122B | 122.8 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodRepository.java`. `JpaRepository<RetainerPeriod, UUID>`. Methods: `findByAgreementIdAndStatus()`, `findByAgreementIdOrderByPeriodStartDesc()` (paginated), `findPeriodsReadyToClose()` (JPQL: status = OPEN AND periodEnd <= CURRENT_DATE). Exact code in architecture doc Section 17.8.4. |
| 122.12 | Write `RetainerFrequency` unit tests | 122B | 122.4 | `backend/src/test/java/.../retainer/RetainerFrequencyTest.java`. Tests: all 6 frequencies with `calculateNextEnd()`. Edge cases: month-end (Jan 31 + 1 month = Feb 28), leap year, year boundary. ~8 unit tests. |
| 122.13 | Write entity lifecycle unit tests | 122B | 122.7, 122.8 | `backend/src/test/java/.../retainer/RetainerAgreementEntityTest.java`. Tests: create agreement (status = ACTIVE), pause from ACTIVE, pause from non-ACTIVE (throws), resume from PAUSED, resume from non-PAUSED (throws), terminate from ACTIVE, terminate from PAUSED, terminate already terminated (throws), updateTerms sets fields. `RetainerPeriod` tests: updateConsumption recalculates remainingHours, close sets fields. ~10 unit tests. |
| 122.14 | Write V31 migration verification test | 122B | 122.1, 122.10--122.11 | `backend/src/test/java/.../retainer/V31MigrationTest.java`. Verify migration applies cleanly. Save a `RetainerAgreement`, save a `RetainerPeriod`, verify FK constraints, verify unique constraint on `(agreement_id, period_start)`, verify `retainer_period_id` on `InvoiceLine` is nullable. ~4 integration tests. |

### Key Files

**Slice 122A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V31__create_retainer_tables.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreement.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriod.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerType.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerFrequency.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RolloverPolicy.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/PeriodStatus.java`

**Slice 122A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java` -- Add `retainerPeriodId` field

**Slice 122B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreementRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodRepository.java`
- `backend/src/test/java/.../retainer/RetainerFrequencyTest.java`
- `backend/src/test/java/.../retainer/RetainerAgreementEntityTest.java`
- `backend/src/test/java/.../retainer/V31MigrationTest.java`

**Read for context:**
- `architecture/phase17-retainer-agreements-billing.md` Sections 17.2, 17.7, 17.8.3, 17.8.4
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` -- Entity with lifecycle status pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java` -- Entity to extend
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/schedule/RecurringSchedule.java` -- Entity with frequency pattern
- `backend/src/main/resources/db/migration/tenant/V30__project_templates_recurring_schedules.sql` -- Migration format reference
- `backend/CLAUDE.md` -- Conventions

### Architecture Decisions

- **Package `retainer/`**: New top-level feature package following existing convention (e.g., `invoice/`, `schedule/`). NOT nested under `customer/` because retainers are a cross-cutting commercial concept, not a customer sub-entity.
- **Enums as `@Enumerated(EnumType.STRING)`**: Following existing codebase pattern. Database uses VARCHAR with CHECK constraints for safety. Service layer validates.
- **UUID FK columns, not `@ManyToOne`**: `RetainerAgreement.customerId` and `RetainerPeriod.agreementId` are UUID columns, not JPA relationships. Consistent with the entire codebase -- avoids lazy-loading pitfalls.
- **`calculateNextEnd()` on the enum**: Frequency-to-date logic is intrinsic to the frequency value. Placing it on the enum avoids a separate utility class and keeps it easily testable.

---

## Epic 123: Retainer CRUD & Lifecycle

**Goal**: Create the `RetainerAgreementService` with full CRUD (create with first period auto-creation, update terms, list, get) and lifecycle transitions (pause, resume, terminate). Add the `RetainerAgreementController` REST endpoints with permission checks and audit events.

**References**: Architecture doc Sections 17.3.1 (Creation), 17.3.5 (Lifecycle), 17.4.1 (Agreement Endpoints), 17.6.2 (Audit), 17.9 (Permissions).

**Dependencies**: Epic 122 (entities, enums, repositories, migration).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **123A** | 123.1--123.8 | `RetainerAgreementService` -- create (validates customer, checks no duplicate, creates first period), update terms, pause, resume, terminate. Request/response DTOs. Audit events for all operations. Service-level integration tests. ~12 tests. | **Done** (PR #249) |
| **123B** | 123.9--123.15 | `RetainerAgreementController` -- 7 REST endpoints (list, get, create, update, pause, resume, terminate). `SecurityConfig` update. Permission checks (admin/owner for write, member+ for read). Controller integration tests. ~12 tests. | **Done** (PR #250) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 123.1 | Create request/response DTOs | 123A | | `retainer/dto/` package. `CreateRetainerRequest` record (customerId, scheduleId?, name, type, frequency, startDate, endDate?, allocatedHours?, periodFee, rolloverPolicy?, rolloverCapHours?, notes?). `UpdateRetainerRequest` record (name, allocatedHours?, periodFee, rolloverPolicy?, rolloverCapHours?, endDate?, notes?). `RetainerResponse` record (all agreement fields + customerName + currentPeriod embedded). `PeriodSummary` record (id, periodStart, periodEnd, status, allocatedHours, consumedHours, remainingHours, rolloverHoursIn, readyToClose). Validation: `@NotNull`, `@NotBlank`, `@Positive` annotations. Pattern: `invoice/dto/` package. |
| 123.2 | Create `RetainerAgreementService` -- create retainer | 123A | 123.1 | `retainer/RetainerAgreementService.java`. `createRetainer(CreateRetainerRequest, UUID actorMemberId)`. Steps: (1) Load customer via `CustomerRepository`, validate lifecycle status (reject OFFBOARDED, PROSPECT), (2) Check no ACTIVE/PAUSED retainer for customer via `findActiveOrPausedByCustomerId()`, (3) Validate type-specific fields (HOUR_BANK requires allocatedHours + periodFee, CARRY_CAPPED requires rolloverCapHours), (4) Persist `RetainerAgreement`, (5) Calculate first period end via `RetainerFrequency.calculateNextEnd()`, (6) Create first `RetainerPeriod` (OPEN, allocatedHours, remainingHours, rolloverHoursIn=0), (7) Audit `RETAINER_CREATED` + `RETAINER_PERIOD_OPENED`. Return agreement + period. Pattern: `InvoiceService.createInvoice()`. |
| 123.3 | Implement `updateRetainer()` | 123A | 123.2 | In `RetainerAgreementService`. Load agreement, call `updateTerms()`. Validate CARRY_CAPPED requires cap. Audit `RETAINER_UPDATED` with old/new diff. Changes take effect on NEXT period -- do NOT modify current open period. |
| 123.4 | Implement lifecycle transitions | 123A | 123.2 | In `RetainerAgreementService`. `pauseRetainer(id, actorMemberId)` -- calls `agreement.pause()`, audits. `resumeRetainer(id, actorMemberId)` -- calls `agreement.resume()`, audits. `terminateRetainer(id, actorMemberId)` -- calls `agreement.terminate()`, audits. Each method loads agreement, delegates to entity method (which validates state), saves, publishes audit event. |
| 123.5 | Implement list and get methods | 123A | 123.2 | `listRetainers(RetainerStatus status, UUID customerId)` -- filter combinations. `getRetainer(UUID id)` -- returns agreement with current period and last 6 periods. Enrich response with `customerName` from `CustomerRepository`. |
| 123.6 | Add audit event integration | 123A | 123.2--123.4 | Use existing `AuditService.log()` and `AuditEventBuilder`. Event types: `RETAINER_CREATED`, `RETAINER_UPDATED`, `RETAINER_PAUSED`, `RETAINER_RESUMED`, `RETAINER_TERMINATED`, `RETAINER_PERIOD_OPENED`. JSONB details per architecture doc Section 17.6.2. Entity type `"RETAINER_AGREEMENT"`. Pattern: follow audit usage in `InvoiceService`. |
| 123.7 | Write service integration tests | 123A | 123.2--123.6 | `backend/src/test/java/.../retainer/RetainerAgreementServiceTest.java`. Tests: create HOUR_BANK retainer (verify first period auto-created), create FIXED_FEE retainer, create for customer with existing active retainer (409), create for OFFBOARDED customer (400), create HOUR_BANK missing allocatedHours (400), update terms, pause active retainer, pause non-active (400), resume paused, terminate active, terminate paused, get with current period. ~12 integration tests. |
| 123.8 | Verify 123A tests pass | 123A | 123.7 | Run: `./mvnw test -pl backend -Dtest="RetainerAgreementServiceTest" -q`. All ~12 tests pass. |
| 123.9 | Create `RetainerAgreementController` | 123B | 123A | `retainer/RetainerAgreementController.java`. 7 endpoints per architecture doc Section 17.4.1: `GET /api/retainers`, `GET /api/retainers/{id}`, `POST /api/retainers`, `PUT /api/retainers/{id}`, `POST /api/retainers/{id}/pause`, `POST /api/retainers/{id}/resume`, `POST /api/retainers/{id}/terminate`. Extract `memberId` from `RequestScopes.MEMBER_ID`, `orgRole` from `RequestScopes.ORG_ROLE`. Pattern: follow `InvoiceController`. |
| 123.10 | Add permission checks | 123B | 123.9 | Write endpoints (create, update, pause, resume, terminate): `@PreAuthorize("hasAnyRole('ORG_ADMIN', 'ORG_OWNER')")`. List endpoint: admin/owner only (cross-customer data). Get endpoint: `@PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")`. Pattern: follow `InvoiceController` permission annotations. |
| 123.11 | Update `SecurityConfig` | 123B | 123.9 | Add `/api/retainers/**` to authenticated endpoint list. Pattern: follow how `/api/invoices/**` is configured in `SecurityConfig.java`. |
| 123.12 | Write controller integration tests -- CRUD | 123B | 123.9--123.11 | `backend/src/test/java/.../retainer/RetainerAgreementControllerTest.java`. MockMvc tests. Tests: list retainers (200), get retainer by ID (200), get non-existent retainer (404), create HOUR_BANK retainer (201 with first period), create with missing required fields (400), update retainer (200). ~6 tests. |
| 123.13 | Write controller integration tests -- lifecycle | 123B | 123.12 | Additional tests in same file. Tests: pause active retainer (200), resume paused retainer (200), terminate retainer (200), pause non-active retainer (400). ~4 tests. |
| 123.14 | Write controller integration tests -- permissions | 123B | 123.12 | Additional tests in same file. Tests: create as member (403), list as member (403), get as member (200 -- members allowed). ~3 tests. Use `.with(jwt().authorities(...))` for different roles. |
| 123.15 | Verify all 123 tests pass | 123B | 123.12--123.14 | Run: `./mvnw test -pl backend -Dtest="RetainerAgreementServiceTest,RetainerAgreementControllerTest" -q`. All ~24 tests pass. |

### Key Files

**Slice 123A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreementService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/dto/CreateRetainerRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/dto/UpdateRetainerRequest.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/dto/RetainerResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/dto/PeriodSummary.java`
- `backend/src/test/java/.../retainer/RetainerAgreementServiceTest.java`

**Slice 123B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreementController.java`
- `backend/src/test/java/.../retainer/RetainerAgreementControllerTest.java`

**Slice 123B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` -- Add retainer endpoints

**Read for context:**
- `architecture/phase17-retainer-agreements-billing.md` Sections 17.3.1, 17.3.5, 17.4.1, 17.6.2, 17.9
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Service with audit events pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceController.java` -- CRUD controller pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerRepository.java` -- Customer lookup
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/audit/AuditService.java` -- Audit pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/config/SecurityConfig.java` -- Endpoint security config

### Architecture Decisions

- **First period auto-created on retainer creation**: When a retainer is created, the first period is immediately created in the same transaction. This ensures there is always an OPEN period to track consumption against. No separate "start retainer" action needed.
- **Update takes effect on next period**: `updateTerms()` modifies the agreement record but does NOT retroactively adjust the current open period. This avoids mid-period allocation changes that would confuse consumption tracking. The next period (created at close time) uses the updated values.
- **List endpoint admin-only**: The list endpoint shows retainers across all customers -- this is cross-customer financial data. Individual retainer GET is member-accessible so members can see hours remaining.

---

## Epic 124: Consumption Tracking & Summary

**Goal**: Implement the event-driven consumption recalculation pipeline. When a billable time entry changes for a customer with an active retainer, the open period's `consumedHours` and `remainingHours` are recalculated. Add the lightweight customer retainer summary endpoint for UI indicators.

**References**: Architecture doc Sections 17.3.2 (Consumption Tracking), 17.3.7 (Threshold Notifications), 17.4.3 (Customer Retainer Summary). [ADR-074](../adr/ADR-074-query-based-consumption.md).

**Dependencies**: Epic 123 (retainer must exist to track consumption against).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **124A** | 124.1--124.8 | `TimeEntryChangedEvent` domain event. Modify `TimeEntryService` to publish event on create/update/delete. `RetainerConsumptionListener` with consumption query, threshold notifications. Integration tests. ~10 tests. | |
| **124B** | 124.9--124.13 | `RetainerSummaryController` + `RetainerSummaryResponse` DTO. Customer retainer summary endpoint (`GET /api/customers/{customerId}/retainer-summary`). Controller integration tests. ~5 tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 124.1 | Create `TimeEntryChangedEvent` | 124A | | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TimeEntryChangedEvent.java`. Record with fields: `timeEntryId` (UUID), `projectId` (UUID), `action` (String: "CREATED", "UPDATED", "DELETED"). Follows existing event pattern (e.g., `TaskAssignedEvent`, `CommentCreatedEvent`). If `DomainEvent.java` is a sealed interface, add this to the permits list. |
| 124.2 | Modify `TimeEntryService` to publish events | 124A | 124.1 | Modify `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java`. Inject `ApplicationEventPublisher`. Publish `TimeEntryChangedEvent` after create, update, and delete operations. Publish inside the `@Transactional` boundary so the listener runs in the same transaction. Pattern: follow `CommentService` event publishing. |
| 124.3 | Create consumption query method | 124A | | Add native query to `RetainerPeriodRepository` or create in `RetainerConsumptionListener` directly. SQL per architecture doc Section 17.3.2: `SELECT COALESCE(SUM(te.duration_minutes), 0) FROM time_entries te JOIN tasks t ON t.id = te.task_id JOIN customer_projects cp ON cp.project_id = t.project_id WHERE cp.customer_id = :customerId AND te.billable = true AND te.date >= :periodStart AND te.date < :periodEnd`. Use `@Query(nativeQuery = true)` on repository or `JdbcClient`. Return `long` (total minutes). |
| 124.4 | Create `RetainerConsumptionListener` | 124A | 124.1, 124.3 | `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerConsumptionListener.java`. `@Component` with `@EventListener @Transactional` on `onTimeEntryChanged(TimeEntryChangedEvent)`. Steps: (1) Find customer for the project (via `CustomerProjectRepository`), (2) Find ACTIVE retainer for customer (via `findActiveOrPausedByCustomerId()` -- also track PAUSED retainers), (3) Find OPEN period for the retainer, (4) Run consumption query, (5) Convert minutes to hours (minutes / 60.0, scale 2, HALF_UP), (6) Call `period.updateConsumption()`, (7) Check thresholds. Exit early if no customer, no retainer, or no open period found. |
| 124.5 | Implement threshold notification logic | 124A | 124.4 | In `RetainerConsumptionListener`. After recalculation, check consumption percentage for HOUR_BANK retainers. Compare old `consumedHours` (read before update) vs new value. If old < 80% and new >= 80%: publish `RETAINER_APPROACHING_CAPACITY` notification via `NotificationService`. If old < 100% and new >= 100%: publish `RETAINER_FULLY_CONSUMED` notification. FIXED_FEE retainers skip threshold checks. Pattern: follow notification dispatch in `InvoiceService`. |
| 124.6 | Write consumption listener integration tests | 124A | 124.4, 124.5 | `backend/src/test/java/.../retainer/RetainerConsumptionListenerTest.java`. Tests: create billable time entry triggers recalculation (consumed_hours updated), update time entry triggers recalculation, delete time entry triggers recalculation, non-billable time entry does NOT trigger recalculation, time entry for project without customer -- no-op, time entry for customer without retainer -- no-op, time entry outside period date range -- consumed_hours unchanged, threshold 80% notification fires, threshold 100% notification fires, duplicate threshold does NOT re-fire. ~10 integration tests. Setup: create a customer with an HOUR_BANK retainer and linked project. |
| 124.7 | Add consumption query to `RetainerPeriodRepository` | 124A | 124.3 | If not done in 124.3 as standalone, add `@Query(nativeQuery = true) long sumConsumedMinutes(@Param("customerId") UUID customerId, @Param("periodStart") LocalDate periodStart, @Param("periodEnd") LocalDate periodEnd)` to `RetainerPeriodRepository`. Use `CAST(:periodStart AS DATE)` for nullable-safe parameters per lessons learned. |
| 124.8 | Verify 124A tests pass | 124A | 124.6 | Run: `./mvnw test -pl backend -Dtest="RetainerConsumptionListenerTest" -q`. All ~10 tests pass. |
| 124.9 | Create `RetainerSummaryResponse` DTO | 124B | | `retainer/dto/RetainerSummaryResponse.java`. Record with fields: `hasActiveRetainer` (boolean), `agreementId` (UUID, nullable), `agreementName` (String, nullable), `type` (RetainerType, nullable), `allocatedHours` (BigDecimal, nullable), `consumedHours` (BigDecimal, nullable), `remainingHours` (BigDecimal, nullable), `percentConsumed` (BigDecimal, nullable), `isOverage` (boolean). Per architecture doc Section 17.4.3. |
| 124.10 | Create `RetainerSummaryController` | 124B | 124.9 | `retainer/RetainerSummaryController.java`. Single endpoint: `GET /api/customers/{customerId}/retainer-summary`. `@PreAuthorize("hasAnyRole('ORG_MEMBER', 'ORG_ADMIN', 'ORG_OWNER')")`. Load active retainer for customer, get open period, calculate percentConsumed. If no active retainer: return `{ hasActiveRetainer: false }`. If FIXED_FEE: return without allocatedHours/remainingHours/percentConsumed. Pattern: lightweight controller, no service layer needed -- direct repo queries. |
| 124.11 | Update `SecurityConfig` for summary endpoint | 124B | 124.10 | Ensure `/api/customers/*/retainer-summary` is covered by existing customer endpoint auth. Likely already covered by `/api/customers/**`. Verify. |
| 124.12 | Write summary controller integration tests | 124B | 124.10 | `backend/src/test/java/.../retainer/RetainerSummaryControllerTest.java`. Tests: customer with active HOUR_BANK retainer (200 with all fields), customer with active FIXED_FEE retainer (200 with type-specific fields), customer with no retainer (200 with hasActiveRetainer=false), customer with terminated retainer only (200 with hasActiveRetainer=false), member can access (200). ~5 tests. |
| 124.13 | Verify all 124 tests pass | 124B | 124.8, 124.12 | Run: `./mvnw test -pl backend -Dtest="RetainerConsumptionListenerTest,RetainerSummaryControllerTest" -q`. All ~15 tests pass. |

### Key Files

**Slice 124A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/TimeEntryChangedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerConsumptionListener.java`
- `backend/src/test/java/.../retainer/RetainerConsumptionListenerTest.java`

**Slice 124A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` -- Publish `TimeEntryChangedEvent`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodRepository.java` -- Add consumption query

**Slice 124B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/dto/RetainerSummaryResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerSummaryController.java`
- `backend/src/test/java/.../retainer/RetainerSummaryControllerTest.java`

**Read for context:**
- `architecture/phase17-retainer-agreements-billing.md` Sections 17.3.2, 17.3.7, 17.4.3
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/event/CommentCreatedEvent.java` -- Event pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/comment/CommentService.java` -- Event publishing pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/timeentry/TimeEntryService.java` -- To modify for event publishing
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/customer/CustomerProjectRepository.java` -- Find customer for project
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- Threshold notification dispatch

### Architecture Decisions

- **Query-based consumption (ADR-074)**: Every time entry change triggers a full re-query of consumed minutes. Self-healing -- no incremental counter drift. The query is well-indexed and sub-millisecond.
- **Event listener in same transaction**: `@EventListener` (not `@TransactionalEventListener`) runs in the same transaction as the time entry mutation. This ensures consumption is consistent when the time entry save commits. If the listener fails, the time entry save also rolls back -- acceptable because consumption is a derived value.
- **Summary endpoint on customer path**: `GET /api/customers/{customerId}/retainer-summary` rather than `/api/retainers/...` because the frontend consumes it from the customer context (time entry form, project list). The customer ID is the natural lookup key.

---

## Epic 125: Period Close & Invoice Generation

**Goal**: Implement the complete period close flow -- finalize consumption, calculate overage (HOUR_BANK), apply rollover policy, generate DRAFT invoice via existing InvoiceService, close the period, and open the next period. Add the period REST endpoints.

**References**: Architecture doc Sections 17.3.3 (Period Close), 17.3.4 (Rollover Logic), 17.3.6 (Overage Rate Resolution), 17.4.2 (Period Endpoints), 17.5.3 (Sequence Diagram). [ADR-072](../adr/ADR-072-admin-triggered-period-close.md), [ADR-073](../adr/ADR-073-standard-billing-rate-for-overage.md).

**Dependencies**: Epic 124 (consumption data must be tracked for close to finalize). Also depends on existing `InvoiceService` (Phase 10) and `BillingRateService` (Phase 8).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **125A** | 125.1--125.9 | `RetainerPeriodService` -- `closePeriod()` with full close flow. Overage calculation for HOUR_BANK. Rollover logic (all 3 policies). Invoice generation via `InvoiceService`. Rate resolution via `BillingRateService`. Next period creation with rollover. Auto-termination at end date. Service integration tests. ~15 tests. | |
| **125B** | 125.10--125.16 | `RetainerPeriodController` -- period list, current period, close endpoint. `PeriodResponse`, `PeriodCloseResult` DTOs. Audit events for close. Period-ready-to-close query. Controller integration tests. ~8 tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 125.1 | Create `RetainerPeriodService` | 125A | | `retainer/RetainerPeriodService.java`. Main method: `closePeriod(UUID agreementId, UUID actorMemberId)`. Inject: `RetainerAgreementRepository`, `RetainerPeriodRepository`, `InvoiceService`, `BillingRateService`, `AuditService`, `NotificationService`, `ApplicationEventPublisher`. `@Transactional` on `closePeriod()`. |
| 125.2 | Implement close precondition validation | 125A | 125.1 | In `closePeriod()`. Load agreement and OPEN period. Validate: period exists, period status is OPEN, `LocalDate.now() >= period.getPeriodEnd()` (period end date has passed). Return 400 ProblemDetail if period not ready to close. Return 404 if no open period. |
| 125.3 | Implement consumption finalization | 125A | 125.2 | Re-query all billable time entries for the customer within the period date range (reuse consumption query from 124A). Write final `consumedHours`. This is the authoritative value -- even if the listener hasn't caught up, the close uses fresh data. |
| 125.4 | Implement overage calculation | 125A | 125.3 | For HOUR_BANK: `overageHours = MAX(0, consumedHours - allocatedHours)`. For FIXED_FEE: no overage calculation. Resolve overage billing rate: call `BillingRateService` for customer-level rate (new method or direct `BillingRateRepository` query for customer override, falling back to `OrgSettings.defaultBillingRate`). If HOUR_BANK with overage and no billing rate found: reject close with 400 "Cannot calculate overage -- no billing rate configured for customer [name] or org default." |
| 125.5 | Implement rollover calculation | 125A | 125.4 | For HOUR_BANK: `unusedHours = MAX(0, allocatedHours - consumedHours)`. Apply policy: FORFEIT -> `rolloverHoursOut = 0`, CARRY_FORWARD -> `rolloverHoursOut = unusedHours`, CARRY_CAPPED -> `rolloverHoursOut = MIN(unusedHours, agreement.getRolloverCapHours())`. For FIXED_FEE: no rollover. |
| 125.6 | Implement invoice generation | 125A | 125.4, 125.5 | Call `InvoiceService.createInvoice()` (or equivalent -- may need to use `InvoiceRepository` directly if the existing service signature doesn't match). Create DRAFT invoice for the customer. Line 1: base fee (description = "Retainer -- {start} to {end}", quantity=1, unitPrice=periodFee, amount=periodFee, retainerPeriodId=period.id). Line 2 (HOUR_BANK with overage): overage line (description = "Overage ({X} hrs @ {rate}/hr)", quantity=overageHours, unitPrice=resolvedRate, amount=overage*rate, retainerPeriodId=period.id). Use `OrgSettings` for currency. Snapshot customer details for invoice. Pattern: study `InvoiceService.createInvoice()` signature carefully. |
| 125.7 | Implement next period creation and auto-termination | 125A | 125.5, 125.6 | Close the period: set status=CLOSED, closedAt, closedBy, invoiceId, overageHours, rolloverHoursOut, final remainingHours. If agreement is ACTIVE and (endDate is null OR nextPeriodStart < endDate): create next period with `allocatedHours = base + rolloverHoursOut`, `baseAllocatedHours = agreement.allocatedHours`, `rolloverHoursIn = rolloverHoursOut`, `remainingHours = allocatedHours`, status=OPEN. If endDate has passed: set `agreement.status = TERMINATED`, publish `RETAINER_TERMINATED` notification. |
| 125.8 | Write `RetainerPeriodService` integration tests | 125A | 125.1--125.7 | `backend/src/test/java/.../retainer/RetainerPeriodServiceTest.java`. Tests: close HOUR_BANK period with overage (verify invoice lines, overage calculation), close HOUR_BANK period without overage, close FIXED_FEE period (single invoice line), rollover FORFEIT (rolloverOut=0), rollover CARRY_FORWARD (rolloverOut=unused), rollover CARRY_CAPPED (rolloverOut=min(unused,cap)), next period created with rollover allocation, close before end date rejected (400), no open period (404), auto-terminate when agreement endDate passed, close HOUR_BANK with overage but no billing rate (400), zero consumption (full rollover if carry policy), invoice currency from OrgSettings, retainerPeriodId set on invoice lines. ~15 tests. Complex setup: need customer, retainer, period, time entries, billing rates, OrgSettings. |
| 125.9 | Verify 125A tests pass | 125A | 125.8 | Run: `./mvnw test -pl backend -Dtest="RetainerPeriodServiceTest" -q`. All ~15 tests pass. |
| 125.10 | Create `PeriodResponse` and `PeriodCloseResult` DTOs | 125B | | `retainer/dto/PeriodResponse.java` -- all period fields. `retainer/dto/PeriodCloseResult.java` -- record with `closedPeriod` (PeriodResponse), `generatedInvoice` (inline record with id, invoiceNumber, status, total, lines list), `nextPeriod` (PeriodResponse, nullable). Per architecture doc Section 17.4.2 response format. |
| 125.11 | Create `RetainerPeriodController` | 125B | 125.10 | `retainer/RetainerPeriodController.java`. 3 endpoints: `GET /api/retainers/{id}/periods` (paginated, most recent first), `GET /api/retainers/{id}/periods/current` (open period or 404), `POST /api/retainers/{id}/periods/current/close` (admin/owner only). Pattern: follow `InvoiceController`. |
| 125.12 | Add audit events for period close | 125B | 125.11 | Wire audit events in `RetainerPeriodService.closePeriod()`: `RETAINER_PERIOD_CLOSED` (period details, consumed, overage, rollover, invoiceId), `RETAINER_INVOICE_GENERATED` (invoice ID, line items, total), `RETAINER_PERIOD_OPENED` (if next period created). Entity type `"RETAINER_PERIOD"`. |
| 125.13 | Add close notification | 125B | 125.12 | Publish `RETAINER_PERIOD_CLOSED` notification to org admins after successful close. Include agreementName, customerName, periodDates, consumedHours, overageHours, invoiceId. Pattern: follow notification dispatch in existing services. |
| 125.14 | Write controller integration tests | 125B | 125.11--125.13 | `backend/src/test/java/.../retainer/RetainerPeriodControllerTest.java`. Tests: list periods for agreement (200, paginated), get current period (200), get current period when none open (404), close period as admin (200 with close result), close period as member (403), close period before end date (400), close period with overage shows invoice lines in response. ~8 tests. |
| 125.15 | Add `findPeriodsReadyToClose` list method | 125B | 125.11 | In `RetainerPeriodService` or `RetainerAgreementService`: method that returns all OPEN periods past their end date, enriched with agreement name and customer name. Used by dashboard (Epic 126). Could also add to the list retainers endpoint as a filter. |
| 125.16 | Verify all 125 tests pass | 125B | 125.14 | Run: `./mvnw test -pl backend -Dtest="RetainerPeriodServiceTest,RetainerPeriodControllerTest" -q`. All ~23 tests pass. |

### Key Files

**Slice 125A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodService.java`
- `backend/src/test/java/.../retainer/RetainerPeriodServiceTest.java`

**Slice 125B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/dto/PeriodResponse.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/dto/PeriodCloseResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodController.java`
- `backend/src/test/java/.../retainer/RetainerPeriodControllerTest.java`

**Slice 125A/125B -- Modify (Read heavily for integration):**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Call to create DRAFT invoice
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java` -- Constructor/factory method signature
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java` -- Constructor to set retainerPeriodId
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java` -- Rate resolution
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateRepository.java` -- Customer rate query
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` -- Default currency, default billing rate

**Read for context:**
- `architecture/phase17-retainer-agreements-billing.md` Sections 17.3.3, 17.3.4, 17.3.6, 17.4.2, 17.5.3
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- Study createInvoice() signature and parameters
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrate/BillingRateService.java` -- Study resolveRate() for customer-level resolution

### Architecture Decisions

- **Admin-triggered close (ADR-072)**: Period close requires explicit admin action. The `closePeriod()` method validates `today >= periodEnd` to prevent premature close. No scheduled job.
- **Standard billing rate for overage (ADR-073)**: Overage uses the existing rate hierarchy. A new method may be needed on `BillingRateService` to resolve at the customer level (skip project-level, which is irrelevant for retainer overage).
- **Invoice creation**: Study the existing `InvoiceService.createInvoice()` carefully. The retainer close must match its expected parameters. If the existing method requires fields the retainer context doesn't have (e.g., project-level data), create invoice lines directly via repositories.
- **Single transaction**: The entire close operation (finalize, calculate, generate invoice, close period, open next) runs in one `@Transactional`. If any step fails, everything rolls back -- no partial close states.

---

## Epic 126: Retainer Dashboard & Create UI

**Goal**: Build the retainer dashboard page with list, summary cards, status filters, and the create retainer dialog. Add sidebar navigation. Create the API client module.

**References**: Architecture doc Sections 17.8.2 (Frontend Changes), 17.4.1 (API responses for list/create).

**Dependencies**: Epics 122--125 (backend must be in place).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **126A** | 126.1--126.8 | Dashboard page (`/org/[slug]/retainers/page.tsx`), retainer list table, summary cards (active count, ready-to-close count), status filter tabs, sidebar navigation, API client (`lib/api/retainers.ts`), server actions. ~8 frontend tests. | |
| **126B** | 126.9--126.14 | Create retainer dialog (customer selector, type-conditional fields, frequency, rollover), status badges, progress bar component. ~6 frontend tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 126.1 | Create API client module | 126A | | `frontend/lib/api/retainers.ts`. Functions: `fetchRetainers(status?, customerId?)`, `fetchRetainer(id)`, `createRetainer(data)`, `updateRetainer(id, data)`, `pauseRetainer(id)`, `resumeRetainer(id)`, `terminateRetainer(id)`, `fetchPeriods(retainerId, page?)`, `fetchCurrentPeriod(retainerId)`, `closePeriod(retainerId)`, `fetchRetainerSummary(customerId)`. Use existing `fetchApi()` helper pattern. Pattern: `frontend/lib/api/invoices.ts`. |
| 126.2 | Create server actions | 126A | 126.1 | `frontend/app/(app)/org/[slug]/retainers/actions.ts`. Server actions: `createRetainerAction()`, `pauseRetainerAction()`, `resumeRetainerAction()`, `terminateRetainerAction()`. Each calls API client and `revalidatePath()`. Pattern: follow `frontend/app/(app)/org/[slug]/invoices/actions.ts`. |
| 126.3 | Create retainer list component | 126A | 126.1 | `frontend/components/retainers/retainer-list.tsx`. Table with columns: Agreement Name, Customer, Type (Hour Bank / Fixed Fee), Frequency, Current Period Status, Hours Used/Allocated (progress bar for HOUR_BANK), Status Badge. Sortable by name. Loading skeleton. Empty state. Pattern: follow invoice list component. |
| 126.4 | Create summary cards component | 126A | | `frontend/components/retainers/retainer-summary-cards.tsx`. Three cards: Active Retainers (count), Periods Ready to Close (count, highlighted if > 0), Total Overage Hours (sum across OPEN periods in overage). Use Shadcn Card. Pattern: follow dashboard summary card pattern. |
| 126.5 | Create retainer dashboard page | 126A | 126.3, 126.4 | `frontend/app/(app)/org/[slug]/retainers/page.tsx`. Server component. Fetch retainers, compute summary stats. Status filter tabs (Active / Paused / Terminated / All) via URL search params. Render summary cards + retainer list. "New Retainer" button (opens create dialog from 126B). "Periods Ready to Close" section at top with prominence. |
| 126.6 | Add sidebar navigation | 126A | | Add "Retainers" nav item to sidebar, near "Schedules" (Phase 16). Icon: `Clock` or `FileText` from lucide-react. Modify `frontend/components/desktop-sidebar.tsx` and `frontend/components/mobile-sidebar.tsx`. Pattern: follow how "Schedules" nav was added. |
| 126.7 | Write dashboard and list tests | 126A | 126.3--126.5 | `frontend/__tests__/retainers/retainer-list.test.tsx` and `frontend/__tests__/retainers/retainer-dashboard.test.tsx`. Tests: list renders retainer rows, status filter changes displayed retainers, summary cards show correct counts, empty state renders when no retainers, progress bar shows correct percentage for HOUR_BANK, loading state shows skeleton. ~8 tests. Pattern: follow existing component test patterns. |
| 126.8 | Verify 126A tests pass | 126A | 126.7 | Run: `pnpm test -- --run retainers`. All ~8 tests pass. |
| 126.9 | Create status badge component | 126B | | `frontend/components/retainers/retainer-status-badge.tsx`. Renders ACTIVE (green), PAUSED (amber), TERMINATED (slate) badges. Use Shadcn `Badge` with variant. Pattern: follow existing status badges in the codebase. |
| 126.10 | Create progress bar component | 126B | | `frontend/components/retainers/retainer-progress.tsx`. Linear progress bar showing consumed/allocated hours. Changes color: green (< 80%), amber (80-99%), red (>= 100%). Shows "X of Y hrs" label. Handle FIXED_FEE (show consumed hours only, no bar). Pattern: Shadcn `Progress` component with custom colors. |
| 126.11 | Create retainer dialog | 126B | 126.1, 126.2 | `frontend/components/retainers/create-retainer-dialog.tsx`. Shadcn Dialog with form. Customer selector (ComboBox, filtered to exclude OFFBOARDED/PROSPECT + those with existing active retainer). Type selector (Hour Bank / Fixed Fee) -- toggles conditional fields. HOUR_BANK: allocated hours, period fee, rollover policy selector, rollover cap (shown when CARRY_CAPPED). FIXED_FEE: period fee only. Frequency dropdown. Start date picker. End date picker (optional). Schedule link (optional, dropdown of customer's schedules). Notes textarea. Validation before submit. Pattern: follow `frontend/components/schedules/create-schedule-dialog.tsx`. |
| 126.12 | Write create dialog tests | 126B | 126.11 | Tests: form renders with customer selector, switching type toggles fields, HOUR_BANK validation requires allocatedHours + periodFee, CARRY_CAPPED shows rolloverCapHours field, submit calls create action. ~4 tests. |
| 126.13 | Write status badge and progress bar tests | 126B | 126.9, 126.10 | Tests: status badge renders correct variant for each status, progress bar shows correct percentage, progress bar color changes at thresholds. ~2 tests. |
| 126.14 | Verify all 126 tests pass | 126B | 126.12, 126.13 | Run: `pnpm test -- --run retainers`. All ~14 tests pass. |

### Key Files

**Slice 126A -- Create:**
- `frontend/lib/api/retainers.ts`
- `frontend/app/(app)/org/[slug]/retainers/page.tsx`
- `frontend/app/(app)/org/[slug]/retainers/actions.ts`
- `frontend/components/retainers/retainer-list.tsx`
- `frontend/components/retainers/retainer-summary-cards.tsx`
- `frontend/__tests__/retainers/retainer-list.test.tsx`
- `frontend/__tests__/retainers/retainer-dashboard.test.tsx`

**Slice 126A -- Modify:**
- `frontend/components/desktop-sidebar.tsx` -- Add Retainers nav item
- `frontend/components/mobile-sidebar.tsx` -- Add Retainers nav item

**Slice 126B -- Create:**
- `frontend/components/retainers/retainer-status-badge.tsx`
- `frontend/components/retainers/retainer-progress.tsx`
- `frontend/components/retainers/create-retainer-dialog.tsx`
- `frontend/__tests__/retainers/create-retainer-dialog.test.tsx`
- `frontend/__tests__/retainers/retainer-components.test.tsx`

**Read for context:**
- `architecture/phase17-retainer-agreements-billing.md` Sections 17.4.1, 17.8.2
- `frontend/lib/api/invoices.ts` -- API client pattern
- `frontend/app/(app)/org/[slug]/invoices/page.tsx` -- List page pattern
- `frontend/app/(app)/org/[slug]/schedules/page.tsx` -- Dashboard page pattern
- `frontend/components/schedules/create-schedule-dialog.tsx` -- Create dialog pattern
- `frontend/components/desktop-sidebar.tsx` -- Nav item addition
- `frontend/CLAUDE.md` -- Conventions

### Architecture Decisions

- **Status filter via URL search params**: Using `?status=ACTIVE` search params for status filtering (not React state). This makes filter state bookmarkable and shareable. Pattern established in other list pages.
- **"Periods Ready to Close" prominence**: This section appears above the retainer list to draw admin attention. It's the primary action queue for the dashboard. Periods sorted by how overdue they are (most overdue first).
- **Customer selector filtering**: The create dialog filters out customers who already have an ACTIVE/PAUSED retainer. This is a UX guard matching the backend validation (ADR-075).

---

## Epic 127: Customer Retainer Tab & Detail Page

**Goal**: Add a retainer tab to the customer detail page, build the retainer detail page, and implement the close period dialog, edit retainer dialog, and lifecycle action buttons.

**References**: Architecture doc Sections 17.4.1 (GET detail), 17.4.2 (period endpoints), 17.8.2 (Frontend Changes).

**Dependencies**: Epic 126 (shared components: status badge, progress bar, API client).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **127A** | 127.1--127.6 | Customer retainer tab (`customer-retainer-tab.tsx`), active retainer card with progress, period history table. ~5 frontend tests. | |
| **127B** | 127.7--127.13 | Retainer detail page (`/org/[slug]/retainers/[id]/page.tsx`), edit retainer dialog, close period confirmation dialog with invoice preview, pause/resume/terminate action buttons. ~6 frontend tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 127.1 | Create period history table | 127A | | `frontend/components/retainers/period-history-table.tsx`. Table columns: Period Dates (start -- end), Status (OPEN/CLOSED badge), Allocated Hours, Consumed Hours, Overage Hours, Rollover Out, Invoice (link to invoice detail page if closed). Most recent first. Pagination. Pattern: follow existing table components. |
| 127.2 | Create customer retainer tab | 127A | 127.1 | `frontend/components/customers/customer-retainer-tab.tsx`. Conditionally rendered when customer has or had a retainer. Shows: active retainer card (agreement name, type, frequency, terms, progress indicator from 126B), period history table. If no retainer: empty state with "Set up a Retainer" link to create dialog. If only terminated retainers: show historical data with "Create New Retainer" option. |
| 127.3 | Integrate retainer tab into customer detail | 127A | 127.2 | Modify `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` to add "Retainer" tab. Fetch retainer data for the customer. Tab only visible if customer has retainer history OR is eligible for one (not OFFBOARDED/PROSPECT). Pattern: follow how other tabs (Documents, Activity) are added to customer detail. |
| 127.4 | Write customer retainer tab tests | 127A | 127.2, 127.3 | Tests: tab renders active retainer card with progress, period history table renders closed periods, empty state when no retainer, invoice link in period history row, progress bar color at different consumption levels. ~5 tests. |
| 127.5 | Verify 127A tests pass | 127A | 127.4 | Run: `pnpm test -- --run retainers customers`. All ~5 tests pass. |
| 127.6 | Fetch retainer data in customer page | 127A | 127.3 | In customer detail server component: call `fetchRetainers(undefined, customerId)` to get customer's retainer(s). Pass data to retainer tab. Also fetch periods for the active retainer if one exists. |
| 127.7 | Create retainer detail page | 127B | | `frontend/app/(app)/org/[slug]/retainers/[id]/page.tsx`. Server component. Fetches full retainer detail (with recent periods). Shows: retainer info card (name, customer, type, frequency, terms, status badge), current period card (progress, consumed/allocated, rollover info), full period history table, action buttons. Pattern: follow schedule detail page (`frontend/app/(app)/org/[slug]/schedules/[id]/page.tsx`). |
| 127.8 | Create edit retainer dialog | 127B | | `frontend/components/retainers/edit-retainer-dialog.tsx`. Shadcn Dialog pre-filled with current values. Editable fields: name, allocatedHours, periodFee, rolloverPolicy, rolloverCapHours, endDate, notes. Non-editable (display only): type, frequency, startDate, customerId. "Changes take effect on the next period" notice. Pattern: follow edit dialogs in schedules or invoices. |
| 127.9 | Create close period dialog | 127B | | `frontend/components/retainers/close-period-dialog.tsx`. AlertDialog with confirmation. Shows: period summary (dates, allocated, consumed), overage calculation (if HOUR_BANK with consumed > allocated), rollover calculation preview, invoice line items preview (base fee line + optional overage line with amounts). "Close Period & Generate Invoice" button. Warning banner if significant overage. Pattern: follow confirmation dialog patterns. |
| 127.10 | Create server actions for detail page | 127B | | `frontend/app/(app)/org/[slug]/retainers/[id]/actions.ts`. Server actions: `updateRetainerAction()`, `closePeriodAction()`, `pauseRetainerAction()`, `resumeRetainerAction()`, `terminateRetainerAction()`. Each calls API client and revalidates. |
| 127.11 | Add lifecycle action buttons | 127B | 127.7 | On retainer detail page: "Pause" button (when ACTIVE), "Resume" button (when PAUSED), "Terminate" button (when ACTIVE or PAUSED). Terminate uses AlertDialog confirmation. "Close Period" button (when period ready to close). All admin/owner only -- conditionally rendered based on org role. |
| 127.12 | Write detail page and dialog tests | 127B | 127.7--127.11 | Tests: detail page renders retainer info, close dialog shows invoice preview, edit dialog pre-fills values, pause/resume/terminate buttons render for correct states, close button only shows when period ready to close, admin-only actions hidden for members. ~6 tests. |
| 127.13 | Verify all 127 tests pass | 127B | 127.12 | Run: `pnpm test -- --run retainers`. All ~11 new tests pass. |

### Key Files

**Slice 127A -- Create:**
- `frontend/components/retainers/period-history-table.tsx`
- `frontend/components/customers/customer-retainer-tab.tsx`
- `frontend/__tests__/retainers/customer-retainer-tab.test.tsx`

**Slice 127A -- Modify:**
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` -- Add Retainer tab

**Slice 127B -- Create:**
- `frontend/app/(app)/org/[slug]/retainers/[id]/page.tsx`
- `frontend/app/(app)/org/[slug]/retainers/[id]/actions.ts`
- `frontend/components/retainers/edit-retainer-dialog.tsx`
- `frontend/components/retainers/close-period-dialog.tsx`
- `frontend/__tests__/retainers/retainer-detail.test.tsx`
- `frontend/__tests__/retainers/close-period-dialog.test.tsx`

**Read for context:**
- `architecture/phase17-retainer-agreements-billing.md` Sections 17.4.1, 17.4.2, 17.8.2
- `frontend/app/(app)/org/[slug]/schedules/[id]/page.tsx` -- Detail page pattern
- `frontend/app/(app)/org/[slug]/customers/[id]/page.tsx` -- Customer detail page to modify
- `frontend/components/schedules/edit-schedule-dialog.tsx` -- Edit dialog pattern
- `frontend/CLAUDE.md` -- Conventions

### Architecture Decisions

- **Retainer tab on customer detail**: The retainer tab is contextual -- it shows the retainer for THIS customer. The retainer dashboard (Epic 126) shows ALL retainers across customers. Both views are needed: per-customer for relationship management, cross-customer for operational oversight.
- **Close dialog with invoice preview**: The close dialog shows what invoice lines will be generated BEFORE the admin confirms. This is critical for the "admin is always in control" principle -- no surprise invoices. The preview is calculated client-side from period data + agreement terms.
- **Controlled AlertDialog for close**: Use controlled `open` state because the close action revalidates the page (doesn't redirect). Per lessons learned on Radix AlertDialog with revalidation.

---

## Epic 128: Time Entry Indicators & Notifications

**Goal**: Add retainer context to the time entry form (hours remaining indicator, overage warning), wire all retainer notification types through the notification system, and add retainer badge on project list for retainer-linked customers.

**References**: Architecture doc Sections 17.3.7 (Threshold Notifications), 17.3.8 (Period Ready to Close Detection), 17.6.1 (Notification Types), 17.8.2 (Frontend Changes).

**Dependencies**: Epic 124 (summary endpoint), Epic 126 (API client), Epic 127 (frontend pages exist).

**Scope**: Both (Frontend + Backend)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **128A** | 128.1--128.6 | Frontend: retainer indicator on time entry form ("X hrs remaining"), overage warning, retainer badge on project list. ~4 frontend tests. | |
| **128B** | 128.7--128.12 | Backend: wire `RETAINER_PERIOD_READY_TO_CLOSE` notification trigger (on dashboard load or first detection), ensure all 5 notification types dispatch correctly, notification templates. ~5 backend tests. | |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 128.1 | Create retainer indicator component | 128A | | `frontend/components/time-entries/retainer-indicator.tsx`. Displays "Retainer: X hrs remaining" when customer has active HOUR_BANK retainer. Uses `fetchRetainerSummary(customerId)` from API client. If no retainer: render nothing. If FIXED_FEE: show "Fixed Fee Retainer" label (no hours). Color: green (> 20% remaining), amber (< 20%), red (0% -- overage). Pattern: lightweight inline component. |
| 128.2 | Create overage warning component | 128A | 128.1 | Part of `retainer-indicator.tsx` or separate. When `isOverage = true` (from summary endpoint): show warning banner "Retainer fully consumed -- this time will be billed as overage." Informational only -- does NOT block time entry creation. Yellow/amber Alert component from Shadcn. |
| 128.3 | Integrate retainer indicator into time entry form | 128A | 128.1, 128.2 | Modify time entry creation flow. When a project is selected and the project's customer has an active retainer, show the indicator below the duration field. Need to resolve customer from project -- may need to pass customer data from project context. Pattern: study how the time entry form currently gets project context. |
| 128.4 | Add retainer badge to project list | 128A | | On the project list page, for projects linked to a customer with an active retainer, show a small badge or icon (e.g., clock icon with "Retainer"). Helps members identify retainer-covered work. Query retainer summary for each unique customer in the project list. Use `fetchRetainerSummary()` per customer (debounce/batch if needed). Pattern: similar to how billing status indicators are shown. |
| 128.5 | Write frontend indicator tests | 128A | 128.1--128.4 | Tests: indicator shows hours remaining for HOUR_BANK, indicator shows nothing when no retainer, overage warning appears when fully consumed, retainer badge renders on project row. ~4 tests. |
| 128.6 | Verify 128A tests pass | 128A | 128.5 | Run: `pnpm test -- --run retainers time-entries`. All ~4 tests pass. |
| 128.7 | Wire `RETAINER_PERIOD_READY_TO_CLOSE` notification | 128B | | Backend: Create a method in `RetainerPeriodService` or `RetainerAgreementService` that checks for OPEN periods past their end date that haven't been notified yet. Option A: trigger on dashboard API load (when `GET /api/retainers` is called, check and notify). Option B: add a simple tracking mechanism (e.g., a `notified_ready_to_close` boolean on `RetainerPeriod`). Option A is simpler for v1. Dispatch notification to org admins with agreementName, customerName, periodDates. |
| 128.8 | Verify all threshold notifications are wired | 128B | | Review `RetainerConsumptionListener` from Epic 124A. Ensure `RETAINER_APPROACHING_CAPACITY` (80%) and `RETAINER_FULLY_CONSUMED` (100%) notifications call `NotificationService.notify()` correctly. Verify notification payload includes: agreementName, customerName, consumedHours, allocatedHours, remainingHours, percentConsumed. If 124A only updated consumed_hours without actually dispatching notifications, wire them here. |
| 128.9 | Wire `RETAINER_TERMINATED` notification | 128B | | Ensure `RetainerAgreementService.terminateRetainer()` publishes a notification (not just an audit event). Also ensure auto-termination in `RetainerPeriodService.closePeriod()` triggers the notification. Recipients: org admins. |
| 128.10 | Add notification type constants | 128B | | Add retainer notification type strings to the notification type registry (if one exists). Types: `RETAINER_PERIOD_READY_TO_CLOSE`, `RETAINER_PERIOD_CLOSED`, `RETAINER_APPROACHING_CAPACITY`, `RETAINER_FULLY_CONSUMED`, `RETAINER_TERMINATED`. Ensure they render correctly in the notification bell and notifications page. |
| 128.11 | Write notification integration tests | 128B | 128.7--128.10 | `backend/src/test/java/.../retainer/RetainerNotificationTest.java`. Tests: period ready to close triggers notification, approaching capacity (80%) triggers notification, fully consumed (100%) triggers notification, terminate triggers notification, close period triggers notification. ~5 tests. May overlap with tests from earlier epics -- focus on notification dispatch verification, not business logic re-testing. |
| 128.12 | Verify all 128 tests pass | 128B | 128.11 | Run: `./mvnw test -pl backend -Dtest="RetainerNotificationTest" -q`. All ~5 tests pass. |

### Key Files

**Slice 128A -- Create:**
- `frontend/components/time-entries/retainer-indicator.tsx`
- `frontend/__tests__/retainers/retainer-indicator.test.tsx`

**Slice 128A -- Modify:**
- Time entry form component (study which file renders the log time form)
- `frontend/app/(app)/org/[slug]/projects/page.tsx` or project list component -- Add retainer badge

**Slice 128B -- Create:**
- `backend/src/test/java/.../retainer/RetainerNotificationTest.java`

**Slice 128B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerPeriodService.java` -- Ready-to-close notification trigger
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerConsumptionListener.java` -- Verify notification dispatch
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retainer/RetainerAgreementService.java` -- Terminate notification

**Read for context:**
- `architecture/phase17-retainer-agreements-billing.md` Sections 17.3.7, 17.3.8, 17.6.1
- `frontend/components/time-entries/` -- Existing time entry form components
- `frontend/app/(app)/org/[slug]/projects/page.tsx` -- Project list to add badge
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/notification/NotificationService.java` -- Notification dispatch pattern
- `frontend/components/notifications/` -- How notifications render in the UI

### Architecture Decisions

- **Indicator is informational only**: The retainer indicator near the time entry form does NOT block time entry creation. Even when a retainer is fully consumed, members can still log time -- it will be classified as overage. This matches the "transparent, not restrictive" principle.
- **Ready-to-close notification on dashboard load**: Rather than a scheduled job, the ready-to-close check runs when an admin loads the retainer dashboard. This is simpler than adding another scheduler and acceptable because admins who care about retainers will visit the dashboard regularly. If a period has already been notified, skip re-notification.
- **Retainer badge on project list is lightweight**: Uses the summary endpoint per customer, not a separate retainer-project query. The summary endpoint is cached per page load (not per row). If the project list shows 20 projects from 5 customers, only 5 summary API calls are made.

---

## Test Summary

| Epic | Slice | Backend Tests | Frontend Tests | Total |
|------|-------|---------------|----------------|-------|
| 122 | 122A | -- | -- | -- |
| 122 | 122B | ~22 | -- | ~22 |
| 123 | 123A | ~12 | -- | ~12 |
| 123 | 123B | ~12 | -- | ~12 |
| 124 | 124A | ~10 | -- | ~10 |
| 124 | 124B | ~5 | -- | ~5 |
| 125 | 125A | ~15 | -- | ~15 |
| 125 | 125B | ~8 | -- | ~8 |
| 126 | 126A | -- | ~8 | ~8 |
| 126 | 126B | -- | ~6 | ~6 |
| 127 | 127A | -- | ~5 | ~5 |
| 127 | 127B | -- | ~6 | ~6 |
| 128 | 128A | -- | ~4 | ~4 |
| 128 | 128B | ~5 | -- | ~5 |
| **Total** | | **~89** | **~29** | **~118** |
