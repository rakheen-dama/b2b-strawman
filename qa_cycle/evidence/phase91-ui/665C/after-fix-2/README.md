# 665C OBS after-fix-2: F-665C-15 (checklist items table at 1024) re-observed

- **When:** 2026-11-26, after the frontend rebuild at 19:45:52Z.
- **PR / head:** #1872, `b87d6d742` (branch `epic-665C/settings-matters`). This commit adds `headerClassName: "whitespace-normal"` to the Document Required column (`checklist-items-table.tsx:61`).
- **Verdict:** **F-665C-15 PASS.** The PLATFORM "Generic Client Onboarding" items table is **446/446** at 1024 light (it was 446/487 after-fix) and **454/454** at 1024 dark (it was 454/487). Every header, every "Required" badge and every Document Required cell is visible in all 8 page × variant records. `docHOverflowPx` is 0, there is exactly one h1, and the console has 0 errors, 0 warnings and 0 hydration messages.
- **What changed:** no product code was changed and no SQL was run. The two checklist ids came from `GET /api/checklist-templates`. The script makes no writes to the app.
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`), not the MCP. DPR 1 (crop: DPR 3), reduced motion, timezone Africa/Johannesburg, rail expanded, theme via localStorage `kazi-theme`, Node 20.11.1 (nvm), as Alice (owner).
- **Raw tiles:** `/tmp/665C-after-fix-2-raw/`, outside the repo.

## Provenance

- **Frontend:** `e2e-frontend` was created `2026-11-26T19:45:52.457983172Z`, image `sha256:d20ff9430acd8a8b67bb87cb1041aa8c3b5a4f9840746170bc9ae84599166e08`. Its compose `working_dir` label is `/Users/…/worktree-epic-665C/compose`. The container was created after the commit (`b87d6d742`, 19:44:41Z).
- **Served build carries the fix:** in all 8 records the DOCUMENT REQUIRED `<th>` has class `whitespace-normal` and computed `white-space: normal`. The other 4 headers are still `nowrap` (`checks.json` → `summary.drHeaderHasClass`, `headers[].cls`/`ws`).
- **Backend:** `e2e-backend` was created `2026-11-26T19:00:43.865Z`, image `sha256:e7fd89e47741…`. This is the same container as OBS-after and after-fix. `/actuator/health` was UP and `/mock-login` returned 200.
- **Worktree:** HEAD `b87d6d742cad78af6fc4fd4eeda4b1952b8a5f63`. `git status --short frontend packages` was **empty**.
- **Stack:** it was not restarted, rebuilt or reseeded, and no e2e spec was run by this OBS.
- **Data:** the same as after-fix. The ids are PLATFORM `ef084b7a-f1d5-46be-8657-2ed12d7474b0` (4 items, all Required, Document Required "No") and ORG_CUSTOM `561c8fea-38c1-48f9-b1ac-23c6d2dc29ef` (1 item, "Proof of address", Optional, "No").

## Scripts (`scripts/`)

| Script | What |
|---|---|
| `lib.cjs` | A copy of `../after-fix/scripts/lib.cjs`, which only accepts `PHASE=before\|after\|after-fix`. The only change is that it also accepts `after-fix-2` (the diff is below). `../after-fix/` was not edited |
| `11-checklist-items-header.cjs` | New and read-only. It checks the two checklists × 1024/1440 × light/dark (see the list after this table). It writes `checks.json`, the two sheets and the crop |

`11-checklist-items-header.cjs` checks:
- the scroller `clientWidth`/`scrollWidth`;
- `docHOverflowPx`;
- per `<th>`: the class, `white-space`, `vertical-align`, width and line count. It also checks that every text rect sits inside the `<th>` and inside the scroller box, and that `scrollWidth <= clientWidth`;
- every Required badge and Document Required cell, in the box and untruncated;
- the h1 count in `#main-content`;
- the console sink (errors, warnings and anything matching `/hydrat/`).

```diff
2c2
< // not edited). PHASE=before|after|after-fix (default before) picks the output dir (../../$PHASE/) and
---
> // not edited). PHASE=before|after|after-fix|after-fix-2 (default before) picks the output dir (../../$PHASE/) and
24c24
< if (!["before", "after", "after-fix"].includes(PHASE)) throw new Error(`PHASE must be before|after|after-fix, got ${PHASE}`);
---
> if (!["before", "after", "after-fix", "after-fix-2"].includes(PHASE)) throw new Error(`PHASE must be before|after|after-fix|after-fix-2, got ${PHASE}`);
```

The script was run once as `PHASE=after-fix-2 node 11-checklist-items-header.cjs` and exited 0.

## Checks

The "after" values come from `../after/measurements.json` → `personas.alice.variants.checklists-id-*`. The after-fix values come from `../after-fix/fix-checks.json` → `checklist`. The after-fix-2 values come from `checks.json` → `checklist`.

### 1. Items-table scroller, clientWidth/scrollWidth (expected scrollWidth ≤ clientWidth)

| Checklist | Variant | After (`140320f9c`) | After-fix (`3b0ac2574`) | After-fix-2 (`b87d6d742`) | Result |
|---|---|---|---|---|---|
| PLATFORM | 1024 light | 446/1009 | 446/487 | **446/446** | **PASS** |
| PLATFORM | 1024 dark | 454/1009 | 454/487 | **454/454** | **PASS** |
| PLATFORM | 1440 light | 814/1009 | 814/814 | **814/814** | **PASS** |
| PLATFORM | 1440 dark | 822/1009 | 822/822 | **822/822** | **PASS** |
| Custom | 1024 light | 446/497 | 446/446 | **446/446** | **PASS** |
| Custom | 1024 dark | 454/497 | 454/454 | **454/454** | **PASS** |
| Custom | 1440 light | 814/814 | 814/814 | **814/814** | **PASS** |
| Custom | 1440 dark | 822/822 | 822/822 | **822/822** | **PASS** |

### 2. Other checks (all 8 records)

| # | Check | After | After-fix | After-fix-2 | Result |
|---|---|---|---|---|---|
| 2a | `docHOverflowPx` | 0 | 0 | **0** in all 8 | **PASS** |
| 2b | Headers fully visible (`<th>` and its text inside the scroller box, no `<th>` overflow) | 1024 cut; 1440 PLATFORM DR off-screen | PLATFORM 1024 light and dark: DOCUMENT REQUIRED cut by 41 px ("DOCUMENT REQU"), `<th>` right edge 1008 vs scroller 967. Every other record whole | **`headersAllVisible: true` in all 8.** DOCUMENT REQUIRED wraps to 2 lines in 6/8 records: PLATFORM at 95 px (1024) and 138/139 px (1440), custom at 133/137 px (1024). It stays on 1 line in custom at 1440 (264/266 px). The other 4 headers are 1 line and `nowrap` | **PASS** |
| 2c | "Required" badges | 1440 "Requir" cut; 1024 hidden | all visible | PLATFORM **4/4** in the box and untruncated in all 4 variants. The custom checklist has no Required item; its "Optional" cell is in the box | **PASS** |
| 2d | Document Required cells | 1440 off-screen | all visible | Every cell ("No") is in the box and untruncated in all 8 | **PASS** |
| 2e | h1 in `#main-content` | 1 | 1 | **exactly 1** in all 8: "Generic Client Onboarding" or "OBS 665C Checklist" | **PASS** |
| 2f | Console | 0 / 0 | 0 / 0 | 9 page loads (8 checks and 1 crop): **0 errors, 0 warnings, 0 hydration** (`checks.json` → `console.n = 0`) | **PASS** |
| 2g | Header alignment | — | — | Every `<th>` is `vertical-align: middle`. The 2-line DR header's text starts 8 px above the 1-line headers' text in every wrapped record (for example, PLATFORM 1024 light: top 475 vs 483), so it is centred on the same midline | **PASS** |

## Eyeball (each file opened with Read)

| File | Eyeball |
|---|---|
| `sheet-checklist-pack-alice.png` | **PLATFORM, all 4 tiles:** all 5 columns fit inside the card at both widths and in both themes. At 1024 the header reads "DOCUMENT / REQUIRED" on two lines; the after-fix sheet showed it cut as "DOCUMENT REQU". All 4 red "Required" pills and the "No" cells are whole. Name and Description still wrap. At 1440 the header also wraps, into a narrow column, and every column is whole. There is no horizontal scrollbar and nothing is clipped |
| `sheet-checklist-custom-alice.png` | **Custom, all 4 tiles:** the single row "Proof of address / — / Optional / No" fits at both widths. At 1024 the DR header wraps to two lines; at 1440 it is one line. Nothing is clipped, the dark tiles are legible, and the layout is unchanged from after-fix apart from the header line break |
| `crop-checklist-pack-header-1024-light-3x.png` | The header row reads `# · NAME · DESCRIPTION · REQUIRED · DOCUMENT / REQUIRED`. "DOCUMENT" and "REQUIRED" stack left-aligned with the "No" cell below. The two-line block is vertically centred against the one-line headers, which sit on its midline. The letter-spacing and uppercase style match the other headers. There is clear space between it and the REQUIRED column and the card edge |

**Does the wrapped header look right?** Yes. It is aligned with its column and with the other headers, which sit on a shared midline, and it is not cramped: the 95 px column leaves padding on both sides of "DOCUMENT".

**Nit (not a finding):** table auto-layout wraps the header even when there is room for one line. Examples are custom at 1024 (133 px) and PLATFORM at 1440 (138 px). Custom at 1440 (264 px) stays on one line. The header row is 2 lines tall in those cases. This is a cosmetic consequence of `white-space: normal` under auto layout, with no clipping. It is left to the orchestrator.

## found.md

- In the F-665C-15 row, `checklist-items-table.tsx:60` was corrected to `:61`, the `meta` line that carries `headerClassName`. That was the only edit.
