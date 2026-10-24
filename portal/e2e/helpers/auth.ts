import type { Page } from "@playwright/test";

/**
 * Epic 499B — Portal auth helpers used by Playwright visual-baseline specs.
 *
 * Ports the magic-link / exchange flow (originally in the firm-side e2e
 * portal specs, since removed) into the portal's own e2e tree so the portal baselines can run without
 * reaching into the firm-side e2e directory.
 *
 * Epic 617B: the session is written to the localStorage keys the portal
 * actually reads (`portal/lib/auth.ts`: `portal_jwt` + `portal_customer`,
 * plus the `portal_last_org_id` routing hint). The previous keys
 * (`portal-auth-token` cookie, `portal-token` / `portal-auth-token`
 * localStorage) had no reader, so every spec landed on /login.
 *
 * Magic links are capped at 3 per contact per 5 minutes
 * (MagicLinkService.MAX_TOKENS_PER_5_MINUTES) and request-link calls at 3 per
 * email per 15 minutes across firms (PortalLoginRateLimiter; in-memory,
 * cleared only by restarting the backend; the 4th call is a 429). The portal
 * JWT lives 1 h (PortalJwtService.SESSION_TTL). `exchangePortalSession`
 * sends `orgId: ORG_SLUG`, the request-link fast path (ADR-351). Playwright restarts the worker after every
 * failed test (which drops module state), so `global-setup.ts` exchanges once
 * per run for PORTAL_CONTACT_EMAIL and hands the session to every worker via
 * `process.env[PORTAL_SESSIONS_ENV]`. Other emails fall back to one exchange
 * per worker.
 */

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";
const PORTAL_BASE_URL =
  process.env.PLAYWRIGHT_BASE_URL || "http://localhost:3002";
const PORTAL_ORIGIN = new URL(PORTAL_BASE_URL).origin;

/** Env var global-setup.ts uses to pass pre-exchanged sessions to workers. */
export const PORTAL_SESSIONS_ENV = "PORTAL_E2E_SESSIONS";

export interface PortalSession {
  token: string;
  clientId: string;
  clientName: string;
  email: string;
  orgId: string;
}

const sessions = new Map<string, Promise<PortalSession | null>>();
const byToken = new Map<string, PortalSession>();

function sessionFromGlobalSetup(email: string): PortalSession | null {
  const raw = process.env[PORTAL_SESSIONS_ENV];
  if (!raw) return null;
  try {
    const all = JSON.parse(raw) as Record<string, PortalSession>;
    return all[email] ?? null;
  } catch {
    return null;
  }
}

/**
 * Request a magic link for `email` and exchange it for a portal session.
 * Returns `null` when either endpoint fails, the response shape is unexpected,
 * or a network/DNS/timeout error is thrown (fetch rejects on network failures).
 */
export async function exchangePortalSession(
  email: string,
): Promise<PortalSession | null> {
  try {
    const linkRes = await fetch(`${BACKEND_URL}/portal/auth/request-link`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, orgId: ORG_SLUG }),
    });
    if (!linkRes.ok) return null;
    // MagicLinkResponse(message, magicLink) — magicLink is
    // "/auth/exchange?token=…&orgId=…" (dev/test/e2e profiles only).
    const { magicLink } = await linkRes.json();
    const token = magicLink?.split("token=").pop()?.split("&")[0];
    if (!token) return null;

    const exchangeRes = await fetch(`${BACKEND_URL}/portal/auth/exchange`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ token, orgId: ORG_SLUG }),
    });
    if (!exchangeRes.ok) return null;
    // PortalTokenResponse(token, clientId, clientName)
    const data = await exchangeRes.json();
    if (!data.token) return null;
    return {
      token: data.token,
      clientId: data.clientId,
      clientName: data.clientName,
      email,
      orgId: ORG_SLUG,
    };
  } catch {
    // Network failures (DNS, timeout, connection refused) should not escape —
    // the contract is Promise<PortalSession | null>; callers handle null
    // (getPortalJwt returns null, global-setup.ts throws).
    return null;
  }
}

/**
 * Returns a portal JWT for `email`: the global-setup session when there is
 * one, else a magic-link exchange done at most once per worker (the result —
 * including a failure — is cached).
 */
export async function getPortalJwt(email: string): Promise<string | null> {
  let pending = sessions.get(email);
  if (!pending) {
    const preExchanged = sessionFromGlobalSetup(email);
    pending = preExchanged
      ? Promise.resolve(preExchanged)
      : exchangePortalSession(email);
    sessions.set(email, pending);
  }
  const session = await pending;
  if (!session) return null;
  byToken.set(session.token, session);
  return session.token;
}

/**
 * Authenticate the Playwright browser as a portal contact by writing the
 * localStorage keys `portal/lib/auth.ts` reads (`portal_jwt`,
 * `portal_customer`, `portal_last_org_id`). Navigates to the portal origin
 * first so the writes land on the correct origin.
 */
export async function loginAsPortalContact(
  page: Page,
  jwt: string,
): Promise<void> {
  const session = byToken.get(jwt);
  if (!session) {
    throw new Error("loginAsPortalContact: jwt not issued by getPortalJwt");
  }
  await page.goto(PORTAL_ORIGIN + "/login");
  await page.evaluate((s) => {
    localStorage.setItem("portal_jwt", s.token);
    localStorage.setItem(
      "portal_customer",
      JSON.stringify({
        id: s.clientId,
        name: s.clientName,
        email: s.email,
        orgId: s.orgId,
      }),
    );
    localStorage.setItem("portal_last_org_id", s.orgId);
  }, session);
}
