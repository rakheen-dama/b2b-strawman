import { CreateOrganization } from "@clerk/nextjs";
import { redirect } from "next/navigation";
import { CreateOrgForm } from "./create-org-form";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";
const GATEWAY_URL = process.env.NEXT_PUBLIC_GATEWAY_URL || "http://localhost:8443";

export default function CreateOrgPage() {
  if (AUTH_MODE === "mock") {
    redirect("/org/e2e-test-org/dashboard");
  }

  if (AUTH_MODE === "keycloak") {
    return (
      <div className="flex min-h-screen items-center justify-center bg-slate-50">
        <div className="w-full max-w-md space-y-6 rounded-xl border border-slate-200 bg-white p-8 shadow-sm">
          <div className="text-center">
            <h1 className="text-2xl font-display font-semibold text-slate-900">
              Create your organization
            </h1>
            <p className="mt-2 text-sm text-slate-500">
              Set up your workspace to get started with DocTeams.
            </p>
          </div>
          <CreateOrgForm />
          <div className="text-center">
            <a
              href={`${GATEWAY_URL}/logout`}
              className="text-sm text-slate-400 hover:text-slate-600"
            >
              Sign out
            </a>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <CreateOrganization afterCreateOrganizationUrl="/org/:slug/dashboard" />
    </div>
  );
}
