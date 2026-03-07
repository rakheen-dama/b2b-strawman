# Phase 40 — Bulk Billing & Batch Operations

## System Context

DocTeams is a multi-tenant B2B SaaS platform (schema-per-tenant isolation) for professional services firms. After Phase 39, the platform has:

- **Invoicing (Phase 10)**: `Invoice` entity with status lifecycle (DRAFT -> APPROVED -> SENT -> PAID / VOID). `InvoiceLine` entity for time-based and manual line items. `InvoiceService` handles single-customer invoice creation from selected time entry IDs. `InvoiceNumberService` generates sequential invoice numbers per tenant. `InvoiceCounter` tracks the sequence.
- **Unbilled time query (Phase 10)**: `InvoiceService.getUnbilledTime(customerId, from, to)` returns billable time entries grouped by project, with rate snapshots and calculated billable values. Native SQL query joining time_entries -> tasks -> projects -> customer_projects -> members.
- **Expense billing (Phase 30)**: `Expense` entity with billable flag and markup. Expenses linked to invoices via `InvoiceLine` with type `EXPENSE`. `ExpenseRepository.findUnbilledBillableByCustomerId()` retrieves unbilled expenses.
- **Tax handling (Phase 26)**: `TaxRate` entity, `TaxCalculationService` auto-applies default tax rates to invoice lines. Tax breakdown per invoice.
- **Retainer billing (Phase 17)**: `RetainerAgreement` with period close generating invoices automatically via `RetainerPeriodService.closePeriod()`. Retainer invoices include consumed hours as line items.
- **Email delivery (Phase 24)**: `EmailNotificationChannel` with SMTP and SendGrid adapters. Invoice sending triggers email with HTML preview via `InvoiceEmailService`.
- **Payment collection (Phase 25)**: `PaymentGateway` port with Stripe and PayFast adapters. Payment links generated per invoice. Webhook reconciliation for payment confirmation.
- **Customer lifecycle (Phase 14)**: `CustomerLifecycleGuard` blocks invoice creation for non-ACTIVE customers. `PrerequisiteService` gates invoice generation on configurable prerequisites (e.g., required fields, portal contact).
- **Custom fields on invoices (Phase 23)**: Invoices support custom fields via the field definition system. Field groups auto-applied per entity type.
- **Automation engine (Phase 37)**: Rule-based automations with `INVOICE_STATUS_CHANGED` trigger type. Actions include send notification, send email, update status.
- **Document templates (Phase 12/31)**: Invoice PDF generation via Tiptap rendering + OpenHTMLToPDF. Templates with variable substitution.

**Gap**: Invoicing is strictly one-customer-at-a-time. `createDraft()` accepts a single `customerId` + `timeEntryIds`. A firm with 40 monthly bookkeeping clients must repeat the same workflow 40 times: open customer, view unbilled time, select entries, generate draft, review, approve, send. This is the #1 operational bottleneck for firms doing periodic billing. Every competitor (Xero Practice Manager, Harvest, Accelo) offers batch invoice generation. Without it, month-end billing consumes an entire day instead of an hour.

## Objective

Build a **bulk billing system** that allows firm administrators to:

1. **Initiate a billing run** — select multiple customers (or "all active with unbilled work") and a billing period (date range) to create a billing batch.
2. **Preview unbilled work per customer** — before generating any invoices, see a summary of unbilled time and expenses per customer, with the ability to cherry-pick which entries to include or exclude per customer.
3. **Generate draft invoices in batch** — create DRAFT invoices for all selected customers in one action, reusing existing invoice creation logic. Failures on individual customers (prerequisites, inactive status) are captured without blocking the batch.
4. **Review the batch** — view all generated drafts as a cohesive group. Edit individual invoices within the batch context (adjust line items, notes, due dates) without navigating away from the batch view.
5. **Approve and send in batch** — transition all drafts to APPROVED, then send emails with payment links for the entire batch. Rate-limited email sending to respect provider throttling.
6. **Track billing history** — view past billing runs with statistics (total invoices generated, total amount, sent count, paid count). Enables month-over-month billing comparison.
7. **Close retainer periods in batch** — trigger period close for all retainer agreements with due periods in a single action, generating retainer invoices alongside time-based invoices.

## Constraints & Assumptions

- **Billing runs are a convenience layer, not a new invoice type.** Invoices generated in a batch are standard `Invoice` entities. The `BillingRun` entity tracks the batch context but doesn't alter invoice behavior.
- **Cherry-pick is per customer, not per time entry initially.** The preview shows unbilled time and expenses grouped by customer, with per-entry checkboxes for inclusion/exclusion. The firm can also exclude entire customers from the run.
- **One billing run at a time per tenant.** A `BillingRun` in `IN_PROGRESS` status blocks creating another. This prevents race conditions on time entry billing flags.
- **Existing `createDraft()` validation applies.** Each customer in the batch goes through the same prerequisite checks, lifecycle guards, and currency validation as a single invoice. Failures are captured in `BillingRunItem` status, not thrown.
- **Batch operations are synchronous for small batches (<= 50 customers), async with progress polling for large batches.** The threshold is configurable in OrgSettings.
- **Tax auto-application follows existing behavior.** Default tax rates are applied to invoice lines during generation, same as single-invoice flow.
- **No partial sends.** "Send batch" sends all approved invoices in the batch. If a firm wants to hold one back, they remove it from the batch (or void it) before sending.
- **Retainer period close is opt-in per billing run.** The firm checks "Include retainer invoices" and selects which retainer agreements to close.
- **Email rate limiting: 5 emails/second default** (configurable in OrgSettings). SendGrid tier limits vary; SMTP servers are typically more constrained. Batch send queues emails and processes them with a rate limiter.

---

## Section 1 — BillingRun Data Model

### BillingRun

The batch context entity — tracks a single billing cycle execution.

```
BillingRun:
  id                  UUID (PK)
  name                String (nullable, max 300) — e.g., "March 2026 Monthly Billing"
  status              String (not null) — PREVIEW, IN_PROGRESS, COMPLETED, CANCELLED
  periodFrom          LocalDate (not null) — billing period start
  periodTo            LocalDate (not null) — billing period end
  currency            String (not null, max 3) — ISO currency code (e.g., "ZAR")
  includeExpenses     Boolean (not null, default true) — include unbilled expenses
  includeRetainers    Boolean (not null, default false) — include retainer period close
  totalCustomers      Integer — number of customers included
  totalInvoices       Integer — number of invoices generated
  totalAmount         BigDecimal — sum of all invoice subtotals
  totalSent           Integer — number of invoices sent
  totalFailed         Integer — number of customers that failed generation
  createdBy           UUID (not null)
  createdAt           Timestamp
  updatedAt           Timestamp
  completedAt         Timestamp (nullable)
```

**Status transitions:**
```
PREVIEW -> IN_PROGRESS -> COMPLETED
PREVIEW -> CANCELLED
IN_PROGRESS -> COMPLETED (all items processed)
IN_PROGRESS -> CANCELLED (user aborts)
```

- `PREVIEW`: Billing run created, customer selection and entry cherry-picking in progress. No invoices generated yet.
- `IN_PROGRESS`: Invoice generation started. Items being processed.
- `COMPLETED`: All items processed (some may have failed). Summary stats populated.
- `CANCELLED`: Run aborted. Any generated drafts are voided.

### BillingRunItem

Per-customer tracking within a billing run.

```
BillingRunItem:
  id                  UUID (PK)
  billingRunId        UUID (FK to BillingRun, not null)
  customerId          UUID (FK to Customer, not null)
  status              String (not null) — PENDING, GENERATING, GENERATED, FAILED, EXCLUDED
  invoiceId           UUID (FK to Invoice, nullable) — set after successful generation
  unbilledTimeAmount  BigDecimal (nullable) — preview: total unbilled time value
  unbilledExpenseAmount BigDecimal (nullable) — preview: total unbilled expense value
  unbilledTimeCount   Integer (nullable) — number of unbilled time entries
  unbilledExpenseCount Integer (nullable) — number of unbilled expenses
  failureReason       String (nullable, max 1000) — if status = FAILED, why
  createdAt           Timestamp
  updatedAt           Timestamp

  UNIQUE(billingRunId, customerId)
```

### BillingRunEntrySelection

Tracks which time entries and expenses the user selected/deselected during preview.

```
BillingRunEntrySelection:
  id                  UUID (PK)
  billingRunItemId    UUID (FK to BillingRunItem, not null)
  entryType           String (not null) — TIME_ENTRY, EXPENSE
  entryId             UUID (not null) — FK to TimeEntry or Expense
  included            Boolean (not null, default true)

  UNIQUE(billingRunItemId, entryType, entryId)
```

**Design rationale**: Store explicit selections rather than "select all minus exclusions" because the preview data (unbilled entries) can change between preview and generation. Storing explicit inclusions ensures deterministic generation — exactly the entries the user reviewed get billed.

### OrgSettings Extension

```
OrgSettings extension:
  billingBatchAsyncThreshold  Integer (default 50) — customer count above which batch runs async
  billingEmailRateLimit       Integer (default 5) — emails per second during batch send
  defaultBillingRunCurrency   String (nullable, max 3) — pre-fill currency on new runs
```

---

## Section 2 — BillingRun Service

### BillingRunService

Orchestrates the billing run lifecycle.

```
Key methods:

createRun(name, periodFrom, periodTo, currency, includeExpenses, includeRetainers, createdBy) -> BillingRunResponse
  — Creates BillingRun in PREVIEW status
  — Validates no other run is IN_PROGRESS for this tenant
  — Returns the run with empty item list

loadPreview(billingRunId, customerIds) -> BillingRunPreviewResponse
  — For each customerId, queries unbilled time + expenses for the billing period
  — Creates BillingRunItem records in PENDING status with preview amounts
  — Auto-excludes customers that would fail prerequisite checks (marks as EXCLUDED with reason)
  — Returns per-customer summary with unbilled totals

  If customerIds is empty/null, auto-discovers all ACTIVE customers with unbilled work
  in the period (query: customers with unbilled time entries or expenses in date range).

updateEntrySelection(billingRunItemId, selections: [{entryType, entryId, included}]) -> void
  — Updates BillingRunEntrySelection records for a specific customer in the run
  — Recalculates preview totals for the item

excludeCustomer(billingRunItemId) -> void
  — Sets item status to EXCLUDED. Removes from generation queue.

includeCustomer(billingRunItemId) -> void
  — Sets item status back to PENDING. Re-includes in generation queue.

generate(billingRunId) -> BillingRunResponse
  — Transitions run to IN_PROGRESS
  — For each PENDING item:
    1. Resolve selected entries (from BillingRunEntrySelection, or all unbilled if no explicit selection)
    2. Call existing InvoiceService.createDraft() with the customer + selected entries
    3. On success: set item status to GENERATED, link invoiceId
    4. On failure: set item status to FAILED, capture failureReason
  — After all items processed: transition run to COMPLETED, compute summary stats
  — Returns updated run with per-item results

cancelRun(billingRunId) -> void
  — If PREVIEW: delete all items and the run
  — If IN_PROGRESS/COMPLETED: void all DRAFT invoices created by this run, mark run CANCELLED

batchApprove(billingRunId) -> BatchOperationResult
  — Approves all DRAFT invoices linked to the run
  — Returns success/failure per invoice

batchSend(billingRunId, defaultDueDate, defaultPaymentTerms) -> BatchOperationResult
  — Sends all APPROVED invoices linked to the run
  — Applies defaultDueDate and defaultPaymentTerms to invoices that don't have them set
  — Rate-limited email sending (OrgSettings.billingEmailRateLimit per second)
  — Returns success/failure per invoice, updates run.totalSent
```

### RetainerBatchClose

When `includeRetainers = true` on the billing run:

```
loadRetainerPreview(billingRunId) -> List<RetainerPeriodPreview>
  — Finds all retainer agreements with periods due for close within the billing period
  — Returns: agreementId, customerName, periodStart, periodEnd, consumedHours, estimatedAmount

generateRetainerInvoices(billingRunId, retainerAgreementIds) -> List<BillingRunItem>
  — Calls RetainerPeriodService.closePeriod() for each selected agreement
  — Creates BillingRunItem records linking the generated invoices to the billing run
  — These invoices then participate in batch approve/send like any other
```

---

## Section 3 — Backend API

### Billing Run API

```
POST   /api/billing-runs                                    — Create billing run (PREVIEW)
GET    /api/billing-runs                                    — List billing runs (paginated, filter: status)
GET    /api/billing-runs/{id}                               — Get billing run with summary stats
DELETE /api/billing-runs/{id}                               — Cancel billing run

POST   /api/billing-runs/{id}/preview                      — Load preview (body: customerIds or empty for auto-discover)
GET    /api/billing-runs/{id}/items                         — List billing run items with preview data
GET    /api/billing-runs/{id}/items/{itemId}                — Get single item detail (unbilled entries)
PUT    /api/billing-runs/{id}/items/{itemId}/selections     — Update entry selections for a customer
PUT    /api/billing-runs/{id}/items/{itemId}/exclude        — Exclude customer from run
PUT    /api/billing-runs/{id}/items/{itemId}/include        — Re-include customer in run

GET    /api/billing-runs/{id}/items/{itemId}/unbilled-time  — Get unbilled time entries for cherry-pick
GET    /api/billing-runs/{id}/items/{itemId}/unbilled-expenses — Get unbilled expenses for cherry-pick

POST   /api/billing-runs/{id}/generate                     — Generate draft invoices
POST   /api/billing-runs/{id}/approve                      — Batch approve all drafts
POST   /api/billing-runs/{id}/send                         — Batch send all approved (body: defaultDueDate, defaultPaymentTerms)

GET    /api/billing-runs/{id}/retainer-preview              — Preview retainer periods due for close
POST   /api/billing-runs/{id}/retainer-generate             — Generate retainer invoices (body: retainerAgreementIds)
```

### Access Control

- All billing run endpoints: `org:admin` and `org:owner` only. Batch billing is an administrative function.

### Unbilled Discovery Endpoint (New)

```
GET    /api/invoices/unbilled-summary                       — Cross-customer unbilled summary
  Query params: periodFrom, periodTo, currency (optional filter)
  Returns: List<CustomerUnbilledSummary>
    - customerId, customerName, customerEmail
    - unbilledTimeEntryCount, unbilledTimeAmount
    - unbilledExpenseCount, unbilledExpenseAmount
    - totalUnbilledAmount
    - hasPrerequisiteIssues (boolean) — would fail generation
    - prerequisiteIssueReason (string, nullable)
```

This powers the customer selection step of the billing run wizard — shows at a glance which customers have unbilled work and how much.

---

## Section 4 — Frontend: Billing Run Wizard

### Billing Runs Page (Top-Level: Invoices > Billing Runs Tab)

Add a "Billing Runs" tab to the existing Invoices page. Shows:

- **Active run banner**: If a billing run is in PREVIEW or IN_PROGRESS, show a prominent banner with "Resume Billing Run" link.
- **Run history table**: Past billing runs with columns: name, period, created date, status, customers, invoices generated, total amount, total sent. Click row -> run detail.
- **"New Billing Run" button**: Opens the wizard.

### Billing Run Wizard (Multi-Step)

**Step 1 — Configure Run**
- Name (optional, auto-generated if blank: "March 2026 Billing")
- Billing period: date range picker (default: previous calendar month)
- Currency selector (pre-filled from OrgSettings.defaultBillingRunCurrency)
- Include expenses checkbox (default: checked)
- Include retainer period close checkbox (default: unchecked)
- "Next" button creates the BillingRun in PREVIEW status

**Step 2 — Select Customers**
- Table of all active customers with unbilled work in the period (from unbilled-summary endpoint)
- Columns: checkbox, customer name, unbilled time (hours + amount), unbilled expenses (amount), total, status icon (green = ready, amber = prerequisite warning)
- "Select All" / "Deselect All" toggle
- Customers with prerequisite issues shown with warning icon + tooltip explaining why
- Search/filter by customer name
- Customer count + total amount summary bar at bottom
- "Load Preview" button triggers preview loading for selected customers

**Step 3 — Review & Cherry-Pick**
- Accordion list: one section per customer, expandable
- Each customer section shows:
  - Customer name + total unbilled amount
  - **Time entries table**: date, member, task, project, hours, rate, amount — each row has an include/exclude checkbox
  - **Expenses table** (if includeExpenses): date, description, project, amount, markup — each row has include/exclude checkbox
  - Customer subtotal (recalculates as entries are toggled)
  - "Exclude Customer" button (removes from batch entirely)
- **Retainer section** (if includeRetainers): list of retainer agreements with periods due, each with include/exclude checkbox
- Batch summary bar at bottom: total customers, total invoices to generate, total amount
- "Generate Invoices" button

**Step 4 — Review Drafts**
- Table of generated invoices: customer name, invoice number, line items count, subtotal, tax, total, status (DRAFT / FAILED)
- Failed items shown with red status + failure reason. Can retry individual failures.
- Click invoice row -> inline editor (due date, notes, payment terms, line item adjustments) in a sheet/drawer
- Quick-edit columns: due date (date picker inline), payment terms (dropdown inline)
- "Batch set due date" action: applies a due date to all invoices that don't have one
- "Batch set payment terms" action: applies payment terms to all
- Batch summary bar: total drafts, total amount, ready to approve count
- "Approve All" button

**Step 5 — Send**
- Table of approved invoices: customer name, invoice number, total, email address
- Preview of the email that will be sent (using existing invoice email template)
- "Send All" button with confirmation dialog ("Send X invoices totaling Y to Z customers?")
- Progress indicator during send (updates as emails are dispatched)
- Final summary: sent count, total amount, any send failures
- "Done" button returns to billing runs list

### Billing Run Detail Page

Accessible from the billing runs list. Shows:
- Run metadata (name, period, currency, created by, status)
- Summary stats cards: generated, approved, sent, paid, failed, total amount
- Items table with status column (color-coded)
- Actions (depending on status): Resume, Cancel, Approve Remaining, Send Remaining

---

## Section 5 — Batch Approve & Send Backend

### Batch Approve

```java
batchApprove(billingRunId) -> BatchOperationResult
```

- Loads all BillingRunItems with status GENERATED and linked invoiceId
- For each invoice in DRAFT status: calls existing `InvoiceService.approve(invoiceId, approvedBy)`
- Captures success/failure per invoice
- Returns `BatchOperationResult { successCount, failureCount, failures: [{invoiceId, reason}] }`

### Batch Send

```java
batchSend(billingRunId, defaultDueDate, defaultPaymentTerms) -> BatchOperationResult
```

- Loads all BillingRunItems with linked invoices in APPROVED status
- For invoices missing due date: applies `defaultDueDate`
- For invoices missing payment terms: applies `defaultPaymentTerms`
- Queues emails via a rate-limited executor (`ScheduledExecutorService` with token bucket or simple `Thread.sleep` between batches)
- For each invoice: calls existing `InvoiceService.send(invoiceId, sendRequest)`
- Updates `BillingRun.totalSent` as emails dispatch
- Returns `BatchOperationResult`

### Email Rate Limiting

Use a simple token-bucket approach:
- `OrgSettings.billingEmailRateLimit` = max emails per second (default 5)
- Process invoices in bursts of `rateLimit` size, sleep 1 second between bursts
- If SendGrid BYOAK is configured, respect the adapter's own rate limits
- For SMTP: lower default (2/second) to avoid rejection

---

## Section 6 — Invoice Entity Extension

### Invoice Extension

```
Invoice extension:
  billingRunId        UUID (nullable, FK to BillingRun) — set when created via batch
```

This enables:
- Filtering invoices by billing run
- "Created via billing run" indicator in the UI
- Batch operations on run-linked invoices

No changes to existing invoice behavior — `billingRunId` is informational only.

---

## Section 7 — Notifications & Audit

### Audit Events

- `BILLING_RUN_CREATED` (details: name, periodFrom, periodTo, currency, customerCount)
- `BILLING_RUN_GENERATED` (details: billingRunId, invoiceCount, totalAmount, failedCount)
- `BILLING_RUN_APPROVED` (details: billingRunId, approvedCount)
- `BILLING_RUN_SENT` (details: billingRunId, sentCount, totalAmount)
- `BILLING_RUN_CANCELLED` (details: billingRunId, voidedInvoiceCount)

### Notifications

| Event | Recipient | Channel |
|-------|-----------|---------|
| Billing run completed (generation done) | Run creator | In-app |
| Billing run sent (all emails dispatched) | Run creator | In-app |
| Billing run has failures | Run creator | In-app + email |

### Activity Feed

Billing run events appear in the org-level activity feed (not project-level, since runs span multiple projects/customers).

---

## Section 8 — Automation Integration

No new trigger types needed — existing `INVOICE_STATUS_CHANGED` fires for each invoice in the batch as it transitions through DRAFT -> APPROVED -> SENT. Automations like "when invoice is sent, notify the project lead" work automatically for batch-generated invoices.

Consider adding a condition field `generatedViaBillingRun` (boolean) to the invoice status change trigger config, so firms can create automations that only fire for batch invoices (or only for manual invoices).

---

## Out of Scope

- **Credit notes / adjustments** — separate domain with its own lifecycle (credit memo -> apply to invoice -> refund). Not batch-specific.
- **Write-offs** — marking unbilled time as non-billable in bulk ("we're eating these hours"). Useful but different from billing. Could be a quick add in a future phase.
- **Recurring invoice schedules** — "auto-generate invoice for Customer X every month." Already handled by retainer agreements + recurring schedules. Batch billing is for ad-hoc periodic billing runs.
- **Multi-currency in a single run** — a billing run has one currency. Customers with unbilled work in a different currency are excluded with a warning. Multi-currency runs add complexity for minimal gain.
- **Invoice grouping options** — "one invoice per project" vs "one invoice per customer (all projects combined)." V1 does one invoice per customer with all selected entries. Per-project splitting is v2.
- **Approval workflow** — "partner must approve invoices over R50,000 before sending." This is a general invoice approval enhancement, not batch-specific.
- **PDF pre-generation** — generating PDFs for all invoices in the batch before sending. V1 generates PDFs on-demand when viewed/downloaded, same as single invoices.

## ADR Topics

1. **Billing run as entity vs. tag**: Why a dedicated `BillingRun` entity rather than just a `batchId` tag on invoices. Decision: dedicated entity provides lifecycle management (cancel with void), preview/selection tracking, and historical reporting. A tag would lose the preview/selection state.
2. **Explicit entry selection vs. snapshot**: Why store explicit `BillingRunEntrySelection` records rather than snapshotting unbilled entries at preview time. Decision: explicit selections survive time entry changes between preview and generate. If a new time entry is logged during preview, it doesn't sneak into the batch. The user's explicit selections are what get billed.
3. **Sync vs. async batch generation**: Why the threshold approach (sync for <= 50, async for > 50) rather than always-async. Decision: sync is simpler to implement, debug, and gives immediate feedback. Most firms have < 50 active clients. Async adds complexity (polling, status tracking, failure recovery) that's only needed at scale.
4. **Email rate limiting strategy**: Why token-bucket over queue-based. Decision: billing runs are infrequent (monthly/weekly), not high-throughput. A simple sleep-between-bursts approach is sufficient. A full message queue (Redis, SQS) is over-engineering for batch sizes under 200.

## Style & Boundaries

- Follow existing entity patterns: Spring Boot entity + JpaRepository + Service + Controller
- `BillingRunService` is the primary orchestrator. It delegates to `InvoiceService.createDraft()` for individual invoice generation — does NOT duplicate invoice creation logic.
- The preview/selection system (BillingRunEntrySelection) is the novel part. The actual generation reuses existing infrastructure entirely.
- Frontend wizard should feel like a guided flow, not a complex form. Progress through steps is linear (can go back but not skip ahead). The wizard state persists server-side (BillingRun entity in PREVIEW status) so closing the browser doesn't lose work.
- Cherry-pick UI (Step 3) is the most complex frontend component. Use virtualized lists for customers with many time entries. Collapsible customer sections keep the view manageable.
- Billing run history table reuses the same DataTable component used for invoices list.
- Migration: adds billing_runs, billing_run_items, billing_run_entry_selections tables. Extends invoices with billing_run_id. Extends org_settings with batch billing config. Single tenant migration file.
- Indexes: `(billing_run_id)` on billing_run_items, `(billing_run_item_id)` on entry selections, `(billing_run_id)` on invoices.
- Test coverage: integration tests for run lifecycle (create -> preview -> generate -> approve -> send -> complete), entry selection persistence, prerequisite failure handling in batch context, batch approve/send with partial failures, email rate limiting, retainer batch close, cancel with void.
