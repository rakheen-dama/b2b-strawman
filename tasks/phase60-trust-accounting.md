# Phase 60 -- Trust Accounting (Legal Practice Act Section 86)

Phase 60 replaces the `trust_accounting` module stub (registered in Phase 49) with a full double-entry trust ledger system satisfying the Legal Practice Act, 2014 (Section 86) for South African law firms. This is the single feature that determines whether the legal vertical is viable -- every matter involving client money (litigation, conveyancing, collections, estate administration) requires proper trust handling. The implementation introduces ten new entities (`TrustAccount`, `LpffRate`, `TrustTransaction`, `ClientLedgerCard`, `BankStatement`, `BankStatementLine`, `TrustReconciliation`, `InterestRun`, `InterestAllocation`, `TrustInvestment`), three new RBAC capabilities (`VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT`), seven new report definitions, and a full frontend with dashboard, transaction entry, approval workflow, bank reconciliation split-pane, interest calculation, investment register, and Section 35 compliance reports.

**Architecture doc**: `architecture/phase60-trust-accounting.md`

**ADRs**:
- [ADR-230](adr/ADR-230-double-entry-trust-ledger.md) -- Double-entry with immutable transactions and cached balances on ClientLedgerCard
- [ADR-231](adr/ADR-231-negative-balance-prevention.md) -- Belt and suspenders: SELECT FOR UPDATE + CHECK (balance >= 0) constraint
- [ADR-232](adr/ADR-232-configurable-dual-authorization.md) -- Configurable per-trust-account single/dual approval with optional threshold
- [ADR-233](adr/ADR-233-bank-reconciliation-matching.md) -- Multi-signal scored matching with 0.80 confidence threshold for auto-matching
- [ADR-234](adr/ADR-234-interest-daily-balance-method.md) -- Daily balance method implemented via transaction-weighted computation, pro-rata rate splits

**Dependencies on prior phases**:
- Phase 49: `VerticalModuleGuard`, `VerticalModuleRegistry`, `VerticalProfileRegistry`, `ModuleGate` component -- module/profile infrastructure
- Phase 10/25/26: `Invoice`, `InvoiceLine`, `InvoiceService`, `PaymentEvent` -- fee transfer integration
- Phase 41/46: `Capability` enum, `@RequiresCapability`, `OrgRole`, `MemberFilter` -- RBAC infrastructure
- Phase 6: `AuditEventService`, `AuditEventBuilder` -- audit trail for all trust operations
- Phase 6.5: `NotificationService` -- approval notifications, maturity alerts, overdue reconciliation reminders
- Phase 19: `ReportDefinition`, report execution framework, rendering pipeline -- Section 35 reports
- Phase 9/21: `StorageService` -- S3 storage for bank statement CSV files
- Phase 13: Schema-per-tenant isolation -- all new entities are plain `@Entity` with no multitenancy boilerplate
- Phase 55: Legal vertical patterns -- court calendar, conflict check, tariff entities as reference patterns

**Migration note**: V85 tenant migration creates all 10 trust tables, indexes, constraints, and capability seeding. V84 is the latest existing migration.

---

## Epic Overview

| Epic | Name | Scope | Deps | Effort | Slices | Status |
|------|------|-------|------|--------|--------|--------|
| 438 | Foundation: V85 Migration + Module Registration + RBAC Capabilities | Backend | -- | M | 438A, 438B | **Done** (PRs #921, #922) |
| 439 | TrustAccount + LpffRate Entity + Service + Controller | Backend | 438 | M | 439A, 439B | **Done** (PRs #923, #924) |
| 440 | TrustTransaction + ClientLedgerCard Entity + Deposit/Transfer Service | Backend | 439 | L | 440A, 440B | **Done** (PRs #925, #926) |
| 441 | Approval Workflow + Payment/FeeTransfer/Refund Recording | Backend | 440 | L | 441A, 441B | **Done** (PRs #928, #929) |
| 442 | Transaction Controller + Approval Endpoints + Client Ledger Controller | Backend | 441 | M | 442A, 442B | **Done** (PRs #931, #932) |
| 443 | Bank Statement Import + CSV Parsers | Backend | 439 | M | 443A, 443B | **Done** (PRs #933, #934) |
| 444 | Auto-Matching + Manual Matching + Reconciliation | Backend | 440, 443 | L | 444A, 444B | **Done** (PRs #935, #936) |
| 445 | Interest Calculation + Posting | Backend | 440 | L | 445A, 445B | **Done** (PRs #937, #938) |
| 446 | Trust Investments | Backend | 441 | M | 446A, 446B | **Done** (PRs #939, #940) |
| 447 | Trust Reports (Section 35) + Event/Notification Handlers | Backend | 442, 444, 445, 446 | M | 447A, 447B | **Done** (PRs #941, #942) |
| 448 | Frontend: Trust Dashboard + Transaction Entry + Approval UX | Frontend | 442 | L | 448A, 448B | **Done** (PRs #944, #945) |
| 449 | Frontend: Client Ledger + Reconciliation Split-Pane | Frontend | 442, 444 | L | 449A, 449B | **Done** (PRs #947, #948) |
| 450 | Frontend: Interest + Investments + Reports Pages | Frontend | 445, 446, 447 | M | 450A, 450B | In progress (450A done PR #951) |
| 451 | Frontend: Matter/Customer Trust Tabs + Settings + Sidebar + Coexistence Tests | Frontend | 448, 449, 450 | M | 451A, 451B | Not started |

---

## Dependency Graph

```
BACKEND FOUNDATION
──────────────────────────────────────────────────────────────────

[E438A V85 tenant migration (10 tables + capability seeding),
 VIEW_TRUST + MANAGE_TRUST + APPROVE_TRUST_PAYMENT capabilities
 added to Capability enum + default role mapping]
        |
[E438B Module registry update (trust_accounting → active,
 legal-za profile update, stub controller capability update)]
        |
[E439A TrustAccount + LpffRate entities + repos +
 TrustAccountService (CRUD + LPFF rate management +
 closing guard + module guard) + integration tests]
        |
[E439B TrustAccountingController replaces stub (7 endpoints:
 list, get, create, update, close, listRates, addRate) +
 integration tests]
        |
        +─────────────────────────+─────────────────────────+
        |                         |                         |
TRUST LEDGER CORE             BANK STATEMENT IMPORT     (parallel
(sequential)                  (sequential)               after 440)
────────────────              ──────────────────         ─────────
        |                         |
[E440A TrustTransaction +     [E443A BankStatement +
 ClientLedgerCard entities +   BankStatementLine
 repos + TrustTransaction      entities + repos +
 Service (deposit, transfer,   BankStatementParser
 reversal) + ClientLedger      interface + CSV parser
 Service + negative balance    implementations (5 banks)
 check + integration tests]    + integration tests]
        |                         |
[E440B Transaction reversal   [E443B BankStatement
 + cashbook balance +          import controller
 client ledger endpoints       (multipart upload + S3) +
 (service layer only) +        statement list/detail
 additional integration        endpoints + integration
 tests]                        tests]
        |                         |
[E441A Payment + FeeTransfer      |
 + Refund recording              |
 (AWAITING_APPROVAL) +           |
 approveTransaction +            |
 rejectTransaction +             |
 single/dual/threshold           |
 approval + self-approval        |
 prevention + invoice            |
 integration + tests]            |
        |                         |
[E441B Dual approval edge     [E444A Auto-matching
 cases + concurrent             algorithm (4-level
 approval tests + fee           confidence scoring) +
 transfer invoice PAID          manual match/unmatch/
 transition + notification      exclude service methods
 fan-out + tests]               + matching tests]
        |                         |
[E442A TrustTransaction       [E444B TrustReconciliation
 Controller (7 transaction     entity + repo + three-way
 recording endpoints +         reconciliation calculation
 3 approval endpoints +        + completion guard +
 pending/cashbook              reconciliation controller
 endpoints) + tests]           + integration tests]
        |                         |
[E442B ClientLedger               |
 Controller (5 endpoints)         |
 + trust account closing          |
 guard integration +              |
 controller tests]                |
        |                         |
        +-────────────────────────+
        |                                   |
INTEREST (parallel)                  INVESTMENTS (parallel)
──────────────                       ─────────────────────
        |                                   |
[E445A InterestRun +                [E446A TrustInvestment
 InterestAllocation entities         entity + repo +
 + repos + daily balance             TrustInvestmentService
 calculation + pro-rata              (place, recordInterest,
 rate splits + rounding +            withdraw, maturity
 integration tests]                  detection) + tests]
        |                                   |
[E445B InterestService              [E446B TrustInvestment
 posting (INTEREST_CREDIT            Controller (6 endpoints)
 + INTEREST_LPFF transactions)       + integration tests]
 + InterestController                    |
 (6 endpoints) + tests]                  |
        |                                |
        +─────────────+─────────────────+
                      |
[E447A Trust report data providers (7 report types) +
 ReportDefinition seed data + Section 35 data pack +
 integration tests]
        |
[E447B Trust domain events + TrustNotificationHandler +
 notification types + legal pack content (templates +
 automation rules) + integration tests]
        |
        +─────────────────────────────────────+
        |                                     |
FRONTEND CORE                           FRONTEND ADVANCED
(sequential)                            (parallel after 448)
──────────────                          ─────────────────
        |                                     |
[E448A Trust dashboard page              [E449A Client ledger
 (replaces stub) + summary               list page + detail
 cards + recent transactions              view + transaction
 + alerts + types + schemas               history + statement
 + server actions + nav item              generation + types +
 update + tests]                          schemas + actions +
        |                                 tests]
[E448B Transaction page +                     |
 record deposit/payment/                 [E449B Reconciliation
 transfer/feeTransfer/refund              page + bank statement
 dialogs + approval badge +               upload + split-pane
 approve/reject/reverse                   matching interface +
 actions + tests]                         completion flow +
        |                                 tests]
        +──────────+──────────────────────+
                   |
[E450A Interest page + interest run wizard (create → calculate
 → approve → post) + LPFF rate management + types + schemas +
 actions + tests]
        |
[E450B Investment register page + place/record/withdraw
 dialogs + maturity alerts + trust reports page + report
 generation dialogs + tests]
        |
[E451A Project detail "Trust" tab (module-gated) + customer
 detail "Trust" tab (module-gated) + sidebar nav update
 (sub-items) + trust settings page + tests]
        |
[E451B Multi-vertical coexistence tests (trust + accounting
 tenant isolation, module guard, no cross-contamination) +
 trust-specific E2E smoke tests]
```

**Parallel opportunities**:
- After E439B: E440 (trust ledger) and E443 (bank statement import) can run in parallel. They share only the TrustAccount entity from E439.
- After E440B: E445 (interest) can start in parallel with E441 (approval workflow) and E444 (matching/reconciliation) -- interest calculation does not depend on approval.
- After E441B: E446 (investments) can start while E444 continues.
- After E442: E448 (frontend core) can start immediately.
- After E444: E449 (frontend ledger + reconciliation) can start.
- After E445 + E446: E450 (frontend interest + investments) can start.
- E447 (reports + events) waits for all backend domain epics.
- E451 (integration tabs + coexistence) waits for all frontend epics.

---

## Implementation Order

### Stage 0: Backend Foundation

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 0a | 438 | 438A | V85 tenant migration (10 tables: `trust_accounts`, `lpff_rates`, `trust_transactions`, `client_ledger_cards`, `bank_statements`, `bank_statement_lines`, `trust_reconciliations`, `interest_runs`, `interest_allocations`, `trust_investments` + all indexes, constraints, deferred FK). Capability seeding (VIEW_TRUST, MANAGE_TRUST, APPROVE_TRUST_PAYMENT for owner/admin/member roles). `VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT` added to `Capability` enum. Default role mapping updates. Unit tests (~4). Backend only. | **Done** (PR #921) |
| 0b | 438 | 438B | Update `VerticalModuleRegistry`: `trust_accounting` status `"stub"` → `"active"` with 7 nav items. Update `VerticalProfileRegistry`: `legal-za` profile `enabled_modules` includes `trust_accounting`. Update stub `TrustAccountingController` capability from `VIEW_LEGAL` to `VIEW_TRUST`. Integration tests (~4). Backend only. | **Done** (PR #922) |

### Stage 1: Trust Account CRUD

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 1a | 439 | 439A | `TrustAccount` entity + `LpffRate` entity + `TrustAccountRepository` + `LpffRateRepository` + `TrustAccountService` (CRUD, close guard, LPFF rate management, module guard, audit events). Integration tests (~8). Backend only. | **Done** (PR #923) |
| 1b | 439 | 439B | Replace stub `TrustAccountingController` with 7 endpoints (list, get, create, update, close, listRates, addRate). `@RequiresCapability` on all endpoints. Integration tests (~6). Backend only. | **Done** (PR #924) |

### Stage 2: Trust Ledger Core + Bank Statement Import (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 2a (parallel) | 440 | 440A | `TrustTransaction` entity + `ClientLedgerCard` entity + repos + `TrustTransactionService` (recordDeposit, recordTransfer with paired TRANSFER_IN/TRANSFER_OUT, reverseTransaction) + `ClientLedgerService` (getClientLedger, listClientLedgers, getClientTransactionHistory, getClientBalanceAsOfDate, getTotalTrustBalance). Negative balance prevention (SELECT FOR UPDATE + CHECK constraint verification). Integration tests (~12). Backend only. | **Done** (PR #925) |
| 2b (parallel) | 443 | 443A | `BankStatement` entity + `BankStatementLine` entity + repos + `BankStatementParser` interface + `CsvBankStatementParser` abstract base + `FnbCsvParser` + `StandardBankCsvParser` + `NedbankCsvParser` + `AbsaCsvParser` + `GenericCsvParser`. CSV fixture files in `src/test/resources/fixtures/trust/`. Unit tests (~10). Backend only. | **Done** (PR #933) |

### Stage 3: Transaction Controllers + Bank Statement Controller (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 3a (parallel) | 440 | 440B | Transaction reversal conditional approval (credit vs debit reversals), `getCashbookBalance()`, client ledger card query methods. Additional integration tests (~8). Backend only. | **Done** (PR #926) |
| 3b (parallel) | 443 | 443B | `TrustReconciliationController` bank statement endpoints: multipart upload (S3 storage at `trust-statements/{tenantSchema}/{accountId}/{filename}`), statement list, statement detail with lines. Integration tests (~5). Backend only. | **Done** (PR #934) |

### Stage 4: Approval Workflow (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 4a | 441 | 441A | `recordPayment()`, `recordFeeTransfer()`, `recordRefund()` (all create in AWAITING_APPROVAL status). `approveTransaction()` with single approval mode + self-approval prevention + negative balance check. `rejectTransaction()`. Integration tests (~10). Backend only. | **Done** (PR #928) |
| 4b | 441 | 441B | Dual approval mode + threshold-based dual approval + concurrent approval race condition tests. Fee transfer → `InvoiceService.recordPayment()` integration. `TrustNotificationHandler` skeleton for approval notifications. Integration tests (~10). Backend only. | **Done** (PR #929) |

### Stage 5: Controllers + Matching + Interest + Investments (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 5a (parallel) | 442 | 442A | `TrustTransactionController` (7 recording endpoints: deposit, payment, transfer, feeTransfer, refund + list + get) + 3 approval endpoints (approve, reject, reverse) + pending-approvals + cashbook-balance endpoints. Integration tests (~8). Backend only. | **Done** (PR #931) |
| 5b (parallel) | 444 | 444A | Auto-matching algorithm (4-level confidence scoring: exact reference 1.0, amount+date 0.8, amount+close-date 0.6, amount-only 0.4). `autoMatchStatement()`, `manualMatch()`, `unmatch()`, `excludeLine()` service methods. Sign validation. Integration tests (~8). Backend only. | **Done** (PR #935) |
| 5c (parallel) | 445 | 445A | `InterestRun` entity + `InterestAllocation` entity + repos + `InterestService.createInterestRun()` + `calculateInterest()` (daily balance method via transaction-weighted computation, pro-rata LPFF rate splits, HALF_UP rounding). Integration tests (~10). Backend only. | **Done** (PR #937) |
| 5d (parallel) | 446 | 446A | `TrustInvestment` entity + repo + `TrustInvestmentService` (placeInvestment, recordInterestEarned, withdrawInvestment, getMaturing). Investment lifecycle: ACTIVE → MATURED → WITHDRAWN. Integration tests (~6). Backend only. | **Done** (PR #939) |

### Stage 6: Remaining Backend Controllers + Services (parallel tracks)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 6a (parallel) | 442 | 442B | `ClientLedgerController` (5 endpoints: list, getByCustomer, history, statement, totalBalance). Trust account closing guard integration (sum of ledger balances check). Controller integration tests (~6). Backend only. | **Done** (PR #932) |
| 6b (parallel) | 444 | 444B | `TrustReconciliation` entity + repo + `TrustReconciliationService` (createReconciliation, calculateReconciliation with three-way balance check, completeReconciliation with is_balanced guard, outstanding items query). Auto-match + match controller endpoints. Reconciliation controller (5 endpoints). Integration tests (~8). Backend only. | **Done** (PR #936) |
| 6c (parallel) | 445 | 445B | `InterestService.approveInterestRun()` + `postInterestRun()` (creates INTEREST_CREDIT + INTEREST_LPFF transactions atomically). `InterestController` (6 endpoints: create, list, get, calculate, approve, post). Integration tests (~7). Backend only. | **Done** (PR #938) |
| 6d (parallel) | 446 | 446B | `TrustInvestmentController` (6 endpoints: list, get, place, recordInterest, withdraw, maturing). Integration tests (~5). Backend only. | **Done** (PR #940) |

### Stage 7: Reports + Events

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 7a | 447 | 447A | 7 trust `ReportDefinition` seed registrations (TRUST_RECEIPTS_PAYMENTS, CLIENT_TRUST_BALANCES, CLIENT_LEDGER_STATEMENT, TRUST_RECONCILIATION, INVESTMENT_REGISTER, INTEREST_ALLOCATION, SECTION_35_DATA_PACK). Data provider implementations for each. Integration tests (~7). Backend only. | **Done** (PR #941) |
| 7b | 447 | 447B | `TrustDomainEvent` sealed interface + `TrustNotificationHandler` (approval pending, approved, rejected, reconciliation overdue, investment maturing, approval aging). Legal pack content extension (trust templates + automation rules). Integration tests (~5). Backend only. | **Done** (PR #942) |

### Stage 8: Frontend Core (sequential)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 8a | 448 | 448A | Trust dashboard page (replaces stub at `/trust-accounting`): summary cards (total balance, active clients, pending approvals, last reconciliation), recent transactions, reconciliation status indicator, alert badges. TypeScript types, Zod schemas, server actions, nav item update. Frontend tests (~5). Frontend only. | **Done** (PR #944) |
| 8b | 448 | 448B | Transactions page (`/trust-accounting/transactions`): transaction table with filters, `RecordDepositDialog`, `RecordPaymentDialog`, `RecordTransferDialog`, `RecordFeeTransferDialog`, `RecordRefundDialog`, `ApprovalBadge` with approve/reject actions, reversal action. Frontend tests (~5). Frontend only. | **Done** (PR #945) |

### Stage 9: Frontend Ledger + Reconciliation (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 9a (parallel) | 449 | 449A | Client ledger list page (`/trust-accounting/client-ledgers`): ledger card table, detail view with transaction history, running balance column, "Print Statement" button. Types, schemas, actions. Frontend tests (~4). Frontend only. | **Done** (PR #947) |
| 9b (parallel) | 449 | 449B | Reconciliation page (`/trust-accounting/reconciliation`): reconciliation list, new reconciliation flow, `BankStatementUpload` component, `ReconciliationSplitPane` (bank lines left / unmatched transactions right), match/exclude actions, three-way check indicator, "Complete Reconciliation" button. Frontend tests (~5). Frontend only. | **Done** (PR #948) |

### Stage 10: Frontend Interest + Investments + Reports (parallel)

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 10a (parallel) | 450 | 450A | Interest page (`/trust-accounting/interest`): interest runs list, `InterestRunWizard` (create → calculate → approve → post), allocation table, LPFF rate management sub-section. Types, schemas, actions. Frontend tests (~4). Frontend only. | **Done** (PR #951) |
| 10b (parallel) | 450 | 450B | Investment register page (`/trust-accounting/investments`): investment table, `InvestmentDialog` (place), record interest dialog, withdraw action, maturity alerts. Trust reports page (`/trust-accounting/reports`): report list with generation dialogs, format selection. Frontend tests (~4). Frontend only. | Not started |

### Stage 11: Frontend Integration + Coexistence

| Order | Epic | Slice | Summary | Status |
|-------|------|-------|---------|--------|
| 11a | 451 | 451A | Project detail "Trust" tab (module-gated): client trust balance, matter-filtered transaction history, quick actions. Customer detail "Trust" tab (module-gated): total trust balance, ledger summary, active investments. Sidebar nav update (trust-accounting sub-items). Trust settings page (`/settings/trust-accounting`). Frontend tests (~5). Frontend only. | Not started |
| 11b | 451 | 451B | Multi-vertical coexistence tests: trust + accounting tenant isolation (5 backend integration tests), module guard verification, no cross-contamination. Trust-specific E2E smoke tests (3 frontend tests). Tests only. | Not started |

### Timeline

```
Stage 0:  [438A] -> [438B]                                                <- foundation (sequential)
Stage 1:  [439A] -> [439B]                                                <- trust account CRUD (sequential)
Stage 2:  [440A]  //  [443A]                                              <- ledger + import (parallel)
Stage 3:  [440B]  //  [443B]                                              <- continued (parallel)
Stage 4:  [441A] -> [441B]                                                <- approval workflow (sequential)
Stage 5:  [442A]  //  [444A]  //  [445A]  //  [446A]                      <- controllers + domains (parallel)
Stage 6:  [442B]  //  [444B]  //  [445B]  //  [446B]                      <- remaining backend (parallel)
Stage 7:  [447A] -> [447B]                                                <- reports + events (sequential)
Stage 8:  [448A] -> [448B]                                                <- frontend core (sequential)
Stage 9:  [449A]  //  [449B]                                              <- frontend ledger + recon (parallel)
Stage 10: [450A]  //  [450B]                                              <- frontend interest + invest (parallel)
Stage 11: [451A] -> [451B]                                                <- integration + coexistence (sequential)
```

---

## Epic 438: Foundation -- V85 Migration + RBAC Capabilities + Module Registration

**Goal**: Lay the database and infrastructure foundation for all Phase 60 features. Create the V85 tenant migration with all 10 new tables, indexes, constraints, deferred FKs, and capability seeding. Register `VIEW_TRUST`, `MANAGE_TRUST`, and `APPROVE_TRUST_PAYMENT` capabilities. Update module and profile registries. Update stub controller capability.

**References**: Architecture doc Sections 7 (V85 migration SQL), 9 (permission model), 8.1 (module registry); ADR-230 (immutability constraints), ADR-231 (CHECK balance >= 0), ADR-232 (dual approval columns).

**Dependencies**: None (first epic).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **438A** | 438.1--438.5 | V85 tenant migration (10 tables + all indexes + constraints + deferred FK + capability seeding). `VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT` added to `Capability` enum. Default role mapping updates (owner: all 3, admin: VIEW+MANAGE, member: VIEW only). Unit tests (~4). Backend only. | **Done** (PR #921) |
| **438B** | 438.6--438.11 | `VerticalModuleRegistry`: `trust_accounting` → `"active"` with 7 nav items. `VerticalProfileRegistry`: `legal-za` profile update. Stub `TrustAccountingController` capability update to `VIEW_TRUST`. Integration tests (~4). Backend only. | **Done** (PR #922) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 438.1 | Create V85 tenant migration | 438A | -- | New file: `backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql`. Full SQL from architecture doc Section 7: 10 CREATE TABLE blocks (trust_accounts, lpff_rates, trust_transactions, client_ledger_cards, bank_statements, bank_statement_lines, trust_reconciliations, interest_runs, interest_allocations, trust_investments), all CHECK constraints, indexes (17 total), deferred FK (trust_transactions → bank_statement_lines via DO block), and capability seeding (6 INSERT statements for 3 capabilities × owner/admin/member roles). Must be idempotent (`IF NOT EXISTS`, `ON CONFLICT DO NOTHING`). Pattern: `V83__create_legal_foundation_tables.sql`. |
| 438.2 | Add `VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT` to Capability enum | 438A | -- | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java`. Add three new enum values with descriptions: VIEW_TRUST ("View trust accounts, transactions, and reports"), MANAGE_TRUST ("Manage trust accounts and record transactions"), APPROVE_TRUST_PAYMENT ("Approve or reject trust payments"). Pattern: existing VIEW_LEGAL/MANAGE_LEGAL entries. |
| 438.3 | Update default role capability mappings | 438A | 438.2 | Modify: role seed/configuration to include VIEW_TRUST for owner + admin + member, MANAGE_TRUST for owner + admin, APPROVE_TRUST_PAYMENT for owner only. Check `OrgRoleService` or the role seeder for the mechanism. Pattern: how VIEW_LEGAL/MANAGE_LEGAL were added (Phase 55, epic 397). |
| 438.4 | Write unit test for capability registration | 438A | 438.2 | New test or extend existing: verify `Capability.VIEW_TRUST`, `Capability.MANAGE_TRUST`, `Capability.APPROVE_TRUST_PAYMENT` exist and are resolvable. 2 tests. Pure unit tests, no Spring context. |
| 438.5 | Write migration smoke test | 438A | 438.1 | Extend existing migration test or add new: verify V85 migration runs without error on a fresh tenant schema and creates all 10 tables. 2 tests: (1) V85 creates all tables, (2) CHECK constraints are present (test by inserting a row with negative balance into client_ledger_cards and verifying constraint violation). Integration test with Testcontainers. Pattern: existing migration tests from Phase 55 (epic 397). |
| 438.6 | Update `VerticalModuleRegistry` -- activate trust_accounting | 438B | 438.1 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java`. Change `trust_accounting` status from `"stub"` to `"active"`. Add `defaultEnabledFor: ["legal-za"]`. Add 7 nav items: ("/trust-accounting", "Trust Accounting", "legal"), ("/trust-accounting/transactions", "Transactions", "legal"), ("/trust-accounting/client-ledgers", "Client Ledgers", "legal"), ("/trust-accounting/reconciliation", "Reconciliation", "legal"), ("/trust-accounting/interest", "Interest", "legal"), ("/trust-accounting/investments", "Investments", "legal"), ("/trust-accounting/reports", "Trust Reports", "legal"). Pattern: how `court_calendar` was activated in Phase 55 (epic 397). |
| 438.7 | Update `VerticalProfileRegistry` -- add trust_accounting to legal-za | 438B | 438.6 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java`. Update `legal-za` profile `enabled_modules` to include `trust_accounting`: `["court_calendar", "conflict_check", "lssa_tariff", "trust_accounting"]`. Pattern: Phase 55 epic 397 task 397.9. |
| 438.8 | Update stub controller capability | 438B | 438.2 | Modify: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingController.java`. Change `@RequiresCapability("VIEW_LEGAL")` to `@RequiresCapability("VIEW_TRUST")`. This is a temporary change -- the controller will be fully replaced in epic 439. |
| 438.9 | Write integration test for module registration | 438B | 438.6 | 2 tests: (1) `VerticalModuleRegistry.getModule("trust_accounting")` returns active module with 7 nav items, (2) `trust_accounting` module has `defaultEnabledFor` containing `"legal-za"`. Pattern: Phase 55 epic 397 task 397.11. |
| 438.10 | Write integration test for profile update | 438B | 438.7 | 2 tests: (1) `legal-za` profile includes `trust_accounting` in enabled modules, (2) `legal-za` profile includes all 4 legal modules (court_calendar, conflict_check, lssa_tariff, trust_accounting). |
| 438.11 | Write integration test for capability on stub | 438B | 438.8 | 1 test: GET `/api/trust-accounting/status` with member having VIEW_TRUST returns 200, without VIEW_TRUST returns 403. Verifies capability update is correct. |

### Key Files

**Slice 438A -- Create:**
- `backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql`

**Slice 438A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` -- Add VIEW_TRUST, MANAGE_TRUST, APPROVE_TRUST_PAYMENT
- Default role capability mapping (OrgRoleService or seeder) -- Add new capabilities to default roles

**Slice 438B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java` -- Activate trust_accounting with nav items
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalProfileRegistry.java` -- Update legal-za profile
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingController.java` -- Update capability

**Slice 438A/438B -- Read for context:**
- `backend/src/main/resources/db/migration/tenant/V83__create_legal_foundation_tables.sql` -- Migration pattern reference
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/VerticalModuleRegistry.java` -- Module registration pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/orgrole/Capability.java` -- Capability enum pattern

### Architecture Decisions

- **Single V85 migration for all DDL**: All Phase 60 DDL changes go into one tenant migration. This avoids ordering issues between parallel epics. The architecture doc specifies V85 and V84 is the latest existing migration -- confirmed.
- **Capability seeding in migration SQL**: The V85 migration includes INSERT statements that seed the 3 new capabilities into the `org_role_capabilities` table for each default role. This is the same pattern used in Phase 55.
- **Capabilities registered before module activation**: The capability enum additions (438A) must be in place before any endpoint uses `@RequiresCapability("VIEW_TRUST")`. This is why 438A (capabilities) precedes 438B (module activation + controller update).

---

## Epic 439: TrustAccount + LpffRate Entity + Service + Controller

**Goal**: Build the trust account CRUD with LPFF rate management. Replace the stub `TrustAccountingController` with a real implementation serving 7 endpoints. Establish the foundation entity pattern that all subsequent trust entities will follow.

**References**: Architecture doc Sections 2.1 (TrustAccount), 2.2 (LpffRate), 3.1 (service signatures), 4.1 (endpoints); ADR-232 (dual approval fields on TrustAccount).

**Dependencies**: Epic 438 (V85 migration, capabilities, module registration).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **439A** | 439.1--439.7 | `TrustAccount` entity + `LpffRate` entity + `TrustAccountRepository` + `LpffRateRepository` + `TrustAccountService` (list, get, create, update, close with guard, LPFF rate CRUD, module guard, audit events). Integration tests (~8). Backend only. | **Done** (PR #923) |
| **439B** | 439.8--439.12 | Replace stub `TrustAccountingController` with full implementation (7 endpoints: list accounts, get account, create account, update account, close account, list LPFF rates, add LPFF rate). `@RequiresCapability` on all endpoints. Integration tests (~6). Backend only. | **Done** (PR #924) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 439.1 | Create `TrustAccount` entity | 439A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccount.java`. Plain `@Entity` + `@Table(name = "trust_accounts")`. Fields per architecture doc Section 2.1: id (UUID), accountName, bankName, branchCode, accountNumber, accountType (String), isPrimary (boolean), requireDualApproval (boolean), paymentApprovalThreshold (BigDecimal nullable), status (String default "ACTIVE"), openedDate (LocalDate), closedDate (LocalDate nullable), notes (String nullable), createdAt (Instant), updatedAt (Instant). No `@Filter`, no `tenant_id`. Protected no-arg constructor + public business constructor. No Lombok. Pattern: `verticals/legal/courtcalendar/CourtDate.java`. |
| 439.2 | Create `LpffRate` entity | 439A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/lpff/LpffRate.java`. `@Entity` + `@Table(name = "lpff_rates")`. Fields: id (UUID), trustAccountId (UUID, not @ManyToOne), effectiveFrom (LocalDate), ratePercent (BigDecimal), lpffSharePercent (BigDecimal), notes (String nullable), createdAt (Instant). Pattern: same as TrustAccount. |
| 439.3 | Create `TrustAccountRepository` | 439A | 439.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountRepository.java`. Extends `JpaRepository<TrustAccount, UUID>`. Custom queries: `findByStatus(String)`, `findByAccountTypeAndIsPrimaryTrue(String)`. Pattern: `CourtDateRepository.java`. |
| 439.4 | Create `LpffRateRepository` | 439A | 439.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/lpff/LpffRateRepository.java`. Extends `JpaRepository<LpffRate, UUID>`. Custom queries: `findByTrustAccountIdOrderByEffectiveFromDesc(UUID)`, `@Query` for `findEffectiveRate(UUID accountId, LocalDate asOfDate)` -- most recent rate where effectiveFrom <= asOfDate. |
| 439.5 | Create `TrustAccountService` | 439A | 439.3, 439.4 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountService.java`. Spring `@Service`. Constructor-injected: repos, `VerticalModuleGuard`, `AuditEventService`. Methods: `listTrustAccounts()`, `getTrustAccount(id)`, `createTrustAccount(dto)`, `updateTrustAccount(id, dto)`, `closeTrustAccount(id)` (closing guard: sum of client ledger balances must be zero), `getPrimaryAccount()`, `addLpffRate(accountId, dto)`, `listLpffRates(accountId)`, `getCurrentLpffRate(accountId, date)`. Every method starts with `moduleGuard.requireModule("trust_accounting")`. DTO records: `CreateTrustAccountRequest`, `UpdateTrustAccountRequest`, `TrustAccountResponse`, `CreateLpffRateRequest`, `LpffRateResponse`. Pattern: `CourtCalendarService.java`. |
| 439.6 | Write integration tests for TrustAccountService | 439A | 439.5 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountServiceTest.java`. 8 tests: (1) create trust account saves with ACTIVE status, (2) only one primary GENERAL account allowed, (3) update account changes bank details, (4) close account succeeds when no client ledger balances, (5) close account fails with non-zero ledger balance (deferred -- test placeholder for now, actual test when ledger exists), (6) list returns active accounts, (7) LPFF rate added and effective rate resolved correctly, (8) module guard throws when trust_accounting not enabled. Use `@SpringBootTest` + `TestcontainersConfiguration`. |
| 439.7 | Write integration test for LPFF rate resolution | 439A | 439.5 | 2 additional tests: (1) `getCurrentLpffRate` returns most recent rate before date, (2) `getCurrentLpffRate` with no rates throws appropriate error. |
| 439.8 | Replace stub `TrustAccountingController` with full implementation | 439B | 439A | Modify (full replacement): `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingController.java`. Remove stub `/status` endpoint. Add 7 endpoints: `GET /api/trust-accounts` (VIEW_TRUST), `GET /api/trust-accounts/{id}` (VIEW_TRUST), `POST /api/trust-accounts` (MANAGE_TRUST), `PUT /api/trust-accounts/{id}` (MANAGE_TRUST), `POST /api/trust-accounts/{id}/close` (MANAGE_TRUST), `GET /api/trust-accounts/{id}/lpff-rates` (VIEW_TRUST), `POST /api/trust-accounts/{id}/lpff-rates` (MANAGE_TRUST). Pure delegation to `TrustAccountService`. Change base mapping from `/api/trust-accounting` to `/api/trust-accounts`. Pattern: `CourtCalendarController.java`. **Thin controller discipline**: every method is a one-liner. |
| 439.9 | Define request/response DTO records | 439B | 439.8 | DTO records nested in controller or in `dto/` sub-package. `CreateTrustAccountRequest`, `UpdateTrustAccountRequest`, `TrustAccountResponse` (all fields + computed `currentBalance` placeholder), `CreateLpffRateRequest(LocalDate effectiveFrom, BigDecimal ratePercent, BigDecimal lpffSharePercent, String notes)`, `LpffRateResponse`. Pattern: existing controller DTOs. |
| 439.10 | Write controller integration tests -- CRUD | 439B | 439.8 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingControllerTest.java`. 4 tests: (1) POST creates trust account returns 201, (2) GET list returns accounts, (3) GET by id returns detail, (4) PUT updates trust account. |
| 439.11 | Write controller integration tests -- lifecycle + rates | 439B | 439.8 | 2 tests: (1) POST close returns 200 (empty account), (2) POST LPFF rate returns 201 with rate detail. |
| 439.12 | Write controller authorization test | 439B | 439.8 | 1 test: POST /api/trust-accounts with member lacking MANAGE_TRUST returns 403. |

### Key Files

**Slice 439A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccount.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/lpff/LpffRate.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/lpff/LpffRateRepository.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountServiceTest.java`

**Slice 439B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingController.java` -- Full replacement of stub

**Slice 439B -- Create:**
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountingControllerTest.java`

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDate.java` -- Entity pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarService.java` -- Service pattern
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarController.java` -- Controller pattern

### Architecture Decisions

- **Closing guard deferred**: The `closeTrustAccount()` method checks that the sum of all `ClientLedgerCard.balance` values for the account is zero. Since `ClientLedgerCard` is not created until epic 440, the closing guard will always pass at this stage. A placeholder integration test is added now; the real test is in epic 442B.
- **UUID-based FK references**: `LpffRate.trustAccountId` is a `UUID` field, not a `@ManyToOne` JPA relationship. This follows the established Phase 55 pattern for the legal vertical.
- **Controller base path change**: The stub used `/api/trust-accounting`. The real controller uses `/api/trust-accounts` to follow REST resource naming conventions. The old `/status` endpoint is removed entirely.

---

## Epic 440: TrustTransaction + ClientLedgerCard Entity + Deposit/Transfer Service

**Goal**: Build the core trust transaction and client ledger entities with the deposit and transfer recording flows. Establish the immutable transaction pattern and the negative balance prevention mechanism (SELECT FOR UPDATE + CHECK constraint).

**References**: Architecture doc Sections 2.3 (TrustTransaction), 2.4 (ClientLedgerCard), 3.1 (recording flows), 3.3 (negative balance prevention), 3.4 (reversal); ADR-230 (immutability), ADR-231 (negative balance).

**Dependencies**: Epic 439 (TrustAccount must exist for FK).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **440A** | 440.1--440.8 | `TrustTransaction` entity + `ClientLedgerCard` entity + repos (including `@Lock(PESSIMISTIC_WRITE)` query) + `TrustTransactionService` (recordDeposit, recordTransfer with paired TRANSFER_IN/TRANSFER_OUT) + `ClientLedgerService` (getClientLedger, listClientLedgers, getClientTransactionHistory). Negative balance prevention. Integration tests (~12). Backend only. | **Done** (PR #925) |
| **440B** | 440.9--440.14 | `reverseTransaction()` with conditional approval (credit vs debit reversals). `getCashbookBalance()`. `getClientBalanceAsOfDate()`. `getTotalTrustBalance()`. `getClientLedgerStatement()`. Additional integration tests (~8). Backend only. | **Done** (PR #926) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 440.1 | Create `TrustTransaction` entity | 440A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransaction.java`. `@Entity` + `@Table(name = "trust_transactions")`. Fields per architecture doc Section 2.3: all 21 columns. No `updatedAt` column (intentional -- immutability). String-typed status/type. Protected no-arg + public business constructor. |
| 440.2 | Create `ClientLedgerCard` entity | 440A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerCard.java`. `@Entity` + `@Table(name = "client_ledger_cards")`. Fields per architecture doc Section 2.4: id, trustAccountId, customerId, balance, totalDeposits, totalPayments, totalFeeTransfers, totalInterestCredited, lastTransactionDate, createdAt, updatedAt. |
| 440.3 | Create `TrustTransactionRepository` | 440A | 440.1 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionRepository.java`. Custom queries: `findByTrustAccountIdOrderByTransactionDateDesc(UUID, Pageable)`, `findByCustomerIdAndTrustAccountIdOrderByTransactionDateDesc(UUID, UUID)`, `findByStatusAndTrustAccountId(String, UUID)` (for pending approvals), `@Query` for cashbook balance SUM. |
| 440.4 | Create `ClientLedgerCardRepository` with FOR UPDATE query | 440A | 440.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerCardRepository.java`. Methods: `findByTrustAccountIdAndCustomerId(UUID, UUID)`, `@Lock(LockModeType.PESSIMISTIC_WRITE) @Query("SELECT c FROM ClientLedgerCard c WHERE c.trustAccountId = :accountId AND c.customerId = :customerId") findByAccountAndCustomerForUpdate(UUID accountId, UUID customerId)`, `findByTrustAccountId(UUID, Pageable)`. Pattern: ADR-231 specifies SELECT FOR UPDATE via `@Lock(PESSIMISTIC_WRITE)`. |
| 440.5 | Create `TrustTransactionService` -- deposit and transfer | 440A | 440.3, 440.4 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java`. Methods: `recordDeposit(trustAccountId, dto)` -- creates DEPOSIT with status RECORDED, upserts ClientLedgerCard (increment balance + totalDeposits), emits audit event. `recordTransfer(trustAccountId, dto)` -- validates source != target, acquires FOR UPDATE lock on source ledger, checks balance >= amount, creates paired TRANSFER_OUT + TRANSFER_IN atomically, updates both ledger cards. All within `@Transactional`. DTO records: `RecordDepositRequest`, `RecordTransferRequest`, `TrustTransactionResponse`. |
| 440.6 | Create `ClientLedgerService` | 440A | 440.4 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java`. Methods: `getClientLedger(customerId, trustAccountId)`, `listClientLedgers(trustAccountId, filters, pageable)`, `getClientTransactionHistory(customerId, trustAccountId, dateRange)`. Module guard on all methods. |
| 440.7 | Write integration tests for deposit and ledger | 440A | 440.5, 440.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionServiceTest.java`. 8 tests: (1) deposit creates transaction with RECORDED status, (2) deposit upserts ClientLedgerCard with correct balance, (3) second deposit increments existing ledger card, (4) deposit emits audit event, (5) list client ledger returns cards, (6) get client transaction history returns deposit, (7) transfer creates paired TRANSFER_OUT + TRANSFER_IN, (8) transfer updates both ledger cards. |
| 440.8 | Write negative balance prevention tests | 440A | 440.5 | 4 additional tests: (1) transfer fails with insufficient balance (clear error message with client name), (2) concurrent transfers for same client -- second blocked by FOR UPDATE lock and sees updated balance, (3) CHECK constraint verification -- direct SQL attempt to set negative balance fails, (4) transfer with exact balance succeeds (edge case: balance goes to zero). |
| 440.9 | Implement reversal flow | 440B | 440A | Extend `TrustTransactionService` with `reverseTransaction(transactionId, reason)`. Validates original is APPROVED and not already reversed. Creates REVERSAL transaction with `reversal_of` FK. Updates original's `reversed_by_id` and status to REVERSED. Conditional approval: debit reversals (add money back) are immediate; credit reversals (remove money) require approval + negative balance check. |
| 440.10 | Implement cashbook balance calculation | 440B | 440A | Add `getCashbookBalance(trustAccountId)` to `TrustTransactionService`. `@Query` that SUMs cashbook-affecting transactions: DEPOSIT + INTEREST_CREDIT (positive), PAYMENT + FEE_TRANSFER + REFUND + INTEREST_LPFF (negative). Only APPROVED + RECORDED status transactions. Returns BigDecimal. |
| 440.11 | Implement historical balance and total balance | 440B | 440A | Add `getClientBalanceAsOfDate(customerId, trustAccountId, date)` to `ClientLedgerService` -- reconstructs balance by summing transactions up to date. Add `getTotalTrustBalance(trustAccountId)` -- SUM of all ClientLedgerCard.balance values. Add `getClientLedgerStatement(customerId, trustAccountId, dateRange)` -- returns list of transactions with running balance. |
| 440.12 | Write reversal integration tests | 440B | 440.9 | 4 tests: (1) reverse a DEPOSIT (credit reversal) -- requires approval, status AWAITING_APPROVAL, (2) reverse a TRANSFER_OUT debit reversal -- immediate ledger effect, (3) attempt to reverse already-reversed transaction fails, (4) attempt to reverse AWAITING_APPROVAL transaction fails. |
| 440.13 | Write cashbook balance tests | 440B | 440.10 | 2 tests: (1) cashbook balance correct after deposits and transfers, (2) cashbook balance excludes REJECTED transactions. |
| 440.14 | Write historical balance tests | 440B | 440.11 | 2 tests: (1) historical balance at point in time matches expected, (2) total trust balance equals sum of ledger card balances. |

### Key Files

**Slice 440A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransaction.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerCard.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerCardRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionServiceTest.java`

**Slice 440B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java` -- Add reversal, cashbook balance
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerService.java` -- Add historical balance, total balance, statement

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtDate.java` -- Entity pattern reference
- Architecture doc Section 3.3 -- Negative balance prevention pseudocode

### Architecture Decisions

- **Ledger card auto-creation**: The `ClientLedgerCard` is upserted (create if not exists, update if exists) on the first deposit for a client. This avoids requiring separate ledger card creation. The upsert is within the same `@Transactional` as the transaction INSERT.
- **Transfer lock ordering**: When a transfer locks two ledger cards (source and target), locks are acquired in ascending UUID order to prevent deadlocks. This is documented in the `recordTransfer()` method.
- **No controller in this epic**: This epic builds service-layer only. The `TrustTransactionController` and `ClientLedgerController` are created in epic 442 after the approval workflow (epic 441) is complete. This keeps each slice within the file count limit.

---

## Epic 441: Approval Workflow + Payment/FeeTransfer/Refund Recording

**Goal**: Implement the approval workflow for trust payments, fee transfers, and refunds. Support single approval, dual approval, and threshold-based dual approval. Enforce self-approval prevention. Integrate fee transfer approval with the invoice system.

**References**: Architecture doc Sections 3.1 (payment/feeTransfer/refund flows), 3.2 (approval workflow), 3.3 (negative balance); ADR-231 (negative balance), ADR-232 (configurable dual authorization).

**Dependencies**: Epic 440 (TrustTransaction, ClientLedgerCard entities and services).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **441A** | 441.1--441.6 | `recordPayment()`, `recordFeeTransfer()`, `recordRefund()` creating transactions in AWAITING_APPROVAL status. `approveTransaction()` with single approval mode + self-approval prevention + negative balance check on approval. `rejectTransaction()` with reason. Integration tests (~10). Backend only. | **Done** (PR #928) |
| **441B** | 441.7--441.12 | Dual approval mode + threshold-based dual approval. Fee transfer → `InvoiceService.recordPayment()` on approval. `TrustNotificationHandler` skeleton for approval notification fan-out. Concurrent approval race condition tests. Integration tests (~10). Backend only. | **Done** (PR #929) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 441.1 | Implement recordPayment | 441A | -- | Extend `TrustTransactionService`. `recordPayment(trustAccountId, dto)` -- validate account ACTIVE, customer exists, create TrustTransaction with type PAYMENT, status AWAITING_APPROVAL. Do NOT update ledger (deferred to approval). Emit audit event `TRUST_PAYMENT_RECORDED`. DTO: `RecordPaymentRequest(UUID customerId, UUID projectId, BigDecimal amount, String reference, String description, LocalDate transactionDate)`. |
| 441.2 | Implement recordFeeTransfer | 441A | -- | `recordFeeTransfer(trustAccountId, dto)` -- validate account ACTIVE, customer exists, invoice exists and belongs to customer, invoice status APPROVED or SENT. Create TrustTransaction with type FEE_TRANSFER, status AWAITING_APPROVAL, invoiceId set. Emit audit event. DTO: `RecordFeeTransferRequest(UUID customerId, UUID invoiceId, BigDecimal amount, String reference)`. |
| 441.3 | Implement recordRefund | 441A | -- | `recordRefund(trustAccountId, dto)` -- validate account ACTIVE, customer exists. Create TrustTransaction with type REFUND, status AWAITING_APPROVAL. Emit audit event. DTO: `RecordRefundRequest(UUID customerId, BigDecimal amount, String reference, String description, LocalDate transactionDate)`. |
| 441.4 | Implement approveTransaction -- single mode | 441A | 441.1 | `approveTransaction(transactionId, approverId)` -- validate status AWAITING_APPROVAL, approver has APPROVE_TRUST_PAYMENT capability, self-approval prevention (approverId != recordedBy). Acquire FOR UPDATE lock on ClientLedgerCard, check balance >= amount for debit types. Set approved_by, approved_at, status APPROVED. Update ClientLedgerCard (decrement balance for debits). All within `@Transactional`. Emit audit event TRUST_PAYMENT_APPROVED. |
| 441.5 | Implement rejectTransaction | 441A | -- | `rejectTransaction(transactionId, rejecterId, reason)` -- validate status AWAITING_APPROVAL, rejector has APPROVE_TRUST_PAYMENT. Set rejected_by, rejected_at, rejection_reason, status REJECTED. No ledger effect. Emit audit event TRUST_PAYMENT_REJECTED. |
| 441.6 | Write approval integration tests -- single mode | 441A | 441.4, 441.5 | 10 tests: (1) payment created in AWAITING_APPROVAL, (2) approve transitions to APPROVED and debits ledger, (3) self-approval prevention returns 400, (4) approve with insufficient balance returns 400 with clear message, (5) reject transitions to REJECTED with no ledger effect, (6) approve non-AWAITING transaction returns 400, (7) fee transfer created with invoiceId, (8) refund created in AWAITING_APPROVAL, (9) approve payment emits audit event, (10) rejection includes reason in response. |
| 441.7 | Implement dual approval mode | 441B | 441A | Extend `approveTransaction()`. When `trustAccount.requireDualApproval == true` and (threshold is null or amount >= threshold): first approval sets `approved_by`, status remains AWAITING_APPROVAL. Second approval (must differ from first) sets `second_approved_by`, proceeds with negative balance check and ledger update. Self-approval in dual mode: recorder can be one of two approvers but not both. |
| 441.8 | Implement threshold-based dual approval | 441B | 441.7 | When `trustAccount.requireDualApproval == true` and `paymentApprovalThreshold != null`: if `amount < threshold`, single approval suffices. If `amount >= threshold`, dual approval required. The determination is at approval time based on current account settings. |
| 441.9 | Implement fee transfer invoice integration | 441B | 441A | On approval of FEE_TRANSFER: call `InvoiceService.recordPayment(invoiceId, reference)` to transition invoice to PAID status. This is within the same `@Transactional` block as the ledger update. Inject `InvoiceService` into `TrustTransactionService`. |
| 441.10 | Create TrustNotificationHandler skeleton | 441B | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustNotificationHandler.java`. Listens for `@TransactionalEventListener` events. On AWAITING_APPROVAL: notify members with APPROVE_TRUST_PAYMENT capability (in-app + email). On APPROVED/REJECTED: notify recorder (in-app). Skeleton -- wires to NotificationService. |
| 441.11 | Write dual approval integration tests | 441B | 441.7, 441.8 | 6 tests: (1) dual mode -- first approval sets approved_by, status stays AWAITING_APPROVAL, (2) second approval completes and debits ledger, (3) same person as first and second approver rejected, (4) recorder as first approver + different second approver succeeds, (5) threshold -- amount below threshold uses single approval, (6) threshold -- amount at threshold requires dual. |
| 441.12 | Write concurrent approval and fee transfer tests | 441B | 441.9 | 4 tests: (1) concurrent approvals for same client -- second sees updated balance, (2) fee transfer approval marks invoice as PAID, (3) fee transfer for already-PAID invoice fails, (4) notification handler sends to approvers on AWAITING_APPROVAL. |

### Key Files

**Slice 441A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java` -- Add recordPayment, recordFeeTransfer, recordRefund, approveTransaction, rejectTransaction

**Slice 441B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java` -- Add dual approval, threshold, invoice integration

**Slice 441B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustNotificationHandler.java`

**Read for context:**
- Architecture doc Section 3.2 -- Approval workflow pseudocode
- Architecture doc Section 9.3 -- Self-approval prevention rules
- Architecture doc Section 9.4 -- Dual approval + threshold interaction table
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceService.java` -- recordPayment() method

### Architecture Decisions

- **Approval determination at approval time**: The required approval mode (single vs dual) is determined when `approveTransaction()` is called, not when the transaction is recorded. This means changing a trust account's approval settings affects pending transactions. This is the architecture doc's specified behavior.
- **Invoice integration is fire-and-within-transaction**: The `InvoiceService.recordPayment()` call happens within the same `@Transactional` block as the ledger update. If the invoice update fails, the entire approval rolls back. No external calls while holding the row lock.
- **Notification via @TransactionalEventListener**: Notifications are dispatched after the transaction commits successfully, not during. This avoids sending notifications for rolled-back transactions.

---

## Epic 442: Transaction Controller + Approval Endpoints + Client Ledger Controller

**Goal**: Expose all trust transaction and client ledger operations as REST endpoints. Complete the backend API surface for the core trust accounting workflows.

**References**: Architecture doc Sections 4.2 (transaction endpoints), 4.3 (approval endpoints), 4.4 (client ledger endpoints), 4.9 (request/response shapes).

**Dependencies**: Epic 441 (all service methods must be available).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **442A** | 442.1--442.5 | `TrustTransactionController` (7 recording endpoints + 3 approval endpoints + pending-approvals + cashbook-balance). Integration tests (~8). Backend only. | **Done** (PR #931) |
| **442B** | 442.6--442.10 | `ClientLedgerController` (5 endpoints). Trust account closing guard integration (real ledger balance check). Controller integration tests (~6). Backend only. | **Done** (PR #932) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 442.1 | Create `TrustTransactionController` | 442A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionController.java`. 12 endpoints total: `GET /api/trust-accounts/{accountId}/transactions` (VIEW_TRUST, paginated with filters: dateFrom, dateTo, type, status, customerId, projectId), `GET /api/trust-accounts/{accountId}/transactions/{id}` (VIEW_TRUST), `POST .../transactions/deposit` (MANAGE_TRUST), `POST .../transactions/payment` (MANAGE_TRUST), `POST .../transactions/transfer` (MANAGE_TRUST), `POST .../transactions/fee-transfer` (MANAGE_TRUST), `POST .../transactions/refund` (MANAGE_TRUST), `POST /api/trust-transactions/{id}/approve` (APPROVE_TRUST_PAYMENT), `POST /api/trust-transactions/{id}/reject` (APPROVE_TRUST_PAYMENT), `POST /api/trust-transactions/{id}/reverse` (MANAGE_TRUST), `GET /api/trust-accounts/{accountId}/pending-approvals` (VIEW_TRUST), `GET /api/trust-accounts/{accountId}/cashbook-balance` (VIEW_TRUST). **Thin controller**: every method is a one-liner delegating to service. Pattern: `CourtCalendarController.java`. |
| 442.2 | Define transaction DTO records | 442A | 442.1 | Response records: `TrustTransactionResponse` (all fields + customerName + recordedByName + approvedByName). Include `clientBalance` field in approve response. Pattern: architecture doc Section 4.9 response shapes. |
| 442.3 | Write controller integration tests -- recording | 442A | 442.1 | 4 tests: (1) POST deposit returns 201, (2) POST payment returns 201 with AWAITING_APPROVAL, (3) POST transfer returns 201 with paired transactions, (4) GET transactions returns paginated list with filters. |
| 442.4 | Write controller integration tests -- approval | 442A | 442.1 | 3 tests: (1) POST approve returns 200 with APPROVED status, (2) POST reject returns 200 with REJECTED status and reason, (3) POST reverse returns 201 with reversal transaction. |
| 442.5 | Write controller authorization tests | 442A | 442.1 | 1 test: POST approve with member lacking APPROVE_TRUST_PAYMENT returns 403. |
| 442.6 | Create `ClientLedgerController` | 442B | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerController.java`. 5 endpoints: `GET /api/trust-accounts/{accountId}/client-ledgers` (VIEW_TRUST, paginated, filters: nonZeroOnly, search), `GET .../client-ledgers/{customerId}` (VIEW_TRUST), `GET .../client-ledgers/{customerId}/history` (VIEW_TRUST), `GET .../client-ledgers/{customerId}/statement` (VIEW_TRUST, date range params), `GET /api/trust-accounts/{accountId}/total-balance` (VIEW_TRUST). Pure delegation. |
| 442.7 | Define client ledger DTO records | 442B | 442.6 | `ClientLedgerCardResponse` (all fields + customerName), `LedgerStatementResponse` (transactions with running balance, opening balance, closing balance). |
| 442.8 | Integrate trust account closing guard with real ledger | 442B | 442.6 | Modify `TrustAccountService.closeTrustAccount()`: now queries `ClientLedgerCardRepository` for SUM of balances. If > 0, throw `InvalidStateException("Cannot close trust account: R{total} in client trust balances must be disbursed first.")`. |
| 442.9 | Write controller integration tests -- ledger | 442B | 442.6 | 4 tests: (1) GET client ledgers returns list, (2) GET by customer returns ledger card, (3) GET history returns transaction list, (4) GET total-balance returns correct sum. |
| 442.10 | Write closing guard integration test | 442B | 442.8 | 2 tests: (1) close account fails when client has non-zero trust balance, (2) close account succeeds after all balances are zero. |

### Key Files

**Slice 442A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionControllerTest.java`

**Slice 442B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerController.java`
- `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerControllerTest.java`

**Slice 442B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/TrustAccountService.java` -- Real closing guard

**Read for context:**
- Architecture doc Section 4.9 -- Request/response JSON shapes
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/courtcalendar/CourtCalendarController.java` -- Thin controller pattern

### Architecture Decisions

- **Two controllers for transactions**: `TrustTransactionController` handles recording and approval at `/api/trust-accounts/{accountId}/transactions/*` and `/api/trust-transactions/{id}/*`. `ClientLedgerController` handles the client-facing view at `/api/trust-accounts/{accountId}/client-ledgers/*`. This matches the domain boundary in the architecture doc.
- **Approval endpoints at transaction level**: Approve/reject/reverse use `/api/trust-transactions/{id}/...` (not nested under account) because the transaction ID is globally unique and the account context is derivable from the transaction itself.

---

## Epic 443: Bank Statement Import + CSV Parsers

**Goal**: Build the bank statement import pipeline with pluggable CSV parsers for the four major SA banks plus a generic fallback. Store original files in S3. Expose import and listing endpoints.

**References**: Architecture doc Sections 2.5 (BankStatement), 2.6 (BankStatementLine), 6.1 (CSV parser strategy pattern), 3.5 (import flow); ADR-233 (matching strategy context).

**Dependencies**: Epic 439 (TrustAccount for FK).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **443A** | 443.1--443.7 | `BankStatement` + `BankStatementLine` entities + repos + `BankStatementParser` interface + 5 CSV parser implementations (FNB, StandardBank, Nedbank, ABSA, Generic) + CSV fixture files. Unit tests (~10). Backend only. | **Done** (PR #933) |
| **443B** | 443.8--443.12 | Bank statement import endpoint (multipart upload + S3 storage) + list/detail endpoints in reconciliation controller. Integration tests (~5). Backend only. | **Done** (PR #934) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 443.1 | Create `BankStatement` entity | 443A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/BankStatement.java`. Fields per architecture doc Section 2.5. |
| 443.2 | Create `BankStatementLine` entity | 443A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/BankStatementLine.java`. Fields per architecture doc Section 2.6. Note: `amount` is signed (positive = credit, negative = debit). |
| 443.3 | Create repositories | 443A | 443.1, 443.2 | `BankStatementRepository` + `BankStatementLineRepository`. Custom queries: `findByTrustAccountIdOrderByPeriodEndDesc(UUID)`, `findByBankStatementIdOrderByLineNumber(UUID)`, `findByMatchStatus(String)`. |
| 443.4 | Create `BankStatementParser` interface + `ParsedStatement` record | 443A | -- | New files in `reconciliation/parser/`: `BankStatementParser.java` (interface: `canParse(String fileName, String headerLine) -> boolean`, `parse(InputStream) -> ParsedStatement`), `ParsedStatement.java` (record: periodStart, periodEnd, openingBalance, closingBalance, `List<ParsedStatementLine>` lines), `ParsedStatementLine.java` (record: date, description, reference, amount, runningBalance). |
| 443.5 | Implement CSV parsers | 443A | 443.4 | New files: `CsvBankStatementParser.java` (abstract base with common CSV reading logic), `FnbCsvParser.java` (header detection: "FNB"/"First National", date format dd/MM/yyyy), `StandardBankCsvParser.java` (header: "Standard Bank", date: yyyy-MM-dd), `NedbankCsvParser.java` (header: "Nedbank", date: dd MMM yyyy), `AbsaCsvParser.java` (header: "Absa"/"ABSA", date: dd/MM/yyyy), `GenericCsvParser.java` (fallback, configurable columns). Each implements `BankStatementParser`. |
| 443.6 | Create CSV fixture files | 443A | 443.5 | New files in `backend/src/test/resources/fixtures/trust/`: `fnb-sample.csv`, `standard-bank-sample.csv`, `nedbank-sample.csv`, `absa-sample.csv`, `generic-sample.csv`. Each with realistic SA bank statement data (5-10 lines). |
| 443.7 | Write parser unit tests | 443A | 443.5, 443.6 | New file: `backend/src/test/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/parser/BankStatementParserTest.java`. 10 tests: (1-4) each bank parser correctly detects its format via canParse, (5) FNB parser extracts period + lines correctly, (6) Standard Bank parser handles yyyy-MM-dd dates, (7) Nedbank parser handles dd MMM yyyy dates, (8) Generic parser works as fallback, (9) parser handles empty statement, (10) parser handles malformed CSV gracefully. |
| 443.8 | Create bank statement import service | 443B | 443A | Extend or create `TrustReconciliationService` in `reconciliation/`. Method: `importBankStatement(accountId, multipartFile)` -- store file in S3 at `trust-statements/{tenantSchema}/{accountId}/{filename}`, detect format (iterate parsers, first `canParse()` wins), parse file, create BankStatement + BankStatementLine records, return statement with lines. Inject `StorageService`. |
| 443.9 | Create reconciliation controller -- import endpoints | 443B | 443.8 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationController.java`. 3 endpoints: `POST /api/trust-accounts/{accountId}/bank-statements` (MANAGE_TRUST, multipart), `GET /api/trust-accounts/{accountId}/bank-statements` (VIEW_TRUST), `GET /api/bank-statements/{statementId}` (VIEW_TRUST, includes lines). Thin controller. |
| 443.10 | Write import integration tests | 443B | 443.9 | 3 tests: (1) multipart upload of FNB CSV creates statement + lines, (2) statement detail includes all parsed lines, (3) list statements returns paginated results. |
| 443.11 | Write S3 storage test | 443B | 443.8 | 1 test: verify file stored at correct S3 path pattern. Use mock S3 or LocalStack. |
| 443.12 | Write format detection test | 443B | 443.8 | 1 test: upload generic CSV when no bank header detected -- GenericCsvParser used as fallback. |

### Key Files

**Slice 443A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/BankStatement.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/BankStatementLine.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/BankStatementRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/BankStatementLineRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/parser/` (7 files)
- `backend/src/test/resources/fixtures/trust/` (5 CSV fixtures)
- `backend/src/test/java/.../reconciliation/parser/BankStatementParserTest.java`

**Slice 443B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationController.java`
- `backend/src/test/java/.../reconciliation/TrustReconciliationControllerTest.java`

**Read for context:**
- Architecture doc Section 6.1 -- CSV parser strategy pattern with bank detection table
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/s3/` -- S3 storage service pattern

### Architecture Decisions

- **Strategy pattern for parsers**: Each bank format is a separate class implementing `BankStatementParser`. New bank formats are added by implementing the interface -- no changes to import logic. The detection flow iterates all registered parsers.
- **S3 storage of originals**: The original CSV file is stored in S3 for audit trail purposes. Even after parsing, the original is available for re-import or verification.
- **Parser as unit tests, import as integration tests**: CSV parsers are pure logic (no Spring context needed) and tested as unit tests with fixture files. The import flow (S3 + DB) requires integration tests.

---

## Epic 444: Auto-Matching + Manual Matching + Reconciliation

**Goal**: Implement the bank statement auto-matching algorithm, manual matching endpoints, and the three-way reconciliation calculation with completion guard.

**References**: Architecture doc Sections 3.5 (auto-matching), 3.6 (three-way reconciliation), 6.2 (algorithm details), 2.7 (TrustReconciliation entity); ADR-233 (matching strategy).

**Dependencies**: Epic 440 (trust transactions for matching candidates), Epic 443 (bank statements).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **444A** | 444.1--444.5 | Auto-matching algorithm (4-level confidence scoring), manual match/unmatch/exclude service methods. Integration tests (~8). Backend only. | **Done** (PR #935) |
| **444B** | 444.6--444.11 | `TrustReconciliation` entity + repo + three-way reconciliation calculation + completion guard + reconciliation controller endpoints (auto-match trigger, manual match, reconciliation CRUD). Integration tests (~8). Backend only. | **Done** (PR #936) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 444.1 | Implement auto-matching algorithm | 444A | -- | Add `autoMatchStatement(statementId)` to `TrustReconciliationService`. For each UNMATCHED bank statement line: build candidate pool (approved transactions without bank_statement_line_id, date range ±7 days). Score each candidate: (1) exact reference match → 1.00, (2) amount + exact date with single candidate → 0.80, (3) amount + close date (±3 days) with single candidate → 0.60, (4) amount only → 0.40. Sign validation: positive bank line → credit types, negative → debit types. Auto-match at confidence >= 0.80. Update BankStatementLine.matchStatus, trustTransactionId, matchConfidence. Update BankStatement.matchedCount. |
| 444.2 | Implement manual match/unmatch/exclude | 444A | -- | `manualMatch(lineId, transactionId)` -- validate line is UNMATCHED, transaction is approved and unmatched. Set matchStatus = MANUALLY_MATCHED, trustTransactionId, update transaction's bankStatementLineId. `unmatch(lineId)` -- revert to UNMATCHED, clear FK. `excludeLine(lineId, reason)` -- set EXCLUDED with reason. |
| 444.3 | Write auto-match tests | 444A | 444.1 | 5 tests: (1) exact reference match → confidence 1.00 + AUTO_MATCHED, (2) amount + same date + single candidate → 0.80 + AUTO_MATCHED, (3) amount + close date → 0.60 + stays UNMATCHED, (4) amount only multiple candidates → 0.40 + UNMATCHED, (5) sign mismatch excluded from candidates. |
| 444.4 | Write manual match tests | 444A | 444.2 | 3 tests: (1) manual match links line and transaction, (2) unmatch reverts both, (3) exclude sets reason and EXCLUDED status. |
| 444.5 | Write edge case tests | 444A | 444.1 | 2 tests: (1) auto-match skips already-matched transactions, (2) auto-match handles empty candidate pool gracefully. |
| 444.6 | Create `TrustReconciliation` entity + repository | 444B | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliation.java`. Fields per architecture doc Section 2.7. `TrustReconciliationRepository` with query `findByTrustAccountIdOrderByPeriodEndDesc(UUID)`. |
| 444.7 | Implement reconciliation service methods | 444B | 444.6, 444A | `createReconciliation(accountId, periodEnd, bankStatementId)` -- create DRAFT. `calculateReconciliation(reconciliationId)` -- compute bank_balance (from statement), cashbook_balance (SUM of approved cashbook transactions), client_ledger_total (SUM of ledger card balances), outstanding_deposits (approved deposits not matched), outstanding_payments (approved payments not matched), adjusted_bank_balance, is_balanced check. `completeReconciliation(reconciliationId)` -- guard: is_balanced must be true. Set status COMPLETED. |
| 444.8 | Add reconciliation controller endpoints | 444B | 444.7 | Extend `TrustReconciliationController` with: `POST /api/bank-statements/{statementId}/auto-match` (MANAGE_TRUST), `POST /api/bank-statement-lines/{lineId}/match` (MANAGE_TRUST), `POST /api/bank-statement-lines/{lineId}/unmatch` (MANAGE_TRUST), `POST /api/bank-statement-lines/{lineId}/exclude` (MANAGE_TRUST), `POST /api/trust-accounts/{accountId}/reconciliations` (MANAGE_TRUST), `GET /api/trust-accounts/{accountId}/reconciliations` (VIEW_TRUST), `GET /api/trust-reconciliations/{reconciliationId}` (VIEW_TRUST), `POST /api/trust-reconciliations/{reconciliationId}/calculate` (MANAGE_TRUST), `POST /api/trust-reconciliations/{reconciliationId}/complete` (MANAGE_TRUST). |
| 444.9 | Write reconciliation calculation tests | 444B | 444.7 | 4 tests: (1) calculation populates all balance fields correctly, (2) is_balanced true when all three agree, (3) is_balanced false with discrepancy, (4) outstanding items correctly identified (approved but unmatched transactions). |
| 444.10 | Write completion guard tests | 444B | 444.7 | 2 tests: (1) complete succeeds when balanced, (2) complete fails with 400 when not balanced (error details the difference). |
| 444.11 | Write reconciliation controller tests | 444B | 444.8 | 2 tests: (1) POST reconciliation returns 201, (2) POST complete returns 200 when balanced. |

### Key Files

**Slice 444A -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationService.java` -- Add auto-match, manual match

**Slice 444B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliation.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationRepository.java`

**Slice 444B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationService.java` -- Add reconciliation CRUD + calculation
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationController.java` -- Add matching + reconciliation endpoints

**Read for context:**
- Architecture doc Section 6.2 -- Auto-matching algorithm details with priority table
- Architecture doc Section 3.6 -- Three-way reconciliation calculation pseudocode
- ADR-233 -- Confidence threshold rationale

### Architecture Decisions

- **0.80 auto-match threshold is a constant**: Not configurable per firm. Trust reconciliation accuracy is not something firms should be able to lower. Hardcoded in service.
- **Outstanding items are timing differences**: Approved transactions not matched to any bank line are normal -- they appear on next month's statement. Tracked as outstanding_deposits/outstanding_payments in the reconciliation.
- **Completion guard is a hard block**: `completeReconciliation()` returns 400 with detailed difference breakdown if not balanced. This is the core compliance check per Section 86.

---

## Epic 445: Interest Calculation + Posting

**Goal**: Implement the daily balance method interest calculation, pro-rata LPFF rate splits for mid-period rate changes, and atomic interest posting that creates INTEREST_CREDIT and INTEREST_LPFF trust transactions.

**References**: Architecture doc Sections 2.8 (InterestRun), 2.9 (InterestAllocation), 3.7 (calculation), 3.8 (posting), 4.6 (endpoints); ADR-234 (daily balance method).

**Dependencies**: Epic 440 (trust transactions, client ledger for balance calculation).

**Scope**: Backend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **445A** | 445.1--445.7 | `InterestRun` + `InterestAllocation` entities + repos + `InterestService.createInterestRun()` + `calculateInterest()` (daily balance method, transaction-weighted computation, pro-rata rate splits, HALF_UP rounding). Integration tests (~10). Backend only. | **Done** (PR #937) |
| **445B** | 445.8--445.13 | `approveInterestRun()` + `postInterestRun()` (creates INTEREST_CREDIT + INTEREST_LPFF transactions atomically) + `InterestController` (6 endpoints). Integration tests (~7). Backend only. | **Done** (PR #938) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 445.1 | Create `InterestRun` entity | 445A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestRun.java`. Fields per architecture doc Section 2.8. |
| 445.2 | Create `InterestAllocation` entity | 445A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestAllocation.java`. Fields per architecture doc Section 2.9. |
| 445.3 | Create repositories | 445A | 445.1, 445.2 | `InterestRunRepository` + `InterestAllocationRepository`. Custom query: `findByInterestRunId(UUID)`. Unique index check for overlapping runs. |
| 445.4 | Implement `InterestService.createInterestRun()` | 445A | 445.3 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestService.java`. Create DRAFT interest run. Validate no overlapping runs for the same account/period. Look up LPFF rate effective on period_start. |
| 445.5 | Implement `calculateInterest()` -- daily balance method | 445A | 445.4 | For each client with a ledger card: get approved transactions in period, reconstruct daily balance via transaction-weighted computation (start with opening balance, apply each transaction, calculate balance-days between transactions). Handle mid-period rate change by splitting at rate boundary. `average_daily_balance = Σ(balance × days) / total_days`. `gross_interest = avg_balance × (rate/365) × days`. `lpff_share = gross × lpff_share_percent` (rounded HALF_UP). `client_share = gross - lpff_share`. Create/update InterestAllocation per client. Sum totals on InterestRun. |
| 445.6 | Write interest calculation tests | 445A | 445.5 | 7 tests: (1) single client single rate -- correct average daily balance, (2) multiple clients proportional allocation, (3) mid-period rate change -- pro-rata split, (4) client with zero balance for part of period, (5) rounding -- LPFF share rounded HALF_UP, client share = gross - lpff, (6) client with no transactions in period but opening balance, (7) empty account -- no allocations created. |
| 445.7 | Write edge case tests | 445A | 445.5 | 3 tests: (1) overlapping run prevention, (2) no LPFF rate available returns error, (3) single-day period calculates correctly. |
| 445.8 | Implement `approveInterestRun()` | 445B | 445A | Validate status DRAFT, approver != creator (self-approval prevention). Set status APPROVED, approved_by. Emit audit event. |
| 445.9 | Implement `postInterestRun()` | 445B | 445A | Validate status APPROVED. Within single `@Transactional`: for each allocation with client_share > 0, create INTEREST_CREDIT TrustTransaction (credits client ledger), set allocation.trustTransactionId. Create single INTEREST_LPFF transaction (total_lpff_share). Set run status POSTED, posted_at. Emit audit event TRUST_INTEREST_POSTED. |
| 445.10 | Create `InterestController` | 445B | 445.9 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestController.java`. 6 endpoints: `POST /api/trust-accounts/{accountId}/interest-runs` (MANAGE_TRUST), `GET .../interest-runs` (VIEW_TRUST), `GET /api/interest-runs/{runId}` (VIEW_TRUST, includes allocations), `POST /api/interest-runs/{runId}/calculate` (MANAGE_TRUST), `POST /api/interest-runs/{runId}/approve` (APPROVE_TRUST_PAYMENT), `POST /api/interest-runs/{runId}/post` (MANAGE_TRUST). |
| 445.11 | Write posting integration tests | 445B | 445.9 | 4 tests: (1) post creates INTEREST_CREDIT per client, (2) post creates single INTEREST_LPFF, (3) client ledger balances increased by client_share, (4) posted run cannot be recalculated. |
| 445.12 | Write controller tests | 445B | 445.10 | 2 tests: (1) POST interest run returns 201, (2) POST calculate returns allocations. |
| 445.13 | Write interest self-approval test | 445B | 445.8 | 1 test: creator cannot approve their own interest run. |

### Key Files

**Slice 445A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestRun.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestAllocation.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestRunRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestAllocationRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestService.java`
- `backend/src/test/java/.../interest/InterestServiceTest.java`

**Slice 445B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/interest/InterestController.java`
- `backend/src/test/java/.../interest/InterestControllerTest.java`

**Read for context:**
- Architecture doc Section 3.7 -- Daily balance calculation pseudocode
- Architecture doc Section 3.8 -- Interest posting flow
- ADR-234 -- Transaction-weighted computation rationale

### Architecture Decisions

- **Transaction-weighted computation**: The daily balance method is specified for compliance but implemented via the transaction-weighted approach (Option 3 in ADR-234). Produces identical results without requiring daily snapshots.
- **HALF_UP rounding**: LPFF share is rounded first. Client share is `gross - lpff_share` (not independently rounded) to avoid penny discrepancies.
- **Atomic posting**: All INTEREST_CREDIT and INTEREST_LPFF transactions are created in a single `@Transactional` block. If any fails, all roll back. Posted runs cannot be modified -- corrections require a reversal run.

---

## Epic 446: Trust Investments

**Goal**: Implement the trust investment lifecycle: place investment (creates PAYMENT transaction), record interest earned, withdraw investment (creates DEPOSIT transaction), and maturity detection.

**References**: Architecture doc Sections 2.10 (TrustInvestment), 3.9 (investment lifecycle), 4.7 (endpoints).

**Dependencies**: Epic 441 (payment approval for the PAYMENT transaction that funds the investment).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **446A** | 446.1--446.5 | `TrustInvestment` entity + repo + `TrustInvestmentService` (placeInvestment, recordInterestEarned, withdrawInvestment, getMaturing). Integration tests (~6). Backend only. | **Done** (PR #939) |
| **446B** | 446.6--446.9 | `TrustInvestmentController` (6 endpoints: list, get, place, recordInterest, withdraw, maturing). Integration tests (~5). Backend only. | **Done** (PR #940) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 446.1 | Create `TrustInvestment` entity | 446A | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/TrustInvestment.java`. Fields per architecture doc Section 2.10. |
| 446.2 | Create `TrustInvestmentRepository` | 446A | 446.1 | Custom queries: `findByTrustAccountIdOrderByDepositDateDesc(UUID, Pageable)`, `findByCustomerId(UUID)`, `findByStatusAndMaturityDateBetween(String, LocalDate, LocalDate)` (maturity detection). |
| 446.3 | Create `TrustInvestmentService` | 446A | 446.2 | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/TrustInvestmentService.java`. `placeInvestment(accountId, dto)` -- validate account ACTIVE, customer exists, sufficient balance. Create PAYMENT transaction via `TrustTransactionService.recordPayment()`. Create TrustInvestment with status ACTIVE, link depositTransactionId. Emit audit event. `recordInterestEarned(investmentId, amount)` -- validate ACTIVE, increment interestEarned. `withdrawInvestment(investmentId)` -- validate ACTIVE/MATURED, calculate withdrawalAmount = principal + interestEarned, create DEPOSIT transaction via `TrustTransactionService.recordDeposit()`, set status WITHDRAWN, link withdrawalTransactionId. `getMaturing(accountId, daysAhead)` -- query by maturityDate <= today + daysAhead. |
| 446.4 | Write investment lifecycle tests | 446A | 446.3 | 4 tests: (1) place investment creates PAYMENT + investment record, (2) record interest increments total, (3) withdraw creates DEPOSIT with principal + interest, (4) maturity query returns investments maturing within window. |
| 446.5 | Write investment edge case tests | 446A | 446.3 | 2 tests: (1) place investment with insufficient balance fails, (2) withdraw already-withdrawn investment fails. |
| 446.6 | Create `TrustInvestmentController` | 446B | 446A | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/TrustInvestmentController.java`. 6 endpoints: `GET /api/trust-accounts/{accountId}/investments` (VIEW_TRUST, filters: status, customer), `GET /api/trust-investments/{investmentId}` (VIEW_TRUST), `POST /api/trust-accounts/{accountId}/investments` (MANAGE_TRUST), `PUT /api/trust-investments/{investmentId}/interest` (MANAGE_TRUST), `POST /api/trust-investments/{investmentId}/withdraw` (MANAGE_TRUST), `GET /api/trust-accounts/{accountId}/investments/maturing` (VIEW_TRUST, param: daysAhead). |
| 446.7 | Define investment DTO records | 446B | 446.6 | `PlaceInvestmentRequest`, `RecordInterestRequest`, `TrustInvestmentResponse` (all fields + customerName). |
| 446.8 | Write controller integration tests | 446B | 446.6 | 3 tests: (1) POST investment returns 201, (2) PUT interest returns updated investment, (3) GET maturing returns investments. |
| 446.9 | Write controller authorization test | 446B | 446.6 | 2 tests: (1) POST withdraw returns 200 with WITHDRAWN status, (2) member without MANAGE_TRUST cannot place investment. |

### Key Files

**Slice 446A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/TrustInvestment.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/TrustInvestmentRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/TrustInvestmentService.java`
- `backend/src/test/java/.../investment/TrustInvestmentServiceTest.java`

**Slice 446B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/investment/TrustInvestmentController.java`
- `backend/src/test/java/.../investment/TrustInvestmentControllerTest.java`

**Read for context:**
- Architecture doc Section 3.9 -- Investment lifecycle flow
- Architecture doc Section 4.7 -- Investment endpoints

### Architecture Decisions

- **Investment creates PAYMENT transaction**: The `placeInvestment()` method creates a PAYMENT via `TrustTransactionService`. This means the investment is only truly activated after the payment is approved (normal approval workflow). The investment status starts ACTIVE immediately but the trust balance deduction goes through the standard approval pipeline.
- **Interest earned does not affect trust ledger**: `recordInterestEarned()` only updates the investment's `interestEarned` field. The interest stays in the investment until withdrawal, at which point the full amount (principal + interest) is deposited back to trust.

---

## Epic 447: Trust Reports (Section 35) + Event/Notification Handlers

**Goal**: Register seven trust report types in the ReportDefinition framework, implement their data providers, and complete the trust domain event and notification infrastructure.

**References**: Architecture doc Sections 4.8 (report endpoints), 8.1 (backend changes -- report package), 8.4 (testing strategy); requirements Section 7 (report types), Section 10 (audit/notification).

**Dependencies**: Epics 442, 444, 445, 446 (all data must be available for comprehensive reports).

**Scope**: Backend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **447A** | 447.1--447.5 | 7 `ReportDefinition` seed registrations + data provider implementations for each report type + Section 35 data pack (composite report). Integration tests (~7). Backend only. | **Done** (PR #941) |
| **447B** | 447.6--447.10 | `TrustDomainEvent` sealed interface + `TrustNotificationHandler` completion (6 notification types) + legal pack content extension (trust templates + automation rules). Integration tests (~5). Backend only. | **Done** (PR #942) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 447.1 | Seed 7 report definitions | 447A | -- | Add to existing report seeder or create new seed data. 7 reports: TRUST_RECEIPTS_PAYMENTS, CLIENT_TRUST_BALANCES, CLIENT_LEDGER_STATEMENT, TRUST_RECONCILIATION, INVESTMENT_REGISTER, INTEREST_ALLOCATION, SECTION_35_DATA_PACK. All with `category = 'TRUST'`. Parameters per requirements Section 7.1. |
| 447.2 | Create report data provider interface + implementations | 447A | 447.1 | New files in `report/` sub-package: `TrustReportDataProvider.java` (interface), `TrustReceiptsPaymentsProvider.java` (chronological journal), `ClientTrustBalancesProvider.java` (point-in-time balances), `ClientLedgerStatementProvider.java` (per-client history with running balance), `TrustReconciliationReportProvider.java` (three-way reconciliation), `InvestmentRegisterProvider.java` (investment list), `InterestAllocationReportProvider.java` (per-client interest). |
| 447.3 | Create Section 35 data pack provider | 447A | 447.2 | `Section35DataPackProvider.java` -- composite report combining all sub-reports for a financial year. Calls other providers and assembles multi-section output. Parameters: trust_account_id, financial_year_end. |
| 447.4 | Write report data provider tests | 447A | 447.2 | 7 tests: one per report type, verifying correct data assembly. Create test data (accounts, transactions, reconciliations, interest runs, investments) and verify each provider returns the expected structure. |
| 447.5 | Write Section 35 data pack test | 447A | 447.3 | 1 test: verify composite report includes all sections with correct data. |
| 447.6 | Create `TrustDomainEvent` sealed interface | 447B | -- | New file: `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustDomainEvent.java`. Sealed interface with record implementations: `PaymentAwaitingApproval`, `PaymentApproved`, `PaymentRejected`, `ReconciliationCompleted`, `InvestmentMaturing`, `InterestPosted`. |
| 447.7 | Complete `TrustNotificationHandler` | 447B | 447.6 | Modify: `TrustNotificationHandler.java` (created as skeleton in 441B). Add handling for all 6 event types: AWAITING_APPROVAL → notify APPROVE_TRUST_PAYMENT members, APPROVED → notify recorder, REJECTED → notify recorder, RECONCILIATION_OVERDUE → notify admin, INVESTMENT_MATURING → notify creator, APPROVAL_AGING (>48h) → notify approvers. Wire to `NotificationService`. |
| 447.8 | Register new notification types | 447B | 447.7 | Modify `NotificationService` or notification type registry: add TRUST_PAYMENT_AWAITING_APPROVAL, TRUST_PAYMENT_APPROVED, TRUST_PAYMENT_REJECTED, TRUST_RECONCILIATION_OVERDUE, TRUST_INVESTMENT_MATURING, TRUST_APPROVAL_AGING. |
| 447.9 | Extend legal pack content | 447B | -- | Add trust-specific document templates to legal-za template pack: "Client Trust Statement", "Trust Receipt", "Section 35 Cover Letter". Add automation rules: investment maturity reminder (30 days), reconciliation overdue reminder (35 days), pending approval aging (48h). |
| 447.10 | Write notification and pack tests | 447B | 447.7 | 5 tests: (1) AWAITING_APPROVAL event sends to approvers, (2) APPROVED event notifies recorder, (3) REJECTED event notifies recorder with reason, (4) trust templates present in legal-za pack, (5) automation rules present in pack. |

### Key Files

**Slice 447A -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/report/` (8 files)

**Slice 447B -- Create:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustDomainEvent.java`

**Slice 447B -- Modify:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustNotificationHandler.java`
- NotificationService/type registry -- new notification types

**Read for context:**
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/reporting/` -- ReportDefinition framework pattern
- Architecture doc Section 4.8 -- Report types and parameters

### Architecture Decisions

- **Reports use existing framework**: No new report endpoints. Trust reports are registered as `ReportDefinition` entries and executed via the Phase 19 `POST /api/reports/execute` endpoint. Data providers implement the framework's interface.
- **Section 35 data pack is a composite**: It calls individual report providers and assembles a multi-section PDF. This avoids duplicating query logic.
- **Sealed interface for events**: `TrustDomainEvent` is sealed with specific record types. This enables exhaustive pattern matching in the handler.

---

## Epic 448: Frontend -- Trust Dashboard + Transaction Entry + Approval UX

**Goal**: Replace the trust accounting stub frontend page with a real dashboard. Build the transactions page with recording dialogs and approval/rejection/reversal actions.

**References**: Requirements Section 8.1 (dashboard), 8.2 (transactions); Architecture doc Section 8.2 (frontend changes).

**Dependencies**: Epic 442 (backend transaction + ledger APIs).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **448A** | 448.1--448.6 | Trust dashboard page (replaces stub): summary cards, recent transactions table, reconciliation status, alert badges. TypeScript types, Zod schemas, server actions, nav item update. Frontend tests (~5). Frontend only. | **Done** (PR #944) |
| **448B** | 448.7--448.12 | Transactions page: transaction table with filters, 5 recording dialogs (deposit, payment, transfer, feeTransfer, refund), `ApprovalBadge` component, approve/reject/reverse actions. Frontend tests (~5). Frontend only. | **Done** (PR #945) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 448.1 | Create trust TypeScript types | 448A | -- | New file: `frontend/lib/types/trust.ts`. Types: `TrustAccount`, `LpffRate`, `TrustTransaction`, `ClientLedgerCard`, `TrustDashboardData`. Match backend response shapes from architecture doc Section 4.9. |
| 448.2 | Create trust Zod schemas | 448A | -- | New file: `frontend/lib/schemas/trust.ts`. Schemas for: `createTrustAccountSchema`, `recordDepositSchema`, `recordPaymentSchema`, `recordTransferSchema`, `recordFeeTransferSchema`, `recordRefundSchema`. Validation rules per requirements. |
| 448.3 | Create trust server actions | 448A | -- | New file: `frontend/app/(app)/org/[slug]/trust-accounting/actions.ts`. Actions: `fetchTrustAccounts()`, `fetchDashboardData()`, `fetchRecentTransactions()`, `fetchPendingApprovals()`, `fetchCashbookBalance()`. All call backend via `lib/api.ts` with Bearer JWT. |
| 448.4 | Replace stub dashboard page | 448A | 448.3 | Modify: `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`. Replace stub content with real dashboard: `TrustBalanceCard` (total balance), active clients count card, pending approvals count card, last reconciliation status card, recent transactions table (last 10), reconciliation status indicator (green/red), alerts section (maturing investments, overdue reconciliation, aging approvals). Module-gated behind `trust_accounting`. |
| 448.5 | Update nav item | 448A | -- | Modify: `frontend/lib/nav-items.ts`. Update trust accounting nav item: change capability from `FINANCIAL_VISIBILITY` to `VIEW_TRUST`. Add sub-items: Transactions, Client Ledgers, Reconciliation, Interest, Investments, Reports (matching architecture doc Section 8.11). |
| 448.6 | Write dashboard frontend tests | 448A | 448.4 | 5 tests: (1) dashboard renders summary cards, (2) recent transactions table displays data, (3) module gate hides page when module disabled, (4) pending approvals count badge renders, (5) nav items include trust sub-items. |
| 448.7 | Create transactions page | 448B | 448A | New file: `frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx`. Transaction table with columns: Date, Reference, Type (badge), Client, Matter, Amount, Status (badge), Recorded By, Approved By. Filters: date range, type, status, client, matter. Pagination. |
| 448.8 | Create recording dialogs | 448B | 448.7 | New files in `frontend/components/trust/`: `RecordDepositDialog.tsx` (client selector, matter optional, amount, reference, description, date), `RecordPaymentDialog.tsx` (same + payee details), `RecordTransferDialog.tsx` (source client, target client, amount, reference, description), `RecordFeeTransferDialog.tsx` (client, invoice selector filtered to unpaid, amount pre-filled, reference), `RecordRefundDialog.tsx` (client, amount max: trust balance, reference, description). All use Zod schema + react-hook-form. |
| 448.9 | Create transaction server actions | 448B | 448.8 | New file: `frontend/app/(app)/org/[slug]/trust-accounting/transactions/actions.ts`. Actions: `fetchTransactions()`, `recordDeposit()`, `recordPayment()`, `recordTransfer()`, `recordFeeTransfer()`, `recordRefund()`, `approveTransaction()`, `rejectTransaction()`, `reverseTransaction()`. |
| 448.10 | Create `ApprovalBadge` component | 448B | -- | New file: `frontend/components/trust/approval-badge.tsx`. Displays transaction status with colored badge. When AWAITING_APPROVAL and user has APPROVE_TRUST_PAYMENT: shows Approve/Reject buttons. Approve triggers server action. Reject opens reason dialog. |
| 448.11 | Create reversal action | 448B | -- | Reverse button (visible for APPROVED transactions) opens reason dialog. Calls `reverseTransaction()` server action. |
| 448.12 | Write transaction page tests | 448B | 448.7 | 5 tests: (1) transactions table renders, (2) deposit dialog submits correctly, (3) approval badge shows for AWAITING_APPROVAL, (4) reject dialog requires reason, (5) reverse dialog creates reversal. |

### Key Files

**Slice 448A -- Create:**
- `frontend/lib/types/trust.ts`
- `frontend/lib/schemas/trust.ts`
- `frontend/app/(app)/org/[slug]/trust-accounting/actions.ts`

**Slice 448A -- Modify:**
- `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` -- Replace stub with real dashboard
- `frontend/lib/nav-items.ts` -- Update trust nav item + sub-items

**Slice 448B -- Create:**
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/page.tsx`
- `frontend/app/(app)/org/[slug]/trust-accounting/transactions/actions.ts`
- `frontend/components/trust/RecordDepositDialog.tsx`
- `frontend/components/trust/RecordPaymentDialog.tsx`
- `frontend/components/trust/RecordTransferDialog.tsx`
- `frontend/components/trust/RecordFeeTransferDialog.tsx`
- `frontend/components/trust/RecordRefundDialog.tsx`
- `frontend/components/trust/approval-badge.tsx`

**Read for context:**
- `frontend/app/(app)/org/[slug]/legal/tariffs/page.tsx` -- Legal page pattern reference
- `frontend/lib/schemas/legal.ts` -- Zod schema pattern for legal features
- `frontend/components/ui/dialog.tsx` -- Dialog pattern reference

### Architecture Decisions

- **Dashboard at `/trust-accounting` not `/legal/trust-accounting`**: The existing stub and nav item place trust accounting directly under the org slug, not under `/legal/`. This follows the current codebase convention where trust accounting is a top-level finance module, not nested under legal routes.
- **Server Components for pages, Client Components for dialogs**: Pages fetch data server-side. Recording dialogs use `"use client"` for form interactivity. `ApprovalBadge` is client-side for button interactivity.
- **SWR for approval state**: The approval badge can use SWR to poll for approval status changes without page navigation.

---

## Epic 449: Frontend -- Client Ledger + Reconciliation Split-Pane

**Goal**: Build the client ledger list/detail pages and the bank reconciliation page with split-pane matching interface.

**References**: Requirements Section 8.3 (client ledger), 8.4 (reconciliation); Architecture doc Section 6.3 (manual matching UX flow).

**Dependencies**: Epic 442 (client ledger API), Epic 444 (reconciliation API).

**Scope**: Frontend

**Estimated Effort**: L

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **449A** | 449.1--449.5 | Client ledger list page + detail view + transaction history + running balance + statement generation. Frontend tests (~4). Frontend only. | **Done** (PR #947) |
| **449B** | 449.6--449.11 | Reconciliation page + bank statement upload + split-pane matching interface + three-way check + completion flow. Frontend tests (~5). Frontend only. | **Done** (PR #948) |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 449.1 | Create client ledger types + actions | 449A | -- | New file: `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions.ts`. Types for `ClientLedgerCard`, `LedgerStatementEntry`. Actions: `fetchClientLedgers()`, `fetchClientLedger()`, `fetchClientHistory()`, `fetchClientStatement()`. |
| 449.2 | Create client ledger list page | 449A | 449.1 | New file: `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/page.tsx`. Table columns: Client Name, Trust Balance, Total Deposits, Total Payments, Total Fee Transfers, Last Transaction Date. Sort by balance (highest first). Filter: non-zero balance toggle, client name search. |
| 449.3 | Create client detail page | 449A | 449.2 | New file: `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[customerId]/page.tsx`. Full transaction history for client. Running balance column. Same filters as transactions page but scoped to one client. "Print Statement" button generates PDF via report API. |
| 449.4 | Write client ledger tests | 449A | 449.2 | 4 tests: (1) ledger list renders client cards, (2) balance filter works, (3) detail page shows transaction history, (4) print statement triggers report generation. |
| 449.5 | Add additional client ledger types | 449A | -- | Extend `frontend/lib/types/trust.ts` with `ClientLedgerResponse`, `LedgerStatementResponse`. |
| 449.6 | Create reconciliation types + actions | 449B | -- | New file: `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/actions.ts`. Types: `BankStatement`, `BankStatementLine`, `TrustReconciliation`, `MatchResult`. Actions: `fetchReconciliations()`, `uploadBankStatement()`, `autoMatch()`, `manualMatch()`, `unmatch()`, `excludeLine()`, `createReconciliation()`, `calculateReconciliation()`, `completeReconciliation()`. |
| 449.7 | Create reconciliation list page | 449B | 449.6 | New file: `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/page.tsx`. Table: Period, Bank Balance, Cashbook Balance, Client Ledger Total, Status (Balanced/Unbalanced), Completed Date. "New Reconciliation" button. |
| 449.8 | Create `BankStatementUpload` component | 449B | -- | New file: `frontend/components/trust/BankStatementUpload.tsx`. File picker for CSV. Multipart upload via server action. Shows import result (line count, bank detected). |
| 449.9 | Create `ReconciliationSplitPane` component | 449B | 449.8 | New file: `frontend/components/trust/ReconciliationSplitPane.tsx`. `"use client"`. Left panel: bank statement lines (color-coded: green=auto-matched, blue=manually-matched, grey=excluded, yellow=unmatched). Right panel: unmatched trust transactions. Click bank line → highlight candidates (same amount). Click "Match" to link. Click "Exclude" for non-trust items. Progress bar (matched/total). Summary panel: three-way balance check. "Complete" button (disabled until balanced). |
| 449.10 | Write reconciliation tests | 449B | 449.9 | 5 tests: (1) reconciliation list renders, (2) upload triggers import, (3) split pane renders bank lines and transactions, (4) manual match links items, (5) complete button disabled when not balanced. |
| 449.11 | Create new reconciliation flow page | 449B | 449.7 | New file: `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx`. Step flow: select trust account + period → upload statement → auto-match → split-pane review → three-way check → complete. |

### Key Files

**Slice 449A -- Create:**
- `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/page.tsx`
- `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/actions.ts`
- `frontend/app/(app)/org/[slug]/trust-accounting/client-ledgers/[customerId]/page.tsx`

**Slice 449B -- Create:**
- `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/page.tsx`
- `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/actions.ts`
- `frontend/app/(app)/org/[slug]/trust-accounting/reconciliation/new/page.tsx`
- `frontend/components/trust/BankStatementUpload.tsx`
- `frontend/components/trust/ReconciliationSplitPane.tsx`

**Read for context:**
- Architecture doc Section 6.3 -- Manual matching UX flow
- `frontend/app/(app)/org/[slug]/legal/tariffs/page.tsx` -- Legal page pattern

### Architecture Decisions

- **Split-pane is a client component**: The `ReconciliationSplitPane` is `"use client"` because it requires interactive click-to-match behavior, candidate highlighting, and real-time state updates.
- **Step-based reconciliation flow**: The new reconciliation page is a multi-step wizard (upload → match → review → complete) rather than a single page. This guides the bookkeeper through the required workflow.

---

## Epic 450: Frontend -- Interest + Investments + Reports Pages

**Goal**: Build the interest calculation page with run wizard, investment register page, and trust reports page.

**References**: Requirements Sections 8.5 (interest), 8.6 (investments), 8.7 (reports).

**Dependencies**: Epic 445 (interest API), Epic 446 (investment API), Epic 447 (report API).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **450A** | 450.1--450.5 | Interest page: runs list, `InterestRunWizard` (create → calculate → approve → post), allocation table, LPFF rate management. Frontend tests (~4). Frontend only. | **Done** (PR #951) |
| **450B** | 450.6--450.10 | Investment register page + place/record/withdraw dialogs + maturity alerts. Trust reports page + generation dialogs. Frontend tests (~4). Frontend only. | Not started |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 450.1 | Create interest types + actions | 450A | -- | New file: `frontend/app/(app)/org/[slug]/trust-accounting/interest/actions.ts`. Types: `InterestRun`, `InterestAllocation`. Actions: `fetchInterestRuns()`, `createInterestRun()`, `calculateInterest()`, `approveInterestRun()`, `postInterestRun()`, `fetchLpffRates()`, `addLpffRate()`. |
| 450.2 | Create interest page | 450A | 450.1 | New file: `frontend/app/(app)/org/[slug]/trust-accounting/interest/page.tsx`. Interest runs table: Period, Total Interest, LPFF Share, Client Share, Status, Posted Date. LPFF Rate History section with "Add Rate" dialog. |
| 450.3 | Create `InterestRunWizard` component | 450A | 450.1 | New file: `frontend/components/trust/InterestRunWizard.tsx`. `"use client"`. Multi-step: (1) Select account + period, (2) "Calculate" shows per-client allocations table (Client, Avg Daily Balance, Gross Interest, LPFF Share, Client Share), (3) "Approve" step, (4) "Post to Ledger" creates transactions. Status transitions: DRAFT → APPROVED → POSTED. |
| 450.4 | Create LPFF rate dialog | 450A | -- | New file: `frontend/components/trust/LpffRateDialog.tsx`. Dialog: effective date, rate %, LPFF share %. Uses Zod schema from trust.ts. |
| 450.5 | Write interest page tests | 450A | 450.2 | 4 tests: (1) interest runs table renders, (2) wizard calculate step shows allocations, (3) LPFF rate dialog submits, (4) post action triggers correctly. |
| 450.6 | Create investment types + actions | 450B | -- | New file: `frontend/app/(app)/org/[slug]/trust-accounting/investments/actions.ts`. Actions: `fetchInvestments()`, `placeInvestment()`, `recordInterest()`, `withdrawInvestment()`, `fetchMaturing()`. |
| 450.7 | Create investment register page | 450B | 450.6 | New file: `frontend/app/(app)/org/[slug]/trust-accounting/investments/page.tsx`. Table columns: Client, Institution, Principal, Interest Rate, Deposit Date, Maturity Date, Interest Earned, Status. Maturity alerts: investments maturing within 30 days highlighted amber. |
| 450.8 | Create investment dialogs | 450B | 450.6 | New files: `frontend/components/trust/PlaceInvestmentDialog.tsx` (client selector, institution, account number, principal, interest rate, deposit date, maturity date), `frontend/components/trust/RecordInvestmentInterestDialog.tsx` (amount earned), `frontend/components/trust/WithdrawInvestmentDialog.tsx` (confirmation with principal + interest total). |
| 450.9 | Create trust reports page | 450B | -- | New file: `frontend/app/(app)/org/[slug]/trust-accounting/reports/page.tsx`. List of 7 available trust reports with "Generate" buttons. Each opens a parameter dialog (date range, client, etc.) and format selector (PDF/CSV/Excel). Calls `POST /api/reports/execute` and downloads result. |
| 450.10 | Write investment + reports tests | 450B | 450.7, 450.9 | 4 tests: (1) investment table renders, (2) place investment dialog submits, (3) maturity alert highlights, (4) reports page lists all 7 report types. |

### Key Files

**Slice 450A -- Create:**
- `frontend/app/(app)/org/[slug]/trust-accounting/interest/page.tsx`
- `frontend/app/(app)/org/[slug]/trust-accounting/interest/actions.ts`
- `frontend/components/trust/InterestRunWizard.tsx`
- `frontend/components/trust/LpffRateDialog.tsx`

**Slice 450B -- Create:**
- `frontend/app/(app)/org/[slug]/trust-accounting/investments/page.tsx`
- `frontend/app/(app)/org/[slug]/trust-accounting/investments/actions.ts`
- `frontend/app/(app)/org/[slug]/trust-accounting/reports/page.tsx`
- `frontend/components/trust/PlaceInvestmentDialog.tsx`
- `frontend/components/trust/RecordInvestmentInterestDialog.tsx`
- `frontend/components/trust/WithdrawInvestmentDialog.tsx`

**Read for context:**
- Requirements Section 8.5 -- Interest page layout
- Requirements Section 8.6 -- Investment register layout
- `frontend/components/reports/` -- Report generation pattern

### Architecture Decisions

- **Interest wizard as client component**: The multi-step wizard requires client-side state (current step, calculation results, approval status). Implemented as `"use client"` with internal state machine.
- **Reports use existing execution framework**: No new report UI infrastructure. Trust reports use the same `POST /api/reports/execute` → download pattern as all other reports.

---

## Epic 451: Frontend -- Matter/Customer Trust Tabs + Settings + Sidebar + Coexistence Tests

**Goal**: Add trust integration tabs to project (matter) and customer detail pages, build the trust settings page, finalize sidebar navigation, and run coexistence tests.

**References**: Requirements Sections 8.8 (trust settings), 8.9 (matter integration), 8.10 (customer integration), 8.11 (sidebar).

**Dependencies**: Epics 448, 449, 450 (all frontend pages must be available for integration).

**Scope**: Frontend

**Estimated Effort**: M

### Slices

| Slice | Tasks | Summary | Status |
|-------|-------|---------|--------|
| **451A** | 451.1--451.6 | Project detail "Trust" tab (module-gated), customer detail "Trust" tab (module-gated), sidebar nav sub-items, trust settings page. Frontend tests (~5). Frontend only. | Not started |
| **451B** | 451.7--451.10 | Multi-vertical coexistence tests (trust + accounting tenant isolation, module guard verification). Trust-specific smoke tests. Tests only. | Not started |

### Tasks

| ID | Task | Slice | Deps | Notes |
|----|------|-------|------|-------|
| 451.1 | Add "Trust" tab to project detail page | 451A | -- | Modify: `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` (or tab component). Add module-gated "Trust" tab (behind `trust_accounting`). Content: client's trust balance for this matter, transaction history filtered to this project, quick actions (Record Deposit, Record Payment, Record Fee Transfer pre-filled with matter context). `TrustBalanceCard` component. |
| 451.2 | Create `TrustBalanceCard` component | 451A | -- | New file: `frontend/components/trust/TrustBalanceCard.tsx`. Reusable card showing trust balance, used in dashboard, project detail, and customer detail. Props: customerId, trustAccountId, showQuickActions. |
| 451.3 | Add "Trust" tab to customer detail page | 451A | -- | Modify: customer detail page. Add module-gated "Trust" tab. Content: total trust balance across all accounts, ledger card summary, transaction history, active investments, quick actions (Record Deposit, View Full Ledger). |
| 451.4 | Create trust settings page | 451A | -- | New file: `frontend/app/(app)/org/[slug]/settings/trust-accounting/page.tsx`. Sections: trust account management (add/edit/close), bank details, approval settings (single/dual toggle, threshold input), LPFF rate management, default reminder settings. Module-gated. |
| 451.5 | Finalize sidebar navigation | 451A | -- | Verify: `frontend/lib/nav-items.ts` includes all trust sub-items with correct routing, capabilities, and module gating. Ensure trust items appear in the correct zone (under legal/finance group). Verify icons (Scale for main, appropriate icons for sub-items). |
| 451.6 | Write integration tests | 451A | 451.1, 451.3, 451.4 | 5 tests: (1) project detail "Trust" tab visible when module enabled, (2) project detail "Trust" tab hidden when module disabled, (3) customer detail "Trust" tab shows balance, (4) trust settings page renders all sections, (5) sidebar shows trust sub-items for legal-za profile. |
| 451.7 | Write backend coexistence tests | 451B | -- | 5 backend integration tests: (1) accounting tenant cannot access trust endpoints (module guard), (2) legal tenant with trust_accounting enabled can access trust endpoints, (3) trust tables exist in all tenant schemas but are empty for accounting tenants, (4) trust capability assignment does not affect accounting tenants, (5) module guard prevents cross-module access. |
| 451.8 | Write frontend coexistence tests | 451B | -- | 3 frontend tests: (1) no trust nav items for accounting-profile org, (2) trust nav items visible for legal-profile org, (3) trust dashboard returns 404 for non-trust-enabled org. |
| 451.9 | Write trust E2E smoke tests | 451B | -- | 2 E2E tests: (1) full deposit → approval → client balance update flow, (2) trust dashboard loads with summary data. |
| 451.10 | Final cleanup and verification | 451B | -- | Verify all module-gated pages return 404 for non-trust tenants. Verify sidebar items are conditionally rendered. Verify all `@RequiresCapability` annotations are correct. |

### Key Files

**Slice 451A -- Create:**
- `frontend/components/trust/TrustBalanceCard.tsx`
- `frontend/app/(app)/org/[slug]/settings/trust-accounting/page.tsx`

**Slice 451A -- Modify:**
- `frontend/app/(app)/org/[slug]/projects/[id]/page.tsx` -- Add Trust tab
- Customer detail page -- Add Trust tab
- `frontend/lib/nav-items.ts` -- Verify/finalize trust sub-items

**Slice 451B -- Create:**
- `backend/src/test/java/.../trustaccounting/TrustCoexistenceTest.java`
- `frontend/__tests__/trust-coexistence.test.tsx`

**Read for context:**
- Phase 55 Epic 408A -- Project detail tabs + sidebar pattern reference
- Phase 55 Epic 409A -- Multi-vertical coexistence test pattern

### Architecture Decisions

- **Module-gated tabs**: Trust tabs on project and customer detail pages are wrapped in `ModuleGate` component checks. They are completely invisible (not just disabled) when `trust_accounting` is not in the org's enabled modules.
- **TrustBalanceCard is reusable**: Used in dashboard (total balance), project detail (matter balance), and customer detail (client balance). Takes customerId + trustAccountId props and fetches balance via SWR.
- **Coexistence tests prove isolation**: Tests from both the accounting-tenant and legal-tenant perspectives verify that trust features are completely invisible and inaccessible to non-trust tenants.

---

### Critical Files for Implementation
- `backend/src/main/resources/db/migration/tenant/V85__create_trust_accounting_tables.sql`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/ledger/ClientLedgerCardRepository.java`
- `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/reconciliation/TrustReconciliationService.java`
- `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx`
