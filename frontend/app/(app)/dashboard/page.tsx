import { auth, clerkClient } from "@clerk/nextjs/server";
import { redirect } from "next/navigation";

export default async function DashboardRedirectPage() {
  const { orgSlug, orgId, userId } = await auth();

  // Active org exists — go to its dashboard
  if (orgId && orgSlug) {
    redirect(`/org/${orgSlug}/dashboard`);
  }

  // No active org — find first membership
  if (userId) {
    const client = await clerkClient();
    const memberships = await client.organizationMemberships.getOrganizationMembershipList({
      userId,
      limit: 1,
    });
    const firstSlug = memberships.data[0]?.organization.slug;
    if (firstSlug) {
      redirect(`/org/${firstSlug}/dashboard`);
    }
  }

  // No orgs — create one
  redirect("/create-org");
}
