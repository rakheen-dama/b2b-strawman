"use client";

import { useState, type ReactNode } from "react";
import type { VariantProps } from "class-variance-authority";
import { Button, buttonVariants } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { updateComment } from "@/lib/actions/comments";
import type { Comment } from "@/lib/actions/comments";

type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;
type ButtonSize = NonNullable<VariantProps<typeof buttonVariants>["size"]>;

interface EditCommentDialogProps {
  comment: Comment;
  orgSlug: string;
  projectId: string;
  canManageVisibility: boolean;
  onCommentChange?: () => void;
  triggerLabel: ReactNode;
  triggerVariant?: ButtonVariant;
  triggerSize?: ButtonSize;
  triggerClassName?: string;
  triggerIcon?: ReactNode;
  triggerAriaLabel?: string;
}

export function EditCommentDialog({
  comment,
  orgSlug,
  projectId,
  canManageVisibility,
  onCommentChange,
  triggerLabel,
  triggerVariant = "ghost",
  triggerSize = "xs",
  triggerClassName,
  triggerIcon,
  triggerAriaLabel,
}: EditCommentDialogProps) {
  const [open, setOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(formData: FormData) {
    setError(null);

    const body = formData.get("body")?.toString().trim() ?? "";
    if (!body) {
      setError("Comment cannot be empty.");
      return;
    }

    const visibility = canManageVisibility ? formData.get("visibility")?.toString() : undefined;

    setIsSubmitting(true);

    try {
      const result = await updateComment(orgSlug, projectId, comment.id, body, visibility);
      if (result.success) {
        setOpen(false);
        onCommentChange?.();
      } else {
        setError(result.error ?? "Failed to update comment.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
    }
    setOpen(newOpen);
  }

  // OBS-2103 / OBS-2103b / audit-03 sweep: dialog owns the trigger button.
  // No DialogTrigger / Slot wrapper, so adjacent dialog triggers cannot
  // collide on Radix Slot reconciliation (PR #1242 pattern).
  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
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
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Edit Comment</DialogTitle>
          <DialogDescription>Update your comment.</DialogDescription>
        </DialogHeader>
        <form action={handleSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edit-comment-body">Comment</Label>
            <Textarea
              id="edit-comment-body"
              name="body"
              defaultValue={comment.body}
              required
              disabled={isSubmitting}
            />
          </div>

          {canManageVisibility && (
            <div className="space-y-2">
              <Label htmlFor="edit-comment-visibility">Visibility</Label>
              <select
                id="edit-comment-visibility"
                name="visibility"
                defaultValue={comment.visibility}
                disabled={isSubmitting}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-slate-400 focus-visible:ring-1 focus-visible:ring-slate-500 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800"
              >
                <option value="INTERNAL">Internal only</option>
                <option value="SHARED">Customer visible</option>
              </select>
            </div>
          )}

          {error && <p className="text-destructive text-sm">{error}</p>}

          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => setOpen(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Saving..." : "Save"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
