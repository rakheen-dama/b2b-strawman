import type { ProjectMember } from "@/lib/types";
import { Card, CardContent } from "@/components/ui/card";
import { StatusBadge } from "@/components/ui/status-badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { Users } from "lucide-react";

interface TeamTabProps {
  members: ProjectMember[];
}

export function TeamTab({ members }: TeamTabProps) {
  if (members.length === 0) {
    return (
      <div className="flex flex-col items-center py-16 text-center">
        <Users className="size-12 text-slate-300" />
        <h3 className="mt-4 font-display text-lg text-slate-900">
          No team members
        </h3>
        <p className="mt-1 text-sm text-slate-500">
          Add members to this project to assign tasks and track time.
        </p>
      </div>
    );
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      {members.map((member) => (
        <Card key={member.id}>
          <CardContent className="flex items-center gap-3 pt-6">
            <AvatarCircle name={member.name} size={40} />
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <p className="truncate text-sm font-medium text-slate-900">
                  {member.name}
                </p>
                <StatusBadge status={member.projectRole} />
              </div>
              <p className="truncate text-xs text-slate-500">{member.email}</p>
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
