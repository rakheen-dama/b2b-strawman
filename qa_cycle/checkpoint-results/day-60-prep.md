# Day 60 Prep — Settle matter for closure happy-path (FIRM)

**Branch**: `bugfix_cycle_2026-04-30b`
**Cycle**: 19 (2026-04-30)
**Actor**: Bob Ndlovu (Admin) for steps 1–4; Thandi Mathebula (Owner) for step 5 (trust payment approval).

## Result: PASS — All 5 prep steps complete; closure gates ALL GREEN.

## Step 1 — Close 9 open RAF tasks: PASS

Backend `TaskStatus` enum (`OPEN, IN_PROGRESS, DONE, CANCELLED`). Closure gate `AllTasksResolvedGate` accepts BOTH terminal states (DONE or CANCELLED).

UI flow: matter Tasks tab → click task → status combobox in detail dialog. Combobox dropdown only exposes `Open / In Progress / Cancelled` directly; `DONE` is reached via a separate `Mark Done` button that appears when status is `IN_PROGRESS` (per `task-detail-header.tsx:51` `canMarkDone = task.status === "IN_PROGRESS" && canChangeStatus`).

Initial approach was to walk OPEN → IN_PROGRESS → Mark Done for the first task ("Initial RAF claim assessment & instructions"). This succeeded but the original task has a recurrence rule that auto-spawns a "Follow-up: <title>" task on `complete` (per `TaskService.createRecurringNextInstance`, only fires on DONE, not CANCELLED). After 3 originals were marked Done, 3 new "Follow-up:" tasks appeared.

Switched to canceling the rest (OPEN → CANCELLED, no recurrence spawn). Eventually all 9 originals + 3 spawned follow-ups were terminal (3 DONE, 9 CANCELLED). Final state on the Tasks tab with default `Open + In Progress` filter: **0 rows**.

Verified via the closure dialog gate: `All tasks resolved.` (green check).

Evidence: `qa_cycle/evidence/day-60/tasks-zero-open-fixed.png`.

## Step 2 — Mark court date complete (cancelled): PASS

Matter Court Dates tab → Pre-Trial 2026-05-14 row → row Actions menu (kebab) → 4 options: Edit / Postpone / Cancel / Record Outcome.

Used `Cancel` option. Cancel dialog opens with `Reason` textarea. Set reason `"Matter concluding via settlement; pre-trial no longer required."` → click Cancel Court Date → row status transitions `Scheduled → Cancelled`.

Verified via closure dialog: `No court dates scheduled for today or later.` (green check).

Note: URL kebab-case quirk — scenario `?tab=court_dates` does NOT switch tab (server falls back to Overview). Canonical key per `frontend/components/projects/project-tabs.tsx:80` is **`court-dates`** (kebab). Using `?tab=court-dates` works. Triaged: scenario amend (cosmetic; clicking the tab in UI also works without the URL hint).

## Step 3 — Firm reviews + accepts REQ-0003: PASS

Bob navigates to `/org/mathebula-partners/information-requests/1a02aaa4-...` → REQ-0003 detail page → 2 items both render `Submitted` status with Download / Accept / Reject buttons. Click `Accept` on item 1 → click `Accept` on item 2 (button list re-rendered each time). Envelope auto-transitions:

- Before: `0/2 accepted, status In Progress`
- After: `2/2 accepted, status COMPLETED, Completed on Apr 30, 2026`

Both items show `Accepted` status with `test-doc.pdf` download link. Envelope status `COMPLETED` confirms the lifecycle (Sent → IN_PROGRESS → COMPLETED) per OBS-403 design.

Verified via closure dialog: `All client information requests closed.` (green check).

Evidence: `qa_cycle/evidence/day-60/req-0003-completed.png`.

## Step 4 — Bulk-bill DRP-002 R 500 → INV-0002 → PAID: PASS

`/org/mathebula-partners/invoices/billing-runs/new` → wizard 5-step flow:
1. **Configure**: period 2026-04-30 → 2026-05-31, no cut-off, no retainer, no notes → Next.
2. **Select Customers**: Sipho Dlamini auto-listed with Unbilled Time R 0,00 + Unbilled Expenses R 500,00 + Total R 500,00 (matches DRP-002 sheriff fee). 1 customer selected → Next.
3. **Review & Cherry-Pick**: 1 customer included R 500 → Next.
4. **Generate**: status auto-advances to `COMPLETED` (Run UUID `0504ec7b-36d1-498b-a27d-4bdb3e7493cb`); INV-0002 emitted UUID `ae0ebc64-f446-4c51-8cdf-1018331e1394`.
5. **View Invoice** link → INV-0002 Draft Fee Note R 500.00 (1 EXPENSE row, ZAR, no tax).

INV-0002 lifecycle:
- Draft → click `Approve` → status `Approved`.
- Click `Send Fee Note` → OBS-2102 prereq dialog appears ("✗ Tax Number is required to send an invoice") → click `Send Anyway` → status `Sent`.
- Click `Record Payment` → inline form opens (Reference optional, Confirm Payment / Cancel) → click `Confirm Payment` → status `Paid`.
- Payment History shows: STATUS=Completed / PROVIDER=Manual / REFERENCE=`MOCK-MANUAL-243c782e-2dd5-4749-b05b-94f40690c7a7` / AMOUNT=R 500,00 / DATE=Apr 30, 2026.

(A second `Cancelled / mock` row appears in Payment History reflecting the unused mock-payment online-link that was auto-created and superseded by the Manual recording — cosmetic, doesn't impact totals.)

Verified via closure dialog: `Final bill issued with no unbilled items.` AND `All approved disbursements are settled.` (both green).

Evidence: `qa_cycle/evidence/day-60/inv-0002-paid.png`.

## Step 5 — Transfer R 71,000 trust to Sipho: PASS

Matter Trust tab → balance R 71,000 → 3 actions (Record Deposit / Record Payment / Fee Transfer). Used **Record Payment** (refund-style trust payment to client, not a fee transfer because all fee notes are already paid via mock-payment / manual recording).

Form fields (Bob session):
- Client: Sipho Dlamini (locked from matter context)
- Matter: Dlamini v Road Accident Fund (locked)
- Amount: 71000
- Reference: TPM/2026/001
- Description: "Refund of trust funds to Sipho Dlamini on matter closure (Day 60)."
- Transaction Date: 2026-04-30 (default)

Submit → dialog closes → record created in `AWAITING APPROVAL` status (R 71,000 exceeds the auto-approve threshold).

Bob attempted to approve via `/trust-accounting/transactions?status=AWAITING_APPROVAL` → got toast `Insufficient permissions for this operation` (Admin role lacks trust-payment-approval capability — owner-only per Section 86 dual-control conventions).

**Switched session**: signed out Bob → KC sign-in as Thandi (`thandi@mathebula-test.local` / `<redacted>`) → user menu shows TM. Returned to `/trust-accounting/transactions?status=AWAITING_APPROVAL` → TPM/2026/001 row → click `Approve` → status `APPROVED`, row now shows `Reverse` button.

Reload matter Trust tab:
- **Funds Held**: R 0,00 (No Funds)
- Deposits: R 71 000,00
- Payments: R 71 000,00
- Fee Transfers: R 0,00

Verified via closure dialog: `Matter trust balance is R0.00.` (green check).

Evidence: `qa_cycle/evidence/day-60/trust-balance-zero.png`.

## Closure Gate Verification (Step 6 — readiness check, NOT closure execution)

After all 5 prep steps, opened the Close Matter dialog as Thandi. Step 1 gate report renders **ALL 9 GATES GREEN**:

1. ✓ Matter trust balance is R0.00.
2. ✓ All disbursements approved.
3. ✓ All approved disbursements are settled.
4. ✓ Final bill issued with no unbilled items.
5. ✓ No court dates scheduled for today or later.
6. ✓ No prescription timers still running.
7. ✓ All tasks resolved.
8. ✓ All client information requests closed.
9. ✓ No document acceptances pending.

Continue button is enabled. **Day 60 happy-path closure execution is now runnable** in the next dispatch (Step 2 of the dialog: pick Reason `Concluded` + leave SoA + closure-letter checkboxes checked + click `Confirm Close`).

Did NOT click Continue / Confirm Close in this cycle — that's the formal Day 60 happy-path execution. Cancelled the dialog instead.

Evidence: `qa_cycle/evidence/day-60/closure-gates-all-green.png`.

## Defects filed during Day 60 prep

### OBS-2105 (Medium severity, cosmetic) — Matter detail header layout collapse

**Symptom**: On `/org/{slug}/projects/{id}` (and any `?tab=*` variant), the matter title and description column is squashed to ~110px width while sibling action buttons (Close Matter / Generate SoA / Complete Matter / ⋯ / Generate Document / New Engagement Letter / Save as Template / Edit) consume the remaining ~1200px. Result: "Dlamini v Road Accident Fund" wraps to 3 lines, description wraps letter-by-letter for ~30 lines.

**Root cause** (verified via DOM walk):
- Title container `div.min-w-0.flex-1` has computed width 0 (`flexBasis: 0%, flexShrink: 1`)
- Action buttons sibling `div.flex.items-center.gap-3` consumed all 1200px of the parent flex row
- Parent `div.flex.items-start.justify-between.gap-4` allocates space content-first to actions

**Reproduction**: Visit any matter detail page at viewport ≥1280px. Title visibly wrapped narrow.

**Impact**: Cosmetic only — content is functional, all buttons clickable, all data accessible. Daily-use UX poor; demo screenshot quality degraded.

**Triaged**: Filed for next bug-fix cycle. NOT a blocker for Day 60 closure happy-path. Spec at `qa_cycle/fix-specs/OBS-2105.md` (to be authored by Product agent). Evidence: `qa_cycle/evidence/day-60/matter-overview-after-tasks.png`, `qa_cycle/evidence/day-60/trust-balance-zero.png`, `qa_cycle/evidence/day-60/closure-gates-all-green.png` (dialog OK; matter behind it shows the collapse).

### Triage notes (not bugs, scenario amends)

- **Recurring task spawn during closure**: Marking `DONE` on the 9 original RAF tasks (which carry recurrence) auto-spawns "Follow-up:" tasks. These break naive "close all tasks" demo flows. Workaround: cancel rather than complete during closure prep, OR clear recurrence rule first. Spec amend: scenario Day 60 prep checklist should advise "Cancel" for non-billable closure cleanup.
- **`?tab=court_dates` ignored**: Canonical URL key is `court-dates` (kebab). Scenario URL hints with snake_case fall back to Overview tab. Cosmetic.
- **Trust payment approval requires Owner role**: Admin (Bob) gets `Insufficient permissions` on trust transaction approval. Section 86 dual-control behaviour is correct; scenario amend to specify "Thandi approves" (she's already the actor in scenario Day 60.3).

## Console + Network

All page navigations during prep work registered 0 console errors. The matter detail page showed 1 routing-related console message (favicon 404, pre-existing dev-only).

## Summary Table

| Step | Action | Outcome |
|------|--------|---------|
| 1 | Close 9 open RAF tasks (3 DONE + 9 CANCELLED with follow-up spawn handling) | PASS — gate `All tasks resolved.` green |
| 2 | Cancel Pre-Trial 2026-05-14 court date | PASS — gate `No court dates scheduled for today or later.` green |
| 3 | Accept REQ-0003 items (firm review, both items + envelope auto-completes) | PASS — gate `All client information requests closed.` green |
| 4 | Bulk-bill DRP-002 → INV-0002 → Paid (R 500 manual record) | PASS — gates `Final bill issued with no unbilled items.` + `All approved disbursements are settled.` both green |
| 5 | Trust payment R 71,000 (Bob records, Thandi approves) | PASS — gate `Matter trust balance is R0.00.` green |
| Final | Close Matter dialog opened — all 9 gates green | READY for happy-path closure execution next dispatch |

## QA Position post-prep

- **Day**: 46 (PASS) + Day 60 prep (PASS).
- **Closure happy-path**: ALL 9 GATES GREEN. Next QA dispatch can run Day 60 happy-path: Continue → pick Reason `Concluded` → leave SoA + closure-letter checked → Confirm Close → matter status `CLOSED`, retention row inserted, SoA + closure letter PDFs generated, Mailpit email to Sipho.
- **Carry-over entities**:
  - REQ-0003: COMPLETED, 2/2 accepted (was Sent, 0/2)
  - DRP-002 R 500: BILLED via INV-0002 → PAID (was Approved, Unbilled)
  - INV-0002 (`ae0ebc64-...`): R 500 PAID via Manual record
  - 9 RAF tasks + 3 follow-ups: ALL TERMINAL (3 DONE, 9 CANCELLED)
  - Court date 2026-05-14: CANCELLED
  - Trust balance: R 0,00 (was R 71,000)
  - TPM/2026/001 R 71,000: APPROVED Payment to Sipho
- **New defect**: OBS-2105 (matter detail header layout collapse, medium cosmetic).
- **Stack health**: backend / gateway / frontend / portal all green.
