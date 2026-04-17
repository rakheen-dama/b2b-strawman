"use client";

import { useEffect } from "react";

/**
 * Client-only redirector used by both `/accept-invite` and
 * `/accept-invite/continue`. The page (server component) is responsible for
 * validating the redirect target against the allow-list before this component
 * is rendered — this component just performs the replace() as soon as it
 * mounts in the browser.
 *
 * `useEffect` ensures the redirect only runs client-side (not during SSR /
 * build). `window.location.replace` is used instead of `router.replace` so
 * the bounce page is not kept in the browser history — users pressing "back"
 * from the destination should skip the bounce page entirely.
 */
export function AcceptInviteRedirect({ redirectUrl }: { redirectUrl: string }) {
  useEffect(() => {
    window.location.replace(redirectUrl);
  }, [redirectUrl]);

  // No visible output — the parent page renders the loading copy. A noscript
  // fallback lets users without JS follow the redirect manually.
  return (
    <noscript>
      <p className="mt-4 text-sm text-slate-600 dark:text-slate-400">
        JavaScript is required to continue automatically.{" "}
        <a
          href={redirectUrl}
          className="font-medium text-teal-600 underline dark:text-teal-400"
        >
          Click here to continue
        </a>
        .
      </p>
    </noscript>
  );
}
