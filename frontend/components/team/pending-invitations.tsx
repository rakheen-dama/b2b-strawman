"use client";

import { useOrganization } from "@clerk/nextjs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { useState } from "react";

const ROLE_LABELS: Record<string, string> = {
  "org:owner": "Owner",
  "org:admin": "Admin",
  "org:member": "Member",
};

export function PendingInvitations({ isAdmin }: { isAdmin: boolean }) {
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
      <div className="text-muted-foreground py-8 text-center text-sm">Loading invitations...</div>
    );
  }

  if (!invitations?.data?.length) {
    return (
      <div className="text-muted-foreground py-8 text-center text-sm">No pending invitations.</div>
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
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Email</TableHead>
            <TableHead>Role</TableHead>
            <TableHead>Invited</TableHead>
            {isAdmin && <TableHead>Actions</TableHead>}
          </TableRow>
        </TableHeader>
        <TableBody>
          {invitations.data.map((inv) => (
            <TableRow key={inv.id}>
              <TableCell className="font-medium">{inv.emailAddress}</TableCell>
              <TableCell>
                <Badge variant="outline">{ROLE_LABELS[inv.role] ?? inv.role}</Badge>
              </TableCell>
              <TableCell className="text-muted-foreground">
                {inv.createdAt ? new Date(inv.createdAt).toLocaleDateString() : "—"}
              </TableCell>
              {isAdmin && (
                <TableCell>
                  <Button
                    variant="ghost"
                    size="xs"
                    onClick={() => handleRevoke(inv.id)}
                    disabled={revokingId === inv.id}
                    className="text-destructive hover:text-destructive"
                  >
                    {revokingId === inv.id ? "Revoking..." : "Revoke"}
                  </Button>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>

      {(invitations.hasPreviousPage || invitations.hasNextPage) && (
        <div className="flex justify-center gap-2">
          <Button
            variant="outline"
            size="sm"
            disabled={!invitations.hasPreviousPage || invitations.isFetching}
            onClick={() => invitations.fetchPrevious?.()}
          >
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={!invitations.hasNextPage || invitations.isFetching}
            onClick={() => invitations.fetchNext?.()}
          >
            Next
          </Button>
        </div>
      )}
    </div>
  );
}
