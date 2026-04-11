import Link from "next/link";
import { redirect } from "next/navigation";
import { getSessionIdentity } from "@/lib/auth/server";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";
const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

export default async function CreateOrgPage() {
  if (AUTH_MODE === "mock") {
    redirect("/org/e2e-test-org/dashboard");
  }

  // Keycloak mode — platform admins should go to the admin panel
  let isPlatformAdmin = false;
  try {
    const identity = await getSessionIdentity();
    isPlatformAdmin = identity.groups.includes("platform-admins");
  } catch {
    // not authenticated or no groups
  }
  if (isPlatformAdmin) {
    redirect("/platform-admin/access-requests");
  }
  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <div className="w-full max-w-md space-y-6 rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
        <div className="text-center">
          <h1 className="font-display text-2xl font-semibold text-slate-900">
            Organization Access
          </h1>
          <p className="mt-2 text-sm text-slate-500">
            Organization creation is managed by administrators. Please submit an access request to
            get started.
          </p>
        </div>
        <div className="text-center">
          <Link
            href="/request-access"
            className="inline-flex w-full justify-center rounded-full bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            Request Access
          </Link>
        </div>
        <div className="text-center">
          <a href={`${GATEWAY_URL}/logout`} className="text-sm text-slate-400 hover:text-slate-600">
            Sign out
          </a>
        </div>
      </div>
    </div>
  );
}
