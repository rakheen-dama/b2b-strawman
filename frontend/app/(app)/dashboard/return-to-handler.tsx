"use client";

import { useEffect } from "react";
import { consumeReturnTo } from "@/lib/auth/return-to";

/**
 * Post-login return-to read-back. The gateway always lands the user on
 * `/dashboard` (alwaysUseDefaultTargetUrl=true) after OAuth2. On mount this
 * reads + clears the persisted `kazi.returnTo`, re-validates it through the
 * allowlist, and forwards the user to their original destination.
 *
 * Renders nothing. Skips redirecting when the stored destination resolves to
 * `/dashboard` (the default) — that's where we already are, so there's nothing
 * to do and we avoid a redundant navigation.
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
