"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { RoleCard } from "@/components/roles/role-card";
import { RoleDialog } from "@/components/roles/role-dialog";
import { DeleteRoleDialog } from "@/components/roles/delete-role-dialog";
import { SettingsSection } from "@/components/settings/settings-section";
import type { OrgRole } from "@/lib/api/org-roles";

interface CustomRolesSectionProps {
  slug: string;
  customRoles: OrgRole[];
}

export function CustomRolesSection({ slug, customRoles }: CustomRolesSectionProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<OrgRole | null>(null);
  const [deletingRole, setDeletingRole] = useState<OrgRole | null>(null);

  return (
    <SettingsSection
      title="Custom Roles"
      description="Create roles with specific capabilities for your team."
      actions={
        <Button size="sm" onClick={() => setIsCreateOpen(true)}>
          <Plus className="mr-1.5 size-4" />
          New Role
        </Button>
      }
    >
      {customRoles.length === 0 ? (
        <div className="bg-muted/50 rounded-lg border border-dashed p-8 text-center">
          <p className="text-muted-foreground text-sm">
            No custom roles yet. Create one to assign granular permissions to team members.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
          {customRoles.map((role) => (
            <RoleCard
              key={role.id}
              role={role}
              onEdit={(r) => setEditingRole(r)}
              onDelete={(r) => setDeletingRole(r)}
            />
          ))}
        </div>
      )}

      {/* Create Role Dialog */}
      <RoleDialog slug={slug} open={isCreateOpen} onOpenChange={setIsCreateOpen} />

      {/* Edit Role Dialog */}
      {editingRole && (
        <RoleDialog
          slug={slug}
          role={editingRole}
          open={!!editingRole}
          onOpenChange={(open) => {
            if (!open) setEditingRole(null);
          }}
        />
      )}

      {/* Delete Role Dialog */}
      {deletingRole && (
        <DeleteRoleDialog
          slug={slug}
          role={deletingRole}
          open={!!deletingRole}
          onOpenChange={(open) => {
            if (!open) setDeletingRole(null);
          }}
        />
      )}
    </SettingsSection>
  );
}
