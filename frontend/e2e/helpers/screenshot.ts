import { expect, Page, Locator } from "@playwright/test";
import { mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";

const CURATED_DIR = path.resolve(
  __dirname,
  "..",
  "..",
  "..",
  "documentation",
  "screenshots",
  "legal-vertical"
);

export interface ScreenshotOptions {
  /** Save a curated screenshot for documentation (default: false = regression mode, E2E_VISUAL=1 only) */
  curated?: boolean;
  /** Capture the full scrollable page (default: false) */
  fullPage?: boolean;
  /** Capture a specific element instead of the full page */
  locator?: Locator;
}

/**
 * Validate that the screenshot name does not escape the target directory.
 */
function assertSafeName(name: string, baseDir: string): string {
  const resolved = path.resolve(baseDir, `${name}.png`);
  if (!resolved.startsWith(path.resolve(baseDir))) {
    throw new Error(`Screenshot name "${name}" would write outside curated directory`);
  }
  return resolved;
}

/**
 * Baseline compares are opt-in (founder ruling 2026-11-03; G89-F67): `toHaveScreenshot()` runs
 * only when `E2E_VISUAL=1`. Without it a gate needs no capture-then-score stack pair, and
 * volatile frames cannot fail a functional run. Curated screenshots are always written.
 */
export function visualComparesEnabled(env: NodeJS.ProcessEnv = process.env): boolean {
  return env.E2E_VISUAL === "1";
}

/**
 * Capture a screenshot in one of two modes:
 *
 * **Regression mode** (default): with `E2E_VISUAL=1`, uses Playwright's `toHaveScreenshot()` to
 * compare against baselines in `e2e/screenshots/legal-lifecycle/` (or `legal-depth-ii/`). Without
 * the flag it is a no-op (see `visualComparesEnabled`).
 *
 * **Curated mode** (`curated: true`): Saves a standalone PNG to
 * `documentation/screenshots/legal-vertical/` for use in walkthroughs and blog posts, with or
 * without the flag.
 */
export async function captureScreenshot(
  page: Page,
  name: string,
  options?: ScreenshotOptions
): Promise<void> {
  const { curated = false, fullPage = false, locator } = options ?? {};

  if (curated) {
    mkdirSync(CURATED_DIR, { recursive: true });
    const filePath = assertSafeName(name, CURATED_DIR);

    if (locator) {
      const buffer = await locator.screenshot();
      writeFileSync(filePath, buffer);
    } else {
      const buffer = await page.screenshot({ fullPage });
      writeFileSync(filePath, buffer);
    }
  } else {
    if (!visualComparesEnabled()) return;
    // fullPage is only valid for page-level assertions, not locator assertions
    if (locator) {
      await expect(locator).toHaveScreenshot(`${name}.png`);
    } else {
      await expect(page).toHaveScreenshot(`${name}.png`, {
        fullPage: fullPage ? true : undefined,
      });
    }
  }
}
