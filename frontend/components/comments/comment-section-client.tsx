"use client";

import { useCallback, useEffect, useState } from "react";
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
  const [comments, setComments] = useState<Comment[] | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => setRefreshKey((k) => k + 1), []);

  useEffect(() => {
    let cancelled = false;
    fetchComments(projectId, entityType, entityId)
      .then((data) => {
        if (!cancelled) {
          setComments(data);
          setError(null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setComments([]);
          setError("Failed to load comments.");
        }
      });
    return () => {
      cancelled = true;
    };
  }, [projectId, entityType, entityId, refreshKey]);

  const isLoading = comments === null;

  if (isLoading) {
    return (
      <p className="text-sm text-slate-500 dark:text-slate-400" aria-live="polite">
        Loading comments...
      </p>
    );
  }

  return (
    <div className="space-y-4">
      <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">
        Comments
      </h4>

      {error && <p className="text-sm text-red-600">{error}</p>}

      {comments.length === 0 && !error ? (
        <EmptyState
          icon={MessageSquare}
          title={t("comments.section.heading")}
          description={t("comments.section.description")}
        />
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
