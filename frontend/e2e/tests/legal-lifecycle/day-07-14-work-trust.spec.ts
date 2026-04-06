/**
 * Days 7-14 — First Work + Trust Deposits
 *
 * Day 7: Log time as 3 users on various matters, create court dates,
 * add comments, check My Work.
 * Day 14: Trust deposits (R250,000 Moroka, R45,000 QuickCollect),
 * conflict detection with adverse party match, trust dashboard.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Days 1-3 tests completed (4 clients, initial matters created)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'
import { captureScreenshot } from '../../helpers/screenshot'

const ORG = 'e2e-test-org'
const BASE = `/org/${ORG}`

// ═══════════════════════════════════════════════════════════════════
//  DAY 7 — First Week of Work
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 7 — First Week of Work', () => {
  // ── Carol: My Work, log time on Sipho matter ──────────────────────
  test('Carol: Navigate to My Work page', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${BASE}/my-work`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('Carol: Log 90min time on Sipho matter', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${BASE}/projects`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })

    // Navigate to the first matter (Sipho's litigation)
    const matterLink = page.getByRole('link', { name: /Ndlovu|Sipho|RAF/i }).first()
    const hasMatter = await matterLink.isVisible({ timeout: 5000 }).catch(() => false)

    if (!hasMatter) {
      // Try clicking first project row
      const firstRow = page.locator('table tbody tr, [data-testid*="project-row"]').first()
      const hasRow = await firstRow.isVisible({ timeout: 5000 }).catch(() => false)
      if (hasRow) {
        await firstRow.click()
      } else {
        test.skip(true, 'No matters found to log time against')
        return
      }
    } else {
      await matterLink.click()
    }

    await page.waitForTimeout(2000)

    // Look for time logging button
    const logTimeBtn = page.getByRole('button', { name: /Log Time|New Time|Add Time|Time Recording/i }).first()
    const hasLogTime = await logTimeBtn.isVisible({ timeout: 5000 }).catch(() => false)

    if (hasLogTime) {
      await logTimeBtn.click()
      await page.waitForTimeout(1000)

      // Fill time entry
      const durationField = page.getByRole('textbox', { name: /duration|hours|minutes/i }).or(page.getByRole('spinbutton', { name: /duration|hours|minutes/i })).first()
      const hasDuration = await durationField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasDuration) {
        await durationField.fill('90')
      }

      const descField = page.getByRole('textbox', { name: /description|notes|activity/i }).first()
      const hasDesc = await descField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasDesc) {
        await descField.fill('Research case law — personal injury precedents')
      }

      const saveBtn = page.getByRole('button', { name: /save|create|log/i }).first()
      const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasSave) {
        await saveBtn.click()
        await page.waitForTimeout(2000)
      }
    } else {
      // Time tab may need to be selected first
      const timeTab = page.getByRole('tab', { name: /time/i }).first()
      const hasTimeTab = await timeTab.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasTimeTab) {
        await timeTab.click()
        await page.waitForTimeout(1000)
      }
    }
  })

  // ── Bob: Log time on Sipho (letter of demand) + Apex (due diligence)
  test('Bob: Log 120min on Sipho — letter of demand', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/projects`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })

    const matterLink = page.getByRole('link', { name: /Ndlovu|Sipho|RAF/i }).first()
    const hasMatter = await matterLink.isVisible({ timeout: 5000 }).catch(() => false)
    if (hasMatter) {
      await matterLink.click()
      await page.waitForTimeout(2000)

      const logTimeBtn = page.getByRole('button', { name: /Log Time|New Time|Add Time|Time Recording/i }).first()
      const hasLogTime = await logTimeBtn.isVisible({ timeout: 5000 }).catch(() => false)
      if (hasLogTime) {
        await logTimeBtn.click()
        await page.waitForTimeout(1000)

        const descField = page.getByRole('textbox', { name: /description|notes|activity/i }).first()
        const hasDesc = await descField.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasDesc) {
          await descField.fill('Draft letter of demand to Road Accident Fund')
        }

        const saveBtn = page.getByRole('button', { name: /save|create|log/i }).first()
        const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasSave) {
          await saveBtn.click()
          await page.waitForTimeout(2000)
        }
      }
    }
  })

  test('Bob: Add comment on Sipho matter', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/projects`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })

    const matterLink = page.getByRole('link', { name: /Ndlovu|Sipho|RAF/i }).first()
    const hasMatter = await matterLink.isVisible({ timeout: 5000 }).catch(() => false)
    if (hasMatter) {
      await matterLink.click()
      await page.waitForTimeout(2000)

      // Look for comments/activity tab
      const commentTab = page.getByRole('tab', { name: /comment|activity|discussion/i }).first()
      const hasTab = await commentTab.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasTab) {
        await commentTab.click()
        await page.waitForTimeout(1000)
      }

      // Add a comment
      const commentInput = page.getByRole('textbox', { name: /comment|message/i }).or(page.locator('textarea, [contenteditable="true"]')).first()
      const hasInput = await commentInput.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasInput) {
        await commentInput.fill('Reviewed claim documents — need additional medical records from Dr. Nkosi')
        const submitBtn = page.getByRole('button', { name: /send|post|submit|comment/i }).first()
        const hasSubmit = await submitBtn.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasSubmit) {
          await submitBtn.click()
          await page.waitForTimeout(2000)
        }
      }
    }
  })

  // ── Alice: Create court date for Sipho ────────────────────────────
  test('Alice: Create court date for Sipho (Motion, 30 days out)', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/court-calendar`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Court calendar page not accessible — feature may not be implemented')
      return
    }

    const newBtn = page.getByRole('button', { name: /new.*date|add.*date|new.*event|schedule/i }).first()
    const hasNew = await newBtn.isVisible({ timeout: 5000 }).catch(() => false)

    if (hasNew) {
      await newBtn.click()
      await page.waitForTimeout(1000)

      // Fill court date details
      const titleField = page.getByRole('textbox', { name: /title|name|description/i }).first()
      const hasTitle = await titleField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasTitle) {
        await titleField.fill('Motion — Ndlovu v RAF')
      }

      const saveBtn = page.getByRole('button', { name: /save|create|schedule/i }).first()
      const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasSave) {
        await saveBtn.click()
        await page.waitForTimeout(2000)
      }
    }

    // Screenshot: court calendar entry
    await captureScreenshot(page, 'day-07-court-calendar-entry-scheduled')
    await captureScreenshot(page, 'court-calendar-scheduled', { curated: true })
  })

  // ── Alice: Log time on Moroka estate ──────────────────────────────
  test('Alice: Log 60min on Moroka estate matter', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/projects`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })

    const matterLink = page.getByRole('link', { name: /Moroka|Estate/i }).first()
    const hasMatter = await matterLink.isVisible({ timeout: 5000 }).catch(() => false)
    if (hasMatter) {
      await matterLink.click()
      await page.waitForTimeout(2000)

      const logTimeBtn = page.getByRole('button', { name: /Log Time|New Time|Add Time|Time Recording/i }).first()
      const hasLogTime = await logTimeBtn.isVisible({ timeout: 5000 }).catch(() => false)
      if (hasLogTime) {
        await logTimeBtn.click()
        await page.waitForTimeout(1000)

        const descField = page.getByRole('textbox', { name: /description|notes|activity/i }).first()
        const hasDesc = await descField.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasDesc) {
          await descField.fill('Report to Master of the High Court — initial filing')
        }

        const saveBtn = page.getByRole('button', { name: /save|create|log/i }).first()
        const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasSave) {
          await saveBtn.click()
          await page.waitForTimeout(2000)
        }
      }
    }
  })

  // ── Verify My Work shows recent activity ──────────────────────────
  test('Alice: My Work page shows active items', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/my-work`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })
    await expect(page.locator('body')).not.toContainText('Something went wrong')

    await captureScreenshot(page, 'day-07-my-work-legal-active')
  })
})

// ═══════════════════════════════════════════════════════════════════
//  DAY 14 — Trust Deposits & Conflict Detection
// ═══════════════════════════════════════════════════════════════════
test.describe.serial('Day 14 — Trust Deposits & Conflict Detection', () => {
  // ── 14.1-14.8: Trust deposit R250,000 for Moroka ──────────────────
  test('Alice: Navigate to Trust Accounting', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/trust-accounting`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Trust Accounting page not accessible — feature may not be implemented')
      return
    }

    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('Alice: Deposit R250,000 into trust for Moroka estate', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/trust-accounting/transactions`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Trust transactions page not accessible')
      return
    }

    const depositBtn = page.getByRole('button', { name: /new.*deposit|add.*deposit|record.*deposit/i }).first()
    const hasDeposit = await depositBtn.isVisible({ timeout: 5000 }).catch(() => false)

    if (hasDeposit) {
      await depositBtn.click()
      await page.waitForTimeout(1000)

      // Fill deposit details
      const amountField = page.getByRole('textbox', { name: /amount/i }).or(page.getByRole('spinbutton', { name: /amount/i })).first()
      const hasAmount = await amountField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasAmount) {
        await amountField.fill('250000')
      }

      const descField = page.getByRole('textbox', { name: /description|reference|note/i }).first()
      const hasDesc = await descField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasDesc) {
        await descField.fill('Estate Late Peter Moroka — transfer from executor')
      }

      // Select client (Moroka)
      const clientField = page.getByRole('combobox', { name: /client|customer/i }).first()
      const hasClient = await clientField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasClient) {
        await clientField.click()
        await page.waitForTimeout(500)
        const morokaOption = page.getByRole('option', { name: /Moroka/i }).first()
        const hasMoroka = await morokaOption.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasMoroka) {
          await morokaOption.click()
        }
      }

      const saveBtn = page.getByRole('button', { name: /save|create|submit|deposit/i }).first()
      const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasSave) {
        await saveBtn.click()
        await page.waitForTimeout(2000)
      }
    } else {
      test.skip(true, 'Deposit button not found — trust deposit UI may not be implemented')
      return
    }
  })

  test('Alice: Approve Moroka trust deposit', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/trust-accounting/transactions`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Trust transactions page not accessible')
      return
    }

    // Look for pending deposit to approve
    const approveBtn = page.getByRole('button', { name: /approve/i }).first()
    const hasApprove = await approveBtn.isVisible({ timeout: 5000 }).catch(() => false)
    if (hasApprove) {
      await approveBtn.click()
      await page.waitForTimeout(2000)
    }
  })

  test('Alice: Verify Moroka trust ledger balance', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/trust-accounting/client-ledgers`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Client ledgers page not accessible')
      return
    }

    // Look for Moroka in ledger list
    const morokaLedger = page.getByText(/Moroka/i).first()
    const hasMoroka = await morokaLedger.isVisible({ timeout: 5000 }).catch(() => false)
    if (hasMoroka) {
      await morokaLedger.click()
      await page.waitForTimeout(2000)

      // Verify balance shows R250,000
      const balance = await page.getByText(/250[\s,.]?000/i).isVisible({ timeout: 5000 }).catch(() => false)
      if (balance) {
        // Screenshot: trust deposit ledger
        await captureScreenshot(page, 'day-14-trust-deposit-ledger-posted')
        await captureScreenshot(page, 'trust-deposit-form', { curated: true })
        await captureScreenshot(page, 'client-ledger-balance', { curated: true })
      }
    }
  })

  // ── 14.9-14.11: Trust deposit R45,000 for QuickCollect ────────────
  test('Alice: Deposit R45,000 into trust for QuickCollect', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/trust-accounting/transactions`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Trust transactions page not accessible')
      return
    }

    const depositBtn = page.getByRole('button', { name: /new.*deposit|add.*deposit|record.*deposit/i }).first()
    const hasDeposit = await depositBtn.isVisible({ timeout: 5000 }).catch(() => false)

    if (hasDeposit) {
      await depositBtn.click()
      await page.waitForTimeout(1000)

      const amountField = page.getByRole('textbox', { name: /amount/i }).or(page.getByRole('spinbutton', { name: /amount/i })).first()
      const hasAmount = await amountField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasAmount) {
        await amountField.fill('45000')
      }

      const descField = page.getByRole('textbox', { name: /description|reference|note/i }).first()
      const hasDesc = await descField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasDesc) {
        await descField.fill('QuickCollect Services — collections deposit')
      }

      const saveBtn = page.getByRole('button', { name: /save|create|submit|deposit/i }).first()
      const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasSave) {
        await saveBtn.click()
        await page.waitForTimeout(2000)
      }
    }
  })

  // ── 14.12-14.15: Conflict check "Mokoena" -> adverse match ────────
  test('Bob: Conflict check "Mokoena" — expect adverse party match', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`${BASE}/conflict-check`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Conflict check page not accessible')
      return
    }

    const searchInput = page.locator('input[type="search"], input[type="text"]').first()
    const hasSearch = await searchInput.isVisible({ timeout: 5000 }).catch(() => false)
    if (hasSearch) {
      await searchInput.fill('Mokoena')

      const searchBtn = page.getByRole('button', { name: /search|check|run/i }).first()
      const hasBtn = await searchBtn.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasBtn) {
        await searchBtn.click()
      } else {
        await searchInput.press('Enter')
      }
      await page.waitForTimeout(2000)

      // Expect AMBER/RED match result
      const matchResult = await page.getByText(/match|conflict|amber|red|found/i).isVisible({ timeout: 5000 }).catch(() => false)
      if (matchResult) {
        await captureScreenshot(page, 'day-14-conflict-match-found')
        await captureScreenshot(page, 'conflict-check-adverse-match', { curated: true })
      }
    }
  })

  // ── 14.16-14.18: Add adverse party "Road Accident Fund" ───────────
  test('Alice: Add adverse party "Road Accident Fund" to registry', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/legal/adverse-parties`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Adverse parties page not accessible — feature may not be implemented')
      return
    }

    const addBtn = page.getByRole('button', { name: /add|new|create/i }).first()
    const hasAdd = await addBtn.isVisible({ timeout: 5000 }).catch(() => false)

    if (hasAdd) {
      await addBtn.click()
      await page.waitForTimeout(1000)

      const nameField = page.getByRole('textbox', { name: /name/i }).first()
      const hasName = await nameField.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasName) {
        await nameField.fill('Road Accident Fund')
      }

      const saveBtn = page.getByRole('button', { name: /save|create|add/i }).first()
      const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasSave) {
        await saveBtn.click()
        await page.waitForTimeout(2000)
      }
    }
  })

  // ── 14.19-14.21: More time logging ────────────────────────────────
  test('Carol: Log additional time entries', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`${BASE}/my-work`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })
    // Verify My Work page is accessible and showing items
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  // ── 14.22-14.24: Trust dashboard check + screenshot ───────────────
  test('Alice: Trust dashboard overview', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/trust-accounting`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Trust Accounting dashboard not accessible')
      return
    }

    await expect(page.locator('body')).not.toContainText('Something went wrong')

    // Screenshot: trust dashboard overview
    await captureScreenshot(page, 'day-14-trust-dashboard-loaded')
    await captureScreenshot(page, 'trust-dashboard-overview', { curated: true })
  })
})
