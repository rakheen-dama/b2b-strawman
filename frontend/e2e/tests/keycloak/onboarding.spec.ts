import { test, expect } from '@playwright/test'
import {
  loginAsPlatformAdmin,
  loginAs,
  registerFromInvite,
} from '../../fixtures/keycloak-auth'
import {
  clearMailbox,
  waitForEmail,
  extractOtp,
  extractInviteLink,
} from '../../helpers/mailpit'

// Test data — see architecture/phase54-keycloak-e2e-test-suite.md §2.7
const OWNER_EMAIL = 'owner@thornton-za.e2e-test.local'
const OWNER_FIRST_NAME = 'Thandi'
const OWNER_LAST_NAME = 'Thornton'
const OWNER_PASSWORD = 'SecureP@ss1'
const ORG_NAME = 'Thornton & Associates'
const ORG_COUNTRY = 'South Africa'
const ORG_INDUSTRY = 'Accounting'

const BASE_HOST = new URL(
  process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000'
).host

// Task 399.5 verification note:
// KEYCLOAK_AUTH_SERVER_URL defaults to http://localhost:8180 in application-keycloak.yml.
// docker-compose.yml does NOT override it for the backend service.
// Therefore Keycloak invite action tokens use localhost:8180 — reachable from Playwright host.
// No fix required.

test.describe.serial('Accounting firm onboarding', () => {
  test.beforeAll(async () => {
    // Step 1: Clear Mailpit for a clean slate
    await clearMailbox()
  })

  test('Step 2: Submit access request form', async ({ page }) => {
    await page.goto('/request-access')

    // Wait for form to be ready
    await page.waitForSelector('[data-testid="request-access-form"]')

    // Fill email
    await page.fill('[data-testid="email-input"]', OWNER_EMAIL)

    // Fill full name
    await page.fill(
      '[data-testid="full-name-input"]',
      `${OWNER_FIRST_NAME} ${OWNER_LAST_NAME}`
    )

    // Fill org name
    await page.fill('[data-testid="org-name-input"]', ORG_NAME)

    // Select country — Shadcn Select triggers a listbox; use click + option select
    await page.click('[data-testid="country-select"]')
    await page.getByRole('option', { name: ORG_COUNTRY }).click()

    // Select industry
    await page.click('[data-testid="industry-select"]')
    await page.getByRole('option', { name: ORG_INDUSTRY }).click()

    // Submit
    await page.click('[data-testid="submit-request-btn"]')

    // Assert OTP step is shown (step transitions to 2)
    await expect(page.getByText('Check Your Email')).toBeVisible({
      timeout: 15_000,
    })
  })

  test('Step 3: Enter OTP and verify', async ({ page }) => {
    // Each test() gets a fresh page fixture — re-fill the form to get back to step 2
    await page.goto('/request-access')
    await page.waitForSelector('[data-testid="request-access-form"]')
    await page.fill('[data-testid="email-input"]', OWNER_EMAIL)
    await page.fill(
      '[data-testid="full-name-input"]',
      `${OWNER_FIRST_NAME} ${OWNER_LAST_NAME}`
    )
    await page.fill('[data-testid="org-name-input"]', ORG_NAME)
    await page.click('[data-testid="country-select"]')
    await page.getByRole('option', { name: ORG_COUNTRY }).click()
    await page.click('[data-testid="industry-select"]')
    await page.getByRole('option', { name: ORG_INDUSTRY }).click()
    await page.click('[data-testid="submit-request-btn"]')
    await expect(page.getByText('Check Your Email')).toBeVisible({
      timeout: 15_000,
    })

    // Wait for OTP email
    const otpEmail = await waitForEmail(OWNER_EMAIL, {
      timeout: 30_000,
    })
    const otp = extractOtp(otpEmail)

    // Enter OTP
    await page.fill('[data-testid="otp-input"]', otp)
    await page.click('[data-testid="verify-otp-btn"]')

    // Assert success state
    await expect(page.getByTestId('success-message')).toBeVisible({
      timeout: 15_000,
    })
  })

  test('Step 4-5: Platform admin approves request', async ({ page }) => {
    // Login as platform admin
    await loginAsPlatformAdmin(page)

    // Navigate to access requests page
    await page.goto('/platform-admin/access-requests')
    await expect(page.getByTestId('access-requests-page')).toBeVisible({
      timeout: 15_000,
    })

    // Switch to Pending tab
    await page.click('[data-testid="pending-tab"]')

    // Find and click approve on the Thornton row
    const orgRow = page.getByTestId(`request-row-${ORG_NAME}`)
    await expect(orgRow).toBeVisible({ timeout: 10_000 })
    await orgRow.getByTestId('approve-btn').click()

    // Confirm in dialog
    await expect(page.getByTestId('confirm-approve-btn')).toBeVisible({
      timeout: 5_000,
    })
    await page.click('[data-testid="confirm-approve-btn"]')

    // Assert dialog closes (no provisioning error shown)
    await expect(page.getByTestId('confirm-approve-btn')).not.toBeVisible({
      timeout: 30_000,
    })
  })

  test('Step 6-7: Owner registers via invite link', async ({ page }) => {
    // Wait for invite email
    const inviteEmail = await waitForEmail(OWNER_EMAIL, {
      subject: 'invite',
      timeout: 60_000, // Provisioning + Keycloak invite can take up to 60s
    })
    const inviteLink = extractInviteLink(inviteEmail)

    // Register via invite link
    await registerFromInvite(
      page,
      inviteLink,
      OWNER_FIRST_NAME,
      OWNER_LAST_NAME,
      OWNER_EMAIL,
      OWNER_PASSWORD
    )

    // Step 8: Assert dashboard loads with org slug in URL
    await expect(page).toHaveURL(new RegExp(BASE_HOST), { timeout: 30_000 })
    // The redirect should land on an org-scoped dashboard
    await expect(page).toHaveURL(/\/org\/[^/]+\/dashboard/, {
      timeout: 15_000,
    })
  })

  test('Step 8: Verify owner listed in Team page', async ({ page }) => {
    // Login as owner (session from registerFromInvite is lost across test() blocks)
    await loginAs(page, OWNER_EMAIL, OWNER_PASSWORD)

    // Navigate to the team page — URL contains the org slug
    // We don't hardcode the slug; navigate from dashboard
    await page.waitForURL(/\/org\/([^/]+)\/dashboard/, { timeout: 15_000 })
    const url = page.url()
    const slugMatch = url.match(/\/org\/([^/]+)\//)
    const orgSlug = slugMatch?.[1]
    expect(orgSlug).toBeTruthy()

    await page.goto(`/org/${orgSlug}/team`)

    // Assert Thandi Thornton appears with Owner role
    const ownerRow = page.getByTestId(`member-row-${OWNER_EMAIL}`)
    await expect(ownerRow).toBeVisible({ timeout: 15_000 })
    await expect(ownerRow).toContainText('Owner')
  })
})
