# 667B OBS after-fix @ `a5e0197d5` (PR #1879)

**Verdict: PASS.** The three OBS-after findings were re-measured on a real build of the new head.
- **F-667B-A1 (MEDIUM): fixed.** At 1024 the open-statements table fits (662 / 662, dark 670 / 670) and Discard is fully in view, 8 px clear.
- **F-667B-A3: fixed as specified.** Completed is hidden only at 1024. It shows and fits at 1280, 1366 and 1440, on page 1 and page 2. List locked stays in view at every width.
- **F-667B-A2: improved to the accepted residual.** The sticky column is 97 px (was 135). Balance is 38 px clear (dark 46). Status is still partly covered at scroll 0: Active 36 px (dark 28), the others 62 px (dark 54).

Nothing else changed apart from what the third investment explains. That covers SEL-COMPAT, the shell, console, gating, the monthly list, pager hrefs and interactions. **No new defect.**

## Run and environment

**When:** 2026-11-27, 06:10–06:20Z.

**Stack:** the same e2e mock-auth stack and data as `after/`. There was no re-seed, no reset and no SQL.

**Frontend:** rebuilt from the PR worktree (`worktree-epic-667B`, HEAD `a5e0197d5`) with `bash compose/scripts/e2e-rebuild.sh frontend`.
- Fresh `next build`: "Compiled successfully".
- Image `docteams-e2e-frontend` **`sha256:7e3cc7a6f02b`**, created 06:11:22Z. The container started 06:11:25Z and is healthy. Compose working dir: `worktree-epic-667B/compose`.
- **Proof it is the fix build:**
  - The served CSS chunk `3w9rs8gxbkpeo.css` contains `@container (min-width:56rem){….\@min-\[56rem\]\:table-cell{display:table-cell}` (F-667B-A3).
  - It also contains `@container not (min-width:56rem){.\@max-\[56rem\]\:\[\&_button\]\:whitespace-normal button{white-space:normal}…` (F-667B-A2).
  - The Completed `th` is `table-cell` at 1280, 1366 and 1440. Before the fix it was `display:none` at 1280 and 1366.
- This time localstack was not re-created. Backend, postgres, mock-idp and mailpit were untouched.

**Browser:** the Playwright library from the main checkout's `frontend/node_modules`, Node 20.11.1. The MCP was not used.

**Data delta vs `before/`:** the investment `28a448ce…` placed by OBS-after (R 1 000, maturity 2026-12-15, ACTIVE and awaiting approval, testid `maturity-alert`). The register now has 3 rows. `03-maturity.cjs` found it by its notes and **reused** it (`maturity.json` › `write = {created:false, reused:true}`). This run made **no write**.

Investments are compared **like for like** with `after/maturity.json`, which was measured after that write (3 rows). `after/layout.json` had only 2 rows.

## Scripts

**Unedited replay** (`before/scripts/`): `PHASE=after-fix node 01-capture.cjs && PHASE=after-fix STACK=… node 02-assemble.cjs` (`lib.cjs` accepts `after-fix`).
- 60 page loads and 16 interactions.
- Outputs: `measurements.json`, `sel-compat.json`, `console.json`, `discover.json`, 13 sheets, and the crops `crop-k91-investments-1024-light.png` and `crop-recon-p2-1024-light.png`.
- The K-36 and K-76 crops were dropped under EVIDENCE-BUDGET: they were unchanged and not part of this fix.

**Unedited, from `after/scripts/` with `PHASE=after-fix`:**
- `02-crops.cjs` → `crop-open-statements-1024-{light,dark}.png`.
- `03-maturity.cjs` → `maturity.json` and `crop-maturity-1024-dark.png`. The light crop was dropped because it duplicates the K-91 crop.

**New, in `after-fix/scripts/`:**
- `01-layout.cjs`: a copy of `after/scripts/01-layout.cjs`. Only the plan changed: it adds 1280 light/dark, 1366, page 1 at every width and investments at 1280/1366. Viewports are 1280×800 and 1366×768. Output: `layout.json`.
- `02-crops-fix.cjs` → `crop-recon-p2-1280-light.png` and `crop-recon-p1-1024-light.png`.
- `03-checks.cjs` → `checks.json`. It records:
  - the open-statements Period line boxes;
  - Discard scrolled into view, with a hit-test and a Playwright trial click (no click is performed);
  - the monthly list's `<table>`, its tfoot, the after-lock row and the change sentence.

`03-checks.cjs` was corrected once before its recorded run. Its first version read the button box synchronously after an in-page smooth `scrollIntoView`, which gave a pre-scroll box and `hit:false`. It now scrolls through Playwright first.

## Checks: before → after → after-fix

Numbers are Alice, light, unless stated. "a / b" means table scrollWidth / scroller clientWidth.

### (a) Open statements, F-667B-A1

| Check | Before `427d6826c` | After `6a5d7ef2e` | After-fix `a5e0197d5` | Result |
|---|---|---|---|---|
| Table at 1024, light | 662 / 662 | **701 / 662 (+39)** | **662 / 662** | PASS |
| Table at 1024, dark | 670 / 670 | 701 / 670 | **670 / 670** | PASS |
| Period cell at 1024 | 2 lines | 202 px, 1 line | 126 px, `white-space: normal`. The lines are "02 Nov 2026 –" / "03 Nov 2026", so the break falls between the dates and never inside one | PASS |
| Discard at 1024 | in view | 31 px (dark 23 px) out of view, reads "Disc" | x 865–959 in a scroller at 305–967: **8 px clear** (dark 873–967 in 305–975, 8 px). Hit-test on the button: true. Trial click OK | PASS |
| Row height at 1024 | 76 | 76 | 76 | = |
| 1280 / 1366 / 1440 | fits | fits | 918/918, 1004/1004, 1078/1078. Discard in view, rows 56/48/48 | PASS |
| Carol at 1024 (no Actions) | 662 / 662 | 662 / 662 | 662 / 662 | PASS |

### (b) Reconciliation history, F-667B-1 and F-667B-A3

All rows are **page 1 / page 2**.

| Check | Before | After | After-fix | Result |
|---|---|---|---|---|
| 1024, fit | 678 / 662, 729 / 662 | 662 / 662, 662 / 662 | **662 / 662, 662 / 662** (dark 670 / 670) | PASS |
| 1024, Completed | shown, 2 lines | hidden | **hidden** (`display: none`) | PASS (by design) |
| 1024, List locked (p2) | out of view | x 855–959 in 305–967, 8 px clear | x 855–959, **8 px clear**, hit OK (dark 8 px) | PASS |
| 1024, rows and Period | 85 px, Period 3 lines | 57, 2 lines | **57, Period 2 lines** ("06 Nov" / "2026"). Headers wrap to at most 2 lines | PASS |
| 1280, fit | — | 918 / 918 (p2 only) | **918 / 918, 918 / 918** (dark 926 / 926) | PASS |
| 1280, Completed | — | **hidden** | **shown**: `table-cell`, 101 / 105 px. The p2 cell reads "27 Nov 2026" on the 31 Oct row (`crop-recon-p2-1280-light.png`) | PASS |
| 1280, List locked (p2) | — | 29 px clear | **13 px clear** (dark 14), hit OK | PASS |
| 1366, fit and Completed | — | 1004 / 1004, Completed hidden | **1004 / 1004** on both pages. Completed shown (110 / 115 px). List locked **24 px** clear | PASS |
| 1440, fit and Completed | 1078 / 1078 | 1078 / 1078, shown 123 px | **1078 / 1078** on both pages (dark 1086 / 1086). Completed 119 / 123 px. List locked **34 px** clear | PASS |
| Rows from 1280 up | 47 | 39 | **39**, every column on 1 line | PASS |
| Carol, 1024 p1/p2 and 1280 p2 | — | — | 662/662, 662/662 and 918/918. Completed hidden at 1024 and shown at 1280. List locked 8 / 13 px clear | PASS |

### (c) Investments, K-91 and F-667B-A2

Rows are listed in register order: maturity-alert (Awaiting approval) | Khumalo Active | Ndlovu Withdrawn.

| Check | After (from `after/maturity.json` with 3 rows, or `after/layout.json` with 2 rows) | After-fix | Result |
|---|---|---|---|
| 1024, table | 771 / 662 (2 rows) | **732 / 662** (dark 732 / 670) | = (sticky) |
| 1024, sticky Actions width | 135 px (x 832–967 page; 527–662 scroller) | **97 px** (565–662; dark 573–670) | PASS |
| 1024, buttons | single-line, in view | wrap to 2 lines ("Record / interest", "Interest / entries"). All 3 buttons are in view (573–662) | PASS |
| 1024, Balance amount → sticky gap | **0 px** (dark 8) | **38 px** (dark 46) | PASS |
| 1024, Status covered at scroll 0 | 58/58, 84/84, 84/84 (fully) | **62 of 84, 36 of 58, 62 of 84 px** (dark 54, 28, 54). Centre hit-test still lands on the sticky cell (`crop-k91-investments-1024-light.png`: "Av", "Ac", "W"; header "STA") | residual accepted (D-81 class), as predicted |
| 1024, Status at max scroll (70 px; dark 62) | revealed at 109 px (dark 101) | **fully clear** (covered 0, hit on chip), Balance gap 108 | PASS |
| 1024, row heights | 149, 149, 149 (3 rows, maturity.json row tops 366/514/663) | **149, 165, 149** (tops 366/514/679). The Active row grows by 16 px from the wrapped buttons | as predicted |
| 1280 / 1440 | 1440: sticky x 1146–1383, row tops 366/482/599 (maturity.json, 3 rows) | 1440: **identical** (1146–1383; 366/482/599; dark 1151–1391, 358/474/591). Layout: 1078 / 1078, sticky 237 px, Balance gap 126, Status covered 0. 1280: 918 / 918 (dark 926), sticky 184 px, buttons single-line (127 px, the same as 1440), gap 117, Status covered 0, rows 117/129/133 (dark 117/129/117) | PASS (the 1440 comparison is like for like; there is no earlier 1280 baseline, and the `@max-[56rem]` rule cannot apply at a 918 px frame) |
| 1366 | — | 1004 / 1004, sticky 213 px, gap 122, Status clear, rows 117/129/117 | PASS |
| Maturity tint, light | row #fffbeb, sticky #fffbeb | row **#fffbeb**, sticky **#fffbeb** (CSS composite = screenshot pixels) | PASS |
| Maturity tint, dark | row #221813 (pixel #231814), sticky #241917 | **identical** (#221813 / #231814 / #241917) | PASS |
| Untinted rows | sticky = row | sticky = row (#fdfeff; dark #14171c) | PASS |
| Carol at 1024 | 662 / 662, no sticky | 662 / 662 (dark 670 / 670), no Actions column, Status clear, rows 149 | PASS |

### (d)–(i) Selectors, shell, console, gating, monthly list, pager

| Check | After | After-fix | Result |
|---|---|---|---|
| (d) SEL-COMPAT vs `before/` | all equal except "Investment Register" 1 → 2 | **all equal except:** "Investment Register" 1 → 2 (known caption); **`maturity-alert` 0 → 1** and **`main tbody tr` 2 → 3** on `/investments` (Alice and Carol, 1024 and 1440). Both are explained by the extra investment. `investment-row-*` stays 2 | PASS (explained) |
| (d) testid diff vs `after/` | — | only the extra investment's cells: `investment-client-cell` / `investment-account-cell` 2 → 3, `maturity-alert` +1, and `alerts-section` +1 on the trust landing. No testid lost | PASS (explained) |
| (d) Other diffs vs `after/` | — | "2 → 3 investments found". On the landing, the newest transaction is the awaiting-approval INV-28A448CE deposit. The Investment Register run has rowCount 3 → 4 (11 headers unchanged, LPFF Rate 0). The open-statements width (the fix). Every other table's headers, widths and cells are identical | PASS (explained) |
| (e) Shell | 60/60 | **60/60:** `main#main-content` 1, `<main>` 1, h1 in main 1, h1 on page 1, HTTP 200, document h-overflow 0. h1 texts identical | PASS |
| (f) Console | 0 / 0 / 0 / 0 | **0 messages:** 0 errors, 0 warnings, 0 hydration (60 loads) | PASS |
| (g) Carol gating | no New Reconciliation, Place Investment, Actions, Discard or report cards; FV notice 1 | the same. New Reconciliation, Place Investment and Discard are Alice-only. Investments has 7 columns. Report cards 0 + FV notice 1 | PASS |
| (h) Monthly list | fits; tfoot "Total trust creditors R 101 630,00"; after-lock row; sentence | `monthly-list-creditors` **is the `<table>`** (tagName TABLE), 6 body rows, tfoot **"Total trust creditors R 101 630,00"**. The after-lock row is "28 Oct 2026 … 667B-AFTER-LOCK R 500,00". The sentence reads "…are now R 102 130,00, a change of R 500,00 from the locked list". 662 / 662 (dark 670) at 1024, 1078 at 1440. Rows 45/37/45/37/65/65 at 1024, the same as after. Carol: the same | PASS |
| (i) Pager | p1 Next `?page=1`; p2 Previous `?page=0`, Next disabled `<button>` | **identical** (Alice and Carol). The 16 interactions are identical apart from the register row count | PASS |
| Unchanged pre-existing | F-667B-6 +14 px; F-667B-10 777/662; F-667B-A4 "Page 10 of 2" → `?page=8` | the same: 726 in 712; 777 / 662; "Page 10 of 2", Previous `?page=8` | unchanged (out of scope) |
| K-36 / K-76 | 5.41 / 5.46 / 6.76 | the same. The new tinted row's "5% (statutory)" is #00776a on #fffbeb, 5.26 (dark 9.32) | PASS |

## Evidence

- JSON from the replay: `measurements.json`, `sel-compat.json`, `console.json`, `discover.json`.
- JSON from the probes: `layout.json`, `maturity.json`, `checks.json`.
- 13 sheets.
- 7 crops:
  - open statements at 1024, light and dark;
  - reconciliation history at 1024 (page 1 and page 2) and at 1280 (page 2);
  - investments at 1024, light (and the dark maturity crop).
- Raw tiles are in `/tmp/667B-after-fix-raw/` and are not committed.
