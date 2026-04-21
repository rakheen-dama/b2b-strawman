import type { Page } from "@playwright/test";

/**
 * Epic 499B — Portal auth helpers used by Playwright visual-baseline specs.
 *
 * Ports the magic-link / exchange flow from
 *   frontend/e2e/tests/portal/portal-navigation.spec.ts
 * into the portal's own e2e tree so the portal baselines can run without
 * reaching into the firm-side e2e directory.
 *
 * The portal stores the exchanged JWT in both a `portal-auth-token` cookie
 * and two localStorage keys (`portal-token`, `portal-auth-token`). All three
 * must be set for `useAuth()` to consider the session authenticated.
 */

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";
const PORTAL_BASE_URL =
  process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3002";
const PORTAL_ORIGIN = new URL(PORTAL_BASE_URL).origin;

/**
 * Request a magic-link token for `email` and exchange it for a portal JWT.
 * Returns `null` when either endpoint fails, the response shape is unexpected,
 * or a network/DNS/timeout error is thrown (fetch rejects on network failures).
 */
export async function getPortalJwt(email: string): Promise<string | null> {
  try {
    const linkRes = await fetch(`${BACKEND_URL}/portal/auth/request-link`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, orgId: ORG_SLUG }),
    });
    if (!linkRes.ok) return null;
    const linkData = await linkRes.json();
    const token =
      linkData.token ||
      linkData.magicLink?.split("token=").pop()?.split("&")[0] ||
      null;
    if (!token) return null;

    const exchangeRes = await fetch(`${BACKEND_URL}/portal/auth/exchange`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, orgId: ORG_SLUG }),
    });
    if (!exchangeRes.ok) return null;
    const data = await exchangeRes.json();
    return data.token || data.accessToken || data.access_token || null;
  } catch {
    // Network failures (DNS, timeout, connection refused) should not escape —
    // the contract is Promise<string | null> and callers gate on `if (!jwt)`.
    return null;
  }
}

/**
 * Authenticate the Playwright browser as a portal contact by setting
 * the cookie and localStorage keys that the portal's `useAuth()` hook reads.
 * Must be called AFTER navigating to the portal origin at least once so the
 * localStorage call can land on the correct origin.
 */
export async function loginAsPortalContact(
  page: Page,
  jwt: string,
): Promise<void> {
  await page.context().addCookies([
    {
      name: "portal-auth-token",
      value: jwt,
      url: PORTAL_ORIGIN,
      httpOnly: false,
      sameSite: "Lax" as const,
    },
  ]);
  // Touch the portal origin so localStorage writes land correctly.
  await page.goto(PORTAL_ORIGIN);
  await page.evaluate((token) => {
    localStorage.setItem("portal-token", token);
    localStorage.setItem("portal-auth-token", token);
  }, jwt);
}
