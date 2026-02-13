"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { updateComment } from "@/lib/actions/comments";
import type { Comment } from "@/lib/actions/comments";

interface EditCommentDialogProps {
  comment: Comment;
  orgSlug: string;
  projectId: string;
  canManageVisibility: boolean;
  children: React.ReactNode;
}

export function EditCommentDialog({
  comment,
  orgSlug,
  projectId,
  canManageVisibility,
  children,
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

    const visibility = canManageVisibility
      ? formData.get("visibility")?.toString()
      : undefined;

    setIsSubmitting(true);

    try {
      const result = await updateComment(
        orgSlug,
        projectId,
        comment.id,
        body,
        visibility
      );
      if (result.success) {
        setOpen(false);
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

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
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
                className="flex h-9 w-full rounded-md border border-olive-200 bg-transparent px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-olive-400 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-olive-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-olive-800"
              >
                <option value="INTERNAL">Internal only</option>
                <option value="SHARED">Customer visible</option>
              </select>
            </div>
          )}

          {error && <p className="text-sm text-destructive">{error}</p>}

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
