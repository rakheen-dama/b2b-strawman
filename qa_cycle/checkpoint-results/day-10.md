# Day 10 — Firm verifies proposal acceptance, deposits trust funds

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-13`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)
**Actor**: Thandi Mathebula (Owner) on firm `:3000`

## Pre-flight

- All services healthy: frontend :3000 (200), backend :8080 (200), Mailpit :8025 (200).
- Day 8 closed clean: PROP-0001 ACCEPTED on portal by Sipho. 11/11 PASS.
- Branch: `bugfix_cycle_2026-05-13`.

## Phase A — Verify proposal acceptance flowed through

### 10.1 Proposal PROP-0001 = Accepted with Sipho's timestamp

- Navigated to `/org/mathebula-partners/proposals`. Breadcrumb reads "Engagement Letters" (legal-za terminology).
- Proposals index shows: 1 Total, 0 Pending, 1 Accepted, 100.0% Conversion Rate.
- PROP-0001 row: "Engagement Letter — Litigation (Dlamini v RAF)" / PROP-0001 / **Accepted** / Sent Date May 14, 2026.
- Clicked into detail page (`/proposals/d7481b7a-8878-43ee-928c-2845bf8bffd0`):
  - Title: "Engagement Letter — Litigation (Dlamini v RAF)"
  - Status badge: **Accepted**
  - Fee Model: Hourly
  - Hourly Rate: R 2,500/hr (LSSA tariff High Court Party-and-Party 2024/2025) — 30h Bob Ndlovu + 5h Thandi Mathebula ~ R 87,500.00 estimate.
  - Created: May 14, 2026
  - Sent: May 14, 2026
  - **Accepted: May 14, 2026** (matches Day 8 portal accept)
  - Expires: May 21, 2026
- **PASS.**
- Evidence: `qa_cycle/evidence/day-10/day-10.1-proposal-accepted-firm-side.png`.

### 10.2 Matter lifecycle = ACTIVE

- Matter detail (`/projects/c90832a4-c993-4eaa-9ea7-404a259b0e29`): heading "Dlamini v Road Accident Fund" with **Active** green badge.
- Ref: RAF-2026-001, Client: Sipho Dlamini.
- Full tab set present: Overview, Documents, Members, Clients, Tasks, Time, Fee Estimate, Financials, Staffing, Rates, Generated Docs, Requests, Client Comments, Court Dates, Adverse Parties, Trust, Disbursements, Statements, Activity, Audit Trail.
- FICA Status card: "Done — Verified May 13, 2026".
- **PASS.**

## Phase B — Trust deposit

### 10.3 Navigate to Trust Accounting overview (pre-deposit state)

- Finance > Trust Accounting navigated successfully.
- Pre-deposit state: Trust Balance R 0,00, Active Clients 0, Pending Approvals 0, Recent Transactions empty.
- Trust account: Mathebula Trust — Main (Standard Bank, 051001, SECTION_86, Single approval).
- **PASS.**
- Evidence: `qa_cycle/evidence/day-10/day-10.3-trust-accounting-pre-deposit.png`.

### 10.4 Record manual deposit (via matter Trust tab)

- **OBS-1002 (BLOCKER for standalone transactions page)**: On the standalone Trust Accounting > Transactions page, the "Record Deposit" dialog's Client combobox picker is non-functional. The Popover (Radix) does not open on click. Root cause: triple `Slot` composition — `<PopoverTrigger asChild>` wraps `<FormControl>` (which uses `Slot`) wraps `<Button>`. The Radix Popover event handler does not bind to the final button element. `aria-expanded` stays `false`, `onclick` is null on the button, no `data-radix-popover-trigger` attribute present. Same anti-pattern class as OBS-2103 (documented in CLAUDE.md Dialog Trigger Composition section).
- **Workaround**: Used the matter Trust tab (`/projects/{id}?tab=trust`) > "Record Deposit" button instead. When invoked from the matter context, `defaultCustomerId` and `defaultProjectId` are pre-supplied, so the pickers render pre-populated and disabled — bypassing the broken combobox.
- Filled via matter Trust tab:
  - Client: Sipho Dlamini (pre-filled, disabled)
  - Matter: Dlamini v Road Accident Fund (pre-filled, disabled)
  - Amount: 50000
  - Reference: DEP/2026/001
  - Description: "Initial trust deposit — RAF-2026-001"
  - Transaction Date: 2026-05-14 (default)
- Evidence: `qa_cycle/evidence/day-10/day-10.4-deposit-form-filled.png`.

### 10.5 Submit — deposit recorded

- Clicked "Record Deposit" in the dialog. Dialog closed. Trust tab immediately refreshed.
- Status = **RECORDED** (single-approval account — posts directly, no approval queue).
- **PASS.**

### 10.6 Approval queue

- **N/A** — Mathebula Trust — Main is configured for Single approval (set Day 1). Pending Approvals = 0 throughout. Skipped per scenario branching.

### 10.7 Trust account balance + client ledger reconcile to R 50,000.00

- Trust Accounting overview:
  - **Trust Balance = R 50 000,00**
  - **Active Clients = 1**
  - Pending Approvals = 0
  - Recent Transactions: 14 May 2026 / DEP/2026/001 / Deposit / R 50 000,00 / RECORDED
- Client Ledgers page:
  - 1 client found — **Sipho Dlamini** / Trust Balance **R 50 000,00** / Total Deposits **R 50 000,00** / Total Payments R 0,00 / Total Fee Transfers R 0,00 / Last Transaction 14 May 2026.
- Section 86 client ledger card correctly attributes the deposit to Sipho.
- **PASS.**
- Evidence: `qa_cycle/evidence/day-10/day-10.7-trust-overview-50000.png`, `qa_cycle/evidence/day-10/day-10.7-client-ledgers-sipho.png`.

### 10.8 Matter Trust tab = R 50,000.00

- Matter RAF-2026-001 > Trust tab:
  - Trust Balance card: **Funds Held — R 50 000,00**
  - Deposits: R 50 000,00
  - Payments: R 0,00
  - Fee Transfers: R 0,00
  - Last transaction: 2026/05/14
- **PASS.**
- Evidence: `qa_cycle/evidence/day-10/day-10.8-matter-trust-tab-50000.png`.

### 10.9 Trust deposit notification email

- Mailpit confirms email sent to `sipho.portal@example.com`:
  - Subject: "Mathebula & Partners: Trust account activity"
  - Body: "A new transaction has been recorded in your trust account. Date 13 May 2026. Type DEPOSIT. Amount R 50 000,00. View trust ledger."
- **PASS.**

## Day 10 day-end checkpoints

| # | Checkpoint | Result |
|---|---|---|
| 1 | Proposal acceptance flowed from portal to firm side (timestamp matches) | PASS — proposal detail shows `Accepted: May 14, 2026`, matches Day 8 portal accept. |
| 2 | Trust deposit posts against the correct client ledger card (Section 86 compliance) | PASS — Sipho Dlamini ledger row R 50 000,00 deposit; trust account = SECTION_86. |
| 3 | Client ledger + matter trust tab + account balance all reconcile to R 50,000.00 | PASS — three surfaces all read R 50 000,00 (account: Trust Balance card; matter: Funds Held card; client: Trust Balance / Total Deposits). |

## Day 10 summary

| Item | Outcome | Evidence |
|---|---|---|
| 10.1 Proposal accepted firm-side | PASS (org `/proposals` index + detail page) | day-10.1-proposal-accepted-firm-side.png |
| 10.2 Matter ACTIVE | PASS (Active badge on header) | (in 10.1 navigation) |
| 10.3 Trust Accounting nav + pre-deposit state | PASS | day-10.3-trust-accounting-pre-deposit.png |
| 10.4 Deposit form (via matter Trust tab workaround) | PASS (form fills correctly when client/matter pre-locked) | day-10.4-deposit-form-filled.png |
| 10.5 Submit (Single approval direct post) | PASS — status RECORDED | (dialog closed, tab refreshed) |
| 10.6 Approval queue | N/A (single-approval account) | n/a |
| 10.7 Trust overview + client ledger reconcile | PASS — R 50 000,00 across cashbook + client ledger | day-10.7-trust-overview-50000.png + day-10.7-client-ledgers-sipho.png |
| 10.8 Matter Trust tab = R 50 000,00 | PASS | day-10.8-matter-trust-tab-50000.png |
| 10.9 Trust deposit notification email | PASS — "Trust account activity" email sent to Sipho | (Mailpit API verified) |
| Console errors | No new errors. Pre-existing: OBS-203 (assistant 404), OBS-704 (proposals hydration mismatch). | browser_console_messages log |

## New gaps filed

**OBS-1002** (UX / functional): Trust deposit "Record Deposit" dialog on the standalone Transactions page (`/trust-accounting/transactions`) has a non-functional Client combobox. The Radix Popover does not open on click due to triple Slot composition (`PopoverTrigger asChild` > `FormControl` (Slot) > `Button`). The popover event handler is lost — `aria-expanded` stays `false`, no `onclick` on the button, no `data-radix-popover-trigger` attribute. Same anti-pattern class as OBS-2103 (Dialog Trigger Composition).

**Severity**: HIGH — blocks trust deposit recording from the standalone transactions page. Workaround exists: deposit from the matter Trust tab (where client/matter are pre-locked, bypassing the broken picker). Also affects Record Payment and Record Refund dialogs (same `TrustCustomerPicker` component).

**Workaround**: Use matter Trust tab > "Record Deposit" / "Record Payment" / "Fee Transfer" buttons. Pre-locked client+matter bypass the broken combobox.

## Entities touched

- Trust transaction `DEP/2026/001` recorded for Sipho Dlamini / RAF-2026-001 / R 50 000,00 / status RECORDED / date 2026-05-14 / description "Initial trust deposit — RAF-2026-001".
- Trust account Mathebula Trust — Main: cashbook balance 0,00 -> 50 000,00.
- Sipho Dlamini client ledger: opened with first deposit (R 50 000,00 trust balance).
- Mailpit: trust deposit notification email sent to sipho.portal@example.com.

## QA Position

**Day 10 — COMPLETE.** All scenario checkpoints passed (via matter Trust tab workaround for deposit). One new gap filed (OBS-1002 — broken combobox on standalone transactions page). Ready to dispatch **Day 11** (Sipho on portal sees trust balance card with first deposit).
