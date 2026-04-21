import { defineConfig, devices } from "@playwright/test";

/**
 * Epic 499B — Portal visual baselines (sm / md / lg).
 *
 * testDir:              ./e2e/tests
 * snapshotPathTemplate: writes PNGs under ./e2e/screenshots/portal-v2/<arg>
 *   so a snapshot arg of "sm/home-populated.png" lands at
 *   portal/e2e/screenshots/portal-v2/sm/home-populated.png
 *
 * First-run usage (baselines):
 *   pnpm exec playwright test \
 *     --config=playwright.portal.config.ts --update-snapshots
 *
 * By default the responsive spec is gated behind SKIP_PORTAL_BASELINES.
 * Unset (or set to "false") to run against a seeded portal. See
 * e2e/tests/responsive/portal-pages.spec.ts for details.
 */
export default defineConfig({
  testDir: "./e2e/tests",
  snapshotPathTemplate: "{testDir}/../screenshots/portal-v2/{arg}{ext}",
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
