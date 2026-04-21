import { defineConfig, devices } from "@playwright/test";

/**
 * Epic 499B — Portal visual baselines (sm / md / lg).
 *
 * testDir:              ./e2e/tests
 * snapshotPathTemplate: writes PNGs under
 *   ./e2e/screenshots/portal-v2/{projectName}/<arg>
 *   so a snapshot arg of "sm/home-populated.png" lands at
 *   portal/e2e/screenshots/portal-v2/chromium/sm/home-populated.png
 *   Including `{projectName}` prevents cross-project clobber when additional
 *   browser projects (webkit, firefox) are added later.
 *
 * First-run usage (baselines):
 *   pnpm exec playwright test \
 *     --config=playwright.portal.config.ts --update-snapshots
 *
 * By default the responsive spec runs (and `webServer` boots the portal).
 * Set SKIP_PORTAL_BASELINES=true to skip (the webServer is also skipped in
 * that case — no point booting a dev server when every test is skipped).
 * See e2e/tests/responsive/portal-pages.spec.ts for details.
 */
export default defineConfig({
  testDir: "./e2e/tests",
  snapshotPathTemplate:
    "{testDir}/../screenshots/portal-v2/{projectName}/{arg}{ext}",
  globalTimeout: 600_000,
  timeout: 30_000,
  retries: process.env.CI ? 1 : 0,
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
      : {
          command: "NODE_OPTIONS= pnpm dev",
          url: "http://localhost:3002",
          reuseExistingServer: !process.env.CI,
          timeout: 120_000,
        },
  projects: [
    {
      name: "chromium",
      use: {
        ...devices["Desktop Chrome"],
        deviceScaleFactor: 2,
      },
    },
  ],
});
