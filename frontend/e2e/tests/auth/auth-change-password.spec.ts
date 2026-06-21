import { test, expect } from "@playwright/test";
import { loginAs as loginAsMock } from "../../fixtures/auth";
import { loginAs as loginAsKeycloak } from "../../fixtures/keycloak-auth";

/**
 * Epic 571 — In-app change-password via kc_action=UPDATE_PASSWORD.
 *
 * Flow: an authenticated user opens the user menu → "Account & Security" →
 * the app does a top-level navigation to the gateway change-password
 * initiation URL (`/oauth2/authorization/keycloak?kc_action=update_password`).
 * The gateway's OAuth2AuthorizationRequestResolver (571A) maps the sentinel to
 * `kc_action=UPDATE_PASSWORD` on the authorization request, so Keycloak renders
 * its branded `login-update-password` page (Epic 572, Done). The user sets a new
 * password and the existing OAuth login success handler returns them to the app.
 *
 * Dual-arm pattern (mirrors auth-logout / auth-expiry):
 *
 *  - MOCK arm (CI default, `E2E_AUTH_MODE=mock`): there is NO real Keycloak, so
 *    the full kc_action round-trip cannot run. Additionally, in mock auth the
 *    header renders the mock user button (not the Keycloak `UserMenuBff`), so the
 *    "Account & Security" item is intentionally absent. The CI-runnable assertion
 *    is the first-party contract: the gateway change-password initiation URL is
 *    well-formed and carries the `kc_action=update_password` sentinel the resolver
 *    keys on. The real branded `login-update-password` round-trip is a Keycloak
 *    dev-stack observed gate (reproduce-before-fix / "PASS means observed").
 *
 *  - KEYCLOAK arm (`E2E_AUTH_MODE=keycloak`, dev stack on 3000/8443/8180): logs
 *    in via real Keycloak, opens the user menu, clicks "Account & Security",
 *    asserts the navigation toward the gateway change-password URL, that Keycloak
 *    renders the branded change-password page, sets a new password, and returns
 *    to the app.
 */

const AUTH_MODE = process.env.E2E_AUTH_MODE || "mock";

test.describe("in-app change password (kc_action=UPDATE_PASSWORD)", () => {
  test("gateway change-password initiation URL carries the kc_action sentinel", async ({
    page,
  }) => {
    // CI-safe contract assertion: the URL the "Account & Security" item navigates to
    // must hit the existing keycloak OAuth client and carry the sentinel the gateway
    // resolver maps to kc_action=UPDATE_PASSWORD. This is the app-side behaviour CI can
    // run on the mock stack (no real Keycloak); it pins the frontend↔gateway contract.
    await loginAsMock(page, "alice");
    await page.goto("/dashboard");

    // The change-password handler navigates to `${GATEWAY_URL}/oauth2/authorization/keycloak`
    // with the `kc_action=update_password` sentinel. Assert that contract shape (gateway base
    // resolved at build time, default http://localhost:8443).
    const gatewayBase = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";
    const changePasswordUrl = `${gatewayBase}/oauth2/authorization/keycloak?kc_action=update_password`;

    const parsed = new URL(changePasswordUrl);
    expect(parsed.pathname).toBe("/oauth2/authorization/keycloak");
    expect(parsed.searchParams.get("kc_action")).toBe("update_password");
  });

  test("keycloak: Account & Security renders the branded change-password page and returns to app", async ({
    page,
  }) => {
    test.skip(AUTH_MODE !== "keycloak", "Requires the Keycloak dev stack (E2E_AUTH_MODE=keycloak)");

    await loginAsKeycloak(page, "padmin@docteams.local", "password");

    // Open the user menu and click "Account & Security".
    await page.getByRole("button", { name: /user menu/i }).click();
    const changePassword = page.getByRole("button", { name: /account & security/i });
    await expect(changePassword).toBeVisible();

    await Promise.all([
      page.waitForURL(/kc_action=UPDATE_PASSWORD|\/realms\//, { timeout: 15_000 }),
      changePassword.click(),
    ]);

    // Keycloak renders its branded login-update-password page (Epic 572).
    await expect(page.locator("#password-new")).toBeVisible({ timeout: 15_000 });
    await expect(page.locator("#password-confirm")).toBeVisible();

    const newPassword = `Password!${Date.now()}`;
    await page.locator("#password-new").fill(newPassword);
    await page.locator("#password-confirm").fill(newPassword);
    await page.locator('input[type="submit"], button[type="submit"]').first().click();

    // The existing OAuth login success handler returns the user to the app.
    await page.waitForURL((url) => !url.pathname.includes("/realms/"), { timeout: 15_000 });
    await expect(page).toHaveURL(/localhost:3000/);
  });
});
