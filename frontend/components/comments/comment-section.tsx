import { MessageSquare } from "lucide-react";
import { fetchComments } from "@/lib/actions/comments";
import { EmptyState } from "@/components/empty-state";
import { AddCommentForm } from "@/components/comments/add-comment-form";
import { CommentItem } from "@/components/comments/comment-item";

interface CommentSectionProps {
  projectId: string;
  entityType: "TASK" | "DOCUMENT";
  entityId: string;
  orgSlug: string;
  currentMemberId: string;
  canManageVisibility: boolean;
}

export async function CommentSection({
  projectId,
  entityType,
  entityId,
  orgSlug,
  currentMemberId,
  canManageVisibility,
}: CommentSectionProps) {
  const comments = await fetchComments(projectId, entityType, entityId);

  return (
    <div className="space-y-4">
      <h3 className="font-display text-sm font-medium text-olive-900 dark:text-olive-100">
        Comments
      </h3>

      {comments.length === 0 ? (
        <EmptyState
          icon={MessageSquare}
          title="No comments yet"
          description="Be the first to add a comment."
        />
      ) : (
        <div className="space-y-4">
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
