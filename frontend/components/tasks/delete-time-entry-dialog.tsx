"use client";

import { useState } from "react";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { deleteTimeEntry } from "@/app/(app)/org/[slug]/projects/[id]/time-entry-actions";
import { AlertTriangle } from "lucide-react";

interface DeleteTimeEntryDialogProps {
  slug: string;
  projectId: string;
  timeEntryId: string;
  children: React.ReactNode;
}

export function DeleteTimeEntryDialog({
  slug,
  projectId,
  timeEntryId,
  children,
}: DeleteTimeEntryDialogProps) {
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    setError(null);
    setIsDeleting(true);

    try {
      const result = await deleteTimeEntry(slug, projectId, timeEntryId);
      if (result.success) {
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to delete time entry.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsDeleting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
    }
    setOpen(newOpen);
  }

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">
            Delete Time Entry
          </AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            Delete this time entry? This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-sm text-destructive">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isDeleting}>
            Cancel
          </AlertDialogCancel>
          <Button
            variant="destructive"
            onClick={handleDelete}
            disabled={isDeleting}
          >
            {isDeleting ? "Deleting..." : "Delete"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
