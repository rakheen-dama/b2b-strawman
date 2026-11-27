# 667A OBS-after-fix — PR #1878 @ `c8cbb4d53` (fix for F-667A-A4 + F-667A-A1)

**Verdict: PASS, with one residual for the orchestrator.**
- **F-667A-A4 (Amount under the sticky Actions):** fixed. At scrollLeft 0, Amount is clear of the Actions cell on every row, for every persona, view and theme measured.
- **F-667A-A1 (`<dd>` without `<dt>`):** fixed. axe definition-list and dlitem go from 1 + 9 to **0**.
- **Regression sweep:** no regressions against `a5259de0b`.
- **Residual R-A4-status:** the *Status* half of the original A4 is still there at 1024. The sticky Actions cell covers most of the Status badge and the "1 of 2 approvals" line (numbers below). The fix targeted Amount only, and OBS-after's P2 prototype predicted this. It is reachable by a 91–147 px scroll. Whether that is acceptable is the orchestrator's call. It is not a new defect.

## Stack and run

- **Frontend:** `e2e-frontend`, rebuilt only from the PR worktree at **`c8cbb4d53`** with `compose/scripts/e2e-rebuild.sh frontend`.
  - Fresh `pnpm --filter frontend build`, not cached.
  - Image `docteams-e2e-frontend` **`sha256:702f1dde0f12`**, created **2026-11-27T04:25:09Z**. Healthy.
  - The built chunks contain `ledger-total-*` with dt/dd and the `flex-wrap` approval actions.
- **Unchanged:** backend, postgres and mock-idp were not touched (created 03:09:10Z). localstack was not recreated this time.
- **Data:** the before seed, not re-seeded. No Approve, Reject or Reverse clicks. AWAIT-1 still shows "1 of 2 approvals" (`sheet-approval-rows.png`).
- **Capture:** `before/scripts/01-capture.cjs` + `02-assemble.cjs`, run **unedited** with `PHASE=after-fix`. `lib.cjs` maps that phase to `../../after-fix/`. 55 page loads, same targets.
- **Node:** 20.11.1, Playwright from the main checkout's `frontend/node_modules`, axe-core 4.11.3.
- **`scripts/`:**
  - `03-compare.cjs` and `04-after-checks.cjs` are copies of `after/scripts/`. Only the phase/output path changed (`diff` = the PHASE line, the output dir name and header comments).
  - New, all read-only:
    - `07-a4-a1.cjs` → `a4-a1.json`: the A4 gap table and the A1 tiles and axe.
    - `08-diff-after.cjs` (prints): the after → after-fix regression diff.
    - `09-status-residual.cjs` → `status-residual.json`.
  - The 05/06 prototypes were not re-run: they injected CSS against the old build.
- **PNGs (16):** 12 contact sheets, 2 K-10 crops (parity), `crop-F-667A-A4-1024-await-after-fix.png`, `crop-F-667A-A1-tiles-1440-after-fix.png`. The three crops 04 writes (scroll-start, scroll-end, A2 pager) were pruned. Their numbers are in `checks.json`.

## F-667A-A4: Amount vs the sticky Actions at scrollLeft 0 (`a4-a1.json`)

Gap = Actions-cell left minus Amount-cell right (px, minimum over the rows). The table and scroller columns are px.

| Persona / view | 1024 light: gap · table/scroller · Actions w | 1024 dark: gap · table/scroller | 1440 light: gap · table/scroller | 1440 dark: gap · table/scroller |
|---|---|---|---|---|
| Alice, AWAITING (2 rows) | **+18** · 809/662 · 171 | **+26** · 809/670 | +193 · 1078/1078 | +194 · 1086/1086 |
| Alice, unfiltered p1 (20) | **+18** · 809/662 · 171 | **+26** · 809/670 | +165 · 1078/1078 | +165 · 1086/1086 |
| Alice, unfiltered p2 (20) | **+68** · 698/662 · 113 | **+76** · 698/670 | +116 · 1078/1078 | +117 · 1086/1086 |
| Bob, AWAITING (2) | **+74** · 753/662 · 115 | **+82** · 753/670 | +205 · 1078/1078 | +207 · 1086/1086 |
| Bob, p1 (20) | **+74** · 753/662 · 115 | **+82** · 753/670 | +165 · 1078/1078 | +165 · 1086/1086 |
| Carol, AWAITING / p1 | +115 · 712/662 · 74 (empty) | +123 · 712/670 | +242 / +165 · fits | +244 / +166 · fits |

**Every row, every page:**
- Amount is covered on **0** rows. The hit-test centre of Amount shows Amount on 2/2 await rows (Alice and Bob).
- Actions is `position: sticky` and sits inside the scroller.
- **0** buttons are clipped and **0** Actions cells overflow.
- At 1024 the approval buttons wrap to 2 lines (Approve / Approved by you above Reject).
- The fixer's prototype numbers are reproduced: +18/809/171, +74/753/115, p1 +18, p2 +67 → **68**.

**Before (`fe99599e8`) → after (`a5259de0b`) → after-fix, Alice AWAITING at 1024 light:**
- Table: 749 → 948 → **809** (scroller 662).
- Actions width: 247 → 263 → **171**.
- Amount: visible → covered 2/2 → **visible 2/2, +18 px**.
- Bob: n/a → 892 table, covered 2/2 → **753, +74 px**.

**Row heights at 1024:**
- Await rows: 59/49 → **89** (two button lines).
- p2: 49 everywhere. p1: 49..397 (397 is the K-10 token row, see O-4).
- At 1440, await rows are 49–59 with buttons on 1 line.

**Carol:**
- The Actions column has **0/20 and 0/2 non-empty cells**: no buttons, reverse-button 0. It is 74 px wide, as before the PR (50 px then, also empty).
- Amount is clear (+115).

**Residual R-A4-status** (`status-residual.json`): px of the Status badge (149 px wide on AWAITING) and of the "1 of 2 approvals" line under the Actions cell at scrollLeft 0, 1024.

| | light | dark |
|---|---|---|
| Alice await | 139 of 149 (2/2 rows) | 131 |
| Bob await | 83 of 149 | 75 |
| Carol await | 42 of 149 (empty sticky cell) | 34 |
| Alice p1 | 20/20 rows partly covered (RECORDED 78 of 88) | 20/20 |
| Bob p1 | 20/20 partly covered (22 of 88) | 20/20 |
| Carol p1 | 18/20 clear, 2 partly | same |

- **Before the PR,** Status was fully visible on the await view at 1024 (Status at x 337..502; Actions not sticky).
- **At `a5259de0b`,** Status was covered on every row and on Carol's view.
- **Now** it is better but not clear: an approver at 1024 sees Amount beside Approve, but not the "1 of 2 approvals" line, without scrolling 147 px (Alice) or 91 px (Bob).
- **At 1440** it is fully clear (gap Status → Actions ≥ 0 on all rows and personas).

## F-667A-A1: ledger tiles markup

- **Markup:** `ledger-totals-breakdown` is a `<dl>` (×1, inside `client-summary`) whose children are all `div[dt+dd]`: Khumalo 9, Ndlovu 8, Acme 5.
- **Each tile:** every `ledger-total-*` testid appears **once**, with exactly **1 dt + 1 dd**. The dt holds the label ("Deposits", "General balance", "Invested (section 86(4))", …).
- **dd text vs before:** identical on 16/16 page × variant probes (testid + dd). Examples: balance R 33 963,00, invested R 20 095,00, held R 54 058,00, Ndlovu balance R 69 380,00. Ndlovu still has no invested/held tile.
- **axe (`#main-content`):**
  - Light: hist 1024 / 1440 = `definition-list` + `dlitem` **9 → 0**.
  - 07 across Khumalo and Ndlovu × 4 variants: 0 list/listitem/definition-list/dlitem everywhere.
  - Only remaining: `scrollable-region-focusable` ×1 at 1024 light. It is pre-existing (OBS-after), and only appears when the history table overflows.
  - tx and cl: 0.
- **Tile layout:**
  - Overflow 0 and spill 0 at 1024 (169/171 px) and 1440 (178/179 px), light and dark.
  - Khumalo heights equal to OBS-after: 1024 = 76/96, 1440 = 96. The 1440 crop is pixel-identical in layout to the as-built one.
  - Ndlovu 1024 light: row 1 is 116 px tall. The longest label, "Returned from section 86(4) investments", wraps to 3 lines in 169 px. No spill.

## Regression sweep (`08-diff-after.cjs`: after `a5259de0b` → after-fix; `03-compare.cjs`: before → after-fix)

| Check | Result |
|---|---|
| SEL-COMPAT vs after | **0 diffs**. vs `before/sel-compat.json`: only the known "Generate Statement" card title 1→0 (5 hist probes). `recordTxMenu` same |
| Console (55 capture loads + 04 + 07 + 09) | **0** errors, warnings, page errors or hydration |
| Shell: `main#main-content` ×1, `<main>` ×1, h1 in main ×1, h1 ×1, no doc h-overflow | **55/55**. The 12 empty/out-of-range states in `checks.json › empty` are identical to after |
| URLs, pager, status/type/nonZero hrefs, buttons, headings | page-field diffs vs after: only `ledgerTotals` dt text (now filled) on 16 hist probes. Interactions **19/19 identical to after**, so still 18/19 vs before plus the known F-667A-A2. API identical |
| Gating Alice/Bob/Carol | approve/reject text, disabled and title identical. Alice: "Approved by you" [disabled] + Reject, Approve + Reject. Bob: Approve + Reject ×2. Carol: none. reverse-button 16/20 for Alice/Bob, 0 for Carol (sel-compat) |
| K-10 1440 | tx p1 **1078/1078** (dark 1086/1086). Reference 312 px (was 274), Actions 225 (was 263). Spaced 5 lines / row 97 (was 6/117), token 7 / 137 (was 8/157) |
| K-10 1024 | Reference wraps at the 6rem floor (96 px). Actions sticky and inside, 20/20. Table 809/662 (was 948), max scroll 147 (was 286); Type/Amount/Status inside at max scroll. Token 20 lines / row 397, spaced 17 / 337 (see O-4) |
| K-90 | Date 1 line, 0 wrapped, all tables. Type floor **160 px** on tx/hist at 1024, badge inside 0 outside. History Running Balance: 1440 fits (936..1078, 20/20). 1024 light 669/662: reachable at max scroll 7 (was 55). 1024 dark 670/670 fits. hist-p2 694/662 (was 742) |
| K-89 headers / row sums | `checks.json › k89` identical to after (the columns, and F-667A-A3 unchanged) |
| K-35 / K-40 contrast | identical to after: client links 5.41:1 light / 9.63:1 dark; deposits 7.27:1 / 11.85:1 |
| Client-ledgers row links (basePath change) | hrefs and interactions identical to before and after |
| Dark sheets | sticky Actions bg is the dark card colour. No regressions seen |

## New defects

**None** (no F-667A-B*).

**Observations, not defects:**
- **O-4:** with the 6rem Reference floor, the two K-10 seed references are tall at 1024:
  - Token: 20 lines, row 397 px (after: 14 lines / 277; before the PR: 2 lines in a 1 311 px column).
  - Spaced: 17 lines / 337 px (pre-PR spaced-only control: 15 / 325).
  - Normal references are unaffected (p2 rows 49 px), and at 1440 the rows got shorter.
- **O-5:** history at 1024 light overflows by 7 px (669/662), which is what triggers the pre-existing `scrollable-region-focusable`. Dark fits exactly (670/670).

**Still open, unchanged:** F-667A-A2 (out-of-range pager "Page 10 of 3"), F-667A-A3 (K-89 sums, product decision), O-2, and the pre-existing items listed in `../after/README.md`.
