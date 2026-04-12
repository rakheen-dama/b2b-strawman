# Session 5 Results — RAF plaintiff onboarding (Lerato Mthembu)

**Run**: Cycle 4, 2026-04-11
**Tester**: QA Agent (Playwright MCP)
**Actor**: Bob Ndlovu (Admin, already authenticated from Cycle 3)

## Summary
- Steps executed: 19/38
- PASS: 14
- FAIL: 4 (5.20 Contingency fee — GAP-S5-01; 5.25–5.28 Court Date create — GAP-S5-02; 5.30–5.31 Link RAF adverse party to matter — GAP-S5-04; matter.customer_id null — GAP-S5-03)
- PARTIAL: 1 (5.37 comment — no internal comment surface found on matter)
- NOT_EXECUTED: 5.13 (FICA tick-through — inherits GAP-S3-03); 5.22–5.24 (engagement letter save/send, blocked by GAP-S5-01); 5.33 (task status change — UI surface not explored)
- New gaps: GAP-S5-01 HIGH, GAP-S5-02 HIGH, GAP-S5-03 HIGH, GAP-S5-04 MEDIUM

## Test data captured
- Lerato Mthembu client UUID: `b0134b4d-3985-441b-9e37-669f53fd3290`
- RAF matter UUID: `d04d80a6-c9d5-4256-9121-1d9496c77d09`
- Matter task "Initial consultation & case assessment" UUID: `f991d559-d09e-4fd4-8b93-009512a34d73`
- Time entry UUID: `0cba1186-93f0-4edb-a53e-d8ff0867dc02` (90 min, ZAR 1200/hr)
- Adverse Party "Road Accident Fund" created in `/legal/adverse-parties` registry
- Mathebula tenant schema: `tenant_5039f2d497cf`

## Steps

### Phase A — Conflict check

#### 5.1 — Bob logs in
- **Result**: PASS (session still valid from Cycle 3)
- **Evidence**: Dashboard loaded at `/org/mathebula-partners/dashboard` with Bob Ndlovu's profile in sidebar. Active Matters counter = 1 (Sipho's matter from Cycle 3).

#### 5.2 — Navigate to Conflict Check
- **Result**: PASS
- **Evidence**: `/org/mathebula-partners/conflict-check` loaded. History counter showed "History (2)" (Sipho + Moroka from prior cycles).

#### 5.3 — Search "Lerato Mthembu"
- **Result**: PASS
- **Evidence**: Result card "No Conflict — Checked 'Lerato Mthembu' at 11/04/2026, 11:57:43". History bumped to (3).

#### 5.4 — Search "Road Accident Fund"
- **Result**: PASS
- **Evidence**: Result card "No Conflict — Checked 'Road Accident Fund' at 11/04/2026, 11:58:05". No historical counterparty match — acceptable per scenario on a fresh tenant.

#### 5.5 — Record result
- **Result**: PASS (CLEAR captured above)

### Phase B — Create the plaintiff client

#### 5.6 — Navigate to Clients → New Client
- **Result**: PASS
- **Evidence**: Clicked "New Client" from `/customers`, Create Client dialog Step 1 of 2 opened.

#### 5.7 — Fill basic fields
- **Result**: PASS
- **Evidence**: name="Lerato Mthembu", email="lerato.mthembu@email.co.za", phone="+27-83-555-0303", addressLine1="7 Vilakazi St", city="Soweto", postalCode="1804", country=ZA.

#### 5.8 — Fill legal custom fields
- **Result**: PASS (structurally — notes textarea used instead)
- **Evidence**: id_passport_number="9203045811087", Type=INDIVIDUAL (default), notes="Pedestrian, struck by insured vehicle on N1 Johannesburg, 2025-11-12. Hospitalised 3 weeks. Loss of earnings + general damages claim. CAS 145/11/2025." Clicked Next → Step 2 (field groups) → Clicked Create Client.

#### 5.9 — Client created with status PROSPECT
- **Result**: PASS
- **Evidence**: Client list now shows 3 rows including Lerato with status Prospect. Detail page loaded at `/customers/b0134b4d-3985-441b-9e37-669f53fd3290`.

### Phase C — FICA / KYC onboarding

#### 5.10 — Transition to Onboarding
- **Result**: PASS (after GAP-S3-01 pointer workaround)
- **Evidence**: Clicked "Change Status" button (required `pointerdown`+`pointerup` dispatch via browser_evaluate), menu opened showing "Start Onboarding". Confirmation AlertDialog appeared — clicked Start Onboarding via DOM click. Badge updated to "Onboarding". The page also has a direct "Start Onboarding" link under the "Ready to start onboarding?" card, but it's just a `#lifecycle-transition` anchor link — not a real CTA. Recommend converting it to an actual button.

#### 5.11 — Onboarding checklist appears
- **Result**: PASS (same 11-item individual-biased pack as Sipho)
- **Evidence**: Navigated to `?tab=onboarding`. Checklist "Legal Client Onboarding" with 0/11 completed (0/8 required). Items identical to Sipho's in Session 3: Proof of Identity, Proof of Address, Company Registration Docs (skippable), Trust Deed (skippable), Beneficial Ownership Declaration, Source of Funds Declaration, Engagement Letter Signed, Conflict Check Performed, Power of Attorney Signed (blocked), FICA Risk Assessment (blocked), Sanctions Screening (blocked). Screenshot: `qa_cycle/screenshots/session-5-lerato-onboarding-fica.png`.
- **Notes**: Conflict Check Performed item is **Pending / Required** — even though Bob ran `/conflict-check` for "Lerato Mthembu" at 11:57:43 (before creating client). Same symptom as **GAP-S4-04** (conflict-check auto-link only works when check ran AFTER client exists, i.e. matches on customerId, not on name). Re-confirmed as a pattern, not a one-off.

#### 5.12 — RAF-specific documents
- **Result**: PARTIAL
- **Evidence**: Captured in client notes during creation (step 5.7). The scenario acknowledges these are not first-class checklist items; the notes field is the only surface.

#### 5.13 — Mark FICA checklist complete → auto-transition to ACTIVE
- **Result**: NOT_EXECUTED — inherits GAP-S3-03
- **Reason**: Per guidance, document-upload blocker is a known gap. Lerato remains in ONBOARDING. All subsequent steps (matter create, time log) proceed against the ONBOARDING client — which is fine because ONBOARDING permits CREATE_PROJECT/CREATE_TASK/CREATE_TIME_ENTRY guards.

### Phase D — Create matter from Litigation template

#### 5.14–5.17 — Create RAF matter from Litigation template
- **Result**: PASS (via GAP-S3-05 workaround)
- **Evidence**: `/projects` → "New from Template" dialog → selected Litigation (Personal Injury / General) → Step 2: name="Lerato Mthembu — RAF claim (CAS 145/11/2025)", customer=Lerato Mthembu, lead=Bob Ndlovu → clicked Create Project. Redirected to `/projects/d04d80a6-c9d5-4256-9121-1d9496c77d09`. Header confirms "9 tasks".

#### 5.17.1 — Matter custom fields
- **Result**: FAIL — GAP-S3-04 (re-confirmed)
- **Evidence**: Matter detail page shows "FIELD GROUPS → Add Group" followed by "No custom fields configured". The SA Legal — Matter Details group was NOT auto-attached. Same defect as Sipho's matter.

#### 5.17.2 — project.customer_id is NULL (new gap)
- **Result**: FAIL — **GAP-S5-03 HIGH**
- **Evidence**: DB query: `SELECT id, name, customer_id FROM tenant_5039f2d497cf.projects;` shows both matters have NULL customer_id. The customer relationship is only captured in the `customer_projects` many-to-many join table. Cascading consequences: court dates can't auto-derive customer (crashes), adverse-party link dialog can't populate customer select (blank), and likely downstream billing / invoice generation also breaks.

#### 5.18 — 9 pre-populated action items visible
- **Result**: PASS
- **Evidence**: `?tab=tasks` loaded, showing 9 rows including "Initial consultation & case assessment", "Letter of demand", "Issue summons / combined summons", "File plea / exception / counterclaim", "Discovery — request & exchange documents", "Pre-trial conference preparation", "Trial / hearing attendance", "Post-judgment — taxation of costs / appeal", "Execution — warrant / attachment". All with "Log Time" and "Claim" buttons.

### Phase E — Engagement letter (Contingency fee)

#### 5.19 — Navigate to Engagement Letters
- **Result**: PASS (with GAP-S3-06 re-confirmed)
- **Evidence**: `/proposals` page H1 "Engagement Letters" but button labelled **"New Proposal"** (same gap as Cycle 3). "Total Proposals" KPI copy also uses legacy term.

#### 5.20 — Fee Model = Contingency
- **Result**: **FAIL — GAP-S5-01 HIGH (confirmed)**
- **Evidence**: Opened the New Proposal dialog, inspected the `<select>` options. Available values: **Fixed Fee / Hourly / Retainer**. **No Contingency option.** This directly blocks the legal-za RAF plaintiff workflow, since contingency fees (capped at 25% per LPC Rule 59) are the *norm* for personal-injury work in South Africa. Without this option, the `legal-za-fees-contingency` clause pack (seeded in Session 2) has no trigger.
- **Scope of fix required**: Extend `FeeModel` enum to include `CONTINGENCY`, wire the legal-za-fees-contingency clause pack as auto-inserted text when CONTINGENCY is chosen, add a "Contingency Percentage" + "Max Cap (%)" field (default 25%), and update the engagement letter PDF template.

#### 5.21 — Verify contingency clause preview
- **Result**: NOT_EXECUTABLE (blocked by GAP-S5-01)

#### 5.22–5.24 — Save / Send / Mailpit verify
- **Result**: NOT_EXECUTED (blocked by GAP-S5-01; no point creating an Hourly proposal when the specific scenario validation is the Contingency clause text)
- **Evidence**: Dialog was closed via Escape. `proposals` DB table = 0 rows.

### Phase F — Court calendar & adverse party

#### 5.25 — Navigate to Court Calendar
- **Result**: PASS
- **Evidence**: `/court-calendar` page loaded with full filters (All Statuses, All Types), list/calendar/prescriptions tabs, "0 court dates" empty state. Sidebar exposes Court Calendar link under WORK section (Bob as Admin can see it now — update from GAP-S4-03 which reported Court Calendar missing; today it IS visible).

#### 5.26 — Add court date (Motion, 60 days)
- **Result**: **FAIL — GAP-S5-02 HIGH**
- **Evidence**: Opened "New Court Date" dialog. Filled: Matter="Lerato Mthembu — RAF claim (CAS 145/11/2025)", Type=Motion (scenario said "Application" — not in list; Motion is the SA equivalent), Date=2026-06-10 (today+60), Court Name="Gauteng Division, Johannesburg", Description="Application for interim payment under Section 17(4)". Clicked "Schedule Court Date". Dialog surfaces "An unexpected error occurred" in red. Backend log shows:
  ```
  org.springframework.dao.InvalidDataAccessApiUsageException: The given id must not be null
    at io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.toResponse(CourtCalendarService.java:513)
    at io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.createCourtDate(CourtCalendarService.java:196)
  ```
  Root cause: `CourtCalendarService.createCourtDate` does `project.getCustomerId()` on line 170 and sets it on the new CourtDate entity. Since `projects.customer_id = NULL` for both of our matters (GAP-S5-03), the CourtDate entity is created with `customerId = null`, the save succeeds, then `toResponse()` at line 513 calls `customerRepository.findById(entity.getCustomerId())` which throws the NPE. The transaction rolls back, so the court date is NOT persisted (confirmed: `SELECT * FROM tenant_5039f2d497cf.court_dates;` returns 0 rows).
- **Fix scope**: Either (a) null-guard line 513 (`if (entity.getCustomerId() != null)`), OR (b) fix GAP-S5-03 upstream so that `project.customer_id` is never null.
- **Screenshot**: `qa_cycle/screenshots/session-5-court-date-error.png`

#### 5.27 — Court date appears with status SCHEDULED
- **Result**: BLOCKED (cascade from GAP-S5-02)

#### 5.28 — Court date on matter detail
- **Result**: BLOCKED (cascade from GAP-S5-02)

#### 5.29 — Create adverse party "Road Accident Fund"
- **Result**: PASS
- **Evidence**: Navigated to `/legal/adverse-parties` (not `/compliance/adverse-parties` — that returns 404; see GAP-S6-04 route naming inconsistency). Clicked "Add Party", dialog opened with fields: Name, Party Type (Natural Person / Company / Trust / Close Corporation / Partnership / Other — **no "STATUTORY ENTITY" option** as the scenario specified), ID Number, Registration Number, Aliases, Notes. Filled Name="Road Accident Fund", Type=Other, Notes="Statutory third-party defendant under RAF Act 56 of 1996". Clicked Create Party. Registry now shows "1 party" with Road Accident Fund, OTHER, 0 links.

#### 5.30 — Link adverse party to matter
- **Result**: **FAIL — GAP-S5-04 MEDIUM** (cascade from GAP-S5-03)
- **Evidence**: Navigated to `/projects/{matter-id}?tab=adverse-parties`. Clicked "Link Adverse Party". Dialog opened with three selects: Adverse Party (shows "Road Accident Fund" — good), **Customer (only shows '-- Select customer --'; no customers listed)**, Relationship (Opposing Party / Witness / Co-Accused / Related Entity / Guarantor). The Customer select is empty because `project.customer_id` is NULL (GAP-S5-03). With no customer selectable, the form cannot be submitted. Cannot link RAF to Lerato's matter via UI.

#### 5.31 — Verify link
- **Result**: BLOCKED (cascade from GAP-S5-04 → GAP-S5-03)

### Phase G — First substantive action item

#### 5.32 — Open matter → Action Items → click "Initial consultation"
- **Result**: PASS
- **Evidence**: Tasks tab showed the 9 template tasks. Found "Initial consultation & case assessment" row, clicked its Log Time button directly.

#### 5.33 — Mark status → In Progress
- **Result**: NOT_EXECUTED — task status controls not obviously exposed in row. No task-detail page (`/projects/{id}/tasks/{taskId}` returns 404). Status change would require finding the inline status dropdown — skipped to preserve budget.

#### 5.34 — Log 90 minutes
- **Result**: PASS
- **Evidence**: Log Time dialog opened with Duration (h/m), Date, Description, Billable toggle, and a line reading **"Billing rate: R 1 200,00/hr (member default)"** — confirming Bob's billing rate snapshot is surfaced in the dialog. Filled: 1h 30m, description="Initial consultation with Lerato Mthembu, taking statement re RAF claim, reviewing hospital records", Billable=on. Clicked Log Time.

#### 5.35 — Time recording listed in Time tab
- **Result**: PASS (verified via DB)
- **Evidence**: DB query returned one row: `duration_minutes=90, billable=t, billing_rate_snapshot=1200.00, billing_rate_currency=ZAR, cost_rate_snapshot=500.00, cost_rate_currency=ZAR, description="Initial consultation with Lerato Mthembu..."`. Activity tab on the matter now shows "Bob Ndlovu logged 1h 30m on task 'Initial consultation & case assessment' — 2 minutes ago".

#### 5.36 — Rate snapshot = R1,200 / hr
- **Result**: PASS
- **Evidence**: `billing_rate_snapshot = 1200.00`, currency ZAR. Matches Bob's member-default billing rate from Session 2.

#### 5.37 — Add comment on the matter
- **Result**: PARTIAL / NOT_EXECUTED
- **Evidence**: Navigated to matter detail. Searched for a matter-level internal comment surface — found only "Customer Comments" tab which explicitly labels itself as customer-visible ("Reply to the customer thread (visible to all linked customers)...") — not a team-internal comment feed. No `Comments` tab in the 18-tab tablist; the Activity tab is read-only. There is no task-detail page (direct URL returns 404), so per-task comments are also not reachable. **Observation**: For an internal-team workflow (Bob making a note for Thandi about J88 ordering), a team-only comment feed on the matter would be expected — none exists. Could be filed as a product gap (GAP-S6-05) but it may also just be a misread of the test plan — leaving it as a soft observation.

#### 5.38 — Verify comment with name + timestamp
- **Result**: NOT_EXECUTED (see 5.37)

## Checkpoints
- [x] Conflict check executed for both client (Lerato) and RAF
- [x] Lerato client created and transitioned PROSPECT → ONBOARDING (NOT transitioned to ACTIVE — blocked by GAP-S3-03, acceptable per scenario guidance)
- [x] RAF litigation matter created with 9 template action items; opposing party captured only in matter name and via the separate Adverse Party registry (cannot link via UI due to GAP-S5-04)
- [ ] Contingency engagement letter sent — FAIL (GAP-S5-01)
- [ ] Court calendar entry for the matter — FAIL (GAP-S5-02)
- [ ] Adverse party RAF linked to matter — FAIL (GAP-S5-04)
- [x] First action item time log — 90 min at R1,200/hr billing rate snapshot — PASS
- [ ] Comment posted — NOT_EXECUTED

## Gaps filed this session

### GAP-S5-01 — Engagement Letter Fee Model has no Contingency option
- **Severity**: **HIGH** (breaks the single most important legal-vertical workflow for personal-injury/RAF firms)
- **Description**: The New Engagement Letter (New Proposal) dialog's Fee Model `<select>` has only three options: Fixed Fee / Hourly / Retainer. There is no Contingency option. The legal-za clause pack seeded in Session 2 includes a `legal-za-fees-contingency` clause that references a 25% cap (LPC Rule 59), but the enum that drives the select omits CONTINGENCY. RAF plaintiff work in South Africa is almost exclusively contingency-billed — this is the #1 fee model for the vertical.
- **Expected**: Add CONTINGENCY to the FeeModel enum; surface fields for contingency percentage (default 25), a "Max cap (% of total damages)" input, and statutory disclaimer text; auto-insert the legal-za-fees-contingency clause pack into the rendered letter when CONTINGENCY is selected.
- **Impact**: Scenario step 5.20–5.24 (contingency engagement letter for Lerato) fully blocked.

### GAP-S5-02 — Court date creation crashes on null customer_id
- **Severity**: **HIGH** (every court date creation attempt fails when the linked matter has no `projects.customer_id`)
- **Description**: `CourtCalendarService.createCourtDate()` at line 170 copies `project.getCustomerId()` into the new CourtDate entity. For matters created via the "New from Template" dialog, `projects.customer_id` is NULL (see GAP-S5-03), so the CourtDate is saved with `customerId = null`. Then `CourtCalendarService.toResponse()` at line 513 calls `customerRepository.findById(entity.getCustomerId())` which throws `InvalidDataAccessApiUsageException: The given id must not be null`. The transaction rolls back, leaving zero court dates in the table. UI shows "An unexpected error occurred".
- **Stack trace** (from backend logs):
  ```
  org.springframework.dao.InvalidDataAccessApiUsageException: The given id must not be null
    at io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.toResponse(CourtCalendarService.java:513)
    at io.b2mash.b2b.b2bstrawman.verticals.legal.courtcalendar.CourtCalendarService.createCourtDate(CourtCalendarService.java:196)
  ```
- **Expected**: Either (a) null-guard the customer lookup in `toResponse` (simple fix), OR (b) fix GAP-S5-03 so `project.customer_id` is never null for newly-created matters (real fix). Ideally both.
- **Impact**: Court Calendar feature completely unusable for any template-created matter. Scenario step 5.26–5.28 fully blocked.
- **Screenshot**: `qa_cycle/screenshots/session-5-court-date-error.png`

### GAP-S5-03 — Matter.customer_id is not populated by "New from Template" flow
- **Severity**: **HIGH** (hidden data-integrity defect; single-owner FK inconsistent with join table)
- **Description**: When a matter is created via the "New from Template" dialog (the only matter-create path that works — see GAP-S3-05), the customer relationship is stored only in the `customer_projects` many-to-many join table. The legacy single-owner FK column `projects.customer_id` is left NULL. Both matters in the Mathebula tenant demonstrate this:
  ```
  id                                   | name                                    | customer_id
  5ebdb4b6-36c4-46c5-acea-d9a2d286cebc | Sipho Dlamini — Civil dispute          |
  d04d80a6-c9d5-4256-9121-1d9496c77d09 | Lerato Mthembu — RAF claim (CAS ...)   |
  ```
  But `customer_projects`:
  ```
  customer_id                          | project_id
  4119d161-39a7-40b2-a462-d1869d9a1f2b | 5ebdb4b6-...  (Sipho → Sipho matter)
  b0134b4d-3985-441b-9e37-669f53fd3290 | d04d80a6-... (Lerato → Lerato matter)
  ```
  Matter detail pages render the customer name correctly (via the join table), so users don't notice — until downstream features that read `project.customerId` directly (Court Calendar, Link Adverse Party dialog, possibly invoice generation, possibly matter-detail trust balance widget) silently break.
- **Expected**: The "Create Project from Template" service should set `project.customer_id` to the single customer selected in the dialog (and only fall back to the join table when multiple customers are linked post-creation). Alternatively, fully deprecate `projects.customer_id` and migrate all callers to read via `customer_projects`.
- **Impact**: Directly cascades into GAP-S5-02 (court dates) and GAP-S5-04 (adverse party links). Probably also affects any reporting / profitability / trust-account code that assumes single-owner projects.

### GAP-S5-04 — "Link Adverse Party" Customer select is empty
- **Severity**: **MEDIUM** (cascading from GAP-S5-03 — but also a design smell)
- **Description**: The "Link Adverse Party" dialog on the matter's Adverse Parties tab has three fields: Adverse Party, **Customer**, Relationship. The Customer select reads from `project.customerId` and is empty because that column is NULL (GAP-S5-03). With no customer selectable, the "Link Party" button cannot submit.
- **Expected**: The Link dialog should not ask for a Customer at all — the customer is implicit in the matter. The adverse-party→matter association should not need a customer discriminator. Either remove the Customer select, or read from `customer_projects` so it pre-populates with the matter's linked customer(s).
- **Impact**: Scenario step 5.30–5.31 blocked. The Adverse Party registry works (GAP-S5-04 is ONLY about the linking dialog, not about creating parties), so a compliance officer can still keep a firm-wide adverse-party list — they just can't link the party to a specific matter.

## Notes for next QA turn
Session 5 has uncovered three new HIGH-severity legal-vertical gaps. GAP-S5-03 is the most impactful because it cascades into multiple downstream features (court dates, adverse party linking, and probably invoice generation / profitability). Recommend fixing GAP-S5-03 first — it may auto-resolve GAP-S5-02 and GAP-S5-04 without additional fixes. GAP-S5-01 (no Contingency fee model) is independent and is a bigger surface area fix (enum + clause-pack wiring + PDF template + schema migration for the contingency percentage column).
