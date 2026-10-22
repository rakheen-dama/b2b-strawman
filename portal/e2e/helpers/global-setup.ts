import { PORTAL_SESSIONS_ENV, exchangePortalSession } from "./auth";

const BACKEND_URL = process.env.BACKEND_URL || "http://localhost:8081";
const ORG_SLUG = process.env.PORTAL_ORG_SLUG || "e2e-test-org";

/**
 * Epic 617B — exchange one portal magic link per Playwright run.
 *
 * The backend allows 3 magic links per contact per 5 minutes and Playwright
 * restarts the worker after each failed test, so a per-worker exchange runs
 * out of links on any run with 3+ failures. Exchanging here (once, in the
 * runner process) and passing the session through the environment lets every
 * worker reuse it. Env vars set in globalSetup are inherited by the workers.
 *
 * When PORTAL_CONTACT_EMAIL is set, a failed exchange is fatal: the run fails
 * here with a clear error instead of every spec skipping with "Could not
 * obtain portal JWT", which would read like a data skip.
 *
 * When PORTAL_CONTACT_EMAIL is unset this is a no-op; specs that fall back to
 * their default contact exchange once per worker and their own `getPortalJwt`
 * guards report a failure.
 */
export default async function globalSetup(): Promise<void> {
  if (process.env.SKIP_PORTAL_BASELINES === "true") return;
  const email = process.env.PORTAL_CONTACT_EMAIL;
  if (!email) return;
  const session = await exchangePortalSession(email);
  if (!session) {
    throw new Error(
      `Portal globalSetup: magic-link exchange failed for ${email} ` +
        `(org ${ORG_SLUG}, backend ${BACKEND_URL}). Check the backend is up, ` +
        `the contact is seeded, and the magic-link cap (3 per contact per ` +
        `5 min) has not been hit by a recent run.`,
    );
  }
  process.env[PORTAL_SESSIONS_ENV] = JSON.stringify({ [email]: session });
}
