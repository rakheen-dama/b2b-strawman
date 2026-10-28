"use client";

import { startTransition, useRef, useState, type FormEvent } from "react";
import { Button } from "@b2mash/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@b2mash/ui/label";
import { createComment } from "@/lib/actions/comments";
import { CommentAttachmentsField } from "@/components/comments/comment-attachments-field";
import { useCommentAttachments } from "@/components/comments/use-comment-attachments";

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
  // Synchronous in-flight flag: the submit handler runs inside a transition, so `isSubmitting` is
  // not committed until it has finished and cannot block a second submit on its own.
  const submittingRef = useRef(false);
  const [error, setError] = useState<string | null>(null);
  // Controlled draft: survives a handled failure (the chips stay too); cleared only on success.
  const [body, setBody] = useState("");
  const [visibility, setVisibility] = useState(DEFAULT_VISIBILITY);
  const attachments = useCommentAttachments({ slug: orgSlug, projectId });

  // onSubmit + startTransition rather than `<form action>`: the same transition, minus React's
  // automatic form reset, which a controlled <select> does not survive (its DOM would fall back to
  // the first option while state keeps the user's choice).
  function handleFormSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    startTransition(() => handleSubmit());
  }

  async function handleSubmit() {
    if (submittingRef.current) return;
    setError(null);

    const trimmedBody = body.trim();
    if (!trimmedBody) {
      setError("Comment cannot be empty.");
      return;
    }

    setIsSubmitting(true);
    submittingRef.current = true;

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
        trimmedBody,
        canManageVisibility ? visibility : undefined,
        confirmed.documentIds
      );
      if (result.success) {
        setBody("");
        setVisibility(DEFAULT_VISIBILITY);
        attachments.reset();
        onCommentChange?.();
      } else {
        setError(
          attachments.describeRejection(
            result.error ?? "Failed to add comment.",
            result.attachmentDocumentId
          )
        );
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      submittingRef.current = false;
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleFormSubmit} className="space-y-3">
      <div className="space-y-2">
        <Label htmlFor="comment-body">Add a comment</Label>
        <Textarea
          id="comment-body"
          name="body"
          placeholder="Write a comment..."
          value={body}
          onChange={(e) => setBody(e.target.value)}
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
            value={visibility}
            onChange={(e) => setVisibility(e.target.value)}
            disabled={isSubmitting}
            className="flex h-9 w-full rounded-md border border-slate-200 bg-transparent px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-slate-400 focus-visible:ring-1 focus-visible:ring-slate-500 focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50 dark:border-slate-800"
          >
            <option value="INTERNAL">Internal only</option>
            <option value="SHARED">Client visible</option>
          </select>
        </div>
      )}

      <CommentAttachmentsField
        attachments={attachments}
        disabled={isSubmitting}
        shared={canManageVisibility && visibility === "SHARED"}
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
