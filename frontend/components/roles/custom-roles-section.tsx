"use client";

import { useState } from "react";
import { Plus } from "lucide-react";
import { Button } from "@/components/ui/button";
import { RoleCard } from "@/components/roles/role-card";
import { RoleDialog } from "@/components/roles/role-dialog";
import { DeleteRoleDialog } from "@/components/roles/delete-role-dialog";
import type { OrgRole } from "@/lib/api/org-roles";

interface CustomRolesSectionProps {
  slug: string;
  customRoles: OrgRole[];
}

export function CustomRolesSection({
  slug,
  customRoles,
}: CustomRolesSectionProps) {
  const [isCreateOpen, setIsCreateOpen] = useState(false);
  const [editingRole, setEditingRole] = useState<OrgRole | null>(null);
  const [deletingRole, setDeletingRole] = useState<OrgRole | null>(null);

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-lg font-semibold text-slate-950 dark:text-slate-50">
            Custom Roles
          </h2>
          <p className="mt-0.5 text-sm text-slate-600 dark:text-slate-400">
            Create roles with specific capabilities for your team.
          </p>
        </div>
        <Button size="sm" onClick={() => setIsCreateOpen(true)}>
          <Plus className="mr-1.5 size-4" />
          New Role
        </Button>
      </div>

      {customRoles.length === 0 ? (
        <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-8 text-center dark:border-slate-700 dark:bg-slate-900">
          <p className="text-sm text-slate-600 dark:text-slate-400">
            No custom roles yet. Create one to assign granular permissions to
            team members.
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
      <RoleDialog
        slug={slug}
        open={isCreateOpen}
        onOpenChange={setIsCreateOpen}
      />

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
    </div>
  );
}
