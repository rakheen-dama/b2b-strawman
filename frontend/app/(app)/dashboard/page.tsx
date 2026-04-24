import Link from "next/link";
import { redirect } from "next/navigation";
import { AUTH_MODE, getAuthContext, getSessionIdentity } from "@/lib/auth/server";

const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

export default async function DashboardRedirectPage() {
  if (AUTH_MODE === "keycloak") {
    let orgSlug: string | undefined;
    let authenticated = false;
    try {
      const ctx = await getAuthContext();
      orgSlug = ctx.orgSlug;
      authenticated = true;
    } catch {
      // Either the user is not authenticated at all (expired session) OR they
      // are authenticated but have no active org claims. getSessionIdentity()
      // below distinguishes between the two.
    }
    if (orgSlug) {
      redirect(`/org/${orgSlug}/dashboard`);
    }
    // getAuthContext failed — determine whether the user is authenticated
    // at all. If not, redirect to Keycloak login rather than rendering the
    // "Waiting for Access" pending-state card (GAP-L-20).
    let isPlatformAdmin = false;
    try {
      const identity = await getSessionIdentity();
      authenticated = true;
      isPlatformAdmin = identity.groups.includes("platform-admins");
    } catch {
      // Not authenticated — BFF said authenticated: false.
      authenticated = false;
    }
    if (!authenticated) {
      redirect(`${GATEWAY_URL}/oauth2/authorization/keycloak`);
    }
    if (isPlatformAdmin) {
      redirect("/platform-admin/access-requests");
    }
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="w-full max-w-md space-y-6 rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
          <div className="text-center">
            <h1 className="font-display text-2xl font-semibold text-slate-900">
              Waiting for Access
            </h1>
            <p className="mt-2 text-sm text-slate-500">
              Your access request is being reviewed by an administrator. You will be notified once
              your organization is ready.
            </p>
          </div>
          <div className="space-y-3 text-center">
            <Link
              href="/request-access"
              className="inline-flex w-full justify-center rounded-full bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
            >
              Submit Access Request
            </Link>
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
