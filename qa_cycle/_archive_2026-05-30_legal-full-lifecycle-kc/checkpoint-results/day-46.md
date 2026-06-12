# Day 46 -- Portal: Sipho responds to second info request + trust re-check + isolation spot-check `[PORTAL]`

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, portal :3002, KC :8180, Mailpit :8025)
**Executed by**: QA Agent (Cycle 21)
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Sipho Dlamini (portal contact)
**Context**: Day 45 complete. REQ-0003 sent with 2 items (Hospital discharge summary + Orthopaedic report). Trust balance R 70,000.

---

## Pre-condition: Portal login via magic-link

Extracted magic-link from Mailpit email ID `7vwL3JxVifs3nhB5Kb2xCo` (subject: "Information request REQ-0003 from Mathebula & Partners"). Token URL: `http://localhost:3002/auth/exchange?token=76on9xsJ6JzZpxXLUpjYzqPaHdzE2Gz-KY8cWbp4ncc&orgId=mathebula-partners`. Navigated to magic-link -> token exchanged -> redirected to `/projects`. Portal identity: "Sipho Dlamini". Sidebar: Matters, Trust, Fee Notes, Engagement Letters, Requests. Footer: "Powered by Kazi". Zero Keycloak forms. Zero JS errors.

---

## Checkpoint 46.1: Login via magic-link for second info request

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 46.1 | Login via magic-link for REQ-0003 | **PASS** | Magic-link from Mailpit email (ID `7vwL3JxVifs3nhB5Kb2xCo`) exchanged successfully. Landed on `/projects` with Sipho Dlamini identity. Zero Keycloak forms. |

---

## Checkpoint 46.2: `/home` -> pending info request visible, click into it

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 46.2a | Navigate to `/home` | **PASS** | `/home` rendered: Pending info requests = **1**, Upcoming deadlines = 0, Recent fee notes = INV-0001 (R 1,250.00), Last trust movement = R 20,000.00 (30 May 2026). |
| 46.2b | Click into pending info request -> REQ-0003 detail | **PASS** | Clicked "Pending info requests" link -> `/requests` page. REQ-0003 listed: "Dlamini v Road Accident Fund", SENT, 0/2 submitted. REQ-0001 listed: COMPLETED, 3/3 accepted. Clicked REQ-0003 -> detail page: REQ-0003, "Dlamini v Road Accident Fund", "0/2 submitted, status SENT". 2 items: (1) Hospital discharge summary (required, description: "Hospital discharge summary for injuries sustained in the accident"), (2) Orthopaedic report (required, description: "Orthopaedic specialist report on injuries and prognosis"). Both with Upload file + disabled "Upload and submit" buttons. |

---

## Checkpoint 46.3: Upload 2 test PDFs + submit -> state SUBMITTED

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 46.3a | Upload hospital-discharge-summary.pdf to item 1 | **PASS** | Clicked "Upload file for Hospital discharge summary" -> file chooser opened -> uploaded `hospital-discharge-summary.pdf` (601 bytes). "Upload and submit" button became enabled. |
| 46.3b | Submit item 1 | **PASS** | Clicked "Upload and submit" for Hospital discharge summary. Item transitioned to **SUBMITTED**. Counter: 1/2 submitted. Envelope: IN_PROGRESS. |
| 46.3c | Upload orthopaedic-report.pdf to item 2 | **PASS** | Clicked "Upload file for Orthopaedic report" -> file chooser opened -> uploaded `orthopaedic-report.pdf` (593 bytes). "Upload and submit" button became enabled. |
| 46.3d | Submit item 2 | **PASS** | Clicked "Upload and submit" for Orthopaedic report. Item transitioned to **SUBMITTED**. Counter: **2/2 submitted**. Envelope: **IN_PROGRESS** (awaits firm review, same state machine as Day 4 REQ-0001). |

**State machine confirmed**: SENT -> (per-item submits) -> IN_PROGRESS (2/2 submitted) -> Completed (firm review Day 60).

---

## Checkpoint 46.4: Trust balance = R 70,000

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 46.4 | Navigate to `/trust` -> balance R 70,000 | **PASS** | Trust balance card: **R 70,000.00** (as of 30 May 2026). Matches firm-side Day 45 posting. Note: scenario expected R 71,000 from carry-over cycle; this clean-slate cycle correctly shows R 70,000 (R 50,000 Day 10 + R 20,000 Day 45). |

---

## Checkpoint 46.5: Transaction list shows deposits, ordered descending

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 46.5a | Transaction list shows 2 deposits | **PASS** | 2 rows in "Trust transactions" table. Row 1: 30 May 2026, DEPOSIT, "Top-up per engagement letter", R 20,000.00, running balance R 70,000.00. Row 2: 30 May 2026, DEPOSIT, "Initial trust deposit -- RAF-2026-001", R 50,000.00, running balance R 50,000.00. |
| 46.5b | Ordered descending by running balance (newest first) | **PASS** | R 70,000 row (Day 45 deposit) appears above R 50,000 row (Day 10 deposit). Correct descending order. |
| 46.5c | Amounts and dates correct | **PASS** | All amounts in ZAR (R prefix, South African format with spaces and commas). Both dates show 30 May 2026 (all deposits were recorded on the same calendar day in this single-session QA run). |

**Note on transaction count**: Scenario expected 3 deposits (Day 10 R 50,000 + Day 14 R 1,000 carry-over + Day 45 R 20,000). Clean-slate cycle has 2 deposits (no Day 14 carry-over deposit). This is correct for this cycle.

---

## Checkpoint 46.6: Passive isolation spot-check

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 46.6a | Trust list shows only Sipho's matter | **PASS** | `/trust` shows only matter d80aeac5 (Dlamini v Road Accident Fund). No Moroka matter or EST-2026-002 visible. |
| 46.6b | No Moroka deposit (R 25,000) merged in | **PASS** | Trust balance = R 70,000 (Sipho only). NOT R 95,000 (aggregate with Moroka R 25,000). Transaction table shows exactly 2 deposits, both for Sipho's matter. Zero Moroka references. |

---

## Checkpoint 46.7: `/home` -> pending info requests = 0

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 46.7 | `/home` pending info requests dropped to 0 | **PASS** | Navigated back to `/home`. Pending info requests card: **0**. The medical evidence request is no longer pending from Sipho's perspective (both items submitted, awaiting firm review). |

---

## Console Errors

| Source | Error | Severity | Notes |
|--------|-------|----------|-------|
| (none) | (none) | - | Zero JavaScript errors throughout Day 46 portal session. 1 warning (non-error). |

**Zero new JavaScript errors during Day 46 execution.**

---

## Day 46 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Second info request lifecycle complete | **PASS** | REQ-0003: magic-link login -> detail page (2 items) -> upload hospital-discharge-summary.pdf + orthopaedic-report.pdf -> per-item submit -> 2/2 submitted, IN_PROGRESS. State machine correct. |
| Trust balance update visible on portal (both deposits) | **PASS** | R 70,000.00 (R 50,000 Day 10 + R 20,000 Day 45). Transaction table: 2 deposits, descending order, ZAR currency, correct amounts. |
| Isolation holds -- no Moroka data leak 31 days after explicit check | **PASS** | Trust: R 70,000 (not R 95,000 aggregate). Zero Moroka references on `/trust`, `/home`, `/requests`. Only Sipho's matter and transactions visible. |

---

## Gaps Filed

None. Day 46 passed cleanly with zero new gaps.

---

## Screenshots

- `day-46-portal-trust-two-deposits.png` -- portal `/home` page after submitting both info request items
- `day-46-portal-trust-balance-r70000.png` -- portal `/trust` page showing R 70,000 balance with 2 deposits
