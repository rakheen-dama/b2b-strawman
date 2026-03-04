import { redirect } from "next/navigation";
import { isRedirectError } from "next/dist/client/components/redirect-error";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

// Primary redirect logic lives in proxy.ts (instant, no page render).
// This page only renders if proxy doesn't redirect — either because the user
// is in Keycloak mode (middleware can't decrypt the session cookie) or has
// no active org in Clerk mode.
export default async function DashboardRedirectPage() {
  if (AUTH_MODE === "keycloak") {
    // Try to resolve org from the JWT. If the SPI auto-selected an org
    // (single-org user), getAuthContext() succeeds and we redirect.
    try {
      const { getAuthContext } = await import("@/lib/auth/server");
      const ctx = await getAuthContext();
      if (ctx.orgSlug) {
        redirect(`/org/${ctx.orgSlug}/dashboard`);
      }
    } catch (e) {
      // redirect() throws internally — re-throw so Next.js handles it
      if (isRedirectError(e)) throw e;
      // No org claims in JWT — user needs to create or select an org
    }
  }

  redirect("/create-org");
}
