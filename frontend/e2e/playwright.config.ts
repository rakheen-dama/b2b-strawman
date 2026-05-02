import { defineConfig, devices } from "@playwright/test";

const authMode = process.env.E2E_AUTH_MODE || "mock";

export default defineConfig({
  testDir: "./tests",
  globalTimeout: 600_000,
  timeout: authMode === "keycloak" ? 60_000 : 30_000,
  retries: process.env.CI ? 1 : 0,
  workers: authMode === "keycloak" ? 1 : undefined,
  use: {
    baseURL: process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3000",
    screenshot: "only-on-failure",
    trace: "on-first-retry",
  },
  projects:
    authMode === "keycloak"
      ? [
          // Onboarding writes /tmp/e2e-keycloak-state.json which member-invite-rbac
          // reads at describe-collection time. Run it as a setup project so the
          // state file exists before any dependent specs are collected.
          {
            name: "kc-setup",
            use: { ...devices["Desktop Chrome"] },
            testMatch: /keycloak\/onboarding\.spec\.ts/,
          },
          {
            name: "chromium",
            use: { ...devices["Desktop Chrome"] },
            testIgnore: /keycloak\/onboarding\.spec\.ts/,
            dependencies: ["kc-setup"],
          },
        ]
      : [
          {
            name: "chromium",
            use: { ...devices["Desktop Chrome"] },
            testIgnore: ["**/keycloak/**"],
          },
        ],
});
