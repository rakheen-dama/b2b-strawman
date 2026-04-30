# Day 45 — QA Verification Cycle 17 (2026-04-30)

**Branch**: `bugfix_cycle_2026-04-30`
**Actor**: Thandi (Owner) at firm `:3000`
**Stack health (pre-test)**: backend 99563 / gateway 18539 / frontend 68198 / portal 18737 — all RUNNING + HEALTHY.

## Step 1 — Verify three cosmetic fixes landed via main

### OBS-2103 — Edit + Archive co-existence on customer detail action row
**Result: PARTIAL — FAIL on click handler. Marked REOPENED.**

- Sipho (`a30bb16b-…`, Active/Active/INDIVIDUAL): BOTH Edit + Archive buttons RENDER side-by-side in the action row.
- Moroka (`f09d5032-…`, Active/Prospect/TRUST): BOTH Edit + Archive buttons RENDER side-by-side.
- **Click Edit on Sipho** → dialog does NOT open. React fiber inspection confirms the Edit `<button>`'s `__reactProps$` has no `onClick` attribute (only `data-slot`, `data-variant`, `data-size`, `className`, `children`). React.cloneElement-injected onClick was stripped during commit.
- **Click Archive on Sipho** → AlertDialog opens correctly ("Archive Customer? … Cancel / Archive"). Cancelled out without archiving.
- **Click Edit on Moroka** → dialog OPENS correctly (Moroka's Edit button DOES have onClick; Archive on Moroka does NOT — opposite-side stripping).
- Pattern: one of the two `<EditCustomerDialog>` / `<ArchiveCustomerDialog>` cloneElement onClick injections survives commit; the other is dropped. Side flips based on customer (status-gated `<ArchiveCustomerDialog>` rendering position changes the React element ordering).

**Original bug** (mutual visibility) IS resolved: both buttons render together. The fix uses `React.cloneElement(children, { onClick })` instead of Radix `<DialogTrigger asChild>` Slot. Visibility succeeded; click-handler injection is fragile under sibling Dialog/AlertDialog clone — only ONE wins per render. New sub-bug filed as **OBS-2103b** (root cause: cloneElement-onClick + lazy/RSC `_payload`/`_init` children pattern; the children prop on EditCustomerDialog reads as a thenable React element with `null` `props` accessor, so `cloneElement` produces an element without onClick. ArchiveCustomerDialog receives an already-resolved children element, so its cloneElement adds onClick correctly).

Per QA cycle protocol "If any FAIL → mark REOPENED, stop, return" — OBS-2103 is REOPENED.

Evidence: `qa_cycle/evidence/day-45/obs-2103-verify-sipho-edit-archive.png` (both buttons visible on Sipho), `obs-2103-verify-moroka-edit-archive.png` (both visible on Moroka), `obs-2103-FAIL-sipho-edit-no-onclick.png` (Archive AlertDialog opens; Edit click does nothing).

### OBS-2104b — wizard step 2 totals correct (no Cartesian inflation)
**Result: PASS (VERIFIED).**

- Created fresh disbursement on RAF-2026-001: R 500,00 Sheriff Fees "Day 45 verify-OBS-2104b/c second sheriff service" / Sheriff Pretoria, then `Submit for Approval` → `Approve`. Status: Draft → Pending → **Approved**.
- New Billing Run wizard, period `2026-04-01 → 2026-05-31`, step 2 "Select Customers" lists Sipho with: Unbilled Time R 0,00 / **Unbilled Expenses R 500,00** / Total R 500,00.
- Pre-fix the same shape (1 disbursement × 9 RAF tasks rows in matter) would inflate to R 4,500. Now correctly de-duplicated by per-source CTE aggregation (PR #1240 `BillingRunPreviewCardinalityTest`).

Evidence: `qa_cycle/evidence/day-45/obs-2104b-verify-totals-correct.png`.

### OBS-2104c — wizard step 3 cherry-pick shows Disbursements section
**Result: PASS (VERIFIED).**

- Continued wizard from step 2 (Sipho selected, R 500 total) → Next → step 3 "Review & Cherry-Pick" loads.
- Sipho row expanded → renders a **Disbursements** section heading (`<h3>Disbursements</h3>`) above an attribute-rich row table with columns Include / Date / Description / Category / Supplier / Amount.
- The R 500,00 disbursement appears with the Include checkbox checked by default; subtotal reads `Subtotal: R 500,00`.
- One non-blocking React console warning in CherryPickStep: `Cannot update a component (Router) while rendering a different component (CherryPickStep). … setState() in render. CherryPickStep @ chunks/0v0__next_dist_0hl3d8v._.js:3272`. Cosmetic only — the section renders correctly. Filed as **OBS-2104c2** (LOW, dev console only) for later triage.

Evidence: `qa_cycle/evidence/day-45/obs-2104c-verify-disbursements-section.png` (full-page screenshot showing Disbursements section with all expected columns).

## Step 2 — Day 45 Forward Execution
**SKIPPED.** OBS-2103 REOPENED → protocol mandates stop after first failed verification.

## Summary
| Gap | Verification | Outcome |
|-----|--------------|---------|
| OBS-2103 | Edit + Archive co-existence + clickable | **FAIL** — visibility fixed, click handler stripped from one of the two siblings; **REOPENED** as OBS-2103b |
| OBS-2104b | Wizard step 2 expense total un-inflated | **PASS** — VERIFIED (R 500,00 single, not R 4,500 ×9) |
| OBS-2104c | Wizard step 3 cherry-pick has Disbursements section | **PASS** — VERIFIED (heading + table + checkbox + subtotal) |

## QA Position
- **Day**: still at Day 30 — Day 45 forward not started (blocked on OBS-2103 retry-of-retry).
- 2 of 3 cosmetic fixes verified; 1 needs another attempt at the right layer.
- Stack remains healthy. No new entities created beyond the R 500,00 disbursement (which can be re-used for Day 28 retry-of-retry once OBS-2103b lands).
