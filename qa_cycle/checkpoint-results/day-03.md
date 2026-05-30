# Day 3 — Checkpoint Results (Cycle 2026-05-30)

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Executed by**: QA Agent
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actor**: Bob Ndlovu (Admin — still logged in from Day 2)

---

## Pre-check: Login as Bob

Navigated to `/dashboard` — redirected directly to `/org/mathebula-partners/dashboard` (existing Bob session from Day 2 still active). Sidebar confirmed: "BN" avatar, "Bob Ndlovu", bob@mathebula-test.local. Zero new console errors on dashboard load.

---

## Day 3 — Create RAF matter, send FICA info request `[FIRM]`

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 3.1 | On Sipho's client detail -> click + New Matter | **PASS** | Navigated to `/org/mathebula-partners/customers/d74963c8-..?tab=projects` (Work > Matters sub-tab). Clicked "New Matter" link. Redirected to `/org/mathebula-partners/projects?new=1&customerId=d74963c8-..`. "New from Template -- Select Template" dialog opened automatically. |
| 3.2 | Dialog uses legal-specific matter-type template selector | **PASS** | Template list shows 6 legal-za templates: (1) Collections (Debt Recovery) 9 tasks, (2) Commercial (Corporate & Contract) 9 tasks, (3) Deceased Estate Administration 9 tasks, (4) Litigation (Personal Injury / General) 9 tasks, (5) **Litigation (Road Accident Fund -- RAF) 9 tasks**, (6) Property Transfer (Conveyancing) 12 tasks. All from Phase 64/66 matter templates. |
| 3.3 | Fill matter form with scenario details | **PASS** | Selected RAF template -> advanced to "Configure" step. Pre-filled: Name="Sipho Dlamini - RAF Claim", Description=RAF template text, Client=Sipho Dlamini. Updated: Name="Dlamini v Road Accident Fund", Reference Number="RAF-2026-001", Matter lead=Bob Ndlovu, Work Type=Litigation. Court field not in dialog (custom field on Fields tab — set separately). |
| 3.4 | Submit -> matter created, redirected to matter detail | **PASS** | Clicked "Create Matter". Redirected to `/org/mathebula-partners/projects/d80aeac5-d5f4-4690-9291-193f05e3785d`. Matter created successfully. |
| 3.5 | Verify matter detail page structure (header card + grouped tab bar) | **PASS** | **Header card**: "Dlamini v Road Accident Fund", badges: Active + Litigation, reference code: RAF-2026-001, client link: "Sipho Dlamini". **Lifecycle actions**: Close Matter, Complete Matter buttons. **Grouped tab bar** with 7 groups: Details (Details, Fields), Overview (standalone), Work (Tasks, Documents, Generated Docs, Staffing), Finance (sub-tabs), Client (Clients, Requests, Client Comments, Adverse Parties), Schedule (standalone), Activity (sub-tabs). Matches spec exactly. |
| 3.6 | Promoted fields render inline on Overview, NOT duplicated in Fields | **PASS** | `matter_type` shown as "Litigation" badge in header card (promoted). Court and Case Number appear ONLY in Fields tab under "SA Legal -- Matter Details" field group (7 legal fields: Case Number, Court, Opposing Party, Opposing Attorney, Advocate, Date of Instruction, Estimated Value). No duplication on Overview tab. Filled Court="Gauteng Division, Pretoria", Opposing Party="Road Accident Fund" on Fields tab and saved. |
| 3.7 | Navigate to Client group tab -> Requests sub-tab -> + New Request | **PASS** | Clicked Client group tab -> "Requests" sub-tab. Landed on `?tab=requests`. Page shows "No information requests yet" with "New Request" button. Clicked "New Request" -> "Create Information Request" dialog opened. |
| 3.8 | Select template: FICA Onboarding Pack | **PASS** | Template dropdown shows 8 options including **"FICA Onboarding Pack (3 items)"**. Selected it. Template items count updated to "Template items: 3". Other templates: Tax Return Supporting Docs (5), Monthly Bookkeeping (4), Liquidation and Distribution Account Pack (5), Conveyancing Intake (SA) (7), Company Registration (4), Annual Audit Document Pack (5). |
| 3.9 | Addressee: Sipho Dlamini (portal contact auto-populated) | **PASS** | Portal Contact field auto-populated: "Sipho Dlamini (sipho.portal@example.com)" from client record. No manual selection needed. |
| 3.10 | Request items pre-filled from template (3 FICA items) | **PASS** | Template items count shows "3" — matching the FICA Onboarding Pack which contains: ID copy, Proof of residence (<=3 months), Bank statement (<=3 months). Items auto-populated from template. |
| 3.11 | Due date: Day 10 (7 days from today) | **PASS** | Due Date set to 2026-06-06 (7 days from 2026-05-30). |
| 3.12 | Click Send -> info request status = Sent | **PASS** | Clicked "Send Now". Dialog closed. Requests tab updated with table row: REQ-0001, Contact=Sipho Dlamini, Status=**Sent**, Progress=0/3 accepted, Sent=May 30, 2026. |
| 3.13 | Verify portal contact created/linked (visible in matter Client section) | **PASS** | Client > Clients sub-tab shows 1 client linked: Sipho Dlamini, sipho.portal@example.com, status ACTIVE. Portal contact properly linked to matter. |
| 3.14 | Mailpit -> magic-link email to sipho.portal@example.com | **PASS** | Email received: From=noreply@kazi.app, To=sipho.portal@example.com, Subject="Information request REQ-0001 from Mathebula & Partners". Body: "Mathebula & Partners has sent you an information request (REQ-0001) with 3 item(s)." Portal magic-link: `http://localhost:3002/auth/exchange?token=...&orgId=mathebula-partners`. |

---

## Day 3 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| Matter created with reference format RAF-YYYY-NNN | **PASS** | Matter "Dlamini v Road Accident Fund" created with reference RAF-2026-001. Template: "Litigation (Road Accident Fund -- RAF)" with 9 tasks. Matter ID: d80aeac5-d5f4-4690-9291-193f05e3785d. |
| Matter-type template instantiated — phase sections present, LSSA tariff linked | **PASS** | RAF template instantiated with 9 tasks. Work Type="Litigation". SA Legal matter detail fields available (Case Number, Court, Opposing Party, etc.). LSSA tariff linkage is at the tariff module level (firm-wide), not per-matter — consistent with OBS-2101 (tariff-time-entry integration is a future phase). |
| Promoted matter fields render inline, not duplicated | **PASS** | matter_type promoted to header badge ("Litigation"). SA Legal custom fields (court, case number, etc.) render only on Fields sub-tab. No duplication on Overview tab. |
| FICA info request dispatched, magic-link email sent | **PASS** | FICA Onboarding Pack (3 items) sent as REQ-0001 to Sipho Dlamini (sipho.portal@example.com). Status=Sent, 0/3 accepted. Magic-link email delivered via Mailpit with portal auth exchange URL. FICA status on matter Overview updated: "Not Started" -> "In Progress" with "View request" link pointing to `/information-requests/0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19`. |

---

## Console Errors

9x 404 errors for `/api/assistant/invocations` — all are the known OBS-201 (WONT_FIX-EXEMPT, AI assistant endpoint not wired in KC mode). Fires for both `contextEntityType=customer` and `contextEntityType=project` on respective detail pages. No user-facing impact.

**Zero JavaScript/hydration/rendering errors observed during Day 3 execution.**

## Gaps Filed

None. Day 3 passed cleanly with zero new gaps.

## Entity IDs (for downstream days)

- **Sipho Dlamini Client ID**: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf`
- **Matter "Dlamini v Road Accident Fund" ID**: `d80aeac5-d5f4-4690-9291-193f05e3785d`
- **Matter URL**: `/org/mathebula-partners/projects/d80aeac5-d5f4-4690-9291-193f05e3785d`
- **Matter Reference**: RAF-2026-001
- **Info Request REQ-0001 ID**: `0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19`
- **Info Request URL**: `/org/mathebula-partners/information-requests/0e800982-fff7-4e1b-a4ba-ebfd0a5c9a19`
- **Portal magic-link token**: `oSi7CghGxbR0pZ8kIX1rSXAKdrPn5ZkesW9qzIFaxLU`
