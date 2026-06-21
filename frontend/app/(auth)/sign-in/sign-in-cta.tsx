"use client";

import { persistReturnTo } from "@/lib/auth/return-to";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

/**
 * Sign-in CTA: persists the (already-validated) returnTo to sessionStorage,
 * then hard-navigates to the gateway's Keycloak OAuth2 authorization endpoint.
 * The gateway lands users back on `/dashboard` (alwaysUseDefaultTargetUrl), so
 * the dashboard read-back component consumes the persisted returnTo and
 * forwards the user to their original destination.
 *
 * `returnTo` is a serializable string passed from the Server Component — no
 * function/callback crosses the RSC boundary.
 */
export function SignInCta({ returnTo }: { returnTo: string }) {
  function handleSignIn() {
    persistReturnTo(returnTo);
    window.location.assign(`${GATEWAY_URL}/oauth2/authorization/keycloak`);
  }

  return (
    <button
      type="button"
      onClick={handleSignIn}
      className="inline-flex w-full items-center justify-center rounded-full bg-teal-600 px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-teal-500"
    >
      Continue to sign in
    </button>
  );
}
