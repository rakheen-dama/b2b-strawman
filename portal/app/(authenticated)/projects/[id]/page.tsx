"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import { ArrowLeft, Clock, Receipt } from "lucide-react";
import Link from "next/link";
import { portalGet } from "@/lib/api-client";
import { StatusBadge } from "@/components/status-badge";
import { TaskList } from "@/components/task-list";
import { DocumentList } from "@/components/document-list";
import { CommentSection } from "@/components/comment-section";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { formatCurrency } from "@/lib/format";
import type {
  PortalProjectDetail,
  PortalTask,
  PortalDocument,
  PortalComment,
  PortalProjectSummary,
} from "@/lib/types";

interface ProjectData {
  project: PortalProjectDetail;
  tasks: PortalTask[];
  documents: PortalDocument[];
  comments: PortalComment[];
  summary: PortalProjectSummary;
}

function PageSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-8 w-1/3" />
      <Skeleton className="h-4 w-2/3" />
      <div className="grid gap-4 sm:grid-cols-2">
        <Skeleton className="h-24" />
        <Skeleton className="h-24" />
      </div>
      <Skeleton className="h-48" />
      <Skeleton className="h-48" />
    </div>
  );
}

export default function ProjectDetailPage() {
  const params = useParams();
  const projectId = params.id as string;

  const [data, setData] = useState<ProjectData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchData() {
      try {
        const [project, tasks, documents, comments, summary] =
          await Promise.all([
            portalGet<PortalProjectDetail>(`/portal/projects/${projectId}`),
            portalGet<PortalTask[]>(`/portal/projects/${projectId}/tasks`),
            portalGet<PortalDocument[]>(
              `/portal/projects/${projectId}/documents`,
            ),
            portalGet<PortalComment[]>(
              `/portal/projects/${projectId}/comments`,
            ),
            portalGet<PortalProjectSummary>(
              `/portal/projects/${projectId}/summary`,
            ),
          ]);

        if (!cancelled) {
          setData({ project, tasks, documents, comments, summary });
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof Error ? err.message : "Failed to load project",
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchData();

    return () => {
      cancelled = true;
    };
  }, [projectId]);

  function handleCommentPosted() {
    // Refetch comments
    portalGet<PortalComment[]>(
      `/portal/projects/${projectId}/comments`,
    ).then((comments) => {
      setData((prev) => (prev ? { ...prev, comments } : prev));
    });
  }

  if (isLoading) {
    return <PageSkeleton />;
  }

  if (error) {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-700">
        {error}
      </div>
    );
  }

  if (!data) return null;

  const { project, tasks, documents, comments, summary } = data;

  return (
    <div className="space-y-8">
      {/* Back link */}
      <Link
        href="/projects"
        className="inline-flex items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Back to projects
      </Link>

      {/* Header */}
      <div>
        <div className="flex items-center gap-3">
          <h1 className="font-display text-2xl font-semibold text-slate-900">
            {project.name}
          </h1>
          <StatusBadge status={project.status} variant="project" />
        </div>
        {project.description && (
          <p className="mt-2 text-sm text-slate-600">{project.description}</p>
        )}
      </div>

      {/* Summary card — only shown if there are tracked hours */}
      {summary.totalHours > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Project Summary</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex gap-8">
              <div className="flex items-center gap-2">
                <Clock className="size-4 text-slate-400" />
                <div>
                  <p className="text-sm font-medium text-slate-900">
                    {summary.totalHours.toFixed(1)}h total
                  </p>
                  <p className="text-xs text-slate-500">
                    {summary.billableHours.toFixed(1)}h billable
                  </p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Tasks */}
      <section>
        <h2 className="font-display mb-4 text-lg font-semibold text-slate-900">
          Tasks
        </h2>
        <TaskList tasks={tasks} />
      </section>

      {/* Documents */}
      <section>
        <h2 className="font-display mb-4 text-lg font-semibold text-slate-900">
          Documents
        </h2>
        <DocumentList documents={documents} />
      </section>

      {/* Comments */}
      <section>
        <h2 className="font-display mb-4 text-lg font-semibold text-slate-900">
          Comments
        </h2>
        <CommentSection
          projectId={projectId}
          comments={comments}
          onCommentPosted={handleCommentPosted}
        />
      </section>
    </div>
  );
}
