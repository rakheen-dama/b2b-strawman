import { defineConfig, devices } from "@playwright/test";

const authMode = process.env.E2E_AUTH_MODE || "mock";
if (authMode !== "mock") {
  throw new Error(
    `Legal lifecycle suite only supports mock auth (E2E_AUTH_MODE="${authMode}" is unsupported)`
  );
}

export default defineConfig({
  testDir: "./tests/legal-lifecycle",
  snapshotPathTemplate: "{testDir}/../../screenshots/legal-lifecycle/{arg}{ext}",
  globalTimeout: 600_000,
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
