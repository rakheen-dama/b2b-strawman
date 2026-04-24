import { NextResponse } from "next/server";
import type { NextMiddleware, NextRequest } from "next/server";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

const PUBLIC_ROUTES = [
  "/",
  "/sign-in(.*)",
  "/sign-up(.*)",
  "/api/webhooks(.*)",
  "/portal(.*)",
  "/mock-login(.*)",
  "/request-access(.*)",
  "/accept-invite(.*)",
];

/**
 * Cookie set by the gateway's OAuth2 success handler (see GatewaySecurityConfig).
 * Contains the `sub` of the user that just completed OAuth2 login. Consumed by the
 * middleware on the next authenticated request to detect GAP-L-22 (stale SESSION
 * handoff after registration).
 */
const KC_LAST_LOGIN_SUB_COOKIE = "KC_LAST_LOGIN_SUB";

/** Timeout for the verification /bff/me call. Keep tight — blocks the request. */
const BFF_ME_VERIFY_TIMEOUT_MS = 3_000;

function isPublicRoute(request: NextRequest): boolean {
  const pathname = request.nextUrl.pathname;
  return PUBLIC_ROUTES.some((route) => {
    const pattern = route.replace("(.*)", ".*");
    return new RegExp(`^${pattern}$`).test(pathname);
  });
}

/**
 * Auth middleware factory.
 * Returns Keycloak BFF middleware for production or mock middleware for E2E tests.
 */
export function createAuthMiddleware(): NextMiddleware {
  if (AUTH_MODE === "mock") {
    return createMockMiddleware();
  }
  return createKeycloakMiddleware();
}

function decodeMockJwtPayload(token: string): Record<string, unknown> | null {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    return JSON.parse(atob(base64));
  } catch {
    return null;
  }
}

function createKeycloakMiddleware(): NextMiddleware {
  return async (request: NextRequest) => {
    // Public routes skip auth check
    if (isPublicRoute(request)) {
      return NextResponse.next();
    }

    // Check for gateway session cookie
    const sessionCookie = request.cookies.get("SESSION");
    if (!sessionCookie) {
      // Redirect to gateway OAuth2 authorization endpoint
      const gatewayLoginUrl = new URL("/oauth2/authorization/keycloak", GATEWAY_URL);
      return NextResponse.redirect(gatewayLoginUrl.toString());
    }

    // GAP-L-22: detect stale SESSION handoff after fresh OAuth2 login.
    // The gateway's OAuth2 success handler sets KC_LAST_LOGIN_SUB to the newly
    // authenticated user's `sub`. If the SESSION cookie still points at a
    // different principal (because Spring Security reused a pre-existing
    // session instead of rotating it), /bff/me will return a mismatching
    // userId. In that case we force-clear the session and re-run the OIDC
    // flow with `prompt=login` so the new user's credentials re-auth cleanly.
    const lastLoginSubCookie = request.cookies.get(KC_LAST_LOGIN_SUB_COOKIE);
    if (lastLoginSubCookie?.value) {
      const mismatchResponse = await verifySessionHandoff(
        sessionCookie.value,
        lastLoginSubCookie.value
      );
      if (mismatchResponse) {
        return mismatchResponse;
      }
      // Session matches the expected principal (or verification failed —
      // fail-open): clear the one-shot signal cookie and pass through.
      const res = NextResponse.next();
      clearLastLoginSubCookie(res);
      return res;
    }

    // /dashboard redirect: pass through — let server component resolve org slug
    // via getAuthContext() which calls /bff/me
    return NextResponse.next();
  };
}

/**
 * Verifies that the current BFF SESSION is for the same user that the gateway
 * just authenticated (as recorded in the KC_LAST_LOGIN_SUB cookie). Returns a
 * redirect response when a mismatch is detected, or `null` when the session is
 * valid / the check couldn't be completed (fail-open — don't break routing).
 */
async function verifySessionHandoff(
  sessionCookieValue: string,
  expectedSub: string
): Promise<NextResponse | null> {
  let actualSub: string | null = null;
  try {
    const response = await fetch(`${GATEWAY_URL}/bff/me`, {
      headers: { cookie: `SESSION=${sessionCookieValue}` },
      cache: "no-store",
      signal: AbortSignal.timeout(BFF_ME_VERIFY_TIMEOUT_MS),
    });
    if (!response.ok) {
      // Can't verify — fail open, let downstream handlers deal with it.
      return null;
    }
    const body = (await response.json()) as { authenticated?: boolean; userId?: string | null };
    if (!body.authenticated || !body.userId) {
      // Not authenticated after a "fresh login" — something's genuinely off;
      // send the user back through login rather than rendering a stale shell.
      return redirectToKeycloakLogin(/* clearSession */ true);
    }
    actualSub = body.userId;
  } catch {
    // Network error / timeout — fail open.
    return null;
  }

  if (actualSub !== expectedSub) {
    // Stale handoff detected — the SESSION cookie is bound to a different
    // principal than the user who just completed OAuth2 login. Kick them
    // through an explicit logout + re-login to reset state cleanly.
    return redirectToKeycloakLogin(/* clearSession */ true);
  }
  return null;
}

/**
 * Clears the stale SESSION cookie on the frontend origin and redirects the
 * browser through a fresh OAuth2 authorization flow with `prompt=login`, so
 * Keycloak forces credential re-entry and the gateway rotates the session id
 * via `sessionFixation.changeSessionId`. Spring Security's `/logout` endpoint
 * is POST + CSRF protected, so we can't redirect to it as a GET — clearing
 * the cookie + forcing `prompt=login` gives the same end-state without the
 * CSRF dance and guarantees the user re-authenticates.
 */
function redirectToKeycloakLogin(clearSession: boolean): NextResponse {
  const loginUrl = new URL("/oauth2/authorization/keycloak", GATEWAY_URL);
  // `prompt=login` is passed as an additional OIDC authorization parameter —
  // Spring Security's `authorization-request-resolver` forwards query params
  // it does not recognize onto the KC authorization URL.
  loginUrl.searchParams.set("prompt", "login");
  const response = NextResponse.redirect(loginUrl.toString());
  if (clearSession) {
    // Delete the stale SESSION cookie on the frontend origin. The gateway
    // session row on SPRING_SESSION still exists, but it will be replaced
    // when the fresh OAuth2 flow completes (sessionFixation.changeSessionId
    // rotates the ID). Meanwhile the user's browser no longer presents the
    // stale ID on subsequent requests.
    response.cookies.set("SESSION", "", { path: "/", maxAge: 0 });
  }
  clearLastLoginSubCookie(response);
  return response;
}

function clearLastLoginSubCookie(response: NextResponse): void {
  response.cookies.set(KC_LAST_LOGIN_SUB_COOKIE, "", { path: "/", maxAge: 0 });
}

function createMockMiddleware(): NextMiddleware {
  return (request: NextRequest) => {
    const { pathname } = request.nextUrl;

    // Public routes skip auth check
    if (isPublicRoute(request)) {
      return NextResponse.next();
    }

    // Check for mock auth cookie
    const token = request.cookies.get("mock-auth-token")?.value;
    if (!token) {
      const loginUrl = new URL("/mock-login", request.url);
      loginUrl.searchParams.set("redirect", pathname);
      return NextResponse.redirect(loginUrl);
    }

    // Redirect /dashboard to org-scoped dashboard
    if (pathname === "/dashboard") {
      const payload = decodeMockJwtPayload(token);
      const organization = payload?.organization as string[] | undefined;
      const orgSlug = organization?.[0];
      if (orgSlug) {
        return NextResponse.redirect(new URL(`/org/${orgSlug}/dashboard`, request.url));
      }
      // No org in token — send back to mock login
      const loginUrl = new URL("/mock-login", request.url);
      loginUrl.searchParams.set("redirect", pathname);
      return NextResponse.redirect(loginUrl);
    }

    return NextResponse.next();
  };
}
