"use client";

import { Plus, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { OrgMember } from "@/lib/types";
import type { TeamMemberData } from "@/app/(app)/org/[slug]/proposals/proposal-actions";

interface TeamMemberPickerProps {
  members: TeamMemberData[];
  onChange: (members: TeamMemberData[]) => void;
  orgMembers: OrgMember[];
}

export function TeamMemberPicker({
  members,
  onChange,
  orgMembers,
}: TeamMemberPickerProps) {
  const selectedIds = new Set(members.map((m) => m.memberId));
  const availableMembers = orgMembers.filter((m) => !selectedIds.has(m.id));

  function addMember(memberId: string) {
    const member = orgMembers.find((m) => m.id === memberId);
    if (!member) return;
    onChange([...members, { memberId: member.id, role: "" }]);
  }

  function removeMember(index: number) {
    onChange(members.filter((_, i) => i !== index));
  }

  function updateRole(index: number, role: string) {
    const updated = members.map((m, i) => {
      if (i !== index) return m;
      return { ...m, role };
    });
    onChange(updated);
  }

  function getMemberName(memberId: string): string {
    return orgMembers.find((m) => m.id === memberId)?.name ?? "Unknown";
  }

  return (
    <div className="space-y-3">
      <Label>Team Members</Label>

      {members.length > 0 && (
        <div className="space-y-2">
          {members.map((member, index) => (
            <div
              key={member.memberId}
              className="flex items-center gap-2 rounded-md border border-slate-200 p-2"
            >
              <span className="min-w-[120px] text-sm font-medium text-slate-700">
                {getMemberName(member.memberId)}
              </span>
              <Input
                placeholder="Role (e.g., Lead Developer)"
                value={member.role}
                onChange={(e) => updateRole(index, e.target.value)}
                className="flex-1"
              />
              <Button
                type="button"
                variant="ghost"
                size="icon-xs"
                onClick={() => removeMember(index)}
                aria-label="Remove member"
              >
                <X className="h-3.5 w-3.5 text-slate-400" />
              </Button>
            </div>
          ))}
        </div>
      )}

      {availableMembers.length > 0 && (
        <div className="flex items-center gap-2">
          <Select onValueChange={addMember}>
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder="Select a member" />
            </SelectTrigger>
            <SelectContent>
              {availableMembers.map((m) => (
                <SelectItem key={m.id} value={m.id}>
                  {m.name}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <span className="text-xs text-slate-500">
            <Plus className="inline h-3 w-3" /> Add to team
          </span>
        </div>
      )}

      {members.length === 0 && availableMembers.length === 0 && (
        <p className="text-sm text-slate-500">
          No organization members available.
        </p>
      )}
    </div>
  );
}
