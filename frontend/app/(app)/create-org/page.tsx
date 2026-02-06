import { CreateOrganization } from "@clerk/nextjs";

export default function CreateOrgPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-neutral-50 dark:bg-neutral-950">
      <CreateOrganization afterCreateOrganizationUrl="/org/:slug/dashboard" />
    </div>
  );
}
