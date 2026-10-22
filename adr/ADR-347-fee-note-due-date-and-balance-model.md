# ADR-347: Fee-Note Due Date Stamped at Approve from Firm Payment Terms; Persisted `amount_paid` Balance Model with `PARTIALLY_PAID`; Every Payment Source Posts Its Actual Amount

**Status**: Accepted

**Context**:

Two capstone clusters share the fee-note lifecycle. **Due date (G-D11 b, G-D21 Debtors, G-C7 c):** no code path computes `invoices.due_date` outside batch billing — `Invoice.applyDefaults` has a single caller, `BillingRunGenerationService:327` (R2 §1 Q2). `approve` sets `issueDate` only; `send` validates five checks and none is a due date; the walk's UI path (generate from unbilled → approve → send) never sends one. No firm payment-terms setting exists (`BillingSettings` has three unrelated fields). Meanwhile the debtor rule `status = 'SENT' AND due_date IS NOT NULL` is the definition in `CollectionsReadService:81-82,103-104,120-121`, `ClientListRowRepository:54-55`, `CashDigestService:104`, `InvoiceAgingReportQuery:76` and `CollectionsScanService:59`, while the Fee notes page "Total Outstanding" is a client-side sum over APPROVED+SENT with no due-date predicate (`invoices/page.tsx:32-39`) — the two surfaces disagree by construction.

**Payments (G-C11, N-5):** `RecordPaymentRequest` carries only `paymentReference`; `InvoiceTransitionService.recordPayment:311-370` always posts `invoice.getTotal()`; `InvoiceStatus` is `DRAFT/APPROVED/SENT/PAID/VOID`, enforced by the DB `chk_invoice_status` CHECK (`tenant/V1__baseline.sql:1369`, `.arch-context.md` §10.2); `payment_events` stores per-event amounts that nothing aggregates (R6 §5). Trust fee-transfer approval (`TrustTransactionService:866`), the pay-now webhook (`PaymentReconciliationService:103`) and Xero sync (`AccountingSyncService:441`) all mark PAID regardless of amount. Founder decisions D2 and D8 (`tasks/phase88-gap-register.md` §4) chose a firm setting with default 30 stamped at approve, backfill, and full part payments.

**Options Considered**:

*Due date*

1. **`COALESCE(due_date, issue_date)` in the five debtor queries.**
   - Pros:
     - No write-path change, no setting, no backfill.
   - Cons:
     - Five SQL sites to keep in sync.
     - Aging starts at issue date so every such fee note is overdue from day one and the dunning scan chases the day after send.
     - The PDF, portal and `InvoiceSyncEvent` still carry NULL (G-C7 c stays open).
     - `CollectionsReadApiTest:356` needs a nullable DTO.
2. **Stamp at send.**
   - Pros:
     - Latest possible moment.
     - The firm can still edit the draft's date.
   - Cons:
     - The number, issue date and PDF are produced at approve.
     - A due date that appears only at send means the approved PDF and the portal projection (`InvoiceSyncEvent:295`) are generated without it, and `APPROVED` fee notes have no aging basis.
3. **Stamp at approve from `BillingSettings.defaultPaymentTermsDays` (default 30) when the draft left it null; backfill APPROVED/SENT rows (CHOSEN).**
   - Pros:
     - Same moment as numbering and issue date.
     - One write, every downstream reader (debtor book, digest, aging, scan, PDF, portal) correct without touching their SQL.
     - Matches the `applyDefaults` precedent.
   - Cons:
     - Entity + settings embeddable + V4 column + settings form + backfill.
     - `OrgSettingsSchemaSnapshotTest` re-pinned deliberately.

*Payments*

4. **Whole-amount only, add `payment_method`.**
   - Pros:
     - One PR.
     - No status change.
   - Cons:
     - Leaves N-5 (trust/webhook/Xero mark PAID on any amount) and G-C11 open.
     - Rejected by D8.
5. **Payment ledger only — derive status and balance from `SUM(payment_events.amount)` at read time.**
   - Pros:
     - No new column.
     - Single source of truth is the event table.
   - Cons:
     - Every debtor/aging/digest/Clients-row query gains a correlated aggregate.
     - `ClientListRow` is capped at 10 statements and `MatterListRow` at 7 (615A/B lessons).
     - The portal read model and Xero sync would re-aggregate on every projection.
6. **Persisted `invoices.amount_paid` maintained by the payment write path, derived `balance_due = total − amount_paid`, `PARTIALLY_PAID` status, `payment_events.payment_method` + `amount` as the ledger (CHOSEN).**
   - Pros:
     - Reads stay one column.
     - The ledger stays the audit trail.
     - Status is explicit for list filters, portal badges and Xero.
   - Cons:
     - Guarded `chk_invoice_status` swap.
     - `reversePayment` semantics must be defined.
     - Every payment source changes.

**Decision**: Options 3 and 6 — `BillingSettings.defaultPaymentTermsDays` (V4, `NOT NULL DEFAULT 30`, Settings › Fees › Billing); `Invoice.approve` stamps `dueDate = issueDate + terms` when null; V4 backfills `UPDATE invoices SET due_date = COALESCE(issue_date, created_at::date) + 30 WHERE status IN ('APPROVED','SENT') AND due_date IS NULL`; `payment_method` enum (`EFT, CARD, CASH, TRUST_TRANSFER, XERO, OTHER`) on `payment_events`, per-payment `amount` on `RecordPaymentRequest` (default: balance), `invoices.amount_paid NUMERIC NOT NULL DEFAULT 0`, `InvoiceStatus.PARTIALLY_PAID`, and the debtor book, aging, cash digest, Collections, Home Debtors, Clients row and a new backend Fee-notes summary endpoint read `status IN ('SENT','PARTIALLY_PAID')` with `total − amount_paid` as outstanding. Payment reversal stays an internal service operation with **no REST endpoint**: `InvoiceTransitionService.reversePayment`, called from the trust reversal path (`TrustTransactionService:1140`), now also accepts PARTIALLY_PAID.

**Rationale**:

1. **Approve is where the fee note becomes a document.** Number, issue date and the persisted PDF are produced there; the due date belongs to the same act so that the PDF the client receives and the debtor book agree from the first second.
2. **A persisted balance respects the statement caps.** The 615A/615B lessons set hard statement budgets on the list rows; a column is the only shape that keeps those rows at their caps while giving five aggregate queries a correct outstanding figure.
3. **Partial amounts from external sources are real today** — a trust fee-transfer may be smaller than the fee note, and N-5 is a latent misstatement of a client's debt. Posting the actual amount and transitioning to `PARTIALLY_PAID` or `PAID` only when `amount_paid ≥ total` fixes the three sources with one rule.
4. **Reversal is defined, not implied** (a decision beyond D8): `reversePayment` deletes the payment event row (current behaviour) and recomputes `amount_paid` as `LEAST(total, SUM(remaining COMPLETED events))` — never decremented in place, so reversing an overpaid external event cannot drive it negative; status becomes `SENT` when `amount_paid` returns to 0, else `PARTIALLY_PAID`; `paid_at` clears. It remains internal — no REST endpoint — and the audit literals `invoice.payment_reversed` / `invoice.payment_partially_reversed` are unchanged because portal activity depends on them.
5. **The CHECK swap is guarded and idempotent**: `ALTER TABLE invoices DROP CONSTRAINT IF EXISTS chk_invoice_status; ADD CONSTRAINT … CHECK (status IN ('DRAFT','APPROVED','SENT','PARTIALLY_PAID','PAID','VOID'))` with the catalog guard scoped to `current_schema()` (`backend/db/BASELINE.md`).

**Consequences**:

- Positive:
  - Home Debtors, Clients "Outstanding", the Invoice Aging report, the cash digest, the dunning scan and the Fee notes tile read one definition.
  - "Due Date:" prints on the PDF.
  - The Fee notes "Overdue" tile stops being always 0.
  - A R 5 000 EFT part payment on R 42 182 leaves R 37 182 on Home, the client row and aging.
  - A full payment marks PAID.
  - Overpayment rule by source: for CARD (pay-now webhook), XERO and TRUST_TRANSFER sources an amount exceeding the balance is still recorded as a payment event, the fee note is capped at PAID (`amount_paid = total`), and the excess is written to the audit trail as `invoice.payment_excess` (NOTICE) for reconciliation; only the manual EFT/CASH/OTHER path rejects an excess with a 409. `invoice.payment_excess` is one new additive audit literal, registered with the [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) comment.
  - Reversal: no REST endpoint; `InvoiceTransitionService.reversePayment` (trust reversal path) deletes the event and recomputes `amount_paid` from the remaining events, now from PARTIALLY_PAID as well as PAID; `invoice.payment_reversed` / `invoice.payment_partially_reversed` literals unchanged.
- Negative:
  - Backfill changes the aging of anything already sent on the walked and demo tenants (accepted by D2).
  - `ClientListRowIntegrationTest:203` ("SENT without a due date") becomes a legacy guard case — keep it with an updated javadoc or move the pins to 3 / 2277.00.
  - `CollectionsReadApiTest` non-null pin stays.
  - New `InvoiceLifecycleIntegrationTest` cases (approve stamps due date.
  - Part payment.
  - Full payment.
  - Reversal) and a debtor-book test that creates the fee note through `InvoiceCreationService` + transitions, not entity `markSent`.
  - Xero sync, trust fee-transfer and webhook tests gain amount cases.
  - The portal fee-note view and `PortalInvoice` projection gain `balanceDue`.
  - `invoice-lifecycle.spec.ts:201-225` and `invoice-detail.test.tsx:230-245` change for the amount/method form.
  - Frontend `InvoiceStatus` unions, status badges and filters gain `PARTIALLY_PAID`.
  - `invoices/page.tsx` client-side sum is deleted in favour of the summary endpoint.
  - V4 is shared with [ADR-345](ADR-345-canonical-project-role-values.md), [ADR-353](ADR-353-firm-letterhead-settings.md), [ADR-354](ADR-354-persisted-retention-end-date.md).
  - `OrgSettingsSchemaSnapshotTest` is re-pinned deliberately for the new `org_settings` column.
- Related:
  - [ADR-342](ADR-342-audit-and-entity-tag-strings-stable-under-renamed-wire.md) (`invoice.paid` literal unchanged.
  - A new `invoice.partially_paid` key is additive), [ADR-349](ADR-349-activity-feed-correlation-key.md) (payment events carry `project_id`), [ADR-357](ADR-357-terminology-guard-extension.md) (`INV-` numbering and `/api/invoices` stay), `tasks/phase88-gap-register.md` D2/D8.
