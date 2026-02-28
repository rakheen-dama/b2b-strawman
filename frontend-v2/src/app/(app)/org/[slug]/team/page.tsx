import { getAuthContext } from "@/lib/auth";
import { api } from "@/lib/api";
import { PageHeader } from "@/components/layout/page-header";
import { InviteMemberForm } from "@/components/team/invite-member-form";
import { TeamTabs } from "@/components/team/team-tabs";
import type { BillingResponse } from "@/lib/internal-api";

export default async function TeamPage({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const { orgRole } = await getAuthContext();
  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  let billing: BillingResponse | null = null;
  try {
    billing = await api.get<BillingResponse>("/api/billing/subscription");
  } catch {
    // Non-fatal
  }

  const memberCount = billing?.limits.currentMembers ?? 0;

  return (
    <div className="space-y-8">
      <PageHeader
        title="Team"
        description="Manage your organization members and invitations."
        count={memberCount}
      />

      {isAdmin && billing && (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-6">
          <h2 className="mb-4 text-sm font-semibold text-slate-900">
            Invite a team member
          </h2>
          <InviteMemberForm
            maxMembers={billing.limits.maxMembers}
            currentMembers={billing.limits.currentMembers}
            planTier={billing.tier}
            orgSlug={slug}
          />
        </div>
      )}

      <TeamTabs isAdmin={isAdmin} />
    </div>
  );
}
