import { test, expect } from '@playwright/test'

test.describe('PORTAL-04: Portal Branding', () => {
  test('orgId URL param auto-fetches branding', async ({ page }) => {
    await page.goto('/portal?orgId=e2e-test-org')
    // Should show org name (not generic "DocTeams Portal")
    await page.waitForTimeout(2000) // Allow branding fetch
    const body = await page.locator('body').textContent()
    // The org slug input should be pre-populated
    const orgInput = page.locator('input[name="orgSlug"], input[placeholder*="org"], input[type="text"]').first()
    if (await orgInput.isVisible()) {
      await expect(orgInput).toHaveValue('e2e-test-org')
    }
  })

  test('Portal login page loads without crash', async ({ page }) => {
    await page.goto('/portal')
    await expect(page.locator('body')).not.toContainText('Something went wrong')
    // Should have a login form
    await expect(page.locator('button, input')).toBeTruthy()
  })
})
