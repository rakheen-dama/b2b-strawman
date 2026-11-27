# 666B OBS after-fix: fee-note detail `/invoices/[id]` @ `b8a2e2032` (fix `db387dbc4`)

- **When:** 2026-11-27, 02:51Z to 02:58Z. Rebuild 02:51–02:53Z; `01b-capture alice` + `carol` ~02:53–02:55Z; `21-afterfix-lines` ~02:55Z (includes the R 50 payment); `22-line-counts` ~02:56Z; `23-assemble-afterfix` 02:57Z.
- **Stack:** the same e2e mock-auth stack and data as OBS-before / OBS-after (frontend :3001, backend :8081, mock IDP :8090), org `e2e-test-org`. Only the frontend was rebuilt. No e2e-down / up / reseed.
- **Browser:** Playwright library (headless Chromium from the main checkout's `frontend/node_modules`), Node 20.11.1. DPR 1, reduced motion, timezone Africa/Johannesburg, theme via localStorage `kazi-theme`. Same as OBS-after.
- **Writes (UI only):** one payment on INV-0010, R 50,00 EFT, ref `666B-OBS-AFTERFIX`. On the draft, Add Line was opened and **cancelled** four times (once per variant). The draft was never saved: API `updatedAt` is still `01:40:57Z`, total 3450, tax 450 (F-666B-B1).
- **Raw files:** `/tmp/666B-afterfix-raw/` (PNGs, run JSON, rebuild log), outside the repo.

## Stack observed (build source)

- `cd worktree-epic-666B && SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` → exit 0 (`/tmp/666B-afterfix-raw/rebuild.log`).
- **`e2e-frontend`:** created **`2026-11-27T02:52:50Z`** from image `docteams-e2e-frontend` built **`02:52:49Z`**. Compose label `working_dir` = **`/Users/…/worktree-epic-666B/compose`**.
- **Source:** worktree HEAD = **`b8a2e2032218d1d…`**. `git status --short -- frontend` was empty; only ignored build outputs exist (`.next/`, `next-env.d.ts`, `tsconfig.tsbuildinfo`, `node_modules/`). So the frontend observed is `b8a2e2032`.
- **The fix is live:** line-table header padding measures 12 px (`px-3`), and TAX is ≥ 128 px wide (`min-w-32`).
- **Untouched:** `e2e-backend`, `e2e-postgres`, `e2e-mock-idp` and `e2e-mailpit` were created at 01:40:27Z. `e2e-localstack` was created at 02:23:55Z (the OBS-after rebuild) and was not recreated this time.

## Scripts

- Copied from `after/scripts/` (via `keep/666B-obs-after`).
- **Changed copy:** `lib.cjs`. The only change: `PHASE=after-fix` → raw dir `/tmp/666B-afterfix-raw`.
- **Re-run as-is with `PHASE=after-fix`:** `01b-capture.cjs` (alice, carol). It gave the hero, counts, tables, empties and console checks. `setup-02b.json` was copied into the raw dir.
- **New:**
  - `21-afterfix-lines.cjs`: line tables × 4 notes × 4 variants, draft Add Line and keyboard focus, the R 50 payment with a sentinel.
  - `22-line-counts.cjs`: read-only text-node line counts and a search for section-header rows. It exists because 21's whole-cell Range count adds one line to Description (the flex `<span>` box top). 21's caption counts were also empty, because the uppercase header keys were missed.
  - `23-assemble-afterfix.cjs`: builds the JSON files and the sheets. Its `lines__` captions are rebuilt from 22. That caption patch was added and the script re-run before commit; outputs are regenerated.
- Not re-run: `00/02/02b/03/05/08/11/12/13/14` (setup and lifecycle data already exist; 11/14 are replaced by 21).

## 1. Line-items table (F-666B-A1 / A2): table width / scroller clientWidth (px)

All 16 cases: `scrollWidth == clientWidth`, no header cut, no header clipped inside, every header on 1 line, document h-overflow 0.

| Fee note | 1024 light | 1024 dark | 1440 light | 1440 dark | Description lines @1024 / 1440 | Tax lines @1024 / 1440 |
|---|---|---|---|---|---|---|
| DRAFT de6ac48f (with ACTIONS) | **710 / 710** (was 767 / 710) | **718 / 718** (was 767 / 718) | 1126 / 1126 | 1134 / 1134 | 3 / 1 | **2** / 1 (was 4) |
| APPROVED INV-0008 | 710 / 710 | 718 / 718 | 1126 / 1126 | 1134 / 1134 | 2 / 1 | 2 / 1 |
| PARTIALLY_PAID INV-0010 | 710 / 710 | 718 / 718 | 1126 / 1126 | 1134 / 1134 | 3 / 1 | 2 / 1 |
| INV-0009 (now PARTIALLY_PAID) | 710 / 710 | 718 / 718 | 1126 / 1126 | 1134 / 1134 | 3 / 1 | 2 / 1 |

- **Draft columns @1024 light:** DESCRIPTION 165, MATTER 86, QTY 51, RATE 97, AMOUNT 98, TAX 132, **ACTIONS 82 — fully visible**. Row Edit and Delete are inside the visible scroller at scrollLeft 0, 2/2 at every variant.
- **Line counts:** Description is ≤ 3 lines at 1024 everywhere. That equals the pre-PR draft (3). INV-0010 and INV-0009 are 3 lines; OBS-after counted them as 2 under a wider Description. Tax is 2 lines at 1024 (was 4). Matter is 2 lines at 1024.
- **Draft Add Line:** placeholder "Line item description" 0 → 1 → (Cancel) 0 at all 4 variants. The form renders below the table, outside the cropped section, so the sheet row shows the section only.
- **Keyboard focus on the draft row Edit (Tab from the h1):**
  - Every variant: in the scroller, **scrollLeft 0**, 40 px of room to the scroller's right edge. The focus ring is a native `outline auto 1px` and is not clipped.
  - The next Tab (row Delete) is also in the scroller, scrollLeft 0.
  - OBS-after had Edit partly clipped and Delete scrolling the table by 57 px.
- **Section-header rows:** **not observable**. No fee note on this stack (26) has lines of more than one type, so no `section-header-*` row renders (`measurements.json → sectionHeaderCandidates: []`). In code, the header cell and the `<th>` both use `px-3` after `db387dbc4`.

## 2. Re-confirmation on the rebuilt head (01b-capture, 8 notes × 4 variants)

- **Status and headings:** HTTP 200 everywhere. h1 in main 1, h1 page-wide 1. Document h-overflow 0 everywhere. lowContrast 0 at 1024 light.
- **Tiles vs the totals block:**
  - **Total** tile = totals Total on all 8 notes × 4 variants.
  - Amount paid and Balance due are equal on PARTIALLY_PAID (INV-0010, INV-0009) and PAID.
  - On SENT INV-0001 and PS-1001 the totals block has no paid / balance rows. There the tiles show R 0,00 and Balance = Total, the same as OBS-after.
- **Tile values:** draft R 3 450,00; INV-0008 R 1 437,50 · 27 Dec 2026; INV-0009 R 1 875,00 · R 600,00 · R 1 275,00 · 1 Nov 2026; INV-0001 R 8 625,00; PS-1001 R 4 600,00 · 14 Aug 2026; INV-0010 R 2 875,00 · R 1 000,00 · R 1 875,00; INV-0011 R 718,75 · R 718,75 · R 0,00; INV-0012 R 958,24.
- **Due tile = the header "Due:" date** on all 7 notes that have a Due tile, × 4 variants (`variants.*.dueTileEqualsHeaderDue`). This is the `formatDateInSA` change.
- **Counts** (`invoice-balance-due`, `totals-balance-due`, "Subtotal", record-payment-form, payment-method-select, take-on badge, placeholder, hero tiles) are **identical to OBS-after** for 7 of 8 notes. The exception is INV-0009, which is now 1 / 1 (it became PARTIALLY_PAID in OBS-after; it was SENT when OBS-after captured it). That is exactly INV-0010's pattern, so it is expected.
- **SEL-COMPAT `/overdue/i`:** INV-0009 2, PS-1001 1. Same as OBS-after.
- **Payment History:** table = scroller on INV-0009, INV-0010 and INV-0011 (710 / 718 / 1126 / 1134).
- **Empty states:** "No payment events yet." on INV-0001 and PS-1001, plus the collection-activity and custom-fields one-liners, all present as before.
- **Carol:** HTTP 404, h1 "Page not found" × 4.
- **Console** (`console.json`, 36 01b page loads + 21 + 22 runs): **4 errors, all Carol's expected 404 document × 4.** Alice: 0 errors, 0 warnings, **0 hydration**.

## 3. Record Payment R 50,00 on INV-0010 (1440 light; `measurements.json → payment`)

| | Before | After Confirm, NO reload | After reload |
|---|---|---|---|
| Tiles | Total R 2 875,00 · Amount paid R 1 000,00 · Balance due R 1 875,00 · Due 27 Dec 2026 (30d until due) | **Amount paid R 1 050,00 · Balance due R 1 825,00** | same |
| Totals block / `invoice-balance-due` / `totals-balance-due` | … Amount paid R 1 000,00 · Balance due R 1 875,00 | R 1 050,00 · **R 1 825,00 / R 1 825,00** (tiles equal) | same |
| Payment History | 1 row (R1) | 2 rows (+ `666B-OBS-AFTERFIX` R 50,00 Receipt INV-0010/R2) | same |

- The form defaulted to 1875 / EFT.
- **No reload:** `window.__obsSentinel` survived and there were 0 `load` events during the save.
- `sameAfterReload: true`.
- API: PARTIALLY_PAID 1000 / 1875 → PARTIALLY_PAID 1050 / 1825.
- **State left:** INV-0010 paid R 1 050,00, balance R 1 825,00.

## PASS / FAIL

| # | Check | Result | Evidence |
|---|---|---|---|
| 1a | Line table ≤ scroller, 4 notes × 1024 / 1440 × light / dark | **PASS** (F-666B-A1 fixed) | `sheet-line-tables-alice.png`, `crop-draft-lines-1024-{light,dark}.png`, `measurements.json → lines` |
| 1b | Draft ACTIONS header fully visible; Edit / Delete visible without scrolling | **PASS** | same; `lines.draft.*.rowButtonsVisible` 2/2 |
| 1c | Description ≤ 3 lines @1024, Tax ≤ 3 | **PASS** (D 2–3, Tax 2; F-666B-A2 fixed) | `lines.*.descriptionLines/taxLines` |
| 1d | Section-header row aligned with columns | **NOT OBSERVABLE** (no multi-type fee note on the stack; code uses `px-3` on both) | `sectionHeaderCandidates: []` |
| 1e | Add Line placeholder 0 → 1 → 0 (cancel) | **PASS** | `sheet-draft-editors-alice.png` row 1, `draft.*.placeholder` |
| 1f | Focus on row Edit not clipped | **PASS** (scrollLeft 0, 40 px room) | `crop-draft-focus-edit-1024-light.png`, `sheet-draft-editors-alice.png` row 2 |
| 2a | Hero tiles = totals block, 8 notes | **PASS** | `sheet-hero-alice.png`, `variants.*.agree` |
| 2b | Single h1; counts same as before | **PASS** (INV-0009 differs only by its state change) | `variants.*.counts`, `selCompat` |
| 2c | Due tile = header "Due:" date | **PASS** | `variants.*.dueTileEqualsHeaderDue` |
| 2d | Payment History ≤ scroller; empty states | **PASS** | `variants.*.paymentHistory/empties` |
| 2e | Carol 404 | **PASS** | `sheet-carol.png` |
| 2f | Console 0 errors (Alice) / 0 hydration; doc h-overflow 0 | **PASS** | `console.json` |
| 3 | Payment updates tiles + totals without reload, and after reload | **PASS** | `sheet-record-payment-alice.png`, `payment` |

**Overall: PASS.** F-666B-A1 (MEDIUM) and F-666B-A2 (LOW) are fixed. No new defect was found.

## OBS limits

- Section-header rows were not observable (no data).
- Collection-activity rows: still none on any fee note.
- The F-666B-A3 single-tile width is unchanged (tracked → 670A); it was not re-measured as a defect.
