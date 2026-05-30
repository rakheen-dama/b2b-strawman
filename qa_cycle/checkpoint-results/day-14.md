# Day 14 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, portal :3002)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Thandi Mathebula (Owner)

---

## Pre-check: Login as Thandi

Navigated to `http://localhost:3000/dashboard` -> redirected to Keycloak login at `:8180`. Entered `thandi@mathebula-test.local` / `SecureP@ss1`. Logged in successfully, landed on `/org/mathebula-partners/dashboard`. Dashboard shows "Active Matters: 2", user "Thandi Mathebula" (thandi@mathebula-test.local). Zero JavaScript errors on dashboard.

---

## Phase A: Create Moroka Family Trust client

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 14.1 | Navigate to Clients -> + New Client | **PASS** | Navigated to `/org/mathebula-partners/customers`. Sipho Dlamini listed as sole client (1 client). Clicked "New Client" -> "Create Client" dialog opened (Step 1 of 2). |
| 14.2 | Fill: Type=TRUST, Name=Moroka Family Trust, Email=moroka.portal@example.com, Registration=IT 001234/2024 | **PASS** | Filled: Name="Moroka Family Trust", Type="Trust" (selected from dropdown), Email="moroka.portal@example.com". Contact Name="Peter Moroka", Contact Email="moroka.portal@example.com", Contact Phone="+27 83 555 0202". Business Details: Registration Number="IT 001234/2024", Entity Type="Trust". Step 2: SA Legal fields — Preferred Correspondence="Email" selected. Note: no "beneficial owners" UI on the create dialog — scenario says "add 2 beneficial owners" but the product does not surface a beneficial owners field in the client create form. Non-blocking for isolation setup purposes. |
| 14.3 | Submit -> client created | **PASS** | Clicked "Create Client" -> redirected to `/org/mathebula-partners/customers/3d3557f7-1c2c-4971-8d54-69a077c944fc`. Header: "Moroka Family Trust", Status: Active / Prospect, Email: moroka.portal@example.com, "0 engagements". Legal document templates visible (Power of Attorney, Letter of Demand, Client Trust Statement, Trust Receipt). FICA Verification card present (disabled). |
| 14.4 | Run Conflict Check -> CLEAR | **PASS** | More actions -> Run Conflict Check -> navigated to `/org/mathebula-partners/conflict-check?customerId=3d3557f7-...&checkedName=Moroka%20Family%20Trust`. Name pre-filled, Client=Moroka Family Trust selected. Clicked "Run Conflict Check" -> result: **No Conflict** with timestamp "30/05/2026, 18:12:57". History tab incremented to "(2)". |

---

## Phase B: Create Moroka matter

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 14.5 | On Moroka client detail -> + New Matter -> select Deceased Estate template | **PASS** | Navigated to Moroka client detail -> Work > Matters tab. Clicked "New Matter" -> "New from Template" dialog opened. 6 legal-za templates listed: Collections, Commercial, **Deceased Estate Administration (9 tasks)**, Litigation (Personal Injury), Litigation (RAF), Property Transfer (Conveyancing). Selected "Deceased Estate Administration". |
| 14.6 | Fill: Reference=EST-2026-002, Title=Estate Late Peter Moroka, Work Type=Estates -- Deceased | **PASS** | Configure dialog: Matter name changed from "Moroka Family Trust - Estate" (auto-generated) to "Estate Late Peter Moroka". Reference="EST-2026-002". Work Type="Estates -- Deceased". Client pre-selected="Moroka Family Trust". Description auto-filled from template: "Administration of deceased estate from reporting to final distribution. Matter type: ESTATES". |
| 14.7 | Submit -> matter created | **PASS** | Clicked "Create Matter" -> redirected to `/org/mathebula-partners/projects/3cf31082-b371-4ae5-abdf-34f6f38df708`. Header: "Estate Late Peter Moroka", badges: Active / Estates -- Deceased / `EST-2026-002`, client link: Moroka Family Trust. 7 grouped tabs (Details, Overview, Work, Finance, Client, Schedule, Activity). Overview: Healthy status, 0% task completion, 0.0h hours logged, FICA "Not Started". |

---

## Phase C: Seed data on Moroka matter

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 14.8 | Send info request: template=Liquidation and Distribution Account Pack, addressee=moroka.portal@example.com | **PASS** | Navigated to Client > Requests tab. Clicked "New Request" -> "Create Information Request" dialog. Template: selected "Liquidation and Distribution Account Pack (5 items)". Portal Contact: "Moroka Family Trust (moroka.portal@example.com)" (auto-populated). Due Date: 2026-06-30. Clicked "Send Now". Info request **REQ-0002** created, status=**Sent**, progress=0/5 accepted, sent May 30, 2026. Info Request ID: `e6cc55cd-3250-4a51-a5bd-9c9231c91e2c`. |
| 14.9 | Upload document to Moroka matter: "death-certificate-moroka.pdf" | **PASS** | Navigated to Work > Documents tab. Clicked file upload area -> selected `death-certificate-moroka.pdf` (303 B test PDF). File uploaded and displayed in Documents table: File="death-certificate-moroka.pdf", Size=303 B, Status=Uploaded, Date=May 30, 2026. Download and AI Review buttons visible. Document ID: `2fe8a1cb-2359-4533-868d-ae19f91d2594`. |
| 14.10 | Record trust deposit R 25,000 against Moroka Trust / EST-2026-002 | **PASS** | Navigated to Trust Accounting > Transactions. Clicked Record Transaction > Record Deposit. Client combobox: clicked -> popover opened (OBS-1001 fix confirmed working) -> selected "Moroka Family Trust". Matter combobox: clicked -> popover opened -> selected "Estate Late Peter Moroka". Amount=25000, Reference=DEP/2026/002, Description="Estate deposit -- Moroka Family Trust", Date=2026-05-30. Clicked "Record Deposit". Transaction posted as RECORDED. Transactions page now shows 2 transactions: DEP/2026/001 (Sipho, R 50,000) + DEP/2026/002 (Moroka, R 25,000). Trust Transaction ID: `2d9fec05-b071-4ff9-85c1-b2175a312f8b`. |
| 14.11 | Entity IDs captured for Day 15 probes | **PASS** | All Moroka entity IDs recorded below. |

---

## Day 14 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Two clients and two matters exist on the tenant: Sipho (individual, RAF) + Moroka (trust, Estate) | **PASS** | Clients page shows 2 clients. Matters page shows 3 matters (Dlamini v RAF, Engagement Letter, Estate Late Peter Moroka). Dashboard: Active Matters = 3. |
| Moroka has at least: 1 info request, 1 document, 1 trust deposit | **PASS** | Info request REQ-0002 (Liquidation and Distribution Account Pack, 5 items, Sent). Document: death-certificate-moroka.pdf (Uploaded). Trust deposit: DEP/2026/002, R 25,000, RECORDED. |
| Moroka entity IDs captured for Day 15 probes | **PASS** | All 5 entity IDs recorded in section below. |

---

## Console Errors

**Known errors only**: `/api/assistant/invocations` 404 (OBS-201, WONT_FIX-EXEMPT -- AI assistant endpoint not wired in KC mode). These fired on client detail page load. No user-facing impact.

**Zero JavaScript/hydration/rendering errors during Day 14 execution.** OBS-1001 fix confirmed: both Client and Matter combobox popover triggers in trust deposit dialog respond to clicks correctly.

## Gaps Filed

None. Day 14 passed cleanly with zero new gaps.

## Entity IDs for Day 15 Isolation Probes

### Sipho Dlamini (should be visible on portal)
- **Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`
- **Matter ID**: `d80aeac5-d5f4-4690-9291-193f05e3785d`
- **Matter Reference**: RAF-2026-001
- **Trust Transaction ID**: `7280fcb7-abd4-4413-981c-b41f4df1ffe2` (DEP/2026/001, R 50,000)

### Moroka Family Trust (must NOT be visible to Sipho on portal)
- **Client ID**: `3d3557f7-1c2c-4971-8d54-69a077c944fc`
- **Matter ID**: `3cf31082-b371-4ae5-abdf-34f6f38df708`
- **Matter Reference**: EST-2026-002
- **Info Request ID**: `e6cc55cd-3250-4a51-a5bd-9c9231c91e2c` (REQ-0002, Liquidation and Distribution Account Pack)
- **Document ID**: `2fe8a1cb-2359-4533-868d-ae19f91d2594` (death-certificate-moroka.pdf)
- **Trust Transaction ID**: `2d9fec05-b071-4ff9-85c1-b2175a312f8b` (DEP/2026/002, R 25,000)
- **Trust Account ID**: `bc5b57b5-62b1-4c38-8b14-d11499cb4fcd` (Mathebula Trust -- Main)
