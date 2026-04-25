import type { Metadata } from "next";
import { AcceptInviteRedirect } from "@/app/accept-invite/redirect-client";

export const metadata: Metadata = {
  title: "Finishing sign-in",
  description: "Signing you in to your new organization",
};

const GATEWAY_URL = (process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443").replace(
  /\/+$/,
  ""
);

// Always forward to the gateway OAuth2 kickoff. The gateway starts a fresh
// auth-code flow for client=gateway-bff; KC skips the login screen (SSO cookie
// from the just-completed registration is still valid) and issues a code for
// gateway-bff. The gateway success handler sets KC_LAST_LOGIN_SUB; the L-22
// middleware then verifies the handoff against /bff/me. See
// qa_cycle/fix-specs/GAP-L-22-regression.md.
//
// Any query params KC appended to this redirect (e.g. ?code=…&session_state=…)
// are intentionally dropped — that code is for client=account and has no
// consumer. The gateway flow obtains its own fresh code for gateway-bff.
const OAUTH2_KICKOFF_URL = `${GATEWAY_URL}/oauth2/authorization/keycloak`;

export default function AcceptInviteCompletePage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 px-4 py-12 dark:bg-slate-950">
      <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm dark:border-slate-800 dark:bg-slate-900">
        <h1 className="font-display text-xl font-semibold tracking-tight text-slate-900 dark:text-slate-100">
          Finishing sign-in&hellip;
        </h1>
        <p className="mt-3 text-sm text-slate-600 dark:text-slate-400">
          Welcome aboard — signing you in to your dashboard.
        </p>
        <AcceptInviteRedirect redirectUrl={OAUTH2_KICKOFF_URL} />
      </div>
    </div>
  );
}
