# 663C K-26 final: breadcrumb shrink order at 1024×768

Result: **all 5 acceptance points PASS** on the real build of `7149f9c11`. That is 28/28 desktop captures (1024×768, rail expanded and collapsed, light and dark) plus 8/8 mobile captures.
This is the last fix round for K-26. It replaces the `c4d1f7675` behaviour, which failed on the 5-crumb template editor (`../k26-recheck/README.md`).

## The rule (`frontend/components/breadcrumbs.tsx`)
Each crumb's flex-shrink weight is 10,000× the next one's, and each crumb has a floor. A crumb's share of the shrink is its weight × its width, and it stops at its floor.

| Crumb | Classes added | Weight | Floor |
|---|---|---|---|
| Org (root link) | `min-w-6 shrink-100000000` (keeps `max-w-40 truncate`, `title`) | 1e8 | 24 px |
| Middle wrapper | `min-w-0 shrink-10000` (was `shrink-0`; label keeps `max-w-40 truncate`, `title`) | 1e4 | 0 |
| Last wrapper (current page) | `min-w-12` (was `min-w-0`; default `shrink` = 1) | 1 | 48 px including the chevron, so "Edit" (43.6 px) stays whole |

Unchanged: the nav (`flex min-w-0`), the mobile classes (`hidden … sm:flex` / `sm:inline`, last chevron `hidden sm:block`), labels and copy. No `dark:` classes and no JS.

## Commits
- `5cf74b647`: iteration-1 winner, with weights 1e6 / 100 / 1.
- `7149f9c11`: weights raised to 1e8 / 1e4 / 1. With 1e6 / 100 / 1, the real build showed "Checklist templa…": the last crumb lost 0.6 px on the 4-crumb route at 1024 with the rail expanded. See iteration 2 below.

## Prototype (in the browser, before any code was committed)
- **Method.**
  - `scripts/02-prototype.cjs` loaded every page in the k26-recheck matrix on the served `c4d1f7675` bundle.
  - For each candidate it set inline `flex-shrink` / `min-width` on the org link, the middle wrappers and the last wrapper. (Arbitrary Tailwind classes would not be in the built CSS, so inline styles stand in.)
  - It then measured every crumb.
  - Results: `prototype.json`.
- **Iteration 1** (28 captures per candidate; pass counts against acceptance points 1–4; truncation detected as `scrollWidth > clientWidth`):

| Candidate (org weight/floor · middle weight/floor · last weight/floor) | Pass | Why it failed |
|---|---|---|
| c0: `c4d1f7675` as served | 23 | Template edit, rail expanded: the org and "Edit" collapse to 0 and the crumbs overflow under Search. "Edit" shows as "E…" with the rail collapsed. "C…" (17.9 px) on the checklist page. |
| c1: 1e4/0 · 1/0 · 1/0 (the suggestion as written) | 26 | "Edit" cut to 16.5 / 17.4 px. Nothing protects the last crumb. |
| c2: 1e3/24px · 1/0 · 1/48px | 26 | "Matters" draws an ellipsis at 49.5/49.5 px. **Weight ratio 1,000 is not enough:** the middle loses a sub-pixel while the org shrinks. |
| c3: 1e6/0 · 100/0 · 1/44px | 28 | Passes, but the org reaches 0 px (a lone leading chevron). |
| c4: 1e6/24px · 100/0 · 1/44px | 28 | 44 px is only 0.4 px above "Edit"'s natural 43.6 px. |
| c5: 1e6/24px · 100/0 · 1/48px | 28 | **Picked** and committed as `5cf74b647`. |
| c6: 1e5/24px · 10/0 · 1/48px | 28 | "Checklist template" truncated to 117.9 px. The pass count does not show it: the iteration-1 check only needs ≥ 20 px. |
| c7: 1e6/32px · 100/0 · 1/48px | 27 | "Matters" truncated: the org floor of 32 px is above the 28 px the matter page needs. |
| c8: 1e8/24px · 1e4/0 · 1/48px | 28 | — |

- **Iteration 2** (`scripts/02b-prototype-iter2.cjs`, results in `prototype-iter2.json`).
  - It ran on the served `5cf74b647` bundle after the real-build capture showed "Checklist templa…".
  - It adds a **sub-pixel** check (label narrower than its natural text by more than 0.05 px), because Chromium draws an ellipsis even when `scrollWidth == clientWidth`.
  - Results:
    - served `5cf74b647` and c5: 26/28, failing on "Checklist template" at 122.8/123.4 px (light) and 122.9/123.4 px (dark);
    - **c8: 28/28** → committed as `7149f9c11`.
- **Iteration 1's blind spot.** Its `truncated` flag missed sub-pixel cuts. Iteration 2's check, and `scripts/05-acceptance.cjs`, close that gap.

## Unit tests (`frontend/components/__tests__/breadcrumbs.test.tsx`)
Four K-26 tests assert the classes:
- org: `max-w-40 truncate min-w-6 shrink-100000000` and `title`;
- middle wrapper: `min-w-0 shrink-10000 whitespace-nowrap`, not `shrink-0`;
- long middle: 160 px cap and `title`;
- last wrapper: `flex min-w-12`, no `min-w-0`, no `shrink-*`; "Edit" has `title`.

Results by product code:

| Product code | Result |
|---|---|
| `c4d1f7675` | 4 failed / 57 passed |
| `5cf74b647` | 3 failed / 58 passed |
| `7149f9c11` | 61/61 passed |

## Real-build provenance
- **Rebuild.** `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` was run from this worktree at `7149f9c11`.
  - Exit 0; `e2e-frontend` was created 2026-11-26T11:28:15Z.
  - `working_dir` = `worktree-epic-663C/compose`.
  - Only the frontend container was recreated; backend and data were untouched, with no reseed.
- **The served `.next` contains:**
  - `.shrink-100000000{flex-shrink:100000000}`, `.shrink-10000{flex-shrink:10000}` and `.min-w-12{…}`;
  - the middle class string `hidden min-w-0 shrink-10000 items-center gap-1 whitespace-nowrap sm:flex` (2 files);
  - 0 files with the `c4d1f7675` string (`hidden shrink-0 …`) or the `5cf74b647` string (`shrink-100 …`).

## Capture (`scripts/03-capture.cjs`, a copy of `../k26-recheck/scripts/01-capture.cjs`)
- **What changed in the copy:** only the header lines, the raw directory, the sheet titles and names, and the `stack` string.
- **Pages:** the same ones, re-discovered read-only, with the same ids as the re-check.
- **Its own verdict (kept like-for-like)** fails every truncated middle crumb, so it reports 24/28. The 4 "FAIL"s are the middle crumbs on the 4- and 5-crumb routes with the rail expanded. The final acceptance allows that (point 3), and it is the shrink order asked for.
- **`scripts/05-acceptance.cjs` scores the acceptance points** into `acceptance.json`:
  - inputs: `measurements.json` (final), `../k26-recheck/measurements.json` (`c4d1f7675`), `mobile.json` and `mobile-c4d1f7675.json`;
  - it includes the sub-pixel check.
- **Mobile:** `scripts/04-mobile.cjs` (390×844, light and dark), run on both bundles.

## `c4d1f7675` → final (wrapper px; label px in brackets; "…" = truncated)
Light, plus dark wherever the numbers differ. Rows where every column is unchanged are merged.

| Page · persona · rail | `c4d1f7675`: org / middle… / last | Final: org / middle… / last | Points |
|---|---|---|---|
| Fee note · Alice · exp (light) | 140… / Fee notes 81 / INV-0005… 82 (64 of 65) | 139… / Fee notes 81 / **INV-0005 83 (65)** | P1 P2 P4 PASS |
| Fee note · exp dark; col light/dark | 142 / 81 / 83 | 142 / 81 / 83 (same) | PASS |
| Trust txns · Alice and Carol · exp (light / dark) | 103… / 107… / Trust accounting 126 / Transactions… 74 (56.4) / 78 (59.8) | **74… / 82…** / Trust accounting 126 / **Transactions 103 (84.8), full** | PASS |
| Trust txns · col | 142 / 126 / 103 | 142 / 126 / 103 (same) | PASS |
| Matter · Carol · exp (light / dark) | 96… / 99… / Matters 68 / "Ndlovu v RAF mwxfl2od24ca"… 140 (122.2) / 145 (126.9) | **28… / 36…** / Matters 68 / **full 208 (189.5)** | PASS |
| Matter · col | 142 / 68 / 208 | 142 / 68 / 208 (same) | PASS |
| Client detail · Alice · all 4 | 142 / Clients 62 / Acme Corp 91 | same | PASS |
| **Template edit (5 crumbs)** · exp light | **0… / Settings 71 · Documents 92 · Templates 84 · Template 77 / "Edit" 0, overflowing under Search (683 > 661)** | 24… / Se… 48 (30.2) · Doc… 62 (44) · Tem… 57 (39) · Te… 52 (34.2) / **Edit 48 (25.6), whole**; crumbs end at 649 = the nav's right edge | P1 P3 P4 PASS |
| Template edit · exp dark | 0… / 71 · 92 · 84 · 77 / 0, overflowing | 24… / 50 (32) · 64 (46.2) · 59 (41.1) · 54 (36.1) / **Edit 48 (25.6)** | PASS |
| Template edit · col (light / dark) | 115… / 140… / 71 · 92 · 84 · 77 / "E…" 35 (17.4) / "E…" 43 (25 of 25.6) | 103… / 135… / 71 · 92 · 84 · 77 (all full) / **Edit 48 (25.6), whole** | PASS |
| **Checklist detail (4 crumbs)** · exp (light / dark) | 36… / 40… / Settings 71 · Matters 68 · Checklists 85 / **"C…" 36 (17.9) / 40 (21.9)** | 24… / S… 42 (23.5) · M… 39 (21.3) · Ch… 49 (31.3), dark 44 · 42 · 52 / **"Checklist template" 141 (123.4), whole** | P1 P3 P4 PASS |
| Checklist detail · col (light / dark) | 128… / 142 / 71 · 68 · 85 / "Checklist tem…" 128 (109.8) / full 141 | 114… / 142 / 71 · 68 · 85 / **full 141 (123.4)** in both | PASS |

### Acceptance points (from `acceptance.json`)
1. **PASS, 28/28.** The nav is 20 px (one line) on every page. No crumb's right edge passes the nav's right edge or the first header control.
   - Template edit, rail expanded: crumbs end at 649 / 657 against Search at 661 / 669.
2. **PASS, 20/20.**
   - No middle crumb on a 3-crumb page is cut, including sub-pixel cuts.
   - Every last crumb is at least as wide as in `c4d1f7675`, and wider on 7 captures:
     - fee note (exp light): 64 → 65 px;
     - trust transactions (exp, Alice and Carol, light and dark): 56.4 / 59.8 → 84.8 px;
     - matter (exp light and dark): 122.2 / 126.9 → 189.5 px.
3. **PASS, 8/8.**
   - "Edit" is whole (25.6 px) in all 4 template-edit captures; before it was 0 px or "E…".
   - "Checklist template" is whole (123.4 px) in all 4 checklist captures; before it was "C…" at 17.9 px.
   - Every truncated crumb has its `title`.
   - No last crumb has a sub-pixel cut in any capture (`summary.lastCut` = 0).
4. **PASS, 28/28.** The org crumb is ≤ 142 px with `title`. Middle labels are ≤ 108.5 px (the cap is 160) with `title`.
5. **PASS, 8/8** at 390×844 (fee note, Carol's matter, template edit, checklist; light and dark).
   - Only the last crumb is displayed, and its chevron has `display: none`.
   - Label text, width and truncation are identical to `c4d1f7675`.
   - The only difference: on "Edit" the nav's right edge moves from 78.6 to 101 px. The 48 px floor adds empty space after the label; nothing is drawn there.

## Consequences of the asked-for order (not failures)
- **Org crumb.** On a 3-crumb page with a long current page, the org gives up nearly all its width first. On Carol's matter page (rail expanded) it is "E2…" at 28 px (36 px dark), where it was 96 px. In exchange the matter name is whole.
- **Middle crumbs.** On the 4-crumb checklist page (rail expanded), the middles shrink to "S…", "M…" and "Ch…" (21–34 px labels) so that "Checklist template" stays whole. Every one keeps its `title`.
- **Below the floors** (not seen at 1024): middles can reach 0 px before the last crumb shrinks.
- **Browser coverage.** The weights rely on Chromium dropping a share smaller than 1/64 px. That was observed here in Chromium only; other engines were not tested.

## Console
- 0 errors and 0 hydration warnings over 28 captures and 3 discovery loads. `04-mobile.cjs` does not record the console.
- 4 warnings: `[tiptap warn]: Duplicate extension names found: ['link', 'underline']` on the template editor. They are already in the before and re-check passes, and the editor is outside this diff.

## E2E suites (stack rebuilt at `7149f9c11`; `PLAYWRIGHT_BASE_URL=http://localhost:3001`, `MAILPIT_URL=http://localhost:8026`)
- `pnpm test:e2e:navigation`: **17 passed** (6.0 s).
- `pnpm test:e2e`: **166 passed, 14 skipped** (59.3 s). Both exited 0.

## Files
- `scripts/02-prototype.cjs` → `prototype.json`: iteration 1, on the `c4d1f7675` bundle.
- `scripts/02b-prototype-iter2.cjs` → `prototype-iter2.json`: iteration 2, on the `5cf74b647` bundle, with the sub-pixel check.
- `scripts/03-capture.cjs` → `measurements.json`, `sheet-k26f-3crumb-1024.png` and `sheet-k26f-long-chain-1024.png`. Raw tiles are in `.tmp/663C-k26-final-raw/` (git-ignored).
- `scripts/04-mobile.cjs` → `mobile.json` (final) and `mobile-c4d1f7675.json` (run on the `c4d1f7675` bundle before the rebuild).
- `scripts/05-acceptance.cjs` → `acceptance.json`, scored offline.

## Deviations
- Playwright library scripts, not MCP, as in every K-26 pass.
- Two product commits instead of one. `5cf74b647` was pushed before the real-build capture found the 0.6 px cut, and `7149f9c11` fixes it. History was not rewritten.
- Beyond the brief's two outputs (`02-prototype.cjs` / `prototype.json`), this pass adds:
  - a second prototype script and result (`02b`, `prototype-iter2.json`);
  - a mobile probe (`04`);
  - an offline scorer (`05`), because `03` (a like-for-like copy) keeps 01's stricter middle-crumb verdict.
- The `measurements.json`, the sheets and `mobile.json` from the `5cf74b647` build were overwritten by the `7149f9c11` run. The `5cf74b647` numbers survive as the `served_5cf74b647` candidate in `prototype-iter2.json`.
- No client edit route exists, so client detail stands in, and the checklist "edit" route redirects to the 4-crumb detail. Both are as in the re-check.
