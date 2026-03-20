import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/auth'

const ORG = 'e2e-test-org'

test.describe('SET-02: Rate Cards', () => {
  test('Rates page loads without crash (AvatarCircle regression)', async ({ page }) => {
    await loginAs(page, 'alice')
    await page.goto(`/org/${ORG}/settings/rates`)
    // This was BUG-REG-001: TypeError on null name.length in AvatarCircle
    await expect(page.locator('body')).not.toContainText('Something went wrong')
    await expect(page.locator('body')).not.toContainText('TypeError')
    // Should show rate configuration content
    await expect(page.locator('body')).toContainText(/rate|currency/i)
  })

  test('Rates page loads for Admin', async ({ page }) => {
    await loginAs(page, 'bob')
    await page.goto(`/org/${ORG}/settings/rates`)
    await expect(page.locator('body')).not.toContainText('Something went wrong')
  })

  test('Member sees permission denied on rates', async ({ page }) => {
    await loginAs(page, 'carol')
    await page.goto(`/org/${ORG}/settings/rates`)
    await expect(page.locator('body')).toContainText(/don.t have access|permission/i)
  })
})
