"use client";

import { useState, useRef } from "react";
import { Button } from "@b2mash/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@b2mash/ui/label";
import { createComment } from "@/lib/actions/comments";
import { CommentAttachmentsField } from "@/components/comments/comment-attachments-field";
import {
  ATTACHMENT_REJECTED_LABEL,
  nameAttachmentInError,
  useCommentAttachments,
} from "@/components/comments/use-comment-attachments";

interface AddCommentFormProps {
  projectId: string;
  entityType: "TASK" | "DOCUMENT";
  entityId: string;
  orgSlug: string;
  canManageVisibility: boolean;
  onCommentChange?: () => void;
}

const DEFAULT_VISIBILITY = "INTERNAL";

export function AddCommentForm({
  projectId,
  entityType,
  entityId,
  orgSlug,
  canManageVisibility,
  onCommentChange,
}: AddCommentFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Mirrors the uncontrolled select so the client-visible hint can react to it.
  const [visibilityChoice, setVisibilityChoice] = useState(DEFAULT_VISIBILITY);
  const formRef = useRef<HTMLFormElement>(null);
  const attachments = useCommentAttachments({ slug: orgSlug, projectId });

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

      const result = await createComment(
        orgSlug,
        projectId,
        entityType,
        entityId,
        body,
        visibility,
        confirmed.documentIds
      );
      if (result.success) {
        formRef.current?.reset();
        attachments.reset();
        onCommentChange?.();
      } else {
        const message = result.error ?? "Failed to add comment.";
        const rejectedId = result.attachmentDocumentId;
        const rejected = rejectedId
          ? attachments.items.find((i) => i.documentId === rejectedId)
          : undefined;
        if (rejectedId && rejected) {
          attachments.markError(rejectedId, ATTACHMENT_REJECTED_LABEL);
          setError(nameAttachmentInError(message, rejectedId, rejected.fileName));
        } else {
          setError(message);
        }
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
      // The form action resets the uncontrolled select to its default after every submit.
      setVisibilityChoice(DEFAULT_VISIBILITY);
    }
  }

  return (
    <form ref={formRef} action={handleSubmit} className="space-y-3">
      <div className="space-y-2">
        <Label htmlFor="comment-body">Add a comment</Label>
        <Textarea
          id="comment-body"
          name="body"
          placeholder="Write a comment..."
          required
          disabled={isSubmitting}
        />
      </div>

      {canManageVisibility && (
        <div className="space-y-2">
          <Label htmlFor="comment-visibility">Visibility</Label>
          <select
            id="comment-visibility"
            name="visibility"
            defaultValue={DEFAULT_VISIBILITY}
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
        disabled={isSubmitting}
        shared={canManageVisibility && visibilityChoice === "SHARED"}
      />

      {error && <p className="text-destructive text-sm">{error}</p>}

      <div className="flex justify-end">
        <Button
          type="submit"
          disabled={isSubmitting || attachments.isBusy || attachments.hasErrors}
        >
          {isSubmitting ? "Posting..." : "Post Comment"}
        </Button>
      </div>
    </form>
  );
}
