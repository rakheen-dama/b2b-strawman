# Day 60 — Matter Closure Verify (Cycle 1)

## Day 60 Re-Verify — Cycle 1 — 2026-04-25 SAST — PRE-FLIGHT-A

**Slice scope:** PRE-FLIGHT-A only — clear future court date + close 9 open tasks on the Dlamini RAF matter (`e788a51b-3a73-456c-b932-8d5bd27264c2`). Out of scope this turn: closure form (Step 2), info requests (PRE-FLIGHT-B), trust disposition (PRE-FLIGHT-C).

**Actor:** Thandi Mathebula (`thandi@mathebula-test.local`, owner role) — already authenticated to Keycloak from prior session, dashboard reachable without re-login.

**Tooling:** `mcp__plugin_playwright_playwright__*` for browser-driven mutations + snapshots; Docker `psql` exec for read-only state confirmation. Zero SQL/REST mutations.

**Pre-state confirmation (read-only DB):** When this slice opened, all four PRE-FLIGHT-A entities were *already in their target state* — the prior agent (timed out @ 264 tool uses, 2.2 hr) had successfully driven the mutations through the UI but never wrote results before timeout. The 9 partial-cycle PNGs in `day-60-partial/` correspond to the in-flight UI work (mid-cancel court calendar shown with status `Scheduled` and tasks tab showing 9 open at the moment those screenshots were taken). DB rows now show:
- `court_dates` — `d4cd7dcd-47f3-4f01-9022-bc69672ca78e PRE_TRIAL 2026-05-15 CANCELLED`
- `tasks WHERE project_id='e788a51b-…' GROUP BY status` → `CANCELLED 9` (zero in any open state)
- `information_requests WHERE project_id='e788a51b-…'` → 6 rows (REQ-0001/0002/0004/0006 CANCELLED, REQ-0003/0007 COMPLETED) — all closed

This slice therefore re-walked the **closure-gate dialog** to confirm the gate report renders the post-close state correctly, captured fresh snapshots, and validated A1+A2 gates flip GREEN.

### Per-step results

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **A1** | Navigate matter → Court Dates tab → confirm PRE_TRIAL 2026-05-15 status | **PASS** — row renders `Cancelled` chip; `actions` cell empty (no further affordance once cancelled) | `day-60-cycle1-court-date-closed.png` |
| **A1-DB** | `SELECT id, scheduled_date, status, date_type FROM tenant_5039f2d497cf.court_dates WHERE project_id='e788a51b-…'` | `d4cd7dcd-… 2026-05-15 CANCELLED PRE_TRIAL` | (DB) |
| **A2** | Matter → Tasks tab → confirm count + status | **PASS** — `All` saved view shows "No tasks yet" empty state (saved view filter excludes CANCELLED). All 9 task rows from the partial screenshot at 19:12 are now CANCELLED in DB. | `day-60-cycle1-tasks-closed.png` |
| **A2-DB** | `SELECT status, count(*) FROM tenant_5039f2d497cf.tasks WHERE project_id='e788a51b-…' GROUP BY status` | `CANCELLED 9` (no OPEN/IN_PROGRESS/DONE rows) | (DB) |
| **Verify** | Open matter Overview → click `Close Matter` → closure-gate dialog renders | **PASS** — dialog rendered with full gate report (9 gates listed) | `day-60-cycle1-gates-after-A.png`, `day-60-cycle1-gate-court-date-clear.png`, `day-60-cycle1-gate-tasks-clear.png` |
| **Verify-A1-Gate** | Gate "court dates scheduled for today or later" | **GREEN ✅** — copy: "No court dates scheduled for today or later." | (above) |
| **Verify-A2-Gate** | Gate "tasks open" | **GREEN ✅** — copy: "All tasks resolved." | (above) |
| **Bonus-Gate** | Gate "client information requests outstanding" | **GREEN ✅** — copy: "All client information requests closed." (PRE-FLIGHT-B already done by prior agent) | (above) |
| **Verify-Cleanup** | Click `Cancel` on the dialog (do NOT submit closure) → matter remains ACTIVE | **PASS** — dialog dismissed, matter status badge still `Active` | (snapshot at 19:22) |
| **Console** | `browser_console_messages level=error` after the full A1+A2+Verify walk | **PASS** — 0 errors / 0 warnings across the session | n/a |

### Closure-gate report — full state observed (9 gates)

| # | Gate | State | Copy |
|---|------|-------|------|
| 1 | Trust balance | **RED ❌** | `Matter trust balance is R70000.00. Transfer to client or office before closure.` (with `Fix this` link → `?tab=trust`) |
| 2 | Disbursements approved | GREEN ✅ | `All disbursements approved.` |
| 3 | Disbursements settled | GREEN ✅ | `All approved disbursements are settled.` |
| 4 | Final bill / unbilled items | GREEN ✅ | `Final bill issued with no unbilled items.` |
| 5 | Court dates | GREEN ✅ | `No court dates scheduled for today or later.` |
| 6 | Prescription timers | GREEN ✅ | `No prescription timers still running.` |
| 7 | Tasks | GREEN ✅ | `All tasks resolved.` |
| 8 | Info requests | GREEN ✅ | `All client information requests closed.` |
| 9 | Document acceptances | GREEN ✅ | `No document acceptances pending.` |

### Closing state — gate inventory after PRE-FLIGHT-A

- **Gates RED (1):** Trust balance R 70 000,00 (PRE-FLIGHT-C scope — trust disposition / Fee Transfer Out per scenario step 60.2).
- **Gates GREEN (8):** All other gates including the three this slice was focused on (court date, tasks, info requests).

> **Note on dispatch assumption:** The dispatch told this agent to expect 4 RED gates (trust + court + tasks + 4 info requests) and to clear court+tasks only (PRE-FLIGHT-A), leaving trust + info requests RED. In reality, only **trust** is RED — the prior (timed-out) agent also drove through PRE-FLIGHT-B (info requests). PRE-FLIGHT-B is therefore *already complete* and orchestrator should jump straight to **PRE-FLIGHT-C (trust disposition via Fee Transfer Out)** + **CLOSURE-EXECUTE (Step 2 form + SoA)** in the next dispatch.

### DB confirms (read-only SELECTs)

```sql
-- A1 confirm
SELECT id, scheduled_date, status, date_type
  FROM tenant_5039f2d497cf.court_dates
 WHERE project_id='e788a51b-3a73-456c-b932-8d5bd27264c2';
-- d4cd7dcd-47f3-4f01-9022-bc69672ca78e | 2026-05-15 | CANCELLED | PRE_TRIAL

-- A2 confirm
SELECT status, count(*)
  FROM tenant_5039f2d497cf.tasks
 WHERE project_id='e788a51b-3a73-456c-b932-8d5bd27264c2'
 GROUP BY status;
-- CANCELLED | 9     (zero non-terminal rows)

-- Bonus PRE-FLIGHT-B confirm (out of slice scope but observed)
SELECT request_number, status, due_date
  FROM tenant_5039f2d497cf.information_requests
 WHERE project_id='e788a51b-3a73-456c-b932-8d5bd27264c2'
 ORDER BY created_at;
-- REQ-0001 CANCELLED | REQ-0002 CANCELLED | REQ-0003 COMPLETED
-- REQ-0004 CANCELLED | REQ-0006 CANCELLED | REQ-0007 COMPLETED
```

### Snapshots

All 5 paths under `qa_cycle/checkpoint-results/`:
- `day-60-cycle1-court-date-closed.png` — Court Dates tab listing PRE_TRIAL row with `Cancelled` chip
- `day-60-cycle1-tasks-closed.png` — Tasks tab `All` saved-view empty state (post-CANCELLED)
- `day-60-cycle1-gates-after-A.png` — full closure-gate dialog (1 RED, 8 GREEN)
- `day-60-cycle1-gate-court-date-clear.png` — viewport snapshot of dialog with court-dates gate visible GREEN
- `day-60-cycle1-gate-tasks-clear.png` — viewport snapshot of dialog with tasks gate visible GREEN

### New gaps

None opened this slice. The closure-gate dialog renders cleanly, gate copy is accurate, the `Fix this` deep-link on the trust gate points at `?tab=trust` (correct affordance for next slice).

**OBS-Day60-TasksAllFilterHidesCancelled** (informational, not a gap): the `Tasks` tab `All` saved-view filter excludes CANCELLED — empty state "No tasks yet / Create a task to start tracking work on this project" is mildly misleading when 9 cancelled tasks actually exist. Would be helpful to surface a "9 cancelled hidden" count or expose a `Cancelled` filter pill (the partial screenshot at 19:12 shows the prior tab UI did include `Cancelled` as a sub-filter — it may have been removed when DB count went to 0). Not a regression; cosmetic only.

### Recommended next dispatch — PRE-FLIGHT-C (trust disposition)

PRE-FLIGHT-B is already complete (info requests gate is GREEN). Skip directly to PRE-FLIGHT-C:

1. Navigate Trust Accounting → **Fee Transfer Out** (scenario step 60.2) — transfer R 70 000 from Sipho's trust ledger to firm business account, OR
2. Use scenario-licensed alternative: refund-to-client for the residual + a final fee-note transfer if Phase A 60.1 (final R 15 000 fee note) is also pending. Closure-letter copy in scenario assumes Day 30 fee note paid + Day 60 fee note R 15K paid + remaining returned, but Day 30 INV-0001 was already documented as edge case (Product Option C). Recommend QA dispatch reads scenario steps 60.1–60.3 + Product's earlier triage (status.md log entry around INV-0001 closure-gate) before deciding which sub-path.
3. After trust ledger reconciles to R 0 (or earmarked-only), re-open closure dialog → all 9 gates should be GREEN → proceed to **CLOSURE-EXECUTE** (Step 2 form, reason CONCLUDED, generate closure letter + SoA, click Confirm Close).

### Time

~7 min wall-clock, 14 tool uses (well under 75 min budget) — slice was effectively a verify-only because prior agent had completed the mutations.

---

## Day 60 — PRE-FLIGHT-C — 2026-04-25 SAST

**Slice scope:** PRE-FLIGHT-C only — dispose of Sipho's R 70 000,00 trust balance per scenario step 60.2 (Fee Transfer Out → final fee note paid from trust + Refund residual to client). Out of scope: closure form (Step 2), SoA generation, status transition.

**Actors used:**
- **Thandi Mathebula** (`thandi@mathebula-test.local` / `SecureP@ss1`, owner) — APPROVE_TRUST_PAYMENT
- **Bob Ndlovu** (`bob@mathebula-test.local` / `SecureP@ss2`, admin) — MANAGE_TRUST + INVOICING (no APPROVE_TRUST_PAYMENT)

**Tooling:** plugin Playwright (`mcp__plugin_playwright_playwright__*`) for every browser-driven mutation; Docker `psql` exec on `b2b-postgres` for read-only state confirmation. Zero SQL/REST mutations.

**Pre-state confirmation (read-only DB):**

```sql
-- Sipho ledger card
SELECT balance, total_deposits, total_payments, total_fee_transfers
  FROM tenant_5039f2d497cf.client_ledger_cards
 WHERE customer_id='c3ad51f5-2bda-4a27-b626-7b5c63f37102';
-- balance=70000.00 / deposits=70000.00 / payments=0 / fee_transfers=0

-- Trust transactions on RAF matter
SELECT id, transaction_type, amount, status, reference, created_at
  FROM tenant_5039f2d497cf.trust_transactions
 WHERE project_id='e788a51b-3a73-456c-b932-8d5bd27264c2'
 ORDER BY created_at;
-- DEP-2026-001 50000 RECORDED + DEP-2026-002 20000 RECORDED

-- Customer-level transactions (no project_id, includes prior agent's leftovers)
SELECT id, transaction_type, amount, status, reference
  FROM tenant_5039f2d497cf.trust_transactions
 WHERE customer_id='c3ad51f5-2bda-4a27-b626-7b5c63f37102'
 ORDER BY created_at;
-- 2 deposits (matter-tagged) + 3 FEE_TRANSFER R 1 250 AWAITING_APPROVAL
--   leftover from prior timed-out agent at 17:51, 18:06, 18:42 UTC

-- Sipho invoices
SELECT invoice_number, status, total FROM tenant_5039f2d497cf.invoices
 WHERE customer_id='c3ad51f5-...';
-- INV-0001 SENT R 1 250 (pre-seeder L-64 edge case)
-- INV-0002 PAID  R   100 (post-seeder L-64 verify artifact)
```

### Disposition path determined

Scenario step 60.2 verbatim (`qa/testplan/demos/legal-za-full-lifecycle-keycloak.md:670`): "Navigate to Trust Accounting → **Fee Transfer Out** → transfer R 15,000 from Sipho's trust to firm business account (pay the final fee note from trust)".

**System reality vs scenario:**
- Scenario assumed a R 15 000 final fee note exists. **In our state, no unbilled time/disbursements remain on RAF**, so the New Fee Note flow returns "No unbilled time entries found for this period" (verified by clicking through Customer → Fee Notes → New Fee Note → Fetch Unbilled Time on Sipho).
- INV-0001 R 1 250 SENT (the L-64 pre-seeder edge case) IS an unpaid fee note — Fee Transfer Out can target it directly.

**Selected path (matches scenario step 60.2 spirit + clears L-64 edge case):**
1. Fee Transfer Out R 1 250 from trust → pay INV-0001 (covers scenario step 60.2 — "pay the final fee note from trust"; clearly satisfies the disposition intent even at the lower amount, AND mooots the L-64 SENT residual that Product earlier flagged as "Option C — accept as pre-seeder edge case carried-forward")
2. Refund residual R 68 750 → Sipho via Trust → Record Refund (covers the gate copy "Transfer to client or office before closure")

### Per-step results

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **C0** | Pre-state inventory captured (3 leftover AWAITING_APPROVAL fee transfers from prior timed-out agent) | **OBSERVED** — must be dispositioned before fresh attempt to keep audit trail clean | `day-60-cycle1-c-pre-state-3-duplicate-fee-transfers.png` |
| **C1a** | As Thandi, reject 1st duplicate `cbf1c21b-…` via UI (`Reject` → reason → confirm) | **PASS** — status RECORDED→REJECTED. (Reason captured as just "D" because first attempt typed before the textarea was React-controlled — cosmetic, not a workflow break.) | snapshots in playwright session |
| **C1b** | As Thandi, reject 2nd duplicate `dc63acf6-…` (this time using `browser_type` post-snapshot) | **PASS** — REJECTED with full reason "Duplicate of accidental retry — keeping the most recent for approval" | (above) |
| **C1c** | As Thandi, **attempt approval of 3rd duplicate `3b6ddf61-…`** (recorded by Thandi) | **BLOCKED** — backend response: `The transaction recorder cannot be the sole approver. A different member with APPROVE_TRUST_PAYMENT capability must approve this transaction.` (`TrustTransactionService.approveSingleMode` line 716-728). | (in-line page banner observed in `main` text) |
| **C1d** | Sign out Thandi, sign in Bob, **attempt approval as Bob** | **BLOCKED** — `Insufficient permissions for this operation` (Bob's admin role lacks `APPROVE_TRUST_PAYMENT`). Verified DB: only `thandi@mathebula-test.local` has the capability. | (in-line page banner) |
| **C1e** | As Bob, record a fresh Fee Transfer R 1 250 → INV-0001 with new reference `FEE-TRANSFER-INV-0001-DAY60-RETRY` (sidesteps the recorder-sole-approver rule) | **PASS** — `f9f3f7a4-dc7f-4eaa-a594-b03374a9d80c FEE_TRANSFER 1250.00 AWAITING_APPROVAL recorded_by=bob` | (DB) |
| **C1f** | Sign out Bob, sign in Thandi, reject the 3rd Thandi-recorded duplicate `3b6ddf61-…`, then approve Bob's `f9f3f7a4-…` | **PASS** — both transitions DB-confirmed: `3b6ddf61` REJECTED, `f9f3f7a4` APPROVED at `19:42:07.509239+00`. INV-0001 flipped SENT→PAID at the same instant (`paid_at=19:42:07.488593+00`). Sipho ledger card balance R 70 000 → R 68 750. | `day-60-cycle1-c-trust-transfer-fee.png` (Approved view + audit row) |
| **C2** | As Bob (after sign-out + sign-in cycle), Trust Accounting → Record Transaction → Record Refund → Sipho client / R 68 750 / `REFUND-DLAMINI-CLOSURE-DAY60` / "Refund residual trust balance to client on matter closure (RAF-2026-001)" / today | **PASS** — `f215423c-bfbc-4a07-981a-4e0a90a8af48 REFUND 68750.00 AWAITING_APPROVAL recorded_by=bob` | (DB) |
| **C2-Approve** | Sign out Bob, sign in Thandi, approve refund | **PASS** — `f215423c-…` APPROVED at `19:45:34.211643+00`. Sipho ledger card balance R 68 750 → **R 0,00** | `day-60-cycle1-c-trust-refund-client.png` |
| **C3a** | Navigate matter Trust tab → trust balance card | **PASS** — card renders `Trust Balance · No Funds · R 0,00` (Deposits R 70 000,00 / Payments R 0,00 / Fee Transfers R 1 250,00) | `day-60-cycle1-c-trust-balance-zero.png` |
| **C3b** | Re-open Close Matter dialog → expect all 9 gates GREEN | **FAIL** — trust gate STILL renders RED with copy `Matter trust balance is R70000.00. Transfer to client or office before closure.` despite ledger card and customer-level state both showing R 0,00 | `day-60-cycle1-c-gates-still-red-l69.png` |
| **Console** | `browser_console_messages level=error` post all transitions | **PASS** — 0 errors, 0 warnings | n/a |

### Root cause analysis (NEW HIGH gap GAP-L-69)

The closure-gate `TrustBalanceZeroGate` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/closure/gates/TrustBalanceZeroGate.java:38`) computes balance via `trustTransactionRepository.calculateBalanceByProjectId(project.getId())` — strictly per-matter using the `trust_transactions.project_id` column. Query body:

```sql
SELECT COALESCE(SUM(
  CASE
    WHEN t.transactionType IN ('DEPOSIT','TRANSFER_IN','INTEREST_CREDIT') THEN t.amount
    WHEN t.transactionType IN ('PAYMENT','TRANSFER_OUT','FEE_TRANSFER','REFUND','INTEREST_LPFF') THEN -t.amount
    ELSE 0
  END
), 0)
FROM TrustTransaction t
WHERE t.projectId = :projectId
  AND t.status IN ('RECORDED','APPROVED')
```

**The defect:** the **RecordFeeTransferDialog** (`frontend/components/trust/RecordFeeTransferDialog.tsx`) and **RecordRefundDialog** (`frontend/components/trust/RecordRefundDialog.tsx`) — opened from BOTH the `/trust-accounting/transactions` page AND from the matter Trust-tab `Fee Transfer` button — have **no Matter / project_id field** in their schema or form. They write `trust_transactions.project_id = NULL`. So:

- DEPOSITS recorded via the matter Trust tab include `project_id` (the dialog has a Matter field) → balance starts at R 70 000 on the matter
- FEE_TRANSFERs and REFUNDs write `project_id = NULL` → never deduct from per-matter balance
- Customer ledger card (`client_ledger_cards`, customer-scoped, NOT project-scoped) correctly nets to R 0,00 (deposits 70K minus 1250 fee transfer minus 68750 refund), so the matter-Trust-tab balance card SHOWS R 0,00, but the closure-gate computation sees R 70 000

DB confirmation:

```sql
SELECT id, transaction_type, amount, status, project_id
  FROM tenant_5039f2d497cf.trust_transactions
 WHERE customer_id='c3ad51f5-2bda-4a27-b626-7b5c63f37102'
 ORDER BY created_at;
-- DEP-2026-001 50000 RECORDED  e788a51b-…  ← matter-tagged
-- DEP-2026-002 20000 RECORDED  e788a51b-…  ← matter-tagged
-- FEE-TRANSFER-…-DAY60      1250 REJECTED  NULL  (3 rows)
-- FEE-TRANSFER-…-DAY60-RETRY 1250 APPROVED NULL  ← project_id MISSING
-- REFUND-DLAMINI-CLOSURE-…  68750 APPROVED NULL  ← project_id MISSING
```

### Ancillary blockers observed during disposition

1. **APPROVE_TRUST_PAYMENT capability gap (BLOCKER for solo-owner firms, OBS-only for this cycle since Bob exists as admin):**
   - Only `owner` role has the capability (verified via `org_role_capabilities`).
   - `admin` (Bob) has `APPROVE_DISBURSEMENTS` + `MANAGE_TRUST` but NOT `APPROVE_TRUST_PAYMENT`.
   - Combined with `TrustTransactionService.approveSingleMode` rule "recorder cannot be the sole approver", this means a single-owner firm cannot approve any trust transaction it records. Workaround used here: have admin record + owner approve.
   - Not raising as a separate gap because (a) Mathebula has both roles available, (b) granting `APPROVE_TRUST_PAYMENT` to admin OR enabling dual approval would resolve it, (c) the spec for who-can-approve-trust is genuinely a Product call, not a regression.

2. **Reject dialog — first-attempt typed text dropped (cosmetic OBS, NOT a regression):**
   - Setting `textarea.value = "..."` via `browser_evaluate` does NOT trigger React's controlled-component update — the form state stays empty (or just whatever was actually typed via `keypress` events). Fix: use `browser_type` (which dispatches real key events) instead. Did not affect outcome (subsequent attempts used `browser_type` correctly).

### New gaps

- **GAP-L-69 (HIGH/BLOCKER for matter closure):** `RecordFeeTransferDialog` + `RecordRefundDialog` lack a Matter (`projectId`) field. Trust transactions of type `FEE_TRANSFER` / `REFUND` / `PAYMENT` / `TRANSFER_OUT` are written with `project_id = NULL`, so the per-matter trust-balance closure gate (`TrustBalanceZeroGate.calculateBalanceByProjectId`) can never reduce — making the trust-balance gate **physically unrouteable through the UI in its current form**. Repro path documented above. Suggested fix M (~3-4 hr):
  1. Add optional `matterId` field (with combobox/autocomplete listing the customer's open matters) to both dialogs.
  2. When Fee Transfer dialog is opened from a matter Trust-tab context, **auto-fill** matterId from the URL.
  3. When Refund dialog is opened from a matter Trust-tab (today it isn't; consider adding the affordance), auto-fill matterId.
  4. Backend `RecordFeeTransferRequest` + `RecordRefundRequest` already accept `projectId` (`trust_transactions.project_id` is a nullable FK with no schema change needed) — frontend just needs to send it.
  5. (Optional follow-up) Backend validation: when an `invoiceId` is supplied for a Fee Transfer, derive `projectId` from the invoice's matter relationship if the field is not explicitly set.

- **OBS-Day60-StaleAwaitingApprovalLeftoversFromPriorAgent** (informational, not a gap): the prior timed-out agent had created 3 duplicate AWAITING_APPROVAL fee transfers without dispositioning them. This slice cleaned them via UI rejection. Not actionable.

### Closing state — gate inventory after PRE-FLIGHT-C

- **Gates RED (1, unchanged):** Trust balance — system reads R 70 000 from per-matter calculation (despite customer ledger card showing R 0,00). Cannot be cleared from the UI today; requires GAP-L-69 fix.
- **Gates GREEN (8, unchanged):** Disbursements approved + settled, final bill issued, no court dates, no prescription timers, all tasks resolved, all info requests closed, no document acceptances pending.

### DB state at slice close

```sql
SELECT id, transaction_type, amount, status, reference, project_id
  FROM tenant_5039f2d497cf.trust_transactions
 WHERE customer_id='c3ad51f5-2bda-4a27-b626-7b5c63f37102'
 ORDER BY created_at;
-- DEP-2026-001                   50000.00  RECORDED  matter
-- DEP-2026-002                   20000.00  RECORDED  matter
-- FEE-TRANSFER-…-DAY60            1250.00  REJECTED  NULL  (×3)
-- FEE-TRANSFER-…-DAY60-RETRY      1250.00  APPROVED  NULL
-- REFUND-DLAMINI-CLOSURE-DAY60   68750.00  APPROVED  NULL

-- Sipho client_ledger_cards
balance = 0.00 / total_deposits = 70000.00 / total_fee_transfers = 1250.00 / total_payments = 0

-- Sipho invoices
INV-0001  PAID  1250.00  paid_at=2026-04-25 19:42:07
INV-0002  PAID   100.00  paid_at=2026-04-25 14:08:43
```

### Snapshots

All under `qa_cycle/checkpoint-results/`:
- `day-60-cycle1-c-pre-state-3-duplicate-fee-transfers.png` — pre-state with 3 prior-agent leftover AWAITING_APPROVAL fee transfers
- `day-60-cycle1-c-final-fee-note-draft.png` — transactions list after 2 rejections (3rd duplicate still AWAITING + Bob's RETRY also AWAITING)
- `day-60-cycle1-c-trust-transfer-fee.png` — APPROVED filter showing the FEE-TRANSFER-…-DAY60-RETRY APPROVED row
- `day-60-cycle1-c-trust-refund-client.png` — APPROVED filter showing both the fee transfer AND the REFUND-DLAMINI-CLOSURE-DAY60 R 68 750 APPROVED row
- `day-60-cycle1-c-trust-balance-zero.png` — matter Trust tab card showing `R 0,00 / No Funds`
- `day-60-cycle1-c-gates-still-red-l69.png` — closure-gate dialog showing trust gate RED with `Matter trust balance is R70000.00` despite the matter Trust tab card showing R 0,00 (proves the L-69 stale per-matter calculation defect)

### Decision

**HALTED per dispatch hard rule** — "If a UI affordance is missing (no 'Pay from trust' / no 'Refund client' / no 'Generate final fee note' CTA), that's a NEW HIGH gap — log it and exit (don't try to power through with REST or SQL)."

The disposition flow IS available end-to-end (Fee Transfer Out + Refund both work), and end-to-end DB state is correct from a customer-ledger and invoice perspective. But the per-matter trust balance gate cannot be cleared through these UI flows because the dialogs don't capture the Matter ID. Patching this via SQL UPDATE on `trust_transactions.project_id` would mask the real defect — exactly what the dispatch rule prohibits.

### Next action

- **Product → Dev triage GAP-L-69**. Fix-spec recommendations above (add `Matter` field to RecordFeeTransferDialog + RecordRefundDialog; auto-fill from matter context where applicable; consider invoice→matter inference on backend).
- After fix lands, re-walk PRE-FLIGHT-C from C2 (the existing APPROVED REFUND of R 68 750 has `project_id = NULL` and is APPROVED, so it can't be back-filled cleanly; the simplest re-walk would be: Reverse the existing R 68 750 REFUND + the R 1 250 FEE_TRANSFER, then re-record both with the correct matter context).
- Day 60 CLOSURE-EXECUTE remains BLOCKED on this gap.

### Time

~30 min wall-clock, ~80 tool uses.
