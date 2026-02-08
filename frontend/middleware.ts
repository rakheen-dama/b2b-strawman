import { clerkMiddleware, createRouteMatcher } from "@clerk/nextjs/server";
import { NextResponse } from "next/server";

const isPublicRoute = createRouteMatcher([
  "/",
  "/sign-in(.*)",
  "/sign-up(.*)",
  "/api/webhooks(.*)",
]);

export default clerkMiddleware(
  async (auth, request) => {
    if (!isPublicRoute(request)) {
      await auth.protect();
    }

    // Redirect /dashboard to org-scoped dashboard
    if (request.nextUrl.pathname === "/dashboard") {
      const { orgSlug } = await auth();
      if (orgSlug) {
        return NextResponse.redirect(new URL(`/org/${orgSlug}/dashboard`, request.url));
      }
      return NextResponse.redirect(new URL("/create-org", request.url));
    }
  },
  {
    organizationSyncOptions: {
      organizationPatterns: ["/org/:slug", "/org/:slug/(.*)"],
    },
  }
);

export const config = {
  matcher: [
    // Skip Next.js internals and static files
    "/((?!_next|[^?]*\\.(?:html?|css|js(?!on)|jpe?g|webp|png|gif|svg|ttf|woff2?|ico|csv|docx?|xlsx?|zip|webmanifest)).*)",
    // Always run for API routes
    "/(api|trpc)(.*)",
  ],
};
