# 642C after-observation (OBS, task 642.14)

Captured 2026-11-13 against the e2e mock-auth stack on :3001 (backend :8081). The stack was freshly built from the worktree @ `32c89d2c1` (branch `phase90/42C-grouped-tab-bar`). I used the Playwright-library script `obs-after.cjs` (nvm node v20.11.1, `@playwright/test` from the worktree `frontend/`), based on `../before/obs-before.cjs`. I did not use the MCP browser. The theme was set via `localStorage["kazi-theme"]`. No source files were edited. Raw data is in `obs-after.json` (compact) and the console log of the run is in `run.txt`.

## Pages (lifecycle run `mwe43hoif41f` on the fresh stack)

| Page | URL |
|---|---|
| Matter | `/org/e2e-test-org/matters/c77bf2fa-a12d-4d2d-bb4c-1e6476c300b8`: "Ndlovu v RAF mwe43hoif41f", LITIGATION, next court date 12 Jan 2027, outstanding R 5 482,63 |
| Client | `/org/e2e-test-org/clients/690187db-6dd1-40a3-93f9-0121b0d0f783`: "Sipho Ndlovu mwe43hoif41f", 2 matters, fee notes, trust, FICA done |

## Results

| # | Check | Result | Key numbers |
|---|---|---|---|
| 1 | Both pages, Alice, 1440/1024 × light/dark | **PASS** | 8/8 loads: 1 `role=tablist` (1 visible), 1 visible tabpanel (matter 22 total / client 14 total). Active trigger `::after`: opacity 1, height 2px, bg = `--foreground`. Inactive triggers: `::after` opacity 0, colour = `--muted-foreground` (light `lab(35.5…)`, dark `lab(65.1…)`); active colour = `--foreground`. Trigger heights all 40px at 1440 and 1024 (one line, also at 390/768). Sticky bar bg = `--background`, alpha 1 (light `lab(96.7…)`, dark `lab(2.2…)`). Pixel sample of the stuck bar: uniform 244,246,248 (light), 6,7,10 (dark). The only gradient is the header's own drop shadow at y 64–75. |
| 2 | ≥3 tabs/page incl. dropdowns, URL mechanics, menu not clipped | **PASS** | Matter: Diary → `court-dates`, Work→Tasks → `tasks`, Fees→Disbursements → `disbursements`, Fees→Statements → `statements`. Every change: 2× `replaceState`, **0 RSC**, 0 navigations. Client: Activity → `audit`, Matters→Documents → `documents`, Fees→Trust → `trust`, Fees→Fee notes → `invoices`. Every change: 1× `replaceState` + **1 RSC** (5 on Fee notes = 1 + 4 `<Link>` prefetches, as before). aria-selected moved each time and the underline followed (opacity 1). All 6 menus were fully inside the 1440×900 viewport (largest: Fees on the matter, 510→744). The current item has `data-active="true"`. Screenshots: `matter-dropdown-work-open-1440-light.png`, `client-dropdown-fees-open-1440-light.png`. |
| 3 | Sticky | **PASS** | Matter `?tab=activity` @1440×900, scrolled 600 (settled, max 857): bar top **466 → 64** (light and dark). Client `?tab=audit`: at 1440×900 the page only scrolls 359px, so the bar can't reach its offset (467 → 108). I re-measured at **1440×600** (max 659), scrolled 600: **467 → 64** (light and dark). Header rect y 8→64, z 30; bar z 20. `elementFromPoint`: header centre and header bottom edge hit the header (not covered); bar empty area, tab centre and header+4px hit the sticky bar (no bleed-through). Dropdown opened while stuck (Details menu, y 108→214) is in the viewport, and the topmost element at its last item is the menuitem (`tab-item-team` / `tab-item-tags`). After Escape the bar stays at 64 and scrollY stays 600. Screenshots: `alice-{matter-…-1440x900,client-…-1440x600}-sticky-scrolled600-{light,dark}.png`, `matter-sticky-menu-open-1440-light.png`. |
| 4 | Overflow / F7 / scrollbar / fades | **PASS** (matter page overflow is F8, not the tab bar) | Client doc scrollW/innerW: **390/390** (before 515), **768/768** (before 779), 1024/1024. Matter: **609/390**, **873/768** (unchanged from before). The widest elements are the header action row (`Generate Document…` div and `overflow-actions-trigger`, right = 609/873), i.e. **F8 → 644B**. The tab row is no longer an offender, and matter 1024 is 1024/1024. Row viewport scrollW/clientW: matter 536/326 @390, 536/432 @768; client 563/358, 563/464; no overflow at 1024 (672/672, 720/720 → no scrollbar, both fades hidden). Horizontal scrollbar **mounted on load with no prior scroll** (`data-state=visible`, opacity 1, h 6px, thumb 197/347/226/381px wide). Scrollbar top equals the max trigger bottom (808=808, 708=708, 729=729, 681=681), so the underline is **not covered**; the root gets `pb-1.5` (6px) only while overflowing. Fades: at scrollLeft 0, start hidden (opacity 0) and end visible (1); mid → both 1; end → start 1, end 0. Fades are `pointer-events: none`. A mouse click 10px inside the end fade landed on the partly hidden trigger (`elementFromPoint` = trigger), which opened its menu or navigated (`court-dates` @768 matter). A normal Playwright click on the last trigger succeeded on all 4 overflowing cases (matter Activity → menu opened; client Activity → `?tab=audit`). |
| 5 | Deep link at 390 | **PASS** | Matter `?tab=disbursements`: scrollLeft 126, active "Fees · Disbursements" x 151–326 inside viewport 32–358 (clears the 32px end fade), start fade visible, window.scrollY **0**, max scrollY during load **0**. Client `?tab=audit`: scrollLeft 205 (max), "Activity" 309–374 inside 16–374, start fade visible / end hidden, scrollY 0, max 0. Screenshots `alice-*-390-deeplink-*.png`. |
| 6 | Keyboard | **FAIL (partial)** | 1440: focus() → ArrowRight ×2 → `tab-group-work` on both pages, `:focus-visible`, box-shadow `lab(52.87 -45.78 -2.85) 0 0 0 2px inset` (= `--ring`). Ring fully drawn and not clipped (`alice-*-keyboard-focus-1440-light.png`), fixes before-finding F-c. Arrowing to a **fully off-screen** trigger scrolls it in (matter Diary → scrollLeft 210, client Activity → 205), and the wrap back to Details resets to 0. **Defect:** arrowing (or Tab-ing) to a **partly hidden** trigger does NOT scroll it into view. Matter `tab-group-client` x 349–419 in viewport 32–358: **9 of 70px visible**, all of it under the end fade (0px outside the fade). Client `tab-group-compliance` ("FICA & compliance") 349–505 in 16–374: **25 of 156px visible**, also all under the fade. It was unchanged after 1s, and the Tab key gives the same result. The focus ring is a faint sliver behind the fade (`alice-matter-keyboard-partial-390-light.png`, `alice-client-keyboard-partial-390-light.png`). window.scrollY/scrollX stay 0. |
| 7 | Carol matter 1440 light | **PASS** | 7 groups, `tab-group-activity` is a single tab (no `aria-haspopup`; Audit hidden), same as before. All load checks are ok (1 tablist, 1 panel, underline, muted, opaque, 40px). `carol-matter-1440-light.png`. |
| 8 | Console | **PASS** | 0 errors, 0 warnings, **0 hydration** across all loads (Alice ×~40 navigations, keyboard context, Carol). |
| 9 | SEL-COMPAT | **PASS** | `git diff origin/main --stat -- frontend/e2e` is empty: no spec was edited and no role changed. The selector → file:line map is in `sel-compat-after.txt`. |

## Defect: D-642C-OBS-1, a keyboard-focused, partly hidden trigger stays hidden under the end fade (390 px)

- **Repro:** 390×844, matter or client detail, default tab. Focus "Details", then press ArrowRight 4 times. Focus lands on Client (matter) or FICA & compliance (client), which is 9px or 25px visible, entirely under the 32px end fade, and the row does not scroll. Tab from the 4th trigger gives the same result.
- **Hypothesis:** `frontend/components/shared/grouped-tab-bar.tsx:159-175`. `handleKeyDown` calls `groupRefs.current[nextIndex]?.focus()` and relies on the browser's focus-scroll. Chromium does not scroll a scroll container for an element that is already *partly* visible, so it only scrolls the fully hidden triggers (Diary/Activity scrolled fine). The keep-in-view effect at `:143-156` already has the right fade-aware maths (`EDGE_FADE_PX`), but it keys only on `activeTab`, not on focus. A likely fix is to run the same scroll-into-viewport logic for the focused trigger: an `onFocus` on the triggers, which also covers Tab and Shift+Tab. That is a builder/orchestrator call. WCAG 2.4.11 (Focus Not Obscured) is at stake.
- **Scope:** only when the row overflows (≤ 768 here). 1440/1024 are unaffected.

## Other observations (not failures)

- **F-a is unchanged.** Clicking an inactive multi-tab group opens its menu on the first click and does not navigate (`visits[].menu.openedOnFirstClick = true`; screenshot `client-dropdown-fees-open…` shows "Matters · Documents" still underlined while the Fees menu is open). The `onClick` "navigate to first sub-tab" branch (`grouped-tab-bar.tsx:236-243`) is still dead in real browsers. It was carried over as-is; this is not a regression.
- **Scrollbar contrast is low.** The thumb is `bg-border` (≈224 on 244 light) at 6px. It is present and measurable, but hard to see at 1× in `alice-client-390-light.png`; it is clearly visible in the 2× crops.
- **The sticky bar height grows by 6px (41 → 47)** when the row overflows, because of the `pb-1.5` strip. This is by design.
- **Matter "Activity · Activity" label.** The Activity group label repeats the sub-tab name when the Activity sub-tab is active. This was the label logic before the slice and is cosmetic.
- The client bar sits at y 577 at 1024 (before: 625). This is a different client record on the fresh stack (the hero content wraps differently), not a tab-bar change. At 1440 the positions match before exactly (matter 466, client 467).
- When I first viewed the dark sticky screenshot it looked light. Pixel sampling (6,7,10 bar / 7,8,11 content) confirms it is dark; the bar and theme are correct.

## Comparison with before (`../before/obs-before.json`)

| | Before (e2e73ca8f) | After (32c89d2c1) |
|---|---|---|
| Bar sticky? | No (`position: relative`); matter `?tab=activity` 466 → **−134** | Yes; 466 → **64** (matter), 467 → **64** (client @1440×600) |
| Bar background | transparent | `--background`, alpha 1, both themes |
| F7 client doc width | 515/390, 779/768 | **390/390, 768/768** (fixed) |
| F7 matter doc width | 609/390, 873/768 (header actions) | 609/390, 873/768 (unchanged → F8/644B; tab row no longer overflows the page) |
| Tab row at 390 | `overflow-x: visible`, spills, "FICA & compliance" wraps to 2 lines | scrolls inside the ScrollArea viewport, all triggers 40px (one line) |
| Focus ring | none (outline-none won) | 2px inset `--ring` box-shadow, visible |
| URL mechanics | matter replaceState/0 RSC, client router.replace/1 RSC | identical |
| Console | 0/0/0 | 0/0/0 |

## Files

`obs-after.cjs`, `obs-after.json`, `run.txt`, `sel-compat-after.txt`, and 27 screenshots: `alice-{matter,client}-{1440,1024}-{light,dark}.png`, `alice-{matter,client}-{390,768}-light.png`, `{matter-dropdown-work,client-dropdown-fees}-open-1440-light.png`, `alice-matter-sticky-scrolled600-1440x900-{light,dark}.png`, `alice-client-sticky-scrolled600-1440x600-{light,dark}.png`, `matter-sticky-menu-open-1440-light.png`, `alice-{matter-390-deeplink-disbursements,client-390-deeplink-audit}.png`, `alice-{matter,client}-keyboard-focus-1440-light.png`, `alice-matter-keyboard-focus-390-light.png`, `alice-{matter,client}-keyboard-partial-390-light.png`, `carol-matter-1440-light.png`.
