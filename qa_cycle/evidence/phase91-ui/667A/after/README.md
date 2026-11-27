# 667A OBS-after — trust Transactions, Client Ledgers, a client's history (PR #1878 @ `a5259de0b`)

**Verdict: FAIL.** K-10, K-90 (Date / Type / Running Balance), K-35/K-40, gating, URLs, shell, console and dark mode pass. K-89's "row adds up" check fails on this data (F-667A-A3). The 1024 approval view now hides Amount and Status under the sticky Actions (F-667A-A4, regression). Two smaller new defects: the ledger tiles break `dl` markup (F-667A-A1, axe serious) and out-of-range pages show "Page 10 of 3" (F-667A-A2).

## Stack and run

- **Frontend:** `e2e-frontend`, rebuilt from the PR worktree at **`a5259de0b`** with `compose/scripts/e2e-rebuild.sh frontend`.
  - Image `docteams-e2e-frontend` `sha256:58781e87106b`, created **2026-11-27T03:57:47Z**. Container created 03:57:49Z, healthy.
- **Unchanged since the before capture:** `e2e-backend`, `e2e-postgres` and `e2e-mock-idp` were not touched (created 03:09:10Z). The data is the before seed, not re-seeded, and the "1 of 2 approvals" row is still pending.
- **Side effect:** compose also recreated `e2e-localstack`. That is S3 only and none of these pages use it.
- **Capture:** the before scripts, run **unedited** from `before/scripts/` in the evidence worktree.
  - Command: `PHASE=after node 01-capture.cjs && PHASE=after STACK=… node 02-assemble.cjs`, on Node 20.11.1.
  - Output resolves to `../../after/`, so the scripts did not need copying. 55 page loads, the same targets as before.
- **Added checks:** in `after/scripts/`. All of them are read-only DOM, scroll and style edits, with no clicks on Approve, Reject or Reverse.
  - `03-compare.cjs` diffs before against after.
  - `04-after-checks.cjs` → `checks.json`: sticky visibility, Date/Type line counts, contrast, tiles, empty and out-of-range pages, axe 4.11.3.
  - `05-prototypes.cjs` → `prototypes.json`: live-DOM fix prototypes.
  - `06-proto-sticky.cjs` → `prototype-sticky.json`.
- **PNGs (21):** the 12 contact sheets, 2 K-10 crops (parity with before), plus the crops listed below. The 1024 copies of the A1/A3 prototype crops were pruned; their numbers are in `prototypes.json`.

## Before → after

| Check | Before (`fe99599e8`) | After (`a5259de0b`) | Result |
|---|---|---|---|
| **K-10** tx page 1, table vs scroller, 1440 light | 1 976 / 1 078 (+898) | **1 078 / 1 078** (dark 1 086 / 1 086) | PASS |
| K-10, 1024 light | 1 976 / 662 (+1 314) | 948 / 662 (+286) | PASS (see A4) |
| K-10 Reference column, 1024 / 1440 | 1 311 / 1 311 px | 144 / 274 px (`whitespace-normal`, `overflow-wrap:anywhere`) | PASS |
| K-10 token text: lines (widest) | 2 (1 295) | 14 (128) at 1024, 8 (258) at 1440 | PASS |
| K-10 spaced text, 1024 / 1440 | 98 px wide, 15 lines (row 325) / 3 lines | 11 lines (row 217) / 6 lines (row 117) | PASS |
| K-10 Actions at 1024 (sticky right) | x 1 730, 1 314 px out of view | `position:sticky; right:0`, x 399..662 inside the 662 px scroller on 20/20 rows (Bob 455..662, Carol 588..662) | PASS |
| K-10 Amount / Status reachable at 1024 | off-screen (x 1 449 / 1 565) | at max scroll (286): Amount 119..235 and Status 235..400 inside, 20/20 rows | PASS |
| **K-90** Date lines, every page × 4 variants | 3 lines at 50 px | **1 line, 101 px**, 0 wrapped rows on all tables | PASS |
| K-90 Type | 88 px; long labels widen the table | 160 px floor (10rem) at 1024. Badge inside its cell 0 outside. Long labels wrap up to 4 lines at 144/160 (tx-p2) | PASS |
| K-90 history, Running Balance | 1 745 / 1 078 at 1440: out of view | 1440: 1 078 / 1 078, Running Balance 936..1 078 visible (20/20). 1024: 717 / 662, inside at max scroll (55 px) | PASS |
| **K-89** client ledgers (Khumalo has R 20 095 invested) | 7 columns, "Trust Balance" | + "Placed in s86(4)", "Invested (s86(4))". Header reads **"General balance"**. Ndlovu-only search keeps "Trust Balance" + Placed (no Invested) | columns PASS |
| K-89, visible columns sum to the balance? | Ndlovu 79 000 vs 69 380; Khumalo 43 963 vs 33 963 | Ndlovu **29 000 vs 69 380**; Khumalo **23 963 vs 33 963**; Acme 0 = 0 | **FAIL → F-667A-A3** |
| **K-35** client-name links, light | 3.13:1 (register) | **5.41:1** `#00776a` on `#fdfeff` (composited), 14 px / 500. Dark 9.63:1 | PASS |
| **K-40** deposit amounts, light | (register) | **7.27:1** `#02642b` on `#fdfeff`, 16 rows. Dark 11.85:1 | PASS |
| Tiles `ledger-total-*`, Khumalo / Ndlovu / Acme × 4 variants + Carol | list rows | each testid **×1**, 1 `<dd>` each. Text equal to before: balance R 33 963,00, invested R 20 095,00, held R 54 058,00. Ndlovu has no invested/held tile. `ledger-totals-breakdown` ×1, inside `client-summary` | PASS |
| Tile overflow (scrollWidth ≤ clientWidth; children inside the tile rect) | n/a | 0 / 0 at 1024 (169 px tiles, 4-up) and at 1440 (178 px, 6-up), light and dark | PASS |
| Tile markup | `dl > div > dt + dd` | `dl > Card[role=group] > … > dd`, 0 `dt`. axe: definition-list ×1, dlitem ×9 (serious) | **F-667A-A1** |
| Page-2 deep link | "Page 2 of 3", 20 rows, Prev `?page=0`, Next `?page=2` | identical | PASS |
| Pager keeps params; filter/status/type links drop `page` | 19 interactions | **18/19 identical** (all filter, status, type, search, pill, Next/Prev hrefs and URLs); `statusLinks`/`typeLinks`/`nonZeroLink`/pager on 55 probes: identical | PASS |
| Out-of-range `?page=9` | no pager, "No transactions match…" | same message plus a **"Page 10 of 3"** pager, Prev → `?page=8`. History `?page=5` shows "Page 6 of 2" | **F-667A-A2** |
| Gating, `?status=AWAITING_APPROVAL` | Alice: "Approved by you" [disabled] + Reject on AWAIT-1, Approve + Reject on AWAIT-0. Bob: Approve + Reject on both. Carol: none | identical (button text, disabled and title). `approval-progress` ×1. reverse-button 16/20 for Alice and Bob, 0 for Carol | PASS |
| `main#main-content` ×1, `<main>` ×1, h1 in main ×1, h1 on the page ×1 | 55/55 | **55/55**, plus 12 empty/out-of-range states (tx `?status=REJECTED`, `?page=9`, hist `?status=REJECTED`, `?page=5`, cl no-match, cl `?page=1`). h1 text identical | PASS |
| Console | 0 / 0 / 0 hydration | **0** errors, warnings, page errors or hydration across the 55 capture loads plus the 04 checks and 05 prototype runs (06 not watched) | PASS |
| Dark mode, 1024 and 1440 | sheets | no regression seen (sheets). Sticky column bg is the dark card colour | PASS |

## SEL-COMPAT after (`sel-compat.json`)

Every selector in `before/sel-compat.json` has the same count, with **one exception**:

- `text "Generate Statement" (card title)` goes **1 → 0** on hist, hist-ndlovu and hist-p2 (Alice and Carol).
- The card was removed on purpose. `print-statement` / `print-statement-btn` (1/1) now sit in the PageHeader actions, which the PR's unit test `trust-client-ledgers.test.tsx:574` asserts.
- `histPrintStatementEmpty` still reads "Please select both start and end dates".
- No e2e spec uses the string on this page. The only "Generate Statement" hits are the matter SoA dialog in `day-30-45-soa-writeoff.spec.ts`.
- `recordTxMenu` is unchanged.

## Not in the before set, measured now

- **Row heights at 1024:**
  - tx-p2: 65/65/85 → 49/49/49.
  - tx-await: 85 → 59/49.
- **Table width at 1024:** the floors widen tables that used to fit or nearly fit.
  - tx-p2: 662/662 → **746/662**.
  - tx-await: 749 → **948**.
  - hist-p2: 698 → 742.
  - Client ledgers: 686 → **1 041** (2 new columns, Client Name 144, Last Transaction 146).
- **axe, light, `#main-content`:**
  - tx: 0 violations.
  - cl: 0 violations.
  - hist: A1, plus `scrollable-region-focusable` ×1 at 1024 (the history scroller has no focusable content). The history table also overflowed before (1 745/662) with the same rows, so this is probably pre-existing. axe was not run in the before capture.

## New defects introduced by the PR

| Id | Sev | Finding (measured) | Repro | Prototype (live DOM, measured) |
|---|---|---|---|---|
| **F-667A-A4** | **MEDIUM** | At 1024 the sticky Actions column (Alice 263 px, Bob 207 px) sits over **Amount and Status** at scrollLeft 0. An approver sees Approve/Reject with no amount. On `?status=AWAITING_APPROVAL` the hit-test shows Amount covered 2/2 and Status covered 2/2, for Alice and for Bob. Before, Amount (x 221..337) and Status were visible there. On page 1, page 2 and Carol's view at 1024, Status is covered on every row. Reachable only after scrolling 286 px, and nothing signals the hidden columns. | `crop-F-667A-A4-1024-await-as-built.png` (columns DATE, REFERENCE, TYPE, ACTIONS), `checks.json` › pages, `prototypes.json` › 1024.txStickyCover | `prototype-sticky.json`. **P2** = Reference floor 9rem→6rem + Type floor 10rem→7rem: Amount **shown 2/2** beside Approve for Alice and Bob. tx-p2 then fits **662/662** with every column shown 4/4. Status on the await view stays covered (Alice 852/662, Bob 796/662). **P3** = P2 + Status badge wrap: 789/662, Status still covered. Stacking Approve/Reject (flex-col on the actions div) has no effect: both buttons sit in one `ApprovalBadge` row. Which fix is a call for the orchestrator/designer. |
| **F-667A-A3** | **MEDIUM** (product decision) | K-89 is only partly met. The approved columns are present, but a row still does not add up when the client has transfers or investment *returns*, which have no column. Khumalo (invested): visible 23 963 vs General balance 33 963 (+R 10 000 transfer in). **Ndlovu's gap grew** from −9 620 (before) to **+40 380**: "Placed in s86(4) R 50 000" is shown but the R 50 380 returned is not, and R 10 000 transfers out is hidden. API totals reconcile exactly (`checks.json` › k89). | `crop-F-667A-A3-k89-1440-as-built.png` | Two extra columns, "Transfers (net)" and "Returned from s86(4)", shown only when used: every row sums exactly (69 380 / 33 963 / 0). The table grows to **1 244 / 1 078 at 1440** (+166) and 1 244 / 662 at 1024 (`crop-…-1440-prototype.png`). Fitting needs narrower Client Name/Last Transaction floors or one combined "Other movements" column. Copy and columns need founder sign-off (the register approved only the two columns). |
| **F-667A-A1** | LOW (axe serious) | The tile layout of `LedgerTotalsBreakdown` puts `StatCard` (`div[role=group]`) directly in the `<dl>` and nests the `<dd>` 3 levels down, with no `<dt>` (the label is a `<span>`). axe on hist (1024 and 1440): `definition-list` ×1 and `dlitem` ×9. Before, the list layout was `dl > div > dt + dd`. Each tile is still a labelled group, so screen-reader impact is limited, but the "− less" spoken signs are gone too. | `crop-F-667A-A1-tiles-1440-as-built.png`, `checks.json` › axe | Flatten each tile to `dl > div[data-testid=ledger-total-*][card classes] > dt(label) + dd(value)`. axe definition-list/dlitem goes **9 → 0** at 1024 and 1440, tile heights are unchanged (76 / 96 px), all 9 testids keep 1 `dd` with the same text (`crop-F-667A-A1-tiles-1440-prototype.png`). This keeps the `trust-balanced-month.spec.ts:508` `.locator("dd")` contract. Needs a StatCard `as`/`labelAs="dt"` option or a local tile. |
| **F-667A-A2** | LOW | An out-of-range page now shows a pager that reads "**Page 10 of 3**", with Previous → `?page=8`, also out of range. History `?page=5` shows "Page 6 of 2" → `?page=4`. The before state had no pager (F-667A-3 copy). Client ledgers `?page=1` has no pager (≤ 1 page). | `crop-F-667A-A2-out-of-range-pager.png`, `checks.json` › empty | Code, not CSS. `data-table.tsx:373` shows the pager when `pageCount > 1` regardless of `pageIndex`. Hide it when `pageIndex >= pageCount`, or clamp the text and Previous href to `pageCount - 1` in `data-table-page-links.tsx:30-42`. Kit-level, so check the 664B consumers too. |

**Observation, not a defect (O-2).** Client ledgers at 1024 now overflow by 379 px (was 24), and Client Name is not sticky. Scrolled to the s86(4) columns, the client name is off-screen (0/3 rows). Making the first column `sticky left-0 bg-card` keeps the name visible on 3/3 rows at max scroll (`prototypes.json` › 1024.clStickyProto).

## Pre-existing items still present

These are listed in `../found.md` and were not re-listed above:
- **F-667A-3:** the out-of-range copy is unchanged, now joined by A2.
- **F-667A-4:** `?page=1` still shows "0 clients found" plus the empty state.
- **F-667A-5:** not exercised; no reversal was made.
- **F-667A-6, F-667A-7:** the landing page's `link "Record Transaction"` is still 1 for Carol.
- **F-667A-8:** the title is still generic on all 55 pages.
- **F-667A-9:** not exercised; no approval was made.
- **F-667A-1 / F-667A-2:** fixed, apart from A4.
