# Day 10 — Firm activates matter, deposits trust funds

**Date**: 2026-04-30
**Branch**: `bugfix_cycle_2026-04-30`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)
**Actor**: Thandi Mathebula (Owner) on firm `:3000`

## Pre-flight

- All 4 services healthy via `svc.sh status` (backend 13557, gateway 18539, frontend 18686, portal 18737).
- Day 8 closed clean: PROP-0003 ACCEPTED on portal by Sipho. OBS-704 v2 + OBS-703 + OBS-702 all VERIFIED.

## Phase A — Verify proposal acceptance flowed through

### 10.1 Matter → Proposals → PROP-0003 = Accepted with Sipho's timestamp
- Matter `RAF-2026-001` does **not** have a per-matter Proposals tab. Tab list (canonical legal-za, per OBS-302 amendment): Overview, Documents, Members, Clients, Tasks, Time, **Fee Estimate**, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, **Trust**, Disbursements, Statements, Activity.
- Proposals are managed at the org level (`/org/{slug}/proposals`). Index page confirms PROP-0003 row: `Engagement Letter — Litigation (Dlamini v RAF) — verify cycle 2 / PROP-0003 / Accepted / Apr 30, 2026`.
- Proposal detail (`/proposals/d9589314-…`) shows **Accepted** badge + metadata `Created: Apr 30, 2026 · Sent: Apr 30, 2026 · Accepted: Apr 30, 2026 · Expires: May 12, 2026`.
- Sipho's acceptance flowed through firm-side. **PASS.**
- Evidence: `qa_cycle/evidence/day-10/day-10.1-proposal-accepted-firm-side.png`.

### 10.2 Matter lifecycle = ACTIVE
- Matter detail header shows **Active** badge (green pill, ref `e168` in snapshot).
- Phase 29 lifecycle auto-transition path confirmed (matter was created Day 3 already in ACTIVE state per template instantiation defaults; no manual transition required). **PASS.**

## Phase B — Trust deposit

### 10.3 Navigate to Trust Accounting → Mathebula Trust — Main
- Sidebar Finance group → Trust Accounting (per OBS-102 fix landed at start of cycle).
- Pre-deposit state: Trust Balance R 0,00, Active Clients 0, Pending Approvals 0, Recent Transactions empty.
- Page renders the firm's single trust account (Mathebula Trust — Main · Standard Bank · 051001 · 12345678 · SECTION_86 · Single approval). **PASS.**

### 10.4 Record manual deposit (option (b) from scenario — chose (b) over CSV import for direct path)
- Click **Record Transaction** → dropdown menu opens with Record Deposit / Payment / Transfer / Fee Transfer / Refund.
- Click **Record Deposit** → modal `Record Deposit` opens with form fields: Client ID, Matter (Optional), Amount, Reference, Description (Optional), Transaction Date.
- Filled:
  - Client ID = `a30bb16b-743c-45a5-9fb5-13167fb92fde` (Sipho Dlamini)
  - Matter = `b7e319f7-fd7e-4526-a8b3-b40b1f85b34b` (RAF-2026-001)
  - Amount = 50000
  - Reference = `DEP/2026/001`
  - Description = `Initial trust deposit — RAF-2026-001`
  - Transaction Date = 2026-04-30 (default)
- Evidence: `day-10.4-record-deposit-dialog.png`, `day-10.4-deposit-form-filled.png`.

> **Observation OBS-1001 (UX nit)**: The Record Deposit form requires raw UUIDs for Client and Matter (placeholders read `Client UUID` / `Matter UUID (optional)`), not picker dropdowns. Functional — QA filled by pasting the known UUIDs from the status tracker — but real users will not have UUIDs to hand. Filed for triage; not a blocker for Day 10 (workaround = paste UUID).

### 10.5 Submit
- Clicked **Record Deposit** → dialog closed → transaction list immediately reflects 1 row: `30 Apr 2026 / DEP/2026/001 / Deposit / R 50 000,00 / RECORDED`.
- Status = **RECORDED** (not AWAITING_APPROVAL) because Mathebula Trust — Main is configured for **Single approval** (set Day 1, ck 1.6). Per scenario this is the "posts directly" branch. **PASS.**
- Evidence: `day-10.5-deposit-recorded.png`.

### 10.6 Approval queue
- **N/A** for this trust account (Single approval). Pending Approvals counter = 0 throughout. Skipped per scenario branching.

### 10.7 Trust account balance reflects R 50,000.00 + Sipho's client ledger card
- Trust Accounting overview: **Trust Balance = R 50 000,00**, **Active Clients = 1**, Pending Approvals = 0, Recent Transactions row = `30 Apr 2026 / DEP/2026/001 / Deposit / R 50 000,00 / RECORDED`. Evidence: `day-10.7-trust-overview-50000.png`.
- Client Ledgers page: 1 client found — `Sipho Dlamini / Trust Balance R 50 000,00 / Total Deposits R 50 000,00 / Total Payments R 0,00 / Total Fee Transfers R 0,00 / Last Transaction 30 Apr 2026`. Section 86 client ledger card correctly attributes the deposit to Sipho. Evidence: `day-10.7-client-ledgers-sipho.png`. **PASS.**

### 10.8 Matter Trust tab = R 50,000.00
- Matter `RAF-2026-001` → Trust tab renders: **Trust Balance — Funds Held — R 50 000,00**, Deposits R 50 000,00, Payments R 0,00, Fee Transfers R 0,00, Last transaction: 2026/04/30. Action buttons: Record Deposit / Record Payment / Fee Transfer.
- Evidence: `day-10.8-matter-trust-tab.png`, `day-10.8-matter-trust-tab-full.png`. **PASS.**

### 10.9 Optional screenshot
- Captured: `day-10.7-trust-overview-50000.png` (firm-side trust deposit recorded view).

## Day 10 day-end checkpoints

| # | Checkpoint | Result |
|---|---|---|
| 1 | Proposal acceptance flowed from portal to firm side (timestamp matches) | PASS — proposal detail shows `Accepted: Apr 30, 2026`, matches Day 8 portal accept time. |
| 2 | Trust deposit posts against the correct client ledger card (Section 86 compliance) | PASS — Sipho Dlamini ledger row R 50 000,00 deposit; trust account = SECTION_86 confirmed Day 1. |
| 3 | Client ledger + matter trust tab + account balance all reconcile to R 50,000.00 | PASS — three surfaces all read R 50 000,00 (account: Trust Balance card; matter: Funds Held card; client: Trust Balance / Total Deposits). |

## Day 10 summary

| Item | Outcome | Evidence |
|---|---|---|
| 10.1 Proposal accepted firm-side | PASS (org `/proposals` index + detail page; matter has no Proposals tab — canonical) | day-10.1-proposal-accepted-firm-side.png |
| 10.2 Matter ACTIVE | PASS (Active badge already on header) | (in 10.1 screenshot) |
| 10.3 Trust Accounting nav | PASS | (covered by OBS-102 verify) |
| 10.4 Deposit form | PASS (form fills via React-friendly setters; ENV-001 applied) | day-10.4-record-deposit-dialog.png + day-10.4-deposit-form-filled.png |
| 10.5 Submit (Single approval direct post) | PASS — status RECORDED | day-10.5-deposit-recorded.png |
| 10.6 Approval queue | N/A (single-approval account) | n/a |
| 10.7 Trust + client ledger reconcile | PASS — R 50 000,00 across cashbook + client ledger | day-10.7-trust-overview-50000.png + day-10.7-client-ledgers-sipho.png |
| 10.8 Matter Trust tab = R 50 000,00 | PASS | day-10.8-matter-trust-tab.png + day-10.8-matter-trust-tab-full.png |
| 10.9 Screenshot | PASS | (above) |
| Console clean | PASS — 0 errors / 0 warnings throughout (verified after each navigation) | (browser_console_messages logs) |

## New gaps filed

**OBS-1001** (UX nit): Record Deposit form has no client/matter pickers — fields require raw UUID paste. Functional but not real-user-friendly. Not blocking. Recommend Product triage for typeahead picker (matches Conflict Check pattern).

## Entities touched

- Trust transaction `DEP/2026/001` recorded for Sipho Dlamini / RAF-2026-001 / R 50 000,00 / status RECORDED / date 2026-04-30 / description "Initial trust deposit — RAF-2026-001".
- Trust account Mathebula Trust — Main: cashbook balance 0,00 → 50 000,00.
- Sipho Dlamini client ledger: opened with first deposit (R 50 000,00 trust balance).

## QA Position

**Day 10 — COMPLETE.** All checkpoints passed. Frontend ran clean (0 errors / 0 warnings). One UX nit filed (OBS-1001 — UUID-only deposit form). Ready to dispatch **Day 11** (Sipho on portal sees trust balance card with first deposit).
