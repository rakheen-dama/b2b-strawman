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

const ROLE_LABELS: Record<string, { label: string; variant: "default" | "secondary" | "outline" }> =
  {
    "org:owner": { label: "Owner", variant: "default" },
    "org:admin": { label: "Admin", variant: "secondary" },
    "org:member": { label: "Member", variant: "outline" },
  };

export function MemberList() {
  const { memberships, isLoaded } = useOrganization({
    memberships: {
      infinite: true,
      keepPreviousData: true,
    },
  });

  if (!isLoaded) {
    return <div className="text-muted-foreground py-8 text-center text-sm">Loading members...</div>;
  }

  if (!memberships?.data?.length) {
    return <div className="text-muted-foreground py-8 text-center text-sm">No members found.</div>;
  }

  return (
    <div className="space-y-4">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Name</TableHead>
            <TableHead>Email</TableHead>
            <TableHead>Role</TableHead>
            <TableHead>Joined</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {memberships.data.map((member) => {
            const roleInfo = ROLE_LABELS[member.role] ?? {
              label: member.role,
              variant: "outline" as const,
            };
            return (
              <TableRow key={member.id}>
                <TableCell className="font-medium">
                  {member.publicUserData?.firstName ?? ""} {member.publicUserData?.lastName ?? ""}
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {member.publicUserData?.identifier ?? "—"}
                </TableCell>
                <TableCell>
                  <Badge variant={roleInfo.variant}>{roleInfo.label}</Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {member.createdAt ? new Date(member.createdAt).toLocaleDateString() : "—"}
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>

      {memberships.hasNextPage && (
        <div className="flex justify-center">
          <Button
            variant="outline"
            size="sm"
            onClick={() => memberships.fetchNext?.()}
            disabled={memberships.isFetching}
          >
            {memberships.isFetching ? "Loading..." : "Load more"}
          </Button>
        </div>
      )}
    </div>
  );
}
