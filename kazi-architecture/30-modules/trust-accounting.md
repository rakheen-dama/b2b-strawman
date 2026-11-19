# Trust Accounting (Legal Vertical)

**Bounded context:** see [`10-bounded-contexts.md` § trust-accounting](../10-bounded-contexts.md). **Vertical:** legal-za only — entire module is gated by profile + module + capability.

> **Updated for Phase 92 (2026-11, slice 651D).** This page describes `main` after PRs #1786–#1812. Trust interest is recorded from the bank statement and never calculated. Every balance is derived from per-row posting effects. Money held for non-clients, and money invested, stays on the books. One equation is checked at four levels. The calculated interest run and the LPFF-rate table were removed (ADR-375, ADR-383). Design record: [`architecture/phase92-trust-books-provably-right.md`](../../architecture/phase92-trust-books-provably-right.md) and ADR-375..384.

This is the only **vertical-specific** bounded context in Kazi (all others are universal-with-overlay or conditionally-present). The whole package lives under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`. It is conditionally present at runtime — when `OrgSettings.enabledModules` lacks `"trust_accounting"` (only the `legal-za` profile adds it), every entry point self-refuses with `ModuleNotEnabledException` (HTTP 403; `verticals/VerticalModuleGuard.java:23` checks the module list, not the profile). The trust tables exist in every tenant schema all the same (tenant `V1__baseline.sql:605` onward, ADR-191); with the module off they stay empty.

---

## 1. Purpose

This module does statutory trust accounting for South African legal practitioners under section 86 of the **Legal Practice Act 28 of 2014** and the Legal Practice Council's Rule 54. It records money the firm holds in trust: for clients, for the Legal Practitioners' Fidelity Fund (the Fund), and for depositors not yet identified. That money sits in trust banking accounts (section 86(2)) and trust investment accounts (section 86(3) and 86(4)).

The module mirrors the bank statement in the cashbook and reconciles each account monthly. When a month-end reconciliation completes, it freezes the Rule 54.15 list of trust creditors. It produces the Rule 54.28 interest-and-charges schedule, the Section 35 data pack and client statements.

The Legal Practice Council's auditors read these books, so the module's load-bearing properties are **auditability** and **provable balance**. Every balance is a sum over posted rows as at a date (ADR-378). The trust equation is checked by a row constraint, a test harness, a visible integrity check and a reconciliation gate. It is never enforced by blocking a posting (ADR-377).

The single hardest design constraint is the **boundary** with `invoicing` and `integration-ports`: trust money must never appear in the firm's operating-account general ledger. Phase 71 (Xero accounting integration) introduces an export path; ADR-276 closes the boundary with a fail-closed `TrustBoundaryGuard`.

---

## 2. Entities Owned

All entities live under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/` (paths below are relative to it). Every entity is **per-tenant**. Phase 92 tables come from tenant migrations V16–V28.

| Entity | Anchor | Notes |
|---|---|---|
| `TrustAccount` | `TrustAccount.java:20` (table `trust_accounts`) | A trust bank account; it carries no client or matter. `accountType`, `investmentBasis` (`:45`, non-null exactly on `INVESTMENT`, V22), `requireDualApproval`, `paymentApprovalThreshold`, `status`, `openedDate`, `closedDate`. The API adds `section` and `lockedThrough` — the last month whose Rule 54.15 list is locked (`TrustAccountService.java:133-147`). |
| `TrustAccountType` enum | `TrustAccountType.java` | `GENERAL`, `SECTION_86` (trust banking accounts), `INVESTMENT` (a section 86(3) or 86(4) investment account, by basis). |
| `TrustAccountSection` enum | `TrustAccountSection.java` | Derived, not stored: `S86_2` (`GENERAL`, `SECTION_86`), `S86_3` (`INVESTMENT` + `FIRM_DISCRETION`), `S86_4` (`INVESTMENT` + `CLIENT_INSTRUCTION`). `SECTION_86` → section 86(2) is working assumption OL-1. |
| `InvestmentBasis` enum | `InvestmentBasis.java:13` | `FIRM_DISCRETION` (section 86(3)), `CLIENT_INSTRUCTION` (section 86(4)) — on an investment and on an `INVESTMENT` account. |
| `TrustTransaction` | `transaction/TrustTransaction.java:19` (table `trust_transactions`) | One posting. `transactionType` stays a `varchar(20)` string (ADR-238). Posting effects `ledger`, `cashbookEffect`, `ledgerEffect`, `postedAt` (`:99-109`, V16) are written once, only by `TrustPostingService`. A row targets exactly one ledger — a client and matter (`customerId`, `projectId`), an investment (`investmentId`, `:124`), a non-client creditor account (`creditorAccountId`, `:120`, with `unidentifiedDepositId`, `:133`, on the unidentified-deposits ledger) or the inter-account term (`chk_trust_txn_ledger_target`, V18). `BANK_CHARGE` rows carry `chargeRecoverable` and `chargeVatAmount` (`:140-144`, V20). Status `RECORDED, AWAITING_APPROVAL, APPROVED, REJECTED, REVERSED`; `reversalOf` / `reversedById` (`:80-83`). |
| `TrustTransactionType` (the type registry) | `transaction/TrustTransactionType.java:16` | The single source of every type's cashbook sign, ledger, ledger sign, creditor-account type, approval rule, reversal rule, origin (`FIRM` / `BANK`), client visibility, audit name and labels — **25 types** (ADR-378). Pinned to `chk_trust_txn_type` by `TrustTransactionTypeSchemaTest`; served by `GET /api/trust-transaction-types`. No SQL, report, portal or UI code restates a type's sign or label. |
| `TrustLedger` enum | `transaction/TrustLedger.java` | `CLIENT, CLIENT_INVESTMENT, NON_CLIENT, INTER_ACCOUNT` — the creditor ledger a posted row moves. |
| `ClientLedgerCard` | `ledger/ClientLedgerCard.java:23` (table `client_ledger_cards`) | The per-(trust account, client) **lock anchor**: every client posting path takes `SELECT … FOR UPDATE` on it, so a sufficiency check and the posting it guards cannot race (ADR-231 as amended by ADR-378). It stores no balance — the balance, totals and last-transaction columns were dropped in V16; balances come from `transaction/TrustLedgerQueryRepository.java:27`. |
| `TrustCreditorAccount` | `creditor/TrustCreditorAccount.java:23` (table `trust_creditor_accounts`, V18) | A non-client trust creditor ledger of one trust account (ADR-376). `TrustCreditorAccountType`: `FUND_INTEREST_AND_CHARGES` ("Legal Practitioners' Fidelity Fund — interest and charges") and `UNIDENTIFIED_DEPOSITS`; both are opened for every trust account. The `account_type` column has no CHECK on purpose. UI: "Other trust creditors". |
| `UnidentifiedDeposit` | `creditor/UnidentifiedDeposit.java:29` (table `unidentified_deposits`, V21) | One item per bank credit whose owner is unknown. Status `HELD, ALLOCATED, PAID_TO_FUND, REVERSED` describes the present only; whether it was open as at a date is derived from the item's own ledger rows (EQ-4). |
| `TrustInterestMemo` | `creditor/TrustInterestMemo.java:27` (table `trust_interest_memos`, V26) | Rule 54.28 gross interest, gross charges and VAT for one account and month that never reached the account (swept at source). Posts nothing; read by the Rule 54.28 schedule and the Section 35 pack (ADR-384). |
| `TrustInvestment` | `investment/TrustInvestment.java:21` (table `trust_investments`) | An investment placed from a funding trust banking account (`trustAccountId`) into an `INVESTMENT` trust account of the same basis (`investmentAccountId`, `:36`, V23). Section 86(4) names a client and matter; section 86(3) names neither. Status `ACTIVE, MATURED, WITHDRAWN, CANCELLED`. |
| `TrustInvestmentInterest` | `investment/TrustInvestmentInterest.java:29` (table `trust_investment_interest`, V24) | One entry per bank credit of section 86(4) interest: gross, the Fund's 5 %, net, `presentation` (`InterestPresentation`: `GROSS`, `NET`), optional statement line and client tax reference (IT3(b) figures kept, not filed — A3). Replaces the dropped `interest_earned` running total. |
| `BankStatement` | `reconciliation/BankStatement.java:18` | An imported CSV statement; parsers for FNB, ABSA, Standard Bank, Nedbank and a generic layout in `reconciliation/parser/`. |
| `BankStatementLine` | `reconciliation/BankStatementLine.java:17` | `matchStatus` `UNMATCHED, AUTO_MATCHED, MANUALLY_MATCHED, EXCLUDED, RECORDED` (V20). `classification` (`StatementLineClassification`: `FUND_INTEREST, FUND_SWEEP, BANK_CHARGE, CHARGE_REFUND, UNIDENTIFIED_DEPOSIT, INVESTMENT_INTEREST`) is set exactly when `RECORDED`; `suggestedClassification` is the parser's keyword suggestion. |
| `TrustReconciliation` | `reconciliation/TrustReconciliation.java:25` | One account, one statement, one period end. Stores the creditor side as calculated as at the period end — client ledgers, investment creditors, Fund ledger, unidentified deposits, inter-account term, creditor total, `difference` — and `integrity_flags` (V19). Status `DRAFT, COMPLETED`. |
| `TrustMonthlyList` | `books/TrustMonthlyList.java:31` (table `trust_monthly_lists`, V25) | The Rule 54.15 list of one account at one month end, frozen when that month's reconciliation completes: money side, cashbook, every creditor-side total, every creditor by name (`lines`), flags, `lockedBy` / `lockedAt`. Never updated, re-locked or deleted (ADR-382). |

**The trust equation (ADR-377).** For trust account `A` as at date `D` (`books/TrustBooksService.java:38`), EQ-2 is:

> cashbook = client ledgers + client investment creditors + Fund ledger + unidentified deposits + other non-client creditors + inter-account term

The other parts of the equation:
- **EQ-1:** at a reconciliation's period end, the adjusted bank balance equals the cashbook.
- **EQ-3:** firm-wide, the inter-account terms net to zero.
- **EQ-4:** each unidentified deposit agrees with its ledger rows.
- **EQ-5:** each investment agrees with its ledger rows.

The cashbook and every creditor term is a `SUM` of `cashbook_effect` or `ledger_effect` over rows with `posted_at IS NOT NULL`, dated on or before `D`. EQ-1's bank side is not a posting sum: it is the statement's closing balance plus outstanding deposits less outstanding payments (`TrustBooksService.bankPosition`). The equation is checked at four levels:
1. the row CHECK `chk_trust_txn_posting` (V16, tightened in V17). It validates one row at a time — a posted row moves its ledger by exactly its amount and the cashbook by the same amount or by zero — so it supports EQ-2 but cannot see a missing paired row (a `TRANSFER_OUT` moves no cash and needs its `TRANSFER_IN` for EQ-2 to balance);
2. the test harness `backend/src/test/java/io/b2mash/b2b/b2bstrawman/testutil/TrustBooksHarness.java`;
3. the integrity check `books/TrustIntegrityService.java:57`;
4. the reconciliation completion gate.

The equation and its integrity flags never block recording a transaction (founder decision 7). Postings are refused only by other rules, such as the client sufficiency check under the lock (ADR-231) and the date rules (a fee transfer or a reversal cannot be dated in the future).

---

## 3. REST Surface

All paths begin under `/api/`. Every controller method reaches a service that calls `requireModule("trust_accounting")` (the twelve gated services are listed in §6). Paths are relative to the package.

**Trust accounts** — `TrustAccountingController.java:22`, `@RequestMapping("/api/trust-accounts")`:
- `GET /api/trust-accounts` — `:31`
- `GET /api/trust-accounts/{id}` — `:37`
- `POST /api/trust-accounts` (create; an `INVESTMENT` account takes a basis) — `:43`
- `PUT /api/trust-accounts/{id}` — `:51`
- `POST /api/trust-accounts/{id}/close` — `:58`

**Transaction types** — `transaction/TrustTransactionTypeController.java`:
- `GET /api/trust-transaction-types` — `:20` (the registry, in declaration order)

**Trust transactions** — `transaction/TrustTransactionController.java`:
- `GET /api/trust-accounts/{accountId}/transactions` — `:40`
- `GET /api/trust-accounts/{accountId}/transactions/{id}` — `:55`
- `POST /api/trust-accounts/{accountId}/transactions/deposit` — `:62`
- `POST /api/trust-accounts/{accountId}/transactions/payment` — `:70`
- `POST /api/trust-accounts/{accountId}/transactions/transfer` — `:78` (`TRANSFER_OUT` + `TRANSFER_IN` between two clients)
- `POST /api/trust-accounts/{accountId}/transactions/fee-transfer` — `:86` (optional `transactionDate`)
- `POST /api/trust-accounts/{accountId}/transactions/refund` — `:94`
- `POST /api/trust-transactions/{id}/approve` — `:104`
- `POST /api/trust-transactions/{id}/reject` — `:111`
- `POST /api/trust-transactions/{id}/reverse` — `:120` (contra entry; optional `reversalDate`)
- `GET /api/trust-accounts/{accountId}/pending-approvals` — `:130`
- `GET /api/trust-accounts/{accountId}/cashbook-balance?asAt=` — `:137`

**Client ledgers** — `ledger/ClientLedgerController.java`:
- `GET /api/trust-accounts/{accountId}/client-ledgers` — `:28`
- `GET /api/trust-accounts/{accountId}/client-ledgers/{clientId}` — `:35`
- `GET /api/trust-accounts/{accountId}/client-ledgers/{clientId}/history` — `:42`
- `GET /api/trust-accounts/{accountId}/client-ledgers/{clientId}/statement` — `:60` (statement document download)
- `GET /api/trust-accounts/{accountId}/total-balance?asAt=` — `:71`

**Other trust creditors** — `creditor/`:
- `GET /api/trust-accounts/{accountId}/creditor-accounts` — `TrustCreditorLedgerController.java:26`
- `GET /api/trust-creditor-accounts/{creditorAccountId}/transactions` — `TrustCreditorLedgerController.java:33`
- `GET /api/trust-accounts/{accountId}/unidentified-deposits` — `UnidentifiedDepositController.java:28`
- `POST /api/unidentified-deposits/{depositId}/allocate` — `UnidentifiedDepositController.java:37` (to a client and matter; approval required)
- `POST /api/unidentified-deposits/{depositId}/pay-to-fund` — `UnidentifiedDepositController.java:44` (approval required)
- `GET /api/trust-accounts/{accountId}/interest-memos` — `TrustInterestMemoController.java:32`
- `PUT /api/trust-accounts/{accountId}/interest-memos/{month}` — `TrustInterestMemoController.java:39`

**Reconciliation** — `reconciliation/TrustReconciliationController.java`:
- `POST /api/trust-accounts/{accountId}/bank-statements` (upload CSV) — `:40`
- `GET /api/trust-accounts/{accountId}/bank-statements` — `:50`
- `GET /api/bank-statements/{statementId}` — `:57`
- `POST /api/bank-statements/{statementId}/auto-match` — `:65`
- `POST /api/bank-statement-lines/{lineId}/match` — `:71`
- `POST /api/bank-statement-lines/{lineId}/record` (record from statement) — `:79`
- `POST /api/bank-statement-lines/{lineId}/unmatch` — `:87`
- `POST /api/bank-statement-lines/{lineId}/exclude` — `:94`
- `POST /api/trust-accounts/{accountId}/reconciliations` — `:104`
- `GET /api/trust-accounts/{accountId}/reconciliations` — `:114`
- `GET /api/trust-reconciliations/{reconciliationId}` — `:121`
- `POST /api/trust-reconciliations/{reconciliationId}/calculate` — `:128`
- `POST /api/trust-reconciliations/{reconciliationId}/complete` — `:135` (gate G1–G4; locks the Rule 54.15 list at a month end)

**Integrity and monthly lists** — `books/`:
- `GET /api/trust-accounts/{accountId}/integrity` — `TrustIntegrityController.java:27`
- `GET /api/trust-integrity` (firm-wide) — `TrustIntegrityController.java:34`
- `GET /api/trust-accounts/{accountId}/monthly-lists` — `TrustMonthlyListController.java:26`
- `GET /api/trust-monthly-lists/{monthlyListId}` — `TrustMonthlyListController.java:32`

**Investments (section 86(3) and 86(4))** — `investment/TrustInvestmentController.java`:
- `GET /api/trust-accounts/{accountId}/investments` — `:37`
- `GET /api/trust-investments/{investmentId}` — `:47`
- `POST /api/trust-accounts/{accountId}/investments` (place) — `:54`
- `POST /api/trust-investments/{investmentId}/interest` (record a section 86(4) interest entry) — `:63`
- `GET /api/trust-investments/{investmentId}/interest` — `:72`
- `POST /api/trust-investment-interest/{interestId}/reverse` — `:79`
- `POST /api/trust-investments/{investmentId}/withdraw` — `:85`
- `GET /api/trust-accounts/{accountId}/investments/maturing` — `:92`

**Read surfaces outside the package:**
- `GET /api/matters/{matterId}/trust-balance` — `backend/.../project/ProjectController.java:236`
- `GET /api/dashboard/trust-position` and `GET /api/dashboard/trust-balance-trend` — `backend/.../dashboard/DashboardController.java:138, 150`
- MCP: `get_trust_balance` and `list_trust_transactions` — `backend/.../mcp/tool/TrustTools.java:84, 175`. The balance DTO carries `balanceMinor` (the general client ledger), `investedMinor`, `fundLedgerMinor`, `unidentifiedDepositsMinor`, `totalTrustCreditorsMinor`, `cashbookMinor` and `asAt` (`mcp/dto/McpTrustBalanceDto.java:34`). Transaction items carry `typeLabel`, `direction` and `ledger`.

**Removed in Phase 92:** `/api/trust-accounts/{id}/lpff-rates` (651E) and every `/interest-runs` endpoint (651A). Both now answer 404 (`InterestRunRemovalTest`).

**Reports** — query classes under `verticals/legal/trustaccounting/report/` (ClientLedgerStatementQuery, ClientTrustBalancesQuery, TrustPositionQuery, InvestmentRegisterQuery, TrustInterestChargesScheduleQuery — the Rule 54.28 interest-and-charges schedule, UnidentifiedDepositsAgeQuery — the unidentified deposits age analysis, Section35DataPackQuery, TrustReceiptsPaymentsQuery, TrustReconciliationReportQuery) seeded into the generic `ReportDefinition` registry by `report/TrustReportPackSeeder.java` (pack `trust-reports` v4, Phase 92 650C: nine report definitions for `legal-za` tenants; `interest-allocation` and its `InterestAllocationReportQuery` were retired in v4, replaced by the schedule). Surfaces via the standard `/api/report-definitions` endpoint and the `reporting` module's exporters, whose report entry points read at read-only `REPEATABLE_READ`.

**Portal-facing** (separate filter chain — see `customer-portal.md` for auth model):
- `GET /portal/trust/summary` (per A3 §11; `customerbackend/controller/PortalTrustController.java`)
- `GET /portal/trust/movements?limit=N`
- `GET /portal/trust/matters/{matterId}/transactions`
- `GET /portal/trust/matters/{matterId}/statement-documents`

The portal endpoints route through `customerbackend/service/PortalTrustLedgerService.java:37`. It holds `MODULE_ID = "trust_accounting"` and returns **404** (not 403) when the module is off, which hides the module's *existence* from portal callers (A6 §4 layer 9).

The read model is kept by `customerbackend/service/TrustLedgerPortalSyncService.java`. It projects each posted row with its `ledger` into `portal.portal_trust_transaction`, and keeps the general and the section 86(4) invested balance in `portal.portal_trust_balance` (global V7, 648D). Client-facing descriptions go through `TrustTransactionTypeLabels.clientDescription` (`transaction/TrustTransactionTypeLabels.java:73`), so a reversal's reason never reaches a client (649B).

---

## 4. Frontend Pages / Components

**Staff app** — under `frontend/app/(app)/org/[slug]/trust-accounting/`:

| Path | Purpose |
|---|---|
| `trust-accounting/page.tsx` | Trust overview: balance card and trend, `TrustIntegrityCard` (the integrity check, firm-wide, as at today), alerts (`MATURING_INVESTMENT, OVERDUE_RECONCILIATION, AGING_APPROVAL`) |
| `trust-accounting/transactions/page.tsx` | Transaction list; record deposit, payment, transfer, fee transfer and refund; approve, reject, reverse. Type labels and direction come from the API |
| `trust-accounting/client-ledgers/page.tsx`, `client-ledgers/[clientId]/page.tsx` | Derived client balances (general and invested) and per-client history and statement |
| `trust-accounting/other-creditors/page.tsx` | "Other trust creditors": the Fund interest-and-charges ledger with its running balance (`FundLedgerTable`), unidentified deposits with allocate and pay-to-Fund (`UnidentifiedDepositsTable`, `AllocateUnidentifiedDialog`, `PayToFundDialog`), interest-and-charges memos (`InterestMemosTable`, `InterestMemoDialog`) |
| `trust-accounting/reconciliation/page.tsx`, `reconciliation/new/page.tsx` | Reconciliation list and wizard: upload a statement, auto-match, then match, record from statement (`RecordFromStatementDialog`) or exclude each line, calculate, complete. `ReconciliationSplitPane` shows the bank and books comparisons |
| `trust-accounting/reconciliation/lists/[listId]/page.tsx` | A locked Rule 54.15 monthly list, read-only, with the entries posted after the lock |
| `trust-accounting/investments/page.tsx` | Section 86(3) and 86(4) investments: place (`PlaceInvestmentDialog`), record interest (`RecordInvestmentInterestDialog`), withdraw (`WithdrawInvestmentDialog`) |
| `trust-accounting/reports/page.tsx` | Trust report catalogue (pack `trust-reports` v4) |
| `settings/fees/_tabs/trust-accounting.tsx` | Settings → Trust accounting tab: trust accounts (including `INVESTMENT` accounts and their basis), approval settings, reminder settings |

Each page gates on the server (A6 §4 layers 5–6): it reads `enabledModules` from `getOrgSettings()`. When `trust_accounting` is off, the overview renders a "Module Not Available" placeholder (`trust-accounting/page.tsx:157`) and every sub-page calls `notFound()` (e.g. `transactions/page.tsx:101-102`; `reconciliation/layout.tsx:20-21` covers the reconciliation routes). The sub-pages also check `VIEW_TRUST` server-side and call `notFound()` without it; owners and admins pass (e.g. `transactions/page.tsx:106-109`, `reconciliation/layout.tsx:26-29`). Nav is the "Trust" group in `frontend/lib/nav-items.ts:226`: Overview, Transactions, Client ledgers, Other trust creditors, Reconciliation, Investments, Trust reports, each with `requiredModule: "trust_accounting"` and `requiredCapability: "VIEW_TRUST"`. Posting dialogs show `LockedMonthWarning` (`frontend/components/trust/locked-month-warning.tsx`) when the chosen date falls in a month whose list is locked (650D).

Domain types live in `frontend/lib/types/trust.ts`. `TrustAccount` is at `:28`. `TrustTransactionType` is a plain `string` at `:79`: there is no local list or label map, and labels arrive from the API. Other types: `TrustLedger`, `TrustAccountSection`, `IntegrityFlagCode`, `MonthlyList`, `TrustCreditorAccount`, `UnidentifiedDeposit`, `InvestmentInterest` and `InterestMemo`. Components live under `frontend/components/trust/`; they include the dialogs above, `TrustBalanceCard`, `TrustIntegrityCard` and the `Section866Advisory` callout.

**Customer portal** — under `portal/app/(authenticated)/`:

| Path | Purpose |
|---|---|
| `/trust/page.tsx` | Trust matter selector — auto-redirects if exactly 1 matter; calls `GET /portal/trust/summary` (A3 §48) |
| `/trust/[matterId]/page.tsx` | Per-matter detail: balance card (general and section 86(4) invested balance), transaction list, statement document downloads (A3 §49) |

Portal nav (`portal/lib/nav-items.ts:37`) declares trust with `modules: ["trust_accounting"]`; the `profiles: ["legal-za"]` key A6 describes was dropped when Kazi became legal-only (617A, #1639). The page does a client-side `ctx.enabledModules` check and `router.replace("/home")` if missing (A3 §159, A6 §4 layer 8). Statement downloads are further hardened: `isSafeDownloadUrl()` validates `https:` only (A3 §269).

API clients: the staff app calls the backend from each route's server actions (`trust-accounting/**/actions.ts`); the portal uses `portal/lib/api/trust.ts`.

---

## 5. Domain Events

Trust events are **not** part of the central sealed `DomainEvent` hierarchy (`backend/.../event/DomainEvent.java`).

**Published** (via `ApplicationEventPublisher`):
- `event/TrustTransactionRecordedEvent.java:25` (`recorded(...)`) fires when a row is first persisted. Publishers: `TrustTransactionService`, `TrustInvestmentService` and `UnidentifiedDepositService`.
- `event/TrustTransactionApprovalEvent.java:17` (`awaitingApproval` / `approved` / `rejected`) fires on the approval lifecycle. Publishers: `TrustTransactionService` (all three), and `TrustInvestmentService` and `UnidentifiedDepositService` (`awaitingApproval`, for a placement and for an allocation or payment to the Fund; `creditor/UnidentifiedDepositService.java:674`).

**Subscribers** (all `@TransactionalEventListener(phase = AFTER_COMMIT)`):
- `event/TrustNotificationHandler.java:50` creates the `TRUST_PAYMENT_*` notifications.
- `customerbackend/service/TrustLedgerPortalSyncService.java:119, 152` keeps the portal read model.

**Declared but never published.** The sealed interface `event/TrustDomainEvent.java:19` has these subtypes:
- `PaymentAwaitingApproval` (`:31`)
- `PaymentApproved` (`:69`)
- `PaymentRejected` (`:110`)
- `ReconciliationCompleted` (`:154`)
- `InvestmentMaturing` (`:180`)

No main class publishes any of them, so their listeners are dormant: `TrustNotificationHandler.java:73, 90` and `TrustLedgerPortalSyncService.java:172`. This is recorded as noted-not-fixed in Phase 92 §16.14.4. The Rule 54.15 list lock is a direct call inside reconciliation completion and needs no event. `TrustDomainEvent.InterestPosted`, its two listeners and notification type `TRUST_INTEREST_POSTED` were removed with the interest run (651A).

**Why AFTER_COMMIT, specifically here.** Trust postings are the legal record: a notification telling an approver "please approve TXN-123" must never be sent for a transaction that subsequently rolled back. AFTER_COMMIT is the same pattern used by `notifications` and the `customer-portal` read model (A6 §6), but the regulatory stakes are higher.

**Why a separate hierarchy?** Trust events are vertical-specific. Putting them in the central permits-list of `DomainEvent` would make the central bus trip over `verticals/legal/...` types. Keeping them separate means the universal `AutomationEventListener` does not subscribe to trust events.

---

## 6. Cross-Cutting Touchpoints — The Nine-Layer Defence

A6 §4 documents trust accounting as the canonical **nine-layer** verticalisation example. Quoting A6, the layers are:

1. **Profile registry** — `legal-za.json` is the only profile whose `enabledModules` array contains `"trust_accounting"` (A6 §237).
2. **Backend service gates** — every trust service self-checks via `verticalModuleGuard.requireModule("trust_accounting")` in each method a controller reaches (A6 §238). By design a few public methods carry no guard: helpers joined to a guarded caller's transaction (`creditor/TrustCreditorLedgerService.java:130` `openDefaultAccounts`, also called by the demo seeder; `investment/TrustInvestmentService.java:945` `markReversedByUnmatch`), and `TrustAccountService.java:162` `matterBalance`, which answers zero instead of 403 when the module is off. Twelve service classes hold `MODULE_ID = "trust_accounting"` (Phase 92 added the `books/`, `creditor/` and statement-line services; 651A removed `InterestService`):
   - `TrustAccountService.java:35`
   - `transaction/TrustTransactionService.java:58`
   - `reconciliation/TrustReconciliationService.java:55`
   - `reconciliation/StatementLinePostingService.java:63`
   - `ledger/ClientLedgerService.java:45`
   - `investment/TrustInvestmentService.java:115`
   - `creditor/TrustCreditorLedgerService.java:34`
   - `creditor/UnidentifiedDepositService.java:70`
   - `creditor/TrustInterestMemoService.java:46`
   - `books/TrustBooksService.java:40`
   - `books/TrustIntegrityService.java:59`
   - `books/TrustMonthlyListService.java:76`

   `transaction/TrustPostingService.java:70`, the only writer of trust transactions (ArchUnit `TrustPostingRulesTest`), holds no gate of its own; only gated services call it.
3. **Backend invoice export hard guard** — `TrustBoundaryGuard` blocks any trust-related invoice from being pushed to Xero (A6 §239; ADR-276; Phase 71 §11.6).
4. **Frontend nav gate** — `frontend/lib/nav-items.ts` declares the trust nav group with `requiredModule: "trust_accounting"` (A6 §240).
5. **Frontend page server gate** — each trust page reads `enabledModules` from `getOrgSettings()`; the overview returns a placeholder if the module is off (`frontend/app/(app)/org/[slug]/trust-accounting/page.tsx:157`) and every sub-page calls `notFound()` (see §4). There is no `<ModuleGate>` or `isModuleEnabledServer` on these pages (A6 §241 described them; the code no longer uses them here).
6. **Frontend capability gate** — `VIEW_TRUST / MANAGE_TRUST / APPROVE_TRUST_PAYMENT` capabilities (`backend/.../orgrole/Capability.java:19-23`); the sub-pages check `VIEW_TRUST` server-side (owners and admins pass) and the nav items carry `requiredCapability: "VIEW_TRUST"`; there is no `<RequiresCapability>` wrapper (A6 §242). Owners and admins hold all three capabilities by default; other roles need an explicit grant.
7. **Portal nav gate** — `portal/lib/nav-items.ts:37` declares trust nav with `modules: ["trust_accounting"]` (A6 §243; the `profiles: ["legal-za"]` key was dropped in 617A, #1639, when Kazi became legal-only).
8. **Portal page redirect** — `portal/app/(authenticated)/trust/page.tsx` checks `ctx.enabledModules` and `router.replace("/home")` if off (A6 §244).
9. **Portal data endpoints** — `customerbackend/service/PortalTrustLedgerService.java:37` checks `MODULE_ID = "trust_accounting"` and returns **404** (not 403) — module existence hidden from the portal (A6 §245).

A6 §247 verdict, quoted: *"Nine layers. Most are belt-and-braces UX defenses; the load-bearing ones are #2 (backend service gate) and #3 (the export guard). The gateway, notably, has zero awareness of trust-accounting — it transparently proxies whatever the backend chooses to expose (A3 §12)."*

### The export hard guard (ADR-276)

The third defence — the `TrustBoundaryGuard` (`backend/.../integration/accounting/sync/TrustBoundaryGuard.java`) — is the regulatory core. Quoting Phase 71 §11.6 and ADR-276:

- **Three-condition refusal** (Phase 71 lines 793–795): (1) `Invoice.customFields["is_trust_invoice"] == true`, (2) any line's `disbursement_id` links to a `LegalDisbursement` with non-null `trust_account_id`, (3) the customer has a non-zero general trust balance. Since Phase 92 that balance is derived from posting effects (`TrustLedgerQueryRepository.generalBalance`, `TrustBoundaryGuard.java:120-126`). **Any one** triggers refusal.
- **Fail-closed on error** (Phase 71 line 799): *"If any trust-related entity lookup fails (database error, missing data): the guard refuses the push. Trust boundary violations are worse than missed syncs."*
- **No bypass** (ADR-276:51): *"Hard-coded `TrustBoundaryGuard` with audit-only refusal. No bypass, no override, no configuration. Fails closed."*
- **Audit on every refusal** (ADR-276:43): sync entry `state=BLOCKED_TRUST_BOUNDARY`, audit event `integration.xero.push_blocked_trust`.
- **Skipped if module never provisioned** (Phase 71 line 800): non-legal tenants without trust tables incur no guard cost.

The guard runs **inside** `AccountingSyncService`, not on the public `/api/invoices` controller — i.e. trust invoices can be created in Kazi (legal vertical's whole point), they just cannot leave (A6 §311).

### Capabilities

`Capability.java:7` is the capability enum. Three values are exclusive to this module: `VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT` (A6 §112, glossary). `APPROVE_TRUST_PAYMENT` is the second leg of dual approval. None of the three is in `Capability.OWNER_ONLY` (`Capability.java:67-71`), so owners and admins hold all three by default (`OrgRoleService.java:68-74`); any other role needs an explicit grant. Phase 92 added no capability: the integrity check and monthly lists read under `VIEW_TRUST`, and record-from-statement, allocations and placements post under `MANAGE_TRUST` with approval under `APPROVE_TRUST_PAYMENT`.

### Audit posture

Every mutating service emits an `AuditEvent` in the same transaction as the state change (universal pattern, A6 §3). For trust accounting this matters more: the audit event is the legal record. Combined with the JPA `@Immutable` + Postgres trigger `prevent_audit_update` / `prevent_audit_delete` belt-and-braces immutability, an LPC auditor reading the audit log gets a tamper-evident trail. Audit rows for the removed interest run and LPFF rates (`interest_run`, `lpff_rate`) are kept and still render (ADR-383: audit history is never rewritten).

---

## 7. Vertical Specifics

This module is the worked example of a **vertical-specific** context (cf. `60-verticals/legal-za.md`). It exists only because the Legal Practice Act and the LPC Rules require it; non-legal verticals neither need nor see it. Cross-link:

- [`60-verticals/legal-za.md`](../60-verticals/legal-za.md) — Section 86 / Fund context, FICA, conflict-check, court-calendar, prescription tracking.
- [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md) — pack inventory for legal-za. `TrustReportPackSeeder` installs the Java-defined pack `trust-reports` v4 (nine report definitions — `report/TrustReportPackSeeder.java:49-50, 72-81`); the `legal-za` template pack is v16 (statement of account and client trust statement, 648F).
- [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) — full nine-layer mechanism walkthrough.

Section 86 of the Legal Practice Act describes three kinds of trust money, and each maps to an account type:

| Money | Kazi account | Who the interest belongs to |
|---|---|---|
| Section 86(2): trust money in a trust banking account | `GENERAL`, `SECTION_86` | The Fund, wholly (s86(5)(a)) |
| Section 86(3): surplus trust money the firm invests on its own initiative | `INVESTMENT` with basis `FIRM_DISCRETION` | The Fund, wholly (s86(5)(a)) |
| Section 86(4): money invested on a client's instruction | `INVESTMENT` with basis `CLIENT_INSTRUCTION` | The client, less the Fund's 5 % (s86(5)(b)) |

Kazi keeps no Fund or bank interest-rate table and calculates no interest (ADR-375). An investment placement records the rate it was placed at (`investment/TrustInvestment.java:56`, `interest_rate`) for the investment register; nothing calculates from it.

- **Fund interest, sweeps and bank charges.** These are recorded from statement lines on the account's Fund interest-and-charges ledger, as `FUND_INTEREST`, `FUND_SWEEP`, `BANK_CHARGE` and `CHARGE_REFUND` (ADR-380, ADR-381). Where the bank sweeps at source, the firm records a memo for Rule 54.28 instead (ADR-384).
- **Section 86(4) interest.** Each bank credit is recorded per investment as gross, the Fund's 5 % and net.
  - The 5 % is the constant `TrustAccountingConstants.STATUTORY_LPFF_SHARE_PERCENT` (`TrustAccountingConstants.java:26`), with a R0,01 tolerance for the bank's rounding (OL-5, ADR-379).
  - Under a `GROSS` presentation the 5 % is posted as `FUND_INV_SHARE` on the investment account's Fund ledger. Under a `NET` presentation it never reached the account and lives only on the entry.
- **Investments stay on the books.** A section 86(4) placement moves the client's money from the general ledger to a client investment creditor balance on the investment account, so the client's total is unchanged. A section 86(3) placement moves cash between the two accounts through the inter-account term (ADR-379).
- **Section 86 advisory copy.** The frontend `Section866Advisory` component carries it.

---

## 8. Active ADRs

The canonical trust ADRs are **ADR-375..384** (Phase 92), resting on **ADR-230..233**, plus **ADR-247..249** (legal-vertical structural decisions) and **ADR-276** (the export guard). From `90-adr-index.md` § Legal vertical:

| ADR | Title | Bearing on this module |
|---|---|---|
| ADR-230 | double-entry-trust-ledger | Immutable postings; amended by ADR-378 — cached balances replaced by balances derived from per-row effects. |
| ADR-231 | negative-balance-prevention | The per-client row lock stays (`ClientLedgerCard` as lock anchor); amended by ADR-378 — the stored-balance CHECK is replaced by a derived sufficiency check under the lock. |
| ADR-232 | configurable-dual-authorization | `TrustAccount.requireDualApproval` + `paymentApprovalThreshold`; second approver via `APPROVE_TRUST_PAYMENT`. Applied by ADR-380 to every approval-required registry type. |
| ADR-233 | bank-reconciliation-matching | Auto-match algorithm + manual override; extended by ADR-380 (record from statement, `RECORDED` lines). |
| ADR-247 | legal-disbursement-sibling-entity | `LegalDisbursement` is a sibling of generic `Expense`, not a subtype — keeps trust commingling out of the universal expenses table. |
| ADR-248 | matter-closure-distinct-state-with-gates | Matter cannot close with a non-zero client trust balance — gating couples this module to `projects`. |
| ADR-249 | retention-clock-starts-on-closure | Trust ledgers retained per regulatory clock anchored at matter closure. |
| ADR-276 | trust-accounting-hard-guard-export | The fail-closed export guard. **The single most load-bearing ADR for this module's external boundary.** |
| ADR-279 | sibling-payment-source-port | Disbursement payment source (`OFFICE_ACCOUNT` vs `TRUST_ACCOUNT`) modelled as a sibling port. |
| ADR-356 | pack-content-reconciliation | How `trust-reports` v4 and `legal-za` template v16 reach existing tenants. |
| ADR-375 | statement-driven-trust-interest | Interest is recorded from the bank statement, never calculated; the Fund's 5 % is a constant. Supersedes ADR-234, 235, 362, 366. |
| ADR-376 | non-client-trust-creditor-ledger | `trust_creditor_accounts` (Fund interest-and-charges, unidentified deposits); itemised unidentified deposits. |
| ADR-377 | trust-equation-and-where-it-is-enforced | EQ-1..EQ-5 as at a date; row CHECK, harness, integrity check, completion gate — never a block on posting. |
| ADR-378 | ledger-balances-derived-from-posting-effects | `TrustTransactionType` registry; effects on the row written by `TrustPostingService`; contra reversals. |
| ADR-379 | investment-accounting-on-the-books | `INVESTMENT` accounts with a basis; paired placement and withdrawal rows; per-entry section 86(4) interest. |
| ADR-380 | statement-line-classification-and-posting | Record from statement; bank-originated types need no approval; unmatch reverses. |
| ADR-381 | bank-charges-and-fund-ledger-debit | Charges debit the Fund ledger with recoverable flag and VAT; a Fund ledger in debit is allowed and flagged. |
| ADR-382 | monthly-trust-creditor-list-locking | Rule 54.15 list frozen on month-end completion; back-dated postings recorded and flagged against it. |
| ADR-383 | retired-interest-run-data-is-dropped | Interest-run tables, `lpff_rates` and the two interest types dropped, not archived. |
| ADR-384 | memo-records-for-at-source-sweeps | `trust_interest_memos` for Rule 54.28 figures that never reach the account. |

Also relevant: **ADR-238** (entity-type varchar vs enum) — why `TrustTransaction.transactionType` stays a varchar column with a Java registry rather than a Postgres enum.

---

## 9. Key Flows

The canonical flow in [`50-flows/payment-receipt-to-trust-allocation.md`](../50-flows/payment-receipt-to-trust-allocation.md) covers the money path:

1. A client pays into a trust banking account and the firm records the deposit.
2. The firm imports the statement. Each line is matched, recorded from the statement (Fund interest, sweeps, charges, unidentified deposits, section 86(4) interest) or excluded.
3. Outflows (payments, fee transfers, refunds, investment placements) are approved under the maker-checker rule.
4. The month's reconciliation completes through the integrity gate and freezes the Rule 54.15 list.

It cross-cuts `customer-lifecycle`, `projects` (matter), `audit` (every step), `notifications` (approval alerts, AFTER_COMMIT) and `customer-portal` (read model).

Companion flows that touch this module:
- `50-flows/matter-to-cash.md` — fee-transfer leg from trust to operating once a fee note is paid from trust funds.
- `50-flows/portal-magic-link-to-task-completion.md` — portal trust-ledger view from the customer side.

---

## 10. Open Questions / Known Fragility

### 10.1 Profile-switch fragility (orphaned trust tables)

A6 §4 (`bounded-contexts.md:467`) documents that `VerticalProfileReconciliationSeeder.reconcile(...)` only **adds** modules. Switching a tenant from `legal-za` back to `consulting-generic` does not uninstall legal packs nor disable trust services — orphaned `trust_accounts`, `trust_transactions`, `client_ledger_cards`, etc. remain in the schema. They are unreachable from the UI (module-gated at all nine layers), but they are still queryable from any future code path that does not self-check.

**Why this matters here specifically**: trust data is regulatory. A profile-switch that orphans trust tables makes the trust audit trail unreachable through the canonical paths. The data is intact but no longer surfaceable through the canonical paths.

**Question**: should profile switch from `legal-za` be blocked outright (one-way trapdoor) when any `TrustAccount` exists, or should we ship a guided uninstall flow that exports the trust audit before disabling the module? Tracked in A6's "documented gap" wording; no ADR yet.

### 10.2 Disbursement-payment-source pattern (ADR-247 / ADR-279)

The `expenses` module's open question (cross-link `30-modules/expenses.md`) cites ADR-247 here. `LegalDisbursement` is a **sibling** of `Expense`, not a subtype (ADR-247). The payment-source enum `DisbursementPaymentSource` (`verticals/legal/disbursement/DisbursementPaymentSource.java:14`) carries `OFFICE_ACCOUNT, TRUST_ACCOUNT`. When `TRUST_ACCOUNT`, `DisbursementService.validateTrustLink` (`verticals/legal/disbursement/DisbursementService.java:662`) requires a link to an existing `APPROVED` trust `PAYMENT` on the same matter — the disbursement module never writes a trust transaction (only `TrustPostingService` does, enforced by `TrustPostingRulesTest`). There is no `DISBURSEMENT_PAYMENT` type.

ADR-279 (sibling-payment-source-port) frames this as a **port**: trust is one implementation, office-account a sibling. The pattern keeps trust-aware code out of the universal expense path. The open question is the inverse: any future payment-source (e.g. credit card facility, third-party financing) must implement the same sibling pattern, and there is no compile-time mechanism stopping a contributor from adding a third enum value without the sibling treatment. A `sealed interface PaymentSource permits ...` would close this; current state is enum + dispatch.

### 10.3 Reconciliation cadence and dual-approval enforcement

`TrustAccount.requireDualApproval` is per-account and per-tenant-configurable (ADR-232). `paymentApprovalThreshold` (`TrustAccount.java`) is a BigDecimal threshold above which dual approval kicks in regardless of the flag. Two unresolved items:

- **Cadence enforcement**: the `OVERDUE_RECONCILIATION` `TrustAlert` exists, but nothing *blocks* transactions when a reconciliation is overdue. The LPC Rules expect monthly reconciliation. Should an overdue reconciliation gate new payments? Currently advisory only.
- **Threshold + flag interaction**: if a tenant sets `requireDualApproval=false` but `paymentApprovalThreshold=10000`, what is the contract for a 9,999.99 payment? Code path: `TrustTransactionService` evaluates threshold-or-flag, but the precedence is not explicitly documented. Recommend an ADR clarification.

### 10.4 Statement-document signing and audit trail

Client trust statements are downloadable PDFs (`GET /api/trust-accounts/{accountId}/client-ledgers/{clientId}/statement` and the portal endpoint). They are generated on the fly from `ClientLedgerStatementQuery`. The open question: should these be **signed** (cryptographic hash + timestamp) at generation, persisted as `Document` instances, and cross-referenced from the audit log? Today the document is rendered-on-demand and not a `GeneratedDocument` row, which means there is no permanent record of *what* the client received and *when* they received it — only that a download endpoint was hit (audit event). For a regulatory artefact, this is thin.

The portal layer hardens download URLs to `https:` only (A3 §269) but provides no integrity proof.

### 10.5 After Phase 92 — open legal points and known follow-ups

**Open legal points.** The design rests on assumptions A1–A6 and open legal points OL-1..OL-9 (`architecture/phase92-trust-books-provably-right.md` §16.14.1–§16.14.2). Each has a working assumption, chosen so that a different answer changes configuration, copy or a query rather than the data model. Examples: a `SECTION_86` account reports under section 86(2) (OL-1); a client ledger in debit is flagged, not blocked (OL-7). A practising attorney or trust auditor still has to confirm each.

**Follow-up PRs ruled after the phase** (`tasks/phase92-decisions.md`, FU-1..FU-15). The main ones:
- Approval-time cashbook cover for firm-initiated outflows, plus a `CASHBOOK_IN_DEBIT` flag (FU-1, FU-9).
- Trust-figure reports gated by `VIEW_TRUST` (FU-2).
- One consistent read for statements (FU-3).
- A back-dated deposit reversal checked as at its own date (FU-5).
- Refusing duplicate same-end-date statements and future-dated lines (FU-6).
- Postings dated before an account's opened date (FU-14).

**Noted, not fixed** (§16.14.4):
- No `TrustDomainEvent` is published (see §5).
- Nothing sets an investment `MATURED`.
- A `CLOSED` trust account refuses postings, including recording a late statement line from that account.

---

**See also:** [`60-verticals/legal-za.md`](../60-verticals/legal-za.md) (Section 86 / Fund context), [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) (nine-layer mechanism), [`30-modules/invoicing.md`](./invoicing.md) (trust-flagged invoice + Xero hard guard), [`30-modules/expenses.md`](./expenses.md) (legal-disbursement sibling), [`50-flows/payment-receipt-to-trust-allocation.md`](../50-flows/payment-receipt-to-trust-allocation.md), [`50-flows/matter-to-cash.md`](../50-flows/matter-to-cash.md), [`adr/ADR-276`](../../adr/ADR-276-trust-accounting-hard-guard-export.md), [`adr/ADR-375`](../../adr/ADR-375-statement-driven-trust-interest.md), [`adr/ADR-376`](../../adr/ADR-376-non-client-trust-creditor-ledger.md), [`adr/ADR-377`](../../adr/ADR-377-trust-equation-and-where-it-is-enforced.md), [`adr/ADR-378`](../../adr/ADR-378-ledger-balances-derived-from-posting-effects.md), [`adr/ADR-379`](../../adr/ADR-379-investment-accounting-on-the-books.md), [`adr/ADR-380`](../../adr/ADR-380-statement-line-classification-and-posting.md), [`adr/ADR-381`](../../adr/ADR-381-bank-charges-and-fund-ledger-debit.md), [`adr/ADR-382`](../../adr/ADR-382-monthly-trust-creditor-list-locking.md), [`adr/ADR-383`](../../adr/ADR-383-retired-interest-run-data-is-dropped.md), [`adr/ADR-384`](../../adr/ADR-384-memo-records-for-at-source-sweeps.md), [`architecture/phase92-trust-books-provably-right.md`](../../architecture/phase92-trust-books-provably-right.md), `architecture/phase60-trust-accounting.md`, `architecture/phase71-xero-accounting-integration.md` §11.6.
