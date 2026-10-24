import { defineConfig, devices } from "@playwright/test";

/**
 * Epic 499B — Portal visual baselines (sm / md / lg).
 * Epic 500A — Portal client-POV 90-day lifecycle capstone (new project).
 *
 * testDir:              ./e2e/tests
 * snapshotPathTemplate: writes PNGs under
 *   ./e2e/screenshots/portal-v2/{projectName}/<arg>
 *   so a snapshot arg of "sm/home-populated.png" lands at
 *   portal/e2e/screenshots/portal-v2/chromium/sm/home-populated.png
 *   Including `{projectName}` prevents cross-project clobber when additional
 *   browser projects (webkit, firefox) are added later.
 *
 * Projects:
 *   - `chromium`              — responsive visual baselines (Epic 499B).
 *                               Scoped to ./e2e/tests/responsive so it does
 *                               not double-run the lifecycle capstone specs.
 *   - `portal-client-90day`   — sequential client-POV 90-day lifecycle
 *                               (Epic 500A scaffold / 500B execution).
 *                               Scoped to ./e2e/tests/portal-client-90day.
 *                               Relies on the top-level `workers: 1` so later
 *                               days observe state created by earlier days.
 *   - `login-bare`            — Phase 88 624D: bare /login → email-only
 *                               request-link → Mailpit link → /matters.
 *                               See e2e/tests/login-bare.spec.ts.
 *                               - OPT-IN: registered only when LOGIN_BARE=1,
 *                                 so the default all-projects run does not
 *                                 include it. Run it via
 *                                 `pnpm test:e2e:login-bare`.
 *                               - With the flag set, `webServer` self-serves
 *                                 a PRODUCTION portal on :3002
 *                                 (`pnpm run build && pnpm run start`), with
 *                                 NEXT_PUBLIC_PORTAL_API_URL passed through
 *                                 (default: BACKEND_URL, else the e2e backend
 *                                 :8081) so the build targets that backend.
 *                                 Under `pnpm dev` the exchange double-POSTs
 *                                 (StrictMode in app/auth/exchange/page.tsx;
 *                                 follow-up: exchange double-POST under
 *                                 next dev). A server already on :3002 is
 *                                 still reused, and the spec's exchange-count
 *                                 check fails loudly if that is a dev server.
 *                               - Spends 1 request of the per-email cap
 *                                 (3 / 15 min) per run; PORTAL_CONTACT_EMAIL
 *                                 set to the same address spends a second
 *                                 (globalSetup). Run it without that env.
 *
 * First-run usage (baselines):
 *   pnpm exec playwright test \
 *     --config=playwright.portal.config.ts --update-snapshots
 *
 *   # Just the lifecycle project:
 *   pnpm exec playwright test \
 *     --config=playwright.portal.config.ts \
 *     --project=portal-client-90day --update-snapshots
 *
 * By default all projects except the opt-in `login-bare` run (and
 * `webServer` boots the portal with `pnpm dev`).
 * Set SKIP_PORTAL_BASELINES=true to skip (the webServer is also skipped in
 * that case — no point booting a dev server when every test is skipped).
 * See e2e/tests/responsive/portal-pages.spec.ts and
 *     e2e/tests/portal-client-90day/*.spec.ts for details.
 */
// Phase 88 624D: opt-in `login-bare` project + production webServer.
const LOGIN_BARE = process.env.LOGIN_BARE === "1";

export default defineConfig({
  snapshotPathTemplate:
    "{testDir}/../../screenshots/portal-v2/{projectName}/{arg}{ext}",
  // Epic 617B: one magic-link exchange per run (see e2e/helpers/global-setup.ts).
  globalSetup: "./e2e/helpers/global-setup.ts",
  globalTimeout: 600_000,
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
  // `workers: 1` is mandatory for the `portal-client-90day` project because
  // later days read state created by earlier days. Keeping it at the top
  // level also preserves the existing single-worker behaviour for the
  // responsive project (Epic 499B) and prevents cross-project interference.
  workers: 1,
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.01,
      animations: "disabled",
    },
  },
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3002",
    screenshot: "only-on-failure",
    trace: "on-first-retry",
  },
  webServer:
    process.env.SKIP_PORTAL_BASELINES === "true"
      ? undefined
      : LOGIN_BARE
        ? {
            // Production build: `next dev` double-POSTs the exchange.
            command: "pnpm run build && pnpm run start",
            url: "http://localhost:3002",
            reuseExistingServer: !process.env.CI,
            timeout: 300_000,
            env: {
              NODE_OPTIONS: "",
              NEXT_PUBLIC_PORTAL_API_URL:
                process.env.NEXT_PUBLIC_PORTAL_API_URL ||
                process.env.BACKEND_URL ||
                "http://localhost:8081",
            },
          }
        : {
            command: "NODE_OPTIONS= pnpm dev",
            url: "http://localhost:3002",
            reuseExistingServer: !process.env.CI,
            timeout: 120_000,
          },
  projects: [
    {
      name: "chromium",
      testDir: "./e2e/tests/responsive",
      use: {
        ...devices["Desktop Chrome"],
        deviceScaleFactor: 2,
      },
    },
    {
      name: "portal-client-90day",
      testDir: "./e2e/tests/portal-client-90day",
      use: {
        ...devices["Desktop Chrome"],
        deviceScaleFactor: 2,
      },
      // Sequential by virtue of the top-level `workers: 1` — later days
      // depend on state created by earlier days in the lifecycle.
    },
    // Phase 88 624D: opt-in (LOGIN_BARE=1). The spec sits at the top of
    // ./e2e/tests, outside the responsive/ and portal-client-90day/ dirs the
    // other projects scope to.
    ...(LOGIN_BARE
      ? [
          {
            name: "login-bare",
            testDir: "./e2e/tests",
            testMatch: /login-bare\.spec\.ts$/,
            use: {
              ...devices["Desktop Chrome"],
            },
          },
        ]
      : []),
  ],
});
