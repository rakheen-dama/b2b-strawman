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
- Bob session: fresh Keycloak two-step login (`bob@mathebula-test.local` / `<REDACTED>`) → landed on `/org/mathebula-partners/dashboard`. Sidebar identity stable throughout turn ("BN — Bob Ndlovu — bob@mathebula-test.local"). Breadcrumb OK.
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
| 21.11 | **PASS (with copy-drift notes)** | Filled: Matter = "Dlamini v Road Accident Fund" (2 matters available — good, no PROSPECT filter here), Type = "Pre-Trial" (scenario says "Pre-trial conference" but enum value is `PRE_TRIAL`; label reads "Pre-Trial"), Date = 2026-05-06 (Day 35 = today + 14), Court Name = "Gauteng Division, Pretoria", Description = "Pre-trial conference — Attorney attending: Bob Ndlovu — Day 21 scenario 21.11". Submit → dialog closes, court date appears in tab table. **Scenario-specified field "Attorney attending" does not exist** on the dialog — I folded it into Description as a workaround. Screenshot: `day-21-21.11-court-date-created.png`. DB probe: `SELECT id, date_type, scheduled_date, court_name, status FROM tenant_5039f2d497cf.court_dates ORDER BY created_at DESC LIMIT 1` → `3305fabf-… \| PRE_TRIAL \| 2026-05-06 \| Gauteng Division, Pretoria \| SCHEDULED`. |
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

---

## Re-verify post-L56/L57 fixes

Cycle: 1 turn 6 | Date: 2026-04-23 00:55 SAST | Auth: Keycloak (Bob / Admin) | Frontend: :3000 | Backend: :8080 (PID 25040 post L-56 rebuild) | Actor: Bob Ndlovu

**Verdict**: **Both HIGH/BLOCKER fixes VERIFIED.** All re-exposed 21.x checkpoints now work. Day 21 final tally (post-fix): **12/12 executed — 6 PASS, 1 PASS (partial copy-drift on 21.11), 4 FAIL (pre-existing non-fix LSSA tariff + no-rate-card), 1 PARTIAL (21.12 Overview deadlines feed, pre-existing GAP-L-58).**

### GAP-L-56 re-verify — Time entry PROSPECT gate

| Checkpoint | Result | Evidence |
|---|---|---|
| 21.1 | **FAIL (unchanged)** | Matter Time tab still shows "No time tracked yet" with no "+ Log Time" CTA on the Time tab itself. Per-task Log Time lives on Action Items tab. Scenario wording drift; not caused by L-56. |
| 21.2 | **FAIL (unchanged)** | Per-task Log Time dialog still has no LSSA tariff activity dropdown. Warning "No rate card found for this combination." still surfaced. Pre-existing LSSA integration gap — tracked as follow-up; not in scope for L-56 fix. |
| 21.3 | **FAIL (unchanged)** | No tariff dropdown → rate cannot auto-populate from LSSA. Workaround = project-level rate override (see Day 28 preamble below). |
| **21.4** | **PASS** | Filled Duration=1h 30m, Description="QA L-56 re-verify: 1h30m consultation on case assessment", Billable=on → Submit. Dialog closed cleanly (no inline error). DB probe: row `8070053c-0224-4193-b41f-e1e34cc04158` inserted at `22:26:35.281938+00`, `task_id=16a292d1-…`, `duration_minutes=90`, `billable=t`. **Previous error "Cannot create time entry for customer in PROSPECT lifecycle status" no longer appears.** Screenshot: `day-21-L56-reverify-time-entry-success.png`. |
| **21.5** | **PASS** | Second entry against different task ("Post-judgment -- taxation of costs / appeal"): Duration=1h 30m, Description="QA L-56 re-verify #2: 1h30m drafting particulars of claim" → Submit → row `f3990567-3356-4bf9-855f-4789557453f8` inserted at `22:27:30`. Both entries persist for the same PROSPECT customer (Sipho `8fe5eea2-…` confirmed still PROSPECT in DB at time of posts). |
| Timesheet-grid path | **PASS** | Navigated to `/my-work/timesheet` (Apr 20–26, 2026 week). Grid reflected L-56 row posted earlier (Wed 22 Apr = 1.5h on Post-judgment task). Added new entries via weekly grid cells: Trial/hearing row Mon=2, Tue=1; Pre-trial conf row Thu=0.75. Save button flipped from disabled → enabled on input → clicked Save → 3 new rows persisted (`034065b3-…` 60min, `c2a3f81a-…` 120min, `5b7b9804-…` 90min). **Previous silent-200-no-persistence path now persists rows.** Total 5 time entries visible in DB. Screenshot: `day-21-L56-reverify-timesheet-saved.png`. |

**Final time-entry state in tenant**: 5 rows (1h / 2h / 1.5h / 1.5h / 1.5h) = **7.5h billable across 3 tasks** on RAF-2026-001. Matter Time tab aggregates now read: Total Time 7h 30m, Billable 7h 30m, Non-billable 0m, Contributors 1, Entries 5 (confirmed via Time tab navigation post-fix).

**GAP-L-56 → VERIFIED.** `CustomerLifecycleGuard.java` CREATE_TIME_ENTRY switch arm now only blocks OFFBOARDED; PROSPECT/ONBOARDING/ACTIVE/DORMANT/OFFBOARDING all write successfully.

### GAP-L-57 re-verify — Disbursement Matter combobox

| Checkpoint | Result | Evidence |
|---|---|---|
| **21.6** | **PASS** | Navigated to matter Disbursements tab (and org-level `/legal/disbursements`) → "+ New Disbursement" CTA present on both. Note: org-level path is `/legal/disbursements` (not `/disbursements` — `/disbursements` 404s, and scenario wording drift). |
| **21.7** | **PASS** | Dialog opens; Matter combobox **now populated with both matters** (`Estate Late Peter Moroka` + `Dlamini v Road Accident Fund` with real IDs). Customer combobox still populates 3 options as before. Selected Matter=`RAF-2026-001` / Customer=`Sipho Dlamini` / Category=`Sheriff Fees` / Amount=`1250` / Description=`QA L-57 re-verify: Sheriff service of summons on RAF` / Supplier=`Sheriff Pretoria` / Incurred Date=`2026-04-22`. Zero 404s in console. Screenshot: `day-21-L57-reverify-dialog-filled.png`. |
| 21.8 | **FAIL (by design — deferred to GAP-L-59)** | Dialog still has no "recoverable" / "client-rebillable" checkbox. Per Product triage 2026-04-22 22:45 SAST, recoverable flag carved out as new **GAP-L-59** (Flyway migration + entity column + DTO + schema + form field; >2hr scope; does NOT block Day 28). Not re-opening L-57 on this. |
| **21.9** | **PASS** | Submit → `POST /api/legal/disbursements` 201 → dialog closed → disbursement list renders the row: `2026-04-22 / Dlamini v Road Accident Fund / Sheriff Fees / QA L-57 re-verify… / Sheriff Pretoria / R 1 250,00 / Draft / Unbilled`. DB probe: `legal_disbursements` row `c9986a8f-e332-4dc4-8da5-f8a99b673bca` inserted `22:30:58.451735+00`, `project_id=40881f2f-…`, `customer_id=8fe5eea2-…`, `amount=1250.00`, `approval_status=DRAFT`, `billing_status=UNBILLED`. Screenshot: `day-21-L57-reverify-disbursement-created.png`. |

**GAP-L-57 → VERIFIED.** `fetchProjects` defensive array handling in `legal/disbursements/actions.ts` now populates Matter combobox with flat-array backend response. No Next.js proxy routes were needed (original QA framing was wrong; Product fix-spec identified the real root cause).

### Carry-forward / re-observed this turn

- **GAP-L-58** re-observed: matter Overview "Upcoming Deadlines" still reads "No upcoming deadlines" despite 2026-05-06 Pre-Trial date existing on org Court Calendar + on matter's own Court Dates tab. No regression, no progress. Still LOW/OPEN.
- **GAP-L-22** session handoff clean (Bob session held across full turn, no cross-user leak).
- **GAP-L-26** sidebar branding still absent.
- LSSA tariff dropdown on Log Time dialog still missing (21.2/21.3 pre-existing FAIL; tracked as follow-up slice — no new gap needed).

### Final checkpoint tally (post-fix)

- **PASS**: 21.4, 21.5, 21.6, 21.7, 21.9, 21.10, 21.11 — **7/12**
- **PARTIAL**: 21.12 (court date visible on org Calendar but still not on matter Overview — GAP-L-58)
- **FAIL (pre-existing, not L-56/L-57 regression)**: 21.1 (no "+ Log Time" CTA on Time tab), 21.2 (no LSSA tariff dropdown), 21.3 (no auto-rate from tariff), 21.8 (no recoverable flag — tracked as GAP-L-59)
- Net delta from pre-fix: 0 PASS → 7 PASS; 8 FAIL → 4 FAIL; 3 BLOCKED → 0 BLOCKED; 1 PARTIAL → 1 PARTIAL. **Two HIGH blockers cleared.**


---

## Day 21 Re-Verify — Cycle 1 — 2026-04-25 SAST

Cycle: 1 (verify) | Date: 2026-04-25 SAST | Auth: Keycloak (Bob admin) | Frontend: :3000 | Backend: :8080 | Actor: Bob Ndlovu

**Scope (per dispatch)**: Verify L-57 (disbursement Matter combobox) end-to-end on RAF matter `e788a51b-3a73-456c-b932-8d5bd27264c2` (Sipho `c3ad51f5-2bda-4a27-b626-7b5c63f37102`); record one Sheriff Fees disbursement; transition DRAFT → APPROVED to feed L-63 (Day 28 fee-note dialog must surface unbilled disbursements). Method: Playwright MCP browser-driven for all UI; read-only `SELECT` for state confirmation.

### Verdict

**Disbursement creation: PASS. Approval transition: BLOCKED — new HIGH gap GAP-L-61 opened.**

L-57 holds end-to-end (Matter combobox hydrates with all 3 matters, RAF auto-pre-selected on the matter-tab path). One disbursement persisted to DB at DRAFT/UNBILLED. **Cannot transition DRAFT → PENDING_APPROVAL via the UI** — there is no "Submit for Approval" button anywhere on the disbursement detail page or list rows. The `DisbursementApprovalPanel` component only renders when `approvalStatus === "PENDING_APPROVAL"`, and no UI affordance exists to flip DRAFT → PENDING_APPROVAL. Backend `POST /api/legal/disbursements/{id}/submit-for-approval` and the API client helper `submitForApproval()` exist but have **zero UI consumers** (`grep -rn submitForApproval frontend/app frontend/components` finds the helper definition only).

**Implication for L-63**: `DisbursementRepository.findUnbilledBillableByCustomerId` filters on `approvalStatus = 'APPROVED' AND billingStatus = 'UNBILLED'`. The Day-21 Sheriff Fees row will NOT appear in the Day-28 fee-note dialog because it is stuck at DRAFT. **L-63 verification on Day 28 will fail without GAP-L-61 being fixed.**

### Pre-flight

- Tab 0 (Bob firm) and Tab 1 (Sipho portal) both alive on entry; Bob session valid (no Keycloak re-login needed; sidebar reads "BN — Bob Ndlovu — bob@mathebula-test.local"; 6 unread notifications).
- DB pre-check: `SELECT id FROM tenant_5039f2d497cf.projects WHERE id='e788a51b-3a73-456c-b932-8d5bd27264c2';` → 1 row (RAF matter exists).
- Matter UUID **correction**: dispatch context referenced `e788a51b-…` truncated; full UUID resolved via `/projects` list = `e788a51b-3a73-456c-b932-8d5bd27264c2` (NOT the `e788a51b-3027-462a-94c5-2cb3c8df9f0e` shown in dispatch — that ID 500s on matter-detail load. The `3a73-…` UUID is correct per `/projects` list and persisted disbursement).

### Phase A — RAF matter Disbursements tab navigation

Navigated `/projects` → clicked "Dlamini v Road Accident Fund" card → matter detail loaded cleanly (Active badge, Sipho client link, RAF-2026-001 ref). Clicked Disbursements tab → empty list with "+ New Disbursement" CTA visible. Snapshot: `day-21-cycle1-disbursements-tab-initial.yml`.

### Phase B — L-57 verify: Add Disbursement dialog

Clicked "+ New Disbursement" → dialog opens. **L-57 VERIFIED (browser-driven)**:
- Matter combobox **hydrated with 3 options** (Estate Late Peter Moroka, L-37 Regression Probe, Dlamini v Road Accident Fund) and `Dlamini v Road Accident Fund [selected]` pre-set automatically because the dialog opened from the matter-tab context. The combobox itself is `[disabled]` — correct UX since matter is locked when entered from a matter context (prevents user from re-pointing the disbursement to a different matter).
- Customer combobox hydrated with 2 client options (Sipho Dlamini, Moroka Family Trust); selected Sipho.
- Category dropdown hydrated with 9 options (Sheriff Fees, Counsel Fees, Search Fees, Deeds Office Fees, Court Fees, Advocate Fees, Expert Witness, Travel, Other); selected Sheriff Fees.
- VAT Treatment auto-flipped from "Standard (15%)" → "Zero-rated pass-through" when Sheriff Fees was selected (correct South African legal-disbursement treatment — sheriff fees are zero-rated pass-throughs, not VATable services).
- Filled: Description="Sheriff service of summons on RAF — Day 21 cycle-1 verify", Amount=1250, Incurred Date=2026-04-25, Supplier="Sheriff Pretoria", Supplier Reference="SHF-RAF-2026-001". Payment Source = Office Account (default radio).

Zero `/api/projects` 404s in console (the original L-57 symptom). Snapshots: `day-21-cycle1-disbursement-create-dialog-open.yml`, `day-21-cycle1-disbursement-create-dialog-filled.yml`. Screenshot: `day-21-cycle1-disbursement-create-dialog-filled.png`.

### Phase C — Submit + verify list

Clicked "Create Disbursement" → dialog closed cleanly → matter-tab Disbursements list refreshes to 1 row: `2026-04-25 / — / Sheriff Fees / Sheriff service of summons on RAF — Day 21 cycle-1 verify / Sheriff Pretoria / R 1 250,00 / Draft / Unbilled / Actions`. Snapshot: `day-21-cycle1-disbursements-list-after-create.yml`.

**Read-only DB confirmation**:
```
SELECT id, project_id, customer_id, category, amount, vat_treatment,
       approval_status, billing_status, supplier_name, supplier_reference,
       incurred_date, payment_source, created_at
  FROM tenant_5039f2d497cf.legal_disbursements
 WHERE id='bb9ee2ac-b1e5-4e2f-bf43-e40a63809530';
```
→ `bb9ee2ac-… | e788a51b-3a73-… | c3ad51f5-… | SHERIFF_FEES | 1250.00 | ZERO_RATED_PASS_THROUGH | DRAFT | UNBILLED | Sheriff Pretoria | SHF-RAF-2026-001 | 2026-04-25 | OFFICE_ACCOUNT | 2026-04-25 11:31:13.790544+00`.

The disbursement is bound to Sipho + RAF, R 1 250,00 ZAR zero-rated, DRAFT/UNBILLED — i.e. **created by browser through the dialog, not by REST**.

### Phase D — Approval transition (BLOCKED → GAP-L-61 opened)

**Goal**: flip DRAFT → APPROVED so L-63 (Day 28 fee-note) can pick up the disbursement.

Walked the row's Actions menu (matter-tab Disbursements list) → only one menuitem renders: **"Edit"**. No "Submit for Approval", no "Approve", no "Submit". Closed menu.

Clicked the row → navigated to `/legal/disbursements/bb9ee2ac-…` detail page. Detail page header: `Sheriff Fees · Draft · Unbilled · Supplier: Sheriff Pretoria · Ref SHF-RAF-2026-001 · Incurred 2026-04-25`. **Action buttons in the header: only "Upload receipt" and "Edit"**. No "Submit for Approval" button. No "Approve" button. Confirmed via `Array.from(document.querySelectorAll('button, a, [role=menuitem]'))` enumeration — full button list at the bottom of detail page = `["Upload receipt", "Edit"]`. Screenshots: `day-21-cycle1-disbursement-detail-draft.png`, `day-21-cycle1-org-disbursements-list.png`.

Clicked Edit → dialog header reads "Update disbursement details before approval." Dialog has only Cancel + "Save changes" — **no approval action embedded**. Closed.

Org-level `/legal/disbursements` list-row also renders no Actions cell content for the row (only sortable column headers). Re-clicking the row navigates to the same detail page (no approval affordance).

**Source confirmation**:
- `frontend/components/legal/disbursement-approval-panel.tsx:55-57` — `if (!canApprove || disbursement.approvalStatus !== "PENDING_APPROVAL") { return null; }` → panel only renders for PENDING_APPROVAL, never for DRAFT.
- `frontend/app/(app)/org/[slug]/legal/disbursements/[id]/detail-client.tsx:58` — `disbursement.approvalStatus === "DRAFT" || disbursement.approvalStatus === "PENDING_APPROVAL"` is the only DRAFT-aware predicate, and it's only used to gate the "Edit" affordance — not to render a "Submit for Approval" CTA.
- `frontend/lib/api/legal-disbursements.ts:167` — `submitForApproval(id)` API client helper exists but has **zero callers** in `frontend/app` or `frontend/components` (verified via `grep -rn submitForApproval frontend --include="*.ts" --include="*.tsx" --exclude-dir=.next --exclude-dir=node_modules` → only the definition line).
- Backend route `POST /api/legal/disbursements/{id}/submit-for-approval` (`DisbursementController.java:85`) and entity `submitForApproval()` (`LegalDisbursement.java:188`) are wired and tested. The gap is purely frontend-UI.

**Approval was NOT performed** (per scenario rules: state mutations through the browser UI only; REST is forbidden as a UI substitute outside of Mailpit). Disbursement remains at DRAFT/UNBILLED in DB.

### Phase E — L-63 impact assessment

`DisbursementRepository.findUnbilledBillableByCustomerId` (lines 40–47) JPQL:
```
WHERE d.customerId = :customerId
  AND d.approvalStatus = 'APPROVED'
  AND d.billingStatus = 'UNBILLED'
```
The new disbursement's `approvalStatus=DRAFT` → it will NOT appear in the Day-28 fee-note dialog's "Unbilled disbursements" section. **L-63 verification on Day 28 is dependent on GAP-L-61 being fixed first.**

### New gap opened

| GAP_ID | Severity | Summary |
|--------|----------|---------|
| **GAP-L-61** | **HIGH (BLOCKER for L-63 / Day 28 fee-note)** | DRAFT disbursement has no UI affordance to transition to PENDING_APPROVAL. `DisbursementApprovalPanel` only renders for `approvalStatus === "PENDING_APPROVAL"`; detail-client.tsx and the matter-tab/org-level list views render no "Submit for Approval" / "Approve" / "Submit" CTAs at all. Backend `submitForApproval()` and API client helper exist but have no UI consumer (zero callers of `submitForApproval` in `frontend/app` or `frontend/components`). Disbursements created via the L-57 dialog land at DRAFT and cannot be moved forward through the UI, so Day-28's `findUnbilledBillableByCustomerId` (which requires `approvalStatus='APPROVED'`) returns empty. **Owner: frontend.** Suggested fix scope: (a) add a "Submit for Approval" button on `disbursement-detail-client.tsx` that's visible when `approvalStatus === "DRAFT" && canEdit`; (b) wire it to a new `submitForApprovalAction` server action calling `submitForApproval(id)`; (c) on success, refresh the panel — `DisbursementApprovalPanel` then renders for admins/owners and the existing approve/reject flow takes over. Estimated <1hr for the UI; no backend changes needed. |

### Verify-focus tally (this turn)

- **L-57 (disbursement Matter combobox)** — **VERIFIED end-to-end** browser-driven. Matter combobox hydrates with all 3 RAF-tenant matters and RAF pre-selects on matter-tab entry. Zero `/api/projects` 404s.
- **L-63 (fee-note dialog surfaces unbilled disbursements)** — **CANNOT VERIFY ON DAY 28** until GAP-L-61 is fixed. The Day-21 evidence row exists in DB but is stuck at DRAFT; L-63's filter requires APPROVED. Halt the Day-28 dispatch until L-61 is fixed and the Day-21 row is approved through the UI.

### Carry-forward / re-observed this turn

- **GAP-L-22** session handoff clean (Bob session held across full turn).
- **GAP-L-26** sidebar branding still absent (slate-950 default, no Mathebula logo on sidebar).
- **GAP-L-37-regression** still observable on RAF matter detail (3 field groups attached: SA Conveyancing — Matter Details, SA Legal — Matter Details, Project Info). PR #1132's V112 fix only applies to *new* matters created post-V112; old matters created pre-V112 are not retroactively cleaned. Not a regression — pre-existing state.
- **GAP-L-58** still LOW/OPEN — not re-tested this turn.
- 2 hydration-mismatch warnings on initial load (radix `aria-controls` ID drift; pre-existing pattern). Zero functional console errors.
- 1 cosmetic 404 on `GET /org/mathebula-partners/portal-contacts:0` (pre-existing).
- VAT auto-flip on Sheriff Fees → Zero-rated pass-through is **correct UX** (not a gap; sheriff fees in SA are zero-rated pass-throughs).

### Evidence chain for Day 28 (when L-61 lands)

- Disbursement ID: `bb9ee2ac-b1e5-4e2f-bf43-e40a63809530`
- RAF matter: `e788a51b-3a73-456c-b932-8d5bd27264c2`
- Customer (Sipho): `c3ad51f5-2bda-4a27-b626-7b5c63f37102`
- Tenant: `tenant_5039f2d497cf` (Mathebula & Partners)
- Amount: R 1 250,00 (zero-rated pass-through)
- Category: SHERIFF_FEES, Supplier: Sheriff Pretoria, Reference: SHF-RAF-2026-001, Incurred: 2026-04-25
- State at end-of-turn: `approval_status=DRAFT`, `billing_status=UNBILLED`

### Stopping rule

Per dispatch instructions: "If you hit a HIGH/blocker, write Tracker row OPEN and HALT." GAP-L-61 is HIGH (blocks Day 28 L-63 verify). Halting Day 21 here. Disbursement create + L-57 verified PASS; approval phase BLOCKED pending L-61 fix.

### Final checkpoint tally (cycle-1 verify, this turn)

- **PASS**: Phase A (matter detail nav), Phase B (L-57 dialog hydration end-to-end browser-driven), Phase C (DB-confirmed persistence), Phase E (L-63 impact analysis).
- **BLOCKED**: Phase D (approval transition) — no UI affordance to flip DRAFT → PENDING_APPROVAL.
- **L-57 verify-focus**: **VERIFIED** browser-driven (was VERIFIED REST-only previously; now browser-confirmed).
- **L-63 verify-focus**: **CANNOT VERIFY** — depends on GAP-L-61.

---

## Day 21 Phase D Re-Verify (after L-61 fix) — Cycle 1 — 2026-04-25 SAST

**Method**: Playwright MCP browser-driven on Tab 0 (Bob KC firm session — preserved from prior turn). Tab 1 (Sipho portal) untouched. Read-only SQL `SELECT` for state confirmation. **Zero REST mutations** — every state change went through the browser UI per HARD rule.

**Context**: PR #1133 (merge SHA `9a7fcad2`) shipped frontend-only "Submit for Approval" CTA. NEEDS_REBUILD=false; frontend HMR picked up the change automatically.

### Checkpoint table

| Checkpoint | Phase | Method | Result | Evidence |
|------------|-------|--------|--------|----------|
| 21.D.1 | Disbursement detail snapshot at DRAFT | browser_snapshot | **PASS** — page loaded, status badges = `Draft / Unbilled`, supplier "Sheriff Pretoria · Ref SHF-RAF-2026-001". | `day-21-cycle1-l61-fixed-detail-draft.yml` + `.png` |
| 21.D.2 | "Submit for Approval" CTA visible in header (gated on `approvalStatus==='DRAFT'`) | DOM enumeration via snapshot | **PASS** — header buttons now `["Submit for Approval", "Upload receipt", "Edit"]`. CTA placed before Upload/Edit per spec. `data-testid="disbursement-submit-for-approval-button"` present. | `day-21-cycle1-l61-fixed-detail-draft.yml` line 119–128 |
| 21.D.3 | Click Submit for Approval | browser_click on `Submit for Approval` button | **PASS** — server action fired, page revalidated, status badge transitioned `Draft → Pending`, "Submit for Approval" CTA disappeared. | `day-21-cycle1-l61-fixed-submitted.yml` + `.png` |
| 21.D.4 | DisbursementApprovalPanel renders for PENDING_APPROVAL | browser_snapshot | **PASS** — "Approval Required" panel rendered with copy "This disbursement is pending approval. Review the amount and supplier details before approving, or reject with a reason." + Approve/Reject buttons. | `day-21-cycle1-l61-fixed-submitted.yml` line 151–161 |
| 21.D.5 | Console clean during submit | browser_console_messages level=error | **PASS** — 122 messages total, **0 errors / 0 warnings**. | `console-2026-04-25T11-35-27-682Z.log` |
| 21.D.6 | Click Approve → confirmation dialog | browser_click on Approve | **PASS** — modal dialog "Approve Disbursement" opened with optional Notes textarea + Cancel/Approve Disbursement buttons. | (intermediate snapshot) |
| 21.D.7 | Confirm approval | browser_click on dialog "Approve Disbursement" confirm | **PASS** — dialog closed, status transitioned `Pending → Approved`, Approval Required panel disappeared. | `day-21-cycle1-l61-fixed-approved.yml` + `.png` |
| 21.D.8 | DB read-only confirmation | docker exec psql `SELECT` on `tenant_5039f2d497cf.legal_disbursements` | **PASS** — `approval_status='APPROVED'`, `billing_status='UNBILLED'`, `approved_at=2026-04-25 11:57:03.055511+00`, `approved_by='5fabd245-0cc3-4f70-8605-3f4e2a369140'` (Bob Ndlovu, `bob@mathebula-test.local` — confirmed via `members` join). | (terminal output) |
| 21.D.9 | L-63 prerequisite satisfied | DB predicate check | **PASS** — disbursement now matches `findUnbilledBillableByCustomerId(c3ad51f5-…)` predicate (`approval_status='APPROVED' AND billing_status='UNBILLED'`). Day 28 fee-note dialog L-63 verify is now unblocked. | n/a — derived from Phase F |

### State transitions captured

```
DRAFT  ── browser-click [Submit for Approval]  ──▶  PENDING_APPROVAL
PENDING_APPROVAL  ── browser-click [Approve] → [Approve Disbursement (confirm)]  ──▶  APPROVED
```

### Console / network sanity

- **Errors**: 0
- **Warnings**: 0
- All pre-existing hydration-mismatch warnings from earlier turn DID NOT recur this turn (page was fully hydrated when actions fired).

### Evidence files added this turn

- `day-21-cycle1-l61-fixed-detail-draft.yml` / `.png` — DRAFT state with new CTA visible
- `day-21-cycle1-l61-fixed-submitted.yml` / `.png` — PENDING_APPROVAL state with approval panel
- `day-21-cycle1-l61-fixed-approved.yml` / `.png` — APPROVED state, panel gone

### Final tally (Phase D re-verify)

- **9/9 PASS, 0 BLOCKED, 0 OPEN**.
- **GAP-L-61** → **VERIFIED** (FIXED → VERIFIED).
- **L-57** holds (no regression on data shape during transitions).
- **L-63 setup** complete — disbursement is APPROVED+UNBILLED, ready for Day 28.
- Method confirmed browser-driven for every state mutation; only DB read was a `SELECT`.

**Next action**: Day 28 fee-note dispatch (L-62 + L-63 verify).
