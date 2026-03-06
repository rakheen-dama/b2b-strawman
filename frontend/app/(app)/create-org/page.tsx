import { CreateOrganization } from "@clerk/nextjs";
import { redirect } from "next/navigation";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

export default function CreateOrgPage() {
  if (AUTH_MODE === "mock") {
    redirect("/org/e2e-test-org/dashboard");
  }

  if (AUTH_MODE === "keycloak") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-neutral-50 dark:bg-neutral-950">
        <div className="max-w-md text-center space-y-4 p-8 bg-white rounded-xl shadow-sm border border-slate-200">
          <h1 className="text-2xl font-display font-semibold text-slate-900">No organization yet</h1>
          <p className="text-slate-500">
            Your account is not part of an organization. Ask your administrator to invite you, or contact support.
          </p>
          <a
            href="http://localhost:8443/logout"
            className="inline-block text-sm text-slate-400 hover:text-slate-600"
          >
            Sign out
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-50 dark:bg-neutral-950">
      <CreateOrganization afterCreateOrganizationUrl="/org/:slug/dashboard" />
    </div>
  );
}
