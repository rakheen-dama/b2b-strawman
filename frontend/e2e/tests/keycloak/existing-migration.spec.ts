import { test, expect } from '@playwright/test'
import { loginAs } from '../../fixtures/keycloak-auth'

const ORG_SLUG = 'acme-corp'

test.describe.serial('Smoke tests — Keycloak stack migration', () => {
  test('owner login via Keycloak redirects to dashboard', async ({ page }) => {
    await loginAs(page, 'alice@example.com', 'password')
    await expect(page).toHaveURL(/\/org\/acme-corp\/dashboard/, {
      timeout: 30_000,
    })
  })

  test('owner can create a project', async ({ page }) => {
    const name = `E2E Test ${Date.now()}`
    await loginAs(page, 'alice@example.com', 'password')
    await page.goto(`/org/${ORG_SLUG}/projects`)
    await page.getByRole('button', { name: 'New Project' }).click()
    await page.getByLabel('Name').fill(name)
    await page.getByRole('button', { name: 'Create Project' }).click()
    await expect(page.getByText(name)).toBeVisible()
  })

  test('member cannot access admin settings', async ({ page }) => {
    await loginAs(page, 'carol@example.com', 'password')
    await page.goto(`/org/${ORG_SLUG}/settings/rates`)
    await expect(page.getByText(/permission/i)).toBeVisible()
  })
})
