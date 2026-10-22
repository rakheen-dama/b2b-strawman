# R6 — Disbursements / Fee notes / Payments cluster: root-cause dossier

Read-only research on `main` @ `50b88ed5c` (2026-10-22). Repo root `/Users/rakheendama/Projects/2026/b2b-strawman`; paths below are relative to it unless absolute. `B=backend/src/main/java/io/b2mash/b2b/b2bstrawman`, `FE=frontend`.

Gaps covered: G-C9, G-B11, G-B12, G-C10, G-C11, G-C6, G-C8 (`qa_cycle/evidence/phase87-capstone/gaps.md:24-25,42-47`; attribution rows `:83-84,106,113`). All seven are classed **Pre-existing** by the 617C attribution (code archaeology); nothing here contradicts that.

## 0. The nine `FIXME(depth-ii:*)` markers (CONFIRMED by grep)

Only the Playwright specs under `frontend/e2e/tests/legal-depth-ii/` carry the markers (docs/testplans reference them but are not the source).

| # | Slug | file:line | In this cluster? |
|---|---|---|---|
| 1 | `soa-preview-unbounded-scale` | `frontend/e2e/tests/legal-depth-ii/day-30-45-soa-writeoff.spec.ts:205` | no (G-C4) |
| 2 | `e2e-s3-presigner-unreachable` | `…/day-30-45-soa-writeoff.spec.ts:286` | no — environment limitation, not a product bug |
| 3 | `no-write-off-action` (×3) | `…/day-30-45-soa-writeoff.spec.ts:311,338,360` | **yes — G-C9** |
| 4 | `trust-link-payment-type` (×2) | `…/day-05-disbursements.spec.ts:270,323` | **yes — G-B11** |
| 5 | `matter-unbilled-disbursements-summary` | `…/day-05-disbursements.spec.ts:457` | **yes — G-B12** |
| 6 | `retention-period-unconfigurable` | `…/day-75-85-closure.spec.ts:297` | no (G-D3) |
| 7 | `admin-offered-closure-override` | `…/day-75-85-closure.spec.ts:319` | no (G-D7) |
| 8 | `checklist-stale-after-complete` | `…/day-14-conveyancing.spec.ts:124` | no (intermittent) |
| 9 | `otp-clause-associations` | `…/day-14-conveyancing.spec.ts:328` | no (G-B18) |

(`page-transition-remount` / `page-transition-blank` were fixed by #1584/#1585 and no longer appear — `tasks/phase86-subtraction.md:706`.) The "nine" in `tasks/qa-scenario-amendments-post86.md:248` counts the 8 product slugs + e2e-s3 as nine marker families; 3 of the 8 product slugs are in this cluster.

---

## 1. G-C9 — No Write Off action in the UI (HIGH)

### Backend (CONFIRMED — Epic 491, complete and tested)
- Endpoint: `POST /api/legal/disbursements/{id}/write-off` — `B/verticals/legal/disbursement/DisbursementController.java:104-108`, `@RequiresCapability("WRITE_OFF_DISBURSEMENTS")`.
- Request DTO: `WriteOffRequest(String reason)` — `@NotBlank`, `@Size(max=5000)` — `B/verticals/legal/disbursement/dto/WriteOffRequest.java:7-10`.
- Service: `DisbursementService.writeOff(id, reason)` `:326-349` — module guard, blank-reason → `InvalidStateException` (400), entity transition, `IllegalStateException` → `ResourceConflictException` (409). Audit event `disbursement.written_off`, entityType `legal_disbursement`, details `{project_id, reason}` `:340-346`.
- Entity transition: `LegalDisbursement.writeOff(reason)` `:294-305` — **from-state is the billing axis only: requires `billingStatus == UNBILLED`**; sets `WRITTEN_OFF` + `writeOffReason`. The approval axis is NOT checked (a DRAFT/PENDING/REJECTED-but-UNBILLED disbursement can also be written off). `restore()` `:312-320` exists (WRITTEN_OFF → UNBILLED) but has **no controller endpoint**.
- Response: `DisbursementResponse` (includes `writeOffReason` `dto/DisbursementResponse.java:36,63`, `billingStatus`).
- Capability seeding: owner and admin both hold `WRITE_OFF_DISBURSEMENTS` (`backend/src/main/resources/db/migration/tenant/V1__baseline.sql:2316,2327`); member does not.
- Tests already pin it: `DisbursementServiceTest.writeOff_UNBILLED_transitionsAndPersistsReason/_blank_reason_throws/_BILLED_throws` (`:280,292,303`), `DisbursementControllerIntegrationTest.legalTenant_POST_writeOff_blankReason_returns400_validationFailure` (`:283`).

### Frontend (CONFIRMED — client exists, nothing calls it)
- `FE/lib/api/legal-disbursements.ts:185-190` `writeOffDisbursement(id, {reason})` → POST `/write-off`. **Zero call sites** (`rg writeOffDisbursement` → only the definition and the depth-ii spec comment).
- `FE/app/(app)/org/[slug]/legal/disbursements/actions.ts` has `submitForApproval/approve/rejectDisbursementAction` (`:125-210`) — **no `writeOffDisbursementAction`**. The reject action (`:180-210`) is the exact template to copy: capability pre-check via `fetchMyCapabilities()`, trim/require notes, call API, `revalidatePath` list + detail + matter.
- Detail page `FE/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client.tsx`: header action row `:150-176` renders only Submit (DRAFT) and Upload/Edit (`editable` = DRAFT|PENDING_APPROVAL, `:62-63`). For an APPROVED/UNBILLED row **nothing renders** (matches the capstone "zero buttons"). `:222-224` only *displays* `writeOffReason`. The page only computes `canApprove` (`[id]/page.tsx:29-33`: `isAdmin || isOwner || APPROVE_DISBURSEMENTS`) — no `canWriteOff` prop.
- Firm list `FE/components/legal/disbursement-list-view.tsx:179-247`: the Actions dropdown exists but `hasActions = !!onEdit || !!onUploadReceipt` and is gated by `editable = canEditStatus(approvalStatus)` → empty cell for APPROVED rows. Matter tab `FE/components/legal/project-disbursements-tab.tsx` reuses the same list view.
- Approval UI pattern to clone: `FE/components/legal/disbursement-approval-panel.tsx` (dialog with notes textarea, `data-testid="disbursement-approve-dialog"`, `-notes-input`, `-confirm-approve-button`).

### What the spec expects (pins the UI contract)
`day-30-45-soa-writeoff.spec.ts:318-333`: button `/write[\s-]*off/i` on the detail page → dialog named `/write[\s-]*off/i` with a textbox labelled `/reason/i` → confirm button `/write[\s-]*off|confirm/i` → detail shows "Written off" badge + "Write-off reason" row; then audit filter `disbursement.written_off` with reason in details (`:341-356`); then written-off row excluded from the next fee-note draft (`:359+`) — the exclusion is already true server-side (`findUnbilledBillableByCustomerId` filters by billing status).

### Root cause
Epic 491 shipped the API + capability + tests; the UI slice (dialog/action) was never built. Pre-existing (#1582 recorded it).

### Fix shape (UI-only, no backend change)
1. `actions.ts`: add `writeOffDisbursementAction(slug, id, reason)` mirroring `rejectDisbursementAction`; gate on `isAdmin || isOwner || capabilities.includes("WRITE_OFF_DISBURSEMENTS")`.
2. `[id]/page.tsx`: compute `canWriteOff` alongside `canApprove`; pass to `DisbursementDetailClient`.
3. New `FE/components/legal/disbursement-write-off-dialog.tsx` (dialog-owns-button pattern per `frontend/CLAUDE.md` "Dialog Trigger Composition"), zod schema `writeOffDisbursementSchema` in `lib/schemas/`. Show the trigger when `canWriteOff && billingStatus === "UNBILLED"` (optionally also require `approvalStatus === "APPROVED"` — product call, see §9).
4. Optional in the same PR only if authorised as same-bug-class: add `onWriteOff` to `DisbursementListView` so the firm list / matter tab ACTIONS cell gets the item (currently the cell is empty for APPROVED rows anyway).
5. Add `data-testid`s matching the spec (`disbursement-write-off-button`, `-dialog`, `-reason-input`, `-confirm-button`) and un-fixme the three depth-ii tests.

---

## 2. G-B11 — Trust-linked disbursement cannot be created (MEDIUM)

### Both sides CONFIRMED
- **Dialog filter**: `FE/app/(app)/org/[slug]/legal/disbursements/actions.ts:231-290` `fetchApprovedTrustDisbursementPayments` walks every trust account, pages `/api/trust-accounts/{id}/transactions` (the query params `matterId/status/type` it sets at `:249-251` are **ignored** by the backend — `TrustTransactionService.listTransactions(UUID, Pageable)` `:160` has no filters), then client-filters `t.status === "APPROVED" && t.transactionType === "DISBURSEMENT_PAYMENT"` (`:285-289`). Dialog `FE/components/legal/trust-transaction-link-dialog.tsx:36-38,86-87` renders the empty copy. The unit test `FE/components/legal/__tests__/trust-transaction-link-dialog.test.tsx:26,61` pins `DISBURSEMENT_PAYMENT` as the mock type.
- **Ledger**: `chk_trust_txn_type` allows only `DEPOSIT, PAYMENT, TRANSFER_IN, TRANSFER_OUT, FEE_TRANSFER, REFUND, INTEREST_CREDIT, INTEREST_LPFF, REVERSAL` (`V1__baseline.sql:1821`) — **no `DISBURSEMENT_PAYMENT`**. The record-payment flow writes `"PAYMENT"` (`B/verticals/legal/trustaccounting/transaction/TrustTransactionService.java:483-495`, endpoint `POST /api/trust-accounts/{accountId}/transactions/payment` `TrustTransactionController.java:60`). `RecordPaymentDialog.tsx` (`FE/components/trust/`) has Client / Matter (optional) / Amount / Reference / Description / Date — no "this is a disbursement" flag; `transactions/actions.ts:110-121` posts no type.
- **Backend link validation agrees with the ledger, not the dialog**: `DisbursementService.EXPECTED_TRUST_TRANSACTION_TYPE = "PAYMENT"` `:69` with a javadoc (`:62-68`) explicitly saying the arch spec's `DISBURSEMENT_PAYMENT` is not in the CHECK and PAYMENT is the stand-in. `validateTrustLink` `:633-676` requires APPROVED + type PAYMENT + same project. `DisbursementServiceTest.create_TRUST_ACCOUNT_approvedPaymentSameMatter_succeeds` `:442` pins PAYMENT.
- So the frontend filter was written to the architecture spec while the backend was written to the schema. The result: the dialog can never list anything; even if it did, the backend only accepts PAYMENT.

### Which is semantically right?
A trust payment made on the client's behalf to the Deeds Office *is* a disbursement withdrawal from trust. Under the LPA/LPC rules it is simply a trust **payment** (a debit to the client's trust ledger) — the "disbursement" nature is a billing attribute of the firm's cost record, not a separate trust transaction type. Every trust-account disbursement is a PAYMENT; not every PAYMENT is a disbursement (e.g. paying out proceeds to the client or a third party). Keeping one type and linking by reference (the `legal_disbursements.trust_transaction_id` FK) is the cleaner model. A separate `DISBURSEMENT_PAYMENT` type would also need the CHECK, the cashbook/balance CASE lists (`TrustTransactionRepository.java:87-88,109-110,135-136,175,187`, `TrustTransactionService.java:986-989`), reconciliation matching and the SoA template to learn a new value — large blast radius for no accounting benefit.

### Is there a UI to create a DISBURSEMENT_PAYMENT transaction?
No — CONFIRMED. No endpoint, no dialog, no CHECK value.

### Recommended fix (frontend-only, one PR)
**Option A (recommended)**: `fetchApprovedTrustDisbursementPayments` filters `transactionType === "PAYMENT"` (keep matter + APPROVED); drop the ignored `type=DISBURSEMENT_PAYMENT` param or set it to `PAYMENT`; reword the empty copy to "No approved trust payments found for this matter"; update the unit test mock type. Also fix the javadoc at `actions.ts:213-229` which repeats the wrong type. This aligns with the backend contract and `DisbursementService` javadoc. Blast radius: `trust-transaction-link-dialog.test.tsx` + depth-ii spec 5.9/5.10 un-fixme.
**Option B**: add a `DISBURSEMENT_PAYMENT` type end-to-end (migration V-next CHECK, repository CASE lists, `EXPECTED_TRUST_TRANSACTION_TYPE`, payment dialog "disbursement" checkbox, SoA/reconciliation). Not recommended — product/accounting change, no observed need.
**Option C (nice-to-have later)**: a second-pass UX — exclude PAYMENTs already linked to another disbursement (`legal_disbursements.trust_transaction_id`) so the picker does not offer a used payment twice; needs a small backend filter or an extra client fetch.

Scenario note (not a code fix): the 90-day doc creates the deeds-office disbursement before any Sipho trust deposit/payment exists (`gaps.md:24`). After the fix the scenario still needs a deposit → payment → approve sequence first (the depth-ii spec already does this at 5.7-5.8: `day-05-disbursements.spec.ts:243-266`). Doc reordering is a §6 product authorisation item.

---

## 3. G-B12 — Matter Fees › Disbursements has no unbilled summary (MEDIUM)

### CONFIRMED
- Backend endpoint exists: `GET /api/legal/disbursements/unbilled?matterId=` → `UnbilledDisbursementsResponse(matterId, currency="ZAR", items[], totalAmount, totalVat)` (`DisbursementController.java:76-80`, `dto/UnbilledDisbursementsResponse.java`, `DisbursementService.listUnbilledResponseForProject` `:552-575` — filters approved + UNBILLED via `findUnbilledBillableByCustomerId(customerId, projectId)`, totals HALF_UP scale 2). Test: `DisbursementUnbilledEndpointIntegrationTest`.
- Frontend client + action exist: `lib/api/legal-disbursements.ts:192-199 listUnbilled`, `actions.ts:292-303 fetchUnbilledDisbursementsAction` (SWR-friendly).
- Only consumer today: the invoice editor picker `FE/app/(app)/org/[slug]/invoices/[id]/edit/(components)/add-disbursements-picker.tsx:69-75` (it recomputes its own incl-VAT totals at `:239,261` rather than using `totalAmount/totalVat`).
- `FE/components/legal/project-disbursements-tab.tsx` fetches the paged list only (`:10,36-37`) — no summary. The **firm** Disbursements page has no summary either (`disbursements-list-client.tsx` — grep for unbilled/total returns nothing; `page.tsx:23-32` only passes `initialTotal = totalElements`, a row count). So there is nothing to "reuse" from the firm page; the reusable piece is the unbilled endpoint.

### Spec contract
`day-05-disbursements.spec.ts:463-470`: `[data-testid=project-disbursements-tab]` must contain text matching `/Unbilled\s+Disbursements:?\s*R\s?2[\s,]?000[.,]00/` — i.e. amount **excl. VAT** sum of approved-unbilled rows ("R2,000.00" = R750 sheriff + R1,250 deeds, both before VAT). Use `formatCurrency(totalAmount, "ZAR")` from `@b2mash/shared/format` (renders "R 2 000,00" — the regex tolerates space/comma separators).

### Fix shape (UI-only)
Add a small summary strip at the top of `ProjectDisbursementsTab`: `useSWR(["unbilled-disbursements", projectId], () => fetchUnbilledDisbursementsAction(projectId))` → "Unbilled disbursements: R … (VAT R …)" with `data-testid="unbilled-disbursements-summary"`; revalidate after approve/write-off. Optionally mount the same strip on the firm list when a matter filter is active (follow-up). Vitest: new test alongside `components/legal/__tests__/`.

---

## 4. G-C10 — Fee-note line description drops the time-entry narrative (HIGH)

### CONFIRMED
- Builder: `B/invoice/InvoiceCreationService.java:1166-1181` `buildTimeEntryDescription` → `taskTitle + " -- " + timeEntry.getDate() + " -- " + memberName` ("Untitled"/"Unknown" fallbacks). Called once at `:865` inside the time-entry → `InvoiceLine` loop (`:898-906`, `lineType=TIME`, quantity = minutes/60 scale 4, unitPrice = snapshot or resolved rate).
- `time_entries.description text` exists (`V1__baseline.sql` time_entries DDL) and `TimeEntry.getDescription()` is used elsewhere (`UnbilledTimeService.java:347`) — **it is never read by the invoice line builder**.
- `invoice_lines` has no metadata/JSON column: `description, quantity, unit_price, amount, sort_order, tax_*, line_type, line_source, time_entry_id, …` (`V1__baseline.sql` invoice_lines DDL; `InvoiceLine.java:25-80`). The date and fee earner survive only inside the description string (and via `time_entry_id` → join). The PDF/preview contexts expose **only** the string.
- Rendering: template context `B/template/InvoiceContextBuilder.java:96-113` emits per line `{description, quantity, unitPrice, amount, taxAmount, taxRateName, lineTotal}`; the legal-za fee-note template table columns are Description / Qty / Unit Price / VAT / Total (`backend/src/main/resources/template-packs/legal-za/fee-note-za.json:112-118`). The Thymeleaf preview (`InvoiceRenderingService.java:72-134`, `invoice-preview` template) also shows `line.description`. So the "Description" column is the only place the narrative can appear; there is no date or fee-earner column.
- Lines are one-per-entry (CONFIRMED, `:898` creates one `InvoiceLine` per time entry). Keep that — it is what an LPC-style itemised account needs, and `time_entries.invoice_id` / `invoice_lines.time_entry_id` give the 1:1 audit link.
- Comparison: disbursement lines already use a richer LSSA format `"{Category}: {description} ({supplier}, {date})"` (`:1123-1134`), and expense lines `description + " [" + category + "]"` (`:956`).

### Tests pinning the current format
- Backend: **no test asserts the builder's output**. The only `" -- "` string in tests is `InvoicePreviewIntegrationTest.java:159` which *creates* a line directly with `"Backend API dev -- 2025-01-20 -- Preview Owner"` (fixture, not a builder assertion). `rg '" -- "' src/main` → only `:1180`.
- Frontend: no vitest pins the pattern. E2E `invoice-lifecycle.spec.ts` does not assert line text.
- The capstone evidence `logs/walkC1-db-days50-58.txt` shows `invoice_lines.description` vs `time_entries.description` divergence.

### Proposed line format (legal fee note)
`"{yyyy-MM-dd} · {Fee earner} · {narrative}"` where `narrative = timeEntry.description` when non-blank, else `taskTitle`. Rationale: a fee note is read per date (attorneys and clients reconcile against diaries), the fee earner is required on SA fee notes for tariff-rate justification, and the narrative is "what the client pays for" (gaps.md:46). Keep `taskTitle` as the fallback so entries logged without narrative still bill sensibly; do **not** append the task title when a narrative exists (the RAF example shows the task title is a category, not a description). Consider a `maxLength` guard (narratives are TEXT; the PDF cell wraps but a 5 000-char narrative would be ugly) — trim at, say, 500 chars with "…", or leave to the template.
Alternative (smaller diff): keep the current `" -- "` structure but swap the first segment to `narrative ?: taskTitle`. Either way: one line changed at `:1166-1181` + a new integration test that generates from an entry with a description and asserts the line.

Related (same file, not this gap): the depth-ii capstone found the preview shows raw `1.5000 · 1200.00` (G-C7 (d)) — don't bundle.

---

## 5. G-C11 — Record Payment: no amount, no method (MEDIUM)

### CONFIRMED — the backend model is full-settlement only
- Endpoint `POST /api/invoices/{id}/payment`, `@RequiresCapability("INVOICING")`, body `RecordPaymentRequest(String paymentReference)` (`B/invoice/InvoiceController.java:181-187`; the record has **only** `paymentReference`).
- `InvoiceTransitionService.recordPayment` `:311-370`: requires `status == SENT` (409 otherwise), cancels any payment-link session, calls `gateway.recordManualPayment(PaymentRequest(invoiceId, invoice.getTotal(), currency, …))` — amount is **always `invoice.getTotal()`** — then `invoice.recordPayment(ref)` (`Invoice.java:246-254`: SENT → PAID, `paidAt`, `paymentReference`), writes a `PaymentEvent(invoiceId, "manual", null, COMPLETED, invoice.getTotal(), currency, paymentDestination)` and audit `invoice.paid`.
- `invoices` columns: `payment_reference`, `paid_at`, `payment_terms`, `payment_session_id/url`, `payment_destination` (`Invoice.java:46-109`). **No `amount_paid`, no `balance_due`.** `InvoiceStatus = DRAFT, APPROVED, SENT, PAID, VOID` — **no `PARTIALLY_PAID`** (`InvoiceStatus.java`).
- `payment_events` does carry `amount`, `currency`, `provider_slug` ("manual" | gateway slug), `payment_reference`, `payment_destination` (`PaymentEvent.java:29-61`) — so per-event amounts are storable, but nothing aggregates them against the invoice total.
- Other payment sources already use the same transition: trust fee-transfer approval (`TrustTransactionService.java:866` → `invoiceService.recordPayment(id, ref, true)`), webhook reconciliation (`PaymentReconciliationService.java:103`), Xero sync (`AccountingSyncService.java:441`). All are full-settlement.
- Collections (Phase 83) reads: `CollectionsReadService` debtor book = `invoices WHERE status='SENT' AND due_date IS NOT NULL`, outstanding = `i.total` (`:75-125`). It knows nothing about partial payments; a PARTIALLY_PAID status would be invisible to it unless the SQL is extended (and G-D11/G-D21 already note the `due_date IS NULL` exclusion).
- Frontend: `FE/components/invoices/invoice-status-sections.tsx:79-99` PaymentForm = one text input "Payment Reference (optional)" + "Confirm Payment"; server action `invoice-payment-actions.ts:86-114` (owner/admin gate) posts `RecordPaymentRequest {paymentReference?}` (`lib/types/invoice.ts:156-158`). Pins: `__tests__/app/invoices/invoice-detail.test.tsx:230-245` (button presence only), `e2e/tests/invoices/invoice-lifecycle.spec.ts:201-225` (placeholder `/CHK-12345|Wire transfer/`, "Confirm Payment").

### Scope
- **UI-only is NOT enough for amount**: the backend ignores any amount. Part-payments require backend work (status or ledger model) — a product decision.
- **Method is cheap**: `payment_events.provider_slug` is "manual" for all UI payments; a method could live in a new nullable `invoices.payment_method` + `payment_events.payment_method` (V-next migration) or — zero-migration — be folded into the existing reference ("EFT · ABC123") which is what the walker did and what the gap complains about.

### Fix options
- **A (small, backend+UI)**: add `paymentMethod` (enum EFT | CASH | CARD | TRUST_TRANSFER | OTHER; nullable) and `paidAmount` display-only = total. Migration adds `invoices.payment_method varchar(20)`, `payment_events.payment_method`. UI gets a Method select + a read-only "Amount: R …" (full). Satisfies "method = EFT" and makes the full-settlement explicit; no partial payments. Tests: `InvoiceLifecycleIntegrationTest.shouldRecordPaymentOnSentInvoice`, `PaymentEventsControllerIntegrationTest.recordPayment_*`, `OrgSettingsSchemaSnapshotTest` untouched (not org_settings).
- **B (full)**: partial payments — `PARTIALLY_PAID` status (or keep SENT + derived balance), `amount` on `RecordPaymentRequest`, aggregate `SUM(payment_events.amount WHERE COMPLETED)` vs `total`, transition to PAID only when balance ≤ 0, update `CollectionsReadService` (outstanding = total − paid), portal invoice views, Xero sync (`AccountingSyncService`), trust fee-transfer (which already carries an `amount` that may be less than the invoice total — today it marks PAID regardless, arguably a latent bug), statement of account, debtor widgets (614B/615B). Large; needs its own epic.
- Recommendation: ship A now (one PR, backend+UI), file B as a product question (§9).

---

## 6. G-C6 — Pre-check says send is blocked, send succeeds (LOW)

### CONFIRMED — both halves say "blocked" for a case that is not blocked
- Backend `B/invoice/InvoiceValidationService.java:119-136 checkCustomerTaxNumber`: for `CustomerType.INDIVIDUAL` the severity is **capped at WARNING** (LZKC-008, `:125-127`), but the message is severity-independent: `"Tax Number is required to send " + invoiceNounWithArticle()` (`:134`) → "Tax Number is required to send a fee note". `validateInvoiceGeneration` `:50-72` returns it at WARNING; `validateInvoiceSend` `:78-88` at CRITICAL for COMPANY/TRUST, WARNING for INDIVIDUAL. `send()` only blocks on CRITICAL (`InvoiceTransitionService.java:214-217`). Tests pin exactly this: `InvoiceGenerationValidationIntegrationTest.sendInvoice_companyMissingTaxNumber_isBlocked` `:311` and `sendInvoice_individualMissingTaxNumber_sendsWithWarning` `:344`; `InvoiceTerminologyLegalZaTest.validateInvoiceSend_legalZa_taxNumberMessageSaysFeeNote` `:140-162` pins the literal message "Tax Number is required to send a fee note".
- Frontend `FE/components/invoices/invoice-generation-dialog.tsx:252-270`: a hard-coded banner keyed on `c.name === "customer_tax_number" && !c.passed` (ignores `c.severity`) says "This draft can be saved, but the invoice cannot be sent until a tax number is added to the customer profile." — also still says "invoice" (ADR-338 vocabulary slip) and "customer profile".
- Sipho is INDIVIDUAL → warning only → send succeeded. The copy on both sides is wrong for individuals; the behaviour (LZKC-008) is the intended one.

### What is right for SA
A tax invoice (VAT Act s20) must show the **supplier's** VAT number and, for invoices ≥ R5 000, the **recipient's** VAT number *only if the recipient is a VAT vendor*. Natural-person clients usually are not vendors and never need a number on the fee note. So: block for COMPANY/TRUST (they may be vendors — arguably even that should be "VAT-registered?" rather than "has a tax number"), warn-only for INDIVIDUAL — which is what the backend does. The copy must say so.

### Fix shape (copy, both sides; no behaviour change)
- Backend: make the message severity-aware, e.g. WARNING → "Client has no tax number — the fee note will be sent without a client VAT number" ; CRITICAL → current text. Update `InvoiceTerminologyLegalZaTest:162,188` and the generation-validation tests' message assertions if any.
- Frontend: branch the banner on `check.severity` (`CRITICAL` → "cannot be sent until…", `WARNING` → "can be saved and sent; the fee note will not show a client VAT number"), and say "fee note"/"client". Tests: `__tests__/components/invoices/invoice-generation-prereq.test.tsx`, `invoice-generation-validation.test.tsx`, `invoice-generation-dialog.test.tsx` (check for assertions on the banner text).

---

## 7. G-C8 — Approve and Send are two buttons (LOW)

### CONFIRMED — deliberate three-state lifecycle
- Entity: `Invoice.approve()` DRAFT → APPROVED (assigns `INV-%04d` via `InvoiceNumberService`), `markSent()` APPROVED → SENT (`Invoice.java:239-244` "Only approved invoices can be sent"), `recordPayment()` SENT → PAID, `voidInvoice()` APPROVED|SENT → VOID. Controller: `/approve` `:167`, `/send` `:174` (with `SendInvoiceRequest.overrideWarnings`), `/payment` `:181`, `/void` `:201`.
- Send does real work beyond a status flip: validation (§6), payment-link generation, audit `invoice.sent`, email with PDF attachment + generated-document persistence (`InvoiceSentDocumentPersistenceIntegrationTest`, `InvoiceEmailServiceIntegrationTest`), portal notification. Approve is the numbering/lock step (lines become immutable; fee transfer from trust is allowed from APPROVED — `TrustTransactionService.java:561-565`).
- UI: `FE/components/invoices/invoice-header-actions.tsx:66-116` — `isDraft` → Preview/Approve/Delete; `isApproved` → "Send fee note"/Void; `isSent` → "Record Payment"/Void. The send-override dialog (`invoice-status-sections.tsx:26`) handles CRITICAL warnings.
- Pins: `__tests__/app/invoices/invoice-detail.test.tsx` (state × buttons), `__tests__/invoices/invoice-generation-validation.test.tsx:247-248` (clicks "Send fee note"), `e2e/tests/invoices/invoice-lifecycle.spec.ts:117,146` (Approve then Send), backend `InvoiceLifecycleIntegrationTest.shouldSendApprovedInvoice/shouldRejectSendWhenNotApproved` (`:300,362`).

### Is a combined "Approve & send" reasonable?
Yes as an *additional* convenience, not a replacement: an "Approve & send" button on DRAFT that calls approve then send (client-side sequence, or a new `POST /{id}/approve-and-send` that reuses both transitions in one transaction). Keep Approve alone for firms whose partner approves and whose secretary sends later, and because send can fail validation (CRITICAL) after approval succeeded — the combined action must surface that as "approved, not sent". The cheapest honest fix for the scenario is a doc amendment ("Approve → APPROVED; Send fee note → SENT") — §6 authorisation.

---

## 8. Blast radius — tests to run

Backend (full `bash backend/scripts/verify.sh` is the merge bar; inner loop by area):
- Disbursements: `verticals/legal/disbursement/{DisbursementControllerIntegrationTest, DisbursementServiceTest, DisbursementUnbilledEndpointIntegrationTest, LegalDisbursementRepositoryTest}`; trust link: `DisbursementServiceTest.create_TRUST_ACCOUNT_*` (`:442,475,510`).
- Invoice creation/lines: `invoice/{InvoiceIntegrationTest, InvoiceLineIntegrationTest, InvoicePreviewIntegrationTest, InvoicePreviewTaxIntegrationTest, InvoiceCreationDisbursementIntegrationTest, InvoiceAppendDisbursementLinesIntegrationTest, InvoiceTariffIntegrationTest, UnbilledTimeIntegrationTest, InvoiceSentDocumentPersistenceIntegrationTest}` (G-C10 changes `invoice_lines.description` → anything asserting PDF/preview text).
- Validation/send: `invoice/{InvoiceGenerationValidationIntegrationTest, InvoiceTerminologyLegalZaTest, InvoiceLifecycleIntegrationTest, InvoiceEmailServiceIntegrationTest, InvoiceEmailFallbackGuardIntegrationTest}`.
- Payments: `invoice/{PaymentEventsControllerIntegrationTest, PaymentReconciliationServiceIntegrationTest, PaymentLinkServiceIntegrationTest, PaymentEventTest, PaymentEventRepositoryIntegrationTest}`, `verticals/legal/trustaccounting/transaction/{TrustTransactionServiceTest, TrustFeeTransferReversalCascadeTest}`, `integration/accounting/sync/*` (if `recordPayment` signature changes), `collections/*` (if partial payments).
- Schema pins if a migration is added: `OrgSettingsSchemaSnapshotTest` is org_settings-only (not affected by invoice columns); check any `*SchemaSnapshot*`/Flyway tests for `invoices`.

Frontend (`pnpm lint && pnpm build && pnpm test` + `pnpm format:check`):
- `components/legal/__tests__/{trust-transaction-link-dialog, disbursement-list-view, add-disbursements-picker}.test.tsx`
- `__tests__/app/invoices/invoice-detail.test.tsx`, `__tests__/invoice-payment-ux.test.tsx`, `__tests__/invoices/{invoice-generation-validation, invoice-generation-with-expenses}.test.tsx`, `__tests__/components/invoices/{invoice-generation-dialog, invoice-generation-prereq, invoice-tax}.test.tsx`, `__tests__/components/customers/customer-invoices-tab.test.tsx`, `__tests__/trust-tabs-settings.test.tsx:413` (Record Payment menu — trust side).
- `__tests__/no-terminology-remnants.test.ts` (any new copy must say fee note / client / matter).

Playwright (E2E stack :3001; legal configs need `PLAYWRIGHT_BASE_URL`, run from a worktree — they dirty the tree):
- `e2e/tests/legal-depth-ii/day-05-disbursements.spec.ts` (5.9/5.10/5.12 un-fixme), `day-30-45-soa-writeoff.spec.ts` (45.5-45.11 un-fixme), `e2e/tests/invoices/invoice-lifecycle.spec.ts` (Approve/Send/Record Payment; placeholder regex at `:216` if the form changes).

---

## 9. Product questions for the founder

1. **Write-off gating (G-C9)**: backend allows write-off from any approval state as long as UNBILLED. Should the UI only offer it on APPROVED (the scenario) or also on REJECTED/DRAFT? And should `restore()` (WRITTEN_OFF → UNBILLED) get an endpoint + "Undo write-off" action in the same slice?
2. **Trust type (G-B11)**: confirm Option A — treat every approved trust **PAYMENT** on the matter as linkable; no new `DISBURSEMENT_PAYMENT` type. Should already-linked payments be hidden from the picker (Option C)?
3. **Scenario order (G-B11)**: authorise re-ordering 90-day steps so Sipho's trust deposit + deeds-office payment + approval precede 5.9 (the depth-ii spec already does this).
4. **Unbilled summary semantics (G-B12)**: excl. VAT (matches the spec's R2 000) or incl. VAT, and show both? Also on the firm list?
5. **Fee-note line format (G-C10)**: approve `date · fee earner · narrative (task title fallback)`; any length cap; should the task title be dropped entirely when a narrative exists?
6. **Payments (G-C11)**: accept Option A (method + explicit full amount) now and defer part-payments (Option B, needs PARTIALLY_PAID + Collections/Xero/trust changes) to a scoped epic? Note the trust fee-transfer path already marks PAID regardless of amount — flag as a latent defect to own or document.
7. **Tax-number copy (G-C6)**: confirm the SA rule (block COMPANY/TRUST, warn INDIVIDUAL) stays; approve new copy on both sides.
8. **Approve vs Send (G-C8)**: doc amendment only, or also add an "Approve & send" convenience on DRAFT?

## 10. One-fix-per-PR plan (suggested order)
1. G-B11 dialog filter → PAYMENT (frontend, tiny, unblocks 5.9/5.10 and the R2 000 figure).
2. G-C9 write-off action + dialog (frontend, pattern-clone of approval panel).
3. G-B12 unbilled summary strip (frontend, reuses `/unbilled`).
4. G-C10 line description (backend, one method + test; verify PDF/preview).
5. G-C6 severity-aware copy (backend message + frontend banner).
6. G-C11 Option A (backend migration + DTO + UI) after the §9.6 answer.
7. G-C8 scenario wording (authorisation) ± "Approve & send".
