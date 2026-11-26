# 665E OBS after-fix: Templates column widths (F-665E-A1) on PR #1874 @ `092a21309`

- **When:** captured 2026-11-26 22:28:04Z to 22:28:21Z (`01-templates`), assembled right after (`02-assemble`).
- **Verdict: PASS.** The K-117 alignment now holds with the Word row in place: every column's left x **and** width are identical across all 7 category tables (spread **0 px**), for both personas, at 1024 and 1440, in light and dark. At 1440 the tables still fit (814/814, dark 822/822). The invariants, gating and console checks all pass.
- **Side effect (recorded, not a gate):** at 1024 the tables now overflow **more**: 720 px in a 446 px scroller (Alice), against 666/676 in OBS-after. B1 / D-44 is accepted and unchanged in status, but it is worse by 44–54 px. See check 2.
- **Scope:** the Settings › Documents › Templates tab only. No product code was changed, no SQL was run, nothing was written (page loads and API GETs only).

## Stack / build source

- **The stack:** the e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090), org `e2e-test-org`. It was not restarted, rebuilt or reseeded.
- **`docker inspect e2e-frontend`:**
  - Created **`2026-11-26T22:25:20.444035711Z`**
  - Image `sha256:78aedbc96084cef0a837fab309c213fff8a88e58671d3e930cae99db5aa48c25`
  - `com.docker.compose.project.working_dir` = **`/Users/rakheendama/Projects/2026/worktree-epic-665E/compose`**
  - Running and healthy; `GET /` → 200; backend `/actuator/health` → 200.
- **Worktree:** `git rev-parse HEAD` = **`092a21309b0fc6f691182c5809f3945d8d4d26b6`**. `git status --short` showed only the untracked evidence directories.
- **`min-w-24` is in the served build** under `/app/frontend/.next` (server chunk + static CSS/JS).
- **The new build is the one served:** in the DOM, the FORMAT `th` and `td` both carry `w-24 min-w-24` (`measurements.json` → `k117.*.wordRow.thCls/tdCls`).
- **Data:** API `GET /api/templates` → 23 templates. **"OBS 665E Word" `97f9a4ff-5e6f-42a9-a2a8-3162308a5be1`** is DOCX, ENGAGEMENT_LETTER, ORG_CUSTOM and active. It renders in the **Engagement Letter** table in all 8 persona × variant loads.

## Settings

- **Browser:** the Playwright library (worktree `frontend/node_modules`), headless Chromium, DPR 1, `reducedMotion: reduce`, timezone Africa/Johannesburg.
- **App:** rail expanded (`sidebar_state=true`), theme via localStorage `kazi-theme` (the `<html>` class was verified per variant). Node 20.11.1 (nvm), `NODE_OPTIONS=""`.
- **Not used:** the Playwright MCP browser.
- **Variants:** Alice (owner) and Carol (member) × 1024×768 / 1440×900 × light / dark = 8 loads of `/settings/documents?tab=templates`.
- **Raw files:** full-page tiles and the unannotated K-117 clips are in `/tmp/665E-afterfix-raw/` (outside the repo).

## Results

| # | Check | Result | Numbers |
|---|---|---|---|
| 1 | **K-117 alignment** (7 tables, incl. Engagement Letter with the Word row) | **PASS** | Every header's x and width spread across the 7 tables is **0 px** in all 8 variants (table below). OBS-after had a spread of **10 / 9 / 10 / 10 px** at 1024 (ENTITY TYPE / SOURCE / STATUS / ACTIONS) and **4 px** on FORMAT at 1440. The "Word" badge (68.5 px) now fits the FORMAT cell's 80 px inner width; "Tiptap" is 59 px. FORMAT is 96 px in every table. |
| 2 | **Table overflow** (scroller clientWidth / scrollWidth) | **1440: PASS** (it fits). **1024: still clipped, worse** (D-44, measured only). | **1440:** 814/814 (dark 822/822) for both personas, unchanged and 0 cut. **1024 Alice:** 446/**720** (dark 454/720), was 446/666–676. ENTITY TYPE is cut 18 px (dark 10). SOURCE (114), STATUS (194) and ACTIONS (274) are hidden at scroll 0. In OBS-after, SOURCE was cut 72/82 and STATUS / ACTIONS were hidden. **1024 Carol:** 446/**640** (dark 454/640), was 446/592–602. ENTITY TYPE is cut 18, SOURCE and STATUS are hidden. |
| 3 | **Invariants** (8 variants) | **PASS** | `main#main-content` = 1 and `<main>` = 1; h1 in main = `["Templates"]` (1); doc h-overflow **0**; no "Something went wrong"; the theme class matches. |
| 3b | **Gating parity vs OBS-after** | **PASS** | **0 diffs over 80 comparisons:** selectedTab, tabs, rail, notice, url, status, main counts, h1, and the per-table header lists. **Full-probe button names at 1440 light:** 0 lost and 0 added (Alice and Carol); headings and chips are identical. **SEL-COMPAT at 1024 light:** 44 keys, **0 diffs**. **Alice:** Upload Word Template 1, New Template 1, Save Branding 1, h2 Branding 1, ACTIONS column in 7/7 tables, 23 row buttons. **Carol:** 0 / 0 / 0 / 0, ACTIONS in 0 tables, 0 row buttons. |
| 4 | **Console** (8 loads) | **PASS** | 0 errors, 0 page errors, 0 hydration messages, 0 warnings. |
| 5 | **Artefacts** | done | `sheet-templates-{alice,carol}.png`, `crop-k117-align-{1024,1440}.png` |

### K-117 header left x / width (every one of the 7 tables identical; spread 0)

| Persona / variant | NAME | FORMAT | ENTITY TYPE | SOURCE | STATUS | ACTIONS |
|---|---|---|---|---|---|---|
| Alice 1024 light / dark | 521 w256 | 777 w96 | 873 w112 | 985 w96 | 1081 w80 | 1161 w80 |
| Alice 1440 light | 569 w350 | 919 w96 | 1015 w112 | 1127 w96 | 1223 w80 | 1303 w80 |
| Alice 1440 dark | 569 w358 | 927 w96 | 1023 w112 | 1135 w96 | 1231 w80 | 1311 w80 |
| Carol 1024 light / dark | 521 w256 | 777 w96 | 873 w112 | 985 w96 | 1081 w80 | — |
| Carol 1440 light | 569 w430 | 999 w96 | 1095 w112 | 1207 w96 | 1303 w80 | — |
| Carol 1440 dark | 569 w438 | 1007 w96 | 1103 w112 | 1215 w96 | 1311 w80 | — |

- **Dark at 1440 is offset +8 px from light**, but uniformly: the section is 8 px wider, as in OBS-after. So the theme has no effect on alignment.
- **At 1024 the declared widths are now honoured:** 96 / 112 / 96 / 80 / 80, set by the `min-w-*` classes. In OBS-after they were shrunk to 75 / 100 / 87 / 74. This is exactly why the 1024 tables grew from 666/676 to 720 (check 2).
- **NAME at 1440** is 350 px for Alice (was 366 in the Tiptap-only tables and 362 in the Word table) and 430 for Carol. This is because FORMAT grew from 80 to 96.

### Comparison with OBS-after (`../after/`, `5ebc6be31`)

| Item | OBS-after | After-fix |
|---|---|---|
| K-117 spread 1024 (ENTITY / SOURCE / STATUS / ACTIONS) | 10 / 9 / 10 / 10 px → FAIL | 0 / 0 / 0 / 0 → **PASS** |
| K-117 spread 1440 FORMAT | 4 px (931 vs 935) → FAIL | 0 → **PASS** |
| FORMAT width, Tiptap-only vs Word table | 75 vs 85 (1024); 80 vs 85 (1440) | 96 everywhere |
| Templates scroller at 1024, Alice | 446 / 666–676 | 446 / 720 (+44–54) |
| Templates scroller at 1024, Carol | 446 / 592–602 | 446 / 640 (+38–48) |
| Templates scroller at 1440 | 814/814 | 814/814 |
| Invariants / gating / console | PASS | PASS (0 diffs vs OBS-after) |

## How to read the crops

- **The crops:** `crop-k117-align-{1024,1440}.png` are Alice's light fullPage clips, from the top of the "Cover Letter" section (Tiptap only) to the bottom of the "Engagement Letter" section (the "OBS 665E Word" row first).
- **The guide lines:** the red 1 px lines and labels are drawn **offline** by `02-assemble` on a copy of the clip. They sit at the measured left x of each Engagement Letter header, and they run through both tables. Every Cover Letter header edge sits on the same line.
- **At 1024:** the clip covers only the visible part of the scroller (NAME, FORMAT, part of ENTITY TYPE). The other columns are scrolled out of view (D-44).
- **The raw clips** without the guide lines are `/tmp/665E-afterfix-raw/k117-align-{1024,1440}.png`.

## Files

- **`measurements.json`** (single line):
  - `verdict`
  - `k117[persona][variant]`: spread, the OBS-after `beforeSpread`, per-table x and w, `formatFit`, and the Word row with its `th` / `td` classes
  - `overflow` (before → after)
  - `invariants`
  - `gating` (diffs + per-variant gated-control counts)
  - `fullProbeButtons`
  - `selCompat`
  - `console`
- **Sheets:** `sheet-templates-alice.png` and `sheet-templates-carol.png` (cols 1024 light | 1024 dark | 1440 light | 1440 dark, full page).
- **Crops:** `crop-k117-align-1024.png` and `crop-k117-align-1440.png`.
- **`scripts/` (run in this order, with `PHASE=after-fix`):**
  - **`lib.cjs`:** a copy of `after/scripts/lib.cjs`. The only change is the raw dir, `/tmp/665E-afterfix-raw`.
  - **`01-templates.cjs`:** new. It contains HELPERS / FULL / COMPACT / selCompat copied verbatim from `after/scripts/01-capture.cjs:45-300`, plus a gating probe and the K-117 clips.
  - **`02-assemble.cjs`:** new. It does the offline compare against `../after/measurements.json` and `../after/sel-compat.json`, and builds the sheets, the annotated crops and `measurements.json`.
