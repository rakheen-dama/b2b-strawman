# 643C OBS: profitability tables on DataTable + reports index PageHeader (PR #1777 @ b0fe90ccf)

**Stack:** the mock-auth E2E stack, built from `worktree-epic-643C`. It already held data from the legal-lifecycle, legal-depth-ii and finance suites, with matter suffix `mwemk51df698`. Date 2026-11-13 (SAST). The stack was not rebuilt or reseeded.

**Browser:** Node `@playwright/test` chromium, headless, nvm node v20.11.1, timezone Africa/Johannesburg, `reducedMotion: "reduce"`. Dark mode was set with `localStorage["kazi-theme"]` plus `colorScheme`, and `html.dark` was confirmed for every dark measurement (`htmlClass` in after.json). The scripts are in `scripts/`: `common.cjs`, `observe.cjs`, `interact.cjs`, `matter-clip.cjs` and `roles.cjs`.

**State changes:** none persistent. Date-range edits are client state only, and the CSV export is a read.

**Before:** 643B `after/after.json` and `after/profitability-{1024,1440}-dark.png` / `-1024-light.png` in the main checkout. 643B ran on the same page on main, with matter suffixes of the same length.

All numbers are in `after/after.json` (compact). It has these keys: `static`, `interactions`, `matterClip`, `roles`, `reportCardsVsMain` and `matterWidthCounterfactual`.

| # | Check | Result | Numbers |
|---|---|---|---|
| 1 | **Profitability structure** | PASS | `#main-content` holds 3 tables (utilization, matter, client) at all 8 width × theme combinations. There is 1 `h1`, "Profitability", inside `data-testid=page-header`. The description is unchanged. `profitability-cost-note` reads "Costs reflect logged expenses only; disbursements are shown as fee-note lines", the same as main. Card order matches 643B: Fees Billed vs Collected, Team Utilization, Matter Profitability, Client Profitability. Billing trend is `data-state=ready` with 1 svg and sits above the utilization card. The utilization chart has 3 bars and the matter ranking chart has 6; both are above their tables (`chartAboveTable: true`). |
| 2 | **Column headers (role `columnheader`)** | PASS | Utilization: (empty expander) / Name / Total Hours / Billable / Non-Billable / Utilization %. Matter: Matter / Client / Currency / Billable Hours / Revenue / Cost / Margin / Margin %. Same as main. Sortable headers are `button`s whose accessible name equals the label; icons are `aria-hidden`. Currency is plain text with no `aria-sort`. |
| 3a | **Default sort** | PASS | Utilization % is `descending`, every other header is `none`. Matter: Margin is `descending` (rows R 10 500 → R 300). This matches main's `useState("utilization"/"margin")` + `"desc"`. |
| 3b | **Click sort, both tables, each column clicked twice** | PASS | 24 of 24 clicks: exactly one `th` is non-`none`, and the first click gives `descending`, the second `ascending`. The row order was checked monotonic for text (`localeCompare`) and numbers (hours, ZAR, %). Example: Matter → asc "Apex…, Estate…, Ndlovu v RAF — Depth II…, Ndlovu v RAF m…, Profit…, QC…". Utilization % and Non-Billable are ties in the data (all 100% / 0.0h). |
| 3c | **Keyboard sort** | PASS | 6 Tabs from `#util-to` reach the "Name" header button, with `:focus-visible` and a 3px `ring/50` box-shadow in both themes (`util-header-focus-1440-{light,dark}.png`). Enter sorts `descending` (Carol, Bob, Alice) and Space sorts `ascending`. Tab → Total Hours, then Enter sorts `descending`. Matter header: Enter sorts `descending`. Focus stays on the **same DOM node** (`sameNode: true`, `isConnected: true`) after every re-render. |
| 3d | **Header hover** | PASS | Hovering the Client header changes its colour from muted to foreground. Light: contrast 7.57 → 17.79. Dark: 6.74 → 17.96 (`matter-header-hover-1440-dark.png`). |
| 4 | **Expand / collapse currency breakdown** | PASS | All 3 members have a ZAR breakdown, so all 3 rows can expand. Clicking Bob's toggle changes its name from "Expand currency breakdown" to "Collapse currency breakdown". A `data-table-expanded-row` appears directly under Bob, in a single cell with `colSpan` 6 = 6 columns that spans the full table width. It reads "ZAR: Billable R 3 600,00 \| Cost R 1 500,00". It stays under Bob after sorting by Name desc (Carol, Bob, Alice) and asc. Keyboard: Enter collapses (0 expanded rows) and Space re-expands (1). Focus stays on the same node and the label flips. Background is `bg-muted/50`: composite rgb(246,247,250) in light and rgb(26,30,36) in dark. Muted text contrast is 7.13 (light) and 6.27 (dark). See `util-expanded-1440-{light,dark}.png`. |
| 5 | **Date range** | PASS | Utilization and Matter cards were each changed to four ranges: 10-01..10-31, 11-01..11-12, 11-13..11-13 and 11-01..11-13. **Empty ranges:** the chart and the table both unmount, the empty copy shows, and the `#main-content` table count drops accordingly. **11-13 ranges:** utilization shows 3 rows, 3 bars and categories equal to the rows. Matter shows 6 rows, 6 bars and categories equal to the rows, truncated. All time in this tenant is dated 11-13, so no range gives a different non-empty set. 0 console messages. See `util-range-oct-empty-1440-light.png`. |
| 6 | **CSV export** | PASS | There is 1 "Export CSV" button in the Matter card and 1 on the page. The download is `profitability-2026-11-01-to-2026-11-13.csv`, 7 lines. The header is `Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %`, and row 1 is `Estate Late Peter Moroka mwemk51df698,…,ZAR,7.00,17500.00,7000.00,10500.00,60.00`. The file is in `after/`. |
| 7a | **Page overflow (`scrollWidth − clientWidth`)** | PASS for this PR | Light and dark give the same results: **390: +5** (643B: +5), **768: 0** (643B: +8), **1024: 0**, **1440: 0**. Team Utilization's `#util-to` now wraps inside the card at 390 (right edge 182 against a card right edge of 374), so the 643B Utilization overflow is **fixed**. The +5 left at 390 comes only from `#cust-prof-to` in the Client Profitability header, which this PR does not touch (N1). |
| 7b | **Table container scroll** | **DEFECT D1** | Matter table: scrollWidth 1209 at both widths. At 1440 the container is 1086 wide, so it overflows by 123 px; at 1024 it is 670 wide and overflows by 539 px. Utilization: 1440 fits (1086/1086), 1024 fits (670/670). Client (unchanged): fits at 1440, overflows 97 px at 1024. |
| 7c | **Long names** | PASS | 0 text-over-cell overlaps and 0 neighbour overlaps in all three tables at all 8 combinations. Names stay on one line (`whitespace-nowrap`). |
| 7d | **Dark legibility** | PASS | Margin values (`text-success-foreground`): contrast 11.85 in dark, 7.27 in light. No negative margins exist in the data, so danger red was not rendered. Utilization bar fill against its track: 6.21 dark / 3.27 light; fill against the card: 7.16 / 3.66. Header button text: 6.74 dark / 7.57 light. |
| 8 | **Console** | PASS | 0 messages of any type, including 0 errors and 0 hydration warnings. This covers fresh loads of profitability ×8 and reports ×8 (390, 768, 1024, 1440 × light, dark) plus the two interaction sessions. |
| 9 | **Reports index** | PASS | There is 1 `h1`, "Reports", inside `page-header`. The first `h2` is "Firm performance". The 11 card links (title + href) **equal** main's code output (the Profitability card plus one card per `/api/report-definitions` entry, in order) at all 8 combinations. Grid columns: 1 at 390, 2 at 768, 3 at 1024 and 1440; the TRUST first row has 1/2/3/3 cards. Overflow is 0 at 390, 768, 1024 and 1440 in both themes. See `reports-{1024,1440}-{light,dark}.png` and `reports-390-light.png`. |

## Defects

### D1: Matter Profitability table hides more of its right-hand columns at 1440 (MEDIUM, regression)

At 1440 the Margin column is cut mid-value and Margin % is fully hidden behind the inner scroll.

- **Measurement:** Margin shows 78 of 108 px; the "R 10 500,00" value shows 70 of 92 px, so it reads "R 10 500,". Margin % shows 0 of 93 px. This is the same in light and dark. The container's horizontal scrollbar takes up no layout height, so nothing on screen signals the hidden columns.
- **Before:** in the 643B screenshot `profitability-1440-dark.png`, Margin is fully visible ("R 10 500,00") and only Margin % is clipped.
- **In-page counterfactual:** I removed `font-mono` from the money cells and set the header icons to 12 px, approximating main. The table then measures 1172 px against the 1086 px container. Margin fits and only Margin % is cut, which matches the 643B screenshot.
- **Cause of the extra width (+37 px):**
  - `MoneyCell` renders money in JetBrains Mono (`components/data-table/cells.tsx:24`, `font-mono`). It is used at `components/profitability/project-profitability-table.tsx:109,118,128`. Revenue, Cost and Margin each grow by about 11 px, about +33 px in total.
  - The kit's 14 px sort icons replace main's 12 px ones, adding about 4 px.
- **Underlying cause (pre-existing):** the nowrap Matter and Client name columns are 322 + 268 px wide (`components/ui/table.tsx:84`, `whitespace-nowrap`).
- **Evidence:** `matter-card-1440-{light,dark}.png`, `matter-card-counterfactual-no-mono-12px-icons-1440-light.png`, after.json `matterClip` / `matterWidthCounterfactual`.
- **Severity:** not a data defect, and the CSV is complete. But Margin is the card's key figure and it is now truncated at a standard laptop width. Whether to fix it in this PR or log it as 644B is the orchestrator's call.
- **Possible remedies (not applied):** pass a non-mono class to `MoneyCell` here, or cap and truncate the Matter and Client columns.

### D2: money renders in a different font in two adjacent tables (LOW, 644B polish)

Money in the Matter table is monospace, while the Client Profitability table directly below (not migrated) and main both use the proportional font. "R 17 500,00" therefore renders differently in two neighbouring tables on the same page. The cause is the same `cells.tsx:24`. Evidence: `profitability-1440-{light,dark}.png`.

## Notes (not defects of this PR)

- **N1 (pre-existing, untouched file):** the Client Profitability From/To row has no `flex-wrap` (`components/profitability/customer-profitability-section.tsx:164`).
  - At 390, `#cust-prof-to` ends at 395 against a card edge of 374, which causes the page's +5 px overflow.
  - At 768 it ends at 764 against a card edge of 744. That spills 20 px outside the card, but the page itself does not overflow.
  - This is the same class as the Utilization row this PR fixed. Suggest a 644B one-liner.
- **N2 (kit polish, same as main):** the utilization expand toggle shows the browser's default focus outline (`outline: auto 1px`), not the kit's 3px `ring/50`. The `button` className in `components/profitability/utilization-table.tsx`, around line 108, has no `focus-visible` classes; main had none either. It is visible: `util-expand-toggle-focus-1440-light.png`.
- **N3:** the page `h1` is now 24px semibold (the PageHeader standard) instead of main's 30px `font-display`, on both pages. This is intentional kit adoption.
- **N4 (643B N4, pre-existing):** a chart tooltip stays visible after keyboard focus tabs past the utilization chart. It is visible in `util-header-focus-1440-light.png`.
- **N5 (by design):** an expanded breakdown row stays expanded across a date-range refetch (empty → back). `autoResetExpanded: false` is documented in `data-table.tsx`, and main's `Set` state behaved the same way.
- **N6 (data limits):** every member has exactly one currency (ZAR), so a multi-currency breakdown was not observed. Every member is at 100% utilization, and there are no negative margins and no null costs, so the tie order, the red margin colour and the "N/A" cost were not exercised in the browser.

---

## Re-OBS @8dbb9a408 (fix head: money `font-sans`, toggle focus ring, DataTableSort union)

**Stack:** the mock-auth E2E stack, freshly built from `8dbb9a408`, after the legal and finance suites had run on it. It now holds matter suffixes `mwenki245a65` and `mwenoez8c4ed`, the same length as the first OBS. It was not rebuilt or reseeded. **Browser:** same as the first OBS (Node `@playwright/test` chromium headless, nvm node v20.11.1, Africa/Johannesburg, `reducedMotion: "reduce"`). `html.dark` / `html.light` was confirmed on every measurement.

**Scripts:** `scripts/refix/`. These are copies of `observe.cjs`, `interact.cjs`, `roles.cjs` and `common.cjs` with OUT set to `after-fix/`. `matter-clip.cjs` was extended to record column right edges, every Margin value's clipping and the money fonts. `font-probe.cjs` is new. **Evidence:** `after-fix/after-fix.json` (compact), which has the keys `static`, `interactions`, `matterClip`, `fontProbe` and `roles`. There are 12 PNGs (8 standard + `matter-card-1440-{light,dark}` + `util-expand-toggle-focus-1440-{light,dark}`) and the CSV.

| # | Check | Result | Numbers |
|---|---|---|---|
| R1 | **D1: Matter table width at 1440** | **PASS (fixed)** | Table and scrollWidth are **1154** (was 1209; main counterfactual 1172) against a container of 1086. The **Margin column ends at 1365** and the container ends at **1391**, so Margin has 26 px of slack. Margin is fully visible and **0 of 6 Margin values are clipped**: "R 10 500,00" shows 78 of 78 px. Margin % shows 26 of 93 px, the same as main/643B, where only Margin % was cut. Light and dark are the same. See `matter-card-1440-{light,dark}.png`. |
| R2 | **Matter table at 1024** | PASS (same as main) | 1154 against 670, overflow 484 (was 539). Columns from Billable Hours onward sit behind the inner scroll, as they did before the migration (Client also overflows 91 at 1024). The page itself has 0 overflow. |
| R3 | **Money font: not monospace** | PASS | Revenue, Cost and Margin spans have class `tabular-nums font-sans`, computed `plexSans, "plexSans Fallback"`, 14px, `tabular-nums`. The Matter body has 0 monospace elements in all 4 width × theme combinations. |
| R4 | **Money font matches Client Profitability** | **DEFECT D2b (LOW)** | Client money cells compute `ui-sans-serif, system-ui, sans-serif, …`, while Matter money computes **plexSans**. See D2b. |
| R5 | **Expand toggle focus-visible ring** | PASS | Keyboard focus (Enter, Space and a Shift+Tab → Tab return): `:focus-visible` is true, `outline-style: none`, and box-shadow is `0 0 0 3px oklab(0.58 -0.13 -0.007 / 0.5)` in light and `oklab(0.72 -0.15 -0.013 / 0.5)` in dark. This is the same ring as the Name sort-header button in the same session. Mouse-modality focus shows no ring (`focusVisible: false`, `boxShadow: none`). The browser-default outline is gone. See `util-expand-toggle-focus-1440-{light,dark}.png`. **First-OBS N2 is closed.** |
| R6 | **Structure + headers** | PASS | 3 tables at all 8 width × theme combinations. 1 `h1`, "Profitability", inside `page-header`. The description and cost note are unchanged. The card order is unchanged. Billing trend is `ready`, util has 3 bars, matter has 6 bars, and both charts are above their tables. The columnheader names are the same as the first OBS (util 6, matter 8, client 7). Currency has no `aria-sort`. |
| R7 | **Sorting** | PASS | Default sort: Utilization % `descending` and Margin `descending`. **24 of 24** mouse clicks pass: one active `th`, first click `descending` then `ascending`, rows monotonic. Keyboard: 6 Tabs from `#util-to` reach Name, which has a 3px ring. Enter gives desc (Carol, Bob, Alice) and Space gives asc. Tab → Total Hours, then Enter gives desc. Matter header, Enter gives desc. `sameNode: true` throughout, in both themes. Header hover contrast: 7.57 → 17.79 (light), 6.74 → 17.96 (dark). |
| R8 | **Expand / collapse** | PASS | All 3 rows can expand. Bob expands to `data-table-expanded-row` with colSpan 6 = 6 columns, spans the table, and reads "ZAR: Billable R 3 600,00 \| Cost R 1 500,00". It stays under Bob after Name desc and asc. Keyboard Enter → 0 expanded rows, Space → 1, and the label flips. Composite background is rgb(246,247,250) / rgb(26,30,36), and muted text contrast is 7.13 / 6.27. |
| R9 | **Date range** | PASS | Same as the first OBS. Oct and 11-01..11-12: table and chart unmount, empty copy shows. 11-13 and 11-01..11-13: util 3 rows / 3 bars, matter 6 rows / 6 bars, categories equal to the rows. |
| R10 | **CSV** | PASS | 1 button in the Matter card and 1 on the page. File `profitability-2026-11-01-to-2026-11-13.csv`, 7 lines, header `Matter,Client,Currency,Billable Hours,Revenue,Cost,Margin,Margin %`. |
| R11 | **Overflow / long names / dark contrast** | PASS (unchanged) | Page overflow: 390 +5 (N1, `#cust-prof-to`, untouched file), 768 0, 1024 0, 1440 0, in both themes. 0 text overlaps and 1 line per name in all 3 tables at all 8 combinations. Margin contrast is 7.27 (light) and 11.85 (dark). Util bar fill against track is 3.27 / 6.21. |
| R12 | **Reports index** | PASS | 1 `h1`, "Reports", in `page-header`. The first `h2` is "Firm performance". 11 cards, **equal** to the expected list (Profitability + API definitions) at all 8 combinations. Grid columns 1/2/3/3, overflow 0 everywhere. |
| R13 | **Console** | PASS | **0 messages** of any type (0 errors, 0 hydration warnings). This covers profitability ×8 and reports ×8 fresh loads plus both interaction sessions. |

### D2b: Matter money renders in IBM Plex while every other cell on the page uses the system sans (LOW, 644B unless the orchestrator wants it in-PR)

**Root cause (pre-existing, app-wide):**
- `packages/ui/styles/globals.css:107` declares `--font-sans: var(--font-plex)`.
- But `--font-plex` is only defined on `<body>`, through the next/font class (`app/layout.tsx:79`).
- At the root, `--font-sans` and `--font-plex` are both empty (`fontProbe.htmlFontSansVar`/`htmlFontPlexVar` = ""), so `html` and `body` inherit the preflight fallback `ui-sans-serif, system-ui, …`.
- Only elements with an explicit `font-sans` class resolve Plex.

**On this page:**
- The **18** Plex elements in `#main-content` are exactly the 6×3 Matter money spans.
- The Matter name, Hours and header cells, the Client money cells and the utilization cells all use the system sans.

**Effect:**
- The fix's code comment says money is "same typography as before the DataTable migration and as the Client Profitability table". By computed style that is not true: before the migration, money inherited the system sans.
- The difference is subtle in the screenshots (Plex against SF), and widths are fine.
- Possible remedy (not applied): an inheriting override, such as `font-[inherit]`, instead of `font-sans`. The app-wide `--font-plex`-on-body scoping is a separate Phase 90/91 typography item.

**Not a regression of D1:** it does not affect width or clipping.

### Notes carried forward
N1, N3, N4, N5 and N6 from the first OBS are unchanged. N2 is closed (R5). In the first OBS, the `matterWidthCounterfactual` probe was a no-op on this head, because there is no `font-mono` left. With 12px icons the table would be 1150 instead of 1154.

## Final probe @f14cbb32f (orchestrator, re-OBS D2b)

Fresh stack built from `f14cbb32f`, after legal-lifecycle 105 / legal-depth-ii 40 + 4 skipped / finance 7.

| Check | Result | Numbers |
|---|---|---|
| D2b money font = table font | PASS | Matter Revenue span, Client Profitability revenue cell, all name cells: `ui-sans-serif, system-ui, …`; 0 of 755 `#main-content` elements compute to Plex (`final-fix/font-probe-raw.json`) |
| D1 Matter width @1440 | PASS | table 1167px in 1086px (main ≈1172); Margin right edge 1378 ≤ container 1391; 0/6 Margin values clipped; Margin % partially cut as on main (`final-fix/matter-clip-raw.json`, `matter-card-1440-{light,dark}.png`) |
| Matter @1024 | same as main | 1167 in 670 — scrolls inside the card; page overflow 0 |
