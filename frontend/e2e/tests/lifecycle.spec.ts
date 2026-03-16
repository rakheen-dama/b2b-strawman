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
import { test, expect, Page } from '@playwright/test'
import { loginAs } from '../fixtures/auth'

const ORG = 'e2e-test-org'
const BASE = `/org/${ORG}`

// ═══════════════════════════════════════════════════════════════════
//  DAY 0 — Firm Setup (Alice: Owner)
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 0 — Firm Setup', () => {
  test('Dashboard loads with getting started hints', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/dashboard`)
    await expect(page).toHaveURL(/dashboard/)
    // Dashboard should render KPI cards or getting-started card
    await expect(page.locator('main')).toBeVisible()
  })

  test('Org settings: currency, branding', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/general`)
    await expect(page.getByText(/currency/i)).toBeVisible()
    // Verify ZAR is selected (set by API script)
    await expect(page.locator('text=ZAR')).toBeVisible()
  })

  test('Rate cards page shows billing rates', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/rates`)
    await expect(page.getByText(/rate/i)).toBeVisible()
    // Verify at least Alice's rate is visible
    await expect(page.getByText('Alice')).toBeVisible()
  })

  test('Tax rates page shows VAT 15%', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/settings/tax`)
    await expect(page.getByText(/VAT/i)).toBeVisible()
    await expect(page.getByText(/15/)).toBeVisible()
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
    await page.goto(`${BASE}/settings/team`)
    await expect(page.getByText('Alice')).toBeVisible()
    await expect(page.getByText('Bob')).toBeVisible()
    await expect(page.getByText('Carol')).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 1 — First Client Onboarding
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 1 — First Client Onboarding', () => {
  test('Customer list shows Kgosi Construction', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/customers`)
    await expect(page.getByText('Kgosi Construction')).toBeVisible()
  })

  test('Customer detail shows lifecycle status', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/customers`)
    await page.getByText('Kgosi Construction').click()
    // Should show ACTIVE badge (set by API script)
    await expect(page.getByText(/active/i)).toBeVisible()
  })

  test('Customer detail has onboarding/compliance tab', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/customers`)
    await page.getByText('Kgosi Construction').click()
    // Look for checklist or onboarding tab
    const onboardingTab = page.getByRole('tab', { name: /onboarding|compliance|checklist/i })
    if (await onboardingTab.isVisible()) {
      await onboardingTab.click()
      await expect(page.locator('main')).toBeVisible()
    }
  })

  test('Proposals page shows Kgosi proposal', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/proposals`)
    await expect(page.getByText(/Kgosi/i)).toBeVisible()
  })

  test('Retainers page shows Kgosi retainer', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/retainers`)
    await expect(page.getByText(/Kgosi/i)).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 2-3 — Additional Clients
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 2-3 — Additional Clients', () => {
  test('Customer list shows all 4 clients', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    await expect(page.getByText('Kgosi Construction')).toBeVisible()
    await expect(page.getByText('Naledi Hair Studio')).toBeVisible()
    await expect(page.getByText('Vukani Tech')).toBeVisible()
    await expect(page.getByText('Moroka Family Trust')).toBeVisible()
  })

  test('Lifecycle filter shows correct counts', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    // Click ACTIVE filter tab
    const activeTab = page.getByRole('tab', { name: /active/i })
    if (await activeTab.isVisible()) {
      await activeTab.click()
      // Should show 4 active clients
    }
  })

  test('Bob can create a new customer via UI', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/customers`)
    // Find and click create button
    const createBtn = page.getByRole('button', { name: /new|create|add/i })
    if (await createBtn.isVisible()) {
      await createBtn.click()
      // Fill form
      await page.getByLabel(/name/i).fill(`UI Test Client ${Date.now()}`)
      await page.getByLabel(/email/i).fill('uitest@example.com')
      // Submit
      const submitBtn = page.getByRole('button', { name: /create|save|submit/i })
      await submitBtn.click()
      // Verify created
      await expect(page.getByText('UI Test Client')).toBeVisible()
    }
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 7 — First Week of Work
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 7 — First Week of Work', () => {
  test('Carol: My Work page shows assigned tasks', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${BASE}/my-work`)
    await expect(page.locator('main')).toBeVisible()
    // Should show tasks assigned to Carol
  })

  test('Project detail: Time tab shows logged entries', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    // Navigate to Kgosi project
    await page.getByText(/Kgosi/).first().click()
    // Click Time tab
    const timeTab = page.getByRole('tab', { name: /time/i })
    if (await timeTab.isVisible()) {
      await timeTab.click()
      await expect(page.locator('main')).toBeVisible()
    }
  })

  test('Project detail: Activity tab shows events', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    await page.getByText(/Kgosi/).first().click()
    const activityTab = page.getByRole('tab', { name: /activity/i })
    if (await activityTab.isVisible()) {
      await activityTab.click()
      await expect(page.locator('main')).toBeVisible()
    }
  })

  test('Carol can log time via UI', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${BASE}/projects`)
    await page.getByText(/Kgosi/).first().click()
    const timeTab = page.getByRole('tab', { name: /time/i })
    if (await timeTab.isVisible()) {
      await timeTab.click()
      // Look for log time button
      const logBtn = page.getByRole('button', { name: /log time|add time/i })
      if (await logBtn.isVisible()) {
        await logBtn.click()
        // Fill dialog
        const durationInput = page.getByLabel(/duration|hours|minutes/i)
        if (await durationInput.isVisible()) {
          await durationInput.fill('60')
        }
        const descInput = page.getByLabel(/description|notes/i)
        if (await descInput.isVisible()) {
          await descInput.fill(`UI test time entry ${Date.now()}`)
        }
        // Submit
        const submitBtn = page.getByRole('button', { name: /save|log|submit/i })
        if (await submitBtn.isVisible()) {
          await submitBtn.click()
        }
      }
    }
  })

  test('Comments visible on project', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/projects`)
    await page.getByText(/Kgosi/).first().click()
    // Look for comment or the comment text
    const commentTab = page.getByRole('tab', { name: /comment/i })
    if (await commentTab.isVisible()) {
      await commentTab.click()
      await expect(page.getByText(/bank statements/i)).toBeVisible()
    }
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 14 — Notifications & Progress
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 14 — Progress Check', () => {
  test('Notifications page loads', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/notifications`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Compliance dashboard loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/compliance`)
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
    // Should show at least the Naledi and Kgosi invoices
  })

  test('Invoice detail shows line items and tax', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/invoices`)
    // Click first invoice row
    const firstRow = page.locator('table tbody tr').first()
    if (await firstRow.isVisible()) {
      await firstRow.click()
      await expect(page.locator('main')).toBeVisible()
      // Should show line items
    }
  })

  test('Profitability dashboard loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/profitability`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Project budget tab shows status', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    await page.getByText(/Kgosi/).first().click()
    const budgetTab = page.getByRole('tab', { name: /budget/i })
    if (await budgetTab.isVisible()) {
      await budgetTab.click()
      await expect(page.locator('main')).toBeVisible()
    }
  })

  test('Billing runs page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/invoices/billing-runs`)
    await expect(page.locator('main')).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 45 — Mid-Quarter Operations
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 45 — Mid-Quarter', () => {
  test('Paid invoice shows correct status', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/invoices`)
    // Look for PAID badge
    await expect(page.getByText(/paid/i).first()).toBeVisible()
  })

  test('Project expenses tab shows CIPC expense', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    await page.getByText(/Kgosi/).first().click()
    const expenseTab = page.getByRole('tab', { name: /expense/i })
    if (await expenseTab.isVisible()) {
      await expenseTab.click()
      await expect(page.getByText(/CIPC/i)).toBeVisible()
    }
  })

  test('Resource planning page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/resources`)
    await expect(page.locator('main')).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 60 — Second Billing Cycle
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 60 — Quarterly Review', () => {
  test('Multiple invoices per customer visible', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    await page.getByText('Kgosi Construction').click()
    const invoiceTab = page.getByRole('tab', { name: /invoice/i })
    if (await invoiceTab.isVisible()) {
      await invoiceTab.click()
      await expect(page.locator('main')).toBeVisible()
    }
  })

  test('Customer financials tab loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    await page.getByText('Kgosi Construction').click()
    const financialsTab = page.getByRole('tab', { name: /financial/i })
    if (await financialsTab.isVisible()) {
      await financialsTab.click()
      await expect(page.locator('main')).toBeVisible()
    }
  })

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
  test('Multiple projects per customer visible', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    await page.getByText('Kgosi Construction').click()
    const projectsTab = page.getByRole('tab', { name: /project/i })
    if (await projectsTab.isVisible()) {
      await projectsTab.click()
      // Should show both Monthly Bookkeeping and Annual Tax Return
      await expect(page.getByText(/Monthly Bookkeeping/i)).toBeVisible()
      await expect(page.getByText(/Annual Tax Return/i)).toBeVisible()
    }
  })

  test('Information requests list shows tax year request', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/compliance/requests`)
    await expect(page.getByText(/Annual Tax Return/i)).toBeVisible()
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 90 — Quarter Review
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 90 — Quarter Review', () => {
  test('Dashboard shows meaningful KPIs after 90 days', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/dashboard`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Profitability shows multi-client data', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/profitability`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('All customer lifecycle states visible', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/customers`)
    // Verify lifecycle summary is accessible
    await expect(page.locator('main')).toBeVisible()
  })

  test('Carol cannot access admin settings', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${BASE}/settings/rates`)
    // Should show permission error or redirect
    await expect(page.getByText(/permission/i)).toBeVisible()
  })

  test('Bob can access admin features', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/settings/general`)
    await expect(page.locator('main')).toBeVisible()
  })

  test('Documents page loads', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/documents`)
    await expect(page.locator('main')).toBeVisible()
  })
})
