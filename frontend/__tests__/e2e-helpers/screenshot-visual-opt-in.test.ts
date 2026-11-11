import type { Page } from "@playwright/test";
import { afterEach, describe, expect, it, vi } from "vitest";
import { captureScreenshot, visualComparesEnabled } from "../../e2e/helpers/screenshot";

// Founder ruling 2026-11-03 + G89-F67: the e2e baseline compare (`toHaveScreenshot`) is opt-in
// via E2E_VISUAL=1. Without the flag the regression-mode call is a no-op, so a gate needs no
// capture-then-score stack pair and volatile frames (G89-F67) cannot fail it. Curated
// documentation screenshots are unaffected (they never compared).

/** A stand-in that is not a Playwright Page: `expect(page).toHaveScreenshot` throws on it. */
function fakePage() {
  return { screenshot: vi.fn(async () => Buffer.from("png")) } as unknown as Page & {
    screenshot: ReturnType<typeof vi.fn>;
  };
}

afterEach(() => {
  vi.unstubAllEnvs();
});

describe("visualComparesEnabled", () => {
  it("is on only for E2E_VISUAL=1", () => {
    expect(visualComparesEnabled({})).toBe(false);
    expect(visualComparesEnabled({ E2E_VISUAL: "" })).toBe(false);
    expect(visualComparesEnabled({ E2E_VISUAL: "0" })).toBe(false);
    expect(visualComparesEnabled({ E2E_VISUAL: "true" })).toBe(false);
    expect(visualComparesEnabled({ E2E_VISUAL: "1" })).toBe(true);
  });
});

describe("captureScreenshot regression mode", () => {
  it("skips the baseline compare without E2E_VISUAL", async () => {
    vi.stubEnv("E2E_VISUAL", "");
    const page = fakePage();
    await expect(captureScreenshot(page, "day-00-probe")).resolves.toBeUndefined();
    expect(page.screenshot).not.toHaveBeenCalled();
  });

  it("runs the baseline compare with E2E_VISUAL=1", async () => {
    vi.stubEnv("E2E_VISUAL", "1");
    // The compare is attempted: Playwright's expect rejects the stand-in page.
    await expect(captureScreenshot(fakePage(), "day-00-probe")).rejects.toThrow();
  });
});
