"use client";

import { useEffect } from "react";
import { consumeReturnTo } from "@/lib/auth/return-to";

/**
 * Post-login return-to read-back. The gateway lands the user on `/dashboard`
 * (alwaysUseDefaultTargetUrl=true) after OAuth2, and `/dashboard` then resolves
 * to the org-scoped dashboard via the redirect chain
 * (`/dashboard` → `/org/{slug}/dashboard`, performed by the dashboard RSC in
 * keycloak mode and by the middleware in mock mode). This component is therefore
 * mounted on the org-scoped dashboard — the *real* post-login landing — so the
 * read-back fires after the chain resolves rather than on bare `/dashboard`
 * (which a normal org user never actually renders).
 *
 * On mount it reads + clears the persisted `kazi.returnTo`, re-validates it
 * through the allowlist, and forwards the user to their original destination.
 *
 * Renders nothing. Skips redirecting when the stored destination resolves to
 * `/dashboard` (the default) — that resolves back to this dashboard, so there's
 * nothing to do and we avoid a redundant navigation.
 */
export function ReturnToHandler() {
  useEffect(() => {
    const target = consumeReturnTo();
    if (target && target !== "/dashboard") {
      window.location.assign(target);
    }
  }, []);

  return null;
}
