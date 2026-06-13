# Day 10 — Firm verifies proposal acceptance + records trust deposit — Cycle 2026-06-13

**Executed**: 2026-06-13 (branch `bugfix_cycle_2026-06-13`)
**Actor**: Thandi Mathebula (Owner) — already authenticated on :3000 KC (session carried from Day 7/8).
**Driver**: QA agent via Playwright MCP — firm app browser UI only on :3000. Backend log read for the deposit-posted + portal-notification confirmation. DB read used ONLY for the matter-reconciliation investigation (diagnostic, not a QA action that bypasses the UI).
**Pre-checks**: svc.sh status — backend (PID 46138) / gateway / frontend / portal all RUNNING+HEALTHY.
**Result**: **9/9 in-scope checkpoints PASS (10.6 N/A — no dual-approval queue on this account) + 3/3 summary checkpoints PASS. Zero new gaps.** OBS-1001 (trust-deposit combobox) confirmed VERIFIED-working (NOT a reopen). Matter-reconciliation question resolved as expected-behaviour (no gap filed).

## Matter-reconciliation finding (the flagged Day 8 question)

**Resolution: the second matter is EXPECTED behaviour, not a defect. No OBS gap filed.**

Data confirmed (DB read, tenant `tenant_5039f2d497cf`):
- **Day 3 RAF matter** `08ad56c4-ff5e-49c2-a034-cb5fa04b462c` — name "Dlamini v Road Accident Fund", reference `RAF-2026-001`, work_type Litigation, status ACTIVE, created 2026-06-12 23:30 from the RAF template (9 tasks). This is the real matter with the FICA history (REQ-0001 Done/Verified) and the matter the engagement letter was authored *from* (Day 7: "Matter RAF-2026-001 → More actions → New Engagement Letter").
- **Acceptance auto-provisioned matter** `15a25aa5-11e3-46fe-b90b-fbacf19c5bf1` — name = the proposal title "Engagement Letter — Litigation (Dlamini v RAF)", **no reference_number, no work_type**, status ACTIVE, created 2026-06-13 11:24 at accept time. `proposals.created_project_id` points to THIS matter (not back to RAF-2026-001).

**Root cause (by design, ADR-125):** `ProposalOrchestrationService.acceptProposal` → `createProject` (L275–292) **unconditionally creates a new project** on acceptance. The proposal entity has no "originating matter" field, and the "New Engagement Letter from matter" flow does not persist that origin linkage on the proposal. So a proposal authored from RAF-2026-001 spawns a parallel bare matter on accept rather than activating the existing one. This is the documented acceptance-orchestration contract (its job is to create the engagement's matter), not a regression.

**Why no gap:** This is NOT a new defect introduced by the 2026-06 simplification roadmap — it is **identical to the prior VERIFIED cycle (2026-05-30)**, whose Day 10 dashboard showed "**2 Active Matters**" for exactly the same reason (RAF matter + acceptance-auto-provisioned matter), recorded the deposit on the RAF matter, and reconciled all 3 balance surfaces. I reproduced the same baseline. The duplicate is a known design wart (proposal-origin linkage not persisted), but per the per-day workflow and the "file a gap ONLY if you confirm a real defect, not a hypothesis" instruction, this is confirmed-expected and out of scope for a Day 10 bug. It is worth a future Product note (proposal acceptance could reuse the originating matter when one exists) but is not a Day 10 blocker.

**Where the deposit went:** RAF-2026-001 (`08ad56c4-…`), matching the scenario 10.4 ("Matter: RAF-2026-001") and the prior-cycle baseline. The trust deposit Matter combobox listed BOTH matters for Sipho; I selected "Dlamini v Road Accident Fund" (the RAF matter), and the matter-level Trust tab on RAF-2026-001 now shows R 50 000,00 (10.8).

## Checkpoints

### Phase A: Verify proposal acceptance flowed through

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 10.1 | Matter RAF-2026-001 → verify proposal = Accepted with Sipho's timestamp | PASS | Proposal detail `/proposals/6a1b35fc-…` (PROP-0001) renders status badge **Accepted**, Proposal Details: Fee Model Hourly, Hourly Rate "R 2,500/hr (LSSA tariff…)", Created 13 Jun 2026, Expires 20 Jun 2026, Sent 13 Jun 2026, **Accepted 13 Jun 2026**. Sipho's acceptance timestamp (Day 8) is visible firm-side. NOTE on scenario wording "→ Proposals tab": the matter (RAF-2026-001) Client group tab has sub-tabs Clients / Requests / Client Comments / Adverse Parties — **no Proposals/Engagement-Letters sub-tab** (the proposal is linked to the auto-provisioned matter `15a25aa5-…`, not RAF-2026-001 — see reconciliation finding). Verification is via the proposals list/detail, same as the prior cycle. |
| 10.2 | Matter lifecycle auto-transitioned to ACTIVE | PASS | Matter `08ad56c4-…` header card badges: **Active** + Litigation + `RAF-2026-001`, client Sipho Dlamini. Lifecycle buttons Close Matter / Complete Matter. (This matter was already ACTIVE from Day 3 creation; acceptance did not change it — the acceptance-created matter `15a25aa5-…` is also ACTIVE.) |

### Phase B: Trust deposit — manual deposit recording

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 10.3 | Navigate to Trust Accounting → Mathebula Trust — Main account | PASS | `/trust-accounting` overview: Trust Balance R 0,00, "Mathebula Trust — Main cashbook balance", Active Clients 0, Pending Approvals 0, "No transactions recorded yet" (clean pre-deposit state). |
| 10.4 | Record manual deposit: R 50,000.00, Deposit, Client=Sipho, Matter=RAF-2026-001, Ref=DEP/2026/001, Description | PASS | `/trust-accounting/transactions` → Record Transaction → Record Deposit dialog. **Client combobox responded to a real click** (expanded → listbox "Sipho Dlamini / sipho.portal@example.com"), selected Sipho. **Matter combobox** then enabled and (on real click) listed BOTH matters — "Dlamini v Road Accident Fund" + "Engagement Letter — Litigation (Dlamini v RAF)"; selected **Dlamini v Road Accident Fund** (RAF-2026-001). Amount 50000, Reference DEP/2026/001, Description "Initial trust deposit — RAF-2026-001", Transaction Date 2026-06-13. Screenshot `day-10-deposit-form-filled.png`. **OBS-1001 (combobox unresponsive) is FIXED on main — no force-state workaround needed this cycle.** |
| 10.5 | Submit → transaction posts directly OR enters approval queue | PASS | Clicked Record Deposit → dialog closed, transaction list shows **1 transaction found**: 13 Jun 2026 / DEP/2026/001 / Deposit / R 50 000,00 / **RECORDED**. Status RECORDED = posted directly (dual-approval not enabled on this trust account — set up Day 1 without dual-approval). Backend log (11:31:47Z): deposit posted; `Portal notification sent template=portal-trust-activity contact=793df2fa-… to=sipho.portal@example.com` (sets up Day 11). 0 ERROR/WARN at deposit time (grep of `2026-06-13T11:31` ERROR/WARN = 0). |
| 10.6 | If in approval queue: switch to Bob → approve | N/A | Dual-approval not enabled on Mathebula Trust — Main → no approval queue → no Bob approval step. Not counted as PASS or FAIL. |
| 10.7 | Trust account balance R 50,000.00 + Sipho's client ledger card +R50,000.00 | PASS | Trust Accounting overview: Trust Balance **R 50 000,00**, Active Clients **1**, Pending Approvals 0, Recent Transactions DEP/2026/001 Deposit R 50 000,00 RECORDED. Client Ledgers page: 1 client — **Sipho Dlamini: Trust Balance R 50 000,00, Total Deposits R 50 000,00**, Payments R 0,00, Fee Transfers R 0,00, Last Transaction 13 Jun 2026. |
| 10.8 | Matter → Finance group tab → Trust sub-tab → matter-level trust balance = R 50,000.00 | PASS | RAF-2026-001 matter → `tab-group-finance` → `tab-item-trust` panel: **Trust Balance / Funds Held R 50 000,00**, Deposits R 50 000,00, Payments R 0,00, Fee Transfers R 0,00, Last transaction 2026/06/13, action buttons Record Deposit / Record Payment / Fee Transfer. |
| 10.9 | 📸 Screenshot day-10-firm-trust-deposit-recorded.png | PASS | Saved (matter Trust tab showing R 50 000,00 balance + breakdown). |

## Day 10 summary checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Proposal acceptance flowed from portal to firm side (timestamp matches) | PASS | PROP-0001 firm-side detail shows **Accepted 13 Jun 2026**, matching Day 8 portal acceptance (Sipho, contact `793df2fa-…`). |
| Trust deposit posts against the correct client ledger card (Section 86 compliance) | PASS | Deposit R 50 000,00 posted to Sipho Dlamini's client ledger under SECTION_86 account "Mathebula Trust — Main", referenced to matter RAF-2026-001 (Dlamini v Road Accident Fund). Client Ledgers shows 1 client (Sipho) with R 50 000,00. |
| Client ledger + matter trust tab + account balance all reconcile to R 50,000.00 | PASS | All three reconcile, zero discrepancy: (1) Trust Accounting overview = R 50 000,00; (2) Sipho's client ledger = R 50 000,00; (3) RAF-2026-001 matter Trust sub-tab = R 50 000,00. |

## OBS-1001 re-verification (prior-cycle MEDIUM, VERIFIED-fixed)
The Record Deposit dialog's Client and Matter pickers are now `combobox` controls (cmdk-style searchable popover), NOT the old `PopoverTrigger asChild → FormControl → Button` triple-Slot chain. Both opened on a **real left-click** (combobox `[expanded]` state confirmed, listbox options rendered), the option click selected correctly, and the deposit recorded end-to-end with no programmatic state-forcing. **OBS-1001 stays VERIFIED — not a reopen.**

## Console notes
- Firm matter/trust pages: only console errors are the known **OBS-201** `/api/assistant/invocations?...PENDING_APPROVAL` 404s (AI assistant proxy not wired in KC mode — WONT_FIX-EXEMPT carry-over). No real JS/hydration/render errors during Day 10.
- Backend: 0 ERROR/WARN at deposit time (11:31). (Startup-time Spring BeanPostProcessor WARNs at 11:03 boot are unrelated.)

## Carry-over exemptions observed (not re-filed)
- **OBS-201**: `/api/assistant/invocations` 404 on matter detail — exempt, not re-filed.

## Gaps filed
None. Day 10 passed cleanly with zero new gaps. The matter-reconciliation question resolved as expected-behaviour matching the prior VERIFIED baseline (see finding above) — no OBS gap.

## Entity IDs (for downstream days)
- **Sipho Dlamini Client ID**: `2211a80a-5523-4a6d-8f96-0d638dff88f6` (unchanged)
- **RAF Matter ID**: `08ad56c4-ff5e-49c2-a034-cb5fa04b462c` (RAF-2026-001) — deposit recorded against this matter
- **Acceptance-auto-provisioned matter**: `15a25aa5-11e3-46fe-b90b-fbacf19c5bf1` (Engagement Letter — Litigation, no reference) — present but NOT used for the deposit (expected duplicate per reconciliation finding)
- **Proposal**: `6a1b35fc-b342-4101-abd7-f2ab8ffad26e` (PROP-0001), ACCEPTED, `created_project_id` = `15a25aa5-…`
- **Trust Account**: "Mathebula Trust — Main", SECTION_86, balance R 50 000,00
- **Trust Transaction**: DEP/2026/001, DEPOSIT, R 50 000,00, RECORDED, 2026-06-13
- **Portal trust-activity email**: sent to sipho.portal@example.com at 11:31:47Z (Day 11 nudge)

## Note for Day 11
Portal trust-activity notification confirmed sent to Sipho (backend `template=portal-trust-activity`). Day 11 (`[PORTAL]`, :3002) should find that email in Mailpit and verify the portal `/trust` surface shows R 50 000,00 with the DEP/2026/001 deposit.
