import { test, expect } from "@playwright/test";
import { loginAsPlatformAdmin } from "../../fixtures/keycloak-auth";

/**
 * Epic 571 — In-app change-password via kc_action=UPDATE_PASSWORD.
 *
 * Flow: an authenticated user opens the user menu → "Account & Security" →
 * the app does a top-level navigation to the gateway change-password
 * initiation URL (`/oauth2/authorization/keycloak?bff_action=change_password`).
 * The gateway's OAuth2AuthorizationRequestResolver (571A) maps the gateway-private
 * `bff_action` sentinel to `kc_action=UPDATE_PASSWORD` on the authorization request,
 * so Keycloak renders its branded `login-update-password` page (Epic 572, Done). The
 * user sets a new password and the existing OAuth login success handler returns them
 * to the app.
 *
 * Coverage split (mirrors auth-logout / auth-expiry):
 *
 *  - The first-party contract (the "Account & Security" menu item renders and
 *    navigates to the gateway change-password URL carrying the `bff_action`
 *    sentinel) is covered by a CI-runnable VITEST component test
 *    (`frontend/__tests__/components/auth/user-menu-bff.test.tsx`). It lives there
 *    because in mock auth mode the header renders `MockUserButton`, NOT the Keycloak
 *    `UserMenuBff` (see `auth-header-controls.tsx`), so a DOM-interacting mock-arm
 *    e2e would be impossible / tautological.
 *
 *  - The full branded `login-update-password` round-trip below is a Keycloak
 *    dev-stack OBSERVED gate (`E2E_AUTH_MODE=keycloak`, dev stack on
 *    3000/8443/8180): reproduce-before-fix / "PASS means observed". It is skipped
 *    on the mock CI stack because there is no real Keycloak to render the page.
 */

const AUTH_MODE = process.env.E2E_AUTH_MODE || "mock";

test.describe("in-app change password (kc_action=UPDATE_PASSWORD)", () => {
  test("keycloak: Account & Security renders the branded change-password page and returns to app", async ({
    page,
  }) => {
    test.skip(AUTH_MODE !== "keycloak", "Requires the Keycloak dev stack (E2E_AUTH_MODE=keycloak)");

    await loginAsPlatformAdmin(page);

    // Open the user menu and click "Account & Security".
    await page.getByRole("button", { name: /user menu/i }).click();
    const changePassword = page.getByRole("button", { name: /account & security/i });
    await expect(changePassword).toBeVisible();

    await Promise.all([page.waitForURL(/\/realms\//, { timeout: 15_000 }), changePassword.click()]);

    // Keycloak renders its branded login-update-password page (Epic 572).
    await expect(page.locator("#password-new")).toBeVisible({ timeout: 15_000 });
    await expect(page.locator("#password-confirm")).toBeVisible();

    const newPassword = `Password!${Date.now()}`;
    await page.locator("#password-new").fill(newPassword);
    await page.locator("#password-confirm").fill(newPassword);
    await page.locator('input[type="submit"], button[type="submit"]').first().click();

    // The existing OAuth login success handler returns the user to the app
    // (environment-agnostic assertion: off the Keycloak realm, back on the app dashboard).
    await page.waitForURL((url) => !url.pathname.includes("/realms/"), { timeout: 15_000 });
    await expect(page).not.toHaveURL(/\/realms\//);
    await expect(page).toHaveURL(/\/dashboard/);
  });
});
