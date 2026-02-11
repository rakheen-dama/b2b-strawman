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

const ROLE_BADGE: Record<ProjectRole, { label: string; variant: "lead" | "member" }> = {
  lead: { label: "Lead", variant: "lead" },
  member: { label: "Member", variant: "member" },
};

const AVATAR_COLORS = [
  "bg-olive-200 text-olive-700",
  "bg-indigo-100 text-indigo-700",
  "bg-amber-100 text-amber-700",
  "bg-green-100 text-green-700",
  "bg-rose-100 text-rose-700",
];

function MemberAvatar({ name }: { name: string }) {
  const initials = name
    .split(" ")
    .map((part) => part.charAt(0))
    .slice(0, 2)
    .join("")
    .toUpperCase();
  const colorIndex =
    name.split("").reduce((acc, char) => acc + char.charCodeAt(0), 0) % AVATAR_COLORS.length;

  return (
    <div
      className={`flex size-8 shrink-0 items-center justify-center rounded-full text-xs font-medium ${AVATAR_COLORS[colorIndex]}`}
    >
      {initials}
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
      <div className="flex items-center gap-2">
        <h2 className="font-semibold text-olive-900 dark:text-olive-100">Members</h2>
        {members.length > 0 && (
          <Badge variant="neutral">{members.length}</Badge>
        )}
      </div>
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
        <div className="rounded-lg border border-dashed border-olive-300 p-8 dark:border-olive-700">
          <div className="flex flex-col items-center text-center">
            <Users className="size-10 text-olive-300 dark:text-olive-600" />
            <p className="mt-3 text-sm font-medium text-olive-900 dark:text-olive-100">
              No members yet
            </p>
            <p className="mt-1 text-xs text-olive-600 dark:text-olive-400">
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
      {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
      <div className="rounded-lg border border-olive-200 dark:border-olive-800">
        <Table>
          <TableHeader>
            <TableRow className="border-olive-200 hover:bg-transparent dark:border-olive-800">
              <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                Member
              </TableHead>
              <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                Email
              </TableHead>
              <TableHead className="text-xs uppercase tracking-wide text-olive-600 dark:text-olive-400">
                Role
              </TableHead>
              <TableHead className="hidden text-xs uppercase tracking-wide text-olive-600 sm:table-cell dark:text-olive-400">
                Added
              </TableHead>
              {canManage && <TableHead className="w-[60px]" />}
            </TableRow>
          </TableHeader>
          <TableBody>
            {members.map((member) => {
              const badge = ROLE_BADGE[member.projectRole];
              const isLead = member.projectRole === "lead";
              const isRemoving = removingMemberId === member.memberId;

              return (
                <TableRow
                  key={member.id}
                  className="border-olive-100 transition-colors hover:bg-olive-50 dark:border-olive-800/50 dark:hover:bg-olive-900"
                >
                  <TableCell>
                    <div className="flex items-center gap-3">
                      <MemberAvatar name={member.name} />
                      <div className="min-w-0">
                        <p className="truncate text-sm font-medium text-olive-950 dark:text-olive-50">
                          {member.name}
                        </p>
                        <p className="truncate text-xs text-olive-600 sm:hidden dark:text-olive-400">
                          {member.email}
                        </p>
                      </div>
                    </div>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    <span className="text-sm text-olive-600 dark:text-olive-400">
                      {member.email}
                    </span>
                  </TableCell>
                  <TableCell>
                    <Badge variant={badge.variant}>{badge.label}</Badge>
                  </TableCell>
                  <TableCell className="hidden sm:table-cell">
                    <span className="text-sm text-olive-600 dark:text-olive-400">
                      {formatDate(member.createdAt)}
                    </span>
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
                              className="text-red-600 dark:text-red-400"
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
