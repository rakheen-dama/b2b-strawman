"use client";

import { useEffect, useRef, useState } from "react";
import { Pencil, Trash2 } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EditCommentDialog } from "@/components/comments/edit-comment-dialog";
import { DeleteCommentDialog } from "@/components/comments/delete-comment-dialog";
import { CommentAttachmentChip } from "@/components/comments/comment-attachment-chip";
import { RelativeDate } from "@/components/ui/relative-date";
import type { MentionCandidate } from "@/lib/comments";
import type { Comment } from "@/lib/types";
import { cn } from "@/lib/utils";

interface CommentItemProps {
  comment: Comment;
  currentMemberId: string;
  canManageVisibility: boolean;
  orgSlug: string;
  projectId: string;
  onCommentChange?: () => void;
  /** Targeted by a notification deep link (G88-M6): scrolled into view and ringed for ~2 s. */
  highlighted?: boolean;
  /** Members the edit dialog's @-mention autocomplete offers (634C). */
  mentionCandidates?: MentionCandidate[];
}

const HIGHLIGHT_MS = 2000;

export function CommentItem({
  comment,
  currentMemberId,
  canManageVisibility,
  orgSlug,
  projectId,
  onCommentChange,
  highlighted = false,
  mentionCandidates,
}: CommentItemProps) {
  const ref = useRef<HTMLDivElement>(null);
  const [ringOn, setRingOn] = useState(highlighted);
  // Re-arm the ring on every activation (false→true), not just on mount: an already-mounted item
  // can be re-targeted by a later notification click. Adjusting state during render keeps this
  // lint-clean (no synchronous setState in an effect).
  const [prevHighlighted, setPrevHighlighted] = useState(highlighted);
  if (highlighted !== prevHighlighted) {
    setPrevHighlighted(highlighted);
    setRingOn(highlighted);
  }

  useEffect(() => {
    if (!highlighted) return;
    ref.current?.scrollIntoView({ block: "center" });
    const timer = setTimeout(() => setRingOn(false), HIGHLIGHT_MS);
    return () => clearTimeout(timer);
  }, [highlighted]);

  const isOwnComment = comment.authorMemberId === currentMemberId;
  const authorName = comment.authorName ?? "Unknown";
  const attachments = comment.attachments ?? [];

  return (
    <div
      ref={ref}
      id={`comment-${comment.id}`}
      data-highlighted={ringOn ? "true" : undefined}
      className={cn(
        "flex gap-3 rounded-md transition-shadow",
        ringOn && "ring-2 ring-teal-500 ring-offset-2 dark:ring-offset-slate-950"
      )}
    >
      <AvatarCircle name={authorName} size={32} className="mt-0.5 shrink-0" />

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
            {authorName}
          </span>
          {/* Portal-authored (client) comments — parity with the retired Client comments section (628A). */}
          {comment.source === "PORTAL" && <Badge variant="outline">Client</Badge>}
          <span className="text-xs text-slate-500 dark:text-slate-400">
            <RelativeDate iso={comment.createdAt} />
          </span>
          {comment.visibility === "SHARED" && <Badge variant="success">Client visible</Badge>}
        </div>

        <p className="mt-1 text-sm whitespace-pre-wrap text-slate-700 dark:text-slate-300">
          {comment.body}
        </p>

        {attachments.length > 0 && (
          <ul aria-label="Attachments" className="mt-2 flex flex-wrap gap-2">
            {attachments.map((attachment) => (
              <li key={attachment.documentId} className="max-w-full">
                <CommentAttachmentChip
                  mode="posted"
                  documentId={attachment.documentId}
                  fileName={attachment.fileName}
                  size={attachment.size}
                />
              </li>
            ))}
          </ul>
        )}

        {isOwnComment && (
          <div className="mt-1 flex gap-1">
            <EditCommentDialog
              comment={comment}
              orgSlug={orgSlug}
              projectId={projectId}
              canManageVisibility={canManageVisibility}
              onCommentChange={onCommentChange}
              triggerLabel="Edit"
              triggerIcon={<Pencil className="size-3" />}
              triggerAriaLabel={`Edit comment by ${authorName}`}
              mentionCandidates={mentionCandidates}
            />

            <DeleteCommentDialog
              commentId={comment.id}
              orgSlug={orgSlug}
              projectId={projectId}
              onCommentChange={onCommentChange}
              triggerLabel="Delete"
              triggerIcon={<Trash2 className="size-3" />}
              triggerAriaLabel={`Delete comment by ${authorName}`}
            />
          </div>
        )}
      </div>
    </div>
  );
}
