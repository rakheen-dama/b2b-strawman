"use client";

import { useEffect, useState } from "react";
import { fetchComments } from "@/lib/actions/comments";
import type { Comment } from "@/lib/actions/comments";
import { AddCommentForm } from "@/components/comments/add-comment-form";
import { CommentItem } from "@/components/comments/comment-item";

interface CommentSectionClientProps {
  projectId: string;
  entityType: "TASK" | "DOCUMENT";
  entityId: string;
  orgSlug: string;
  currentMemberId: string;
  canManageVisibility: boolean;
}

export function CommentSectionClient({
  projectId,
  entityType,
  entityId,
  orgSlug,
  currentMemberId,
  canManageVisibility,
}: CommentSectionClientProps) {
  const [comments, setComments] = useState<Comment[] | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchComments(projectId, entityType, entityId)
      .then((data) => {
        if (!cancelled) setComments(data);
      })
      .catch(() => {
        if (!cancelled) setComments([]);
      });
    return () => {
      cancelled = true;
    };
  }, [projectId, entityType, entityId]);

  const isLoading = comments === null;

  if (isLoading) {
    return (
      <p className="text-sm text-olive-500 dark:text-olive-400">
        Loading comments...
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <h4 className="text-sm font-medium text-olive-900 dark:text-olive-100">
        Comments
      </h4>

      {comments.length === 0 ? (
        <p className="text-sm text-olive-500 dark:text-olive-400">
          No comments yet. Be the first to add one.
        </p>
      ) : (
        <div className="space-y-3">
          {comments.map((comment) => (
            <CommentItem
              key={comment.id}
              comment={comment}
              currentMemberId={currentMemberId}
              canManageVisibility={canManageVisibility}
              orgSlug={orgSlug}
              projectId={projectId}
            />
          ))}
        </div>
      )}

      <AddCommentForm
        projectId={projectId}
        entityType={entityType}
        entityId={entityId}
        orgSlug={orgSlug}
        canManageVisibility={canManageVisibility}
      />
    </div>
  );
}
