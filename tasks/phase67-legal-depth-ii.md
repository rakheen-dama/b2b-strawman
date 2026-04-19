# Phase 67 — Legal Depth II: Daily Operational Loop

> Architecture doc: `architecture/phase67-legal-depth-ii.md`
> ADRs: [ADR-247](../adr/ADR-247-legal-disbursement-sibling-entity.md), [ADR-248](../adr/ADR-248-matter-closure-distinct-state-with-gates.md), [ADR-249](../adr/ADR-249-retention-clock-starts-on-closure.md), [ADR-250](../adr/ADR-250-statement-of-account-template-and-context.md), [ADR-251](../adr/ADR-251-acceptance-eligible-template-manifest-flag.md)
> Starting epic: 486 · Last completed: 485 (Phase 66)
> Migration high-water: tenant V95 (in architecture) — but tenant migrations on disk are at **V99**, so the two new migrations will land as the next two free numbers (likely V100 / V101). The architecture refers to them as V96 / V97 by content name; slice builders must rename to next-available V-number at implementation time and preserve the same contents.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 486 | Disbursement Entity + Service + Module Registration | Backend | -- | M | 486A, 486B | **Done** (PRs #1067, #1068) |
| 487 | Disbursement Invoicing Integration | Backend | 486, 489A | M | 487A, 487B | **Done** (PRs #1071, #1072) |
| 488 | Disbursement Frontend | Frontend | 486, 487 | M | 488A, 488B | **Done** (PRs #1073, #1074) |
| 489 | Matter Closure Workflow (Backend) | Backend | 486A | L | 489A, 489B | **Done** (PRs #1069, #1070) |
| 490 | Matter Closure Frontend | Frontend | 489 | M | 490A, 490B | **Done** (PRs #1075, #1076) |
| 491 | Statement of Account | Both | 486B | M | 491A, 491B | **Done** (PRs #1077, #1078) |
| 492 | Conveyancing Pack | Backend (pack content) | 489A (for `acceptance_eligible` column) | M | 492A, 492B | **Pending** |
| 493 | QA Capstone — Lifecycle + Screenshots + Gap Report | E2E/Process | 486–492 | L | 493A | **Pending** |

Slice count: **15 slices across 8 epics**. Every code slice pairs its implementation with the integration or component tests that exercise it; pack slices pair JSON content with seeder-level assertions.

---

## Dependency Graph

```
PHASE 55 (legal foundation), PHASE 60 (trust accounting), PHASE 61 (KYC / §86),
PHASE 64 (legal terminology + templates), PHASE 65 (pack installer pipeline),
PHASE 50 (retention), PHASE 12/31 (templates + VariableResolver),
PHASE 28 (acceptance), PHASE 6.5 (notification handlers) — all complete
                                |
                   ┌────────────┴────────────┐
                   |                         |
            [E486A  LegalDisbursement        |
             entity + V96 migration +        |
             module registration +           |
             6 enums + DTOs + repo]          |
                   |                         |
       ┌───────────┼──────────────┬──────────┼───────────────────────┐
       |           |              |          |                       |
[E486B             [E487A         [E488A     [E489A                  [E492A
 DisbursementSvc    /unbilled      list page  V97 migration +          field pack +
 (CRUD, approval,   endpoint +     + create    Project CLOSED          project template
 writeOff, events,  UnbilledTime   dialog +   status + MatterClosureLog  append +
 controller,        extension]    project     entity + 9 ClosureGates  clause pack]
 integration                       tab +       + retention cancelled_at +     |
 tests)]                           API client] acceptance_eligible            |
       |             |             |          column + gate unit tests]    [E492B
       |             |             |                 |                      document
       |             |             |          [E489B                        templates +
       |             |             |           MatterClosureService          acceptance
       |             |             |           (evaluate/close/reopen)       manifest flag
       |             |             |           + retention wiring +          + request pack]
       |             |             |           MatterClosedEvent +                |
       |             |             |           notification handler +             |
       |             |             |           closure letter template +          |
       |             |             |           controller + integration tests]    |
       |             |             |                 |                             |
       |             |             |           [E490A                              |
       |             |             |            Matter closure dialog              |
       |             |             |            (3-step) + gate report             |
       |             |             |            + API client +                     |
       |             |             |            component tests]                   |
       |             |             |                 |                             |
       |             |             |           [E490B                              |
       |             |             |            Reopen action +                    |
       |             |             |            CLOSED filter chip +               |
       |             |             |            matter-list default excl           |
       |             |             |            + status badge update]             |
       |             |             |                                               |
       |         [E487B                                                            |
       |          InvoiceLine.disbursementId mapping +                             |
       |          createDisbursementLines + tax-rate resolution                    |
       |          + markBilled + DisbursementBilledEvent +                         |
       |          mixed-draft integration test]                                    |
       |             |                                                             |
       |         [E488B                                                            |
       |          approval panel + trust-link                                      |
       |          dialog + invoice-editor                                          |
       |          "Add Disbursements" picker]                                      |
       |                                                                           |
[E491A  StatementOfAccountContextBuilder                                           |
 + statement-of-account.json Tiptap +                                              |
 /api/matters/{id}/statements endpoints +                                          |
 GENERATE_STATEMENT_OF_ACCOUNT capability +                                        |
 empty-period + coexistence tests]                                                 |
       |                                                                           |
[E491B  SoA dialog (period picker + preview +                                      |
 save) + statements tab on matter detail +                                         |
 API client + component tests]                                                     |
                                                                                   |
                                          ┌────────────────────────────────────────┘
                                          |
                                   [E493A  Retarget legal 90-day keycloak
                                    lifecycle to phase 67 checkpoints
                                    (Day 5 disbursements, Day 14 conveyancing,
                                    Day 30 SoA, Day 45 write-off,
                                    Day 75 closure, Day 85 override) +
                                    Playwright screenshot baselines +
                                    documentation curated shots +
                                    phase67-gap-report.md]
```

**Parallel opportunities**:
- After 486A: 486B, 487A, 488A, 489A, and 492A can all run concurrently — 487A stubs the contract 486B will commit to, 488A stubs the API client 486B/487A will honour, 489A has its own migration and gate unit tests, 492A is pack-only and independent.
- 487B begins once 487A merges **and** 489A merges (V97 migration carries the `invoice_lines.disbursement_id` FK column that references `legal_disbursements` created in 486A's V96).
- 488B waits on 487B (invoice editor picker).
- 490A begins once 489B merges; 490B follows 490A.
- 491A needs 486B (disbursement read-model for statement inclusion). 491B follows 491A.
- 492B needs 489A (`document_templates.acceptance_eligible` column added by V97) and 492A (field pack + clause pack present for the Property Transfer template to attach to).
- 493A is the capstone — blocks until every other slice is merged and installable in a fresh `legal-za` tenant.

---

## Implementation Order

### Stage 0: Foundation (single slice — unblocks everything)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 0a | 486 | 486A | `LegalDisbursement` entity + repo + 6 enums + DTOs + `disbursements` module registration + V96 migration. **Done** (PR #1067) |

### Stage 1: Fan-out (five parallel tracks after 486A)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 1a (parallel) | 486 | 486B | `DisbursementService` (CRUD, approval, writeOff, events) + `DisbursementController` + integration tests. **Done** (PR #1068) |
| 1b (parallel) | 487 | 487A | `/unbilled?projectId=` endpoint, `DisbursementRepository.findUnbilledBillableByCustomerId`, `UnbilledTimeService`/`UnbilledTimeResponse` module-gated extension. **Done** (PR #1071) |
| 1c (parallel) | 488 | 488A | List page + detail page + create dialog + project-detail Disbursements tab + `frontend/lib/api/legal-disbursements.ts`. **Done** (PR #1073) |
| 1d (parallel) | 489 | 489A | V97 migration + `Project.CLOSED` + `ProjectLifecycleGuard` transitions + `MatterClosureLog` entity/repo + `ClosureGate` interface + 9 gate classes + gate unit tests. **Done** (PR #1069) |
| 1e (parallel) | 492 | 492A | Field pack `conveyancing-za-project.json` + Property Transfer template appended to `project-template-packs/legal-za.json` + `conveyancing-za-clauses` pack with 10 clauses + profile manifest update. **Done** (PR #1079) |

### Stage 2: Integration (unblocked by 489A + 487A)

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 2a | 487 | 487B | `InvoiceLine.disbursementId` mapping + `CreateInvoiceRequest.disbursementIds` + `InvoiceCreationService.createDisbursementLines` + `markBilled` side-effect + `DisbursementBilledEvent` + mixed-sources integration test. **Done** (PR #1072) |
| 2b (parallel with 2a) | 489 | 489B | `MatterClosureService` (evaluate/close/reopen) + retention-policy insert + `matter-closure-letter` Tiptap + `MatterClosedEvent`/`MatterReopenedEvent` + `MatterClosureNotificationHandler` + `MatterClosureContextBuilder` + `MatterClosureController` + integration tests. **Done** (PR #1070) |
| 2c (parallel with 2a) | 491 | 491A | `StatementOfAccountContextBuilder` + `statement-of-account.json` Tiptap + `StatementController` + `GENERATE_STATEMENT_OF_ACCOUNT` capability wiring + `StatementOfAccountGeneratedEvent` + context-builder tests. **Done** (PR #1077) |
| 2d (parallel with 2a) | 492 | 492B | 4 conveyancing Tiptap templates + extend legal-za `pack.json` with `acceptanceEligible` flag + `TemplatePackSeeder` wiring of new column + `conveyancing-intake-za.json` request pack + install tests. |

### Stage 3: Frontend consumers

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 3a | 488 | 488B | Approval panel + trust-link dialog + invoice-editor "Add Disbursements" picker. **Done** (PR #1074) |
| 3b | 490 | 490A | Matter closure dialog (3-step) + matter closure report component + `frontend/lib/api/matter-closure.ts` + component tests. **Done** (PR #1075) |
| 3c | 491 | 491B | Statement of Account dialog + statements tab on matter detail + `frontend/lib/api/statement-of-account.ts` + component tests. **Done** (PR #1078) |

### Stage 4: Closure polish

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 4a | 490 | 490B | Reopen action + matter-list CLOSED filter chip + default-filter update + status badge CLOSED variant. **Done** (PR #1076) |

### Stage 5: QA capstone

| Order | Epic | Slice | Summary |
|-------|------|-------|---------|
| 5a | 493 | 493A | Retarget legal 90-day Keycloak lifecycle with phase 67 checkpoints + Playwright baselines under `e2e/screenshots/legal-depth-ii/` + curated documentation screenshots + `phase67-gap-report.md`. |

### Timeline

```
Stage 0:  [486A]                                                           <- entity + migration + module
Stage 1:  [486B] // [487A] // [488A] // [489A] // [492A]                   <- fan-out after 486A
Stage 2:  [487B] // [489B] // [491A] // [492B]                             <- integration (after 489A + 487A for 487B)
Stage 3:  [488B] -> [490A] -> [491B]                                       <- frontend consumers
Stage 4:  [490B]                                                           <- closure polish
Stage 5:  [493A]                                                           <- QA capstone
```

---

## Parallel Tracks

- **Disbursement track** (486 → 487 → 488): entity → service/controller → invoicing integration → frontend. Once 486A is in, three parallel branches (backend service 486B, backend unbilled read-model 487A, frontend list/create 488A) can all move; 487B and 488B converge later.
- **Closure track** (489 → 490): 489A ships the V97 migration + the 9 pure-function gate classes with unit tests (no transactional orchestrator yet), 489B wires the orchestrator + retention + letter generation + notifications, then 490A/490B follow for the frontend. The track runs in parallel with the invoicing track.
- **Statement of Account track** (491): 491A blocks on 486B (disbursement read-model for statement inclusion) and runs in parallel with 487B/489B. 491B is the frontend, runs once 491A merges.
- **Pack track** (492): 492A and 492B are pure content — no backend services. 492A is independent of every other backend track; 492B needs the `document_templates.acceptance_eligible` column that V97 (slice 489A) creates and the `TemplatePackSeeder` wiring in the same slice, plus the clause pack from 492A.
- **QA track** (493): blocks on every other slice being merged. Ships lifecycle retarget, screenshot baselines, and the phase 67 gap report.

A realistic day-by-day cadence: 486A days 1–3; fan-out days 3–7 (486B, 487A, 488A, 489A, 492A concurrent); integration days 7–12 (487B, 489B, 491A, 492B concurrent); frontend consumers days 10–15 (488B, 490A, 491B sequential); closure polish day 15; QA capstone days 16–19.

---

## Epic 486: Disbursement Entity + Service + Module Registration

**Goal**: Ship the `LegalDisbursement` sibling entity with its approval + billing state machine, register the new `disbursements` vertical module, and expose the full write-side service plus REST controller so every downstream epic can depend on a stable contract.

**References**: Architecture Sections 67.2.1, 67.3.1, 67.3.2, 67.4.1, 67.7, 67.8.1, 67.9.1, 67.9.3, 67.9.4; [ADR-247](../adr/ADR-247-legal-disbursement-sibling-entity.md).

**Dependencies**: None at the epic level. Inherits Phase 55 module-guard conventions, Phase 60 trust transaction machinery (for the `TRUST_ACCOUNT` branch), and Phase 12 `Document` entity (for `receiptDocumentId`).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **486A** | 486.1–486.8 | V96 migration creating `legal_disbursements` (table + 5 indexes + 7 CHECK constraints), `LegalDisbursement` entity with `@PrePersist` + state-transition methods, 5 Java-side enum classes (`DisbursementCategory`, `VatTreatment`, `DisbursementPaymentSource`, `DisbursementApprovalStatus`, `DisbursementBillingStatus`), `DisbursementRepository` with base JPA methods, 6 new `Capability` enum values + default role bindings, `disbursements` module registered in `VerticalModuleRegistry` auto-enabled under `legal-za`, repository-only integration test confirming CHECK constraints fire. **Done** (PR #1067) |
| **486B** | 486.9–486.16 | Request/response DTO records, `DisbursementService` (create/update/submitForApproval/approve/reject/writeOff/markBilled/listForStatement + default-VAT-per-category helper + trust-transaction validation), 3 domain events (`DisbursementApprovedEvent`, `DisbursementRejectedEvent`, `DisbursementBilledEvent`), `DisbursementController` covering all endpoints except `/unbilled` (owned by 487A) and `/receipt` multipart (covered here), full service + controller integration tests (approval lifecycle, trust-link validation both branches, capability enforcement, module guard blocks non-legal tenants). **Done** (PR #1068) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 486.1 | Create V96 migration `create_legal_disbursements.sql` | 486A | -- | New file: `backend/src/main/resources/db/migration/tenant/V96__create_legal_disbursements.sql` (rename to next available V-number on implementation — tenant migrations on disk are already at V99, so this will be **V100** in practice; preserve architecture's filename intent in a leading SQL comment). DDL exactly per architecture §67.8.1: table `legal_disbursements` with 22 columns, 7 CHECK constraints (`amount > 0`, category enum set, vat_treatment set, payment_source set, trust-link XOR, approval_status set, billing_status set, writeoff_reason required when WRITTEN_OFF), 5 indexes (project_id, customer_id, partial on billing_status='UNBILLED', partial on approval_status='PENDING_APPROVAL', partial on trust_transaction_id). Pattern: `V85__create_trust_accounting_tables.sql`. |
| 486.2 | Create 5 Java-side enums in `verticals/legal/disbursement/` | 486A | -- | New files under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/`: `DisbursementCategory.java` (9 values per arch §67.2.1), `VatTreatment.java` (STANDARD_15, ZERO_RATED_PASS_THROUGH, EXEMPT), `DisbursementPaymentSource.java` (OFFICE_ACCOUNT, TRUST_ACCOUNT), `DisbursementApprovalStatus.java` (DRAFT, PENDING_APPROVAL, APPROVED, REJECTED), `DisbursementBillingStatus.java` (UNBILLED, BILLED, WRITTEN_OFF). Each enum plain (no converters) — DB is varchar per [ADR-238]. Pattern: `verticals/legal/trustaccounting/TrustAccountStatus.java`. |
| 486.3 | Create `LegalDisbursement` entity | 486A | 486.1, 486.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/LegalDisbursement.java`. Fields exactly per arch §67.9.3 skeleton: `@Entity @Table(name="legal_disbursements")`, `@Id @GeneratedValue(strategy=GenerationType.UUID)`, status fields as `String` (varchar), bare `UUID` FKs (no `@ManyToOne`), `BigDecimal` with `precision=15, scale=2`, `@PrePersist` sets `createdAt`+`updatedAt`+ defaults (`approvalStatus="DRAFT"`, `billingStatus="UNBILLED"`), `@PreUpdate` bumps `updatedAt`, protected no-arg constructor, public constructor taking required fields. State-transition methods: `submitForApproval()`, `approve(approverId, notes)`, `reject(approverId, notes)`, `markBilled(invoiceLineId)`, `writeOff(reason)`, `restore()` — each guards legal transition and throws `IllegalStateException` on invalid. Pattern: `verticals/legal/trustaccounting/transaction/TrustTransaction.java`. |
| 486.4 | Create `DisbursementRepository` | 486A | 486.3 | New file: `verticals/legal/disbursement/DisbursementRepository.java`. Extends `JpaRepository<LegalDisbursement, UUID>`. Include `findByProjectIdAndApprovalStatusIn(UUID, Collection<String>)`, `countByProjectIdAndBillingStatus(UUID, String)`, `countByProjectIdAndApprovalStatusIn(UUID, Collection<String>)`, `findForStatement(UUID projectId, LocalDate from, LocalDate to)` per arch §67.9.4. **Do NOT add** `findUnbilledBillableByCustomerId` — that belongs to slice 487A to keep 486A self-contained. Pattern: `ExpenseRepository.java`. |
| 486.5 | Add 6 new `Capability` enum values + register default role bindings | 486A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java`. Append values `MANAGE_DISBURSEMENTS`, `APPROVE_DISBURSEMENTS`, `WRITE_OFF_DISBURSEMENTS`, `CLOSE_MATTER`, `OVERRIDE_MATTER_CLOSURE`, `GENERATE_STATEMENT_OF_ACCOUNT`. Defaults per arch §67.7: Owner gets all six; Admin gets all except `OVERRIDE_MATTER_CLOSURE`; Member gets `MANAGE_DISBURSEMENTS` + `GENERATE_STATEMENT_OF_ACCOUNT` only. Defaults are applied by the existing role-seeding path (there is no standalone `OrgRoleSeeder` class in the codebase — find where Capability defaults live by searching for existing entries such as `APPROVE_TRUST_PAYMENT` and extend the same location; likely `OrgRoleService` bootstrap or a capability-default map). |
| 486.6 | Register `disbursements` module in `VerticalModuleRegistry` | 486A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java`. Add module id `"disbursements"` auto-enabled under `legal-za` profile. Pattern: existing `trust_accounting` / `court_calendar` registrations. Do NOT yet register `matter_closure` — that lives on slice 489A. |
| 486.7 | Repository-only integration test for CHECK constraints + indexes | 486A | 486.1–486.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/LegalDisbursementRepositoryTest.java`. ~8 tests: (1) insert with `amount <= 0` throws `DataIntegrityViolationException`, (2) insert with bad `category` fails, (3) insert with `payment_source=TRUST_ACCOUNT` but null `trust_transaction_id` fails CHECK, (4) insert with `payment_source=OFFICE_ACCOUNT` + non-null trust fails CHECK, (5) insert with `billing_status='WRITTEN_OFF'` + null `write_off_reason` fails CHECK, (6) happy-path insert succeeds with defaults `approval_status='DRAFT'`, `billing_status='UNBILLED'`, (7) `findByProjectIdAndApprovalStatusIn(PENDING_APPROVAL)` returns only pending rows, (8) `findForStatement` bounds by `incurred_date`. Pattern: `backend/src/test/java/.../verticals/legal/trustaccounting/transaction/TrustTransactionRepositoryTest.java`. |
| 486.8 | Coexistence smoke-test: `disbursements` module is disabled for non-legal tenants | 486A | 486.6 | New test class or extension to `backend/src/test/java/.../verticals/MultiVerticalCoexistenceTest.java` (extend an existing file if present, else new). 2 tests: (1) `VerticalModuleRegistry.isEnabled("disbursements", accountingZaTenantId) == false`, (2) `...consultingZaTenantId == false`. Also assert `VerticalModuleRegistry.isEnabled("disbursements", legalZaTenantId) == true`. Pattern: existing `trust_accounting` coexistence assertions. |
| 486.9 | Create DTO records | 486B | 486A | New files under `verticals/legal/disbursement/dto/`: `CreateDisbursementRequest.java`, `UpdateDisbursementRequest.java`, `ApprovalDecisionRequest.java` (notes only — used for both approve and reject), `WriteOffRequest.java` (reason only), `DisbursementResponse.java` (exposes every entity field), `UnbilledDisbursementDto.java` (subset: id, incurredDate, category, description, amount, vatTreatment, vatAmount, supplierName — used by 487A), `DisbursementStatementDto.java` (used by 491A). All Java records with `@Valid` / `@NotNull` / `@Positive` bean-validation annotations matching arch §67.3.1 validation rules. Pattern: `expense/dto/`. |
| 486.10 | Create 3 domain events | 486B | 486A | New files under `verticals/legal/disbursement/event/`: `DisbursementApprovedEvent.java`, `DisbursementRejectedEvent.java`, `DisbursementBilledEvent.java`. Each a Java record carrying `UUID disbursementId`, `UUID projectId`, `UUID customerId`, `UUID actorId`, `Instant occurredAt`. `DisbursementBilledEvent` also carries `UUID invoiceLineId`. Pattern: `verticals/legal/trustaccounting/event/TrustTransactionApprovedEvent.java`. |
| 486.11 | Implement `DisbursementService` core | 486B | 486.9, 486.10 | New file: `verticals/legal/disbursement/DisbursementService.java`. Method signatures exactly per arch §67.3.1: `create`, `update`, `submitForApproval`, `approve` (fires `DisbursementApprovedEvent`), `reject`, `writeOff`, `markBilled`, `listForStatement`. Private helper `defaultVatTreatmentFor(DisbursementCategory)` implementing the table in arch §67.3.1 (SHERIFF_FEES/DEEDS_OFFICE_FEES/COURT_FEES → ZERO_RATED_PASS_THROUGH; others → STANDARD_15). Private helper `computeVatAmount(amount, vatTreatment)`: 15% on STANDARD, 0 on zero-rated/exempt. All persistence through `DisbursementRepository`. All mutation methods `@Transactional`. Event publication via injected `ApplicationEventPublisher`. |
| 486.12 | Implement trust-link validation in `DisbursementService` | 486B | 486.11 | Same file. Private method `validateTrustLink(CreateDisbursementRequest)` called from `create` and `update`: if `paymentSource == TRUST_ACCOUNT`, fetch `TrustTransaction` via existing `TrustTransactionRepository`, assert `status == "APPROVED"`, `transactionType == "DISBURSEMENT_PAYMENT"`, `projectId == req.projectId`. Throws semantic `IllegalStateException` with specific message for each failure mode. Reference arch §67.3.2. |
| 486.13 | Implement `DisbursementController` | 486B | 486.11 | New file: `verticals/legal/disbursement/DisbursementController.java`. `@RestController @RequestMapping("/api/legal/disbursements") @VerticalModuleGuard("disbursements")`. Endpoints per arch §67.4.1 excluding `/unbilled` (487A owns). Each method annotated with `@RequiresCapability(...)`: POST/PATCH/submit/receipt → `MANAGE_DISBURSEMENTS`; approve/reject → `APPROVE_DISBURSEMENTS`; write-off → `WRITE_OFF_DISBURSEMENTS`; GET/list → `VIEW_LEGAL`. Include `POST /{id}/receipt` multipart endpoint routing through existing `DocumentService` for S3 upload, setting `receiptDocumentId` on the disbursement. Return ProblemDetail 422 on semantic violations, 404 on missing entity (before module guard), 403 on capability miss. Pattern: `verticals/legal/trustaccounting/TrustAccountingController.java`. |
| 486.14 | Service integration test — approval lifecycle + state transitions | 486B | 486.11, 486.12 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementServiceIntegrationTest.java`. ~10 tests: (1) `create` yields DRAFT + UNBILLED with VAT defaulted per category, (2) `create` with explicit `vatTreatment` override persists the override, (3) `submitForApproval` DRAFT→PENDING_APPROVAL, illegal from APPROVED, (4) `approve` fires `DisbursementApprovedEvent` and sets approver + timestamp, (5) `reject` fires event, (6) `update` on APPROVED throws, (7) `writeOff` UNBILLED→WRITTEN_OFF with reason, blocks without reason, (8) `writeOff` on BILLED throws, (9) `markBilled` fires `DisbursementBilledEvent`, (10) `listForStatement` respects date range + orders by category/date. Pattern: `verticals/legal/trustaccounting/transaction/TrustTransactionServiceTest.java`. |
| 486.15 | Service integration test — trust-link validation both branches | 486B | 486.12 | Same file, +5 tests: (1) `create` with `paymentSource=OFFICE_ACCOUNT` + null `trustTransactionId` succeeds, (2) with `paymentSource=OFFICE_ACCOUNT` + non-null fails at service validation (before CHECK), (3) with `paymentSource=TRUST_ACCOUNT` + approved DISBURSEMENT_PAYMENT tx for same matter succeeds, (4) with trust tx for different `projectId` fails, (5) with trust tx whose `status != APPROVED` fails. Seed trust tx via existing Phase 60 fixtures. |
| 486.16 | Controller integration test — capability enforcement + module guard | 486B | 486.13 | New file: `DisbursementControllerIntegrationTest.java` (same package as 486.14). ~8 tests: (1) non-legal tenant request returns 404 via module guard, (2) legal tenant without `APPROVE_DISBURSEMENTS` POST approve returns 403, (3) POST create with valid body returns 201 and expected response shape, (4) PATCH on APPROVED row returns 422, (5) POST write-off without reason returns 400, (6) GET list with `projectId`/`billingStatus`/`approvalStatus` filters narrows results, (7) POST receipt multipart persists document id and links it on row, (8) GET one returns full DisbursementResponse. Uses MockMvc + existing auth fixtures for legal-za + accounting-za tenants. Pattern: `expense/ExpenseControllerIntegrationTest.java`. |

### Key Files

**Create:**
- `backend/src/main/resources/db/migration/tenant/V96__create_legal_disbursements.sql` (rename to next available V-number)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/LegalDisbursement.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementCategory.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/VatTreatment.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementPaymentSource.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementApprovalStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementBillingStatus.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/dto/*.java` (7 DTOs)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/event/DisbursementApprovedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/event/DisbursementRejectedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/event/DisbursementBilledEvent.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/LegalDisbursementRepositoryTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementServiceIntegrationTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementControllerIntegrationTest.java`

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` — add 6 new enum values
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java` — register `disbursements` module
- Capability default-binding registry (locate by searching for existing legal capability defaults such as `APPROVE_TRUST_PAYMENT`)
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/MultiVerticalCoexistenceTest.java` — add `disbursements` module-disabled assertions

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransaction.java` — entity shape reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/Expense.java` — sibling horizontal entity (what we are NOT extending)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/ExpenseService.java` — mutator/state-transition pattern to mirror
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingController.java` — module-guard + capability pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/RequiresCapability.java` and `CapabilityAuthorizationManager.java` — capability interceptor contract
- `backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql` — migration style

### Architecture Decisions

- **Sibling entity, not an `Expense` extension** ([ADR-247](../adr/ADR-247-legal-disbursement-sibling-entity.md)). `Expense.markup` / `Expense.billable` semantics diverge; mutant overload of a horizontal entity rejected.
- **Status fields as varchar with Java-side enums** per [ADR-238]. Enums live on the Java side only; the DB uses CHECK constraints for enforcement.
- **`vatAmount` stored at write-time**, never computed on read. Prevents retroactive drift when `TaxRate` rows change.
- **`trustTransactionId` is a bare UUID FK**, not `@ManyToOne`. Joins occur at the service layer per Phase 55/60 convention.
- **Capability default matrix** (arch §67.7): Owner = all 6; Admin = all except `OVERRIDE_MATTER_CLOSURE`; Member = `MANAGE_DISBURSEMENTS` + `GENERATE_STATEMENT_OF_ACCOUNT` only. `OVERRIDE_MATTER_CLOSURE` is siloed to Owner by default.
- **`matter_closure` module is NOT registered here.** It lives on slice 489A to keep migration ownership clean (V97 adds the `CLOSED` status the closure module depends on).

### Non-scope

- No `findUnbilledBillableByCustomerId` repository method (owned by 487A).
- No invoice integration — `InvoiceLine.disbursementId` and `createDisbursementLines` are 487B.
- No matter-closure code at all — separate epic.
- No frontend — handled by 488.
- No Statement of Account code — 491.
- No bulk-import or CSV surface (out of scope this phase per arch §67.12).
- No markup field — disbursements are pass-through by SA statute.

---

## Epic 487: Disbursement Invoicing Integration

**Goal**: Plumb approved-unbilled disbursements into the unbilled-summary read model and into `InvoiceCreationService.createDraft(...)` as a third parallel track alongside time entries and expenses, without introducing a shared `Billable` interface.

**References**: Architecture Sections 67.3.3 (parallel-path integration), 67.4.1 (`/unbilled` endpoint), 67.8.2 (invoice_line changes in V97), 67.9.1 (InvoiceLine / InvoiceCreationService modifications); [ADR-247](../adr/ADR-247-legal-disbursement-sibling-entity.md).

**Dependencies**: 486 (entity + service must exist), and **489A for 487B** (V97 migration adds the `invoice_lines.disbursement_id` column + extends the `line_source` CHECK constraint to include `'DISBURSEMENT'`).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **487A** | 487.1–487.4 | `DisbursementRepository.findUnbilledBillableByCustomerId`, `/api/legal/disbursements/unbilled?projectId=` endpoint, `UnbilledTimeService` module-gated extension, `UnbilledTimeResponse` new `disbursements` field, integration tests for picker feed + response shape with and without module. **Done** (PR #1071) |
| **487B** | 487.5–487.10 | `InvoiceLine.disbursementId` entity mapping (V97 column present from 489A), `CreateInvoiceRequest.disbursementIds` optional field, `InvoiceCreationService.createDisbursementLines(...)` private helper with tax-rate resolution by VAT treatment, `markBilled` side-effect in same transaction, `DisbursementBilledEvent` publication, mixed time + expense + disbursement draft integration test, rollback-unbinds test. **Done** (PR #1072) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 487.1 | Add `findUnbilledBillableByCustomerId` to `DisbursementRepository` | 487A | 486 | Modify: `verticals/legal/disbursement/DisbursementRepository.java`. Add the exact `@Query` from arch §67.3.3 / §67.9.4: selects APPROVED + UNBILLED rows, filters optionally by `projectId`, orders by `incurredDate ASC, createdAt ASC`. Pattern: `ExpenseRepository.findUnbilledBillableByCustomerId`. |
| 487.2 | Add `/unbilled?projectId=` endpoint | 487A | 487.1 | Modify: `verticals/legal/disbursement/DisbursementController.java`. New `GET /api/legal/disbursements/unbilled?projectId={uuid}` method, `@RequiresCapability("VIEW_LEGAL")`. Returns response shape per arch §67.4.1: `{ projectId, currency: "ZAR", items: [UnbilledDisbursementDto], totalAmount, totalVat }`. Delegates to new `DisbursementService.listUnbilledForProject(projectId)` method that calls the repo method with `customerId` resolved from the matter. Note: architecture signature is `listUnbilledForProject(projectId)` — add to `DisbursementService` here (not in 486B). |
| 487.3 | Extend `UnbilledTimeService` with module-gated disbursements list | 487A | 487.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeService.java`. Inject `DisbursementRepository` + `VerticalModuleRegistry`. In `getUnbilledTime(...)`: when `VerticalModuleRegistry.isEnabled("disbursements", currentTenantId())`, call `findUnbilledBillableByCustomerId(customerId, projectId)` and populate the new `disbursements` list on the response. When disabled, leave field empty (byte-compatible with pre-phase-67 callers). Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeResponse.java` — add `List<UnbilledDisbursementDto> disbursements` field with Jackson default-empty annotation. |
| 487.4 | Integration test: `/unbilled` + `UnbilledTimeService` response shape | 487A | 487.1–487.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementUnbilledEndpointTest.java`. ~5 tests: (1) legal-za tenant with module enabled — GET `/unbilled?projectId=X` returns APPROVED + UNBILLED rows only (seed 4: DRAFT / PENDING / APPROVED+UNBILLED / APPROVED+BILLED — expect only the third), (2) ordering by `incurredDate ASC`, (3) `totalAmount` and `totalVat` sums match, (4) `UnbilledTimeService.getUnbilledTime(...)` for legal-za tenant includes `disbursements` list, (5) for accounting-za tenant the `disbursements` list is empty. Pattern: existing `UnbilledTimeServiceTest` for shape, expense unbilled tests for ordering. |
| 487.5 | Add `disbursementId` field to `InvoiceLine` entity | 487B | 489A (V97 column) | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java`. Add `@Column(name="disbursement_id") private UUID disbursementId;` with getter/setter. Mirrors existing `timeEntryId`, `expenseId`, `tariffItemId` on same entity. No migration needed here — 489A's V97 creates the column. |
| 487.6 | Add `disbursementIds` to `CreateInvoiceRequest` | 487B | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/CreateInvoiceRequest.java` (or equivalent request record under `invoice/dto/`). Add optional `List<UUID> disbursementIds` field defaulting to empty list. Bean validation: no constraint (null or empty → skip the disbursement path). |
| 487.7 | Implement `createDisbursementLines` in `InvoiceCreationService` | 487B | 487.5, 487.6, 486 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCreationService.java`. Inject `DisbursementRepository`, `DisbursementService`, existing `TaxRateRepository`. New private method per arch §67.3.3 pseudocode: `createDisbursementLines(Invoice invoice, List<UUID> disbursementIds, UUID customerId, int sortOffset)`. Steps per disbursement: (1) fetch by id, assert `approvalStatus == APPROVED` + `billingStatus == UNBILLED` else IllegalStateException, (2) assert `projectId` matches invoice scope if invoice is project-scoped, (3) resolve `TaxRate` via helper `resolveTaxRateForVatTreatment(VatTreatment)` — STANDARD_15 → default 15% rate, ZERO_RATED_PASS_THROUGH → zero-rated row, EXEMPT → exempt row (query by rate + flags; do not cache), (4) build `InvoiceLine` with `lineSource="DISBURSEMENT"`, `disbursementId=d.id`, `description=formatDisbursementLineDescription(d)`, `amount=d.amount`, `taxRateId=resolvedTaxRate.id`, `sortOrder=sortOffset+i`, (5) save line, (6) call `disbursementService.markBilled(d.id, line.id)`. Wire into `createDraft(...)` alongside existing time-entry + expense calls, preserving `@Transactional` scope. Add private helper `formatDisbursementLineDescription(LegalDisbursement)` producing `"{category-label}: {description} ({supplier}, {incurred_date})"`. |
| 487.8 | Extend `line_source` Java handling + InvoiceTaxService trigger | 487B | 487.7 | Verify (may be no-op) that `InvoiceTaxService.recalculateInvoiceTotals` picks up the new `DISBURSEMENT`-source lines via their `taxRateId`. The arch §67.3.3 states no code change is required here — confirm by reading `InvoiceTaxService.java`. If it filters by `lineSource`, add `DISBURSEMENT`. |
| 487.9 | Integration test: disbursement-only draft creation | 487B | 487.7 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCreationDisbursementTest.java`. ~5 tests: (1) `createDraft` with 3 disbursement ids (one STANDARD_15, one ZERO_RATED, one EXEMPT) produces 3 `DISBURSEMENT`-source lines with correct tax rates, (2) each linked disbursement is moved to `BILLED` with `invoiceLineId` set, (3) `DisbursementBilledEvent` fires once per disbursement, (4) draft with a DRAFT (not APPROVED) disbursement id throws 422, (5) draft with an already-BILLED disbursement throws 422. |
| 487.10 | Integration test: mixed-sources draft + rollback unbinds | 487B | 487.9 | Same file, +3 tests: (1) `createDraft` with time-entry-ids + expense-ids + disbursement-ids produces interleaved lines with correct `lineSource` values and `InvoiceTaxService.recalculateInvoiceTotals` sums them all, (2) `Invoice.subtotal`, `Invoice.taxTotal`, `Invoice.total` reflect all three sources, (3) forced rollback (e.g. throw in a post-create hook via a @TestConfiguration bean) leaves disbursements UNBILLED — prove the `markBilled` mutation is in the same `@Transactional` as the invoice insert. Pattern: existing `InvoiceCreationServiceTest` mixed-draft scenarios. |

### Key Files

**Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementUnbilledEndpointTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCreationDisbursementTest.java`

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementRepository.java` — add `findUnbilledBillableByCustomerId`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementService.java` — add `listUnbilledForProject`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementController.java` — add `/unbilled` endpoint
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeService.java` — module-gated disbursements list
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeResponse.java` — add `disbursements` field
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceLine.java` — add `disbursementId`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/CreateInvoiceRequest.java` — add `disbursementIds`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCreationService.java` — add `createDisbursementLines` + wire into `createDraft`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceTaxService.java` — verify DISBURSEMENT-source coverage

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/expense/ExpenseRepository.java` — `findUnbilledBillableByCustomerId` shape
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCreationService.java` — existing time-entry + expense parallel paths
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeService.java` — current DTO composition

### Architecture Decisions

- **Parallel-path integration, not a shared `Billable` interface** ([ADR-247](../adr/ADR-247-legal-disbursement-sibling-entity.md)). The codebase uses explicit `timeEntryIds` + `expenseIds` in `createDraft`; adding `disbursementIds` as a third list matches the existing style.
- **Tax-rate resolution happens in `InvoiceCreationService`**, not on the disbursement entity. The entity only carries a `vatTreatment` enum string; the rate lookup is scoped to invoice creation.
- **`markBilled` runs inside the same `@Transactional`** as invoice insert so a rollback unbinds. Covered by an explicit test.
- **`UnbilledTimeService` stays backward-compatible** for non-legal tenants — the `disbursements` list stays empty when the module is disabled, so existing callers see no schema drift.

### Non-scope

- No shared `Billable` / `UnbilledBillableItem` interface (explicitly rejected by [ADR-247](../adr/ADR-247-legal-disbursement-sibling-entity.md)).
- No frontend invoice-editor picker (488B).
- No new `TaxRate` rows or tax engine changes — purely a lookup from the existing `tax_rates` table.
- No auto-inclusion of unbilled disbursements — selection remains explicit by caller.

---

## Epic 488: Disbursement Frontend

**Goal**: Deliver the full frontend surface for disbursements — standalone list page, detail page, creation dialog, matter-scoped tab, approval panel, trust-link picker, and invoice-editor picker — all module-gated and capability-aware.

**References**: Architecture Section 67.9.2 (frontend file map), 67.4.1 (API contract), 67.3.1 (UX states).

**Dependencies**: 486B (API contract must exist for 488A to compile client code; 488A can start against the 486A entity shape as a mock while 486B lands), 487B (invoice-editor picker).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **488A** | 488.1–488.7 | `frontend/lib/api/legal-disbursements.ts` API client, disbursements list page, disbursement detail page, create-disbursement dialog (category picker + VAT override + payment-source toggle + supplier fields + receipt upload), matter-scoped disbursements tab, unbilled-summary widget extension, list-view component tests. **Done** (PR #1073) |
| **488B** | 488.8–488.12 | Disbursement approval panel (capability-gated visibility), trust-transaction-link dialog (picker of APPROVED DISBURSEMENT_PAYMENT trust txs for the matter), invoice-editor "Add Disbursements" picker (module-gated), component tests for approval action + trust-link correctness + invoice-editor integration. **Done** (PR #1074) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 488.1 | Create `frontend/lib/api/legal-disbursements.ts` API client | 488A | 486A contract | New file. Exports: `listDisbursements`, `getDisbursement`, `createDisbursement`, `updateDisbursement`, `submitForApproval`, `approveDisbursement`, `rejectDisbursement`, `writeOffDisbursement`, `uploadReceipt`, `listUnbilled`. Typed request/response matching arch §67.4.1 JSON. Uses existing `lib/api/client.ts` fetch wrapper. Pattern: `frontend/lib/api/expenses.ts` / `frontend/lib/api/trust-transactions.ts`. |
| 488.2 | Create disbursements list page | 488A | 488.1 | New file: `frontend/app/(app)/org/[slug]/legal/disbursements/page.tsx`. `"use client"`. Wrapped in `<ModuleGate module="disbursements">`. Filters: project, category (9 values), approval status, billing status, date range. "New Disbursement" button opens create dialog. Table columns: incurred date, matter, category, description, supplier, amount (incl VAT), approval status chip, billing status chip. Row click → detail page. Pattern: existing `frontend/app/(app)/org/[slug]/legal/adverse-parties/page.tsx` for module-gated list layout. |
| 488.3 | Create disbursement detail page | 488A | 488.1 | New file: `frontend/app/(app)/org/[slug]/legal/disbursements/[id]/page.tsx`. Shows all row fields, linked receipt document (download link), linked invoice line (if BILLED), linked trust transaction (if TRUST_ACCOUNT). Shows approval action buttons only when `approvalStatus=PENDING_APPROVAL` AND user has `APPROVE_DISBURSEMENTS` (approval panel is owned by 488B — stub here with a placeholder). "Upload receipt" button → multipart upload. "Edit" button visible when DRAFT/PENDING_APPROVAL. Pattern: `frontend/app/(app)/org/[slug]/expenses/[id]/page.tsx`. |
| 488.4 | Create `create-disbursement-dialog.tsx` | 488A | 488.1 | New file: `frontend/components/legal/create-disbursement-dialog.tsx`. Form fields per arch §67.4.1 body: project + customer pickers, category (9-item Select), description (Textarea), amount (CurrencyInput ZAR), VAT treatment (Select, defaults from category per arch §67.3.1 VAT default table — implement helper `defaultVatTreatmentForCategory` in `lib/legal/disbursement-defaults.ts`), payment source (RadioGroup), `trust_transaction_id` picker only visible when `paymentSource=TRUST_ACCOUNT` (stub here; slot for trust-link dialog from 488B), incurred date (DatePicker), supplier name, supplier reference (optional), receipt upload (optional FileUpload). Client-side validation mirrors backend CHECK constraints. Include `afterEach(() => cleanup())` note in forthcoming test. Pattern: `frontend/components/expense/create-expense-dialog.tsx`. |
| 488.5 | Create matter-scoped disbursements tab | 488A | 488.2, 488.4 | New file: `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/disbursements.tsx`. Wrapped in `<ModuleGate module="disbursements">`. Reuses a list-view component (extract shared `components/legal/disbursement-list-view.tsx` in this task — returns rows for a given project filter) and the create dialog. Pattern: existing Phase 55 `(tabs)/court-dates.tsx` tab. |
| 488.6 | Extend unbilled-summary widget on matter detail | 488A | 487A | Modify: `frontend/components/project/unbilled-summary.tsx` (verify name by searching if needed). Add a third sub-total "Unbilled Disbursements" only when `useVerticalModule("disbursements").enabled`. Click-through opens the matter disbursements tab scoped to unbilled. Must remain byte-compatible for non-legal tenants (widget render must not break when the field is absent from the response). |
| 488.7 | Component tests: list + create dialog + tab + widget | 488A | 488.2–488.6 | New files under `frontend/__tests__/legal/`: `disbursement-list.test.tsx` (~4 tests — filter application, status chip rendering, row click navigation, empty state), `create-disbursement-dialog.test.tsx` (~5 tests — category selection defaults VAT, payment source toggle shows/hides trust slot, amount positivity validation, successful POST, error display), `disbursements-tab.test.tsx` (~2 tests — renders under ModuleGate, non-legal profile returns null). Include `afterEach(() => cleanup())` per frontend/CLAUDE.md. Pattern: existing Phase 55 component tests. |
| 488.8 | Create `disbursement-approval-panel.tsx` | 488B | 488.3 | New file: `frontend/components/legal/disbursement-approval-panel.tsx`. Visible on a disbursement detail page only when `approvalStatus=PENDING_APPROVAL` AND the user has `APPROVE_DISBURSEMENTS` capability (via existing `useCapability(...)` hook). Approve + Reject buttons each open a small note-input dialog; submit calls `approveDisbursement` / `rejectDisbursement`. Pattern: existing trust-transaction approval panel in `frontend/components/legal/trust/`. |
| 488.9 | Create `trust-transaction-link-dialog.tsx` | 488B | 488.4 | New file: `frontend/components/legal/trust-transaction-link-dialog.tsx`. Shown when user toggles `paymentSource=TRUST_ACCOUNT` in the create-disbursement dialog. Fetches approved trust transactions for the current matter via existing Phase 60 trust API (`GET /api/trust-transactions?projectId={id}&status=APPROVED&type=DISBURSEMENT_PAYMENT`). Lists them by date + amount + supplier; on select, populates `trust_transaction_id` in the create dialog and disables the amount input (trust tx amount wins). |
| 488.10 | Wire approval panel into detail page | 488B | 488.8 | Modify: `frontend/app/(app)/org/[slug]/legal/disbursements/[id]/page.tsx`. Replace stub placeholder from 488A with the real `<DisbursementApprovalPanel />`. Ensure trust-tx link is rendered as a section on the detail page too (read-only display linking into the Phase 60 trust-transaction detail). |
| 488.11 | Invoice-editor "Add Disbursements" picker | 488B | 487B, 488.1 | New file: `frontend/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker.tsx`. Wrapped in `<ModuleGate module="disbursements">`. Invoked from the invoice-editor "Add" menu. Calls `listUnbilled({ projectId })`, renders a table with checkboxes. On submit, passes selected ids into the existing invoice-draft update flow (extend existing update payload with `disbursementIds`). Pattern: existing invoice-editor expense picker. Modify the parent invoice-editor page (if needed) to register the new picker button next to expense picker. |
| 488.12 | Component tests: approval panel + trust-link + invoice-editor picker | 488B | 488.8–488.11 | New files under `frontend/__tests__/legal/`: `disbursement-approval-panel.test.tsx` (~4 tests — renders for user with capability, hidden without capability, approve happy path, reject requires notes), `trust-transaction-link-dialog.test.tsx` (~3 tests — lists only APPROVED DISBURSEMENT_PAYMENT txs for this matter, empty state, selection populates the parent form), `add-disbursements-picker.test.tsx` (~3 tests — module-gated hide, selection submits `disbursementIds`, checkbox state persists across re-open). Include `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `frontend/lib/api/legal-disbursements.ts`
- `frontend/lib/legal/disbursement-defaults.ts`
- `frontend/app/(app)/org/[slug]/legal/disbursements/page.tsx`
- `frontend/app/(app)/org/[slug]/legal/disbursements/[id]/page.tsx`
- `frontend/components/legal/create-disbursement-dialog.tsx`
- `frontend/components/legal/disbursement-list-view.tsx`
- `frontend/components/legal/disbursement-approval-panel.tsx`
- `frontend/components/legal/trust-transaction-link-dialog.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/disbursements.tsx`
- `frontend/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker.tsx`
- `frontend/__tests__/legal/disbursement-list.test.tsx`
- `frontend/__tests__/legal/create-disbursement-dialog.test.tsx`
- `frontend/__tests__/legal/disbursements-tab.test.tsx`
- `frontend/__tests__/legal/disbursement-approval-panel.test.tsx`
- `frontend/__tests__/legal/trust-transaction-link-dialog.test.tsx`
- `frontend/__tests__/legal/add-disbursements-picker.test.tsx`

**Modify:**
- `frontend/components/project/unbilled-summary.tsx` — add disbursements sub-total (module-gated)
- `frontend/app/(app)/org/[slug]/invoices/[id]/edit/page.tsx` (or equivalent) — wire "Add Disbursements" picker button

**Read for context:**
- `frontend/components/expense/create-expense-dialog.tsx` — sibling create-dialog shape
- `frontend/app/(app)/org/[slug]/legal/adverse-parties/page.tsx` — module-gated list layout
- `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/court-dates.tsx` — matter tab pattern
- `frontend/components/legal/trust/*` — trust approval panel shape (if present)

### Architecture Decisions

- **All new surfaces wrap `<ModuleGate module="disbursements">`** — non-legal tenants render nothing, no React warnings on missing data.
- **Approval panel is capability-gated via `useCapability("APPROVE_DISBURSEMENTS")`**, not role-name checks.
- **Default VAT per category is computed on the client** (helper in `lib/legal/disbursement-defaults.ts`) to match backend arch §67.3.1 table. Backend still authoritative — if the caller overrides, the override wins.
- **Trust-link dialog filters server-side** (existing trust API supports `status` + `type` query params); no client-side over-filtering.

### Non-scope

- No statement-of-account UI (491B).
- No matter-closure UI (490).
- No bulk-import / CSV upload UI.
- No mobile-specific layout — uses existing responsive breakpoints.
- No new terminology keys — reuses existing `en-ZA-legal`.

---

## Epic 489: Matter Closure Workflow (Backend)

**Goal**: Introduce the `CLOSED` matter lifecycle state with its compliance-gate evaluation, owner-only override path, retention-clock start, closure-letter generation, notification fan-out, and full REST surface. Split across two slices so that 489A ships pure foundation (migration + entities + pure-function gate classes) and 489B layers in the transactional orchestrator + side-effects.

**References**: Architecture Sections 67.2.2, 67.2.3, 67.3.4, 67.3.5, 67.3.6, 67.4.2, 67.8.2, 67.9.1; [ADR-248](../adr/ADR-248-matter-closure-distinct-state-with-gates.md), [ADR-249](../adr/ADR-249-retention-clock-starts-on-closure.md).

**Dependencies**: 486A (two of the nine gates query `legal_disbursements`, which must exist). No dependency on 486B — the gates hit the repository directly.

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **489A** | 489.1–489.10 | V97 migration (projects CLOSED status + `closed_at` + `matter_closure_log` table + `invoice_lines.disbursement_id` + `invoice_lines.line_source` extended + `retention_policy.cancelled_at` + `document_templates.acceptance_eligible`), `Project.java` + `ProjectStatus.java` + `ProjectLifecycleGuard.java` extensions, `MatterClosureLog` entity + repository, `ClosureGate` interface + `GateResult` record + 9 concrete gate implementations, `matter_closure` module registered in `VerticalModuleRegistry`, `OrgSettings.legalMatterRetentionYears` field added, per-gate unit tests. Higher file count (~15) is tolerable because each gate class is ~30–60 lines. **Done** (PR #1069) |
| **489B** | 489.11–489.20 | `MatterClosureService` (evaluate/close/reopen), retention-policy insert + soft-cancel wiring, 2 domain events (`MatterClosedEvent`, `MatterReopenedEvent`), `MatterClosureNotificationHandler` (Phase 6.5 pattern), `MatterClosureContextBuilder`, `MatterClosureController`, `matter-closure-letter.json` Tiptap template + pack.json manifest entry + `TemplatePackSeeder` wiring of `acceptance_eligible` flag, service + controller integration tests for all-pass path, per-gate failure, override (with + without capability), reopen (both within and beyond retention), notification fan-out. **Done** (PR #1070) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 489.1 | Create V97 migration | 489A | 486A (V96 must land first) | New file: `backend/src/main/resources/db/migration/tenant/V97__matter_closure_and_invoice_line_disbursement.sql` (rename to next available V-number at implementation — likely **V101**; preserve architecture's intent in a leading SQL comment). DDL exactly per arch §67.8.2: (1) drop + re-add `projects.status` CHECK to include `CLOSED`, add `projects.closed_at TIMESTAMPTZ`; (2) create `matter_closure_log` table with 13 columns, 3 CHECK constraints (reason enum set, override justification required + ≥20 chars when override, reopen fields consistent), 2 indexes (`(project_id, closed_at DESC)` + partial on `override_used=true`); (3) drop + re-add `invoice_lines.line_source` CHECK to include `DISBURSEMENT`, add `invoice_lines.disbursement_id UUID REFERENCES legal_disbursements(id) ON DELETE RESTRICT`, partial index `WHERE disbursement_id IS NOT NULL`; (4) `ALTER TABLE retention_policy ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ`; (5) `ALTER TABLE document_templates ADD COLUMN acceptance_eligible BOOLEAN NOT NULL DEFAULT false`. Coordination with 487B: slice 487B needs the `invoice_lines.disbursement_id` column to map in `InvoiceLine.java`. Both slices have a hard dependency on this migration. |
| 489.2 | Extend `Project`, `ProjectStatus`, `ProjectLifecycleGuard` | 489A | 489.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectStatus.java` — add `CLOSED` enum constant. Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java` — add `@Column(name="closed_at") private Instant closedAt;` field with getter, add domain method `closeMatter(ClosureRequest req)` (delegates to lifecycle guard; sets status + closedAt) and `reopenMatter()` (clears closedAt; transitions CLOSED → ACTIVE). Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectLifecycleGuard.java` — add two valid transitions: `ACTIVE → CLOSED`, `COMPLETED → CLOSED`, `CLOSED → ACTIVE`. Pattern: existing `complete()`, `archive()`, `reopen()` methods. |
| 489.3 | Create `MatterClosureLog` entity + repository | 489A | 489.1 | New files under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/`: `MatterClosureLog.java` with fields per arch §67.2.2 (id, projectId, closedBy, closedAt, reason, notes, gateReport as JSONB via `@JdbcTypeCode(SqlTypes.JSON)`, overrideUsed, overrideJustification, closureLetterDocumentId, reopenedAt, reopenedBy, reopenNotes, createdAt), plus `MatterClosureLogRepository extends JpaRepository<MatterClosureLog, UUID>` with `findByProjectIdOrderByClosedAtDesc(UUID)` and `findTopByProjectIdOrderByClosedAtDesc(UUID)`. Pattern: existing `AuditEvent.java` for JSONB columns. |
| 489.4 | Create `ClosureGate` interface + `GateResult` record | 489A | -- | New files in `verticals/legal/closure/`: `ClosureGate.java` interface with `String code()`, `GateResult evaluate(Project project)`, `int order()` per arch §67.3.4. `GateResult.java` record `{ boolean passed, String code, String message, Map<String,Object> detail }`. |
| 489.5 | Implement 9 `ClosureGate` beans | 489A | 489.4, 486A (for 2 gates) | New files in `verticals/legal/closure/gates/`: `TrustBalanceZeroGate.java` (order=1, calls existing `ClientLedgerService.getBalanceForMatter`), `AllDisbursementsApprovedGate.java` (order=2, `DisbursementRepository.countByProjectIdAndApprovalStatusIn(projectId, List.of("DRAFT","PENDING_APPROVAL"))`), `AllDisbursementsSettledGate.java` (order=3, count billing_status='UNBILLED' AND approval_status='APPROVED'), `FinalBillIssuedGate.java` (order=4, joins `invoices` + `time_entries` + `legal_disbursements` — delegate query to existing service or write a @Query repo method), `NoOpenCourtDatesGate.java` (order=5, count from `court_dates`), `NoOpenPrescriptionsGate.java` (order=6, count from `prescription_trackers`), `AllTasksResolvedGate.java` (order=7, count from `tasks`), `AllInfoRequestsClosedGate.java` (order=8, count from `information_requests`), `AllAcceptanceRequestsFinalGate.java` (order=9, count from `acceptance_requests`). Each class `@Component`, pure function — no side effects, no writes. Failure message template per arch §67.3.4 table. Use existing repository beans; do not create duplicates. |
| 489.6 | Register `matter_closure` module | 489A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java`. Add module id `"matter_closure"` auto-enabled under `legal-za`. |
| 489.7 | Add `legalMatterRetentionYears` to `OrgSettings` | 489A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java`. Add `@Column(name="legal_matter_retention_years") private Integer legalMatterRetentionYears;` with getter defaulting to `5` (null → 5). No migration needed if schema already permits nullable additions; otherwise include a small ALTER in V97 or piggy-back existing pattern. Pattern: existing retention-related columns. |
| 489.8 | Coexistence smoke-test for `matter_closure` module | 489A | 489.6 | Modify: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/MultiVerticalCoexistenceTest.java`. Add: legal-za tenant → `matter_closure` enabled; accounting-za + consulting-za → disabled. |
| 489.9 | Per-gate unit tests (one class per gate) | 489A | 489.5 | New files under `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/`: `TrustBalanceZeroGateTest.java`, `AllDisbursementsApprovedGateTest.java`, `AllDisbursementsSettledGateTest.java`, `FinalBillIssuedGateTest.java`, `NoOpenCourtDatesGateTest.java`, `NoOpenPrescriptionsGateTest.java`, `AllTasksResolvedGateTest.java`, `AllInfoRequestsClosedGateTest.java`, `AllAcceptanceRequestsFinalGateTest.java`. Each ~3 tests: passes on clean fixture, fails on seeded violation(s), failure message interpolates the correct count / amount. Use `@SpringBootTest` or lighter `@DataJpaTest` as each gate only reads. Pattern: existing Phase 60 ledger-service tests for seeding helpers. |
| 489.10 | Project-lifecycle + repository integration tests | 489A | 489.2, 489.3 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/project/ProjectLifecycleClosureTest.java`. ~4 tests: `ACTIVE → CLOSED` transition succeeds and stamps `closedAt`, `COMPLETED → CLOSED` succeeds, `CLOSED → ACTIVE` clears `closedAt`, illegal `ARCHIVED → CLOSED` throws. New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureLogRepositoryTest.java` — ~3 tests: insert with override_used=true + justification <20 chars fails CHECK, reopen fields must all be set or all null (CHECK), `findTopByProjectIdOrderByClosedAtDesc` returns latest. |
| 489.11 | Implement `MatterClosureService.evaluate` | 489B | 489A | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java`. Inject `List<ClosureGate> gates`, `ProjectRepository`, `MatterClosureLogRepository`, `VerticalModuleGuard`, `ApplicationEventPublisher`, `RetentionPolicyRepository` or service, `DocumentGenerationService`. Implement `MatterClosureReport evaluate(UUID projectId)` per arch §67.3.4 pseudocode: verify module enabled, fetch project, run every gate sorted by `order()`, assemble report. Return `MatterClosureReport(projectId, allPassed, List<GateResult>, Instant evaluatedAt)`. Pattern: existing orchestrator services in `verticals/legal/`. |
| 489.12 | Implement `MatterClosureService.close` + override branch | 489B | 489.11 | Same file. Implement `void close(UUID projectId, ClosureRequest req)` per arch §67.3.5 happy path: (1) re-evaluate gates, (2) if any failed AND `!req.override` → throw `ClosureGateFailedException` with report attached, (3) if override, re-check `OVERRIDE_MATTER_CLOSURE` capability via `CapabilityAuthorizationService` and assert justification ≥ 20 non-whitespace chars, (4) `project.closeMatter(req)` and save, (5) insert `MatterClosureLog` with serialised `gate_report` + `override_used` + `override_justification`, (6) insert `RetentionPolicy` (entityType=PROJECT, entityId=projectId, retentionStart=now, retentionYears=`OrgSettings.legalMatterRetentionYears` default 5), (7) if `req.generateClosureLetter`: call `DocumentGenerationService.generate("matter-closure-letter", contextBuilder.build(projectId, req))` — on failure log warning but DO NOT roll back transaction; stamp `closureLetterDocumentId` on the log row, (8) publish `MatterClosedEvent`. Everything 4–7 in a single `@Transactional` scope (8 fires post-commit via event bus). |
| 489.13 | Implement `MatterClosureService.reopen` | 489B | 489.12 | Same file. `void reopen(UUID projectId, String notes)` per arch §67.3.6: verify `notes.length() >= 10`, look up the matching retention-policy row — if `retentionStart + retentionYears` elapsed AND data already purged (`cancelledAt IS NULL` check or equivalent) throw `RetentionElapsedException`, else soft-cancel by setting `cancelled_at = now()`, then transition project back to ACTIVE via `project.reopenMatter()`, update the most recent closure-log row's `reopenedAt` / `reopenedBy` / `reopenNotes`, publish `MatterReopenedEvent`. |
| 489.14 | Create 2 domain events + notification handler | 489B | 489.12, 489.13 | New files in `verticals/legal/closure/event/`: `MatterClosedEvent.java` (record: `UUID projectId, UUID closureLogId, String reason, boolean override, UUID closedBy, Instant occurredAt`), `MatterReopenedEvent.java` (record: `UUID projectId, UUID reopenedBy, String notes, Instant occurredAt`). New file: `verticals/legal/closure/event/MatterClosureNotificationHandler.java` — `@Component` with `@EventListener` handlers sending in-app notifications (via existing Phase 6.5 `NotificationService`) to the matter owner + all org admins on both events. Pattern: existing `TrustTransactionNotificationHandler`. |
| 489.15 | Implement `MatterClosureContextBuilder` | 489B | 489.12 | New file: `verticals/legal/closure/MatterClosureContextBuilder.java`. `@Component`. `public Map<String, Object> build(UUID projectId, ClosureRequest req)` assembling the context for `matter-closure-letter` template (per arch §67.9.1 and the template's variable list from requirements §2.6): `project.name`, `customer.name`, `closure.reason`, `closure.date`, `closure.notes`, `matter.total_fees_billed` (query), `matter.total_disbursements` (sum `legal_disbursements.amount + vat_amount` where projectId + billing_status='BILLED'), `matter.duration_months`, `org.name`, `org.principal_attorney`. |
| 489.16 | Implement `MatterClosureController` | 489B | 489.11, 489.12, 489.13 | New file: `verticals/legal/closure/MatterClosureController.java`. `@RestController @RequestMapping("/api/matters/{projectId}/closure") @VerticalModuleGuard("matter_closure")`. Endpoints per arch §67.4.2: `GET /evaluate` (`@RequiresCapability("VIEW_LEGAL")`), `POST /close` (`@RequiresCapability("CLOSE_MATTER")`; programmatic check for `OVERRIDE_MATTER_CLOSURE` when body `override=true`), `POST /reopen` (`@RequiresCapability("CLOSE_MATTER")`), `GET /log` (`@RequiresCapability("VIEW_LEGAL")`). Map `ClosureGateFailedException` → 409 ProblemDetail with the full report in `report` extension; `RetentionElapsedException` → 409; override-missing-capability → 403. |
| 489.17 | Ship `matter-closure-letter.json` system template + manifest entry + seeder wiring | 489B | 489.1 | New file: `backend/src/main/resources/template-packs/legal-za/matter-closure-letter.json` — Tiptap JSON doc with variables per 489.15. Modify: `backend/src/main/resources/template-packs/legal-za/pack.json` — append entry `{"templateKey": "matter-closure-letter", "name": "Matter Closure Letter", "category": "CLOSURE", "primaryEntityType": "PROJECT", "contentFile": "matter-closure-letter.json", "sortOrder": 10, "acceptanceEligible": false}` and bump `version` (currently 2 → 3). Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` — wire the new manifest field `acceptanceEligible` through to `DocumentTemplate.acceptanceEligible` (new boolean field on entity from V97 column, defaults false; omitted manifest entries remain false). Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` — add `@Column(name="acceptance_eligible", nullable=false) private boolean acceptanceEligible = false;` with getter. |
| 489.18 | Service integration test: all-pass + per-gate-fail + override paths | 489B | 489.12 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureServiceIntegrationTest.java`. ~10 tests: (1) seed clean matter, all 9 gates pass, `close()` succeeds: project.status=CLOSED, log row inserted with serialised gate_report, RetentionPolicy row inserted with retentionEndsAt = closureDate + 5y, closure letter document id stamped, MatterClosedEvent fires, (2) single gate failing (trust balance 4200) → ClosureGateFailedException, project remains ACTIVE, no log row, (3) `close(override=true)` without OVERRIDE_MATTER_CLOSURE → 403, (4) `close(override=true)` with capability + justification <20 chars → 422, (5) `close(override=true)` with everything → succeeds, `override_used=true`, `override_justification` persisted, (6) generateClosureLetter=false → no closure-letter doc, no closureLetterDocumentId, (7) DocumentGenerationService failure → closure still commits, warning logged, (8) `reopen()` within retention — project ACTIVE, retention cancelled_at stamped, latest log row reopened fields set, MatterReopenedEvent fires, (9) `reopen()` beyond retention + purged → RetentionElapsedException, (10) `reopen()` notes <10 chars → 422. |
| 489.19 | Controller integration test | 489B | 489.16 | New file: `MatterClosureControllerIntegrationTest.java`. ~6 tests: 200 on `GET /evaluate` happy path, 200 on `POST /close` happy, 409 on failing-gate no-override (ProblemDetail includes `report` field with full gate list), 403 on override without capability, 200 on override with capability, 404 on non-legal tenant (module guard). Pattern: `DisbursementControllerIntegrationTest.java` from slice 486B. |
| 489.20 | Notification-handler integration test | 489B | 489.14 | New file: `MatterClosureNotificationHandlerTest.java`. ~2 tests: owner + admins receive `MATTER_CLOSED` in-app notification when MatterClosedEvent fires; matter member does NOT. Uses existing Phase 6.5 notification test harness. |

### Key Files

**Create:**
- `backend/src/main/resources/db/migration/tenant/V97__matter_closure_and_invoice_line_disbursement.sql` (rename to next available V-number)
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureLog.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureLogRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/ClosureGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/GateResult.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureReport.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/TrustBalanceZeroGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/AllDisbursementsApprovedGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/AllDisbursementsSettledGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/FinalBillIssuedGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/NoOpenCourtDatesGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/NoOpenPrescriptionsGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/AllTasksResolvedGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/AllInfoRequestsClosedGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/AllAcceptanceRequestsFinalGate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/event/MatterClosedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/event/MatterReopenedEvent.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/event/MatterClosureNotificationHandler.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/dto/ClosureRequest.java`
- `backend/src/main/resources/template-packs/legal-za/matter-closure-letter.json`
- 9 per-gate unit test files + `MatterClosureServiceIntegrationTest.java` + `MatterClosureControllerIntegrationTest.java` + `MatterClosureNotificationHandlerTest.java` + `MatterClosureLogRepositoryTest.java` + `ProjectLifecycleClosureTest.java`

**Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectStatus.java` — add `CLOSED`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/Project.java` — add `closedAt` + `closeMatter`/`reopenMatter` methods
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/project/ProjectLifecycleGuard.java` — add transitions
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java` — register `matter_closure`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/settings/OrgSettings.java` — add `legalMatterRetentionYears`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentTemplate.java` — add `acceptanceEligible`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/TemplatePackSeeder.java` — wire manifest flag
- `backend/src/main/resources/template-packs/legal-za/pack.json` — append manifest entry + version bump
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/MultiVerticalCoexistenceTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java` — `getBalanceForMatter`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/retention/RetentionPolicy.java` and its service — Phase 50 primitives
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentGenerationService.java` — generation contract
- Phase 6.5 notification handler pattern under `notification/`

### Architecture Decisions

- **`CLOSED` is legal-specific terminal state** ([ADR-248](../adr/ADR-248-matter-closure-distinct-state-with-gates.md)), distinct from `COMPLETED`/`ARCHIVED`. Non-legal tenants can never reach it because the only call site is module-guarded.
- **Retention clock starts on CLOSE, not ARCHIVE** ([ADR-249](../adr/ADR-249-retention-clock-starts-on-closure.md)). Reopen soft-cancels via `cancelled_at`; expired-and-purged matters can't be reopened.
- **Gate interface allows adding 10th gate with a single class drop**, no service change. Current 9 are exhaustive for Phase 67's demo loop.
- **Closure-letter failure does not abort the close** — letter is cosmetic, closure is compliance. Mismatch is logged and exposed on the closure-log row as null `closure_letter_document_id`, letting the UI retry.
- **`matter_closure` is a separate module from `disbursements`** — some firms may want closure without disbursements (unusual but possible); the arch permits it and the registry honours independent module toggles.

### Non-scope

- No frontend — 490 owns UI.
- No standalone matter-closure audit report (uses existing Phase 6.5 audit).
- No multi-reopen history beyond the single `reopened_*` triplet on the most-recent log row (deliberate: reopen updates the last closure, not a new row).
- No auto-evaluation daemon — evaluation is on-demand via `GET /evaluate`.

---

## Epic 490: Matter Closure Frontend

**Goal**: Deliver the matter-closure UI — a 3-step dialog combining gate preview, closure form, and override flow — plus reopen action and `CLOSED` matter-list filter.

**References**: Architecture Sections 67.9.2 (frontend file map), 67.5.2 and 67.5.3 (sequence diagrams for happy + override paths), 67.4.2 (API contract).

**Dependencies**: 489B (closure controller must exist).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **490A** | 490.1–490.6 | `frontend/lib/api/matter-closure.ts` API client, 3-step matter-closure dialog (gate-report preview → closure form → override branch), `matter-closure-report.tsx` sub-component with per-gate pass/fail rows and "fix this" deep links, wire "Close Matter" action into matter-detail action menu (module-gated + status-gated to ACTIVE/COMPLETED), component tests for gate-report rendering, override-visibility by role, success-flow, 409 ProblemDetail handling. **Done** (PR #1075) |
| **490B** | 490.7–490.10 | "Reopen Matter" action on CLOSED matters (owner only via `useCapability("CLOSE_MATTER")`), matter-list `CLOSED` filter chip, default-filter excludes CLOSED matters, status badge CLOSED variant, reopen-flow component tests. **Done** (PR #1076) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 490.1 | Create `frontend/lib/api/matter-closure.ts` API client | 490A | 489B | New file. Exports: `evaluateClosure(projectId)`, `closeMatter(projectId, request)`, `reopenMatter(projectId, notes)`, `listClosureLog(projectId)`. Types matching arch §67.4.2 JSON. Surfaces the 409 `ClosureGatesFailed` ProblemDetail with its `report` extension as a typed error the caller can introspect. |
| 490.2 | Create `matter-closure-report.tsx` sub-component | 490A | -- | New file: `frontend/components/legal/matter-closure-report.tsx`. Props: `gates: GateResult[]`. Renders each with a green check or red X, the interpolated message, and — for failing gates — a "Fix this" CTA that deep-links to the relevant page. Mapping (gate `code` → deep link route): `TRUST_BALANCE_ZERO` → trust dashboard for matter, `ALL_DISBURSEMENTS_APPROVED`/`ALL_DISBURSEMENTS_SETTLED` → disbursements tab on matter, `FINAL_BILL_ISSUED` → invoices list filtered by matter, `NO_OPEN_COURT_DATES` → court calendar filtered by matter, `NO_OPEN_PRESCRIPTIONS` → prescription tracker, `ALL_TASKS_RESOLVED` → tasks tab, `ALL_INFO_REQUESTS_CLOSED` → info requests list, `ALL_ACCEPTANCE_REQUESTS_FINAL` → acceptance requests list. |
| 490.3 | Create `matter-closure-dialog.tsx` 3-step flow | 490A | 490.1, 490.2 | New file: `frontend/components/legal/matter-closure-dialog.tsx`. Wrapped in `<ModuleGate module="matter_closure">`. Step 1: on open, call `evaluateClosure` and render `<MatterClosureReport>`. Step 2: closure form — `reason` Select (CONCLUDED / CLIENT_TERMINATED / REFERRED_OUT / OTHER per arch §67.3.5), `notes` Textarea, `generateClosureLetter` Checkbox (default true). Step 3: if any gate fails AND caller has `OVERRIDE_MATTER_CLOSURE` capability, show "Override and close" toggle + justification Textarea (≥20 chars client-side validation). If caller lacks the capability and gates fail, show "Cannot close — resolve gates" final state with link back to step 1. Submit → `closeMatter(projectId, req)`, handle 409 by re-rendering step 1 with fresh report. |
| 490.4 | Wire "Close Matter" action into matter-detail page | 490A | 490.3 | Modify: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. In the action menu/button row, add "Close Matter" entry — visible only when `useVerticalModule("matter_closure").enabled` AND `project.status in ['ACTIVE','COMPLETED']` AND `useCapability("CLOSE_MATTER")`. Clicking opens `<MatterClosureDialog>`. |
| 490.5 | Component tests: gate report + dialog steps + override visibility | 490A | 490.2, 490.3 | New files: `frontend/__tests__/legal/matter-closure-report.test.tsx` (~5 tests — renders pass/fail rows, interpolated messages, fix-this deep links navigate correctly, empty-gates edge case, red-X styling for failures), `frontend/__tests__/legal/matter-closure-dialog.test.tsx` (~7 tests — step 1 shows report on open, step 2 collects reason+notes, step 3 override toggle hidden without capability, step 3 override toggle visible with capability + justification <20 chars blocks submit, successful happy-path submit navigates and shows toast, 409 response re-renders step 1 with updated report, module-disabled returns null). Include `afterEach(() => cleanup())`. |
| 490.6 | Component test: matter-detail action visibility | 490A | 490.4 | Same `matter-closure-dialog.test.tsx` file, +3 tests: action hidden when status=ARCHIVED, hidden when module disabled, hidden when user lacks `CLOSE_MATTER`. |
| 490.7 | Wire "Reopen Matter" action | 490B | 490A | Modify: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx`. Add "Reopen Matter" entry visible only when `project.status=CLOSED` AND `useCapability("CLOSE_MATTER")`. Clicking opens a small dialog collecting `notes` (≥10 chars) and calling `reopenMatter`. Handle `RetentionElapsedException` (409) with a user-friendly "retention elapsed" message. |
| 490.8 | Add `CLOSED` filter chip + default-exclude on matter list | 490B | -- | Modify: `frontend/app/(app)/org/[slug]/projects/page.tsx`. Add `CLOSED` to the status filter chips (per Phase 66 conventions). Default filter excludes CLOSED (similar to how COMPLETED/ARCHIVED are handled today). Status badge component gets a `CLOSED` variant (color/icon — coordinate with design tokens). |
| 490.9 | Matter-detail status badge for CLOSED | 490B | 490.8 | Modify: the status-badge primitive used on `projects/[id]/page.tsx` (check existing component, extend its variant map). Add a `closed_at` display line near the badge. |
| 490.10 | Component tests: reopen + filter chip | 490B | 490.7, 490.8 | New file: `frontend/__tests__/legal/matter-reopen-dialog.test.tsx` (~4 tests — hidden when status != CLOSED, hidden without capability, notes <10 chars blocks submit, 409 retention-elapsed surfaces friendly error). Extend existing matter-list test to assert CLOSED chip renders and default filter excludes CLOSED rows. Include `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `frontend/lib/api/matter-closure.ts`
- `frontend/components/legal/matter-closure-dialog.tsx`
- `frontend/components/legal/matter-closure-report.tsx`
- `frontend/components/legal/matter-reopen-dialog.tsx`
- `frontend/__tests__/legal/matter-closure-dialog.test.tsx`
- `frontend/__tests__/legal/matter-closure-report.test.tsx`
- `frontend/__tests__/legal/matter-reopen-dialog.test.tsx`

**Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — Close + Reopen actions + CLOSED badge
- `frontend/app/(app)/org/[slug]/projects/page.tsx` — CLOSED filter chip + default filter

**Read for context:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — existing action-menu placement
- Phase 55 matter-detail action wiring
- Existing status-badge component

### Architecture Decisions

- **Override visibility is client-side hint only** — backend authoritative. The dialog hides the toggle if the user lacks `OVERRIDE_MATTER_CLOSURE`, but the server re-checks. If a user somehow posts `override=true` without the cap, backend returns 403.
- **"Fix this" deep links are best-effort** — clicking the link leaves the dialog, user fixes the gate, returns to the matter, re-opens the dialog. No persistent dialog state beyond the step position.
- **Reopen is modal, not inline** — keeps the matter-detail page layout stable and makes the retention-elapsed error prominent.
- **Status badge reuses existing variant primitive** — no new design token unless existing variant set cannot accommodate CLOSED.

### Non-scope

- No dashboard-level closure widget.
- No bulk-close operation (1 matter at a time).
- No mobile-specific closure layout.
- No closure-log viewer UI in this slice (arch §67.4.2 `GET /log` is reserved for audit surfacing; can be revisited in a polish phase).

---

## Epic 491: Statement of Account

**Goal**: Deliver Statement of Account generation end-to-end — context builder, system Tiptap template, API endpoints, generate dialog, and matter-scoped statements tab.

**References**: Architecture Sections 67.3.7, 67.4.3, 67.6 (variable namespace), 67.9.1; [ADR-250](../adr/ADR-250-statement-of-account-template-and-context.md).

**Dependencies**: 486B (`DisbursementService.listForStatement`), 489A (V97 adds `acceptance_eligible` column but 491 does not set it on the SoA template).

**Scope**: Both (backend + frontend)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **491A** | 491.1–491.6 | `StatementOfAccountContextBuilder` (4 sub-queries: fees, disbursements, trust activity, prior balance), `statement-of-account.json` system Tiptap template seeded under `template-packs/legal-za/`, pack.json manifest entry, `StatementController` under `/api/matters/{projectId}/statements` guarded by `@VerticalModuleGuard("disbursements")`, `StatementOfAccountGeneratedEvent`, context-builder integration tests (period filter, empty-period, trust activity inclusion, summary math), controller integration tests. **Done** (PR #1077) |
| **491B** | 491.7–491.10 | `statement-of-account-dialog.tsx` (period picker with defaults + preview + generate/save), matter-detail "Generate Statement of Account" action, `statements.tsx` matter-detail tab listing previously generated statements, `frontend/lib/api/statement-of-account.ts` API client, component tests. **Done** (PR #1078) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 491.1 | Implement `StatementOfAccountContextBuilder` | 491A | 486B | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java`. `@Component`. `public Map<String, Object> build(UUID projectId, LocalDate periodStart, LocalDate periodEnd)`. Assembles variable bundle per arch §67.6.1: `statement.*`, `matter.*`, `customer.*`, `fees.*` (via existing `TimeEntryService.findBillableInPeriod`), `disbursements.*` (via `DisbursementService.listForStatement`), `trust.*` (via `ClientLedgerService.getActivityForMatter` + `getBalanceAt`), `summary.*` (prior outstanding + payments via `InvoiceService.getBalanceSummary`), `org.*` (from `OrgSettings`). Empty sub-queries return empty lists / zero — Phase 31 `{% if %}` handles conditional rendering. Output is a flat `Map<String, Object>` with nested maps + records for list items. Pattern: existing context builders in `template/context/`. |
| 491.2 | Implement `StatementController` | 491A | 491.1 | New file: `verticals/legal/statement/StatementController.java`. `@RestController @RequestMapping("/api/matters/{projectId}/statements") @VerticalModuleGuard("disbursements")`. Endpoints per arch §67.4.3: `POST /` (`@RequiresCapability("GENERATE_STATEMENT_OF_ACCOUNT")`) — body `{periodStart, periodEnd, templateId?}`, builds context, calls `DocumentGenerationService.generate(templateIdOrDefault, context)`, publishes `StatementOfAccountGeneratedEvent`, returns 201 with `GeneratedDocument` + `summary` per arch §67.4.3; `GET /` (`@RequiresCapability("VIEW_LEGAL")`) paginated list of previously generated statements for this matter; `GET /{id}` returns single statement with `htmlPreview` + `pdfUrl`. Co-gated with `disbursements` module per [ADR-250](../adr/ADR-250-statement-of-account-template-and-context.md) since SoA aggregates disbursements. |
| 491.3 | Create `StatementOfAccountGeneratedEvent` | 491A | -- | New file: `verticals/legal/statement/event/StatementOfAccountGeneratedEvent.java`. Record: `UUID projectId, UUID generatedDocumentId, LocalDate periodStart, LocalDate periodEnd, UUID generatedBy, Instant occurredAt`. Wire an `AuditEvent` insert via an existing audit listener on this event type (`STATEMENT_GENERATED`). |
| 491.4 | Ship `statement-of-account.json` system Tiptap template | 491A | -- | New file: `backend/src/main/resources/template-packs/legal-za/statement-of-account.json`. Tiptap doc per arch §67.6.2: firm header → recipient block → matter reference → fees section (`{% for entry in fees.entries %}` iterator) → disbursements section (iterator grouped by category) → trust activity section (opening, deposits, payments, closing) → summary 6-row table → payment-instructions footer. Conditional sections on empty lists via `{% if fees.entries %}`. Modify: `backend/src/main/resources/template-packs/legal-za/pack.json` — append entry `{"templateKey": "statement-of-account", "name": "Statement of Account", "category": "STATEMENT", "primaryEntityType": "PROJECT", "contentFile": "statement-of-account.json", "sortOrder": 11, "acceptanceEligible": false}` and bump `version` (coordinate with 489.17 + 492B version bumps — final version number resolves in 493A's integration run). |
| 491.5 | Context-builder integration test | 491A | 491.1 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilderTest.java`. ~6 tests: (1) period with 3 fee entries + 2 disbursements + trust deposits — all sub-queries populated with expected totals, (2) empty period — every list empty, every aggregate zero, (3) period filter excludes out-of-range fees, (4) period filter excludes out-of-range disbursements, (5) trust opening/closing balance computed at exact period bounds, (6) summary.closing_balance_owing = previous + fees + disbursements - payments. Pattern: existing `VariableResolver`-style context tests. |
| 491.6 | Controller integration test | 491A | 491.2, 491.4 | New file: `StatementControllerIntegrationTest.java`. ~4 tests: 201 happy-path with system template, GeneratedDocument returned with htmlPreview + pdfUrl + summary, event fires, audit row inserted; 403 without `GENERATE_STATEMENT_OF_ACCOUNT`; 404 on accounting-za tenant (module guard); empty-period 201 with zeroed summary and rendered "no activity" HTML sections. Pattern: `DisbursementControllerIntegrationTest.java`. |
| 491.7 | Create `frontend/lib/api/statement-of-account.ts` | 491B | 491A | New file. Exports `generateStatement(projectId, req)`, `listStatements(projectId)`, `getStatement(projectId, id)`. Types match arch §67.4.3 JSON. |
| 491.8 | Create `statement-of-account-dialog.tsx` | 491B | 491.7 | New file: `frontend/components/legal/statement-of-account-dialog.tsx`. Wrapped in `<ModuleGate module="disbursements">` (co-gated per [ADR-250](../adr/ADR-250-statement-of-account-template-and-context.md)). Fields: `periodStart` (DatePicker, default = last statement date OR matter opening date), `periodEnd` (DatePicker, default = today), `templateId` (Select — defaults to system SoA, offers any tenant-owned clones). "Preview" → render returned `htmlPreview` in an iframe. "Generate PDF & Download" → open `pdfUrl` in new tab. "Save to Matter Documents" → the POST already saves as `GeneratedDocument`; confirm via toast and refresh the statements tab. |
| 491.9 | Create statements matter-detail tab | 491B | 491.7 | New file: `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/statements.tsx`. Wrapped in `<ModuleGate module="disbursements">`. Lists previously generated statements (via `listStatements`) with `generatedAt`, `periodStart–periodEnd`, `summary.closingBalanceOwing`. Row click opens statement detail in the existing Phase 12 generated-document viewer. Add "Generate Statement of Account" button that opens the dialog. Also add the action into the matter-detail action menu (parent page). |
| 491.10 | Component tests: dialog + statements tab | 491B | 491.8, 491.9 | New files: `frontend/__tests__/legal/statement-of-account-dialog.test.tsx` (~5 tests — default period calculation, preview renders iframe, generate success calls API + shows toast, validation on periodEnd < periodStart, module-disabled null), `frontend/__tests__/legal/statements-tab.test.tsx` (~3 tests — lists returned statements, empty state, row click opens doc viewer). Include `afterEach(() => cleanup())`. |

### Key Files

**Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilder.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementController.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/event/StatementOfAccountGeneratedEvent.java`
- `backend/src/main/resources/template-packs/legal-za/statement-of-account.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementOfAccountContextBuilderTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/statement/StatementControllerIntegrationTest.java`
- `frontend/lib/api/statement-of-account.ts`
- `frontend/components/legal/statement-of-account-dialog.tsx`
- `frontend/app/(app)/org/[slug]/projects/[id]/(tabs)/statements.tsx`
- `frontend/__tests__/legal/statement-of-account-dialog.test.tsx`
- `frontend/__tests__/legal/statements-tab.test.tsx`

**Modify:**
- `backend/src/main/resources/template-packs/legal-za/pack.json` — append SoA entry + version bump
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` — add "Generate Statement of Account" action

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/context/*ContextBuilder.java` — context-builder pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java` — `getActivityForMatter` / `getBalanceAt`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/DocumentGenerationService.java` — generation entry point
- Existing Phase 12 generated-document viewer

### Architecture Decisions

- **No new entity — statements ride `GeneratedDocument`** ([ADR-250](../adr/ADR-250-statement-of-account-template-and-context.md)). The statement is an informational artifact, not a domain fact.
- **SoA is co-gated by `disbursements` module** — it aggregates disbursements + trust + fees; the simplest coherent gate is disbursements. No separate `statement_of_account` module.
- **Empty sections render via `{% if %}`** — no special "no activity" backend path needed.
- **`{{retainer.hoursRemaining}}` and similar composed variables NOT introduced** — if the SoA template needs a computed field not in the namespace, compose inline in Tiptap rather than extending `VariableResolver`.

### Non-scope

- No scheduled / recurring SoA generation — manual-only this phase.
- No Section 86(5) trust-interest distinction in the SoA (out of scope per arch §67.12; Phase 61 trust reports cover that).
- No SoA email send — user downloads or attaches manually this phase.

---

## Epic 492: Conveyancing Pack

**Goal**: Ship the conveyancing matter-type as pure pack content — field pack, project template append, clause pack, document template pack with acceptance flag, and request pack — all routed through the Phase 65 install pipeline with no new backend services.

**References**: Architecture Sections 67.3.8, 67.9.1 (conveyancing file list); [ADR-251](../adr/ADR-251-acceptance-eligible-template-manifest-flag.md).

**Dependencies**: 489A (for `document_templates.acceptance_eligible` column — 492B populates the flag on conveyancing templates) and for the `TemplatePackSeeder` wiring that reads the manifest flag.

**Scope**: Backend (pack content only)

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **492A** | 492.1–492.6 | `field-packs/conveyancing-za-project.json` (10 fields per arch requirements §4.1), Property Transfer template appended to `project-template-packs/legal-za.json` (12 tasks), `clause-packs/conveyancing-za-clauses/pack.json` + 10 Tiptap clause files, profile-manifest update to `vertical-profiles/legal-za.json`, seeder-level integration tests. Pack-content slice — 14 files, exceeds the normal 10-file ceiling because each clause Tiptap is a ~20-line JSON; called out explicitly per sizing rules. **Done** (PR #1079) |
| **492B** | 492.7–492.11 | 4 new Tiptap templates under `template-packs/legal-za/` (offer-to-purchase, deed-of-transfer, power-of-attorney-transfer, bond-cancellation-instruction), extend `legal-za/pack.json` with 4 entries including `acceptanceEligible: true` on OTP + POA, template-manifest schema extension (optional boolean field with default false), `conveyancing-intake-za.json` request pack, install-level tests including a Phase 28 acceptance-UI surfacing assertion. |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 492.1 | Author `field-packs/conveyancing-za-project.json` | 492A | -- | New file: `backend/src/main/resources/field-packs/conveyancing-za-project.json`. `entityType: "PROJECT"`, `group.autoApply: true` (Phase 23), `verticalProfile: "legal-za"`, conditional: `matter_type = CONVEYANCING`. 10 fields per requirements §4.1: `conveyancing_type` ENUM (5 values), `property_address` TEXT, `erf_number` STRING, `deeds_office` ENUM (10 SA offices), `lodgement_date` DATE, `registration_date` DATE (visible iff `lodgement_date` set), `deed_number` STRING (visible iff `registration_date` set), `purchase_price` CURRENCY, `transfer_duty` CURRENCY, `bond_institution` ENUM (8 values). Pattern: `field-packs/accounting-za-project.json`. |
| 492.2 | Append Property Transfer template to `project-template-packs/legal-za.json` | 492A | 492.1 | Modify in place: `backend/src/main/resources/project-template-packs/legal-za.json`. Append 1 template: name "Property Transfer (Conveyancing)", `matter_type: CONVEYANCING`, `conveyancing_type: TRANSFER` default, 40-hour suggested budget, 12 tasks per requirements §4.2 (Receive instruction, Draft OTP, FICA, Rates clearance, Transfer duty, Draft deed, Draft POA, Lodge, Deeds-office notes, Registration, Finalise statement, Close matter — each with priority + assignee role + sortOrder). Bump pack `version`. |
| 492.3 | Create `clause-packs/conveyancing-za-clauses/pack.json` + 10 Tiptap clauses | 492A | -- | New directory: `backend/src/main/resources/clause-packs/conveyancing-za-clauses/`. `pack.json` manifest with `packId: "conveyancing-za-clauses"`, `verticalProfile: "legal-za"`, 10 entries referencing 10 Tiptap files. 10 clause files (one per row in requirements §4.3 table): `voetstoots.json`, `occupation-date.json`, `suspensive-bond.json`, `transfer-duty-liability.json`, `fica-compliance.json`, `sectional-title-levies.json`, `body-corporate-clearance.json`, `rates-clearance.json`, `cost-of-cancellation.json`, `jurisdiction-za.json`. Each a Tiptap JSON document with `{{customer.name}}` / `{{org.name}}` where appropriate. Pattern: existing `clause-packs/legal-za-clauses/`. |
| 492.4 | Update `vertical-profiles/legal-za.json` manifest | 492A | 492.1, 492.3 | Modify: `backend/src/main/resources/vertical-profiles/legal-za.json`. Append to existing pack references: field pack `conveyancing-za-project`, clause pack `conveyancing-za-clauses`, request pack `conveyancing-intake-za` (even though 492B ships the file — adding the reference here is fine; installer is idempotent). |
| 492.5 | Integration test: field pack + template + clause pack install | 492A | 492.1–492.4 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConveyancingPackInstallTest.java`. ~5 tests: (1) `FieldPackSeeder` creates 10 `FieldDefinition` rows for a legal-za tenant with conditional visibility rules per spec, (2) `ProjectTemplatePackSeeder` creates Property Transfer template with 12 tasks in correct sort order, (3) `ClausePackSeeder` creates 10 clauses, (4) conditional visibility: `registration_date` + `deed_number` hidden until precursor fields set (query FieldDefinition rules), (5) provisioning a fresh legal-za tenant installs all three without error. Pattern: Phase 64 `LegalProjectTemplatePackTest.java` + `ConsultingZaClausePackTest.java` from Phase 66. |
| 492.6 | Integration test: conveyancing field pack does NOT apply to non-legal tenants | 492A | 492.5 | Same file, +1 test: accounting-za + consulting-za tenants do NOT receive the 10 conveyancing fields. Asserts profile-gating is honored. |
| 492.7 | Extend template-manifest schema with `acceptanceEligible` | 492B | 489A (column + `TemplatePackSeeder` wire) | Modify: the template-pack manifest record/DTO under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/template/` (e.g. `TemplatePackManifestEntry` or equivalent; architecture §67.9.1 names it `TemplatePackManifestEntry`). Add optional `Boolean acceptanceEligible` with Jackson default to `false` when absent. Slice 489A landed the schema column + `TemplatePackSeeder` read-through; this task verifies the schema object in the loader is updated if not already, and confirms the seeder persists the flag on `DocumentTemplate.acceptanceEligible`. |
| 492.8 | Author 4 conveyancing Tiptap templates | 492B | 492.7 | New files under `backend/src/main/resources/template-packs/legal-za/`: `offer-to-purchase.json`, `deed-of-transfer.json`, `power-of-attorney-transfer.json`, `bond-cancellation-instruction.json`. Variables per requirements §4.4 — OTP uses `{{customer.name}}` (buyer), seller name, property address, erf, purchase price, occupation date, inserted clauses via clause-library insertions; deed-of-transfer uses property + erf + deeds office + purchase price + transfer duty + parties; POA uses transferor + attorney + property + deeds office; bond cancellation uses bond institution + customer + property + bond number. Modify: `backend/src/main/resources/template-packs/legal-za/pack.json` — append 4 entries. Set `acceptanceEligible: true` on OTP + POA entries; false on deed-of-transfer + bond cancellation. Bump pack `version` — final value converges with 489.17 + 491.4 during 493A integration. |
| 492.9 | Author `request-packs/conveyancing-intake-za.json` | 492B | -- | New file: `backend/src/main/resources/request-packs/conveyancing-intake-za.json`. Questionnaire per requirements §4.5: party ID + contact, property address + erf + deeds office, purchase price + bond amount + bond institution, occupation + possession dates, FICA file uploads (multi), marital status + ANC/community, rates + levy contact. Mirror existing `request-packs/*-za.json` shapes. Bump manifest entry in `vertical-profiles/legal-za.json` if not already present (492.4). |
| 492.10 | Integration test: template pack install + acceptance flag wiring | 492B | 492.7, 492.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConveyancingTemplatePackTest.java`. ~5 tests: (1) `TemplatePackSeeder` installs 4 new templates, bringing legal-za pack total to pre-phase + 4 + SoA (491) + closure letter (489B), (2) OTP + POA have `DocumentTemplate.acceptanceEligible = true`, (3) deed-of-transfer + bond-cancellation have it false, (4) existing templates (pre-phase) have it false by default, (5) a Phase 28 `AcceptanceRequest` creation UI query (or the backend list endpoint that feeds it) surfaces OTP + POA as acceptance-eligible and NOT deed-of-transfer — use the same list query the frontend consumes. Pattern: Phase 65 `TemplatePackInstallerTest.java`. |
| 492.11 | Integration test: request pack install + profile filter | 492B | 492.9 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConveyancingRequestPackTest.java`. ~2 tests: (1) `RequestPackSeeder` creates the conveyancing-intake questionnaire for legal-za tenant, (2) accounting-za + consulting-za tenants do not. Pattern: existing Phase 66 request-pack test. |

### Key Files

**Create:**
- `backend/src/main/resources/field-packs/conveyancing-za-project.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/pack.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/voetstoots.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/occupation-date.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/suspensive-bond.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/transfer-duty-liability.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/fica-compliance.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/sectional-title-levies.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/body-corporate-clearance.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/rates-clearance.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/cost-of-cancellation.json`
- `backend/src/main/resources/clause-packs/conveyancing-za-clauses/jurisdiction-za.json`
- `backend/src/main/resources/template-packs/legal-za/offer-to-purchase.json`
- `backend/src/main/resources/template-packs/legal-za/deed-of-transfer.json`
- `backend/src/main/resources/template-packs/legal-za/power-of-attorney-transfer.json`
- `backend/src/main/resources/template-packs/legal-za/bond-cancellation-instruction.json`
- `backend/src/main/resources/request-packs/conveyancing-intake-za.json`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConveyancingPackInstallTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConveyancingTemplatePackTest.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/packs/ConveyancingRequestPackTest.java`

**Modify:**
- `backend/src/main/resources/project-template-packs/legal-za.json` — append Property Transfer template
- `backend/src/main/resources/template-packs/legal-za/pack.json` — append 4 entries + version bump (converge with 489.17 + 491.4)
- `backend/src/main/resources/vertical-profiles/legal-za.json` — append 3 pack references
- Template-pack manifest record class — add `acceptanceEligible` field if not done by 489A

**Read for context:**
- `backend/src/main/resources/clause-packs/legal-za-clauses/pack.json` — clause pack shape
- `backend/src/main/resources/project-template-packs/legal-za.json` — existing matter templates (Phase 64)
- `backend/src/main/resources/field-packs/accounting-za-project.json` — conditional visibility pattern
- Phase 65 `TemplatePackInstaller` — install pipeline

### Architecture Decisions

- **Pack-only — no new backend entities or services** ([ADR-251](../adr/ADR-251-acceptance-eligible-template-manifest-flag.md)). Every addition flows through existing Phase 65 installers.
- **`acceptanceEligible` is a general-purpose flag** — any vertical can opt templates into Phase 28 acceptance. Conveyancing OTP + POA are the first adopters.
- **Conditional visibility on `registration_date` + `deed_number`** reuses Phase 23. No new primitive.
- **File-count ceiling exception called out explicitly** for 492A (14 files) because 10 of those are 20-line clause JSONs — equivalent LOC well under budget.

### Non-scope

- No conveyancing-specific custom customer fields (reuses horizontal + legal customer fields).
- No deeds-office API integration (out of scope per arch §67.12).
- No transfer-duty or bond-cost calculators (out of scope).
- No backend conveyancing entity or service.
- No new clause-pack loader type.

---

## Epic 493: QA Capstone — Lifecycle Retarget + Screenshot Baselines + Gap Report

**Goal**: Retarget the Phase 64 legal 90-day Keycloak lifecycle script with Phase 67 checkpoints (disbursements, conveyancing, statement of account, write-off, closure, override), execute it end-to-end against a fresh legal-za tenant, capture Playwright screenshot baselines and curated documentation shots, and produce `phase67-gap-report.md`.

**References**: Requirements Section 5 (QA lifecycle + screenshots + gap report), architecture §67.9.5 (testing strategy summary), Phase 64 precedent (`tasks/phase64-legal-vertical-qa.md` Epic 469).

**Dependencies**: Every other slice (486–492) merged.

**Scope**: E2E / Process

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary |
|-------|-------|---------|
| **493A** | 493.1–493.7 | Extend `qa/testplan/demos/legal-small-firm-90day-keycloak.md` with 6 new Phase 67 checkpoints (Day 5 disbursements, Day 14 conveyancing, Day 30 SoA, Day 45 write-off, Day 75 closure, Day 85 override). Author Playwright specs under `frontend/e2e/tests/legal-depth-ii/` capturing baselines to `frontend/e2e/screenshots/legal-depth-ii/`. Curated marketing/demo screenshots under `documentation/screenshots/legal-vertical/`. Execute end-to-end. Produce `tasks/phase67-gap-report.md` classifying any UX rough edges, missing variables, or follow-ups by severity. |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 493.1 | Retarget Day 5: Disbursements | 493A | 486–488 | Modify: `qa/testplan/demos/legal-small-firm-90day-keycloak.md` (or the equivalent Phase 64 file; verify path). Day 5 script: create a sheriff fee disbursement (OFFICE_ACCOUNT), create a deeds-office fee (TRUST_ACCOUNT linked to an approved DISBURSEMENT_PAYMENT trust tx), submit, admin approves, verify unbilled summary on matter shows R x,xxx under "Unbilled Disbursements". Assertions reference terminology per Phase 64 map (Disbursement, not Expense). |
| 493.2 | Retarget Day 14: Conveyancing matter | 493A | 492 | Same file. Day 14: create a new matter using the Property Transfer template, verify the 10 conveyancing custom fields present, fill property_address + erf_number + deeds_office, generate `offer-to-purchase` document, send for acceptance via Phase 28 (relies on `acceptanceEligible: true`), verify client receives acceptance request. |
| 493.3 | Retarget Day 30 + Day 45: SoA + write-off | 493A | 487, 491 | Day 30: on the oldest active matter, generate a Statement of Account for period = matter-opened to today; preview HTML shows fees + disbursements (from Day 5) + trust activity + summary; save as GeneratedDocument. Day 45: write off one approved-unbilled disbursement with reason; verify audit event; verify disbursement is excluded from next invoice draft. |
| 493.4 | Retarget Day 75 + Day 85: Closure (happy + override) | 493A | 489, 490 | Day 75: select a matter where trust balance is R0 and all other gates pass; open Close Matter dialog, verify gate report all-green, confirm reason CONCLUDED, `generateClosureLetter = true`, verify closure letter document created, retention policy inserted with end date = today + 5y, matter status badge shows CLOSED. Day 85: pick another matter with trust balance > 0 (intentionally unresolved), log in as Admin — attempt close, verify 409 gate report displayed and override toggle hidden. Log in as Owner — enter override justification ≥20 chars, submit, verify audit log captures `override_used=true` and the justification text. |
| 493.5 | Add Playwright screenshot specs | 493A | 493.1–493.4 | New directory: `frontend/e2e/tests/legal-depth-ii/`. Author 4 spec files (one per checkpoint cluster): `day-05-disbursements.spec.ts`, `day-14-conveyancing.spec.ts`, `day-30-45-soa-writeoff.spec.ts`, `day-75-85-closure.spec.ts`. Each exercises the checkpoint actions via Playwright + captures `toHaveScreenshot()` baselines into `frontend/e2e/screenshots/legal-depth-ii/` (directory created by this task). Reuse Phase 64 `playwright.legal-lifecycle.config.ts` or extend to a new `playwright.legal-depth-ii.config.ts` following the same pattern (deviceScaleFactor 2, maxDiffPixelRatio 0.01). Extend `frontend/package.json` with `test:e2e:legal-depth-ii` npm script. |
| 493.6 | Capture curated documentation screenshots | 493A | 493.5 | New directory: `documentation/screenshots/legal-vertical/phase67/`. Curated PNG captures for marketing/demo: disbursement list view, disbursement approval dialog, trust-link dialog, matter closure dialog with gate report (failing), matter closure dialog (all green), closure letter preview, Statement of Account preview (HTML + summary block), conveyancing matter detail with custom fields, OTP document with inserted clauses, acceptance request for OTP. Mirror Phase 64's `documentation/screenshots/legal-vertical/` structure. |
| 493.7 | Run full lifecycle end-to-end + produce `phase67-gap-report.md` | 493A | 493.5, 493.6 | Execute `pnpm test:e2e:legal-depth-ii` against a fresh legal-za tenant. Record pass/fail per step. Triage failures: pack-content bug → fix in 486–492 slice; lifecycle-script bug → fix here; genuine product gap → log in gap report. New file: `tasks/phase67-gap-report.md` with sections: Executive summary, Statistics (passed/failed/skipped per day), Gaps by severity (Blocker/Major/Minor), Recommended fix phase for each. Pre-log the architecture-acknowledged gaps: time-entry-age trigger does not exist (arch called this out for Phase 66 automations — verify still open for legal); unified deadline calendar; fee-notes-as-entity; deeds-office API; bulk disbursement CSV import (all from arch §67.12). Pattern: `tasks/phase64-gap-report.md`. |

### Key Files

**Create:**
- `frontend/e2e/tests/legal-depth-ii/day-05-disbursements.spec.ts`
- `frontend/e2e/tests/legal-depth-ii/day-14-conveyancing.spec.ts`
- `frontend/e2e/tests/legal-depth-ii/day-30-45-soa-writeoff.spec.ts`
- `frontend/e2e/tests/legal-depth-ii/day-75-85-closure.spec.ts`
- `frontend/e2e/screenshots/legal-depth-ii/` (baseline directory, populated on first run)
- `documentation/screenshots/legal-vertical/phase67/` (curated PNGs)
- `tasks/phase67-gap-report.md`
- Potentially `frontend/e2e/playwright.legal-depth-ii.config.ts` (if extending the Phase 64 config isn't sufficient)

**Modify:**
- `qa/testplan/demos/legal-small-firm-90day-keycloak.md` (or the Phase 64-authored file under `qa/testplan/demos/`) — add 6 checkpoints
- `frontend/package.json` — add `test:e2e:legal-depth-ii` npm script

**Read for context:**
- `tasks/phase64-legal-vertical-qa.md` — Epic 469 precedent for structure, file naming, assertion depth
- `frontend/e2e/playwright.legal-lifecycle.config.ts` — Phase 64 screenshot config
- `qa/testplan/demos/legal-small-firm-90day-keycloak.md` — existing lifecycle script
- `documentation/screenshots/legal-vertical/` — Phase 64 curated screenshot directory

### Architecture Decisions

- **Retarget, don't rewrite** — the Phase 64 90-day lifecycle stays the spine. Phase 67 appends checkpoints.
- **Screenshot baselines under `e2e/screenshots/legal-depth-ii/`** (regression) separate from `documentation/screenshots/legal-vertical/phase67/` (curated).
- **Sequential execution (`workers: 1`)** because later days depend on data created in earlier days — same constraint Phase 64 adopted.
- **Pre-logged gaps** rather than discovering everything at runtime — shortens the feedback loop for a Phase 68 polish slice.

### Non-scope

- No new Playwright infrastructure — reuses Phase 64 patterns.
- No new Keycloak tenant archetype — extends the existing Mathebula & Partners fiction.
- No automated regression harness beyond the lifecycle spec set.
- No gap fixes in this slice — gaps are documented, not resolved.

---

## Risk Register

| # | Risk | Likelihood | Impact | Mitigation |
|---|------|------------|--------|------------|
| 1 | **Migration number collision**: Architecture calls the new migrations V96 + V97, but tenant migrations on disk are already at V99. If a builder applies the files literally, Flyway will fail or (worse) no-op. | High | High — blocks the whole phase. | Every migration-related task explicitly notes "rename to next available V-number at implementation." Slices 486A and 489A must verify the current high-water in `backend/src/main/resources/db/migration/tenant/` as their first check and rename accordingly. Preserve architecture's descriptive filename after the number (e.g. `V100__create_legal_disbursements.sql`). |
| 2 | **V97 migration ownership across slices 487B and 489A**: The single V97 file covers both matter-closure additions AND the `invoice_lines.disbursement_id` column 487B depends on. If 487B lands before 489A, the column doesn't exist; if 489A lands after 487B has already shipped entity code, the entity code fails to map. | Medium | High — runtime NPE or test failures on 487B side. | Phase-level ordering is explicit: 489A lands BEFORE 487B. Slice 487B Task 487.5 explicitly notes the V97 dependency. Both slice briefs must cross-reference each other — the known-decisions section calls this out, and the Stage 2 timeline shows 487B unblocked only after 489A. |
| 3 | **Capability default registry location is fuzzy**: Architecture §67.7 says `OrgRoleSeeder` owns defaults, but there is no `OrgRoleSeeder` class in the codebase — defaults likely live on `OrgRoleService` bootstrap or in a static map near `Capability.java`. If the builder guesses wrong, default role bindings don't apply. | Medium | Medium — capabilities exist but no role holds them; backend 403s everywhere until fixed. | Slice 486A Task 486.5 directs the builder to locate the existing `APPROVE_TRUST_PAYMENT` default binding site and extend it. The 486B controller integration test asserts end-to-end capability enforcement — if defaults aren't wired, 486.16's happy-path tests fail loudly. |
| 4 | **Closure gate count mismatch**: 9 gates depend on data from 6 different modules (trust, disbursements, invoices, court-calendar, prescriptions, tasks, info-requests, acceptances). A single gate calling a repository method with the wrong signature silently returns empty lists and always-passes, hiding real blockers. | Medium | High — fails open; matter closes with violations. | Per-gate unit tests in 489.9 seed explicit violation rows and assert the gate fails with correct message. 489A does not ship the orchestrator yet — 489B re-tests the orchestrator path end-to-end against all 9 gates in an integration test. Lifecycle script Day 75 exercises a real-data closure as final validation. |
| 5 | **Variable-namespace gap in Statement of Account template**: `StatementOfAccountContextBuilder` populates a specific shape (`fees.entries[]`, `disbursements.entries[]`, etc.), but if the Tiptap template references a key not in the builder output, Phase 31 VariableResolver silently renders the raw placeholder. | Medium | Medium — broken-looking generated statements, hard to spot in QA. | Slice 491A's context-builder test 491.5 seeds full fixtures and builds the bundle; a second pass renders the system template through `DocumentGenerationService` and asserts the rendered HTML contains no un-interpolated `{{` sequences. Gap report scans for same. |
| 6 | **`acceptance_eligible` flag isn't wired to Phase 28 UI**: The column lands in V97, `TemplatePackSeeder` maps it, but the Phase 28 send-for-acceptance list may not filter by it yet — templates could appear or not appear inconsistently. | Medium | Low-Medium — conveyancing OTP/POA won't surface as first-class acceptance-eligible options. | Slice 492B Task 492.10 writes an integration test calling the backend list endpoint Phase 28 consumes and asserts OTP + POA appear with `acceptanceEligible=true` while deed-of-transfer does not. If that endpoint doesn't filter, the test fails and forces 492B to extend the Phase 28 query — or to surface the flag on the DTO and leave UI work for a follow-up. |
| 7 | **Disbursement → TrustTransaction reference integrity at close time**: When a matter closes, the `trust_transactions(id) ON DELETE RESTRICT` FK means a closed matter's disbursements cannot cascade-delete even if the archival path wanted them to. Closure must not touch that FK. | Low | Medium — unexpected constraint violation on closure if archival later introduced. | Migration V96 sets `ON DELETE RESTRICT` deliberately. Closure service never deletes; it only inserts the retention-policy row. Phase 50 retention job is the only path that purges data after retentionEndsAt, and that's out of scope for this phase. |
| 8 | **Frontend capability-gating race**: `<ModuleGate>` + `useCapability()` both fetch asynchronously. A render might briefly show the "Close Matter" action before `useCapability` resolves, then hide it — flakes screenshot baselines. | Medium | Low (UX only, CI noise). | Components rely on the existing Phase 49 provider's loading state (returns `null` while resolving). Slice 493A screenshot specs use `waitForLoadState('networkidle')` before capture, same as Phase 64. |
| 9 | **Pack-version collisions**: Three separate slices (489B for matter-closure-letter, 491A for SoA, 492B for 4 conveyancing templates) each bump `template-packs/legal-za/pack.json#version`. Last-merge-wins semantics mean the pre-final version numbers are wrong for whoever merges second and third. | High | Low — confusing but recoverable. | Slice 493A's first action is to verify + finalise the pack.json version number. Each intermediate slice may land with version = base + 1 and the capstone normalises. Alternative mitigation: architect explicitly owns version as a monotonic counter incremented by whoever merges last. |
| 10 | **Playwright screenshot drift across mid-phase + end-phase runs**: If screenshots are captured mid-phase (for per-slice docs) and re-captured end-phase, font-rendering or tiny layout shifts can fail the diff. | Medium | Low | Capstone (493A) is the single source of truth for baselines. No intermediate slice should capture screenshots — only functional component tests. The `test:e2e:legal-depth-ii` script is only ever run during 493A. |

---

### Critical Files for Implementation

- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/LegalDisbursement.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/MatterClosureService.java`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/db/migration/tenant/V96__create_legal_disbursements.sql`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/resources/db/migration/tenant/V97__matter_closure_and_invoice_line_disbursement.sql`
- `/Users/rakheendama/Projects/2026/b2b-strawman/backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceCreationService.java`