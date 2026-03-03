"use client";

import { useOrganization } from "@clerk/nextjs";
import { Mail } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { useState, useEffect, useCallback } from "react";
import { useSession } from "next-auth/react";
import { BACKEND_URL } from "@/lib/auth/client";
import { formatDate } from "@/lib/format";

const AUTH_MODE = process.env.NEXT_PUBLIC_AUTH_MODE || "clerk";

const ROLE_BADGES: Record<string, { label: string; variant: "owner" | "admin" | "member" }> = {
  "org:owner": { label: "Owner", variant: "owner" },
  "org:admin": { label: "Admin", variant: "admin" },
  "org:member": { label: "Member", variant: "member" },
  owner: { label: "Owner", variant: "owner" },
  admin: { label: "Admin", variant: "admin" },
  member: { label: "Member", variant: "member" },
};

function getOrgIdFromToken(token: string): string | null {
  try {
    const payload = JSON.parse(atob(token.split(".")[1]));
    return payload.o?.id ?? null;
  } catch {
    return null;
  }
}

interface InvitationResponse {
  id: string;
  email: string;
  role?: string;
  status: string;
  createdAt: string;
}

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
    return <div className="py-8 text-center text-sm text-slate-600 dark:text-slate-400">Loading invitations...</div>;
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
    const invitation = invitations.data?.find((inv) => inv.id === invitationId);
    if (!invitation) return;

    setRevokingId(invitationId);
    try {
      await invitation.revoke();
      await Promise.all([invitations.revalidate?.(), memberships?.revalidate?.()]);
    } catch (err) {
      console.error("Failed to revoke invitation:", err);
    } finally {
      setRevokingId(null);
    }
  };

  return (
    <div className="space-y-4">
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
          <tbody>
            {invitations.data.map((inv) => {
              const roleInfo = ROLE_BADGES[inv.role] ?? {
                label: inv.role,
                variant: "member" as const,
              };
              return (
                <tr
                  key={inv.id}
                  className="border-b border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/30"
                >
                  <td className="py-3 pr-4 font-medium text-slate-900 dark:text-slate-100">
                    {inv.emailAddress}
                  </td>
                  <td className="py-3 pr-4">
                    <Badge variant={roleInfo.variant}>{roleInfo.label}</Badge>
                  </td>
                  <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">
                    {inv.createdAt ? formatDate(inv.createdAt) : "—"}
                  </td>
                  {isAdmin && (
                    <td className="py-3">
                      <button
                        onClick={() => handleRevoke(inv.id)}
                        disabled={revokingId === inv.id}
                        className="text-sm font-medium text-red-600 hover:text-red-700 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 dark:text-red-400 dark:hover:text-red-300"
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

      {(invitations.hasPreviousPage || invitations.hasNextPage) && (
        <div className="flex justify-center gap-4">
          <button
            disabled={!invitations.hasPreviousPage || invitations.isFetching}
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

function KeycloakPendingInvitations({ isAdmin }: { isAdmin: boolean }) {
  const { data: session } = useSession();
  const [invitations, setInvitations] = useState<InvitationResponse[]>([]);
  const [isLoaded, setIsLoaded] = useState(false);
  const [revokingId, setRevokingId] = useState<string | null>(null);

  const token = session?.accessToken ?? null;
  const orgId = token ? getOrgIdFromToken(token) : null;

  const fetchInvitations = useCallback(() => {
    if (!token || !orgId) return;

    let cancelled = false;

    fetch(`${BACKEND_URL}/api/orgs/${orgId}/invitations`, {
      headers: { Authorization: `Bearer ${token}` },
    })
      .then((res) => (res.ok ? res.json() : []))
      .then((data: InvitationResponse[]) => {
        if (!cancelled) {
          setInvitations(Array.isArray(data) ? data : []);
        }
      })
      .catch((err) => {
        console.error("KeycloakPendingInvitations: failed to fetch invitations", err);
      })
      .finally(() => {
        if (!cancelled) setIsLoaded(true);
      });

    return () => {
      cancelled = true;
    };
  }, [token, orgId]);

  useEffect(() => {
    fetchInvitations();
  }, [fetchInvitations]);

  if (!isLoaded) {
    return <div className="py-8 text-center text-sm text-slate-600 dark:text-slate-400">Loading invitations...</div>;
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
    if (!token || !orgId) return;

    setRevokingId(invitationId);
    try {
      const res = await fetch(`${BACKEND_URL}/api/orgs/${orgId}/invitations/${invitationId}`, {
        method: "DELETE",
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok) {
        setInvitations((prev) => prev.filter((inv) => inv.id !== invitationId));
      }
    } catch (err) {
      console.error("Failed to revoke invitation:", err);
    } finally {
      setRevokingId(null);
    }
  };

  return (
    <div className="space-y-4">
      <div className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-slate-200 dark:border-slate-800">
              <th className="pb-3 pr-4 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Email
              </th>
              <th className="w-[100px] pb-3 pr-4 text-left text-xs font-medium tracking-wide text-slate-600 uppercase dark:text-slate-400">
                Status
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
          <tbody>
            {invitations.map((inv) => {
              const statusVariant = inv.status === "pending" ? "admin" : "member";
              return (
                <tr
                  key={inv.id}
                  className="border-b border-slate-100 transition-colors hover:bg-slate-50 dark:border-slate-800/50 dark:hover:bg-slate-900/30"
                >
                  <td className="py-3 pr-4 font-medium text-slate-900 dark:text-slate-100">
                    {inv.email}
                  </td>
                  <td className="py-3 pr-4">
                    <Badge variant={statusVariant}>
                      {inv.status.charAt(0).toUpperCase() + inv.status.slice(1)}
                    </Badge>
                  </td>
                  <td className="py-3 pr-4 text-slate-600 dark:text-slate-400">
                    {inv.createdAt ? formatDate(inv.createdAt) : "—"}
                  </td>
                  {isAdmin && inv.status === "pending" && (
                    <td className="py-3">
                      <button
                        onClick={() => handleRevoke(inv.id)}
                        disabled={revokingId === inv.id}
                        className="text-sm font-medium text-red-600 hover:text-red-700 disabled:opacity-50 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-red-600 dark:text-red-400 dark:hover:text-red-300"
                      >
                        {revokingId === inv.id ? "Revoking..." : "Revoke"}
                      </button>
                    </td>
                  )}
                  {isAdmin && inv.status !== "pending" && <td className="py-3" />}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
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
  if (AUTH_MODE === "keycloak") return <KeycloakPendingInvitations isAdmin={isAdmin} />;
  if (AUTH_MODE === "mock") return <MockPendingInvitations />;
  return <ClerkPendingInvitations isAdmin={isAdmin} />;
}
