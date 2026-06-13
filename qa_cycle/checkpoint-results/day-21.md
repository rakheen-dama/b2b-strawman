# Day 21 — Firm logs time, adds disbursement, creates court date `[FIRM]`

**Cycle**: 22 (bugfix_cycle_2026-06-13)
**Date executed**: 2026-06-13 SAST
**Actor**: Bob Ndlovu (Admin/Associate, firm :3000 Keycloak)
**Tooling**: Playwright MCP exclusively (`mcp__playwright__browser_*`) — clean Chromium, no claude-in-chrome. Keycloak login page rendered and accepted credentials cleanly (Day 14 extension-hijack block did NOT recur). Backend log + dashboard activity trail used as out-of-band evidence.
**Scenario**: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` Day 21 (lines 579–646)
**Matter**: RAF-2026-001 / Dlamini v Road Accident Fund (`08ad56c4-ff5e-49c2-a034-cb5fa04b462c`)

## Verdict

**ALL CHECKPOINTS PASS (Phase A + B + C) + 3/3 summary checkpoints PASS. Zero new gaps. NOT blocked.**
Time entries (non-tariff path, OBS-2101 warning as expected), Sheriff Fees disbursement (UNBILLED), and
Pre-Trial court date all recorded firm-side and reconcile across matter tabs, Court Calendar, and Dashboard.

## Session setup

- Session had expired → Keycloak login at `:8180/realms/docteams` (username-first flow). Entered
  `bob@mathebula-test.local` → password `SecureP@ss2` → landed `/org/mathebula-partners/dashboard`.
  User menu = "Bob Ndlovu / bob@mathebula-test.local". No extension hijack (clean Playwright Chromium).
- Matter route is `/org/{slug}/projects/{id}` (matters are "projects" internally; `/matters/{id}` 404s — my
  initial wrong-route nav, benign).

## Phase A — Time entry (non-tariff free-text path)

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 21.1 | Work group tab (`tab-group-work`) → Tasks (`tab-item-tasks`) | PASS | Work tab opened dropdown (Tasks/Documents/Generated Docs/Staffing — no standalone "Time" item; Time is a read-only Finance summary). Tasks sub-tab → 9 RAF tasks, per-row **Log Time** CTA |
| 21.2 | "Initial RAF claim assessment & instructions" → Log Time; dialog has Duration/Date/Description/Billable, NO tariff dropdown | PASS | Dialog "Log Time" — Duration (h/m spinbuttons), Date (2026-06-13 default), Description (optional), Billable (checked). **No tariff/activity combobox** (OBS-2101 as expected). `day-21-time-entry-1-dialog.png` |
| 21.3 | Duration 2h 30m, Date today, Description "Initial consultation…quantum", Billable=Yes; rate banner | PASS | Fields set. Yellow warning: **"No rate card found for this combination. This time entry will have no billable rate. Set up rates in Project Settings > Rates."** — exactly the OBS-2101-class warning the scenario permits |
| 21.4 | Submit → entry saved (visible in Time summary); billable_value populated OR R0 + warning served | PASS | Time tab: **Total Time 2h 30m / Billable 2h 30m / Entries 1 / Contributors 1 (Bob Ndlovu)**. By Task: "Initial RAF claim assessment & instructions — 2h 30m". Backend `TimeEntryService: Created time entry f1df86cd-… for task 4b1ead9b-…`. R0 billable value (no rate card) — warning served its purpose |
| 21.5 | Second task → log 1h 30m "Drafted particulars…quantum schedule" | PASS | Logged on "File RAF1 claim form + supporting documents" (no task literally named "Draft particulars"; scenario says "e.g." → representative drafting task). Time tab now: **Total 4h / Billable 4h / Entries 2 / Contributors 1**. By Task adds "File RAF1… — 1h 30m". Backend `Created time entry 910f4ff8-… for task 0c73719c-…`. `day-21-time-summary.png` |

## Phase B — Disbursement (Phase 67)

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 21.6 | Finance group tab (`tab-group-finance`) → Disbursements (`tab-item-disbursements`) → + New Disbursement | PASS | Finance dropdown lists `tab-item-disbursements`. Disbursements tab "No disbursements found" + **New Disbursement** button (`create-disbursement-trigger`) |
| 21.7 | Type Sheriff's fee, R 1,250.00, today, "Sheriff service of summons on RAF" | PASS | Dialog "New Disbursement": Matter pre-filled+disabled "Dlamini v Road Accident Fund"; Category=**Sheriff Fees**, Amount=1250 (ZAR excl VAT), VAT Standard (15%), Client=Sipho Dlamini, Incurred Date 2026-06-13, Supplier "Sheriff, Pretoria Central". `day-21-disbursement-dialog.png` |
| 21.8 | Mark as recoverable (client-rebillable) | PASS (by-design — no toggle) | **No `recoverable` field exists** in the dialog OR the `LegalDisbursement` entity. Every disbursement is inherently a client-rebillable cost: created `approval_status=DRAFT`, `billing_status=UNBILLED`, then DRAFT→PENDING_APPROVAL→APPROVED→**BILLED** onto a fee-note invoice line. The scenario's "recoverable" maps to this UNBILLED→BILLED path. Same OBS-2101-class treatment (product structured differently than scenario's literal wording). Noted, not filed. |
| 21.9 | Submit → saved, appears in tab with status UNBILLED | PASS | Row: 2026-06-13 / Sheriff Fees / "Sheriff service of summons on RAF" / Sheriff, Pretoria Central / **R 1 437,50** (R1 250 + 15% VAT) / Approval **Draft** / Billing **Unbilled**. Backend `DisbursementService: Created disbursement 41e2eb54-… on project 08ad56c4-… (SHERIFF_FEES, amount=1250)`. `day-21-disbursement-recorded.png` |

## Phase C — Court date

| # | Checkpoint | Result | Evidence |
|---|---|---|---|
| 21.10 | Schedule tab (`tab-group-schedule`, single-tab group) → + Add Court Date | PASS | Schedule tab (selected directly, no dropdown) → "Court Dates / No court dates found" + **New Court Date** button |
| 21.11 | Court Gauteng Division Pretoria, Date Day 35 (+14d), Type Pre-trial conference, Attorney Bob Ndlovu | PASS | Dialog "Schedule Court Date": Matter=Dlamini v RAF, Type=**Pre-Trial** (closest enum to "Pre-trial conference"), Date **2026-06-27** (Day 35 = today + 14d), Court Name "Gauteng Division, Pretoria". No dedicated "attorney attending" field → captured in Description "Pre-trial conference. Attorney attending: Bob Ndlovu." Reminder default 7d. `day-21-court-date-dialog.png` |
| 21.12 | Submit → court event saved, on firm-side Court Calendar + matter Overview | PASS | **(a) Matter Schedule tab**: 2026-06-27 / Pre-Trial / Gauteng Division, Pretoria / Dlamini v RAF / Sipho Dlamini / **Scheduled** (`day-21-court-date-matter.png`). **(b) Court Calendar page** (`/court-calendar`): "1 court dates, 1 upcoming" — same row (`day-21-court-calendar.png`). **(c) Matter Overview** Upcoming Deadlines: "Court / Gauteng Division, Pretoria / Jun 27" (was "No upcoming deadlines") (`day-21-matter-overview.png`). **(d) Dashboard** "Upcoming Court Dates" widget shows the same (`day-21-dashboard.png`) |

## Day 21 summary checkpoints

- [x] Time entries post (non-tariff path — duration + yellow "No rate card found" warning) — OBS-2101 WONT_FIX confirmed; 2 entries, 4h total
- [x] Disbursement recorded (Sheriff Fees R 1 437,50 incl VAT, UNBILLED) — feeds Day 28 fee note (recoverable-by-design, no separate toggle)
- [x] Court date added (Pre-Trial 2026-06-27), visible on Court Calendar + matter Schedule + matter Overview + Dashboard

## Cross-surface reconciliation (Dashboard)

Dashboard corroborates all 3 phases in one view:
- **Hours This Month 4.0h**; Matter Health: Dlamini v RAF = 4.0h, 0/9 tasks; Team Time: Bob Ndlovu 4h on Dlamini.
- **Upcoming Court Dates**: 2026-06-27 Pre-Trial Dlamini v RAF Gauteng Division, Pretoria.
- **Recent Activity** (newest first): "created a court_date (just now)", "created a disbursement (2 min ago)",
  "created a time_entry (3 min ago)", "created a time_entry (4 min ago)" — all 4 Day 21 actions audit-logged by Bob.

## Console / errors

- Backend log during the Day 21 window: **0 ERROR**, only one pre-existing benign WARN
  (`MaximumAllowableTagsMeterFilter` http.server.requests uri-tag cap — unrelated to Day 21). **No rollback /
  UnexpectedRollback / TransactionSystemException / 500.** All 4 entities persisted with INFO confirmations.
- Frontend console: the only repeated error is `/api/assistant/invocations…PENDING_APPROVAL 404`
  (**OBS-201 exempt** — AI-proxy not wired in KC mode) + Keycloak `favicon.ico` 404 (benign) + my own
  `/matters/…` wrong-route 404 (benign).
- **Cosmetic observation (NOT a gap, flagged for Product)**: on `/dashboard` the Team Time chart
  (`horizontal-bar-chart.tsx`, a recharts `referenceLine`) emits one SVG warning
  `<path> attribute d: Expected moveto path command ('M' or 'm'), " L 2,20 L 2,20 Z"`. Library-internal
  degenerate path (single contributor / reference-line edge case). Dashboard + chart render fully and
  correctly (Bob 4h shown). Pre-existing dashboard behaviour, NOT a Day 21 workflow defect, no functional
  impact → below gap threshold per "confirm a real defect, not a hypothesis" + scope discipline. Recommend
  Product log as LOW cosmetic if it recurs.

## Carry-over exemptions (noted, not re-filed)

- **OBS-2101** — no tariff↔time-entry binding / no tariff dropdown on Log Time dialog. Observed exactly as the
  scenario amendment predicts (free-text duration + "No rate card found" warning). WONT_FIX feature-gap.
- **OBS-201 / OBS-506** — firm-side `/api/assistant/*` 404s (AI-proxy unwired in KC mode). Observed, exempt.

## Created Day 21 (entities)

- Time entry **f1df86cd-ebc9-4c46-9317-846c928d7a91** — 2h 30m, billable, task 4b1ead9b (Initial RAF claim
  assessment & instructions), member Bob (f753c371), date 2026-06-13, no rate card → R0 billable value.
- Time entry **910f4ff8-66e4-41e9-a060-bb21b880b997** — 1h 30m, billable, task 0c73719c (File RAF1 claim form),
  member Bob, date 2026-06-13, no rate card → R0 billable value.
- Disbursement **41e2eb54-1d08-46cf-b53f-6e884436b583** — SHERIFF_FEES, amount 1250 excl VAT (R 1 437,50 incl
  15% VAT), project 08ad56c4, client Sipho, supplier "Sheriff, Pretoria Central", approval DRAFT, billing
  UNBILLED. Feeds Day 28 fee note.
- Court date — Pre-Trial, 2026-06-27, "Gauteng Division, Pretoria", project 08ad56c4, client Sipho, status
  Scheduled, reminder 7d.

## Next

Day 28 — Firm generates first fee note (bulk billing) `[FIRM]`. The Day 28 fee note will pull the 2 NULL-rate
TIME entries (TIME line-type, R0 due to no rate card per OBS-2101) + the UNBILLED Sheriff Fees disbursement.
