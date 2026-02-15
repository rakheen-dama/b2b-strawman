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
import { deleteFieldDefinitionAction } from "@/app/(app)/org/[slug]/settings/custom-fields/actions";

interface DeleteFieldDialogProps {
  slug: string;
  fieldId: string;
  fieldName: string;
  children: React.ReactNode;
}

export function DeleteFieldDialog({
  slug,
  fieldId,
  fieldName,
  children,
}: DeleteFieldDialogProps) {
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    setIsDeleting(true);
    setError(null);

    try {
      const result = await deleteFieldDefinitionAction(slug, fieldId);

      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to delete field definition.");
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
          <AlertDialogTitle>Delete Field Definition</AlertDialogTitle>
          <AlertDialogDescription>
            Are you sure you want to delete the field &quot;{fieldName}&quot;?
            This will deactivate the field and it will no longer appear on new
            records. Existing data will be preserved.
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
