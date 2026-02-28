"use client";

import { useOrganization } from "@clerk/nextjs";
import { Mail } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { useState } from "react";
import { formatDate } from "@/lib/format";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

const ROLE_BADGES: Record<
  string,
  { label: string; variant: "owner" | "admin" | "member" }
> = {
  "org:owner": { label: "Owner", variant: "owner" },
  "org:admin": { label: "Admin", variant: "admin" },
  "org:member": { label: "Member", variant: "member" },
};

function ClerkPendingInvitations({ isAdmin }: { isAdmin: boolean }) {
  const { invitations, memberships, isLoaded } = useOrganization({
    invitations: { pageSize: 10, keepPreviousData: true },
    memberships: { pageSize: 5, keepPreviousData: true },
  });
  const [revokingId, setRevokingId] = useState<string | null>(null);

  if (!isLoaded) {
    return (
      <div className="py-8 text-center text-sm text-slate-500">
        Loading invitations...
      </div>
    );
  }

  if (!invitations?.data?.length) {
    return (
      <EmptyState
        icon={Mail}
        title="No pending invitations"
        description="Invited members will appear here"
      />
    );
  }

  const handleRevoke = async (invitationId: string) => {
    const invitation = invitations.data?.find(
      (inv) => inv.id === invitationId,
    );
    if (!invitation) return;

    setRevokingId(invitationId);
    try {
      await invitation.revoke();
      await Promise.all([
        invitations.revalidate?.(),
        memberships?.revalidate?.(),
      ]);
    } catch (err) {
      console.error("Failed to revoke invitation:", err);
    } finally {
      setRevokingId(null);
    }
  };

  return (
    <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200">
            <th className="px-4 pb-3 pt-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              Email
            </th>
            <th className="w-[100px] px-4 pb-3 pt-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              Role
            </th>
            <th className="w-[140px] px-4 pb-3 pt-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
              Invited
            </th>
            {isAdmin && (
              <th className="w-[80px] px-4 pb-3 pt-4 text-left text-xs font-medium uppercase tracking-wide text-slate-500">
                Actions
              </th>
            )}
          </tr>
        </thead>
        <tbody>
          {invitations.data.map((inv) => {
            const roleInfo = ROLE_BADGES[inv.role] ?? {
              label: inv.role,
              variant: "member" as const,
            };
            return (
              <tr
                key={inv.id}
                className="border-b border-slate-100 transition-colors last:border-0 hover:bg-slate-50"
              >
                <td className="px-4 py-3 font-medium text-slate-900">
                  {inv.emailAddress}
                </td>
                <td className="px-4 py-3">
                  <Badge variant={roleInfo.variant}>{roleInfo.label}</Badge>
                </td>
                <td className="px-4 py-3 text-slate-500">
                  {inv.createdAt ? formatDate(inv.createdAt) : "--"}
                </td>
                {isAdmin && (
                  <td className="px-4 py-3">
                    <button
                      onClick={() => handleRevoke(inv.id)}
                      disabled={revokingId === inv.id}
                      className="text-sm font-medium text-red-600 hover:text-red-700 disabled:opacity-50"
                    >
                      {revokingId === inv.id ? "Revoking..." : "Revoke"}
                    </button>
                  </td>
                )}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function MockPendingInvitations() {
  return (
    <EmptyState
      icon={Mail}
      title="No pending invitations"
      description="Invitations are managed through the auth provider"
    />
  );
}

export function PendingInvitations({ isAdmin }: { isAdmin: boolean }) {
  if (AUTH_MODE === "mock") return <MockPendingInvitations />;
  return <ClerkPendingInvitations isAdmin={isAdmin} />;
}
