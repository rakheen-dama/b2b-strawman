"use client";

import { Mail } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { createContext, use, useState, useEffect } from "react";
import type { CellContext } from "@tanstack/react-table";
import { DataTable } from "@/components/data-table/data-table";
import { defineColumns } from "@/components/data-table/define-columns";
import { DateCell } from "@/components/data-table/cells";
import { listInvitations, revokeInvitation } from "@/app/(app)/org/[slug]/team/invitation-actions";
import type { MappedInvitation } from "@/app/(app)/org/[slug]/team/invitation-actions";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "keycloak";

const REVOKE_FAILED = "Could not revoke the invitation. Please try again.";

const ROLE_BADGES: Record<string, { label: string; variant: "owner" | "admin" | "member" }> = {
  "org:owner": { label: "Owner", variant: "owner" },
  "org:admin": { label: "Admin", variant: "admin" },
  "org:member": { label: "Member", variant: "member" },
};

function roleBadge(role: string) {
  return ROLE_BADGES[role] ?? { label: role, variant: "member" as const };
}

/**
 * The Revoke cell's per-render inputs. The columns are module constants and the Revoke cell reads
 * these from context (669A adverse-parties precedent), so a revoke in flight never remounts cells.
 */
interface RevokeContextValue {
  revokingId: string | null;
  onRevoke: (invitationId: string) => void;
}

const RevokeContext = createContext<RevokeContextValue | null>(null);

function RevokeCell({ row }: CellContext<MappedInvitation, unknown>) {
  const context = use(RevokeContext);
  if (!context) throw new Error("RevokeCell needs RevokeContext");
  const { revokingId, onRevoke } = context;
  const isRevoking = revokingId === row.original.id;
  return (
    <button
      onClick={() => onRevoke(row.original.id)}
      disabled={isRevoking}
      className="text-sm font-medium text-red-600 hover:text-red-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 disabled:opacity-50 dark:text-red-400 dark:hover:text-red-300"
    >
      {isRevoking ? "Revoking..." : "Revoke"}
    </button>
  );
}

const COLUMNS = defineColumns<MappedInvitation>([
  {
    id: "email",
    enableHiding: false,
    meta: { label: "Email", cellClassName: "text-foreground font-medium" },
    cell: ({ row }) => row.original.emailAddress,
  },
  {
    id: "role",
    meta: { label: "Role", headerClassName: "w-[100px]" },
    cell: ({ row }) => {
      const role = roleBadge(row.original.role);
      return <Badge variant={role.variant}>{role.label}</Badge>;
    },
  },
  {
    id: "invited",
    meta: {
      label: "Invited",
      headerClassName: "w-[140px]",
      cellClassName: "text-muted-foreground",
    },
    // Client-rendered: the South African calendar (formatDateInSA); none → "—".
    cell: ({ row }) => <DateCell value={row.original.createdAt} />,
  },
]);

/** Admins also get the Revoke column. */
const ADMIN_COLUMNS = defineColumns<MappedInvitation>([
  ...COLUMNS,
  {
    id: "actions",
    enableHiding: false,
    meta: { label: "Actions", headerClassName: "w-[80px]" },
    cell: RevokeCell,
  },
]);

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
      <RevokeContext value={{ revokingId, onRevoke: handleRevoke }}>
        <DataTable
          columns={isAdmin ? ADMIN_COLUMNS : COLUMNS}
          data={invitations}
          getRowId={(inv) => inv.id}
          rowTestId="pending-invite-row"
          columnVisibility={false}
          stickyHeader={false}
          empty={
            <EmptyState
              variant="default"
              icon={Mail}
              title="No pending invitations"
              description="Invited members will appear here"
            />
          }
        />
      </RevokeContext>
    </div>
  );
}

function MockPendingInvitations() {
  return (
    <EmptyState
      variant="default"
      icon={Mail}
      title="No pending invitations"
      description="Invitations are managed through the auth provider"
    />
  );
}

export function PendingInvitations({ isAdmin }: { isAdmin: boolean }) {
  if (AUTH_MODE === "mock") return <MockPendingInvitations />;
  return <KeycloakBffPendingInvitations isAdmin={isAdmin} />;
}
