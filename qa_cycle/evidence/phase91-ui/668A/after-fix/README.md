# 668A OBS-after-fix — PR #1880 @ `7d0336046` (fix on top of `ee7929515`)

## Verdict: **PASS**

The OBS-after failure **F-668A-A-1 is fixed**. At 1024 the court-date list is now **808 px in the 664 px scroller, with 56 px of document overflow**. Before the slice it was 814 / 62, and OBS-after measured 824 / 72. This matches the OBS-after `px-2` prototype exactly.

The other fix items also pass:
- **F-668A-A-3**: My Work columns.
- The single row-activation path.
- The pager clamp.
- Urgency grouping.

The regression sweep found no regressions. The console stayed clean, with 0 errors, 0 warnings and 0 hydration messages. Every page had 1 `main#main-content` and 1 h1 (one pre-existing exception, B-4, listed below).

## Environment and provenance

**When:** 2026-11-27. The frontend was rebuilt at 07:54Z. The replay ran ~07:55–07:59Z and the measurements ~08:00–08:04Z.

**Worktree:** `wt-668A-obs`, branch `keep/668A-obs-after`. The fix was merged in with `git merge --no-edit 7d0336046` (merge commit `da8f5dfb3`). `git diff 7d0336046 -- frontend` is empty (0 lines).

**Rebuild:** run from the worktree, using the same command as OBS-after: `docker compose -f compose/docker-compose.e2e.yml up -d --no-deps --build frontend`. That is `e2e-rebuild.sh`'s own build/up plus `--no-deps`, so `e2e-localstack` is not recreated and S3 is kept.
- Only `e2e-frontend` was recreated. Postgres, backend, localstack, mock-idp and mailpit are untouched (created 06:28:21–22Z).
- Health: healthy, and `/mock-login` returns 200.

**Frontend image:** `docteams-e2e-frontend` **`sha256:6e5a4929fab5de61319f752214916b42a445b98699a8f1c212c26382783a1868`**, created **2026-11-27T07:54:04Z**.
- Compose label `working_dir` = `/Users/rakheendama/Projects/2026/wt-668A-obs/compose`.
- The fix is in the image: the task-table client chunk contains `hidden @min-[34rem]:table-cell` and `hidden @min-[38rem]:table-cell`, and no `@min-[40rem]` / `@min-[46rem]` table-cell.
- Backend image unchanged.

**Data:** the same data as OBS-after, including the F-668A-R1 writes from `after/seed-r1.json`: Alice has 2 × 60 min on Mon 23 Nov and 30 min on Tue 24 Nov for task `4743c754…`. Nothing was reseeded or written in this run.

**Browser:** Playwright library (Chromium from the main checkout's `frontend/node_modules`), Node 20.11.1 (nvm), DPR 1, reduced motion, TZ Africa/Johannesburg, rail expanded, theme via localStorage `kazi-theme`. Logins went through `/mock-login`.

### Scripts

**Replay (unedited):** from `before/scripts/`:
- `PHASE=after-fix node 01-capture.cjs`: 64 loads plus interactions, exit 0.
- `PHASE=after-fix STACK="frontend docteams-e2e-frontend sha256:6e5a4929fab5 @7d0336046 (wt-668A-obs)" node 02-assemble.cjs`: exit 0.
- Output: 14 contact sheets, 7 crops, `measurements.json`, `sel-compat.json`, `console.json`, `discover.json`.

**New scripts, as executed, in `after-fix/scripts/`:**
- `20-afterfix-measure.cjs` → `measure.json`, `crop-court-dates-{1024-light,1024-dark,1440-light}.png`, `crop-k09-tasks-{1024,1280,1366}-light.png`, `crop-pager-p3-after-close-1440-light.png`. Note: its `crop-court-dates-1440-light.png` overwrote the replay crop of the same name. Both are crops of the court-date list at 1440 light.
- `21-sweep.cjs` → `sweep.json`, `sheet-home-{alice,carol}.png`.
- `22-collapse-layout.cjs` → `collapse-layout.json`.
- `23-collapse-proto.cjs` → `collapse-proto.json`. Read-only, CSS injection only.

Raw tiles are in `/tmp/668A-after-fix-raw/` (not committed).

## 1. F-668A-A-1 — court-date list (Khumalo matter, Schedule tab, Alice)

| Viewport | Before (`cf539e61a`) | After (`ee7929515`) | **After-fix (`7d0336046`)** | Result |
|---|---|---|---|---|
| 1024 light: table / scroller, doc overflow | 814 in 664, **62** | 824 in 664, 72 | **808 in 664, 56** | PASS (6 px better than before) |
| 1024 dark | 814 in 672, 62 | 824 in 672, 72 | **808 in 672, 56** | PASS |
| 1440 light | 1 080 / 1 080, 0 | 1 080 / 1 080, 0 | **1 080 / 1 080, 0** | PASS |
| Status column width | 122 (no icon, `px-4`) | 132 (icon, `px-4`) | **116** (icon, `px-2`, padding 8/8 px) | PASS |

**Badges:** all five rows show the icon and the label at 1024 light, 1024 dark and 1440 light. Widths are unchanged from OBS-after, and no badge is clipped:

| Row | Status | Badge width |
|---|---|---|
| 24 Nov | CANCELLED "Cancelled" | 95 |
| 25 Nov | HEARD "Heard" | 73 |
| 26 Nov | POSTPONED "Postponed" | 100 |
| 27 Nov | SCHEDULED "Scheduled" | 99 |
| 30 Nov | SCHEDULED "Scheduled" | 99 |

At 1440 the 16 px given back goes to Court, Matter and Client: 226/183/141 → 232/189/145.

**Reference only, not required:** adding `px-2` to the Actions column as well would give 792 / 48 (`measure.json › courtProto.plusActionsPx2`). This is the same as the OBS-after prototype.

## 2. F-668A-A-3 — My Work task tables (Alice, `?from=2026-11-23&to=2026-11-29`)

| Viewport | Before: Upcoming / Available (scroll/client) | After (`ee7929515`) | **After-fix**: scroll/client (clip) — columns shown | Result |
|---|---|---|---|---|
| 1024 light | 1 019/419 · 668/419 | 419/419 · 419/419; M T D | **419/419 (0) · 419/419 (0)** — Matter 128, Title 190/211, Due 101/80 | PASS |
| 1024 dark | 1 019/424 · 668/424 | 424/424 · 424/424; M T D | **424/424 (0) · 424/424 (0)** — same columns | PASS |
| 1280 light | — | 572/572; M T **P** D (Status hidden) | **572/572 (0)**: Upcoming M T163 P **S103** D. Available M T P D | PASS (Status now shown) |
| 1366 light | — | 624/624; M T P D (Status and Logged hidden) | **624/624 (0)**: Upcoming M T145 P S D **L70**. Available M T P D | PASS (Status and Logged shown) |
| 1440 light | 1 019/668 · 668/668 | 668/668; M T P S D | **668/668 (0)**: Upcoming M176 T142 P S D **L70**. Available M T P D | PASS (Logged shown) |
| 1440 dark | 1 019/673 · 673/673 | 673/673; M T P S D | **673/673 (0)**: Upcoming M T147 P S D L. Available M T P D | PASS |

Key: M = Matter, T = Title, P = Priority, S = Status, D = Due Date, L = Logged.

- Every visible column is fully inside its scroller at every viewport, and document h-overflow is 0 at all six.
- At 1024, Matter, Title and Due Date are visible and fully in view in both tables.
- The matter badge is truncated (`scrollWidth > clientWidth`) and carries the full name in `title` ("Khumalo — lease dispute", "Ndlovu — estate transfer") at all six viewports.
- Available Tasks uses the `available` column set (no Status or Logged). That is unchanged and by design.
- These numbers match the OBS-after CSS prototypes for A-3: Title 163 at 1280 with Status; Title 145 / 142 at 1366 / 1440 with Logged.
- Logged reads "2h 30m". That value comes from the R1 entries.

## 3. Row activation (1440 light, history pushes counted per activation; `measure.json › activation`)

| Activation | Dialogs | `taskId` | Pushes | After Escape |
|---|---|---|---|---|
| Title button click (Available, row 1) | 1 "Task Detail" | `f3236396…` | **1** | taskId removed, 0 dialogs |
| Plain cell: Due Date | 1 | `f3236396…` | 1 | closed |
| Plain cell: Priority | 1 | `f3236396…` | 1 | closed |
| Matter badge (not interactive) | 1 | `f3236396…` | 1 | closed |
| My Tasks row (Due cell) | 1 | `4743c754…` | 1 | closed |
| Keyboard: from the "Available Tasks" toggle, **1 Tab** to the title button (`:focus-visible`), then **Enter** | 1 | `f3236396…` | 1 | closed |
| Keyboard: 1 Tab, then **Space** | 1 | `f3236396…` | 1 | closed |
| Pager Next / Previous (×5) | **0** | none | **0** | — |

Rows are not tab stops: there are 0 `tbody tr[tabindex]`. The title button has no `onClick` of its own, and a click on it opens its task exactly once.

## 4. Pager clamp (Available Tasks, 25 tasks)

- **Page 1 of 3:** 10 rows, first "Update the client on the hearing outcome", Previous disabled.
- **Next:** Page 2 of 3, 10 rows ("Review the lease agreement…").
- **Next:** Page 3 of 3, 5 rows ("Collate the payment history…"), Next disabled.
- **Previous:** Page 2. **Previous:** Page 1.
- The URL never changed, and no pager click opened anything.
- **Page 2:** opening the first task (`36678947…`) and closing it with Escape leaves the list on Page 2 of 3, same first row.
- **Page 3:** opening the last task (`35c45f5f…`) and closing it leaves the list on Page 3 of 3, still 3 after a further 1.5 s (`crop-pager-p3-after-close-1440-light.png`).

**No unexpected page jumps.**

## 5. Urgency grouping (My Tasks)

Same as OBS-after:
- A single group, "Upcoming", with 1 row: "Khumalo — prepare the heads of argument…" (`4743c754…`, due 30 Nov).
- No Overdue, Due This Week or No Due Date groups. The data has none.
- SEL-COMPAT counts are identical to OBS-after (see §6).

## 6. Regression sweep (replay of the after capture, plus Home)

- **SEL-COMPAT:** `after-fix/sel-compat.json` and `after/sel-compat.json` are **identical** (0 differing selectors). For example: `main tbody tr` on My Work is 11, `court-date-list` is 1 with 5 rows, diary testids are 4/4/4, the 7 "Hours for day N" inputs are present, and the bell selectors match.
- **Page measurements** (`measurements.json`, 64 loads) compared with OBS-after. Only these changed:
  - Court dates at 1024, L and D: document overflow **72 → 56** (the fix).
  - My Work page height **+28 px** (1801 → 1829 at 1440 L; similar on every variant). Cause: the "This Week" card grew from 343 to 371, because of the **R1 time entries**. This is data, not code.
- **Interaction diff** compared with OBS-after (excluding file paths and relative timestamps). Only these changed, and **all are due to the R1 data**:
  - **Timesheet keyboard** (1440 L and 1024 D): Monday now loads **pre-filled with 2** (the R1 duplicate), so typing "2.5" produces "22.5". Totals are 24.75h (was 4.75h).
    - The Tab sequence days 1→7 → "Add task…", ArrowUp ×2 = 0.5, and Save enabled are all unchanged.
    - **Nothing was saved.**
  - **My Work "Available Tasks" collapse:** open +28 (the card), collapsed 1526 → 1578 at 1440 L (1570 → 1642 at 1024 D).
    - With the section collapsed, the page height is set by the right-hand column (`lg:col-span-2`: `TimeBreakdown` + `TodayTimeEntries`; container 504 px vs tasks-panel 243 px, `collapse-layout.json`). That column grew with the R1 entries.
    - Hiding the newly shown Logged column with CSS changes nothing: 1578 / 1642 either way (`collapse-proto.json`). So the change is not caused by A-3.
  - **Bell popover:** relative timestamps only ("58 minutes ago" → "1 hour ago").
- **Diary:** the POSTPONED row (26 Nov) still reads "… Trial **Postponed** Overdue …". Its menu is still "Record outcome · Cancel". Every view and filter URL transition is byte-identical to OBS-after. Carol's empty states are unchanged.
- **Notifications:** the toggles carry `aria-pressed` (All=true / Unread=false, and they flip). There are 10 items, plus "Mark all as read" and "Load more". Carol sees the "You're all caught up" EmptyState. All unchanged.
- **Timesheet header:** PageHeader h1 "Timesheet" with the same subtitle (`sheet-timesheet-*.png`).
- **Home `/dashboard`** (`sweep.json`, `sheet-home-*.png`), Alice and Carol × 1024/1440 × light/dark:
  - 1 main, 1 h1 "Home", `todays-diary` = 1, document overflow 0.
  - Alice: "Court date 10:00 … Pre-trial … 1 overdue". Carol: "Nothing in the diary today".

## Console and shell

**Console:** **0** messages in the replay (64 loads; `console.json` totals: 0 errors, 0 warnings, 0 page errors, 0 hydration). Also 0 in each of `20`, `21`, `22` and `23`.

**h1 / main:** `main#main-content` = 1 and h1 in main = 1 on every load. The one exception is Carol on the Khumalo matter page ("This matter isn't on your list"), which has 0 h1. That is pre-existing **F-668A-B-4**, unchanged.

**Document h-overflow:** 0 everywhere except the court-date matter page at 1024 (**56**, previously 62 before the slice and 72 in OBS-after).

## Findings

**Fixed:**
- **F-668A-A-1:** 808 / 56, better than before.
- **F-668A-A-3:** Status from 34rem, Logged from 38rem. No clip at 1024, 1280, 1366 or 1440.

**No new findings.**

**Still open and unchanged:** F-668A-A-2 (agenda slate-500 in dark), F-668A-T1, F-668A-T3, B-1, B-3, B-4, B-5, and F-668A-R1 (timesheet duplicate save; its data is still on this stack).
