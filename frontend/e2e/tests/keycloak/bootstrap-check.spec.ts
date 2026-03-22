import { test, expect } from '@playwright/test'
import { loginAsPlatformAdmin } from '../../fixtures/keycloak-auth'

const MAILPIT_API = process.env.MAILPIT_API_URL || 'http://localhost:8025/api/v1'

test.describe('Keycloak bootstrap check', () => {
  test('platform admin can log in via Keycloak', async ({ page }) => {
    await loginAsPlatformAdmin(page)
    await expect(page).toHaveURL(/localhost:3000/)
  })

  test('Mailpit API is accessible', async () => {
    const res = await fetch(`${MAILPIT_API}/messages`)
    expect(res.status).toBe(200)
  })
})
