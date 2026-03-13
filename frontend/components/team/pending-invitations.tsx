"use client";

import { useOrganization } from "@clerk/nextjs";
import { Mail } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { useState, useEffect } from "react";
import { formatDate } from "@/lib/format";
import {
  listInvitations,
  revokeInvitation,
} from "@/app/(app)/org/[slug]/team/actions";
import type { MappedInvitation } from "@/app/(app)/org/[slug]/team/actions";

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
    invitations: {
      pageSize: 10,
      keepPreviousData: true,
    },
    memberships: {
      pageSize: 5,
      keepPreviousData: true,
    },
  });
  const [revokingId, setRevokingId] = useState<string | null>(null);

  if (!isLoaded) {
    return (
      <div className="py-8 text-center text-sm text-slate-600 dark:text-slate-400" aria-live="polite">
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
    <div className="space-y-4">
      <InvitationTable isAdmin={isAdmin}>
        {invitations.data.map((inv) => {
          const roleInfo = ROLE_BADGES[inv.role] ?? {
            label: inv.role,
            variant: "member" as const,
          };
          return (
            <InvitationRow
              key={inv.id}
              email={inv.emailAddress}
              role={roleInfo}
              createdAt={inv.createdAt ? formatDate(inv.createdAt) : "\u2014"}
              isAdmin={isAdmin}
              isRevoking={revokingId === inv.id}
              onRevoke={() => handleRevoke(inv.id)}
            />
          );
        })}
      </InvitationTable>

      {(invitations.hasPreviousPage || invitations.hasNextPage) && (
        <div className="flex justify-center gap-4">
          <button
            disabled={
              !invitations.hasPreviousPage || invitations.isFetching
            }
            onClick={() => invitations.fetchPrevious?.()}
            className="text-sm font-medium text-slate-600 hover:text-slate-900 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-600 dark:text-slate-400 dark:hover:text-slate-200"
          >
            Previous
          </button>
          <button
            disabled={!invitations.hasNextPage || invitations.isFetching}
            onClick={() => invitations.fetchNext?.()}
            className="text-sm font-medium text-slate-600 hover:text-slate-900 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-600 dark:text-slate-400 dark:hover:text-slate-200"
          >
            Next
          </button>
        </div>
      )}
    </div>
  );
}

function KeycloakBffPendingInvitations({ isAdmin }: { isAdmin: boolean }) {
  const [invitations, setInvitations] = useState<MappedInvitation[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  useEffect(() => {
    listInvitations()
      .then(setInvitations)
      .finally(() => setIsLoaded(true));
  }, []);

  if (!isLoaded) {
    return (
      <div className="py-8 text-center text-sm text-slate-600 dark:text-slate-400">
        Loading invitations...
      </div>
    );
  }

  if (!invitations.length) {
    return (
      <EmptyState
        icon={Mail}
        title="No pending invitations"
        description="Invited members will appear here"
      />
    );
  }

  const handleRevoke = async (invitationId: string) => {
    setRevokingId(invitationId);
    try {
      const result = await revokeInvitation(invitationId);
      if (result.success) {
        // Refresh the list
        const updated = await listInvitations();
        setInvitations(updated);
      } else {
        console.error("Failed to revoke invitation:", result.error);
      }
    } catch (err) {
      console.error("Failed to revoke invitation:", err);
    } finally {
      setRevokingId(null);
    }
  };

  return (
    <InvitationTable isAdmin={isAdmin}>
      {invitations.map((inv) => {
        const roleInfo = ROLE_BADGES[inv.role] ?? {
          label: inv.role,
          variant: "member" as const,
        };
        return (
          <InvitationRow
            key={inv.id}
            email={inv.emailAddress}
            role={roleInfo}
            createdAt={inv.createdAt ? formatDate(inv.createdAt) : "\u2014"}
            isAdmin={isAdmin}
            isRevoking={revokingId === inv.id}
            onRevoke={() => handleRevoke(inv.id)}
          />
        );
      })}
    </InvitationTable>
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

// --- Shared UI components ---

function InvitationTable({
  isAdmin,
  children,
}: {
  isAdmin: boolean;
  children: React.ReactNode;
}) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="pb-3 pr-4 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Email
            </th>
            <th className="w-[100px] pb-3 pr-4 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Role
            </th>
            <th className="w-[140px] pb-3 pr-4 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Invited
            </th>
            {isAdmin && (
              <th className="w-[80px] pb-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Actions
              </th>
            )}
          </tr>
        </thead>
        <tbody>{children}</tbody>
      </table>
    </div>
  );
}

function InvitationRow({
  email,
  role,
  createdAt,
  isAdmin,
  isRevoking,
  onRevoke,
}: {
  email: string;
  role: { label: string; variant: "owner" | "admin" | "member" };
  createdAt: string;
  isAdmin: boolean;
  isRevoking: boolean;
  onRevoke: () => void;
}) {
  return (
    <tr className="border-b border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/30">
      <td className="py-3 pr-4 font-medium text-slate-900 dark:text-slate-100">
        {email}
      </td>
      <td className="py-3 pr-4">
        <Badge variant={role.variant}>{role.label}</Badge>
      </td>
      <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">
        {createdAt}
      </td>
      {isAdmin && (
        <td className="py-3">
          <button
            onClick={onRevoke}
            disabled={isRevoking}
            className="text-sm font-medium text-red-600 hover:text-red-700 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 dark:text-red-400 dark:hover:text-red-300"
          >
            {isRevoking ? "Revoking..." : "Revoke"}
          </button>
        </td>
      )}
    </tr>
  );
}

export function PendingInvitations({ isAdmin }: { isAdmin: boolean }) {
  if (AUTH_MODE === "mock") return <MockPendingInvitations />;
  if (AUTH_MODE === "keycloak")
    return <KeycloakBffPendingInvitations isAdmin={isAdmin} />;
  return <ClerkPendingInvitations isAdmin={isAdmin} />;
}
