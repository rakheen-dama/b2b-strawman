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
    const filePath = path.join(CURATED_DIR, `${name}.png`)

    if (locator) {
      const buffer = await locator.screenshot()
      writeFileSync(filePath, buffer)
    } else {
      const buffer = await page.screenshot({ fullPage })
      writeFileSync(filePath, buffer)
    }
  } else {
    const target = locator ?? page
    await expect(target).toHaveScreenshot(`${name}.png`, {
      fullPage: fullPage ? true : undefined,
    })
  }
}
