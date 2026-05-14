# Day 14 — Firm onboards Moroka Family Trust (isolation setup)

**Date**: 2026-05-14
**Branch**: `bugfix_cycle_2026-05-13`
**Cycle**: 2 (clean slate cycle started 2026-05-13)
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)
**Actor**: Thandi Mathebula (Owner) on firm `:3000`

## Pre-flight

- All 3 core services healthy: frontend `:3000` (200), backend `:8080` (200), gateway `:8443` (200).
- Day 11 closed clean (8/8 PASS, 0 blockers, 0 new gaps).
- Already logged in as Thandi Mathebula on firm `:3000`.

## Phase A — Create Moroka Family Trust client

- Navigated to `/customers` (1 client: Sipho Dlamini) → clicked **+ New Client** → "Create Client" 2-step wizard opened.
- Step 1 fields filled:
  - Name: **Moroka Family Trust** / Type: **Trust**
  - Email: `moroka.portal@example.com` / Phone: `+27 11 555 0102`
  - Address: 45 Helen Joseph St, Johannesburg, Gauteng 2001, ZA
  - Contact Name: Lerato Moroka
  - Registration Number: **IT 001234/2024** / Entity Type: Trust
- Step 2 (Additional Information / SA Legal — Client Details): left optional fields blank, clicked **Create Client**.
- Redirected to `/customers/b7e205be-4e7e-40f1-9d8d-940e6a2e4fee` — client detail page with header "Moroka Family Trust", Active + Prospect badges.
- **Result**: ck 14.1, 14.2, 14.3 PASS.
- **customer_id = `b7e205be-4e7e-40f1-9d8d-940e6a2e4fee`**.

## Phase A.4 — Conflict check

- Clicked **Run Conflict Check** on Moroka detail → loaded `/conflict-check?customerId=b7e205be-...&checkedName=Moroka+Family+Trust`.
- Form pre-filled with name "Moroka Family Trust"; clicked **Run Conflict Check** action.
- Result panel: "**No Conflict** — Checked 'Moroka Family Trust' at 14/05/2026, 00:41:29". History counter advanced from 1 → 2.
- **ck 14.4 PASS** (CLEAR).

## Phase B — Create Moroka matter

- From Moroka client detail → Matters tab → clicked **New Matter** → "New from Template — Select Template" dialog opened.
- Selected **Deceased Estate Administration** (9 tasks).
- Clicked Next → Configure dialog auto-prefilled: name "Moroka Family Trust - Estate" / description auto-generated / customer Moroka.
- Renamed to **Estate Late Peter Moroka**, set reference **EST-2026-002**, expanded description with Master's Office Johannesburg.
- Clicked **Create Matter** → redirected to `/projects/43c3dd6b-4bc8-4504-b775-bd61fd19ed7a`. Status badge **Active**, 9 tasks attached, applied field groups: SA Legal — Matter Details + Project Info.
- **Result**: ck 14.5, 14.6, 14.7 PASS.
- **matter_id = `43c3dd6b-4bc8-4504-b775-bd61fd19ed7a`**.

## Phase C — Seed isolation data on Moroka matter

### 14.8 Info request — Liquidation and Distribution Account Pack

- Matter Requests tab → **New Request** → "Create Information Request" dialog.
- Template combobox: selected **Liquidation and Distribution Account Pack (5 items)**.
- Portal Contact pre-filled "Moroka Family Trust (moroka.portal@example.com)". Set due date to 2026-05-30. Clicked **Send Now**.
- Requests table now shows row: REQ-0002 / Estate Late Peter Moroka / Moroka Family Trust / Sent / 0/5 accepted / May 14, 2026.
- **Result**: ck 14.8 PASS.
- **info_request_id = `d114eae8-7b44-460e-984c-1f3044e30690`**.

### 14.9 Upload internal document

- Matter Documents tab → file input chooser → uploaded `qa_cycle/test-fixtures/letters-of-authority.pdf` (951 B).
- Documents table now shows row: `letters-of-authority.pdf / 951 B / Uploaded / May 14, 2026 / Download`.
- Download button navigated to LocalStack S3 signed URL, confirming upload to `localhost:4566/docteams-dev/org/mathebula-partners/project/43c3dd6b-.../c1e78e13-a3b2-49b3-91f7-bebef1d589c3`.
- **Result**: ck 14.9 PASS.
- **document_storage_key = `c1e78e13-a3b2-49b3-91f7-bebef1d589c3`**.

### 14.10 Record trust deposit R 25,000

- Matter Trust tab quick-action **Record Deposit** → modal opened with locked pickers: Customer = "Moroka Family Trust" (disabled), Matter = "Estate Late Peter Moroka" (disabled).
- Filled: Amount 25000 / Reference DEP/2026/EST-002 / Description "Initial trust deposit -- EST-2026-002 Estate Late Peter Moroka" / Date 2026-05-14.
- Submitted → modal closed → matter Trust balance card updated to **R 25 000,00**. Status "Funds Held".
- Verified at org-level `/trust-accounting/transactions`: 2 rows visible (DEP/2026/001 R50k Sipho, DEP/2026/EST-002 R25k Moroka).
- Row data-testids captured: Sipho `transaction-row-55c094e4-...`, Moroka `transaction-row-d52ff25d-...`.
- **Result**: ck 14.10 PASS.
- **trust_transaction_id = `d52ff25d-a0af-44e4-9651-b10bb781e038`**.

### 14.11 Record IDs for Day 15 probe

- Wrote `qa_cycle/isolation-probe-ids.txt` with all Moroka entity IDs + Sipho reference IDs and the Day 15 probe plan.

## Day 14 checkpoints (scenario summary)

| ID | Description | Result |
|-----|-------------|--------|
| 14.1 | Navigate Clients → New Client | PASS |
| 14.2 | Fill type=TRUST, trust details, primary contact, registration number | PASS (FICA beneficial-owners list optional in current SA Legal client field group; entity type Trust + reg number captured) |
| 14.3 | Submit → client created | PASS |
| 14.4 | Conflict check CLEAR | PASS |
| 14.5 | New Matter from Deceased Estate template | PASS |
| 14.6 | Reference EST-2026-002, name/description filled | PASS |
| 14.7 | Submit → matter created (Active, 9 tasks) | PASS |
| 14.8 | Info request sent — Liquidation and Distribution Account Pack (5 items) | PASS |
| 14.9 | One internal document uploaded to Moroka matter | PASS |
| 14.10 | Trust deposit R 25,000 recorded against Moroka/EST-2026-002 | PASS |
| 14.11 | IDs captured to isolation-probe-ids.txt | PASS |

**Phase summary** (from scenario):
- Two clients + two matters on tenant: Sipho (RAF-2026-001) + Moroka (EST-2026-002) → **PASS**
- Moroka has at least 1 info request, 1 document, 1 trust deposit → **PASS** (REQ-0002 + letters-of-authority.pdf + R 25 000 deposit)
- Moroka entity IDs captured → **PASS** (5 IDs in `qa_cycle/isolation-probe-ids.txt`)

## Console health

- Firm `:3000` console: 0 JS errors from product code during Moroka onboarding.
- Known pre-existing: `/api/assistant/invocations` 404 on customer detail page loads (OBS-203, nit, non-blocking).
- Next.js dev mode warnings: scroll-behavior smooth warning (non-blocking).

## Screenshots

- `qa_cycle/evidence/day-14/day-14-moroka-matter-trust-balance.png` — Moroka matter detail with R 25 000 trust balance
- `qa_cycle/evidence/day-14/day-14-clients-list-both.png` — Clients list showing both Sipho (Onboarding) and Moroka (Prospect)

## New gaps

None. All 11 checkpoints PASS with zero blockers.

## Status

- **Day 14 COMPLETE.** All 11 checkpoints PASS.
- Two clients + two matters seeded; Moroka loaded with info request + doc + R 25 000 deposit.
- IDs captured for Day 15 isolation probes.
- Ready for Day 15 (BLOCKER-severity isolation check).
