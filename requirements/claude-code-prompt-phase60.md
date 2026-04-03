# Phase 60 — Trust Accounting (Legal Practice Act Section 86)

## System Context

DocTeams (Kazi) is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) with 59 phases of functionality. The legal vertical is operational: Phase 49 built the vertical architecture (module guards, profile system, tenant-gated modules), Phase 55 built legal foundations (court calendar, conflict check, LSSA tariff), and a `legal-za` profile provisions legal tenants with field packs, template packs, clause packs, compliance packs, and tariff seed data.

**The existing infrastructure that this phase builds on**:

- **Vertical module system** (Phase 49): `VerticalModuleGuard`, `ModuleRegistry`, `OrgSettings.enabled_modules` (JSONB). Legal profile defines modules: `trust_accounting`, `court_calendar`, `conflict_check`. The `trust_accounting` module is currently a stub — this phase replaces it with a full implementation.
- **Legal stubs** (Phase 49): Stub controller in `verticals.legal.trustaccounting` package. Stub frontend page at `/legal/trust-accounting`. These return placeholder responses.
- **Invoice system** (Phase 10, extended Phases 25-26): `Invoice`, `InvoiceLine`, `PaymentEvent` entities. Invoice lifecycle: DRAFT → APPROVED → SENT → PAID/OVERDUE/VOID. Invoices can be paid via portal (Stripe/PayFast), direct EFT, or (new in this phase) trust transfer.
- **RBAC** (Phase 41/46): Capability-based authorization. Legal capabilities: `VIEW_LEGAL`, `MANAGE_LEGAL`. This phase adds trust-specific capabilities.
- **Rate card system** (Phase 8): `BillingRate`, `CostRate` with 3-level hierarchy. LSSA tariff rates (Phase 55) extend this for legal.
- **Customer entity**: `Customer` with lifecycle, linked projects (matters), trust is tracked per customer.
- **Audit infrastructure** (Phase 6): `AuditEvent` entity with JSONB details. All trust transactions must be audit-logged.
- **Notification infrastructure** (Phase 6.5): Notification entity, channels, templates. Trust events generate notifications.
- **Reporting framework** (Phase 19): `ReportDefinition`, execution framework, rendering pipeline (PDF/CSV/Excel). Trust reports use this framework.
- **S3 storage** (Phase 9/21): `StorageService` port for file uploads. Bank statement files stored in S3.
- **BYOAK integration ports** (Phase 21): Adapter pattern for external services. Future accounting software sync would use this — not in scope for this phase.

**The problem**: The `trust_accounting` module is a stub. Without trust accounting, no SA law firm can use Kazi for practice management — trust money handling is a hard legal requirement under the Legal Practice Act, Section 86. Every matter that involves client money (litigation, conveyancing, collections, estate administration) requires proper trust accounting. This is the single feature that determines whether the legal vertical is viable.

**The fix**: Build a full double-entry trust ledger system with client ledger cards, bank statement import and reconciliation, configurable authorization workflows, interest calculation and LPFF allocation, investment tracking, and Section 35 compliance reporting.

## Objective

1. **Trust Account Configuration** — Replace the `trust_accounting` stub with a real module. Support multiple trust bank accounts per firm (general trust + investment accounts). Store bank details, LPFF rate history, and authorization settings.

2. **Double-Entry Trust Ledger** — Every movement of trust money is a `TrustTransaction` with immutable audit trail. Transaction types: DEPOSIT, PAYMENT, TRANSFER (between client accounts), FEE_TRANSFER (trust → office, linked to invoice), REFUND, INTEREST_CREDIT, INTEREST_LPFF. Transactions cannot be edited or deleted — corrections via reversal transactions only.

3. **Client Ledger Cards** — Per-client trust balance derived from transactions. Hard invariant: **client trust balance can never go negative** (illegal under the Act). Atomic balance check on every debit transaction. Running balance maintained for query performance.

4. **Bank Statement Import & Reconciliation** — CSV and OFX file upload. Auto-matching by reference/amount/date. Manual matching via split-pane UI (bank lines left, unmatched transactions right). Monthly reconciliation workflow: DRAFT → COMPLETED. Three-way reconciliation check: bank statement balance = cashbook balance = Σ client ledger balances.

5. **Configurable Authorization** — Org-level setting: single or dual approval for trust payments. Payment transactions carry `approved_by` and optional `second_approved_by`. Pending payments in AWAITING_APPROVAL status until authorized. New RBAC capabilities: `VIEW_TRUST`, `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT`.

6. **Interest Calculation & LPFF Allocation** — Daily balance method for interest calculation per client. LPFF rate history table (rate + effective_from). Periodic interest run: calculate per-client interest, allocate LPFF portion, credit remainder to client ledger. Generate interest allocation report.

7. **Trust Investments** — Track client money placed on interest-bearing deposit. Investment register: principal, institution, account number, deposit date, maturity date, interest rate. Investment lifecycle: ACTIVE → MATURED → WITHDRAWN. Interest earned on investments tracked separately.

8. **Section 35 Compliance Reports** — Trust receipts & payments journal, client trust balance report, individual client ledger card, trust reconciliation statement, investment register, interest allocation report, Section 35 certificate data pack. All reports available via the existing reporting framework (PDF/CSV/Excel).

9. **Frontend** — Trust dashboard (total trust balance, recent transactions, reconciliation status, alerts), transaction entry forms, client ledger view, bank reconciliation split-pane interface, investment register page, interest calculation page, trust settings, trust reports page. All module-gated behind `trust_accounting`.

## Constraints & Assumptions

- **This phase replaces the `trust_accounting` stub.** The stub controller and stub frontend page are deleted and replaced with real implementations.
- **Double-entry semantics are mandatory.** Every transaction affects two sides: the trust cashbook and a client ledger card. The system must maintain referential integrity between them.
- **Immutable transactions.** Trust transactions cannot be edited or soft-deleted. Corrections are reversal transactions with a `reversal_of` FK pointing to the original. This is a legal requirement, not a design preference.
- **Negative balance prevention is a hard block.** Any operation that would reduce a client's trust balance below zero MUST be rejected with a clear error. This includes payments, transfers, fee transfers, and refunds. The check must be atomic (database-level constraint or SELECT FOR UPDATE) to prevent race conditions.
- **Trust accounting is separate from office accounting.** The trust ledger is not part of the firm's P&L. Fee transfers move money from trust to office — at that point the invoice system takes over. The boundary is clean: trust records the outflow, invoice records the payment.
- **Bank statement import supports CSV (mandatory) and OFX (stretch).** SA banks export CSV reliably. OFX support is nice-to-have but not blocking. The import parser should be pluggable (strategy pattern) for future formats.
- **Flyway migrations are non-conditional.** Trust-specific tables are created in every tenant schema. Module guard controls access, not table presence (existing pattern from Phase 55).
- **Interest calculation uses daily balance method.** For each client, calculate the average daily balance for the period, apply the applicable rate, and allocate between client credit and LPFF.
- **LPFF rate is published annually.** Model as a rate history table (effective_from + rate) on the trust account. The firm's bookkeeper updates this when the Fund publishes new rates.
- **No external integrations.** No bank API, no LPFF submission API, no accounting software sync. Bank reconciliation is via file upload. LPFF reporting is via generated PDF. Accounting sync is a future BYOAK integration.
- **Multi-currency is NOT in scope.** Trust accounts are in ZAR. Multi-currency trust accounting (e.g., foreign litigation) is a future enhancement.

---

## Section 1 — Trust Account & Configuration

### 1.1 Data Model

**TrustAccount entity** (tenant-scoped, new table `trust_accounts`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `account_name` | `VARCHAR(200)` | Display name, e.g., "General Trust Account" |
| `bank_name` | `VARCHAR(200)` | E.g., "First National Bank" |
| `branch_code` | `VARCHAR(20)` | Bank branch code |
| `account_number` | `VARCHAR(30)` | Trust bank account number |
| `account_type` | `VARCHAR(20)` | Enum: `GENERAL`, `INVESTMENT` |
| `is_primary` | `BOOLEAN` | Default true. Only one GENERAL account can be primary. |
| `require_dual_approval` | `BOOLEAN` | Default false. When true, payments require two approvers. |
| `payment_approval_threshold` | `DECIMAL(15,2)` | Optional. Dual approval only required above this amount. NULL = all payments. |
| `status` | `VARCHAR(20)` | Enum: `ACTIVE`, `CLOSED` |
| `opened_date` | `DATE` | When the account was opened |
| `closed_date` | `DATE` | Nullable. When the account was closed. |
| `notes` | `TEXT` | |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**Unique constraint**: `(account_type, is_primary)` partial unique where `is_primary = true AND account_type = 'GENERAL'` — only one primary general trust account per tenant.

**LpffRate entity** (tenant-scoped, new table `lpff_rates`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `trust_account_id` | `UUID FK` | Which trust account this rate applies to |
| `effective_from` | `DATE` | When this rate takes effect |
| `rate_percent` | `DECIMAL(5,4)` | Annual interest rate, e.g., 0.0750 for 7.5% |
| `lpff_share_percent` | `DECIMAL(5,4)` | Portion of interest payable to LPFF, e.g., 0.75 for 75% |
| `notes` | `VARCHAR(500)` | E.g., "LPFF Circular 2025/1" |
| `created_at` | `TIMESTAMP` | |

**Unique constraint**: `(trust_account_id, effective_from)` — one rate per account per effective date.

### 1.2 Trust Account Service

```
TrustAccountService
  + listTrustAccounts() → List<TrustAccount>
  + getTrustAccount(id) → TrustAccount
  + createTrustAccount(dto) → TrustAccount
  + updateTrustAccount(id, dto) → TrustAccount
  + closeTrustAccount(id) → TrustAccount  // Only if zero balance across all client ledgers
  + getPrimaryAccount() → TrustAccount
  + addLpffRate(accountId, dto) → LpffRate
  + listLpffRates(accountId) → List<LpffRate>
  + getCurrentLpffRate(accountId, asOfDate) → LpffRate  // Most recent rate effective on or before date
```

**Module gating**: Every method starts with `moduleGuard.requireModule("trust_accounting")`.

**Closing guard**: A trust account can only be closed if the sum of all client ledger card balances for that account is exactly zero. Any non-zero balance means there is client money that must be dealt with first.

### 1.3 Endpoints

```
GET    /api/trust-accounts                        — list trust accounts
GET    /api/trust-accounts/{id}                   — single trust account
POST   /api/trust-accounts                        — create trust account
PUT    /api/trust-accounts/{id}                   — update trust account (name, bank details, approval settings)
POST   /api/trust-accounts/{id}/close             — close account (guarded: zero balance required)

GET    /api/trust-accounts/{id}/lpff-rates        — list LPFF rate history
POST   /api/trust-accounts/{id}/lpff-rates        — add new LPFF rate
```

**Authorization**: `VIEW_TRUST` for GET, `MANAGE_TRUST` for mutations.

---

## Section 2 — Trust Transaction Ledger

### 2.1 Data Model

**TrustTransaction entity** (tenant-scoped, new table `trust_transactions`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `trust_account_id` | `UUID FK` | Which trust account |
| `transaction_type` | `VARCHAR(20)` | Enum: `DEPOSIT`, `PAYMENT`, `TRANSFER_IN`, `TRANSFER_OUT`, `FEE_TRANSFER`, `REFUND`, `INTEREST_CREDIT`, `INTEREST_LPFF`, `REVERSAL` |
| `amount` | `DECIMAL(15,2)` | Always positive. Direction determined by type. |
| `customer_id` | `UUID FK` | The client whose trust money is affected |
| `project_id` | `UUID FK` | Nullable. The matter this relates to. |
| `counterparty_customer_id` | `UUID FK` | Nullable. For TRANSFER_IN/TRANSFER_OUT — the other client. |
| `invoice_id` | `UUID FK` | Nullable. For FEE_TRANSFER — the invoice being paid from trust. |
| `reference` | `VARCHAR(200)` | Transaction reference (used for bank statement matching) |
| `description` | `TEXT` | What this transaction is for |
| `transaction_date` | `DATE` | The date of the transaction |
| `status` | `VARCHAR(20)` | Enum: `RECORDED`, `AWAITING_APPROVAL`, `APPROVED`, `REJECTED`, `REVERSED` |
| `approved_by` | `UUID FK` | Nullable. Member who approved. |
| `approved_at` | `TIMESTAMP` | |
| `second_approved_by` | `UUID FK` | Nullable. Second approver (dual approval). |
| `second_approved_at` | `TIMESTAMP` | |
| `rejected_by` | `UUID FK` | Nullable. Member who rejected. |
| `rejected_at` | `TIMESTAMP` | |
| `rejection_reason` | `VARCHAR(500)` | |
| `reversal_of` | `UUID FK` | Nullable. Points to the original transaction if this is a reversal. |
| `reversed_by_id` | `UUID FK` | Nullable. Points to the reversal transaction from the original. |
| `bank_statement_line_id` | `UUID FK` | Nullable. Set during reconciliation when matched to a bank statement line. |
| `recorded_by` | `UUID FK` | Member who recorded the transaction |
| `created_at` | `TIMESTAMP` | Immutable — no updated_at. Trust transactions are never modified. |

**Critical constraints**:
- No UPDATE on trust_transactions except for: `status` (lifecycle transitions), `approved_by`/`second_approved_by` (approval), `bank_statement_line_id` (reconciliation matching), `reversed_by_id` (linking to reversal). All other columns are immutable after creation.
- `amount` must be positive.
- `customer_id` is always required — every trust transaction is associated with a client.

### 2.2 Transaction Type Semantics

| Type | Cashbook Effect | Client Ledger Effect | Notes |
|------|----------------|---------------------|-------|
| `DEPOSIT` | +amount | +amount (credit client) | Client deposits money into trust |
| `PAYMENT` | -amount | -amount (debit client) | Firm pays from trust on behalf of client. **Requires approval.** |
| `TRANSFER_OUT` | 0 | -amount (debit source client) | Transfer between clients — source side |
| `TRANSFER_IN` | 0 | +amount (credit target client) | Transfer between clients — target side. Created as a pair with TRANSFER_OUT. |
| `FEE_TRANSFER` | -amount | -amount (debit client) | Transfer to office account to pay invoice. **Requires approval.** Links to Invoice. |
| `REFUND` | -amount | -amount (debit client) | Return unused trust money to client. **Requires approval.** |
| `INTEREST_CREDIT` | +amount | +amount (credit client) | Interest allocated to client |
| `INTEREST_LPFF` | -amount | 0 (no client effect) | Interest portion paid to Fidelity Fund. Recorded against a system-level "LPFF" client or null customer. |
| `REVERSAL` | opposite of original | opposite of original | Reverses a previous transaction. `reversal_of` FK required. |

**Paired transactions**: `TRANSFER_IN` and `TRANSFER_OUT` are always created as an atomic pair. They share no linking FK — instead they're created in the same service call and can be associated via matching `reference` and timestamp. A transfer from Client A to Client B creates two transactions: TRANSFER_OUT (customer=A, counterparty=B) and TRANSFER_IN (customer=B, counterparty=A).

### 2.3 Approval Workflow

Transactions that require approval: `PAYMENT`, `FEE_TRANSFER`, `REFUND`.

**Single approval mode** (default):
1. Transaction created with status `AWAITING_APPROVAL`
2. Member with `APPROVE_TRUST_PAYMENT` capability approves → status `APPROVED`
3. Client ledger balance is only affected when status becomes `APPROVED`
4. Reject → status `REJECTED`, no ledger effect

**Dual approval mode** (when `trust_account.require_dual_approval = true`):
1. Transaction created with status `AWAITING_APPROVAL`
2. First approver approves → `approved_by` set, status remains `AWAITING_APPROVAL`
3. Second approver (different member) approves → `second_approved_by` set, status `APPROVED`
4. Ledger effect only on final approval
5. Either approver can reject at any stage

**Threshold-based dual approval**: If `payment_approval_threshold` is set, dual approval only required when `amount >= threshold`. Below threshold, single approval suffices even when dual mode is enabled.

**Self-approval prevention**: The member who recorded the transaction (`recorded_by`) cannot be the sole approver. In single-approval mode, a different member must approve. In dual-approval mode, the recorder can be one of the two approvers but not both.

### 2.4 Negative Balance Prevention

Before any debit transaction (PAYMENT, TRANSFER_OUT, FEE_TRANSFER, REFUND) is approved:

1. Calculate the client's current trust balance from `ClientLedgerCard`
2. Subtract the pending transaction amount
3. If result < 0, **reject the transaction** with error: "Insufficient trust balance for [Client Name]. Available: R{balance}, Requested: R{amount}"

This check must be atomic. Implementation: `SELECT ... FOR UPDATE` on the `ClientLedgerCard` row within the approval transaction. This prevents race conditions where two concurrent approvals could overdraw the balance.

### 2.5 Reversal Flow

To reverse a transaction:
1. Create a new `TrustTransaction` with type `REVERSAL` and `reversal_of` pointing to the original
2. The reversal has the opposite ledger effect (see table above)
3. The original transaction's `reversed_by_id` is set to point to the reversal
4. The original transaction's status changes to `REVERSED`
5. Reversals of debit transactions (adding money back) do not require approval
6. Reversals of credit transactions (removing money) require approval and negative balance check
7. A reversed transaction cannot be reversed again

### 2.6 Trust Transaction Service

```
TrustTransactionService
  + recordDeposit(trustAccountId, dto) → TrustTransaction
  + recordPayment(trustAccountId, dto) → TrustTransaction        // Creates in AWAITING_APPROVAL
  + recordTransfer(trustAccountId, dto) → Pair<TrustTransaction>  // Creates paired TRANSFER_OUT + TRANSFER_IN
  + recordFeeTransfer(trustAccountId, dto) → TrustTransaction     // Creates in AWAITING_APPROVAL, links to Invoice
  + recordRefund(trustAccountId, dto) → TrustTransaction          // Creates in AWAITING_APPROVAL
  + approveTransaction(id, approverId) → TrustTransaction
  + rejectTransaction(id, rejecterId, reason) → TrustTransaction
  + reverseTransaction(id, reason) → TrustTransaction
  + listTransactions(trustAccountId, filters) → Page<TrustTransaction>
  + getTransaction(id) → TrustTransaction
  + getCashbookBalance(trustAccountId) → BigDecimal               // Sum of all approved cashbook-affecting transactions
  + getClientBalance(customerId, trustAccountId) → BigDecimal     // Current trust balance for a specific client
  + getPendingApprovals(trustAccountId) → List<TrustTransaction>  // All AWAITING_APPROVAL transactions
```

### 2.7 Endpoints

```
GET    /api/trust-accounts/{accountId}/transactions                 — list transactions (filterable by date range, type, status, customer, project)
GET    /api/trust-accounts/{accountId}/transactions/{id}            — single transaction detail
POST   /api/trust-accounts/{accountId}/transactions/deposit         — record deposit
POST   /api/trust-accounts/{accountId}/transactions/payment         — record payment (→ AWAITING_APPROVAL)
POST   /api/trust-accounts/{accountId}/transactions/transfer        — record inter-client transfer
POST   /api/trust-accounts/{accountId}/transactions/fee-transfer    — record fee transfer to office (links to invoice)
POST   /api/trust-accounts/{accountId}/transactions/refund          — record refund to client

POST   /api/trust-transactions/{id}/approve                         — approve a pending transaction
POST   /api/trust-transactions/{id}/reject                          — reject a pending transaction
POST   /api/trust-transactions/{id}/reverse                         — reverse an approved transaction

GET    /api/trust-accounts/{accountId}/cashbook-balance             — current cashbook balance
GET    /api/trust-accounts/{accountId}/pending-approvals            — list pending approvals
```

**Authorization**: `VIEW_TRUST` for GET endpoints. `MANAGE_TRUST` for recording transactions. `APPROVE_TRUST_PAYMENT` for approve/reject.

---

## Section 3 — Client Ledger Cards

### 3.1 Data Model

**ClientLedgerCard entity** (tenant-scoped, new table `client_ledger_cards`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `trust_account_id` | `UUID FK` | Which trust account |
| `customer_id` | `UUID FK` | The client |
| `balance` | `DECIMAL(15,2)` | Current trust balance. Updated atomically on every approved transaction. |
| `total_deposits` | `DECIMAL(15,2)` | Running total of all deposits (for reporting) |
| `total_payments` | `DECIMAL(15,2)` | Running total of all payments made on behalf |
| `total_fee_transfers` | `DECIMAL(15,2)` | Running total of fee transfers to office |
| `total_interest_credited` | `DECIMAL(15,2)` | Running total of interest credited |
| `last_transaction_date` | `DATE` | Date of the most recent transaction |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**Unique constraint**: `(trust_account_id, customer_id)` — one ledger card per client per trust account.

**Critical invariant**: `balance >= 0` enforced at application level (SELECT FOR UPDATE before debit) AND at database level (CHECK constraint `balance >= 0`). Belt and suspenders — this invariant cannot be violated under any circumstances.

### 3.2 Ledger Card Lifecycle

- **Auto-created**: When the first trust transaction for a client on a specific trust account is approved, the `ClientLedgerCard` is created with the transaction amount as the initial balance.
- **Updated atomically**: Every approved transaction updates the balance, running totals, and last_transaction_date within the same database transaction.
- **Never deleted**: Even when balance reaches zero, the ledger card persists (historical record). It may receive future transactions.

### 3.3 Client Ledger Service

```
ClientLedgerService
  + getClientLedger(customerId, trustAccountId) → ClientLedgerCard
  + listClientLedgers(trustAccountId, filters) → Page<ClientLedgerCard>  // Filterable: non-zero balance only, customer name search
  + getClientTransactionHistory(customerId, trustAccountId, dateRange) → List<TrustTransaction>
  + getClientBalanceAsOfDate(customerId, trustAccountId, date) → BigDecimal  // Historical balance at a point in time
  + getTotalTrustBalance(trustAccountId) → BigDecimal  // Sum of all client ledger balances (must equal cashbook balance)
  + getClientLedgerStatement(customerId, trustAccountId, dateRange) → LedgerStatement  // For PDF rendering
```

### 3.4 Endpoints

```
GET    /api/trust-accounts/{accountId}/client-ledgers                        — list all client ledger cards
GET    /api/trust-accounts/{accountId}/client-ledgers/{customerId}           — single client ledger card with balance
GET    /api/trust-accounts/{accountId}/client-ledgers/{customerId}/history   — transaction history for this client
GET    /api/trust-accounts/{accountId}/client-ledgers/{customerId}/statement — ledger statement (date range, renderable)
GET    /api/trust-accounts/{accountId}/total-balance                         — sum of all client balances
```

---

## Section 4 — Bank Statement Import & Reconciliation

### 4.1 Data Model

**BankStatement entity** (tenant-scoped, new table `bank_statements`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `trust_account_id` | `UUID FK` | |
| `period_start` | `DATE` | Statement period start |
| `period_end` | `DATE` | Statement period end |
| `opening_balance` | `DECIMAL(15,2)` | Balance at start of period |
| `closing_balance` | `DECIMAL(15,2)` | Balance at end of period |
| `file_key` | `VARCHAR(500)` | S3 key for the uploaded file |
| `file_name` | `VARCHAR(200)` | Original filename |
| `format` | `VARCHAR(20)` | Enum: `CSV`, `OFX` |
| `line_count` | `INTEGER` | Number of lines imported |
| `matched_count` | `INTEGER` | Number of lines matched to transactions |
| `status` | `VARCHAR(20)` | Enum: `IMPORTED`, `MATCHING_IN_PROGRESS`, `MATCHED`, `RECONCILED` |
| `imported_by` | `UUID FK` | |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**BankStatementLine entity** (tenant-scoped, new table `bank_statement_lines`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `bank_statement_id` | `UUID FK` | |
| `line_number` | `INTEGER` | Position in the statement |
| `transaction_date` | `DATE` | Date from the bank |
| `description` | `VARCHAR(500)` | Description from the bank |
| `reference` | `VARCHAR(200)` | Reference from the bank |
| `amount` | `DECIMAL(15,2)` | Signed: positive = credit (deposit), negative = debit (payment) |
| `running_balance` | `DECIMAL(15,2)` | Bank's running balance (if available) |
| `match_status` | `VARCHAR(20)` | Enum: `UNMATCHED`, `AUTO_MATCHED`, `MANUALLY_MATCHED`, `EXCLUDED` |
| `trust_transaction_id` | `UUID FK` | Nullable. The matched trust transaction. |
| `match_confidence` | `DECIMAL(3,2)` | Nullable. Auto-match confidence score (0-1). |
| `excluded_reason` | `VARCHAR(200)` | Nullable. Why this line was excluded (e.g., bank charges, not trust money). |
| `created_at` | `TIMESTAMP` | |

### 4.2 Import Parser

**Strategy pattern**: `BankStatementParser` interface with implementations:
- `CsvBankStatementParser` — Required. Handles common SA bank CSV formats (FNB, Standard Bank, Nedbank, ABSA). Each has slightly different column layouts. Parser auto-detects bank by header row patterns.
- `OfxBankStatementParser` — Stretch goal. Standard OFX/QFX format.

**Import flow**:
1. User uploads file via multipart POST
2. File stored in S3 (`trust-statements/{tenantSchema}/{accountId}/{filename}`)
3. Parser reads file, creates `BankStatement` + `BankStatementLine` records
4. Returns the statement with all lines for review

### 4.3 Auto-Matching Algorithm

After import, the system attempts to auto-match bank statement lines to existing trust transactions:

**Matching criteria** (scored):
1. **Exact reference match** (highest confidence: 1.0) — bank line reference matches `TrustTransaction.reference`
2. **Amount + date match** (confidence: 0.8) — same amount, same date, only one candidate
3. **Amount + close date match** (confidence: 0.6) — same amount, date within ±3 days, only one candidate
4. **Amount match only** (confidence: 0.4) — same amount, multiple candidates. Flagged for manual review.

**Auto-match threshold**: Lines with confidence ≥ 0.8 are auto-matched. Below 0.8, flagged as `UNMATCHED` for manual matching.

**Matching only considers**: Approved trust transactions that don't already have a `bank_statement_line_id` set. Transactions in the date range of the statement ±7 days.

### 4.4 Reconciliation Workflow

**TrustReconciliation entity** (tenant-scoped, new table `trust_reconciliations`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `trust_account_id` | `UUID FK` | |
| `period_end` | `DATE` | Reconciliation date (typically month-end) |
| `bank_statement_id` | `UUID FK` | Nullable. The bank statement used. |
| `bank_balance` | `DECIMAL(15,2)` | Balance per bank statement |
| `cashbook_balance` | `DECIMAL(15,2)` | Balance per trust cashbook |
| `client_ledger_total` | `DECIMAL(15,2)` | Sum of all client ledger card balances |
| `outstanding_deposits` | `DECIMAL(15,2)` | Deposits recorded but not yet on bank statement |
| `outstanding_payments` | `DECIMAL(15,2)` | Payments recorded but not yet on bank statement |
| `adjusted_bank_balance` | `DECIMAL(15,2)` | bank_balance + outstanding_deposits - outstanding_payments |
| `is_balanced` | `BOOLEAN` | True when adjusted_bank_balance = cashbook_balance = client_ledger_total |
| `status` | `VARCHAR(20)` | Enum: `DRAFT`, `COMPLETED` |
| `completed_by` | `UUID FK` | |
| `completed_at` | `TIMESTAMP` | |
| `notes` | `TEXT` | |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**Three-way reconciliation check**:
```
adjusted_bank_balance = bank_balance + outstanding_deposits - outstanding_payments
cashbook_balance = sum of all approved cashbook-affecting trust transactions
client_ledger_total = sum of all client_ledger_cards.balance

is_balanced = (adjusted_bank_balance == cashbook_balance) AND (cashbook_balance == client_ledger_total)
```

A reconciliation can only be marked `COMPLETED` when `is_balanced = true`. This is the core compliance check.

**Outstanding items**: Trust transactions that are approved but not matched to any bank statement line are "outstanding" — they'll appear on the next month's bank statement. These are normal (timing differences) and tracked as outstanding deposits/payments.

### 4.5 Reconciliation Service

```
TrustReconciliationService
  + importBankStatement(accountId, file) → BankStatement
  + getBankStatement(statementId) → BankStatement with lines
  + autoMatchStatement(statementId) → MatchResult (matched count, unmatched count)
  + manualMatch(statementLineId, transactionId) → void
  + unmatch(statementLineId) → void
  + excludeLine(statementLineId, reason) → void
  + createReconciliation(accountId, dto) → TrustReconciliation
  + calculateReconciliation(reconciliationId) → TrustReconciliation  // Fills in all balance fields
  + completeReconciliation(reconciliationId) → TrustReconciliation   // Guarded: must be balanced
  + listReconciliations(accountId) → Page<TrustReconciliation>
  + getReconciliation(reconciliationId) → TrustReconciliation
  + getOutstandingItems(accountId, asOfDate) → OutstandingItems      // Unmatched transactions
```

### 4.6 Endpoints

```
POST   /api/trust-accounts/{accountId}/bank-statements              — upload & import statement (multipart)
GET    /api/trust-accounts/{accountId}/bank-statements               — list imported statements
GET    /api/bank-statements/{statementId}                            — statement detail with lines
POST   /api/bank-statements/{statementId}/auto-match                 — trigger auto-matching
POST   /api/bank-statement-lines/{lineId}/match                      — manual match (body: { transactionId })
POST   /api/bank-statement-lines/{lineId}/unmatch                    — remove match
POST   /api/bank-statement-lines/{lineId}/exclude                    — exclude line (body: { reason })

POST   /api/trust-accounts/{accountId}/reconciliations               — create new reconciliation
GET    /api/trust-accounts/{accountId}/reconciliations               — list reconciliations
GET    /api/trust-reconciliations/{reconciliationId}                  — reconciliation detail
POST   /api/trust-reconciliations/{reconciliationId}/calculate       — calculate/refresh balances
POST   /api/trust-reconciliations/{reconciliationId}/complete        — mark complete (guarded: must balance)
```

---

## Section 5 — Interest Calculation & LPFF Allocation

### 5.1 Data Model

**InterestRun entity** (tenant-scoped, new table `interest_runs`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `trust_account_id` | `UUID FK` | |
| `period_start` | `DATE` | Interest calculation period start |
| `period_end` | `DATE` | Interest calculation period end |
| `lpff_rate_id` | `UUID FK` | The LPFF rate used for this calculation |
| `total_interest` | `DECIMAL(15,2)` | Total interest earned in the period |
| `total_lpff_share` | `DECIMAL(15,2)` | LPFF's portion |
| `total_client_share` | `DECIMAL(15,2)` | Total credited to clients |
| `status` | `VARCHAR(20)` | Enum: `DRAFT`, `APPROVED`, `POSTED` |
| `approved_by` | `UUID FK` | |
| `posted_at` | `TIMESTAMP` | When interest transactions were created |
| `created_at` / `updated_at` | `TIMESTAMP` | |

**InterestAllocation entity** (tenant-scoped, new table `interest_allocations`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `interest_run_id` | `UUID FK` | |
| `customer_id` | `UUID FK` | |
| `average_daily_balance` | `DECIMAL(15,2)` | Client's average daily balance for the period |
| `days_in_period` | `INTEGER` | |
| `gross_interest` | `DECIMAL(15,2)` | Interest earned on this client's balance |
| `lpff_share` | `DECIMAL(15,2)` | LPFF portion |
| `client_share` | `DECIMAL(15,2)` | Amount credited to client |
| `trust_transaction_id` | `UUID FK` | Nullable. FK to the INTEREST_CREDIT transaction created when posted. |
| `created_at` | `TIMESTAMP` | |

### 5.2 Interest Calculation Logic

**Daily balance method**:
1. For each client with a ledger card on the trust account:
   a. Get all approved transactions in the period, ordered by date
   b. Calculate the balance at the start of each day
   c. Sum (daily_balance × 1) for each day → total balance-days
   d. Average daily balance = total balance-days / days_in_period
   e. Gross interest = average_daily_balance × (annual_rate / 365) × days_in_period
   f. LPFF share = gross_interest × lpff_share_percent
   g. Client share = gross_interest - LPFF share

2. Sum all client allocations for totals.

**Rate lookup**: Use the `LpffRate` effective on the `period_start` date. If the rate changes mid-period, split the calculation at the rate change date (pro-rata).

### 5.3 Interest Posting

When an `InterestRun` is approved and posted:
1. For each `InterestAllocation` with `client_share > 0`:
   - Create a `TrustTransaction` (type: `INTEREST_CREDIT`, amount: client_share, customer: client)
   - This credits the client's ledger card
   - Link the transaction to the allocation (`trust_transaction_id`)
2. Create a single `TrustTransaction` (type: `INTEREST_LPFF`, amount: total_lpff_share)
   - This is a debit on the cashbook (money owed/paid to LPFF)
3. All transactions created atomically

### 5.4 Service & Endpoints

```
InterestService
  + createInterestRun(accountId, periodStart, periodEnd) → InterestRun
  + calculateInterest(runId) → InterestRun with allocations
  + approveInterestRun(runId) → InterestRun
  + postInterestRun(runId) → InterestRun  // Creates trust transactions
  + listInterestRuns(accountId) → Page<InterestRun>
  + getInterestRun(runId) → InterestRun with allocations
```

```
POST   /api/trust-accounts/{accountId}/interest-runs                 — create interest run
GET    /api/trust-accounts/{accountId}/interest-runs                  — list interest runs
GET    /api/interest-runs/{runId}                                     — interest run detail with allocations
POST   /api/interest-runs/{runId}/calculate                           — calculate/recalculate
POST   /api/interest-runs/{runId}/approve                             — approve
POST   /api/interest-runs/{runId}/post                                — post to ledger (creates transactions)
```

---

## Section 6 — Trust Investments

### 6.1 Data Model

**TrustInvestment entity** (tenant-scoped, new table `trust_investments`):

| Column | Type | Notes |
|--------|------|-------|
| `id` | `UUID` | PK |
| `trust_account_id` | `UUID FK` | Source trust account |
| `customer_id` | `UUID FK` | Client whose money is invested |
| `institution` | `VARCHAR(200)` | Investment institution (e.g., "FNB Money Market") |
| `account_number` | `VARCHAR(50)` | Investment account number |
| `principal` | `DECIMAL(15,2)` | Amount invested |
| `interest_rate` | `DECIMAL(5,4)` | Annual interest rate |
| `deposit_date` | `DATE` | When the investment was placed |
| `maturity_date` | `DATE` | When the investment matures. Nullable for call deposits. |
| `interest_earned` | `DECIMAL(15,2)` | Running total of interest earned. Updated when interest is received. |
| `status` | `VARCHAR(20)` | Enum: `ACTIVE`, `MATURED`, `WITHDRAWN` |
| `withdrawal_date` | `DATE` | Nullable. |
| `withdrawal_amount` | `DECIMAL(15,2)` | Nullable. Principal + earned interest at withdrawal. |
| `deposit_transaction_id` | `UUID FK` | The PAYMENT transaction that moved money from trust to investment |
| `withdrawal_transaction_id` | `UUID FK` | Nullable. The DEPOSIT transaction that returned money from investment to trust |
| `notes` | `TEXT` | |
| `created_at` / `updated_at` | `TIMESTAMP` | |

### 6.2 Investment Lifecycle

1. **Place investment**: Firm records a PAYMENT from trust (debits client's trust balance), creates `TrustInvestment` with status ACTIVE.
2. **Record interest**: Periodically update `interest_earned` as interest accrues. This doesn't affect the trust ledger — interest stays in the investment until withdrawal.
3. **Mature**: When maturity date is reached, status → MATURED. Notification sent.
4. **Withdraw**: Firm withdraws principal + interest back to trust. Creates a DEPOSIT transaction (credits client's trust balance). `withdrawal_amount` and `withdrawal_transaction_id` recorded.

### 6.3 Service & Endpoints

```
TrustInvestmentService
  + placeInvestment(accountId, dto) → TrustInvestment   // Also creates PAYMENT transaction
  + recordInterestEarned(investmentId, amount) → TrustInvestment
  + withdrawInvestment(investmentId) → TrustInvestment   // Also creates DEPOSIT transaction
  + listInvestments(accountId, filters) → Page<TrustInvestment>
  + getInvestment(investmentId) → TrustInvestment
  + getMaturing(accountId, daysAhead) → List<TrustInvestment>  // Investments maturing soon
```

```
GET    /api/trust-accounts/{accountId}/investments                   — list investments (filterable by status, customer)
GET    /api/trust-investments/{investmentId}                          — investment detail
POST   /api/trust-accounts/{accountId}/investments                   — place new investment
PUT    /api/trust-investments/{investmentId}/interest                 — record interest earned
POST   /api/trust-investments/{investmentId}/withdraw                — withdraw investment back to trust
GET    /api/trust-accounts/{accountId}/investments/maturing           — investments maturing within N days
```

---

## Section 7 — Trust Reports (Section 35 Compliance)

### 7.1 Report Types

All reports use the existing `ReportDefinition` execution framework from Phase 19. Each report type is registered as a new `ReportDefinition` with `category = 'TRUST'`.

| Report | Description | Parameters |
|--------|-------------|------------|
| `TRUST_RECEIPTS_PAYMENTS` | Chronological journal of all trust transactions | trust_account_id, date_from, date_to |
| `CLIENT_TRUST_BALANCES` | All clients with trust balances at a point in time | trust_account_id, as_of_date |
| `CLIENT_LEDGER_STATEMENT` | Full transaction history for one client | trust_account_id, customer_id, date_from, date_to |
| `TRUST_RECONCILIATION` | Three-way reconciliation statement | reconciliation_id |
| `INVESTMENT_REGISTER` | All trust investments with status and returns | trust_account_id, as_of_date |
| `INTEREST_ALLOCATION` | Interest calculation details and LPFF allocation | interest_run_id |
| `SECTION_35_DATA_PACK` | Combined report: balances, reconciliation, investments, interest — the data pack an auditor needs for the annual Section 35 certificate | trust_account_id, financial_year_end |

### 7.2 Report Rendering

All reports support PDF, CSV, and Excel output via the existing rendering pipeline. The `SECTION_35_DATA_PACK` generates a multi-section PDF combining all sub-reports for the financial year.

### 7.3 Endpoints

Reports use the existing report execution endpoints from Phase 19:
```
POST   /api/reports/execute                                          — execute a report (body: { reportType, parameters, format })
GET    /api/reports/{executionId}/download                           — download rendered report
```

No new endpoints needed — just new `ReportDefinition` registrations and query implementations.

---

## Section 8 — Frontend

### 8.1 Trust Dashboard (`/legal/trust-accounting`)

Replace the stub page with a real dashboard. Module-gated behind `trust_accounting`.

**Layout:**
- **Summary cards**: Total trust balance (all clients), number of active clients with trust balances, pending approvals count, last reconciliation date & status
- **Recent transactions**: Last 10 trust transactions with type, client, amount, status
- **Reconciliation status**: Current month — reconciled? Outstanding items? Three-way balance check result (green/red indicator)
- **Alerts**: Maturing investments, overdue reconciliations (no reconciliation in >30 days), pending approvals aging >48 hours

### 8.2 Trust Transactions Page (`/legal/trust-accounting/transactions`)

**Table columns**: Date, Reference, Type (badge: colored by type), Client, Matter, Amount, Status (badge), Recorded By, Approved By

**Filters**: Date range, transaction type, status, client, matter

**Actions**:
- Record Deposit — dialog: client selector, matter (optional), amount, reference, description, date
- Record Payment — dialog: client selector, matter (optional), amount, reference, description, date, payee details
- Record Transfer — dialog: source client, target client, amount, reference, description
- Record Fee Transfer — dialog: client selector, invoice selector (filtered to client's unpaid invoices), amount (pre-filled from invoice), reference
- Record Refund — dialog: client selector, amount (max: client's trust balance), reference, description

**Approval actions** (visible when status = AWAITING_APPROVAL and user has APPROVE_TRUST_PAYMENT):
- Approve button
- Reject button (with reason dialog)

**Reversal action** (visible for APPROVED transactions):
- Reverse button (with reason dialog)

### 8.3 Client Ledger Page (`/legal/trust-accounting/client-ledgers`)

**List view**: Table of all client ledger cards. Columns: Client Name, Trust Balance, Total Deposits, Total Payments, Total Fee Transfers, Last Transaction Date. Sort by balance (highest first).

**Detail view** (click on a client): Full transaction history for that client on the selected trust account. Running balance column. Same filter/sort as transactions page but scoped to one client. "Print Statement" button generates a PDF client ledger statement.

### 8.4 Bank Reconciliation Page (`/legal/trust-accounting/reconciliation`)

**Reconciliation list**: Table of completed and in-progress reconciliations. Columns: Period, Bank Balance, Cashbook Balance, Client Ledger Total, Status (Balanced ✓ / Unbalanced ✗), Completed Date.

**New Reconciliation flow**:
1. Select trust account and period end date
2. Upload bank statement (CSV file picker)
3. System imports and auto-matches
4. **Split-pane matching interface**:
   - Left panel: Bank statement lines (unmatched highlighted)
   - Right panel: Unmatched trust transactions
   - Click a bank line → highlight candidate transaction matches (by amount/date)
   - Click "Match" to link them. Click "Exclude" to exclude a bank line (with reason).
   - Match confidence indicators on auto-matched lines
5. Summary panel at bottom: bank balance, cashbook balance, client ledger total, outstanding deposits, outstanding payments, adjusted bank balance
6. Three-way check indicator: green when all three agree, red with difference amount when they don't
7. "Complete Reconciliation" button (disabled until balanced)

### 8.5 Interest Calculation Page (`/legal/trust-accounting/interest`)

**Interest runs list**: Table of past interest calculations. Columns: Period, Total Interest, LPFF Share, Client Share, Status, Posted Date.

**New Interest Run flow**:
1. Select trust account, period start, period end
2. "Calculate" → shows allocations per client: Client Name, Average Daily Balance, Gross Interest, LPFF Share, Client Share
3. Review and "Approve" → then "Post to Ledger" → creates INTEREST_CREDIT and INTEREST_LPFF transactions

**LPFF Rate Management**: Sub-section showing rate history. "Add Rate" dialog: effective date, rate %, LPFF share %.

### 8.6 Investment Register Page (`/legal/trust-accounting/investments`)

**Table columns**: Client, Institution, Principal, Interest Rate, Deposit Date, Maturity Date, Interest Earned, Status

**Actions**:
- Place Investment — dialog: client selector, institution, account number, principal, interest rate, deposit date, maturity date
- Record Interest — dialog: amount earned
- Withdraw — confirms and creates deposit transaction back to trust

**Alert indicators**: Investments maturing within 30 days highlighted in amber.

### 8.7 Trust Reports Page (`/legal/trust-accounting/reports`)

Simple page listing available trust reports with "Generate" buttons. Each opens a dialog for parameters (date range, client, etc.) and format selection (PDF/CSV/Excel). Generated reports download directly.

### 8.8 Trust Settings (in Settings area)

Under Settings > Trust Accounting (module-gated):
- Trust account management (add/edit/close accounts)
- Bank details configuration
- Approval settings (single/dual, threshold)
- LPFF rate management
- Default reminder settings (investment maturity warnings)

### 8.9 Matter (Project) Detail Integration

On the project/matter detail page, add a **"Trust" tab** (module-gated behind `trust_accounting`):
- Client's trust balance for this matter
- Transaction history filtered to this matter
- Quick actions: Record Deposit, Record Payment, Record Fee Transfer (pre-filled with matter context)

### 8.10 Customer Detail Integration

On the customer/client detail page, add a **"Trust" tab** (module-gated behind `trust_accounting`):
- Client's total trust balance across all trust accounts
- Ledger card summary
- Transaction history for this client
- Active investments for this client
- Quick actions: Record Deposit, View Full Ledger

### 8.11 Sidebar Navigation

Trust accounting nav items in the Legal zone (conditionally shown via `ModuleGate`):
- "Trust Accounting" (dashboard) — `/legal/trust-accounting`
- Sub-items: Transactions, Client Ledgers, Reconciliation, Interest, Investments, Reports

---

## Section 9 — Legal Pack Content Extension

### 9.1 Document Template Pack

Add trust-specific document templates to the `legal-za` template pack:
- **Client Trust Statement** — Tiptap template for a printable client ledger statement
- **Trust Receipt** — Tiptap template for a trust deposit receipt (given to client when they pay money into trust)
- **Section 35 Cover Letter** — Template for the cover letter accompanying the Section 35 certificate to auditors

### 9.2 Automation Pack

Add trust-related automation rules to the `legal-za` automation pack:
- **Investment maturity reminder** — 30 days before maturity, notify the assigned member
- **Reconciliation overdue reminder** — If no completed reconciliation in the last 35 days, notify the firm admin
- **Pending approval aging** — If a trust payment has been AWAITING_APPROVAL for >48 hours, escalate notification

### 9.3 RBAC Capability Registration

Register new capabilities for the trust module:
- `VIEW_TRUST` — View trust accounts, transactions, ledgers, reports
- `MANAGE_TRUST` — Record transactions, manage accounts, run interest calculations
- `APPROVE_TRUST_PAYMENT` — Approve/reject trust payments, fee transfers, refunds

Default capability assignment:
- `owner` role: all three
- `admin` role: `VIEW_TRUST`, `MANAGE_TRUST`
- `member` role: `VIEW_TRUST`

---

## Section 10 — Audit & Notification Integration

### 10.1 Audit Events

All trust operations emit audit events via the existing `AuditEventService`:

| Event Type | Trigger |
|------------|---------|
| `TRUST_ACCOUNT_CREATED` | New trust account configured |
| `TRUST_ACCOUNT_CLOSED` | Trust account closed |
| `TRUST_DEPOSIT_RECORDED` | Deposit transaction created |
| `TRUST_PAYMENT_RECORDED` | Payment transaction created (awaiting approval) |
| `TRUST_PAYMENT_APPROVED` | Payment approved (single or dual) |
| `TRUST_PAYMENT_REJECTED` | Payment rejected |
| `TRUST_TRANSFER_RECORDED` | Inter-client transfer created |
| `TRUST_FEE_TRANSFER_RECORDED` | Fee transfer to office account |
| `TRUST_REFUND_RECORDED` | Refund transaction created |
| `TRUST_TRANSACTION_REVERSED` | Transaction reversed |
| `TRUST_RECONCILIATION_COMPLETED` | Monthly reconciliation marked complete |
| `TRUST_INTEREST_POSTED` | Interest run posted to ledger |
| `TRUST_INVESTMENT_PLACED` | Investment created |
| `TRUST_INVESTMENT_WITHDRAWN` | Investment withdrawn back to trust |

### 10.2 Notifications

| Event | Recipients | Channel |
|-------|-----------|---------|
| Payment awaiting approval | Members with `APPROVE_TRUST_PAYMENT` | In-app + email |
| Payment approved | Transaction recorder | In-app |
| Payment rejected | Transaction recorder | In-app + email |
| Reconciliation overdue (>30 days) | Firm admin | In-app + email |
| Investment maturing (<30 days) | Member who placed investment | In-app |
| Pending approval aging (>48h) | All approvers | In-app + email |

---

## Out of Scope

- **Trust accounting for non-SA jurisdictions.** The data model is generic, but the LPFF interest allocation, Section 35 reporting, and prescription rules are SA-specific. Other jurisdictions would need their own regulatory layer — not in this phase.
- **Multi-currency trust accounts.** ZAR only. Foreign litigation trust accounts are a future enhancement.
- **Bank API integration.** No direct bank feeds. Reconciliation is via CSV upload only.
- **Accounting software sync.** No Xero/Sage integration. Fee transfers are recorded in the trust ledger; the firm manually records the corresponding receipt in their office accounting system.
- **Tenant impersonation for trust review.** Platform admin cannot view tenant trust data (privacy/compliance).
- **Trust accounting for the customer portal.** Clients do not see their trust balance via the portal in this phase. This is a future enhancement.
- **Section 35 certificate submission.** The system generates the data; the firm submits to the Fidelity Fund manually.
- **Trust money receipt printing.** Physical receipt printing (beyond the PDF template) is not in scope.

## ADR Topics

1. **ADR: Double-entry vs. single-entry trust ledger** — Why double-entry (cashbook + client ledger as two views of the same transactions) is the correct model for Section 86 compliance, and how immutable transactions with reversal semantics maintain audit integrity.

2. **ADR: Negative balance prevention strategy** — Database CHECK constraint + application-level SELECT FOR UPDATE. Why both layers are necessary (defense in depth for a legal compliance requirement). Trade-off: SELECT FOR UPDATE introduces contention, but trust transaction volumes are low (~50-200/day for a small firm) so this is acceptable.

3. **ADR: Configurable dual authorization** — Org-level setting for single vs. dual approval with optional threshold. Why self-approval prevention is critical. How this maps to the existing RBAC capability system.

4. **ADR: Bank reconciliation matching strategy** — Reference-first matching with amount/date fallback. Why a 0.8 confidence threshold for auto-matching balances accuracy vs. manual effort. How the split-pane UX supports the bookkeeper workflow.

5. **ADR: Interest calculation — daily balance method** — Why daily balance (not monthly average) is the correct method for trust interest. How mid-period rate changes are handled via pro-rata splitting. LPFF allocation calculation.

## Style & Boundaries

- **Module gating everywhere.** Every controller method, every frontend page and tab MUST be gated behind `trust_accounting` module. No trust code should execute or render for accounting-profile tenants.
- **Immutability is sacrosanct.** Trust transactions are append-only. No UPDATE on amount, date, client, or type. The only mutable fields are status (lifecycle), approval fields, and reconciliation links.
- **Negative balance check is a hard block.** Not a warning, not an advisory — a hard 400 error that prevents the operation. This is a legal requirement.
- **The trust ledger is not the invoice system.** Fee transfers link to invoices via FK, but the trust ledger and invoice system are separate domains with a clean boundary. An invoice can be paid from trust, portal, or direct EFT — the invoice doesn't know or care which path was used.
- **Pack content uses real SA legal terms.** LPFF = Legal Practitioners Fidelity Fund. Section 86 = Legal Practice Act, 2014. Use the correct statutory references in UI labels and report headers.
- **Follow existing Phase 55 patterns.** Legal module controllers live in `verticals.legal.*` packages. Frontend pages under `/legal/*`. Module registration in `ModuleRegistry`. Same architecture as court calendar and conflict check.
