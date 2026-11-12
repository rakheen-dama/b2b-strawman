# 637B evidence: theme provider, Light/Dark/System toggle, themed toaster

Head under test: **`1b78ae412`** (PR #1760). Stack: fresh mock-auth e2e stack built from this worktree (frontend :3001, backend :8081, mock IDP :8090), user Alice (owner), org `e2e-test-org`. Previous slice: `../637A/README.md`.

## Gates

_(orchestrator fills in)_

## OBS (task 637.12), observed 2026-11-12

### Method: library-script deviation

The Playwright **MCP** browser could not launch (`Browser is already in use for …/mcp-chrome-5d273ba`); another live session holds that profile, so it was left alone. All observations were driven in a real headless Chromium through the Playwright library (`@playwright/test` 1.59.1 from this worktree's `frontend/node_modules`), the same fallback 637A used.

- `obs.cjs` covers observations 1–6. Raw output is in `obs-report.json`. It ran twice. The first run clicked Light first, which is a no-op on a fresh browser (see 1). For the second run, which is the run recorded here, the order was changed to Dark → Light → System, `data-sonner-theme` was added and HTTP ≥400 logging was added. The committed file is byte-for-byte the second-run script.
- `fouc-diag.cjs` is a follow-up to observation 2. It records every composited frame with CDP `Page.startScreencast`, measures each frame's mean luminance in a separate decoder page and records the `<body>` children at the first rAF. Batch 1 (`fouc-diag-report-batch1.json`, 5 runs) used an earlier revision of the file, with a hard-coded run list and the theme not pre-stored before the outgoing page loaded. Batch 2 (`fouc-diag-report-batch2.json`, 13 runs, `b2-` prefix) used the committed revision. The first 3 frames of every run are in `fouc-frames/`.
- Page readiness: each navigation waits for `load` and then the page `h1`. `networkidle` is never used.
- No app source was modified. No business data was created, changed or deleted. The only "action" was the client-only **Export Register** button, which fires `toast.info` and nothing else (`components/data-protection/processing-register-table.tsx:320-322`).

### 1. Persistence: **PASS**

Each choice: open the user menu → click the radio → read the state → `reload` → read the state → reopen the menu → read the radios. Context `colorScheme: light` unless stated.

| Choice | After click: `kazi-theme` / html class / menu still open / checked | After reload: `kazi-theme` / html class / checked (`aria-checked`, `data-state`) | body bg after reload |
|---|---|---|---|
| Dark | `dark` / `dark` / **open** / Dark | `dark` / `dark` / Dark (`true`, `on`); Light and System `false`/`off` | `lab(2.214 …)` |
| Light | `light` / `light` / **open** / Light | `light` / `light` / Light (`true`, `on`) | `lab(96.74 …)` |
| System, OS light | `system` / `light` / **open** / System | `system` / `light` / System (`true`, `on`) | `lab(96.74 …)` |
| System, `emulateMedia dark` (no reload) | `system` / **`dark`** / open / System | | `lab(2.214 …)` |
| System, `emulateMedia light` (no reload) | `system` / **`light`** / open / System | | `lab(96.74 …)` |
| System, OS dark, then reload | | `system` / **`dark`** / System (`true`, `on`) | `lab(2.214 …)` |

- The popover stayed open after every toggle click (`menuOpen: true` in all three `afterClick` states).
- `<html style="color-scheme">` follows the theme (`dark` / `light`).
- Initial state on a fresh browser: `kazi-theme` = `null`, html class `light`. Clicking **Light** while it is already checked does not write storage: Radix emits `""` and `theme-toggle.tsx` ignores it on purpose (seen in the first run: `ls=null` after clicking Light). This is harmless because the default is light.

### 2. No FOUC on a throttled reload

Settings: CDP `Emulation.setCPUThrottlingRate {rate:4}`, `Network.emulateNetworkConditions {latency:562.5, downloadThroughput:180000, uploadThroughput:84375}`, `addInitScript` sampler (readyState changes, DOMContentLoaded/load, the first 40 rAFs, a MutationObserver on html `class`, and paint entries).

| Variant | Stored | Result | Key samples |
|---|---|---|---|
| Reload, normal HTTP cache (as specified) | `dark` | **PASS** | html class mutated to `dark` at 599 ms, before the first rAF (603 ms, `dark`). first-paint @616 ms `dark`. 0 light samples. |
| Reload, normal HTTP cache (as specified) | `light` | **PASS** | first rAF 601 ms `light`. first-paint @616 ms. `dark` never present. |
| Reload, **cache disabled** (`Network.setCacheDisabled`) | `dark` | **FAIL (intermittent)** | see below |
| Reload, cache disabled | `light` | **PASS** | `dark` never present in 4/4 runs (obs + batch 2 ×3). |

**Cold-cache dark FOUC: reproduced, real.** Under cold cache the first rAF sometimes runs **before** the next-themes script has executed:

- `obs.cjs`: first rAF at 1904.5 ms has `class=""`, `bodyChildren: 2`. The class mutates to `dark` at 1911.5 ms.
- Frame-level proof (`fouc-diag.cjs`): the outgoing page was already dark (mean luminance about 20), so any bright frame after the reload can only come from the new document. A whole-viewport **light frame** (mean luminance 246, 100 % light pixels, i.e. the empty light `--background` canvas) was presented before the dark frames in **3 of 10** cold-cache dark runs (batch 2, runs 3, 5 and 8). The next frame was dark 6–63 ms later. Example: `fouc-frames/b2-dark-nocache-run3-frame3.png`. Batch 1 runs 1–3: 0 of 3. Warm reload: 0 of 3 in total (obs ×2 + batch 1 run 5).
- In every light-frame run the rAF sample shows `body = ["div", "script[inline:((a,b,c,d,e,f,g,h)=>{let i=document.documentElement,j=["ligh…]"]`, `class=""`, body bg `lab(96.74 …)`. The next-themes inline script is already in the DOM but has not executed: the streamed HTML chunk ended inside it, the parser yielded, and Chrome rendered a frame of the hidden metadata `<div>` on the light `:root` canvas.
- Code: `frontend/components/theme/theme-provider.tsx:21-22` says "next-themes' blocking script writes the class … so the first paint is already in the right theme". Under cold cache and Slow 4G that is not always true. The script is emitted inside `<body>` (`frontend/app/layout.tsx:73-76` → `theme-provider.tsx:27`) after Next's hidden metadata `<div>`, so a frame can land between them. Which fix to apply is an orchestrator call. It is reported here, not fixed.

Early screenshots during the throttled loads: `fouc-{dark,light}{,-nocache}-early-{1-commit,2-dcl,3-load}.png`. Playwright's screenshot after `commit` only returns once the page is already rendering, so these did not catch the light frame. The screencast frames did.

### 3. Console: **PASS** (0 hydration warnings)

`/org/e2e-test-org/dashboard` was loaded fresh in light and in dark, the user menu opened, and the page then held for 1 s. Result: **0 errors, 0 warnings, 0 pageerrors, 0 hydration messages** in both themes.

All console output across the whole `obs.cjs` run: **1 error, 0 warnings, 0 pageerrors, 0 hydration**. Verbatim:

```
[error] http://localhost:3001/ :: Failed to load resource: the server responded with a status of 404 (Not Found)
```

Its HTTP ≥400 counterpart is `404 http://localhost:3001/sign-up?_rsc=5CB68i4pnAekjehf` (page `/`). This is Next's prefetch of the marketing "Get Started" link: `components/marketing/{nav-bar,hero-section,cta-section}.tsx` use `/sign-up` in mock auth mode, and no `/sign-up` route exists in `app/`. This PR changes no marketing file, so the 404 is not caused by it.

### 4. Forced light on non-`/org/` routes: **PASS**

`kazi-theme=dark` was stored on `/org/e2e-test-org/dashboard`, which rendered dark (`dark`, body `lab(2.214 …)`) before the visits below.

| Requested | Final URL | html class | `color-scheme` | body bg | Screenshot |
|---|---|---|---|---|---|
| `/` | `/` | `light` | light | `lab(96.74 …)` | `forced-root.png` |
| `/dashboard` | redirects to `/org/e2e-test-org/dashboard` (themed) | `dark` (correct: themed route) | dark | `lab(2.214 …)` | `forced-dashboard.png` |
| `/mock-login` | `/mock-login` | `light` | light | `lab(96.74 …)` | `forced-mock-login.png` |
| Client-side: dark `/org/…/dashboard` → sidebar link `/platform-admin/access-requests` (no full load, `window` marker survived) | `/platform-admin/access-requests` | `light` | light | `lab(96.74 …)` | `forced-clientnav.png` |
| Browser Back (client-side) | `/org/e2e-test-org/dashboard` | `dark` (restored) | dark | `lab(2.214 …)` | — |

`kazi-theme` stayed `dark` throughout, so forcing does not overwrite the stored choice. The marketing landing `/` looks dark in `forced-root.png` because its sections hard-code `bg-slate-950` (`components/marketing/hero-section.tsx:11`, `nav-bar.tsx:12`). That is its own design, not the theme, and the html class is `light`.

### 5. Toasts follow the theme: **PASS**

Trigger: **Export Register** on `/org/e2e-test-org/settings/firm?tab=data-protection` → `toast.info("Export register is coming soon.")`, which is client-only. Sonner 2.0.7 writes the theme to **`data-sonner-theme`** on `[data-sonner-toaster]`. `data-theme` is not set at all (it reads `null` in every run).

| Stored / OS | html class | `[data-sonner-toaster]` `data-sonner-theme` | toast bg | Screenshot |
|---|---|---|---|---|
| `dark` / light | `dark` | **`dark`** | `rgb(0, 0, 0)` | `toast-dark.png` |
| `light` / light | `light` | **`light`** | `rgb(255, 255, 255)` | `toast-light.png` |
| `system` / dark | `dark` | **`dark`** | `rgb(0, 0, 0)` | `toast-system-osdark.png` |

**Not observed in the browser:** a toast on a forced-light route while `dark` is stored (the `forcedTheme ?? resolvedTheme` fix in `1b78ae412`). No harmless toast trigger exists outside `/org/`. The substitute is the vitest coverage in `__tests__/components/theme/themed-toaster.test.tsx`.

### 6. Dark "before" record for 640A

| Surface | Light | Dark |
|---|---|---|
| Home 1024×768 | `home-1024-light.png` | `home-1024-dark.png` |
| Home 1440×900 | `home-1440-light.png` | `home-1440-dark.png` |
| User menu open, 1440 | `user-menu-light.png` | `user-menu-dark.png` |

#### Recorded, not fixed (dark mode; owners are the later slices)

- **Sidebar has no separation from the canvas in dark.** `desktop-sidebar.tsx` hard-codes `bg-slate-950`, which is almost the same as the dark `--background`. Owner: **638A** (`--sidebar-*` tokens).
- **Debtors aging bars (Home)**: in dark every bar is a dull brick orange (`--aging-current` on this data). Legibility is fine, but the ramp has not been tuned for dark. Owner: **640A**.
- **Low-contrast muted text in dark cards**: card action links ("Open diary", "Open unbilled work") and the Trust-position label column (`Bank cashbook`, `Client ledgers`). Owner: **640A**.
- **Settings › Data protection in dark** (seen in `toast-dark.png`, outside the Phase 90 surfaces): native `<select>` controls on black, the disabled "Generate PAIA Manual" button is a light-grey block, and the white primary "Add Activity" button stands out against the canvas. Owner: not in the Phase 90 surface list. Recorded for the backlog.

## Screenshot / artefact index

| File | What |
|---|---|
| `obs.cjs`, `obs-report.json` | Observations 1–6, script and raw output |
| `fouc-diag.cjs`, `fouc-diag-report-batch{1,2}.json`, `fouc-frames/*.png` | Observation 2, frame-level FOUC diagnostic |
| `fouc-*-early-*.png` | Early screenshots during the throttled reloads |
| `forced-{root,dashboard,mock-login,clientnav}.png` | Observation 4 |
| `toast-{dark,light,system-osdark}.png` | Observation 5 |
| `home-{1024,1440}-{light,dark}.png`, `user-menu-{light,dark}.png` | Observation 6 |
| `e2e/` | Suite output (orchestrator) |
