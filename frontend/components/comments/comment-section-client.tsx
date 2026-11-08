"use client";

import { useMemo } from "react";
import useSWR from "swr";
import { useSearchParams } from "next/navigation";
import { MessageSquare } from "lucide-react";
import { fetchComments, fetchMentionCandidates } from "@/lib/actions/comments";
import type { MentionCandidate } from "@/lib/comments";
import type { Comment } from "@/lib/types";
import { createMessages } from "@/lib/messages";
import { EmptyState } from "@/components/empty-state";
import { AddCommentForm } from "@/components/comments/add-comment-form";
import { CommentItem } from "@/components/comments/comment-item";

interface CommentSectionClientProps {
  projectId: string;
  entityType: "TASK" | "DOCUMENT" | "PROJECT";
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
  // G88-M6: a notification deep link (?commentId=) highlights that comment. `?.` — the hook returns
  // null outside an App Router context (unit tests that render the section without a router).
  const targetCommentId = useSearchParams()?.get("commentId") ?? null;

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

  // 634C: @-mention candidates, fetched once per matter and shared by the form and every edit
  // dialog. Best-effort — without them the textarea simply offers no suggestions.
  const { data: candidates } = useSWR<MentionCandidate[]>(
    `mention-candidates-${projectId}`,
    () => fetchMentionCandidates(projectId),
    { revalidateOnFocus: false, shouldRetryOnError: false }
  );
  const mentionCandidates = useMemo(
    () => (candidates ?? []).filter((candidate) => candidate.id !== currentMemberId),
    [candidates, currentMemberId]
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
      <h4 className="text-sm font-medium text-slate-900 dark:text-slate-100">Comments</h4>

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
              highlighted={comment.id === targetCommentId}
              mentionCandidates={mentionCandidates}
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
        mentionCandidates={mentionCandidates}
      />
    </div>
  );
}
