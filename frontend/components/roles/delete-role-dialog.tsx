"use client";

import { useState } from "react";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { deleteRoleAction } from "@/app/(app)/org/[slug]/settings/roles/actions";
import type { OrgRole } from "@/lib/api/org-roles";

interface DeleteRoleDialogProps {
  slug: string;
  role: OrgRole;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export function DeleteRoleDialog({ slug, role, open, onOpenChange }: DeleteRoleDialogProps) {
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const hasMembers = role.memberCount > 0;

  async function handleDelete() {
    setIsDeleting(true);
    setError(null);

    try {
      const result = await deleteRoleAction(slug, role.id);

      if (result.success) {
        onOpenChange(false);
      } else {
        setError(result.error ?? "Failed to delete role.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete Role</AlertDialogTitle>
          <AlertDialogDescription>
            {hasMembers ? (
              <>
                The role &quot;{role.name}&quot; has{" "}
                <strong>
                  {role.memberCount} {role.memberCount === 1 ? "member" : "members"}
                </strong>{" "}
                assigned. Reassign all members to another role before deleting.
              </>
            ) : (
              <>
                Are you sure you want to delete the role &quot;{role.name}
                &quot;? This action cannot be undone.
              </>
            )}
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-destructive text-sm">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            onClick={handleDelete}
            disabled={isDeleting || hasMembers}
          >
            {isDeleting ? "Deleting..." : "Delete"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
