# 641A — OBS BEFORE (task 641.1): Matters list parity oracle

- **Build SHA:** `01e6782d14499a574c820a746ee529995bc39ace` (worktree-epic-641A, branch `phase90/41A-matters-datatable` == origin/main; no product change)
- **Date:** 2026-11-12 (SAST)
- **Stack:** mock-auth e2e (:3001 / :8081), fresh: `e2e-down.sh` → `docker builder prune -f` → `e2e-up.sh` from this worktree (containers created 15:57 SAST, all healthy; `/tmp/e2e-up-641A-before.log` exit 0)
- **Driver:** `obs-641a-before.cjs` (Playwright library, headless Chromium, nvm node 20). Login `/mock-login` → Sign In (Alice, owner). Theme via `localStorage["kazi-theme"]` (`<html class="dark">` confirmed).
- **Structured data:** `before.json` (`pristine` + `rich` runs, overflow measurement, spec counts). Screenshots: `pristine/` and `rich/` (`<step>-<width>-<theme>.png`).

## Datasets (replay these in the AFTER run)

| Run | Setup | Command |
|---|---|---|
| `pristine` | fresh e2e seed only: **1 matter** (Website Redesign / Acme Corp / Active), 0 tags, 0 saved views | `node obs-641a-before.cjs before-pristine 0` |
| `rich` | then `bash compose/seed/rich-seed.sh` (`rich-seed.txt`, exit 0) + API setup `setup-b-api.txt` (tags **Urgent** on Website Redesign, SEO Audit; **Litigation+Urgent** on Annual Report; **Legacy Migration archived**) + the script creates saved view **"OBS 641A view"** via the Save View dialog (default columns, no filters, not shared) | `node obs-641a-before.cjs before-rich 1` |

The pristine seed has one matter, so every sort order is trivially the same there; **use `rich` as the sort/search/filter oracle.**
Order: pristine capture → rich setup → rich capture → projects specs.

## 1. Default list — `/org/e2e-test-org/matters`
- h1 `Matters`, subtitle `5 matters` (pristine `1 matter`), caption (sr-only) `Matters — Open matters`, selected tab **Open matters**, status chip **Active** active.
- **Rows (rich, default sort = Next in diary asc, nulls last):** Mobile App MVP, Annual Report, Brand Guidelines, Website Redesign, SEO Audit. Footer `Showing 5 matters`; 5 × `[data-testid="matter-row"]`. Pristine: Website Redesign / `Showing 1 matter`.
- **Columns in order:** Reference, Matter, Client, Work type, Fee earner, Status, Opened, Unbilled, Trust, Next in diary, + Actions (sr-only header).
  - 1440 (table container 1134px): hidden = **Work type, Opened**.
  - 1024 (container 718px): hidden = **Work type, Fee earner, Opened**; the table still overflows: table 844px in a 718px `overflow-x:auto` container → Next in diary is clipped and the table scrolls sideways.
  - The active sort column is never hidden (Work type/Opened appear when sorted by them).
  - Saved view "OBS 641A view" (explicit columns) shows **all 10 columns** at 1440 (no priority hiding).
- **Row DOM:** real `<table data-testid="matters-table" data-slot="table">` in `div[data-slot=table-container]` in `div.@container`; `<tr data-testid="matter-row" data-slot="table-row" class="… cursor-pointer">`, 11 `<td data-slot="table-cell">`, no role, not focusable (tabIndex -1). Name = `<a data-testid="matter-link" href="/org/e2e-test-org/matters/<id>" title=<name>>` (+ outline tag badges, max 3 + "+N"); Client = `<a href="/org/.../clients/<id>">`; Reference = link when present (else "—"); status `<span data-testid="matter-status-badge">`; last cell = `<button data-testid="matter-row-actions-trigger" aria-haspopup="menu">` (sr-only "Matter actions"). **Clicking outside a link (status badge cell) navigates to the matter**: `/org/e2e-test-org/matters/686e9060-…`.
- Screenshots: `*/01-default-1440-light.png`, `*/01-default-1440-dark.png`, `*/01-default-1024-light.png`, `*/01-default-1024-dark.png`.

## 2. Sort (every sortable column, click + click again) — rich
All 10 matter columns are sort links. First click → `?sort=<key>&dir=asc`, aria-sort `ascending`, arrow ↑; second → `dir=desc`, `descending`, ↓; third href back to `asc`. Inactive headers `aria-sort="none"`, Actions header has no aria-sort. Default-active `Next in diary` (no URL params) first click goes to **`dir=desc`**.

| Column | key | asc rows | desc rows |
|---|---|---|---|
| Reference | referenceNumber | AR, BG, MA, SEO, WR | same (all null → name asc) |
| Matter | name | AR, BG, MA, SEO, WR | WR, SEO, MA, BG, AR |
| Client | clientName | BG, WR, MA, SEO, AR | AR, MA, SEO, BG, WR |
| Work type* | workType | AR, BG, MA, SEO, WR | same (all null) |
| Fee earner | feeEarnerName | AR, BG, MA, SEO, WR | same (all Alice Owner → tie) |
| Status | status | AR, BG, MA, SEO, WR | same (all Active) |
| Opened* | createdAt | WR, BG, MA, SEO, AR | AR, SEO, MA, BG, WR |
| Unbilled | unbilled | AR, BG, SEO, MA, WR | WR, MA, SEO, BG, AR |
| Trust | trustBalance | AR, BG, MA, SEO, WR | same (all R 0,00) |
| Next in diary | nextDiaryDate | MA, AR, BG, WR, SEO | AR, BG, WR, MA, SEO |

(AR Annual Report, BG Brand Guidelines, MA Mobile App MVP, SEO SEO Audit, WR Website Redesign. *hidden at 1440 by default → navigated to the header's href; the header then shows.) **Parity trap:** ties and nulls do NOT reverse with `desc` — ties fall back to name **asc** and nulls stay **last** in both directions (Reference/Work type/Fee earner/Status/Trust keep name-asc order under `desc`; SEO Audit with no diary date is last under both Next-in-diary directions). Unbilled sorts by minutes (Annual Report = 0 → first asc, last desc). `02-sort-last-1440-light.png`.

## 3. Saved views
Tabs (role=tab): **My matters, Open matters, Awaiting fee note, Dormant** (system, `data-system-view`), separator, then saved views. Switch URLs: `?view=system%3Amy-matters` (5 rows), Open matters → `/matters` (no param; default), `?view=system%3Aawaiting-fee-note` (MA, BG, WR, SEO — `Showing 4 matters`), `?view=system%3Adormant` (empty: "No matters match this view. / Clear filters"), saved → `?view=635c2ac5-83e1-4aa8-aa79-0698139beeea` (5 rows, all columns). Caption follows the view (`Matters — <view>`).
- **clearOnSavedView:** from `?tags=obs-nonexistent-tag&status=ALL`: → My matters = `?tags=obs-nonexistent-tag&status=ALL&view=system%3Amy-matters` (**tags kept**, still empty); → saved view = `?status=ALL&view=635c2ac5-…` (**tags dropped, status kept**; Filters button disappears on a saved view).
- Save View dialog titles: Configure Filters → Select Columns → Name Your View. `03-view-switch-1440-light.png`, `rich/03-save-view-dialog-step1-1440-light.png`.

## 4. Status filter (`[data-testid="project-status-filter"]`, links)
Active → `/matters` (5 rows), Completed → `?status=COMPLETED` (empty state, subtitle `0 matters`, no footer), Archived → `?status=ARCHIVED` (Legacy Migration, `Showing 1 matter`), Closed → `?status=CLOSED` (empty), All → `?status=ALL` (MA, AR, BG, WR, **Legacy Migration**, SEO; `Showing 6 matters`). `04-status-closed-…`, `04-status-all-…`.

## 5. Search (placeholder `Search matters…`, `role=search`)
Debounced `router.replace` → `?search=Mobile` → Mobile App MVP, `Showing 1 matter`. Non-match `?search=zzqx-no-such-matter` → no table, no footer, subtitle `0 matters`, dashed box "No matters match this view." + link **Clear filters** (`href=/org/e2e-test-org/matters`, drops every param); clicking it → 5 rows, search box cleared. `05-search-match-…`, `05-search-empty-…`.

## 6. Filter sheet (`matter-filters-trigger`, only when tags exist and not on a saved view)
Sheet "Filter matters" / "Show only matters carrying every selected tag."; controls: Tags label, Add Tag (command popover: Litigation, Urgent), Clear, Apply, Close. Litigation + Apply → `?tags=litigation` → Annual Report, `Showing 1 matter`, trigger reads `Filters` + count badge `1`. `06-filter-sheet-…`, `06-filter-applied-…`.

## 7. Create buttons
`button "New Matter"` → dialog **Create Matter**; `button "New from Template"` → dialog **New from Template — Select Template** (both enabled for Alice; closed with Escape, nothing created). `07-new-matter-…`, `07-new-from-template-…`.

## 8. Keyboard
From the search input, Tab ×7 (pristine ×6, no Filters button) reaches the first header link **Reference** (focus outline `auto`); Enter → `?sort=referenceNumber&dir=asc`. `08-keyboard-focus-…`.

## 9. Combined (query preservation)
Start `?status=ALL&search=Mobile&sort=name&dir=asc` → Mobile App MVP. Raw sort hrefs keep every param, e.g. Matter `/org/e2e-test-org/matters?status=ALL&search=Mobile&sort=name&dir=desc`, Client `…?status=ALL&search=Mobile&sort=clientName&dir=asc` (full list in `before.json` → `rich.steps.9-combined.start.sortHrefs`). Click Client → `?status=ALL&search=Mobile&sort=clientName&dir=asc` (search box keeps "Mobile", chip All). Status chip hrefs also keep search+sort; click Active → `?search=Mobile&sort=clientName&dir=asc`. Clearing search → `?sort=clientName&dir=asc` (5 rows, client order). `09-combined-…`.

## 10. Console
Only `error MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch` on **/dashboard** right after login (once per context; mock-auth artefact, not the matters page). **No errors, warnings or hydration messages on any Matters page load.**

## SEL-COMPAT specs (`pnpm test:e2e:projects`, after the rich capture)
**27 passed, 0 failed, 0 skipped, 0 flaky** (21.8s), exit 0 — `e2e-projects-before.txt`. Tracked artefacts restored; stray `frontend/test-results/` removed.

## Pre-existing oddities seen (not 641A scope, recorded only)
- Save View dialog step 1 (with tags + custom fields) is ~2330px tall with `overflow-y: visible` → at 1440×900 the dialog spans y −714…1614 and **Next is off-screen and cannot be clicked by mouse** (script used a DOM click).
- 1024: the table overflows its container horizontally even after priority hiding (Next in diary clipped).
- Ties/nulls are not reversed by `desc` (by design per `sortMatterRows`, but a TanStack swap will differ unless replicated).
