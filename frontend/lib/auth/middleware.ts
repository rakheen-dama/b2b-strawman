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
  return (request: NextRequest) => {
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

    // /dashboard redirect: pass through — let server component resolve org slug
    // via getAuthContext() which calls /bff/me
    return NextResponse.next();
  };
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
