import { defineConfig, devices } from "@playwright/test";

const authMode = process.env.E2E_AUTH_MODE || "mock";
if (authMode !== "mock") {
  throw new Error(
    `Consulting lifecycle suite only supports mock auth (E2E_AUTH_MODE="${authMode}" is unsupported)`
  );
}

export default defineConfig({
  testDir: "./tests/consulting-lifecycle",
  snapshotPathTemplate: "{testDir}/../../screenshots/consulting-lifecycle/{arg}{ext}",
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
