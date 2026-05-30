# Day 60 -- Firm matter closure + generate Statement of Account `[FIRM]`

**Date**: 2026-05-30
**Stack**: Keycloak dev stack (frontend :3000, backend :8080, gateway :8443, KC :8180, Mailpit :8025)
**Executed by**: QA Agent (Cycle 25)
**Scenario**: legal-za-full-lifecycle-keycloak.md (Mathebula & Partners)
**Actors**: Bob Ndlovu (Admin), Thandi Mathebula (Owner)

---

## Phase A: Resolve closure gate prerequisites

### A1: Review REQ-0003 submitted items (clear Info Requests gate)

**Actor**: Bob Ndlovu

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.1 | Navigate to matter RAF-2026-001 > Client > Requests > open REQ-0003 | **PASS** | Navigated to Client > Requests tab. REQ-0003 visible: In Progress, 0/2 accepted. REQ-0001 visible: Completed, 3/3 accepted. |
| 60.2 | REQ-0003 shows 2 items submitted by Sipho on Day 46, both SUBMITTED | **PASS** | REQ-0003 detail: 2 items (Hospital discharge summary + Orthopaedic report), both status SUBMITTED, each with uploaded PDF file and Download button. |
| 60.3 | Accept item 1 (Hospital discharge summary) -> 1/2 accepted | **PASS** | Clicked Accept on Hospital discharge summary. Status transitioned to ACCEPTED. Progress: 1/2 accepted. |
| 60.4 | Accept item 2 (Orthopaedic report) -> 2/2 accepted, envelope COMPLETED | **PASS** | Clicked Accept on Orthopaedic report. Status transitioned to ACCEPTED. Progress: 2/2 accepted. Envelope auto-transitioned to **Completed**. "Completed on May 30, 2026" stamp appeared. |
| 60.5 | Verify REQ-0003 status = COMPLETED | **PASS** | REQ-0003 heading shows "Completed", progress 2/2 accepted. Both REQ-0001 and REQ-0003 now COMPLETED. |

### A2: Complete/cancel open tasks (clear Open Tasks gate)

**Actor**: Bob Ndlovu

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.6 | Navigate to Work > Tasks > view all 9 tasks | **PASS** | Tasks tab shows 9 tasks, all status "Open". Filter buttons: All, Open (pressed), In Progress (pressed), Done, Cancelled. |
| 60.7 | Mark 2 tasks as COMPLETED (with logged time), cancel remaining 7 | **PASS** | "Initial RAF claim assessment & instructions" (2h30m): Open -> In Progress -> Done ("Completed by Bob Ndlovu on May 30, 2026"). "File RAF1 claim form + supporting documents" (1h30m): Open -> In Progress -> Done. Remaining 7 tasks: each set from Open to Cancelled via task detail dialog status combobox. **Note:** Completing tasks spawned auto-generated "Follow-up" tasks (recurring task pattern). Follow-up tasks were also resolved (cancelled or completed) until no open/in-progress tasks remained. |
| 60.8 | Verify Tasks tab shows 0 open tasks remaining | **PASS** | Tasks tab with Open + In Progress filter shows empty state ("No tasks yet") -- zero open tasks remaining. |

### A3: Resolve court date (clear Court Dates gate)

**Actor**: Bob Ndlovu

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.9 | Navigate to Schedule tab > locate Pre-Trial court date Jun 13 2026 | **PASS** | Schedule tab shows: 2026-06-13, Pre-Trial, Gauteng Division Pretoria, Sipho Dlamini, Status: Scheduled. |
| 60.10 | Update court date status via Record Outcome | **PASS** | Clicked Actions > Record Outcome. Entered outcome: "Settlement reached between parties. Matter concluded. Pre-trial no longer required." Clicked Record Outcome. Status transitioned Scheduled -> **Heard**. |
| 60.11 | Court date no longer shows as Scheduled blocking closure | **PASS** | Court date now shows status "Heard" -- no longer blocking closure. Actions column removed (only history visible). |

### A4: Record & approve trust payment (clear Trust Balance gate -- Section 86 dual-approval)

**Actor**: Thandi Mathebula (recorder), Bob Ndlovu (approver)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.12 | Trust payment PAY/2026/001 (R 70,000) recorded, AWAITING_APPROVAL | **PASS** | Thandi recorded payment via Trust Accounting > Transactions > Record Transaction > Record Payment. Client: Sipho Dlamini, Matter: Dlamini v Road Accident Fund, Amount: R 70,000, Reference: PAY/2026/001, Description: "Settlement payout to Sipho Dlamini -- RAF-2026-001 concluded", Date: 2026-05-30. Status: AWAITING APPROVAL. |
| 60.13 | Bob approves the payment (dual-approval: Bob != Thandi) | **PASS** | Switched to Bob (browser close + fresh KC login). Navigated to Trust Accounting > Transactions > Awaiting Approval filter. PAY/2026/001 visible with Approve/Reject buttons. Clicked Approve. Section 86 dual-approval satisfied: Thandi recorded, Bob approved. |
| 60.14 | Payment status APPROVED, trust balance R 0.00 | **PASS** | Payment status transitioned AWAITING_APPROVAL -> **APPROVED**. Reverse button now visible. |
| 60.15 | Client ledger shows Sipho balance R 0.00 | **PASS** | Client Ledgers page: Sipho Dlamini -- Trust Balance: **R 0,00**, Total Deposits: R 70,000, Total Payments: R 70,000. Moroka Family Trust: R 25,000 (unchanged). |

---

## Phase B: Verify all gates green (pre-closure check)

**Actor**: Thandi Mathebula (Owner)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.16 | Click Close Matter on matter header | **PASS** | Switched to Thandi (browser close + fresh KC login). Navigated to matter RAF-2026-001 (status Active). Clicked "Close Matter" button in header card. Closure dialog opened. |
| 60.17 | Gate report: all 9 gates GREEN | **PASS** | Closure dialog Step 1 shows "Matter closure gate report" with 9 items, ALL green checkmarks: (1) Trust balance R0.00, (2) All disbursements approved, (3) All approved disbursements settled, (4) Final bill issued with no unbilled items, (5) No court dates scheduled for today or later, (6) No prescription timers still running, (7) All tasks resolved, (8) All client information requests closed, (9) No document acceptances pending. Screenshot: `day-60-closure-gates-all-green.png`. |

---

## Phase C: Run matter closure workflow

**Actor**: Thandi Mathebula (Owner)

| ID | Checkpoint | Result | Evidence |
|----|-----------|--------|----------|
| 60.18 | Click Continue -> Step 2 Close form | **PASS** | Clicked Continue. Step 2 rendered with Reason dropdown, Notes field, and two checkboxes. |
| 60.19 | Reason: CONCLUDED | **PASS** | Reason combobox defaulted to "Concluded" (settlement reached). |
| 60.20 | Generate closure letter checked + Generate Statement of Account checked | **PASS** | Both checkboxes checked by default: "Generate closure letter -- A PDF closure letter will be attached to this matter." and "Generate Statement of Account -- A PDF Statement of Account (Section 86 ledger reconciliation) will be attached to this matter." |
| 60.21 | Click Confirm Close -> matter status = CLOSED | **PASS** | Added notes: "Settlement reached with RAF. Matter concluded successfully." Clicked "Close matter". Matter header badge changed from "Active" to **"Closed"**. "Reopen Matter" button replaced lifecycle actions. Screenshot: `day-60-matter-closed.png`. |
| 60.22 | Closure letter + Statement of Account attached to Documents | **PASS** | Work > Documents tab shows 2 new documents: (1) `matter-closure-letter-dlamini-v-road-accident-fund-2026-05-30.pdf` (1.6 KB, Uploaded, May 30 2026), (2) `statement-of-account-dlamini-v-road-accident-fund-2026-05-30.pdf` (5.0 KB, Uploaded, May 30 2026). Both have Download and AI Review buttons. |
| 60.23 | Retention policy row inserted with end_date = today + 5 years | **PARTIAL** | Overview tab shows "Retention period" section: "Retention clock started on **30 May 2026**. Your firm's matter-retention period isn't configured yet, so the scheduled deletion date can't be computed." with link to "Configure retention period". **Retention clock started correctly but end_date cannot be computed because firm hasn't configured the retention period duration in Settings > Data Protection.** Not a code bug -- the feature works; the configuration is missing for this tenant. |

---

## Day 60 Summary Checkpoints

| Checkpoint | Result | Evidence |
|-----------|--------|----------|
| All 4 closure gates resolved before closure attempt | **PASS** | Trust: R 0,00 (dual-approval payment PAY/2026/001 approved by Bob). Court: Heard (outcome recorded). Tasks: 2 completed + 7 cancelled + follow-ups resolved = 0 open. Info requests: REQ-0001 + REQ-0003 both COMPLETED. |
| Matter closes cleanly after gate resolution (no override needed) | **PASS** | All 9 gates GREEN. No override or force-close required. Clean-path closure with reason "Concluded". |
| Statement of Account PDF generated and attached to matter Documents | **PASS** | `statement-of-account-dlamini-v-road-accident-fund-2026-05-30.pdf` (5.0 KB) attached to Work > Documents, dated May 30 2026. |
| Mailpit -> notification email to sipho.portal@example.com | **PARTIAL** | Closure letter email sent: "Document ready: matter-closure-letter-...pdf from Mathebula & Partners" with body "Your matter has been closed ... closure letter for your records". Trust payment activity email also sent. **However, no separate Statement of Account notification email was sent.** Only the closure letter document triggered a notification email. Gap: OBS-6001. |

---

## Closure history (from Overview tab)

| Date | Reason | By |
|------|--------|----|
| May 30, 2026 | Concluded | Thandi Mathebula (bd023476-e52b-4516-b520-9d50548cd179) |

---

## Console Errors

| Source | Error | Severity | Notes |
|--------|-------|----------|-------|
| `/api/assistant/invocations` | 404 Not Found (3x) | LOW | Known OBS-201 (WONT_FIX-EXEMPT). AI infra not wired for KC mode. |
| SVG path attribute | `<path> attribute d: Expected moveto path command` | COSMETIC | Dashboard chart SVG rendering. Non-functional. |

**Zero new functional JavaScript errors during Day 60 execution.**

---

## Gaps Filed

| Gap ID | Summary | Severity | Notes |
|--------|---------|----------|-------|
| OBS-6001 | No Statement of Account notification email sent to portal contact | LOW | Closure letter document notification was sent ("Document ready: matter-closure-letter-...pdf") but no separate email for the Statement of Account document. The SoA PDF was generated and attached correctly. The scenario expected "Your Statement of Account is ready" email to sipho.portal@example.com. Only the closure letter triggered a portal document notification. |

---

## Screenshots

- `day-60-closure-gates-all-green.png` -- Closure dialog Step 1 showing all 9 gates GREEN
- `day-60-matter-closed.png` -- Matter header showing "Closed" status with "Reopen Matter" button

---

## Entity IDs (for Day 61)

- Matter: `d80aeac5-d5f4-4690-9291-193f05e3785d` (CLOSED)
- Client: `d74963c8-4527-41b8-bd67-a2ca3ed6a3cf` (Sipho Dlamini)
- Trust payment: PAY/2026/001, R 70,000, APPROVED
- Closure letter: `matter-closure-letter-dlamini-v-road-accident-fund-2026-05-30.pdf` (1.6 KB)
- Statement of Account: `statement-of-account-dlamini-v-road-accident-fund-2026-05-30.pdf` (5.0 KB)
- Retention clock: started 30 May 2026 (end_date unconfigured)
- Closure reason: CONCLUDED
- Mailpit closure letter email ID: `5Y7mSMMAKEbLeLvFUvfvhS`
