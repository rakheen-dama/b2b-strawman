import { Page } from '@playwright/test'

/**
 * Keycloak E2E auth fixture.
 *
 * Authenticates by navigating through the real OIDC flow:
 *   1. Navigate to a protected page (middleware redirects to /api/auth/signin)
 *   2. next-auth auto-redirects to Keycloak login page
 *   3. Fill username/password on Keycloak's login form
 *   4. Keycloak redirects back -> next-auth exchanges code -> sets session cookie
 *   5. User lands on the app, fully authenticated
 */

type SeedUser = 'alice' | 'bob' | 'carol'

const KEYCLOAK_USERS: Record<SeedUser, { email: string; password: string }> = {
  alice: { email: 'alice@e2e-test.local', password: 'alice-e2e-pass' },
  bob:   { email: 'bob@e2e-test.local',   password: 'bob-e2e-pass' },
  carol: { email: 'carol@e2e-test.local', password: 'carol-e2e-pass' },
}

export async function loginAsKeycloak(page: Page, user: SeedUser): Promise<void> {
  const creds = KEYCLOAK_USERS[user]

  // Navigate to sign-in page — KeycloakSignIn component auto-redirects to Keycloak
  await page.goto('/sign-in')

  // Wait for redirect to Keycloak login page (running on port 9091 externally,
  // but the OIDC flow uses the issuer URL which resolves through the browser)
  // The Keycloak login form has username and password fields
  await page.waitForSelector('#username, #kc-form-login, input[name="username"]', {
    timeout: 30_000,
  })

  // Fill Keycloak login form
  // Keycloak uses either #username or input[name="username"] depending on theme
  const usernameField = page.locator('#username, input[name="username"]').first()
  const passwordField = page.locator('#password, input[name="password"]').first()

  await usernameField.fill(creds.email)
  await passwordField.fill(creds.password)

  // Click the sign-in button on Keycloak's form.
  // Primary selector is #kc-login (Keycloak's default theme); the fallbacks
  // cover custom themes that may use a generic submit button instead.
  await page.locator('#kc-login, button[type="submit"], input[type="submit"]').first().click()

  // Wait for the OIDC callback to complete and redirect to the app
  // The app should land on dashboard, org page, or create-org page
  await page.waitForURL(/\/(org|dashboard|create-org)/, { timeout: 30_000 })
}
