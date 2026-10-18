import { defineConfig, devices } from "@playwright/test";

const authMode = process.env.E2E_AUTH_MODE || "mock";
if (authMode !== "mock") {
  throw new Error(
    `Legal depth-II suite only supports mock auth (E2E_AUTH_MODE="${authMode}" is unsupported)`
  );
}

// helpers/mailpit.ts reads MAILPIT_URL (default :8025, the dev stack's Mailpit). The E2E
// mock-auth stack maps its Mailpit to :8026 (compose/docker-compose.e2e.yml), so default
// to that here; an explicit MAILPIT_URL still wins. Workers inherit the runner's env.
process.env.MAILPIT_URL ||= "http://localhost:8026";

export default defineConfig({
  testDir: "./tests/legal-depth-ii",
  snapshotPathTemplate: "{testDir}/../../screenshots/legal-depth-ii/{arg}{ext}",
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
