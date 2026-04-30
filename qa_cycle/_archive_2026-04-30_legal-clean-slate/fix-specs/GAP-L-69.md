# Fix Spec: GAP-L-69 — Trust Fee Transfer / Refund missing matter context for closure gate

## Problem (verbatim from QA evidence)

Day 60 cycle-1 PRE-FLIGHT-C (2026-04-25 SAST,
`qa_cycle/checkpoint-results/day-60.md` §"Day 60 — PRE-FLIGHT-C — 2026-04-25
SAST") HALTED at the matter-trust closure gate per scenario hard rule
("If a UI affordance is missing, log gap and exit — don't power through with
REST or SQL").

QA drove the entire client-funds disposition end-to-end through the browser:

- Pre-state: Sipho Dlamini ledger `R 70 000,00` on RAF-2026-001
  (project `e788a51b-3a73-…`); 2 RECORDED+APPROVED deposits both with
  `trust_transactions.project_id = e788a51b-…` (recorded via
  `RecordDepositDialog` which DOES carry a Matter field).
- Bob recorded R 1 250 fee transfer against INV-0001 → Thandi approved →
  INV-0001 SENT→PAID, Sipho ledger 70 000 → 68 750.
  DB row `f9f3f7a4-dc7f-4eaa-a594-b03374a9d80c` (FEE_TRANSFER, APPROVED) —
  `project_id IS NULL`.
- Bob recorded R 68 750 refund → Thandi approved → Sipho ledger 68 750 → 0.
  DB row `f215423c-bfbc-4a07-981a-4e0a90a8af48` (REFUND, APPROVED) —
  `project_id IS NULL`.
- Customer ledger card: `R 0,00` ✓ (matches Sipho's
  `client_ledger_cards.current_balance = 0`).
- Matter Trust-tab `TrustBalanceCard`: `R 0,00 / No Funds` ✓ (customer-scoped
  view).
- Closure-gate dialog: trust gate **STILL RED** at
  `Matter trust balance is R70000.00. Transfer to client or office before
  closure.` ✗

Net effect: matter cannot be closed via the UI flow as currently shipped.
Day 60 CLOSURE-EXECUTE blocked, exit checkpoints E.13 (matter closed) and
E.14 (Day 88 audit trail through closure) cannot be reached.

## Root Cause (grep-confirmed)

Two layers cooperating to drop the per-matter binding:

1. **Closure gate is strictly per-`project_id`** —
   `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/TrustBalanceZeroGate.java:38`
   computes `trustTransactionRepository.calculateBalanceByProjectId(project.getId())`.
   The repository query is
   `backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionRepository.java:120-138`
   — `WHERE t.projectId = :projectId` with the standard credit/debit sign
   rules. Rows with `project_id IS NULL` are invisible to the gate.

2. **FEE_TRANSFER + REFUND drop projectId on every code path:**
   - **Frontend `RecordFeeTransferDialog`**
     (`frontend/components/trust/RecordFeeTransferDialog.tsx:44-52`)
     defaults `{ customerId, invoiceId, amount, reference }` — no
     `projectId` field anywhere in the form or schema.
   - **Frontend `RecordRefundDialog`**
     (`frontend/components/trust/RecordRefundDialog.tsx:35-45`)
     defaults `{ customerId, amount, reference, description, transactionDate }`
     — no `projectId` field anywhere in the form or schema.
   - **Frontend Zod schemas**
     (`frontend/lib/schemas/trust.ts:54-71`) — `recordFeeTransferSchema` and
     `recordRefundSchema` do not declare a `projectId`/`matterId` key.
   - **Backend request DTOs**
     (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java:101-109`)
     — `RecordFeeTransferRequest(customerId, invoiceId, amount, reference)`
     and `RecordRefundRequest(customerId, amount, reference, description,
     transactionDate)` — neither record carries a `projectId` field.
     **(QA's note in the L-69 row that "backend already accepts project_id"
     is incorrect — it does not. This spec corrects that.)**
   - **Backend service**
     (`TrustTransactionService.java:553-565`, `623-635`) — both `recordFeeTransfer`
     and `recordRefund` construct a `new TrustTransaction(...)` with `null`
     hardcoded into the projectId positional argument. Net: no UI path
     can write a non-null `project_id` on FEE_TRANSFER or REFUND today.

The `RecordDepositDialog` is the working sibling — it has a `projectId`
free-text input (`RecordDepositDialog.tsx:118-130`), and the deposit
service constructs the row with `request.projectId()`
(`TrustTransactionService.java:217`). That's why the 2 deposits in the QA
trace carry the correct `project_id` and the dispositions don't.

## Chosen Fix (Hybrid — backend inference for FEE_TRANSFER, frontend Matter field for REFUND)

Rationale for the split (vs the QA-suggested all-frontend Matter combobox
on both dialogs):

- **FEE_TRANSFER is intrinsically bound to one invoice**, and the invoice's
  matter binding already lives in `InvoiceLine.projectId`
  (see `InvoiceRepository.java:23` —
  `SELECT DISTINCT il.invoiceId FROM InvoiceLine il WHERE il.projectId = :projectId`).
  The backend already knows enough to infer the matter — adding a UX
  affordance for the user to re-pick it is redundant and forces the
  RecordFeeTransfer dialog (often opened from a list of invoices, not from
  a matter) to either (a) auto-fill from invoice, which still requires
  a backend round-trip, or (b) ask the user to disambiguate, which they
  cannot — the invoice is the source of truth. Backend inference is
  smaller, safer, and removes a UX surface entirely.
- **REFUND has no inherent matter binding** — refunds are customer-level
  ("we're sending the residual back to Sipho"). The backend cannot infer.
  UI is the right place. We mirror the existing `RecordDepositDialog`
  pattern (free-text Matter UUID input, optional) verbatim — keeping it
  consistent with the rest of the trust-dialog family until a future epic
  upgrades the whole family to a typeahead matter combobox.
- **No frontend Matter combobox component exists yet** in
  `frontend/components/trust/` — building one + wiring autocomplete would
  push this past the verify-cycle SPEC_READY bar (cf. L-67's M-effort
  deferral on similar grounds). The free-text UUID pattern is what
  `RecordDepositDialog` ships today and what QA already drove successfully
  for the 2 deposits.

### Step-by-step

**Backend — FEE_TRANSFER infers projectId from invoice line:**

1. In `TrustTransactionService.recordFeeTransfer`
   (`TrustTransactionService.java:514`), after the existing invoice
   fetch + customer-binding check (lines 534–542), insert:
   ```java
   var inferredProjectId =
       invoiceLineRepository.findDistinctProjectIdByInvoiceId(invoice.getId())
           .orElse(null);
   ```
   Then change line 559 — replace the second positional `null` (projectId
   slot in the `TrustTransaction` constructor) with `inferredProjectId`.
2. Add the helper method to `InvoiceLineRepository` (or wherever
   `InvoiceRepository.java:23-26`'s `findDistinctInvoiceIdsByProjectId`
   lives — symmetric query):
   ```java
   @Query("""
       SELECT DISTINCT il.projectId FROM InvoiceLine il
       WHERE il.invoiceId = :invoiceId AND il.projectId IS NOT NULL
       """)
   Optional<UUID> findDistinctProjectIdByInvoiceId(@Param("invoiceId") UUID invoiceId);
   ```
   If the invoice spans multiple matters (rare for fee notes, but
   possible), `Optional.empty()` falls through and `inferredProjectId`
   stays `null` — preserving today's behaviour for the multi-matter edge
   case rather than silently picking one. Future story can decide on
   multi-matter splits; out of scope here.
3. Audit log entry (`TrustTransactionService.java:571-583`) — add
   `"project_id", inferredProjectId == null ? "" : inferredProjectId.toString()`
   to the details map for traceability.

**Backend — REFUND accepts optional projectId on the request DTO:**

4. Update record signature
   (`TrustTransactionService.java:104-109`) to:
   ```java
   public record RecordRefundRequest(
       UUID customerId,
       UUID projectId,        // NEW — optional
       BigDecimal amount,
       String reference,
       String description,
       LocalDate transactionDate) {}
   ```
5. In `recordRefund` (`TrustTransactionService.java:623-635`) replace the
   second positional `null` in the `TrustTransaction` constructor with
   `request.projectId()` (mirrors `recordDeposit` line 217 exactly).
6. Audit log details map (`TrustTransactionService.java:644-649`) — add
   `"project_id", request.projectId() == null ? "" : request.projectId().toString()`.

**Frontend — Matter field on RecordRefundDialog (mirrors RecordDepositDialog):**

7. `frontend/lib/schemas/trust.ts:63-69` — add to `recordRefundSchema`:
   ```ts
   projectId: z.string().uuid().optional().or(z.literal("")),
   ```
   Position the key right after `customerId` to match deposit/payment
   schema ordering.
8. `frontend/components/trust/RecordRefundDialog.tsx:35-45` — add
   `projectId: ""` to `getDefaultValues()` (after `customerId`).
9. Add a `<FormField name="projectId">` block immediately after the Client
   ID FormField (around line 105), copying the deposit dialog's exact
   markup (`RecordDepositDialog.tsx:118-130`):
   ```tsx
   <FormField
     control={form.control}
     name="projectId"
     render={({ field }) => (
       <FormItem>
         <FormLabel>Matter (Optional)</FormLabel>
         <FormControl>
           <Input placeholder="Matter UUID (optional)" {...field} />
         </FormControl>
         <FormMessage />
       </FormItem>
     )}
   />
   ```
10. `frontend/app/(app)/org/[slug]/trust-accounting/transactions/actions.ts`
    — ensure the `recordRefund` server action passes `projectId` through
    to the backend POST body (mirror how `recordDeposit` does it). If the
    action currently spreads `data` into the body, no change needed; if it
    cherry-picks fields, add `projectId`.

### Why we are NOT doing the QA-suggested broad changes

- **No Matter combobox/autocomplete component**: scope creep, fits in a
  future "trust UX polish" epic, not the verify cycle. Free-text UUID is
  the existing dialog convention.
- **No auto-fill from `?tab=trust` URL context**: the
  `RecordRefundDialog` is reachable from Trust Accounting list (no matter
  context) AND from `TrustBalanceCard` quick-actions (which doesn't
  currently mount the refund dialog at all — see
  `TrustBalanceCard.tsx:165-200`). Auto-fill plumbing requires threading
  `projectId` prop through `transaction-actions.tsx` →
  `RecordRefundDialog` → `useEffect` to seed the form. Optional polish,
  punt to follow-up.
- **No "Trust → Refund" affordance on matter Trust tab**: again, polish.
  Today refunds are recorded from Trust Accounting overview which is the
  canonical surface — Day 60 worked from there.

## Scope

| Layer    | File                                                                                                     | Change                            |
| -------- | -------------------------------------------------------------------------------------------------------- | --------------------------------- |
| Backend  | `TrustTransactionService.java` (record DTO + 2 service methods + 2 audit logs)                           | ~12 lines added/changed           |
| Backend  | `InvoiceLineRepository.java` (or wherever `findDistinctInvoiceIdsByProjectId` lives — add reverse query) | ~6 lines (one `@Query` method)    |
| Backend  | New unit test: `TrustTransactionServiceTest` — assert FEE_TRANSFER infers projectId from single-matter invoice; REFUND honours request.projectId() | ~30 lines (2 test methods, reuse existing fixtures) |
| Frontend | `lib/schemas/trust.ts` (1 line in `recordRefundSchema`)                                                  | 1 line                            |
| Frontend | `components/trust/RecordRefundDialog.tsx` (defaults + 1 FormField block)                                 | ~16 lines                         |
| Frontend | `app/(app)/org/[slug]/trust-accounting/transactions/actions.ts` (`recordRefund` action — verify projectId pass-through; likely 0–1 line) | 0–1 line |

**Migrations**: none. `trust_transactions.project_id` column already exists
(see `V85__create_trust_accounting_tables.sql` per QA's grep) and is
nullable.

**NEEDS_REBUILD**: `true` (backend Java change requires
`bash compose/scripts/svc.sh restart backend`; frontend HMR picks up TSX
changes automatically).

## Verification

### Re-walk PRE-FLIGHT-C after fix lands

After the dev branch merges + backend restart:

1. **Cleanup the existing dirty rows from PRE-FLIGHT-C** — two trust
   transactions are APPROVED with `project_id IS NULL` and they will
   permanently obscure the gate because the gate sums signed credits and
   debits across the matter. The tenant-isolated cleanup is a one-off
   reversal pair driven through the same UI we're fixing:
   - Sign in as Bob → Trust Accounting → Transactions → locate
     `f9f3f7a4-…` (FEE_TRANSFER R 1 250 ref `FEE-TRANSFER-INV-0001-DAY60-RETRY`)
     → click `Reverse` → reason "L-69 cleanup — original row had project_id NULL".
     Sign in as Thandi → approve the reversal.
   - Same for `f215423c-…` (REFUND R 68 750 ref `REFUND-DLAMINI-CLOSURE-DAY60`)
     — reverse → approve.
   - Net DB state: customer ledger card returns to R 70 000 (the ledger
     deltas net to zero across original+reversal pairs since reversals
     mirror the original with opposite sign), and matter trust gate
     returns to RED at R 70 000 — the **legitimate** pre-disposition
     state. Now we can re-walk the disposition with the fix in place.

   _If `Reverse` is unavailable for APPROVED transactions
   (`TrustTransactionService.java:941` includes FEE_TRANSFER+REFUND in
   `REVERSIBLE_TYPES`, so it should be), QA may instead drive a fresh
   compensating fee transfer + deposit pair — but reverse is cleaner._

2. **Re-walk the disposition with the fix:**
   - Bob → Trust Accounting → Record Transaction → Record Fee Transfer
     → customerId=Sipho, invoiceId=INV-0001, amount=R 1 250,
     reference=`FEE-TRANSFER-INV-0001-DAY60-FIXED` → Thandi approves.
   - Bob → Trust Accounting → Record Transaction → Record Refund
     → customerId=Sipho, **matterId=`e788a51b-…` (RAF-2026-001)** ← NEW
     field, amount=R 68 750, reference=`REFUND-DLAMINI-CLOSURE-DAY60-FIXED`,
     description=Refund residual on closure → Thandi approves.

3. **Closure-gate re-walk:** open Close Matter on RAF-2026-001 →
   trust gate must render **GREEN** at `Matter trust balance is R0.00.`
   All 9 gates GREEN → click Close → matter closed → CLOSURE-EXECUTE
   unblocked.

4. **DB confirmations** (read-only SELECT):
   ```sql
   -- new FEE_TRANSFER row should have project_id = e788a51b-... (inferred from invoice)
   SELECT id, transaction_type, project_id, status FROM tenant_5039f2d497cf.trust_transactions
     WHERE reference = 'FEE-TRANSFER-INV-0001-DAY60-FIXED';
   -- new REFUND row should have project_id = e788a51b-... (carried from form)
   SELECT id, transaction_type, project_id, status FROM tenant_5039f2d497cf.trust_transactions
     WHERE reference = 'REFUND-DLAMINI-CLOSURE-DAY60-FIXED';
   -- gate calc returns 0
   SELECT SUM(CASE WHEN transaction_type IN ('DEPOSIT','TRANSFER_IN') THEN amount ELSE -amount END)
     FROM tenant_5039f2d497cf.trust_transactions
     WHERE project_id = 'e788a51b-3a73-...' AND status = 'APPROVED';
   ```

### Backend unit tests to add (in fix PR)

- `TrustTransactionServiceTest.recordFeeTransfer_singleMatterInvoice_inheritsProjectIdFromInvoiceLine()`
- `TrustTransactionServiceTest.recordFeeTransfer_multiMatterInvoice_leavesProjectIdNull()`
- `TrustTransactionServiceTest.recordRefund_withProjectId_persistsProjectId()`
- `TrustTransactionServiceTest.recordRefund_withoutProjectId_persistsNullProjectId()` (preserves backward-compat for non-matter refunds)

## Estimated Effort

**S–M (~1.5–2 hr)**:

- Backend: ~30 min — record DTO addition (REFUND), 2 one-line service edits,
  1 new repository query, 4 unit tests (reuse existing fixtures).
- Frontend: ~20 min — schema 1 line, dialog 1 FormField copy from deposit.
- Backend restart + smoke (record one fee transfer through Trust UI, confirm
  `project_id` populates in DB): ~10 min.
- Buffer for the cleanup-via-reversal walk before QA re-walks the gate: ~30 min.

Well under the verify-cycle SPEC_READY bar.

## Acknowledged out-of-scope (Sprint 2 follow-ups)

- **L-69-followup-A**: typeahead matter combobox component for the trust
  dialog family — replace free-text UUID inputs across deposit, payment,
  refund, transfer.
- **L-69-followup-B**: auto-fill matter context when refund dialog is
  opened from `?tab=trust` matter scope (and add a Refund quick-action to
  `TrustBalanceCard` matter-tab variant).
- **L-69-followup-C**: multi-matter invoice support for FEE_TRANSFER —
  prompt user to allocate the transferred amount across matters when
  the invoice spans more than one project; today inference falls through
  to `null` for that edge case.
- **L-69-followup-D** (capability side-finding from PRE-FLIGHT-C): only
  `owner` role has `APPROVE_TRUST_PAYMENT` — single-owner firms cannot
  approve any trust transaction they record (combined with the
  recorder-sole-approver rule). Decide whether to grant the capability
  to `admin` by default, or document the dual-approval requirement
  explicitly. Not opened as a separate gap because Mathebula has both
  roles available and QA worked around it cleanly.
