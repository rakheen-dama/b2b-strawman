# 665C OBS after-fix: F-665C-10 / 11 / 12 re-observed, regression sweep

- **When:** 2026-11-26, 19:35Z to 19:42Z.
- **PR / head:** #1872, `3b0ac2574` (branch `epic-665C/settings-matters`), the fix commit for F-665C-10/11/12.
- **Verdict:** **F-665C-10 PASS, F-665C-11 PASS, K-15 PASS, regression sweep PASS. F-665C-12 PASS at 1440 and on the custom checklist; FAIL (residual) at 1024 on the PLATFORM checklist:** the items table is 446/487. All body cells and every "Required" badge are visible, but the **DOCUMENT REQUIRED header** is cut by 41 px ("DOCUMENT REQU"). Before had 446/452 here. New finding **F-665C-15**, below.
- **What changed:** no product code was changed and no SQL was run. Reads went through `GET`; the only writes were 2 tags × 2 runs, created and deleted through the UI.
- **Browser:** the Playwright library (headless Chromium from the main checkout's `frontend/node_modules`), not the MCP. DPR 1 (crops: DPR 3), reduced motion, timezone Africa/Johannesburg, rail expanded, theme via localStorage `kazi-theme`, Node 20.11.1 (nvm).
- **Raw tiles:** `/tmp/665C-after-fix-raw/`, outside the repo.

## Provenance

- **Frontend:** `e2e-frontend` was created `2026-11-26T19:33:07.351Z`, image `sha256:67ef5197ef9465d6d02da1b8c92b7b1c5c00f56ebcca53389de08923ad661e67`. Its compose `working_dir` label is `/Users/…/worktree-epic-665C/compose`.
- **Backend:** `e2e-backend` was created `19:00:43.865Z`, image `sha256:e7fd89e47741…`. This is the same container as OBS-after.
- **Worktree:** HEAD `3b0ac2574a24a241b445144acc3cd387dd8ec737`. `git status --short frontend packages` was **empty**.
- **Stack:** `/actuator/health` UP, `/mock-login` 200. It was not restarted, rebuilt or reseeded, and no e2e spec was run by this OBS.
- **Data drift from OBS-after:**
  - Tags held 2 tags before this OBS started: "Applied Tag y7h2" (no colour) and "E2E Tag y7h2" (`#3B82F6`). OBS-after ended with 0 tags.
  - These match `tags.spec.ts`'s naming, which fits the fixer's `test:e2e:settings` run cited in found.md F-665C-13.
  - They were left untouched. So the Tags tab has 4 rows here, not the 2 that OBS-after saw.
  - The Naming pattern is still `" "` (`GET /api/settings` → `matterNamingPattern: " "`, the F-665C-3 state), so the Pattern input shows no placeholder.

## Scripts (`scripts/`)

**Copied unchanged** from `../after/scripts/` (`diff` is empty), and run with `PHASE=after-fix`: `lib.cjs` and `07-after-checks.cjs`. Their outputs are `after-checks.json` and `sheet-tags-rows-k15-alice.png`.

**New:**

| Script | What |
|---|---|
| `09-fix-checks.cjs` | Creates "OBS 665C Dark" `#1E293B` and "OBS 665C Light" `#FDE68A` through Add Tag. Then, per 1024/1440 × light/dark: <br>• **Templates / Tags:** the computed `color` of the Delete / Edit / Duplicate buttons vs a probe `<span style="color: var(--destructive)">` in `#main-content`, at rest and on `hover()` (bg too). <br>• **`checklists/{id}`** for PLATFORM "Generic Client Onboarding" and ORG_CUSTOM "OBS 665C Checklist" (ids found by name via `GET /api/checklist-templates`): the scroller widths, the Name / Description `white-space` and line count, and whether every Required badge and Document Required cell sits inside the scroller box untruncated. <br>• **Naming:** the composited background of each `<code>` chip vs its box. <br>• **K-15:** the composited tag chips. <br>• **docHOverflowPx** on every page. <br>It then writes the 4 sheets and the 3× action crops, and deletes both tags through the UI in a `finally`. Writes `fix-checks.json` |
| `09b-crop-naming-chips.cjs` | Read-only: 3× crops of the placeholders box (light, dark), and a GET of the stored pattern |
| `09c-checklist-header-probe.cjs` | Read-only diagnostic for the 1024 residual. It measures the `<th>` widths, then sets `white-space: normal` on the `<th>`s **in the page's memory only** and re-measures. Its stdout is `header-probe.json` |
| `10-compare.cjs` | Offline. Compares `../after/after-checks.json` with `after-checks.json` over the union of surface × variant keys → `compare.json` |

- **Executed, in this order:** `07` → `09` → `09b` → `09c` → `10`. Each exited 0 and ran once.
- **Tags at the end:** `["Applied Tag y7h2", "E2E Tag y7h2"]`, the same as at the start, after both 07 and 09.

## Checks

| # | Check | Before | After (`140320f9c`) | After-fix (`3b0ac2574`) | Result | Evidence |
|---|---|---|---|---|---|---|
| 1a | **F-665C-10** Templates Delete, dark | `dark:text-red-400` (red) | `lab(65.14 -0.85 -6.68)` = Edit grey | `lab(63.71 60.75 31.31)` **= `--destructive`** (`#ff6467`), at 1024 and 1440 | **PASS** | `fix-checks.json` → `icons`, `crop-fixed-templates-actions-dark-3x.png` |
| 1b | **F-665C-10** Tags Delete, dark (with rows) | red | grey (07c crop) | `#ff6467` **= token** at 1024 and 1440, on all 4 rows | **PASS** | `icons["tags|*-dark"]`, `crop-fixed-tags-actions-dark-3x.png` |
| 1c | Delete, light (Templates + Tags) | `text-red-600` | `lab(48.45 77.43 61.55)` | `#e7000b` **= token** at 1024 and 1440 | **PASS** | `icons["*|*-light"]` |
| 1d | Delete, on hover | red | dark: slate-100 | color = token in all 8 cases (`:hover` true). bg `destructive/10`: `#e2000a` α 0.10 light, `#ff6262` α 0.10 dark | **PASS** | `hover` |
| 1e | Edit / Duplicate stay neutral | slate | light `#3a4049`, dark `#989faa` | Same: light `#3a4049`, dark `#989faa`, ≠ token. On hover: light `#07080b` on `#eff1f5`, dark `#eff1f5` on `#20242b` | **PASS** | `icons`, `hover` |
| 2a | **F-665C-12** PLATFORM items scroller (client/scroll), 1440 | 814/814 | 814/1009 (822/1009 dark) | **814/814**, **822/822** dark | **PASS** | `checklist["pack|1440-*"]` |
| 2b | **F-665C-12** PLATFORM, 1024 | 446/452 | 446/1009 (454/1009 dark) | **446/487**, 454/487 dark. The DOCUMENT REQUIRED `<th>` ends at x=1008, past the scroller's right edge at 967 | **FAIL (residual)** | `checklist["pack|1024-*"]`, `crop-fixed-checklist-items-1024-light.png`, `header-probe.json` |
| 2c | Custom "OBS 665C Checklist" scroller | 446/446 at 1024 | 446/497 at 1024 | **446/446**, 454/454, 814/814, 822/822 | **PASS** | `checklist["custom|*"]` |
| 2d | Name and Description wrap | wrapped | `nowrap` | `white-space: normal` on every row. PLATFORM max lines: 1024 Name 4 / Description 7; 1440 Name 2 / Description 2 | **PASS** | `checklist.*.summary` |
| 2e | "Required" badge and Document Required cells visible | yes | 1440 "Requir" cut, DR off-screen; 1024 hidden | All 4 badges in the box and untruncated at both widths. Every DR cell ("No") is in the box. **Only the DR header text is cut at 1024 (2b)** | **PASS** (cells) | `checklist.*.rows` |
| 2f | `docHOverflowPx`, checklist detail | 0 | 0 | **0** × 2 templates × 4 variants | **PASS** | `docHOverflowPx` |
| 3 | **F-665C-11** Naming chip bg vs box bg | slate-200 chip on slate-50 box (found.md) | `bg-muted` on `bg-muted`: the same colour, no chip | light `#fdfeff` on `#eff1f5` (**1.12:1**); dark `#07080b` on `#20242b` (**1.29:1**); distinct in all 4 variants. Chip text vs chip: 7.57:1 light, 7.51:1 dark | **PASS** | `naming`, `crop-fixed-naming-chips-{light,dark}-3x.png` |
| 4 | **K-15** tag chips (text vs fill) | — | Dark 14.63:1, Light 16.08:1 | "OBS 665C Dark" `#ffffff` on `#1e293b` **14.63:1**; "OBS 665C Light" `#07080b` on `#fde68a` **16.08:1**, in all 4 variants (07 and 09 agree). Drift tags: "E2E Tag" `#07080b` on `#3b82f6` 5.45:1; "Applied Tag" (no colour) 10.35:1 light / 11.58:1 dark | **PASS** | `chips`, `after-checks.json` → `tags.variants` |
| 5a | One h1 in `#main-content`, text the same as after | — | 1 per tab | **72/72** surface × variant records have identical h1 text (the 8 tabs, the wizard, 9 sub-pages). The 8 tabs at 1024 light/dark each have 1: Matter Templates, Matter Naming, Custom Fields, Tags, Checklists, Request Templates, Request Settings, Take-on. The wizard is 0, as after (F-665C-5, known) | **PASS** | `compare.json` → `sweep`, `h1AllSame` |
| 5b | `docHOverflowPx` | 0 | 0 | **0** on all 72 records and all 09 pages | **PASS** | `compare.json` → `maxOverflow`, `fix-checks.json` |
| 5c | Console | 0 / 0 | 0 / 0 | 07: 78 loads; 09: 24 loads. **0 errors, 0 warnings, 0 hydration** | **PASS** | `after-checks.json` → `console`, `fix-checks.json` → `console` |
| 5d | Other probe deltas, after → after-fix | — | — | **Only** the checklist-detail tables (fixed) and the Tags tab. Tags: EmptyState → a table, because of the 2 drift tags; at 1024 it is 446/465, with ACTIONS reachable by scroll. Low-contrast counts, nested frames, bordered boxes and white boxes in dark are all unchanged | **PASS** | `compare.json` → `diffs` |

- **Probe caveat:** in 07's `CHIPS`, "Applied Tag" (no colour) reads as `#3a4049` on `#000000`, 2.01:1, in light. That is an artefact of the probe: its `rgb()` paints `#000` and then fails to parse `rgba(0,0,0,0)` over it, so a transparent fill reads as black.
- 09's composited measure gives `#3a4049` on `#fdfeff`, 10.35:1. This is not a defect.

## Per-sheet eyeball (each opened next to its `after/` sheet)

| Sheet | vs after |
|---|---|
| `sheet-templates-alice` | The layout is identical. **1440 dark: the trash icons are now red** (grey in after); light is still red. At 1024 the Delete column sits past the card edge and is reachable by scroll, as in after. Nothing new clipped |
| `sheet-tags-rows-alice` (vs `after/sheet-tags-rows-k15-alice`) | 4 rows instead of 2 (drift tags). **The dark Delete icons are red at 1024 and 1440** (grey in after). The chips are legible in both themes. At 1024 the tab strip still starts at "…plates", the same as after. The sheet title says "2 UI-made tags": the other 2 rows are the drift tags |
| `sheet-tags-rows-k15-alice` (07 re-run) | The same view as `sheet-tags-rows-alice`, with chip ratios in the captions. Red Delete in dark |
| `sheet-sub-checklists-detail-alice` | **PLATFORM:** Name and Description now wrap. At 1440 all 5 columns fit and the "Required" badges are whole (after: "Requir" cut, DR off-screen). At 1024 the #, Name, Description, Required and "No" cells all show (after: only #, NAME and a cut DESCRIPTION), **but the header reads "DOCUMENT REQU"** (F-665C-15). **Custom:** "Proof of address" wraps and every column fits at 1024 (after: the DR header cut) |
| `sheet-naming-alice` | The chips now show as white pills in light and near-black pills in dark, inside the muted box (after: invisible chip backgrounds). The Pattern input looks empty because the stored value is `" "` (F-665C-3 state, not a regression). The footer save is unchanged |
| `crop-fixed-*` | Templates/Tags dark 3×: Edit / Duplicate grey, trash red. Checklist 1440: every column whole. Checklist 1024: the "DOCUMENT REQU" header is cut. Naming 3×: the chips are clearly distinct in both themes |

## New finding

**F-665C-15** (introduced by 665C; the F-665C-12 residual, appended to `../found.md`):

- **Where:** `checklists/{id}`, PLATFORM "Generic Client Onboarding", at 1024.
- **Symptom:** the items table is still 446/487, and the "DOCUMENT REQUIRED" header is cut by 41 px.
- **Cause:** the DataTable `TableHead` is `whitespace-nowrap tracking-wider uppercase` (`frontend/components/ui/table.tsx:71`). That makes the 2-word header 161 px wide, and it sets the table's minimum width.
- **Proof (`header-probe.json`):** setting `white-space: normal` on that one `<th>`, in the page only, gives **446/446**. The header becomes 95 px across 2 lines.
- **Suggested fix:** `meta.headerClassName: "whitespace-normal"` on the Document Required column (`checklist-items-table.tsx:58-59`; the meta key exists at `components/data-table/define-columns.tsx:128`).
- **Before:** the old hand-rolled `<th>` (`px-3`, no nowrap) was 446/452.
