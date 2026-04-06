/**
 * Day 0 — Setup Baseline Screenshots
 *
 * Validates the screenshot infrastructure pipeline by logging in as Alice
 * and capturing a baseline screenshot of the dashboard.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Playwright browsers installed: npx playwright install chromium
 *
 * Run:
 *   pnpm test:e2e:legal-lifecycle
 */
import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'
import { captureScreenshot } from '../../helpers/screenshot'

const ORG = 'e2e-test-org'
const BASE = `/org/${ORG}`

test.describe.serial('Day 0 — Setup Baselines', () => {
  test('Dashboard baseline screenshot', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/dashboard`)
    await expect(page.locator('main')).toBeVisible()

    await captureScreenshot(page, 'day-00-dashboard-initial')
  })
})
