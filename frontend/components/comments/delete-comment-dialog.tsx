"use client";

import { useState, type ReactNode } from "react";
import { AlertTriangle } from "lucide-react";
import type { VariantProps } from "class-variance-authority";
import { Button, buttonVariants } from "@b2mash/ui/button";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { deleteComment } from "@/lib/actions/comments";

type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;
type ButtonSize = NonNullable<VariantProps<typeof buttonVariants>["size"]>;

interface DeleteCommentDialogProps {
  commentId: string;
  orgSlug: string;
  projectId: string;
  onCommentChange?: () => void;
  triggerLabel: ReactNode;
  triggerVariant?: ButtonVariant;
  triggerSize?: ButtonSize;
  triggerClassName?: string;
  triggerIcon?: ReactNode;
  triggerAriaLabel?: string;
}

export function DeleteCommentDialog({
  commentId,
  orgSlug,
  projectId,
  onCommentChange,
  triggerLabel,
  triggerVariant = "ghost",
  triggerSize = "xs",
  triggerClassName,
  triggerIcon,
  triggerAriaLabel,
}: DeleteCommentDialogProps) {
  const [open, setOpen] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDelete() {
    setError(null);
    setIsDeleting(true);

    try {
      const result = await deleteComment(orgSlug, projectId, commentId);
      if (result.success) {
        setOpen(false);
        onCommentChange?.();
      } else {
        setError(result.error ?? "Failed to delete comment.");
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

  // OBS-2103 / OBS-2103b / audit-03 sweep: dialog owns the trigger button.
  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <Button
        type="button"
        variant={triggerVariant}
        size={triggerSize}
        className={triggerClassName}
        aria-label={triggerAriaLabel}
        onClick={() => setOpen(true)}
      >
        {triggerIcon}
        {triggerLabel}
      </Button>
      <AlertDialogContent className="border-t-4 border-t-red-500">
        <AlertDialogHeader>
          <div className="flex justify-center">
            <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
              <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
            </div>
          </div>
          <AlertDialogTitle className="text-center">Delete Comment</AlertDialogTitle>
          <AlertDialogDescription className="text-center">
            Delete this comment? This action cannot be undone.
          </AlertDialogDescription>
        </AlertDialogHeader>
        {error && <p className="text-destructive text-sm">{error}</p>}
        <AlertDialogFooter>
          <AlertDialogCancel variant="plain" disabled={isDeleting}>
            Cancel
          </AlertDialogCancel>
          <Button variant="destructive" onClick={handleDelete} disabled={isDeleting}>
            {isDeleting ? "Deleting..." : "Delete"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
