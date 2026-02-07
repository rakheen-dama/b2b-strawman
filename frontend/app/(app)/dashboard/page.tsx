"use client";

import { useAuth, useOrganizationList } from "@clerk/nextjs";
import { redirect } from "next/navigation";

export default function DashboardRedirectPage() {
  const { orgSlug, orgId, isLoaded: authLoaded } = useAuth();
  const { isLoaded: orgListLoaded, userMemberships } = useOrganizationList({
    userMemberships: { pageSize: 1 },
  });

  if (!authLoaded || !orgListLoaded) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <p className="text-muted-foreground text-sm">Redirecting...</p>
      </div>
    );
  }

  // Active org exists — go to its dashboard
  if (orgId && orgSlug) {
    redirect(`/org/${orgSlug}/dashboard`);
  }

  // No active org — pick first membership with a valid slug
  const firstSlug = userMemberships.data?.[0]?.organization.slug;
  if (firstSlug) {
    redirect(`/org/${firstSlug}/dashboard`);
  }

  // No orgs — create one
  redirect("/create-org");
}
