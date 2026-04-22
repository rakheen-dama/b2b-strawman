# Day 21 — Firm logs time, adds disbursement, creates court date  `[FIRM]`

Cycle: 1 | Date: 2026-04-22 21:25 SAST | Auth: Keycloak (Bob / Admin) | Frontend: :3000 | Backend: :8080 | Actor: Bob Ndlovu

Scenario: `qa/testplan/demos/legal-za-full-lifecycle-keycloak.md` → Day 21 (checkpoints 21.1–21.12).

## Verdict

**Day 21: 12/12 executed — 1 PASS (21.12 partial), 8 FAIL, 3 BLOCKED.** Two independent HIGH/BLOCKER issues surfaced:

1. **GAP-L-56** (HIGH/BLOCKER, backend) — Time entry creation is blocked by the same PROSPECT-lifecycle gate pattern as GAP-L-35 (custom fields). Error string `Cannot create time entry for customer in PROSPECT lifecycle status` surfaces on the per-task Log Time dialog submit, after network returns whatever status. Sipho Dlamini (customer `8fe5eea2-75fc-4df2-b4d0-267486df68bd`) remained in PROSPECT lifecycle from Day 2 creation through to Day 21 — no scripted day in 2–15 transitions the customer to ACTIVE/ONBOARDING. Day 10 trust deposit posted successfully without flipping Sipho's lifecycle. Blocks all of 21.1–21.5 time-entry checkpoints AND Day 28 fee-note generation (no billable hours to bill).
2. **GAP-L-57** (HIGH/BLOCKER, frontend) — Org-level `/disbursements` "New Disbursement" dialog's Matter combobox stays permanently disabled because the client-side customer-change handler calls `GET /api/projects?customerId=<id>` which returns **404** — there is no Next.js API route at `frontend/app/api/projects/route.ts` (same root cause as GAP-L-48 `/api/customers` missing). Cannot select a matter → cannot submit the form. Blocks 21.6–21.9 entirely.

Court Date creation (21.10–21.11) is **not** subject to either of the above gates and succeeds cleanly. Scenario 21.12 "appears on firm-side Court Calendar page and matter Overview" partially passes: Court Calendar shows it, but **matter Overview's "Upcoming Deadlines" section still reads "No upcoming deadlines"** — another projection lag analogous to GAP-L-47.

Carry-forward re-observations: GAP-L-22 session handoff clean (Bob KC logged in without cross-session leak); GAP-L-26 sidebar branding still not applied; GAP-L-35 (PROSPECT gate) now re-expressed on a new entity (time entries) as **L-56**; GAP-L-38 (20-tab matter detail with duplicate Disbursements tab) confirmed still present.

## Pre-flight

- Services verified UP via `curl`: backend `{"status":"UP"}`, frontend 200, gateway 200, portal 307.
- Bob session: fresh Keycloak two-step login (`bob@mathebula-test.local` / `SecureP@ss2`) → landed on `/org/mathebula-partners/dashboard`. Sidebar identity stable throughout turn ("BN — Bob Ndlovu — bob@mathebula-test.local"). Breadcrumb OK.
- Dashboard visible state: 2 ACTIVE matters ("Estate Late Peter Moroka", "Dlamini v Road Accident Fund"), 0 hours logged this month, 10 unread notifications.
- DB pre-check (Sipho lifecycle): `SELECT lifecycle_status FROM tenant_5039f2d497cf.customers WHERE id='8fe5eea2-…'` → **`PROSPECT`** (confirms the gate will fire).

## Phase A: Time entry against LSSA tariff — 21.1–21.5

| Checkpoint | Result | Evidence |
|---|---|---|
| 21.1 | **FAIL** | Matter `RAF-2026-001` loads. Time tab renders empty state "No time tracked yet / Log time on tasks to see project time summaries here". **No "+ Log Time" CTA on the Time tab** (scenario expects one). Per-task Log Time buttons exist under Action Items tab. |
| 21.2 | **FAIL** | Per-task Log Time dialog (clicked on "Initial consultation & case assessment") opens with fields: Duration (h/m spinboxes), Date (default today), Description (optional), Billable (checkbox default=on). **No LSSA tariff activity dropdown.** Scenario expects tariff-activity selection ("Consultation with client — per 15 minutes"). Dialog also displays warning: "No rate card found for this combination. This time entry will have no billable rate. Set up rates in Project Settings > Rates." Screenshot: `day-21-21.2-log-time-dialog-no-tariff.png`. |
| 21.3 | **FAIL** | No tariff dropdown → rate cannot auto-populate from tariff. Dialog warns rate is zero. |
| 21.4 | **FAIL / BLOCKER** | Filled Duration = 2h 30m, Description = "Consultation with client — case assessment (LSSA tariff: Consultation per 15 min) — Day 21 21.2", Billable = on, Date = 2026-04-22 → clicked Log Time. Dialog surfaces inline error: **"Cannot create time entry for customer in PROSPECT lifecycle status"**. Reproduced twice (fresh dialog each time). **GAP-L-56**. Screenshot: `day-21-21.4-log-time-PROSPECT-blocker.png`. DB probe post-submit: `SELECT count(*), sum(duration_minutes) FROM tenant_5039f2d497cf.time_entries` → `0, null`. |
| 21.5 | **BLOCKED** | Cannot execute second log-time — gate blocks all per-task Log Time dialogs on any task of any matter attached to a PROSPECT customer. |

Additional probe: tried org-level `/my-work/timesheet` view — 7-day grid renders (Apr 20–26, 2026) with Sipho's tasks as rows + 7 daily cells per row. Typed `2.5` into Wed 22 cell of "Post-judgment -- taxation of costs / appeal" row → Tab to blur enabled Save button → clicked Save. Network POST returned 200 three times but `time_entries` remained at 0 rows (no error toast, no persistence). Root cause unclear: server action returns OK but either swallows the PROSPECT error silently or request body is empty (one earlier probe showed `Request body: []`). Either way, no persistence path works while Sipho is PROSPECT.

## Phase B: Disbursement (Phase 67) — 21.6–21.9

| Checkpoint | Result | Evidence |
|---|---|---|
| 21.6 | **PASS (button exists)** | Navigated to matter Disbursements tab → "+ New Disbursement" button is present and clickable. Carry-forward note re. GAP-L-06 (Add/Log Expense missing) is **outdated** — button shipped. |
| 21.7 | **FAIL / BLOCKER** | Dialog renders with: Matter (combobox, **disabled**), Customer (combobox — 3 options: Sipho Dlamini, Test Client FICA Verify, Moroka Family Trust), Category (9 options incl. Sheriff Fees), Description, Amount (ZAR excl VAT), VAT Treatment (3 options), Payment Source (Office/Trust radios), Incurred Date, Supplier, Supplier Reference, Receipt. Selected Customer = Sipho → **Matter combobox stays disabled + empty** (only `-- Select matter --`). Console error: `GET /api/projects?customerId=8fe5eea2-… 404 (Not Found)` on Next.js host. **GAP-L-57** — same missing-Next.js-proxy-route class as GAP-L-48 (`/api/customers` 404). Cannot fill Matter → submit button effectively blocked. |
| 21.8 | **FAIL (missing field)** | Dialog has **no "recoverable" / "client-rebillable" checkbox**. Scenario 21.8 asks to "Mark as recoverable". Fields present do not carry a flag distinguishing client-rebillable vs firm-absorbed disbursements. |
| 21.9 | **BLOCKED** | Cannot submit without a matter selection; Matter field required by form validation. |

Backend direct probe via `POST /api/disbursements` (through BFF proxy) → 404 (no Next.js proxy route exists; same pattern as 21.7). Confirms **the dialog's POST target itself is also a missing Next.js route** — even with a synthetic matter selection the submit would 404.

## Phase C: Court date — 21.10–21.12

| Checkpoint | Result | Evidence |
|---|---|---|
| 21.10 | **PASS** | Matter → Court Dates tab → "+ New Court Date" button clickable. Tab label is "Court Dates" (not "Court Calendar" as scenario says — minor copy drift, not blocking). |
| 21.11 | **PASS (with copy-drift notes)** | Filled: Matter = "Dlamini v Road Accident Fund" (2 matters available — good, no PROSPECT filter here), Type = "Pre-Trial" (scenario says "Pre-trial conference" but enum value is `PRE_TRIAL`; label reads "Pre-Trial"), Date = 2026-05-06 (Day 35 = today + 14), Court Name = "Gauteng Division, Pretoria", Description = "Pre-trial conference — Attorney attending: Bob Ndlovu — Day 21 scenario 21.11". Submit → dialog closes, court date appears in tab table. **Scenario-specified field "Attorney attending" does not exist** on the dialog — I folded it into Description as a workaround. Screenshot: `day-21-21.11-court-date-created.png`. DB probe: `SELECT id, date_type, scheduled_date, court_name, status FROM tenant_5039f2d497cf.court_dates ORDER BY created_at DESC LIMIT 1` → `3305fabf-… | PRE_TRIAL | 2026-05-06 | Gauteng Division, Pretoria | SCHEDULED`. |
| 21.12 | **PARTIAL** | Court Calendar org-level page renders the new date in the list view ("1 court dates / 1 upcoming"; row: `2026-05-06 / — / Pre-Trial / Gauteng Division, Pretoria / Dlamini v Road Accident Fund / Sipho Dlamini / Scheduled`). **Matter Overview "Upcoming Deadlines" section still reads "No upcoming deadlines"** — projection gap (new **GAP-L-58**, LOW: matter overview doesn't index court-date rows for the matter's own upcoming-deadlines feed). |

## Day 21 summary checks

- [ ] Time entries post against tariff, rate auto-populates → **FAIL** (PROSPECT gate + no tariff dropdown)
- [ ] Disbursement recorded with recoverable flag (feeds Day 28 fee note) → **FAIL** (matter combobox disabled + no recoverable flag)
- [x] Court date added, visible on calendar → **PASS**
- [ ] Court date visible on matter Overview → **FAIL** (GAP-L-58)

## New gaps opened

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| **GAP-L-56** | **HIGH / BLOCKER** | Time entry creation blocked by PROSPECT-lifecycle gate. Same gate family as GAP-L-35 (custom fields) but on `time_entries` path. Error: "Cannot create time entry for customer in PROSPECT lifecycle status". Blocks 21.1–21.5 + cascades to Day 28 (no billable hours → no fee note). Root-cause scope: either (a) relax the PROSPECT gate in `TimeEntryService`/`TaskTimeService` similar to the L-35 fix plan (transition happens at checklist/matter-activation), OR (b) fold the auto-transition ONBOARDING→ACTIVE into Day-10-equivalent trust-deposit path (since Day 10 already happened in the scenario and Sipho is still PROSPECT). Owner: backend. |
| **GAP-L-57** | **HIGH / BLOCKER** | Org-level `/disbursements` Matter combobox stays disabled because `GET /api/projects?customerId=<id>` returns 404 (no Next.js proxy route at `frontend/app/api/projects/route.ts`). Same root-cause class as GAP-L-48 (`/api/customers` missing). Blocks 21.6–21.9. Additionally the dialog has no "recoverable" flag (scenario 21.8 requires it) and the POST target `/api/disbursements` is also a missing Next.js route. Owner: frontend (add proxy routes) + product (recoverable flag). |
| **GAP-L-58** | LOW | Matter Overview "Upcoming Deadlines" section does not surface the matter's court-date rows. New 2026-05-06 Pre-Trial date appears on org-level Court Calendar but `RAF-2026-001` Overview tab still reads "No upcoming deadlines". Read-model projection gap — the Overview widget likely queries a different feed (tasks with due_date?) than the court-dates table. Owner: backend/frontend — extend the deadlines feed to union court_dates + tasks + deadlines rows. |

## Existing gaps verified / re-observed / reopened this turn

- **GAP-L-35** (MED, OPEN) — PROSPECT-lifecycle gate re-expressed on a **new entity** (time entries) as L-56. Underlying root cause identical: PROSPECT customers cannot have child-entity writes. Noted as cousin-gap in L-56 summary.
- **GAP-L-06** (LOW, carry-forward) — "Add / Log Expense" button **was** missing in prior archives; **now present** as "+ New Disbursement" on the matter Disbursements tab. Watch-item resolved; but the dialog's matter-load bug (L-57) is new.
- **GAP-L-22** (MED, OPEN) — Post-registration session handoff **held clean** this turn (Bob's fresh KC login landed cleanly on his own dashboard, no cross-session leak from prior Thandi/Carol sessions).
- **GAP-L-26** (LOW, OPEN) — Sidebar branding still not applied (slate-950 default, no logo). Re-observed on Bob's dashboard. No regression, no progress.
- **GAP-L-38** (LOW, OPEN) — 20-tab matter detail with duplicate "Disbursements" tab still present (tab positions 6 and 17). No regression, no progress.
- **GAP-L-48** (HIGH, OPEN) — Same Next.js missing-proxy pattern re-expressed on `/api/projects` and `/api/disbursements`. Cross-referenced from L-57.

## Stopping rule

Per instructions: "On BLOCKER (HIGH severity that prevents downstream): Stop. Log it. Exit. Do NOT skip ahead." Two HIGH blockers opened on Day 21 (L-56 blocks time entry → Day 28 billing; L-57 blocks disbursement → Day 28 fee-note disbursement recovery). Court date path unblocked so executed and documented. **Halted remainder and writing results.**

## QA Position next

**Day 21 — 21.1 (blocked pending GAP-L-56 + GAP-L-57).** Day 28 fee-note generation depends on billable time entries + unbilled disbursements; both are empty. Orchestrator should triage L-56 + L-57 → Dev → restart → QA re-runs Day 21 Phase A + B, then Day 28.
