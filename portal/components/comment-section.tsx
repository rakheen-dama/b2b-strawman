"use client";

import { useState } from "react";
import { MessageSquare, Loader2, Paperclip } from "lucide-react";
import { Button } from "@b2mash/ui/button";
import { portalPost } from "@/lib/api-client";
import { fetchDocumentDownloadUrl } from "@/lib/api/documents";
import { formatFileSize, formatRelativeDate } from "@b2mash/shared/format";
import type { PortalComment, PortalCommentAttachment } from "@/lib/types";

const MAX_CHARS = 2000;
const WARN_THRESHOLD = 1800;
const DOWNLOAD_ERROR = "Download failed. Please try again.";

interface AttachmentChipProps {
  attachment: PortalCommentAttachment;
}

function AttachmentChip({ attachment }: AttachmentChipProps) {
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleDownload() {
    if (isLoading) return;
    setIsLoading(true);
    setError(null);
    try {
      const presignedUrl = await fetchDocumentDownloadUrl(
        attachment.documentId,
      );
      window.open(presignedUrl, "_blank");
    } catch {
      // A 401 is handled by portalFetch (clear auth, go to /login);
      // anything else stays on the page with an inline error.
      setError(DOWNLOAD_ERROR);
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <span className="inline-flex max-w-full flex-wrap items-center gap-1.5">
      <button
        type="button"
        onClick={handleDownload}
        disabled={isLoading}
        aria-busy={isLoading}
        aria-label={`Download ${attachment.fileName}`}
        className="inline-flex min-h-11 max-w-full items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs text-slate-700 hover:bg-slate-100 disabled:opacity-60 md:min-h-0"
      >
        {isLoading ? (
          <Loader2
            className="size-3 shrink-0 animate-spin"
            aria-hidden="true"
          />
        ) : (
          <Paperclip
            className="size-3 shrink-0 text-slate-400"
            aria-hidden="true"
          />
        )}
        <span className="truncate font-medium">{attachment.fileName}</span>
        <span className="shrink-0 text-slate-500">
          {formatFileSize(attachment.size)}
        </span>
      </button>
      {error && (
        <span role="alert" className="text-xs text-red-600">
          {error}
        </span>
      )}
    </span>
  );
}

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
  const [localComments, setLocalComments] = useState<PortalComment[]>(comments);
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
      await portalPost(`/portal/matters/${projectId}/comments`, {
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
              {comment.attachments?.length ? (
                <ul
                  aria-label="Attachments"
                  className="mt-3 flex flex-wrap gap-2"
                >
                  {comment.attachments.map((attachment) => (
                    <li key={attachment.documentId} className="max-w-full">
                      <AttachmentChip attachment={attachment} />
                    </li>
                  ))}
                </ul>
              ) : null}
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
            disabled={!content.trim() || isOverLimit || isSubmitting}
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
