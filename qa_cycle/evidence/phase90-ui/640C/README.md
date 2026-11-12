# 640C OBS (task 640.14): My Work weekly summary + Home loading skeleton

- Stack: mock-auth e2e (:3001 / :8081), built from worktree-epic-640C @ `a9e613443`, seeded by legal-depth-ii. Not rebuilt or reseeded.
- Driver: Playwright library (`chromium.launch()`, headless), not MCP. Scripts as run: `obs-640c.cjs` (observation) and `seed-ui.cjs` (seeding through the UI only).
- Throttle (Home): CDP `Network.emulateNetworkConditions` latency 2000 ms, 50 KB/s down and up, cache disabled, plus `Emulation.setCPUThrottlingRate` ×4.
- Sampling: in-page `requestAnimationFrame` sampler (≥ 40 ms apart, plus every state change) installed by init script, and Playwright polling every 50 ms (used for the skeleton screenshot).
- CLS: `PerformanceObserver({type:"layout-shift", buffered:true})`.
- Theme: `kazi-theme` localStorage; `<html>` class verified per run.

## Seeding (UI only: matter → Work → Tasks → "Log Time" dialog)

| When | User | Entry | Why |
|---|---|---|---|
| 13:23Z | Alice | 1h 30m **non-billable**, 2026-11-12, on Khumalo Property Holdings (Billable checkbox unticked) | Alice's week had 2h billable / 0m non-billable. This gives the meter two segments. Dialog: `seed-alice-2026-11-12-nonbillable-dialog.png` |
| 13:24Z | Alice | 2h billable, **2026-10-01** | Previous 30-day window (Sep 14–Oct 13) had no hours, so there were no KPI delta rows. This entry gives the steady-state firm anatomy. Dialog: `seed-alice-2026-10-01-billable-dialog.png` |
| 13:23Z | Carol | attempted, **not done** | Carol is on no matters ("You're not on any matters yet"). Her weekly card was observed in its genuinely empty state only. |

Log: `seed-ui.log`. The 3rd seed run logged one console error, `MockAuthContextProvider: failed to fetch userinfo … Failed to fetch`. It came from the seed script's own `page.goto` aborting an in-flight fetch, not from an OBS load.

KPI cache: the backend org cache has a 3-minute TTL (`DashboardService`). Home state **B** was therefore measured more than 3 minutes after the 13:24Z seed. A first B attempt inside the TTL still showed state A and was discarded.

## 1. My Work, "This Week" card (`mywork-B.json`, `mywork-B-*`)

| User × width × theme | Result | Meter name / sr summary | Segment fill = token | Text contrast (min) | White surfaces | Empty week |
|---|---|---|---|---|---|---|
| Alice 1024 light | PASS | "Billable vs non-billable time" / "Billable 2h, Non-billable 1h 30m" | billable `#009787` = `--chart-1`, non-billable `#e06623` = `--chart-2` | 7.18 | card itself only (light, expected) | Nov 2–8: "No time tracked this week", no meter |
| Alice 1024 dark | PASS | same | `#00aa99` = `--chart-1`, `#df6c32` = `--chart-2` | 6.27 | 0 | same |
| Alice 1440 light | PASS | same | same as light | 7.18 | card only | same |
| Alice 1440 dark | PASS | same | same as dark | 6.27 | 0 | same |
| Carol 1024/1440 × light/dark | PASS (empty state) | no meter (`role=img` count 0) | n/a | 7.57 light / 6.74 dark | 0 dark | current and previous week both "No time tracked this week" |

What the card showed, as seen and read from the DOM:

- **Visible totals:** Total 3h 30m; Billable 2h; Non-billable 1h 30m.
- **sr summary:** it is sr-only and matches the visible figures exactly (`formatDuration`).
- **Segment widths:** 625.1 / 468.9 px of 1094 @1440 (= 2 : 1.5), and 387.4 / 290.6 px of 678 @1024. There is a 2px surface gap line between the segments, and each `<title>` reads "Billable: 2h" / "Non-billable: 1h 30m".
- **Swatches:** the swatches before "Billable" / "Non-billable" and the per-matter billable swatches compute to the same colour as their segments in both themes.
- **Colour use:** no status green remains.
- **a11y tree:** `mywork-B-<user>-<w>-<theme>-aria.txt`, e.g. `img "Billable vs non-billable time"` followed by text "Billable 2h, Non-billable 1h 30m".
- **Screenshots:** `mywork-B-alice-{1024,1440}-{light,dark}-week.png`, `…-empty-week.png`, and full page `…-page.png`.

## 2. Home loading state on a throttled navigation (`home-A.json`, `home-B.json`)

**StatCard loading state was observed (OQ #27):** yes, on every run.

- In every skeleton sample there were four `[data-slot="stat-card"]` elements with `role="group"`, `aria-busy="true"` and `data-state="loading"`.
- On the skeleton root, `aria-busy="true"` was set and there were zero `[data-testid^="home-"]` and no `data-audience`.
- Every loading value block was 32px high, equal to the ready value line. That confirms the R1 `stat-card.tsx` fix.
- Screenshots: `home-{A,B}-alice-{1024,1440}-{light,dark}-client-skeleton.png`.
- Timing on client-side navigation (sidebar "Home" link from My Work): the skeleton painted about 110–165 ms after the click (the loading boundary was prefetched) and stayed up for about 4.4 s.
- Timing on hard reload: the streamed skeleton was up for only one frame (about 40 ms) before the content chunk replaced it. It was observed in the DOM but too brief to screenshot.

**CLS was 0.0000 on all 19 throttled runs**, both including and excluding recent input. There were no `layout-shift` entries at all. The CLS API cannot see a fallback-to-content swap, because Suspense *replaces* the skeleton nodes rather than moving them. The px deltas below are therefore the real measure of visual jump.

Deltas are loaded minus skeleton, in px, measured on the transform-free layout. The template.tsx `slide-in-from-bottom-[2px]` 100 ms entrance animation shifts the first frame's absolute y by 2px; this is a transform, not layout. Results were identical in light and dark and for client and reload.

| State | Width | Header h1 | KPI cell (skel → loaded h) | Diary y | Row 3 top | Row 3 h | Trust card y (in row 3) | Rows 4/5 y | Verdict |
|---|---|---|---|---|---|---|---|---|---|
| **A** (as found: no previous-period hours) | 1440 | 0 | 196 → 174 (**−22**) | 388 → 366 (**−22**) | −22 | +62.5 | 784 → 824.5 | +40.5 / +125.5 | matches the documented expectation (−22 @1440) |
| **A** | 1024 | 0 | 332 → 288 (**−44**) | 524 → 480 (**−44**) | −44 | +62.5 | 920 → 938.5 | +18.5 / +103.5 | matches the documented expectation (−44 @1024) |
| **B** (steady state, after the UI seed) | 1440 | 0 | 196 → 196 (**0**) | 388 → 388 (**0**) | **0** | +62.5 | 784 → 846.5 (+62.5) | +62.5 / +147.5 | header, KPI row and diary match exactly |
| **B** | 1024 | 0 | 332 → 332 (**0**) | 524 → 524 (**0**) | **0** | +62.5 | 920 → 982.5 | +62.5 / +147.5 | header, KPI row and diary match exactly |

- **KPI tiles.** In the skeleton: 172/172/172/172 @1440 and 172/172/120/120 @1024 (2×2).
  - Loaded state A: 150 / 98, with no delta rows.
  - Loaded state B: 172 / 120. In B, Hours logged shows "+75,0 % vs previous 30 days" and Billable % shows "−42,9 pp".
- **Row 3 (WIP | Debtors | Trust).** The skeleton sizes WIP and Debtors to the empty-tenant height of 250. On this seeded tenant they load at 312.5 (data-dependent), so the documented "exactness below the diary is not achievable without data" applies.
  - **@1440×900 this is partly in-viewport:** the Trust position card's top edge, visible at y 784–900, moves down 62.5px when content arrives. Compare `home-B-alice-1440-light-client-skeleton.png` with `…-loaded.png`.
  - @1024×768 row 3 starts at 654, and all growth falls below the fold.
- **`[data-slot="kpi-row"]`.** It exists only on the loaded row; `KpiRowSkeleton` does not set it. I measured the skeleton's `@container` frame instead, at the same position and 1136/720 wide.

## 3. Carol (member, fee-earner audience), 1440 light (`home-C.json`)

- Skeleton is the owner layout: KPI cell 196 at y 168, diary at y 388.
- Loaded (`data-audience="feeEarner"`) has no KPI row: diary at y 168, then tasks + WIP at 298, then matter health + activity at 572.
- **The diary moves up 220px** (= skeleton KPI cell 196 + 24 gap). The brief's "~198px @1440" assumed the 174px young-tenant row; with the steady-state skeleton (196) it is 220.
- This is the accepted R4 tradeoff. Reload behaves the same.
- Screenshots: `home-C-carol-1440-light-client-{skeleton,loaded}.png`.

## 5. Bob (admin, firm audience), 1440 light, client navigation (`home-D.json`)

- Header, KPI row (196 → 196) and diary (388 → 388) match.
- Row 3 is +62.5 (data), as for Alice.
- Row 4 is the firm's `[ficaOutstanding, sensitive]` (244 tall vs the owner skeleton's 583), so row 5 moves −276.5. That is below the fold, and expected.

## 4. Console (`console.json`, 27 keys = every OBS load)

| Surface | Loads | errors | warnings | hydration | pageerror |
|---|---|---|---|---|---|
| My Work (Alice + Carol × 2 widths × 2 themes, incl. week navigation) | 8 | 0 | 0 | 0 | 0 |
| Home throttled, Alice A + B (2 widths × 2 themes × client/reload) | 16 | 0 | 0 | 0 | 0 |
| Home throttled, Carol (client + reload) and Bob (client) | 3 | 0 | 0 | 0 | 0 |

The only console message seen anywhere was the seed-script fetch abort noted above, which is not an OBS load.

## Observations (no defects found in 640C scope)

1. The no-CLS goal **holds exactly for header, KPI row and diary** on a steady-state firm. On a young firm with no previous-period hours, the documented −22 / −44 px applies, and it was measured exactly as predicted.
2. Data-dependent growth of the WIP/Debtors cells (+62.5px on this tenant) moves the Trust card's visible top edge at 1440×900. The brief anticipated this ("exactness below the diary is not achievable without data"). It is listed here because it is in-viewport at 1440.
3. Dark mode: the skeleton blocks are low-contrast against the card (`home-*-dark-client-skeleton.png`). They are visible but faint. This is cosmetic, comes from the Skeleton primitive, and was not measured as a defect.
4. Carol's weekly card was observed empty only, because the UI gives no member path to log time without being added to a matter.
