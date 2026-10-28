"use client";

import { Pencil, Trash2 } from "lucide-react";
import { Badge } from "@b2mash/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import { EditCommentDialog } from "@/components/comments/edit-comment-dialog";
import { DeleteCommentDialog } from "@/components/comments/delete-comment-dialog";
import { CommentAttachmentChip } from "@/components/comments/comment-attachment-chip";
import { RelativeDate } from "@/components/ui/relative-date";
import type { Comment } from "@/lib/types";

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
  const attachments = comment.attachments ?? [];

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
