import { getAuthContext } from "@/lib/auth";
import { InviteMemberForm } from "@/components/team/invite-member-form";
import { TeamTabs } from "@/components/team/team-tabs";
import { api } from "@/lib/api";
import type { BillingResponse } from "@/lib/internal-api";

export default async function TeamPage() {
  const { orgRole } = await getAuthContext();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  const billing = await api.get<BillingResponse>("/api/billing/subscription");

  return (
    <div className="space-y-8">
      {/* Page header */}
      <div className="flex items-center gap-3">
        <h1 className="font-display text-3xl">Team</h1>
        <span className="rounded-full bg-slate-200 px-2.5 py-0.5 text-sm text-slate-700 dark:bg-slate-800 dark:text-slate-300">
          {billing.limits.currentMembers} member{billing.limits.currentMembers !== 1 ? "s" : ""}
        </span>
      </div>

      {/* Invite section (admin only) */}
      {isAdmin && (
        <div className="rounded-lg border border-slate-200 bg-slate-50 p-6 dark:border-slate-800 dark:bg-slate-900/50">
          <h2 className="mb-4 font-semibold">Invite a team member</h2>
          <InviteMemberForm
            maxMembers={billing.limits.maxMembers}
            currentMembers={billing.limits.currentMembers}
            planTier={billing.tier}
          />
        </div>
      )}

      {/* Members / Pending Invitations tabs */}
      <TeamTabs isAdmin={isAdmin} />
    </div>
  );
}
