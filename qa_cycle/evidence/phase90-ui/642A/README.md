# 642A: OBS AFTER (task 642.5), Clients list on DataTable

- **Build SHA:** `dd1fa28b48f7bfab50c2605a7994f20fdada7fe0` (worktree-epic-642A, branch `phase90/42A-clients-datatable`, PR #1772). No commits or pushes; no source modified.
- **Date:** 2026-11-12 (SAST). **Driver:** Playwright library 1.59.1, headless Chromium, nvm node v20.11.1, `NODE_OPTIONS=""`. Theme set via `localStorage["kazi-theme"]`.
- **Stack run 1 (parity + checks + customers):** `e2e-down.sh` (removed the 642.1 before stack), then `docker builder prune -f` (2.18 GB reclaimed), then `e2e-up.sh` from the worktree. Exit 0; the log is `e2e-up-run1.txt` ("Building from source at: …/worktree-epic-642A"). **The frontend was rebuilt:** `RUN pnpm --filter frontend build` ran (47.1 s, not CACHED). Backend layers were cache hits, which is expected because the PR changes no backend code. Runs 2 and 3 used fresh containers and volumes on the same images (same source, fully cached; `legal/e2e-up-run{2,3}.txt`).
- **Datasets:** replayed exactly as `before/README.md` prescribes:
  - `pristine` is the fresh seed.
  - Then `rich-seed.sh` (`rich-seed.txt`, exit 0).
  - Then `before/setup-rich-api.sh` (`setup-rich-api.txt`, 13 × HTTP 20x; both deposits RECORDED). `GET /api/clients` is identical to the before run.
  - Then "OBS 642A view", created through the Save View dialog.
  - Then `setup-rich-api.sh revert` (`setup-rich-api-revert.txt`, HTTP 200) before the specs.

## 1. Parity vs `before/before-*.json`

Driver: `obs-642a-after.cjs`, a copy of `before/obs-642a-before.cjs`. **The only changes** (`diff` is 3 hunks):
1. The header comment.
2. The arrow probe. The sort arrows are now lucide `<svg>` icons, so `arrow` reads `svg.lucide-arrow-up` / `svg.lucide-arrow-down` and maps them to the same `"↑"` / `"↓"` / `null` values. This is the same change 641A made.
3. The default `PW_PATH` points at the worktree's `@playwright/test`. It is the same version, 1.59.1.

The runs were `node obs-642a-after.cjs after-pristine 0` and `node obs-642a-after.cjs after-rich 1`. The comparison is `python3 -I before/compare-642a.py pristine|rich`, which writes `parity-pristine.txt` and `parity-rich.txt`.

| Run | IDENTICAL | DIFF |
|---|---|---|
| pristine | 509 | 2 |
| rich | 549 | 3 |

All 8 sortable columns match on both clicks: URL, aria-sort, arrow direction, row order (including the tie/null-not-reversed trap), footer, third href and other aria-sorts. The following also match:
- Views, including clearOnSavedView: tags and lifecycleStatus are dropped on the saved view and kept on system views.
- Lifecycle pills and Show incomplete (including the pristine "All clients are complete" EmptyState).
- Search, including the e-mail match and Clear filters.
- Filter sheet, New Client dialog, and combined query preservation.
- All 7 empty and edge variants.
- Visible and hidden headers at 1024 and 1440, light and dark.
- Row DOM: `tr[data-testid=client-row]`, 10 cells, cursor pointer, links and buttons.
- Row click on a plain cell, the client link, the row menu, and Carol.

Every DIFF:

| # | Run | Field | Before → After | Classification |
|---|---|---|---|---|
| 1 | both | `8-keyboard tabsFromSearch` | 9 → 11 (pristine), 10 → 12 (rich) | **Expected** (PR: Columns menu + Export CSV). The two new tab stops are the `Columns` and `Export CSV` buttons. The target is the same: the `Client` header, then `?sortBy=name&sortDir=desc`. |
| 2 | both | `8-keyboard path(text,testid)` | `…, Filters, Client↑` → `…, Filters, Columns, Export CSV, Client` | **Expected.** It has the same two new stops, and the `↑` text glyph is gone because the arrow is now an `aria-hidden` lucide icon (PR D11 i). |
| 3 | rich | `1b 1024-light savedView table(width, …, overflowPx)` | 1021.3 / 718 / **303** → 1057.3 / 718 / **339** | **Expected side effect of the kit's inactive `ChevronsUpDown` (PR D11 i), with a visible cost.** See §3 "Client column". The probe shows exactly two columns widened by 18 px each: Open matters 77 → 95 and Completeness 121 → 139. The table still scrolls inside its frame and the page does not. Screenshots: `rich/01-saved-view-1024-light.png` vs `before/rich/01-saved-view-1024-light.png`. |

Not field-compared, but changed (all expected per the PR):
- `PageHeader` replaces the hand-rolled header: the h1 is smaller, at `text-2xl`.
- The sort-link focus indicator is a 3 px `ring/50` box-shadow (it was `outline: auto`), the same as matters.
- Inactive sortable headers show a muted chevron.
- `thead th` is now `position: sticky` on the default view and `static` on the saved view (§3).

Console in both runs: only the known `MockAuthContextProvider: failed to fetch userinfo` on **/dashboard**, 3 times per run. There are **no errors, warnings or hydration messages on any clients page.**

## 2. 642.5 checks (rich, Alice; driver `obs-642a-extra.cjs` → `extra.json`, screenshots `checks/`)

### Columns menu
- The menu is labelled "Show columns" and has **8 items: Lifecycle, FICA, Open matters, Outstanding fee notes, Trust balance, Completeness, Created, Contact**, all checked. Client and Actions are not listed. The saved view lists the same 8.
- Same 639C contract as matters: Contact appears **checked** at 1440 even though priority hides it.
- Unchecking Trust balance removes the header and its cells (8 visible cells, Actions included) and the menu stays open (`checks/colmenu-trust-hidden-1440-light.png`).
- **Resets on navigation:** clicking the Lifecycle sort link brings Trust balance back (`checks/colmenu-reset-after-sort-1440-light.png`). Hiding Completeness and then clicking the Active pill also brings it back.
- Menu screenshots: `checks/colmenu-open-{1024,1440}-{light,dark}.png`. The menu is fully on-screen at both widths.

### Export CSV
- The download is named **`clients.csv`**, with a UTF-8 BOM and CRLF line endings. It is saved as `clients.csv`.
  ```
  Client,Lifecycle,FICA,Open matters,Outstanding fee notes,Trust balance,Completeness,Created,Contact
  Acme Corp,Active,Done · 12 Nov 2026,2,"R 8 625,00 · 1",0,0%,12 Nov 2026,contact@acme.example.com · +1-555-0100
  ```
- **Empty cell → empty field:** Bright Solutions' "—" outstanding exports as `…,2,,15000,…`.
- **Trust is a raw number:** `0`, `15000`, `2500`.
- Contact with no phone (Zulu) exports as the e-mail alone.
- **A menu-hidden column drops out:** `clients-export-trust-hidden.csv` has no Trust balance column.
- Priority-hidden columns are **exported**. Contact is in the 1440 export; Open matters, Completeness, Created and Contact are in `clients-export-1024.csv`, whose header is identical to the 1440 one. This matches the 639C contract.
- The saved-view export (`clients-export-saved-view.csv`) has the same header.
- Outstanding exports as the rendered text (`"R 8 625,00 · 1"`), as PR D7 specifies.

### Sticky header (OBS-CARRY 639C, from 641A)
Default view, `extra.json → sticky`, page scroller = window:

| Viewport | Before scroll: `th` position / top | After scroll: `th` top (scrollY) | App header bottom | Covered? | Element at the header's centre | Page sideways overflow |
|---|---|---|---|---|---|---|
| 1440×500 light/dark | sticky / 313 | **64** (260 = max) | 64 | no | `a` in `thead` | none (1440/1440) |
| 1024×500 light/dark | sticky / 389 | 239 (150), 89 (300), **64** (336 = max) | 64 | no | `a` in `thead` | none (1024/1024) |

- `top: 64px` = `--app-header-height` (56) + 8. The header pins exactly at the floating header's bottom edge, and the rows scroll beneath it (first row top 94 < `th` bottom 105).
- The container is `overflow-x: visible`, and the table is not wider than its container (1134/1134 and 718/718).
- Screenshots: `checks/sticky-default-{top,scrolled}-{1440x500,1024x500}-{light,dark}.png`.

Saved view (explicit columns):
- `th` is `position: static` and the container is `overflow-x: auto`.
- At 1024 the table **scrolls sideways inside its frame** (scrollWidth 1057 / clientWidth 718; `scrollLeft` reaches 339). The page does not (1024/1024).
- Scrolling the page 220 px moves the header to top 49, under the app header. It is not sticky, as designed.
- Screenshots: `checks/sticky-saved-view-scrolled-*.png`, `checks/saved-view-scrolled-right-1024x500-light.png`.

### Two-line headers and accessible names
- At 1440, **Open matters, Outstanding fee notes and Trust balance** render on two lines: the two spans have different `top` values and every `th` is 40 px tall. One-word labels stay on one line. Contact (saved view) is one line.
- `thead` aria snapshot: `columnheader "Client"` … `"Created"`, each holding `link "<label>"`, then `columnheader "Actions"`.
- `getByRole("columnheader"|"link", {name, exact})` finds exactly one of each, for all 8 visible sortable labels. Contact is priority-hidden at 1440 on the default view. **The accessible names are unchanged.**
- Screenshot: `checks/headers-two-line-1440-light.png`.

### Client column
Default view:

| Width | Client `th` | Name links | Truncated |
|---|---|---|---|
| 1024 | 219 px | 73–155 px | **0 of 6** |
| 1440 | 301 px | 73–155 px | **0 of 6** |

Before (estimated from `before/rich/01-default-1440-dark.png`): the Lifecycle header sat at x≈626, now it is at x≈590, so the Client column at 1440 is ~36 px narrower.

Saved view, from the before probe re-run **unchanged** (`probe-saved-view-client-column-after.json` vs `before/probe-saved-view-client-column.json`):

| Width | Before | After | Verdict |
|---|---|---|---|
| 1024 | Client `th` 16 px, link **0 px** | Client `th` 16 px, link **0 px** | **Unchanged.** Names are still invisible (`checks/saved-view-1024-light.png`). |
| 1440 | Client `th` 129 px | Client `th` **93 px**; links 77 px, **5 of 6 names truncated** ("Bright Sol…", "Carlos M…", "Dormant …", "Mkhize F…", "Zulu Pros…"; `checks/saved-view-1440-light.png`) | **Worse.** The 36 px went to the chevrons on Open matters and Completeness. |

### Tag chips (found during this check)
- Zulu has 4 tags, shown as 3 chips plus "+1".
- At 1440 on the default view the **"+1" chip is now clipped** at the cell edge (`chips[+1].fullyVisible=false`, `checks/default-1440-*.png`). Before, it was fully visible (`before/rich/01-default-1440-dark.png`).
- At 1024 "Litigation" and "+1" were already clipped before, so that part is pre-existing.

### Keyboard
- From search, Tab goes through 7 pills, Show incomplete, Filters, **Columns**, **Export CSV**, then the `Client` header link: 12 tabs.
- The header link shows a 3 px ring (`checks/keyboard-focus-sort-link-1440-light.png`).
- Enter goes to `?sortBy=name&sortDir=desc` with order ZP MT DI CM BS AC.
- The Columns menu opens with Enter.

### Row interactions
- Clicking the Trust balance cell (plain text, `R 15 000,00`) opens Bright Solutions (`/clients/<id>`, h1 "Bright Solutions").
- The client link navigates **once**: 1 main-frame navigation.
- The row menu opens with one item, "Open client", and the URL is unchanged after 1 s (`checks/row-menu-open-1440-light.png`). "Open client" navigates.
- There is no pagination control: no rows-per-page, no "Page X of Y", no previous/next.

### Empty variants
- These show "No clients match this view." plus Clear filters (`/org/e2e-test-org/clients`):
  - `?search=zzqx-no-such-client`
  - `?lifecycleStatus=OFFBOARDED`
  - `?tags=litigation&showIncomplete=true`
- In those states there is **no table, no footer, and no Columns or Export CSV**. The toolbar stays: views, search, pills and Show incomplete.
- Clear filters returns 6 rows.
- An unknown view UUID falls back to All clients with 6 rows.
- Screenshots: `checks/empty-*.png` and `checks/filtered-empty-{1024,1440}-{light,dark}.png`.
- The pristine EmptyState variants are covered by parity: identical.

### Dates (SA calendar)
The API `createdAt` values are 17:48–17:52Z on 12 Nov, which is 19:48–19:52 SAST and **13 Nov in Pacific/Kiritimati**. On the saved view (every column shown), Created and FICA dates are checked in three browser zones:

| Zone (offset) | Created | FICA Done date | Hard reload | CSV row |
|---|---|---|---|---|
| Africa/Johannesburg (+120) | 6/6 = `12 Nov 2026` | 3/3 = `12 Nov 2026` | same | same |
| America/New_York (−300) | 6/6 | 3/3 | same | same |
| **Pacific/Kiritimati (+840)** | 6/6 = `12 Nov 2026` (browser-local formatting would show 13 Nov) | 3/3 | same | same |

All the CSVs are identical (`clients-export-{johannesburg,new_york,kiritimati}.csv`). There are zero hydration messages.

### Carol (member)
- At 1440 and 1024 Carol gets **"You don't have access to Clients / Contact your organisation admin to update your role. / Go to dashboard"**.
- There is no table and no Columns or Export controls (`checks/carol-{1440,1024}-light.png`).
- This is identical to before, and parity step 11 is IDENTICAL.

### Console
`extra.json` has 8 entries, all the known `MockAuthContextProvider: failed to fetch userinfo` on /dashboard. With the parity runs, the totals on clients pages are **0 errors, 0 warnings and 0 hydration messages**. That covers 3 browser zones and 1024/1440 × light/dark.

## Primitive observation (639C)
- **Toolbar slots:**
  - The views row comes first, then search plus filters (lifecycle pills, Show incomplete, Filters sheet).
  - Columns and Export CSV sit on the right.
  - At 1024 the controls wrap to the Filters line. They are hidden when the list is empty.
- **Columns menu:** works and resets on navigation. Priority-hidden columns are listed as checked (same as matters).
- **CSV:** visible columns × rendered rows. It has a BOM and CRLF line endings, and empty cells export as empty fields. It includes priority-hidden columns and excludes menu-hidden ones.
- **Sticky header:** **first sticky consumer, and it works.**
  - It pins at 64 px under the floating header at 1440 and 1024, in light and dark.
  - Nothing covers it, and the page has no horizontal overflow.
  - `stickyHeader={false}` on the saved view gives in-frame sideways scroll as designed.
- **`DataTableColumnHeader` children override:** two-line labels render. The names, the sort link, the icon and the focus ring all stay in the kit.
- **Pagination:** no control is rendered (`pageSize={null}`).

## SEL-COMPAT after and `test:e2e:customers`
- The PR changes **no** e2e file (`git diff --stat 38c34ce92...HEAD -- frontend/e2e` is empty).
- `sel-compat-after.txt` has the same selector inventory as `before/sel-compat-before.txt` (order-insensitive diff: IDENTICAL).
- `pnpm test:e2e:customers` ran after the rich checks and the revert: **18 passed / 2 skipped / 0 failed**, exit 0, 10.6 s. Before was 18 passed / 2 skipped. The skips are the same `628F-BLOCKER` tests. The log is `e2e-customers-after.txt`.

## E2E-LEGAL
- `test:e2e:legal-lifecycle` (run 2, fresh stack: `e2e-down` then `e2e-up`): **105 passed / 0 failed**, 3.0 min, exit 0. Matches the 105 baseline. Log: `legal/e2e-legal-lifecycle-run1.txt`.
  - That includes `day-90-review.spec.ts` ("Clients list", `clients-table`) and every `openClient` call site.
- `test:e2e:legal-depth-ii` (run 3, fresh stack): **40 passed / 4 skipped / 0 failed**, 2.0 min, exit 0. Matches the 40/4 baseline. The skips are the opt-in `null-prefetch-probe` tests. Log: `legal/e2e-legal-depth-ii-run1.txt`.
- No failures, so no re-runs were needed.
- After every suite, tracked artefacts were restored. The untracked `documentation/screenshots/legal-vertical/*.png` (16 after the lifecycle run) and `frontend/test-results/` were removed.
- **The run-3 stack is left running.**

## Defects and observations

| # | Severity | New / pre-existing | Finding |
|---|---|---|---|
| D1 | LOW–MEDIUM | **Pre-existing, made worse at 1440 by this PR** | **Saved view (explicit columns): Client column squeezed.** At 1024 it is unchanged: `th` 16 px, links 0 px, names invisible. At 1440 the `th` goes from 129 to 93 px and 5 of 6 names are now truncated. **Cause:** the kit's inactive `ChevronsUpDown` (+18 px) on Open matters and Completeness, combined with `CLIENT_CELL_CLASS = "w-full max-w-0"` and no priority hiding on saved views. The PR's own note (D11 / "Client column's minimum width drops…") anticipated this. Evidence: `probe-saved-view-client-column-after.json`, `checks/saved-view-{1024,1440}-light.png`. |
| D2 | LOW | **New at 1440** (pre-existing at 1024) | **Default view at 1440: Zulu's "+1" tag chip is clipped**, because the Client column is ~36 px narrower (same cause as D1). A user cannot see that a 4th tag exists. Evidence: `checks/default-1440-light.png` vs `before/rich/01-default-1440-dark.png`; `extra.json → layout.1440-*.clientColumn.tagRows`. |
| — | — | Pre-existing, unchanged | The Save View step 1 dialog is taller than the viewport (top −169, bottom 1070 at 1440×900), so Next is off-screen (`after-rich.json → 3-views.createDialogStep1`). Ties and nulls are still not reversed by `desc`, and that is replicated exactly. `day-90-review.spec.ts:73` "hidden copy" comment is still stale: `probe-duplicate-list-after.json` shows 1 `clients-table` in the document. |

None of these block 642A on behaviour: parity has no unexplained DIFFs, and all suites match their baselines. D1 and D2 are layout costs of the kit's sort chevrons and are recorded for an orchestrator ruling. A possible follow-up is to drop the inactive chevron on narrow columns, or give the Client column a `min-w` on saved views.

## Files
- **Scripts:** `obs-642a-after.cjs` (the before driver plus 3 documented edits) and `obs-642a-extra.cjs` (642.5 checks). The comparison uses `before/compare-642a.py` and `before/probe-*.cjs`, both unchanged.
- **Data:**
  - `after-pristine.json`, `after-rich.json`, `parity-pristine.txt`, `parity-rich.txt`, `extra.json`.
  - `probe-saved-view-client-column-after.json`, `probe-duplicate-list-after.json`.
- **CSVs:**
  - `clients.csv`, `clients-export-trust-hidden.csv`, `clients-export-1024.csv`, `clients-export-saved-view.csv`.
  - `clients-export-{johannesburg,new_york,kiritimati}.csv`.
- **Logs:**
  - `e2e-down-run1.txt`, `e2e-up-run1.txt`, `rich-seed.txt`, `setup-rich-api.txt`, `setup-rich-api-revert.txt`.
  - `obs-run-{pristine,rich,extra}.log` (empty = no stderr).
  - `e2e-customers-after.txt`, `sel-compat-after.txt`.
  - `legal/e2e-{down,up}-run{2,3}.txt`, `legal/e2e-legal-lifecycle-run1.txt`, `legal/e2e-legal-depth-ii-run1.txt`.
- **Screenshots:** `pristine/` (25) and `rich/` (28), with the same names as `before/`, plus `checks/` (42).

## Final `git status --short` (worktree)
```
?? qa_cycle/evidence/phase90-ui/642A/
```

## Final code (706919c0a)

Re-gate on the review fix `706919c0ab3591832a37a761719ca64e07615e83`. It is CSS only: `DataTable` sticky mode applies from a 42rem `@container` frame (`@2xl:sticky`, `@2xl:overflow-x-visible`), and narrower frames keep `overflow-x-auto`. No source was modified and nothing was committed or pushed. All evidence is in `final/`.

### Stack
- Run 1: `e2e-down.sh` (`final/e2e-down-final1.txt`), then `docker builder prune -f` (2.18 GB reclaimed, `final/builder-prune.txt`), then `e2e-up.sh` from the worktree (`final/e2e-up-final1.txt`, exit 0, "Building from source at: …/worktree-epic-642A").
- **The frontend was rebuilt:** `#77 [frontend builder 11/11] RUN pnpm --filter frontend build` finished `DONE 46.9s`, not CACHED. The image was created at 20:29:10 SAST. The running container's CSS chunk (`.next/static/chunks/28m0l17xlvc0v.css`) contains `@2xl\:overflow-x-visible`.
- Runs 2 and 3 used fresh containers and volumes on the same images (`final/legal/e2e-{down,up}-run{2,3}.txt`). **The run-3 stack is left running.**

### Parity (pristine, run first on the fresh stack)
- `node final/obs-642a-after.cjs after-pristine 0` (an unchanged copy of `obs-642a-after.cjs`), then `python3 -I before/compare-642a.py pristine final/after-pristine.json`.
- Result: **IDENTICAL 509 / DIFF 2**, the same 2 explained DIFFs as at dd1fa28b4: `8-keyboard tabsFromSearch` 9 → 11, and `path` + Columns + Export CSV, `Client↑` → `Client`.
- Files: `final/parity-pristine.txt`, `final/after-pristine.json`, `final/pristine/*.png`.

### Narrow / wide probe (default view, Alice, light and dark identical)
Driver: `final/probe-642a-final.cjs` → `final/probe-{pristine,rich}.json`, `final/shots-{pristine,rich}/`. The rich data came from `rich-seed.sh` plus `before/setup-rich-api.sh` (`final/rich-seed.txt`, `final/setup-rich-api.txt`: 13 × HTTP 20x, both deposits RECORDED). Rich numbers:

| Viewport | docScrollWidth | `@container` frame | container width / overflow-x / scrollWidth | table width | first `th` position |
|---|---|---|---|---|---|
| 390×844 | **390** | 358 | 356 / **auto** / 515 | 514.9 | **static** |
| 768×1024 | **768** | 464 | 462 / **auto** / 515 | 514.9 | **static** |
| 1024×768 | **1024** | 720 | 718 / **visible** / 718 | 718 | **sticky** (top 64px) |
| 1440×900 | **1440** | 1136 | 1134 / **visible** / 1134 | 1134 | **sticky** (top 64px) |

- Pristine has 1 row and the same modes: table 485.7, container 356 at 390 and 462 at 768, and docScrollWidth equals the viewport at all 4 widths.
- **The page no longer scrolls sideways.** At dd1fa28b4 it did: 524 at 390, 788 at 768.
- **Row actions are reachable at 390 and 768.** Container `scrollLeft` = max (rich 159 / 53, pristine 130 / 24), and the "Client actions" button is inside the container and the viewport. `elementFromPoint` hits the button. A real click opens the menu (`["Open client"]`) and the URL is unchanged.
  - Screenshots: `shots-*/scrolled-right-*`, `shots-*/row-menu-scrolled-*`.

### Sticky scroll (rich; pristine cannot scroll: maxScrollY 0 at 1440×500, 39 at 1024×500)

| Viewport | `th` top at 0 | after scrollY 150 | 300 | 600 (clamped) | App header bottom | Covered | Hit in `thead` | Page overflow |
|---|---|---|---|---|---|---|---|---|
| 1440×500 L/D | 313 sticky | 163 | **64** (260 = max) | 64 | 64 | no | yes | none, container visible |
| 1024×500 L/D | 389 sticky | 239 | 89 | **64** (336 = max) | 64 | no | yes | none, container visible |

The first row top is 94, under the `th` bottom at 104, so rows scroll beneath the header. These are identical to the dd1fa28b4 numbers. Screenshots: `shots-rich/sticky-{top,scrolled}-*`.

### Console
- Every load across all probes (8 width × theme layout loads plus a client-side navigation each, 4 sticky loads, pristine and rich) logged only the known `MockAuthContextProvider: failed to fetch userinfo`, and only on **/dashboard**: 9 in pristine, 7 in rich, 3 in parity.
- On clients pages: **0 errors, 0 warnings, 0 hydration messages.**

### Duplicate `clients-table` sampling
- In `final/probe-*.json → layout.*.dup*`, 50 ms samples plus a MutationObserver over the first 5 s, covering both a full load and a client-side navigation from the dashboard (at 390 that navigation goes through the mobile nav):
  - **1 of 32 loads saw 2 tables:** rich, 768-dark full load, at about 121 ms.
  - Every client-side navigation saw at most 1.
- In `final/probe-dup-final.cjs`, 10 ms samples plus a MutationObserver:
  - Warm context, light: **0 of 30** (`probe-dup.json`).
  - **Fresh context per load, dark: 4 of 24** (390: 1/8, 768: 1/8, 1440: 2/8; `probe-dup-fresh.json`).
  - In every hit the copies coexist from about **104–124 ms to about 311–313 ms**. Copy 1 is visible in `#main-content` (6 rows). Copy 2 is **invisible, inside React's streamed-segment `<div hidden id="S:0">`, outside `#main-content`** (6 rows). After that it is always 1.
- **Reading:** this is a transient copy from streaming SSR on cold loads. A strict-mode `getByTestId("clients-table")` can match 2 during that window. The `day-90-review.spec.ts:73` comment ("a second, hidden copy … outside #main-content") describes it accurately as a transient, and the spec's `#main-content` scoping is the right guard. This was not chased further, and it is not caused by 706919c0a, which changes CSS only; an earlier session had already seen a strict-mode 2-match at 390.

### Specs
- `pnpm test:e2e:customers` (run-1 stack, after `setup-rich-api.sh revert` → HTTP 200, `final/setup-rich-api-revert.txt`): **18 passed / 2 skipped / 0 failed**, 10.1 s, exit 0 (`final/e2e-customers-final.txt`).
- `pnpm test:e2e:legal-lifecycle` (run 2, fresh stack): **105 passed / 0 failed / 0 flaky**, 3.1 min, exit 0 (`final/legal/e2e-legal-lifecycle-run1.txt`).
- `pnpm test:e2e:legal-depth-ii` (run 3, fresh stack): **40 passed / 4 skipped / 0 failed**, 1.9 min, exit 0 (`final/legal/e2e-legal-depth-ii-run1.txt`). The skips are the opt-in `null-prefetch-probe` tests.
- All match their baselines. No re-runs were needed.
- After each suite, tracked artefacts were restored. The untracked `documentation/screenshots/legal-vertical/*.png` (16) and `frontend/test-results/` were removed.

### Defect found in the final code
**D3 (MEDIUM, primary column): at narrow frames the default view's Client column collapses to its padding, and client names are invisible.**
- Evidence: `final/probe-client-col-narrow.json` (run-3 data), and `final/shots-rich/default-{390x844,768x1024}-*.png`.
- At 390 and 768:
  - Client `th`/`td` are **16 px** and every `client-link` is **0 px** wide.
  - The `CLIENT` header link (65 px) **overlaps `LIFECYCLE`**; the screenshot reads "CLIEIFECYCLE".
  - Rows show Lifecycle, FICA … with no client name. Scrolling the container does not reveal names, because the column itself is 16 px.
- At 1024 and 1440 the column is fine: 227 / 309 px, with names 73–285 px.
- **Mechanism (same as D1 on the saved view at 1024):** `CLIENT_CELL_CLASS = "w-full max-w-0"` (`frontend/components/clients/client-columns.tsx:70`) adds nothing to the table's min-content width. When the frame is narrower than the other columns' min-content (≈ 491 px of the 507–515 px table), the auto table layout gives the Client column 0 content width.
- **Provenance not established.** The class predates 642A (main `38c34ce92:frontend/components/clients/clients-table.tsx:60`). At dd1fa28b4 the same min-content math applied, although the page overflowed instead. None of the before or dd1fa28b4 evidence captures 390 or 768. 706919c0a did not introduce the mechanism; it made the narrow table scroll in place. The PASS criteria for the fix hold: no page overflow, local scroll, row actions reachable.
- **Orchestrator ruling needed:** possible fixes are a `min-w` on the Client cell below `@2xl`, or a sticky first column. Both are product and architecture calls.

### Files (final/)
- **Scripts:**
  - `obs-642a-after.cjs`, an unchanged copy of the parity driver.
  - `probe-642a-final.cjs` (layout, sticky, duplicate, console), `probe-dup-final.cjs` (duplicate characterisation), `probe-client-col-narrow.cjs` (D3).
- **Data:**
  - `after-pristine.json`, `parity-pristine.txt`.
  - `probe-{pristine,rich}.json`, `probe-dup.json`, `probe-dup-fresh.json`, `probe-client-col-narrow.json`.
- **Logs:**
  - `e2e-{down,up}-final1.txt`, `builder-prune.txt`, `rich-seed.txt`, `setup-rich-api{,-revert}.txt`, `e2e-customers-final.txt`.
  - `obs-run-pristine.log`, `probe-run-{pristine,rich}.log`, `probe-dup{,-fresh}-run.log` (empty = no stderr).
  - `legal/e2e-{down,up}-run{2,3}.txt`, `legal/e2e-legal-lifecycle-run1.txt`, `legal/e2e-legal-depth-ii-run1.txt`.
- **Screenshots:** `pristine/` (parity, 25), plus `shots-pristine/` and `shots-rich/` (24 each): default, scrolled-right and row-menu at 390 and 768, and sticky top/scrolled at 1440×500 and 1024×500, each in light and dark.

Final `git status --short` (worktree): `?? qa_cycle/evidence/phase90-ui/642A/`

> **Orchestrator note (evidence trim, merge-hook 300-file cap):** the final-code run's `final/pristine/` (25 parity-replay screenshots, matching `./pristine/`; `final/after-pristine.json` + `final/parity-pristine.txt` kept) and `final/shots-pristine/` (24 probe screenshots, same modes as `final/shots-rich/`; `final/probe-pristine.json` kept) were not committed.

> **Orchestrator note (compact JSON):** the 13 `*.json` evidence files are stored as compact single-line JSON (`json.dumps(separators=(",", ":"))`), each proven equal to the pretty-printed original by `json.load` comparison. The merge-gate hook classifies a PR via `gh pr diff --name-only`, which GitHub refuses for diffs over 20,000 lines (HTTP 406). The pretty-printed files put this PR at +33k lines. Pretty-print locally with `python3 -m json.tool <file>`; `compare-642a.py` reads either form.
