"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  ArrowLeft,
  Loader2,
  AlertCircle,
  Calendar,
  FileText,
  CheckSquare,
  User,
  Clock,
  DollarSign,
  Activity,
  MessageSquare,
  Send,
} from "lucide-react";
import { portalApi, PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import { PortalDocumentTable } from "@/components/portal/portal-document-table";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Card, CardContent } from "@/components/ui/card";
import { formatDate } from "@/lib/format";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import type {
  PortalProject,
  PortalDocument,
  PortalTask,
  PortalSummary,
  PortalComment,
} from "@/lib/types";

function getTaskStatusVariant(status: string): "neutral" | "warning" | "success" | "destructive" {
  switch (status) {
    case "IN_PROGRESS":
      return "warning";
    case "DONE":
      return "success";
    case "CANCELLED":
      return "destructive";
    default:
      return "neutral";
  }
}

function formatTaskStatus(status: string): string {
  return status.replace(/_/g, " ");
}

function PortalTaskList({ tasks }: { tasks: PortalTask[] }) {
  if (tasks.length === 0) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center justify-center py-12 text-center">
          <CheckSquare className="mb-3 size-8 text-slate-300 dark:text-slate-600" />
          <p className="text-sm text-slate-500 dark:text-slate-400">No tasks for this project</p>
        </CardContent>
      </Card>
    );
  }

  const sorted = [...tasks].sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <Card>
      <CardContent className="divide-y divide-slate-100 p-0 dark:divide-slate-800">
        {sorted.map((task) => (
          <div key={task.id} className="flex items-center justify-between px-4 py-3">
            <div className="flex flex-col gap-1">
              <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                {task.name}
              </span>
              <span className="flex items-center gap-1 text-xs text-slate-500 dark:text-slate-400">
                <User className="size-3" />
                {task.assigneeName ?? "Unassigned"}
              </span>
            </div>
            <Badge variant={getTaskStatusVariant(task.status)}>
              {formatTaskStatus(task.status)}
            </Badge>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function PortalCommentSection({ projectId }: { projectId: string }) {
  const [comments, setComments] = useState<PortalComment[]>([]);
  const [newComment, setNewComment] = useState("");
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    portalApi
      .get<PortalComment[]>(`/portal/projects/${projectId}/comments`)
      .then(setComments)
      .catch(() => {})
      .finally(() => setIsLoading(false));
  }, [projectId]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const content = newComment.trim();
    if (!content || isSubmitting) return;
    setIsSubmitting(true);
    try {
      const comment = await portalApi.post<PortalComment>(
        `/portal/projects/${projectId}/comments`,
        { content }
      );
      setComments((prev) => [...prev, comment]);
      setNewComment("");
    } catch {
      // silently fail — user can retry
    } finally {
      setIsSubmitting(false);
    }
  }

  if (isLoading) {
    return (
      <Card>
        <CardContent className="flex items-center justify-center py-12">
          <Loader2 className="size-5 animate-spin text-slate-400" />
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {/* Comment list */}
      {comments.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center justify-center py-12 text-center">
            <MessageSquare className="mb-3 size-8 text-slate-300 dark:text-slate-600" />
            <p className="text-sm text-slate-500 dark:text-slate-400">
              No comments yet — start the conversation
            </p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="divide-y divide-slate-100 p-0 dark:divide-slate-800">
            {comments.map((comment) => (
              <div key={comment.id} className="px-4 py-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                    {comment.authorName}
                  </span>
                  <span className="text-xs text-slate-500">{formatDate(comment.createdAt)}</span>
                </div>
                <p className="mt-1 text-sm whitespace-pre-wrap text-slate-600 dark:text-slate-400">
                  {comment.content}
                </p>
              </div>
            ))}
          </CardContent>
        </Card>
      )}

      {/* New comment form */}
      <form onSubmit={handleSubmit} className="flex gap-2">
        <Textarea
          value={newComment}
          onChange={(e) => setNewComment(e.target.value)}
          placeholder="Write a comment..."
          className="min-h-[80px] resize-none"
          maxLength={2000}
        />
        <Button
          type="submit"
          variant="default"
          size="icon"
          className="mt-auto shrink-0"
          disabled={!newComment.trim() || isSubmitting}
        >
          {isSubmitting ? <Loader2 className="size-4 animate-spin" /> : <Send className="size-4" />}
        </Button>
      </form>
    </div>
  );
}

export default function PortalProjectDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const [project, setProject] = useState<PortalProject | null>(null);
  const [documents, setDocuments] = useState<PortalDocument[]>([]);
  const [tasks, setTasks] = useState<PortalTask[]>([]);
  const [summary, setSummary] = useState<PortalSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchData() {
      try {
        const [projectData, docsData, tasksData, summaryData] = await Promise.all([
          portalApi.get<PortalProject>(`/portal/projects/${params.id}`),
          portalApi.get<PortalDocument[]>(`/portal/projects/${params.id}/documents`),
          portalApi.get<PortalTask[]>(`/portal/projects/${params.id}/tasks`),
          portalApi.get<PortalSummary>(`/portal/projects/${params.id}/summary`).catch(() => null),
        ]);
        setProject(projectData);
        setDocuments(docsData);
        setTasks(tasksData);
        setSummary(summaryData);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(err instanceof Error ? err.message : "Failed to load project");
      } finally {
        setIsLoading(false);
      }
    }
    fetchData();
  }, [params.id, router]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="space-y-4">
        <Link
          href="/portal/projects"
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
        <div
          className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300"
          role="alert"
        >
          <AlertCircle className="size-4 shrink-0" />
          {error}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Back link */}
      <div>
        <Link
          href="/portal/projects"
          className="inline-flex items-center text-sm text-slate-600 transition-colors hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100"
        >
          <ArrowLeft className="mr-1.5 size-4" />
          Back to Projects
        </Link>
      </div>

      {/* Project header */}
      <div>
        <div className="flex items-center gap-3">
          <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
            {project?.name}
          </h1>
          {project?.status && <Badge variant="neutral">{project.status}</Badge>}
        </div>
        {project?.description ? (
          <p className="mt-2 text-slate-600 dark:text-slate-400">{project.description}</p>
        ) : (
          <p className="mt-2 text-sm text-slate-400 italic dark:text-slate-600">No description</p>
        )}
        {project?.createdAt && (
          <div className="mt-2 flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-500">
            <Calendar className="size-3.5" />
            <span>Created {formatDate(project.createdAt)}</span>
          </div>
        )}
      </div>

      {/* Summary */}
      {summary && (
        <div className="grid grid-cols-3 gap-4">
          <Card>
            <CardContent className="flex items-center gap-3 py-4">
              <Clock className="size-5 text-slate-400" />
              <div>
                <p className="text-xs text-slate-500">Total Hours</p>
                <p className="font-mono text-lg font-semibold text-slate-900 tabular-nums dark:text-slate-100">
                  {summary.totalHours}
                </p>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="flex items-center gap-3 py-4">
              <DollarSign className="size-5 text-slate-400" />
              <div>
                <p className="text-xs text-slate-500">Billable Hours</p>
                <p className="font-mono text-lg font-semibold text-slate-900 tabular-nums dark:text-slate-100">
                  {summary.billableHours}
                </p>
              </div>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="flex items-center gap-3 py-4">
              <Activity className="size-5 text-slate-400" />
              <div>
                <p className="text-xs text-slate-500">Last Activity</p>
                <p className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {summary.lastActivityAt ? formatDate(summary.lastActivityAt) : "—"}
                </p>
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {/* Documents & Tasks */}
      <Tabs defaultValue="documents">
        <TabsList>
          <TabsTrigger value="documents">
            <FileText className="mr-1.5 size-4" />
            Documents ({documents.length})
          </TabsTrigger>
          <TabsTrigger value="tasks">
            <CheckSquare className="mr-1.5 size-4" />
            Tasks ({tasks.length})
          </TabsTrigger>
          <TabsTrigger value="comments">
            <MessageSquare className="mr-1.5 size-4" />
            Comments{project?.commentCount ? ` (${project.commentCount})` : ""}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="documents">
          <PortalDocumentTable documents={documents} title="Project Documents" />
        </TabsContent>
        <TabsContent value="tasks">
          <PortalTaskList tasks={tasks} />
        </TabsContent>
        <TabsContent value="comments">
          {params.id && <PortalCommentSection projectId={params.id} />}
        </TabsContent>
      </Tabs>
    </div>
  );
}
