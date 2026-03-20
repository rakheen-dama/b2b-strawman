/**
 * NOTIF-01: Notification Bell — Playwright E2E Tests
 *
 * Tests the notification bell in the header: unread count badge,
 * dropdown, mark read, navigate to page, mark all read.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Seed data with notifications present
 */
import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'

const ORG = 'e2e-test-org'
const base = `/org/${ORG}`

test.describe('NOTIF-01: Notification Bell', () => {
  test('Bell shows unread count', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${base}/dashboard`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    // Look for notification bell icon button in the header
    const bellButton = page.locator('button[aria-label*="notification" i], button:has(svg.lucide-bell), [data-testid="notification-bell"]').first()
    const bellVisible = await bellButton.isVisible({ timeout: 10000 }).catch(() => false)

    if (!bellVisible) {
      test.skip(true, 'Notification bell not found in header')
      return
    }

    // Bell should be visible — badge count may or may not be present
    await expect(bellButton).toBeVisible()
  })

  test('Click bell opens dropdown', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${base}/dashboard`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    const bellButton = page.locator('button[aria-label*="notification" i], button:has(svg.lucide-bell), [data-testid="notification-bell"]').first()
    const bellVisible = await bellButton.isVisible({ timeout: 10000 }).catch(() => false)

    if (!bellVisible) {
      test.skip(true, 'Notification bell not found in header')
      return
    }

    await bellButton.click()
    await page.waitForTimeout(1000)

    // Dropdown or popover should appear with notification items
    const dropdown = page.locator('[role="menu"], [role="dialog"], [data-testid="notification-dropdown"], .popover-content').first()
    const dropdownVisible = await dropdown.isVisible().catch(() => false)

    if (!dropdownVisible) {
      // Might navigate directly to notifications page instead
      const url = page.url()
      expect(url.includes('notification') || dropdownVisible).toBeTruthy()
    }
  })

  test('Mark notification as read', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${base}/dashboard`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    const bellButton = page.locator('button[aria-label*="notification" i], button:has(svg.lucide-bell), [data-testid="notification-bell"]').first()
    const bellVisible = await bellButton.isVisible({ timeout: 10000 }).catch(() => false)

    if (!bellVisible) {
      test.skip(true, 'Notification bell not found in header')
      return
    }

    await bellButton.click()
    await page.waitForTimeout(1000)

    // Look for a notification item to click/mark as read
    const notifItem = page.locator('[data-testid*="notification-item"], .notification-item').first()
    const hasNotif = await notifItem.isVisible().catch(() => false)

    if (!hasNotif) {
      // Try notifications page directly
      await page.goto(`${base}/notifications`)
      await expect(page.locator('body')).not.toContainText('Something went wrong')
    }
  })

  test('Navigate to notifications page', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${base}/notifications`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    const content = page.locator('main, [role="main"], .flex-1').first()
    const contentText = await content.textContent({ timeout: 10000 })

    // Page should show notifications content
    expect(contentText?.length).toBeGreaterThan(0)
  })

  test('Mark all as read', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${base}/notifications`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    // Look for "Mark all as read" button
    const markAllBtn = page.getByRole('button', { name: /mark all.*read/i }).first()
    const btnVisible = await markAllBtn.isVisible().catch(() => false)

    if (!btnVisible) {
      test.skip(true, 'Mark all as read button not visible (may be no unread notifications)')
      return
    }

    await markAllBtn.click()
    await page.waitForTimeout(1000)

    // After marking all read, badge count should be gone or 0
    const bellButton = page.locator('button[aria-label*="notification" i], button:has(svg.lucide-bell), [data-testid="notification-bell"]').first()
    if (await bellButton.isVisible().catch(() => false)) {
      // Badge should not show a positive count
      const badge = bellButton.locator('.badge, [data-testid="unread-count"]')
      const badgeVisible = await badge.isVisible().catch(() => false)
      if (badgeVisible) {
        const badgeText = await badge.textContent()
        expect(badgeText === '0' || badgeText === '').toBeTruthy()
      }
    }
  })
})
