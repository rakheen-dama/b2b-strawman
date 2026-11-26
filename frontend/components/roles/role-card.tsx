"use client";

import { Badge } from "@b2mash/ui/badge";
import { Button } from "@b2mash/ui/button";
import { Pencil, Trash2 } from "lucide-react";
import type { OrgRole } from "@/lib/api/org-roles";
import { capabilityLabel } from "@/lib/capabilities";

interface RoleCardProps {
  role: OrgRole;
  onEdit?: (role: OrgRole) => void;
  onDelete?: (role: OrgRole) => void;
}

/**
 * Capability pill showing the capability's label ("INVOICING" → "Billing"). K-104: it never runs
 * out of its card — `max-w-full` on the pill, ellipsis on the inner span (text-overflow does not
 * apply to the flex pill's own text) and the full label as its `title`.
 */
export function CapabilityBadge({ cap }: { cap: string }) {
  const label = capabilityLabel(cap);
  return (
    <Badge variant="secondary" className="max-w-full" title={label}>
      <span className="truncate">{label}</span>
    </Badge>
  );
}

export function RoleCard({ role, onEdit, onDelete }: RoleCardProps) {
  return (
    <div data-slot="role-card" className="min-w-0 rounded-lg border p-6">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <h3 className="text-foreground font-semibold">{role.name}</h3>
          {role.description && (
            <p className="text-muted-foreground mt-1 text-sm">{role.description}</p>
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
            className="text-destructive hover:bg-destructive/10 hover:text-destructive"
            onClick={() => onDelete?.(role)}
            aria-label={`Delete ${role.name}`}
          >
            <Trash2 className="size-4" />
            <span className="sr-only">Delete {role.name}</span>
          </Button>
        </div>
      </div>

      {role.capabilities.length > 0 && (
        <div className="mt-3 flex min-w-0 flex-wrap gap-1.5">
          {role.capabilities.map((cap) => (
            <CapabilityBadge key={cap} cap={cap} />
          ))}
        </div>
      )}

      <p className="text-muted-foreground mt-3 text-xs">
        {role.memberCount} {role.memberCount === 1 ? "member" : "members"}
      </p>
    </div>
  );
}
