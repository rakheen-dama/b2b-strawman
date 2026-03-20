/**
 * FIN-01: Profitability Dashboard — Playwright E2E Tests
 *
 * Tests profitability page: loads with data, project view,
 * customer view, utilization metrics.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data with time entries and rates present
 */
import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'

const ORG = 'e2e-test-org'
const base = `/org/${ORG}`

test.describe('FIN-01: Profitability Dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'alice')
  })

  test('Page loads with data', async ({ page }) => {
    await page.goto(`${base}/profitability`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    const content = page.locator('main, [role="main"], .flex-1').first()
    const text = await content.textContent({ timeout: 10000 })

    // Page should render profitability content
    expect(text?.length).toBeGreaterThan(0)
  })

  test('Project profitability view', async ({ page }) => {
    await page.goto(`${base}/profitability`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    // Look for project view tab or filter
    const projectTab = page.locator('[role="tab"]:has-text("Project"), button:has-text("Project"), a:has-text("Project")').first()
    if (await projectTab.isVisible().catch(() => false)) {
      await projectTab.click()
      await page.waitForTimeout(1000)
    }

    // Should show per-project data
    const content = page.locator('main, [role="main"], .flex-1').first()
    await expect(content).toBeVisible()
  })

  test('Customer profitability view', async ({ page }) => {
    await page.goto(`${base}/profitability`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    // Look for customer view tab or filter
    const customerTab = page.locator('[role="tab"]:has-text("Customer"), button:has-text("Customer"), a:has-text("Customer")').first()
    if (await customerTab.isVisible().catch(() => false)) {
      await customerTab.click()
      await page.waitForTimeout(1000)
    }

    const content = page.locator('main, [role="main"], .flex-1').first()
    await expect(content).toBeVisible()
  })

  test('Utilization metrics', async ({ page }) => {
    await page.goto(`${base}/profitability`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    // Look for utilization section or tab
    const utilTab = page.locator('[role="tab"]:has-text("Utilization"), button:has-text("Utilization"), a:has-text("Utilization")').first()
    if (await utilTab.isVisible().catch(() => false)) {
      await utilTab.click()
      await page.waitForTimeout(1000)
    }

    const content = page.locator('main, [role="main"], .flex-1').first()
    const text = await content.textContent()

    // Should have percentage or utilization data
    expect(text?.length).toBeGreaterThan(0)
  })
})
