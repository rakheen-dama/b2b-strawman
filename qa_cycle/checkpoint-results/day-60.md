# Day 60 — Firm matter closure + generate Statement of Account [FIRM]

**Date**: 2026-05-21
**Actor**: Thandi Mathebula (Owner)
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443)
**Matter**: Dlamini v Road Accident Fund (RAF-2026-001, ID: 85b09bb3-5cdd-42b9-8364-1bea1e83153d)

---

## Phase A: Settle matter finances before closure

### 60.1 — Generate final fee note for remaining unbilled time/disbursements

**Result**: NO-OP (no unbilled items exist)

All time entries (4.0h by Bob) and the sheriff's fee disbursement (R 1,250) were billed in Day 28's INV-0001 (PAID via mock payment gateway on Day 30). The closure gate report confirms: "Final bill issued with no unbilled items" (GREEN). The scenario's narrative assumption of "one more small fee note R 15,000" does not apply because there are no actual unbilled items to generate a fee note from.

### 60.2 — Fee Transfer Out / Trust payment to clear trust balance

**Result**: PARTIAL — payment recorded but AWAITING_APPROVAL (dual-approval constraint)

- Trust balance at start of Day 60: **R 70,000.00** (3 deposits: R 50,000 Day 10 + R 20,000 Day 45 + inherited carry-over adjustments)
- Recorded trust payment PAY/2026/001 for R 70,000 via Record Payment dialog on matter Finance > Trust tab
- Payment status: **AWAITING_APPROVAL** — the trust account has dual-approval enabled (Section 86 compliance)
- Error when Thandi attempts to approve: **"The transaction recorder cannot be the sole approver. A different member with APPROVE_TRUST_PAYMENT capability must approve this transaction."**
- This means a second user (Bob or Carol) must log in and approve the payment before the trust balance can be zeroed
- Two duplicate payment submissions were rejected (artifact of testing the dialog interaction)

### 60.3 — Approve fee transfer

**Result**: BLOCKED — requires user switch to Bob/Carol for dual approval

---

## Phase B: Run matter closure workflow

### 60.4 — Click Close Matter in sidebar footer

**Result**: PASS — button located at `data-testid="sidebar-lifecycle-action"` area in left sidebar footer

The "Close Matter" button is correctly located in the sidebar footer per Phase 73 relocation. Clicking it opens the closure dialog.

### 60.5 — Closure dialog Step 1: Gate report

**Result**: PARTIAL — gate report renders correctly but shows 4 blocking gates

Gate report findings:

| # | Gate | Status | Detail |
|---|------|--------|--------|
| 1 | Trust balance | FAIL | R 70,000 — must transfer to client/office before closure |
| 2 | Disbursements approved | PASS | All disbursements approved |
| 3 | Disbursements settled | PASS | All approved disbursements settled |
| 4 | Final bill issued | PASS | No unbilled items |
| 5 | Court dates | FAIL | 1 court date scheduled for today or later (Pre-Trial Jun 4 2026) |
| 6 | Prescription timers | PASS | No prescription timers running |
| 7 | Open tasks | FAIL | 9 tasks remain open |
| 8 | Info requests | FAIL | 1 client information request outstanding (REQ-0003 IN_PROGRESS) |
| 9 | Document acceptances | PASS | No document acceptances pending |

Each failing gate has a "Fix this" link pointing to the correct sub-tab for remediation.

### 60.6–60.11 — Complete closure, SoA generation, retention policy

**Result**: NOT REACHED — blocked by 4 open gates

---

## BLOCKER: OBS-6001 — Matter closure requires resolving 4 gates before clean-path closure

**Severity**: BLOCKER (cascading — blocks Day 60, 61, and all subsequent days)

**Root cause**: The Day 60 scenario assumes a "clean happy path" closure, but the current state of the RAF matter has 4 unresolved gates:

1. **Trust balance R 70,000** — payment recorded but requires dual approval (different user must approve). This is a correct Section 86 compliance behaviour, not a bug. The scenario did not account for the dual-approval requirement when planning the single-user Day 60 flow.

2. **Court date Jun 4 2026** — scheduled in Day 21, still active. The scenario does not include a step to cancel/complete the court date before closure.

3. **9 open tasks** — the RAF matter template created 9 tasks on Day 3. None were completed during the lifecycle (only time was logged against 2 of them). The scenario does not include a step to complete/close tasks.

4. **REQ-0003 outstanding** — the second info request (Day 45) has status IN_PROGRESS (2/2 items submitted by Sipho on Day 46, but firm has not reviewed/accepted them like REQ-0001 was on Day 5). The scenario does not include a firm review step for REQ-0003.

**Impact**: Cannot proceed to closure (60.6), SoA generation (60.8–60.10), portal SoA download (Day 61), or any subsequent days.

**Resolution options**:
- (A) Fix all 4 gates: approve trust payment (Bob), complete/cancel court date, close 9 tasks, review REQ-0003
- (B) Amend scenario to include pre-closure gate resolution steps
- (C) Test with "Continue" override if the product allows closing with open gates (the gate report has a "Continue" button — unclear if it forces through or requires all green)

**Recommendation**: Option (A) — resolve all gates as a multi-user orchestrated flow, then proceed with closure. The "Continue" button in the gate report dialog is present despite red gates, suggesting the product may allow an override close. Worth testing, but the proper path is to resolve the gates.

---

## Console Errors

- `/api/assistant/invocations` 404 (recurring, AI assistant endpoint not deployed — pre-existing, not a Day 60 issue)
- No JavaScript errors from application code

## New Gaps

| Gap ID | Summary | Severity | Status |
|--------|---------|----------|--------|
| OBS-6001 | Matter closure requires resolving 4 gates (trust dual-approval, court date, 9 open tasks, REQ-0003 review) before clean-path closure can proceed | BLOCKER | OPEN |

## Observations

1. **Close Matter button location**: Correctly in sidebar footer per Phase 73 — PASS
2. **Gate report rendering**: All 9 gates render correctly with accurate status — PASS
3. **"Fix this" links**: Each failing gate has a working link to the correct remediation page — PASS
4. **Generate Statement of Account button**: Visible and available in the toolbar area (separate from closure flow) — noted but not yet tested
5. **Trust dual-approval**: Working correctly per Section 86 — not a bug, but a scenario gap
6. **Trust payment dialog**: Form validation and submission work correctly (react-hook-form + zod + server action), but initial attempts appeared to fail silently because the balance doesn't update until the payment is approved
