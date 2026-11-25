# 661B — E2E-LEGAL, SEL-COMPAT and OBS "after" (task 661.14, observation part)

| | |
|---|---|
| Head | `phase91/61B-light-scheme` @ **`6e3e0566f`** (PR #1863), worktree `worktree-epic-661B` |
| Stack | Fresh e2e mock-auth stack (`:3001` / `:8081`), built from `6e3e0566f`: `bash compose/scripts/e2e-down.sh && docker builder prune -f && bash compose/scripts/e2e-up.sh`. The frontend image was rebuilt: `pnpm --filter frontend build` ran (not cached), Next 16.3.8. The backend image came from cached layers; the branch has no `backend/` change. The served CSS chunk contains `data-app-shell` (8 matches) |
| Date / node | 2026-11-25, nvm `v20.11.1`, pnpm `/opt/homebrew/bin/pnpm`, `SHELL=/bin/bash NODE_OPTIONS=""` |
| Data | Populated by legal-lifecycle, then legal-depth-ii, then navigation on this stack. No default suite ran, so this stack's data differs from the before stack's (8 fee notes vs 21) |
| User | Alice (owner), logged in through the `/mock-login` UI |

**Deviation (same as before).** The Playwright MCP Chrome profile belongs to the user's live session (memory `env_playwright_mcp_profile_lock_obs`), so a Playwright **library** script drove the pages: `scripts/capture-after.cjs`. It is a new file, adapted from `../before/scripts/capture-before.cjs`, which was not edited. A supplement, `scripts/capture-after-sticky-short.cjs`, covers check (b) in short viewports. Both are committed byte-for-byte as executed. Run from the worktree root:

```
BEFORE_RAW=/tmp/661b-before-raw node qa_cycle/evidence/phase91-ui/661B/after/scripts/capture-after.cjs qa_cycle/evidence/phase91-ui/661B/after
node qa_cycle/evidence/phase91-ui/661B/after/scripts/capture-after-sticky-short.cjs qa_cycle/evidence/phase91-ui/661B/after
```

What the script adds over the before script:

- **Carry-over 5:** printed and written paths are repo-relative.
- **Carry-over 6:** the accent probe matches `[data-variant="accent"]` on any element. It now finds the fee-notes list's "New fee note" accent button, which sits in `PopoverTrigger asChild` and carries `data-slot="popover-trigger"`. The 661A selector `[data-slot="button"]` missed it (`probes.json` `shellExtra-fee-notes-1024-collapsed-*.accentButtonPaint`: light `#00776a`, dark `#00a38f`).
- **Checks a–h** below, plus pixel diffs against the before run's raw tiles. Those are kept out of the tree under EVIDENCE-BUDGET, at `/tmp/661b-before-raw`; the after raw tiles are at `/tmp/661b-after-raw`.

**Setup step (named).** The stack held no APPROVED fee note. The script approved the **last** DRAFT (`b46f2343-…`, Acme Corp) through the UI "Approve" button, the way a user would, to get the "Send fee note" header (`notes.json`). The "Approve" shot uses the first DRAFT, `62c9aa43-…`, by the same rule as before. The portal dev server was the worktree's own: `next dev --port 3012`, `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`, with the same Origin shim as before. The Next dev badge appears in both runs.

## 1. E2E-LEGAL + navigation (vs `../../baseline/README.md`)

| Suite | Baseline | After | Exit |
|---|---|---|---|
| `test:e2e:legal-lifecycle` | 105 passed | **105 passed**, 0 failed, 0 skipped (3.0 min) | 0 |
| `test:e2e:legal-depth-ii` | 40 passed, 4 skipped | **40 passed, 4 skipped**, 0 failed (1.9 min). The 4 skips are the same tests: `day-30-45-soa-writeoff.spec.ts:361` and 3× opt-in `null-prefetch-probe.spec.ts:74` | 0 |
| `test:e2e:navigation` | (no baseline count) | **17 passed**, 0 failed (5.3 s) | 0 |

There were no failures. Afterwards `git checkout -- documentation/screenshots` was run; `frontend/test-results` is not tracked on this branch. `git status` showed no tracked change. The 25 untracked `documentation/screenshots/legal-vertical/*.png` are the suites' curated output, the same class the baseline agent left; they were neither staged nor deleted.

## 2. SEL-COMPAT after

See `sel-compat.md`. Every selector and invariant is still present. Exactly one `<main id="main-content">` and one h1 in it were counted on the matter, dashboard, fee-notes and matters pages. No spec changed.

## 3. Checks a–h

| # | Check | Result | Numbers (file) |
|---|---|---|---|
| a | Collapsed rail, 1024×768, fee-notes page: the active "Fee notes" item renders whole (F-661A-8) | **PASS** | Light: the active item spans y 580–612 and the rail scroll area (`sidebar-content`) ends at 613, `scrollTop` 0. Dark (= today): the same 580–612 / 613. The rail keeps today's vertical geometry: container h 768 in both; light container 66 px wide with padding `0 8px` (`checks.json` `a-feeNotesCollapsed1024-*`; `fee-notes-1024-collapsed.png`) |
| b | AppHeader inside the rounded inset card, no double margin | **PASS** | Light, at rest: card top 8, header top **16** (8 below the card top: the header's own `mt-2`, with no second offset), header x = card x + 16 (`md:mx-4`) on both sides. Scrolled: header sticks at **8**, bottom 64. Dark (no card): header 8 at rest and 8 scrolled. `headerInsideCard` is true at 1024 and 1440 in both themes (`checks.json` `b-sticky-*`) |
| b | GroupedTabBar (matter detail) sticks under the header | **PASS** | Tab bar `position: sticky; top: 64px`. Scrolled, its top is **64** = header bottom 64 (gap 0) at 1024×768 light and dark (`checks.json`), and at 1440×600 light and dark (`sticky-short.json`). At 1440×900 the page only scrolls 286 px, so the bar never reaches the header there |
| b | DataTable sticky head lands under the header | **PASS** | The matters table has `stickyHeader={false}` (`matters-table.tsx:80`). The sticky DataTable is the **clients** list (`clients-table.tsx:90`). Its `th` is `sticky; top 64px`, and scrolled its top is **64** = header bottom at 1440×560 and 1024×520, light and dark (`sticky-short.json`; `sticky-short-viewports.png`). At 1440×900 and 1024×768 the list is too short to scroll that far |
| c | 390 Sheet unaffected | **PASS** | Inset margin 0, radius 0, no shadow, wrapper transparent at 390 in both themes. The Sheet is 288×844 at x 0. Dark Sheet region is pixel-identical to before (0 / 243 072 px differ). The light Sheet takes the firm rail tokens (bg `#f1f4f7`, fg `#3a4049`), as ADR-392 intends (`checks.json` `c-390-*`; `shell-390-sheet.png`) |
| c | Exactly 768 px coherent | **PASS** | 768: `(width >= 48rem)` true, the hook's `(max-width: 767px)` false, the desktop rail is rendered, the light inset margin is `8px 8px 8px 0px` with radius 12px, and dark has no inset. 767: CSS mobile, hook mobile, no desktop rail, no inset (`checks.json` `c-768-*`, `c-767-light`; `shell-768.png`). Header overlap and a horizontal overflow (scrollWidth 873) exist at 768 in **both** themes. They are pre-existing and logged in `../found.md` |
| d | Dark pixel identity: shell chrome (rail) | **PASS** | Rail crop at 1024/1440 × expanded/collapsed: **0** px differ in all 4. The full dark tiles differ only in the content data (4 348–4 957 px inside x≥283, y 22–694: matter-name suffix `mwwihzua2286`→`mwwjtue3603b`, notification badge 28→19) |
| d | Dark focused menu item + open row menu | **PASS** | Whole row-menu tiles (ArrowDown ×1 / ×2): **0** / 117 564 px differ. "Edit" and "Delete" crops: 0 px. Probes are exactly equal: `#00c2b1`/`#ffffff`, `#00c2b1`/`#ff6467` |
| d | Dark accent buttons (fee-note Approve / Send) | **FAIL (strict), PASS at tolerance** | Computed fill is exactly equal (`#00a38f`). The crops differ on **126 / 2 528** ("Approve") and **161 / 3 712** ("Send fee note") px, every one by **Δ1** (tol8 = 0), all on glyph edges of the label. Cause, proven by counterfactual: the label's computed colour went from `rgb(255, 255, 255)` (`text-white`) to `lab(100 0 0)` (`text-(--button-accent-foreground)` = `oklch(100% 0 0)`, `packages/ui/src/button.tsx` accent variant). Forcing `rgb(255,255,255)` on the live button gives **0** px differ for both crops (`diffs.json` `COUNTERFACTUAL …`). Imperceptible, but not bit-identical (`dark-pixel-diff.png`) |
| d | Dark theme toggle / Select / dialog close | **PASS by token; no before tile** | Before captured these in light only. Dark probes: toggle "Dark" (on) `#00c2b1`/`#ffffff`, Select highlight `#00c2b1`/`#ffffff`, dialog close opacity 0.7, fg `#ffffff`. These are the `.dark` `--accent` (teal-500), the same paint as before's dark focused menu item. Dark `--button-accent` = `#00a38f` = before's `bg-teal-600` (`dark-controls.png`) |
| e | Portal `/home` 1024 light | **PASS** | **0** / 786 432 px differ (full tile) |
| e | Portal `/login` 1024 light | **FAIL (strict), PASS at tolerance** | **214** px differ, Δ1, tol8 0, all in the "Send Magic Link" label (x 458–565, y 455–468). The cause is the same `oklch(100% 0 0)` label colour; the counterfactual removes the label diff. Its residual 5 px are at y 443, x 326–330, Δ≤40, on the button's top-left rounded edge. They are not in the as-rendered diff, which differs only in the label box. They come from repainting the button with an inline style; the label is outside them. The portal tokens are unchanged: `--accent` = `--button-accent` = teal-600 (`probes.json` `portal-login`) |
| e | Marketing `/` 1024 light | **FAIL (strict), PASS at tolerance** | **394** px differ, Δ1, tol8 0, on accent-button labels ("Book a Demo" etc.). Counterfactual with `rgb(255,255,255)`: **0** px. `data-app-shell` count 0, `--accent` and `--button-accent` = teal-600 (unchanged, K-04 stays outside the firm shell by design) |
| f | Light accent / focused item / rail text contrast | **PASS** | See the AA table. Accent button and focused item: white on `#00776a` = **5.459**. The measured `--kazi-teal-700` is `#00776a`, not the `#007469` the brief mentioned as a possible compiled fallback |
| g | Console on every captured page (light + dark) | **PASS** | **0** hydration warnings. 1 error total: marketing `/` "Failed to load resource: 404", identical to before. 0 warnings. Pages: matter, dashboard, fee notes (list, draft, approved), matters, clients, settings → matters custom fields, settings → firm general, marketing, portal `/login` and `/home` (`checks.json` `g_console`, `console.json`, `sticky-short.json`) |
| h | Radix portals in light get the A1 tokens | **PASS** | Row menu (portal under `<body>`, outside `[data-app-shell]`): focused "Edit" `#00776a` (teal-700, not teal-600 `#00a38f`), and the menu's own `--accent` resolves to teal-700. New Matter dialog (outside the shell wrapper): `--accent` `#00776a`, `--primary` `#14171c` (slate-900, A1; it was slate-950 `#07080b`), "Create Matter" button `#14171c`/`#ffffff`. The Select popper highlight is `#00776a`, and so is the theme toggle "Light" (on) (`probes.json` `menuFocused1-light`, `dialogTokens-light`, `selectHighlighted-light`, `themeToggle-light`) |

## 4. Measured AA (from `checks.json` `f_aa`; WCAG 2.x, foreground composited over its real background, unrounded)

| Pair (light, firm shell) | fg / bg | Measured | Floor | Result |
|---|---|---|---|---|
| Accent button "Approve" | `#ffffff` / `#00776a` | **5.459** | 4.5 | PASS (before: 3.165 on `#00a38f`) |
| Accent button "Send fee note" | `#ffffff` / `#00776a` | **5.459** | 4.5 | PASS |
| Focused menu item "Edit" | `#ffffff` / `#00776a` | **5.459** | 4.5 | PASS |
| Select highlighted item "ZAR — South African Rand" | `#ffffff` / `#00776a` | **5.459** | 4.5 | PASS |
| Theme toggle "Light" (on) | `#ffffff` / `#00776a` | **5.459** | 4.5 | PASS |
| Rail zone label "Today" | `#4d545f` / `#f1f4f7` | **6.920** | 4.5 | PASS |
| Rail user-footer email | `#4d545f` / `#f1f4f7` | **6.920** | 4.5 | PASS |
| Rail search pill text vs rail | `#4d545f` / `#f1f4f7` | **6.920** | 4.5 | PASS |
| Rail search pill text vs pill fill | `#4d545f` / `#caf0e9` | **6.240** | 4.5 | PASS |
| Rail idle nav item "Home" | `#3a4049` / `#f1f4f7` | **9.467** | 4.5 | PASS |
| Rail active item "Matters" vs active fill | `#00312e` / `#caf0e9` | **11.599** | 4.5 | PASS |
| Focused destructive item "Delete" (red className) | `#e7000b` / `#00776a` | **1.144** | 4.5 | **FAIL**, pre-existing (before 1.507 on teal-600); logged in `../found.md` |

Dark, unchanged by design (K-21 stays in the backlog): accent button white on `#00a38f` 3.165; focused item, Select and toggle white on `#00c2b1` 2.246; "Delete" `#ff6467`/`#00c2b1` 1.286. All are below 4.5, as before. The dark rail text passes: label 5.357, email and pill 7.286, pill on its fill 6.841, idle item 19.316, active item 17.024.

## 5. Computed-style probes, before → after (sRGB hex; `probes.json`)

| Probe | Light before → after | Dark before → after |
|---|---|---|
| Rail `[data-slot=sidebar-inner]` bg / fg | `#07080b`/`#ffffff` → **`#f1f4f7`/`#3a4049`** | `#0c0e12`/`#ffffff` → `#0c0e12`/`#ffffff` (equal) |
| Active nav item bg / fg | `#ffffff`@0.06/`#ffffff` → **`#caf0e9`/`#00312e`** | equal (`#ffffff`@0.06/`#ffffff`) |
| Accent button bg / fg | `#00a38f`/`#ffffff` → **`#00776a`/`#ffffff`** | equal (`#00a38f`/`#ffffff`); fg string `rgb(255,255,255)` → `lab(100 0 0)` (see d) |
| Focused menu item "Edit" / "Delete" | `#00a38f` + `#ffffff`/`#e7000b` → **`#00776a`** + `#ffffff`/`#e7000b` | equal (`#00c2b1` + `#ffffff`/`#ff6467`) |
| `main#main-content` bg | `#f4f6f8` → transparent; the inset card paints **`#fdfeff`** | `#07080b` → transparent; the inset paints **`#07080b`** (same pixels) |
| Inset card (`sidebar-inset`) | — → margin `8px 8px 8px 0`, radius 12px, shadow `0 1px 3px / 0 1px 2px -1px rgb(0 0 0/.1)`, wrapper bg `#f1f4f7` | margin 0, radius 0, no shadow, wrapper transparent (unchanged) |
| Sidebar container | — → padding `0 8px`, border-right 0 | padding 0, border-right 1px (unchanged) |
| Header bg / fg | `#fdfdff`@0.90/`#14171c` (equal) | `#14171c`@0.90/`#ffffff` (equal) |
| 390 Sheet bg / fg | `#07080b`/`#ffffff` → **`#f1f4f7`/`#3a4049`** | equal |
| Dialog close | opacity 0.7, fg `#14171c` (equal) | — / opacity 0.7, fg `#ffffff` |

## 6. Performance proxy (§18.6.4), the same method as before

Each run is a CDP `Performance.getMetrics` delta. It runs from the settled dashboard to the matter page (h1 visible + 1.5 s), 3 runs per theme, at 1440×900. Values are in milliseconds.

| | RecalcStyleDuration median (runs) | LayoutDuration median | RecalcStyleCount | LayoutCount |
|---|---|---|---|---|
| Light before → after | 15.5 (15.4/15.5/15.9) → **15.1** (15.4/15.1/12.7) | 5.2 → **5.1** | 31–34 → 26–29 | 6–7 → 6 |
| Dark before → after | 17.4 (17.0/17.4/17.9) → **16.2** (17.2/16.2/16.1) | 5.1 → **5.2** | 32–35 → 25–27 | 6–7 → 6–7 |

Chromium trace, light, the same load: UpdateLayoutTree 22.3 → **14.9** ms, Layout 6.71 → 6.62, Paint 2.78 → 1.91. The `:root:not(.dark):has([data-app-shell="firm"])` selector shows **no measurable style-recalc cost**: the medians are within run-to-run noise or lower. One caveat: the DOM differs a little between stacks (Nodes ≈2 950 after vs ≈2 860 before), because the data differs.

## 7. Defects the slice caused (not fixed here; for the orchestrator)

1. **Accent-button labels are not bit-identical (Δ1).** The cause is `--button-accent-foreground: oklch(100% 0 0)` in `packages/ui/styles/globals.css`, consumed by `text-(--button-accent-foreground)`. The label's computed colour went from `rgb(255,255,255)` to `lab(100 0 0)`. Chromium then blends glyph anti-aliasing 1/255 differently. This hits dark accent buttons in the firm app, marketing `/` and portal `/login`. The resolved colour is the same `#ffffff`, and the eye cannot see Δ1. The counterfactual makes it go away: `diffs.json` `COUNTERFACTUAL …` gives 0 px. It matters only if "pixel-identical" is read as bit-identical. A one-token way to restore exact identity is `--button-accent-foreground: #fff` (or `var(--color-white)`) in both the shared `:root` and `.dark`. That is a builder / orchestrator call. Evidence: `dark-pixel-diff.png`, `diffs.json`. Severity: LOW.
2. **At 1024×768 with the rail collapsed (light), the first breadcrumb wraps.** "E2E Test Organization" breaks onto two lines in the header. Before, and in dark after, it sits on one line. The collapsed light inset takes 32 px of content width: the inset is 944 px wide, at x 72–1016, vs 976 px at x 48–1024 (the F-661A-4 correction). That tips the breadcrumb past its wrap point. The breadcrumb already wraps at 1024 expanded in both themes today. Evidence: `shell-1024.png` top-right tile vs the before collapsed-light tile. Cosmetic; LOW.

The pre-existing items (the 768 overlap/overflow, and the destructive "Delete" contrast getting worse on teal-700) are appended to `../found.md`.

## Files (14 PNG + 10 JSON/MD + 2 scripts)

| File | Content |
|---|---|
| `shell-1024.png`, `shell-1440.png` | Matter detail, expanded/collapsed × light/dark (2×2) |
| `shell-390-sheet.png` | 390×844, closed + Sheet open, light/dark |
| `shell-768.png` | 768×1024 light/dark + 767×1024 light (check c) |
| `fee-notes-1024-collapsed.png` | Check a, light/dark |
| `sticky-scrolled.png` | Check b at 1440×900, scrolled (matter tabs, clients list) |
| `sticky-short-viewports.png` | Check b in short viewports, where the elements reach the header |
| `marketing-1024-light.png` | Marketing `/` 1024 light (the raw tile, as before) |
| `portal-1024-light.png` | Portal `/login` + `/home` 1024 light |
| `dark-accent-buttons.png` | Fee-note Approve (DRAFT) / Send fee note (APPROVED), light and dark |
| `dark-row-menu-focused.png` | Row menu, ArrowDown ×1/×2, light and dark |
| `light-controls.png`, `dark-controls.png` | Theme toggle, dialog close + full dialog, Select open |
| `dark-pixel-diff.png` | Before / after / diff crops: dark rail 1440, Approve, Send fee note, focused "Edit" |
| `probes.json`, `checks.json`, `diffs.json`, `perf.json`, `live-selectors.json`, `console.json`, `notes.json`, `sticky-short.json` | Single-line JSON |
| `sel-compat.md` | SEL-COMPAT after table + suite counts |
| `scripts/capture-after.cjs`, `scripts/capture-after-sticky-short.cjs` | As executed |
