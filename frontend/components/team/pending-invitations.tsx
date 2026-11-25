"use client";

import { Mail } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { useState, useEffect } from "react";
import { formatDate } from "@/lib/format";
import { listInvitations, revokeInvitation } from "@/app/(app)/org/[slug]/team/invitation-actions";
import type { MappedInvitation } from "@/app/(app)/org/[slug]/team/invitation-actions";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";

const REVOKE_FAILED = "Could not revoke the invitation. Please try again.";

const ROLE_BADGES: Record<string, { label: string; variant: "owner" | "admin" | "member" }> = {
  "org:owner": { label: "Owner", variant: "owner" },
  "org:admin": { label: "Admin", variant: "admin" },
  "org:member": { label: "Member", variant: "member" },
};

function KeycloakBffPendingInvitations({ isAdmin }: { isAdmin: boolean }) {
  const [invitations, setInvitations] = useState<MappedInvitation[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  // FR-8: the backend refuses a revoke it cannot make safe (400 "Invitation not revoked" when
  // Keycloak fails); the action returns the ProblemDetail detail — show it
  const [revokeError, setRevokeError] = useState<string | null>(null);

  useEffect(() => {
    listInvitations()
      .then(setInvitations)
      .finally(() => setIsLoaded(true));
  }, []);

  if (!isLoaded) {
    return (
      <div
        className="py-8 text-center text-sm text-slate-600 dark:text-slate-400"
        aria-live="polite"
      >
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
    setRevokeError(null);
    try {
      let result: Awaited<ReturnType<typeof revokeInvitation>>;
      try {
        result = await revokeInvitation(invitationId);
      } catch (err) {
        console.error("Failed to revoke invitation:", err);
        setRevokeError(REVOKE_FAILED);
        return;
      }
      if (!result.success) {
        setRevokeError(result.error || REVOKE_FAILED);
        return;
      }
      // Revoked: drop the row now, so a failed refresh below can never read as a failed revoke
      setInvitations((current) => current.filter((inv) => inv.id !== invitationId));
      try {
        setInvitations(await listInvitations());
      } catch (err) {
        console.error("Invitation revoked, but the list could not be refreshed:", err);
      }
    } finally {
      setRevokingId(null);
    }
  };

  return (
    <div className="space-y-3">
      {revokeError && (
        <p role="alert" data-testid="revoke-invitation-error" className="text-destructive text-sm">
          {revokeError}
        </p>
      )}
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

// --- Shared UI components ---

function InvitationTable({ isAdmin, children }: { isAdmin: boolean; children: React.ReactNode }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-slate-200 dark:border-slate-800">
            <th className="pr-4 pb-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Email
            </th>
            <th className="w-[100px] pr-4 pb-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
              Role
            </th>
            <th className="w-[140px] pr-4 pb-3 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
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
    <tr
      data-testid="pending-invite-row"
      className="border-b border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/30"
    >
      <td className="py-3 pr-4 font-medium text-slate-900 dark:text-slate-100">{email}</td>
      <td className="py-3 pr-4">
        <Badge variant={role.variant}>{role.label}</Badge>
      </td>
      <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">{createdAt}</td>
      {isAdmin && (
        <td className="py-3">
          <button
            onClick={onRevoke}
            disabled={isRevoking}
            className="text-sm font-medium text-red-600 hover:text-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 disabled:opacity-50 dark:text-red-400 dark:hover:text-red-300"
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
  return <KeycloakBffPendingInvitations isAdmin={isAdmin} />;
}
