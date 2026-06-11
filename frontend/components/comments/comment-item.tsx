"use client";

import { Pencil, Sparkles, Trash2 } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EditCommentDialog } from "@/components/comments/edit-comment-dialog";
import { DeleteCommentDialog } from "@/components/comments/delete-comment-dialog";
import { RelativeDate } from "@/components/ui/relative-date";
import { SPECIALIST_STRINGS } from "@/components/assistant/specialist-strings";
import type { Comment } from "@/lib/actions/comments";

interface CommentItemProps {
  comment: Comment;
  currentMemberId: string;
  canManageVisibility: boolean;
  orgSlug: string;
  projectId: string;
  onCommentChange?: () => void;
}

export function CommentItem({
  comment,
  currentMemberId,
  canManageVisibility,
  orgSlug,
  projectId,
  onCommentChange,
}: CommentItemProps) {
  const isOwnComment = comment.authorMemberId === currentMemberId;
  const authorName = comment.authorName ?? "Unknown";

  return (
    <div className="flex gap-3">
      <AvatarCircle name={authorName} size={32} className="mt-0.5 shrink-0" />

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
            {authorName}
          </span>
          <span className="text-xs text-slate-500 dark:text-slate-400">
            <RelativeDate iso={comment.createdAt} />
          </span>
          {comment.attribution === "Inbox Assistant" && (
            <span
              data-testid="inbox-assistant-tag"
              className="inline-flex items-center gap-1 rounded-full bg-teal-50 px-2 py-0.5 text-[10px] font-semibold text-teal-700 dark:bg-teal-900/40 dark:text-teal-300"
            >
              <Sparkles className="size-3" />
              {SPECIALIST_STRINGS.inboxAssistantTag}
            </span>
          )}
          {comment.visibility === "SHARED" && <Badge variant="success">Customer visible</Badge>}
        </div>

        <p className="mt-1 text-sm whitespace-pre-wrap text-slate-700 dark:text-slate-300">
          {comment.body}
        </p>

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
