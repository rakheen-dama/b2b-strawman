import { test, expect } from '@playwright/test'
import {
  loginAs,
  loginAsPlatformAdmin,
  registerFromInvite,
} from '../../fixtures/keycloak-auth'
import {
  clearMailbox,
  waitForEmail,
  extractOtp,
  extractInviteLink,
} from '../../helpers/mailpit'
import { saveState } from '../../helpers/e2e-state'

// Unique run ID to avoid collisions between test runs
const RUN_ID = Date.now().toString(36).slice(-5)
const OWNER_EMAIL = `owner-${RUN_ID}@thornton-za.e2e-test.local`
const ORG_NAME = `Thornton ${RUN_ID}`

test.describe.serial(
  'Keycloak Onboarding: Access Request → Approval → Registration',
  () => {
    test.beforeAll(async () => {
      await clearMailbox()
    })

    test('1. Submit access request and verify OTP', async ({ page }) => {
      // Combined into one test to keep same page session —
      // splitting would require re-submitting the form (fresh page per test),
      // which risks creating duplicate AccessRequest rows.

      await page.goto('/request-access')

      // Fill the access request form
      await page.getByTestId('email-input').fill(OWNER_EMAIL)
      await page.getByTestId('full-name-input').fill('Thandi Thornton')
      await page.getByTestId('org-name-input').fill(ORG_NAME)

      // Select country — Shadcn Select renders via Radix portal
      await page.getByTestId('country-select').click()
      await page.getByRole('option', { name: 'South Africa' }).click()

      // Select industry
      await page.getByTestId('industry-select').click()
      await page.getByRole('option', { name: 'Accounting' }).click()

      // Submit
      await page.getByTestId('submit-request-btn').click()

      // Should advance to OTP step
      await expect(page.getByTestId('otp-input')).toBeVisible({
        timeout: 10_000,
      })

      // Wait for OTP email to arrive in Mailpit
      const email = await waitForEmail(OWNER_EMAIL, { timeout: 15_000 })
      const otp = extractOtp(email)
      expect(otp).toMatch(/^\d{6}$/)

      // Enter OTP on the same page
      await page.getByTestId('otp-input').fill(otp)
      await page.getByTestId('verify-otp-btn').click()

      // Should show success message
      await expect(page.getByTestId('success-message')).toBeVisible({
        timeout: 10_000,
      })
    })

    test('2. Platform admin approves request', async ({ page }) => {
      await loginAsPlatformAdmin(page)

      await page.goto('/platform-admin/access-requests')
      await page.waitForLoadState('networkidle')

      // Verify org appears in the pending list
      await expect(page.getByText(ORG_NAME)).toBeVisible({ timeout: 10_000 })

      // Click Approve — use data-testid for precision
      const row = page.getByTestId(`request-row-${ORG_NAME}`)
      await row.getByTestId('approve-btn').click()

      // Confirm in the AlertDialog
      await expect(page.getByRole('alertdialog')).toBeVisible({
        timeout: 5_000,
      })
      await page.getByTestId('confirm-approve-btn').click()

      // Wait for dialog to close and status to update
      await expect(page.getByRole('alertdialog')).not.toBeVisible({
        timeout: 15_000,
      })

      // Verify status changed to Approved
      await expect(page.getByText(/approved/i)).toBeVisible({
        timeout: 10_000,
      })
    })

    test('3. Owner receives invitation and registers', async ({ page }) => {
      // Wait for Keycloak invitation email
      const email = await waitForEmail(OWNER_EMAIL, {
        subject: 'invitation',
        timeout: 30_000,
      })
      const inviteLink = extractInviteLink(email)
      expect(inviteLink).toBeTruthy()

      // Complete registration via Keycloak
      await registerFromInvite(
        page,
        inviteLink,
        'Thandi',
        'Thornton',
        'SecureP@ss1'
      )

      // Should be redirected to the app after registration
      await page.waitForLoadState('networkidle')

      // Verify we land somewhere authenticated
      await expect(page.locator('body')).not.toContainText('Sign in')
    })

    test('4. Owner can login and sees dashboard', async ({ page }) => {
      await loginAs(page, OWNER_EMAIL, 'SecureP@ss1')

      await page.waitForLoadState('networkidle')

      // Verify authenticated
      await expect(page.locator('body')).not.toContainText('Sign in')
      // Verify we're on an org-scoped page
      await expect(page).toHaveURL(/\/org\//)

      // Extract org slug and save state for downstream tests
      const url = page.url()
      const slugMatch = url.match(/\/org\/([^/]+)/)
      const orgSlug = slugMatch?.[1] ?? ''
      expect(orgSlug).toBeTruthy()

      saveState({
        runId: RUN_ID,
        ownerEmail: OWNER_EMAIL,
        ownerPassword: 'SecureP@ss1',
        orgSlug,
      })
    })
  }
)
