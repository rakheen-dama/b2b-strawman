import { test, expect } from '@playwright/test'
import { loginAsKeycloak } from '../fixtures/keycloak-auth'

/**
 * Keycloak E2E smoke tests.
 *
 * These tests run against the E2E Docker stack with Keycloak as the auth provider.
 * Pre-requisite: `docker compose -f compose/docker-compose.e2e.yml up -d` with seed complete.
 *
 * The seed creates:
 *   - Keycloak org: e2e-test-org
 *   - Users: alice (owner), bob (admin), carol (member)
 *   - Backend tenant provisioned with customer + project
 */

test.describe('Keycloak auth smoke tests', () => {
  test('login as Alice and see dashboard', async ({ page }) => {
    await loginAsKeycloak(page, 'alice')

    // After login, user should land on dashboard or org page
    // Navigate to the seeded org's dashboard
    await page.goto('/org/e2e-test-org/dashboard')

    // Verify authenticated content renders (not redirected to sign-in)
    await expect(page.locator('body')).not.toContainText('Redirecting to sign in')
    // Dashboard should show something meaningful (project name, org name, etc.)
    await expect(page.getByText(/dashboard|Website Redesign|e2e-test-org/i).first()).toBeVisible({
      timeout: 10_000,
    })
  })

  test('team page shows members', async ({ page }) => {
    await loginAsKeycloak(page, 'alice')

    await page.goto('/org/e2e-test-org/team')

    // Verify the team page loads with the seeded members
    await expect(page.getByText('Alice Owner')).toBeVisible({ timeout: 10_000 })
    await expect(page.getByText('Bob Admin')).toBeVisible()
    await expect(page.getByText('Carol Member')).toBeVisible()

    // Alice is the owner, so the invite form should be visible
    await expect(
      page.getByRole('button', { name: /invite/i })
    ).toBeVisible()
  })

  test('org creation flow', async ({ page }) => {
    // Login as Alice first (she already has an authenticated session in the seed org)
    await loginAsKeycloak(page, 'alice')

    // Navigate to create-org page
    await page.goto('/create-org')

    // Fill the org creation form
    const orgName = `E2E Smoke ${Date.now()}`
    await page.getByLabel(/organization name/i).fill(orgName)
    await page.getByRole('button', { name: /create organization/i }).click()

    // The form calls POST /api/orgs, then re-authenticates via Keycloak.
    // This triggers a second Keycloak login redirect.
    // Wait for either: the Keycloak login form (re-auth) or the dashboard (if session reused)
    const loginFormOrDashboard = await Promise.race([
      page.waitForSelector('#username, input[name="username"]', { timeout: 30_000 }).then(() => 'keycloak-form' as const),
      page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 30_000 }).then(() => 'dashboard' as const),
    ])

    if (loginFormOrDashboard === 'keycloak-form') {
      // Re-authenticate — Keycloak may have an active SSO session and skip the form,
      // but if the form is shown, fill it in
      const usernameField = page.locator('#username, input[name="username"]').first()
      const isVisible = await usernameField.isVisible()
      if (isVisible) {
        await usernameField.fill('alice@e2e-test.local')
        await page.locator('#password, input[name="password"]').first().fill('alice-e2e-pass')
        await page.locator('#kc-login, button[type="submit"], input[type="submit"]').first().click()
      }
      // Wait for redirect to the new org's dashboard
      await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 30_000 })
    }

    // Verify we landed on a dashboard (the new org's dashboard)
    await expect(page.locator('body')).not.toContainText('Redirecting to sign in')
  })
})
