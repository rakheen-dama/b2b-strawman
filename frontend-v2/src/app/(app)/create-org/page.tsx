import { CreateOrganization } from "@clerk/nextjs";
import { redirect } from "next/navigation";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

export default function CreateOrgPage() {
  // In mock mode, org creation is not available (orgs are pre-seeded)
  if (AUTH_MODE === "mock") {
    redirect("/org/e2e-test-org/dashboard");
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-50 dark:bg-neutral-950">
      <CreateOrganization afterCreateOrganizationUrl="/org/:slug/dashboard" />
    </div>
  );
}
