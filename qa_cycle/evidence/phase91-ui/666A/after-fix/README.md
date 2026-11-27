# 666A OBS after-fix: fee-run tables at 1024 + fee-note dates — PR #1876 @ `97d84aeed`

- **When:** 2026-11-27. Frontend rebuild 01:27:56–01:29:11Z; capture (`09-after-fix`) 01:30:49–01:31:32Z; dates re-crop (`10-dates-crop`) 01:32:37–01:32:41Z.
- **Browser / settings:** as OBS-after: Playwright library (headless Chromium from the main checkout's `frontend/node_modules`), DPR 1, reduced motion, Africa/Johannesburg, rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme`, Node 20.11.1 (nvm). Alice (Owner); Carol (Member) through the `mock-auth-token` cookie (`L.loginState`). **No SQL, no data writes, no reseed, backend untouched** (page loads only; the wizard was loaded at step 1 and not advanced).
- **Raw files:** 26 full-page PNGs in `/tmp/666A-after-fix-raw/` (outside the repo).

## Stack (build proof)

- **Worktree:** `worktree-epic-666A` HEAD **`97d84aeed7d47df32316a20fde84e1eef78529fc`**, `git status --short` empty (`.epic-brief.md` is ignored), checked 01:27:52Z.
- **Rebuild:** `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend`, exit 0, "Done. Rebuilt and restarted: frontend" (`/tmp/666A-afterfix-rebuild.log`).
- **`e2e-frontend`:** created **`2026-11-27T01:29:09.808Z`** (was `00:56:20.976Z`, the OBS-after build), image **`sha256:ec9c2169c36e…`** (was `89867ddf333f…`), compose `working_dir` label `/Users/…/worktree-epic-666A/compose`, config file `…/worktree-epic-666A/compose/docker-compose.e2e.yml`. `BUILD_ID` `NTtUezM9dC28YihXuXbrM`. `curl :3001/` → 200, `/mock-login` → 200.
- **The served bundle is the fix:** `curl :3001/_next/static/chunks/…` →
  - `0gpj_9ugr5ht2.js`: `meta:{label:"Name",cellClassName:"whitespace-normal",minWidth:"11rem"}` and `label:"Period",priority:"low"` (A1);
  - `3t4ydq7miu7rb.js`: `label:"Client",cellClassName:"font-medium whitespace-normal",minWidth:"12rem"`, `label:"Failure Reason",…,minWidth:"10rem"`, `label:"Time Amount",align:"end",priority:"low"` (A2);
  - `2t-dhq5-lthtj.js`: `label:"Client",cellClassName:"text-muted-foreground whitespace-normal",minWidth:"8rem"` and `label:"Issue Date",cellClassName:"text-muted-foreground",csv:…` (no `priority`: dates).
  - In the browser the DataTable renders `meta.minWidth` as inline `style="min-width: …"` (`components/data-table/data-table.tsx:251-253`); measured on the live th/td: Name `11rem`, items Client `12rem`, Failure Reason `10rem`, fee-notes Client `8rem` (`measurements.json` → `cols[].minWidthTh/minWidthTd`).
- **Unchanged:** `e2e-backend` created `2026-11-26T23:51:00.513Z`, image `sha256:e7fd89e47741…`; postgres / mock-idp / mailpit `23:51:00Z`; `e2e-localstack` still `00:56:19Z` (not recreated this time).
- **Data:** 23 fee notes, 1 fee run `af572249…` COMPLETED (2 items, both GENERATED, `failureReason` null) — the OBS-after final state.

## PASS / FAIL

| # | Check | Verdict | Key numbers (1024 light unless noted) |
|---|---|---|---|
| A1 | Fee runs list `/invoices/billing-runs` | **PASS** | Scroller **710/710** (dark 718/718; 1440 1126/1126, dark 1134/1134). Name **250 px**, title on **2 lines** ("Fee run · 01 Nov 2026 – 27 Nov / 2026"), row **57 px**. Visible at 1024: NAME, STATUS, CLIENTS, FEE NOTES, TOTAL, CREATED — **Period hidden**; CREATED x 609–710, in view. 1440: all 7 columns, Name 322 px on 1 line, row 39. Clipped cells 0 at every variant. |
| A2 | Run Items `/invoices/billing-runs/af572249…` | **PASS** | Scroller **710/710** (dark 718/718; 1440 1126/1126). Hidden at 1024: **Time Amount + Disbursement Amount**; **Total visible** (x 346–446). CLIENT **248 px**: "Khumalo Property Holdings mwy7mytk9055" on 2 lines (row 57), "Sipho Ndlovu mwy7iyj8bd18" on 1 (row 39). **FAILURE REASON 160 px at x 550–710, in view** (dark 558–718). 1440: all 7 columns in view, rows 39/39. Clipped 0. Caveat: both items' failure reason is null ("—"); a long reason can't be shown without creating a failed run (a data write), so only the column's position/width is observed, not the wrap of a long reason. |
| D | Fee notes dates `/invoices` | **PASS** | **Issue Date (x 391–492) + Due Date (492–593) visible at 1024**, and at 1440 (685–825 / 825–965). Scroller **710/710** at 1024 (dark 718/718), 1126/1126 at 1440. CLIENT **197 px** at 1024 (417 at 1440). **Currency hidden** at 1024 and 1440. K-72 holds: 23/23 number links on 1 line, every header on 1 line, 5 rows above the 768 fold (11 at 1440). Rows **39–57**: 15 × 39, 8 × 57 (dark, 718 px: 18 × 39, 5 × 57) — the tall rows are client names that now wrap to 2 lines ("Khumalo Property Holdings / mwy7mytk9055", "Moroka Family Trust / mwy7iyj8bd18", …); 1440 rows 39–53 (53 = the Partially Paid balance hint), as before. |
| S | Sweep, 6 routes × 1024/1440 × light/dark (24 loads) | **PASS** | Every load: HTTP 200, `main#main-content` 1, `<main>` 1, **h1 1** (in main and page-wide), document h-overflow **0**, every table scroller overflow **0**, clipped cells 0, no table fades, no nowrap-clipped text; dark: no large bright surfaces. h1s: "Fee notes", "Fee runs", "Fee run · 01 Nov 2026 – 27 Nov 2026", "New fee run", "Debtors", "Unbilled work". `/collections` and `/unbilled` measure the same as OBS-after (Debtor book 710/710, Signals 529–710 in view, rows 53; Unbilled 710/710, row 36). `/billing-runs/new` step 1: badges unchanged (active 5.46 light / 3.16 dark = B6 residual; pending 6.76 / 5.84). Only sub-AA text: "New fee note" 3.16 in dark (B19, pre-existing). |
| C | Console | **PASS** | 26 loads: **1 error, 0 warnings, 0 hydration, 0 page errors** — the error is "Failed to load resource … 404" on Carol's `/invoices` (the expected notFound; OBS-after had the same 404 per Carol notFound load). Alice: 0 messages. |
| R | Carol (Member) 1440 light | **PASS** | `/invoices` → **HTTP 404**, h1 "Page not found" (as before). `/invoices/billing-runs` → 200, h1 **"Fee runs"** + "You do not have permission to view fee runs. Only admins and owners can access this page." (as before). 1 main, 1 h1 each, h-overflow 0. Caps: Member, 5. |

**Overall: PASS** — F-666A-A1, F-666A-A2 and the CodeRabbit dates finding are fixed on the running build; no new defect.

## Before (`1e75514c4`, `../after/`) → After-fix (`97d84aeed`)

| Measure (1024 light unless noted) | Before | After-fix |
|---|---|---|
| Fee runs list scroller | **716 in 710** (CREATED cut 6) | **710/710** |
| Fee runs Name width / lines / row | **54 px / 8 lines / 177 px** | **250 px / 2 lines / 57 px** |
| Fee runs columns at 1024 | all 7 (Period 202 px) | Period hidden; 6 in view |
| Fee runs 1440 | 1126/1126, Name 322, row 39, all 7 | identical |
| Run Items scroller | **841 in 710** (dark 841 in 718) | **710/710** (dark 718/718) |
| Items CLIENT width / row heights | 124 px / 57–97 px | 248 px / 39–57 px |
| Items FAILURE REASON | **cut 131 px**, out of view, not keyboard-reachable | 160 px at 550–710, **in view** |
| Items columns at 1024 | all 7 | Time + Disbursement hidden; Total visible |
| Items 1440 | 1126/1126, all 7 | 1126/1126, all 7 |
| Fee notes columns at 1024 | Fee note, Client, Status, Total (**Issue/Due hidden**) | Fee note, Client, Status, **Issue Date, Due Date**, Total |
| Fee notes scroller 1024 / 1440 | 710/710 / 1126/1126 | 710/710 / 1126/1126 |
| Fee notes CLIENT width 1024 | 350 px | 197 px (`min-width: 8rem`) |
| Fee notes rows 1024 | 39–53 px | 39–57 px (8 of 23 rows light, 5 dark: client on 2 lines) |
| Number links on 1 line / rows above fold | 23/23 / 5 | 23/23 / 5 |
| Currency | hidden at 1024 + 1440 | hidden at 1024 + 1440 |
| Debtors / Unbilled 1024 | 710/710 / 710/710 | identical |
| h1 / main / doc h-overflow (24 Alice loads) | 1 / 1 / 0 | 1 / 1 / 0 |
| Console (Alice) | 0 | 0 |

## Observations (no defect filed)

- **O-1 (trade-off of showing the dates):** at 1024 the fee-notes Client column is 197 px, so 8 of the 23 seeded client names (the long `… mwy7…` suffixed ones) wrap to 2 lines and those rows grow 39 → 57 px. Nothing is cut or clipped, number links stay on 1 line and the fold count is unchanged (5). Recorded for the orchestrator; not a regression of K-72 (which is about the fee-note number).
- **Kit scroller a11y (check 9 of OBS-after, D-44 class):** still no `tabindex`/edge fade on table scrollers (`components/ui/table.tsx`), but on these 666A routes nothing overflows any more at 1024 or 1440, so no column is unreachable.
- 1024 dark scrollers are 718 px vs 710 light — same 8 px difference as in OBS-after (identical in before/after), not introduced here.

## Files

- **Sheets (4 variants each: 1024 light | 1024 dark | 1440 light | 1440 dark, full page):** `sheet-list-alice.png`, `sheet-billing-runs-alice.png`, `sheet-billing-run-detail-alice.png`, `sheet-billing-runs-new-alice.png`, `sheet-collections-alice.png`, `sheet-unbilled-alice.png`; `sheet-carol.png` (2 routes, 1440 light); `sheet-crops-1024.png` (the six crops).
- **Crops (table container, 1024):** `crop-fixed-A1-runs-list-1024-{light,dark}.png`, `crop-fixed-A2-run-items-1024-{light,dark}.png`, `crop-fixed-dates-fee-notes-1024-{light,dark}.png`.
- **JSON (single line):** `measurements.json` (per route × variant: page globals, `cols` per-column probe — visible, width, x0/x1 in the scroller, in view, header/cell line counts, inline min-width, clipped cells, row heights; plus the copied AFTER() probe's header/tables/empties/steps/dark audit; API snapshot; Carol), `console.json`.
- **Scripts (as executed, in order):** `09-after-fix.cjs` → `10-dates-crop.cjs`. `lib.cjs`, `probes.cjs`, `probes-after.cjs` copied **unchanged** from `../after/scripts/` (run with `PHASE=after-fix`; lib already accepted it). `09` is new (the OBS-after 01b/06/08 capture writes, role/module toggles and wizard walk are out of scope here). `10` is new: 09's element screenshot of the 1081 px fee-notes table scrolled the page and the sticky top bar was painted over the table header (capture artefact); 10 re-shoots both dates crops as a clip of one full-page capture and rebuilds `sheet-crops-1024.png`. Line-count caveat: `cellLinesMax` counts 2 for badge cells (Status) and the unbilled "(n)" cells even at 1 visual line (inline-flex rects); row heights are the reliable signal and are what the verdicts use.
