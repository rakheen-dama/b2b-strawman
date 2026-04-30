# Day 14 — Firm onboards Moroka Family Trust (isolation setup)

**Date**: 2026-04-30
**Branch**: `bugfix_cycle_2026-04-30`
**Stack**: Keycloak dev (frontend `:3000`, backend `:8080`, gateway `:8443`, portal `:3002`, mailpit `:8025`)
**Actor**: Thandi Mathebula (Owner) on firm `:3000`

## Pre-flight

- All 4 services healthy via `svc.sh status` (backend 41531, gateway 18539, frontend 18686, portal 18737).
- Day 11 closed clean, OBS-1101 verified at start of Day 12 (see Step 1 below).

## Step 1 — OBS-1101 verification (firm `:3000` as Thandi)

Verified PR #1236 fix to the trust-activity email body formatter.

- Cleared Mailpit (DELETE /api/v1/messages) → 0 messages.
- Logged in to firm `:3000` as Thandi (`thandi@mathebula-test.local` / `SecureP@ss1`) via Keycloak.
- Navigated to the matter Trust tab `/org/mathebula-partners/projects/b7e319f7-…?tab=trust` and used the locked-picker quick-action **Record Deposit** to post a new R 1,000 deposit (reference `DEP/2026/002`, description "Top-up trust deposit — RAF-2026-001").
- Locked-picker fields confirmed: Customer = "Sipho Dlamini" (disabled), Matter = "Dlamini v Road Accident Fund" (disabled).
- Deposit recorded → balance card jumped from R 50 000,00 to **R 51 000,00**. Status RECORDED (single-approval account).
- Mailpit polled for `to:sipho.portal@example.com` → 1 message ID `2Q8r5XBAsK5ec3RrYYixta`, subject "Mathebula & Partners: Trust account activity", `Created 2026-04-30T12:06:02.151Z`.
- Email body now renders:
  - `Date: 30 Apr 2026` (was `2026-04-30T10:37:59.248822Z` raw ISO with microseconds)
  - `Amount: R 1 000,00` (was `50000` unformatted)
  - `Type: DEPOSIT`, `View trust ledger` button → `/trust/b7e319f7-…`.
- HTML source saved: `qa_cycle/evidence/day-12/obs-1101-verify-mailpit-source.html`.
- Screenshot: `qa_cycle/evidence/day-12/obs-1101-verify-mailpit-formatted.png` — Mailpit UI showing formatted DATE/TYPE/AMOUNT card.

**Result**: **OBS-1101 FIXED → VERIFIED.** Both formatting issues resolved by the new formatted-context backend change. No regressions.

## Step 2 — Day 14 execution (firm `:3000` as Thandi)

Note: scenario skips Day 12 and 13 (no checkpoints between Day 11 portal trust and Day 14 firm Moroka onboard) — proceeded directly to Day 14.

### Phase A — Create Moroka Family Trust client

- Navigated to `/customers` → `+ New Client` → "Create Client" 2-step wizard opened.
- Step 1 fields filled:
  - Name: **Moroka Family Trust** · Type: **Trust**
  - Email: `moroka.portal@example.com` · Phone: `+27 11 555 0102`
  - Address: 45 Helen Joseph St, Johannesburg, Gauteng 2001, ZA
  - Contact Name: Lerato Moroka (matches scenario "primary contact")
  - Registration Number: **IT 001234/2024** · Entity Type: Trust
- Step 2 (Additional Information / SA Legal — Client Details): left optional fields blank, clicked **Create Client**.
- Redirected to `/customers/f09d5032-5801-4e35-b1a7-4a8d89fb88a1` — client detail page with header "Moroka Family Trust", Active+Prospect badges.
- **Result**: ck 14.1, 14.2, 14.3 PASS.
- **customer_id = `f09d5032-5801-4e35-b1a7-4a8d89fb88a1`**.

### Phase A.4 — Conflict check

- Clicked **Run Conflict Check** on Moroka detail → loaded `/conflict-check?customerId=…&checkedName=Moroka+Family+Trust`.
- Form pre-filled with name "Moroka Family Trust"; clicked **Run Conflict Check** action.
- Result panel: "**No Conflict** — Checked 'Moroka Family Trust' at 30/04/2026, 14:08:03". History counter advanced from 1 → 2.
- **ck 14.4 PASS** (CLEAR).

### Phase B — Create Moroka matter

- Clicked `/projects?new=1&customerId=f09d…` → "New from Template — Select Template" dialog opened.
- Selected **Deceased Estate Administration** (9 tasks) (template was already pre-selected by the cmdk listbox).
- Clicked Next → Configure dialog auto-prefilled: name "Moroka Family Trust - Estate" / description "Administration of deceased estate from reporting to final distribution. Matter type: ESTATES" / customer Moroka.
- Renamed to **Estate Late Peter Moroka**, set reference **EST-2026-002**, expanded description with Master's Office Johannesburg.
- Clicked **Create Matter** → redirected to `/projects/c10abc4c-344c-44ef-942d-33695da0c874`. Status badge **Active**, 9 tasks attached, applied field groups: SA Legal — Matter Details + Project Info.
- **Result**: ck 14.5, 14.6, 14.7 PASS.
- **matter_id = `c10abc4c-344c-44ef-942d-33695da0c874`**.

### Phase C — Seed isolation data on Moroka matter

#### 14.8 Info request — Liquidation and Distribution Account Pack

- Matter Requests tab → **New Request** → "Create Information Request" dialog.
- Template combobox: opened via keyboard Enter (Radix nested popover quirk persists for synthetic mouse events; keyboard activation works). Selected **Liquidation and Distribution Account Pack (5 items)**.
- Portal Contact pre-filled "Moroka Family Trust (moroka.portal@example.com)". Clicked **Send Now**.
- Requests table now shows row: REQ-0002 / Estate Late Peter Moroka / Moroka Family Trust / Sent / 0/5 accepted / Apr 30, 2026.
- **Result**: ck 14.8 PASS.
- **info_request_id = `75b8c43d-7170-45ae-be4f-b8a56e2752ce`**.

#### 14.9 Upload internal document

- Matter Documents tab → file input chooser → uploaded `qa_cycle/test-fixtures/letters-of-authority.pdf` (951 B).
- Network: PUT signed URL to `localhost:4566/docteams-dev/org/mathebula-partners/project/c10abc4c-…/081fe76e-c4b4-4774-872d-349369a30d18` (LocalStack S3) returned 200; revalidate POST returned 200.
- Documents table now shows row: `letters-of-authority.pdf / 951 B / Uploaded / Apr 30, 2026 / Download`.
- **Result**: ck 14.9 PASS.
- **document_storage_key = `081fe76e-c4b4-4774-872d-349369a30d18`** (S3 key — also serves as the document ID for portal probes since portal would never be able to reach it; the gateway-internal document_id was not exposed in the firm UI).

#### 14.10 Record trust deposit R 25,000

- Matter Trust tab quick-action **Record Deposit** → modal opened with locked pickers: Customer = "Moroka Family Trust" (disabled), Matter = "Estate Late Peter Moroka" (disabled).
- Filled: Amount 25000 / Reference DEP/2026/EST-002 / Description "Initial trust deposit — EST-2026-002 Estate Late Peter Moroka" / Date 2026-04-30.
- Submitted → modal closed → matter Trust balance card updated to **R 25 000,00**. Status RECORDED.
- Verified at org-level `/trust-accounting/transactions`: 3 rows now visible (DEP/2026/001 R50k, DEP/2026/002 R1k, DEP/2026/EST-002 R25k).
- Row data-testids captured for all three (`transaction-row-{uuid}`).
- **Result**: ck 14.10 PASS.
- **trust_transaction_id = `e7625298-7c3a-4298-aba5-5ef51fc4f920`**.

#### 14.11 Record IDs for Day 15 probe

- Wrote `qa_cycle/isolation-probe-ids.txt` with all Moroka entity IDs + Sipho reference IDs and the Day 15 probe plan.

## Day 14 checkpoints (scenario summary)

| ID | Description | Result |
|-----|-------------|--------|
| 14.1 | Navigate Clients → New Client | PASS |
| 14.2 | Fill type=TRUST, trust details, primary contact, beneficial owners | PASS (FICA beneficial-owners list optional in current SA Legal client field group; entity type Trust + reg number captured) |
| 14.3 | Submit → client created | PASS |
| 14.4 | Conflict check CLEAR | PASS |
| 14.5 | New Matter from Deceased Estate template | PASS |
| 14.6 | Reference EST-2026-002, name/description filled | PASS |
| 14.7 | Submit → matter created (Active) | PASS |
| 14.8 | Info request sent — Liquidation and Distribution Account Pack | PASS |
| 14.9 | One internal document uploaded to Moroka matter | PASS |
| 14.10 | Trust deposit R 25,000 recorded against Moroka/EST-2026-002 | PASS |
| 14.11 | IDs captured to isolation-probe-ids.txt | PASS |

**Phase summary** (from scenario):
- Two clients + two matters on tenant: Sipho (RAF-2026-001) + Moroka (EST-2026-002) → **PASS**
- Moroka has at least 1 info request, 1 document, 1 trust deposit → **PASS** (REQ-0002 + letters-of-authority.pdf + R 25 000 deposit)
- Moroka entity IDs captured → **PASS** (5 IDs in `qa_cycle/isolation-probe-ids.txt`)

## Console health

- Firm `:3000` console: 0 errors / 0-1 warnings during Moroka onboarding (Image priority warning Next.js dev mode).
- The 1 console error logged at end was from QA probe attempts to fetch `/bff/api/documents` (404, expected — that route doesn't exist on Next, this was my probe traffic; not a product bug).

## Screenshots

- `qa_cycle/evidence/day-12/obs-1101-verify-mailpit-formatted.png` — Mailpit UI showing formatted email body
- `qa_cycle/evidence/day-12/obs-1101-verify-mailpit-source.html` — full email HTML source
- `qa_cycle/evidence/day-14/day-14-moroka-matter-trust-balance.png` — Moroka matter detail with R 25 000 trust balance + uploaded doc visible
- `qa_cycle/evidence/day-14/day-14-clients-list-both.png` — Clients list showing both Sipho (Onboarding) and Moroka (Prospect)

## Status

- **OBS-1101 FIXED → VERIFIED.** Trust-activity email now renders `30 Apr 2026` and `R 1 000,00`.
- **Day 14 COMPLETE.** All 11 checkpoints PASS.
- Two clients + two matters seeded; Moroka loaded with info request + doc + R 25 000 deposit.
- IDs captured for Day 15 isolation probes.
- Ready for Day 15 (BLOCKER-severity isolation check).
