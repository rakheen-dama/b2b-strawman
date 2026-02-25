"use client";

import { useEffect, useState, useTransition } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { AvatarCircle } from "@/components/ui/avatar-circle";
import {
  type Comment,
  fetchProjectComments,
  createProjectComment,
} from "@/lib/actions/comments";
import { formatRelativeDate } from "@/lib/format";

interface ProjectCommentsSectionProps {
  projectId: string;
  orgSlug: string;
}

export function ProjectCommentsSection({
  projectId,
  orgSlug,
}: ProjectCommentsSectionProps) {
  const [comments, setComments] = useState<Comment[]>([]);
  const [body, setBody] = useState("");
  const [isPending, startTransition] = useTransition();
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    fetchProjectComments(projectId)
      .then(setComments)
      .finally(() => setIsLoading(false));
  }, [projectId]);

  function handleSubmit() {
    if (!body.trim()) return;
    startTransition(async () => {
      await createProjectComment(orgSlug, projectId, body);
      setBody("");
      const updated = await fetchProjectComments(projectId);
      setComments(updated);
    });
  }

  if (isLoading) {
    return (
      <p className="text-sm text-muted-foreground">Loading comments...</p>
    );
  }

  return (
    <div className="space-y-4">
      {comments.length === 0 ? (
        <p className="text-sm text-muted-foreground">
          No customer comments yet. Comments shared between your team and
          customers will appear here.
        </p>
      ) : (
        <div className="space-y-3">
          {comments.map((comment) => (
            <div key={comment.id} className="flex gap-3">
              <AvatarCircle
                name={comment.authorName ?? "?"}
                size={32}
                className="mt-0.5 shrink-0"
              />
              <div className="flex-1 space-y-1">
                <div className="flex items-center gap-2">
                  <span className="text-sm font-medium">
                    {comment.authorName ?? "Unknown"}
                  </span>
                  {comment.source === "PORTAL" && (
                    <Badge variant="outline">Customer</Badge>
                  )}
                  <span className="text-xs text-muted-foreground">
                    {formatRelativeDate(comment.createdAt)}
                  </span>
                </div>
                <p className="text-sm whitespace-pre-wrap">{comment.body}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      <div className="space-y-2 border-t pt-4">
        <Textarea
          placeholder="Reply to the customer thread (visible to all linked customers)..."
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={3}
        />
        <Button
          onClick={handleSubmit}
          disabled={isPending || !body.trim()}
          size="sm"
        >
          {isPending ? "Sending..." : "Post Reply"}
        </Button>
      </div>
    </div>
  );
}
