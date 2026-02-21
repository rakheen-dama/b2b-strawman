import { test, expect } from '@playwright/test'
import { loginAs } from '../fixtures/auth'

test('mock login redirects to dashboard', async ({ page }) => {
  await page.goto('/mock-login')
  // Alice Owner is the default selection (first option)
  await page.getByRole('button', { name: 'Sign In' }).click()
  await expect(page).toHaveURL(/\/dashboard/)
})

test('owner can create a project', async ({ page }) => {
  const name = `E2E Test ${Date.now()}`
  await loginAs(page, 'alice')
  await page.goto('/org/e2e-test-org/projects')
  await page.getByRole('button', { name: 'New Project' }).click()
  await page.getByLabel('Name').fill(name)
  await page.getByRole('button', { name: 'Create Project' }).click()
  await expect(page.getByText(name)).toBeVisible()
})

test('member cannot access admin settings', async ({ page }) => {
  await loginAs(page, 'carol')
  await page.goto('/org/e2e-test-org/settings/rates')
  await expect(page.getByText(/permission/i)).toBeVisible()
})
