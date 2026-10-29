import { defineConfig, devices } from "@playwright/test";

const authMode = process.env.E2E_AUTH_MODE || "mock";

// Phase 88 628F (P-1, U22): the legal suites run under their own configs
// (playwright.legal-*.config.ts) with run state and serial chains; the default config must never
// re-run them against a populated tenant. A project-level testIgnore replaces the config-level one,
// so both chromium projects list them.
const LEGAL_SUITES = ["**/legal-lifecycle/**", "**/legal-depth-ii/**"];
// KC-only demo recording scripts: they cannot run on the mock stack; not converted (628F R-2).
// Follow-up: convert under KC-STACK or delete.
const KC_ONLY_SCRIPTS = ["**/demo-full-90day.spec.ts", "**/demo-recording.spec.ts"];

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
            testIgnore: [/keycloak\/onboarding\.spec\.ts/, ...LEGAL_SUITES],
            dependencies: ["kc-setup"],
          },
        ]
      : [
          {
            name: "chromium",
            use: { ...devices["Desktop Chrome"] },
            testIgnore: ["**/keycloak/**", ...LEGAL_SUITES, ...KC_ONLY_SCRIPTS],
          },
        ],
});
