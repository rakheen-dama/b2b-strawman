import { auth } from "@clerk/nextjs/server";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { MemberList } from "@/components/team/member-list";
import { InviteMemberForm } from "@/components/team/invite-member-form";
import { PendingInvitations } from "@/components/team/pending-invitations";
import { api } from "@/lib/api";
import type { BillingResponse } from "@/lib/internal-api";

export default async function TeamPage() {
  const { orgRole } = await auth();

  const isAdmin = orgRole === "org:admin" || orgRole === "org:owner";

  const billing = await api.get<BillingResponse>("/api/billing/subscription");

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Team</h1>
        <p className="text-muted-foreground mt-1 text-sm">
          Manage your organization&apos;s members and invitations.
        </p>
      </div>

      {isAdmin && (
        <div className="rounded-lg border p-4">
          <h2 className="mb-3 text-sm font-semibold">Invite a team member</h2>
          <InviteMemberForm
            maxMembers={billing.limits.maxMembers}
            currentMembers={billing.limits.currentMembers}
          />
        </div>
      )}

      <Tabs defaultValue="members">
        <TabsList>
          <TabsTrigger value="members">Members</TabsTrigger>
          <TabsTrigger value="invitations">Invitations</TabsTrigger>
        </TabsList>
        <TabsContent value="members">
          <MemberList />
        </TabsContent>
        <TabsContent value="invitations">
          <PendingInvitations isAdmin={isAdmin} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
