# PR #1753 (G89 Batch F: self-hosted fonts F46, opt-in visual compares, F66 SA-date specs): pre-merge e2e gate

**Verdict: PASS at PR head `b146c09e0`. No e2e regression is attributable to the PR.**

- Every required suite meets its baseline at the PR head. The per-test diff against the prior gate is empty (table below).
- **One failure, class (c), pre-existing on main:** G1 default `invoices/invoice-lifecycle.spec.ts:88` "DRAFT -> APPROVED".
  - The spec's `getByRole('button', { name: 'Approve' })` is a substring match. On an approved fee note it also resolves to the audit-trail row "Fee note approved". This is a load-dependent race.
  - **Main `9c268e376` reproduces it:** 5 of 10 `--repeat-each` iterations fail on the same locator. A deterministic probe gives the identical result on main and on the PR.
  - A full default re-run on a fresh PR-head stack was **165/0/14**, with an empty diff against #1752 and against main.
- **Opt-in visual compares behave as designed:**
  - every flag-off run had 0 "writing actual", 0 snapshot mentions and 0 baseline files written;
  - curated documentation screenshots were still written (lifecycle 16, depth-ii 10);
  - `E2E_VISUAL=1` still runs the compare path ("A snapshot doesn't exist … writing actual", then a passing compare).
- **G89-F46 (self-hosted fonts) is confirmed on both production builds:**
  - Neither build log has a Google Fonts fetch, and neither build failed. No build failed anywhere in the gate (4 stacks, plus a standalone portal build).
  - The served `@font-face` CSS points to `/_next/static/media/*.woff2`. All 6 files return 200 `font/woff2`, with byte sizes equal to the committed files.
  - Screenshots show the same typography as the prior gate's Google-Fonts frames.
- Lifecycle is **105/0/0** with compares off. Its prior *scored* 72/5/28 was G89-F67, and that is now gone.

## Source and recipe

- **Worktree:** detached `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp6` at `b146c09e0b5a8b14bf393f65534ec6d09fa0a207`, created fresh. `pwd` was confirmed and the tree was clean (dirty=0) at start.
  - The merge base is main `9c268e376`. Main has since gained only the docs commits `9174142f2` and `9c84a3743`.
- **What changed vs main (runtime-relevant):**
  - `frontend/app/layout.tsx` and `portal/app/layout.tsx`: `next/font/google` → `next/font/local`, with woff2 files under `{frontend,portal}/app/fonts/`;
  - `frontend/e2e/helpers/screenshot.ts`: compares only with `E2E_VISUAL=1`;
  - `portal/playwright.portal.config.ts`: `ignoreSnapshots` unless `E2E_VISUAL=1`;
  - `.dockerignore`: adds `frontend/e2e/screenshots` and `portal/e2e/screenshots`;
  - three `projects/*.spec.ts` now use `todayInSA()`;
  - `backend/pom.xml`: a Surefire system property only. The backend image still rebuilt because the pom changed;
  - one deleted uncalled backend method.
  - **No Flyway migrations, and no frontend or portal production code beyond the two layouts.**
- **Recipe:** the #1752 recipe without capture-then-score. Every scored run was a single run, with `E2E_VISUAL` unset (logged `E2E_VISUAL=unset`).
  - Every stack was fresh: `e2e-down.sh && SHELL=/bin/bash e2e-up.sh`, nohup-detached. No reseed.
  - The foreign-stack guard reported "no foreign e2e stack" before every build.
  - pnpm was `/opt/homebrew/bin/pnpm`, with `NODE_OPTIONS=""` and no `--` before Playwright flags.
  - Portal suites were spaced by at least 330 s, with an `e2e-backend` restart and health wait before each.
  - `caffeinate -dims` was held throughout.
- **Node:** v20.11.1 was first on PATH, and every run logs `node=v20.11.1 envnode=v20.11.1`. `pnpm install --frozen-lockfile` returned rc 0.
- **Timing:** all runs were 11:01–11:49 SAST on 2026-11-11, when the UTC and SAST dates are equal. The G89-F66 window does not apply.
- **Drivers in `raw/`:**
  - `lib.sh`: the #1752 `lib.sh` with WT, RAW and W changed. It adds `run_g`, which does per-run accounting of curated, baseline and snapshot output and then archives and restores the tree, and `fontprobe`;
  - `run-g1.sh`, `run-g2.sh`, `main-control.sh` and `rerun-default.sh`;
  - `shot.cjs`: screenshot plus loaded-font report;
  - `approve-locator-probe.cjs`.
- **KC app services:** I stopped backend, gateway, frontend and portal before the gate and restarted them at the end (`raw/kc-svc-status-after.txt`). The `b2b-*` infrastructure containers were untouched.

## Verdict table

The counts are passed / failed / skipped (and did-not-run, dnr) from Playwright JSON `.stats`. The per-test diff is `diff <(cut -f1,3,4,5 prior.tsv | sort) <(cut -f1,3,4,5 run.tsv | sort)`. The priors are #1752 `pr-default-clean/D-{lifecycle,depth-ii}-functional.tsv` and `D-default.tsv` (head `6fcd91abe`), `rerun-lifecycle/G-depth-ii.tsv`, and #1752 `G2-*.tsv`.

| Suite | Stack | p/f/s (dnr) | Baseline | Per-test diff | Snapshots (flag off) | Status |
|---|---|---|---|---|---|---|
| portal vitest | — | 51 files / **284 passed** | ≥284 | — | — | **PASS** |
| legal-lifecycle | G1 | **105/0/0** | 105/0/0 | empty vs D functional | writing-actual 0, baselines 0, **curated 16** | **PASS** |
| legal-depth-ii | G1 | **40/0/4** | 40/0/4 | empty vs D functional and vs G | writing-actual 0, baselines 0, **curated 10** | **PASS** |
| default `test:e2e` | G1 | 157/1/14 (7 dnr) | 165/0/14 | `invoice-lifecycle` ×8 only | 0 / 0 / 0 | (c) C1 below |
| default `test:e2e`, re-run | R (fresh) | **165/0/14** | 165/0/14 | **empty** (vs #1752 D and vs main M) | 0 / 0 / 0 | **PASS** |
| portal 90-day | G2 | **13/0/0** | 13/0/0 | empty | 0 / 0 | **PASS** |
| portal responsive | G2 | **21/0/0** | 21/0/0 | empty | 0 / 0 | **PASS** |
| portal-data-isolation (`KGOSI_EMAIL=contact@acme.example.com`) | G2 | **2/0/0**, both ran | 2/0/0 | empty | — | **PASS** |
| login-bare (production portal build) | G2 | **1/0/0** | 1/0/0 | empty | — | **PASS** |
| opt-in `E2E_VISUAL=1`, informational | G1 | VIS1 0/1/0, VIS2 1/0/0 | — | — | compare path live | as designed |

- **Curated screenshots** are counted as files under `documentation/screenshots/` with an mtime newer than the run start (`raw/stacks.txt` lists each one).
  - All are new untracked files under `legal-vertical/`; no tracked file was modified (`tracked-modified=0`).
  - The 16 + 10 = 26 files match the 26 PNGs the #1752 gate archived.
  - Each run's dirty files were archived to the scratchpad, then restored. `after-restore git-status-lines=0` after every run.
- **Magic-link exchanges** (cumulative): 1 / 2 / 4 / 5 after 90-day / responsive / isolation / login-bare, identical to #1752.
- **Token age at release:** 330 s, 330 s, 333 s and 331 s, the last before the F46 portal probe. `:3002` was free each time.

## Stack table

Every stack:
- built with rc 0 on the **first attempt**, so the retry branch never fired;
- had backend `UP`, frontend / mock-idp / mailpit 200, and `E2E Boot-Seed Complete!`;
- had tenant `tenant_7d218705360b`, with `Website Redesign|ACTIVE` and `portal_deadline_view=0` at start;
- had compose `working_dir` `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp6/compose`;
- removed the previous stack's volumes;
- ran **Flyway tenant `14|g89 contains trust data` / global `5|portal strip matter type suffix`**. That is **14 / 5**, as expected.

| Stack | Purpose | sha | Build (SAST) | Backend image | Frontend image |
|---|---|---|---|---|---|
| G1 | scored legal + default; F46 frontend; opt-in | `b146c09e0` | 11:01:46 → 11:11:35 (backend deps re-downloaded: pom changed) | `c30c1589a065` | `c5e0af146945` |
| G2 | scored portal; F46 portal | `b146c09e0` | 11:20:59 → 11:21:38 | `c30c1589a065` | `c5e0af146945` |
| M | main control | `9c268e376` | 11:45:15 → 11:45:55 | `7fc4f588f6a8` (= #1752 head backend) | `6c78ab2d7464` (= #1752 D frontend) |
| R | PR default re-run | `b146c09e0` | 11:47:40 → 11:48:18 | `c30c1589a065` | `c5e0af146945` |

- **The frontend image is now identical across PR stacks** (`c5e0af146945`). This is the PR's `.dockerignore` change: local baselines no longer enter the build context, which removes the 636A-c1 effect seen in #1752.
- **Teardown:** `e2e-down.sh` at 11:49:22 returned rc 0, leaving 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.

## F46 evidence (self-hosted fonts)

### (a) Build logs: no Google Fonts fetch

| Build | Log | Result | Google-font lines |
|---|---|---|---|
| Frontend e2e Docker image, `next build` (Next.js 16.3.8, Turbopack) | `raw/stack-G1.log`, `raw/f46-frontend-build.txt` | ✓ Compiled successfully in 11.2s, 14/14 pages | **0** |
| Portal production build (`pnpm run build`, `NEXT_PUBLIC_PORTAL_API_URL=http://localhost:8081`) | `raw/f46-portal-prod-build.txt` | ✓ Compiled successfully in 3.1s, 13/13 pages, rc 0 | **0** |
| Portal production build inside login-bare (webServer `pnpm run build && pnpm run start`) | `raw/G2-login-bare.txt` | spec passed | **0** |
| Stacks G2, M and R | `raw/stack-{G2,R}.log`, `raw/main-9c268e376/stack-M.log` | rc 0 | **0** |

- The grep was `fonts.googleapis|fonts.gstatic|Failed to fetch|next/font/google|font/google`, case-insensitive.
- In the running image, `/app/frontend/.next/static` contains 0 files referencing Google font hosts, and 6 woff2 files under `static/media/`.
- For contrast, #1752's G1 attempt 1 failed with 24 × `Can't resolve '@vercel/turbopack-next/internal/font/google/font'`.

### (b) Served CSS and font files

The probe fetched the page, read each stylesheet, and resolved each `@font-face` URL from `../media/…` to `/_next/static/media/…`. Results are in `raw/fontprobe-frontend-mock-login-resolved.txt` and `raw/fontprobe-portal-login.txt`.

**Frontend `:3001/mock-login`, on the e2e Docker production image:**
- 0 Google refs in the HTML. The stylesheet `/_next/static/chunks/10jk34lh5g694.css` has 8 `@font-face` rules and 0 Google refs:
  - `sora` (wght 100–800);
  - `plexSans` 400/500/600/700;
  - `jetbrainsMono` (100–800);
  - `sora Fallback` and `plexSans Fallback`, which are `local(Arial)` with metric overrides.
- All 6 woff2 files return `http=200 type=font/woff2`. The bytes are 22588 / 24184 / 24252 / 22832 / 40404 / 33652, equal to the committed `frontend/app/fonts/**` files.
  - (The first unresolved probe, `raw/fontprobe-frontend-mock-login.txt`, shows `http=000` because it requested the relative URL literally. It is superseded by the resolved probe.)

**Portal `:3002/login`, on the production `next start` of the build login-bare made:**
- 0 Google refs. `/_next/static/chunks/27wc7gnqytyb1.css` has the same 8 `@font-face` rules.
- All 6 woff2 files return 200 `font/woff2`, with the same byte sizes.

The browser saw the same: every font response in `raw/f46-{frontend-dashboard,portal-home}.json` is `200 font/woff2` from `/_next/static/media/`, and there are no requests to Google hosts.

### (c) Rendered typography

| Frame | PR head `b146c09e0` (self-hosted) | Prior gate, #1752 head (Google Fonts) |
|---|---|---|
| Frontend dashboard | `raw/f46-frontend-dashboard.png` (1440×900) and `raw/VIS1-written-actual-day-00-dashboard-legal-nav-active.png` (1280×720) | `raw/f46-prior-frames/pr1752-head-day-00-dashboard-legal-nav-active.png` |
| Portal | `raw/f46-portal-home.png` (`/matters`, the post-exchange landing) | `raw/f46-prior-frames/pr1752-head-portal-lg-home-populated.png` |

- **Visual comparison:** the 1280×720 dashboard frames match glyph for glyph. That covers:
  - the Sora "Home" heading;
  - the JetBrains Mono amounts ("R 8 625,00");
  - the sans nav, card titles and body text.

  The portal Sora headings ("Your matters" / "Home", "E2E Test Organization", "Portal") and the nav and body text also match. Only the data differs.
- **Computed styles** (`raw/f46-*.json`):
  - The h1 is `sora, "sora Fallback"` on both frontend and portal. Main (`raw/main-9c268e376/main-frontend-dashboard.json`) shows `Sora, "Sora Fallback"`.
  - Loaded faces on the frontend: `sora` and `jetbrainsMono`. On main: `Sora`, `JetBrains Mono` and `JetBrains Mono Fallback`. The PR sets `adjustFontFallback: false` for mono on purpose.
- **Pre-existing, not F46: body text does not render in IBM Plex Sans on either head.**
  - The computed `body` font-family is `ui-sans-serif, system-ui, …` on the PR and on main (`raw/main-9c268e376/main-frontend-dashboard.json`). Neither page loads a Plex face.
  - **Cause:** `--font-plex` is defined only on the `<body>` class (the `next/font` `variable`). But `packages/ui/styles/globals.css:58` sets `--font-sans: var(--font-plex)` in the theme. Tailwind's html default font (`--default-font-family` → `--font-sans`) is resolved at `:root`/`html`, where `--font-plex` is undefined, so it falls back to `ui-sans-serif`.
  - Elements with an explicit `font-display` (Sora) or `font-mono` class resolve correctly.
  - The Plex woff2 files are still downloaded (preloaded) on both heads.
  - The PR does not change this CSS or where the class is applied. **Recommend a separate LOW finding.** It is not a gate failure.

## Opt-in compares (`E2E_VISUAL=1`), informational, on the G1 stack after the scored runs

The spec is `legal-lifecycle/day-00-firm-setup.spec.ts -g "Capture dashboard with legal navigation"`, a regression-mode `captureScreenshot`. There is no committed baseline.

- **VIS1:** 0/1/0. `Error: A snapshot doesn't exist at …/frontend/e2e/screenshots/legal-lifecycle/day-00-dashboard-legal-nav-active.png, writing actual.` This is from `screenshot.ts:79` `expect(page).toHaveScreenshot(…)`, and one baseline file was written.
- **VIS2** (same command, now with that baseline): **1/0/0**. The compare executed and matched.
- **Flag-off contrast:** the same helper ran in G1 lifecycle and depth-ii with 0 writing-actual and 0 baseline files.
- The written baseline was archived and removed, leaving `VIS after-restore git-status-lines=0`.

## Classification

| ID | Failure | Where | Class | Status |
|---|---|---|---|---|
| C1 | `invoices/invoice-lifecycle.spec.ts:88` "DRAFT -> APPROVED" (`expect(approveBtn).toHaveCount(0)` received 1), with 7 serial tests dnr | G1 default, 11:16–11:17 SAST | **(c) pre-existing spec race, reproduces on main** | Re-run on G1 8/8 passed. Fresh-stack PR re-run R 165/0/14. |
| (a) | product regression | — | **none** | — |

### C1: `getByRole('button', { name: 'Approve' })` also matches the audit row "Fee note approved"

- **Spec** (`invoice-lifecycle.spec.ts:99-106`, unchanged by the PR): it clicks Approve, waits for "Send fee note", then asserts `getByRole('button', { name: 'Approve' })` has count 0.
  - Without `exact: true`, Playwright matches the accessible name as a case-insensitive substring.
- **Error context** (`raw/failure-artifacts/G1-default/…/error-context.md`):
  - the header shows `Approved` status, with `Send fee note` and `Void` buttons, so the transition itself succeeded;
  - the audit trail shows `button "INFO Fee note approved Alice Owner just now"`.
- **Deterministic probe** (`approve-locator-probe.cjs`), run on an APPROVED fee note after the audit trail rendered. PR: `raw/G1-approve-locator-probe.json`; main: `raw/main-9c268e376/M-approve-locator-probe.json`. Both return identically:
  - `substringApproveCount: 1`, matching `"INFOFee note approvedAlice Ownerjust now"`;
  - `exactApproveCount: 0`.

  The test therefore passes only if one 5 s poll lands before the audit trail re-renders with the approval event.
- **Rates:**

  | Head | Run | Locator failures | Collision noise (excluded) |
  |---|---|---|---|
  | PR | `--repeat-each=10`, parallel (`raw/G1-repeat10-invoice-lifecycle.txt`) | 2 | 2 (`POST /api/clients` 500/409) |
  | PR | `--repeat-each=10 --workers=1` (`raw/G1-repeat10w1-*.txt`) | **0**: 80/80 | — |
  | **Main `9c268e376`** | `--repeat-each=10`, parallel (`raw/main-9c268e376/M-repeat10-invoice-lifecycle.txt`) | **5** | 3 |

  The collision noise comes from `--repeat-each` itself: parallel copies share the same RUN_ID client name, which also accounts for the one backend ERROR at 09:18:17Z.
- **Origin:**
  - The label "Fee note approved" comes from main's `AuditEventTypeRegistry.java:173`. Since #1752 / G-D10a, audit rows render labels.
  - The PR's frontend production diff is `app/layout.tsx` only (fonts), and the invoice page is unchanged.
  - The main full default run M was 165/0/14 and the PR re-run R was 165/0/14, with an identical per-test set. The race is intermittent on both heads.
- **Suggested spec fix** (not applied; the orchestrator decides): `getByRole('button', { name: 'Approve', exact: true })`, or scope the locator to the header actions.

## Backend log

Summary in `raw/backend-log-scan.txt` (JSON-level WARN/ERROR by logger):
- **G1:** the WARN classes are as before:
  - PDFBox font fallback;
  - draft invoice without tax number;
  - `No recipients found for DOCUMENT_GENERATED` (12);
  - `Forbidden … /time-summary/by-member` (12, the same as #1752);
  - invoice email rate limit (15, from my repeat runs).
- **G1 ERROR:** 1, at 09:18:17Z (11:18:17 SAST): the duplicate-key 500 from the parallel `--repeat-each` client-name collision. That was after the scored runs, which ended 11:17:19. **The scored runs logged 0 ERROR.**
- **G2:** 0 ERROR.

## Raw files (`raw/`)

- **Scored runs:**
  - `G1-{lifecycle,depth-ii,default}.{json,tsv,txt}` and `G2-{90day,responsive,isolation,login-bare}.{json,tsv,txt}`;
  - `portal-vitest.txt`, `pnpm-install.txt`;
  - a `*.dirty.txt` per run: the paths written, before restore.
- **Re-runs and controls:**
  - `G1-rerun-invoice-lifecycle.*`, `G1-repeat10-invoice-lifecycle.*`, `G1-repeat10w1-invoice-lifecycle.*`, `G1-approve-locator-probe.json`;
  - `R-default.*`;
  - `main-9c268e376/` (`M-default.*`, `M-repeat10-invoice-lifecycle.*`, `M-approve-locator-probe.json`, `main-frontend-dashboard.{png,json}`, `stacks.txt`, `timeline.txt`, `stack-M.log`).
- **Opt-in:** `VIS1-optin-missing.*`, `VIS2-optin-compare.*`, `VIS1-written-actual-day-00-dashboard-legal-nav-active.png`.
- **F46:**
  - `f46-frontend-build.txt`, `f46-portal-prod-build.txt`;
  - `fontprobe-*.txt`;
  - `f46-frontend-dashboard.{png,json}`, `f46-portal-home.{png,json}`;
  - `f46-prior-frames/`.
- **Stacks:** `stacks.txt` (checks, accounting, spacing), `timeline.txt`, `stack-{G1,G2,R}.log`, `e2e-down.txt`, `backend-log-scan.txt`, `kc-svc-status-after.txt`.
- **`failure-artifacts/`:** `G1-default` (C1 screenshot and error-context) and `G1-repeat10`.
- **Drivers:** `lib.sh`, `run-g1.sh`, `run-g2.sh`, `main-control.sh`, `rerun-default.sh`, `shot.cjs`, `approve-locator-probe.cjs`.

## Environment left behind

- The e2e stack is down: 0 `e2e-*` containers and 0 `docteams-e2e_*` volumes.
- **KC app services are restarted and all healthy:** backend 8080, gateway 8443, frontend 3000 and portal 3002 (`raw/kc-svc-status-after.txt`).
- **Worktree:** `/Users/rakheendama/Projects/2026/b2b-strawman-g89-cp6` is detached at **`b146c09e0`**, with `git status --porcelain --untracked-files=all` empty.
  - The main control checked out `9c268e376` in the same worktree and returned to `b146c09e0`.
  - The per-run dirty archives (curated PNGs, the VIS baseline) and full test-results copies are in `/private/tmp/claude-501/-Users-rakheendama-Projects-2026-b2b-strawman/07c4c473-e34a-4781-9427-920cc1025a39/scratchpad/pr1753/`.
- Nothing was committed or merged.
