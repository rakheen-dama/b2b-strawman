# Fix Spec: GAP-L-63 — Disbursements not surfaced in per-customer Generate Fee Note dialog

## Problem

Day 28 Checkpoint 28.2 (per-customer path) lands on `/customers/<Sipho-id>?tab=invoices`
→ New Fee Note → Generate Fee Note, reaches the "Select Unbilled Items"
review step (step 2) and sees only **5 time entries** — the R 1 250,00
Sheriff Fees disbursement created on Day 21
(`legal_disbursements.c9986a8f-e332-4dc4-8da5-f8a99b673bca`) is completely
absent. Evidence: `qa_cycle/checkpoint-results/day-28.md:35` and:36 —
"No disbursements section in this dialog — the R 1 250,00 Sheriff Fee from
Day 21 is not surfaced by the per-customer Generate Fee Note flow at all."

Dialog subtitle advertises "from unbilled time entries **and expenses**" —
but even that is partially misleading because the legal-za vertical also has
a separate `legal_disbursements` track (distinct from `expenses`) which is the
correct source for sheriff fees, court stamps, expert witness costs, etc.
The feature IS shipped on the backend (see Root Cause below) — the gap is
purely on the frontend dialog.

Scenario 28.2 explicitly requires: "Unbilled disbursements: R 1,250
(sheriff's fee)" as part of the fee-note preview line items. Without this
fix, Day 28 cannot mirror the real-world partner workflow for the legal
vertical even after GAP-L-60 lands.

## Root Cause (confirmed, not hypothesised)

### Backend: fully wired, no change needed

**File:** `backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/UnbilledTimeService.java`
lines 361–370:

```java
// Module-gated: only tenants with the `disbursements` module enabled (legal vertical) see a
// populated disbursements list. Non-legal tenants get an empty list — byte-compatible with the
// pre-487A response shape.
List<UnbilledDisbursementDto> unbilledDisbursements = List.of();
if (moduleGuard.isModuleEnabled(DISBURSEMENTS_MODULE_ID)) {
  unbilledDisbursements =
      disbursementRepository.findUnbilledBillableByCustomerId(customerId, null).stream()
          .map(UnbilledDisbursementDto::from)
          .toList();
}
```

The response DTO already carries a `disbursements` field
(`backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/UnbilledTimeResponse.java:23`).
The `POST /api/invoices` body already accepts `disbursementIds`
(`backend/src/main/java/io/b2mash/b2b/b2bstrawman/invoice/dto/CreateInvoiceRequest.java:16`)
and `InvoiceCreationService.createDisbursementLines`
(lines 961+) writes the line items. Module gate `disbursements` is enabled
for the legal-za vertical (confirmed — L-57 was VERIFIED with the same
module gate on Day 21).

### The data is there — verified via repository query

`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/disbursement/DisbursementRepository.java:38-48`:

```java
@Query("""
  SELECT d FROM LegalDisbursement d
  WHERE d.customerId = :customerId
    AND d.approvalStatus = 'APPROVED'
    AND d.billingStatus = 'UNBILLED'
    AND (:projectId IS NULL OR d.projectId = :projectId)
  ORDER BY d.incurredDate ASC, d.createdAt ASC, d.id ASC
""")
```

**Critical filter detail:** the query requires `approval_status = 'APPROVED'`
**AND** `billing_status = 'UNBILLED'`. The QA-created row
(`c9986a8f-e332-4dc4-8da5-f8a99b673bca`, per status.md:113) is
`approval_status=DRAFT`, `billing_status=UNBILLED`. So today that row would
NOT be returned by the backend even if the frontend rendered the section.
The Day 21 scenario creates the disbursement but doesn't script the
`POST /api/legal/disbursements/{id}/submit` → `POST .../approve` transitions
(DRAFT → PENDING_APPROVAL → APPROVED). The approval UI exists at
`frontend/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client.tsx`
(Submit + Approve buttons via `DisbursementController.submit` / `.approve`).

This spec covers the **code gap** (frontend dialog missing the section). The
approval-flow-not-scripted piece is a separate scenario-scripting note for
QA — see **Verification** step 3 below.

### Frontend: three missing pieces

**File 1 — TS type missing field:**
`frontend/lib/types/invoice.ts` lines 103–111:

```typescript
export interface CreateInvoiceDraftRequest {
  customerId: string;
  currency: string;
  timeEntryIds: string[];
  expenseIds?: string[];
  dueDate?: string;
  notes?: string;
  paymentTerms?: string;
}
```

Missing `disbursementIds?: string[]`. The type
`UnbilledDisbursementEntry` (lines 191–200) already exists and
`UnbilledTimeResponse.disbursements?` (line 184) is present — the fetch side
is wired, only the write side is not.

**File 2 — hook doesn't track disbursement selection or pass IDs:**
`frontend/components/invoices/use-invoice-generation.ts` lines 42, 55, 101–105,
168, 189, 197–198, 229–230, 240–278. The hook declares `selectedEntryIds` and
`selectedExpenseIds` but no `selectedDisbursementIds`. The `handleCreateDraft`
call at line 194 passes `{ customerId, currency, timeEntryIds, expenseIds }`
— no `disbursementIds`. The `runningTotal` and `totalItemCount` computed
values at lines 212–230 omit disbursements entirely.

**File 3 — dialog step 2 doesn't render disbursements section:**
`frontend/components/invoices/invoice-generation-dialog.tsx` lines 191–200
render only the `ExpenseSelectionSection`. No `DisbursementSelectionSection`.
The file already has all the primitives needed (`ExpenseSelectionSection`
at lines 353–456 is a close structural twin).

Note: `frontend/components/invoices/unbilled-summary.tsx` (lines 262–331)
already has a rendered **read-only** disbursements section — but that
component is used on dashboard/summary pages, NOT inside the invoice
generation dialog. Cross-use is tempting but the dialog needs checkbox-driven
selection, which `UnbilledSummary` does not provide. The simpler path is to
add a sibling `DisbursementSelectionSection` in the dialog file (following
the `ExpenseSelectionSection` pattern), not to reshape `UnbilledSummary`.

## Fix

Four-file frontend change. No backend changes.

### File 1: `frontend/lib/types/invoice.ts`

Line 103–111, add `disbursementIds`:

```typescript
export interface CreateInvoiceDraftRequest {
  customerId: string;
  currency: string;
  timeEntryIds: string[];
  expenseIds?: string[];
  disbursementIds?: string[];
  dueDate?: string;
  notes?: string;
  paymentTerms?: string;
}
```

### File 2: `frontend/components/invoices/use-invoice-generation.ts`

Three additions (preserves all existing behaviour for non-legal tenants where
`data.disbursements` is empty/undefined):

1. Declare selection state (after line 42):

```typescript
const [selectedDisbursementIds, setSelectedDisbursementIds] = useState<Set<string>>(new Set());
```

2. Reset in `resetState()` (after line 55):

```typescript
setSelectedDisbursementIds(new Set());
```

3. Pre-select all disbursements on fetch success (after line 105):

```typescript
const matchingDisbursementIds = new Set<string>(
  (result.data.disbursements ?? []).map((d) => d.id)
);
setSelectedDisbursementIds(matchingDisbursementIds);
```

Note: disbursements are single-currency (ZAR-only for legal-za MVP per the
backend comment in `UnbilledSummary.tsx:66`), so no currency-mismatch
filter is needed. If `currency !== "ZAR"`, none of the disbursements should
be pre-selected — add a guard:

```typescript
const matchingDisbursementIds = new Set<string>(
  currency === "ZAR"
    ? (result.data.disbursements ?? []).map((d) => d.id)
    : []
);
```

4. Add toggle handlers (after line 165):

```typescript
function handleToggleDisbursement(disbursementId: string) {
  setSelectedDisbursementIds((prev) => {
    const next = new Set(prev);
    if (next.has(disbursementId)) next.delete(disbursementId);
    else next.add(disbursementId);
    return next;
  });
}

function handleToggleAllDisbursements() {
  if (!unbilledData) return;
  const disbursements = unbilledData.disbursements ?? [];
  const selectableDisbursements = currency === "ZAR" ? disbursements : [];
  const allSelected =
    selectableDisbursements.length > 0 &&
    selectableDisbursements.every((d) => selectedDisbursementIds.has(d.id));

  setSelectedDisbursementIds((prev) => {
    const next = new Set(prev);
    if (allSelected) {
      for (const d of selectableDisbursements) next.delete(d.id);
    } else {
      for (const d of selectableDisbursements) next.add(d.id);
    }
    return next;
  });
}
```

5. Update computed values (around lines 222–230):

```typescript
const disbursementTotal = unbilledData
  ? (unbilledData.disbursements ?? [])
      .filter((d) => selectedDisbursementIds.has(d.id))
      .reduce((s, d) => s + d.amount + d.vatAmount, 0)
  : 0;

const runningTotal = timeTotal + expenseTotal + disbursementTotal;
const totalItemCount =
  selectedEntryIds.size + selectedExpenseIds.size + selectedDisbursementIds.size;
```

6. Pass through to `createInvoiceDraft` (line 194):

```typescript
const result = await createInvoiceDraft(slug, customerId, {
  customerId,
  currency,
  timeEntryIds: Array.from(selectedEntryIds),
  expenseIds: selectedExpenseIds.size > 0 ? Array.from(selectedExpenseIds) : undefined,
  disbursementIds:
    selectedDisbursementIds.size > 0 ? Array.from(selectedDisbursementIds) : undefined,
});
```

7. Also relax `handleRunValidation` and `handleCreateDraft` bail-out guards
   (lines 168 and 189) — today they bail if `selectedEntryIds.size === 0 &&
   selectedExpenseIds.size === 0`. Add disbursements to that check:

```typescript
if (
  selectedEntryIds.size === 0 &&
  selectedExpenseIds.size === 0 &&
  selectedDisbursementIds.size === 0
) return;
```

8. Export the new state + handlers from the returned object (line 240):

```typescript
selectedDisbursementIds,
handleToggleDisbursement,
handleToggleAllDisbursements,
```

### File 3: `frontend/components/invoices/invoice-generation-dialog.tsx`

1. Widen the type import at line 16:

```typescript
import type {
  UnbilledProjectGroup,
  UnbilledTimeEntry,
  UnbilledExpenseEntry,
  UnbilledDisbursementEntry,
} from "@/lib/types";
```

2. After the `ExpenseSelectionSection` render block (after line 200), add:

```tsx
{/* Disbursements section (legal vertical — module-gated; backend returns [] for non-legal tenants) */}
{(h.unbilledData.disbursements ?? []).length > 0 && (
  <DisbursementSelectionSection
    disbursements={h.unbilledData.disbursements ?? []}
    currency={h.currency}
    selectedDisbursementIds={h.selectedDisbursementIds}
    onToggleDisbursement={h.handleToggleDisbursement}
    onToggleAll={h.handleToggleAllDisbursements}
  />
)}
```

3. Below `ExpenseSelectionSection` (after line 456), add the new component —
   model it on `ExpenseSelectionSection`:

```tsx
function DisbursementSelectionSection({
  disbursements,
  currency,
  selectedDisbursementIds,
  onToggleDisbursement,
  onToggleAll,
}: {
  disbursements: UnbilledDisbursementEntry[];
  currency: string;
  selectedDisbursementIds: Set<string>;
  onToggleDisbursement: (id: string) => void;
  onToggleAll: () => void;
}) {
  // Legal disbursements are ZAR-only in the MVP — currency mismatch disables the row.
  const currencyMismatch = currency !== "ZAR";
  const selectableDisbursements = currencyMismatch ? [] : disbursements;
  const allSelected =
    selectableDisbursements.length > 0 &&
    selectableDisbursements.every((d) => selectedDisbursementIds.has(d.id));

  function categoryLabel(category: string): string {
    return category
      .split("_")
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
      .join(" ");
  }

  return (
    <div data-testid="disbursement-selection-section">
      <div className="mb-2 flex items-center justify-between">
        <h3 className="text-sm font-semibold text-slate-700 dark:text-slate-300">Disbursements</h3>
        <button
          type="button"
          onClick={onToggleAll}
          className="text-xs text-teal-600 hover:text-teal-700 dark:text-teal-400"
          disabled={selectableDisbursements.length === 0}
        >
          {allSelected ? "Deselect All" : "Select All Disbursements"}
        </button>
      </div>
      <div className="max-h-60 space-y-1 overflow-y-auto rounded-lg border border-slate-200 dark:border-slate-800">
        {disbursements.map((d) => {
          const disabled = currencyMismatch;
          return (
            <label
              key={d.id}
              className={cn(
                "flex items-center gap-3 px-4 py-2 text-sm",
                disabled
                  ? "cursor-not-allowed opacity-50"
                  : "cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-900/50"
              )}
            >
              <input
                type="checkbox"
                checked={selectedDisbursementIds.has(d.id)}
                onChange={() => onToggleDisbursement(d.id)}
                disabled={disabled}
                className="size-4 rounded accent-teal-600"
              />
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2">
                  <span className="font-medium text-slate-900 dark:text-slate-100">
                    {d.description}
                  </span>
                  <span className="rounded bg-slate-100 px-1.5 py-0.5 text-[10px] font-medium text-slate-700 dark:bg-slate-800 dark:text-slate-300">
                    {categoryLabel(d.category)}
                  </span>
                  {currencyMismatch && <span className="text-xs text-slate-400">(ZAR)</span>}
                </div>
                <div className="text-xs text-slate-500">
                  {d.supplierName} &middot; {formatDate(d.incurredDate)}
                </div>
              </div>
              <span className="shrink-0 text-right font-medium text-slate-700 dark:text-slate-300">
                {safeFormatCurrency(d.amount + d.vatAmount, "ZAR")}
              </span>
            </label>
          );
        })}
      </div>
    </div>
  );
}
```

### File 4: `frontend/app/(app)/org/[slug]/customers/[id]/invoice-actions.ts`

No direct edits needed — `createInvoiceDraft` already accepts a
`CreateInvoiceDraftRequest` and forwards it to `POST /api/invoices` unchanged
(line 77). Once File 1 adds `disbursementIds` to the TS type, this server
action transparently forwards them.

### Tests

Add/extend Vitest coverage for the dialog behaviour:

1. **`frontend/__tests__/components/invoices/invoice-generation-dialog.test.tsx`**:
   add a case "renders disbursements section when API returns disbursements"
   — mock `fetchUnbilledTime` to return `disbursements: [{ id: "d1",
   incurredDate: "2026-04-22", category: "SHERIFF_FEES", description:
   "Sheriff Fees", amount: 1250, vatTreatment: "STANDARD", vatAmount: 187.5,
   supplierName: "Sheriff Pretoria" }]`, advance to step 2, assert the
   section by `data-testid="disbursement-selection-section"` is visible and
   the row shows "Sheriff Fees / R 1 437.50" (amount + VAT).

2. Add a case "createInvoiceDraft passes selected disbursementIds" — submit
   the dialog with one disbursement selected, assert the mock
   `createInvoiceDraft` call payload includes
   `disbursementIds: ["d1"]`.

3. Add a case "disbursements hidden when currency is not ZAR" — set currency
   to "USD" before fetching, assert the section is rendered but rows are
   disabled (or, per the guard above, disbursements aren't pre-selected).

Existing tests at
`frontend/components/invoices/__tests__/unbilled-summary-disbursements.test.tsx`
cover `UnbilledSummary` not the dialog — don't touch them.

## Scope

- **Frontend only.**
- Files to modify:
  - `frontend/lib/types/invoice.ts` (+1 field, 1 line)
  - `frontend/components/invoices/use-invoice-generation.ts` (~50 lines —
    state, handlers, computed values, createDraft call, return object)
  - `frontend/components/invoices/invoice-generation-dialog.tsx` (~80 lines
    — new section invocation + new `DisbursementSelectionSection`
    component, modelled on `ExpenseSelectionSection`)
  - `frontend/__tests__/components/invoices/invoice-generation-dialog.test.tsx`
    (~60 lines — 3 new cases)
- Files to create: none
- Backend files: **none** — fully wired already.
- Migration needed: **no**
- Env / config: none

## Verification

1. **Frontend restart NOT required** — HMR picks up TSX edits. Backend is
   unchanged. Cross-check by hitting `/customers/<Sipho-id>?tab=invoices`
   in the running dev stack post-merge.

2. **Prerequisite for QA re-verify**: the Day 21 Sheriff Fees disbursement
   (`c9986a8f-…`) is currently `approval_status=DRAFT`. To land in the
   dialog's read query, QA must transition it to APPROVED:
   - Navigate to `/org/mathebula-partners/legal/disbursements/c9986a8f-…`
   - Click "Submit for Approval" (DRAFT → PENDING_APPROVAL)
   - Click "Approve" (PENDING_APPROVAL → APPROVED) — requires
     `APPROVE_DISBURSEMENTS` capability; Bob/Owner has it per L-57 evidence.

   This is a **scenario-scripting gap** — Day 21 script should explicitly
   include the submit+approve steps. Note it in day-28.md on re-verify but
   do NOT open a new GAP; the existing disbursement-flow covers it.

3. Run frontend tests:
   ```bash
   pnpm test -- --run invoice-generation-dialog
   ```
   Expect 3 new cases pass, all existing cases remain green.

4. QA re-runs **Day 28 Phase A (per-customer path)** after L-60 lands
   AND the Day-21 disbursement is APPROVED:
   - `/customers/<Sipho-id>?tab=invoices` → New Fee Note → From=2026-04-01,
     To=2026-04-30, Currency=ZAR → Fetch Unbilled Time.
   - **28.2 primary assertion**: dialog step 2 shows TWO sections:
     - "Time Entries" — 5 rows, all checked, R 11 250,00 subtotal
     - "Disbursements" — 1 row (Sheriff Fees, R 1 437,50 incl VAT),
       checked by default.
   - Running total reads R 12 687,50 (11 250 + 1 437.50).
   - Click Validate & Create Draft → Create Draft → dialog closes.
   - DB probe:
     ```sql
     SELECT il.line_type, il.description, il.amount, il.disbursement_id
     FROM tenant_5039f2d497cf.invoice_lines il
     JOIN tenant_5039f2d497cf.invoices i ON il.invoice_id = i.id
     WHERE i.customer_id = '8fe5eea2-75fc-4df2-b4d0-267486df68bd'
     ORDER BY il.sort_order;
     ```
     Expect 6 rows total: 5 × TIME + 1 × TARIFF/DISBURSEMENT (check actual
     `line_type` enum value by inspecting another non-test invoice) with
     `disbursement_id = c9986a8f-…`.
   - `legal_disbursements.c9986a8f-…` row: expect
     `billing_status = BILLED`.

5. **Regression spot-check**:
   - On a non-legal tenant (accounting-za or consulting-za), the
     "Disbursements" section MUST NOT appear (backend returns
     `disbursements: []` due to module gate; the conditional render at
     File 3 / Step 2 above hides the section when the array is empty).
   - Currency=USD test: section renders if data present, but rows disabled
     with ZAR hint.
   - Empty case: if no unbilled disbursements, no section rendered — the
     dialog matches pre-fix behaviour exactly.

## Estimated Effort

**M (60–90 min)** — modest but non-trivial frontend work: type widen, hook
state + handlers + computed values + return object, new dialog sub-component
modeled on an existing twin, 3 new Vitest cases. Clean structural twin to
`ExpenseSelectionSection` means the pattern is already established — reduces
review and regression risk. Well under the 2-hour ceiling.

If Dev discovers `unbilled-summary.tsx`'s disbursement table can be
refactored for reuse inside the dialog (instead of the standalone
`DisbursementSelectionSection`), that's a fine alternative but NOT required.
Ship the simpler sibling-component path first; refactor later if duplication
becomes a pain point.

## Parallelisation Notes

**SAFE for parallel Dev execution alongside GAP-L-60.** Zero file overlap:

- L-60: `backend/.../compliance/CustomerLifecycleGuard.java`,
  `backend/.../compliance/LifecycleAction.java`, +1 backend test.
- L-63: 3 frontend files + 1 frontend test file (all `frontend/`).

Two Dev agents in separate worktrees can merge independently. QA sees the
complete Day 28 unblock only after BOTH land: L-60 allows the draft to
persist, L-63 puts the disbursement line into that draft. Either can merge
first without blocking the other.

## Status Triage

**SPEC_READY.** Smallest Day-28-unblocking scope — no product redesign, no
infrastructure, no backend schema/API changes. Root cause confirmed by
direct code read (`UnbilledTimeService.java:361-370` backend wired;
`invoice-generation-dialog.tsx:191-200` frontend missing;
`use-invoice-generation.ts:42-278` hook state missing;
`invoice-actions.ts:77` server action transparent). Scenario-scripting note
for Day 21 (disbursement approval steps) logged in Verification — not a
separate GAP.
