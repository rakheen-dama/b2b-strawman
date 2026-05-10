# Invoicing

**Bounded context:** see [`10-bounded-contexts.md` § invoicing](../10-bounded-contexts.md).

## Purpose

Invoicing is Kazi's money-out flow: turn unbilled work (time, expenses, retainers, tariffs, manual lines) into a numbered, taxed, sent, paid (or voided) `Invoice`. It owns four bounded-context aggregates:

1. **`Invoice` + `InvoiceLine`** — the bill itself; lifecycle `DRAFT → APPROVED → SENT → PAID | VOID` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceStatus.java:3`. Once `APPROVED` the financial substance is locked: number allocated, lines and tax frozen.
2. **`TaxRate`** — per-tenant catalogue of rates applied to lines, with inclusive/exclusive computation and a denormalised snapshot policy on every line so historical rate changes don't rewrite past invoices (ADR-103).
3. **`BillingRun` + `BillingRunItem`** — bulk invoicing across many customers for a period: preview → cherry-pick → generate → batch-approve → batch-send. Module-gated `bulk_billing` `→ ../10-bounded-contexts.md:185`.
4. **`PaymentEvent`** — append-only ledger of payment recordings, reversals, and partial reversals against an invoice; emitted as domain events for accounting-sync consumers.

What this module does **not** own (despite physical proximity in the URL space and on dashboards):
- **Trust accounting** — separate sibling module `30-modules/trust-accounting.md`. Trust ledgers are not invoice lines; trust-flagged invoices are exportable from this module but blocked at egress by `TrustBoundaryGuard` (ADR-276; see §6).
- **Retainer agreements / periods** — sibling module `30-modules/retainers.md`. This module **consumes** `RetainerPeriod` to generate retainer invoices via `BillingRun.retainer-generate` (`InvoiceLineType.RETAINER`); the agreement state machine and consumption query (ADR-074) live in retainers.
- **Rate cards** — `BillingRate` and `CostRate` live in their own top-level packages `backend/.../billingrate/` and `backend/.../costrate/`. They are **not** owned here. See Open Questions §3 (the same gap `time-entry.md` flagged).
- **Payment integration** — `PaymentLinkService` calls into the payment port (ADR-098/100/279); the adapter itself is in `integration-ports`.

The hard line is: **once `APPROVED`, the invoice is a financial record.** Everything downstream — number, lines, tax snapshots, customer name, line-source FKs back to consumed time/expense/retainer rows — is immutable from that point. The only escape hatches are `void` (ADR-050) and partial/full payment reversal (recorded as new `PaymentEvent` rows, never as edits to old ones).

## Entities owned

### `Invoice` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/Invoice.java:24`

Table `invoices`. Key columns:
- `customerId` (NOT NULL FK→customers), `customerName`, `customerEmail`, `customerAddress` — denormalised at approval so a later customer rename/delete does not mutate a sent invoice.
- `invoiceNumber` (length 20, nullable while `DRAFT`) — allocated at `APPROVED`, format `INV-%04d` `→ invoice/InvoiceNumberService.java:49`. Per-tenant counter (ADR-048).
- `status` `InvoiceStatus` (`DRAFT, APPROVED, SENT, PAID, VOID`) `→ invoice/InvoiceStatus.java:3`.
- `currency` (3-char ISO) — fixed at draft creation; never reconciled across currencies (ADR-041).
- `issueDate`, `dueDate`, `paidAt`.
- `subtotal`, `taxAmount`, `total` (BigDecimal 14,2). Recomputed on every line CRUD while `DRAFT`; frozen on `APPROVED`.
- `paymentReference`, `paymentTerms`, `notes`.
- `orgName` — denormalised tenant brand line for header rendering.
- `createdBy`, `approvedBy`, `createdAt`, `updatedAt`.
- `customFields` (jsonb) — entity-typed custom fields; `appliedFieldGroups` resolved via `fielddefinition`.

### `InvoiceLine` `→ invoice/InvoiceLine.java:19`

Table `invoice_lines`. Key columns:
- `invoiceId` (NOT NULL FK→invoices).
- `lineType` `InvoiceLineType` (`TIME, EXPENSE, RETAINER, MANUAL, FIXED_FEE, TARIFF, DISBURSEMENT`) `→ invoice/InvoiceLineType.java:4` — discriminator per ADR-118 (and ADR-211, ADR-247 per file header).
- `lineSource` (string, length 20) — soft discriminator paired with the typed FK columns.
- `timeEntryId, retainerPeriodId, expenseId, tariffItemId, disbursementId` — sibling FKs (each nullable; exactly one set for non-MANUAL lines). The `tariffItemId` and `disbursementId` columns are legal-vertical-only sources; column presence is universal but population is profile-conditional.
- `projectId` (nullable) — line-level project attribution; needed for project profitability rollups even when the invoice spans projects.
- `description, quantity, unitPrice, amount, sortOrder`.
- **Tax snapshot fields** (`taxRateId, taxRateName, taxRatePercent, taxAmount, taxExempt`) `→ invoice/InvoiceLine.java:70-83` — the immutability of historical tax (ADR-103) is implemented here: every line carries its own copy of the rate name + percent at the moment it was last edited in `DRAFT`. Rewriting `tax_rates` later does not retroactively change this row.

`InvoiceLineType.TARIFF` and `DISBURSEMENT` are legal-vertical line sources; they exist in the universal enum so any tenant that imports legal seed data has the discriminator available, but only legal tenants emit them via the `Disbursement` and `TariffItem` flows.

### `TaxRate` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/tax/TaxRate.java:19`

Table `tax_rates`. Per-tenant rate catalogue:
- `name, rate (5,2), isDefault, isExempt, active, sortOrder`.
- Mutation contract: editable while active (`update(...)` on the entity `→ tax/TaxRate.java:73`); deactivation clears the default flag `→ tax/TaxRate.java:89`. Mutation does **not** retroactively re-tax existing invoice lines because lines carry their own snapshot (ADR-103).
- `TaxType` is a separate enum on the *invoice* level (`VAT, GST, SALES_TAX, NONE`) `→ invoice/TaxType.java:4` — disambiguates jurisdictional naming (a SA tenant displays "VAT", an AU tenant "GST"). Tax computation does not branch on `TaxType`; it is presentation metadata.

`TaxCalculationService` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/tax/TaxCalculationService.java:32` implements both inclusive (extract tax from a tax-inclusive line amount) and exclusive (add tax on top) modes per ADR-101 / ADR-102, and produces a `TaxBreakdownEntry` list grouped by `(rateName, ratePercent)` for the invoice footer.

### `BillingRun` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRun.java:18`

Table `billing_runs`. Key columns:
- `name, status, periodFrom, periodTo, currency, includeExpenses, includeRetainers`.
- `BillingRunStatus` (`PREVIEW, IN_PROGRESS, COMPLETED, CANCELLED`) `→ billingrun/BillingRunStatus.java:3`.
- Aggregate counters `totalCustomers, totalInvoices, totalAmount, totalSent, totalFailed`.

### `BillingRunItem` `→ billingrun/BillingRunItem.java`

Per-customer slot inside a run. State machine `BillingRunItemStatus` (`PENDING, GENERATING, GENERATED, FAILED, EXCLUDED, CANCELLED`) `→ billingrun/BillingRunItemStatus.java:3`. The item is the granular cherry-pick unit — a user excludes one customer from a run without re-running the period query (ADR-158).

### `BillingRunEntrySelection` `→ billingrun/BillingRunEntrySelection.java`

Per-entry inclusion table. ADR-158 chose **explicit selection** over **snapshot copy**: rather than freezing a snapshot of which time/expense rows were in the period at preview time, the selection table stores `(billingRunItemId, entryType, entryId, included=true|false)` so that edits to underlying rows after preview are picked up at generate. `EntryType` `→ billingrun/EntryType.java` discriminates time/expense/disbursement.

### `PaymentEvent` `→ invoice/PaymentEvent.java`

Append-only payment ledger keyed to an invoice. `PaymentEventStatus` `→ invoice/PaymentEventStatus.java` covers initial recording, reversal, partial reversal. Surfaced via `GET /api/invoices/{id}/payment-events`.

### `InvoiceCounter` `→ invoice/InvoiceCounter.java:17`

Single-row-per-tenant table `invoice_counters` with a `singleton` constraint. Number allocation uses `INSERT … ON CONFLICT … DO UPDATE SET next_number = invoice_counters.next_number + 1 RETURNING next_number` `→ invoice/InvoiceNumberService.java:42-45` — atomic increment under contention without explicit locking. Per-tenant counter; never gapped by void (ADR-048 trade-off — sequential, gap-free, per-tenant).

## REST surface

Three families: `/api/invoices/*`, `/api/billing-runs/*`, `/api/tax-rates/*`. All write paths are gated by `@RequiresCapability("INVOICING")`. Tax-rate writes additionally require `FINANCIAL_VISIBILITY` (settings-tier permission).

### `/api/invoices/*` — InvoiceController `→ invoice/InvoiceController.java:39`

| Verb + path | Anchor | Notes |
|---|---|---|
| `POST /api/invoices` | `→ invoice/InvoiceController.java:50` | Create draft. Capability `INVOICING`. |
| `GET /api/invoices` | `→ invoice/InvoiceController.java:95` | Filter `customerId`, `status`, `projectId`. |
| `GET /api/invoices/{id}` | `→ invoice/InvoiceController.java:73` | |
| `PUT /api/invoices/{id}` | `→ invoice/InvoiceController.java:59` | Edit draft only — service throws `InvalidStateException` if not `DRAFT`. |
| `DELETE /api/invoices/{id}` | `→ invoice/InvoiceController.java:66` | Draft only. Hard delete; sets `timeEntry.invoiceId = null` for any consumed entries `→ InvoiceCreationService.java:400`. |
| `GET /api/invoices/{id}/preview` | `→ invoice/InvoiceController.java:79` | HTML render via `InvoiceRenderingService`. |
| `GET /api/invoices/unbilled-summary` | `→ invoice/InvoiceController.java:86` | Per-customer unbilled totals for a period; powers the billing-run preview seed. |
| `PUT /api/invoices/{id}/custom-fields` | `→ invoice/InvoiceController.java:106` | |
| `PUT /api/invoices/{id}/field-groups` | `→ invoice/InvoiceController.java:113` | |
| `POST /api/invoices/{id}/lines` | `→ invoice/InvoiceController.java:122` | Add manual/fixed-fee line. Draft only. |
| `POST /api/invoices/{id}/disbursement-lines` | `→ invoice/InvoiceController.java:130` | Legal-vertical: append disbursement IDs as `DISBURSEMENT` lines. |
| `PUT /api/invoices/{id}/lines/{lineId}` | `→ invoice/InvoiceController.java:138` | Draft only. |
| `DELETE /api/invoices/{id}/lines/{lineId}` | `→ invoice/InvoiceController.java:147` | Draft only. |
| `POST /api/invoices/validate-generation` | `→ invoice/InvoiceController.java:156` | Pre-flight check: returns `List<ValidationCheck>` (customer prerequisites, billable entries available, template resolvable). Used by the create-invoice wizard before the user commits. |
| `POST /api/invoices/{id}/approve` | `→ invoice/InvoiceController.java:167` | Allocates `invoiceNumber`, freezes lines, sets `approvedBy`. Emits `InvoiceApprovedEvent`. |
| `POST /api/invoices/{id}/send` | `→ invoice/InvoiceController.java:174` | Email + (optionally) payment link. AFTER_COMMIT email dispatch (§6). Emits `InvoiceSentEvent`. |
| `POST /api/invoices/{id}/payment` | `→ invoice/InvoiceController.java:181` | Record payment. Optional `paymentReference`. Emits `InvoicePaidEvent` (full) or stays open (partial). |
| `GET /api/invoices/{id}/payment-events` | `→ invoice/InvoiceController.java:189` | Append-only payment ledger. |
| `POST /api/invoices/{id}/refresh-payment-link` | `→ invoice/InvoiceController.java:195` | Re-mint the payment link via PSP adapter (ADR-100). |
| `POST /api/invoices/{id}/void` | `→ invoice/InvoiceController.java:201` | Set `VOID`, clear `timeEntry.invoiceId` on every linked row `→ InvoiceCreationService.java:635`. Emits `InvoiceVoidedEvent`. |

`A1-backend-map` reports "~18 endpoints" `→ ../_discovery/A1-backend-map.md:391`. The exact count above is 20 (InvoiceController + the implicit `/{id}/{lines,custom-fields,…}` paths).

### `/api/billing-runs/*` — BillingRunController `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/billingrun/BillingRunController.java:37`

| Verb + path | Anchor | Notes |
|---|---|---|
| `POST /api/billing-runs` | `:45` | Create + auto-preview. Body: `periodFrom, periodTo, currency, includeExpenses, includeRetainers`. |
| `GET /api/billing-runs` | `:54` | Page; filter `status` (multi). |
| `GET /api/billing-runs/{id}` | `:61` | |
| `DELETE /api/billing-runs/{id}` | `:67` | Cancel run (semantic: not destructive). |
| `POST /api/billing-runs/{id}/generate` | `:75` | Sync batch invoice creation per ADR-159 (chosen sync over async; small-N tenant scale). |
| `POST /api/billing-runs/{id}/preview` | `:82` | (Re)load preview — accepts `LoadPreviewRequest` for filters. |
| `GET /api/billing-runs/{id}/items` | `:89` | Per-customer items. |
| `GET /api/billing-runs/{id}/items/{itemId}` | `:95` | |
| `GET /api/billing-runs/{id}/items/{itemId}/unbilled-time` | `:102` | Drill-in. |
| `GET /api/billing-runs/{id}/items/{itemId}/unbilled-expenses` | `:109` | |
| `GET /api/billing-runs/{id}/items/{itemId}/unbilled-disbursements` | `:116` | Legal-vertical. |
| `PUT /api/billing-runs/{id}/items/{itemId}/selections` | `:123` | Cherry-pick entries. ADR-158. |
| `PUT /api/billing-runs/{id}/items/{itemId}/exclude` | `:132` | Toggle off. |
| `PUT /api/billing-runs/{id}/items/{itemId}/include` | `:139` | Toggle on. |
| `POST /api/billing-runs/{id}/approve` | `:146` | Batch-approve all `GENERATED` invoices. Returns `BatchOperationResult{success, failure}`. |
| `POST /api/billing-runs/{id}/send` | `:153` | Batch-send. AFTER_COMMIT dispatch per invoice. |
| `GET /api/billing-runs/{id}/retainer-preview` | `:161` | Eligible retainer periods. |
| `POST /api/billing-runs/{id}/retainer-generate` | `:167` | Generate retainer invoices for selected `RetainerPeriod` IDs. |

A1 reports "~14"; the actual count is 18. The seam to retainers is `retainer-preview` + `retainer-generate` — those two endpoints are the only place a `RetainerPeriod → InvoiceLine` materialisation happens at the controller layer.

### `/api/tax-rates/*` — TaxRateController `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/tax/TaxRateController.java:24`

| Verb + path | Anchor | Notes |
|---|---|---|
| `GET /api/tax-rates` | `:34` | All authenticated members; `includeInactive` query flag — needed because historical line snapshots reference deactivated rate IDs `→ tax/TaxRateController.java:32-33`. |
| `POST /api/tax-rates` | `:40` | Capability `FINANCIAL_VISIBILITY` (note: not `INVOICING` — tax-rate management is a settings-tier action). |
| `PUT /api/tax-rates/{id}` | `:48` | `FINANCIAL_VISIBILITY`. |
| `DELETE /api/tax-rates/{id}` | `:55` | `FINANCIAL_VISIBILITY`. Soft-deactivate via `TaxRate.deactivate()` `→ tax/TaxRate.java:89` — not a hard delete; line snapshots keep referring by ID. |

Four endpoints, not five. `A1-backend-map` reports 5 `→ ../_discovery/A1-backend-map.md:408`; the actual is 4 — A1 is one off.

## Frontend pages / components

- **`/org/[slug]/invoices/page.tsx`** — Invoice list `→ ../_discovery/A2-frontend-map.md:128`. Status summary cards, filter by `customerId`/`status`/`projectId`. Server actions in `actions.ts` `→ ../_discovery/A2-frontend-map.md:129`.
- **`/org/[slug]/invoices/[id]/page.tsx`** — Invoice detail: lines, tax breakdown, payment events, PDF preview iframe, action buttons (`Approve`, `Send`, `Record Payment`, `Void`, `Refresh Link`) `→ ../_discovery/A2-frontend-map.md:135-136`.
- **`/org/[slug]/invoices/billing-runs/page.tsx`** — Batch billing list. **Module-gated `bulk_billing`** `→ ../_discovery/A2-frontend-map.md:130`, `→ ../_discovery/A2-frontend-map.md:484`.
- **`/org/[slug]/invoices/billing-runs/[id]/page.tsx`** — Run detail: per-customer cherry-pick, drill-in panels for unbilled time/expenses/disbursements, batch-approve / batch-send action bar `→ ../_discovery/A2-frontend-map.md:132-134`.
- **`/org/[slug]/settings/tax/page.tsx`** — Tax registration number, tax label (UI-shown name), inclusive flag, rate CRUD `→ ../_discovery/A2-frontend-map.md:208-209`.
- **`/org/[slug]/settings/rates/page.tsx`** — Billing rates, cost rates, default currency `→ ../_discovery/A2-frontend-map.md:206-207`. **The page lives under `/settings/`** but the rate-card *entities* live under their own backend packages — see Open Questions §3 (the cross-link `time-entry.md` already flagged).
- **Components:** `frontend/components/invoices/` — `StatusBadge`, `CreateInvoiceButton`, invoice line editor `→ ../_discovery/A2-frontend-map.md:434`. Types: `frontend/lib/types/invoice.ts` (`InvoiceResponse`, `InvoiceStatus`, `InvoiceLineType`, `UnbilledTimeResponse`, `UnbilledProjectGroup`, `UnbilledDisbursementEntry`) `→ ../_discovery/A2-frontend-map.md:341-344`.
- **Vertical labelling:** the route `/invoices` and the page title both come through `TerminologyProvider`. In `legal-za`, "Invoice" → "Fee Note" `→ ../_discovery/A2-frontend-map.md:465`. The route slug stays `/invoices` (URLs are not retranslated).

The portal-side mirror is `PortalInvoiceController` (`/portal/invoices`, `/{id}`, `/{id}/pdf`) `→ ../_discovery/A1-backend-map.md:425` — listed here only as a cross-reference; the portal is its own bounded context (`30-modules/customer-portal.md`).

## Domain events

Six invoice events + one cross-cutting `BudgetThresholdEvent`. All emitted from `InvoiceTransitionService` `→ backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/InvoiceTransitionService.java`:

| Event | Emit site | Trigger |
|---|---|---|
| `InvoiceApprovedEvent` | `InvoiceTransitionService.java:188` | `POST /{id}/approve` succeeds. Number allocated, lines frozen. |
| `InvoiceSentEvent` | `InvoiceTransitionService.java:242` | `POST /{id}/send` succeeds (status set to `SENT`). Email is dispatched on `AFTER_COMMIT` so a rolled-back send fires no email (§6). |
| `InvoicePaidEvent` | `InvoiceTransitionService.java:371`, `:467` | Manual record-payment OR PSP webhook reconciliation flips invoice to `PAID`. Two emit sites because two code paths converge on the same terminal state. |
| `InvoiceVoidedEvent` | `InvoiceTransitionService.java:809` | `POST /{id}/void`. Triggers cleanup of consumed entry FKs (timeEntry.invoiceId = null). |
| `InvoicePaymentReversedEvent` | `InvoiceTransitionService.java:619` | Full payment reversal. Returns invoice to pre-paid state but **never** mutates prior `PaymentEvent` rows. |
| `InvoicePaymentPartiallyReversedEvent` | `InvoiceTransitionService.java:645` (approx) | Partial reversal. Invoice stays `PAID` if balance is zero or returns to `SENT` otherwise. |
| `BudgetThresholdEvent` | `InvoiceService` (per A1) `→ ../_discovery/A1-backend-map.md:467` | Budget tracking — emitted when an approval pushes a project past a budget threshold. Cross-cutting: notifications subscribes for warning emails. |

A1 enumerates the same six invoice events `→ ../_discovery/A1-backend-map.md:458` and the budget threshold separately at line 467. The bounded-contexts entry summarises as "6 invoice events + BudgetThresholdEvent" `→ ../10-bounded-contexts.md:188`.

**Subscribers** (consumers of these events):
- `automation/AutomationEventListener` — universal, drives rule matching `→ ../_discovery/A1-backend-map.md:474`.
- `notification/NotificationService` — subscribes to `InvoiceApprovedEvent`, `InvoiceSentEvent`, `InvoicePaidEvent`, `InvoiceVoidedEvent`, `BudgetThresholdEvent` `→ ../_discovery/A1-backend-map.md:475`. AFTER_COMMIT.
- `portal` read-model listeners — sync `InvoiceSentEvent` and `InvoicePaidEvent` into the portal-facing read model `→ ../_discovery/A1-backend-map.md:476`. AFTER_COMMIT.
- Phase 71 accounting-sync queue listens to `InvoiceApprovedEvent` and pushes to Xero, gated by `TrustBoundaryGuard` (ADR-276; see §6).

The `BudgetThresholdEvent` is the only cross-domain emit from `InvoiceService` proper — the rest are bookkeeping events from `InvoiceTransitionService`.

## Cross-cutting touchpoints

### Capability gates

Every invoice and billing-run write is gated by `@RequiresCapability("INVOICING")` `→ ../_discovery/A6-cross-cutting.md:112`. Tax-rate writes use `FINANCIAL_VISIBILITY` (a strictly narrower capability — settings-tier `→ tax/TaxRateController.java:41,49,56`). The list-tax-rates GET is open to all authenticated members because invoice detail pages need it to render historical rate names on snapshotted lines `→ tax/TaxRateController.java:32-33`.

`FINANCIAL_VISIBILITY` is also what guards profitability/reporting reads, which makes "view tax rates as a member" interestingly broader than "edit tax rates as an admin" — historical names are not financially sensitive, but rate edits are.

### Audit on every transition

Every state-machine step (`approve`, `send`, `record-payment`, `void`, `payment-reverse`, line CRUD while DRAFT) writes an `AuditEvent` row in the same transaction `→ ../_discovery/A6-cross-cutting.md:327`. The audit row shares the source transaction (not the event bus) so a rolled-back transition cannot leave a stale audit `→ ../10-bounded-contexts.md:451`. `AuditDeltaBuilder` computes field-level diffs for line edits `→ ../_discovery/A1-backend-map.md:537`.

### AFTER_COMMIT semantics for invoice email

`PortalEmailNotificationChannel` registers four `@TransactionalEventListener(AFTER_COMMIT)` handlers, one of which is the invoice handler `→ ../_discovery/A6-cross-cutting.md:329`. Reasoning is the standard one: **email is irreversible** — if the source transaction rolls back after the email goes out, the customer has already received the invoice `→ ../_discovery/A6-cross-cutting.md:336`. AFTER_COMMIT eliminates that class of bug for both individual `InvoiceSentEvent` dispatch and the batch-send flow inside a billing run.

### Invoice-numbering strategy (ADR-048)

Per-tenant counter, format `INV-%04d`, gap-free (numbers are not reissued on void) `→ invoice/InvoiceNumberService.java:49`. Atomic increment via `INSERT … ON CONFLICT … RETURNING` on a single-row table with a `singleton` constraint `→ invoice/InvoiceNumberService.java:42-45`. Per-tenant scope is implicit in the schema-per-tenant model — each tenant's `invoice_counters` table is independent, no cross-tenant coordination needed. ADR-048 is the choice point; ADR-050 (double-billing-prevention) is its complement on the consumption side.

### Tax computation with inclusive flag (ADR-101 / ADR-102 / ADR-103)

- **ADR-101 (calculation strategy):** per-line tax computed at `quantity × unitPrice`, then summed; the breakdown groups by `(rateName, ratePercent)`.
- **ADR-102 (inclusive total display):** when the tenant flags tax-inclusive (settings/tax page), the line `amount` is treated as already including tax and the rate is *extracted* `→ tax/TaxCalculationService.java:32-34`. Otherwise tax is *added*. Both modes share the same `TaxBreakdownEntry` output shape so the renderer doesn't branch.
- **ADR-103 (rate immutability):** every `InvoiceLine` carries `taxRateName, taxRatePercent, taxAmount, taxExempt` as immutable snapshots `→ invoice/InvoiceLine.java:73-83`. Mutating or deactivating a `TaxRate` does not retroactively re-tax a sent invoice. This is why `GET /api/tax-rates?includeInactive=true` exists — UI rendering needs the historical name.

### Double-billing prevention (ADR-050)

`TimeEntry.invoiceId`, `Expense.invoiceId`, and `Disbursement.invoiceId` are nullable FKs *set by* `InvoiceCreationService.markBilled` `→ invoice/InvoiceCreationService.java:917` (also documented in `time-entry.md:89`). Once non-null, the entry is filtered out of every "unbilled" query. Void clears the FKs back to null `→ invoice/InvoiceCreationService.java:636`; delete-draft also clears them `→ :400`. The invariant: a single source row contributes at most one non-voided `InvoiceLine`. `ADR-050` codifies this; the load-bearing line is the **DB-level** filter on `invoiceId IS NULL` in every unbilled-summary query rather than any application-level lock.

### Trust-accounting hard guard (ADR-276)

`TrustBoundaryGuard.evaluate(invoice)` runs **inside the Phase 71 accounting-sync service**, not on `/api/invoices/*` `→ ../_discovery/A6-cross-cutting.md:311`. The guard refuses (fail-closed) if any of three conditions hold: invoice is flagged trust-related, any line item draws from a trust account, or the customer has active trust balances `→ architecture/phase71-xero-accounting-integration.md:793-799`. On refusal, the sync queue records `state = BLOCKED_TRUST_BOUNDARY`, audit event `integration.xero.push_blocked_trust` is emitted, and no Xero call is made. **This module is therefore the producer of trust-flagged invoices but not the enforcer of the export boundary** — the legal vertical's whole point is that you *can* invoice against trust money in Kazi; you just cannot leak that detail to a downstream accounting system. This is the third defense on top of the module gate (`trust_accounting`) and the `MANAGE_TRUST` capability `→ ../_discovery/A6-cross-cutting.md:311`.

### Schedulers

No invoice-owned scheduler. The closest adjacent jobs:
- `SubscriptionEnforcementScheduler` (in `billing/`, *platform* subscriptions, not customer invoices) — unrelated namespace collision.
- Phase 71 payment-poll scheduler (ADR-277) — pulls payment status from Xero/PSP and feeds `recordPayment` / payment-event reconciliation. Lives in the accounting-sync service, not here.

### Money + currency invariants (ADR-041)

Every `Invoice` carries a single `currency`; we never reconcile across currencies. Multi-currency tenants must produce one invoice per currency. Line `amount`, invoice `subtotal/taxAmount/total`, and tax breakdown all share that currency. Cross-checked at `BillingRun.currency` — a run is currency-scoped, so a multi-currency tenant runs the bulk-billing flow once per currency.

## Vertical specifics

**Terminology overlay (frontend-only):**

| Profile | "Invoice" → | "Rate Card" → | Notes |
|---|---|---|---|
| `legal-za` | "Fee Note" `→ ../glossary.md:129` | "Tariff Schedule" `→ ../_discovery/A2-frontend-map.md:465`, `→ ../glossary.md:313` | **Conflicts** with the legal-vertical `TariffSchedule` *entity* (`backend/.../verticals/legal/tariff/TariffSchedule.java`) — see Open Questions §4. |
| `accounting-za` | "Invoice" | "Fee Schedule" `→ ../glossary.md:130` | |
| `consulting-za` | "Invoice" | "Billing Rates" | |
| `consulting-generic` | "Invoice" | "Billing Rates" | |

**Conditionally-present line types:**
- `InvoiceLineType.TARIFF` — populated only by legal-vertical flows that materialise a `TariffItem` into an invoice line. The enum value is universal; population is profile-conditional.
- `InvoiceLineType.DISBURSEMENT` — same shape; the legal `Disbursement` entity (`backend/.../verticals/legal/disbursement/`) is the source. Non-legal tenants surface "Disbursement" as a frontend label override of "Expense" `→ ../glossary.md:104` but the line-type column remains `EXPENSE` for them.

**Module-gating:**
- `bulk_billing` module gates the billing-run UI `→ ../_discovery/A2-frontend-map.md:484`. Backend endpoints for `BillingRun` are not module-self-defended (no `verticalModuleGuard.requireModule("bulk_billing")` in `BillingRunController`); enforcement is frontend-nav + page-server gate. This is a softer gate than `trust_accounting` — bulk-billing is accessible to any tenant whose admin enables the flag, vs trust which is profile-locked.
- `lssa_tariff` (legal-only) gates the tariff-schedule browser at `/legal/tariffs`; it is upstream of `InvoiceLineType.TARIFF` lines but does not appear in invoicing's surface.

**Capabilities are universal but only granted in legal roles:** `MANAGE_TRUST`, `APPROVE_TRUST_PAYMENT`, `VIEW_TRUST` exist in the `Capability` enum across every tenant `→ ../_discovery/A6-cross-cutting.md:112`. They are referenced by the trust hard guard but have no authorisation surface inside invoicing itself.

## Active ADRs

**Numbering & lifecycle:**
- **ADR-048** — invoice-numbering-strategy (per-tenant, gap-free, `INV-%04d`).
- **ADR-049** — line-item-granularity (one line per source entry — time, expense, disbursement; not pre-aggregated by member/day).
- **ADR-050** — double-billing-prevention (FK-set-once invariant on consumed entries).
- **ADR-118** — invoice-line-type-discriminator.
- **ADR-126** — milestone-invoice-creation-strategy (how fixed-fee project milestones become invoice lines).
- **ADR-129** — fee-model-architecture (proposal `feeModel` carries through to invoice generation).
- **ADR-223** — billing-method-separate-dimension (orthogonal to fee model — TIME_AND_MATERIALS / FIXED / RETAINER).

**Tax:**
- **ADR-101** — tax-calculation-strategy (per-line-then-sum; group-by for breakdown).
- **ADR-102** — tax-inclusive-total-display (extract vs add).
- **ADR-103** — tax-rate-immutability (denormalised line snapshots).

**Currency:**
- **ADR-041** — multi-currency-store-in-original (no FX reconciliation).

**Profitability cross-link:**
- **ADR-043** — margin-aware-profitability (consumes `costRateSnapshot` and invoice totals).

**Bulk billing:**
- **ADR-157** — billing-run-entity-vs-tag (chose dedicated entity over a tag-on-invoices model).
- **ADR-158** — explicit-entry-selection-vs-snapshot (selections are live FKs, not frozen copies).
- **ADR-159** — sync-vs-async-batch-generation (chose sync — small N, simpler error semantics).

**Retainer integration:**
- **ADR-072** — admin-triggered-period-close (period close is admin action that becomes an invoice line).
- **ADR-073** — standard-billing-rate-for-overage (overage on a retainer is invoiced at the standard billing rate, not the retainer rate).
- **ADR-074** — query-based-consumption (retainer consumption is a query against `time_entries`, not a counter — same source-of-truth as invoicing's unbilled-summary).
- **ADR-075** — one-active-retainer-per-customer.

**Payment integration (cross-link to integration-ports):**
- **ADR-098** — payment-gateway-interface-design.
- **ADR-099** — webhook-tenant-identification-payments.
- **ADR-100** — payment-link-lifecycle.
- **ADR-220** — platform-vs-tenant-payfast-integration (platform PayFast is for subscriptions; tenant PayFast is for customer invoices — different stacks).
- **ADR-279** — sibling-payment-source-port (Phase 71 — payment port sits beside ADR-098, not inside accounting-sync).

**Phase 71 accounting integration:**
- **ADR-272** — xero-only-accounting-adapter-v1.
- **ADR-273** — one-way-accounting-sync-permanent (Kazi → Xero, never read-back).
- **ADR-274** — dedicated-accounting-sync-service-not-rule-engine.
- **ADR-275** — oauth2-augmentation-org-integration.
- **ADR-276** — trust-accounting-hard-guard-export (the load-bearing one — see §6).
- **ADR-277** — poll-over-webhooks-payment-reconciliation-v1.
- **ADR-278** — idempotent-push-via-external-reference (Xero side stores Kazi invoice ID; pushes are idempotent).

ADR index `→ ../90-adr-index.md:80-107`, Phase 71 cluster `→ ../90-adr-index.md:346-389`.

## Key flows

- **`50-flows/matter-to-cash.md`** *(to be written)* — the central long flow: customer onboarded → project created → tasks claimed → time logged with rate snapshot (`time-entry.md:120`) → `BillingRun` previews the period → cherry-picks per customer → `generate` materialises `Invoice` rows, calls `markBilled` on consumed entries → batch-approve allocates numbers and emits `InvoiceApprovedEvent` → batch-send dispatches email AFTER_COMMIT → PSP webhook reconciles to `recordPayment` → `InvoicePaidEvent`.
- **`50-flows/payment-receipt-to-trust-allocation.md`** *(to be written)* — legal-vertical specialisation: customer payment received against a trust-flagged invoice → `recordPayment` writes the `PaymentEvent` → trust-accounting module allocates the payment to the client's trust ledger (separate sibling module). The hand-off point is the `InvoicePaidEvent`; trust-accounting is the consumer, not invoicing.

Both flows are currently outlined in this document only by reference to their constituent steps — the `50-flows/` pages will sequence them with anchors to controllers + services.

## Open questions / known fragility

1. **Three coexisting `BillingStatus` enums** — same divergence `time-entry.md` flagged. `timeentry/BillingStatus.java:13` (`UNBILLED, BILLED, NON_BILLABLE`), `expense/ExpenseBillingStatus.java:4`, `verticals/legal/disbursement/DisbursementBillingStatus.java:9` `→ ../glossary.md:62, :106, :124, :332`. They share a name and intent but diverge in values and are not interchangeable. Any unbilled-summary aggregator (this module's `GET /api/invoices/unbilled-summary` and the billing-run preview) must enumerate all three. Consolidation candidate, but each enum lives in a different aggregate so the merge isn't free. Cross-link: `time-entry.md:125`.

2. **Trust-flagged invoice flow boundary is in two places.** Trust invoices can be created and approved through this module's standard flow (the legal vertical's whole point). The hard guard runs *outside* this module — inside the Phase 71 accounting-sync service `→ ../_discovery/A6-cross-cutting.md:311`. So the module that *produces* the trust-flagged invoice is not the module that *enforces* the trust export boundary. Reasonable separation (single-responsibility) but worth flagging because someone reading "what stops a trust invoice leaking to Xero?" finds the answer in `trust-accounting.md` + the accounting-sync code, not here. The cross-link to `30-modules/trust-accounting.md` is the canonical home; this section is a forwarding pointer.

3. **Rate-card ownership is unassigned (same gap `time-entry.md` flagged at §1).** `BillingRate` and `CostRate` live in their own top-level packages — `backend/.../billingrate/BillingRate.java:16` (table `billing_rates`) and `backend/.../costrate/CostRate.java:16` (table `cost_rates`) — each with its own controller, service, and repository. Neither lives in `invoice/`, `tax/`, `billingrun/`, or `settings/`. The `/api/billing-rates` and `/api/cost-rates` URL families are not surfaced in any §3 module table; A1's REST table has no row for them `→ ../_discovery/A1-backend-map.md:386`. The `/settings/rates` frontend page is a co-location, not a backend-ownership claim. Three plausible homes: (a) attach to **invoicing** because the rates are consumed at `markBilled` time and at line-level pricing; (b) attach to **time-entry** because that's where the snapshot is taken; (c) attach to **settings-navigation** because the page lives there. None is currently chosen. A new `30-modules/billing-rates.md` page is the cleanest resolution. **Picking arbitrarily would entrench a wrong choice** — flagging here per `time-entry.md:124` and not deciding.

4. **"Tariff Schedule" is overloaded between vertical entity and UI label.** In `legal-za`, the frontend label for `BillingRate` is "Tariff Schedule" `→ ../_discovery/A2-frontend-map.md:465`, `→ ../glossary.md:313`. But `TariffSchedule` is *also* a backend entity in `backend/.../verticals/legal/tariff/TariffSchedule.java:19` representing LSSA court-tariff schedules — a completely different concept (LSSA tariffs are statutory court-fee tables; `BillingRate` is the firm's own hourly rate card). The glossary calls this "Divergence #7" `→ ../glossary.md:337`. The risk: a new contributor reading "Tariff Schedule" in a UI screenshot and grepping the codebase will find the legal-vertical entity, not the rate-card it's labelling. Documentation hazard, not a bug, but the overloaded term has surfaced more than once.

5. **A1 endpoint counts are slightly off.** A1 reports `/api/invoices` "~18", `/api/billing-runs` "~14", `/api/tax-rates` "5" `→ ../_discovery/A1-backend-map.md:391-408`. The actual counts (above) are 20, 18, 4. Not a fragility — A1 explicitly says "approximate" — flagging here so future module-page authors know to count from the controller, not from A1.

6. **`refresh-payment-link` semantics are not in any ADR.** The endpoint `POST /api/invoices/{id}/refresh-payment-link` `→ invoice/InvoiceController.java:195` exists for a real reason — payment-link expiry / PSP rotation — but ADR-100 (payment-link-lifecycle) does not specifically cover the refresh path. If the link refresh produces a new external reference, the idempotency contract in ADR-278 (idempotent push via external reference) needs to know what the canonical reference is post-refresh. Worth documenting in either ADR-100 follow-up or an addendum.

7. **`PaymentReconciliationService` is co-located with this module but consumed from elsewhere.** `invoice/PaymentReconciliationService.java` exists in this module's package but is invoked by the Phase 71 payment-poll scheduler (per ADR-277). The split is "domain rules in invoicing, scheduling outside" which is fine, but the code-locality suggests ownership ambiguity. Confirm at Phase 71 final cut whether this class moves to the accounting-sync service or stays here as a domain-rule library.
