"use client";

import { useState, useTransition } from "react";
import { MoreVertical, Plus, Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { AddMemberDialog } from "@/components/projects/add-member-dialog";
import { TransferLeadDialog } from "@/components/projects/transfer-lead-dialog";
import { removeProjectMember } from "@/app/(app)/org/[slug]/projects/[id]/member-actions";
import { formatDate } from "@/lib/format";
import type { ProjectMember, ProjectRole } from "@/lib/types";

const ROLE_BADGE: Record<ProjectRole, { label: string; variant: "default" | "outline" }> = {
  lead: { label: "Lead", variant: "default" },
  member: { label: "Member", variant: "outline" },
};

function MemberAvatar({ name }: { name: string }) {
  const initial = name.charAt(0).toUpperCase();
  return (
    <div className="bg-muted text-muted-foreground flex size-7 shrink-0 items-center justify-center rounded-full text-xs font-medium">
      {initial}
    </div>
  );
}

interface ProjectMembersPanelProps {
  members: ProjectMember[];
  slug: string;
  projectId: string;
  canManage: boolean;
  isCurrentLead: boolean;
  currentUserId: string;
}

export function ProjectMembersPanel({
  members,
  slug,
  projectId,
  canManage,
  isCurrentLead,
  currentUserId,
}: ProjectMembersPanelProps) {
  const [isPending, startTransition] = useTransition();
  const [removingMemberId, setRemovingMemberId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  function handleRemoveMember(memberId: string) {
    setError(null);
    setRemovingMemberId(memberId);

    startTransition(async () => {
      try {
        const result = await removeProjectMember(slug, projectId, memberId);
        if (!result.success) {
          setError(result.error ?? "Failed to remove member.");
        }
      } catch {
        setError("An unexpected error occurred.");
      } finally {
        setRemovingMemberId(null);
      }
    });
  }

  const header = (
    <div className="flex items-center justify-between">
      <h2 className="text-lg font-semibold">
        Members{members.length > 0 && ` (${members.length})`}
      </h2>
      {canManage && (
        <AddMemberDialog slug={slug} projectId={projectId} existingMembers={members}>
          <Button size="sm" variant="outline">
            <Plus className="mr-1.5 size-4" />
            Add Member
          </Button>
        </AddMemberDialog>
      )}
    </div>
  );

  if (members.length === 0) {
    return (
      <div className="space-y-4">
        {header}
        <div className="rounded-lg border border-dashed p-8">
          <div className="flex flex-col items-center text-center">
            <Users className="text-muted-foreground size-10" />
            <p className="mt-3 text-sm font-medium">No members yet</p>
            <p className="text-muted-foreground mt-1 text-xs">
              Add team members to collaborate on this project
            </p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {header}
      {error && <p className="text-destructive text-sm">{error}</p>}
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead className="hidden sm:table-cell">Email</TableHead>
              <TableHead>Role</TableHead>
              <TableHead className="hidden sm:table-cell">Added</TableHead>
              {canManage && <TableHead className="w-12" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {members.map((member) => {
              const badge = ROLE_BADGE[member.projectRole];
              const isLead = member.projectRole === "lead";
              const isRemoving = removingMemberId === member.memberId;

              return (
                <TableRow key={member.id}>
                  <TableCell>
                    <div className="flex items-center gap-2">
                      <MemberAvatar name={member.name} />
                      <span className="truncate text-sm font-medium">{member.name}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground hidden sm:table-cell">
                    {member.email}
                  </TableCell>
                  <TableCell>
                    <Badge variant={badge.variant}>{badge.label}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground hidden sm:table-cell">
                    {formatDate(member.createdAt)}
                  </TableCell>
                  {canManage && (
                    <TableCell>
                      {!isLead && (
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button
                              variant="ghost"
                              size="sm"
                              className="size-8 p-0"
                              disabled={isRemoving || isPending}
                            >
                              <MoreVertical className="size-4" />
                              <span className="sr-only">Open menu</span>
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            {isCurrentLead && member.memberId !== currentUserId && (
                              <TransferLeadDialog
                                slug={slug}
                                projectId={projectId}
                                targetMemberId={member.memberId}
                                targetMemberName={member.name}
                              >
                                <DropdownMenuItem onSelect={(e) => e.preventDefault()}>
                                  Transfer Lead
                                </DropdownMenuItem>
                              </TransferLeadDialog>
                            )}
                            <DropdownMenuItem
                              className="text-destructive"
                              onClick={() => handleRemoveMember(member.memberId)}
                              disabled={isRemoving}
                            >
                              {isRemoving ? "Removing..." : "Remove"}
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      )}
                    </TableCell>
                  )}
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
