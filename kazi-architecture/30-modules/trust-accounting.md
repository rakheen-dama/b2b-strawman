# Trust Accounting (Legal Vertical)

**Bounded context:** see [`10-bounded-contexts.md` § trust-accounting](../10-bounded-contexts.md). **Vertical:** legal-za only — entire module is gated by profile + module + capability.

This is the only **vertical-specific** bounded context in Kazi (all others are universal-with-overlay or conditionally-present). The whole package lives under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`. It is conditionally present at runtime — when `OrgSettings.verticalProfile != "legal-za"` or `enabledModules` lacks `"trust_accounting"`, every entry point self-refuses with `ModuleNotEnabledException` (HTTP 403). On a non-legal tenant the schema may not even contain the trust tables.

---

## 1. Purpose

Statutory third-party-trust accounting for South African legal practitioners under the **Legal Practice Act**. The system records client money the firm holds in fiduciary capacity (separate from operating funds), reconciles it against the bank, allocates LPFF interest to the Legal Practitioners' Fidelity Fund, tracks Section 86 investment trust accounts, and produces statement-of-trust documents for client and regulator. Because the regulator (Legal Practice Council) audits these books, the module's load-bearing property is **irreversibility + auditability**, not flexibility.

The single hardest design constraint is the **boundary** with `invoicing` and `integration-ports`: trust money must never appear in the firm's operating-account general ledger. Phase 71 (Xero accounting integration) introduces an export path; ADR-276 closes the boundary with a fail-closed `TrustBoundaryGuard`.

---

## 2. Entities Owned

All entities live under `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/`. Every entity is **per-tenant** (sits in the `tenant_<hex>` schema, not `public`).

| Entity | Anchor | Notes |
|---|---|---|
| `TrustAccount` | `verticals/legal/trustaccounting/TrustAccount.java:19` (table `trust_accounts`) | Bank account holding trust money. Fields: `accountName, bankName, branchCode, accountNumber, accountType, isPrimary, requireDualApproval, paymentApprovalThreshold, status, openedDate, closedDate` (`TrustAccount.java:26-67`). |
| `TrustAccountType` enum | `verticals/legal/trustaccounting/TrustAccountType.java` | `GENERAL` (LSSA-compliant pooled trust), `INVESTMENT` (interest-bearing pooled), `SECTION_86` (statutory mandate-specific investment per Legal Practice Act s.86). |
| `TrustAccountStatus` enum | `verticals/legal/trustaccounting/TrustAccountStatus.java` | `ACTIVE`, `CLOSED`. |
| `TrustTransaction` | `verticals/legal/trustaccounting/transaction/TrustTransaction.java:17` (table `trust_transactions`) | Receipt/payment/transfer ledger entry. Stores `transactionType` (varchar20, see types below), `amount`, `customerId/projectId/counterpartyCustomerId/invoiceId`, `transactionDate`, `status`, dual-approval audit fields (`approvedBy/approvedAt/secondApprovedBy/secondApprovedAt/rejectedBy/rejectedAt/rejectionReason`), `reversalOf/reversedById` (`TrustTransaction.java:26-80`). |
| `TrustTransactionType` (frontend, varchar in DB) | `frontend/lib/types/trust.ts:39` | 10 variants per glossary: `DEPOSIT, PAYMENT, TRANSFER, FEE_TRANSFER, REFUND, REVERSAL, INTEREST_ALLOCATION, LPFF_PAYOUT, INVESTMENT_PLACEMENT, DISBURSEMENT_PAYMENT`. The varchar-in-DB pattern follows ADR-238 (entity-type varchar vs enum). |
| `BankStatement` | `verticals/legal/trustaccounting/reconciliation/BankStatement.java` | Imported file (CSV/OFX) attached to a `TrustAccount` for reconciliation. |
| `BankStatementLine` | `verticals/legal/trustaccounting/reconciliation/BankStatementLine.java` | Per-row match status `MATCHED, UNMATCHED, IGNORED` (per `glossary.md:55`). |
| `TrustReconciliation` | `verticals/legal/trustaccounting/reconciliation/TrustReconciliation.java` | Reconciliation run for a trust account against a bank statement. Status `DRAFT, COMPLETED` (`reconciliation/ReconciliationStatus.java`). |
| `ClientLedgerCard` | `verticals/legal/trustaccounting/ledger/ClientLedgerCard.java` | Per-(trustAccount, customer) running balance. The "client ledger" — statutory legal-vertical concept. |
| `LpffRate` | `verticals/legal/trustaccounting/lpff/LpffRate.java` | Effective-dated LPFF interest rate per trust account (lookup via `LpffRateRepository.findByTrustAccountIdOrderByEffectiveFromDesc` and `findFirstByTrustAccountIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc` — `TrustAccountService.java:313, 324`). |
| `InterestRun` | `verticals/legal/trustaccounting/interest/InterestRun.java` | Periodic LPFF interest calculation across client ledgers. Lifecycle `DRAFT → APPROVED → POSTED` (`InterestRunStatus` per glossary). |
| `InterestAllocation` | `verticals/legal/trustaccounting/interest/InterestAllocation.java` | Per-client allocation row inside an interest run. |
| `TrustInvestment` | `verticals/legal/trustaccounting/investment/TrustInvestment.java` | Section 86 tracked investment of trust funds; status `ACTIVE, MATURED, WITHDRAWN`. |
| `InvestmentBasis` enum | `verticals/legal/trustaccounting/InvestmentBasis.java` | `FIRM_DISCRETION, CLIENT_INSTRUCTION` (per glossary:148). |

The **double-entry ledger** invariant (ADR-230) — every `TrustTransaction` mutates exactly one `TrustAccount` and one `ClientLedgerCard` such that account balance = Σ(client ledger balances) — is enforced inside the service layer, not at the DB level.

---

## 3. REST Surface

All paths begin under `/api/`. Every controller method ultimately reaches a `Service.requireModule("trust_accounting")` gate (the four service classes that hold `MODULE_ID = "trust_accounting"` are listed in §6).

**Trust accounts** — `TrustAccountingController.java:24`, `@RequestMapping("/api/trust-accounts")`:
- `GET /api/trust-accounts` (list active accounts) — `:33`
- `GET /api/trust-accounts/{id}` — `:39`
- `POST /api/trust-accounts` (create) — `:45`
- `PUT /api/trust-accounts/{id}` — `:53`
- `POST /api/trust-accounts/{id}/close` — `:60`
- `GET /api/trust-accounts/{id}/lpff-rates` — `:66`
- `POST /api/trust-accounts/{id}/lpff-rates` — `:72`

**Trust transactions** — `transaction/TrustTransactionController.java`:
- `GET /api/trust-accounts/{accountId}/transactions` — `:38`
- `GET /api/trust-accounts/{accountId}/transactions/{id}` — `:45`
- `POST /api/trust-accounts/{accountId}/transactions/deposit` — `:52`
- `POST /api/trust-accounts/{accountId}/transactions/payment` — `:60`
- `POST /api/trust-accounts/{accountId}/transactions/transfer` — `:68`
- `POST /api/trust-accounts/{accountId}/transactions/fee-transfer` — `:76` (matter-fee → operating)
- `POST /api/trust-accounts/{accountId}/transactions/refund` — `:84`
- `POST /api/trust-transactions/{id}/approve` — `:94` (dual-approval second leg)
- `POST /api/trust-transactions/{id}/reject` — `:101`
- `POST /api/trust-transactions/{id}/reverse` — `:110`
- `GET /api/trust-accounts/{accountId}/pending-approvals` — `:120`
- `GET /api/trust-accounts/{accountId}/cashbook-balance` — `:127`

**Client ledgers** — `ledger/ClientLedgerController.java`:
- `GET /api/trust-accounts/{accountId}/client-ledgers` — `:27`
- `GET /api/trust-accounts/{accountId}/client-ledgers/{customerId}` — `:34`
- `GET /api/trust-accounts/{accountId}/client-ledgers/{customerId}/history` — `:41`
- `GET /api/trust-accounts/{accountId}/client-ledgers/{customerId}/statement` — `:49` (statement document download)
- `GET /api/trust-accounts/{accountId}/total-balance` — `:60`

**Reconciliation** — `reconciliation/TrustReconciliationController.java`:
- `POST /api/trust-accounts/{accountId}/bank-statements` (upload CSV/OFX) — `:34`
- `GET /api/trust-accounts/{accountId}/bank-statements` — `:44`
- `GET /api/bank-statements/{statementId}` — `:51`
- `POST /api/bank-statements/{statementId}/auto-match` — `:59`
- `POST /api/bank-statement-lines/{lineId}/match` — `:65`
- `POST /api/bank-statement-lines/{lineId}/unmatch` — `:73`
- `POST /api/bank-statement-lines/{lineId}/exclude` — `:80`
- `POST /api/trust-accounts/{accountId}/reconciliations` — `:90`
- `GET /api/trust-accounts/{accountId}/reconciliations` — `:100`
- `GET /api/trust-reconciliations/{reconciliationId}` — `:107`
- `POST /api/trust-reconciliations/{reconciliationId}/calculate` — `:114`
- `POST /api/trust-reconciliations/{reconciliationId}/complete` — `:121`

**Interest runs** — `interest/InterestController.java`:
- `POST /api/trust-accounts/{accountId}/interest-runs` — `:29`
- `GET /api/trust-accounts/{accountId}/interest-runs` — `:39`
- `GET /api/interest-runs/{runId}` — `:45`
- `POST /api/interest-runs/{runId}/calculate` — `:51`
- `POST /api/interest-runs/{runId}/approve` — `:57`
- `POST /api/interest-runs/{runId}/post` — `:63`

**Investments (Section 86)** — `investment/TrustInvestmentController.java`:
- `GET /api/trust-accounts/{accountId}/investments` — `:37`
- `GET /api/trust-investments/{investmentId}` — `:47`
- `POST /api/trust-accounts/{accountId}/investments` — `:53`
- `PUT /api/trust-investments/{investmentId}/interest` — `:61`
- `POST /api/trust-investments/{investmentId}/withdraw` — `:69`
- `GET /api/trust-accounts/{accountId}/investments/maturing` — `:76`

**Reports** — query classes under `verticals/legal/trustaccounting/report/` (ClientLedgerStatementQuery, ClientTrustBalancesQuery, InterestAllocationReportQuery, InvestmentRegisterQuery, Section35DataPackQuery, TrustReceiptsPaymentsQuery, TrustReconciliationReportQuery) seeded into the generic `ReportDefinition` registry by `report/TrustReportPackSeeder.java:15` ("Seeds 7 trust accounting report definitions for tenants with the trust_accounting module enabled"). Surfaces via the standard `/api/report-definitions` endpoint and the `reporting` module's exporters.

**Portal-facing** (separate filter chain — see `customer-portal.md` for auth model):
- `GET /portal/trust/summary` (per A3 §11; `customerbackend/controller/PortalTrustController.java`)
- `GET /portal/trust/movements?limit=N`
- `GET /portal/trust/matters/{matterId}/transactions`
- `GET /portal/trust/matters/{matterId}/statement-documents`

The portal endpoints route through `customerbackend/service/PortalTrustLedgerService.java:31` which holds `MODULE_ID = "trust_accounting"` and returns **404** (not 403) when the module is off — the module's *existence* is hidden from portal callers (A6 §4 layer 9).

---

## 4. Frontend Pages / Components

**Staff app** — under `frontend/app/(app)/org/[slug]/trust-accounting/` (per A2 §166–179):

| Path | Purpose |
|---|---|
| `trust-accounting/page.tsx` | Overview + summary stats, alerts (`MATURING_INVESTMENT, OVERDUE_RECONCILIATION, AGING_APPROVAL` per glossary `TrustAlert`) |
| `trust-accounting/transactions/page.tsx` | Trust transaction ledger with filters |
| `trust-accounting/client-ledgers/page.tsx` | Per-client trust ledger view |
| `trust-accounting/reconciliation/page.tsx` | Trust reconciliation workflow (upload statement → auto-match → manual override → complete) |
| `trust-accounting/interest/page.tsx` | LPFF interest runs (calculate → approve → post) |
| `trust-accounting/investments/page.tsx` | Section 86 investment tracking |
| `trust-accounting/reports/page.tsx` | Trust-specific report catalogue |
| `(app)/org/[slug]/settings/trust-accounting/page.tsx` | Trust account setup, LPFF rates, Section 86 (admin-only, A2 §251–252) |

Every page is wrapped in (a) `<ModuleGate slug="trust_accounting">`, (b) `<RequiresCapability cap="VIEW_TRUST">`, and (c) page-level server gate via `isModuleEnabledServer("trust_accounting")` (A6 §4 layers 4–6). Nav declared in `frontend/lib/nav-items.ts` with `requiredModule: "trust_accounting"`.

Domain types live in `frontend/lib/types/trust.ts` (`TrustAccount` at `:7` with `accountType` + `requireDualApproval`; `TrustTransactionType` at `:39` with 10 variants; plus `TrustAlert, TrustInvestment, TrustInvestmentStatus, BankStatementResponse, BankStatementLineResponse, CashbookBalance, ClientLedgerCard, LedgerStatementResponse, InterestRunStatus`). Components under `frontend/components/trust/` (per A2 §436) include trust account widgets and a `Section866Advisory` callout.

**Customer portal** — under `portal/app/(authenticated)/`:

| Path | Purpose |
|---|---|
| `/trust/page.tsx` | Trust matter selector — auto-redirects if exactly 1 matter; calls `GET /portal/trust/summary` (A3 §48) |
| `/trust/[matterId]/page.tsx` | Per-matter detail: balance card, transaction list, statement document downloads (A3 §49) |

Portal nav (`portal/lib/nav-items.ts:43`) declares trust with **both** `profiles: ["legal-za"]` AND `modules: ["trust_accounting"]`. The page does a client-side `ctx.enabledModules` check and `router.replace("/home")` if missing (A3 §159, A6 §4 layer 8). Statement downloads are further hardened: `isSafeDownloadUrl()` validates `https:` only (A3 §269).

API clients: `frontend/lib/api/trust.ts` (staff) and `portal/lib/api/trust.ts` (portal).

---

## 5. Domain Events

Trust events are **not** part of the central sealed `DomainEvent` hierarchy (`backend/.../event/DomainEvent.java`). They are a **separate** sealed interface `TrustDomainEvent` and a sibling standalone record `TrustTransactionApprovalEvent` — published via `ApplicationEventPublisher` and consumed by `TrustNotificationHandler` using `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`:

`→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/event/TrustDomainEvent.java:19` — `public sealed interface TrustDomainEvent` carrying `tenantId, orgId, occurredAt`.

Permitted subtypes:
- `TrustDomainEvent.PaymentAwaitingApproval` (`:31`) — payment/fee-transfer/refund entered AWAITING_APPROVAL
- `TrustDomainEvent.PaymentApproved` (`:69`)
- `TrustDomainEvent.PaymentRejected` (`:110`)
- `TrustDomainEvent.ReconciliationCompleted` (`:154`)
- `TrustDomainEvent.InvestmentMaturing` (`:180`)
- `TrustDomainEvent.InterestPosted` (`:221`)

Sibling records:
- `event/TrustTransactionRecordedEvent.java:24` — fired when a transaction is first persisted (independent of approval lifecycle)
- `event/TrustTransactionApprovalEvent.java:13` — `awaitingApproval/approved/rejected` factories

Subscriber:
- `event/TrustNotificationHandler.java:50, 73, 90, 107` — four `@TransactionalEventListener(phase = AFTER_COMMIT)` methods. Comment on `:18`: *"Runs AFTER_COMMIT in a new transaction to ensure notifications are only created for committed [transactions]."*

**Why AFTER_COMMIT, specifically here.** Trust transactions are statutorily irreversible — a notification telling an approver "please approve TXN-123" must never be sent for a transaction that subsequently rolled back. AFTER_COMMIT is the same pattern used by `notifications` and `customer-portal` read-model (A6 §6) but the regulatory stakes are higher.

**Why a separate sealed hierarchy?** Trust events are vertical-specific. Putting them in the central permits-list of `DomainEvent` would make the central bus trip over `verticals/legal/...` types — a non-legal vertical's automation listener would still see them in its switch. Keeping them in a sibling sealed type means the universal `AutomationEventListener` does not subscribe to trust events; only the trust-local handler does.

---

## 6. Cross-Cutting Touchpoints — The Nine-Layer Defence

A6 §4 documents trust accounting as the canonical **nine-layer** verticalisation example. Quoting A6 verbatim, the layers are:

1. **Profile registry** — `legal-za.json` is the only profile whose `enabledModules` array contains `"trust_accounting"` (A6 §237).
2. **Backend service gates** — every trust service self-checks via `verticalModuleGuard.requireModule("trust_accounting")` (A6 §238). Confirmed at four service classes:
   - `verticals/legal/trustaccounting/TrustAccountService.java:26`
   - `verticals/legal/trustaccounting/transaction/TrustTransactionService.java:45`
   - `verticals/legal/trustaccounting/reconciliation/TrustReconciliationService.java:43`
   - `verticals/legal/trustaccounting/ledger/ClientLedgerService.java:28`
   - plus `interest/InterestService.java:41` and `investment/TrustInvestmentService.java:36` (six total in the module).
3. **Backend invoice export hard guard** — `TrustBoundaryGuard` blocks any trust-related invoice from being pushed to Xero (A6 §239; ADR-276; Phase 71 §11.6).
4. **Frontend nav gate** — `frontend/lib/nav-items.ts` declares the trust-accounting nav group with `requiredModule: "trust_accounting"` (A6 §240).
5. **Frontend page server gate** — `frontend/app/(app)/org/[slug]/trust-accounting/page.tsx` calls `isModuleEnabledServer` and returns a placeholder if disabled (A6 §241).
6. **Frontend capability gate** — `VIEW_TRUST / MANAGE_TRUST / APPROVE_TRUST_PAYMENT` capabilities (`backend/.../orgrole/Capability.java:20-24`); pages wrapped in `<RequiresCapability cap="VIEW_TRUST">` (A6 §242).
7. **Portal nav gate** — `portal/lib/nav-items.ts:43` declares trust nav with both `profiles: ["legal-za"]` AND `modules: ["trust_accounting"]` (A6 §243).
8. **Portal page redirect** — `portal/app/(authenticated)/trust/page.tsx` checks `ctx.enabledModules` and `router.replace("/home")` if off (A6 §244).
9. **Portal data endpoints** — `customerbackend/service/PortalTrustLedgerService.java:31` checks `MODULE_ID = "trust_accounting"` and returns **404** (not 403) — module existence hidden from the portal (A6 §245).

A6 §247 verdict, quoted: *"Nine layers. Most are belt-and-braces UX defenses; the load-bearing ones are #2 (backend service gate) and #3 (the export guard). The gateway, notably, has zero awareness of trust-accounting — it transparently proxies whatever the backend chooses to expose (A3 §12)."*

### The export hard guard (ADR-276)

The third defense — the `TrustBoundaryGuard` — is the regulatory core. Quoting Phase 71 §11.6 and ADR-276:

- **Three-condition refusal** (Phase 71 lines 793–795): (1) `Invoice.customFields["is_trust_invoice"] == true`, (2) any line's `disbursement_id` links to a `LegalDisbursement` with non-null `trust_account_id`, (3) customer has any non-zero balance on a `ClientLedgerCard`. **Any one** triggers refusal.
- **Fail-closed on error** (Phase 71 line 799): *"If any trust-related entity lookup fails (database error, missing data): the guard refuses the push. Trust boundary violations are worse than missed syncs."*
- **No bypass** (ADR-276:51): *"Hard-coded `TrustBoundaryGuard` with audit-only refusal. No bypass, no override, no configuration. Fails closed."*
- **Audit on every refusal** (ADR-276:43): sync entry `state=BLOCKED_TRUST_BOUNDARY`, audit event `integration.xero.push_blocked_trust`.
- **Skipped if module never provisioned** (Phase 71 line 800): non-legal tenants without trust tables incur no guard cost.

The guard runs **inside** `AccountingSyncService`, not on the public `/api/invoices` controller — i.e. trust invoices can be created in Kazi (legal vertical's whole point), they just cannot leave (A6 §311).

### Capabilities

`Capability.java:7` is a 19-value enum. Three values are exclusive to this module: `VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT` (A6 §112, glossary:67). `APPROVE_TRUST_PAYMENT` is the second leg of dual approval and is `OWNER_ONLY`-gated (admin does not inherit by default).

### Audit posture

Every mutating service emits an `AuditEvent` in the same transaction as the state change (universal pattern, A6 §3). For trust accounting this matters more: the audit event is the legal record. Combined with the JPA `@Immutable` + Postgres trigger `prevent_audit_update` / `prevent_audit_delete` belt-and-braces immutability, an LPC auditor reading the audit log gets a tamper-evident trail.

---

## 7. Vertical Specifics

This module is the worked example of a **vertical-specific** context (cf. `60-verticals/legal-za.md`). It exists only because the South African Legal Practice Act mandates it; non-legal verticals neither need nor see it. Cross-link:

- [`60-verticals/legal-za.md`](../60-verticals/legal-za.md) — LSSA / Section 86 / LPFF context, FICA, conflict-check, court-calendar, prescription tracking.
- [`60-verticals/seeds-and-packs.md`](../60-verticals/seeds-and-packs.md) — pack inventory for legal-za, including `TrustReportPackSeeder` (7 report definitions — `TrustReportPackSeeder.java:15`).
- [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) — full nine-layer mechanism walkthrough.

The `TrustAccountType.SECTION_86` value is the legal anchor: s.86 of the Legal Practice Act allows attorneys to invest trust money on a client mandate; the investment's interest accrues to the client (not to LPFF as with general trust accounts) and the firm has fiduciary tracking obligations (mature/withdraw lifecycle in `TrustInvestment`, advisory copy in the frontend `Section866Advisory` component).

LPFF (Legal Practitioners' Fidelity Fund) is the SA statutory body that receives interest from `GENERAL` trust accounts (glossary:164). Rate management lives on the trust account itself (effective-dated `LpffRate` rows). Interest runs (`InterestRun` → `InterestAllocation`) calculate per-client-day balances using the daily-balance method (ADR-234) and split between client and LPFF using the configurable share (ADR-235 — statutory-vs-configurable LPFF share).

---

## 8. Active ADRs

Canonical trust ADRs are in the **ADR-230..235** cluster, plus **ADR-247..249** (legal-vertical structural decisions) and **ADR-276** (the export guard). From `90-adr-index.md:368-389`:

| ADR | Title | Bearing on this module |
|---|---|---|
| ADR-230 | double-entry-trust-ledger | Every `TrustTransaction` mutates account + client ledger as a pair (invariant: account balance = Σ client balances). |
| ADR-231 | negative-balance-prevention | Per-client-ledger balance cannot go negative; guard inside `TrustTransactionService`. |
| ADR-232 | configurable-dual-authorization | `TrustAccount.requireDualApproval` flag + `paymentApprovalThreshold`; second approver via `APPROVE_TRUST_PAYMENT` capability; second-leg fields on `TrustTransaction`. |
| ADR-233 | bank-reconciliation-matching | Auto-match algorithm + manual override semantics for `BankStatementLine.matchStatus`. |
| ADR-234 | interest-daily-balance-method | LPFF interest computed on daily balances, not period averages. |
| ADR-235 | statutory-vs-configurable-lpff-share | Default LPFF share is statutory; tenants may override the client share within statutory bounds. |
| ADR-247 | legal-disbursement-sibling-entity | `LegalDisbursement` is a sibling of generic `Expense`, not a subtype — keeps trust commingling out of the universal expenses table. |
| ADR-248 | matter-closure-distinct-state-with-gates | Matter cannot close with a non-zero client trust balance — gating couples this module to `projects`. |
| ADR-249 | retention-clock-starts-on-closure | Trust ledgers retained per regulatory clock anchored at matter closure. |
| ADR-276 | trust-accounting-hard-guard-export | The fail-closed export guard. **The single most load-bearing ADR for this module's external boundary.** |
| ADR-279 | sibling-payment-source-port | Disbursement payment source (`OFFICE_ACCOUNT` vs `TRUST_ACCOUNT`) modelled as a sibling port — keeps the legal-only `TRUST_ACCOUNT` source from leaking into commercial expense flows. |

Also relevant: **ADR-238** (entity-type varchar vs enum) — explains why `TrustTransaction.transactionType` is a varchar column rather than a Postgres enum, despite the strong taxonomy.

---

## 9. Key Flows

The canonical flow in `50-flows/payment-receipt-to-trust-allocation.md` covers the inbound path: client pays into a general trust account → bank statement line imported → matched to a `TrustTransaction(DEPOSIT)` → allocated to a `ClientLedgerCard` → reconciliation closes. Cross-cuts `customer-lifecycle` (customer must be ACTIVE), `projects` (matter must be open), `audit` (every step), and `notifications` (recipient gets an in-app alert via `TrustDomainEvent` AFTER_COMMIT).

Companion flows that touch this module:
- `50-flows/matter-to-cash.md` — fee-transfer leg from trust to operating once an invoice is paid from trust funds.
- `50-flows/portal-magic-link-to-task-completion.md` — portal trust-ledger view from the customer side.

---

## 10. Open Questions / Known Fragility

### 10.1 Profile-switch fragility (orphaned trust tables)

A6 §4 (`bounded-contexts.md:467`) documents that `VerticalProfileReconciliationSeeder.reconcile(...)` only **adds** modules. Switching a tenant from `legal-za` back to `consulting-generic` does not uninstall legal packs nor disable trust services — orphaned `trust_accounts`, `trust_transactions`, `client_ledger_cards`, etc. remain in the schema. They are unreachable from the UI (module-gated at all nine layers), but they are still queryable from any future code path that does not self-check.

**Why this matters here specifically**: trust data is regulatory. A profile-switch that orphans trust tables loses LPFF audit-trail accessibility. The data is intact but no longer surfaceable through the canonical paths.

**Question**: should profile switch from `legal-za` be blocked outright (one-way trapdoor) when any `TrustAccount` exists, or should we ship a guided uninstall flow that exports the trust audit before disabling the module? Tracked in A6's "documented gap" wording; no ADR yet.

### 10.2 Disbursement-payment-source pattern (ADR-247 / ADR-279)

The `expenses` module's open question (cross-link `30-modules/expenses.md`) cites ADR-247 here. `LegalDisbursement` is a **sibling** of `Expense`, not a subtype (ADR-247). The payment-source enum `DisbursementPaymentSource` (`verticals/legal/disbursement/DisbursementPaymentSource.java:14`) carries `OFFICE_ACCOUNT, TRUST_ACCOUNT`. When `TRUST_ACCOUNT`, `DisbursementService` (line 634-635) creates a linked `TrustTransaction` of type `DISBURSEMENT_PAYMENT` — that is the single touchpoint where a non-trust-package class writes to trust ledgers.

ADR-279 (sibling-payment-source-port) frames this as a **port**: trust is one implementation, office-account a sibling. The pattern keeps trust-aware code out of the universal expense path. The open question is the inverse: any future payment-source (e.g. credit card facility, third-party financing) must implement the same sibling pattern, and there is no compile-time mechanism stopping a contributor from adding a third enum value without the sibling treatment. A `sealed interface PaymentSource permits ...` would close this; current state is enum + dispatch.

### 10.3 Reconciliation cadence and dual-approval enforcement

`TrustAccount.requireDualApproval` is per-account and per-tenant-configurable (ADR-232). `paymentApprovalThreshold` (`TrustAccount.java:48`) is a BigDecimal threshold above which dual approval kicks in regardless of the flag. Two unresolved items:

- **Cadence enforcement**: the `OVERDUE_RECONCILIATION` `TrustAlert` exists (glossary:276) but there is no automated *block* on transactions when reconciliation is overdue. The Legal Practice Act expects monthly reconciliation. Should an overdue reconciliation gate new payments? Currently advisory only.
- **Threshold + flag interaction**: if a tenant sets `requireDualApproval=false` but `paymentApprovalThreshold=10000`, what is the contract for a 9,999.99 payment? Code path: `TrustTransactionService` evaluates threshold-or-flag, but the precedence is not explicitly documented. Recommend an ADR clarification.

### 10.4 Statement-document signing and audit trail

Client trust statements are downloadable PDFs (`GET /api/trust-accounts/{accountId}/client-ledgers/{customerId}/statement` and the portal endpoint). They are generated on the fly from `ClientLedgerStatementQuery`. The open question: should these be **signed** (cryptographic hash + timestamp) at generation, persisted as `Document` instances, and cross-referenced from the audit log? Today the document is rendered-on-demand and not a `GeneratedDocument` row, which means there is no permanent record of *what* the client received and *when* they received it — only that a download endpoint was hit (audit event). For a regulatory artefact, this is thin.

The portal layer hardens download URLs to `https:` only (A3 §269) but provides no integrity proof.

---

**See also:** [`60-verticals/legal-za.md`](../60-verticals/legal-za.md) (LSSA/Section 86/LPFF context), [`20-cross-cutting/multi-vertical.md`](../20-cross-cutting/multi-vertical.md) (nine-layer mechanism), [`30-modules/invoicing.md`](./invoicing.md) (trust-flagged invoice + Xero hard guard), [`30-modules/expenses.md`](./expenses.md) (legal-disbursement sibling), [`50-flows/payment-receipt-to-trust-allocation.md`](../50-flows/payment-receipt-to-trust-allocation.md), [`50-flows/matter-to-cash.md`](../50-flows/matter-to-cash.md), [`adr/ADR-276`](../../adr/ADR-276-trust-accounting-hard-guard-export.md), `architecture/phase60-trust-accounting.md`, `architecture/phase71-xero-accounting-integration.md` §11.6.
