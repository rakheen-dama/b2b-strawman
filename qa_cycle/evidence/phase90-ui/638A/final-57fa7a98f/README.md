# 638A OBS: final-head re-run (head 57fa7a98f)

- **Head under test:** `57fa7a98f8c1bfc1a8167cad37f56368d727800f`, the final head of PR #1761. It adds `fix(phase90-638A): platform-aware sidebar shortcut (OBS F1)` on top of `e25c38f11`, the head of the first OBS in `../README.md`.
- **Captured:** 2026-11-12, about 03:55–04:00 UTC. Headless Chromium on macOS, using the Playwright library (`@playwright/test` from `/Users/rakheendama/Projects/2026/worktree-638A-gate/frontend/node_modules`; that worktree is at 57fa7a98f).
- **Stack provenance:** the orchestrator built a fresh stack: `e2e-down` → `docker builder prune -f` → `e2e-up` from a detached worktree at 57fa7a98f. Mock-auth stack: frontend :3001, backend :8081, mock IDP :8090, org `e2e-test-org`, user Alice (owner). The legal suites ran on this stack first: navigation **16 passed**, legal-lifecycle **105 passed**, legal-depth-ii **40 passed / 4 skipped**. This agent did not inspect the containers, because docker commands were out of scope. The stack's build head is taken from the orchestrator. The F1 behaviour observed below matches the 57fa7a98f code and differs from the e25c38f11 behaviour, which is consistent with that.

## Method (deviation: library script, not Playwright MCP)
The Playwright MCP Chrome profile is held by the user's live interactive session, and that profile must not be killed. As in the first OBS (and in 637A, 637B and 638A-before), the captures come from standalone Playwright **library** scripts run with `node`.

The four original scripts were copied into this folder and changed **only** in output paths and head labels. `OUT = __dirname` already points at this folder, so the copies write here automatically. They ran in the original order: `obs.cjs` → `obs-probe.cjs` → `obs-probe2.cjs` → `obs-probe3.cjs`. That order matters because `obs-probe2.cjs` overwrites `rail-tooltip-hover.png`, as it did in the first run.

Changes (output of `diff ../X.cjs X.cjs`, also saved as `script-diff.txt`):
```
=== obs.cjs
1c1
< // 638A OBS (task 638.7): new shadcn sidebar shell on the mock-auth e2e stack (head e25c38f11).
---
> // 638A OBS (task 638.7): new shadcn sidebar shell on the mock-auth e2e stack (head 57fa7a98f, final re-run).
12c12
< const report = { head: 'e25c38f110004ef67941ce77dbe7db746234fa7d', capturedAt: new Date().toISOString(), obs: {}, pages: [] };
---
> const report = { head: '57fa7a98f8c1bfc1a8167cad37f56368d727800f', capturedAt: new Date().toISOString(), obs: {}, pages: [] };
130c130
<   const before = JSON.parse(fs.readFileSync(`${OUT}/before/before-report.json`, 'utf8')).captures.find((c) => c.file === 'shell-1440-light.png');
---
>   const before = JSON.parse(fs.readFileSync(`${OUT}/../before/before-report.json`, 'utf8')).captures.find((c) => c.file === 'shell-1440-light.png');
=== obs-probe.cjs
1c1
< // 638A OBS follow-up probe: (a) collapsed-rail hover tooltip with an explicit wait; (b) Ctrl+B in the
---
> // [final re-run at head 57fa7a98f] 638A OBS follow-up probe: (a) collapsed-rail hover tooltip with an explicit wait; (b) Ctrl+B in the
=== obs-probe2.cjs
1c1
< // 638A OBS follow-up probe 2: (a) clean hover-tooltip capture (pointer approaches the icon horizontally
---
> // [final re-run at head 57fa7a98f] 638A OBS follow-up probe 2: (a) clean hover-tooltip capture (pointer approaches the icon horizontally
=== obs-probe3.cjs
1c1
< // 638A OBS follow-up probe 3: is the /matters table's right-edge clipping caused by the shell (main 1184 vs
---
> // [final re-run at head 57fa7a98f] 638A OBS follow-up probe 3: is the /matters table's right-edge clipping caused by the shell (main 1184 vs
```
In summary: one comment or head label per script, plus the `before/` path in `obs.cjs`, which moved one level up. There are no logic changes.

**One new script, `obs-probe4-f1.cjs`** (new code, not a copy). It covers the full F1 matrix, because the original scripts do not press keys on the page body under a faked platform, and do not record the cookie, the selection or `defaultPrevented` in the editor.
- Each editor case opens a fresh New Clause sheet, types "Boldword", selects all, presses **one** key, records the facts, and then clicks **Close**.
- The facts recorded are: editor HTML, DOM selection, sidebar `data-state`, `sidebar_state` cookie, and the keydown's `defaultPrevented` after dispatch (read by a window keydown listener added in an init script, using `setTimeout(0)`).
- It runs on three platforms:
  - `mac`: no spoof.
  - `win-np`: `Navigator.prototype.platform` is set to `Win32`. This is exactly the obs-probe method.
  - `win-cdp`: consistent Windows emulation via CDP `Emulation.setUserAgentOverride`, with `platform: 'Win32'`, a Windows UA string, and `userAgentMetadata.platform: 'Windows'`.

The new script's output is `obs-probe4-f1-report.json` plus `f1-{mac,win-np,win-cdp}-{editor,body}-ctrl-b.png`.

Login and readiness were unchanged: `/mock-login` → "Sign In" → dashboard, with the storage state reused and the `sidebar_state` cookie stripped. Readiness = `load` + visible `h1` + a settle wait; `networkidle` was not used.

**No business data was mutated.** Every New Clause sheet (2 in `obs.cjs`, 2 in `obs-probe.cjs`, 6 in `obs-probe4-f1.cjs`) was closed with **Close**, and the dialog count afterwards was 0 every time. "Create Clause" was never clicked.

## Result table
| # | Observation | First OBS (e25c38f11) | This run (57fa7a98f) | Same / changed |
|---|---|---|---|---|
| 1 | Shell grid (8 shots) | PASS | **PASS**: sidebar 256/48, main 256/768, 48/976, 256/1184, 48/1392; scrollWidth = viewport in all 8; `main`=1; Toggle menu by role=1; links 11/28; aria-current Home; `<html class>` matches the theme | Same (all numbers identical) |
| 2 | Contract vs before | PASS | **PASS**: `zonesEqual: true`, `linksEqual: true`; aria-current is Home now and none before | Same |
| 3 | Cookie persistence, no width shift | PASS | **PASS**: click → cookie `false`/48. First sample t=7 ms (normal) / 44 ms (6× CPU) = 48, distinct `[48]`. Expanded: t=7/41 ms = 256, distinct `[256]`. 487–523 samples per run | Same |
| 4 | ⌘/Ctrl+B on the page (macOS) | PASS (both modifiers toggled) | **PASS (D21 design)**: Control+b ×2: expanded→expanded, cookie `true` (no toggle). Meta+b: expanded→collapsed/48/`false`, then collapsed→expanded/256/`true` | **Changed, as intended**: Ctrl+B no longer toggles on macOS |
| 5 | ⌘B inside Tiptap (macOS) | FAIL (LOW, partial): Ctrl+B toggled | **PASS**: ⌘B → `<p><strong>Boldword</strong></p>`, sidebar and cookie unchanged (`true`). Ctrl+B → sidebar `expanded`, cookie `true`, HTML unchanged | **Changed: FAIL → PASS** |
| 6 | Collapsed icon rail | PASS | **PASS**: 28 links, 5 separators, triggers `display:none` and never Tab-focused (28 nav stops), scrollHeight 1077 > 640, scrollTop 437. Hover tooltip "Matters" (probe2); focus tooltip "My work". `obs.cjs`'s own hover read `[]` again, and obs-probe again caught two tooltips, both as before | Same |
| 7 | Keyboard (expanded) | PASS | **PASS**: identical 22-stop Tab order. Fees Enter → `true`, 17 links; Space → `false`, 11 links; sidebar stayed `expanded` | Same |
| 8 | Mobile 390 | PASS + F2 | **PASS**: closed → no nav, main 0/390. Sheet: 1 dialog "Kazi" 288×844, Close 16×16 at (255,16), nav count 1. Matters link → `/matters`, dialogs 0. Dark Close → 0. **F2 still present**: Fees/Trust/Firm tops at 681/730/779 vs content bottom 685 | Same (F2 unchanged) |
| 9 | Matters route | PASS | **PASS**: h1 Matters, aria-current Matters only, 256 / 256/1184, scrollWidth 1440. Table clip is the same: 1161 vs 1134 at 1440; 1161 vs 1150 at 1456; fits collapsed and at 1600 | Same |
| 10 | Console | PASS | **PASS**: 0 errors, 0 page errors, 0 hydration messages. 1 warning, the pre-existing `[tiptap warn]: Duplicate extension names found: ['link', 'underline']` on `/settings/documents?tab=clauses`. The probes saw only that same warning | Same |
| 11 | Dark visual note | Faint separation | Same: border-right `1px lab(100 0 0 / 0.08)`, sidebar `lab(4.04 …)` vs canvas `lab(2.21 …)`. `shell-1440-dark-expanded.png` opened and checked: hairline visible, faint | Same |
| F1 | Platform-aware ⌘/Ctrl+B | FAIL | **PASS on macOS and under consistent Windows emulation**. Under the obs-probe `navigator.platform`-only spoof, 2 of 3 Windows checks do not hold; this is a harness artifact (see below) | **Fixed** |

## F1 detail (`obs-probe4-f1-report.json`, with cross-checks from `obs-report.json` o4/o5 and `obs-probe-report.json`)

### macOS (`mac`: `navigator.platform` = `MacIntel`, `userAgentData.platform` = `macOS`)
| Where | Key | Sidebar | Cookie | Editor / keydown | Verdict |
|---|---|---|---|---|---|
| New Clause editor | ⌘B (Meta+b) | expanded → expanded | true → true | `<p>Boldword</p>` → `<p><strong>Boldword</strong></p>`, selection kept; `defaultPrevented: true` (ProseMirror) | **PASS** |
| New Clause editor | Ctrl+B | expanded → expanded | true → true | HTML unchanged (`<p>Boldword</p>`, no bold). The selection "Boldword" collapsed to a caret at offset 0, which is the macOS native Ctrl+B "move backward" text binding. `defaultPrevented: false`: neither ProseMirror nor the shell took the key; it was left to the OS editing binding | **PASS** |
| Page body | ⌘B | expanded → collapsed | true → false | `defaultPrevented: true` | **PASS** |
| Page body | Ctrl+B | expanded → expanded | true → true | `defaultPrevented: false` | **PASS** (D21 design) |

The same results appear in two other places:
- `obs-report.json` o4 (body: Ctrl+B ×2 no toggle; ⌘B toggles twice).
- `obs-report.json` o5 (⌘B bolds; Ctrl+B afterwards leaves the sidebar `expanded` and the cookie `true`).

The unchanged `obs-probe.cjs` MacIntel case changed in two ways:
- **Ctrl+B in the editor no longer toggles** (first OBS: expanded→collapsed).
- **The following ⌘B no longer bolds** (`<p>Boldword</p>`). The cause is the Ctrl+B before it: now that the shell no longer `preventDefault`s Ctrl+B, the native move-backward collapses the select-all selection, so ⌘B toggles only the stored mark at the caret. This is correct editor behaviour, not a regression. ⌘B on a real selection bolds (row 1 above).

Also in the plain Title `<input>`: ⌘B still toggles (shadcn default, same as before), and Ctrl+B no longer toggles (first OBS: it did).

Screenshots:
- `f1-mac-editor-ctrl-b.png`, opened: sidebar expanded, "Boldword" not bold, toolbar B inactive.
- `tiptap-meta-b.png`, opened: "Boldword" bold and selected, toolbar B active, sidebar expanded.

### Faked Windows, obs-probe method (`win-np`: `navigator.platform` = `Win32`, but `userAgentData.platform` stays `macOS`)
| Check | Observed | Expected | Verdict |
|---|---|---|---|
| Ctrl+B in editor | bolds (`<strong>`), sidebar expanded→expanded, cookie true→true | bolds, no toggle | PASS |
| Meta+B in editor | **sidebar expanded→collapsed, cookie true→false**, no bold | no toggle | **does not hold** |
| Ctrl+B on body | **no toggle** (expanded→expanded, cookie true) | toggles | **does not hold** |
| Meta+B on body | toggles (expanded→collapsed, cookie false) | (Windows: should not) | does not hold |

`obs-probe-report.json` (the unchanged copy) shows the same: Win32 Ctrl+B bolds without toggling, and Meta+B toggles, exactly as in the first OBS.

**Why this is a harness artifact, not a product defect:** the fix's `isApplePlatform()` in `frontend/components/ui/sidebar.tsx` reads `navigator.userAgentData?.platform || navigator.platform || navigator.userAgent`. ProseMirror/Tiptap decides `Mod` from `navigator.platform`. The obs-probe spoof overrides only `navigator.platform`, so the page is in a mixed state that no real browser produces: ProseMirror thinks it is on Windows (so Ctrl+B bolds), while the shell sees `userAgentData.platform = "macOS"` and stays in Apple mode (⌘B toggles, Ctrl+B does not). Recorded evidence: `win-np.env.uaDataPlatform = "macOS"`. The obs-probe method was adequate for the e25c38f11 code, which ignored the platform. It does not emulate Windows for the 57fa7a98f code.

### Consistent Windows emulation (`win-cdp`: `navigator.platform` = `Win32`, `userAgentData.platform` = `Windows`, Windows UA)
| Check | Observed | Verdict |
|---|---|---|
| Ctrl+B in editor | bolds (`<p><strong>Boldword</strong></p>`), sidebar expanded→expanded, cookie true→true; `defaultPrevented: true` | **PASS** |
| Meta+B in editor | sidebar expanded→expanded, cookie true→true; `defaultPrevented: false` (the shell did not take it) | **PASS** (no toggle) |
| Ctrl+B on body | **expanded→collapsed, cookie true→false**; `defaultPrevented: true` | **PASS** |
| Meta+B on body | no toggle (expanded→expanded, cookie true) | PASS (Windows ignores Meta) |

Note on `win-cdp` Meta+B in the editor: the text *did* turn bold, but with `defaultPrevented: false`. That means ProseMirror's keymap did not handle it; the host's **macOS Chromium native contenteditable binding** (Cmd+B → bold) did, and ProseMirror then parsed the DOM change. A UA override cannot change the host's native editing bindings, and on real Windows the Win key does not bold. The point of the check, that the sidebar does not toggle, holds.

Screenshots: `f1-win-cdp-body-ctrl-b.png` (opened: rail collapsed after Ctrl+B on the body), `f1-win-cdp-editor-ctrl-b.png`, `f1-win-np-*.png`, `f1-mac-body-ctrl-b.png`.

Every clause sheet in this probe closed with dialog count 0. The console showed only the pre-existing tiptap duplicate-extension warning.

## Defects / findings
- **F1: FIXED** (observed on macOS and under consistent Windows emulation).
- **F2 (LOW, visual): unchanged.** Not in scope for 57fa7a98f.
- **Note N1 (harness, not a product defect):** the shell and ProseMirror read the platform from different sources (`userAgentData.platform` first vs `navigator.platform`). They only disagree when `navigator.platform` alone is spoofed, as in obs-probe. That they agree in real browsers is reasoned, not observed here: Chromium derives both from the OS, and Safari/Firefox have no `userAgentData`. Any future test that fakes Windows should spoof both (CDP `setUserAgentOverride` with `userAgentMetadata`), or stub `userAgentData` as well.
- **No new defects.**

## Screenshots opened and checked by eye
`f1-mac-editor-ctrl-b.png`, `tiptap-meta-b.png`, `f1-win-cdp-body-ctrl-b.png`, `shell-1440-dark-expanded.png`, `rail-tooltip-hover.png` (clean "Matters" tooltip), `mobile-390-sheet-light.png` (Sheet with Fees/Trust/Firm below the fold, F2). None is a login page.

## Files (this folder)
- Scripts: `obs.cjs`, `obs-probe.cjs`, `obs-probe2.cjs`, `obs-probe3.cjs` (copies, diff above); `obs-probe4-f1.cjs` (new).
- Data: `obs-report.json`, `obs-probe-report.json`, `obs-probe2-report.json`, `obs-probe3-report.json`, `obs-probe4-f1-report.json`; `script-diff.txt`; stdout logs `probe2.log`, `probe3.log`, `probe4.log`. The `obs.cjs` and `obs-probe.cjs` stdout was read in the terminal and not saved; their JSON reports hold the same data.
- PNGs: the same 21 names as the first OBS: `shell-{1024,1440}-{light,dark}-{expanded,collapsed}.png`, `rail-1440-collapsed-bottom.png`, `rail-tooltip-{hover,focus}.png`, `keyboard-zone-toggle.png`, `tiptap-meta-b.png`, `tiptap-ctrl-b-mac.png`, `mobile-390-{closed,sheet}-{light,dark}.png`, `mobile-390-sheet-scrolled-light.png`, `mobile-390-after-nav.png`, `matters-1440-light.png`. Plus 6 new: `f1-{mac,win-np,win-cdp}-{editor,body}-ctrl-b.png`.

## Orchestrator probe 5: active bar (D8) and separator inset (D15)

`obs-probe5-bar-separators.cjs` → `obs-probe5-report.json`, `bar-separators-1440-{light,dark}-{expanded,collapsed}.png`. Run at 1440×900 on the same stack, `57fa7a98f`.

| Check | light / expanded | light / collapsed | dark / expanded | dark / collapsed |
|---|---|---|---|---|
| Active item (`aria-current="page"`) | Home, `data-active=true` | Home | Home | Home |
| `::before` bar background vs resolved `var(--teal-500)` | equal, `lab(69.32 -52.28 -5.24)`, 2px | equal | equal | equal |
| Visible separators in the sidebar container / all inside it | 10 / yes (widths 223, 239 in a 256 container) | 9 / yes (15, 31 in a 48 rail) | 10 / yes | 9 / yes |
| Console errors and warnings | 0 | 0 | 0 | 0 |

D8 (teal `--sidebar-primary` bar) and D15 (inset separators, none overflowing the rail): **PASS**.

Note on the console. The first version of this probe navigated a second time immediately after the login redirect, which aborted the dashboard's in-flight mock `userinfo` fetch. Three of the four runs then logged `MockAuthContextProvider: failed to fetch userinfo TypeError: Failed to fetch`. The probe now waits for `load` + `h1` + 1.5 s before navigating again (commented `v2` in the script), and the message no longer appears (0 in all four runs). The message came from the harness, not the product. The OBS scripts above, which wait for readiness, never logged it.
