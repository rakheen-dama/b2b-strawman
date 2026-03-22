import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/keycloak-auth'

// Test data — org created by onboarding.spec.ts
// These credentials must match onboarding.spec.ts exactly
const OWNER_EMAIL = 'owner@thornton-za.e2e-test.local'
const OWNER_PASSWORD = 'SecureP@ss1'

/**
 * Pack verification tests for accounting-za vertical profile.
 * Assumes Thornton & Associates org has been provisioned by onboarding.spec.ts.
 *
 * Slice 400A covers: currency, tax, custom fields, compliance checklists.
 * Slice 400B will add: templates, clauses, automations, request templates.
 *
 * ADR-208: All assertions are UI-only. Test failures surface provisioning gaps
 * (not test bugs). Do not switch to API assertions.
 */

// Helper: login as owner and extract org slug from URL
async function loginAndGetSlug(page: Parameters<typeof loginAs>[0]): Promise<string> {
  await loginAs(page, OWNER_EMAIL, OWNER_PASSWORD)
  await page.waitForURL(/\/org\/([^/]+)\/dashboard/, { timeout: 15_000 })
  const url = page.url()
  const match = url.match(/\/org\/([^/]+)\//)
  if (!match?.[1]) throw new Error(`Could not extract org slug from URL: ${url}`)
  return match[1]
}

test.describe.serial('accounting-za pack verification', () => {

  test.describe('1. Default currency — ZAR', () => {
    test('Settings > General shows ZAR as default currency', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/general`)

      // Wait for the currency selector to be visible
      const currencySelector = page.getByTestId('default-currency')
      await expect(currencySelector).toBeVisible({ timeout: 10_000 })

      // The SelectTrigger shows the selected value as text
      await expect(currencySelector).toContainText('ZAR')
    })
  })

  test.describe('2. Tax defaults — VAT 15%', () => {
    test('Settings > Tax shows VAT rate at 15.00% as default', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/tax`)

      // Wait for tax rate rows to appear
      const firstRow = page.getByTestId('tax-rate-row').first()
      await expect(firstRow).toBeVisible({ timeout: 10_000 })

      // Find the VAT row by name
      const vatRow = page.getByTestId('tax-rate-row').filter({
        has: page.getByTestId('tax-rate-name').filter({ hasText: 'VAT' })
      })
      await expect(vatRow).toBeVisible()

      // Assert rate value
      await expect(vatRow.getByTestId('tax-rate-value')).toContainText('15.00%')

      // Assert it is marked as default
      await expect(vatRow.getByTestId('tax-rate-default')).toContainText('Default')
    })
  })

  test.describe('4. Customer custom fields (16 fields)', () => {
    test('Settings > Custom Fields > Customers tab shows SA Accounting client group', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/custom-fields`)

      // The default tab is PROJECT — switch to CUSTOMER
      await page.getByRole('tab', { name: 'Customers' }).click()

      // Verify the accounting_za_client group header is present
      const clientGroup = page.getByTestId('field-group-accounting_za_client')
      await expect(clientGroup).toBeVisible({ timeout: 10_000 })
      await expect(clientGroup).toContainText('SA Accounting — Client Details')

      // Verify trust details group is also present
      const trustGroup = page.getByTestId('field-group-accounting_za_trust_details')
      await expect(trustGroup).toBeVisible()
      await expect(trustGroup).toContainText('SA Accounting — Trust Details')
    })

    test('Customers tab has 22 field rows (16 client + 6 trust)', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/custom-fields`)
      await page.getByRole('tab', { name: 'Customers' }).click()

      // Wait for fields to render
      await expect(page.getByTestId('field-row').first()).toBeVisible({ timeout: 10_000 })

      const fieldRows = await page.getByTestId('field-row').count()
      // accounting-za-customer: 16 fields + accounting-za-customer-trust: 6 fields = 22
      expect(fieldRows).toBe(22)
    })

    test('Customers tab contains key field names', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/custom-fields`)
      await page.getByRole('tab', { name: 'Customers' }).click()

      await expect(page.getByTestId('field-row').first()).toBeVisible({ timeout: 10_000 })

      // Assert specific field names from accounting-za-customer pack
      await expect(page.getByRole('cell', { name: 'SARS Tax Reference' })).toBeVisible()
      await expect(page.getByRole('cell', { name: 'Financial Year-End' })).toBeVisible()
      await expect(page.getByRole('cell', { name: 'FICA Verified' })).toBeVisible()
    })
  })

  test.describe('5. Project custom fields (5 fields)', () => {
    test('Settings > Custom Fields > Projects tab shows SA Accounting engagement group', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/custom-fields`)

      // Projects tab is the default tab
      const engagementGroup = page.getByTestId('field-group-accounting_za_engagement')
      await expect(engagementGroup).toBeVisible({ timeout: 10_000 })
      await expect(engagementGroup).toContainText('SA Accounting — Engagement Details')
    })

    test('Projects tab has 5 engagement field rows', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/custom-fields`)

      await expect(page.getByTestId('field-row').first()).toBeVisible({ timeout: 10_000 })

      const fieldRows = await page.getByTestId('field-row').count()
      // accounting-za-project: 5 fields
      expect(fieldRows).toBe(5)
    })

    test('Projects tab contains key engagement field names', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/custom-fields`)

      await expect(page.getByTestId('field-row').first()).toBeVisible({ timeout: 10_000 })
      await expect(page.getByRole('cell', { name: 'Engagement Type' })).toBeVisible()
      await expect(page.getByRole('cell', { name: 'SARS Submission Deadline' })).toBeVisible()
    })
  })

  test.describe('6. Trust custom fields group present', () => {
    test('Settings > Custom Fields > Customers tab shows trust details group', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/custom-fields`)
      await page.getByRole('tab', { name: 'Customers' }).click()

      const trustGroup = page.getByTestId('field-group-accounting_za_trust_details')
      await expect(trustGroup).toBeVisible({ timeout: 10_000 })

      // Assert 6 trust fields are present in the field table
      // (Both client and trust fields appear in the Customers tab together)
      await expect(page.getByRole('cell', { name: 'Trust Registration Number' })).toBeVisible()
      await expect(page.getByRole('cell', { name: 'Trust Type' })).toBeVisible()
    })
  })

  test.describe('7. FICA KYC compliance checklist (11 items)', () => {
    test('Settings > Checklists shows FICA KYC template row', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/checklists`)

      // Wait for template rows to appear
      const firstRow = page.getByTestId('checklist-template-row').first()
      await expect(firstRow).toBeVisible({ timeout: 10_000 })

      // Find the FICA template by name text
      await expect(page.getByText('FICA KYC — SA Accounting')).toBeVisible()
    })

    test('FICA KYC template has 11 checklist items', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/checklists`)

      await expect(page.getByTestId('checklist-template-row').first()).toBeVisible({ timeout: 10_000 })

      // Click through to the FICA template detail page
      await page.getByRole('link', { name: 'FICA KYC — SA Accounting' }).click()
      await page.waitForURL(/\/settings\/checklists\/[^/]+$/, { timeout: 10_000 })

      // Wait for item rows
      const itemRows = page.getByTestId('checklist-item-row')
      await expect(itemRows.first()).toBeVisible({ timeout: 10_000 })

      const count = await itemRows.count()
      expect(count).toBe(11)
    })

    test('FICA KYC template contains key item names', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/checklists`)

      await expect(page.getByTestId('checklist-template-row').first()).toBeVisible({ timeout: 10_000 })
      await page.getByRole('link', { name: 'FICA KYC — SA Accounting' }).click()
      await page.waitForURL(/\/settings\/checklists\/[^/]+$/, { timeout: 10_000 })

      await expect(page.getByTestId('checklist-item-row').first()).toBeVisible({ timeout: 10_000 })

      // Assert specific item names from fica-kyc-za pack
      await expect(page.getByText('Certified ID Copy')).toBeVisible()
      await expect(page.getByText('Proof of Residence')).toBeVisible()
      await expect(page.getByText('Tax Clearance Certificate')).toBeVisible()
      await expect(page.getByText('Beneficial Ownership Declaration')).toBeVisible()
      await expect(page.getByText('Trust Deed (Certified Copy)')).toBeVisible()
    })

    test('FICA KYC template has required items marked', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/checklists`)

      await expect(page.getByTestId('checklist-template-row').first()).toBeVisible({ timeout: 10_000 })
      await page.getByRole('link', { name: 'FICA KYC — SA Accounting' }).click()
      await page.waitForURL(/\/settings\/checklists\/[^/]+$/, { timeout: 10_000 })

      await expect(page.getByTestId('checklist-item-row').first()).toBeVisible({ timeout: 10_000 })

      // Items 1,2,3,4,5,8,10,11 are required = 8 required badges
      const requiredBadges = page.getByTestId('checklist-item-required')
      await expect(requiredBadges.first()).toBeVisible()
      const requiredCount = await requiredBadges.count()
      expect(requiredCount).toBe(8)
    })
  })

})
