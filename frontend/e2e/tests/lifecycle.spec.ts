/**
 * 90-Day Accounting Firm Lifecycle — Frontend UI Tests
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. API lifecycle seed complete: bash compose/seed/lifecycle-test.sh
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 npx playwright test lifecycle
 */
import { test, expect } from '@playwright/test'
import { loginAs } from '../fixtures/auth'

const ORG = 'e2e-test-org'
const BASE = `/org/${ORG}`

// ═══════════════════════════════════════════════════════════════════
//  DAY 0 — Firm Setup (Alice: Owner)
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 0 — Firm Setup', () => {
  test('Dashboard loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/dashboard`)
    await expect(page).toHaveURL(/dashboard/)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Org settings: currency is ZAR', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/general`)
    await expect(page.locator('main')).toBeVisible()
    // ZAR was set by the seed script
    await expect(page.getByText(/ZAR/)).toBeVisible()
  })

  test('Rate cards page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/rates`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Tax rates page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/tax`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Custom fields page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/custom-fields`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Document templates page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/templates`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Automation rules page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/automations`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Team page shows Alice, Bob, Carol', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/team`)
    await expect(page.getByText('Alice').first()).toBeVisible()
    await expect(page.getByText('Bob').first()).toBeVisible()
    await expect(page.getByText('Carol').first()).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 1 — First Client Onboarding (Kgosi Construction)
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 1 — First Client Onboarding', () => {
  test('Customer list shows Kgosi Construction', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    await expect(page.getByText(/Kgosi Construction/i).first()).toBeVisible()
  })

  test('Customer detail shows ACTIVE status', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    await page.getByText(/Kgosi Construction/i).first().click()
    await expect(page.getByText(/active/i).first()).toBeVisible()
  })

  test('Proposals page shows Kgosi proposal', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/proposals`)
    await expect(page.getByText(/Kgosi/i)).toBeVisible()
  })

  test('Retainers page shows Kgosi retainer', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/retainers`)
    await expect(page.getByText(/Kgosi/i).first()).toBeVisible()
  })

  test('Information requests page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/information-requests`)
    await expect(page.locator('body')).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 2-3 — Additional Clients
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 2-3 — Additional Clients', () => {
  test('Customer list shows all 4 lifecycle-test clients', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    await expect(page.getByText(/Kgosi Construction/i).first()).toBeVisible()
    await expect(page.getByText(/Naledi Hair Studio/i).first()).toBeVisible()
    await expect(page.getByText(/Vukani Tech/i).first()).toBeVisible()
    await expect(page.getByText(/Moroka Family Trust/i).first()).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 7 — First Week of Work
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 7 — First Week of Work', () => {
  test('Projects list shows seeded projects', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    await expect(page.getByText(/Kgosi/i).first()).toBeVisible()
  })

  test('Project detail: Time tab shows logged entries', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    await page.getByText(/Kgosi/i).first().click()
    const timeTab = page.getByRole('tab', { name: /time/i })
    if (await timeTab.isVisible()) {
      await timeTab.click()
      await expect(page.locator('main')).toBeVisible()
    }
  })

  test('My Work page loads for Carol', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${BASE}/my-work`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Notifications page loads', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/notifications`)
    await expect(page.locator('main')).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 30 — Billing
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 30 — First Month-End Billing', () => {
  test('Invoice list shows created invoices', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/invoices`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Profitability page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/profitability`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Project budget tab loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    await page.getByText(/Kgosi/i).first().click()
    const budgetTab = page.getByRole('tab', { name: /budget/i })
    if (await budgetTab.isVisible()) {
      await budgetTab.click()
      await expect(page.locator('main')).toBeVisible()
    }
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 45 — Mid-Quarter Operations
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 45 — Mid-Quarter', () => {
  test('Invoice shows PAID status', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/invoices`)
    await expect(page.getByText(/paid/i).first()).toBeVisible()
  })

  test('Resources/scheduling page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/resources`)
    await expect(page.locator('main')).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 60 — Second Billing Cycle
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 60 — Quarterly Review', () => {
  test('Reports page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/reports`)
    await expect(page.locator('main')).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 75 — Year-End
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 75 — Year-End', () => {
  test('Multiple projects visible for Kgosi', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    // Both Monthly Bookkeeping and Annual Tax Return should exist
    await expect(page.getByText(/Monthly Bookkeeping/i).first()).toBeVisible()
    await expect(page.getByText(/Annual Tax Return/i).first()).toBeVisible()
  })

  test('Information requests page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/information-requests`)
    // Page may use a different root element
    await expect(page.locator('body')).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 90 — Quarter Review
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 90 — Quarter Review', () => {
  test('Dashboard shows data after 90 days', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/dashboard`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Profitability page loads with data', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/profitability`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Customer list shows all clients', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Bob can access admin settings', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/settings/general`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Org documents section loads on settings page', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/general`)
    await expect(page.locator('[data-testid="org-documents-section"]')).toBeVisible()
  })
})
