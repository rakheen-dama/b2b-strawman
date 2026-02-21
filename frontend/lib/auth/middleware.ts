import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";
import { NextResponse } from "next/server";
import type { NextMiddleware } from "next/server";

const isPublicRoute = createRouteMatcher([
  "/",
  "/sign-in(.*)",
  "/sign-up(.*)",
  "/api/webhooks(.*)",
  "/portal(.*)",
]);

/**
 * Auth middleware factory.
 * Returns Clerk middleware for production, mock middleware for E2E tests.
 * Epic 141 will add the mock branch — for now, always returns Clerk middleware.
 */
export function createAuthMiddleware(): NextMiddleware {
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
