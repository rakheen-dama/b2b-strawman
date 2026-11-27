# 667B OBS-after @ `6a5d7ef2e` (PR #1879)

**Verdict: FAIL.** One MEDIUM layout regression: at 1024 the open-statements table clips its Discard button (F-667B-A1). Everything the PR set out to change was measured, and each of those changes works: K-36, K-76 in light, K-91's Actions, F-667B-1 and PageHeader. SEL-COMPAT, URLs, interactions and the console are unchanged. Four new findings are listed in `../found.md` under "Found in OBS-after".

## Run and environment

**When:** 2026-11-27, ~05:40–05:55Z.

**Stack:** the same e2e mock-auth stack and data as `before/`. There was no re-seed, no reset and no SQL.
- The frontend was rebuilt from the PR worktree with `bash compose/scripts/e2e-rebuild.sh frontend` (run from `worktree-epic-667B`, HEAD `6a5d7ef2e`).
  - New image `docteams-e2e-frontend` **`sha256:d33371e03ac0`**, created 2026-11-27T05:43:46Z. The container started 05:43:49Z and is healthy.
  - Compose working dir: `worktree-epic-667B/compose`.
  - Proof it is the PR build: the bundle contains `investments-register` (server chunk) and the `maturity-alert` tint CSS. Every page now carries `data-testid="page-header"` (0 before). The K-76 badge class is `bg-(--button-accent)`.
- **Side effect of the rebuild:** compose also re-created `e2e-localstack` (05:43:47Z, the same `localstack:3` image). Without persistence, S3 objects from before may be gone. No captured surface reads S3.
- The backend (`sha256:e7fd89e47741`), postgres, mock-idp and mailpit were not touched (created 04:41:53Z).

**Browser:** the Playwright library from the main checkout's `frontend/node_modules`, Node 20.11.1, and the same settings as before. The MCP was not used.

**Replayed unedited:** from `before/scripts/`:
`PHASE=after node 01-capture.cjs && PHASE=after STACK="…" node 02-assemble.cjs`
- 60 page loads and 16 interactions.
- Outputs: `measurements.json`, `sel-compat.json`, `console.json`, `discover.json`, 13 sheets and 5 crops.

**New scripts** (`after/scripts/`):
- `00-peek.cjs`: a read-only REST peek at the investments.
- `01-layout.cjs` → `layout.json`: read-only. It measures:
  - the List locked link against the scroller;
  - the Completed column at 1024, 1280, 1366 and 1440;
  - the Discard button against the scroller;
  - the sticky Actions column against the Balance amount and the Status chip, at scroll 0 and at maximum scroll.
- `02-crops.cjs` → `crop-open-statements-1024-{light,dark}.png`.
- `03-maturity.cjs` → `maturity.json` and `crop-maturity-1024-{light,dark}.png`. This script makes **one write** (see below).

**Order:** the unedited capture and `01-layout` ran **before** the write. Every before→after number below therefore comes from the same data as `before/`.

## The one write (maturity-alert probe)

- No investment had a maturity date, so `03-maturity.cjs` placed one as Alice through the PlaceInvestmentDialog REST call: `POST /api/trust-accounts/789f111c…/investments` with an Idempotency-Key.
  - Basis CLIENT_INSTRUCTION, Ndlovu Family Trust, on Ndlovu's s86(4) account `28691bf6…`.
  - R 1 000 at 7.5%, deposited 2026-11-27, maturing **2026-12-15**.
  - Notes "667B OBS-after maturity-alert probe".
- Result: **`28a448ce-2102-43c7-a256-3deff644a544`**, ACTIVE with `awaitingApproval: true`. Its deposit transaction `78d22a64-1455-4346-bf43-fb8c326b0a52` is awaiting approval, so no ledger has moved yet.
- `/investments/maturing` now returns this investment.
- `?investmentBasis=FIRM_DISCRETION` is **still empty**: the API returns 0 rows and the page still renders `empty-investments` = 1.
- **Effect on any later re-run (after-fix):**
  - `/investments` shows "3 investments found" (it was 2), and one row's testid is `maturity-alert`.
  - The CLIENT_INSTRUCTION filter shows 3 rows.
  - An awaiting-approval deposit now exists, which may show on the trust landing page and in the transactions list.
  - Compare those surfaces against this README, not against `before/`.

## Checks: before → after

Numbers are 1024 / 1440 light, for Alice unless stated.

### Shell, URLs, selectors and interactions

| Check | Before | After | Result |
|---|---|---|---|
| Page loads (same URLs, including `?page=1`, `?page=9`, `?investmentBasis=…`) | 60 | 60, same URLs, all HTTP 200 | PASS |
| `main#main-content` / `<main>` / h1 in main / h1 on the page | 1/1/1/1 everywhere | 1/1/1/1 everywhere (60/60) | PASS |
| h1 text | unchanged | identical on all 60 | PASS |
| h1 size | 30 px raw on the 4 raw pages | 24 px PageHeader (`page-header` testid 0 → 1 on the 4 pages) | PASS (intended) |
| Document h-overflow | 0 | 0 on all 60 | PASS |
| Console: errors / warnings / page errors / hydration | 0/0/0/0 | **0/0/0/0** | PASS |
| SEL-COMPAT (`sel-compat.json`, every selector × target × persona) | — | **all equal** except `text "Investment Register"` on `/investments`, 1 → 2: the DA-20 sr-only `<caption>` (`investments-data-table.tsx:313`) | PASS (explained) |
| Column headers, exact text, every table and persona | — | identical | PASS |
| `testid` diff (`discover.json`) | — | only `page-header` +1 on the 4 pages; no testid lost | PASS |
| Pager page 1 | "Page 1 of 2", Next → `?page=1` | same | PASS |
| Pager page 2 (`?page=1`) | "Page 2 of 2", 3 rows, Previous → `?page=0`, Next disabled `<button>` | same | PASS |
| 16 interactions: Next/Previous, List locked → list, New Reconciliation → `/new`, wizard step 1, monthly-list back link, basis filter Firm → Client → All, Discard opened and cancelled, Section 35 click-through, Investment Register run (11 headers, LPFF Rate 0) | — | **all identical** (`interactions`, ignoring file paths and classes) | PASS |
| Gating, Carol | no New Reconciliation, no Place Investment, no investments Actions (7 columns), no Discard, 0 report cards + FV notice | same (0/0/7 columns/0/0 + 1) | PASS |

### Surfaces and register rows

| Check | Before | After | Result |
|---|---|---|---|
| **F-667B-1**: reconciliation history at 1024, page 1 | 678 / 662 (+16), rows 85 px, Period 3 lines | **662 / 662**, rows 57 px, Period 2 lines (74 px) | PASS |
| **F-667B-1**: page 2 | 729 / 662 (+67), List locked out of view | **662 / 662**. The link spans x 855–959 in a scroller at 305–967 (8 px clear), and the centre hit-test lands on the link. Dark: 670 / 670, 8 px clear | PASS |
| Reconciliation history at 1440 | 1078 / 1078, rows 47 | 1078 / 1078, rows 39 | PASS |
| **Completed column** (DA-5 deviation) | shown, 2 lines at 1024 | `display:none` at 1024, **1280 and 1366**; shown only at 1440 (123 px). The date is shown nowhere else in the row. → F-667B-A3 | deviation, needs a ruling |
| **Open statements at 1024** | **662 / 662, fits** | **701 / 662 (+39)**, dark 701 / 670 (+31). Period is 202 px and no longer wraps. **Discard is 31 px (dark 23 px) out of view and reads "Disc".** Carol: 662 / 662. → **F-667B-A1** | **FAIL** |
| Open statements at 1440 | fits | 1078 / 1078 | PASS |
| `?page=9` (out of range) | "23 reconciliations found" + "No reconciliations yet…", no pager | the same copy (now an inline EmptyState) + **pager "Page 10 of 2", Previous → `?page=8`** → F-667B-A4 | LOW (kit, F-667A-A2 class) |
| **K-91** Actions at 1024 | 761 / 662, Actions 99 px out of view | 771 / 662. Sticky Actions at x 527–662; every button is in view (Record interest 535–662, Interest entries 535–661, Withdraw 535–624). Dark: sticky 535–670, all in view | PASS |
| K-91: Balance amount vs the sticky column at scroll 0 | — | amount ends at x 527 and sticky starts at 527: **0 px gap** in light; **8 px** in dark. No overlap | PASS (0 px, residual per F-667B-B4) |
| K-91: Status vs the sticky column at scroll 0 | Status visible | **Fully covered** on every row: Active 58/58 px, Withdrawn 84/84, Awaiting approval 84/84 (probe row); light and dark. The hit-test lands on the sticky cell. A scroll of 109 px (dark 101) reveals it. Rows without actions show an **empty** sticky panel over the status. → F-667B-A2 | residual (D-81 class), needs a ruling |
| K-91 at 1440 | fits | 1078 / 1078. Sticky at 834; Balance amount 108 px clear; Status clear | PASS |
| K-91 Carol at 1024 | 662 / 662 | 662 / 662, no sticky column | PASS |
| K-91: the 86(3) note in sans (out of `font-mono`) | not observable | **not observable**: no s86(3) investment; `investment-interest-fund-note` = 0 | N/O (unit test only) |
| Investments rows | 157 px at 1024 / 125 px at 1440 | 149 / 117. At 1024 Principal is 8 lines, Rate 4, Term 3–5 (same class as before) | PASS |
| **K-36** "Run Report →" ×9, light | #00a38f on #fdfeff, **3.13** | **#00776a on #fdfeff, 5.41** (all 9; 1024 and 1440) | PASS |
| K-36 "Run Report →", dark | 9.63 | 9.63 (#00d5be on #14171c) | PASS |
| K-36 "5% (statutory)" | 3.13 / dark 9.63 | **5.41** / 9.63 | PASS |
| **K-76** active badge, light | #ffffff on #00a38f, **3.16** | **#ffffff on #00776a, 5.46** | PASS |
| K-76 active badge, dark | 3.16 | **3.16**: `--button-accent` is teal-600 in `.dark`. Predicted by the PR (666A parity) | as expected |
| K-76 pending badges | 4.18 light / 5.84 dark | **6.76** (#4d545f on #eff1f5) light / 5.84 dark | PASS |
| Wizard step indicator at 1024 (F-667B-6, pre-existing) | 726 in 712, +14 px, badges 21–24 × 32 | unchanged: +14 px (dark +6), badges 21–24 × 32 | unchanged (not in scope) |
| Monthly list tables at 1024 / 1440 | fit; rows 45/37/65; tfoot "Total trust creditors R 101 630,00"; after-lock row; sentence | identical. Fits 662 / 662 (dark 670). Same rows and cells; the sentence "…are now R 102 130,00, a change of R 500,00…" is still rendered | PASS |
| Other creditors (untouched) | `fund-ledger-table` 777 / 662 | 777 / 662 (F-667B-10, unchanged) | unchanged |

### Maturity tint and empty states

| Check | Before | After | Result |
|---|---|---|---|
| **Maturity-alert tint** (probe row, Alice, 1024 and 1440) | not observable | **Light:** row #fffbeb, sticky Actions cell #fffbeb (CSS composite and screenshot pixels agree). **Dark:** row #221813 (1024 pixel #231814), sticky cell #241917, at most 3 levels per channel, so not a distinct block. Untinted rows: #fdfeff / #14171c, sticky identical | PASS (`crop-maturity-1024-*.png`) |
| `empty-investments` (FIRM_DISCRETION) | bare `div.py-8`, 1078 × 84, no icon, text 4.68 | shared EmptyState inline: 1078 × 44, **icon**, same text, `text-foreground` 17.79 (dark 17.96). Carol: the same. Still no action and still "yet" (F-667B-9 copy, unchanged) | PASS |
| `?page=9` empty | `p` 1078 × 20, 4.68 | inline EmptyState `p` 380 × 20, 17.79; same text | PASS (copy is F-667B-7) |
| Carol's Trust Reports FV notice | `p`, 4.68 | compact EmptyState `h2`, same text; the testid stays on its Card (1) | PASS |

## Evidence

- `measurements.json`, `sel-compat.json`, `console.json`, `discover.json`: the unedited replay.
- `layout.json` (`01-layout.cjs`) and `maturity.json` (`03-maturity.cjs`).
- 13 sheets and 5 crops from the replay.
- Defect crops: `crop-open-statements-1024-{light,dark}.png` (F-667B-A1) and `crop-maturity-1024-{light,dark}.png` (the tint, and F-667B-A2's empty sticky cell over Status).
- Raw tiles: `/tmp/667B-after-raw/`, not committed.
