# 668A OBS-before — Diary, court dates, My Work, Timesheet, Notifications (task 668.1)

## Run and environment

**When (2026-11-27):** seed ~06:50Z (one run); capture run twice while the probe was refined (the second run only added the Settings › Notifications SEL-COMPAT read); the committed output is the second run, ~07:20Z. The data did not change between the two capture runs.

**Stack:** the running e2e mock-auth stack, org `e2e-test-org`. Not rebuilt, restarted, reseeded or torn down.
- Ports: frontend :3001, backend :8081, mock IDP :8090.
- All `e2e-*` containers created 2026-11-27T06:28:22Z (fresh seed at that time).
- Frontend image `docteams-e2e-frontend` `sha256:7e3cc7a6f02b`, built **2026-11-27T06:11:22Z** from `worktree-epic-667B/compose` (compose labels), i.e. 667B's after-fix head `a5e0197d5` (06:08:47Z). `git diff --stat a5e0197d5 cf539e61a -- frontend packages compose backend` is **empty**, so the frontend is main **`cf539e61a`**, this worktree's HEAD.
- Backend image `sha256:e7fd89e47741`, built 2026-11-25T16:31:26Z. The only later backend commit on main is `d8aaba16a` (take-on, #1861) — no diary / court-date / my-work / notification code.

**Browser:** Playwright library (Chromium from the main checkout's `frontend/node_modules`), not the MCP. Node 20.11.1 (nvm), DPR 1, reduced motion, TZ Africa/Johannesburg, rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`. Personas log in through `/mock-login` (`#user-select`, "Sign In"): **Alice (owner)**, **Carol (member)**.

**Committed:** 14 contact sheets + 7 crops (21 PNGs), 5 single-line JSON, scripts. Raw tiles in `/tmp/668A-before-raw/` (not committed).

**OBS-after** (no re-seed), from `before/scripts/`, unedited:
`PHASE=after node 01-capture.cjs && PHASE=after STACK="<image + SHA>" node 02-assemble.cjs`
Every diary / timesheet / my-work URL carries an explicit date (`?from=2026-11-23`, `?week=2026-11-23`, …) so the replay loads the same windows on a later day. Interaction URLs that follow "today" (the diary Day tab, My Work "This Week") will move if the replay runs after 29 Nov.

## Data before the seed (`seed.json` › `dataBefore`)

- **Court dates: 0.** Diary 1–30 Nov: **0 events** for Alice, Bob and Carol (`X-Diary-Types: TASK_DUE,MATTER_DUE,COURT_DATE,PRESCRIPTION` for all three).
- Matters (Alice): Khumalo — lease dispute `85aa3f60-…`, Ndlovu — estate transfer `106a65ae-…`, Website Redesign. **Carol and Bob are on no matter** (`/api/matters` → 0 for Carol).
- `/api/my-work/tasks`: Alice 0 assigned / 1 unassigned ("Estate transfer — draft the transfer documents", Ndlovu); Bob 0/0; Carol 0/0.
- Time entries 23–29 Nov: **Alice 1 entry / 240 min (Fri 27 Nov, on that unassigned Ndlovu task)**; Bob 0; Carol 0. (The register's K-88 numbers — Alice 5 / 630 min, Carol 2 / 135 min — come from an earlier seed.)
- Notifications: Alice 21 (21 unread), Bob 15 (15 unread), **Carol 0** — a naturally empty state, so nothing was marked read.
- TASK saved views: 0 (`/api/views?entityType=TASK` → `[]`); the selector shows only the system tab "All".

## Seed (`scripts/00-seed.cjs`; ids in `seed.json`) — all as Alice, no SQL

1. **Tasks** (REST, the call the matter "New task" dialog makes: `createTask` → `POST /api/matters/{id}/tasks`):
   - **1 assigned to Alice:** "Khumalo — prepare the heads of argument for the lease dispute hearing", HIGH, due Mon 30 Nov → `4743c754-dbdf-483b-9c39-6e6c037c6cf5` (the backend sets it IN_PROGRESS). Why: the timesheet builds rows from *assigned* tasks only (`my-work/timesheet/page.tsx:51-70`), so without it there is no cell to type into; it also gives My Tasks (urgency) a row to measure for K-09.
   - **24 unassigned** (12 legal task titles × Khumalo + Ndlovu, MEDIUM, no due date) → Alice's Available Tasks = **25**. Why: with 1 row the "every row unpaged" defect (K-97) is not observable. Ids in `seed.json › ids.unassignedTaskIds`.
   - Alice's 240-min entry stays on the unassigned Ndlovu task, so K-88 is untouched.
2. **Court dates — real UI clicks** on Khumalo — lease dispute › Diary tab (`tab-group-schedule`) › Court Dates (`project-court-dates-tab`), "New Court Date" dialog ×4, court "Gauteng Division, Johannesburg", references 668A-OBS-1..4:
   - 24 Nov Hearing → row menu **Cancel** ("Matter settled — hearing withdrawn by agreement") → `dae1767c-…` **CANCELLED**.
   - 25 Nov Motion → **Record Outcome** ("Heard — order granted as prayed, costs reserved") → `0497719d-…` **HEARD**.
   - 26 Nov 09:30 Trial → **Postpone** to 30 Nov ("Court roll full — postponed by the registrar") → original `bc250ac9-…` **POSTPONED** (date kept) + new `edf293e3-…` **SCHEDULED** 30 Nov, description "Rescheduled from 2026-11-26" (`CourtCalendarService.java:280,288`).
   - 27 Nov 10:00 Pre-Trial → `091cc69f-…` left **SCHEDULED**.

**Other writes:** none. Timesheet keystrokes were typed and never saved; no notification was clicked or marked read; dialogs/menus were Escaped.

## Required answer — does the diary feed return non-SCHEDULED court dates?

**The diary feed returns POSTPONED court dates; it never returns HEARD or CANCELLED ones.**
- Code: `DiaryService.java:65` `OPEN_COURT_STATUSES = List.of("SCHEDULED", "POSTPONED")`, used at `:158` (`findByStatusInAndScheduledDateBetween`).
- Observed (`measurements.json › kinks.diaryFeed`): `GET /api/diary?from=2026-11-01&to=2026-11-30` (what `getDiary({from,to})` sends, `diary-loader.tsx:59`, no `types`) as Alice returns 4 events: COURT_DATE 26 Nov **POSTPONED** `overdue: true`, 27 Nov SCHEDULED, 30 Nov SCHEDULED (the reschedule), TASK_DUE 30 Nov. `courtDatesNotInDiary` = **24 Nov CANCELLED, 25 Nov HEARD**. Week 23–29 Nov: POSTPONED 26 + SCHEDULED 27 only.
- In the UI, the POSTPONED row shows **no status at all** — only an "Overdue" chip, because POSTPONED is not in `DiaryEventResponse.TERMINAL` (`:37-40`) so `isOverdue` is true (F-668A-B-1). Its diary row menu offers "Record outcome · Cancel". So a "Postponed" badge in the diary has one status to show (POSTPONED); "Heard"/"Cancelled" can never reach the diary.
- Carol (no matter): the feed returns 0 events; all five court dates are outside her matters.

## 1. Diary (`sheet-diary-*.png`)

**URL params the page reads** (`diary/page.tsx:41-55`): `view` (`parseDiaryView`, default week), `from` (snapped by `diaryWindow`), `types` (`parseDiaryKindsParam`), `feeEarner`, `matter` (validated in `diary-loader.tsx`). URL writing: `buildUrl` (`diary-page-client.tsx:148`) — order from, view (≠ week), types (≠ all served), feeEarner (≠ role default), matter; in-window view/filter changes use `replaceState` (`:160`), window changes `router.push` (`:174`).

**Observed transitions** (Alice and Carol identical): start `/diary?from=2026-11-23` → Day `?from=2026-11-27&view=day` (today, inside the week) → Month `?from=2026-11-01&view=month` → List `?from=2026-11-01&view=list` (replaceState) → Week `?from=2026-11-23`; Next → `?from=2026-11-30`, Previous → back. Court dates chip off → `…&types=PRESCRIPTION%2CMATTER_DUE%2CTASK_DUE` (rows 4 → 1), on → param dropped. `?from=2026-11-18&view=month` canonicalises to `?from=2026-11-01&view=month`. `?types=COURT_DATE` → only the Court dates chip pressed, 3 rows. `?matter=<Khumalo>` → matter combobox shows "Khumalo — lease dispute". Fee-earner options: All fee earners · Mine · Alice Owner · Bob Admin · Carol Member. Default: Alice "All fee earners", Carol "Mine".

**Views (Alice):** week 26 Nov = 1 event (09:30 Trial, "Overdue" chip, red, 5.27:1), 27 Nov = 1 (10:00 Pre-trial); day 26 Nov = the POSTPONED trial only; month = 4 chips; list = 4 rows (3 COURT_DATE + 1 TASK_DUE). **Carol:** "Nothing in the diary for this week/day/month." in every view.

## 2. Court dates (`sheet-court-dates.png`, `crop-court-dates-1440-light.png`)

`court-date-list-view.tsx` renders only inside the matter's Diary tab (`project-court-dates-tab.tsx`, mounted at `matters/[id]/page.tsx:356`); there is no standalone court-dates page. URL after the tab click: `/matters/<id>?tab=court-dates`.
- Columns Date · Time · Type · Court · Matter · Client · Status · Actions. Raw ISO date (`2026-11-24`, `:179`) and raw time with seconds (`09:30:00`, `:182`) (F-668A-B-3).
- **Status badges today** (`statusBadge`, `:28-44`), light / dark composite contrast: Scheduled raw `bg-blue-100 text-blue-700` #1447e6 on #dbeafe **5.60** / 5.72; Postponed `warning` #bb4d00 on #fef3c6 **4.52** / 6.26; Heard `success` #008236 on #dbfce7 **4.50** / 6.46; Cancelled `neutral` 6.76 / 5.84. Colour + label only, no icon.
- Row menus: Cancelled / Heard rows have no Actions button; Postponed → Edit · Cancel · Record Outcome; Scheduled → Edit · Postpone · Cancel · Record Outcome.
- 1440: table 1 080 / 1 080. **1024: table 814 in a 664 px scroller (+150), Actions out of view; the matter page has 62 px document h-overflow** (F-668A-B-2).
- Carol: "This matter isn't on your list …" with **no h1 in main** (F-668A-B-4).

## 3. My Work (`sheet-my-work-*.png`, `crop-k09-*`, `crop-k96-*`, `crop-k39-*`)

Header row `my-work/page.tsx:172` (`flex items-center justify-between`) holds `MyWorkHeader` (`my-work-header.tsx:23`, h1 + subtitle + `DateRangeSelector`) and the "Timesheet" link. Task lists: `UrgencyTaskList` ("My Tasks", grouped; `urgency-task-list.tsx:108`) and `AvailableTaskList` (`available-task-list.tsx:75`); matter `Badge variant="member"` at `:111` / `:153`. Carol has no tasks → the `hasNoTasks` EmptyState (`page.tsx:199`), no task tables, no saved-view selector.

**K-09** (`kinks.k09`; card = bordered container clientWidth):

| Alice | 1440 light | 1440 dark | 1024 light | 1024 dark |
|---|---|---|---|---|
| My Tasks › Upcoming (1 row): table / card | **1 019 / 668** | 1 019 / 673 | **1 019 / 419** | 1 019 / 424 |
| columns in view | Matter, Title (Matter fully) | Matter, Title | Matter, Title (**only Matter fully**) | same |
| Available Tasks (25 rows): table / card | 668 / 668 (fits) | 673 / 673 | **668 / 419** | 668 / 424 |
| columns in view | all 4 | all 4 | Matter, Title (**only Matter fully**) | same |

Upcoming headers: Matter 182 · Title 490 · Priority 74 · Status 103 · Due Date 101 · Logged 68. Available: Matter 182 · Title 324 · Priority 84 · Due Date 77. Carol: no tables (member K-09 not observable on this data — she is on no matter).

**K-96** (1024, Alice and Carol identical): title block **80 px** wide; "My Work" **2 lines** (h1 72 px tall); subtitle **5 lines**; the **"Timesheet" link spans x 922–1 025, 1 px past the 1 024 viewport**; header row scrollWidth 745 in 712; document h-overflow 1. At 1440: 301 px block, 1 + 1 lines, Timesheet ends 1 408 (32 px inside).

**K-97:** Available Tasks renders **25 rows unpaged** (Alice); page height **2 433 px at 1024** light (2 417 dark), 2 277 at 1440; collapsing the section → 1 482 (1440) / 1 642 (1024 dark). Carol 0 rows (page 1 272 at 1024).

**K-39 (My Work):** every `text-slate-400` element in Today's agenda is **#989faa on #fdfeff = 2.64:1** in light (Alice "/ 8h"; Carol "Nothing in the diary today", "No upcoming deadlines", "Nothing in the diary for the next 14 days", "/ 8h"); dark 6.74.

**Date range control:** presets This Week · This Month · Last 30 Days · This Quarter · Custom (disabled) + Refresh; "This Month" → `?from=2026-11-01&to=2026-11-30`, "This Week" → `?from=2026-11-23&to=2026-11-29`. **TASK saved views:** tab "All" + "Save View" (opens "Configure Filters", Escaped).

## 4. Timesheet (`sheet-timesheet-*.png`, `crop-timesheet-typed-1440-light.png`)

h1 "Timesheet" (`timesheet/page.tsx:75`, 24 px; the other three pages are 30 px).
- **K-88:** Alice's grid shows **1 row** (the seeded assigned task) and **Daily Total "—" on every day, Total "—"**, while `GET /api/my-work/time-entries?from=2026-11-23&to=2026-11-29` (the page's call) returns **1 entry / 240 min on Fri 27 Nov** (an unassigned Ndlovu task). My Work shows "4.0h this week" on the same data. Carol: 0 entries, grid = Daily Total row only, **0 cells to type into**. Cause unchanged: rows = assigned tasks (`page.tsx:51-70`), totals sum only rows (`weekly-time-grid.tsx:145-147`). "Add task…" search finds 0 matches for "Estate" (the entry's task is unassigned → not in `allTasks`).
- **Keyboard entry (Alice, typed, NOT saved):** click Mon → type 2.5 → Tab → Tue type 1 → Tab → Wed ArrowUp ×2 (= 0.5, step 0.25) → Tab → Thu 0.75 → Tab Fri/Sat/Sun → Tab lands on "Add task…". Focus order Hours for day 1…7 (native number inputs; arrows change the value, they do not move between cells). Row total 4.75h, Daily Total 2.5h · 1h · 0.5h · 0.75h, Save enabled (was disabled). Same at 1024 dark.
- **1024: grid 996 px in a 710 px scroller (+286)** — Sat/Sun/Total out of view (F-668A-B-5). 1440 fits.

## 5. Notifications (`sheet-notifications-*.png`, `crop-k31-*`)

h1 "Notifications" (`notifications/page.tsx:25`, raw 30 px). Toggle All / Unread = two plain buttons, no `aria-pressed` (F-668A-B-6). Alice: 10 items (page 1 of 21), 10 "Unread" dots, "Mark all as read", "Load more"; Unread toggle → the same 10 (all unread). **Empty state:** Carol (0 notifications, natural) — "You're all caught up" (`notifications-page-client.tsx:124`), in both All and Unread. Bell popover: Alice "Mark all read" + 10 unread + "View all notifications" 1; Carol "View all notifications" 1, no items; Escape closes.

**K-31:** header bell badge "21" (`notification-bell.tsx:69`) **#ffffff on #00a38f = 3.16:1 in light and dark**, 16 × 16; Carol has no badge.

## 6. Shell and console

- Every capture (64 loads): `main#main-content` = 1, `<main>` = 1. Level-1 headings in main = 1 on every Diary / My Work / Timesheet / Notifications / matter page; **0 only on Carol's "matter isn't on your list" page** (F-668A-B-4). Document h-overflow: 0 except My Work at 1024 (1 px, K-96) and the matter page at 1024 (62 px, F-668A-B-2).
- `console.json`: **0 errors, 0 warnings, 0 page errors, 0 hydration** messages.

## 7. SEL-COMPAT before (`sel-compat.json`; 1440 light, Alice | Carol)

| Selector (spec:line) | Alice | Carol |
|---|---|---|
| h1 "My Work" (day-07-14:57,170; project-time:244) | 1 | 1 |
| heading "By Task" (project-time:145) | 0 on My Work (it lives on the matter Time tab) | 0 |
| link "Timesheet Report" (day-60:483-484) | 0 on My Work (a reports-page link) | 0 |
| h1 "Notifications" (notification-bell:112,138) | 1 | 1 |
| "View all notifications" in the bell popover (notification-bell:86) | 1 | 1 |
| `#main-content` level-1 heading on Diary / My Work / Notifications (sidebar-navigation:38-39) | 1 / 1 / 1 | 1 / 1 / 1 |
| button "Notifications" exact (bell) | 1 | 1 |
| notification-preferences.spec: `/settings/notifications` → `/settings/connections?tab=notifications`; h1 /^Notifications/; tabpanel; "Tasks"/"Matters"/"Billing"/"Time Tracking"; switch "Time Reminders" (checked); `#email-TIME_REMINDER` (unchecked); "Save preferences" | redirect ✓, 1, 1, 1/1/1/1, 1 (true), 1 (false), 1 | — |
| `court-date-list` / rows (day-45:141, day-90:369) | 1 / 5 | 0 (no access) |
| `tab-group-schedule` / `project-court-dates-tab` / `create-court-date-trigger` | 1 / 1 / 1 (+1 on the Diary header) | 0 |
| `diary-range` / `diary-filter-row` / `diary-week-grid` | 1 / 1 / 1 | 1 / 1 / 0 (empty) |
| `diary-event-<id>` / `diary-list-row-<id>` / `diary-row-actions-<id>` (list) | 4 / 4 / 4 | 0 |
| `tasks-panel` / `saved-view-selector` / `extended-widgets` | 1 / 1 / 1 | 0 / 0 / 1 |
| `todays-diary` / `time-progress-today` / `next-deadline` / `time-activity-panel` | 1 / 1 / 1 / 1 | same |
| input "Hours for day N" | 7 | 0 |

`frontend/e2e/` uses no other testids on these surfaces (grep: `court-date-list`, `tab-group-schedule`, `postpone-dialog`, `prescription-tracker-list`, `create-prescription-dialog` only). Every testid per page is in `discover.json`.

## Findings (pre-existing, before)

- **F-668A-B-1** — Diary shows a POSTPONED court date as an ordinary court date with an "Overdue" chip and no "Postponed" indication, next to its rescheduled copy; the row menu still offers Record outcome / Cancel. (668.2's badge covers the label; the overdue flag is backend `DiaryEventResponse.isOverdue` — out of scope, record only.)
- **F-668A-B-2** — Matter page at 1024: court-date list 814 in 664 (+150), Actions out of view; document h-overflow 62 px.
- **F-668A-B-3** — Court-date list prints raw `2026-11-24` and `09:30:00` (seconds).
- **F-668A-B-4** — A member opening a matter she is not on gets a page with no h1 in `#main-content`.
- **F-668A-B-5** — Timesheet grid 996 in 710 at 1024 (+286): Sat, Sun, Total out of view.
- **F-668A-B-6** — Notifications All / Unread toggle buttons expose no pressed/selected state (`aria-pressed` absent).
- Register kinks reproduced: K-09, K-96, K-97 (25 rows), K-39 (2.64), K-31 (3.16), K-88 (1 entry / 240 min invisible).

## What OBS-after must replay

Run `01-capture.cjs` + `02-assemble.cjs` with `PHASE=after`, unedited, on this data — **do not re-seed**. Must stay identical: the 64 page loads (same URLs), diary URL transitions and filter round-trip, court-date row menus, date-range transitions, timesheet keyboard sequence (typed, unsaved), notifications toggle/empty state, bell popover, SEL-COMPAT counts (incl. the notification-preferences selectors). Should change: K-09 (no clipped columns at 1440 or 1024), K-96 (header fits at 1024, Timesheet link inside the viewport), K-97 (Available Tasks paged), K-31 (badge ≥ 4.5:1 light), PageHeader on Diary / My Work / Timesheet / Notifications, court-date status badge with an icon (and "Postponed" on the POSTPONED diary row), K-88 (the Fri 27 Nov 240-min entry gets a row; Daily Total Fri 4h).
