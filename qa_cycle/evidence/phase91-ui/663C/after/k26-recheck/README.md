# 663C K-26 re-check after `c4d1f7675`: breadcrumbs at 1024×768

Captured 2026-11-26 11:04 UTC by `scripts/01-capture.cjs`. Result: **26/28 captures PASS, 2 FAIL.**
The middle-crumb over-truncation is fixed. A **new defect** shows on the 5-crumb template-editor route with the rail expanded: the org and last crumbs collapse to 0 px, and the crumb row runs under the Search button.

## Stack provenance
- **Fresh stack.** The stack was torn down with `e2e-down.sh` and `docker builder prune -f` was run (2.199 GB reclaimed). It was then rebuilt with `e2e-up.sh` from this worktree, and the containers were created 10:48:45 UTC.
  - `docker inspect e2e-frontend` gives `com.docker.compose.project.working_dir` = `worktree-epic-663C/compose`.
- **Code under test.** The worktree HEAD is `ab5ea53cd`. Its frontend and backend code is identical to `c4d1f7675` (`git diff c4d1f7675 ab5ea53cd -- frontend backend` is empty).
  - The running frontend bundle contains the new class string `hidden shrink-0 items-center gap-1 whitespace-nowrap sm:flex`. It does not contain the old `hidden min-w-0 … sm:flex`. Both were checked by grepping `.next` inside the container.
- **Data.** The data is what the four e2e suites left on this stack (legal-lifecycle, legal-depth-ii, navigation, default), run just before this capture. There was no reseed.

## What changed (`c4d1f7675`, `frontend/components/breadcrumbs.tsx:190`)
- **Before.** At 84f867eef, middle crumb wrappers were `hidden min-w-0 … whitespace-nowrap sm:flex`, so every crumb shrank together.
- **Now.** They are `hidden shrink-0 … whitespace-nowrap sm:flex`.
  - Middle crumbs keep their natural width, capped by the label's `max-w-40` (160 px, with the full label in `title`).
  - Only the org crumb (`max-w-40 truncate`) and the last crumb wrapper (`min-w-0`) can shrink.

## Method
The method follows the committed `after/scripts/03-capture.cjs` and `05-capture-extra.cjs`, which are untouched. Every difference is listed in the first lines of `scripts/01-capture.cjs`.
- **Browser setup.**
  - Playwright library (`chromium.launch()`), not MCP.
  - Login uses `661B/walk/scripts/common.cjs`, required unedited. Alice signs in through `/mock-login`; Carol uses the mock-auth-token cookie.
  - Viewport 1024×768. Theme set through localStorage `kazi-theme`, rail state through cookie `sidebar_state`.
  - Reduced motion on, timezone Africa/Johannesburg.
- **Ids were re-discovered, read-only.**
  - INV-0005 (SENT) through `GET /api/invoices`.
  - The longest-named template through `GET /api/templates`.
  - Carol's first matter, Alice's first client and the first checklist template from the UI lists.
  - No SQL, no REST writes, no form input.
- **Per crumb, the script records:**
  - wrapper width `w` (the same element `03-capture.cjs` measured);
  - label width;
  - natural text width (a Range over the label);
  - `scrollWidth > clientWidth`, reported as truncated;
  - the `title` attribute.
- **Per page, it records:** nav height, and whether any crumb's right edge passes the nav or the first header control to its right.
- **Measurement change vs `05`.** Crumbs are not filtered by `width > 0`. With that filter, a crumb that collapses to 0 px silently disappears and the roles shift; on the template route it would have reported "Template" as the last crumb.
- **Verdict per capture:**
  - nav = 20 px (one line);
  - org crumb ≤ 160 px;
  - no middle crumb truncated unless its natural label width is over 160 px;
  - last crumb visible (label ≥ 1 px and inside the nav);
  - no crumb drawn past the nav or under a header control.
- **Pages:**
  - Alice: fee note INV-0005, `/trust-accounting/transactions`, `/settings/documents/templates/<id>/edit` (5 crumbs).
  - Carol: `/trust-accounting/transactions`, her first matter.
  - Additions:
    - **Client edit:** no client edit route exists (no `clients/[id]/edit` under `frontend/app`), so Alice's client detail `/clients/<id>` is captured instead.
    - **Checklist editor:** `/settings/matters/checklists/<id>/edit` redirects to the 4-crumb detail page, because seeded templates are not `ORG_CUSTOM` (`checklists/[id]/edit/page.tsx:56-57`). It is captured as a 4-crumb page under the key `checklist-edit`.

## Before (84f867eef, from `../measurements.json` and `../README.md` K-26) → now
Widths are wrapper px (chevron + gap + label; the org crumb has no chevron). "…" means truncated.
"Exp" is the rail expanded and "col" is the rail collapsed. The nav is 20 px (one line) in every capture, before and now.

| Page · persona | Rail · theme | Before: org / middle… / last | Now: org / middle… / last | Middle truncated now? | Last crumb now | Verdict |
|---|---|---|---|---|---|---|
| Fee note · Alice | exp · light | 141 / "Fee not…" 81 / "INV-00…" 82 | 140… / Fee notes 81 / INV-0005 82… (64 of 65 px) | no | visible, 1 px sub-pixel ellipsis | **PASS** |
| Fee note · Alice | exp · dark | 142 / 81 / 83 | 142 / 81 / 83 | no | full | **PASS** |
| Fee note · Alice | col · light/dark | 142 / 81 / 83 | 142 / 81 / 83 | no | full | **PASS** |
| Trust txns · Alice | exp · light | 116 / "Trust acco…" 103 / "Transac…" 84 | 103… / **Trust accounting 126** / Transa… 74 | **no** (fixed) | 56 px label, truncated | **PASS** |
| Trust txns · Alice | exp · dark | 119 / 106 / 86 | 107… / 126 / 78… | no | 60 px, truncated | **PASS** |
| Trust txns · Alice | col · light/dark | 142 / 126 / 103 | 142 / 126 / 103 | no | full | **PASS** |
| Trust txns · Carol | exp · light / dark | 116 / 103 / 84 ; 119 / 106 / 86 | 103… / 126 / 74… ; 107… / 126 / 78… | no | 56 / 60 px, truncated | **PASS** |
| Trust txns · Carol | col · light/dark | 142 / 126 / 103 | 142 / 126 / 103 | no | full | **PASS** |
| Matter · Carol | exp · light | 102 / **"M…" 49** / 152 | 96… / **Matters 68** / Ndlovu v RAF m… 140 | **no** (fixed) | 122 px label of 190 | **PASS** |
| Matter · Carol | exp · dark | 105 / 50 / 156 | 99… / 68 / 145… | no | 127 px of 190 | **PASS** |
| Matter · Carol | col · light/dark | 142 / 68 / 211 | 142 / 68 / 208 | no | full | **PASS** |
| **Template edit · Alice (5 crumbs)** | **exp · light** | 81… / S… 41 · Do… 52 · Te… 48 · T… 44 / **"E" 25** | **0… / Settings 71 · Documents 92 · Templates 84 · Template 77 / Edit 0** | no | **0 px: not visible**. The crumbs end at x = 683, past the nav (649) and **under Search (x = 661)** | **FAIL** |
| **Template edit · Alice (5 crumbs)** | **exp · dark** | 83… / 42 · 54 · 50 · 45 / "E" 26 | **0… / 71 · 92 · 84 · 77 / 0** | no | **0 px**; crumbs end at 683, under Search (x = 669) | **FAIL** |
| Template edit · Alice | col · light | 132… / 67 · 85 · 79 · 72 (all …) / "E…" 41 | 115… / 71 · 92 · 84 · 77 / "E…" 35 (17 px label) | no | visible as "E…" | **PASS** (barely readable) |
| Template edit · Alice | col · dark | 141… / 71 · 91… · 84 · 77 / 43… | 140… / 71 · 92 · 84 · 77 / "E…" 43 (25 px label) | no | visible as "E…" | **PASS** (barely readable) |
| Checklist detail · Alice (4 crumbs, new) | exp · light / dark | (not captured before) | "E2E…" 36 / Settings 71 · Matters 68 · Checklists 85 / **"C…" 36** (18 px label) | no | visible as "C…" only | **PASS** (barely readable) |
| Checklist detail · Alice | col · light / dark | — | 128… / 71 · 68 · 85 / "Checklist tem…" 128 ; 142 / 71 · 68 · 85 / full 141 | no | readable | **PASS** |
| Client detail · Alice (new; no edit route) | all 4 | — | 142 / Clients 62 / Acme Corp 91 | no | full | **PASS** |

- **Pass/fail by page:**
  - The 3-crumb pages pass: fee note, trust transactions (Alice and Carol), Carol's matter, client detail.
  - The checklist detail passes.
  - Template edit fails with the rail expanded (light and dark) and passes with it collapsed.
- **Fixed by `c4d1f7675`:** no middle crumb is truncated in any of the 28 captures. "Matters" grew 49 → 68 px and "Trust accounting" 103 → 126 px. Every middle label is under 160 px, so none qualifies for truncation.
- **Unchanged:**
  - The nav is one line (20 px) and never wraps.
  - The org crumb is ≤ 142 px with `title`.
  - Every crumb label carries `title`.

## FAIL: 5-crumb route at 1024, rail expanded (new in `c4d1f7675`)
- **Measurements.** The nav is 311 px wide (319 dark). The four middle crumbs are now `shrink-0` and need 71 + 92 + 84 + 77 = 324 px plus 3 × 4 px gaps, more than the whole nav.
- **What renders.** Only the org crumb and the last crumb can shrink, so both collapse to 0 px.
  - The tile shows a lone leading chevron where the org crumb was. "Edit" is gone; its only trace is its `title`.
  - The remaining row overflows the nav. The nav is `min-w-0` with no overflow clip, so "Template" is drawn under the header Search button. The crumbs' right edge is 679–683 against Search's left edge at 661/669; see the sheet.
- **Compared with before.** At 84f867eef this capture was ugly but contained: every crumb was cut, the last showed "E", and nothing overflowed. This is a regression for long chains, caused by `breadcrumbs.tsx:190` (`shrink-0` with no floor or overflow handling).
- **Related, below the FAIL bar:** with the rail expanded, the last crumb can shrink to one letter: "C…" (18 px) on the 4-crumb checklist detail, and "E…" with the rail collapsed on template edit. It is still visible, so it meets the register's "last crumb visible", but it is barely readable.
- **Possible directions (orchestrator/user decision; no code changed here):**
  - let middle crumbs shrink with a floor, e.g. `min-w-*`, or a lower `shrink-[…]` weight than the org crumb;
  - give the last crumb a minimum width;
  - collapse interior crumbs to a "…" when the chain is longer than about 3.

## Console
- 0 errors and 0 hydration warnings over 28 captures and 3 discovery loads.
- 4 warnings, all `[tiptap warn]: Duplicate extension names found: ['link', 'underline']` on the template editor. These were already seen at 84f867eef; the editor is outside the 663C diff.

## Files
- `scripts/01-capture.cjs`: discovery, capture, verdicts and the two contact sheets.
- `measurements.json`: one line. It holds `pages` (discovered ids), `captures[<who>-<page>-<rail>-<theme>]` (per-crumb data plus `verdict`), `summary` and `console`.
- `sheet-k26r-3crumb-1024.png`: fee note, trust transactions (Alice, Carol) and Carol's matter. Columns are rail expanded / collapsed; rows are page × theme.
- `sheet-k26r-long-chain-1024.png`: template editor (5 crumbs), checklist detail and client detail, laid out the same way.
- Raw tiles and `tiles.json` are in `.tmp/663C-k26-raw/`, which git ignores.

## Deviations
- The capture uses Playwright library scripts, not MCP, as in the before and after passes.
- **No client edit route exists**, so client detail was captured instead.
- The checklist editor route redirects to its detail page for seeded (non-`ORG_CUSTOM`) templates, so it is a 4-crumb page, not 5. Making an `ORG_CUSTOM` template would need a UI write, and this pass is read-only.
- The script was run twice. The first run used `05`'s `width > 0` crumb filter, which mislabelled the template-edit failure as "last crumb = Template". The filter was removed and the script re-run. Only the second run's output is kept; the raw dir was wiped between runs.
