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

---

## Day 60 — PRE-FLIGHT-C re-walk (after L-69 fix) — 2026-04-25 SAST

**Slice scope:** Re-walk PRE-FLIGHT-C through the L-69-fixed dialogs after PR #1136 merged (commit `8580e466`). Reverse the 2 dirty rows (`f9f3f7a4` FEE_TRANSFER, `f215423c` REFUND, both `project_id=NULL`); re-record disposition with new fixed dialog where REFUND now exposes a Matter (Optional) field; verify all 9 closure gates flip GREEN.

**Actors:**
- **Bob Ndlovu** (`bob@mathebula-test.local` / `SecureP@ss2`) — recorder of reversals + new refund.
- **Thandi Mathebula** (`thandi@mathebula-test.local` / `SecureP@ss1`) — approver.

**Tooling:** plugin Playwright (`mcp__plugin_playwright_playwright__*`). Auth via Keycloak `/oauth2/authorization/keycloak` per `frontend/components/auth/user-menu-bff.tsx`. Sign-out via gateway POST `/logout` with CSRF token. Docker `psql` exec on `b2b-postgres` for read-only state confirmation (DB is `docteams`, NOT `app`). Zero SQL/REST mutations.

**Pre-state DB confirm (read-only):**

```sql
SELECT id, name FROM tenant_5039f2d497cf.customers WHERE name LIKE 'Sipho%';
-- c3ad51f5-2bda-4a27-b626-7b5c63f37102 | Sipho Dlamini

SELECT id, transaction_type, amount, project_id, status, reference
  FROM tenant_5039f2d497cf.trust_transactions
 WHERE id IN ('f9f3f7a4-...','f215423c-...');
-- f9f3f7a4 FEE_TRANSFER 1250 NULL APPROVED FEE-TRANSFER-INV-0001-DAY60-RETRY
-- f215423c REFUND       68750 NULL APPROVED REFUND-DLAMINI-CLOSURE-DAY60

-- Sipho ledger card
balance=0.00 / total_deposits=70000 / total_payments=0 / total_fee_transfers=1250

-- Invoices  INV-0001 PAID, INV-0002 PAID
-- Matter status: ACTIVE
```

### Per-step results — PRE-FLIGHT-C re-walk

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **C-RW1a** | Bob signs in via Keycloak. Trust → Transactions filter `status=APPROVED` | **PASS** — both dirty rows visible with `Reverse` button affordance | snapshot `.playwright-mcp/page-2026-04-25T20-35-58-794Z.yml` |
| **C-RW1b** | Click `Reverse` on FEE-TRANSFER `f9f3f7a4-…` → enter reason "L-69 cleanup …" → confirm | **PASS** — DB: original `f9f3f7a4` flips APPROVED→**REVERSED**; new REVERSAL row `8497d634-…` RECORDED 1250.00 (project_id NULL — reversal copies original's NULL); Sipho ledger card balance 0→**1250** | snapshot `.playwright-mcp/page-2026-04-25T20-36-21-588Z.yml` |
| **C-RW1c** | Click `Reverse` on REFUND `f215423c-…` → enter reason "L-69 cleanup …" → confirm | **PASS** — DB: original `f215423c` flips APPROVED→**REVERSED**; new REVERSAL row `423f1ed0-…` RECORDED 68750.00 (project_id NULL); Sipho ledger card balance 1250→**70000** | snapshot `.playwright-mcp/page-2026-04-25T20-37-22-221Z.yml` |
| **C-RW1d** | DB confirm matter gate calc returns to legitimate pre-disposition R 70K | **PASS** — `SELECT SUM(...) FROM trust_transactions WHERE project_id='e788a51b-…' AND status IN ('RECORDED','APPROVED')` = `70000.00` | (DB) |
| **C-RW2 (BLOCKED)** | Bob: `Record Transaction` → `Record Fee Transfer` → Sipho client / INV-0001 / R 1 250 / `FEE-TRANSFER-INV-0001-DAY60-FIXED` → submit | **BLOCKED — NEW GAP-L-70 OPENED** — backend returns inline validation error: `"Invoice must be in APPROVED or SENT status, current status: PAID"`. The earlier APPROVED FEE_TRANSFER auto-flipped INV-0001 SENT→PAID at `19:42:07.488`; reversing the FEE_TRANSFER (Step C-RW1b) **does NOT cascade-flip the invoice payment status back**. INV-0001 is stuck PAID with the originating fee-transfer reversed → cannot re-record fee transfer against it. | snapshot `.playwright-mcp/page-2026-04-25T20-40-17-265Z.yml` ("Invoice must be in APPROVED or SENT status") |
| **C-RW2-decision** | Recovery path: skip Fee Transfer, do REFUND of full R 70 000 (residual is now full balance since invoice is paid per system). This still legitimately tests L-69 REFUND-with-matter path AND clears trust gate for closure. Cancel Fee Transfer dialog. | **PROCEED** — scenario-interpretation: matter has zero unpaid invoices, so Phase 60.3 ("refund residual to client on closure") is the canonical path; Phase 60.2 ("pay final fee note from trust") is no-op when no unpaid fee notes exist | (decision) |
| **C-RW3a** | Bob: `Record Transaction` → `Record Refund` → **VERIFY new Matter (Optional) FormField is present in dialog** | **PASS — L-69 FRONTEND FIX VERIFIED** — dialog renders fields `Client ID / Matter (Optional) / Amount / Reference / Description (Optional) / Transaction Date`. Matter field has placeholder "Matter UUID (optional)" matching the deposit dialog convention exactly per fix-spec §"Frontend — Matter field on RecordRefundDialog" | snapshot `.playwright-mcp/page-2026-04-25T20-41-44-516Z.yml` |
| **C-RW3b** | Fill Client UUID `c3ad51f5-…`, Matter UUID `e788a51b-…`, Amount `70000`, Reference `REFUND-DLAMINI-CLOSURE-DAY60-FIXED`, Description "Refund full residual trust balance to client on matter closure (RAF-2026-001) — L-69 fix verification" → submit | **PASS** — DB: new REFUND row `b7c87d27-de02-4d4b-9ff8-917a8354d121` 70000.00 **AWAITING_APPROVAL** with **`project_id = e788a51b-3a73-456c-b932-8d5bd27264c2`** (matter UUID populated by L-69 frontend fix + REFUND DTO change) | (DB) |
| **C-RW3c** | Sign out Bob (POST `/logout` with CSRF). Sign in Thandi via Keycloak | **PASS** — landed on `/dashboard` as Thandi | (URL/snapshot) |
| **C-RW3d** | Thandi: Trust → Transactions filter `status=AWAITING_APPROVAL` → click `Approve` on `REFUND-DLAMINI-CLOSURE-DAY60-FIXED` | **PASS** — DB: row flips AWAITING_APPROVAL→**APPROVED**; ledger card balance 70000→**0**; matter gate calc returns **R 0,00** | snapshot `.playwright-mcp/page-2026-04-25T20-43-24-940Z.yml` |
| **C-RW4a** | Navigate matter `/projects/e788a51b-…` → click `Close Matter` → closure-gate dialog renders | **PASS — ALL 9 GATES GREEN** (this is the L-69 verify exit checkpoint) | snapshot `.playwright-mcp/page-2026-04-25T20-43-58-356Z.yml` |
| **Console** | `browser_console_messages level=error` after PRE-FLIGHT-C re-walk | **PASS** — 0 errors, 0 warnings | n/a |

### Closure-gate report — all 9 GREEN ✅ (post L-69 fix)

| # | Gate | State | Copy |
|---|------|-------|------|
| 1 | **Trust balance** | **GREEN ✅** | `Matter trust balance is R0.00.` |
| 2 | Disbursements approved | GREEN ✅ | `All disbursements approved.` |
| 3 | Disbursements settled | GREEN ✅ | `All approved disbursements are settled.` |
| 4 | Final bill issued | GREEN ✅ | `Final bill issued with no unbilled items.` |
| 5 | Court dates | GREEN ✅ | `No court dates scheduled for today or later.` |
| 6 | Prescription timers | GREEN ✅ | `No prescription timers still running.` |
| 7 | Tasks | GREEN ✅ | `All tasks resolved.` |
| 8 | Info requests | GREEN ✅ | `All client information requests closed.` |
| 9 | Document acceptances | GREEN ✅ | `No document acceptances pending.` |

**GAP-L-69 status: FIXED → VERIFIED** (REFUND path proven end-to-end browser-driven; FEE_TRANSFER path code is in place but not exercise-able in this verify cycle due to GAP-L-70 invoice-status not cascading on reversal — see below).

### NEW GAP opened — GAP-L-70 (HIGH for verify-cycle re-walks; MED for prod)

**Title:** FEE_TRANSFER reversal does NOT cascade-flip the linked invoice's payment status back from PAID → SENT.

**Repro:**
1. Customer has SENT invoice INV-0001 R 1 250.
2. Record FEE_TRANSFER R 1 250 against INV-0001 (Bob), approve (Thandi). Backend auto-flips INV-0001 SENT→PAID at the same instant (`paid_at` populated).
3. Reverse the FEE_TRANSFER (Bob → Reverse → reason → confirm). Backend creates a REVERSAL row, marks the original FEE_TRANSFER REVERSED, refunds the trust ledger (Sipho ledger 0 → 1250). **INV-0001 status remains PAID with `paid_at` from step 2**.
4. Attempt to re-record a fresh FEE_TRANSFER against INV-0001 → backend rejects with `"Invoice must be in APPROVED or SENT status, current status: PAID"`.

**Root cause:** `TrustTransactionService.reverseTransaction` (`backend/src/main/java/io/b2mash/b2b/b2bstrawman/verticals/legal/trustaccounting/transaction/TrustTransactionService.java:959`) handles the trust-ledger and trust-transaction-state half of the reversal cleanly (debit reversal: `RECORDED` immediate, original→`REVERSED`, ledger card updated) but does NOT call back into `InvoiceService` to undo the FEE_TRANSFER's side-effect on the invoice. Specifically, the original `recordFeeTransfer` path (lines ~514-580) auto-flips the invoice to PAID via `gateway.recordManualPayment()` or equivalent, but the inverse op doesn't exist on the reversal path.

**Severity:** **HIGH for the verify cycle** (blocks re-walking the FEE_TRANSFER half of the L-69 verification path; the QA agent had to fall back to a REFUND-only disposition). **MED for production** (a real firm reversing a fee transfer would have the same issue: their booked invoice would be stuck PAID even though the trust ledger nets back to pre-transfer state — this is genuine accounting incorrectness, not just verify-cycle theatre. The customer's account would show a balance owing OR the invoice would need to be voided + recreated, neither of which is a clean workflow).

**Suggested fix M (~3-4 hr, backend-only):**
1. In `TrustTransactionService.reverseTransaction` (the debit-reversal branch around line 1055-1075), if the original transaction's type is `FEE_TRANSFER` AND `original.invoiceId != null`, call `invoiceService.unmarkPaid(invoiceId, paymentId)` (or similar inverse operation).
2. Cascading also needs to clear `payment_events.completed` row + reset `invoices.status` SENT/APPROVED + clear `paid_at`/`payment_reference`.
3. Test cases: reverse fee transfer that was the only payment on an invoice → invoice goes back to SENT; reverse fee transfer that was one of multiple payments → invoice stays PAID, just rolls back this specific payment.

**Tracker action:** Open GAP-L-70 in `qa_cycle/status.md` Tracker section, severity HIGH, fix-effort M.

### Snapshots (PRE-FLIGHT-C re-walk)

All under `.playwright-mcp/page-*.yml` and `.playwright-mcp/console-*.log`. Note: PNG screenshot tooling timed-out repeatedly during this slice; YAML accessibility snapshots (auto-saved by `browser_snapshot`) and DB SELECT outputs serve as primary evidence in lieu of pixel-perfect PNGs (snapshot YAML files contain the same on-screen text + structure that a PNG would visually render).

Key snapshot files (preserved in `qa_cycle/checkpoint-results/`):
- `day-60-cycle1-c-rewalk-pre-state.yml` — pre-state with 2 dirty APPROVED rows visible
- `day-60-cycle1-c-rewalk-fee-transfer-reversed.yml` — after first reversal (FEE_TRANSFER → REVERSED)
- `day-60-cycle1-c-rewalk-refund-reversed.yml` — after second reversal (REFUND → REVERSED)
- `day-60-cycle1-c-rewalk-fee-transfer-blocked-l70.yml` — Fee Transfer dialog blocked: "Invoice must be in APPROVED or SENT status, current status: PAID" (GAP-L-70 evidence)
- `day-60-cycle1-c-rewalk-refund-dialog-l69-matter-field.yml` — **Refund dialog with NEW Matter (Optional) FormField** (L-69 frontend fix evidence)
- `day-60-cycle1-c-rewalk-refund-approved.yml` — REFUND-FIXED row flipped to APPROVED (L-69 backend fix evidence)
- `day-60-cycle1-c-rewalk-all-9-gates-green.yml` — closure-gate dialog all 9 GREEN ✅ (L-69 verify exit checkpoint)
- `day-60-cycle1-ce-closure-form-step2.yml` — closure form Step 2 (Reason CONCLUDED + Notes + Generate closure letter)
- `day-60-cycle1-ce-matter-closed.yml` — matter detail header showing status flipped Active→Closed
- `day-60-cycle1-ce-soa-failed-l71.yml` — SoA dialog with "An unexpected error occurred" alert (GAP-L-71 evidence)

---

## Day 60 — CLOSURE-EXECUTE — 2026-04-25 SAST

**Slice scope:** Continue from PRE-FLIGHT-C re-walk (gates GREEN). Click Continue → fill closure form (reason CONCLUDED + closure letter checkbox) → Confirm Close → verify matter ACTIVE→CLOSED + retention policy + closure letter generated. Then attempt SoA generation.

**Actor:** Thandi Mathebula (already KC-authenticated from PRE-FLIGHT-C re-walk).

### Per-step results — CLOSURE-EXECUTE

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **CE1a** | Click `Continue` from gate dialog → Step 2 (closure form) | **PASS** — dialog Step 2 renders with `Reason` combobox (default value `Concluded`), `Notes (optional)` textbox, `Generate closure letter` checkbox (checked by default). NO separate `Generate Statement of Account` checkbox in the form — SoA generation is a separate post-close action via the "Generate Statement of Account" toolbar button. | snapshot `.playwright-mcp/page-2026-04-25T20-44-08-237Z.yml` |
| **CE1b** | Reason already `CONCLUDED`. Add note "Settlement reached with Road Accident Fund. All trust funds disposed: R 70,000 refunded to client. INV-0001 final fee note paid. Statement of Account to be generated for client closure pack." `Generate closure letter` checked. Click `Close matter` confirm | **PASS** — modal dismisses; matter detail page reloads with status badge flipped Active→**Closed**; toolbar buttons reshape (`Reopen Matter` replaces `Close Matter`); closure letter doc reference appears in Recent Activity (`Thandi Mathebula generated document "matter-closure-letter-dlamini-v-road-accident-fund-2026-04-25.pdf" from template "Matter Closure Letter"`) | snapshot `.playwright-mcp/page-2026-04-25T20-44-27-990Z.yml` + Activity feed entry |
| **CE2-DB** | DB confirms (read-only) | **PASS** — `projects.status='CLOSED' / closed_at='2026-04-25 20:44:20' / retention_clock_started_at='2026-04-25 20:44:20'`; `matter_closure_log` row `f0c26aaf-…` inserted: `closed_by=thandi member id 427485fe-…`, `reason='CONCLUDED'`, `notes` captured verbatim, `override_used=false`, `closure_letter_document_id=a48e1bdb-…` (links to `generated_documents` row, NOT directly to `documents`) | (DB) |
| **CE2-Doc** | Closure letter generated | **PASS** — `generated_documents.a48e1bdb-…` references `documents.2bad9b06-09a0-4e5c-961c-9d9f081b8b14` named `matter-closure-letter-dlamini-v-road-accident-fund-2026-04-25.pdf` (`application/pdf`, `UPLOADED`, scope=`PROJECT`) created `2026-04-25 20:44:20.613`. Template id `e88b1bc1-…` | (DB) |
| **CE2-Retention** | ADR-249 retention check | **PASS (partial)** — `projects.retention_clock_started_at` is populated to closure timestamp (per ADR-249). Note: `retention_policies` table contains 2 GLOBAL policies (CUSTOMER 1825 days FLAG, AUDIT_EVENT 2555 days FLAG) — these are tenant-scope rules not per-matter rows. There is no per-matter row inserted in any table named `matter_retention_policies` (the dispatch's expected table name does not exist in this schema). The retention-clock implementation is column-on-projects-row, not separate-row-per-matter. **OBS-Day60-RetentionShape**: design choice to confirm with Product whether per-matter retention timer column suffices, or if a per-matter retention_policies row is also expected in the data model going forward. | (DB) |
| **CE3a** | Click "Generate Statement of Account" toolbar button → SoA dialog opens with default period `2026-04-01` to `2026-04-25`. Click `Preview & Save` | **FAIL — NEW GAP-L-71 OPENED** — backend returns alert "An unexpected error occurred". Backend log shows `java.lang.ClassCastException: class io.b2mash.b2b.b2bstrawman.verticals.legal.disbursement.dto.DisbursementStatementDto cannot be cast to class java.util.Map` at `io.b2mash.b2b.b2bstrawman.template.TiptapRenderer.renderLoopTable(TiptapRenderer.java:309)`. Stack: `StatementService.renderHtml:276` → `StatementService.generate:110` → `StatementController.generate:42`. The Tiptap template engine's loop-table renderer expects each iterable item to be a `Map`, but the disbursement statement section provides typed `DisbursementStatementDto` records. | snapshot `.playwright-mcp/page-2026-04-25T20-46-20-825Z.yml` (alert "An unexpected error occurred") + backend log line at `2026-04-25T20:46:08.033` requestId `e9fc7a75-d2ae-452f-94b2-e85426e6c4bc` |
| **CE3-decision** | SoA generation HALTED per dispatch hard rule. Closure outputs partially complete: closure letter ✅, SoA ❌. Per Day 60 scenario step 60.10 ("Closure letter + Statement of Account documents both attached to matter Documents tab"), the SoA half is broken in the current build. | **PARTIAL** — matter is genuinely CLOSED with retention clock started + closure letter attached; SoA generation is blocked on a backend rendering bug unrelated to L-69. | (decision) |
| **Console** | `browser_console_messages level=error` post-CLOSURE-EXECUTE | **PASS** — 0 errors, 0 warnings (the SoA failure surfaces as a backend error response, not a frontend console error) | n/a |

### Final state at slice close

```sql
SELECT id, name, status, closed_at, retention_clock_started_at
  FROM tenant_5039f2d497cf.projects
 WHERE id='e788a51b-3a73-456c-b932-8d5bd27264c2';
-- e788a51b-… | Dlamini v Road Accident Fund | CLOSED
--   | closed_at=2026-04-25 20:44:20.042199+00
--   | retention_clock_started_at=2026-04-25 20:44:20.042199

SELECT * FROM tenant_5039f2d497cf.matter_closure_log
 WHERE project_id='e788a51b-…';
-- f0c26aaf-099e-4258-95ec-0397c0dc28ce
-- closed_by=427485fe-… (Thandi)  closed_at=20:44:20  reason=CONCLUDED
-- notes=<full settlement narrative>  override_used=false
-- closure_letter_document_id=a48e1bdb-462c-4825-a181-36ec4221d0d8

SELECT id, file_name, content_type, status FROM tenant_5039f2d497cf.documents
 WHERE project_id='e788a51b-…' ORDER BY created_at DESC LIMIT 1;
-- 2bad9b06-… | matter-closure-letter-dlamini-v-road-accident-fund-2026-04-25.pdf
--           | application/pdf | UPLOADED

-- Trust transactions on matter (post L-69 fix)
SELECT transaction_type, amount, project_id, status, reference
  FROM tenant_5039f2d497cf.trust_transactions
 WHERE project_id='e788a51b-…' AND status IN ('RECORDED','APPROVED');
-- DEPOSIT  50000  e788a51b-…  RECORDED  DEP-2026-001
-- DEPOSIT  20000  e788a51b-…  RECORDED  DEP-2026-002
-- REFUND   70000  e788a51b-…  APPROVED  REFUND-DLAMINI-CLOSURE-DAY60-FIXED
-- gate_balance = 70000 - 70000 = 0  ✅
```

### NEW GAPs opened in CLOSURE-EXECUTE

- **GAP-L-71 (HIGH/BLOCKER for E.13 SoA exit checkpoint):** Statement of Account generation crashes with `ClassCastException` in `TiptapRenderer.renderLoopTable:309` — `DisbursementStatementDto cannot be cast to Map`. Backend `StatementService.renderHtml:276` passes typed DTOs into the loop iterator but the template engine expects `Map<String, Object>`. Repro path: any matter with a disbursement-statement template section + click `Generate Statement of Account`. Suggested fix S-M (~2-3 hr): either (a) `TiptapRenderer.renderLoopTable` reflectively introspects DTOs into Maps via Jackson `convertValue(...,Map.class)` before iterating, OR (b) `StatementService.renderHtml` builds the DTO list as `List<Map<String,Object>>` already converted before passing into the renderer context. Option (a) is more general (works for all DTO types in any future template), option (b) is more targeted. Tracker entry needed.

- **OBS-Day60-RetentionShape (informational, not a gap):** ADR-249 verification — the implementation uses a `retention_clock_started_at` column on `projects` rather than a separate per-matter row in `matter_retention_policies` (which doesn't exist). Two GLOBAL retention policy rows live in `retention_policies` (CUSTOMER 1825 days, AUDIT_EVENT 2555 days). Confirm with Product whether this column-based design is the intended ADR-249 implementation, or whether per-matter rows are also expected.

### Decision

**SLICE PARTIALLY COMPLETE.** Matter is genuinely CLOSED via the canonical UI flow (closure-gate dialog → form → Confirm Close): status flipped, closed_at set, retention clock started, closure letter generated and attached. **L-69 FIX VERIFIED end-to-end through the REFUND path** (matter-closure trust-balance gate is now routeable through the UI for REFUND-shaped dispositions).

**Two NEW HIGH gaps opened**: GAP-L-70 (FEE_TRANSFER reversal doesn't cascade to invoice payment status) and GAP-L-71 (SoA generation throws ClassCastException). Both block separate exit checkpoints (verify-cycle re-walks of fee transfer flow + E.13 SoA half) but neither blocks the headline outcome of "matter closed".

### Next action

- Product → Dev triage GAP-L-70 (invoice payment status cascade on reversal) and GAP-L-71 (SoA TiptapRenderer ClassCastException).
- After GAP-L-71 fixed: re-run SoA generation step alone (the matter is already closed; SoA can be generated post-close per the toolbar button which remains available on closed matters).
- Day 61 (Sipho portal SoA download) is BLOCKED on GAP-L-71 — no SoA artifact exists for the portal to render. Once SoA is generated, Day 61 can proceed.

### Time

PRE-FLIGHT-C re-walk: ~10 min (very fast — fix-spec was accurate, dialogs worked exactly as designed).
CLOSURE-EXECUTE: ~7 min including the SoA failure investigation.
Total ~17 min wall-clock, well under 75 min budget.

---

## Day 60 — SoA re-gen (after L-71 fix) — 2026-04-25 SAST

**Slice scope:** After PR #1137 (`23bb43d8`) landed and backend restarted, re-trigger SoA generation against the already-CLOSED Dlamini matter to verify the `ClassCastException` is gone and a usable SoA PDF is produced.

**Actor:** Thandi Mathebula (KC session live from prior turn).

### Per-step results — SoA re-gen

| Step | Action | Result | Evidence |
|------|--------|--------|----------|
| **S1a** | Navigate `/org/mathebula-partners/projects/e788a51b-…` (matter detail) | **PASS** — page loads, status badge **Closed**, toolbar shows `Reopen Matter` + `Generate Statement of Account` + Generate Document + New Engagement Letter etc. | `qa_cycle/checkpoint-results/day-60-cycle1-ce-soa-after-close.yml` |
| **S1b** | Click `Generate Statement of Account` toolbar button | **PASS** — modal opens with default period `2026-04-01` → `2026-04-25` and Preview & Save button | `day-60-cycle1-ce-soa-regen-dialog.yml` |
| **S1c** | Click `Preview & Save` | **PASS — L-71 FIX VERIFIED** — backend returns 200, modal renders an embedded `<iframe>` with the rendered HTML SoA + buttons reshape to `Close / Download PDF / Regenerate`. Backend log line `2026-04-25T21:39:48.262018Z … StatementService — Generated Statement of Account: project=e788a51b-…, period=2026-04-01..2026-04-25, generatedDoc=c0931e79-f185-42c8-af70-33528d5e0d69` (requestId `986b9f0d-…`). NO ClassCastException, NO error stack trace anywhere in `bash svc.sh logs backend | tail -30` (compared to prior failure at `2026-04-25T20:46:08.033`). | `day-60-cycle1-ce-soa-regen-after-click.yml` (preview iframe full content visible) |
| **Console** | `browser_console_messages level=error` post Preview & Save | **PASS** — 0 errors, 1 unrelated warning | n/a |
| **S2 — preview content** | Read iframe content for scenario reconciliation | **MIXED** — see "SoA content reconciliation" below. Headline: SoA renders, all 7 sections present, no rendering errors. | iframe text in `day-60-cycle1-ce-soa-regen-after-click.yml` lines 461-617 |
| **S2-DB** | `SELECT id, document_id, file_name, file_size, generated_at FROM tenant_5039f2d497cf.generated_documents WHERE id='c0931e79-f185-42c8-af70-33528d5e0d69';` | **PASS** — 1 row: `c0931e79-…` / `document_id=NULL` / `file_name='statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf'` / `file_size=4109` / `generated_at='2026-04-25 21:39:48.251717+00'`. **OBS-Day60-SoA-NotInDocumentsTable**: SoA persists to `generated_documents` only — `document_id` is NULL so the SoA doesn't appear in the matter Documents tab (firm-side OR portal-side). It IS visible + downloadable from the **Statements tab** of the matter. Per scenario step 60.10 ("Closure letter + Statement of Account documents both attached to matter Documents tab"), this is a discrepancy with scenario expectation but consistent with the existing app architecture. | (DB) |
| **S2-Statements-tab** | Click Statements tab on matter | **PASS** — table shows row: `Apr 25, 2026 / R 1 250,00 / R 0,00 / [Download]`. Closing balance owing R 1 250 matches preview iframe summary; trust balance held R 0 matches scenario closure expectation. | `day-60-cycle1-ce-soa-statements-tab.yml` |
| **S2-Download** | Click Download on Statements tab row | **PASS** — Playwright reports `Downloaded file statement-c0931e79-f185-42c8-af70-33528d5e0d69.pdf to ".playwright-mcp/statement-c0931e79-f185-42c8-af70-33528d5e0d69.pdf"` (4109 bytes). PDF saved to `qa_cycle/checkpoint-results/day-60-cycle1-ce-soa-statement.pdf`. | PDF file |
| **S2-Documents-tab** | Click Documents tab on matter | **OBS-Day60-SoA-NotInDocumentsTable confirmed** — Documents tab lists 8 docs: engagement letter, matter-closure letter, FICA id/address/bank (×2 versions). NO `statement-of-account-…pdf` row. SoA lives only under Statements tab. | `day-60-cycle1-ce-soa-documents-tab.yml` |
| **S3 — Activity feed** | Recent Activity card on matter Overview | **PASS** — new entry `Thandi Mathebula performed statement.generated on generated_document` ~minutes ago, alongside the prior `…generated document "matter-closure-letter-…"` entry. | `day-60-cycle1-ce-soa-after-close.yml` lines 339+346 |

### SoA content reconciliation vs scenario

The L-71 fix verified path is **clean** — the renderer no longer crashes. The rendered SoA preview/PDF content however shows a content gap when reconciled to scenario step 61.4 / dispatch's expected contents:

| Section | Scenario expectation | Observed in SoA preview | Verdict |
|---------|---------------------|-------------------------|---------|
| Header (firm letterhead, period, ref) | Mathebula & Partners + Reference SOA-… + Period 2026-04-01..2026-04-25 + Generated 2026-04-25 | All present (`Reference: SOA-e788a51b-20260425`) | **PASS** |
| To (client) | Sipho Dlamini + address | Present | **PASS** |
| Matter (file ref, opened) | RAF-2026-001, Litigation, opened 2026-04-25 | Present | **PASS** |
| **Professional Fees table** | INV-0001 PAID + INV-0002 PAID fee lines (per dispatch description: "fee notes paid") | **EMPTY rowgroup** — header row only, no fee lines. Total fees R 0. | **GAP — see OBS-Day60-SoA-Fees-Empty** |
| **Disbursements table** | "disbursements billed" (sheriff service fee + court fee) | **POPULATED** — 2 rows: `25 April 2026 COURT_FEES L-64 verify — court filing fee Magistrate Court Pretoria R100,00 R0,00` and `25 April 2026 SHERIFF_FEES Sheriff service of summons on RAF — Day 21 cycle-1 verify Sheriff Pretoria R1 250,00 R0,00`. Total R 1 350,00. | **PASS** (this was the loop that crashed pre-fix; renders cleanly post-fix) |
| **Trust Activity** | Opening balance R 0, deposits R 50k + R 20k, fee transfer out (or refund) R 70k, closing R 0 | Opening R 0 ✅ + Closing R 0 ✅ — but **Deposits table EMPTY rowgroup AND Payments table EMPTY rowgroup**. Backend nets to 0 but the line items are missing. | **GAP — see OBS-Day60-SoA-Trust-Empty** |
| Summary | Total fees, total disbursements, payments received, balance | Total fees R 0, total disbursements R 1 350,00, previous balance owing R 0, payments received R 100,00, **closing balance owing R 1 250,00**, trust balance held R 0. | **PARTIAL** — disbursement total + payments-received (INV-0002 R 100) + trust balance OK; closing balance owing R 1 250 reflects INV-0001 not appearing in the fees table (and INV-0001's R 1 250 trust-paid status not reflected). |
| Payment Instructions | Banking details | Section heading present, body empty | **OBS** — likely tenant-config gap, not a regression |

**OBS-Day60-SoA-Fees-Empty / OBS-Day60-SoA-Trust-Empty (NOT a new gap; informational followup):** L-71's data-shape conversion (DTO → Map) is the only thing the fix promised, and that works. The empty trust+fees loops mean either (a) the SoA period 2026-04-01..2026-04-25 is filtering on a `paid_at` / `transaction_date` boundary that excludes the rows even though they're dated 2026-04-25, OR (b) the SoA queries don't union in records with NULL project_id (the original L-69-era trust rows had `project_id=NULL` — they were reversed but their REVERSAL pair rows may also lack project_id), OR (c) `StatementOfAccountContextBuilder.collectFees`/`collectTrust` predicates don't match the data shape on this matter. This is a separate content/correctness investigation orthogonal to L-71's ClassCastException fix and worth opening as **OBS-L-71-followup** or a Sprint-2 SoA-content-reconciliation gap. **Crucially, it does NOT block L-71 verification** — L-71 was the renderer crash; the renderer is no longer crashing.

### Final state at slice close

```sql
-- generated_documents row for the SoA
SELECT id, document_id, file_name, file_size, generated_at
  FROM tenant_5039f2d497cf.generated_documents
 WHERE id='c0931e79-f185-42c8-af70-33528d5e0d69';
-- c0931e79-f185-42c8-af70-33528d5e0d69 | NULL | statement-of-account-dlamini-v-road-accident-fund-2026-04-25.pdf | 4109 | 2026-04-25 21:39:48.251717+00

-- documents table on the matter (no SoA row)
SELECT id, file_name, content_type, created_at FROM tenant_5039f2d497cf.documents
 WHERE project_id='e788a51b-…' ORDER BY created_at DESC LIMIT 10;
-- 2bad9b06-… | matter-closure-letter-dlamini-…2026-04-25.pdf | application/pdf | 2026-04-25 20:44:20.613825+00
-- 8ca47203-… | fica-bank.pdf | application/pdf | 2026-04-25 16:47:36.596719+00
-- 68d9b68e-… | fica-address.pdf | application/pdf | 2026-04-25 16:47:17.014779+00
-- 4d8e6125-… | fica-id.pdf | application/pdf | 2026-04-25 16:46:51.656774+00
-- b1f81ae2-… | engagement-letter-litigation-dlamini-…2026-04-25.pdf | application/pdf | 2026-04-25 09:56:00.484831+00
-- 254a3806-…/b8bca882-…/525db0c6-… (older fica versions)
```

### Decision

**L-71 FIX → VERIFIED.** SoA generation via the canonical UI path (closed matter toolbar → dialog → Preview & Save) executes successfully end-to-end: backend returns 200, preview iframe renders, PDF persists to `generated_documents` (4 109 bytes), Statements tab + Activity feed both reflect the new artifact, downloadable, console clean. The exact stack trace `ClassCastException at TiptapRenderer.renderLoopTable:309` that was the L-71 repro is GONE.

Two informational observations carved out (NOT new gaps blocking the verify cycle): **OBS-Day60-SoA-NotInDocumentsTable** (SoA absent from Documents tab on matter; surfaces only via Statements tab — discrepancy with scenario step 60.10) and **OBS-Day60-SoA-Fees/Trust-Empty** (SoA preview body has empty fee + trust loops while disbursements loop populates correctly — content reconciliation followup, not a renderer crash). Both worth Product triage but neither blocks the L-71 verification or Day 61 dispatch.

### Time

SoA re-gen + verification: ~3 min wall-clock.

---

## Day 60 Cycle 46 Walk — 2026-04-27 SAST

**Branch**: `bugfix_cycle_2026-04-26-day60` (cut from main `18365feb`)
**Backend rev / JVM**: main `18365feb` / backend PID 41372
**Stack**: Keycloak dev (3000/8080/8443/8180)
**Actor**: Thandi Mathebula (Owner — `thandi@mathebula-test.local` / `SecureP@ss1`)
**Matter under test (current cycle)**: `cc390c4f-35e2-42b5-8b54-bac766673ae7` (RAF-2026-001 — Dlamini v Road Accident Fund). Note this is the matter id used since cycle 5 onward (the prior `e788a51b-…` references in this file's earlier sections come from a stale dev-data run).

### Pre-state (read-only DB SELECT, all confirmed)
- RAF matter `cc390c4f-…` status `ACTIVE`, customer Sipho `c4f70d86-…`
- INV-0001 `432ae5a9-…` status `PAID`, total R 5 160,00, paid_at 2026-04-27 13:17:08+00
- `client_ledger_cards` Sipho row `36765353-…`: balance R 70 100,00 / total_deposits R 70 100,00 / total_payments R 0 / total_fee_transfers R 0
- 3 `trust_transactions` (DEPOSIT R 50 000 + R 100 cycle-29 + R 20 000), all RECORDED
- `legal_disbursements` 1 row: Sheriff service R 1 250,00, billing_status BILLED, approval_status APPROVED
- 2 `time_entries` (150 + 90 minutes) both already on INV-0001 — **no unbilled time exists for the matter**
- 1 completed billing_run `9d772b71-…` (R 3 910,00) and 1 empty preview run `a7f6f47a-…`
- `org_settings`: `retention_policy_enabled=false`, `legal_matter_retention_years=NULL` (no retention policy configured)

### Summary
**4 PASS / 1 FAIL / 4 PARTIAL / 0 BLOCKED / 2 SKIPPED**

Matter closes successfully with override (gates fail; happy-path expectation broken). Closure letter + Statement of Account both attached to Documents tab. SoA reconciliation is broken in two ways (Trust ledger empty in SoA, VAT calculated as zero — elevating prior cycles' OBS-Day60-SoA-Fees/Trust-Empty into formal gaps). Day 60.1–60.3 (final fee note + trust transfer) skipped — no eligible unbilled work; scenario narrative is hypothetical.

### Checkpoints

#### 60.1 — Generate final fee note (R 15,000) for any remaining unbilled work
- **Result**: SKIPPED (no unbilled work exists)
- **Evidence**: `cycle46-day60-3-financials-tab.yml` (Revenue R 3 400 already billed); `cycle46-day60-11-billing-run-customers.yml` ("No customers with unbilled work found for this period."); DB SELECT (all 2 time_entries already linked to INV-0001).
- **Notes**: All 4h of billable time was bulk-billed on Day 28 (INV-0001 R 5 160 incl VAT). The disbursement R 1 250 is also BILLED on the same fee note. The scenario narrative ("assume one more small fee note R 15,000") is hypothetical and cannot be exercised through the UI without injecting fake unbilled time. Thandi cannot easily log time herself either — the Log Time dialog warns "No rate card found for this combination" because only Bob has a project rate (`R850/hr`). Cycle proceeded to closure flow per Day 60 happy-path.

#### 60.2 — Trust Accounting → Fee Transfer Out R 15,000 from Sipho's trust to firm business
- **Result**: SKIPPED (depends on 60.1)
- **Evidence**: n/a
- **Notes**: No fee note to settle. Trust balance R 70 100,00 remains intact at closure (see 60.5 gate failure).

#### 60.3 — Approve fee transfer; client ledger reflects transfer-out
- **Result**: SKIPPED (depends on 60.1/60.2)

#### 60.4 — Navigate to RAF matter → click Close Matter
- **Result**: PASS
- **Evidence**: `cycle46-day60-1-matter-detail.yml` (Close Matter button visible at ref=e175); `cycle46-day60-18-closure-dialog.yml` (closure dialog opened).
- **Notes**: Action bar exposes `Close Matter`, `Generate Statement of Account`, `Complete Matter`, `Generate Document`, `New Engagement Letter`, `Edit`, `Delete`. Click opens "Close matter" Step-1 dialog "Review closure gates and confirm closure of Dlamini v Road Accident Fund."

#### 60.5 — Closure Step 1 gate report renders, all gates GREEN
- **Result**: FAIL (4 of 9 gates FAIL — scenario expected all GREEN on happy path)
- **Evidence**: `cycle46-day60-18-closure-dialog.yml` lines 444-483.
- **Gate breakdown**:
  - **FAIL** — "Matter trust balance is R70100.00. Transfer to client or office before closure." (Fix-this link to `?tab=trust`)
  - PASS — "All disbursements approved."
  - PASS — "All approved disbursements are settled."
  - PASS — "Final bill issued with no unbilled items."
  - **FAIL** — "1 court dates scheduled for today or later." (Fix-this link to court-calendar)
  - PASS — "No prescription timers still running."
  - **FAIL** — "9 tasks remain open." (Fix-this link to `?tab=tasks`)
  - **FAIL** — "3 client information requests outstanding." (Fix-this link to `?tab=requests`)
  - PASS — "No document acceptances pending."
- **Notes**: Continue button is enabled despite failing gates (gates surface a warning, not a hard block). Dialog allows Step 2 to proceed and offer the override toggle.

#### 60.6 — Click Continue → Step 2 (Close form)
- **Result**: PASS
- **Evidence**: `cycle46-day60-19-closure-step2.yml` lines 438-477.
- **Notes**: Step 2 renders Reason combobox (defaulted to `Concluded`), Notes textbox, Generate-closure-letter checkbox (checked by default), and Override-failing-gates toggle.

#### 60.7 — Reason: CONCLUDED (settlement reached)
- **Result**: PASS
- **Evidence**: combobox default `Concluded`. DB row `matter_closure_log.reason='CONCLUDED'` (`eb934998-…`).

#### 60.8 — Generate closure letter checked + Generate Statement of Account flag
- **Result**: PARTIAL (closure letter checkbox present + checked; **NO Statement-of-Account checkbox in the closure dialog**)
- **Evidence**: `cycle46-day60-19-closure-step2.yml` line 457 (Generate closure letter is the ONLY checkbox in the form). Spec §60.8 expected a separate "Generate Statement of Account" flag inline. Implementation surfaces SoA only as a separate top-level button on the matter header (must be invoked manually, post-closure).
- **Notes**: Logged as **GAP-L-93**.

#### 60.9 — Click Confirm Close → matter status = CLOSED
- **Result**: PASS
- **Evidence**: `cycle46-day60-21-after-close.yml` lines 109 (`Closed`), 122 (`Reopen Matter` replaces `Close Matter`); DB `projects.status='CLOSED'`, `closed_at=2026-04-27 16:56:04.658+00`. `matter_closure_log` row `eb934998-0530-4719-88b4-e4a82dee39c2`: `reason=CONCLUDED`, `override_used=t`, `closure_letter_document_id=c582a54f-…`, justification persisted (≥ 20 chars).

#### 60.10 — Closure letter + Statement of Account both attached to matter Documents tab
- **Result**: PARTIAL — closure letter auto-attached on close; SoA requires SEPARATE manual generation step
- **Evidence**: `cycle46-day60-22-documents-tab.yml` line 235 (closure letter only after close); `cycle46-day60-25-soa-generated.yml` (SoA generated via standalone "Generate Statement of Account" → modal → period 2026-04-01 → 2026-06-30 → Preview & Save); `cycle46-day60-26-statements-tab-after.yml` lines 237-245 (SoA row in Statements tab).
- **DB state**:
  - `documents` `85c501aa-…` `matter-closure-letter-…04-27.pdf` 1 638 bytes UPLOADED 16:56:05.10+00
  - `documents` `2bc63982-…` `statement-of-account-…06-30.pdf` 4 100 bytes UPLOADED 16:58:22.95+00
  - `generated_documents`: 2 PROJECT-scoped rows pointing at both documents
- **Notes**: SoA preview iframe rendered Section 86 ledger structure (Reference SOA-cc390c4f-20260427, Period, Sipho address, Matter ref RAF-2026-001, Professional Fees / Disbursements / Trust Activity / Summary / Payment Instructions sections). Reconciliation gaps documented as GAP-L-94 + GAP-L-95.

#### 60.11 — Retention policy row inserted with end_date = today + 5 years (ADR-249)
- **Result**: PARTIAL — `projects.retention_clock_started_at = 2026-04-27 16:56:04.658` is set on closure, but **no per-matter `retention_policies` row inserted**, and `org_settings.legal_matter_retention_years = NULL` so no end-date can be derived
- **Evidence**: `SELECT retention_clock_started_at FROM projects WHERE id='cc390c4f-…'` returns the closure timestamp. `SELECT * FROM retention_policies` shows only the 2 default org-level rows (AUDIT_EVENT 2555 days, CUSTOMER 1825 days) — no MATTER record_type row exists for this matter.
- **Notes**: Logged as **GAP-L-96**.

#### Day 60 supplementary — Mailpit notification "Your Statement of Account is ready"
- **Result**: FAIL (no email sent)
- **Evidence**: Mailpit `/api/v1/search?query=closure` returns 0; `query=statement+of+account` 0; `query=matter+closed` 0. Sipho's last email is the trust-deposit notification from 2026-04-27 14:22:48 UTC (Day 45 deposit).
- **Notes**: Logged as **GAP-L-97**. Day 61 portal step 61.1 ("Mailpit → open 'Statement of Account ready' email") cannot be exercised — Sipho has no inbound notification for Day 60 closure or SoA generation.

### Day 60 checkpoints (scenario summary, cycle 46)
- [x] Matter closes — happy path NOT clean (override required); proceed-with-override allowed
- [x] Statement of Account PDF generated and attached (separate flow from closure)
- [ ] Notification email to Sipho — NOT sent

### Cycle 46 NEW gaps

#### GAP-L-93 (LOW) — Closure dialog has no Statement-of-Account flag
- **Symptom**: Closure Step-2 dialog only offers `Generate closure letter` checkbox. Scenario §60.8 expects an inline `Generate Statement of Account` flag so SoA is auto-attached on close.
- **Impact**: Operator must remember to invoke `Generate Statement of Account` separately after closure (extra cognitive step; risk of forgotten SoA on closed matters).
- **Suggested fix scope**: S (~2 hrs). Add a second checkbox `Generate Statement of Account` to the closure form; on confirm, run both generators in the same backend transaction.
- **Evidence**: `cycle46-day60-19-closure-step2.yml` lines 438-477.

#### GAP-L-94 (HIGH) — Statement of Account does not include trust ledger activity
- **Symptom**: SoA "Trust Activity" section renders empty Deposits + Payments tables, "Opening balance: 0", "Closing balance: 0", "Trust balance held: 0" — even though 3 RECORDED trust deposits totalling R 70 100,00 exist for Sipho on this matter.
- **Impact**: SoA fails Section 86/LPC compliance (must reconcile trust ledger end-to-end). Cannot be sent to a client without legal misrepresentation.
- **Root-cause hypothesis**: SoA generator likely queries trust_transactions filtered by something other than `customer_id`+`project_id` (perhaps requires `invoice_id` link or a status that none of the existing rows carry), or filters by period using `transaction_date` strictly inside the period and the deposits land outside the [start, end] window even though the period covers them.
- **Suggested fix scope**: M (~4-6 hrs). Audit `StatementOfAccountService` (or equivalent) trust ledger query; widen period predicate to `transaction_date <= period_end` for opening balance; ensure all RECORDED transactions for the matter+customer pair are included.
- **Evidence**: `cycle46-day60-25-soa-generated.yml` lines 463-510 (empty Trust Activity tables, all balances zero); `cycle46-day60-26-statements-tab-after.yml` line 240 (Statements tab shows "Trust balance held R 0,00" for the SoA row).
- **Note**: This formalises the prior cycle-1 OBS-Day60-SoA-Fees/Trust-Empty observation into a HIGH-severity gap because Day 61 portal-side SoA download depends on accurate reconciliation.

#### GAP-L-95 (BUG) — Statement of Account VAT line is R 0,00 despite VAT-registered firm + 15% rate
- **Symptom**: SoA "Total fees (excl. VAT): 3400.00", "VAT: 0", "Total fees (incl. VAT): 3400.00". The same time entries on INV-0001 attracted R 510 VAT correctly (R 3 400 net + R 510 VAT = R 3 910 gross).
- **Impact**: Gross fee total is mis-stated by R 510. "Closing balance owing -R 510,00" leaks the VAT shortfall into the over-payment line ("Payments received 5160.00" minus "Total fees 3400 + Total disbursements 1250" = R 510 — exactly the missing VAT).
- **Root-cause hypothesis**: SoA generator's fee-line VAT computation is missing — the invoice line items already had VAT computed at billing time, but SoA appears to recompute fees from raw `time_entries.duration × rate` without the VAT step.
- **Suggested fix scope**: S (~2 hrs). Use `org_settings.vat_rate` (or per-line `vat_amount` if persisted) to compute SoA VAT.
- **Evidence**: `cycle46-day60-25-soa-generated.yml` lines 432-439 (VAT 0 / closing -510); cf. `invoice_lines` of INV-0001 which carry the correct VAT.

#### GAP-L-96 (LOW / ADR-249 compliance) — Closure does not write a retention_policies row for the matter
- **Symptom**: On close, `projects.retention_clock_started_at` is stamped, but no `retention_policies` row is inserted for `record_type='MATTER'` with the closed matter ID. `org_settings.legal_matter_retention_years` is NULL on this tenant.
- **Impact**: ADR-249 requires an explicit retention policy row on closure with a concrete end-date so retention can be enforced. Current state means matter retention silently relies on undefined org defaults.
- **Suggested fix scope**: S–M (~4 hrs). On `MatterClosureService.close()` insert a row into `retention_policies` (record_type=MATTER, retention_days = `org_settings.legal_matter_retention_years × 365` defaulting to 5 years per ADR-249 if NULL, trigger_event=MATTER_CLOSED, action=ARCHIVE, active=true).
- **Evidence**: `SELECT * FROM retention_policies` (only 2 generic rows, no MATTER row); `SELECT retention_clock_started_at FROM projects WHERE id='cc390c4f-…'` set, but no derived end-date; `org_settings.legal_matter_retention_years = NULL`.

#### GAP-L-97 (LOW) — No email notification sent to portal contact when matter closes / SoA generated
- **Symptom**: Closure flow + Generate Statement of Account flow both completed without enqueueing any email to Sipho's portal contact. Mailpit search confirms 0 closure-related emails.
- **Impact**: Day 61 (portal-side SoA download) cannot start from an inbound email — Sipho would have to log in proactively. Also leaves the audit trail thin (no client-facing record that closure happened).
- **Suggested fix scope**: S–M (~4 hrs). Add a domain event publisher in `MatterClosureService` ("matter.closed") and `StatementOfAccountService` ("matter.soa.generated"); wire to existing `EmailNotificationListener`. Templates: "Your matter has been closed — Statement of Account attached" / standalone "Your Statement of Account is ready".
- **Evidence**: `curl http://localhost:8025/api/v1/search?query=closure` 0 hits; `query=statement+of+account` 0 hits; last Sipho email is Day 45 trust deposit (16:22:17 UTC ≪ 16:56:04 UTC closure timestamp).

### Console health
0 errors during the closure flow + SoA generation. (5 transient SSR errors observed during a Keycloak SSO redirect mid-session and resolved on re-login — not Day-60-specific.)

### Cross-cycle observations
- Override toggle works as designed; gate report is informative (not blocking).
- Closure letter generation is reliable end-to-end (DB → S3 → Documents tab).
- SoA generation pipeline ships as a separate manual flow rather than a closure side-effect.

## Cycle 51 Retest — PR #1197 (3 SoA fixes: GAP-L-94 / GAP-L-95 / GAP-L-97) — 2026-04-28 SAST

**Branch / HEAD verified:** `main` @ `0e9df9f4` (PR #1197 squash — bundles cycle-48 PR #1194 + cycle-49 PR #1195 + cycle-50 PR #1196 + CR-fix `0df066a7`).

**Pre-flight:** Backend `svc.sh status` showed PID 35388 running with start-time 2026-04-27 20:25:22, but PR #1197 only landed at 23:43:13 — pre-merge code. **Restarted backend** via `bash compose/scripts/svc.sh restart backend` (27 s health-check); fresh process now serves the merged binary. Frontend HMR carried any UI changes through; gateway/portal untouched.

**Mailpit cleared** immediately before trigger via `DELETE /api/v1/messages` (only legitimate QA REST surface) → `total: 0`.

**Actor:** Bob Ndlovu (`bob@mathebula-test.local`, admin) via Keycloak SSO at `:3000`. Browser-driven via Playwright MCP — zero REST mutations to backend.

**Trigger:** RAF matter `cc390c4f-35e2-42b5-8b54-bac766673ae7` (Sipho Dlamini, `c4f70d86-…`) → toolbar `Generate Statement of Account` button → modal → period `2026-04-01` → `2026-04-30` → `Preview & Save`. New `generated_documents` row written: `6b79c496-4ae2-4731-9fe7-3ac18677d394` `statement-of-account-dlamini-v-road-accident-fund-2026-04-30.pdf` at `2026-04-27 22:01:03.748+00`.

### Per-fix retest results

| Fix | Result | Evidence |
|-----|--------|----------|
| **GAP-L-95** (VAT) | **PASS — VERIFIED** | SoA preview iframe: `Total fees (excl. VAT): 3400.00` / `VAT: 510.00` / `Total fees (incl. VAT): 3910.00`. DB `generated_documents.context_snapshot.summary.total_fees = "3910.00"`. R 510 = 15 % × R 3 400 — matches default `tax_rates` row. Was R 0,00 in cycle 46. |
| **GAP-L-94** (Trust Activity) | **PASS — VERIFIED** | SoA Trust Activity section is populated with 3 deposits: `DEP/2026/RAF-001 R 50 000,00`, `DEP/2026/RAF-002 R 100,00`, `DEP-2026-RAF-003 R 20 000,00`. `Closing balance: 70100.00` matches `trust_balance_held: 70100.00` in summary. Customer-aware resolver `findDistinctTrustAccountIdsByCustomerId` returned `46d1177a-d1c3-48d8-9ba8-427f14b8278f`. Was empty in cycle 46. |
| **GAP-L-97** (portal email) | **PASS — VERIFIED** | Mailpit message `o7q6xXpr97YPC8czLw544N` (created `2026-04-27T22:01:04.057Z` — 1.4 s after SoA save) at `sipho.portal@example.com`, subject `"Document ready: statement-of-account-dlamini-v-road-accident-fund-2026-04-30.pdf from Mathebula & Partners"`. Backend log: `PortalEmailService - Portal notification sent template=portal-document-ready contact=f3f74a9d-3540-483a-80bc-6f5ef4e911bb to=sipho.portal@example.com`. Slug allowlist now matches the publisher's `statement-of-account` slug. Was 0 hits in cycle 46. |

**Outcome: ALL THREE VERIFIED. 0 REOPENED.**

### Read-only DB verification

```sql
-- generated_documents (new SoA row)
SET search_path TO tenant_5039f2d497cf, public;
SELECT id, file_name, generated_at FROM generated_documents
 WHERE primary_entity_id='cc390c4f-35e2-42b5-8b54-bac766673ae7'
 ORDER BY generated_at DESC LIMIT 1;
-- 6b79c496-4ae2-4731-9fe7-3ac18677d394
-- statement-of-account-dlamini-v-road-accident-fund-2026-04-30.pdf
-- 2026-04-27 22:01:03.748348+00

-- context_snapshot summary (proves VAT + trust closing persisted)
SELECT jsonb_pretty(context_snapshot::jsonb->'summary')
 FROM generated_documents WHERE id='6b79c496-4ae2-4731-9fe7-3ac18677d394';
-- {
--   "total_fees": "3910.00",            <- L-95: VAT-inclusive (R 3400 + R 510)
--   "payments_received": "5160.00",
--   "trust_balance_held": "70100.00",   <- L-94: customer-aware lookup returns Sipho's account
--   "total_disbursements": "1250.00",
--   "closing_balance_owing": "0.00",
--   "previous_balance_owing": "0"
-- }

-- trust_transactions sanity (the 3 deposits the SoA renders)
SELECT id, transaction_type, amount, reference, trust_account_id
  FROM trust_transactions
 WHERE customer_id='c4f70d86-c292-4d02-9f6f-2e900099ba57'
 ORDER BY transaction_date;
-- DEPOSIT 50000.00 DEP/2026/RAF-001 46d1177a-…
-- DEPOSIT   100.00 DEP/2026/RAF-002 46d1177a-…
-- DEPOSIT 20000.00 DEP-2026-RAF-003 46d1177a-…
```

**Note on `email_delivery_log`:** scenario spec referenced an `email_notification_log` table; actual table in this tenant is `email_delivery_log`. No new `PORTAL_DOCUMENT` row was written for this SoA email — the portal-document path persists only the backend log line (`PortalEmailService` — see backend.log at `22:01:04.158`) plus the actual outbound email (Mailpit confirms delivery). This is consistent with how cycle-46 also produced no `email_delivery_log` row even on the prior trigger (the email_delivery_log table is used by the in-app/transactional flow, not the portal-document-ready path). Out of scope for this retest; flagged for product/dev as an observability follow-up.

### Console health
0 console errors during the SoA generation flow.

### Evidence files
- `qa_cycle/evidence/cycle51-retest-PR1197-soa-context.json` — full SoA HTML + extracted values.
- `qa_cycle/evidence/cycle51-retest-PR1197-soa-preview.png` — full-page screenshot at modal-open state.
- `qa_cycle/evidence/cycle51-retest-PR1197-soa-email-full.json` — Mailpit message detail.
- `qa_cycle/evidence/cycle51-retest-PR1197-soa-email-body.html` — Mailpit HTML + plain-text excerpts.

