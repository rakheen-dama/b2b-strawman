import { test, expect } from '@playwright/test'
import { loginAsPlatformAdmin } from '../../fixtures/keycloak-auth'
import { MAILPIT_API } from '../../helpers/mailpit'

const BASE_HOST = new URL(process.env.PLAYWRIGHT_BASE_URL || 'http://localhost:3000').host

test.describe('Keycloak bootstrap check', () => {
  test('platform admin can log in via Keycloak', async ({ page }) => {
    await loginAsPlatformAdmin(page)
    await expect(page).toHaveURL(new RegExp(BASE_HOST))
  })

  test('Mailpit API is accessible', async () => {
    const res = await fetch(`${MAILPIT_API}/messages`)
    expect(res.status).toBe(200)
  })
})
