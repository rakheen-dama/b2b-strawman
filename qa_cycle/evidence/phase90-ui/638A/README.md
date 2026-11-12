# 638A evidence: shadcn sidebar shell (E2A)

## Gates (orchestrator runs)

Final code head **`57fa7a98f`**. Each E2E row is a fresh stack (`e2e-down` → `docker builder prune -f` → `e2e-up`) built from a detached worktree at that head.

| Gate | Head | Result |
|---|---|---|
| 638.1 observe-first (before) | `68cf12103` (main) | `before/e2e/`: `test:e2e:navigation` **16 passed**; `day-00-firm-setup.spec.ts` **16 passed**. Screenshots and report in `before/` |
| FE-GATE (`rm -rf .next` → lint, typecheck, format:check, full vitest, build) | `57fa7a98f` | `fe-gate-summary.txt`: every step exit 0. **458 files, 3899 passed / 2 skipped** |
| FE-GATE | `e25c38f11` | `fe-gate-summary-e25c38f11.txt`: exit 0; 458 files, 3897 / 2 |
| E2E-LEGAL + `test:e2e:navigation` | `57fa7a98f` | `e2e/summary.txt`: navigation **16 passed**, legal-lifecycle **105 passed**, legal-depth-ii **40 passed / 4 skipped**. All equal the baseline (637.1 / 638.1) |
| E2E-LEGAL + navigation | `e25c38f11` | `e2e/run2-e25c38f11/`: 16; 105; 40 / 4 |
| E2E-LEGAL + navigation | `5da25cf0d` | `e2e/run1-5da25cf0d/`: 16; 105; 40 / 4 |
| OBS | `57fa7a98f` | `final-57fa7a98f/README.md`: observations 1–11 PASS, F1 fixed |
| OBS | `e25c38f11` | this README (below): 9 PASS, obs 5 FAIL (F1), which led to the fix in `57fa7a98f` |

---

## 638A OBS (task 638.7): real-browser observation of the new shadcn sidebar shell

## Final head re-run (57fa7a98f): see `final-57fa7a98f/README.md`
On 2026-11-12 the same scripts were re-run, unchanged in logic (only output paths and head labels changed; the diff is in `final-57fa7a98f/README.md`), against a fresh mock-auth stack built from the final head of PR #1761, `57fa7a98f8c1bfc1a8167cad37f56368d727800f`. Suites on that stack: navigation 16 passed, legal-lifecycle 105, legal-depth-ii 40 passed / 4 skipped. A new probe, `obs-probe4-f1.cjs`, covers the full F1 matrix.

| # | Result at 57fa7a98f | vs e25c38f11 |
|---|---|---|
| 1 Shell grid | PASS | same numbers |
| 2 Contract | PASS | same |
| 3 Cookie / no width shift | PASS | same |
| 4 ⌘/Ctrl+B on page (macOS) | PASS: ⌘B toggles; Ctrl+B no longer toggles (D21 design) | changed, intended |
| 5 ⌘B in Tiptap | **PASS**: ⌘B bolds without toggling; Ctrl+B leaves the sidebar and cookie unchanged | **FAIL → PASS** |
| 6 Collapsed rail | PASS | same |
| 7 Keyboard | PASS | same |
| 8 Mobile 390 | PASS; F2 still present | same |
| 9 Matters | PASS | same |
| 10 Console | PASS (only the pre-existing tiptap duplicate-extension warning) | same |
| 11 Dark note | faint hairline separation, unchanged | same |
| F1 | **FIXED**. macOS: ⌘B bolds in the editor; Ctrl+B in the editor does not toggle and does not bold (native caret-back). Body: ⌘B toggles, Ctrl+B does not. Consistent Windows emulation (CDP UA + `userAgentData`): editor Ctrl+B bolds without toggling, Meta+B does not toggle, body Ctrl+B toggles | FAIL → PASS |

Caveat: with the *original* obs-probe spoof (`navigator.platform` only), editor Meta+B still toggles and body Ctrl+B does not. The fix reads `navigator.userAgentData.platform` first, and that spoof leaves it as `macOS`, so the result is a harness artifact (note N1 in the final README). No new defects.

---

- **Head under test:** `e25c38f110004ef67941ce77dbe7db746234fa7d` (PR #1761 head)
- **Captured:** 2026-11-12, headless Chromium, Playwright library (`@playwright/test` from `worktree-638A-gate/frontend/node_modules`, same head)
- **Stack provenance:** fresh `e2e-down` → `docker builder prune -f` → `e2e-up` from a detached worktree at e25c38f11. Mock-auth stack: frontend :3001, backend :8081, mock IDP :8090, org `e2e-test-org`, user Alice (owner). The legal suites ran on this stack first (see `e2e/summary.txt`): navigation **16 passed**, legal-lifecycle **105 passed**, legal-depth-ii **40 passed / 4 skipped**. That is why this capture has more seed data than `before/`.

## Method (deviation: library script, not Playwright MCP)
The Playwright MCP browser profile is held by the user's live interactive session (a shared Chrome profile that must not be killed). Captures were therefore taken with standalone Playwright **library** scripts run with `node`, the same deviation used by 637A, 637B and 638A-before:
- `obs.cjs` runs observations 1–10 and writes `obs-report.json` (per-page facts in `pages[]`, per-observation facts in `obs.*`).
- `obs-probe.cjs` → `obs-probe-report.json`: the Ctrl/⌘+B editor matrix with `navigator.platform` set to Win32 or MacIntel, plus ⌘B/Ctrl+B in a plain `<input>`.
- `obs-probe2.cjs` → `obs-probe2-report.json`: a clean hover-tooltip capture, which replaced `obs.cjs`'s first hover attempt (see 6), and per-zone visibility inside the sidebar scroll area.
- `obs-probe3.cjs` → `obs-probe3-report.json`: attribution of the /matters table's right-edge clip (see 9).

Login: `/mock-login` → "Sign In" → `/org/e2e-test-org/dashboard`. The storage state is reused across fresh contexts, with the `sidebar_state` cookie stripped from it. Readiness = `load` + visible `h1` + about 1.2 s settle. `networkidle` was never used. Theme was set by an init script (`localStorage['kazi-theme']`) and confirmed by `<html class>`. The grid's collapsed/expanded state was set via the `sidebar_state` cookie. Observation 3 used the real "Toggle menu" button.

Only read-only and UI-only state was touched: the sidebar cookie, zone toggles, theme, and the mobile Sheet. The New Clause sheet editor got some typing and was closed with its **Close** button. "Create Clause" was never clicked, and the sheet only persists through that button: `DocumentEditor.onUpdate` only does `setEditorBody`, and `handleSave` is the only path to `createClause`.

## Summary
| # | Observation | Result |
|---|---|---|
| 1 | Shell grid (8 shots) | **PASS** |
| 2 | Contract vs before | **PASS**: identical zones and links; `aria-current="page"` is new |
| 3 | Cookie persistence, no width shift | **PASS** |
| 4 | ⌘/Ctrl+B on the page | **PASS** |
| 5 | ⌘B inside Tiptap | **FAIL (LOW, partial)**: the platform Mod key (⌘ on macOS) bolds and does not toggle the sidebar. Ctrl+B on macOS **toggles the sidebar** while typing in the editor (defect F1). |
| 6 | Collapsed icon rail | **PASS** |
| 7 | Keyboard (expanded) | **PASS** |
| 8 | Mobile 390 | **PASS**, plus visual finding F2 (closed zones now below the fold in the Sheet) |
| 9 | Matters route | **PASS** |
| 10 | Console | **PASS**: 0 errors, 0 page errors, 0 hydration messages. 1 warning, which is pre-existing and unrelated. |
| 11 | Dark visual note | Observed: separation is improved but faint |

## 1. Shell grid: PASS
`/org/e2e-test-org/dashboard`, `sidebar_state` cookie preset. All 8 captures were opened or checked, and `<html class>` matched the theme in every one.

| File | Sidebar container | `#main-content` x / width | scrollWidth / viewport | `main` | "Toggle menu" by role | zones (expanded) | links | aria-current |
|---|---|---|---|---|---|---|---|---|
| shell-1024-light-expanded.png | 256 | 256 / 768 | 1024 / 1024 | 1 | 1 | 6 (see below) | 11 | Home |
| shell-1024-light-collapsed.png | 48 | 48 / 976 | 1024 / 1024 | 1 | 1 | hidden | 28 | Home |
| shell-1024-dark-expanded.png | 256 | 256 / 768 | 1024 / 1024 | 1 | 1 | 6 | 11 | Home |
| shell-1024-dark-collapsed.png | 48 | 48 / 976 | 1024 / 1024 | 1 | 1 | hidden | 28 | Home |
| shell-1440-light-expanded.png | 256 | 256 / 1184 | 1440 / 1440 | 1 | 1 | 6 | 11 | Home |
| shell-1440-light-collapsed.png | 48 | 48 / 1392 | 1440 / 1440 | 1 | 1 | hidden | 28 | Home |
| shell-1440-dark-expanded.png | 256 | 256 / 1184 | 1440 / 1440 | 1 | 1 | 6 | 11 | Home |
| shell-1440-dark-collapsed.png | 48 | 48 / 1392 | 1440 / 1440 | 1 | 1 | hidden | 28 | Home |

- Expanded zones, in order: `Today=true | Matters=true | Clients=true | Fees=false | Trust=false | Firm=false`.
- Expanded links, in order: Home | My work | Diary | Matters | New matter | Conflict check | Adverse parties | AI reviews | Clients | FICA & compliance | Data requests.
- No horizontal overflow anywhere. The header stays 56 px tall (main y=56).
- The sidebar is wider than before: **256 px vs 240 px**. Main is 16 px narrower in expanded mode (1184 vs 1200 at 1440).
- "Toggle menu" is now visible on desktop as the header's panel icon (by-role count 1; before it was 0 on desktop).

## 2. Contract vs before: PASS
Compared against `before/before-report.json` (`shell-1440-light.png`):
- Zone order and default `aria-expanded`: **identical** (`zonesEqual: true`).
- The 11 default link texts, in order: **identical** (`linksEqual: true`).
- Diff: `aria-current="page"` is now on **Home** (`/org/e2e-test-org/dashboard`); before, no link had it. Nothing else differs.

## 3. Cookie persistence and no width shift: PASS
Measured in `obs.obs.o3_persistence`, at 1440 light, starting with no cookie:
- Initial state is expanded and the cookie is absent. Clicking the visible **Toggle menu** gives `sidebar_state=false`, `data-state=collapsed` and width 48.
- Reload with an init-script sampler: a MutationObserver plus a rAF loop record `[data-slot=sidebar-container]` width and `data-state` on every change for 4 s from document start.
  - **Collapsed, normal CPU:** first sample at t=7 ms (readyState `loading`) = **48 / collapsed**. Distinct widths over 4 s: `[48]`. After load: 48. Cookie `false`.
  - **Collapsed, 6× CPU throttle:** first sample at t=43 ms = **48**. Distinct `[48]`. After load: 48.
- Clicking Toggle menu again gives cookie `true` / expanded.
  - **Expanded, normal:** first sample 7 ms = **256**. Distinct `[256]`. After load: 256.
  - **Expanded, 6× CPU:** first sample 43 ms = **256**. Distinct `[256]`.
- About 490–520 samples per run, with no expanded↔collapsed transition frame. The SSR reads the cookie (`layout.tsx`), so the first paint already has the right width.

## 4. ⌘/Ctrl+B on the page: PASS
Focus was on `BODY` before each press (`obs.obs.o4_shortcut`):

| Key | Before | After | Width | Cookie |
|---|---|---|---|---|
| Control+b | expanded | collapsed | 48 | false |
| Control+b | collapsed | expanded | 256 | true |
| Meta+b | expanded | collapsed | 48 | false |
| Meta+b | collapsed | expanded | 256 | true |

## 5. ⌘B inside a Tiptap editor: FAIL (LOW, partial)
The editor was Settings › Documents › Clauses (`?tab=clauses`) → **New Clause** sheet → Body (Tiptap `DocumentEditor`). I typed "Boldword", selected all, and pressed keys. The sheet was closed via **Close** without saving, and the dialog count afterwards was 0. Headless Chromium reports `navigator.platform = MacIntel`, so ProseMirror's `Mod` is ⌘.
- **⌘B (Meta+b, the Mod key on macOS): PASS.** Editor HTML went `<p>Boldword</p>` → `<p><strong>Boldword</strong></p>`. The sidebar stayed `expanded` and the cookie stayed `true`. The bold toolbar button is active in `tiptap-meta-b.png`. The `defaultPrevented` guard works.
- **Ctrl+B on macOS: FAIL.** The sidebar went `expanded → collapsed` and the cookie became `false` while the caret was in the editor. The text was not bolded, because ProseMirror's macOS keymap has no `Ctrl-b`. The event is not default-prevented, so the shell handler (`event.metaKey || event.ctrlKey`) takes it. This was reproduced twice: in `obs-report.json` `o5_tiptap.ctrlB`, and in `obs-probe-report.json` under `MacIntel` (`Control+b`: sidebar expanded→collapsed, html `<p>Boldword</p>`).
- Same matrix with `navigator.platform` spoofed to **Win32** (`obs-probe-report.json`): **Ctrl+B** bolds and the sidebar does not change. **Meta(Win)+B** toggles the sidebar. The defect is the mirror image on each OS: the modifier that is *not* the editor's Mod key falls through to the shell. In practice it bites on macOS, where Ctrl+B is a native "caret back one character" text binding; the shell handler also `preventDefault`s it.
- Related, likely intended (shadcn default): in a plain `<input>` (the clause Title field), both ⌘B and Ctrl+B toggle the sidebar on macOS (`obs-probe-report.json` `titleInput ...`).
- Screenshots: `tiptap-meta-b.png` (bold applied, sidebar expanded) and `tiptap-ctrl-b-mac.png` (after the Ctrl+B and input presses).

## 6. Collapsed icon rail: PASS
At 1440×900 light, cookie `false` (`obs.obs.o6_rail`):
- `data-state=collapsed`. **28 links** inside Main navigation, all six zones' items in zone order (Home … Settings).
- The six zone triggers have `display: none` with no box, so they are not visible. A 45-press Tab walk through the whole sidebar never focused a trigger (`tabSeenTriggerFocused: false`) and hit 28 nav tab stops (one per link).
- **5 separators** inside the nav, between the six zones.
- The scroll container `[data-slot=sidebar-content]` has `overflow-y: auto`, **scrollHeight 1077 > clientHeight 640**. After scrolling to the bottom, scrollTop = 437 → `rail-1440-collapsed-bottom.png` (Fees/Trust/Firm icons down to Settings, then the footer). The page behind is scrolled because of the Tab walk; this does not affect the rail.
- **Hover tooltip:** pointer moved horizontally onto the "Matters" icon → `role=tooltip` "Matters" → `rail-tooltip-hover.png` (from `obs-probe2.cjs`). The first attempt in `obs.cjs` read no tooltip because it ran right after the 45-press Tab walk. `obs-probe.cjs` saw it appear, but its diagonal pointer path crossed "My work", so two tooltips were mid-animation. The clean capture is the one kept.
- **Focus tooltip:** focus "Home", press Tab → focus on "My work" with a focus ring, and tooltip "My work" appears → `rail-tooltip-focus.png`.

## 7. Keyboard, expanded: PASS
At 1440 light, cookie `true`. The Tab sequence from the top of the page (`obs.obs.o7_keyboard.focusSequence`):
1 link "Skip to content" · 2 button "Search, Command K" · 3 button "Today" (expanded=true, nav) · 4 link Home · 5 My work · 6 Diary · 7 button "Matters" (true) · 8 link Matters · 9 New matter · 10 Conflict check · 11 Adverse parties · 12 AI reviews · 13 button "Clients" (true) · 14 link Clients · 15 FICA & compliance · 16 Data requests · 17 button "Fees" (false) · 18 button "Trust" (false) · 19 button "Firm" (false) · 20 link Platform Admin · 21 link Notifications · 22 button "Toggle menu".

The sidebar comes before the header in DOM order, so "Toggle menu" is reached after the whole sidebar.

Zone toggle: "Fees" focused, `aria-expanded=false` → **Enter** → `true`, and Unbilled work / Fee notes / Fee runs / Debtors / Disbursements / Tariffs render (17 links) → `keyboard-zone-toggle.png`. The nav scrolled to keep the focused trigger in view, and the trigger has a focus ring. **Space** → `false`, back to 11 links. The sidebar stayed `expanded` throughout.

## 8. Mobile 390×844: PASS (plus finding F2)
Light and dark (`obs.obs.o8_mobile`):
- **Closed:** no Main navigation in the DOM, "Toggle menu" by role = 1, main x=0 width 390, scrollWidth 390. Shots: `mobile-390-closed-light.png`, `mobile-390-closed-dark.png`.
- **Toggle menu** → Sheet: exactly 1 dialog, accessible name **"Kazi"**, box 288×844. A visible **Close** button (16×16 at x=255, y=16). Main navigation count = **1** (both by role and in the DOM) while open. Shots: `mobile-390-sheet-light.png`, `mobile-390-sheet-dark.png`.
- Light: clicking **Matters** in the Sheet → URL `/org/e2e-test-org/matters`, h1 "Matters", dialog count **0** (the Sheet closed) → `mobile-390-after-nav.png`. Dark: Close button → dialog count 0.
- **Finding F2 (visual, LOW):** in the Sheet, the closed **Fees / Trust / Firm** zone triggers are **below the fold** of the nav scroll area. The content box is 146–685 px; the triggers' tops are at 681, 730 and 779 (scrollHeight 669 vs clientHeight 539). Nothing on screen indicates the area scrolls. In `before/mobile-390-sheet-open-dark.png`, all six zone headers were visible without scrolling. They are reachable by scrolling (`mobile-390-sheet-scrolled-light.png`). On desktop at 1440×900 expanded, Trust and Firm are now below the fold; before, only Firm was (content 669 vs 628 px before). At 1024×768, Fees/Trust/Firm are below the fold, as they were before. Data: `obs-probe2-report.json`.

## 9. Matters route: PASS
`matters-1440-light.png`: h1 "Matters". `aria-current="page"` is on **Matters** (`/org/e2e-test-org/matters`) only. Sidebar 256, main 256/1184, scrollWidth 1440.

Aside, not attributed to this slice: the matters table is clipped at its right edge inside its own `overflow-x-auto` wrapper (table 1161 vs wrapper 1134). At viewport 1456, where main = 1200 = the old width, it still overflows (1161 vs 1150). So the overflow comes from the richer post-suite data, and the 16 px narrower main only widens it from 11 to 27 px. With the sidebar collapsed, or at 1600, the table fits. There is no document-level overflow. Data: `obs-probe3-report.json`.

## 10. Console: PASS
Collected across every page in `obs.cjs`: **0 errors, 0 page errors, 0 hydration messages**. 1 warning, on `/settings/documents?tab=clauses`: `[tiptap warn]: Duplicate extension names found: ['link', 'underline']`. This comes from `components/editor/DocumentEditor.tsx`, which this PR does not touch (last changed in #997). It is pre-existing and unrelated to the shell. The probes saw the same single warning and nothing else. The marketing `/` page was not visited.

## 11. Dark visual note
Comparing `shell-1440-dark-expanded.png` with `before/shell-1440-dark.png`:
- Before, the sidebar and canvas were essentially indistinguishable: no edge was visible.
- Now there is a visible **1 px hairline** at x=256. Its computed border-right is `1px lab(100 0 0 / 0.08)` (white at 8%). The sidebar surface (`lab(4.04 …)`) is a touch lighter than the canvas (`lab(2.21 …)`).

The separation is real but faint, so it should still be checked at design review. A ΔL of about 1.8 does not read as a different surface on its own; only the hairline does. In light mode the dark sidebar against the light canvas separates clearly.

## Defects / findings
- **F1 (LOW, FAIL in obs 5):** on macOS, **Ctrl+B with the caret in a Tiptap editor collapses or expands the sidebar** (and writes the cookie) instead of being left to the editor or the OS. The cause is that `components/ui/sidebar.tsx`'s keydown handler accepts `event.metaKey || event.ctrlKey`, and ProseMirror only handles (and `preventDefault`s) its platform Mod key. The mirror case is Win/Linux Meta+B. Evidence: `obs-report.json` `obs.o5_tiptap.ctrlB`, `obs-probe-report.json` `MacIntel.Control+b`, `tiptap-ctrl-b-mac.png`. ⌘B (the documented case) is guarded correctly. Not fixed; this is for the orchestrator to triage.
- **F2 (LOW, visual/discoverability):** the closed zones Fees/Trust/Firm are below the fold in the mobile Sheet (all six were visible before). At 1440×900 desktop, Trust is now below the fold too. They are reachable by scrolling, with no affordance. Evidence: `obs-probe2-report.json`, `mobile-390-sheet-light.png` vs `before/mobile-390-sheet-open-dark.png`, `mobile-390-sheet-scrolled-light.png`.

## Files
Scripts: `obs.cjs`, `obs-probe.cjs`, `obs-probe2.cjs`, `obs-probe3.cjs`
Data: `obs-report.json`, `obs-probe-report.json`, `obs-probe2-report.json`, `obs-probe3-report.json`
PNGs: `shell-{1024,1440}-{light,dark}-{expanded,collapsed}.png` (8), `rail-1440-collapsed-bottom.png`, `rail-tooltip-hover.png`, `rail-tooltip-focus.png`, `keyboard-zone-toggle.png`, `tiptap-meta-b.png`, `tiptap-ctrl-b-mac.png`, `mobile-390-closed-{light,dark}.png`, `mobile-390-sheet-{light,dark}.png`, `mobile-390-sheet-scrolled-light.png`, `mobile-390-after-nav.png`, `matters-1440-light.png`

Screenshots opened and checked by eye: shell-1440-light-expanded, shell-1440-dark-expanded, shell-1440-dark-collapsed, rail-1440-collapsed-bottom, rail-tooltip-hover (both versions), rail-tooltip-focus, tiptap-meta-b, keyboard-zone-toggle, mobile-390-sheet-dark, mobile-390-sheet-scrolled-light, mobile-390-after-nav, matters-1440-light. None is a login page.
