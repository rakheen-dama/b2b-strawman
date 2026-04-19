import type { Metadata } from "next";
import { isAllowedKcUrl } from "../validate";
import { AcceptInviteRedirect } from "../redirect-client";

export const metadata: Metadata = {
  title: "Opening your invitation",
  description: "Redirecting you to complete your invitation",
};

/**
 * Continuation page for the `/accept-invite` bounce flow. Keycloak's
 * `end_session_endpoint` redirects here AFTER it has cleared its SSO cookie.
 * At this point the user has a clean cookie jar for `localhost:8180`, so
 * redirecting back to the original invite action URL no longer triggers the
 * "already authenticated as different user" collision that consumed the
 * single-use invite token.
 */
export default async function AcceptInviteContinuePage({
  searchParams,
}: {
  searchParams: Promise<{ kcUrl?: string | string[] }>;
}) {
  const { kcUrl: kcUrlRaw } = await searchParams;
  const kcUrl = Array.isArray(kcUrlRaw) ? kcUrlRaw[0] : kcUrlRaw;

  if (!isAllowedKcUrl(kcUrl)) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12 dark:bg-slate-950">
        <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
          <h1 className="font-display text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
            Invalid invitation link
          </h1>
          <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
            This invitation link is missing or malformed. Please re-open the link from the original
            email, or contact your organisation administrator for a fresh invitation.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12 dark:bg-slate-950">
      <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h1 className="font-display text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Opening your invitation&hellip;
        </h1>
        <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
          Taking you to the registration page now.
        </p>
        <AcceptInviteRedirect redirectUrl={kcUrl} />
      </div>
    </div>
  );
}
