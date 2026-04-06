import { expect, Page, Locator } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const CURATED_DIR = path.resolve(
  __dirname,
  '..',
  '..',
  '..',
  'documentation',
  'screenshots',
  'legal-vertical',
)

export interface ScreenshotOptions {
  /** Save a curated screenshot for documentation (default: false = regression mode) */
  curated?: boolean
  /** Capture the full scrollable page (default: false) */
  fullPage?: boolean
  /** Capture a specific element instead of the full page */
  locator?: Locator
}

/**
 * Validate that the screenshot name does not escape the target directory.
 */
function assertSafeName(name: string, baseDir: string): string {
  const resolved = path.resolve(baseDir, `${name}.png`)
  if (!resolved.startsWith(path.resolve(baseDir))) {
    throw new Error(`Screenshot name "${name}" would write outside curated directory`)
  }
  return resolved
}

/**
 * Capture a screenshot in one of two modes:
 *
 * **Regression mode** (default): Uses Playwright's `toHaveScreenshot()` to compare
 * against committed baselines. Baselines are stored in `e2e/screenshots/legal-lifecycle/`.
 *
 * **Curated mode** (`curated: true`): Saves a standalone PNG to
 * `documentation/screenshots/legal-vertical/` for use in walkthroughs and blog posts.
 */
export async function captureScreenshot(
  page: Page,
  name: string,
  options?: ScreenshotOptions,
): Promise<void> {
  const { curated = false, fullPage = false, locator } = options ?? {}

  if (curated) {
    mkdirSync(CURATED_DIR, { recursive: true })
    const filePath = assertSafeName(name, CURATED_DIR)

    if (locator) {
      const buffer = await locator.screenshot()
      writeFileSync(filePath, buffer)
    } else {
      const buffer = await page.screenshot({ fullPage })
      writeFileSync(filePath, buffer)
    }
  } else {
    // fullPage is only valid for page-level assertions, not locator assertions
    if (locator) {
      await expect(locator).toHaveScreenshot(`${name}.png`)
    } else {
      await expect(page).toHaveScreenshot(`${name}.png`, {
        fullPage: fullPage ? true : undefined,
      })
    }
  }
}
