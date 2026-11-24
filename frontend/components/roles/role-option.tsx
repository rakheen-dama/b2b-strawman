"use client";

import { SelectItem } from "@/components/ui/select";
import { OwnerOnlyHint } from "@/components/roles/owner-only-hint";
import type { OrgRole } from "@/lib/api/org-roles";

/**
 * A role in a role select (member detail panel, invite form). Locked (FR-4: it holds an owner-only
 * capability and the caller is not the owner): shown but not selectable, with the owner-only hint.
 */
export function RoleOption({ role, locked }: { role: OrgRole; locked: boolean }) {
  return (
    <SelectItem
      value={role.id}
      disabled={locked}
      data-testid={`role-option-${role.id}`}
      hint={
        locked ? (
          <OwnerOnlyHint testId={`owner-only-role-hint-${role.id}`} className="inline" />
        ) : undefined
      }
    >
      {role.name}
    </SelectItem>
  );
}
