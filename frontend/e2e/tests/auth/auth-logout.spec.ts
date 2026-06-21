import { test, expect } from "@playwright/test";

/**
 * Epic 570 — Branded /signed-out logout terminus.
 *
 * CI runs this against the mock-auth stack, where the gateway's RP-initiated
 * OIDC logout chain (Keycloak end-session → post-logout redirect) does NOT run
 * — there is no real Keycloak. So the robust CI assertion mirrors the expiry
 * spec: navigate directly to the branded `/signed-out` route (as the gateway's
 * post-logout redirect would land the browser) and assert it renders branded
 * with a "Sign in again" control → `/sign-in`.
 *
 * The full logout → `/signed-out` redirect (gateway 570B.1) is a Keycloak
 * dev-stack manual-QA gate (reproduce-before-fix / "PASS means observed").
 */
test.describe("branded signed-out terminus", () => {
  test("logout lands on the branded /signed-out page with a 'Sign in again' CTA", async ({
    page,
  }) => {
    await page.goto("/signed-out");

    await expect(page).toHaveURL(/\/signed-out/);
    await expect(page.getByText(/you've been signed out/i)).toBeVisible();

    const cta = page.getByRole("link", { name: /sign in again/i });
    await expect(cta).toBeVisible();
    await expect(cta).toHaveAttribute("href", "/sign-in");
  });
});
