import { auth, clerkClient } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";

export default async function DashboardRedirectPage() {
  const { orgSlug, orgId, userId } = await auth();

  // User has an active org — redirect to org-scoped dashboard
  if (orgId && orgSlug) {
    redirect(`/org/${orgSlug}/dashboard`);
  }

  // No active org — check if user has any orgs
  if (userId) {
    const client = await clerkClient();
    const memberships = await client.users.getOrganizationMembershipList({
      userId,
    });

    if (memberships.data.length > 0) {
      const firstOrg = memberships.data[0].organization;
      redirect(`/org/${firstOrg.slug}/dashboard`);
    }
  }

  // No orgs at all — redirect to create org
  redirect("/create-org");
}
