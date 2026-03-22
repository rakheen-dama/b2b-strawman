import { Page } from '@playwright/test'
import { KeycloakLoginPage } from '../page-objects/keycloak-login.page'
import { KeycloakRegisterPage } from '../page-objects/keycloak-register.page'

const GATEWAY_URL = process.env.GATEWAY_URL || 'http://localhost:8443'

/**
 * Logs in as the platform admin (padmin@docteams.local / password).
 * Navigates to the app, follows the OAuth2 redirect to Keycloak,
 * fills the login form, and waits for redirect back to the app.
 */
export async function loginAsPlatformAdmin(page: Page): Promise<void> {
  await loginAs(page, 'padmin@docteams.local', 'password')
}

/**
 * Generic Keycloak login for any user.
 * Triggers the OAuth2 PKCE flow through the Gateway BFF.
 */
export async function loginAs(
  page: Page,
  email: string,
  password: string
): Promise<void> {
  // Navigate to the gateway login endpoint to trigger OAuth2 redirect
  await page.goto(`${GATEWAY_URL}/oauth2/authorization/keycloak`)

  // Wait for redirect to Keycloak login page
  const loginPage = new KeycloakLoginPage(page)
  await loginPage.waitForReady()
  await loginPage.login(email, password)

  // Wait for redirect back to the app (through gateway)
  await page.waitForURL(/localhost:3000/, { timeout: 30_000 })
}

/**
 * Follows a Keycloak invitation link and completes registration.
 * The invite link goes directly to Keycloak's registration form.
 */
export async function registerFromInvite(
  page: Page,
  inviteLink: string,
  firstName: string,
  lastName: string,
  password: string
): Promise<void> {
  await page.goto(inviteLink)

  const registerPage = new KeycloakRegisterPage(page)
  await registerPage.waitForReady()
  await registerPage.register(firstName, lastName, password)

  // After registration, Keycloak redirects to the app via Gateway
  await page.waitForURL(/localhost:3000/, { timeout: 30_000 })
}
