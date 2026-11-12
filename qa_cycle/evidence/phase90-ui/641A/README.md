# 641A — OBS AFTER (task 641.6): Matters list on DataTable

- **Build SHA:** `c74f4d36fcd33772e41023171a358e750edcdda3` (worktree-epic-641A, branch `phase90/41A-matters-datatable`, PR #1770). No commits made.
- **Date:** 2026-11-12 (SAST). **Driver:** Playwright library, headless Chromium, nvm node 20. Theme via `localStorage["kazi-theme"]`.
- **Stack run 1:** `e2e-down.sh` → `docker builder prune -f` (2.17 GB reclaimed) → `e2e-up.sh` (exit 0, `e2e-up-run1.txt`). The frontend image was rebuilt from source (`pnpm --filter frontend build` ran). Backend layers were all cache hits, which is expected: this PR changes no backend code. Runs 2, 2b and 3 used fresh containers and volumes on the same images (identical source, so the layer cache hit).
- **Files:** `after.json` (everything combined), `after-pristine.json` / `after-rich.json` (parity replay), `extra.json` (641.6 checks), `parity-pristine.txt` / `parity-rich.txt` (per-field verdicts), scripts `obs-641a-after.cjs` (the before script with only the arrow probe changed: arrows are now lucide icons), `obs-641a-extra.cjs`, `compare-641a.py`. Screenshots: `pristine/`, `rich/` (same names as `before/`), `checks/`.
- **Datasets:** same as before. `pristine` is the fresh seed. `rich` adds `rich-seed.sh` (`rich-seed.txt`, exit 0), the API setup in `setup-b-api.txt` (Urgent on Website Redesign + SEO Audit, Litigation+Urgent on Annual Report, Legacy Migration archived) and the saved view "OBS 641A view", created through the Save View dialog.

## 1. Parity vs `before/before.json` (fields: URL, aria-sort, visible row order, footer, plus step-specific fields)
| Run | IDENTICAL | DIFF |
|---|---|---|
| pristine | 274 | 4 |
| rich | 298 | 4 |

All 10 sort columns match on both clicks: URL, aria-sort, arrow direction, row order (including the tie/null-not-reversed trap), footer and third href. The following also match: views (including clearOnSavedView tags-drop), status chips, search, Clear filters, filter sheet, create dialogs, combined query preservation, visible/hidden headers at 1024 and 1440, the saved view showing all 10 columns, row DOM (links, buttons, cell count, cursor) and row click outside a link.

The 4 DIFFs (same in both runs):
1. `8-keyboard tabsFromSearch` went from 7 to 9 (pristine 6 to 8). The two new tab stops are the **Columns** and **Export CSV** buttons. Same target (`Reference` → `?sort=referenceNumber&dir=asc`). Expected.
2. `tableContainerWidth` (the `@container` frame) went from 1134 to 1136 at 1440 and from 718 to 720 at 1024. The border moved onto the frame; the inner scroll container is still 1134 / 718. Cosmetic.

Not field-compared but changed: `PageHeader` replaced the hand-rolled header, so the h1 is smaller (font-display 3xl → PageHeader), and the focus indicator on the sort link is now a 3px ring instead of `outline:auto`.

## 2. Table overflow (rich, default list)
| Width | before: table / scroll container | after: table / scroll container |
|---|---|---|
| 1440 | 1134 / 1134 (no scroll) | 1134 / 1134 (no scroll) |
| 1024 | 843.8 / 718 (scrolls) | **904.4 / 718 (scrolls, +60 px)** |

Pristine 1024: 768 / 718. The page itself never scrolls sideways (`docScrollWidth` equals the viewport). **Visual DIFF (defect candidate, LOW–MEDIUM):** at 1024 the table is 60 px wider than before. Before, "Next in diary" was partly visible ("NEXT I…" with the date icon). Now it is **completely off-screen** and Trust is clipped mid-value ("R 0,0"). Next in diary is the default sort column. Compare `before/rich/01-default-1024-light.png` with `checks/default-1024-light.png`, and see `checks/default-scrolled-right-1024-light.png`. Hypothesis, not verified: the new per-header sort icons (`ChevronsUpDown`, 14 px + 4 px gap) widen the narrow columns.

## 3. 641.6 checks (rich, Alice, 1440 light unless noted)
- **Columns menu:** labelled "Show columns", with 9 items (every column except Matter; Actions excluded), all checked.
  - Unchecking Client removes the header and its cells (9 → 8 cells) and keeps the menu open.
  - Visibility resets on navigation: clicking the Matter sort link brings Client back (`?sort=name&dir=asc`). Hiding Trust and then clicking the All chip brings Trust back. Screenshots: `checks/colmenu-open-*`, `colmenu-client-hidden-1440-light.png`, `colmenu-reset-after-sort-1440-light.png`.
  - Observation (639C contract): Work type and Opened appear **checked** although they are hidden at 1440 by priority. Toggling them has no visible effect.
- **Export CSV:** downloads `matters.csv` with a UTF-8 BOM and CRLF line endings. Saved here as `matters-export.csv`.
  - Header row: `Reference,Matter,Client,Work type,Fee earner,Status,Opened,Unbilled,Trust,Next in diary`
  - First data row: `,Mobile App MVP,Bright Solutions,,Alice Owner,Active,12 Nov 2026,8h 30m,0,19 Nov 2026`
  - Columns hidden by narrow width **are exported**: Work type and Opened at 1440, plus Fee earner at 1024 (`matters-export-1024.csv` has an identical header). Only a column removed through the Columns menu is dropped: `matters-export-client-hidden.csv` has no Client column. Empty cells ("—") export as empty fields. Trust exports as a raw number. Tags are not exported. Matches the 639C contract.
- **Redaction (Carol, member; confirmed as "Carol Member" in `checks/carol-default-1440-light.png`):**
  - The API returns `unbilledValue: null` for Carol (Alice gets `0.00`), and trust balance is not redacted.
  - On screen and in the CSV (`matters-export-carol.csv`), Unbilled shows minutes only. No value leaks.
  - Limitation: the rich seed has no billing rates, so Alice's value is also 0 and the two views look the same. The money redaction cannot be told apart on this dataset.
- **Keyboard:** Tab order from search is chips ×5 → Filters → Columns → Export CSV → `Reference` header link. Enter goes to `?sort=referenceNumber&dir=asc`. Columns opens with Enter. Screenshot: `checks/keyboard-focus-sort-link-1440-light.png`.
- **Pagination:** none. No "Rows per page", no "Page X of Y", no previous/next buttons.
- **Row click:**
  - Clicking the Unbilled cell ("—", not a link) opens the matter.
  - Clicking the client link opens `/clients/<id>`.
  - The row actions menu opens with "Open matter" and "Open client" and the URL does not change after 1 s. Each item navigates correctly. Screenshot: `checks/row-menu-open-1440-light.png`.
- **Empty variants:**
  - Filtered-empty (`?search=zzqx…`): no table, "No matters match this view." plus "Clear filters" (`/org/e2e-test-org/matters`), which goes back to 5 rows.
  - Dormant view: the same empty box (`checks/empty-dormant-1440-light.png`).
  - Columns and Export CSV are hidden when the list is empty.
- **Screenshots, 1024/1440 × light/dark:** `checks/default-*`, `sorted-unbilled-desc-*`, `colmenu-open-*`, `filtered-empty-*`.
- **Layout probes (`extra.json → layout`):**
  - h1, toolbar, search and frame all left-align at x=280. New Matter right-aligns with the frame edge.
  - One frame border (1 px, 8 px radius). The scroll container and the table have no border, so there is no double border.
  - Header and cell alignment match on every column, with Unbilled and Trust right-aligned.
  - Dark mode: card surfaces (frame lab 7.7 on body 2.2); no white surfaces in `#main-content`.
  - The toolbar wraps at 1024 (Filters, Columns and Export on a second line, the last two right-aligned) and is one line at 1440.
  - The column menu is fully on-screen at both widths.
- **Console:** across every load and context (pristine, rich, extra, Carol), the only message is the known `MockAuthContextProvider: failed to fetch userinfo` on /dashboard. No errors, warnings or hydration messages on any Matters page.

## Primitive observation (639C)
- **Toolbar slots:** views row, then search + filters (status chips, Filters sheet), then the controls on the right.
- **Columns menu:** works, and resets on navigation.
- **CSV:** visible columns × rendered rows, with BOM, CRLF and formula guarding. Priority-hidden columns are included.
- **Sticky header:** not applicable on matters (`stickyHeader={false}`); carried to 642A. At 1440×500, scrolling 107 px moved the header from 269 to 162 (`position: static`). It does not stick and nothing breaks (`checks/sticky-scrolled-1440x500-light.png`).
- **Sort link via keyboard:** works (focus ring visible).
- **Pagination:** no control rendered.

## 4. SEL-COMPAT and E2E-LEGAL
- `test:e2e:projects` (run 1, after the rich capture): **27 passed / 0 failed / 0 skipped / 0 flaky**, exit 0. Same as before (27). Log: `e2e-projects-after.txt`.
- `test:e2e:legal-lifecycle` run 2 (fresh stack): **91 passed / 2 failed / 12 did not run**, exit 1. Log: `legal/e2e-legal-lifecycle-run1.txt`.
  - `day-75-complex.spec.ts:224` "Log 360min on Moroka estate matter": the Time tabpanel still showed `Report estate … 1h` after 15 s. `openMatter` (matters list → matter) succeeded and the heading was correct. The 6 h entry **was persisted**: the API by-task shows `Prepare Liquidation & Distribution account 360 min` (`legal/ll-run1-moroka-by-task-api.txt`), and reloading the tab shows 6 h / total 7 h (`legal/ll-run1-moroka-time-tab-reload.txt`). So the matter Time tab did not refresh after the server action. That is on the matter detail page, not the list. It matches the known next@16.2.6 lost-ping stall.
  - `day-90-review.spec.ts:88` (test at :83) "Matters list shows the lifecycle's six matters": a **cascade**. "Apex Annual Returns" was never created, because `day-75:270` "Create 2nd Commercial matter for Apex" did not run after the serial failure above. The snapshot shows the DataTable rendering the 6 existing active matters correctly (`legal/ll-run1-day90-83-error-context.txt`).
  - `day-14-conveyancing.spec.ts:229` passed.
- `test:e2e:legal-lifecycle` run 2b (re-run on a fresh stack; the lifecycle specs depend on each other, so one spec cannot be re-run alone): **105 passed / 0 failed**, exit 0, 3.0 min. Matches the 105 baseline. Log: `legal/e2e-legal-lifecycle-run2.txt`.
- `test:e2e:legal-depth-ii` run 3 (fresh stack): **40 passed / 4 skipped / 0 failed**, exit 0. Matches the 40/4 baseline. Log: `legal/e2e-legal-depth-ii.txt`. The stack is left running.
- After every suite, tracked Playwright artefacts were restored and untracked `frontend/test-results/` plus `documentation/screenshots/legal-vertical/*.png` were removed. `git status --short` shows only this directory.

## Pre-existing (unchanged, recorded only)
The Save View step 1 dialog is still taller than the viewport, so Next is off-screen (`overflowY: visible`, top −714). Ties and nulls are still not reversed by `desc`, which is now replicated exactly.

## Final head 2a0610740

- **Build SHA:** `2a0610740d0e1a5b016a60f8dc7127285f692647` (review-fix commit on top of c74f4d36f; no commits made). Outputs are in `final/`. Scripts: `final/obs-641a-after.cjs` and `final/obs-641a-extra.cjs` (copies), `final/compare-641a.py` (path to `../before/before.json`), and a new `final/obs-641a-final-checks.cjs`.
- **Stack run 1:** `e2e-down` → `builder prune -f` → `e2e-up`, exit 0 (`final/e2e-up-run1.txt`). `RUN pnpm --filter frontend build` ran, so the frontend was rebuilt. The rendered Clear filters link has the class `text-primary … underline-offset-4 hover:underline`, which confirms the new source is live.
- **Datasets:** same replay as before: pristine, then `rich-seed.sh` (`final/rich-seed.txt`, exit 0), then the API setup (`final/setup-b-api.txt`), then "OBS 641A view" created through the Save View dialog.

### Parity vs `before/before.json`
| Run | IDENTICAL | DIFF |
|---|---|---|
| pristine | 274 | 4 |
| rich | 298 | 4 |

The DIFFs are the same 4 known ones and there are no new ones: `8-keyboard tabsFromSearch` +2 (Columns and Export CSV), and `tableContainerWidth` +2 px on 3 viewports. See `final/parity-*.txt`.

### Opened column and time zone (`final/final-checks.json`, `final/checks/saved-view-opened-*`)
The frontend SSR container runs in UTC. All matters have `createdAt` 2026-11-12T15:07–15:08Z (from the API, `final/matters-api.json`).

On the saved view at 1440 the Opened column is visible. Every row shows **12 Nov 2026**, which equals createdAt formatted in Africa/Johannesburg (5/5 rows), in all three browser contexts:
- `Africa/Johannesburg` (+120).
- `America/New_York` (−300).
- `Pacific/Kiritimati` (+840). This is the discriminating context: browser-local formatting would show **13 Nov 2026**, but the page still shows 12 Nov.

A hard reload gives the same text, and there are zero hydration messages in every context.

### CSV (`final/matters-export.csv`; Kiritimati copy `final/matters-export-kiritimati.csv` is byte-identical in content)
The file has a BOM and CRLF line endings.
```
Reference,Matter,Client,Work type,Fee earner,Status,Opened,Unbilled,Trust,Next in diary
,Mobile App MVP,Bright Solutions,,Alice Owner,Active,12 Nov 2026,8h 30m,0,19 Nov 2026
```
The numeric custom-field path was not exercised, because the rich dataset has no custom-field columns.

### Filtered-empty state (`?search=zzqx-no-such-matter`; `final/checks/filtered-empty-{1024,1440}-{light,dark}.png`, `filtered-empty-hover-*`)
- Shows "No matters match this view." and "Clear filters". No table is rendered.
- Link colour `text-primary`:
  - Light: `lab(2.2 …)` = #07080b on #f4f6f8, contrast **18.49:1**.
  - Dark: `lab(100 0 0)` = #ffffff on #07080b, contrast **20.03:1**.
  - Same at 1024 and 1440.
- The link has no underline at rest. Hover shows an underline with a 4 px offset. Keyboard focus shows `outline: auto`.
- Clicking it goes to `/org/e2e-test-org/matters`: 5 rows, "Showing 5 matters", search box empty. This holds in all 4 combinations.
- Observation, not a defect: primary is near-black in light mode and white in dark mode, so the link differs from the muted sentence above it by colour and weight (500), not by hue. The old link was teal.

### Console
Across all pristine and rich parity loads and the 20 final-check loads (3 time zones, saved view plus reload, and the empty state in 4 combinations), the only message is the known `MockAuthContextProvider: failed to fetch userinfo` on /dashboard. There are no errors, warnings or hydration messages on any Matters page.

### Suites
- `test:e2e:projects` (run 1, after the checks): **27 passed**, exit 0 (`final/e2e-projects.txt`).
- `test:e2e:legal-lifecycle` (run 2, fresh stack: down, prune, up with the frontend rebuilt): **105 passed / 0 failed**, 3.3 min, exit 0 (`final/legal/e2e-legal-lifecycle.txt`). The `day-75-complex.spec.ts:224` flake did **not** recur.
- `test:e2e:legal-depth-ii` (run 3, fresh stack): **40 passed / 4 skipped**, 1.9 min, exit 0 (`final/legal/e2e-legal-depth-ii.txt`). The stack is left running.
- After each suite, tracked artefacts were restored and the untracked `documentation/screenshots/legal-vertical/*.png` and `frontend/test-results/` were removed. `git status --short` shows only this directory.
