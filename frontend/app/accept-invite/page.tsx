import type { Metadata } from "next";
import { isAllowedKcUrl } from "./validate";
import { AcceptInviteRedirect } from "./redirect-client";

export const metadata: Metadata = {
  title: "Preparing your invitation",
  description: "Clearing your session before completing your invitation",
};

// The KC logout endpoint. Hard-coded for local dev; wire through env when the
// bounce page ships to staging/prod.
const KC_LOGOUT_ENDPOINT =
  "http://localhost:8180/realms/docteams/protocol/openid-connect/logout";
const BOUNCE_CONTINUE_URL = "http://localhost:3000/accept-invite/continue";
const KC_CLIENT_ID = "gateway-bff";

export default async function AcceptInvitePage({
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
            This invitation link is missing or malformed. Please re-open the link
            from the original email, or contact your organisation administrator
            for a fresh invitation.
          </p>
        </div>
      </div>
    );
  }

  // Build the KC logout URL: clears the KC SSO cookie, then redirects back to
  // our /continue page which forwards to the original invite URL with a clean
  // cookie jar.
  const continueUrl = `${BOUNCE_CONTINUE_URL}?kcUrl=${encodeURIComponent(kcUrl)}`;
  const logoutUrl =
    `${KC_LOGOUT_ENDPOINT}?client_id=${encodeURIComponent(KC_CLIENT_ID)}` +
    `&post_logout_redirect_uri=${encodeURIComponent(continueUrl)}`;

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12 dark:bg-slate-950">
      <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h1 className="font-display text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Preparing your invitation&hellip;
        </h1>
        <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
          We&apos;re clearing any previous session so your invitation opens
          cleanly. You&apos;ll be redirected in a moment.
        </p>
        <AcceptInviteRedirect redirectUrl={logoutUrl} />
      </div>
    </div>
  );
}
