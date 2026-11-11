import { afterEach, describe, expect, it, vi } from "vitest";

// Founder ruling 2026-11-03 (G89 Batch F): the portal e2e baseline compares
// (`toHaveScreenshot`, 14 call sites in e2e/tests) are opt-in. The Playwright
// config sets `ignoreSnapshots` unless E2E_VISUAL=1, so a gate needs no
// capture-then-score stack pair. Frontend twin: the `captureScreenshot` helper
// gate (frontend/__tests__/e2e-helpers/screenshot-visual-opt-in.test.ts).

async function loadConfig() {
  vi.resetModules();
  return (await import("../playwright.portal.config")).default;
}

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("playwright.portal.config visual compares", () => {
  it("ignores snapshots without E2E_VISUAL", async () => {
    vi.stubEnv("E2E_VISUAL", "");
    expect((await loadConfig()).ignoreSnapshots).toBe(true);
  });

  it("ignores snapshots for any value other than 1", async () => {
    vi.stubEnv("E2E_VISUAL", "true");
    expect((await loadConfig()).ignoreSnapshots).toBe(true);
  });

  it("compares snapshots with E2E_VISUAL=1", async () => {
    vi.stubEnv("E2E_VISUAL", "1");
    expect((await loadConfig()).ignoreSnapshots).toBe(false);
  });
});
