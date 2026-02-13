"use client";

import { useState } from "react";
import { Pencil, Trash2, AlertTriangle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { deleteComment } from "@/lib/actions/comments";
import { EditCommentDialog } from "@/components/comments/edit-comment-dialog";
import { formatRelativeDate } from "@/lib/format";
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
  const [isDeleting, setIsDeleting] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const isOwnComment = comment.authorMemberId === currentMemberId;
  const authorName = comment.authorName ?? "Unknown";

  async function handleDelete() {
    setDeleteError(null);
    setIsDeleting(true);

    try {
      const result = await deleteComment(orgSlug, projectId, comment.id);
      if (result.success) {
        setDeleteOpen(false);
        onCommentChange?.();
      } else {
        setDeleteError(result.error ?? "Failed to delete comment.");
      }
    } catch {
      setDeleteError("An unexpected error occurred.");
    } finally {
      setIsDeleting(false);
    }
  }

  function handleDeleteOpenChange(newOpen: boolean) {
    if (newOpen) {
      setDeleteError(null);
    }
    setDeleteOpen(newOpen);
  }

  return (
    <div className="flex gap-3">
      <AvatarCircle name={authorName} size={32} className="mt-0.5 shrink-0" />

      <div className="min-w-0 flex-1">
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium text-olive-900 dark:text-olive-100">
            {authorName}
          </span>
          <span className="text-xs text-olive-500 dark:text-olive-400">
            {formatRelativeDate(comment.createdAt)}
          </span>
          {comment.visibility === "SHARED" && (
            <Badge variant="success">Customer visible</Badge>
          )}
        </div>

        <p className="mt-1 whitespace-pre-wrap text-sm text-olive-700 dark:text-olive-300">
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
            >
              <Button
                variant="ghost"
                size="xs"
                aria-label={`Edit comment by ${authorName}`}
              >
                <Pencil className="size-3" />
                Edit
              </Button>
            </EditCommentDialog>

            <AlertDialog open={deleteOpen} onOpenChange={handleDeleteOpenChange}>
              <AlertDialogTrigger asChild>
                <Button
                  variant="ghost"
                  size="xs"
                  aria-label={`Delete comment by ${authorName}`}
                >
                  <Trash2 className="size-3" />
                  Delete
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent className="border-t-4 border-t-red-500">
                <AlertDialogHeader>
                  <div className="flex justify-center">
                    <div className="flex size-12 items-center justify-center rounded-full bg-red-100 dark:bg-red-950">
                      <AlertTriangle className="size-6 text-red-600 dark:text-red-400" />
                    </div>
                  </div>
                  <AlertDialogTitle className="text-center">
                    Delete Comment
                  </AlertDialogTitle>
                  <AlertDialogDescription className="text-center">
                    Delete this comment? This action cannot be undone.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                {deleteError && (
                  <p className="text-sm text-destructive">{deleteError}</p>
                )}
                <AlertDialogFooter>
                  <AlertDialogCancel variant="plain" disabled={isDeleting}>
                    Cancel
                  </AlertDialogCancel>
                  <Button
                    variant="destructive"
                    onClick={handleDelete}
                    disabled={isDeleting}
                  >
                    {isDeleting ? "Deleting..." : "Delete"}
                  </Button>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          </div>
        )}
      </div>
    </div>
  );
}
