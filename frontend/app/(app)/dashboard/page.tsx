import { redirect } from "next/navigation";
import { AUTH_MODE, getAuthContext } from "@/lib/auth/server";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

export default async function DashboardRedirectPage() {
  if (AUTH_MODE === "keycloak") {
    let orgSlug: string | undefined;
    try {
      const ctx = await getAuthContext();
      orgSlug = ctx.orgSlug;
    } catch {
      // no org in token
    }
    if (orgSlug) {
      redirect(`/org/${orgSlug}/dashboard`);
    }
    // No org -- show pending message
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="w-full max-w-md space-y-6 rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
          <div className="text-center">
            <h1 className="text-2xl font-display font-semibold text-slate-900">
              Waiting for Access
            </h1>
            <p className="mt-2 text-sm text-slate-500">
              Your access request is being reviewed by an administrator.
              You will be notified once your organization is ready.
            </p>
          </div>
          <div className="space-y-3 text-center">
            <a
              href="/request-access"
              className="inline-flex w-full justify-center rounded-full bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              Submit Access Request
            </a>
            <a
              href={`${GATEWAY_URL}/logout`}
              className="block text-sm text-slate-400 hover:text-slate-600"
            >
              Sign out
            </a>
          </div>
        </div>
      </div>
    );
  }

  redirect("/create-org");
}
