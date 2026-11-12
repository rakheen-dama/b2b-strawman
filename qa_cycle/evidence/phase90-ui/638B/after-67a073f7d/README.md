# 638B "after": floating AppHeader and command palette restyle (task 638.12 OBS)

- **Head:** `67a073f7d` on the 638B worktree. The commit is "fix(phase90-638B): glyph-only palette hint footer; app-header test cookie cleanup", on top of `d1893e6f4`, the floating AppHeader and palette restyle (E2B).
- **Stack provenance:** the orchestrator brought up the E2E mock-auth stack fresh on 2026-11-12: `e2e-down` → docker builder prune → `e2e-up`, built from `worktree-epic-638B`.
  - Ports: frontend :3001, backend :8081, mock IDP :8090. Org `e2e-test-org`.
  - The legal-lifecycle and depth-ii suites (E2E-LEGAL) ran on this stack before this capture, so the tenant has data.
  - This task did not touch docker, and did not run e2e-up, e2e-down or e2e-reseed.
- **Captured:** 2026-11-12, as Alice (owner) via `/mock-login` → "Sign In".
- **Method:** `after.cjs` is `../before/before.cjs` extended.
  - Same Playwright **library** setup: headless chromium from `worktree-epic-638B/frontend/node_modules/@playwright/test`.
  - Same login. `storageState` is reused in a fresh context for each capture.
  - Same theme init script: `localStorage['kazi-theme']`.
  - Same waits: `load`, then a visible `h1`, then 1.2 s. Never `networkidle`. Screenshots are viewport-only.
  - Scrolled captures use **max scroll** (`scrollTo(0, scrollHeight)`) as the brief asks. "before" used `scrollTo(0,600)`.
  - `after.cjs` is byte-for-byte the script that ran.
  - `focus-probe.cjs` is a follow-up probe, run after `after.cjs`. It wrote `focus-probe.json`; see F1.
- **Deviation:** this used a library script, not Playwright MCP. The MCP Chrome profile is held by the user's live session. No Chrome process was killed.
- **Data:** see `after-report.json`. It covers, for each capture:
  - header tag, role, box, position, top, z-index, radius, border, box-shadow, background, backdrop and overflow
  - `#main-content` box, its first content, and the subscription banner
  - Search and Toggle-menu counts, with where each control lives
  - the palette dialog facts
  - console messages and hydration messages
  - tab order (on the 1440 captures)
  - user-menu theme switching and the bell dropdown hit-tests
  - a `darkTokenCheck` block

## Captures

| File | Viewport | Theme | What |
|---|---|---|---|
| header-1024-light.png | 1024×768 | light | dashboard, scroll 0 |
| header-1024-dark.png | 1024×768 | dark | dashboard, scroll 0 |
| header-1440-light.png | 1440×900 | light | dashboard, scroll 0; tab order recorded |
| header-1440-dark.png | 1440×900 | dark | dashboard, scroll 0; tab order recorded |
| header-scrolled-1440-light.png | 1440×900 | light | dashboard at max scroll (scrollY 1436 of 2336) |
| header-scrolled-1440-dark.png | 1440×900 | dark | same |
| palette-1440-light.png | 1440×900 | light | palette opened with `Meta+k`; ⌘K toggle and Esc checked afterwards |
| palette-1440-dark.png | 1440×900 | dark | palette opened with `Meta+k` |
| palette-search-button-1440-light.png | 1440×900 | light | palette opened by **clicking** the `banner` button "Search, Command K" |
| mobile-390-light.png | 390×844 | light | dashboard, scroll 0 |
| mobile-390-dark.png | 390×844 | dark | dashboard, scroll 0 |
| mobile-390-palette-light.png | 390×844 | light | palette opened by clicking the header's icon-only Search button |
| user-menu-1440-light.png | 1440×900 | light | avatar menu open, Theme Light/Dark/System. "Dark" was clicked **after** the screenshot. |
| user-menu-1440-dark.png | 1440×900 | dark | avatar menu open, Dark selected |
| bell-1440-light.png | 1440×900 | light | Notifications dropdown opened while scrolled to max |

## Observations

| # | Observation | Result | Evidence |
|---|---|---|---|
| O1 | There is one `<header>` (implicit `banner`). It sits outside `<main>` and before `main#main-content`. There is 1 `main` in every capture. | PASS | `after-report.json` `headerCount`=1, `headerRole`="banner (implicit)", `headerInsideMain`=false, `mainCount`=1 in all 15 captures |
| O2 | The header floats: `position: sticky; top: 8px` (top-2), z-index 30, `border-radius: 12px`, 1px border, `backdrop-filter: blur(12px)`, `box-shadow` = `--shadow-overlay`, translucent background. | PASS | light bg `oklab(0.996 … / 0.9)`, shadow `0 8px 24px lab(2.2 … / 0.12)`. Dark bg `oklab(0.205 … / 0.9)`, shadow `0 8px 24px lab(0 0 0 / 0.5)`. |
| O3 | The header is inset from the edges. Height is 56 px (`--app-header-height` 3.5rem). | PASS | 1440: x=272, y=8, 1152×56 (16 px inset from the 256 px sidebar edge and from the right edge). 1024: x=272, y=8, 736×56. 390: x=8, y=8, 374×56. |
| O4 | At scroll 0 the header does not overlap content. Header bottom is 64 = `#main-content` top (64). The first content (`h1` "Home") is at top 88. No subscription banner is rendered on this tenant, so there was nothing to overlap. | PASS | `noOverlapAtScroll0`=true at 1024, 1440 and 390, in both themes. `subscriptionBanner`=null. |
| O5 | After max scroll the header stays at y=8: the delta from scroll 0 is 0 px, in both themes. Content scrolls under it. Hit-tests inside the header band land on the header. | PASS | `header-scrolled-1440-*`: `headerBox.y`=8, `headerYDeltaFromScroll0`=0, `mainTop`=-1372, `headerHitTest` all `inHeader:true` |
| O6 | No button is named exactly "Search". Both "Search, Command K" buttons appear in the /search/i list. | PASS | `searchButtonExact`=0 in all captures. /search/i: 2 at 1024 and 1440 (sidebar pill at (8,106,239×32) plus banner button at (1085,20,127×32) at 1440, (669,20) at 1024). 1 at 390 (banner only, 34×32 icon-only). 0 while a modal palette is open (page is aria-hidden). |
| O7 | There is exactly 1 "Toggle menu" at every viewport (0 only while the palette is modal, same as before). | PASS | `toggleMenuCount`=1 |
| O8 | Tab order from Toggle menu is DOM order, which now matches visual order. The org chip is a non-focusable div, as before. | PASS (changed by design) | 1440, both themes: Toggle menu → E2E Test Organization → **Search, Command K** → **Notifications** → **AO** → Open diary → Open unbilled work → Open collections → Open reconciliation → View all |
| O9 | `Meta+k` opens the palette, a second `Meta+k` closes it, `Meta+k` reopens it, and Esc closes it. Clicking the header Search button opens it at 1440 and at 390. The button has `aria-keyshortcuts="Meta+K Control+K"`. | PASS | `paletteOpened`=true, `metaKSecondPressClosed`=true, `metaKReopened`=true, `escClosed`=true. `headerSearchButtonCount`=1. Mobile `mobileHeaderSearchVisible`=true and the palette opened. |
| O10 | Palette surface. Group headings are uppercase. | PASS | Dialog box 512×387 at (464,257) on 1440. Mobile: 358×387 at (16,229). Inner surface `rounded-xl` = 12px, `bg-popover`, shadow `--shadow-overlay`. Headings "Pages" and "Settings": `text-transform: uppercase`, `font-size: 11px`, `letter-spacing: 0.55px`. 60 options. Placeholder "Search pages, settings...". |
| O11 | The palette hint footer exists. It has `aria-hidden="true"`, sits outside the listbox, and contains **only glyphs**. | PASS | `hintFooter`: kbds `["↑","↓","↵","Esc"]`, `nonKbdText`="" , `onlyGlyphs`=true, `insideListbox`=false |
| O12 | Theme can be reached from the header. The avatar `AO` opens a menu with "Theme" and Light/Dark/System radios. Clicking "Dark" switches `<html>` class `light`→`dark`, sets `kazi-theme`=dark, and the header bg changes to the dark value. | PASS | `user-menu-1440-light`: `themeSwitchedViaHeader`=true, `headerBgAfterSwitch`=`oklab(0.205 … / 0.9)`. The menu panel (1155,60,256×170) extends below the header and its bottom hit-test is inside the panel. |
| O13 | The bell dropdown, opened while scrolled, paints above page content and is not clipped by the header. The header has `overflow: visible`, and the dropdown has z-index 50 inside the z-30 header. | PASS | `bellDropdown`: box (930,56,322×400), bottom 456 vs header bottom 64. All 3 hit-tests (centre, bottom-left, bottom-right) are `inDropdown:true`. |
| O14 | Dark tokens resolve: the header bg and palette bg are not the light values. | PASS | `darkTokenCheck`: header light `oklab(0.996…/0.9)` vs dark `oklab(0.205…/0.9)`. Palette surface light `lab(100 0 0)` vs dark `lab(11.2 -0.47 -4.41)`. `--card`, `--popover` and `--shadow-overlay` all differ. `headerBgDiffers`=true, `paletteBgDiffers`=true. |
| O15 | Console: 0 errors/warnings, 0 page errors, 0 hydration messages across all 15 captures. The Tiptap duplicate-extension warning did not appear. | PASS | `console`=[] and `hydrationMessages`=[] in every capture |
| O16 | Mobile 390: the header fits the viewport with no horizontal overflow. The separator and breadcrumb root are hidden below `sm`, leaving only the "Home" crumb. Search is icon-only. | PASS | `headerOverflowsViewport`: scrollWidth 372 = clientWidth 372, document 390, `controlsOutside`=[] |
| O17 | Visual review of the PNGs (described below). | PASS | all PNGs |

### Visual review (PNGs read by eye)

- **Light (1440 and 1024):**
  - The header is a white, rounded card floating on the slate page background, with a soft shadow and an 8 px gap above it and 16 px on each side.
  - The breadcrumb "E2E Test Organization › **Home**" is muted with a bold last crumb.
  - On the right: a bordered "Search ⌘K" pill, the bell with its teal "18" badge, the mock `e2e-test-org` chip, and the `AO` avatar.
  - A thin vertical separator sits after the sidebar trigger.
  - Nothing is cramped at 1024.
- **Dark:**
  - The header is a dark card, a step lighter than the near-black page, with a visible hairline border.
  - Text contrast is fine: muted crumb root, white last crumb.
  - The ⌘K kbd chip, the bell badge and the org chip are all legible.
- **Scrolled (both themes):**
  - Content (the Recent Activity / Sensitive events cards) scrolls beneath the header.
  - Card edges are visible in the 8 px strip above the header, which is the intended floating look.
  - The header blurs and mostly hides content behind it, and its shadow separates it from the cards.
  - No clipping, no jitter.
- **Palette (light, dark and 390):**
  - It is a rounded-xl panel over a blurred, dimmed backdrop.
  - The "PAGES" heading is small uppercase with tracking.
  - The footer shows ↑ ↓ ↵ on the left and Esc on the right as kbd chips with no words.
  - The dark palette is the popover tone (a step above the card), with a visible border.
  - At 390 it spans 16 px from each edge.
- **User menu (light and dark):**
  - The panel drops from the avatar.
  - Theme is a segmented control with the active segment filled teal: Light in light, Dark in dark.
  - Sign out is below it.
- **Bell:**
  - The dropdown lists notifications above the scrolled Recent Activity content, fully opaque, with "View all notifications" visible at the bottom.
  - Nothing is clipped.
- **Mobile 390:**
  - The header is inset 8 px and holds the trigger, the "Home" crumb, the icon Search, the bell, the org chip and `AO` without wrapping.
- **Cosmetic, not a regression:**
  - Both hand-rolled popovers start slightly inside the header's lower band. The bell starts at y=56 (`mt-1`) and the user menu at y=60 (`mt-2`); the header bottom is 64.
  - So the panel top overlaps the header's bottom 4–8 px.
  - This is inferred, not measured on main: the old 56 px header centred the same 32 px controls with 12 px below them, so the same 8 px overlap would have existed. "before" did not open the bell.
  - It reads as a dropdown attached to the bar and is not a defect.

## Defects

| # | Severity | Finding | Evidence |
|---|---|---|---|
| F1 | LOW, pre-existing (not introduced by 638B) | After the palette closes (Esc), focus goes to `<body>`, not back to the control that opened it. This happens for the new header Search button (click and keyboard Enter), the sidebar pill, and `Meta+k` pressed with Notifications focused. It is an a11y nit (WCAG 2.4.3, focus order: the user loses their place). The sidebar-pill path and the provider-controlled open are unchanged by 638B. 638B only added a className and the footer to the dialog. So this is inferred to be pre-existing, but it was **not observed on main** because the main stack is no longer up. The new header button inherits the behaviour. | `focus-probe.json`: all 4 cases show `whileOpen`=input, then `focusRightAfterClose` / `focusAfter800ms` = `body`. `after-report.json` `palette-search-button-1440-light.focusAfterEsc`="BODY". |

No defects were found in 638B's own scope: the floating header, the palette restyle, the Search trigger, the theme reachability, and dark tokens.

## Before → after (main `9d7d2b62e` → 638B `67a073f7d`)

| Fact | before | after |
|---|---|---|
| Header classes | `sticky top-0 z-30 h-14 border-b bg-slate-100/80 backdrop-blur-md`. Full-bleed bar. | `bg-card/90 sticky top-2 z-30 mx-2 mt-2 h-(--app-header-height) rounded-xl border shadow-(--shadow-overlay) backdrop-blur-md md:mx-4`. Floating card. |
| `position` / `top` | sticky / 0px | sticky / 8px |
| Box at 1440 | (256, 0, 1184×56) | (272, 8, 1152×56): 16 px side inset, 8 px top inset |
| Box at 1024 | (256, 0, 768×56) | (272, 8, 736×56) |
| Box at 390 | (0, 0, 390×56) | (8, 8, 374×56) |
| Header y after scroll | 0 (scrollY 386) | 8 (scrollY 1436; the dashboard is longer now because of E2E-LEGAL data: 2336 vs 1286 px) |
| Radius / shadow | none / none | 12px / `--shadow-overlay` |
| Background | `lab(95.1 … / 0.8)` light, `lab(2.2 … / 0.9)` dark | `oklab(0.996 … / 0.9)` light (card), `oklab(0.205 … / 0.9)` dark (card) |
| `main` count | 1 | 1 |
| Buttons named exactly "Search" | 0 | 0 |
| /search/i buttons, 1024/1440 | 1 (sidebar pill only) | 2: sidebar pill plus banner "Search, Command K". The duplicate name is intentional, both open the palette, and no e2e spec targets that name. |
| /search/i buttons, 390 | 0 | 1 (banner, icon-only) |
| "Toggle menu" | 1 | 1 |
| Header controls (1440) | Toggle menu, org link, AO, Notifications | Toggle menu, org link, **Search, Command K**, Notifications, AO |
| Tab order from Toggle menu | Toggle → org link → **AO → Notifications** → page | Toggle → org link → **Search, Command K → Notifications → AO** → page. The avatar now comes after the bell, matching the visual order. |
| Palette | 512×350, no hint footer, headings in default case | 512×387 (the footer adds about 37 px), `bg-popover` with a 12px radius and `--shadow-overlay`, 11px uppercase tracked headings, aria-hidden glyph-only footer |
| Theme toggle reachable from header | yes (avatar menu) | yes (avatar menu). Click "Dark" → `<html class="dark">` was verified. |
| Console / hydration | 0 / 0 (11 captures) | 0 / 0 (15 captures) |

## Files

- `after.cjs`: the capture script, exactly as run.
- `after-report.json`: per-capture facts, plus `darkTokenCheck`.
- `focus-probe.cjs` and `focus-probe.json`: the follow-up focus-return probe (F1).
- 15 PNGs, listed in the table above.
