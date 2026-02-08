"use client";

import { Users } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
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
}

export function ProjectMembersPanel({ members }: ProjectMembersPanelProps) {
  if (members.length === 0) {
    return (
      <div className="space-y-4">
        <h2 className="text-lg font-semibold">Members</h2>
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
      <h2 className="text-lg font-semibold">Members</h2>
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead className="hidden sm:table-cell">Email</TableHead>
              <TableHead>Role</TableHead>
              <TableHead className="hidden sm:table-cell">Added</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {members.map((member) => {
              const badge = ROLE_BADGE[member.projectRole];
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
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
