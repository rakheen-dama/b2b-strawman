/**
 * Shared session-expiry funnel.
 *
 * One detector + two redirect builders (server + client) used by every
 * authenticated fetch entry-point (server-action `apiRequest`, the `/bff/me`
 * probe, middleware, and client-component fetches). The goal is that an expired
 * session always lands the user on the branded `/sign-in?reason=expired` route
 * with a safe `returnTo`, rather than surfacing a raw error or a dangling 404.
 *
 * NOT `server-only`: `isSessionExpired` and `clientRedirectToReLogin` are used
 * from client components; `redirectToReLogin` is server-side. The `redirect()`
 * import from `next/navigation` is a no-op shim on the client.
 */

import { redirect } from "next/navigation";
import { safeReturnTo } from "./return-to";

type ReLoginReason = "expired";

/** sessionStorage key cleared before a client-side re-login redirect. */
const RETURN_TO_STORAGE_KEY = "kazi.returnTo";

/**
 * Build the branded re-login URL: `/sign-in?reason=<reason>&returnTo=<safe>`.
 * `returnTo` is always passed through {@link safeReturnTo} so unvalidated input
 * can never be reflected.
 */
function buildSignInUrl(returnTo: string, reason: ReLoginReason): string {
  const params = new URLSearchParams({ reason, returnTo: safeReturnTo(returnTo) });
  return `/sign-in?${params.toString()}`;
}

/**
 * Returns `true` when a response indicates the authenticated session has
 * expired (or never was valid). Treats as expired:
 * - a `401` status (direct, or the manual-redirect 3xx already mapped to
 *   `ApiError(401)` upstream)
 * - a `null` response (failed / aborted fetch where the caller couldn't read a
 *   status — treated as expired to fail the user gracefully into re-login)
 * - a `/bff/me` body with `authenticated: false`
 *
 * Returns `false` for a `200` / authenticated response.
 */
export function isSessionExpired(
  res: Response | { status: number } | { authenticated: boolean } | null
): boolean {
  if (res === null) {
    return true;
  }
  if ("authenticated" in res) {
    return res.authenticated === false;
  }
  return res.status === 401;
}

/**
 * Server-side graceful re-login redirect. Throws a typed `NEXT_REDIRECT` via
 * `next/navigation`'s `redirect()` (hence the `never` return type — it never
 * returns normally). Never wrap a call to this in a broad try/catch that
 * swallows the redirect error.
 */
export function redirectToReLogin(returnTo: string, reason: ReLoginReason = "expired"): never {
  redirect(buildSignInUrl(returnTo, reason));
}

/**
 * Client-side graceful re-login. Clears any cached `/bff/me` client identity
 * (the sessionStorage return-to key) and hard-navigates to the branded
 * `/sign-in` route. Hard navigation (not router.push) guarantees the stale
 * client tree is torn down.
 */
export function clientRedirectToReLogin(returnTo: string, reason: ReLoginReason = "expired"): void {
  if (typeof window === "undefined") {
    return;
  }
  try {
    window.sessionStorage.removeItem(RETURN_TO_STORAGE_KEY);
  } catch {
    // ignore storage failures
  }
  window.location.assign(buildSignInUrl(returnTo, reason));
}
