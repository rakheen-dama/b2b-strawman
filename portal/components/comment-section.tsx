"use client";

import { useState } from "react";
import { MessageSquare, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { portalPost } from "@/lib/api-client";
import { formatRelativeDate } from "@/lib/format";
import type { PortalComment } from "@/lib/types";

const MAX_CHARS = 2000;
const WARN_THRESHOLD = 1800;

interface CommentSectionProps {
  projectId: string;
  comments: PortalComment[];
  onCommentPosted: () => void;
}

export function CommentSection({
  projectId,
  comments,
  onCommentPosted,
}: CommentSectionProps) {
  const [localComments, setLocalComments] =
    useState<PortalComment[]>(comments);
  const [content, setContent] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Sync with parent when comments prop changes
  // (e.g., after a refetch triggered by onCommentPosted)
  const [prevComments, setPrevComments] = useState(comments);
  if (comments !== prevComments) {
    setPrevComments(comments);
    setLocalComments(comments);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = content.trim();
    if (!trimmed || isSubmitting) return;

    setIsSubmitting(true);

    // Optimistic update
    const optimisticComment: PortalComment = {
      id: `temp-${Date.now()}`,
      authorName: "You",
      content: trimmed,
      createdAt: new Date().toISOString(),
    };
    setLocalComments((prev) => [...prev, optimisticComment]);
    setContent("");

    try {
      await portalPost(`/portal/projects/${projectId}/comments`, {
        content: trimmed,
      });
      onCommentPosted();
    } catch {
      // Revert optimistic update on failure
      setLocalComments((prev) =>
        prev.filter((c) => c.id !== optimisticComment.id),
      );
      setContent(trimmed);
    } finally {
      setIsSubmitting(false);
    }
  }

  const showCharCount = content.length > WARN_THRESHOLD;
  const isOverLimit = content.length > MAX_CHARS;

  return (
    <div className="space-y-6">
      {localComments.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-12 text-center">
          <MessageSquare className="mb-4 size-10 text-slate-300" />
          <p className="text-sm text-slate-500">No comments yet.</p>
        </div>
      ) : (
        <div className="space-y-4">
          {localComments.map((comment) => (
            <div
              key={comment.id}
              className="rounded-lg border border-slate-200/80 bg-white p-4"
            >
              <div className="mb-2 flex items-center gap-2">
                <span className="text-sm font-medium text-slate-900">
                  {comment.authorName}
                </span>
                <span className="text-xs text-slate-400">
                  {formatRelativeDate(comment.createdAt)}
                </span>
              </div>
              <p className="whitespace-pre-wrap text-sm text-slate-700">
                {comment.content}
              </p>
            </div>
          ))}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-3">
        <label
          htmlFor="comment-input"
          className="text-sm font-medium text-slate-700"
        >
          Add a comment
        </label>
        <textarea
          id="comment-input"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="Write a comment..."
          rows={3}
          maxLength={MAX_CHARS}
          className="border-slate-200 placeholder:text-slate-400 focus-visible:border-slate-400 focus-visible:ring-slate-500/30 dark:border-slate-700 dark:bg-input/30 w-full min-w-0 rounded-md border bg-transparent px-3 py-2 text-sm transition-[color,box-shadow] outline-none focus-visible:ring-[3px]"
        />
        <div className="flex items-center justify-between">
          <div>
            {showCharCount && (
              <span
                className={`text-xs ${isOverLimit ? "text-red-600" : "text-slate-400"}`}
              >
                {content.length}/{MAX_CHARS}
              </span>
            )}
          </div>
          <Button
            type="submit"
            size="sm"
            disabled={
              !content.trim() || isOverLimit || isSubmitting
            }
          >
            {isSubmitting ? (
              <>
                <Loader2 className="size-3.5 animate-spin" />
                Posting...
              </>
            ) : (
              "Post Comment"
            )}
          </Button>
        </div>
      </form>
    </div>
  );
}
