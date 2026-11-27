# 668A OBS-after — Diary, court dates, My Work, Timesheet, Notifications (PR #1880 @ `ee7929515`)

## Verdict: **FAIL** (one layout item; everything else PASS)

The one failing item is **F-668A-A-1 (MEDIUM, a regression this slice introduces)**. The brief required the court-date list at 1024 to be no worse than before. It is now **824 px in the 664 px scroller (was 814): +160 overflow (was +150), and the document overflows by 72 px (was 62)**. The new icon in the status badge widens the Status column from 122 to 132 px. The CSS-injection prototype numbers are under §2.

K-09, K-31, K-39 (668A part), K-96 and K-97 all meet their expected effects. Diary, timesheet keyboard entry, notifications, SEL-COMPAT and the console are unchanged or improved as intended.

## Run and environment

**When (2026-11-27):** capture ~07:28–07:31Z, extra measurements ~07:39Z, then the F-668A-R1 reproduction ~07:45Z (the only write, run last).

**Stack:** the running e2e mock-auth stack, org `e2e-test-org`, on the OBS-before data. It was not reseeded or torn down.
- **Frontend rebuilt from this worktree** (`wt-668A-obs`, branch `keep/668A-obs-after` = `ee7929515`).
  - Image `docteams-e2e-frontend` **`sha256:bb1ae44e727e12327b55e958c1b194cdf4c0d8d55efd6f266e2be0b197183da6`**, created **2026-11-27T07:26:50Z**.
  - Compose label `working_dir` = `/Users/rakheendama/Projects/2026/wt-668A-obs/compose`.
  - The slice code is in the image: `data-task-open` is in the SSR and client chunks, and `@min-[34rem]` is in the CSS chunk.
- **Rebuild command, deviation from the brief:** `docker compose -f compose/docker-compose.e2e.yml up -d --no-deps --build frontend`. That is `e2e-rebuild.sh`'s own command plus `--no-deps`.
  - Why: a `--dry-run` of the stock script showed it would **recreate `e2e-localstack`**. Its `./data/s3` bind-mount path differs between worktrees, and it has no `PERSISTENCE`, so the S3 objects would have been lost.
  - With `--no-deps` only `e2e-frontend` was recreated. The postgres, backend, localstack, mock-idp and mailpit containers are untouched (created 06:28:21–22Z, labels `worktree-epic-667B/compose`).
- Backend image `sha256:e7fd89e47741`, unchanged.

**Browser:** Playwright library (Chromium from the main checkout's `frontend/node_modules`), Node 20.11.1 (nvm), DPR 1, reduced motion, TZ Africa/Johannesburg, rail expanded, theme via localStorage. Personas: Alice (owner) and Carol (member), logged in through `/mock-login`.

**Replay:** the `before/scripts/` were run **unedited**:
- `PHASE=after node 01-capture.cjs`: 64 page loads and all interactions, exit 0.
- `PHASE=after STACK="…" node 02-assemble.cjs`: exit 0.

**New scripts (as executed), in `after/scripts/`:**
- `10-measure.cjs`: K-09 at 1024/1280/1366/1440, column prototypes, K-97 pager, row-open paths, K-96 geometry, K-39 composited contrast on My Work and Home, badge icons, court-date prototypes at 1024, notifications EmptyState.
- `11-r1-timesheet-duplicate.cjs`: the F-668A-R1 write.

**"Today" interactions:** today is Fri 27 Nov, so the replay windows match before. Day tab → `?from=2026-11-27&view=day`, and This Week → `?from=2026-11-23&to=2026-11-29`, both identical to before.

**Committed:**
- 14 contact sheets from the replay, plus 2 Home sheets (`sheet-home-{alice,carol}.png`).
- 7 replay crops, plus `crop-k96-header-1024-light-{alice,carol}-measure.png`, `crop-diary-postponed-row-1440-light.png` and `crop-r1-timesheet-after-second-save.png`.
- JSON, all single-line: `measurements.json`, `sel-compat.json`, `console.json`, `discover.json`, `measure.json`, `seed-r1.json`.
- Raw tiles are in `/tmp/668A-after-raw/` (not committed).

## Measurements, before → after

| Item | Before | After | Result |
|---|---|---|---|
| **K-09** My Tasks › Upcoming, table (scroll) / frame | 1440: 1 019 / 668 · 1024: 1 019 / 419 | 1440 L/D: 668/668, 673/673 · 1024 L/D: 419/419, 424/424 (clip 0) | PASS |
| **K-09** Available Tasks | 1024: 668 / 419 | 1024 L/D: 419/419, 424/424 · 1440: 668/668 (clip 0) | PASS |
| K-09 columns at 1024 | only Matter fully in view | Matter 128 · Title 190–216 · Due Date 80–101, all fully in view | PASS |
| K-09 at 1280 / 1366 light | — | 572/572 and 624/624, clip 0. Upcoming shows Matter, Title, Priority, Due; Status and Logged hidden (F-668A-A-3) | PASS (note) |
| K-09 matter badge | no-wrap, full text | truncated (`scrollWidth > clientWidth`) with `title` = full matter name, 1024–1440 | PASS |
| **K-97** Available Tasks (Alice, 25) | 25 rows, unpaged | 10 rows, "Page 1 of 3", Previous disabled. Next → p2 (10), Next → p3 (5, Next disabled), Previous ×2 → p1. No "Rows per page". URL unchanged | PASS |
| K-97 page height | 1024 L 2 433 · 1024 D 2 417 · 1440 2 277 | 1 841 · 1 825 · 1 801 | PASS |
| **K-96** at 1024 (Alice = Carol, L and D) | title block 80 px, "My Work" 2 lines (30 px), subtitle 5 lines, Timesheet right edge 1 025 (1 px past), doc overflow 1 | title block 301 px, h1 1 line (24 px), subtitle 1 line, Timesheet x 889–992 (light) / 897–1 000 (dark), 32 / 24 px inside. Document scrollWidth 1 024 = innerWidth (overflow 0) | PASS |
| K-96 date range | in the header row beside the title | toolbar row under the title (y 168 > h1 bottom 128), x 280–826. "This Month" click → `?from=2026-11-01&to=2026-11-30` at 1024 | PASS |
| **K-39** My Work agenda (light, composited) | `#989faa` on `#fdfeff` **2.64** ("/ 8h", "Nothing in the diary today", "No upcoming deadlines") | `#4d545f` on `#fdfeff` **7.57**. Dark unchanged at 6.74 | PASS |
| K-39 Home `/dashboard` (same component) | (2.64, same class) | Carol "Nothing in the diary today" 7.57 light / 6.74 dark. Alice's row text 7.57 / 6.74 | PASS |
| K-39 remainder (not 668A) | 2.64 | "Nothing in the diary for the next 14 days" (`upcoming-diary-list.tsx`) still **2.64** light | known F-668A-T1 |
| **K-31** bell badge "21" | `#fff` on `#00a38f` **3.16** light and dark | light `#fff` on `#00776a` **5.46**; dark **3.16** (unchanged, F-668A-T3) | PASS |
| Diary POSTPONED row (26 Nov, `bc250ac9`) | "Overdue" chip only | "Postponed" badge (`data-status=POSTPONED`, CalendarClock svg `aria-hidden`, 4.52:1) + "Overdue" 5.27. Week, day and list views. SCHEDULED and TASK_DUE rows: no badge | PASS |
| Court-date list badges | colour + label | icon + label on all 5: Cancelled 95 px, Heard 73, Postponed 100, Scheduled 99. Contrast unchanged (6.76 / 4.50 / 4.52 / 5.60 light) | PASS |
| **Court-date list at 1024** | table 814 in 664 (+150), doc overflow 62 | **824 in 664 (+160), doc overflow 72**. Status column 122 → 132 | **FAIL (F-668A-A-1)** |
| Court-date list at 1440 | 1 080 / 1 080 | 1 080 / 1 080 | PASS |
| Timesheet grid at 1024 | 996 in 710 | 996 in 710 (unchanged, F-668A-B-5) | PASS (not worse) |
| Timesheet keyboard | Tab days 1→7 → "Add task…". ArrowUp ×2 = 0.5. Totals 4.75h. Save enabled | identical sequence, values, totals and Save state (1440 L and 1024 D) | PASS |
| Notifications toggle | no `aria-pressed` | All `aria-pressed=true` / Unread `false`, flipping on click. Items 10, "Mark all as read", "Load more" unchanged | PASS |
| Notifications empty (Carol) | "You're all caught up" | `data-variant="default"` EmptyState "You're all caught up", one h1, PageHeader | PASS |
| h1 size on the 4 pages | 30 px (Timesheet 24 px) | 24 px kit `PageHeader` on Diary, My Work, Timesheet and Notifications | as designed |

## Per surface

1. **Diary** (`sheet-diary-*.png`). Every URL transition is byte-identical to before:
   - Day / Month / List / Week, Next / Previous.
   - Court-dates chip off → `types=PRESCRIPTION%2CMATTER_DUE%2CTASK_DUE` (4 → 1 rows), on → dropped.
   - Mid-month canonicalised; fee-earner options unchanged.
   - The POSTPONED row menu is still "Record outcome · Cancel" (F-668A-B-1 backend part, not in scope). Carol: empty states in every view.
2. **Court dates** (`sheet-court-dates.png`, `crop-court-dates-1440-light.png`): every row menu is identical to before, and badges are icon + label.
   - **F-668A-A-1 prototypes** (CSS injection at 1024 light, `measure.json › courtProto`; before 814 / 62):
     - as shipped: table 824, doc overflow 72
     - icon hidden: 814 / 62 (back to before, but drops the icon)
     - icon 10 px + gap 2 px: 820 / 68 (still worse)
     - **Status `th`/`td` `px-2` (8 px) instead of `px-4`: 808 / 56** (6 px better than before, icon kept)
     - **Status + Actions `px-2`: 792 / 48** (22 px better than before)
     - Suggested fix: `px-2` on the court-date list's Status (and Actions) header and cells, in `court-date-list-view.tsx` (`:131`/`:141` header, `:178`/`:181` cells).
3. **My Work** (`sheet-my-work-*.png`, `crop-k09-available-tasks-1024-light.png`, `crop-k96-*`, `crop-k39-*`).
   - **Row open** (`measure.json › rowOpen`, history pushes counted per activation): each of these opens the same "Task Detail" sheet, `?taskId=f3236396-…`, **1 push**:
     - clicking the title button
     - clicking the Due Date cell
     - clicking the (non-interactive) matter badge
     - clicking a My Tasks row → `4743c754-…`, 1 push
   - The title button click opens once, not twice. Escape closes the sheet and removes `taskId`.
   - **Keyboard:** from the "Available Tasks" toggle, **1 Tab** reaches the first title button (`:focus-visible`, a 2 px ring box-shadow). **Enter** opens the sheet (1 push). Rows themselves are not tab stops (0 `tr[tabindex]`).
   - Date-range presets, saved-view tab "All" + "Save View" → "Configure Filters" are unchanged. Collapse/expand works (1 801 ↔ 1 526 at 1440).
4. **Timesheet** (`sheet-timesheet-*.png`, `crop-timesheet-typed-1440-light.png`):
   - PageHeader h1 "Timesheet" with the same subtitle.
   - Rows unchanged (K-88 held): Alice's 1 assigned row, Daily Total "—" while the API has 240 min on Fri 27 Nov.
   - "Add task…" "Estate" → 0, "Khumalo" → 0, both as before.
5. **Notifications** (`sheet-notifications-*.png`, `crop-k31-*`): PageHeader, a Card list with 10 items, toggles carry `aria-pressed`, and Unread shows the same 10.
   - Bell popover: "Mark all read" + 10 unread + "View all notifications" 1; Escape closes it.
6. **Home** (`sheet-home-alice.png`, `sheet-home-carol.png`): renders in all 4 variants, 1 `main#main-content`, 1 h1. K-39 as in the table.

## Shell, console, SEL-COMPAT

- **h1 / main:** in every capture (64 loads, plus the 10-measure loads): `main#main-content` = 1 and h1 in main = 1. The exception is Carol's "This matter isn't on your list" page, which has 0 h1 (pre-existing **F-668A-B-4**, unchanged). Document h-overflow is 0 everywhere except the matter page at 1024 (**72**, F-668A-A-1; was 62). My Work at 1024 went from 1 to **0**.
- **Console:** `console.json` shows **0 errors, 0 warnings, 0 page errors, 0 hydration** over 64 loads (before: 0). `11-r1` had 0 as well.
  - The first `10-measure` run logged 135 `Canvas2D … willReadFrequently` warnings. They came from the probe's own canvas, not the app. The re-run with `willReadFrequently: true` (the committed `measure.json`) logged 0.
- **SEL-COMPAT** (`sel-compat.json`, 150 keys): before vs after differ only in `main tbody tr` on My Work (Alice 1024/1440): **26 → 11**. That is K-97 paging (1 Upcoming + 10 Available), expected; the PR records 0 e2e hits for Available Tasks.
  - Every other count is identical: h1 "My Work"/"Notifications", bell, "View all notifications", `court-date-list` 1/5 rows, diary testids 4/4/4, `tasks-panel`/`saved-view-selector`/`extended-widgets`, agenda testids, 7 "Hours for day N" inputs, and all the notification-preferences selectors.
- **Interaction diff** (before vs after, excluding file paths and relative timestamps), only the expected changes:
  - the POSTPONED row text gains "Postponed"
  - notification toggles gain `aria-pressed`; in dark the selected "All" is `#fff` 15.57 (was `#eff1f5` 13.77)
  - My Work page heights drop (K-97)

## F-668A-R1 reproduction (deliberate write; `seed-r1.json`, `crop-r1-timesheet-after-second-save.png`)

**REPRODUCED.** As Alice, through the UI only, on `/my-work/timesheet` (current week 23–29 Nov), row "Khumalo — prepare the heads of argument …" (`4743c754-dbdf-483b-9c39-6e6c037c6cf5`). Each value was typed and committed with Tab, because TimeCell commits on blur.

1. Mon = 1 → Save. Toast "Saved 1 entry" → `3a63018c-33da-4f60-82ee-9492308ed629` (2026-11-23, 60 min, billable, no description).
2. Reload. Mon shows 1 (pre-filled).
3. Tue = 0.5 → Save. Toast **"Saved 2 entries"** → `e2883c20-b1df-48d8-972f-acb8fbbde5e4` (2026-11-24, 30 min) **and `1c6ea6f5-132a-4b11-bfb4-f2cab0b42efa` (2026-11-23, 60 min, a duplicate of the pre-filled Monday)**.
4. `GET /api/my-work/time-entries?from=2026-11-23&to=2026-11-29` (the page's call): **2 Monday entries** for the task (60 + 60 min, both `billable: true`, `description: null`) and 1 Tuesday entry.
5. After reload the grid shows **Mon 2h** (the user typed 1), Daily Total 2h · 0.5h, week 2.5h.

**Data now changed on this stack:**
- Alice has 3 more entries (150 min) on 23/24 Nov.
- My Work "this week", the timesheet row and the K-88 numbers for this week differ from before/after.
- An OBS-after-fix replay must account for these entries. They are **not** deleted (no SQL writes); the R1 fix PR can use them as its reproduction.

The first two attempts of `11-r1` failed before any save, so nothing was written: `fill()` did not commit the cell, so Save stayed disabled. The committed script is the third, successful run.

## Findings (new in OBS-after)

| Id | Severity | What | Routing |
|---|---|---|---|
| **F-668A-A-1** | **MEDIUM** (introduced by 668A; gate item) | Court-date list at 1024 widened by the status icon: table 814 → **824** in 664 (+160), document h-overflow 62 → **72**. Status column 122 → 132 | Fix in PR #1880. Prototype: Status `th`/`td` `px-2` → 808 / 56; Status + Actions `px-2` → 792 / 48 |
| F-668A-A-2 | LOW (pre-existing) | Agenda `text-slate-500` labels in dark ("Today's diary", "Open diary", "Time Today", "Next Deadline", the next-deadline meta) are `#6b7482` on `#14171c` = **3.80:1**. Light 4.68. Lines `todays-agenda.tsx:56,61,182,205,217,226`, unchanged by 668A | 669A/670A light pass (with F-668A-T1) |
| F-668A-A-3 | LOW (tuning, not a clip) | K-09 thresholds hide columns that fit. Status (`@40rem`) is hidden at 1280 (frame 572) and 1366 (624); forced on it fits with clip 0 (Title 163 / 215 px). Logged (`@46rem`) is hidden at 1366/1440; forced on it fits at 1366 (Title 145) and 1440 (Title 142), and clips 35 px at 1280. Thresholds that fit everywhere: Status `@34rem` (544), Logged `@38rem` (608) | Optional retune (D-85 precedent); not blocking |

Confirmed unchanged: F-668A-B-1 (overdue flag + menu), B-2 (now A-1), B-3 (raw date/time), B-4, B-5 (timesheet 996/710). **B-6 is fixed** (`aria-pressed`). K-88 is held.
