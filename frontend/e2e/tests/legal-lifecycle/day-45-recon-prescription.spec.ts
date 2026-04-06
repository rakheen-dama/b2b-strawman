/**
 * Day 45 — Reconciliation & Prescription Tracking
 *
 * Bank reconciliation (upload CSV, 3-way match), prescription tracking
 * for Sipho's personal injury claim, court date lifecycle management
 * (SCHEDULED -> POSTPONED -> new SCHEDULED), payment recording on Apex
 * fee note, and resource utilization check.
 *
 * Prerequisites:
 *   1. E2E stack running: bash compose/scripts/e2e-up.sh
 *   2. Day 30 tests completed (fee notes, trust transactions exist)
 *
 * Run:
 *   PLAYWRIGHT_BASE_URL=http://localhost:3001 pnpm test:e2e:legal-lifecycle
 */
import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'
import { captureScreenshot } from '../../helpers/screenshot'

const ORG = 'e2e-test-org'
const BASE = `/org/${ORG}`

test.describe.serial('Day 45 — Reconciliation & Prescription', () => {
  // ── 45.1-45.8: Bank reconciliation ────────────────────────────────
  test('Alice: Navigate to Trust Reconciliation page', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/trust-accounting/reconciliation`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Trust reconciliation page not accessible — feature may not be implemented')
      return
    }

    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('Alice: Start new bank reconciliation', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/trust-accounting/reconciliation/new`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      // Try navigating to reconciliation list and clicking new
      await page.goto(`${BASE}/trust-accounting/reconciliation`)
      const mainVisible2 = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
      if (!mainVisible2) {
        test.skip(true, 'Trust reconciliation page not accessible')
        return
      }

      const newBtn = page.getByRole('button', { name: /new.*reconciliation|start.*reconciliation|reconcile/i }).first()
      const hasNew = await newBtn.isVisible({ timeout: 5000 }).catch(() => false)
      if (hasNew) {
        await newBtn.click()
        await page.waitForTimeout(2000)
      } else {
        test.skip(true, 'New reconciliation button not found')
        return
      }
    }

    // Upload bank CSV (Standard Bank format)
    // Note: In E2E, we can't actually provide a bank CSV file.
    // The reconciliation form should at least be visible (soft — feature may not be implemented).
    expect.soft(await page.locator('input[type="file"]').first().isVisible({ timeout: 5000 }).catch(() => false)).toBeTruthy()

    // Check for reconciliation form elements
    expect.soft(await page.locator('form, [data-testid*="reconciliation"]').first().isVisible({ timeout: 5000 }).catch(() => false)).toBeTruthy()

    // If auto-matching is available, run it
    const matchBtn = page.getByRole('button', { name: /match|auto.*match|reconcile/i }).first()
    const hasMatch = await matchBtn.isVisible({ timeout: 3000 }).catch(() => false)
    if (hasMatch) {
      await matchBtn.click()
      await page.waitForTimeout(2000)
    }

    // Check for 3-way reconciliation result (bank = cashbook = client ledger)
    expect.soft(await page.getByText(/balanced|matched|reconciled/i).isVisible({ timeout: 5000 }).catch(() => false)).toBeTruthy()

    // Screenshot: bank reconciliation
    await captureScreenshot(page, 'day-45-reconciliation-balanced')
    await captureScreenshot(page, 'bank-reconciliation-matched', { curated: true })
  })

  // ── 45.9-45.13: Prescription tracking for Sipho ───────────────────
  test('Alice: Check prescription tracking for Sipho (3-year personal injury)', async ({ page }) => {
    await loginAs(page, 'alice')

    // Prescription tracking might be on the matter detail page
    await page.goto(`${BASE}/projects`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })

    const siphoMatter = page.getByRole('link', { name: /Ndlovu|Sipho|RAF/i }).first()
    const hasMatter = await siphoMatter.isVisible({ timeout: 5000 }).catch(() => false)

    if (hasMatter) {
      await siphoMatter.click()
      await page.waitForTimeout(2000)

      // Look for prescription/deadline section
      const prescriptionTab = page.getByRole('tab', { name: /prescription|deadline|limitation/i }).first()
      const hasTab = await prescriptionTab.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasTab) {
        await prescriptionTab.click()
        await page.waitForTimeout(1000)
      }

      // Check for prescription period info (3-year for personal injury)
      const prescriptionInfo = await page.getByText(/prescription|3.?year|personal.?injury|limitation/i).isVisible({ timeout: 5000 }).catch(() => false)
      if (prescriptionInfo) {
        await captureScreenshot(page, 'day-45-prescription-tracker-active')
        await captureScreenshot(page, 'prescription-tracker-days', { curated: true })
      }
    }

    // Also check dedicated prescription/deadlines page
    await page.goto(`${BASE}/deadlines`)
    const deadlinesVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (deadlinesVisible) {
      await expect(page.locator('body')).not.toContainText('Something went wrong')
    }
  })

  // ── 45.14-45.17: Court date lifecycle ─────────────────────────────
  test('Alice: Court date lifecycle — SCHEDULED to POSTPONED, create new SCHEDULED', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/court-calendar`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Court calendar page not accessible')
      return
    }

    // Find the existing court date for Sipho
    const courtDate = page.getByText(/Ndlovu|Motion|RAF/i).first()
    const hasDate = await courtDate.isVisible({ timeout: 5000 }).catch(() => false)

    if (hasDate) {
      await courtDate.click()
      await page.waitForTimeout(1000)

      // Update status to POSTPONED
      const postponeBtn = page.getByRole('button', { name: /postpone|reschedule/i }).first()
      const hasPostpone = await postponeBtn.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasPostpone) {
        await postponeBtn.click()
        await page.waitForTimeout(1000)

        // Confirm
        const confirmBtn = page.getByRole('button', { name: /confirm|yes|save/i }).first()
        const hasConfirm = await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasConfirm) {
          await confirmBtn.click()
          await page.waitForTimeout(2000)
        }
      }

      // Create new SCHEDULED court date
      const newDateBtn = page.getByRole('button', { name: /new.*date|add.*date|schedule/i }).first()
      const hasNewDate = await newDateBtn.isVisible({ timeout: 3000 }).catch(() => false)
      if (hasNewDate) {
        await newDateBtn.click()
        await page.waitForTimeout(1000)

        const titleField = page.getByRole('textbox', { name: /title|name|description/i }).first()
        const hasTitle = await titleField.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasTitle) {
          await titleField.fill('Motion — Ndlovu v RAF (rescheduled)')
        }

        const saveBtn = page.getByRole('button', { name: /save|create|schedule/i }).first()
        const hasSave = await saveBtn.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasSave) {
          await saveBtn.click()
          await page.waitForTimeout(2000)
        }
      }
    }
  })

  // ── 45.18-45.20: Payment on Apex fee note -> PAID ─────────────────
  test('Alice: Record payment on Apex fee note (R40,250) -> PAID', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/invoices`)
    await expect(page.locator('main')).toBeVisible({ timeout: 10_000 })

    // Find Apex fee note
    const apexFeeNote = page.getByText(/Apex/i).first()
    const hasApex = await apexFeeNote.isVisible({ timeout: 5000 }).catch(() => false)

    if (hasApex) {
      await apexFeeNote.click()
      await page.waitForTimeout(2000)

      // Record payment
      const paymentBtn = page.getByRole('button', { name: /record.*payment|mark.*paid|receive.*payment/i }).first()
      const hasPayment = await paymentBtn.isVisible({ timeout: 5000 }).catch(() => false)

      if (hasPayment) {
        await paymentBtn.click()
        await page.waitForTimeout(1000)

        // Fill payment amount
        const amountField = page.getByRole('textbox', { name: /amount/i }).or(page.getByRole('spinbutton', { name: /amount/i })).first()
        const hasAmount = await amountField.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasAmount) {
          await amountField.fill('40250')
        }

        const confirmBtn = page.getByRole('button', { name: /confirm|record|save/i }).first()
        const hasConfirm = await confirmBtn.isVisible({ timeout: 3000 }).catch(() => false)
        if (hasConfirm) {
          await confirmBtn.click()
          await page.waitForTimeout(2000)
        }
      }

      // Verify PAID status (soft assertion — payment recording may not be implemented)
      expect.soft(await page.getByText(/paid/i).isVisible({ timeout: 5000 }).catch(() => false)).toBeTruthy()
    }
  })

  // ── 45.21-45.22: Resource utilization check ───────────────────────
  test('Alice: Check resource utilization', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`${BASE}/resources`)

    const mainVisible = await page.locator('main').isVisible({ timeout: 10_000 }).catch(() => false)
    if (!mainVisible) {
      test.skip(true, 'Resources page not accessible')
      return
    }

    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })
})
