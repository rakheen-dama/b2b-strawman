import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'

const ORG = 'e2e-test-org'
const base = `/org/${ORG}`

const NAV_ITEMS = [
  { name: 'Dashboard', path: `${base}/dashboard` },
  { name: 'My Work', path: `${base}/my-work` },
  { name: 'Calendar', path: `${base}/calendar` },
  { name: 'Projects', path: `${base}/projects` },
  { name: 'Documents', path: `${base}/documents` },
  { name: 'Customers', path: `${base}/customers` },
  { name: 'Retainers', path: `${base}/retainers` },
  { name: 'Compliance', path: `${base}/compliance` },
  { name: 'Invoices', path: `${base}/invoices` },
  { name: 'Proposals', path: `${base}/proposals` },
  { name: 'Profitability', path: `${base}/profitability` },
  { name: 'Reports', path: `${base}/reports` },
  { name: 'Team', path: `${base}/team` },
  { name: 'Resources', path: `${base}/resources` },
  { name: 'Notifications', path: `${base}/notifications` },
  { name: 'Settings', path: `${base}/settings` },
]

test.describe('NAV-01: Sidebar Navigation', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'alice')
  })

  for (const item of NAV_ITEMS) {
    test(`${item.name} page loads`, async ({ page }) => {
      await page.goto(item.path)
      await expect(page.locator('body')).not.toContainText('Something went wrong')
      // Page should have some content (not blank)
      const bodyText = await page.locator('main, [role="main"], .flex-1').first().textContent()
      expect(bodyText?.length).toBeGreaterThan(0)
    })
  }
})
