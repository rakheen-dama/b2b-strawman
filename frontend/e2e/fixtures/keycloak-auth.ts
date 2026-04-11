import type { Page } from "@playwright/test";
import { KC_LOGIN, KC_REGISTER } from "./keycloak-selectors";

/**
 * Log in to the app via Keycloak OIDC flow.
 * Navigates to a protected route, gets redirected to Keycloak login page,
 * fills credentials, submits, and waits for redirect back to the app.
 */
export async function loginAs(page: Page, email: string, password: string): Promise<void> {
  // Navigate to a protected route — middleware redirects to gateway OAuth2 endpoint
  await page.goto("/dashboard");

  // Wait for Keycloak login page to load (URL contains /realms/)
  await page.waitForURL(/\/realms\//, { timeout: 15_000 });

  // Fill username — Keycloak may show a combined form or a split flow
  // (username-first, then password on a second page)
  await page.locator(KC_LOGIN.username).fill(email);

  // Check if password field is visible (combined form) or needs a submit first (split flow)
  const passwordVisible = await page
    .locator(KC_LOGIN.password)
    .isVisible()
    .catch(() => false);

  if (passwordVisible) {
    // Combined login form — fill password and submit
    await page.locator(KC_LOGIN.password).fill(password);
    await page.locator(KC_LOGIN.submit).first().click();
  } else {
    // Split login flow — submit username first, then fill password on next page
    await page.locator(KC_LOGIN.submit).first().click();
    await page.waitForSelector(KC_LOGIN.password, { timeout: 10_000 });
    await page.locator(KC_LOGIN.password).fill(password);
    await page.locator(KC_LOGIN.submit).first().click();
  }

  // Wait for redirect back to the app (URL should no longer contain /realms/)
  await page.waitForURL((url) => !url.pathname.includes("/realms/"), { timeout: 15_000 });
}

/**
 * Log in as the platform admin (pre-created by keycloak-bootstrap.sh).
 */
export async function loginAsPlatformAdmin(page: Page): Promise<void> {
  await loginAs(page, "padmin@docteams.local", "password");
}

/**
 * Follow a Keycloak invitation link and complete user registration.
 * The invite link lands on a Keycloak registration page where the user
 * fills in their name and password.
 */
export async function registerFromInvite(
  page: Page,
  inviteLink: string,
  firstName: string,
  lastName: string,
  password: string
): Promise<void> {
  await page.goto(inviteLink);

  // Wait for the registration form to appear
  await page.waitForSelector(KC_REGISTER.firstName, { timeout: 15_000 });

  await page.locator(KC_REGISTER.firstName).fill(firstName);
  await page.locator(KC_REGISTER.lastName).fill(lastName);
  // Email may be pre-filled from the invite — only fill if editable
  const emailField = page.locator(KC_REGISTER.email);
  if (await emailField.isEditable().catch(() => false)) {
    // Pre-filled and editable — leave as-is (Keycloak sets it from invite)
  }
  await page.locator(KC_REGISTER.password).fill(password);
  await page.locator(KC_REGISTER.passwordConfirm).fill(password);
  await page.locator(KC_REGISTER.submit).first().click();

  // Wait for redirect back to the app after registration
  await page.waitForURL((url) => !url.pathname.includes("/realms/"), { timeout: 20_000 });
}

/**
 * Log out the current user by clearing cookies.
 */
export async function logout(page: Page): Promise<void> {
  await page.context().clearCookies();
  await page.goto("/");
}
