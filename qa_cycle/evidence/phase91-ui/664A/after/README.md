# 664A OBS-after: settings shell (PR #1868, head `14d5b9b84`)

Captured 2026-11-26 (~12:25–12:55Z) on the e2e mock-auth stack (frontend :3001, backend :8081, mock IDP :8090). Playwright library, headless Chromium, DPR 1, reduced motion, timezone Africa/Johannesburg, app rail expanded, theme set through localStorage `kazi-theme`. Data is the same as before/ (the backend and Postgres were not touched).

## Stack observed

- `SHELL=/bin/bash bash compose/scripts/e2e-rebuild.sh frontend` was run from the worktree root. Frontend container created `2026-11-26T12:22:52Z`, image `c8124e3fc…`. The compose label `working_dir` is `worktree-epic-664A/compose`. `git status` was clean at `14d5b9b84`, so the build is the PR head.
- The new code was confirmed live: at 390 a visible `nav[aria-label="Settings navigation"]` appeared on `/settings/firm` (count 1, getByRole 1).
- Side effect: compose also recreated `e2e-localstack`, which is a dependency. Backend, Postgres, mock IDP and Mailpit kept running (Up 50 min).

## Scripts (`scripts/`, nvm node 20, run in order)

| Script | What |
|---|---|
| `00-discover.cjs`, `01-capture.cjs`, `02-assemble.cjs` | Copies of `../before/scripts/`. **Only the phase/output constants changed:** RAW `/tmp/664A-before-raw` became `/tmp/664A-after-raw`, sheet titles say "after" instead of "before", and the `stack` string was updated. Two stale hard-coded captions were made neutral: the rail-sticky tile caption `(sticky top-14 = 56)` became `(css top ${railCssTop})`, and the K-03 sheet title no longer says "`top-14` (56 px)". Probes, sheets, matrix, sel-compat and console capture are otherwise identical. |
| `03-verify.cjs` (new) | The 664A acceptance probes. Writes `verify.json`. |
| `04-kbd.cjs` (new) | Keyboard walk. Writes `kbd.json`, `sheet-kbd-390.png`, `sheet-kbd-1024.png`. |
| `05-assemble-verify.cjs` (new) | Builds the verification sheets from 03's raw tiles. |

**Caveat on the copied probe:** `01-capture.cjs`'s SHELL probe takes the tab strip's `parentElement` as its scroller. After 664A that element is Radix's inner table wrapper, so in `measurements.json` the `strip.scrollerScrollWidth/ClientWidth/overflow` values are not meaningful (they read `overflow:false` everywhere). The real overflow figures are in `verify.json` → `strip.*.viewport` (`[data-slot=scroll-area-viewport]`).

## Sheets

| File | Contents |
|---|---|
| `sheet-{firm,matters,documents,fees,connections}-{alice,carol}.png` | Same grid as before: rows 1024 / 1440, cols light / dark |
| `sheet-390-alice.png`, `sheet-390-carol.png` | 390×844: the five families in light, plus Firm in dark |
| `sheet-forbidden-notice.png` | K-12, Carol 1440: `?tab=audit-trail` and `?tab=roles`, light / dark |
| `sheet-pill-tab-strip.png` | Same keys as before (now the underline strip), Alice 1440, element shots |
| `sheet-rail-sticky.png` | K-03, Alice light Firm › General, scrollY 0 / 600 at 1024 and 1440 |
| `sheet-grouped-tab-bar.png` | GroupedTabBar consumers in the default state (same shots as before) |
| `sheet-k02-two-rows-390.png` | 390 Alice Firm, top 300 px at 2×: family bar above the tab strip, light / dark |
| `sheet-k02-family-bar-390.png` | K-02 click-through for both personas (load Firm → Matters → Documents → Fees & Trust → Connections → Firm), plus dark Firm and a dark Connections deep link |
| `sheet-strip-1024.png` | K-101: Alice 1024 Firm / Matters / Fees strips, light / dark, shown at start and scrolled to the end |
| `sheet-k20-grouped-tab-bar.png` | K-20: five deduplicated / normal labels, plus the before/after pixel-diff images (all grey = 0 px differ) |
| `sheet-rail-k03-k100.png` | K-03 rail at 1440 scrollY 600, light / dark; K-100 rail at 1024 (Request templates, Trust accounting); Connections › Integrations and › AI at 1024 |
| `sheet-kbd-390.png`, `sheet-kbd-1024.png` | KBD: one crop per focus step (390 light / dark; 1024 light) |

JSON: `measurements.json`, `sel-compat.json`, `console.json` (01), `verify.json` (03), `kbd.json` (04), `verdicts.json` (single line).

## Verdicts

| # | Check | Verdict | Key numbers |
|---|---|---|---|
| 1 | **K-02** | **PASS** | At 390, for both personas, exactly one visible `nav "Settings navigation"` (the family bar). Links in order Firm, Matters, Documents, Fees & Trust, Connections, with real hrefs `/settings/{family}`. The active link has `aria-current="page"` and an `::after` underline at 2px, opacity 1; the others are at opacity 0. Clicking each link through the bar navigated every time, for both personas: URL, h1, tablist "{Family} settings" and active link all updated. The bar overflows (543 px of content in 358). The active link is scrolled clear of the fades: Fees & Trust scrollLeft 89, Connections 185; a deep link to Connections also loads at 185. Landmark count: `getByRole('navigation',{name})` = 1 and `ariaSnapshot` = 1 at 390, 1024 and 1440 for both personas. The visible landmark is the family bar at 390 and the rail at 1024/1440. Before 664A, 390 had 0 family links in the a11y tree; now all 5 are there. |
| 2 | **K-03** | **PASS** | After `scrollTo(0,600)` on Firm › General: rail top **64** = header bottom **64**, overlap 0 (before: rail 56 vs header 64). Holds at 1024 and 1440, light and dark. CSS `top: 64px`, `height: 696px` / `828px`. Rail bottom 760 ≤ 768 and 892 ≤ 900. Active tab: bg `#eff1f5` = `--muted` (light) and `#20242b` = `--muted` (dark). `::before` bar is 2px wide, inset-y 4px, `#14171c` = `--primary` (light) and `#ffffff` = `--primary` (dark). `border-left` is 0, so the hard-coded teal border is gone. |
| 3 | **Tab strip (K-101 / 664.4)** | **PASS** | Across 40 strip probes (5 families × 390/1024/1440 × light/dark × Alice, plus Carol 1024/1440 light), every strip has exactly one visible underline: 2px, `bottom: 0`, inside the scroll viewport, never clipped. The tablist itself has no `::after` and no border. At 1024, Alice's overflowing strips now scroll: Firm 749/496, Matters 782/496, Fees & Trust 538/496. Each has a horizontal scrollbar mounted, the end fade at opacity 1 when at the start, and the start fade at opacity 1 / end fade at 0 when scrolled to the end. Documents and Connections fit. Click "Roles" → `?tab=roles`, h1 "Roles & Permissions", focus on "New Role" inside the panel. ArrowRight/ArrowLeft move focus only and leave the URL unchanged. Enter → `?tab=audit-trail` and Space → `?tab=data-protection` (390 Matters: Enter → custom-fields, Space → tags), with focus moving into the panel each time. `role="tab"`, `aria-selected` and the tablist name "{Family} settings" are unchanged. |
| 4 | **K-12** | **PASS** | Text is unchanged: "You don't have access to Audit trail — showing General instead." (and the same for Roles). `role="status"`. Light: text `#4d545f` on `#eff1f5`, border `#e0e3e8`, matching `--muted-foreground` / `--muted` / `--border` exactly; contrast **6.756:1**. Dark: `#989faa` on `#20242b`, border `#262a31`, all tokens; contrast **5.839:1**. Both are ≥ 4.5:1 (before, with the hard-coded slate pair: 9.92 / 11.58). |
| 5 | **K-20** | **PASS** | All 32 grouped sub-tabs (20 matter, 12 client) were opened via `?tab=` and the active trigger label read. **32/32 match the expected label.** The four that now show the group label once are matter `details` → "Details", matter `activity` → "Activity", client `details` → "Details" and client `matters` → "Matters"; before 664A these rendered "X · X". Every other label is "Group · Sub" as before. In the default state the bar element shots are **pixel-identical** to before (matter 0/44 280 px, client 0/46 248 px). The full viewports differ by 3 and 19 px at delta 1, all in the header around x 1077–1255, y 30–60 (not the bar). The selected `::after` matches before exactly: `height 2px`, `#14171c`, `bottom/left/right 0px`, `border-radius 3.35544e+07px`, opacity 1 / 0; the sticky bar is `top 64px`, z 20. Caveat: before/ did not capture a "Details"-active state, so "before would be X · X" comes from the diff of `grouped-tab-bar.tsx` (the old condition had no `activeSubTab.label !== group.label`). |
| 6 | **K-100 option A** | **PASS** | Rail `aside` width is **192px at 1024** and **240px at 1280 and 1440**. At every width, 0 rail links are wrapped or truncated (text-node line count 1, scrollWidth ≤ clientWidth), including "Request templates", "Request settings" and "Trust accounting" at 1024. **Remainder:** Connections › Integrations still overflows at 1024 (see `found.md`, K-100 remainder → 665F). Connections › AI does not (0 elements past the panel). |
| 7 | **KBD** | **PASS** | Starting from a fresh load and pressing Tab from the top of the page: at 390 (light and dark) there are 13 focus steps (5 bar links, then Tab into the strip on the selected tab, then 7× ArrowRight); at 1024 there are 8. Every step is `:focus-visible` with a 2px **inset** ring in the `--ring` teal. **0 steps** have a focus box intersecting a fade whose computed opacity is > 0. Every focused item is fully inside the scroll viewport; the row scrolls to keep it clear (bar scrollLeft 0→89→185; strip 0→16→133→235→316→391). The Radix viewport is not a tab stop. ArrowRight never changed the URL. Enter on "Subscription" → `?tab=subscription`, with focus in the panel. |
| 8 | **SEL-COMPAT** | **FAIL** (DOM level only) | Everything at the a11y/role level is identical to before: settings nav by role = 1; tablist "Firm settings" = 1, in main; `role=tab` = 8 (Alice) and 2 (Carol) with the same `aria-selected` values; h1 in `#main-content` = 1 ("General"); `<main id="main-content">` = 1 (1 `<main>` in total); "Search, Command K" exact = 2, of which 1 is in `<header>`; inner Tabs are unchanged. **The DOM-level values differ at 1024**, which is not the expected "<md only" difference (see Defects). |
| 9 | **Console** | **PASS** | 01: 68 page loads. 03: 129 page loads/navigations. 04: 3 keyboard walks. Total: **0 errors, 0 warnings, 0 hydration messages**. |
| 10 | **Dark mode** | **PASS** | Every dark tile was checked visually: family bar and strip at 390, kbd dark, forbidden notice, rail at 1440 and the family sheets. There are no white boxes, and every label, underline (`#ffffff` = foreground) and focus ring is readable. The fades use `from-background` (`lab(2.2…)` ≈ `#07080b` in dark), so no light gradient appears. |

### Defects (FAIL detail)

**#8 SEL-COMPAT, DOM-level difference at ≥ md.** `settings/layout.tsx` always renders `<SettingsFamilyBar>` inside `<div className="mb-4 md:hidden">`. At 1024 (and 1440) that wrapper is `display:none`, but the bar's `<nav aria-label="Settings navigation">` and its five links are still in the DOM. Measured at 1024 light on `/settings/firm`, for both personas:
- `document.querySelectorAll('nav[aria-label="Settings navigation"]').length`: before **1**, after **2** (1 visible).
- `a[aria-current="page"]` inside those navs: before `["Firm"]`, after `["Firm","Firm"]`.
- `anyAriaCurrent` gains a third entry, `a[aria-current=page] "Firm"`.

Expected: identical to before at ≥ md. The same doubling exists in reverse at 390, where the rail's nav is in the DOM but hidden.

Impact: the accessibility tree is unaffected; byRole and ariaSnapshot both give 1, so ADR-394's "one landmark" claim holds. A raw CSS locator such as `page.locator('nav[aria-label="Settings navigation"]')` would now match 2 elements, which is a Playwright strict-mode violation. `grep` of `frontend/e2e` finds no consumer of that selector or of `aria-current` on settings, so nothing breaks today. Crop: `sheet-k02-two-rows-390.png` is not applicable here because the element is invisible. The evidence is `sel-compat.json` and `verify.json` → `landmarks.*.dom` = 2, `domVisible` = 1. This is the orchestrator's call: accept it as by-design (CSS-hidden twin) or ask for conditional rendering.

## Two stacked underlined rows below md

They read as **two clear rows, not a visual defect.** The family row has an icon per link and the tab row has none, and the bar's `mb-4` separates the two `border-b` lines, which sit 16px apart (`sheet-k02-two-rows-390.png`, light and dark at 2×). Design note, not a defect: both rows use the same underline weight and colour, and on overflowing rows each one also shows a persistent 6px scrollbar track and thumb (Radix `type="auto"`) between the underline and its border. At 390 that puts two grey bars and two border lines within ~110px, so the hierarchy between "family" and "tab" rests on the icons alone.

## Findings (not fixed)

1. **K-100 remainder → 665F**: Connections › Integrations still overflows at 1024. Details are in `../found.md`.
2. **Underline-to-border gap varies with overflow.** When a strip overflows, the 2px underline sits **6px above** the row's `border-b` (the `pb-1.5` scrollbar strip). When it fits, the gap is **0px** (`verify.json` `underlineGapToBorderPx` 6 vs 0). Switching families at 1024 therefore moves the underline relative to the border line. GroupedTabBar has the same behaviour by design (642C); recorded only.
3. **Rail active accent changed from teal to `--primary`** (near-black in light, white in dark). Before, the active bar was a teal `border-left #00a38f`. This matches the spec ("bg-primary bar") but is a visible brand-colour change in the rail.
4. **Rail at scrollY 0 extends 24px (light) / 16px (dark) below the fold** (bottom 792 at 768; before 808). It is harmless because the rail content (~450px) never needs to scroll; recorded for completeness.
5. **Automation note, not a product defect:** a keypress sent in the same tick as ArrowRight is delivered to the previous tab, because Radix RovingFocusGroup moves focus in a `setTimeout`. An immediate Enter therefore re-selects the current tab (a no-op). With 300ms between keys, Enter and Space activate reliably (5/5 in a separate repro). Scripted keyboard tests must wait after Arrow keys. Radix behaviour, unchanged by 664A.
6. Re-observed from before: Carol's Fees & Trust family has a single "Rates" tab (K-123, backlog). The header family crumb still truncates at 390 ("Fees & Tr…", "Connectio…"; K-26 area).
7. The Matters › Templates table at 1024 is 607px wide in a 494px panel. It scrolls inside its own `overflow-x-auto` wrapper; the window does not scroll horizontally (scrollX stays 0). Not a defect.
