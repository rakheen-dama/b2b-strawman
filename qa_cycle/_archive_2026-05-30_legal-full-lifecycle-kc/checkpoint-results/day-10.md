# Day 10 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025, portal :3002)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Thandi Mathebula (Owner — signs off on trust deposits)

---

## Pre-check: Login as Thandi

Navigated to `http://localhost:3000/dashboard` -> redirected to Keycloak login at `:8180`. Entered `thandi@mathebula-test.local` / `SecureP@ss1`. Logged in successfully, landed on `/org/mathebula-partners/dashboard`. Sidebar shows "TM" avatar, org "Mathebula & Partners", user "Thandi Mathebula" (thandi@mathebula-test.local). Dashboard shows 2 Active Matters including "Dlamini v Road Accident Fund".

---

## Phase A: Verify proposal acceptance flowed through

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 10.1 | Navigate to matter RAF-2026-001 -> Proposals tab -> verify proposal = Accepted with Sipho's timestamp | **PASS** | Navigated to `/org/mathebula-partners/proposals/40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86`. Proposal detail shows: title "Engagement Letter -- Litigation (Dlamini v RAF)", status badge **Accepted**, ref PROP-0001. Proposal Details section: Fee Model=Hourly, Hourly Rate="R 2,500/hr (LSSA tariff...)", Created=May 30, 2026, Expires=Jun 16, 2026, Sent=May 30, 2026, **Accepted=May 30, 2026**. Sipho's acceptance timestamp recorded and visible on firm side. |
| 10.2 | Matter lifecycle: verify matter has auto-transitioned to ACTIVE | **PASS** | Navigated to `/org/mathebula-partners/projects/d80aeac5-...`. Header card shows badges: **Active** + Litigation, reference RAF-2026-001, client "Sipho Dlamini". Lifecycle buttons: Close Matter, Complete Matter. Matter is in ACTIVE state. |

---

## Phase B: Trust deposit -- manual deposit recording

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 10.3 | Navigate to Trust Accounting -> Mathebula Trust -- Main account | **PASS** | Navigated to `/org/mathebula-partners/trust-accounting`. Page shows: Trust Balance R 0,00, Active Clients 0, Pending Approvals 0, "Mathebula Trust -- Main cashbook balance". "No transactions recorded yet". |
| 10.4 | Record manual deposit: R 50,000.00, Deposit, Client=Sipho Dlamini, Matter=RAF-2026-001, Ref=DEP/2026/001, Description="Initial trust deposit -- RAF-2026-001" | **PASS** | Navigated to `/org/mathebula-partners/trust-accounting/transactions`. Clicked Record Transaction -> Record Deposit. Dialog: "Record Deposit". Filled: Client=Sipho Dlamini, Matter=Dlamini v Road Accident Fund, Amount=50000, Reference=DEP/2026/001, Description="Initial trust deposit -- RAF-2026-001", Transaction Date=2026-05-30. Clicked Record Deposit. Dialog closed. Transaction appeared in list: 30 May 2026, DEP/2026/001, Deposit, R 50 000,00, RECORDED. **Note**: OBS-1001 -- Popover combobox triggers (Client/Matter pickers) inside the Record Deposit dialog do not respond to clicks. Root cause: triple Slot composition chain (`PopoverTrigger asChild` -> `FormControl` (renders Slot) -> `Button`) inside a Radix Dialog. Workaround: forced React state to open popovers and clicked items via DOM evaluate. The deposit itself recorded correctly. |
| 10.5 | Submit -> transaction posts directly (no dual-approval) | **PASS** | Transaction status = **RECORDED** (posted directly). Dual-approval was not enabled on the trust account during Day 1 setup, so no approval queue. |
| 10.6 | If in approval queue: switch to Bob -> approve | **N/A** | Dual-approval not enabled. No approval needed. |
| 10.7 | Trust account balance reflects R 50,000.00 with Sipho's client ledger card showing +R50,000.00 | **PASS** | Trust Accounting overview: Trust Balance **R 50 000,00**, Active Clients **1**, Pending Approvals 0. Recent Transactions: DEP/2026/001, Deposit, R 50 000,00, RECORDED, 30 May 2026. Client Ledgers page (`/trust-accounting/client-ledgers`): 1 client found. Sipho Dlamini row: Trust Balance **R 50 000,00**, Total Deposits R 50 000,00, Total Payments R 0,00, Total Fee Transfers R 0,00, Last Transaction 30 May 2026. |
| 10.8 | Matter -> Finance group tab -> Trust sub-tab -> verify matter-level trust balance = R 50,000.00 | **PASS** | Navigated to matter detail. Clicked Finance tab group -> Trust sub-tab (`tab-item-trust`). Tab panel renders: Trust Balance **R 50 000,00**, "Funds Held". Breakdown: Deposits R 50 000,00, Payments R 0,00, Fee Transfers R 0,00. Last transaction: 2026/05/30. Action buttons: Record Deposit, Record Payment, Fee Transfer. |
| 10.9 | Screenshot: day-10-firm-trust-deposit-recorded.png | **PASS** | Screenshot captured: `day-10-firm-trust-deposit-recorded.png` showing matter trust tab with R 50 000,00 balance, deposits/payments/fee transfers breakdown. |

---

## Day 10 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Proposal acceptance flowed from portal to firm side (timestamp matches) | **PASS** | Proposal detail page at `/proposals/40e7fd6b-...` shows Accepted status with "Accepted: May 30, 2026". PROP-0001 reference, fee model Hourly, rate R 2,500/hr. Acceptance timestamp visible on firm side, matching Day 8 portal acceptance. |
| Trust deposit posts against the correct client ledger card (Section 86 compliance) | **PASS** | Deposit R 50,000.00 posted to Sipho Dlamini's client ledger under "Mathebula Trust -- Main" (SECTION_86 account). Client Ledgers page shows 1 client (Sipho Dlamini) with R 50 000,00 balance. Transaction reference DEP/2026/001 with matter link to RAF-2026-001. |
| Client ledger + matter trust tab + account balance all reconcile to R 50,000.00 | **PASS** | All three reconcile: (1) Trust Accounting overview balance = R 50 000,00, (2) Client Ledger for Sipho Dlamini = R 50 000,00, (3) Matter Trust sub-tab = R 50 000,00. Zero discrepancy. |

---

## Console Errors

3x 404 errors for `/api/assistant/invocations` -- all are the known OBS-201 (WONT_FIX-EXEMPT, AI assistant endpoint not wired in KC mode). Fires on matter detail page load. No user-facing impact.

**Zero JavaScript/hydration/rendering errors observed during Day 10 execution.**

## Gaps Filed

| Gap ID | Summary | Severity | Notes |
|--------|---------|----------|-------|
| OBS-1001 | Trust deposit dialog: Popover combobox triggers (Client/Matter pickers) do not respond to clicks | MEDIUM | **Root cause**: Triple Slot composition chain inside Radix Dialog -- `PopoverTrigger asChild` wraps `FormControl` (which renders `Slot`) wraps `Button`. This is the OBS-2103 bug class documented in CLAUDE.md (Radix Slot composition collision). Neither mouse clicks, keyboard Space/Enter, nor `force: true` opens the Popover. The Popover content renders correctly when state is forced programmatically, and the deposit records successfully. **Impact**: Users cannot click the Client or Matter dropdowns in the Record Deposit (and likely Record Payment/Refund) dialogs. **Workaround for QA**: forced React state via browser evaluate. **Fix**: remove the `asChild` chain -- either have `PopoverTrigger` render its own button, or remove `FormControl` from the composition chain, matching the dialog-owns-button pattern from CLAUDE.md. |

## Entity IDs (for downstream days)

- **Sipho Dlamini Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf` (unchanged)
- **Matter ID**: `d80aeac5-d5f4-4690-9291-193f05e3785d` (unchanged)
- **Matter Reference**: RAF-2026-001 (unchanged)
- **Proposal ID**: `40e7fd6b-efa1-4f53-8a1a-4a8f5291ae86` (unchanged)
- **Proposal Status**: ACCEPTED (confirmed on firm side)
- **Trust Account**: "Mathebula Trust -- Main", SECTION_86, Balance R 50 000,00
- **Trust Transaction**: DEP/2026/001, DEPOSIT, R 50 000,00, RECORDED, 2026-05-30
- **Client Ledger URL**: `/org/mathebula-partners/trust-accounting/client-ledgers/d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`
