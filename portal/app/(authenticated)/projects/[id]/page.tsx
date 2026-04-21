"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import { ArrowLeft, Clock } from "lucide-react";
import Link from "next/link";
import { portalGet } from "@/lib/api-client";
import { StatusBadge } from "@/components/status-badge";
import { TaskList } from "@/components/task-list";
import { DocumentList } from "@/components/document-list";
import { CommentSection } from "@/components/comment-section";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
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

  const isMountedRef = useRef(true);
  const requestIdRef = useRef(0);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  const fetchData = useCallback(async () => {
    const requestId = ++requestIdRef.current;
    setError(null);
    setIsLoading(true);
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

      if (!isMountedRef.current || requestId !== requestIdRef.current) return;
      setData({ project, tasks, documents, comments, summary });
    } catch (err) {
      if (!isMountedRef.current || requestId !== requestIdRef.current) return;
      setError(err instanceof Error ? err.message : "Failed to load project");
    } finally {
      if (isMountedRef.current && requestId === requestIdRef.current) {
        setIsLoading(false);
      }
    }
  }, [projectId]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  function handleCommentPosted() {
    // Refetch comments
    portalGet<PortalComment[]>(
      `/portal/projects/${projectId}/comments`,
    ).then((comments) => {
      setData((prev) => (prev ? { ...prev, comments } : prev));
    }).catch(() => {});
  }

  if (isLoading) {
    return <PageSkeleton />;
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-center">
        <p className="text-lg font-medium text-slate-600">
          {error ?? "Project not found"}
        </p>
        <p className="mt-1 text-sm text-slate-500">
          This project may have been removed, you may not have access, or the
          request failed. Please try again.
        </p>
        <div className="mt-6 flex flex-col items-center gap-3 sm:flex-row">
          <button
            type="button"
            onClick={() => fetchData()}
            className="inline-flex min-h-11 items-center rounded-md bg-white px-3 py-1.5 text-sm font-medium text-teal-700 ring-1 ring-teal-200 hover:bg-teal-50"
          >
            Try again
          </button>
          <Link
            href="/projects"
            className="inline-flex min-h-11 items-center text-sm text-teal-600 hover:underline"
          >
            Back to projects
          </Link>
        </div>
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
        className="inline-flex min-h-11 items-center gap-1.5 text-sm text-slate-500 hover:text-slate-700"
      >
        <ArrowLeft className="size-4" />
        Back to projects
      </Link>

      {/* Header */}
      <div>
        <div className="flex flex-wrap items-center gap-3">
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
            <div className="flex flex-col gap-4 md:flex-row md:gap-8">
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
