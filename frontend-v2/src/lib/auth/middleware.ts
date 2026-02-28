import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";
import { NextResponse } from "next/server";
import type { NextMiddleware, NextRequest } from "next/server";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

// createRouteMatcher is a pure regex-like function with no Clerk runtime dependency.
// Both Clerk and mock paths need the same route matching, so this coupling is acceptable.
const isPublicRoute = createRouteMatcher([
  "/",
  "/sign-in(.*)",
  "/sign-up(.*)",
  "/api/webhooks(.*)",
  "/portal(.*)",
  "/mock-login(.*)",
]);

/**
 * Auth middleware factory.
 * Returns Clerk middleware for production, mock middleware for E2E tests.
 */
export function createAuthMiddleware(): NextMiddleware {
  if (AUTH_MODE === "mock") {
    return createMockMiddleware();
  }
  return createClerkMiddleware();
}

function createClerkMiddleware(): NextMiddleware {
  return clerkMiddleware(
    async (auth, request) => {
      if (!isPublicRoute(request)) {
        await auth.protect();
      }

      // Redirect /dashboard to org-scoped dashboard
      if (request.nextUrl.pathname === "/dashboard") {
        const { orgSlug } = await auth();
        if (orgSlug) {
          return NextResponse.redirect(
            new URL(`/org/${orgSlug}/dashboard`, request.url),
          );
        }
        return NextResponse.redirect(new URL("/create-org", request.url));
      }
    },
    {
      organizationSyncOptions: {
        organizationPatterns: ["/org/:slug", "/org/:slug/(.*)"],
      },
    },
  );
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

    // Redirect /dashboard to org-scoped dashboard (mirrors Clerk middleware)
    if (pathname === "/dashboard") {
      const payload = decodeMockJwtPayload(token);
      const org = payload?.o as { slg?: string } | undefined;
      if (org?.slg) {
        return NextResponse.redirect(
          new URL(`/org/${org.slg}/dashboard`, request.url),
        );
      }
      // No org in token — send back to mock login
      const loginUrl = new URL("/mock-login", request.url);
      loginUrl.searchParams.set("redirect", pathname);
      return NextResponse.redirect(loginUrl);
    }

    return NextResponse.next();
  };
}
