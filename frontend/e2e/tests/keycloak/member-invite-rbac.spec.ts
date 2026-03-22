import { test, expect } from '@playwright/test'
import {
  loginAs,
  registerFromInvite,
} from '../../fixtures/keycloak-auth'
import {
  clearMailbox,
  waitForEmail,
  extractInviteLink,
} from '../../helpers/mailpit'

// --- Test data (must match onboarding.spec.ts) ---
const OWNER_EMAIL = 'owner@thornton-za.e2e-test.local'
const OWNER_PASSWORD = 'SecureP@ss1'

const BOB_EMAIL = 'bob@thornton-za.e2e-test.local'
const BOB_FIRST_NAME = 'Bob'
const BOB_LAST_NAME = 'Ndlovu'
const BOB_PASSWORD = 'SecureP@ss2'

const CAROL_EMAIL = 'carol@thornton-za.e2e-test.local'
const CAROL_FIRST_NAME = 'Carol'
const CAROL_LAST_NAME = 'Mokoena'
const CAROL_PASSWORD = 'SecureP@ss3'

// --- Helper: login and extract org slug ---
async function loginAndGetSlug(
  page: Parameters<typeof loginAs>[0],
  email: string,
  password: string
): Promise<string> {
  await loginAs(page, email, password)
  await page.waitForURL(/\/org\/([^/]+)\/dashboard/, { timeout: 15_000 })
  const url = page.url()
  const match = url.match(/\/org\/([^/]+)\//)
  if (!match?.[1]) throw new Error(`Could not extract org slug from URL: ${url}`)
  return match[1]
}

test.describe.serial('Member invite and RBAC', () => {
  test.beforeAll(async () => {
    await clearMailbox()
  })

  // === INVITE BOB (Admin) ===
  test('Invite Bob as Admin', async ({ page }) => {
    const orgSlug = await loginAndGetSlug(page, OWNER_EMAIL, OWNER_PASSWORD)
    await page.goto(`/org/${orgSlug}/team`)

    // Fill invite form
    await page.getByTestId('invite-email-input').fill(BOB_EMAIL)

    // Select Admin role — click the select trigger, then pick Admin option
    await page.getByTestId('role-select').click()
    await page.getByRole('option', { name: 'Admin' }).click()

    // Submit invite
    await page.getByTestId('invite-member-btn').click()

    // Assert success message
    await expect(page.getByText(/invitation sent/i)).toBeVisible({ timeout: 15_000 })
  })

  test('Bob registers via invite link', async ({ page }) => {
    // Wait for Bob's invite email
    const inviteEmail = await waitForEmail(BOB_EMAIL, { timeout: 60_000 })
    const inviteLink = extractInviteLink(inviteEmail)

    // Register Bob
    await registerFromInvite(page, inviteLink, BOB_FIRST_NAME, BOB_LAST_NAME, BOB_EMAIL, BOB_PASSWORD)

    // Verify redirect to org dashboard
    await expect(page).toHaveURL(/\/org\/[^/]+\/dashboard/, { timeout: 30_000 })
  })

  test('Bob appears in team list with Admin role', async ({ page }) => {
    const slug = await loginAndGetSlug(page, OWNER_EMAIL, OWNER_PASSWORD)
    await page.goto(`/org/${slug}/team`)

    const bobRow = page.getByTestId(`member-row-${BOB_EMAIL}`)
    await expect(bobRow).toBeVisible({ timeout: 15_000 })
    await expect(bobRow.getByTestId('member-role-badge')).toContainText('Admin')
  })

  // === INVITE CAROL (Member) ===
  test('Invite Carol as Member', async ({ page }) => {
    const slug = await loginAndGetSlug(page, OWNER_EMAIL, OWNER_PASSWORD)
    await page.goto(`/org/${slug}/team`)

    await page.getByTestId('invite-email-input').fill(CAROL_EMAIL)

    // Member is the default role, but explicitly select it
    await page.getByTestId('role-select').click()
    await page.getByRole('option', { name: 'Member' }).click()

    await page.getByTestId('invite-member-btn').click()
    await expect(page.getByText(/invitation sent/i)).toBeVisible({ timeout: 15_000 })
  })

  test('Carol registers via invite link', async ({ page }) => {
    const inviteEmail = await waitForEmail(CAROL_EMAIL, { timeout: 60_000 })
    const inviteLink = extractInviteLink(inviteEmail)

    await registerFromInvite(page, inviteLink, CAROL_FIRST_NAME, CAROL_LAST_NAME, CAROL_EMAIL, CAROL_PASSWORD)
    await expect(page).toHaveURL(/\/org\/[^/]+\/dashboard/, { timeout: 30_000 })
  })

  test('Carol appears in team list with Member role', async ({ page }) => {
    const slug = await loginAndGetSlug(page, OWNER_EMAIL, OWNER_PASSWORD)
    await page.goto(`/org/${slug}/team`)

    const carolRow = page.getByTestId(`member-row-${CAROL_EMAIL}`)
    await expect(carolRow).toBeVisible({ timeout: 15_000 })
    await expect(carolRow.getByTestId('member-role-badge')).toContainText('Member')
  })

  // === RBAC: BOB (Admin) ===
  test('RBAC: Bob (Admin) can access admin pages', async ({ browser }) => {
    const bobContext = await browser.newContext()
    const bobPage = await bobContext.newPage()

    const slug = await loginAndGetSlug(bobPage, BOB_EMAIL, BOB_PASSWORD)

    // Admin can access dashboard
    await bobPage.goto(`/org/${slug}/dashboard`)
    await expect(bobPage.locator('h1')).toBeVisible({ timeout: 10_000 })

    // Admin can access Settings > General (full admin view with form)
    await bobPage.goto(`/org/${slug}/settings/general`)
    await expect(bobPage.getByText('General')).toBeVisible({ timeout: 10_000 })
    // Admin sees the full settings form — look for currency selector which is only in admin view
    await expect(bobPage.getByTestId('default-currency')).toBeVisible({ timeout: 10_000 })

    // Admin can access Customers
    await bobPage.goto(`/org/${slug}/customers`)
    await expect(bobPage.locator('h1')).toBeVisible({ timeout: 10_000 })

    // Admin can access Projects
    await bobPage.goto(`/org/${slug}/projects`)
    await expect(bobPage.locator('h1')).toBeVisible({ timeout: 10_000 })

    // Admin can access Invoices
    await bobPage.goto(`/org/${slug}/invoices`)
    await expect(bobPage.locator('h1')).toBeVisible({ timeout: 10_000 })

    await bobContext.close()
  })

  // === RBAC: CAROL (Member) ===
  test('RBAC: Carol (Member) has restricted access', async ({ browser }) => {
    const carolContext = await browser.newContext()
    const carolPage = await carolContext.newPage()

    const slug = await loginAndGetSlug(carolPage, CAROL_EMAIL, CAROL_PASSWORD)

    // Member can access dashboard
    await carolPage.goto(`/org/${slug}/dashboard`)
    await expect(carolPage.locator('h1')).toBeVisible({ timeout: 10_000 })

    // Member can access My Work
    await carolPage.goto(`/org/${slug}/my-work`)
    await expect(carolPage.locator('h1')).toBeVisible({ timeout: 10_000 })

    // Member CANNOT access Settings > General fully (restricted view — no form)
    await carolPage.goto(`/org/${slug}/settings/general`)
    // Wait for page to render before checking negative assertion
    await expect(carolPage.getByText('General')).toBeVisible({ timeout: 10_000 })
    // Non-admin sees restricted view — GeneralSettingsForm is NOT rendered
    // The default-currency testid only appears in admin view
    await expect(carolPage.getByTestId('default-currency')).not.toBeVisible({ timeout: 5_000 })

    // Member CANNOT access Settings > Rates (permission denied message)
    await carolPage.goto(`/org/${slug}/settings/rates`)
    await expect(carolPage.getByText(/do not have permission/i)).toBeVisible({ timeout: 10_000 })

    // Member does NOT see Profitability in sidebar (requires FINANCIAL_VISIBILITY)
    // Navigate to profitability directly — should show access denied
    await carolPage.goto(`/org/${slug}/profitability`)
    await expect(carolPage.getByText(/don.t have access/i)).toBeVisible({ timeout: 10_000 })

    await carolContext.close()
  })
})
