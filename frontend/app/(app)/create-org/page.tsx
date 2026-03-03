import { CreateOrganization } from "@clerk/nextjs";
import { redirect } from "next/navigation";
import { AuthPage } from "@/components/auth-page";
import { KeycloakCreateOrgForm } from "@/components/auth/keycloak-create-org-form";
import { api } from "@/lib/api";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

async function createOrg(
  name: string,
): Promise<{ slug: string; orgId: string }> {
  "use server";
  return api.post<{ slug: string; orgId: string }>("/api/orgs", { name });
}

export default function CreateOrgPage() {
  // In mock mode, org creation is not available (orgs are pre-seeded)
  if (AUTH_MODE === "mock") {
    redirect("/org/e2e-test-org/dashboard");
  }

  if (AUTH_MODE === "keycloak") {
    return (
      <AuthPage
        heading="Create your organization"
        subtitle="Set up your workspace to get started"
      >
        <KeycloakCreateOrgForm createOrgAction={createOrg} />
      </AuthPage>
    );
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50 dark:bg-slate-950">
      <CreateOrganization afterCreateOrganizationUrl="/org/:slug/dashboard" />
    </div>
  );
}
