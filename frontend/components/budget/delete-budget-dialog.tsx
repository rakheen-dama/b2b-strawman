"use client";

import { useState, type ReactNode } from "react";
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
import { deleteBudget } from "@/app/(app)/org/[slug]/projects/[id]/budget-actions";
import { AlertTriangle } from "lucide-react";

interface DeleteBudgetDialogProps {
  slug: string;
  projectId: string;
  children: ReactNode;
}

export function DeleteBudgetDialog({
  slug,
  projectId,
  children,
}: DeleteBudgetDialogProps) {
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    setError(null);
    setIsDeleting(true);
    try {
      const result = await deleteBudget(slug, projectId);
      if (result.success) {
        setIsDeleting(false);
        setOpen(false);
      } else {
        setError(result.error ?? "Failed to delete budget.");
        setIsDeleting(false);
      }
    } catch {
      setError("An unexpected error occurred.");
      setIsDeleting(false);
    }
  }

  return (
    <AlertDialog open={open} onOpenChange={setOpen}>
      <AlertDialogTrigger asChild>{children}</AlertDialogTrigger>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">
            Delete Budget
          </AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            This will remove the budget configuration for this project. Time
            entries and billing data will not be affected.
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
            {isDeleting ? "Deleting..." : "Delete Budget"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
