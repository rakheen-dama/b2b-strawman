# 637B evidence: theme provider, Light/Dark/System toggle, themed toaster

## Gates

Final head under test: **`4816cf440`**. Orchestrator runs:

| Gate | Head | Result |
|---|---|---|
| FE-GATE (`rm -rf .next && pnpm lint && pnpm typecheck && pnpm format:check && pnpm test && pnpm build`) | `4816cf440` | exit 0 for every step. **460 files, 3903 passed / 2 skipped** (637A: 459 files, 3879 / 2. Change: −25 dead-code cases, +49 new theme and menu cases) |
| FE-GATE | `1b78ae412` | exit 0. 459 files, 3874 passed / 2 skipped |
| E2E-LEGAL, fresh stack (`e2e-down` → `docker builder prune -f` → `e2e-up`) | `4816cf440` | `e2e/summary.txt`: e2e-up exit 0. legal-lifecycle **105 passed**, legal-depth-ii **40 passed / 4 skipped**. Both equal the 637.1 baseline |
| E2E-LEGAL, fresh stack | `1b78ae412` | `e2e/run1-1b78ae412/summary.txt`: 105 passed; 40 passed / 4 skipped |

No spec was edited and no role or test id was changed. PORTAL-GATE was not required: only the root `pnpm-lock.yaml` changed, and nothing under `portal/` or `packages/`. The Portal CI job runs on the lockfile path filter.

---

Head under test: **`4816cf440`** (PR #1760). Stack: a fresh mock-auth e2e stack built from this worktree at `4816cf440` (frontend :3001, backend :8081, mock IDP :8090), user Alice (owner), org `e2e-test-org`. The stack was not stopped, rebuilt or reseeded during OBS. Previous slice: `../637A/README.md`. The first OBS run, at `1b78ae412`, is kept unchanged in `run1-1b78ae412/` (its own `README.md`, reports, scripts as run, screenshots, 54 FOUC frames).

## OBS run 2 (task 637.12), observed 2026-11-12

### Method: library-script deviation

The Playwright **MCP** browser profile is held by the user's live session, so it was not used and that Chrome was not touched. Every observation was driven in a real headless Chromium through the Playwright library (`@playwright/test` from this worktree's `frontend/node_modules`), the same fallback 637A and run 1 used.

| Script | Purpose | Output | Change vs run 1 |
|---|---|---|---|
| `head-check.cjs` | Logs in, fetches the raw server HTML of `/org/e2e-test-org/dashboard` and `/` with the session cookies, and locates the theme init script relative to `<head>`/`<body>` | `head-check-report.json` | new |
| `fouc-diag.cjs` | Frame-level FOUC check: CDP `Page.startScreencast` records every composited frame, a separate decoder page measures each frame's mean luminance, and an init script samples the html class at every rAF, paint entry and class mutation | `fouc-diag-report.json`, `fouc-frames/` | **byte-identical** to `run1-1b78ae412/fouc-diag.cjs` (`cmp` clean). The run list, frame prefix and report name were set through its existing `DIAG_RUNS` / `DIAG_PREFIX` / `DIAG_REPORT` env vars. The three part-reports were merged into `fouc-diag-report.json` with a computed summary, and the part files were then deleted. |
| `obs.cjs` | Observations 1–6 | `obs-report.json`, top-level PNGs | `head` label → `4816cf440`. Added `SCRIPT_TAG_RE = /Encountered a script tag/i`, counted per theme in `obs3[t].scriptTag` and overall in `consoleSummary.scriptTag`. Nothing else changed. |

- Throttling, the same as run 1: CDP `Emulation.setCPUThrottlingRate {rate:4}` and `Network.emulateNetworkConditions {latency:562.5, downloadThroughput:180000, uploadThroughput:84375}` (Slow 4G). Cold cache = `Network.setCacheDisabled {true}`.
- Page readiness: each navigation waits for `load` and then the page `h1`. `networkidle` is never used.
- No app source was modified. No business data was created, changed or deleted. The only action was the client-only **Export Register** button (`toast.info`, `components/data-protection/processing-register-table.tsx:320-322`).

### Head script is live (raw server HTML)

`head-check-report.json`. On both routes there is exactly **1** occurrence, and `insideHead: true`.

| Route | HTTP | `<head` at | init `<script>` at | `</head>` at | `<body` at |
|---|---|---|---|---|---|
| `/org/e2e-test-org/dashboard` | 200 | 31 | 2093 | 2571 | 2578 |
| `/` | 200 | 31 | 2380 | 2858 | 2865 |

The script is the last element in `<head>`, after the stylesheet `<link>`s, the async chunk scripts, `<title>` and the meta and icon tags, and directly before `</head><body …><div hidden="">`. Excerpt from the dashboard HTML (the same on `/`):

```html
<link rel="icon" href="/favicon.ico?favicon.2vob68tjqpejf.ico" sizes="256x256" type="image/x-icon"/><meta name="next-size-adjust" content=""/>
<script>(function(){try{var d=document.documentElement,t="light";if(location.pathname.indexOf("/org/")===0){var s=null;try{s=localStorage.getItem("kazi-theme")}catch(e){}if(s==="system"){s=window.matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"}if(s==="dark"||s==="light"){t=s}}d.classList.remove(t==="dark"?"light":"dark");d.classList.add(t);d.style.colorScheme=t}catch(e){}})()</script>
</head><body class="sora_… plexsans_… jetbrainsmono_… antialiased"><div hidden=""><!--$--><!--/$--></div><scrip…
```

## Run 1 (1b78ae412) defect → fix → Run 2 (4816cf440)

**Defect (run 1).** With `kazi-theme=dark` stored, the cache disabled, and CPU 4× plus Slow 4G, a reload of `/org/e2e-test-org/dashboard` presented a whole-viewport **light frame** (mean luminance 246, 100 % light pixels) before the dark frames in **3 of 10** runs (batch 2, runs 3, 5 and 8; for example `run1-1b78ae412/fouc-frames/b2-dark-nocache-run3-frame3.png`). In those runs the first rAF sample had `class=""`, with body `["div", "script[inline:((a,b,c,d,e,f,g,h)=>…]"]`. The next-themes script was in `<body>` and had been parsed but not yet executed when Chrome rendered a frame.

**Fix (4816cf440).** `THEME_INIT_SCRIPT` (`frontend/components/theme/theme-config.ts`) is rendered as a parser-blocking inline `<script>` in the root layout `<head>` (`frontend/app/layout.tsx`).

**Run 2 result.** Pass criterion: for stored dark, no screencast frame with mean luminance > 128 and no rAF, paint or mutation sample with `dark=false`; for stored light, the reverse. The `init-script` sample is excluded because it runs before any document script.

| Variant (CPU 4× + Slow 4G) | Run 1 (1b78ae412) | Run 2 (4816cf440) | Run 2: html class first set (ms) | Run 2: first rAF (ms) | Run 2: first-paint (ms) | Run 2: frame luminance range |
|---|---|---|---|---|---|---|
| Stored **dark**, cache disabled | **3 / 10 light frame** (batch 2); 0 / 3 (batch 1) | **0 / 20** wrong frames, **0 / 20** wrong samples | 1911.1 – 2137.1, `body` = `null` in **20 / 20** | 1921.4 – 2147.7, `dark` in 20 / 20 | 1952 – 2176, `dark` in 20 / 20 | 9 – 20 (never > 20) |
| Stored **light**, cache disabled | 0 / 4 dark | **0 / 10** wrong frames, **0 / 10** wrong samples | 1920.7 – 2126.4, `body` = `null` in **10 / 10** | 1931.5 – 2137.7, `light` in 10 / 10 | 1964 – 2164, `light` in 10 / 10 | 207 – 208 |
| Stored **dark**, normal cache | 0 / 3 | **0 / 5** wrong frames, **0 / 5** wrong samples | 595.8 – 641.6, `body` = `null` in **5 / 5** | 598.5 – 644.6, `dark` in 5 / 5 | 612 – 656, `dark` in 5 / 5 | 9 – 20 |

- The first class mutation in every run (35 / 35) was recorded while `document.body` was still `null`. The class is set in `<head>`, before any body content exists to paint. In run 1 the class was first set after the body already held the hidden `<div>` and the next-themes `<script>`.
- The first new-document frame on a cold cache is the app shell already in the stored theme: dark sidebar and header with an empty dark canvas (`fouc-frames/r2-dark20-dark-nocache-run1-frame3.png`, mean luminance 9, at 2003 ms) or the light equivalent (`r2-light10-light-nocache-run1-frame3.png`, 207, at 2133 ms). The empty light `--background` frame from run 1 never appeared.
- `obs.cjs` observation 2 agrees independently. Each of its 4 throttled reloads passed with 0 mismatches: cold dark first rAF at 1920.8 ms `dark`, cold light at 1919.4 ms `light`, warm dark at 600.3 ms `dark`, warm light at 601.6 ms `light`.

## Per-observation results (run 2, `obs-report.json`, 02:13:38Z → 02:15:08Z)

| # | Observation | Result | Raw values |
|---|---|---|---|
| 1a | Persistence: **Dark** | **PASS** | After click: `kazi-theme=dark`, class `dark`, menu open, Dark checked. After reload: `dark` / `dark`. Radios Light `false/off`, Dark `true/on`, System `false/off`. `color-scheme: dark`. Body `lab(2.214 …)` |
| 1b | Persistence: **Light** | **PASS** | After click: `light` / `light` / open / Light. After reload: `light` / `light`. Light `true/on`. `color-scheme: light`. Body `lab(96.74 …)` |
| 1c | Persistence: **System** + `emulateMedia` | **PASS** | OS light: `system` / `light` / open / System. After reload: `system` / `light` / System `true/on`. `emulateMedia dark` (no reload): `system` / **`dark`**, body `lab(2.214 …)`. `emulateMedia light`: `light`. OS dark then reload: `system` / **`dark`** / System checked. OS light (no reload): `light` |
| 1d | Popover stays open after each toggle | **PASS** | `popoverStayedOpen: true` for Dark, Light and System |
| 2 | No FOUC on a throttled reload | **PASS** | See the table above: 0 / 35 runs in `fouc-diag.cjs` and 4 / 4 `obs.cjs` reloads with 0 mismatches |
| 3 | Console on the dashboard, light and dark, menu open | **PASS** | light: 0 messages, 0 hydration, 0 "Encountered a script tag". dark: 0 / 0 / 0. Whole run: `{"total":1,"errors":1,"warnings":0,"pageerrors":0,"hydration":0,"scriptTag":0}` (the 1 error is listed below) |
| 4a | Forced light `/` with dark stored | **PASS** | class `light`, `color-scheme: light`, body `lab(96.74 …)`, `kazi-theme` still `dark` (`forced-root.png`) |
| 4b | Forced light `/mock-login` with dark stored | **PASS** | class `light`, `color-scheme: light`, `lab(96.74 …)`, `kazi-theme=dark` (`forced-mock-login.png`) |
| 4c | Client-side nav, dark `/org/…/dashboard` → `/platform-admin/access-requests` | **PASS** | Baseline `dark`. After nav (SPA marker survived: `true`): class `light`, `color-scheme: light`, `lab(96.74 …)` (`forced-clientnav.png`). Back (SPA `true`): `dark` restored |
| 4d | `/dashboard` (control) | n/a | Redirects to `/org/e2e-test-org/dashboard`, a themed route, so `dark` is correct (`forced-dashboard.png`) |
| 5a | Toast, stored dark (OS light) | **PASS** | html `dark`, `data-sonner-theme="dark"`, toast bg `rgb(0, 0, 0)`, "Export register is coming soon." (`toast-dark.png`) |
| 5b | Toast, stored light | **PASS** | html `light`, `data-sonner-theme="light"`, bg `rgb(255, 255, 255)` (`toast-light.png`) |
| 5c | Toast, stored system (OS dark) | **PASS** | html `dark`, `data-sonner-theme="dark"`, bg `rgb(0, 0, 0)` (`toast-system-osdark.png`) |
| 5d | Toast on a forced-light route with dark stored | **NOT OBSERVED** | No harmless toast trigger exists outside `/org/`. Covered by vitest `frontend/__tests__/components/theme/themed-toaster.test.tsx` (the `forcedTheme ?? resolvedTheme` fix in `1b78ae412`) |
| 6 | Home dark "before" record | **PASS (recorded)** | `home-{1024,1440}-{light,dark}.png` and `user-menu-{light,dark}.png`. States: `home-*-light` `light`; `home-*-dark` `dark`; `user-menu-*` menu open in the matching theme |

`data-theme` on `[data-sonner-toaster]` is `null` in every run. Sonner 2.0.7 uses `data-sonner-theme`.

### Console errors and warnings, verbatim (whole `obs.cjs` run)

```
[error] (phase obs4, page http://localhost:3001/) Failed to load resource: the server responded with a status of 404 (Not Found)
```

Its HTTP ≥ 400 counterpart is `404 http://localhost:3001/sign-up?_rsc=5CB68i4pnAekjehf` (page `/`). This is Next's prefetch of the marketing "Get Started" link to `/sign-up`, a route that does not exist in mock-auth mode. It matches run 1 exactly and comes from marketing components this PR does not change. There were **0 warnings**, **0 pageerrors**, **0 hydration messages** and **0 "Encountered a script tag"** messages. `head-check.cjs` and `fouc-diag.cjs` do not capture console output.

## Screenshot / artefact index

| File | What |
|---|---|
| `head-check.cjs`, `head-check-report.json` | Head-script placement in raw server HTML |
| `fouc-diag.cjs`, `fouc-diag-report.json` | FOUC run 2: 35 runs, a computed summary and per-run first class mutation, first rAF, first-paint and all frame luminances |
| `fouc-frames/` (**6 kept** of 105 captured) | For each variant, run 1 frame 2 (the last outgoing-document frame) and frame 3 (the first new-document frame), i.e. the reload transition: `r2-dark20-dark-nocache-run1-frame{2,3}.png`, `r2-light10-light-nocache-run1-frame{2,3}.png`, `r2-warm5-dark-warm-run1-frame{2,3}.png`. No failure frames existed. The other 99 were deleted, and their luminances remain in the report. |
| `obs.cjs`, `obs-report.json` | Observations 1–6, script and raw output |
| `fouc-{dark,light}{,-nocache}-early-{1-commit,2-dcl,3-load}.png` | `obs.cjs` observation 2: screenshots during the throttled reloads (all in the stored theme) |
| `forced-{root,dashboard,mock-login,clientnav}.png` | Observation 4 |
| `toast-{dark,light,system-osdark}.png` | Observation 5 |
| `home-{1024,1440}-{light,dark}.png`, `user-menu-{light,dark}.png` | Observation 6, dark "before" record for 640A |
| `run1-1b78ae412/` | Run 1 artefacts, unchanged, plus copies of `obs.cjs` and `fouc-diag.cjs` as they were at run 1 |
| `e2e/` | Suite output (orchestrator) |

## Recorded, not fixed (dark mode; owners are later slices)

These were re-checked on the run-2 screenshots and are unchanged from run 1. The fix touched only the theme bootstrap.

- **The sidebar has no separation from the canvas in dark.** `desktop-sidebar.tsx` hard-codes `bg-slate-950`, which is almost the same as the dark `--background` (`home-1440-dark.png`). Owner: **638A** (`--sidebar-*` tokens).
- **Debtors aging bars (Home)**: in dark every bar is a dull brick orange (`--aging-current` on this data). Legibility is fine, but the ramp has not been tuned for dark. Owner: **640A**.
- **Low-contrast muted text in dark cards**: the card action links ("Open diary", "Open unbilled work", "Open collections", "Open reconciliation") and the Trust-position label column ("Bank cashbook", "Client ledgers", "Last reconciliation", "Awaiting approval"). Owner: **640A**.
- **Settings › Data protection in dark** (`toast-dark.png`, outside the Phase 90 surfaces): native `<select>` controls on black, the disabled "Generate PAIA Manual" button is a light-grey block, and the white primary "Add Activity" button stands out against the canvas. Owner: not in the Phase 90 surface list. Recorded for the backlog.
- Not a dark-mode defect, for completeness: the marketing `/` looks dark in `forced-root.png` because its sections hard-code `bg-slate-950` (`components/marketing/hero-section.tsx`, `nav-bar.tsx`). The html class there is `light`, as required.
