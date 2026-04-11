"use client";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Pencil, Trash2 } from "lucide-react";
import type { OrgRole } from "@/lib/api/org-roles";

interface RoleCardProps {
  role: OrgRole;
  onEdit?: (role: OrgRole) => void;
  onDelete?: (role: OrgRole) => void;
}

export function RoleCard({ role, onEdit, onDelete }: RoleCardProps) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-slate-800 dark:bg-slate-950">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-slate-950 dark:text-slate-50">{role.name}</h3>
          {role.description && (
            <p className="mt-1 text-sm text-slate-600 dark:text-slate-400">{role.description}</p>
          )}
        </div>
        <div className="flex shrink-0 items-center gap-1">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => onEdit?.(role)}
            aria-label={`Edit ${role.name}`}
          >
            <Pencil className="size-4" />
            <span className="sr-only">Edit {role.name}</span>
          </Button>
          <Button
            variant="ghost"
            size="sm"
            className="text-red-600 hover:bg-red-50 hover:text-red-700 dark:text-red-400 dark:hover:bg-red-950 dark:hover:text-red-300"
            onClick={() => onDelete?.(role)}
            aria-label={`Delete ${role.name}`}
          >
            <Trash2 className="size-4" />
            <span className="sr-only">Delete {role.name}</span>
          </Button>
        </div>
      </div>

      {role.capabilities.length > 0 && (
        <div className="mt-3 flex flex-wrap gap-1.5">
          {role.capabilities.map((cap) => (
            <Badge key={cap} variant="secondary">
              {cap.replace(/_/g, " ")}
            </Badge>
          ))}
        </div>
      )}

      <p className="mt-3 text-xs text-slate-500 dark:text-slate-400">
        {role.memberCount} {role.memberCount === 1 ? "member" : "members"}
      </p>
    </div>
  );
}
