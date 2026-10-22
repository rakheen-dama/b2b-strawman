import { PORTAL_SESSIONS_ENV, exchangePortalSession } from "./auth";

/**
 * Epic 617B — exchange one portal magic link per Playwright run.
 *
 * The backend allows 3 magic links per contact per 5 minutes and Playwright
 * restarts the worker after each failed test, so a per-worker exchange runs
 * out of links on any run with 3+ failures. Exchanging here (once, in the
 * runner process) and passing the session through the environment lets every
 * worker reuse it. Env vars set in globalSetup are inherited by the workers.
 *
 * Only PORTAL_CONTACT_EMAIL is pre-exchanged; specs that fall back to their
 * default contact still exchange per worker. A failed exchange is not fatal —
 * the specs' own `getPortalJwt` guards report it.
 */
export default async function globalSetup(): Promise<void> {
  if (process.env.SKIP_PORTAL_BASELINES === "true") return;
  const email = process.env.PORTAL_CONTACT_EMAIL;
  if (!email) return;
  const session = await exchangePortalSession(email);
  if (session) {
    process.env[PORTAL_SESSIONS_ENV] = JSON.stringify({ [email]: session });
  }
}
