"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FolderOpen, FileText, Loader2, AlertCircle } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { EmptyState } from "@/components/empty-state";
import { portalApi, PortalApiError, clearPortalAuth } from "@/lib/portal-api";
import { useRouter } from "next/navigation";
import type { PortalProject } from "@/lib/types";

export default function PortalProjectListPage() {
  const router = useRouter();
  const [projects, setProjects] = useState<PortalProject[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    async function fetchProjects() {
      try {
        const data = await portalApi.get<PortalProject[]>("/portal/projects");
        setProjects(data);
      } catch (err) {
        if (err instanceof PortalApiError && err.status === 401) {
          clearPortalAuth();
          router.replace("/portal");
          return;
        }
        setError(err instanceof Error ? err.message : "Failed to load projects");
      } finally {
        setIsLoading(false);
      }
    }
    fetchProjects();
  }, [router]);

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center">
        <Loader2 className="size-8 animate-spin text-slate-400" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center gap-2 rounded-lg bg-red-50 px-4 py-3 text-sm text-red-700 dark:bg-red-950 dark:text-red-300" role="alert">
        <AlertCircle className="size-4 shrink-0" />
        {error}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-2xl text-slate-950 dark:text-slate-50">
            Projects
          </h1>
          <Badge variant="neutral">{projects.length}</Badge>
        </div>
      </div>

      {/* Content */}
      {projects.length === 0 ? (
        <EmptyState
          icon={FolderOpen}
          title="No projects yet"
          description="You don't have access to any projects at the moment"
        />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2">
          {projects.map((project) => (
            <Link
              key={project.id}
              href={`/portal/projects/${project.id}`}
              className="group rounded-lg border border-slate-200 bg-white p-5 transition-all hover:border-slate-300 hover:shadow-sm dark:border-slate-800 dark:bg-slate-900 dark:hover:border-slate-700"
            >
              <div className="flex items-start gap-3">
                <FolderOpen className="mt-0.5 size-5 text-slate-400 dark:text-slate-500" />
                <div className="min-w-0 flex-1">
                  <h3 className="font-semibold text-slate-900 group-hover:text-slate-950 dark:text-slate-100 dark:group-hover:text-slate-50">
                    {project.name}
                  </h3>
                  {project.description ? (
                    <p className="mt-1 line-clamp-2 text-sm text-slate-600 dark:text-slate-400">
                      {project.description}
                    </p>
                  ) : (
                    <p className="mt-1 text-sm italic text-slate-400 dark:text-slate-600">
                      No description
                    </p>
                  )}
                  <div className="mt-3 flex items-center gap-1.5 text-xs text-slate-500 dark:text-slate-500">
                    <FileText className="size-3.5" />
                    <span>
                      {project.documentCount}{" "}
                      {project.documentCount === 1 ? "document" : "documents"}
                    </span>
                  </div>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  );
}
