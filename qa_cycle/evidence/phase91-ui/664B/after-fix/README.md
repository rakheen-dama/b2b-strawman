# 664B OBS after-fix (PR #1869, head `9ef3ae9c9`, D-38 sort-icon anchor)

Re-check of the one change in `9ef3ae9c9`: the F1 sort icon is anchored to its label (the link or button is `relative`, the icon is `absolute left-full ml-0.5`), and the sortable `th` keeps only `pr-5` (no longer `relative`). Baseline for comparison: `../after/` (on `7a41694fd`) and `../before/`.

**Stack:** e2e mock-auth :3001.
- The frontend was rebuilt with `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` from `worktree-epic-664B` @ `9ef3ae9c9`.
  - Image `sha256:4fefc41e4e76` (was `sha256:9eef57638835`), container created 2026-11-26T14:03:40Z. The `pnpm --filter frontend build` step ran (45.3 s) and was not cached. The container was healthy and `/` returned 200.
  - **Build check:** the served chunk `/_next/static/chunks/0e41pqbneqgdu.js` (fetched over HTTP from :3001) contains `Z="pr-5"` and `"absolute top-1/2 left-full ml-0.5 size-3.5 -translate-y-1/2"`, and the control class contains `relative inline-flex items-center rounded-sm`. No static chunk contains the old slot `top-1/2 right-1 size-3.5` or `"relative pr-5"`. The previous chunk `3j8m0wuof2mk-.js` now returns 404.
  - In `measurements.json`, every icon's class now reads `... absolute top-1/2 left-full ml-0.5 size-3.5 ...`.
- Backend :8081 (container from 12:51Z) and the data were not changed. The 664B seed (K-11 view, 60 / 60 rows) was reused; `00-seed.cjs` was not re-run.
- Captured 2026-11-26 ~14:06–14:09Z with the Playwright library (headless chromium). Settings are the same as before and after.

## Scripts: how they were run (deviation from "unedited", read this)
`before/scripts/lib.cjs` only accepts `PHASE=before|after` (`throw` on anything else), and `after/scripts/03`, `04` require `../../before/scripts/lib.cjs`. So `PHASE=after-fix` cannot run from the original locations. Without editing the originals:
- `scripts/01-capture.cjs` and `scripts/02-assemble.cjs` are **byte-identical** copies of `before/scripts/` (`diff` is empty).
- `scripts/lib.cjs` is a copy of `before/scripts/lib.cjs` with **one line changed**: the allow-list is `before|after|after-fix`. The output dir `../../$PHASE/` and raw dir `/tmp/664B-$PHASE-raw/` logic is unchanged. It resolves to `664B/after-fix/` from here.
- `scripts/03-k47-real-click.cjs` and `scripts/04-header-overlap.cjs` are copies of `after/scripts/` with **one line changed**: `require("../../before/scripts/lib.cjs")` became `require("./lib.cjs")`. This is the line that picks the output path.
  - Their hard-coded labels were left alone: `"phase": "after"` in `header-overlap.json` and `k47-real-click.json`, and the "664B after —" sheet titles. They are after-fix captures. `measurements.json`, `pager.json`, `sel-compat.json` and `console.json` carry `phase: "after-fix"`.
- `scripts/05-label-clip-check.cjs` is **new and additive** (read-only). See §2 for why it was needed.
- Commands: `PHASE=after-fix STACK="…@ 9ef3ae9c9 (image sha256:4fefc41e4e76)" node 01-capture.cjs`, then `PHASE=after-fix node 02-assemble.cjs`, `04-header-overlap.cjs`, `05-label-clip-check.cjs`, `03-k47-real-click.cjs`. All exited 0. `01` reported "tiles 43 pages 45 console 0 failures 0".

Verdicts are in `verdicts.json`, one line.

## 1. Sort icon vs its own label: PASS, the FLAG from `after/` is resolved
Source: `header-overlap.json`, `sheet-header-crops.png` (2x). 60 sort icons were measured on 7 pages: matters for Alice 1024 light / 1440 dark and Carol 1024, clients default at 1024 / 1440, the clients K-11 view, and profitability. 55 are kit icons (`DataTableColumnHeader`). 5 are the profitability client table's own inline `ArrowUpDown` icons, which are not kit icons.
- **Gap from the icon to its own label is 2 px for 55/55 kit icons.** This covers left-aligned, right-aligned money, two-line and active headers.
  - The 5 non-kit icons are at 4 px, unchanged from after.
- **Overlaps: 0/60. Icons outside their `th`: 0/60.** This includes:
  - right-aligned money headers: Unbilled, Trust, Billable Hours, Revenue, Cost, Margin, Margin %, Total Hours, Billable, Non-Billable
  - two-line clients headers: "Outstanding / fee notes", "Trust / balance", "Open / matters"
  - For content-sized and right-aligned headers, the icon's right edge is 4 px inside the `th` right edge, as in after (20 px `pr-5` = 2 + 14 + 4).
- **The gap to the next column's label is now at least 12 px.** 12 px is the minimum, the same as after. It only happens where the column is content-sized, for example REFERENCE → MATTER and TRUST → NEXT IN DIARY, which are unchanged.
- Headers that were "detached" in after (gap to own label in after → after-fix; gap to next label in after → after-fix):

| page | header | own label | next label |
|---|---|---|---|
| matters (Alice 1024 / 1440) | MATTER | 162 → **2** | 12 → 172 (CLIENT) |
| matters (Carol 1024) | MATTER | 125 → **2** | 12 → 135 |
| matters | CLIENT | 103 → **2** | 12 → 113 (STATUS / FEE EARNER) |
| matters 1440 | FEE EARNER | 34 → **2** | 12 → 44 (STATUS) |
| clients 1024 | CLIENT (active ↑) | 130 → **2** | 12 → 140 (LIFECYCLE) |
| clients 1440 | CLIENT (active ↑) | 223 → **2** | 12 → 233 (LIFECYCLE) |
| clients K-11 view 1024 | CLIENT (active ↑) | 159 → **2** | 12 → 169 |
| clients | LIFECYCLE / FICA / CREATED | 11 / 47–48 / 16 → **2** | 12–23 → 21 / 57–69 / 26–57 |
| profitability | Name / Utilization % | 58 / 93 → **2** | 22 → 78 / – |
| profitability | Matter / Client | 241 / 191 → **2** | 12 → 251 / 201 |

- In the crops, the active arrow on `/clients` reads "CLIENT ↑", not "↑ LIFECYCLE".

## 2. Clipped labels: PASS (0 real). `04`'s `clipped` flag is now a false positive (measurement change, not a regression)
- `04-header-overlap` reports **55 "clippedLabels"** (after: 0). These are exactly the 55 kit headers.
  - Its test is `control.scrollWidth > control.clientWidth + 1`. With D-38 the control is `relative` and the icon sits at `left-full`, so the icon overflows the control by design and counts in its `scrollWidth`.
- `05-label-clip-check.cjs` re-measured the same 7 pages (`label-clip-check.json`):
  - For all 55 kit controls, `scrollWidth − clientWidth` is **16 with the icon and 0 with the icon hidden**. The icon overflows the control by **16 px** (2 px gap + 14 px icon).
  - The label text's right edge is never past the control's right edge.
  - `overflow-x` is `visible` on every control and every `th`, so nothing cuts the icon or the label.
  - **labelClipped: 0/60.**
- The crops show every label in full. The `UNBILL…` / `BA…` cut-offs at the right edge of some crops are where the table scrolls sideways in its frame, the same as after.

## 3. Sticky clients header: PASS, unchanged
- On clients, the sortable `th` (CLIENT) and the plain `th` (ACTIONS) are both `position: sticky` and at the same top after a page scroll:
  - 1024: 397 → 111 (scrolled 286)
  - 1440: 321 → 243 (scrolled 78)
  - These are identical to after.
- On matters, sortable `th`s are now `static` (after: `relative`). This is expected, because `relative` moved off the `th`. Sortable and Actions move together: 321 → 128 at a 193 px scroll, identical to after.

## 4. `/matters` table width vs container: PASS, identical to after
| persona | 1024 light | 1024 dark | 1440 light | 1440 dark |
|---|---|---|---|---|
| Alice before | 1054 / 710 (344) | 1054 / 718 (336) | 1193 / 1126 (67) | 1193 / 1134 (59) |
| Alice after | 1036 / 710 (326) | 1036 / 718 (318) | 1175 / 1126 (49) | 1175 / 1134 (41) |
| **Alice after-fix** | **1036 / 710 (326)** | **1036 / 718 (318)** | **1175 / 1126 (49)** | **1175 / 1134 (41)** |
| Carol before | 925 / 710 (215) | 925 / 718 (207) | 1126 / 1126 (0) | 1134 / 1134 (0) |
| Carol after | 895 / 710 (185) | 895 / 718 (177) | 1126 / 1126 (0) | 1134 / 1134 (0) |
| **Carol after-fix** | **895 / 710 (185)** | **895 / 718 (177)** | **1126 / 1126 (0)** | **1134 / 1134 (0)** |

- Every `th` width is identical to after in all 8 cases.
- **Header-width shift on sort: 0 px** in 8/8 cases (clicked REFERENCE, `widthDeltas: []`), and table width is unchanged on sort.
- Chevron at rest: visible 8/8, opacity 0.4, `display: block`, 14 px, `absolute`. The active icon has opacity 1.
- Spot-checks match after exactly: clients 710/710, profitability utilization 662/662, matter profitability 1137 in 662, client table 754, fund ledger 754/662.

## 5. Everything else: identical to after
- A deep diff of `measurements.json` against after, ignoring `capturedAt`, `stack`, `phase` and icon class strings, gives **1 difference**: the K-11 verdict string's phase label `(after)` → `(after-fix)`. `pager.json`, `sel-compat.json` and `console.json` have **0 differences**.
- **K-11: PASS, unchanged.**
  - The saved-view Client `th` is 232 at 1024 / 1440 in light and dark.
  - The saved table is 1270 in 710 and scrolls inside its frame. 3/10 names are truncated.
  - The default view Client `th` is unchanged: 203 / 211 at 1024 and 296 / 304 at 1440 (light / dark).
- **K-47: PASS, unchanged.** `03-k47-real-click` passes **12/12**, with 0 differences from after's JSON:
  - wheel +400
  - Next → Step 2 → Step 3
  - Close X works
  - dialog geometry 38 / 730 (768) and 45 / 855 (900)
- **Other-creditors pagers: PASS, unchanged.** `pager.json` has 0 differences.
- **aria-sort: PASS, unchanged.**
  - `/matters` has 10 `th[aria-sort]`: 9 "none" and 1 "ascending".
  - `/clients` has 8: 7 "none" and 1 "ascending".
- **Console: clean.** 0 errors, 0 warnings, 0 hydration messages over:
  - 45 page loads in `01`
  - 7 in `04`
  - 7 in `05`
  - 12 in `03`

## Regressions vs `after/`
None observed in the product. The only new red number is `04`'s `clippedLabels` (0 → 55). §2 shows it is an artefact of the probe's `scrollWidth` heuristic, which the intended icon overflow now trips. It is not label clipping.
