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
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { deleteFieldGroupAction } from "@/app/(app)/org/[slug]/settings/custom-fields/actions";

interface DeleteGroupDialogProps {
  slug: string;
  groupId: string;
  groupName: string;
  children: React.ReactNode;
}

export function DeleteGroupDialog({
  slug,
  groupId,
  groupName,
  children,
}: DeleteGroupDialogProps) {
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    setIsDeleting(true);
    setError(null);

    try {
      const result = await deleteFieldGroupAction(slug, groupId);

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to delete field group.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Delete Field Group</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to delete the group &quot;{groupName}&quot;?
            This will deactivate the group. Fields within the group will not be
            affected.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isDeleting}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            variant="destructive"
            onClick={handleDelete}
            disabled={isDeleting}
          >
            {isDeleting ? "Deleting..." : "Delete"}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
