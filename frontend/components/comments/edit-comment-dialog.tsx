"use client";

import { useState, type ReactNode } from "react";
import type { VariantProps } from "class-variance-authority";
import { Button, buttonVariants } from "@b2mash/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@b2mash/ui/label";
import { updateComment } from "@/lib/actions/comments";
import { CommentAttachmentsField } from "@/components/comments/comment-attachments-field";
import {
  ATTACHMENT_REJECTED_LABEL,
  nameAttachmentInError,
  useCommentAttachments,
} from "@/components/comments/use-comment-attachments";
import type { Comment, CommentAttachment } from "@/lib/types";

type ButtonVariant = NonNullable<VariantProps<typeof buttonVariants>["variant"]>;
type ButtonSize = NonNullable<VariantProps<typeof buttonVariants>["size"]>;

function sameIds(a: string[], b: string[]): boolean {
  return a.length === b.length && a.every((id, i) => id === b[i]);
}

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
  // Attachments already on the comment that the user is keeping (removable chips).
  const [kept, setKept] = useState<CommentAttachment[]>(comment.attachments ?? []);
  // Mirrors the uncontrolled select so the client-visible hint can react to it.
  const [visibilityChoice, setVisibilityChoice] = useState<string>(comment.visibility);
  const attachments = useCommentAttachments({
    slug: orgSlug,
    projectId,
    existingCount: kept.length,
  });

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
      const confirmed = await attachments.confirmAll();
      if (!confirmed.ok) {
        setError(confirmed.error);
        return;
      }

      // Kept attachments first (original order), then the newly confirmed uploads. The key is
      // sent only when the set changed: omitted = unchanged, so a visibility-only edit never
      // re-sends (and so never re-applies the attach rule to) the comment's documents.
      const originalIds = (comment.attachments ?? []).map((a) => a.documentId);
      const finalIds = [...kept.map((a) => a.documentId), ...confirmed.documentIds];
      const attachmentDocumentIds = sameIds(finalIds, originalIds) ? undefined : finalIds;

      const result = await updateComment(
        orgSlug,
        projectId,
        comment.id,
        body,
        visibility,
        attachmentDocumentIds
      );
      if (result.success) {
        attachments.reset();
        setOpen(false);
        onCommentChange?.();
      } else {
        const message = result.error ?? "Failed to update comment.";
        const rejectedId = result.attachmentDocumentId;
        const rejectedName = rejectedId
          ? (attachments.items.find((i) => i.documentId === rejectedId)?.fileName ??
            kept.find((a) => a.documentId === rejectedId)?.fileName)
          : undefined;
        if (rejectedId && rejectedName) {
          attachments.markError(rejectedId, ATTACHMENT_REJECTED_LABEL);
          setError(nameAttachmentInError(message, rejectedId, rejectedName));
        } else {
          setError(message);
        }
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
      // The form action resets the uncontrolled select to its default after every submit.
      setVisibilityChoice(comment.visibility);
    }
  }

  function handleOpenChange(newOpen: boolean) {
    if (newOpen) {
      setError(null);
      setKept(comment.attachments ?? []);
      setVisibilityChoice(comment.visibility);
    } else {
      // Closing without saving: release staged (still PENDING) uploads.
      attachments.releaseStaged();
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
        onClick={() => handleOpenChange(true)}
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
                onChange={(e) => setVisibilityChoice(e.target.value)}
                disabled={isSubmitting}
                className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-slate-400 focus-visible:ring-1 focus-visible:ring-slate-500 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800"
              >
                <option value="INTERNAL">Internal only</option>
                <option value="SHARED">Customer visible</option>
              </select>
            </div>
          )}

          <CommentAttachmentsField
            attachments={attachments}
            kept={kept}
            onRemoveKept={(documentId) =>
              setKept((prev) => prev.filter((a) => a.documentId !== documentId))
            }
            disabled={isSubmitting}
            shared={canManageVisibility && visibilityChoice === "SHARED"}
          />

          {error && <p className="text-destructive text-sm">{error}</p>}

          <DialogFooter>
            <Button
              type="button"
              variant="plain"
              onClick={() => handleOpenChange(false)}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={isSubmitting || attachments.isBusy || attachments.hasErrors}
            >
              {isSubmitting ? "Saving..." : "Save"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
