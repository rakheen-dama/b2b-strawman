import { defineConfig, devices } from "@playwright/test";

const authMode = process.env.E2E_AUTH_MODE || "mock";
if (authMode !== "mock") {
  throw new Error(
    `Legal lifecycle suite only supports mock auth (E2E_AUTH_MODE="${authMode}" is unsupported)`
  );
}

/**
 * The Phase 92 exit month (652E, rulings D-10 / D-12): its own project, not part of the default
 * lifecycle run. It posts back-dated entries on the seeded primary trust account and is single-shot
 * per stack, so it runs alone on a fresh stack: `pnpm test:e2e:exit-month` (= `--project exit-month`).
 * `pnpm test:e2e:legal-lifecycle` runs `--project chromium`, which ignores it.
 */
const EXIT_MONTH_SPEC = "**/trust-balanced-month.spec.ts";

export default defineConfig({
  testDir: "./tests/legal-lifecycle",
  snapshotPathTemplate: "{testDir}/../../screenshots/legal-lifecycle/{arg}{ext}",
  // 652E D-10: 30 min — the exit month alone is a long serial chain (per-test timeouts set in the
  // spec); the lifecycle suite takes ~3 min
  globalTimeout: 1_800_000,
  timeout: 30_000,
  // No retries: the lifecycle is one serial chain whose records are named per run, not per
  // attempt (helpers/legal-lifecycle.ts run state). A retried group would re-create the same
  // clients/matters and break the "No Conflict" checks and exact-name lookups (628E).
  retries: 0,
  workers: 1,
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.01,
      animations: "disabled",
    },
  },
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3000",
    // Product "today" is South African time (G88-F49); PW_TIMEZONE overrides for boundary runs.
    timezoneId: process.env.PW_TIMEZONE ?? "Africa/Johannesburg",
    screenshot: "only-on-failure",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      testIgnore: EXIT_MONTH_SPEC,
      use: {
        ...devices["Desktop Chrome"],
        deviceScaleFactor: 2,
      },
    },
    {
      name: "exit-month",
      testMatch: EXIT_MONTH_SPEC,
      use: {
        ...devices["Desktop Chrome"],
        deviceScaleFactor: 2,
      },
    },
  ],
});
