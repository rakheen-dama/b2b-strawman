"use client";

import useSWR from "swr";
import { MessageSquare } from "lucide-react";
import { fetchComments } from "@/lib/actions/comments";
import type { Comment } from "@/lib/actions/comments";
import { createMessages } from "@/lib/messages";
import { EmptyState } from "@/components/empty-state";
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
  const { t } = createMessages("empty-states");

  const {
    data: comments,
    error,
    isLoading,
    mutate,
  } = useSWR<Comment[]>(
    `comments-${entityType}-${entityId}`,
    () => fetchComments(projectId, entityType, entityId),
    { dedupingInterval: 2000 }
  );

  const refresh = () => mutate();

  if (isLoading) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400" aria-live="polite">
        Loading comments...
      </p>
    );
  }

  const commentList = comments ?? [];

  return (
    <div className="space-y-4">
      <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">
        Comments
      </h4>

      {error && <p className="text-sm text-red-600">Failed to load comments.</p>}

      {commentList.length === 0 && !error ? (
        <EmptyState
          icon={MessageSquare}
          title={t("comments.section.heading")}
          description={t("comments.section.description")}
        />
      ) : (
        <div className="space-y-3">
          {commentList.map((comment) => (
            <CommentItem
              key={comment.id}
              comment={comment}
              currentMemberId={currentMemberId}
              canManageVisibility={canManageVisibility}
              orgSlug={orgSlug}
              projectId={projectId}
              onCommentChange={refresh}
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
        onCommentChange={refresh}
      />
    </div>
  );
}
