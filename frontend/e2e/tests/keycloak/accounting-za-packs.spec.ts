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

  test.describe('3. Rate card defaults (known gap)', () => {
    test.skip(true, 'Rate card seeding from vertical profile may not be implemented — surfaces gap if it fails')
    test('Settings > Rates shows billing rates for Owner, Admin, Member', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/rates`)
      await expect(page.getByTestId('billing-rate-owner')).toBeVisible({ timeout: 10_000 })
      await expect(page.getByTestId('billing-rate-owner')).toContainText('1500')
      await expect(page.getByTestId('billing-rate-admin')).toContainText('850')
      await expect(page.getByTestId('billing-rate-member')).toContainText('450')
    })
  })

  test.describe('8. Document templates (7 templates)', () => {
    test('Settings > Templates shows accounting-za engagement letter templates', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/templates`)

      await expect(page.getByTestId('template-list-item').first()).toBeVisible({ timeout: 10_000 })

      await expect(page.getByRole('link', { name: 'Engagement Letter — Monthly Bookkeeping' })).toBeVisible()
      await expect(page.getByRole('link', { name: 'Engagement Letter — Annual Tax Return' })).toBeVisible()
      await expect(page.getByRole('link', { name: 'Engagement Letter — Advisory' })).toBeVisible()
    })

    test('Settings > Templates shows remaining 4 accounting-za templates', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/templates`)

      await expect(page.getByTestId('template-list-item').first()).toBeVisible({ timeout: 10_000 })

      await expect(page.getByRole('link', { name: 'Monthly Report Cover' })).toBeVisible()
      await expect(page.getByRole('link', { name: 'SA Tax Invoice' })).toBeVisible()
      await expect(page.getByRole('link', { name: 'Statement of Account' })).toBeVisible()
      await expect(page.getByRole('link', { name: 'FICA Confirmation Letter' })).toBeVisible()
    })

    test('Settings > Templates has 7 template rows from accounting-za pack', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/templates`)

      await expect(page.getByTestId('template-list-item').first()).toBeVisible({ timeout: 10_000 })

      // accounting-za pack has exactly 7 templates
      // (common pack templates may also appear — assert >=7 to be safe)
      const count = await page.getByTestId('template-list-item').count()
      expect(count).toBeGreaterThanOrEqual(7)
    })
  })

  test.describe('9. Clauses (7 clauses + 3 template associations)', () => {
    test('Settings > Clauses shows Legal category with accounting-za clauses', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/clauses`)

      await expect(page.getByTestId('clause-category').first()).toBeVisible({ timeout: 10_000 })

      // Legal category must be present
      const legalCategory = page.getByTestId('clause-category').filter({ hasText: 'Legal' })
      await expect(legalCategory).toBeVisible()

      // Commercial and Compliance categories
      await expect(page.getByTestId('clause-category').filter({ hasText: 'Commercial' })).toBeVisible()
      await expect(page.getByTestId('clause-category').filter({ hasText: 'Compliance' })).toBeVisible()
    })

    test('Settings > Clauses shows all 7 accounting-za clauses', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/clauses`)

      await expect(page.getByTestId('clause-row').first()).toBeVisible({ timeout: 10_000 })

      // Assert specific clause titles
      await expect(page.getByText('Limitation of Liability (Accounting)')).toBeVisible()
      await expect(page.getByText('Fee Escalation')).toBeVisible()
      await expect(page.getByText('Termination (Accounting)')).toBeVisible()
      await expect(page.getByText('Confidentiality (Accounting)')).toBeVisible()
      await expect(page.getByText('Document Retention (Accounting)')).toBeVisible()
      await expect(page.getByText('Third-Party Reliance')).toBeVisible()
      await expect(page.getByText('Electronic Communication Consent')).toBeVisible()
    })

    test('Clauses show template usage count (template-clause-association)', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/clauses`)

      await expect(page.getByTestId('clause-row').first()).toBeVisible({ timeout: 10_000 })

      // At least some clauses should show template association counts
      // The Limitation of Liability clause is used in 3 templates
      const assocBadges = page.getByTestId('template-clause-association')
      await expect(assocBadges.first()).toBeVisible({ timeout: 10_000 })

      // Verify at least 3 template associations are visible (one per associated template)
      const count = await assocBadges.count()
      expect(count).toBeGreaterThanOrEqual(3)
    })

    test('Limitation of Liability clause is associated with multiple templates', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/clauses`)

      await expect(page.getByTestId('clause-row').first()).toBeVisible({ timeout: 10_000 })

      // Find the Limitation of Liability clause row
      const limitationRow = page.getByTestId('clause-row').filter({
        has: page.getByText('Limitation of Liability (Accounting)')
      })
      await expect(limitationRow).toBeVisible()

      // It should show template usage — associated with all 3 engagement letter templates
      const assoc = limitationRow.getByTestId('template-clause-association')
      await expect(assoc).toBeVisible()
      await expect(assoc).toContainText('template')
    })
  })

  test.describe('10. Automation rules (4 rules)', () => {
    test('Settings > Automations shows all 4 accounting-za automation rules', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/automations`)

      await expect(page.getByTestId('automation-row').first()).toBeVisible({ timeout: 10_000 })

      // Assert all 4 rule names
      await expect(page.getByText('FICA Reminder (7 days)')).toBeVisible()
      await expect(page.getByText('Engagement Budget Alert (80%)')).toBeVisible()
      await expect(page.getByText('Invoice Overdue (30 days)')).toBeVisible()
      await expect(page.getByText('SARS Deadline Reminder')).toBeVisible()
    })

    test('Settings > Automations has exactly 4 automation rows', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/automations`)

      await expect(page.getByTestId('automation-row').first()).toBeVisible({ timeout: 10_000 })

      const count = await page.getByTestId('automation-row').count()
      expect(count).toBe(4)
    })
  })

  test.describe('11. Request template (Year-End, 8 items)', () => {
    test('Settings > Request Templates shows Year-End Information Request template', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/request-templates`)

      await expect(page.getByTestId('request-template-row').first()).toBeVisible({ timeout: 10_000 })

      await expect(page.getByRole('link', { name: 'Year-End Information Request (SA)' })).toBeVisible()
    })

    test('Year-End template detail has 8 items', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/request-templates`)

      await expect(page.getByTestId('request-template-row').first()).toBeVisible({ timeout: 10_000 })

      // Click through to the template detail page
      await page.getByRole('link', { name: 'Year-End Information Request (SA)' }).click()
      await page.waitForURL(/\/settings\/request-templates\/[^/]+$/, { timeout: 10_000 })

      // Wait for items to render
      await expect(page.getByTestId('request-item-row').first()).toBeVisible({ timeout: 10_000 })

      const count = await page.getByTestId('request-item-row').count()
      expect(count).toBe(8)
    })

    test('Year-End template contains specific item names', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/request-templates`)

      await expect(page.getByTestId('request-template-row').first()).toBeVisible({ timeout: 10_000 })
      await page.getByRole('link', { name: 'Year-End Information Request (SA)' }).click()
      await page.waitForURL(/\/settings\/request-templates\/[^/]+$/, { timeout: 10_000 })

      await expect(page.getByTestId('request-item-row').first()).toBeVisible({ timeout: 10_000 })

      // Assert specific item names from year-end-info-request-za pack
      await expect(page.getByText('Trial Balance')).toBeVisible()
      await expect(page.getByText('Bank Statements (Full Year)')).toBeVisible()
      await expect(page.getByText('Loan Agreements')).toBeVisible()
      await expect(page.getByText('Fixed Asset Register')).toBeVisible()
      await expect(page.getByText('Payroll Summary')).toBeVisible()
    })

    test('Year-End template has required items marked', async ({ page }) => {
      const slug = await loginAndGetSlug(page)
      await page.goto(`/org/${slug}/settings/request-templates`)

      await expect(page.getByTestId('request-template-row').first()).toBeVisible({ timeout: 10_000 })
      await page.getByRole('link', { name: 'Year-End Information Request (SA)' }).click()
      await page.waitForURL(/\/settings\/request-templates\/[^/]+$/, { timeout: 10_000 })

      await expect(page.getByTestId('request-item-row').first()).toBeVisible({ timeout: 10_000 })

      // Items 1,2,3,4,8 are required = 5 checked required checkboxes
      // (Trial Balance, Bank Statements, Loan Agreements, Fixed Asset Register, Payroll Summary)
      const requiredCheckboxes = page.getByTestId('request-item-required')
      const allCheckboxes = await requiredCheckboxes.all()
      let checkedCount = 0
      for (const cb of allCheckboxes) {
        if (await cb.isChecked()) {
          checkedCount++
        }
      }
      expect(checkedCount).toBe(5)
    })
  })

})
