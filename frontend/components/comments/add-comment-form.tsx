"use client";

import { useState, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { createComment } from "@/lib/actions/comments";

interface AddCommentFormProps {
  projectId: string;
  entityType: "TASK" | "DOCUMENT";
  entityId: string;
  orgSlug: string;
  canManageVisibility: boolean;
}

export function AddCommentForm({
  projectId,
  entityType,
  entityId,
  orgSlug,
  canManageVisibility,
}: AddCommentFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const formRef = useRef<HTMLFormElement>(null);

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
      const result = await createComment(
        orgSlug,
        projectId,
        entityType,
        entityId,
        body,
        visibility
      );
      if (result.success) {
        formRef.current?.reset();
      } else {
        setError(result.error ?? "Failed to add comment.");
      }
    } catch {
      setError("An unexpected error occurred.");
    } finally {
      setIsSubmitting(false);
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
            defaultValue="INTERNAL"
            disabled={isSubmitting}
            className="flex h-9 w-full rounded-md border border-olive-200 bg-transparent px-3 py-1 text-sm shadow-xs transition-colors placeholder:text-olive-400 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-olive-500 disabled:cursor-not-allowed disabled:opacity-50 dark:border-olive-800"
          >
            <option value="INTERNAL">Internal only</option>
            <option value="SHARED">Customer visible</option>
          </select>
        </div>
      )}

      {error && <p className="text-sm text-destructive">{error}</p>}

      <div className="flex justify-end">
        <Button type="submit" disabled={isSubmitting}>
          {isSubmitting ? "Posting..." : "Post Comment"}
        </Button>
      </div>
    </form>
  );
}
